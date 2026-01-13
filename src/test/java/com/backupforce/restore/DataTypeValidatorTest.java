package com.backupforce.restore;

import com.backupforce.restore.DataTypeValidator.IssueSeverity;
import com.backupforce.restore.DataTypeValidator.TypeValidationResult;
import com.backupforce.restore.RelationshipManager.FieldInfo;
import com.backupforce.restore.RelationshipManager.ObjectMetadata;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DataTypeValidator
 */
class DataTypeValidatorTest {

    @Test
    void testValidateFieldType_ExactMatch() {
        assertTrue(DataTypeValidator.validateFieldType("Name", "string", "string"),
            "Exact type match should be valid");
        assertTrue(DataTypeValidator.validateFieldType("Count", "int", "int"),
            "Exact int match should be valid");
        assertTrue(DataTypeValidator.validateFieldType("Active", "boolean", "boolean"),
            "Exact boolean match should be valid");
    }

    @Test
    void testValidateFieldType_StringCompatibility() {
        // Any type can convert to string
        assertTrue(DataTypeValidator.validateFieldType("Value", "int", "string"),
            "Int to string should be valid");
        assertTrue(DataTypeValidator.validateFieldType("Value", "double", "string"),
            "Double to string should be valid");
        assertTrue(DataTypeValidator.validateFieldType("Value", "boolean", "string"),
            "Boolean to string should be valid");
        assertTrue(DataTypeValidator.validateFieldType("Value", "date", "string"),
            "Date to string should be valid");
    }

    @Test
    void testValidateFieldType_TextAreaCompatibility() {
        assertTrue(DataTypeValidator.validateFieldType("Description", "string", "textarea"),
            "String to textarea should be valid");
        assertTrue(DataTypeValidator.validateFieldType("Description", "text", "textarea"),
            "Text to textarea should be valid");
    }

    @Test
    void testValidateFieldType_NumberCompatibility() {
        assertTrue(DataTypeValidator.validateFieldType("Amount", "int", "double"),
            "Int to double should be valid");
        assertTrue(DataTypeValidator.validateFieldType("Amount", "double", "currency"),
            "Double to currency should be valid");
        assertTrue(DataTypeValidator.validateFieldType("Rate", "double", "percent"),
            "Double to percent should be valid");
    }

    @Test
    void testValidateFieldType_IncompatibleTypes() {
        assertFalse(DataTypeValidator.validateFieldType("Name", "string", "int"),
            "String to int should be invalid");
        assertFalse(DataTypeValidator.validateFieldType("Active", "boolean", "double"),
            "Boolean to double should be invalid");
        assertFalse(DataTypeValidator.validateFieldType("Date", "date", "int"),
            "Date to int should be invalid");
    }

    @Test
    void testValidateFieldType_CaseInsensitive() {
        assertTrue(DataTypeValidator.validateFieldType("Name", "STRING", "string"),
            "Should be case insensitive");
        assertTrue(DataTypeValidator.validateFieldType("Name", "String", "STRING"),
            "Should handle mixed case");
    }

    @Test
    void testValidateFieldType_NullTypes() {
        assertFalse(DataTypeValidator.validateFieldType("Name", null, "string"),
            "Null source type should be invalid");
        assertFalse(DataTypeValidator.validateFieldType("Name", "string", null),
            "Null target type should be invalid");
        assertFalse(DataTypeValidator.validateFieldType("Name", null, null),
            "Both null should be invalid");
    }

    @Test
    void testValidateFieldLength_ValidLength() {
        assertTrue(DataTypeValidator.validateFieldLength("Hello", 10),
            "String within limit should be valid");
        assertTrue(DataTypeValidator.validateFieldLength("Hi", 2),
            "String at exact limit should be valid");
    }

    @Test
    void testValidateFieldLength_ExceedsLength() {
        assertFalse(DataTypeValidator.validateFieldLength("Hello World", 5),
            "String exceeding limit should be invalid");
    }

    @Test
    void testValidateFieldLength_NullValue() {
        assertTrue(DataTypeValidator.validateFieldLength(null, 10),
            "Null value should be valid");
    }

    @Test
    void testValidateFieldLength_NoLimit() {
        assertTrue(DataTypeValidator.validateFieldLength("Any length string", 0),
            "Zero max length means no restriction");
        assertTrue(DataTypeValidator.validateFieldLength("Any length string", -1),
            "Negative max length means no restriction");
    }

    @Test
    void testValidatePicklistValue_ValidValue() {
        assertTrue(DataTypeValidator.validatePicklistValue("Active", 
            Arrays.asList("Active", "Inactive", "Pending")),
            "Value in list should be valid");
    }

    @Test
    void testValidatePicklistValue_InvalidValue() {
        assertFalse(DataTypeValidator.validatePicklistValue("Unknown", 
            Arrays.asList("Active", "Inactive", "Pending")),
            "Value not in list should be invalid");
    }

    @Test
    void testValidatePicklistValue_NullOrEmpty() {
        assertTrue(DataTypeValidator.validatePicklistValue(null, 
            Arrays.asList("Active", "Inactive")),
            "Null value should be valid");
        assertTrue(DataTypeValidator.validatePicklistValue("", 
            Arrays.asList("Active", "Inactive")),
            "Empty value should be valid");
    }

