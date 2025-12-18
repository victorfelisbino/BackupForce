package com.backupforce.restore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates backup data fields against the target Salesforce org schema.
 * Performs pre-restore validation to catch issues before any data is modified.
 */
public class FieldValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(FieldValidator.class);
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private Consumer<String> logConsumer;
    
    // Salesforce field limits
    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_TEXTAREA_LENGTH = 131072; // 128KB
    private static final int MAX_PICKLIST_LENGTH = 255;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+\\d\\s\\-().ext]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?$");
    
    public FieldValidator(String instanceUrl, String accessToken) {
        this(instanceUrl, accessToken, "v59.0");
    }
    
    public FieldValidator(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
    }
    
    public void setLogConsumer(Consumer<String> consumer) {
        this.logConsumer = consumer;
    }
    
    private void log(String message) {
        if (logConsumer != null) {
            logConsumer.accept(message);
        }
        logger.info(message);
    }
    
    /**
     * Validates records against the target org schema.
     * 
     * @param objectName The Salesforce object name
     * @param records The records to validate
     * @return ValidationResult containing any errors or warnings
     */
    public ValidationResult validateRecords(String objectName, List<Map<String, String>> records) {
        ValidationResult result = new ValidationResult();
        
        if (records == null || records.isEmpty()) {
            result.addWarning("No records to validate");
            return result;
        }
        
        try {
            // Get object metadata
            RelationshipManager relationshipManager = new RelationshipManager(instanceUrl, accessToken, apiVersion);
            RelationshipManager.ObjectMetadata metadata = relationshipManager.describeObject(objectName);
            if (metadata == null) {
                result.addError("Object '" + objectName + "' not found in target org");
                return result;
            }
            
            Map<String, RelationshipManager.FieldInfo> fieldMap = metadata.getFields().stream()
                .collect(Collectors.toMap(
                    f -> f.getName().toLowerCase(),
                    f -> f,
                    (a, b) -> a // Keep first if duplicate
                ));
            
            // Get CSV field names from first record
            Set<String> csvFields = records.get(0).keySet();
            
            // 1. Check for unknown fields
            validateFieldExistence(csvFields, fieldMap, result);
            
            // 2. Check for missing required fields
            validateRequiredFields(csvFields, metadata.getFields(), result);
            
            // 3. Validate each record's field values
            int recordNum = 0;
            for (Map<String, String> record : records) {
                recordNum++;
                validateRecordValues(recordNum, record, fieldMap, result);
                
                // Limit detailed validation to first 100 records for performance
                if (recordNum >= 100 && records.size() > 100) {
                    result.addWarning("Only validated first 100 of " + records.size() + " records in detail");
                    break;
                }
            }
            
            // 4. Summarize validation
            result.setTotalRecords(records.size());
            
        } catch (Exception e) {
            logger.error("Validation error for " + objectName, e);
            result.addError("Validation failed: " + e.getMessage());
        }
        
        return result;
    }
    
    private void validateFieldExistence(Set<String> csvFields, 
                                        Map<String, RelationshipManager.FieldInfo> fieldMap,
                                        ValidationResult result) {
        for (String csvField : csvFields) {
            // Skip reference columns (used for relationship resolution)
            if (csvField.startsWith("_ref_")) {
                continue;
            }
            
            // Skip Id field - it's special
            if (csvField.equalsIgnoreCase("Id")) {
                continue;
            }
            
            String fieldLower = csvField.toLowerCase();
            if (!fieldMap.containsKey(fieldLower)) {
                result.addWarning("Field '" + csvField + "' not found in target org - will be skipped");
            }
        }
    }
    
    private void validateRequiredFields(Set<String> csvFields,
                                        List<RelationshipManager.FieldInfo> fields,
                                        ValidationResult result) {
        Set<String> csvFieldsLower = csvFields.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        
        for (RelationshipManager.FieldInfo field : fields) {
            // Check if field is required (not nillable and not defaulted)
            if (!field.isNillable() && field.isCreateable()) {
                String fieldLower = field.getName().toLowerCase();
                
                // Skip Id and system fields
                if (fieldLower.equals("id") || !field.isCreateable()) {
                    continue;
                }
                
                // Skip fields with default values (they'll be auto-populated)
                // For now, we can't easily detect defaults, so just warn
                
                if (!csvFieldsLower.contains(fieldLower)) {
                    // Check for relationship field (e.g., AccountId might be set via Account.ExternalId__c)
                    boolean hasRelationship = csvFieldsLower.stream()
                        .anyMatch(f -> f.startsWith("_ref_" + fieldLower.replace("id", "")));
                    
                    if (!hasRelationship) {
                        result.addWarning("Required field '" + field.getName() + "' not present in backup data");
                    }
                }
            }
        }
    }
    
    private void validateRecordValues(int recordNum, Map<String, String> record,
                                      Map<String, RelationshipManager.FieldInfo> fieldMap,
                                      ValidationResult result) {
        for (Map.Entry<String, String> entry : record.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();
            
            // Skip empty values and reference columns
            if (value == null || value.isEmpty() || fieldName.startsWith("_ref_")) {
                continue;
            }
            
            String fieldLower = fieldName.toLowerCase();
            RelationshipManager.FieldInfo fieldInfo = fieldMap.get(fieldLower);
            
            if (fieldInfo == null) {
                continue; // Already warned about unknown fields
            }
            
            // Validate based on field type
            validateFieldValue(recordNum, fieldName, value, fieldInfo, result);
        }
    }
    
    private void validateFieldValue(int recordNum, String fieldName, String value,
                                    RelationshipManager.FieldInfo field, ValidationResult result) {
        String type = field.getType().toLowerCase();
        
        switch (type) {
            case "string":
            case "textarea":
                validateTextLength(recordNum, fieldName, value, field, result);
                break;
                
            case "email":
                if (!EMAIL_PATTERN.matcher(value).matches()) {
                    result.addError("Record " + recordNum + ": Invalid email format in '" + 
                        fieldName + "': " + truncate(value, 50));
                }
                break;
                
            case "phone":
                if (!PHONE_PATTERN.matcher(value).matches()) {
                    result.addWarning("Record " + recordNum + ": Unusual phone format in '" + 
                        fieldName + "': " + truncate(value, 30));
                }
                break;
                
            case "url":
                if (!URL_PATTERN.matcher(value).matches()) {
                    result.addWarning("Record " + recordNum + ": URL may not be valid in '" + 
                        fieldName + "': " + truncate(value, 50));
                }
                break;
                
            case "reference":
            case "id":
                if (!ID_PATTERN.matcher(value).matches()) {
                    result.addError("Record " + recordNum + ": Invalid Salesforce ID format in '" + 
                        fieldName + "': " + truncate(value, 20));
                }
                break;
                
            case "boolean":
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false") 
                    && !value.equals("1") && !value.equals("0")) {
                    result.addError("Record " + recordNum + ": Invalid boolean value in '" + 
                        fieldName + "': " + value);
                }
                break;
                
            case "int":
            case "integer":
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    result.addError("Record " + recordNum + ": Invalid integer in '" + 
                        fieldName + "': " + truncate(value, 20));
                }
                break;
                
            case "double":
            case "currency":
            case "percent":
                try {
                    Double.parseDouble(value.replace(",", ""));
                } catch (NumberFormatException e) {
                    result.addError("Record " + recordNum + ": Invalid number in '" + 
                        fieldName + "': " + truncate(value, 20));
                }
                break;
                
            case "date":
                if (!isValidDate(value)) {
                    result.addError("Record " + recordNum + ": Invalid date format in '" + 
                        fieldName + "': " + value + " (expected YYYY-MM-DD)");
                }
                break;
                
            case "datetime":
                if (!isValidDateTime(value)) {
                    result.addError("Record " + recordNum + ": Invalid datetime format in '" + 
                        fieldName + "': " + truncate(value, 30));
                }
                break;
                
            case "picklist":
            case "multipicklist":
                if (value.length() > MAX_PICKLIST_LENGTH) {
                    result.addError("Record " + recordNum + ": Picklist value too long in '" + 
                        fieldName + "': " + value.length() + " chars (max " + MAX_PICKLIST_LENGTH + ")");
                }
                break;
        }
    }
    
    private void validateTextLength(int recordNum, String fieldName, String value,
                                    RelationshipManager.FieldInfo field, ValidationResult result) {
        int maxLength = field.getLength() > 0 ? field.getLength() : MAX_TEXT_LENGTH;
        
        if (value.length() > maxLength) {
            result.addError("Record " + recordNum + ": Text too long in '" + 
                fieldName + "': " + value.length() + " chars (max " + maxLength + ")");
        }
    }
    
    private boolean isValidDate(String value) {
        // Accepts YYYY-MM-DD format
        if (value.length() != 10) return false;
        try {
            java.time.LocalDate.parse(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isValidDateTime(String value) {
        // Accepts ISO 8601 formats
        try {
            if (value.contains("T")) {
                java.time.ZonedDateTime.parse(value);
            } else {
                java.time.LocalDateTime.parse(value.replace(" ", "T"));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
    
    /**
     * Result of field validation containing errors and warnings.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private int totalRecords = 0;
        
        public void addError(String message) {
            errors.add(message);
        }
        
        public void addWarning(String message) {
            warnings.add(message);
        }
        
        public void setTotalRecords(int count) {
            this.totalRecords = count;
        }
        
        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }
        
        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }
        
        public int getTotalRecords() {
            return totalRecords;
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public int getErrorCount() {
            return errors.size();
        }
        
        public int getWarningCount() {
            return warnings.size();
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation: ");
            if (isValid()) {
                sb.append("✓ PASSED");
            } else {
                sb.append("✗ FAILED");
            }
            sb.append(" (").append(totalRecords).append(" records, ");
            sb.append(errors.size()).append(" errors, ");
            sb.append(warnings.size()).append(" warnings)");
            return sb.toString();
        }
        
        /**
         * Gets detailed report with all errors and warnings.
         * Limits output to first 20 of each to avoid overwhelming output.
         */
        public String getDetailedReport() {
            StringBuilder sb = new StringBuilder();
            sb.append(getSummary()).append("\n");
            
            if (!errors.isEmpty()) {
                sb.append("\n❌ Errors:\n");
                int count = 0;
                for (String error : errors) {
                    sb.append("  • ").append(error).append("\n");
                    count++;
                    if (count >= 20 && errors.size() > 20) {
                        sb.append("  ... and ").append(errors.size() - 20).append(" more errors\n");
                        break;
                    }
                }
            }
            
            if (!warnings.isEmpty()) {
                sb.append("\n⚠️ Warnings:\n");
                int count = 0;
                for (String warning : warnings) {
                    sb.append("  • ").append(warning).append("\n");
                    count++;
                    if (count >= 20 && warnings.size() > 20) {
                        sb.append("  ... and ").append(warnings.size() - 20).append(" more warnings\n");
                        break;
                    }
                }
            }
            
            return sb.toString();
        }
    }
}
