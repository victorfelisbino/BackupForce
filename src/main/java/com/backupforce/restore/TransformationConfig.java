package com.backupforce.restore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Configuration for data transformations during cross-org restore.
 * Handles mappings for RecordTypes, picklist values, users, and custom field transformations.
 * 
 * This allows restoring data from one Salesforce org to another that may have:
 * - Different RecordType IDs/names
 * - Different picklist values
 * - Different users (for OwnerId, CreatedById, etc.)
 * - Different field names or missing fields
 */
public class TransformationConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(TransformationConfig.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private String name;
    private String description;
    private String sourceOrg;
    private String targetOrg;
    private String createdDate;
    private String lastModifiedDate;
    
    // Object-level configurations
    private final Map<String, ObjectTransformConfig> objectConfigs = new LinkedHashMap<>();
    
    // Global mappings that apply to all objects
    private final Map<String, String> userMappings = new LinkedHashMap<>();
    private final Map<String, String> recordTypeMappings = new LinkedHashMap<>();
    
    // Default behaviors
    private UnmappedValueBehavior defaultUnmappedPicklistBehavior = UnmappedValueBehavior.KEEP_ORIGINAL;
    private UnmappedValueBehavior defaultUnmappedRecordTypeBehavior = UnmappedValueBehavior.USE_DEFAULT;
    private UnmappedValueBehavior defaultUnmappedUserBehavior = UnmappedValueBehavior.USE_RUNNING_USER;
    
    public TransformationConfig() {
        this.createdDate = java.time.LocalDateTime.now().toString();
        this.lastModifiedDate = this.createdDate;
    }
    
    public TransformationConfig(String name) {
        this();
        this.name = name;
    }
    
    // ==================== Object Configuration ====================
    
    public ObjectTransformConfig getOrCreateObjectConfig(String objectName) {
        return objectConfigs.computeIfAbsent(objectName, ObjectTransformConfig::new);
    }
    
    public ObjectTransformConfig getObjectConfig(String objectName) {
        return objectConfigs.get(objectName);
    }
    
    public Map<String, ObjectTransformConfig> getObjectConfigs() {
        return objectConfigs;
    }
    
    public void addObjectConfig(ObjectTransformConfig config) {
        objectConfigs.put(config.getObjectName(), config);
    }
    
    // ==================== Global Mappings ====================
    
    public void addUserMapping(String sourceUserId, String targetUserId) {
        userMappings.put(sourceUserId, targetUserId);
    }
    
    public String getMappedUser(String sourceUserId) {
        return userMappings.get(sourceUserId);
    }
    
    public Map<String, String> getUserMappings() {
        return userMappings;
    }
    
    public void addRecordTypeMapping(String sourceRecordTypeId, String targetRecordTypeId) {
        recordTypeMappings.put(sourceRecordTypeId, targetRecordTypeId);
    }
    
    public String getMappedRecordType(String sourceRecordTypeId) {
        return recordTypeMappings.get(sourceRecordTypeId);
    }
    
    public Map<String, String> getRecordTypeMappings() {
        return recordTypeMappings;
    }
    
    // ==================== Persistence ====================
    
    public void save(Path path) throws IOException {
        this.lastModifiedDate = java.time.LocalDateTime.now().toString();
        String json = gson.toJson(this);
        Files.writeString(path, json);
        logger.info("Saved transformation config to: {}", path);
    }
    
    public void saveToFile(File file) throws IOException {
        save(file.toPath());
    }
    
    public static TransformationConfig load(Path path) throws IOException {
        String json = Files.readString(path);
        return gson.fromJson(json, TransformationConfig.class);
    }
    
    public static TransformationConfig loadFromFile(File file) throws IOException {
        return load(file.toPath());
    }
    
    public static TransformationConfig loadFromBackupFolder(String backupFolder) throws IOException {
        Path configPath = Paths.get(backupFolder, "_transformation_config.json");
        if (Files.exists(configPath)) {
            return load(configPath);
        }
        return null;
    }
    
    // ==================== Getters/Setters ====================
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getSourceOrg() { return sourceOrg; }
    public void setSourceOrg(String sourceOrg) { this.sourceOrg = sourceOrg; }
    
    public String getTargetOrg() { return targetOrg; }
    public void setTargetOrg(String targetOrg) { this.targetOrg = targetOrg; }
    
    public String getCreatedDate() { return createdDate; }
    public String getLastModifiedDate() { return lastModifiedDate; }
    
    public UnmappedValueBehavior getDefaultUnmappedPicklistBehavior() { 
        return defaultUnmappedPicklistBehavior; 
    }
    public void setDefaultUnmappedPicklistBehavior(UnmappedValueBehavior behavior) { 
        this.defaultUnmappedPicklistBehavior = behavior; 
    }
    
    public UnmappedValueBehavior getDefaultUnmappedRecordTypeBehavior() { 
        return defaultUnmappedRecordTypeBehavior; 
    }
    public void setDefaultUnmappedRecordTypeBehavior(UnmappedValueBehavior behavior) { 
        this.defaultUnmappedRecordTypeBehavior = behavior; 
    }
    
    public UnmappedValueBehavior getDefaultUnmappedUserBehavior() { 
        return defaultUnmappedUserBehavior; 
    }
    public void setDefaultUnmappedUserBehavior(UnmappedValueBehavior behavior) { 
        this.defaultUnmappedUserBehavior = behavior; 
    }
    
    // ==================== Enums ====================
    
    /**
     * Defines behavior when a source value has no mapping defined
     */
    public enum UnmappedValueBehavior {
        KEEP_ORIGINAL,      // Keep the source value (may cause errors if invalid)
        USE_DEFAULT,        // Use a configured default value
        SET_NULL,           // Set the field to null
        USE_RUNNING_USER,   // For user fields, use the current API user
        SKIP_RECORD,        // Skip the entire record
        FAIL                // Fail the restore with an error
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Transformation configuration for a specific Salesforce object
     */
    public static class ObjectTransformConfig {
        private final String objectName;
        
        // RecordType mappings: sourceRecordTypeId/Name -> targetRecordTypeId
        private final Map<String, String> recordTypeMappings = new LinkedHashMap<>();
        private String defaultRecordTypeId;
        private UnmappedValueBehavior unmappedRecordTypeBehavior = UnmappedValueBehavior.USE_DEFAULT;
        
        // Picklist mappings: fieldName -> (sourceValue -> targetValue)
        private final Map<String, Map<String, String>> picklistMappings = new LinkedHashMap<>();
        private final Map<String, String> defaultPicklistValues = new LinkedHashMap<>();
        private UnmappedValueBehavior unmappedPicklistBehavior = UnmappedValueBehavior.KEEP_ORIGINAL;
        
        // Field mappings: sourceFieldName -> targetFieldName (for renamed fields)
        private final Map<String, String> fieldNameMappings = new LinkedHashMap<>();
        
        // Fields to exclude from restore
        private final Set<String> excludedFields = new LinkedHashSet<>();
        
        // Custom value transformations: fieldName -> transformation
        private final Map<String, ValueTransformation> valueTransformations = new LinkedHashMap<>();
        
        // Owner/User field mappings (object-specific overrides)
        private final Map<String, String> userMappings = new LinkedHashMap<>();
        private UnmappedValueBehavior unmappedUserBehavior = UnmappedValueBehavior.USE_RUNNING_USER;
        
        public ObjectTransformConfig(String objectName) {
            this.objectName = objectName;
        }
        
        // RecordType methods
        public void addRecordTypeMapping(String sourceRecordType, String targetRecordTypeId) {
            recordTypeMappings.put(sourceRecordType, targetRecordTypeId);
        }
        
        public String getMappedRecordType(String sourceRecordType) {
            return recordTypeMappings.get(sourceRecordType);
        }
        
        public Map<String, String> getRecordTypeMappings() {
            return recordTypeMappings;
        }
        
        public String getDefaultRecordTypeId() { return defaultRecordTypeId; }
        public void setDefaultRecordTypeId(String id) { this.defaultRecordTypeId = id; }
        
        public UnmappedValueBehavior getUnmappedRecordTypeBehavior() { return unmappedRecordTypeBehavior; }
        public void setUnmappedRecordTypeBehavior(UnmappedValueBehavior behavior) { 
            this.unmappedRecordTypeBehavior = behavior; 
        }
        
        // Picklist methods
        public void addPicklistMapping(String fieldName, String sourceValue, String targetValue) {
            picklistMappings.computeIfAbsent(fieldName, k -> new LinkedHashMap<>())
                           .put(sourceValue, targetValue);
        }
        
        public String getMappedPicklistValue(String fieldName, String sourceValue) {
            Map<String, String> fieldMappings = picklistMappings.get(fieldName);
            if (fieldMappings != null) {
                return fieldMappings.get(sourceValue);
            }
            return null;
        }
        
        public Map<String, Map<String, String>> getPicklistMappings() {
            return picklistMappings;
        }
        
        public void setDefaultPicklistValue(String fieldName, String defaultValue) {
            defaultPicklistValues.put(fieldName, defaultValue);
        }
        
        public String getDefaultPicklistValue(String fieldName) {
            return defaultPicklistValues.get(fieldName);
        }
        
        public UnmappedValueBehavior getUnmappedPicklistBehavior() { return unmappedPicklistBehavior; }
        public void setUnmappedPicklistBehavior(UnmappedValueBehavior behavior) { 
            this.unmappedPicklistBehavior = behavior; 
        }
        
        // Field name methods
        public void addFieldNameMapping(String sourceFieldName, String targetFieldName) {
            fieldNameMappings.put(sourceFieldName, targetFieldName);
        }
        
        public String getMappedFieldName(String sourceFieldName) {
            return fieldNameMappings.getOrDefault(sourceFieldName, sourceFieldName);
        }
        
        public Map<String, String> getFieldNameMappings() {
            return fieldNameMappings;
        }
        
        // Excluded fields
        public void excludeField(String fieldName) {
            excludedFields.add(fieldName);
        }
        
        public boolean isFieldExcluded(String fieldName) {
            return excludedFields.contains(fieldName);
        }
        
        public Set<String> getExcludedFields() {
            return excludedFields;
        }
        
        // Value transformations
        public void addValueTransformation(String fieldName, ValueTransformation transformation) {
            valueTransformations.put(fieldName, transformation);
        }
        
        public Map<String, ValueTransformation> getValueTransformations() {
            return valueTransformations;
        }
        
        // User mappings
        public void addUserMapping(String sourceUserId, String targetUserId) {
            userMappings.put(sourceUserId, targetUserId);
        }
        
        public String getMappedUser(String sourceUserId) {
            return userMappings.get(sourceUserId);
        }
        
        public Map<String, String> getUserMappings() {
            return userMappings;
        }
        
        public UnmappedValueBehavior getUnmappedUserBehavior() { return unmappedUserBehavior; }
        public void setUnmappedUserBehavior(UnmappedValueBehavior behavior) { 
            this.unmappedUserBehavior = behavior; 
        }
        
        public String getObjectName() { return objectName; }
    }
    
    /**
     * Represents a custom value transformation rule
     */
    public static class ValueTransformation {
        private String fieldName;
        private TransformationType type;
        private String pattern;
        private String replacement;
        private String condition;
        private Map<String, String> lookupTable;
        
        public ValueTransformation() {}
        
        public ValueTransformation(String fieldName, TransformationType type) {
            this.fieldName = fieldName;
            this.type = type;
        }
        
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        
        public TransformationType getType() { return type; }
        public void setType(TransformationType type) { this.type = type; }
        
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public String getReplacement() { return replacement; }
        public void setReplacement(String replacement) { this.replacement = replacement; }
        
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        
        public Map<String, String> getLookupTable() { return lookupTable; }
        public void setLookupTable(Map<String, String> lookupTable) { this.lookupTable = lookupTable; }
    }
    
    /**
     * Types of value transformations
     */
    public enum TransformationType {
        REGEX_REPLACE,      // Replace using regex pattern
        PREFIX,             // Add prefix to value
        SUFFIX,             // Add suffix to value
        TRIM,               // Trim whitespace
        UPPERCASE,          // Convert to uppercase
        LOWERCASE,          // Convert to lowercase
        CONSTANT,           // Replace with constant value
        FORMULA,            // Apply a formula (future)
        LOOKUP,             // Look up value from another source (future)
        CONCATENATE         // Concatenate multiple fields (future)
    }
}