    @Test
    void testValidatePicklistValue_NoRestrictions() {
        assertTrue(DataTypeValidator.validatePicklistValue("Anything", null),
            "Null list should allow any value");
        assertTrue(DataTypeValidator.validatePicklistValue("Anything", Arrays.asList()),
            "Empty list should allow any value");
    }

    @Test
    void testIsValidValue_Integer() {
        assertTrue(DataTypeValidator.isValidValue("123", "int"),
            "Valid integer should pass");
        assertTrue(DataTypeValidator.isValidValue("-456", "integer"),
            "Negative integer should pass");
        assertFalse(DataTypeValidator.isValidValue("abc", "int"),
            "Non-numeric should fail");
        assertFalse(DataTypeValidator.isValidValue("12.34", "int"),
            "Decimal should fail for int");
    }

    @Test
    void testIsValidValue_Double() {
        assertTrue(DataTypeValidator.isValidValue("123.45", "double"),
            "Valid double should pass");
        assertTrue(DataTypeValidator.isValidValue("123", "double"),
            "Integer as double should pass");
        assertTrue(DataTypeValidator.isValidValue("-456.78", "currency"),
            "Negative currency should pass");
        assertFalse(DataTypeValidator.isValidValue("abc", "double"),
            "Non-numeric should fail");
    }

    @Test
    void testIsValidValue_Boolean() {
        assertTrue(DataTypeValidator.isValidValue("true", "boolean"),
            "Lowercase true should pass");
        assertTrue(DataTypeValidator.isValidValue("TRUE", "boolean"),
            "Uppercase TRUE should pass");
        assertTrue(DataTypeValidator.isValidValue("false", "checkbox"),
            "False for checkbox should pass");
        assertTrue(DataTypeValidator.isValidValue("1", "boolean"),
            "1 should pass as true");
        assertTrue(DataTypeValidator.isValidValue("0", "boolean"),
            "0 should pass as false");
        assertFalse(DataTypeValidator.isValidValue("yes", "boolean"),
            "Yes should fail");
    }

    @Test
    void testIsValidValue_Date() {
        assertTrue(DataTypeValidator.isValidValue("2024-01-15", "date"),
            "Valid date format should pass");
        assertTrue(DataTypeValidator.isValidValue("2024-12-31", "date"),
            "Valid end of year should pass");
        assertFalse(DataTypeValidator.isValidValue("15-01-2024", "date"),
            "Wrong date format should fail");
        assertFalse(DataTypeValidator.isValidValue("2024/01/15", "date"),
            "Slash separator should fail");
    }

    @Test
    void testIsValidValue_DateTime() {
        assertTrue(DataTypeValidator.isValidValue("2024-01-15T10:30:00", "datetime"),
            "Valid datetime should pass");
        assertTrue(DataTypeValidator.isValidValue("2024-01-15T10:30:00Z", "datetime"),
            "Valid datetime with Z should pass");
        assertTrue(DataTypeValidator.isValidValue("2024-01-15T10:30:00.123", "datetime"),
            "Valid datetime with milliseconds should pass");
        assertFalse(DataTypeValidator.isValidValue("2024-01-15", "datetime"),
            "Date without time should fail");
    }

    @Test
    void testIsValidValue_String() {
        assertTrue(DataTypeValidator.isValidValue("Any text", "string"),
            "Any text should be valid for string");
        assertTrue(DataTypeValidator.isValidValue("123", "text"),
            "Numbers should be valid for text");
        assertTrue(DataTypeValidator.isValidValue("", "textarea"),
            "Empty should be valid for textarea");
    }

    @Test
    void testIsValidValue_NullOrEmpty() {
        assertTrue(DataTypeValidator.isValidValue(null, "string"),
            "Null should be valid");
        assertTrue(DataTypeValidator.isValidValue("", "string"),
            "Empty should be valid");
        assertTrue(DataTypeValidator.isValidValue("   ", "string"),
            "Whitespace should be valid");
    }

    @Test
    void testValidateFields_AllValid() {
        Map<String, String> sourceFields = new HashMap<>();
        sourceFields.put("Name", "string");
        sourceFields.put("Email", "email");
        sourceFields.put("Active", "boolean");

        ObjectMetadata targetMetadata = new ObjectMetadata("Account");
        targetMetadata.getFields().add(createField("Name", "string", true, 255));
        targetMetadata.getFields().add(createField("Email", "email", true, 80));
        targetMetadata.getFields().add(createField("Active", "boolean", true, 0));

        TypeValidationResult result = DataTypeValidator.validateFields(
            "Account", sourceFields, targetMetadata);

        assertTrue(result.isValid(), "All fields should be valid");
        assertEquals(3, result.getTotalFields(), "Should have 3 total fields");
        assertEquals(3, result.getValidFields(), "Should have 3 valid fields");
        assertEquals(0, result.getInvalidFields(), "Should have 0 invalid fields");
        assertEquals(0, result.getErrorCount(), "Should have 0 errors");
    }

