package com.backupforce.restore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves relationship references during data restoration.
 * Uses the _ref_* columns from enriched backups to look up target org IDs.
 * 
 * The enriched backup format is:
 * - Original field: AccountId (contains source org ID)
 * - Reference field: _ref_AccountId_Name (contains the Name value for lookup)
 * 
 * This class queries the target org to find matching records and replaces
 * the original field value with the target org's ID.
 */
public class RelationshipResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipResolver.class);
    
    // Pattern to match _ref_ columns: _ref_{fieldName}_{lookupField}
    private static final Pattern REF_PATTERN = Pattern.compile("^_ref_(.+?)_(.+)$");
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final CloseableHttpClient httpClient;
    private final RelationshipManager relationshipManager;
    
    // Cache for resolved IDs: objectName -> (lookupField -> lookupValue -> Id)
    private final Map<String, Map<String, Map<String, String>>> resolvedIdCache = new HashMap<>();
    
    private Consumer<String> logCallback;
    
    public RelationshipResolver(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.httpClient = HttpClients.createDefault();
        this.relationshipManager = new RelationshipManager(instanceUrl, accessToken, apiVersion);
    }
    
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }
    
    private void log(String message) {
        logger.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }
    
    /**
     * Resolves relationship references in a list of records.
     * Finds _ref_* columns, looks up matching records in the target org,
     * and updates the original lookup fields with the new IDs.
     * 
     * @param objectName The Salesforce object being restored
     * @param records List of records with potential _ref_ columns
     * @return Records with lookup fields updated to target org IDs
     */
    public List<Map<String, String>> resolveRelationships(String objectName, 
                                                           List<Map<String, String>> records) 
                                                           throws IOException, ParseException {
        
        if (records.isEmpty()) {
            return records;
        }
        
        // Find all _ref_ columns
        Map<String, String> headers = records.get(0);
        Map<String, RefColumnInfo> refColumns = findRefColumns(headers.keySet());
        
        if (refColumns.isEmpty()) {
            log(objectName + ": No relationship reference columns found, skipping resolution");
            return records;
        }
        
        log(objectName + ": Found " + refColumns.size() + " relationship reference columns");
        
        // Get metadata for the object to understand relationships
        RelationshipManager.ObjectMetadata metadata = relationshipManager.describeObject(objectName);
        
        // Collect all unique lookup values per referenced object
        Map<String, Map<String, Set<String>>> valuesToResolve = new LinkedHashMap<>();
        // refObject -> lookupField -> set of values
        
        for (RefColumnInfo refCol : refColumns.values()) {
            // Determine referenced object from field metadata
            String refObject = getReferencedObject(metadata, refCol.fieldName);
            if (refObject == null) {
                logger.warn("Could not determine referenced object for field: {}", refCol.fieldName);
                continue;
            }
            refCol.referencedObject = refObject;
            
            valuesToResolve.computeIfAbsent(refObject, k -> new LinkedHashMap<>())
                           .computeIfAbsent(refCol.lookupField, k -> new HashSet<>());
            
            // Collect values from all records
            for (Map<String, String> record : records) {
                String value = record.get(refCol.columnName);
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    valuesToResolve.get(refObject).get(refCol.lookupField).add(value);
                }
            }
        }
        
        // Resolve all values
        for (Map.Entry<String, Map<String, Set<String>>> objEntry : valuesToResolve.entrySet()) {
            String refObject = objEntry.getKey();
            
            for (Map.Entry<String, Set<String>> fieldEntry : objEntry.getValue().entrySet()) {
                String lookupField = fieldEntry.getKey();
                Set<String> values = fieldEntry.getValue();
                
                if (!values.isEmpty()) {
                    log(objectName + ": Resolving " + values.size() + " " + refObject + " references via " + lookupField);
                    resolveValues(refObject, lookupField, values);
                }
            }
        }
        
        // Apply resolved IDs to records
        List<Map<String, String>> resolvedRecords = new ArrayList<>();
        
        for (Map<String, String> record : records) {
            Map<String, String> resolvedRecord = new LinkedHashMap<>(record);
            
            for (RefColumnInfo refCol : refColumns.values()) {
                if (refCol.referencedObject == null) continue;
                
                String refValue = record.get(refCol.columnName);
                if (refValue != null && !refValue.isEmpty() && !refValue.equals("null")) {
                    // Look up the resolved ID
                    String resolvedId = getCachedId(refCol.referencedObject, refCol.lookupField, refValue);
                    
                    if (resolvedId != null) {
                        // Update the original field with the resolved ID
                        resolvedRecord.put(refCol.fieldName, resolvedId);
                        logger.debug("Resolved {}.{} = {} -> {}", objectName, refCol.fieldName, refValue, resolvedId);
                    } else {
                        logger.debug("Could not resolve {}.{} = {} (no match found)", 
                                    objectName, refCol.fieldName, refValue);
                        // Clear the field to avoid inserting stale ID
                        resolvedRecord.remove(refCol.fieldName);
                    }
                }
            }
            
            resolvedRecords.add(resolvedRecord);
        }
        
        return resolvedRecords;
    }
    
    /**
     * Finds all _ref_* columns in the record headers
     */
    private Map<String, RefColumnInfo> findRefColumns(Set<String> headers) {
        Map<String, RefColumnInfo> refColumns = new LinkedHashMap<>();
        
        for (String header : headers) {
            Matcher matcher = REF_PATTERN.matcher(header);
            if (matcher.matches()) {
                String fieldName = matcher.group(1);
                String lookupField = matcher.group(2);
                refColumns.put(fieldName, new RefColumnInfo(header, fieldName, lookupField));
            }
        }
        
        return refColumns;
    }
    
    /**
     * Gets the referenced object type for a lookup/relationship field
     */
    private String getReferencedObject(RelationshipManager.ObjectMetadata metadata, String fieldName) {
        for (RelationshipManager.RelationshipField relField : metadata.getRelationshipFields()) {
            if (relField.getFieldInfo().getName().equals(fieldName)) {
                List<String> refs = relField.getReferenceTo();
                if (!refs.isEmpty()) {
                    // Return the first (primary) referenced object
                    return refs.get(0);
                }
            }
        }
        
        // Common patterns for standard fields
        if (fieldName.equals("OwnerId")) return "User";
        if (fieldName.equals("CreatedById")) return "User";
        if (fieldName.equals("LastModifiedById")) return "User";
        if (fieldName.endsWith("Id") && !fieldName.equals("Id")) {
            // Try to infer object from field name (e.g., AccountId -> Account)
            String objectName = fieldName.substring(0, fieldName.length() - 2);
            return objectName;
        }
        
        return null;
    }
    
    /**
     * Resolves values to Salesforce IDs by querying the target org
     */
    private void resolveValues(String objectName, String lookupField, Set<String> values) 
            throws IOException, ParseException {
        
        // Initialize cache for this object/field combination
        resolvedIdCache.computeIfAbsent(objectName, k -> new HashMap<>())
                       .computeIfAbsent(lookupField, k -> new HashMap<>());
        
        Map<String, String> cache = resolvedIdCache.get(objectName).get(lookupField);
        
        // Filter out already resolved values
        Set<String> unresolvedValues = new HashSet<>();
        for (String value : values) {
            if (!cache.containsKey(value)) {
                unresolvedValues.add(value);
            }
        }
        
        if (unresolvedValues.isEmpty()) {
            return;
        }
        
        // Query in batches (SOQL IN clause has limits)
        List<String> valueList = new ArrayList<>(unresolvedValues);
        int batchSize = 100;
        
        for (int i = 0; i < valueList.size(); i += batchSize) {
            List<String> batch = valueList.subList(i, Math.min(i + batchSize, valueList.size()));
            queryAndCache(objectName, lookupField, batch, cache);
        }
    }
    
    private void queryAndCache(String objectName, String lookupField, List<String> values, 
                                Map<String, String> cache) throws IOException, ParseException {
        
        // Build SOQL query
        StringBuilder soql = new StringBuilder();
        soql.append("SELECT Id, ").append(lookupField);
        soql.append(" FROM ").append(objectName);
        soql.append(" WHERE ").append(lookupField).append(" IN (");
        
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) soql.append(", ");
            soql.append("'").append(escapeSoqlString(values.get(i))).append("'");
        }
        soql.append(")");
        
        String url = instanceUrl + "/services/data/v" + apiVersion + "/query?q=" + 
                     URLEncoder.encode(soql.toString(), StandardCharsets.UTF_8);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                logger.warn("Failed to resolve {} references via {}: {}", objectName, lookupField, responseBody);
                return;
            }
            
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray records = json.getAsJsonArray("records");
            
            for (int i = 0; i < records.size(); i++) {
                JsonObject record = records.get(i).getAsJsonObject();
                String id = record.get("Id").getAsString();
                
                if (record.has(lookupField) && !record.get(lookupField).isJsonNull()) {
                    String lookupValue = record.get(lookupField).getAsString();
                    cache.put(lookupValue, id);
                }
            }
            
            logger.debug("Resolved {} {} records via {}", records.size(), objectName, lookupField);
        }
    }
    
    private String getCachedId(String objectName, String lookupField, String value) {
        Map<String, Map<String, String>> objectCache = resolvedIdCache.get(objectName);
        if (objectCache == null) return null;
        
        Map<String, String> fieldCache = objectCache.get(lookupField);
        if (fieldCache == null) return null;
        
        return fieldCache.get(value);
    }
    
    private String escapeSoqlString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }
    
    /**
     * Clears the resolution cache
     */
    public void clearCache() {
        resolvedIdCache.clear();
    }
    
    /**
     * Gets statistics about resolved relationships
     */
    public Map<String, Integer> getResolutionStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Map<String, String>>> objEntry : resolvedIdCache.entrySet()) {
            int count = 0;
            for (Map<String, String> fieldCache : objEntry.getValue().values()) {
                count += fieldCache.size();
            }
            stats.put(objEntry.getKey(), count);
        }
        return stats;
    }
    
    /**
     * Information about a reference column
     */
    private static class RefColumnInfo {
        final String columnName;     // Full column name (e.g., _ref_AccountId_Name)
        final String fieldName;      // Original field name (e.g., AccountId)
        final String lookupField;    // Field to look up (e.g., Name)
        String referencedObject;     // Referenced object (e.g., Account)
        
        RefColumnInfo(String columnName, String fieldName, String lookupField) {
            this.columnName = columnName;
            this.fieldName = fieldName;
            this.lookupField = lookupField;
        }
    }
}
