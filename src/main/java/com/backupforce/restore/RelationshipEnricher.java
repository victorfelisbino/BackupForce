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

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Enriches backup CSV files with relationship data for restoration.
 * Adds external ID references to lookup fields so relationships can be 
 * restored without using Salesforce IDs.
 */
public class RelationshipEnricher {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipEnricher.class);
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final CloseableHttpClient httpClient;
    private final RelationshipManager relationshipManager;
    
    // Cache for ID -> External Key mappings per object
    private final Map<String, Map<String, ExternalKeyData>> idMappingCache = new HashMap<>();
    
    public RelationshipEnricher(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.httpClient = HttpClients.createDefault();
        this.relationshipManager = new RelationshipManager(instanceUrl, accessToken, apiVersion);
    }
    
    /**
     * Enriches a backup CSV file with external key data for relationships
     */
    public void enrichBackupFile(String objectName, Path csvPath, Path outputPath) throws IOException, ParseException {
        logger.info("Enriching backup file for {} with relationship data", objectName);
        
        RelationshipManager.ObjectMetadata metadata = relationshipManager.describeObject(objectName);
        List<RelationshipManager.RelationshipField> relationships = metadata.getRelationshipFields();
        
        if (relationships.isEmpty()) {
            logger.info("{}: No relationship fields found, copying file as-is", objectName);
            Files.copy(csvPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        
        // Read the CSV and add relationship columns
        List<String[]> rows = readCsv(csvPath);
        if (rows.isEmpty()) {
            return;
        }
        
        String[] headers = rows.get(0);
        Map<String, Integer> headerIndex = buildHeaderIndex(headers);
        
        // Prepare new headers for relationship data
        List<String> newHeaders = new ArrayList<>(Arrays.asList(headers));
        Map<String, RelationshipColumnInfo> relationshipColumns = new LinkedHashMap<>();
        
        for (RelationshipManager.RelationshipField relField : relationships) {
            String fieldName = relField.getFieldInfo().getName();
            if (!headerIndex.containsKey(fieldName)) {
                continue; // This relationship field wasn't in the backup
            }
            
            // For each referenced object, get its external key strategy
            for (String refObject : relField.getReferenceTo()) {
                try {
                    RelationshipManager.ObjectMetadata refMetadata = relationshipManager.describeObject(refObject);
                    RelationshipManager.ExternalKeyStrategy strategy = determineKeyStrategy(refMetadata);
                    
                    String colName = "_ref_" + fieldName + "_" + strategy.getPrimaryKeyField();
                    if (!newHeaders.contains(colName)) {
                        newHeaders.add(colName);
                        relationshipColumns.put(colName, new RelationshipColumnInfo(
                            fieldName, refObject, strategy.getPrimaryKeyField(), headerIndex.get(fieldName)
                        ));
                    }
                } catch (Exception e) {
                    logger.warn("Could not describe related object {}: {}", refObject, e.getMessage());
                }
            }
        }
        
        // Collect all IDs that need to be resolved
        Map<String, Set<String>> objectIdsToResolve = new LinkedHashMap<>();
        for (RelationshipColumnInfo colInfo : relationshipColumns.values()) {
            objectIdsToResolve.computeIfAbsent(colInfo.referencedObject, k -> new HashSet<>());
            
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (colInfo.sourceColumnIndex < row.length) {
                    String idValue = row[colInfo.sourceColumnIndex];
                    if (idValue != null && !idValue.isEmpty() && !idValue.equals("null")) {
                        objectIdsToResolve.get(colInfo.referencedObject).add(idValue);
                    }
                }
            }
        }
        
        // Resolve all external keys in batches
        for (Map.Entry<String, Set<String>> entry : objectIdsToResolve.entrySet()) {
            String refObject = entry.getKey();
            Set<String> ids = entry.getValue();
            if (!ids.isEmpty()) {
                resolveExternalKeys(refObject, ids);
            }
        }
        
        // Write enriched CSV
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            // Write headers
            writer.println(String.join(",", escapeForCsv(newHeaders)));
            
            // Write data rows
            for (int i = 1; i < rows.size(); i++) {
                String[] originalRow = rows.get(i);
                List<String> newRow = new ArrayList<>(Arrays.asList(originalRow));
                
                // Add relationship columns
                for (Map.Entry<String, RelationshipColumnInfo> entry : relationshipColumns.entrySet()) {
                    RelationshipColumnInfo colInfo = entry.getValue();
                    String idValue = colInfo.sourceColumnIndex < originalRow.length ? 
                        originalRow[colInfo.sourceColumnIndex] : "";
                    
                    String externalKey = "";
                    if (idValue != null && !idValue.isEmpty() && !idValue.equals("null")) {
                        Map<String, ExternalKeyData> objectCache = idMappingCache.get(colInfo.referencedObject);
                        if (objectCache != null) {
                            ExternalKeyData keyData = objectCache.get(idValue);
                            if (keyData != null) {
                                externalKey = keyData.getKeyValue(colInfo.keyField);
                            }
                        }
                    }
                    newRow.add(externalKey);
                }
                
                writer.println(String.join(",", escapeForCsv(newRow)));
            }
        }
        
        logger.info("{}: Enriched backup with {} relationship columns", objectName, relationshipColumns.size());
    }
    
    /**
     * Resolves external keys for a set of Salesforce IDs
     */
    private void resolveExternalKeys(String objectName, Set<String> ids) throws IOException, ParseException {
        if (ids.isEmpty()) return;
        
        logger.info("Resolving {} external keys for {}", ids.size(), objectName);
        
        // Get the fields we need to query
        RelationshipManager.ObjectMetadata metadata = relationshipManager.describeObject(objectName);
        Set<String> fieldsToQuery = new LinkedHashSet<>();
        fieldsToQuery.add("Id");
        
        // Add external ID fields
        for (RelationshipManager.FieldInfo extIdField : metadata.getExternalIdFields()) {
            fieldsToQuery.add(extIdField.getName());
        }
        
        // Add unique fields
        for (RelationshipManager.FieldInfo uniqueField : metadata.getUniqueFields()) {
            fieldsToQuery.add(uniqueField.getName());
        }
        
        // Add name field
        if (metadata.getNameField() != null) {
            fieldsToQuery.add(metadata.getNameField().getName());
        }
        
        // Query in batches (SOQL IN clause has limits)
        List<String> idList = new ArrayList<>(ids);
        int batchSize = 200;
        
        Map<String, ExternalKeyData> objectCache = idMappingCache.computeIfAbsent(objectName, k -> new HashMap<>());
        
        for (int i = 0; i < idList.size(); i += batchSize) {
            List<String> batch = idList.subList(i, Math.min(i + batchSize, idList.size()));
            queryExternalKeys(objectName, batch, fieldsToQuery, objectCache);
        }
    }
    
    private void queryExternalKeys(String objectName, List<String> ids, Set<String> fields, 
                                   Map<String, ExternalKeyData> cache) throws IOException, ParseException {
        String fieldList = String.join(", ", fields);
        String idList = "'" + String.join("','", ids) + "'";
        String soql = String.format("SELECT %s FROM %s WHERE Id IN (%s)", fieldList, objectName, idList);
        
        String url = String.format("%s/services/data/v%s/query?q=%s", 
            instanceUrl, apiVersion, java.net.URLEncoder.encode(soql, "UTF-8"));
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                logger.warn("Failed to query external keys for {}: {}", objectName, responseBody);
                return;
            }
            
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray records = json.getAsJsonArray("records");
            
            for (int i = 0; i < records.size(); i++) {
                JsonObject record = records.get(i).getAsJsonObject();
                String id = record.get("Id").getAsString();
                
                ExternalKeyData keyData = new ExternalKeyData(id);
                for (String field : fields) {
                    if (record.has(field) && !record.get(field).isJsonNull()) {
                        keyData.addKeyValue(field, record.get(field).getAsString());
                    }
                }
                
                cache.put(id, keyData);
            }
        }
    }
    
    private RelationshipManager.ExternalKeyStrategy determineKeyStrategy(RelationshipManager.ObjectMetadata metadata) {
        RelationshipManager.ExternalKeyStrategy strategy = new RelationshipManager.ExternalKeyStrategy(metadata.getObjectName());
        
        if (!metadata.getExternalIdFields().isEmpty()) {
            strategy.setPrimaryKeyField(metadata.getExternalIdFields().get(0).getName());
            strategy.setKeyType(RelationshipManager.ExternalKeyType.EXTERNAL_ID);
        } else if (!metadata.getUniqueFields().isEmpty()) {
            strategy.setPrimaryKeyField(metadata.getUniqueFields().get(0).getName());
            strategy.setKeyType(RelationshipManager.ExternalKeyType.UNIQUE_FIELD);
        } else if (metadata.getNameField() != null) {
            strategy.setPrimaryKeyField(metadata.getNameField().getName());
            strategy.setKeyType(RelationshipManager.ExternalKeyType.NAME_BASED);
        } else {
            strategy.setPrimaryKeyField("Id");
            strategy.setKeyType(RelationshipManager.ExternalKeyType.SALESFORCE_ID);
        }
        
        return strategy;
    }
    
    private List<String[]> readCsv(Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        }
        
        return rows;
    }
    
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        
        return values.toArray(new String[0]);
    }
    
    private Map<String, Integer> buildHeaderIndex(String[] headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            index.put(headers[i], i);
        }
        return index;
    }
    
    private List<String> escapeForCsv(List<String> values) {
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            escaped.add(escapeForCsv(value));
        }
        return escaped;
    }
    
    private String escapeForCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    public void close() {
        try {
            httpClient.close();
            relationshipManager.close();
        } catch (IOException e) {
            logger.warn("Error closing resources", e);
        }
    }
    
    // ==================== Inner Classes ====================
    
    private static class RelationshipColumnInfo {
        final String sourceField;
        final String referencedObject;
        final String keyField;
        final int sourceColumnIndex;
        
        RelationshipColumnInfo(String sourceField, String referencedObject, String keyField, int sourceColumnIndex) {
            this.sourceField = sourceField;
            this.referencedObject = referencedObject;
            this.keyField = keyField;
            this.sourceColumnIndex = sourceColumnIndex;
        }
    }
    
    private static class ExternalKeyData {
        private final String salesforceId;
        private final Map<String, String> keyValues = new HashMap<>();
        
        ExternalKeyData(String salesforceId) {
            this.salesforceId = salesforceId;
        }
        
        void addKeyValue(String field, String value) {
            keyValues.put(field, value);
        }
        
        String getKeyValue(String field) {
            return keyValues.getOrDefault(field, "");
        }
        
        String getSalesforceId() {
            return salesforceId;
        }
    }
}
