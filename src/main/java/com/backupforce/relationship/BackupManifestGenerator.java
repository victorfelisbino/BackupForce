package com.backupforce.relationship;

import com.backupforce.restore.RelationshipManager;
import com.backupforce.restore.RelationshipManager.ObjectMetadata;
import com.backupforce.restore.RelationshipManager.FieldInfo;
import com.backupforce.restore.RelationshipManager.RelationshipField;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Generates comprehensive backup manifests for restore and cross-org migration.
 * 
 * The manifest includes:
 * - Source org information
 * - Object metadata (fields, relationships, external IDs)
 * - Record type mappings
 * - ID mappings for cross-org restore
 * - Restore order based on dependencies
 * 
 * This manifest enables:
 * - Restoring data to the same org
 * - Migrating data to a different org
 * - Seeding test/sandbox environments
 */
public class BackupManifestGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(BackupManifestGenerator.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final RelationshipManager relationshipManager;
    
    // Configuration options
    private boolean captureExternalIds = true;
    private boolean captureFieldMetadata = true;
    private boolean generateIdMapping = true;
    private boolean includeRecordTypes = true;
    
    public BackupManifestGenerator(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.relationshipManager = new RelationshipManager(instanceUrl, accessToken, apiVersion);
    }
    
    public void setCaptureExternalIds(boolean value) { this.captureExternalIds = value; }
    public void setCaptureFieldMetadata(boolean value) { this.captureFieldMetadata = value; }
    public void setGenerateIdMapping(boolean value) { this.generateIdMapping = value; }
    public void setIncludeRecordTypes(boolean value) { this.includeRecordTypes = value; }
    
    /**
     * Generates the complete backup manifest.
     * 
     * @param outputFolder The folder containing backup CSVs
     * @param backedUpObjects List of objects that were backed up
     * @param parentObjects Objects that were primary (user-selected)
     * @param relatedObjects Objects that were included due to relationships
     * @return Path to the generated manifest file
     */
    public Path generateManifest(String outputFolder, List<String> backedUpObjects,
                                List<String> parentObjects, 
                                List<RelatedObjectInfo> relatedObjects) throws Exception {
        
        JsonObject manifest = new JsonObject();
        
        // === Backup Metadata ===
        JsonObject metadata = new JsonObject();
        metadata.addProperty("version", "2.0");
        metadata.addProperty("generatedAt", Instant.now().toString());
        metadata.addProperty("sourceInstanceUrl", instanceUrl);
        metadata.addProperty("apiVersion", apiVersion);
        metadata.addProperty("backupType", relatedObjects.isEmpty() ? "standard" : "relationship-aware");
        manifest.add("metadata", metadata);
        
        // === Options Used ===
        JsonObject options = new JsonObject();
        options.addProperty("captureExternalIds", captureExternalIds);
        options.addProperty("captureFieldMetadata", captureFieldMetadata);
        options.addProperty("generateIdMapping", generateIdMapping);
        options.addProperty("includeRecordTypes", includeRecordTypes);
        manifest.add("options", options);
        
        // === Parent Objects ===
        JsonArray parentsArray = new JsonArray();
        for (String obj : parentObjects) {
            parentsArray.add(obj);
        }
        manifest.add("parentObjects", parentsArray);
        
        // === Related Objects with Relationship Info ===
        JsonArray relatedArray = new JsonArray();
        for (RelatedObjectInfo rel : relatedObjects) {
            JsonObject relObj = new JsonObject();
            relObj.addProperty("objectName", rel.getObjectName());
            relObj.addProperty("parentObject", rel.getParentObject());
            relObj.addProperty("parentField", rel.getParentField());
            relObj.addProperty("depth", rel.getDepth());
            relObj.addProperty("priority", rel.isPriority());
            relatedArray.add(relObj);
        }
        manifest.add("relatedObjects", relatedArray);
        
        // === Object Metadata ===
        if (captureFieldMetadata || captureExternalIds) {
            JsonObject objectsMetadata = new JsonObject();
            
            for (String objectName : backedUpObjects) {
                try {
                    ObjectMetadata objMeta = relationshipManager.describeObject(objectName);
                    JsonObject objJson = generateObjectMetadata(objMeta, outputFolder, objectName);
                    objectsMetadata.add(objectName, objJson);
                } catch (Exception e) {
                    logger.warn("Failed to get metadata for {}: {}", objectName, e.getMessage());
                }
            }
            
            manifest.add("objects", objectsMetadata);
        }
        
        // === Restore Order ===
        List<String> restoreOrder = calculateRestoreOrder(parentObjects, relatedObjects);
        JsonArray orderArray = new JsonArray();
        for (String obj : restoreOrder) {
            orderArray.add(obj);
        }
        manifest.add("restoreOrder", orderArray);
        
        // === Restore Instructions ===
        JsonObject instructions = new JsonObject();
        instructions.addProperty("summary", "This backup can be restored to the same org or migrated to a different org");
        
        JsonArray steps = new JsonArray();
        steps.add("1. Ensure target org has matching object schema");
        steps.add("2. If migrating to different org, update RecordType IDs from recordTypeMappings");
        steps.add("3. Restore objects in the order specified in 'restoreOrder'");
        steps.add("4. Use external IDs for upsert operations when available");
        steps.add("5. Use ID mapping file to resolve lookup references");
        instructions.add("steps", steps);
        
        JsonObject lookupResolution = new JsonObject();
        lookupResolution.addProperty("strategy", "Use external IDs when available, otherwise use ID mapping");
        lookupResolution.addProperty("note", "For objects without external IDs, restore parent records first and map old->new IDs");
        instructions.add("lookupResolution", lookupResolution);
        
        manifest.add("restoreInstructions", instructions);
        
        // === Write manifest ===
        Path manifestPath = Paths.get(outputFolder, "_backup_manifest.json");
        Files.writeString(manifestPath, gson.toJson(manifest));
        
        logger.info("Generated backup manifest: {}", manifestPath);
        
        // === Generate ID mapping file if enabled ===
        if (generateIdMapping) {
            generateIdMappingFile(outputFolder, backedUpObjects);
        }
        
        return manifestPath;
    }
    
    private JsonObject generateObjectMetadata(ObjectMetadata objMeta, String outputFolder, 
                                             String objectName) throws IOException {
        JsonObject obj = new JsonObject();
        
        // Record count
        Path csvPath = Paths.get(outputFolder, objectName + ".csv");
        if (Files.exists(csvPath)) {
            long recordCount = Files.lines(csvPath).count() - 1; // Subtract header
            obj.addProperty("recordCount", recordCount);
            obj.addProperty("fileName", objectName + ".csv");
        }
        
        // External ID fields
        if (captureExternalIds && !objMeta.getExternalIdFields().isEmpty()) {
            JsonArray extIds = new JsonArray();
            for (FieldInfo field : objMeta.getExternalIdFields()) {
                JsonObject fieldObj = new JsonObject();
                fieldObj.addProperty("name", field.getName());
                fieldObj.addProperty("type", field.getType());
                fieldObj.addProperty("label", field.getLabel());
                extIds.add(fieldObj);
            }
            obj.add("externalIdFields", extIds);
            
            // Recommend which field to use for upsert
            if (!objMeta.getExternalIdFields().isEmpty()) {
                obj.addProperty("recommendedUpsertField", objMeta.getExternalIdFields().get(0).getName());
            }
        }
        
        // Unique fields (for matching when no external ID)
        if (!objMeta.getUniqueFields().isEmpty()) {
            JsonArray uniqueFields = new JsonArray();
            for (FieldInfo field : objMeta.getUniqueFields()) {
                uniqueFields.add(field.getName());
            }
            obj.add("uniqueFields", uniqueFields);
        }
        
        // Relationship fields (lookups)
        if (!objMeta.getRelationshipFields().isEmpty()) {
            JsonArray relationships = new JsonArray();
            for (RelationshipField rel : objMeta.getRelationshipFields()) {
                JsonObject relObj = new JsonObject();
                relObj.addProperty("fieldName", rel.getFieldInfo().getName());
                relObj.addProperty("relationshipName", rel.getRelationshipName());
                
                JsonArray refTo = new JsonArray();
                for (String ref : rel.getReferenceTo()) {
                    refTo.add(ref);
                }
                relObj.add("referenceTo", refTo);
                relObj.addProperty("polymorphic", rel.isPolymorphic());
                
                relationships.add(relObj);
            }
            obj.add("relationshipFields", relationships);
        }
        
        // Field metadata for restore validation
        if (captureFieldMetadata) {
            JsonArray fields = new JsonArray();
            for (FieldInfo field : objMeta.getFields()) {
                // Only include createable fields (relevant for restore)
                if (field.isCreateable()) {
                    JsonObject fieldObj = new JsonObject();
                    fieldObj.addProperty("name", field.getName());
                    fieldObj.addProperty("type", field.getType());
                    fieldObj.addProperty("required", field.isRequired());
                    fieldObj.addProperty("length", field.getLength());
                    fields.add(fieldObj);
                }
            }
            obj.add("createableFields", fields);
        }
        
        // Name field (for display/matching)
        if (objMeta.getNameField() != null) {
            obj.addProperty("nameField", objMeta.getNameField().getName());
        }
        
        return obj;
    }
    
    /**
     * Calculate the order in which objects should be restored based on dependencies.
     * Parents must be restored before children to resolve lookup references.
     */
    private List<String> calculateRestoreOrder(List<String> parentObjects, 
                                              List<RelatedObjectInfo> relatedObjects) {
        List<String> order = new ArrayList<>();
        
        // Group by depth
        Map<Integer, List<String>> byDepth = new TreeMap<>();
        
        // Parents are depth 0
        byDepth.put(0, new ArrayList<>(parentObjects));
        
        // Related objects by their depth
        for (RelatedObjectInfo rel : relatedObjects) {
            byDepth.computeIfAbsent(rel.getDepth(), k -> new ArrayList<>()).add(rel.getObjectName());
        }
        
        // Add in order of depth (parents first)
        for (List<String> objects : byDepth.values()) {
            order.addAll(objects);
        }
        
        // Remove duplicates while preserving order
        LinkedHashSet<String> unique = new LinkedHashSet<>(order);
        return new ArrayList<>(unique);
    }
    
    /**
     * Generates an ID mapping file that maps Salesforce IDs to external identifiers.
     * This file is used during restore to resolve lookup references.
     */
    private void generateIdMappingFile(String outputFolder, List<String> objects) throws Exception {
        JsonObject idMapping = new JsonObject();
        
        idMapping.addProperty("generatedAt", Instant.now().toString());
        idMapping.addProperty("purpose", "Maps Salesforce record IDs to external identifiers for cross-org restore");
        
        JsonObject mappings = new JsonObject();
        
        for (String objectName : objects) {
            Path csvPath = Paths.get(outputFolder, objectName + ".csv");
            if (!Files.exists(csvPath)) continue;
            
            try {
                ObjectMetadata metadata = relationshipManager.describeObject(objectName);
                
                // Find the best identifier field
                String identifierField = null;
                if (!metadata.getExternalIdFields().isEmpty()) {
                    identifierField = metadata.getExternalIdFields().get(0).getName();
                } else if (!metadata.getUniqueFields().isEmpty()) {
                    identifierField = metadata.getUniqueFields().get(0).getName();
                } else if (metadata.getNameField() != null) {
                    identifierField = metadata.getNameField().getName();
                }
                
                if (identifierField == null) {
                    continue; // No good identifier, skip
                }
                
                // Read CSV and build ID mapping
                Map<String, String> objectMapping = readIdMapping(csvPath, identifierField);
                
                if (!objectMapping.isEmpty()) {
                    JsonObject objMappingJson = new JsonObject();
                    objMappingJson.addProperty("identifierField", identifierField);
                    objMappingJson.addProperty("recordCount", objectMapping.size());
                    
                    // Store the actual mappings
                    JsonObject records = new JsonObject();
                    for (Map.Entry<String, String> entry : objectMapping.entrySet()) {
                        records.addProperty(entry.getKey(), entry.getValue());
                    }
                    objMappingJson.add("idToIdentifier", records);
                    
                    mappings.add(objectName, objMappingJson);
                }
                
            } catch (Exception e) {
                logger.warn("Failed to generate ID mapping for {}: {}", objectName, e.getMessage());
            }
        }
        
        idMapping.add("mappings", mappings);
        
        Path mappingPath = Paths.get(outputFolder, "_id_mapping.json");
        Files.writeString(mappingPath, gson.toJson(idMapping));
        
        logger.info("Generated ID mapping file: {}", mappingPath);
    }
    
    private Map<String, String> readIdMapping(Path csvPath, String identifierField) throws IOException {
        Map<String, String> mapping = new LinkedHashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return mapping;
            
            String[] headers = parseCSVLine(headerLine);
            int idIndex = -1;
            int identifierIndex = -1;
            
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].trim().replace("\"", "");
                if ("Id".equalsIgnoreCase(header)) {
                    idIndex = i;
                } else if (header.equalsIgnoreCase(identifierField)) {
                    identifierIndex = i;
                }
            }
            
            if (idIndex == -1 || identifierIndex == -1) {
                return mapping;
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);
                if (values.length > Math.max(idIndex, identifierIndex)) {
                    String id = values[idIndex].trim().replace("\"", "");
                    String identifier = values[identifierIndex].trim().replace("\"", "");
                    if (!id.isEmpty() && !identifier.isEmpty()) {
                        mapping.put(id, identifier);
                    }
                }
            }
        }
        
        return mapping;
    }
    
    private String[] parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
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
    
    public void close() {
        relationshipManager.close();
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Information about a related object in the backup.
     */
    public static class RelatedObjectInfo {
        private final String objectName;
        private final String parentObject;
        private final String parentField;
        private final int depth;
        private final boolean priority;
        
        public RelatedObjectInfo(String objectName, String parentObject, String parentField,
                                int depth, boolean priority) {
            this.objectName = objectName;
            this.parentObject = parentObject;
            this.parentField = parentField;
            this.depth = depth;
            this.priority = priority;
        }
        
        public String getObjectName() { return objectName; }
        public String getParentObject() { return parentObject; }
        public String getParentField() { return parentField; }
        public int getDepth() { return depth; }
        public boolean isPriority() { return priority; }
    }
}