    @Test
    void testValidateFields_FieldNotInTarget() {
        Map<String, String> sourceFields = new HashMap<>();
        sourceFields.put("CustomField__c", "string");

        ObjectMetadata targetMetadata = new ObjectMetadata("Account");
        // CustomField__c doesn't exist in target

        TypeValidationResult result = DataTypeValidator.validateFields(
            "Account", sourceFields, targetMetadata);

        assertFalse(result.isValid(), "Should be invalid when field missing");
        assertEquals(1, result.getTotalFields(), "Should have 1 total field");
        assertEquals(0, result.getValidFields(), "Should have 0 valid fields");
        assertEquals(1, result.getInvalidFields(), "Should have 1 invalid field");
        assertEquals(1, result.getErrorCount(), "Should have 1 error");
        assertTrue(result.getIssues().get(0).getIssue().contains("does not exist"),
            "Error should mention field doesn't exist");
    }

    @Test
    void testValidateFields_ReadOnlyField() {
        Map<String, String> sourceFields = new HashMap<>();
        sourceFields.put("CreatedDate", "datetime");

        ObjectMetadata targetMetadata = new ObjectMetadata("Account");
        targetMetadata.getFields().add(createField("CreatedDate", "datetime", false, 0));

        TypeValidationResult result = DataTypeValidator.validateFields(
            "Account", sourceFields, targetMetadata);

        assertEquals(1, result.getTotalFields(), "Should have 1 total field");
        assertEquals(1, result.getWarningCount(), "Should have 1 warning");
        assertTrue(result.getIssues().get(0).getIssue().contains("not createable"),
            "Warning should mention not createable");
    }

    @Test
    void testValidateFields_TypeMismatch() {
        Map<String, String> sourceFields = new HashMap<>();
        sourceFields.put("Count", "string");

        ObjectMetadata targetMetadata = new ObjectMetadata("Account");
        targetMetadata.getFields().add(createField("Count", "int", true, 0));

        TypeValidationResult result = DataTypeValidator.validateFields(
            "Account", sourceFields, targetMetadata);

        assertFalse(result.isValid(), "Should be invalid with type mismatch");
        assertEquals(1, result.getErrorCount(), "Should have 1 error");
        assertTrue(result.getIssues().get(0).getIssue().contains("Type mismatch"),
            "Error should mention type mismatch");
    }

    @Test
    void testValidateFields_LengthWarning() {
        Map<String, String> sourceFields = new HashMap<>();
        sourceFields.put("Description", "string");

        ObjectMetadata targetMetadata = new ObjectMetadata("Account");
        targetMetadata.getFields().add(createField("Description", "string", true, 255));

        TypeValidationResult result = DataTypeValidator.validateFields(
            "Account", sourceFields, targetMetadata);

        assertTrue(result.isValid(), "Should be valid with length info");
        assertEquals(1, result.getInfoCount(), "Should have 1 info message");
        assertTrue(result.getIssues().get(0).getIssue().contains("max length"),
            "Info should mention max length");
    }

    @Test
    void testIssueSeverity_Descriptions() {
        assertEquals("Error - Will prevent restore", 
            IssueSeverity.ERROR.getDescription(),
            "ERROR should have correct description");
        assertEquals("Warning - May cause issues", 
            IssueSeverity.WARNING.getDescription(),
            "WARNING should have correct description");
        assertEquals("Info - Review recommended", 
            IssueSeverity.INFO.getDescription(),
            "INFO should have correct description");
    }

    @Test
    void testValidateFields_MixedIssues() {
        Map<String, String> sourceFields = new HashMap<>();
        sourceFields.put("Name", "string");              // Valid
        sourceFields.put("Email", "email");              // Valid with length info
        sourceFields.put("MissingField", "string");      // Error - doesn't exist
        sourceFields.put("CreatedDate", "datetime");     // Warning - not createable
        sourceFields.put("Count", "string");             // Error - type mismatch

        ObjectMetadata targetMetadata = new ObjectMetadata("Account");
        targetMetadata.getFields().add(createField("Name", "string", true, 255));
        targetMetadata.getFields().add(createField("Email", "email", true, 80));
        targetMetadata.getFields().add(createField("CreatedDate", "datetime", false, 0));
        targetMetadata.getFields().add(createField("Count", "int", true, 0));

        TypeValidationResult result = DataTypeValidator.validateFields(
            "Account", sourceFields, targetMetadata);

        assertFalse(result.isValid(), "Should be invalid with errors");
        assertEquals(5, result.getTotalFields(), "Should have 5 total fields");
        assertEquals(2, result.getErrorCount(), "Should have 2 errors");
        assertEquals(1, result.getWarningCount(), "Should have 1 warning");
        assertTrue(result.getInfoCount() > 0, "Should have info messages");
    }

    // Helper method to create FieldInfo objects for testing
    private FieldInfo createField(String name, String type, boolean createable, int length) {
        FieldInfo field = new FieldInfo(name, type, name);
        field.setCreateable(createable);
        if (length > 0) {
            field.setLength(length);
        }
        return field;
    }
}
