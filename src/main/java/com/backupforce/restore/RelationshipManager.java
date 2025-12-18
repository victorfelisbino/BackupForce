package com.backupforce.restore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manages relationship metadata for backup/restore operations.
 * Captures external IDs and unique business keys to preserve relationships
 * during data restoration (since Salesforce IDs cannot be inserted).
 */
public class RelationshipManager {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipManager.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final CloseableHttpClient httpClient;
    
    // Cache for object metadata
    private final Map<String, ObjectMetadata> metadataCache = new HashMap<>();
    
    public RelationshipManager(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.httpClient = HttpClients.createDefault();
    }
    
    /**
     * Describes an object and returns its metadata including relationships and external IDs
     */
    public ObjectMetadata describeObject(String objectName) throws IOException, ParseException {
        if (metadataCache.containsKey(objectName)) {
            return metadataCache.get(objectName);
        }
        
        String url = String.format("%s/services/data/v%s/sobjects/%s/describe", instanceUrl, apiVersion, objectName);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                throw new IOException("Failed to describe object " + objectName + ": " + responseBody);
            }
            
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            ObjectMetadata metadata = parseObjectMetadata(objectName, json);
            metadataCache.put(objectName, metadata);
            
            return metadata;
        }
    }
    
    private ObjectMetadata parseObjectMetadata(String objectName, JsonObject json) {
        ObjectMetadata metadata = new ObjectMetadata(objectName);
        
        JsonArray fields = json.getAsJsonArray("fields");
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.get(i).getAsJsonObject();
            String fieldName = field.get("name").getAsString();
            String fieldType = field.has("type") ? field.get("type").getAsString() : "";
            String label = field.has("label") ? field.get("label").getAsString() : fieldName;
            boolean isExternalId = field.has("externalId") && field.get("externalId").getAsBoolean();
            boolean isIdLookup = field.has("idLookup") && field.get("idLookup").getAsBoolean();
            boolean isUnique = field.has("unique") && field.get("unique").getAsBoolean();
            boolean isNameField = field.has("nameField") && field.get("nameField").getAsBoolean();
            boolean isNillable = field.has("nillable") && field.get("nillable").getAsBoolean();
            
            FieldInfo fieldInfo = new FieldInfo(fieldName, fieldType, label);
            fieldInfo.setExternalId(isExternalId);
            fieldInfo.setIdLookup(isIdLookup);
            fieldInfo.setUnique(isUnique);
            fieldInfo.setNameField(isNameField);
            fieldInfo.setNillable(isNillable);
            // Required means the field is NOT nillable
            fieldInfo.setRequired(!isNillable);
            
            // Track external ID fields
            if (isExternalId) {
                metadata.addExternalIdField(fieldInfo);
                logger.debug("{}: Found external ID field: {}", objectName, fieldName);
            }
            
            // Track unique fields that can be used as business keys
            if (isUnique && !fieldName.equals("Id")) {
                metadata.addUniqueField(fieldInfo);
            }
            
            // Track name field for fallback identification
            if (isNameField) {
                metadata.setNameField(fieldInfo);
            }
            
            // Track relationship fields (lookup/master-detail)
            if (fieldType.equals("reference")) {
                RelationshipField relField = parseRelationshipField(field, fieldInfo);
                if (relField != null) {
                    metadata.addRelationshipField(relField);
                    logger.debug("{}: Found relationship field: {} -> {}", 
                        objectName, fieldName, relField.getReferenceTo());
                }
            }
            
            metadata.addField(fieldInfo);
        }
        
        return metadata;
    }
    
    private RelationshipField parseRelationshipField(JsonObject field, FieldInfo fieldInfo) {
        JsonArray referenceTo = field.getAsJsonArray("referenceTo");
        if (referenceTo == null || referenceTo.size() == 0) {
            return null;
        }
        
        // Get the relationship name (used for polymorphic lookups)
        String relationshipName = field.has("relationshipName") && !field.get("relationshipName").isJsonNull() 
            ? field.get("relationshipName").getAsString() : null;
        
        List<String> referenceObjects = new ArrayList<>();
        for (int i = 0; i < referenceTo.size(); i++) {
            referenceObjects.add(referenceTo.get(i).getAsString());
        }
        
        return new RelationshipField(fieldInfo, referenceObjects, relationshipName);
    }
    
    /**
     * Generates the relationship mapping configuration for backup
     */
    public BackupRelationshipConfig generateBackupConfig(List<String> objectNames) throws IOException, ParseException {
        BackupRelationshipConfig config = new BackupRelationshipConfig();
        
        for (String objectName : objectNames) {
            ObjectMetadata metadata = describeObject(objectName);
            
            ObjectBackupConfig objConfig = new ObjectBackupConfig(objectName);
            
            // Determine the best external key strategy for this object
            ExternalKeyStrategy strategy = determineExternalKeyStrategy(metadata);
            objConfig.setExternalKeyStrategy(strategy);
            
            // Add relationship field mappings
            for (RelationshipField relField : metadata.getRelationshipFields()) {
                // For each lookup field, we need to store the related record's external key
                RelationshipMapping mapping = new RelationshipMapping(
                    relField.getFieldInfo().getName(),
                    relField.getReferenceTo(),
                    relField.getRelationshipName()
                );
                objConfig.addRelationshipMapping(mapping);
            }
            
            config.addObjectConfig(objConfig);
        }
        
        return config;
    }
    
    /**
     * Determines the best external key strategy for an object
     */
    private ExternalKeyStrategy determineExternalKeyStrategy(ObjectMetadata metadata) {
        ExternalKeyStrategy strategy = new ExternalKeyStrategy(metadata.getObjectName());
        
        // Priority 1: External ID fields (best for restore)
        if (!metadata.getExternalIdFields().isEmpty()) {
            FieldInfo primaryExtId = metadata.getExternalIdFields().get(0);
            strategy.setPrimaryKeyField(primaryExtId.getName());
            strategy.setKeyType(ExternalKeyType.EXTERNAL_ID);
            strategy.setSupportsUpsert(true);
            logger.info("{}: Using external ID field '{}' for relationship mapping", 
                metadata.getObjectName(), primaryExtId.getName());
            return strategy;
        }
        
        // Priority 2: Unique fields (can be used for matching)
        if (!metadata.getUniqueFields().isEmpty()) {
            FieldInfo uniqueField = metadata.getUniqueFields().get(0);
            strategy.setPrimaryKeyField(uniqueField.getName());
            strategy.setKeyType(ExternalKeyType.UNIQUE_FIELD);
            strategy.setSupportsUpsert(false); // Need external ID for upsert
            logger.info("{}: Using unique field '{}' for relationship mapping", 
                metadata.getObjectName(), uniqueField.getName());
            return strategy;
        }
        
        // Priority 3: Name field + other identifying fields (fallback)
        if (metadata.getNameField() != null) {
            strategy.setPrimaryKeyField(metadata.getNameField().getName());
            strategy.setKeyType(ExternalKeyType.NAME_BASED);
            strategy.setSupportsUpsert(false);
            
            // Add additional fields for composite key if needed
            strategy.addCompositeKeyField(metadata.getNameField().getName());
            
            logger.info("{}: Using name-based field '{}' for relationship mapping (may require manual matching)", 
                metadata.getObjectName(), metadata.getNameField().getName());
            return strategy;
        }
        
        // Fallback: ID only (will require ID mapping during restore)
        strategy.setPrimaryKeyField("Id");
        strategy.setKeyType(ExternalKeyType.SALESFORCE_ID);
        strategy.setSupportsUpsert(false);
        logger.warn("{}: No external ID or unique fields found - will use Salesforce ID (requires ID mapping during restore)", 
            metadata.getObjectName());
        
        return strategy;
    }
    
    /**
     * Saves relationship metadata to the backup folder
     */
    public void saveRelationshipMetadata(String outputFolder, BackupRelationshipConfig config) throws IOException {
        Path metadataPath = Paths.get(outputFolder, "_relationship_metadata.json");
        
        String json = gson.toJson(config);
        Files.writeString(metadataPath, json);
        
        logger.info("Saved relationship metadata to: {}", metadataPath);
    }
    
    /**
     * Loads relationship metadata from a backup folder
     */
    public BackupRelationshipConfig loadRelationshipMetadata(String backupFolder) throws IOException {
        Path metadataPath = Paths.get(backupFolder, "_relationship_metadata.json");
        
        if (!Files.exists(metadataPath)) {
            logger.warn("No relationship metadata found in backup folder");
            return null;
        }
        
        String json = Files.readString(metadataPath);
        return gson.fromJson(json, BackupRelationshipConfig.class);
    }
    
    /**
     * Generates the additional columns needed in the backup CSV to preserve relationships
     */
    public List<String> getAdditionalBackupColumns(ObjectMetadata metadata) {
        List<String> additionalColumns = new ArrayList<>();
        
        for (RelationshipField relField : metadata.getRelationshipFields()) {
            // For each lookup field, add columns for the related record's external keys
            String baseName = relField.getRelationshipName();
            if (baseName == null) {
                baseName = relField.getFieldInfo().getName().replace("Id", "");
            }
            
            // Add column for related record's external ID (if available)
            additionalColumns.add("_rel_" + baseName + "_ExternalId");
            
            // Add column for related record's Name (as fallback)
            additionalColumns.add("_rel_" + baseName + "_Name");
        }
        
        return additionalColumns;
    }
    
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.warn("Error closing HTTP client", e);
        }
    }
    
    // ==================== Inner Classes ====================
    
    public static class ObjectMetadata {
        private final String objectName;
        private final List<FieldInfo> fields = new ArrayList<>();
        private final List<FieldInfo> externalIdFields = new ArrayList<>();
        private final List<FieldInfo> uniqueFields = new ArrayList<>();
        private final List<RelationshipField> relationshipFields = new ArrayList<>();
        private FieldInfo nameField;
        
        public ObjectMetadata(String objectName) {
            this.objectName = objectName;
        }
        
        public String getObjectName() { return objectName; }
        public List<FieldInfo> getFields() { return fields; }
        public List<FieldInfo> getExternalIdFields() { return externalIdFields; }
        public List<FieldInfo> getUniqueFields() { return uniqueFields; }
        public List<RelationshipField> getRelationshipFields() { return relationshipFields; }
        public FieldInfo getNameField() { return nameField; }
        
        public void addField(FieldInfo field) { fields.add(field); }
        public void addExternalIdField(FieldInfo field) { externalIdFields.add(field); }
        public void addUniqueField(FieldInfo field) { uniqueFields.add(field); }
        public void addRelationshipField(RelationshipField field) { relationshipFields.add(field); }
        public void setNameField(FieldInfo field) { this.nameField = field; }
    }
    
    public static class FieldInfo {
        private final String name;
        private final String type;
        private final String label;
        private boolean externalId;
        private boolean idLookup;
        private boolean unique;
        private boolean nameField;
        private boolean required;
        private boolean nillable;
        
        public FieldInfo(String name, String type, String label) {
            this.name = name;
            this.type = type;
            this.label = label;
        }
        
        public String getName() { return name; }
        public String getType() { return type; }
        public String getLabel() { return label; }
        public boolean isExternalId() { return externalId; }
        public boolean isIdLookup() { return idLookup; }
        public boolean isUnique() { return unique; }
        public boolean isNameField() { return nameField; }
        public boolean isRequired() { return required; }
        public boolean isNillable() { return nillable; }
        
        public void setExternalId(boolean v) { this.externalId = v; }
        public void setIdLookup(boolean v) { this.idLookup = v; }
        public void setUnique(boolean v) { this.unique = v; }
        public void setNameField(boolean v) { this.nameField = v; }
        public void setRequired(boolean v) { this.required = v; }
        public void setNillable(boolean v) { this.nillable = v; }
    }
    
    public static class RelationshipField {
        private final FieldInfo fieldInfo;
        private final List<String> referenceTo;
        private final String relationshipName;
        
        public RelationshipField(FieldInfo fieldInfo, List<String> referenceTo, String relationshipName) {
            this.fieldInfo = fieldInfo;
            this.referenceTo = referenceTo;
            this.relationshipName = relationshipName;
        }
        
        public FieldInfo getFieldInfo() { return fieldInfo; }
        public List<String> getReferenceTo() { return referenceTo; }
        public String getRelationshipName() { return relationshipName; }
        
        public boolean isPolymorphic() { return referenceTo.size() > 1; }
    }
    
    public static class BackupRelationshipConfig {
        private final Map<String, ObjectBackupConfig> objects = new LinkedHashMap<>();
        private String backupDate;
        private String sourceOrg;
        
        public void addObjectConfig(ObjectBackupConfig config) {
            objects.put(config.getObjectName(), config);
        }
        
        public ObjectBackupConfig getObjectConfig(String objectName) {
            return objects.get(objectName);
        }
        
        public Map<String, ObjectBackupConfig> getObjects() { return objects; }
        
        public void setBackupDate(String date) { this.backupDate = date; }
        public String getBackupDate() { return backupDate; }
        
        public void setSourceOrg(String org) { this.sourceOrg = org; }
        public String getSourceOrg() { return sourceOrg; }
    }
    
    public static class ObjectBackupConfig {
        private final String objectName;
        private ExternalKeyStrategy externalKeyStrategy;
        private final List<RelationshipMapping> relationshipMappings = new ArrayList<>();
        
        public ObjectBackupConfig(String objectName) {
            this.objectName = objectName;
        }
        
        public String getObjectName() { return objectName; }
        
        public ExternalKeyStrategy getExternalKeyStrategy() { return externalKeyStrategy; }
        public void setExternalKeyStrategy(ExternalKeyStrategy strategy) { this.externalKeyStrategy = strategy; }
        
        public List<RelationshipMapping> getRelationshipMappings() { return relationshipMappings; }
        public void addRelationshipMapping(RelationshipMapping mapping) { relationshipMappings.add(mapping); }
    }
    
    public static class RelationshipMapping {
        private final String fieldName;
        private final List<String> referenceTo;
        private final String relationshipName;
        private String externalIdField; // External ID field on the related object
        
        public RelationshipMapping(String fieldName, List<String> referenceTo, String relationshipName) {
            this.fieldName = fieldName;
            this.referenceTo = referenceTo;
            this.relationshipName = relationshipName;
        }
        
        public String getFieldName() { return fieldName; }
        public List<String> getReferenceTo() { return referenceTo; }
        public String getRelationshipName() { return relationshipName; }
        public String getExternalIdField() { return externalIdField; }
        public void setExternalIdField(String field) { this.externalIdField = field; }
    }
    
    public enum ExternalKeyType {
        EXTERNAL_ID,      // Best - object has an External ID field
        UNIQUE_FIELD,     // Good - object has a unique field
        NAME_BASED,       // Okay - using Name field (may not be unique)
        SALESFORCE_ID     // Fallback - using SF ID (requires mapping)
    }
    
    public static class ExternalKeyStrategy {
        private final String objectName;
        private String primaryKeyField;
        private ExternalKeyType keyType;
        private boolean supportsUpsert;
        private final List<String> compositeKeyFields = new ArrayList<>();
        
        public ExternalKeyStrategy(String objectName) {
            this.objectName = objectName;
        }
        
        public String getObjectName() { return objectName; }
        public String getPrimaryKeyField() { return primaryKeyField; }
        public ExternalKeyType getKeyType() { return keyType; }
        public boolean isSupportsUpsert() { return supportsUpsert; }
        public List<String> getCompositeKeyFields() { return compositeKeyFields; }
        
        public void setPrimaryKeyField(String field) { this.primaryKeyField = field; }
        public void setKeyType(ExternalKeyType type) { this.keyType = type; }
        public void setSupportsUpsert(boolean v) { this.supportsUpsert = v; }
        public void addCompositeKeyField(String field) { compositeKeyFields.add(field); }
    }
}
