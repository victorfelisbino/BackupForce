package com.backupforce.restore;

import com.backupforce.restore.RelationshipManager.FieldInfo;
import com.backupforce.restore.RelationshipManager.ObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Validates data types and field compatibility between source and target.
 * Ensures data can be successfully restored without type mismatches.
 */
public class DataTypeValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(DataTypeValidator.class);
    
    // Compatible type mappings (source -> target types that are compatible)
    private static final Map<String, Set<String>> COMPATIBLE_TYPES = new HashMap<>();
    
    static {
        // String/Text types
        Set<String> stringCompatible = new HashSet<>(Arrays.asList(
            "string", "text", "textarea", "url", "email", "phone", "picklist", 
            "multipicklist", "id", "reference", "combobox"
        ));
        COMPATIBLE_TYPES.put("string", stringCompatible);
        COMPATIBLE_TYPES.put("textarea", stringCompatible);
        
        // Number types
        Set<String> numberCompatible = new HashSet<>(Arrays.asList(
            "int", "double", "currency", "percent", "number"
        ));
        COMPATIBLE_TYPES.put("int", numberCompatible);
        COMPATIBLE_TYPES.put("double", numberCompatible);
        COMPATIBLE_TYPES.put("currency", numberCompatible);
        
        // Date/Time types
        Set<String> dateCompatible = new HashSet<>(Arrays.asList(
            "date", "datetime", "time"
        ));
        COMPATIBLE_TYPES.put("date", dateCompatible);
        COMPATIBLE_TYPES.put("datetime", dateCompatible);
        
        // Boolean
        Set<String> booleanCompatible = new HashSet<>(Arrays.asList("boolean", "checkbox"));
        COMPATIBLE_TYPES.put("boolean", booleanCompatible);
        
        // Binary
        Set<String> binaryCompatible = new HashSet<>(Arrays.asList("base64", "byte"));
        COMPATIBLE_TYPES.put("base64", binaryCompatible);
    }
    
    /**
     * Severity levels for validation issues
     */
    public enum IssueSeverity {
        ERROR("Error - Will prevent restore"),
        WARNING("Warning - May cause issues"),
        INFO("Info - Review recommended");
        
        private final String description;
        
        IssueSeverity(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Result of type validation
     */
    public static class TypeValidationResult {
        private boolean valid;
        private int totalFields;
        private int validFields;
        private int invalidFields;
        private List<FieldTypeIssue> issues;
        
        public TypeValidationResult() {
            this.issues = new ArrayList<>();
            this.valid = true;
        }
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public int getTotalFields() { return totalFields; }
        public void setTotalFields(int totalFields) { this.totalFields = totalFields; }
        
        public int getValidFields() { return validFields; }
        public void setValidFields(int validFields) { this.validFields = validFields; }
        
        public int getInvalidFields() { return invalidFields; }
        public void setInvalidFields(int invalidFields) { this.invalidFields = invalidFields; }
        
        public List<FieldTypeIssue> getIssues() { return issues; }
        
        public void addIssue(FieldTypeIssue issue) {
            this.issues.add(issue);
            if (issue.getSeverity() == IssueSeverity.ERROR) {
                this.valid = false;
                this.invalidFields++;
            } else {
                this.validFields++;
            }
        }
        
        public int getErrorCount() {
            return (int) issues.stream()
                .filter(i -> i.getSeverity() == IssueSeverity.ERROR)
                .count();
        }
        
        public int getWarningCount() {
            return (int) issues.stream()
                .filter(i -> i.getSeverity() == IssueSeverity.WARNING)
                .count();
        }
        
        public int getInfoCount() {
            return (int) issues.stream()
                .filter(i -> i.getSeverity() == IssueSeverity.INFO)
                .count();
        }
    }
    
    /**
     * Details about a specific field type issue
     */
    public static class FieldTypeIssue {
        private String objectName;
        private String fieldName;
        private String sourceType;
        private String targetType;
        private String issue;
        private IssueSeverity severity;
        
        public FieldTypeIssue(String objectName, String fieldName, String sourceType, 
                             String targetType, String issue, IssueSeverity severity) {
            this.objectName = objectName;
            this.fieldName = fieldName;
            this.sourceType = sourceType;
            this.targetType = targetType;
            this.issue = issue;
            this.severity = severity;
        }
        
        public String getObjectName() { return objectName; }
        public String getFieldName() { return fieldName; }
        public String getSourceType() { return sourceType; }
        public String getTargetType() { return targetType; }
        public String getIssue() { return issue; }
        public IssueSeverity getSeverity() { return severity; }
    }
    
    /**
     * Validate fields between source and target metadata
     */
    public static TypeValidationResult validateFields(String objectName, 
                                                      Map<String, String> sourceFields, 
                                                      ObjectMetadata targetMetadata) {
        TypeValidationResult result = new TypeValidationResult();
        result.setTotalFields(sourceFields.size());
        
        // Build target field map for quick lookup
        Map<String, FieldInfo> targetFieldMap = new HashMap<>();
        for (FieldInfo field : targetMetadata.getFields()) {
            targetFieldMap.put(field.getName().toLowerCase(), field);
        }
        
        // Validate each source field
        for (Map.Entry<String, String> entry : sourceFields.entrySet()) {
            String fieldName = entry.getKey();
            String sourceType = entry.getValue();
            
            // Check if field exists in target
            FieldInfo targetField = targetFieldMap.get(fieldName.toLowerCase());
            
            if (targetField == null) {
                // Field doesn't exist in target
                result.addIssue(new FieldTypeIssue(
                    objectName, fieldName, sourceType, null,
                    "Field does not exist in target org",
                    IssueSeverity.ERROR
                ));
                continue;
            }
            
            // Check if field is createable
            if (!targetField.isCreateable()) {
                result.addIssue(new FieldTypeIssue(
                    objectName, fieldName, sourceType, targetField.getType(),
                    "Field is not createable (read-only or system field)",
                    IssueSeverity.WARNING
                ));
                continue;
            }
            
            // Validate type compatibility
            String targetType = targetField.getType();
            if (!areTypesCompatible(sourceType, targetType)) {
                result.addIssue(new FieldTypeIssue(
                    objectName, fieldName, sourceType, targetType,
                    "Type mismatch: source is " + sourceType + ", target is " + targetType,
                    IssueSeverity.ERROR
                ));
                continue;
            }
            
            // Check length constraints for string fields
            if (isStringType(targetType) && targetField.getLength() > 0) {
                result.addIssue(new FieldTypeIssue(
                    objectName, fieldName, sourceType, targetType,
                    "String field with max length " + targetField.getLength() + " - values may be truncated",
                    IssueSeverity.INFO
                ));
            } else {
                // Field is valid with no issues
                result.setValidFields(result.getValidFields() + 1);
            }
        }
        
        return result;
    }
    
    /**
     * Validate a single field type
     */
    public static boolean validateFieldType(String fieldName, String sourceType, String targetType) {
        if (sourceType == null || targetType == null) {
            return false;
        }
        
        return areTypesCompatible(sourceType.toLowerCase(), targetType.toLowerCase());
    }
    
    /**
     * Check if two types are compatible
     */
    private static boolean areTypesCompatible(String sourceType, String targetType) {
        if (sourceType == null || targetType == null) {
            return false;
        }
        
        sourceType = sourceType.toLowerCase();
        targetType = targetType.toLowerCase();
        
        // Exact match
        if (sourceType.equals(targetType)) {
            return true;
        }
        
        // Check compatibility map
        Set<String> compatible = COMPATIBLE_TYPES.get(sourceType);
        if (compatible != null && compatible.contains(targetType)) {
            return true;
        }
        
        // Special cases
        // Any type can be converted to string
        if (isStringType(targetType)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if type is a string type
     */
    private static boolean isStringType(String type) {
        if (type == null) return false;
        String lower = type.toLowerCase();
        return lower.equals("string") || lower.equals("text") || 
               lower.equals("textarea") || lower.equals("url") ||
               lower.equals("email") || lower.equals("phone");
    }
    
    /**
     * Validate field length
     */
    public static boolean validateFieldLength(String value, int maxLength) {
        if (value == null) {
            return true;
        }
        
        if (maxLength <= 0) {
            return true; // No length restriction
        }
        
        return value.length() <= maxLength;
    }
    
    /**
     * Validate picklist value
     */
    public static boolean validatePicklistValue(String value, List<String> validValues) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Null/empty is valid
        }
        
        if (validValues == null || validValues.isEmpty()) {
            return true; // No restrictions
        }
        
        return validValues.contains(value);
    }
    
    /**
     * Check if a value is valid for the field type
     */
    public static boolean isValidValue(String value, String fieldType) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Null/empty is generally valid
        }
        
        String type = fieldType.toLowerCase();
        
        try {
            switch (type) {
                case "int":
                case "integer":
                    Integer.parseInt(value);
                    return true;
                    
                case "double":
                case "currency":
                case "percent":
                    Double.parseDouble(value);
                    return true;
                    
                case "boolean":
                case "checkbox":
                    return value.equalsIgnoreCase("true") || 
                           value.equalsIgnoreCase("false") ||
                           value.equals("1") || value.equals("0");
                    
                case "date":
                    // Basic date format check (YYYY-MM-DD)
                    return value.matches("\\d{4}-\\d{2}-\\d{2}");
                    
                case "datetime":
                    // Basic datetime format check
                    return value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
                    
                default:
                    return true; // String types accept anything
            }
        } catch (Exception e) {
            return false;
        }
    }
}
