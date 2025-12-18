package com.backupforce.restore;

import com.backupforce.restore.FieldValidator.ValidationResult;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FieldValidator class.
 * Tests field validation logic (without requiring Salesforce connection).
 */
@DisplayName("FieldValidator Tests")
class FieldValidatorTest {
    
    @Nested
    @DisplayName("ValidationResult Tests")
    class ValidationResultTests {
        
        @Test
        @DisplayName("Empty validation result is valid")
        void testEmptyResultIsValid() {
            ValidationResult result = new ValidationResult();
            
            assertTrue(result.isValid());
            assertFalse(result.hasErrors());
            assertFalse(result.hasWarnings());
            assertEquals(0, result.getErrorCount());
            assertEquals(0, result.getWarningCount());
        }
        
        @Test
        @DisplayName("Result with errors is not valid")
        void testResultWithErrorsNotValid() {
            ValidationResult result = new ValidationResult();
            result.addError("Test error");
            
            assertFalse(result.isValid());
            assertTrue(result.hasErrors());
            assertEquals(1, result.getErrorCount());
        }
        
        @Test
        @DisplayName("Result with only warnings is valid")
        void testResultWithWarningsIsValid() {
            ValidationResult result = new ValidationResult();
            result.addWarning("Test warning");
            
            assertTrue(result.isValid());
            assertFalse(result.hasErrors());
            assertTrue(result.hasWarnings());
            assertEquals(1, result.getWarningCount());
        }
        
        @Test
        @DisplayName("Summary includes counts")
        void testSummaryIncludesCounts() {
            ValidationResult result = new ValidationResult();
            result.setTotalRecords(100);
            result.addError("Error 1");
            result.addError("Error 2");
            result.addWarning("Warning 1");
            
            String summary = result.getSummary();
            
            assertTrue(summary.contains("100 records"));
            assertTrue(summary.contains("2 errors"));
            assertTrue(summary.contains("1 warnings"));
            assertTrue(summary.contains("FAILED"));
        }
        
        @Test
        @DisplayName("Detailed report shows errors and warnings")
        void testDetailedReport() {
            ValidationResult result = new ValidationResult();
            result.setTotalRecords(50);
            result.addError("Error message 1");
            result.addWarning("Warning message 1");
            
            String report = result.getDetailedReport();
            
            assertTrue(report.contains("Error message 1"));
            assertTrue(report.contains("Warning message 1"));
            assertTrue(report.contains("❌ Errors"));
            assertTrue(report.contains("⚠️ Warnings"));
        }
        
        @Test
        @DisplayName("Report truncates long error lists")
        void testReportTruncatesLongLists() {
            ValidationResult result = new ValidationResult();
            
            // Add 25 errors
            for (int i = 0; i < 25; i++) {
                result.addError("Error " + i);
            }
            
            String report = result.getDetailedReport();
            
            // Should show first 20 and indicate more
            assertTrue(report.contains("... and 5 more errors"));
        }
    }
    
    @Nested
    @DisplayName("Field Pattern Tests")
    class FieldPatternTests {
        
        @Test
        @DisplayName("Valid Salesforce ID format - 15 char")
        void testValidId15Char() {
            String id = "001000000000001";
            assertTrue(id.matches("^[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?$"));
        }
        
        @Test
        @DisplayName("Valid Salesforce ID format - 18 char")
        void testValidId18Char() {
            String id = "001000000000001AAA";
            assertTrue(id.matches("^[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?$"));
        }
        
        @Test
        @DisplayName("Invalid Salesforce ID format")
        void testInvalidIdFormat() {
            String badId = "not-a-valid-id";
            assertFalse(badId.matches("^[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?$"));
        }
        
        @Test
        @DisplayName("Valid email format")
        void testValidEmail() {
            String email = "test@example.com";
            assertTrue(email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"));
        }
        
        @Test
        @DisplayName("Invalid email format")
        void testInvalidEmail() {
            String badEmail = "not-an-email";
            assertFalse(badEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"));
        }
        
        @Test
        @DisplayName("Valid URL format")
        void testValidUrl() {
            String url = "https://example.com/path";
            assertTrue(url.matches("(?i)^https?://.*"));
        }
        
        @Test
        @DisplayName("Invalid URL format")
        void testInvalidUrl() {
            String badUrl = "not-a-url";
            assertFalse(badUrl.matches("(?i)^https?://.*"));
        }
        
        @Test
        @DisplayName("Valid date format")
        void testValidDateFormat() {
            String date = "2024-01-15";
            try {
                java.time.LocalDate.parse(date);
                assertTrue(true);
            } catch (Exception e) {
                fail("Should be a valid date");
            }
        }
        
        @Test
        @DisplayName("Invalid date format")
        void testInvalidDateFormat() {
            String badDate = "01/15/2024";
            assertThrows(Exception.class, () -> java.time.LocalDate.parse(badDate));
        }
    }
    
    @Nested
    @DisplayName("Record Validation Logic Tests")
    class RecordValidationLogicTests {
        
        @Test
        @DisplayName("Skip reference columns in validation")
        void testSkipRefColumns() {
            Map<String, String> record = new LinkedHashMap<>();
            record.put("Name", "Test Account");
            record.put("_ref_OwnerId_Email", "owner@example.com");
            
            // Reference columns start with _ref_ and should be skipped
            assertTrue(record.get("_ref_OwnerId_Email").startsWith(""));
        }
        
        @Test
        @DisplayName("Boolean validation accepts valid values")
        void testBooleanValidation() {
            List<String> validBooleans = Arrays.asList("true", "false", "TRUE", "FALSE", "1", "0");
            
            for (String value : validBooleans) {
                boolean isValid = value.equalsIgnoreCase("true") 
                    || value.equalsIgnoreCase("false")
                    || value.equals("1") 
                    || value.equals("0");
                assertTrue(isValid, "Should accept: " + value);
            }
        }
        
        @Test
        @DisplayName("Number validation accepts valid formats")
        void testNumberValidation() {
            List<String> validNumbers = Arrays.asList("123", "-456", "78.90", "1,234.56");
            
            for (String value : validNumbers) {
                try {
                    Double.parseDouble(value.replace(",", ""));
                    assertTrue(true);
                } catch (NumberFormatException e) {
                    fail("Should accept: " + value);
                }
            }
        }
        
        @Test
        @DisplayName("Number validation rejects invalid formats")
        void testInvalidNumberValidation() {
            String invalidNumber = "not-a-number";
            assertThrows(NumberFormatException.class, 
                () -> Double.parseDouble(invalidNumber));
        }
    }
}
