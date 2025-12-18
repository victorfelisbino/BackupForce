package com.backupforce.restore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms backup data for cross-org restores.
 * Applies field mappings, value transformations, user/RecordType mappings
 * based on TransformationConfig settings.
 */
public class DataTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataTransformer.class);
    
    private final TransformationConfig config;
    private Consumer<String> logCallback;
    
    // Statistics for reporting
    private int transformedRecords = 0;
    private int skippedRecords = 0;
    private int fieldTransformations = 0;
    private int userMappingsApplied = 0;
    private int recordTypeMappingsApplied = 0;
    private int picklistMappingsApplied = 0;
    
    public DataTransformer(TransformationConfig config) {
        this.config = config != null ? config : new TransformationConfig();
    }
    
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }
    
    private void log(String message) {
        logger.debug(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }
    
    /**
     * Transforms a list of records for the specified object.
     * Returns transformed records, excluding any that should be skipped.
     */
    public List<Map<String, Object>> transformRecords(String objectName, 
                                                       List<Map<String, String>> records,
                                                       String runningUserId) {
        
        List<Map<String, Object>> transformedRecords = new ArrayList<>();
        TransformationConfig.ObjectTransformConfig objectConfig = config.getObjectConfig(objectName);
        
        for (Map<String, String> record : records) {
            TransformResult result = transformRecord(objectName, record, objectConfig, runningUserId);
            
            if (result.isSkipped()) {
                skippedRecords++;
                log("Skipped record due to unmapped value: " + result.getSkipReason());
            } else if (result.isFailed()) {
                throw new DataTransformationException(
                    "Transformation failed for " + objectName + ": " + result.getSkipReason());
            } else {
                transformedRecords.add(result.getTransformedRecord());
                this.transformedRecords++;
            }
        }
        
        return transformedRecords;
    }
    
    /**
     * Transforms a single record according to configuration.
     */
    public TransformResult transformRecord(String objectName,
                                            Map<String, String> record,
                                            TransformationConfig.ObjectTransformConfig objectConfig,
                                            String runningUserId) {
        
        Map<String, Object> transformed = new LinkedHashMap<>();
        
        // Get effective field exclusions
        Set<String> excludedFields = objectConfig != null ? objectConfig.getExcludedFields() : Set.of();
        
        for (Map.Entry<String, String> entry : record.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();
            
            // Skip internal reference columns
            if (fieldName.startsWith("_ref_") || fieldName.startsWith("_rel_")) {
                continue;
            }
            
            // Skip excluded fields
            if (excludedFields.contains(fieldName)) {
                continue;
            }
            
            // Apply field name mapping
            String targetFieldName = mapFieldName(fieldName, objectConfig);
            
            // Apply value transformations
            Object transformedValue = transformValue(objectName, fieldName, value, objectConfig, runningUserId);
            
            // Check if transformation result indicates skip/fail
            if (transformedValue instanceof SkipRecord) {
                return TransformResult.skipped(((SkipRecord) transformedValue).reason);
            } else if (transformedValue instanceof FailRecord) {
                return TransformResult.failed(((FailRecord) transformedValue).reason);
            }
            
            transformed.put(targetFieldName, transformedValue);
        }
        
        return TransformResult.success(transformed);
    }
    
    private String mapFieldName(String sourceFieldName, TransformationConfig.ObjectTransformConfig objectConfig) {
        if (objectConfig != null && objectConfig.getFieldNameMappings().containsKey(sourceFieldName)) {
            fieldTransformations++;
            return objectConfig.getFieldNameMappings().get(sourceFieldName);
        }
        return sourceFieldName;
    }
    
    private Object transformValue(String objectName, String fieldName, String value,
                                   TransformationConfig.ObjectTransformConfig objectConfig,
                                   String runningUserId) {
        
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        // Check for RecordTypeId mapping
        if (fieldName.equals("RecordTypeId")) {
            return handleRecordTypeId(value, objectConfig);
        }
        
        // Check for User ID fields
        if (isUserField(fieldName)) {
            return handleUserId(fieldName, value, objectConfig, runningUserId);
        }
        
        // Check for picklist mapping
        Object picklistResult = handlePicklistValue(objectName, fieldName, value, objectConfig);
        if (picklistResult != NO_TRANSFORM) {
            return picklistResult;
        }
        
        // Apply custom value transformations
        if (objectConfig != null && objectConfig.getValueTransformations().containsKey(fieldName)) {
            TransformationConfig.ValueTransformation transform = 
                objectConfig.getValueTransformations().get(fieldName);
            
            return applyCustomTransformation(value, transform);
        }
        
        // No transformation needed
        return value;
    }
    
    private boolean isUserField(String fieldName) {
        return fieldName.equals("OwnerId") || 
               fieldName.equals("CreatedById") || 
               fieldName.equals("LastModifiedById") ||
               fieldName.endsWith("__c") && fieldName.contains("User");
    }
    
    private Object handleRecordTypeId(String value, TransformationConfig.ObjectTransformConfig objectConfig) {
        // First check object-level mapping
        if (objectConfig != null && objectConfig.getRecordTypeMappings().containsKey(value)) {
            recordTypeMappingsApplied++;
            return objectConfig.getRecordTypeMappings().get(value);
        }
        
        // Then check global RecordType mapping
        if (config.getRecordTypeMappings().containsKey(value)) {
            recordTypeMappingsApplied++;
            return config.getRecordTypeMappings().get(value);
        }
        
        // Handle unmapped RecordType based on behavior setting
        TransformationConfig.UnmappedValueBehavior behavior = 
            objectConfig != null ? objectConfig.getUnmappedRecordTypeBehavior() 
                                 : TransformationConfig.UnmappedValueBehavior.KEEP_ORIGINAL;
        
        switch (behavior) {
            case KEEP_ORIGINAL:
                return value;
            case SET_NULL:
                return null;
            case SKIP_RECORD:
                return new SkipRecord("RecordTypeId " + value + " not mapped");
            case FAIL:
                return new FailRecord("RecordTypeId " + value + " not mapped and FAIL behavior set");
            case USE_DEFAULT:
                String defaultRt = objectConfig != null ? objectConfig.getDefaultRecordTypeId() : null;
                return defaultRt != null ? defaultRt : value;
            default:
                return value;
        }
    }
    
    private Object handleUserId(String fieldName, String value,
                                 TransformationConfig.ObjectTransformConfig objectConfig,
                                 String runningUserId) {
        
        // First check object-level user mapping
        if (objectConfig != null && objectConfig.getUserMappings().containsKey(value)) {
            userMappingsApplied++;
            return objectConfig.getUserMappings().get(value);
        }
        
        // Then check global user mapping
        if (config.getUserMappings().containsKey(value)) {
            userMappingsApplied++;
            return config.getUserMappings().get(value);
        }
        
        // Handle unmapped user based on behavior setting
        TransformationConfig.UnmappedValueBehavior behavior = 
            objectConfig != null ? objectConfig.getUnmappedUserBehavior() 
                                 : TransformationConfig.UnmappedValueBehavior.USE_RUNNING_USER;
        
        switch (behavior) {
            case USE_RUNNING_USER:
                if (runningUserId != null) {
                    userMappingsApplied++;
                    return runningUserId;
                }
                return value;
            case KEEP_ORIGINAL:
                return value;
            case SET_NULL:
                // OwnerId is typically required, so keep original if null behavior
                return fieldName.equals("OwnerId") ? value : null;
            case SKIP_RECORD:
                return new SkipRecord("User " + value + " not mapped");
            case FAIL:
                return new FailRecord("User " + value + " not mapped and FAIL behavior set");
            default:
                return value;
        }
    }
    
    private Object handlePicklistValue(String objectName, String fieldName, String value,
                                        TransformationConfig.ObjectTransformConfig objectConfig) {
        
        if (objectConfig == null) {
            return NO_TRANSFORM; // No transformation configured
        }
        
        Map<String, Map<String, String>> picklistMappings = objectConfig.getPicklistMappings();
        
        if (picklistMappings.containsKey(fieldName)) {
            Map<String, String> fieldMappings = picklistMappings.get(fieldName);
            
            if (fieldMappings.containsKey(value)) {
                picklistMappingsApplied++;
                return fieldMappings.get(value);
            }
            
            // Handle unmapped picklist value
            TransformationConfig.UnmappedValueBehavior behavior = objectConfig.getUnmappedPicklistBehavior();
            
            switch (behavior) {
                case KEEP_ORIGINAL:
                    return value;
                case SET_NULL:
                    return null;
                case USE_DEFAULT:
                    String defaultValue = objectConfig.getDefaultPicklistValue(fieldName);
                    return defaultValue != null ? defaultValue : null;
                case SKIP_RECORD:
                    return new SkipRecord("Picklist value '" + value + "' for field " + fieldName + " not mapped");
                case FAIL:
                    return new FailRecord("Picklist value '" + value + "' for field " + fieldName + " not mapped");
                default:
                    return value;
            }
        }
        
        return NO_TRANSFORM; // No mapping configured for this field
    }
    
    private Object applyCustomTransformation(String value, TransformationConfig.ValueTransformation transform) {
        if (value == null) {
            return null;
        }
        
        // Check condition if specified
        if (transform.getCondition() != null && !transform.getCondition().isEmpty()) {
            Pattern conditionPattern = Pattern.compile(transform.getCondition());
            if (!conditionPattern.matcher(value).find()) {
                return value; // Condition not met, return original
            }
        }
        
        fieldTransformations++;
        
        switch (transform.getType()) {
            case REGEX_REPLACE:
                if (transform.getPattern() != null && transform.getReplacement() != null) {
                    Pattern pattern = Pattern.compile(transform.getPattern());
                    Matcher matcher = pattern.matcher(value);
                    return matcher.replaceAll(transform.getReplacement());
                }
                return value;
                
            case PREFIX:
                String prefix = transform.getReplacement() != null ? transform.getReplacement() : "";
                return prefix + value;
                
            case SUFFIX:
                String suffix = transform.getReplacement() != null ? transform.getReplacement() : "";
                return value + suffix;
                
            case TRIM:
                return value.trim();
                
            case UPPERCASE:
                return value.toUpperCase();
                
            case LOWERCASE:
                return value.toLowerCase();
                
            case CONSTANT:
                return transform.getReplacement();
                
            case LOOKUP:
                // For lookup transformations, check if we have a mapping table
                Map<String, String> lookupTable = transform.getLookupTable();
                if (lookupTable != null && lookupTable.containsKey(value)) {
                    return lookupTable.get(value);
                }
                return value;
                
            case CONCATENATE:
                // Pattern contains field references like {FieldName}
                // This would need the full record context
                return value;
                
            case FORMULA:
                // Not implemented - would require expression evaluation
                return value;
                
            default:
                return value;
        }
    }
    
    /**
     * Suggests mappings based on name/label similarity
     */
    public static Map<String, String> suggestRecordTypeMappings(
            Collection<SchemaComparer.RecordTypeInfo> sourceTypes,
            Collection<SchemaComparer.RecordTypeInfo> targetTypes) {
        
        Map<String, String> suggestions = new LinkedHashMap<>();
        
        for (SchemaComparer.RecordTypeInfo source : sourceTypes) {
            SchemaComparer.RecordTypeInfo bestMatch = null;
            double bestScore = 0;
            
            for (SchemaComparer.RecordTypeInfo target : targetTypes) {
                // Check exact developer name match first
                if (source.getDeveloperName() != null && 
                    source.getDeveloperName().equalsIgnoreCase(target.getDeveloperName())) {
                    bestMatch = target;
                    break;
                }
                
                // Check exact name match
                if (source.getName().equalsIgnoreCase(target.getName())) {
                    bestMatch = target;
                    bestScore = 1.0;
                    continue;
                }
                
                // Calculate similarity score
                double nameScore = stringSimilarity(source.getName().toLowerCase(), 
                                                    target.getName().toLowerCase());
                if (nameScore > bestScore) {
                    bestScore = nameScore;
                    bestMatch = target;
                }
            }
            
            if (bestMatch != null && bestScore >= 0.5) {
                suggestions.put(source.getId(), bestMatch.getId());
            }
        }
        
        return suggestions;
    }
    
    /**
     * Suggests user mappings based on username/email similarity
     */
    public static Map<String, String> suggestUserMappings(
            Collection<SchemaComparer.UserInfo> sourceUsers,
            Collection<SchemaComparer.UserInfo> targetUsers) {
        
        Map<String, String> suggestions = new LinkedHashMap<>();
        
        for (SchemaComparer.UserInfo source : sourceUsers) {
            SchemaComparer.UserInfo bestMatch = null;
            double bestScore = 0;
            
            for (SchemaComparer.UserInfo target : targetUsers) {
                // Check exact email match
                if (source.getEmail() != null && source.getEmail().equalsIgnoreCase(target.getEmail())) {
                    bestMatch = target;
                    break;
                }
                
                // Check username domain extraction (user@domain -> same domain)
                if (source.getUsername() != null && target.getUsername() != null) {
                    String sourceBase = extractUsernameBase(source.getUsername());
                    String targetBase = extractUsernameBase(target.getUsername());
                    
                    if (sourceBase != null && sourceBase.equalsIgnoreCase(targetBase)) {
                        bestMatch = target;
                        bestScore = 0.9;
                        continue;
                    }
                }
                
                // Check name similarity
                double nameScore = stringSimilarity(source.getName().toLowerCase(), 
                                                    target.getName().toLowerCase());
                if (nameScore > bestScore) {
                    bestScore = nameScore;
                    bestMatch = target;
                }
            }
            
            if (bestMatch != null && bestScore >= 0.7) {
                suggestions.put(source.getId(), bestMatch.getId());
            }
        }
        
        return suggestions;
    }
    
    /**
     * Suggests picklist value mappings based on string similarity
     */
    public static Map<String, String> suggestPicklistMappings(
            Collection<String> sourceValues,
            Collection<String> targetValues) {
        
        Map<String, String> suggestions = new LinkedHashMap<>();
        
        for (String source : sourceValues) {
            String bestMatch = null;
            double bestScore = 0;
            boolean exactMatch = false;
            
            for (String target : targetValues) {
                // Exact match
                if (source.equalsIgnoreCase(target)) {
                    bestMatch = target;
                    exactMatch = true;
                    break;
                }
                
                // Similarity match
                double score = stringSimilarity(source.toLowerCase(), target.toLowerCase());
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = target;
                }
            }
            
            if (exactMatch || (bestMatch != null && bestScore >= 0.6)) {
                suggestions.put(source, bestMatch);
            }
        }
        
        return suggestions;
    }
    
    /**
     * Extracts the username base (part before @) from a Salesforce username
     */
    private static String extractUsernameBase(String username) {
        if (username == null || !username.contains("@")) {
            return null;
        }
        return username.substring(0, username.indexOf("@"));
    }
    
    /**
     * Calculates string similarity using Levenshtein distance
     */
    private static double stringSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }
    
    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1),     // insertion
                    dp[i - 1][j - 1] + cost); // substitution
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    // ==================== Statistics ====================
    
    public void resetStatistics() {
        transformedRecords = 0;
        skippedRecords = 0;
        fieldTransformations = 0;
        userMappingsApplied = 0;
        recordTypeMappingsApplied = 0;
        picklistMappingsApplied = 0;
    }
    
    public TransformationStatistics getStatistics() {
        return new TransformationStatistics(
            transformedRecords,
            skippedRecords,
            fieldTransformations,
            userMappingsApplied,
            recordTypeMappingsApplied,
            picklistMappingsApplied
        );
    }
    
    // ==================== Inner Classes ====================
    
    private static class SkipRecord {
        final String reason;
        SkipRecord(String reason) { this.reason = reason; }
    }
    
    private static class FailRecord {
        final String reason;
        FailRecord(String reason) { this.reason = reason; }
    }
    
    /** Sentinel value indicating no transformation was configured for this field */
    private static final Object NO_TRANSFORM = new Object();
    
    public static class TransformResult {
        private final boolean success;
        private final boolean skipped;
        private final boolean failed;
        private final String skipReason;
        private final Map<String, Object> transformedRecord;
        
        private TransformResult(boolean success, boolean skipped, boolean failed, 
                                 String skipReason, Map<String, Object> transformedRecord) {
            this.success = success;
            this.skipped = skipped;
            this.failed = failed;
            this.skipReason = skipReason;
            this.transformedRecord = transformedRecord;
        }
        
        public static TransformResult success(Map<String, Object> record) {
            return new TransformResult(true, false, false, null, record);
        }
        
        public static TransformResult skipped(String reason) {
            return new TransformResult(false, true, false, reason, null);
        }
        
        public static TransformResult failed(String reason) {
            return new TransformResult(false, false, true, reason, null);
        }
        
        public boolean isSuccess() { return success; }
        public boolean isSkipped() { return skipped; }
        public boolean isFailed() { return failed; }
        public String getSkipReason() { return skipReason; }
        public Map<String, Object> getTransformedRecord() { return transformedRecord; }
    }
    
    public static class TransformationStatistics {
        private final int transformedRecords;
        private final int skippedRecords;
        private final int fieldTransformations;
        private final int userMappingsApplied;
        private final int recordTypeMappingsApplied;
        private final int picklistMappingsApplied;
        
        public TransformationStatistics(int transformedRecords, int skippedRecords,
                                         int fieldTransformations, int userMappingsApplied,
                                         int recordTypeMappingsApplied, int picklistMappingsApplied) {
            this.transformedRecords = transformedRecords;
            this.skippedRecords = skippedRecords;
            this.fieldTransformations = fieldTransformations;
            this.userMappingsApplied = userMappingsApplied;
            this.recordTypeMappingsApplied = recordTypeMappingsApplied;
            this.picklistMappingsApplied = picklistMappingsApplied;
        }
        
        public int getTransformedRecords() { return transformedRecords; }
        public int getSkippedRecords() { return skippedRecords; }
        public int getFieldTransformations() { return fieldTransformations; }
        public int getUserMappingsApplied() { return userMappingsApplied; }
        public int getRecordTypeMappingsApplied() { return recordTypeMappingsApplied; }
        public int getPicklistMappingsApplied() { return picklistMappingsApplied; }
        
        public int getTotalMappingsApplied() {
            return userMappingsApplied + recordTypeMappingsApplied + picklistMappingsApplied;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Transformed: %d, Skipped: %d, Field transforms: %d, " +
                "User mappings: %d, RecordType mappings: %d, Picklist mappings: %d",
                transformedRecords, skippedRecords, fieldTransformations,
                userMappingsApplied, recordTypeMappingsApplied, picklistMappingsApplied
            );
        }
    }
    
    public static class DataTransformationException extends RuntimeException {
        public DataTransformationException(String message) {
            super(message);
        }
        
        public DataTransformationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
