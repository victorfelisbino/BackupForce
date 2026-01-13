package com.backupforce.restore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Scans data for sensitive fields (PII, financial data, etc.)
 * Helps users identify and protect sensitive information during restore.
 */
public class SensitiveDataDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(SensitiveDataDetector.class);
    
    // Field name patterns for sensitive data detection
    private static final Map<SensitivityLevel, List<Pattern>> FIELD_PATTERNS = new HashMap<>();
    
    static {
        // HIGH sensitivity patterns
        List<Pattern> highPatterns = new ArrayList<>();
        highPatterns.add(Pattern.compile(".*SSN.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Social.*Security.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Credit.*Card.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Card.*Number.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*CVV.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Password.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*API.*Key.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Secret.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Token.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Bank.*Account.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Routing.*Number.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Tax.*ID.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Driver.*License.*", Pattern.CASE_INSENSITIVE));
        highPatterns.add(Pattern.compile(".*Passport.*", Pattern.CASE_INSENSITIVE));
        FIELD_PATTERNS.put(SensitivityLevel.HIGH, highPatterns);
        
        // MEDIUM sensitivity patterns
        List<Pattern> mediumPatterns = new ArrayList<>();
        mediumPatterns.add(Pattern.compile(".*Email.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Phone.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Mobile.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Fax.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Address.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Street.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*City.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*State.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Zip.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Postal.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*DOB.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Birth.*Date.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Birthday.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Salary.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Income.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Medical.*", Pattern.CASE_INSENSITIVE));
        mediumPatterns.add(Pattern.compile(".*Health.*", Pattern.CASE_INSENSITIVE));
        FIELD_PATTERNS.put(SensitivityLevel.MEDIUM, mediumPatterns);
        
        // LOW sensitivity patterns
        List<Pattern> lowPatterns = new ArrayList<>();
        lowPatterns.add(Pattern.compile(".*First.*Name.*", Pattern.CASE_INSENSITIVE));
        lowPatterns.add(Pattern.compile(".*Last.*Name.*", Pattern.CASE_INSENSITIVE));
        lowPatterns.add(Pattern.compile(".*Full.*Name.*", Pattern.CASE_INSENSITIVE));
        lowPatterns.add(Pattern.compile(".*Department.*", Pattern.CASE_INSENSITIVE));
        lowPatterns.add(Pattern.compile(".*Title.*", Pattern.CASE_INSENSITIVE));
        lowPatterns.add(Pattern.compile(".*Position.*", Pattern.CASE_INSENSITIVE));
        lowPatterns.add(Pattern.compile(".*Company.*", Pattern.CASE_INSENSITIVE));
        lowPatterns.add(Pattern.compile(".*Organization.*", Pattern.CASE_INSENSITIVE));
        FIELD_PATTERNS.put(SensitivityLevel.LOW, lowPatterns);
    }
    
    /**
     * Sensitivity levels for data classification
     */
    public enum SensitivityLevel {
        HIGH("High Risk - Requires Special Protection"),
        MEDIUM("Medium Risk - Handle with Care"),
        LOW("Low Risk - General PII");
        
        private final String description;
        
        SensitivityLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Report of sensitive data found during scan
     */
    public static class SensitiveDataReport {
        private int totalFieldsScanned;
        private int sensitiveFieldsFound;
        private Map<String, List<String>> sensitiveFieldsByObject; // objectName -> field names
        private List<SensitiveField> details;
        
        public SensitiveDataReport() {
            this.sensitiveFieldsByObject = new LinkedHashMap<>();
            this.details = new ArrayList<>();
        }
        
        public int getTotalFieldsScanned() { return totalFieldsScanned; }
        public void setTotalFieldsScanned(int totalFieldsScanned) { 
            this.totalFieldsScanned = totalFieldsScanned; 
        }
        
        public int getSensitiveFieldsFound() { return sensitiveFieldsFound; }
        public void setSensitiveFieldsFound(int sensitiveFieldsFound) { 
            this.sensitiveFieldsFound = sensitiveFieldsFound; 
        }
        
        public Map<String, List<String>> getSensitiveFieldsByObject() { 
            return sensitiveFieldsByObject; 
        }
        
        public List<SensitiveField> getDetails() { return details; }
        
        public void addSensitiveField(SensitiveField field) {
            this.details.add(field);
            this.sensitiveFieldsFound++;
            
            // Group by object
            sensitiveFieldsByObject
                .computeIfAbsent(field.getObjectName(), k -> new ArrayList<>())
                .add(field.getFieldName());
        }
        
        public boolean hasSensitiveData() {
            return sensitiveFieldsFound > 0;
        }
        
        public int getHighRiskCount() {
            return (int) details.stream()
                .filter(f -> f.getLevel() == SensitivityLevel.HIGH)
                .count();
        }
        
        public int getMediumRiskCount() {
            return (int) details.stream()
                .filter(f -> f.getLevel() == SensitivityLevel.MEDIUM)
                .count();
        }
        
        public int getLowRiskCount() {
            return (int) details.stream()
                .filter(f -> f.getLevel() == SensitivityLevel.LOW)
                .count();
        }
    }
    
    /**
     * Details about a specific sensitive field
     */
    public static class SensitiveField {
        private String objectName;
        private String fieldName;
        private String fieldType;
        private SensitivityLevel level;
        private String reason;
        
        public SensitiveField(String objectName, String fieldName, String fieldType, 
                             SensitivityLevel level, String reason) {
            this.objectName = objectName;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.level = level;
            this.reason = reason;
        }
        
        public String getObjectName() { return objectName; }
        public String getFieldName() { return fieldName; }
        public String getFieldType() { return fieldType; }
        public SensitivityLevel getLevel() { return level; }
        public String getReason() { return reason; }
    }
    
    /**
     * Scan objects for sensitive fields
     */
    public static SensitiveDataReport scanObjects(List<?> objects) {
        SensitiveDataReport report = new SensitiveDataReport();
        
        for (Object obj : objects) {
            try {
                String objectName = getObjectName(obj);
                List<String> fields = getFields(obj);
                
                scanFields(objectName, fields, report);
                
            } catch (Exception e) {
                logger.error("Error scanning object: {}", e.getMessage());
            }
        }
        
        return report;
    }
    
    /**
     * Scan specific fields for sensitive data
     */
    public static void scanFields(String objectName, List<String> fieldNames, 
                                  SensitiveDataReport report) {
        for (String fieldName : fieldNames) {
            report.setTotalFieldsScanned(report.getTotalFieldsScanned() + 1);
            
            SensitivityLevel level = getSensitivityLevel(fieldName);
            if (level != null) {
                String reason = getDetectionReason(fieldName, level);
                SensitiveField field = new SensitiveField(
                    objectName, fieldName, "String", level, reason
                );
                report.addSensitiveField(field);
            }
        }
    }
    
    /**
     * Check if a field is sensitive and return its level
     */
    public static SensitivityLevel getSensitivityLevel(String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }
        
        // Check HIGH level first (most critical)
        for (Pattern pattern : FIELD_PATTERNS.get(SensitivityLevel.HIGH)) {
            if (pattern.matcher(fieldName).matches()) {
                return SensitivityLevel.HIGH;
            }
        }
        
        // Check MEDIUM level
        for (Pattern pattern : FIELD_PATTERNS.get(SensitivityLevel.MEDIUM)) {
            if (pattern.matcher(fieldName).matches()) {
                return SensitivityLevel.MEDIUM;
            }
        }
        
        // Check LOW level
        for (Pattern pattern : FIELD_PATTERNS.get(SensitivityLevel.LOW)) {
            if (pattern.matcher(fieldName).matches()) {
                return SensitivityLevel.LOW;
            }
        }
        
        return null;
    }
    
    /**
     * Check if a field name indicates sensitive data
     */
    public static boolean isSensitiveField(String fieldName) {
        return getSensitivityLevel(fieldName) != null;
    }
    
    /**
     * Get reason why field was detected as sensitive
     */
    private static String getDetectionReason(String fieldName, SensitivityLevel level) {
        String lower = fieldName.toLowerCase();
        
        if (level == SensitivityLevel.HIGH) {
            if (lower.contains("ssn") || lower.contains("social")) {
                return "Contains Social Security Number";
            } else if (lower.contains("credit") || lower.contains("card")) {
                return "Contains Credit Card Information";
            } else if (lower.contains("password") || lower.contains("secret")) {
                return "Contains Security Credentials";
            } else if (lower.contains("bank") || lower.contains("account")) {
                return "Contains Banking Information";
            } else {
                return "Contains Highly Sensitive Data";
            }
        } else if (level == SensitivityLevel.MEDIUM) {
            if (lower.contains("email")) {
                return "Contains Email Address";
            } else if (lower.contains("phone") || lower.contains("mobile")) {
                return "Contains Phone Number";
            } else if (lower.contains("address") || lower.contains("street")) {
                return "Contains Physical Address";
            } else if (lower.contains("birth") || lower.contains("dob")) {
                return "Contains Date of Birth";
            } else {
                return "Contains Personal Information";
            }
        } else {
            return "Contains Identifiable Information";
        }
    }
    
    /**
     * Extract object name from object using reflection
     */
    private static String getObjectName(Object obj) {
        try {
            return (String) obj.getClass().getMethod("getName").invoke(obj);
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Get field names from object
     */
    private static List<String> getFields(Object obj) {
        // This is a placeholder - in real implementation, you'd:
        // 1. Read CSV headers if file-based
        // 2. Query database schema if database-based
        // 3. Use Salesforce metadata if available
        
        // For now, return empty list (will be filled by actual implementation)
        return new ArrayList<>();
    }
}
