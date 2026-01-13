package com.backupforce.restore;

import com.backupforce.restore.SensitiveDataDetector.SensitiveDataReport;
import com.backupforce.restore.SensitiveDataDetector.SensitivityLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SensitiveDataDetector
 */
class SensitiveDataDetectorTest {

    @Test
    void testIsSensitiveField_SSN() {
        assertTrue(SensitiveDataDetector.isSensitiveField("SSN"), "SSN should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Social_Security_Number__c"), 
            "Custom SSN field should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Employee_SSN"), 
            "Field with SSN should be sensitive");
    }

    @Test
    void testIsSensitiveField_CreditCard() {
        assertTrue(SensitiveDataDetector.isSensitiveField("Credit_Card_Number__c"), 
            "Credit card field should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("CardNumber"), 
            "Card number should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("CVV"), 
            "CVV should be sensitive");
    }

    @Test
    void testIsSensitiveField_Password() {
        assertTrue(SensitiveDataDetector.isSensitiveField("Password"), 
            "Password should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("User_Password__c"), 
            "Password field should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("API_Key__c"), 
            "API key should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Secret_Token"), 
            "Secret token should be sensitive");
    }

    @Test
    void testIsSensitiveField_Email() {
        assertTrue(SensitiveDataDetector.isSensitiveField("Email"), 
            "Email should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Personal_Email__c"), 
            "Personal email should be sensitive");
    }

    @Test
    void testIsSensitiveField_Phone() {
        assertTrue(SensitiveDataDetector.isSensitiveField("Phone"), 
            "Phone should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("MobilePhone"), 
            "Mobile phone should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Home_Phone__c"), 
            "Home phone should be sensitive");
    }

    @Test
    void testIsSensitiveField_Address() {
        assertTrue(SensitiveDataDetector.isSensitiveField("BillingAddress"), 
            "Address should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Street"), 
            "Street should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("BillingCity"), 
            "City should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("MailingState"), 
            "State should be sensitive");
    }

    @Test
    void testIsSensitiveField_DateOfBirth() {
        assertTrue(SensitiveDataDetector.isSensitiveField("DOB"), 
            "DOB should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Birth_Date__c"), 
            "Birth date should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Birthday"), 
            "Birthday should be sensitive");
    }

    @Test
    void testIsSensitiveField_Financial() {
        assertTrue(SensitiveDataDetector.isSensitiveField("Salary"), 
            "Salary should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Annual_Income__c"), 
            "Income should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Bank_Account_Number"), 
            "Bank account should be sensitive");
    }

    @Test
    void testIsSensitiveField_Name() {
        assertTrue(SensitiveDataDetector.isSensitiveField("FirstName"), 
            "First name should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("LastName"), 
            "Last name should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("Full_Name__c"), 
            "Full name should be sensitive");
    }

    @Test
    void testIsSensitiveField_NotSensitive() {
        assertFalse(SensitiveDataDetector.isSensitiveField("Id"), 
            "ID should not be sensitive");
        assertFalse(SensitiveDataDetector.isSensitiveField("CreatedDate"), 
            "Created date should not be sensitive");
        assertFalse(SensitiveDataDetector.isSensitiveField("LastModifiedBy"), 
            "LastModifiedBy should not be sensitive");
        assertFalse(SensitiveDataDetector.isSensitiveField("Status__c"), 
            "Status should not be sensitive");
    }

    @Test
    void testIsSensitiveField_CaseInsensitive() {
        assertTrue(SensitiveDataDetector.isSensitiveField("EMAIL"), 
            "Uppercase EMAIL should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("password"), 
            "Lowercase password should be sensitive");
        assertTrue(SensitiveDataDetector.isSensitiveField("CrEdIt_CaRd"), 
            "Mixed case should be sensitive");
    }

    @Test
    void testIsSensitiveField_NullOrEmpty() {
        assertFalse(SensitiveDataDetector.isSensitiveField(null), 
            "Null should not be sensitive");
        assertFalse(SensitiveDataDetector.isSensitiveField(""), 
            "Empty string should not be sensitive");
        assertFalse(SensitiveDataDetector.isSensitiveField("   "), 
            "Whitespace should not be sensitive");
    }

    @Test
    void testGetSensitivityLevel_High() {
        assertEquals(SensitivityLevel.HIGH, 
            SensitiveDataDetector.getSensitivityLevel("SSN"), 
            "SSN should be HIGH risk");
        assertEquals(SensitivityLevel.HIGH, 
            SensitiveDataDetector.getSensitivityLevel("Password"), 
            "Password should be HIGH risk");
        assertEquals(SensitivityLevel.HIGH, 
            SensitiveDataDetector.getSensitivityLevel("Credit_Card__c"), 
            "Credit card should be HIGH risk");
    }

    @Test
    void testGetSensitivityLevel_Medium() {
        assertEquals(SensitivityLevel.MEDIUM, 
            SensitiveDataDetector.getSensitivityLevel("Email"), 
            "Email should be MEDIUM risk");
        assertEquals(SensitivityLevel.MEDIUM, 
            SensitiveDataDetector.getSensitivityLevel("Phone"), 
            "Phone should be MEDIUM risk");
        assertEquals(SensitivityLevel.MEDIUM, 
            SensitiveDataDetector.getSensitivityLevel("BillingAddress"), 
            "Address should be MEDIUM risk");
    }

    @Test
    void testGetSensitivityLevel_Low() {
        assertEquals(SensitivityLevel.LOW, 
            SensitiveDataDetector.getSensitivityLevel("FirstName"), 
            "First name should be LOW risk");
        assertEquals(SensitivityLevel.LOW, 
            SensitiveDataDetector.getSensitivityLevel("Company"), 
            "Company should be LOW risk");
    }

    @Test
    void testGetSensitivityLevel_NotSensitive() {
        assertNull(SensitiveDataDetector.getSensitivityLevel("Id"), 
            "Non-sensitive field should return null");
        assertNull(SensitiveDataDetector.getSensitivityLevel("Status"), 
            "Non-sensitive field should return null");
    }

    @Test
    void testScanFields_NoSensitiveData() {
        SensitiveDataReport report = new SensitiveDataReport();
        List<String> fields = Arrays.asList("Id", "CreatedDate", "Status__c");

        SensitiveDataDetector.scanFields("Account", fields, report);

        assertEquals(3, report.getTotalFieldsScanned(), "Should scan all 3 fields");
        assertEquals(0, report.getSensitiveFieldsFound(), "Should find 0 sensitive fields");
        assertFalse(report.hasSensitiveData(), "Should not have sensitive data");
    }

    @Test
    void testScanFields_WithSensitiveData() {
        SensitiveDataReport report = new SensitiveDataReport();
        List<String> fields = Arrays.asList("Id", "Email", "Phone", "SSN__c");

        SensitiveDataDetector.scanFields("Contact", fields, report);

        assertEquals(4, report.getTotalFieldsScanned(), "Should scan all 4 fields");
        assertEquals(3, report.getSensitiveFieldsFound(), "Should find 3 sensitive fields");
        assertTrue(report.hasSensitiveData(), "Should have sensitive data");
    }

    @Test
    void testScanFields_MultipleObjects() {
        SensitiveDataReport report = new SensitiveDataReport();

        List<String> accountFields = Arrays.asList("Id", "BillingStreet", "Phone");
        SensitiveDataDetector.scanFields("Account", accountFields, report);

        List<String> contactFields = Arrays.asList("Id", "Email", "FirstName", "LastName");
        SensitiveDataDetector.scanFields("Contact", contactFields, report);

        assertEquals(7, report.getTotalFieldsScanned(), "Should scan all 7 fields");
        assertEquals(5, report.getSensitiveFieldsFound(), "Should find 5 sensitive fields");

        // Check grouping by object
        assertEquals(2, report.getSensitiveFieldsByObject().size(), 
            "Should have 2 objects with sensitive data");
        assertTrue(report.getSensitiveFieldsByObject().containsKey("Account"), 
            "Should include Account");
        assertTrue(report.getSensitiveFieldsByObject().containsKey("Contact"), 
            "Should include Contact");
    }

    @Test
    void testSensitiveDataReport_RiskCounts() {
        SensitiveDataReport report = new SensitiveDataReport();
        List<String> fields = Arrays.asList(
            "SSN__c",           // HIGH
            "Password",         // HIGH
            "Email",            // MEDIUM
            "Phone",            // MEDIUM
            "FirstName",        // LOW
            "LastName"          // LOW
        );

        SensitiveDataDetector.scanFields("Contact", fields, report);

        assertEquals(2, report.getHighRiskCount(), "Should have 2 HIGH risk fields");
        assertEquals(2, report.getMediumRiskCount(), "Should have 2 MEDIUM risk fields");
        assertEquals(2, report.getLowRiskCount(), "Should have 2 LOW risk fields");
    }

    @Test
    void testSensitiveDataReport_EmptyFields() {
        SensitiveDataReport report = new SensitiveDataReport();
        List<String> fields = new ArrayList<>();

        SensitiveDataDetector.scanFields("Account", fields, report);

        assertEquals(0, report.getTotalFieldsScanned(), "Should scan 0 fields");
        assertEquals(0, report.getSensitiveFieldsFound(), "Should find 0 sensitive fields");
        assertFalse(report.hasSensitiveData(), "Should not have sensitive data");
    }

    @Test
    void testSensitivityLevel_Descriptions() {
        assertEquals("High Risk - Requires Special Protection", 
            SensitivityLevel.HIGH.getDescription(),
            "HIGH should have correct description");
        assertEquals("Medium Risk - Handle with Care", 
            SensitivityLevel.MEDIUM.getDescription(),
            "MEDIUM should have correct description");
        assertEquals("Low Risk - General PII", 
            SensitivityLevel.LOW.getDescription(),
            "LOW should have correct description");
    }

    @Test
    void testSensitiveField_Properties() {
        var field = new SensitiveDataDetector.SensitiveField(
            "Account", "SSN__c", "String", SensitivityLevel.HIGH, "Contains SSN"
        );

        assertEquals("Account", field.getObjectName());
        assertEquals("SSN__c", field.getFieldName());
        assertEquals("String", field.getFieldType());
        assertEquals(SensitivityLevel.HIGH, field.getLevel());
        assertEquals("Contains SSN", field.getReason());
    }

    @Test
    void testScanFields_RealWorldExample() {
        SensitiveDataReport report = new SensitiveDataReport();

        // Typical Salesforce Contact fields
        List<String> contactFields = Arrays.asList(
            "Id",
            "FirstName",
            "LastName",
            "Email",
            "Phone",
            "MobilePhone",
            "MailingStreet",
            "MailingCity",
            "MailingState",
            "MailingPostalCode",
            "Birthdate",
            "SSN__c",
            "CreatedDate",
            "LastModifiedDate"
        );

        SensitiveDataDetector.scanFields("Contact", contactFields, report);

        assertEquals(14, report.getTotalFieldsScanned(), "Should scan all 14 fields");
        assertTrue(report.getSensitiveFieldsFound() >= 10, 
            "Should find at least 10 sensitive fields");
        assertTrue(report.getHighRiskCount() >= 1, 
            "Should have at least 1 HIGH risk field (SSN)");
        assertTrue(report.getMediumRiskCount() >= 6, 
            "Should have at least 6 MEDIUM risk fields (email, phone, address)");
        assertTrue(report.getLowRiskCount() >= 2, 
            "Should have at least 2 LOW risk fields (names)");
    }
}
