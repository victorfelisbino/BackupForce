package com.backupforce.restore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Loads and parses backup manifests and ID mapping files for restore operations.
 * 
 * The manifest contains:
 * - Source org information
 * - Object metadata (fields, relationships, external IDs)
 * - Restore order based on dependencies
 * - Related objects and their relationships
 * 
 * The ID mapping file contains:
 * - Salesforce ID to external identifier mappings
 * - Used to resolve lookup relationships during cross-org restore
 */
public class BackupManifestLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(BackupManifestLoader.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public static final String MANIFEST_FILENAME = "_backup_manifest.json";
    public static final String ID_MAPPING_FILENAME = "_id_mapping.json";
    
    private final Path backupFolder;
    private BackupManifest manifest;
    private IdMapping idMapping;
    
    public BackupManifestLoader(Path backupFolder) {
        this.backupFolder = backupFolder;
    }
    
    /**
     * Check if the backup folder contains a manifest file.
     */
    public boolean hasManifest() {
        return Files.exists(backupFolder.resolve(MANIFEST_FILENAME));
    }
    
    /**
     * Check if the backup folder contains an ID mapping file.
     */
    public boolean hasIdMapping() {
        return Files.exists(backupFolder.resolve(ID_MAPPING_FILENAME));
    }
    
    /**
     * Load the backup manifest from the backup folder.
     * @return The parsed manifest, or null if not found
     */
    public BackupManifest loadManifest() throws IOException {
        Path manifestPath = backupFolder.resolve(MANIFEST_FILENAME);
        if (!Files.exists(manifestPath)) {
            logger.debug("No manifest file found at {}", manifestPath);
            return null;
        }
        
        try {
            String json = Files.readString(manifestPath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            manifest = new BackupManifest();
            
            // Parse metadata
            if (root.has("metadata")) {
                JsonObject meta = root.getAsJsonObject("metadata");
                manifest.version = getStringOrNull(meta, "version");
                manifest.generatedAt = getStringOrNull(meta, "generatedAt");
                manifest.sourceInstanceUrl = getStringOrNull(meta, "sourceInstanceUrl");
                manifest.apiVersion = getStringOrNull(meta, "apiVersion");
                manifest.backupType = getStringOrNull(meta, "backupType");
            }
            
            // Parse parent objects
            if (root.has("parentObjects")) {
                JsonArray parents = root.getAsJsonArray("parentObjects");
                for (JsonElement elem : parents) {
                    manifest.parentObjects.add(elem.getAsString());
                }
            }
            
            // Parse related objects
            if (root.has("relatedObjects")) {
                JsonArray related = root.getAsJsonArray("relatedObjects");
                for (JsonElement elem : related) {
                    JsonObject rel = elem.getAsJsonObject();
                    RelatedObjectInfo info = new RelatedObjectInfo(
                        getStringOrNull(rel, "objectName"),
                        getStringOrNull(rel, "parentObject"),
                        getStringOrNull(rel, "parentField"),
                        rel.has("depth") ? rel.get("depth").getAsInt() : 0,
                        rel.has("priority") && rel.get("priority").getAsBoolean()
                    );
                    manifest.relatedObjects.add(info);
                }
            }
            
            // Parse restore order
            if (root.has("restoreOrder")) {
                JsonArray order = root.getAsJsonArray("restoreOrder");
                for (JsonElement elem : order) {
                    manifest.restoreOrder.add(elem.getAsString());
                }
            }
            
            // Parse object metadata
            if (root.has("objects")) {
                JsonObject objects = root.getAsJsonObject("objects");
                for (String objectName : objects.keySet()) {
                    JsonObject objData = objects.getAsJsonObject(objectName);
                    ObjectMetadataInfo objInfo = parseObjectMetadata(objectName, objData);
                    manifest.objectMetadata.put(objectName, objInfo);
                }
            }
            
            logger.info("Loaded backup manifest: version={}, type={}, objects={}",
                manifest.version, manifest.backupType, manifest.restoreOrder.size());
            
            return manifest;
            
        } catch (Exception e) {
            logger.error("Failed to parse backup manifest", e);
            throw new IOException("Failed to parse backup manifest: " + e.getMessage(), e);
        }
    }
    
    /**
     * Load the ID mapping file from the backup folder.
     * @return The parsed ID mapping, or null if not found
     */
    public IdMapping loadIdMapping() throws IOException {
        Path mappingPath = backupFolder.resolve(ID_MAPPING_FILENAME);
        if (!Files.exists(mappingPath)) {
            logger.debug("No ID mapping file found at {}", mappingPath);
            return null;
        }
        
        try {
            String json = Files.readString(mappingPath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            idMapping = new IdMapping();
            idMapping.generatedAt = getStringOrNull(root, "generatedAt");
            
            // Parse mappings per object
            if (root.has("mappings")) {
                JsonObject mappings = root.getAsJsonObject("mappings");
                for (String objectName : mappings.keySet()) {
                    JsonObject objMapping = mappings.getAsJsonObject(objectName);
                    
                    ObjectIdMapping objIdMapping = new ObjectIdMapping();
                    objIdMapping.objectName = objectName;
                    objIdMapping.identifierField = getStringOrNull(objMapping, "identifierField");
                    objIdMapping.recordCount = objMapping.has("recordCount") ? 
                        objMapping.get("recordCount").getAsInt() : 0;
                    
                    // Parse the actual ID to identifier mappings
                    if (objMapping.has("idToIdentifier")) {
                        JsonObject idToId = objMapping.getAsJsonObject("idToIdentifier");
                        for (String sfId : idToId.keySet()) {
                            objIdMapping.idToIdentifier.put(sfId, idToId.get(sfId).getAsString());
                        }
                    }
                    
                    idMapping.objectMappings.put(objectName, objIdMapping);
                }
            }
            
            logger.info("Loaded ID mapping file: {} objects with mappings", idMapping.objectMappings.size());
            
            return idMapping;
            
        } catch (Exception e) {
            logger.error("Failed to parse ID mapping file", e);
            throw new IOException("Failed to parse ID mapping file: " + e.getMessage(), e);
        }
    }
    
    private ObjectMetadataInfo parseObjectMetadata(String objectName, JsonObject objData) {
        ObjectMetadataInfo info = new ObjectMetadataInfo();
        info.objectName = objectName;
        
        if (objData.has("recordCount")) {
            info.recordCount = objData.get("recordCount").getAsLong();
        }
        if (objData.has("fileName")) {
            info.fileName = objData.get("fileName").getAsString();
        }
        if (objData.has("recommendedUpsertField")) {
            info.recommendedUpsertField = objData.get("recommendedUpsertField").getAsString();
        }
        if (objData.has("nameField")) {
            info.nameField = objData.get("nameField").getAsString();
        }
        
        // Parse external ID fields
        if (objData.has("externalIdFields")) {
            JsonArray extIds = objData.getAsJsonArray("externalIdFields");
            for (JsonElement elem : extIds) {
                JsonObject field = elem.getAsJsonObject();
                info.externalIdFields.add(getStringOrNull(field, "name"));
            }
        }
        
        // Parse unique fields
        if (objData.has("uniqueFields")) {
            JsonArray unique = objData.getAsJsonArray("uniqueFields");
            for (JsonElement elem : unique) {
                info.uniqueFields.add(elem.getAsString());
            }
        }
        
        // Parse relationship fields
        if (objData.has("relationshipFields")) {
            JsonArray rels = objData.getAsJsonArray("relationshipFields");
            for (JsonElement elem : rels) {
                JsonObject rel = elem.getAsJsonObject();
                RelationshipFieldInfo relInfo = new RelationshipFieldInfo();
                relInfo.fieldName = getStringOrNull(rel, "fieldName");
                relInfo.relationshipName = getStringOrNull(rel, "relationshipName");
                relInfo.polymorphic = rel.has("polymorphic") && rel.get("polymorphic").getAsBoolean();
                
                if (rel.has("referenceTo")) {
                    JsonArray refs = rel.getAsJsonArray("referenceTo");
                    for (JsonElement refElem : refs) {
                        relInfo.referenceTo.add(refElem.getAsString());
                    }
                }
                
                info.relationshipFields.add(relInfo);
            }
        }
        
        return info;
    }
    
    private String getStringOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
    
    public BackupManifest getManifest() {
        return manifest;
    }
    
    public IdMapping getIdMapping() {
        return idMapping;
    }
    
    // ==================== Data Classes ====================
    
    /**
     * Represents the complete backup manifest.
     */
    public static class BackupManifest {
        public String version;
        public String generatedAt;
        public String sourceInstanceUrl;
        public String apiVersion;
        public String backupType; // "standard" or "relationship-aware"
        
        public List<String> parentObjects = new ArrayList<>();
        public List<RelatedObjectInfo> relatedObjects = new ArrayList<>();
        public List<String> restoreOrder = new ArrayList<>();
        public Map<String, ObjectMetadataInfo> objectMetadata = new LinkedHashMap<>();
        
        public boolean isRelationshipAware() {
            return "relationship-aware".equals(backupType);
        }
        
        public int getTotalObjectCount() {
            return restoreOrder.size();
        }
        
        /**
         * Get the recommended external ID field for an object.
         */
        public String getRecommendedUpsertField(String objectName) {
            ObjectMetadataInfo info = objectMetadata.get(objectName);
            return info != null ? info.recommendedUpsertField : null;
        }
        
        /**
         * Get relationship info for an object (if it was included as a related object).
         */
        public RelatedObjectInfo getRelatedObjectInfo(String objectName) {
            for (RelatedObjectInfo rel : relatedObjects) {
                if (rel.objectName.equals(objectName)) {
                    return rel;
                }
            }
            return null;
        }
    }
    
    /**
     * Information about a related object in the backup.
     */
    public static class RelatedObjectInfo {
        public String objectName;
        public String parentObject;
        public String parentField;
        public int depth;
        public boolean priority;
        
        public RelatedObjectInfo() {}
        
        public RelatedObjectInfo(String objectName, String parentObject, String parentField,
                                int depth, boolean priority) {
            this.objectName = objectName;
            this.parentObject = parentObject;
            this.parentField = parentField;
            this.depth = depth;
            this.priority = priority;
        }
    }
    
    /**
     * Metadata about an object in the backup.
     */
    public static class ObjectMetadataInfo {
        public String objectName;
        public long recordCount;
        public String fileName;
        public String recommendedUpsertField;
        public String nameField;
        public List<String> externalIdFields = new ArrayList<>();
        public List<String> uniqueFields = new ArrayList<>();
        public List<RelationshipFieldInfo> relationshipFields = new ArrayList<>();
    }
    
    /**
     * Information about a relationship field.
     */
    public static class RelationshipFieldInfo {
        public String fieldName;
        public String relationshipName;
        public List<String> referenceTo = new ArrayList<>();
        public boolean polymorphic;
    }
    
    /**
     * Complete ID mapping data for cross-org restore.
     */
    public static class IdMapping {
        public String generatedAt;
        public Map<String, ObjectIdMapping> objectMappings = new LinkedHashMap<>();
        
        /**
         * Get the external identifier for a source Salesforce ID.
         * @param objectName The object type
         * @param sourceId The source Salesforce ID
         * @return The external identifier, or null if not found
         */
        public String getIdentifierForId(String objectName, String sourceId) {
            ObjectIdMapping objMapping = objectMappings.get(objectName);
            if (objMapping != null) {
                return objMapping.idToIdentifier.get(sourceId);
            }
            return null;
        }
        
        /**
         * Get the identifier field name for an object.
         */
        public String getIdentifierField(String objectName) {
            ObjectIdMapping objMapping = objectMappings.get(objectName);
            return objMapping != null ? objMapping.identifierField : null;
        }
    }
    
    /**
     * ID mapping for a single object.
     */
    public static class ObjectIdMapping {
        public String objectName;
        public String identifierField;
        public int recordCount;
        public Map<String, String> idToIdentifier = new LinkedHashMap<>();
        
        /**
         * Reverse lookup: find the source ID from an identifier.
         */
        public String getIdForIdentifier(String identifier) {
            for (Map.Entry<String, String> entry : idToIdentifier.entrySet()) {
                if (entry.getValue().equals(identifier)) {
                    return entry.getKey();
                }
            }
            return null;
        }
    }
}
