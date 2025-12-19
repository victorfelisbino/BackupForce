package com.backupforce.restore;

import com.backupforce.restore.SchemaComparer.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for SchemaComparer.
 * Tests the static analysis methods and inner data classes.
 * HTTP-dependent methods are tested in integration tests.
 */
@DisplayName("SchemaComparer Tests")
@ExtendWith(MockitoExtension.class)
class SchemaComparerTest {

    // ==================== BackupDataAnalysis Tests ====================

    @Nested
    @DisplayName("BackupDataAnalysis Tests")
    class BackupDataAnalysisTests {

        @Test
        @DisplayName("analyzeBackupData extracts RecordType IDs correctly")
        void testAnalyzeBackupDataRecordTypes() {
            List<Map<String, String>> records = new ArrayList<>();
            
            Map<String, String> record1 = new HashMap<>();
            record1.put("Id", "001xxx001");
            record1.put("Name", "Test Account 1");
            record1.put("RecordTypeId", "012000000000001AAA");
            records.add(record1);
            
            Map<String, String> record2 = new HashMap<>();
            record2.put("Id", "001xxx002");
            record2.put("Name", "Test Account 2");
            record2.put("RecordTypeId", "012000000000002BBB");
            records.add(record2);
            
            Map<String, String> record3 = new HashMap<>();
            record3.put("Id", "001xxx003");
            record3.put("Name", "Test Account 3");
            record3.put("RecordTypeId", "012000000000001AAA"); // Duplicate
            records.add(record3);
            
            BackupDataAnalysis analysis = SchemaComparer.analyzeBackupData(
                "Account", records, Set.of(), true
            );
            
            assertEquals("Account", analysis.getObjectName());
            assertEquals(2, analysis.getRecordTypeIds().size());
            assertTrue(analysis.getRecordTypeIds().contains("012000000000001AAA"));
            assertTrue(analysis.getRecordTypeIds().contains("012000000000002BBB"));
        }

        @Test
        @DisplayName("analyzeBackupData extracts picklist values correctly")
        void testAnalyzeBackupDataPicklists() {
            List<Map<String, String>> records = new ArrayList<>();
            
            Map<String, String> record1 = new HashMap<>();
            record1.put("Id", "001xxx001");
            record1.put("Industry", "Technology");
            record1.put("Type", "Prospect");
            records.add(record1);
            
            Map<String, String> record2 = new HashMap<>();
            record2.put("Id", "001xxx002");
            record2.put("Industry", "Finance");
            record2.put("Type", "Customer");
            records.add(record2);
            
            Map<String, String> record3 = new HashMap<>();
            record3.put("Id", "001xxx003");
            record3.put("Industry", "Technology"); // Duplicate
            record3.put("Type", "Partner");
            records.add(record3);
            
            BackupDataAnalysis analysis = SchemaComparer.analyzeBackupData(
                "Account", records, Set.of("Industry", "Type"), false
            );
            
            Map<String, Set<String>> picklistValues = analysis.getPicklistValues();
            
            assertEquals(2, picklistValues.size());
            assertTrue(picklistValues.containsKey("Industry"));
            assertTrue(picklistValues.containsKey("Type"));
            
            assertEquals(2, picklistValues.get("Industry").size());
            assertTrue(picklistValues.get("Industry").contains("Technology"));
            assertTrue(picklistValues.get("Industry").contains("Finance"));
            
            assertEquals(3, picklistValues.get("Type").size());
            assertTrue(picklistValues.get("Type").contains("Prospect"));
            assertTrue(picklistValues.get("Type").contains("Customer"));
            assertTrue(picklistValues.get("Type").contains("Partner"));
        }

        @Test
        @DisplayName("analyzeBackupData extracts User IDs correctly")
        void testAnalyzeBackupDataUserIds() {
            List<Map<String, String>> records = new ArrayList<>();
            
            Map<String, String> record1 = new HashMap<>();
            record1.put("Id", "001xxx001");
            record1.put("OwnerId", "005xxx000000001AAA");
            record1.put("CreatedById", "005xxx000000002BBB");
            record1.put("LastModifiedById", "005xxx000000001AAA");
            records.add(record1);
            
            Map<String, String> record2 = new HashMap<>();
            record2.put("Id", "001xxx002");
            record2.put("OwnerId", "005xxx000000003CCC");
            record2.put("CreatedById", "005xxx000000001AAA");
            records.add(record2);
            
            BackupDataAnalysis analysis = SchemaComparer.analyzeBackupData(
                "Account", records, Set.of(), false
            );
            
            Set<String> userIds = analysis.getUserIds();
            
            assertEquals(3, userIds.size());
            assertTrue(userIds.contains("005xxx000000001AAA"));
            assertTrue(userIds.contains("005xxx000000002BBB"));
            assertTrue(userIds.contains("005xxx000000003CCC"));
        }

        @Test
        @DisplayName("analyzeBackupData ignores null and empty values")
        void testAnalyzeBackupDataIgnoresNullEmpty() {
            List<Map<String, String>> records = new ArrayList<>();
            
            Map<String, String> record1 = new HashMap<>();
            record1.put("Id", "001xxx001");
            record1.put("RecordTypeId", null);
            record1.put("Industry", "");
            record1.put("OwnerId", "");
            records.add(record1);
            
            Map<String, String> record2 = new HashMap<>();
            record2.put("Id", "001xxx002");
            record2.put("RecordTypeId", "");
            record2.put("Industry", null);
            record2.put("OwnerId", null);
            records.add(record2);
            
            BackupDataAnalysis analysis = SchemaComparer.analyzeBackupData(
                "Account", records, Set.of("Industry"), true
            );
            
            assertTrue(analysis.getRecordTypeIds().isEmpty());
            assertTrue(analysis.getPicklistValues().isEmpty());
            assertTrue(analysis.getUserIds().isEmpty());
        }

        @Test
        @DisplayName("analyzeBackupData only collects User IDs starting with 005")
        void testAnalyzeBackupDataUserIdPrefix() {
            List<Map<String, String>> records = new ArrayList<>();
            
            Map<String, String> record1 = new HashMap<>();
            record1.put("Id", "001xxx001");
            record1.put("OwnerId", "005xxx000000001AAA"); // Valid User ID
            record1.put("CreatedById", "00Gxxx000000001AAA"); // Queue ID, not a user
            records.add(record1);
            
            BackupDataAnalysis analysis = SchemaComparer.analyzeBackupData(
                "Case", records, Set.of(), false
            );
            
            Set<String> userIds = analysis.getUserIds();
            
            assertEquals(1, userIds.size());
            assertTrue(userIds.contains("005xxx000000001AAA"));
            assertFalse(userIds.contains("00Gxxx000000001AAA"));
        }

        @Test
        @DisplayName("analyzeBackupData with empty records list")
        void testAnalyzeBackupDataEmptyRecords() {
            List<Map<String, String>> records = new ArrayList<>();
            
            BackupDataAnalysis analysis = SchemaComparer.analyzeBackupData(
                "Account", records, Set.of("Industry", "Type"), true
            );
            
            assertEquals("Account", analysis.getObjectName());
            assertTrue(analysis.getRecordTypeIds().isEmpty());
            assertTrue(analysis.getPicklistValues().isEmpty());
            assertTrue(analysis.getUserIds().isEmpty());
        }
    }

    // ==================== ObjectMetadata Tests ====================

    @Nested
    @DisplayName("ObjectMetadata Tests")
    class ObjectMetadataTests {

        @Test
        @DisplayName("ObjectMetadata stores and retrieves fields correctly")
        void testObjectMetadataFields() {
            ObjectMetadata metadata = new ObjectMetadata("Account");
            
            FieldMetadata nameField = new FieldMetadata("Name", "string", true, true);
            FieldMetadata industryField = new FieldMetadata("Industry", "picklist", true, true);
            FieldMetadata idField = new FieldMetadata("Id", "id", false, false);
            
            metadata.addField(nameField);
            metadata.addField(industryField);
            metadata.addField(idField);
            
            assertEquals("Account", metadata.getObjectName());
            assertEquals(3, metadata.getFieldNames().size());
            
            assertNotNull(metadata.getField("Name"));
            assertEquals("string", metadata.getField("Name").getType());
            
            assertNotNull(metadata.getField("Industry"));
            assertTrue(metadata.getField("Industry").isCreateable());
            
            assertNotNull(metadata.getField("Id"));
            assertFalse(metadata.getField("Id").isCreateable());
            
            assertNull(metadata.getField("NonExistent"));
        }

        @Test
        @DisplayName("ObjectMetadata stores RecordTypes correctly")
        void testObjectMetadataRecordTypes() {
            ObjectMetadata metadata = new ObjectMetadata("Account");
            
            RecordTypeInfo rt1 = new RecordTypeInfo("012xxx001", "Business", "Business_Account", true);
            RecordTypeInfo rt2 = new RecordTypeInfo("012xxx002", "Personal", "Personal_Account", false);
            
            metadata.addRecordType(rt1);
            metadata.addRecordType(rt2);
            
            List<RecordTypeInfo> recordTypes = metadata.getRecordTypes();
            assertEquals(2, recordTypes.size());
        }
    }

    // ==================== FieldMetadata Tests ====================

    @Nested
    @DisplayName("FieldMetadata Tests")
    class FieldMetadataTests {

        @Test
        @DisplayName("FieldMetadata stores picklist values correctly")
        void testFieldMetadataPicklistValues() {
            FieldMetadata field = new FieldMetadata("Status", "picklist", true, true);
            
            field.addPicklistValue("New", "New Lead");
            field.addPicklistValue("Working", "Working - Contacted");
            field.addPicklistValue("Closed", "Closed - Converted");
            
            Set<String> values = field.getPicklistValues();
            assertEquals(3, values.size());
            assertTrue(values.contains("New"));
            assertTrue(values.contains("Working"));
            assertTrue(values.contains("Closed"));
            
            Map<String, String> valuesWithLabels = field.getPicklistValuesWithLabels();
            assertEquals("New Lead", valuesWithLabels.get("New"));
            assertEquals("Working - Contacted", valuesWithLabels.get("Working"));
        }

        @Test
        @DisplayName("FieldMetadata getters return correct values")
        void testFieldMetadataGetters() {
            FieldMetadata field = new FieldMetadata("Amount", "currency", true, true);
            
            assertEquals("Amount", field.getName());
            assertEquals("currency", field.getType());
            assertTrue(field.isCreateable());
            assertTrue(field.isUpdateable());
            
            FieldMetadata readOnlyField = new FieldMetadata("CreatedDate", "datetime", false, false);
            assertFalse(readOnlyField.isCreateable());
            assertFalse(readOnlyField.isUpdateable());
        }
    }

    // ==================== ObjectComparisonResult Tests ====================

    @Nested
    @DisplayName("ObjectComparisonResult Tests")
    class ObjectComparisonResultTests {

        @Test
        @DisplayName("ObjectComparisonResult tracks missing fields")
        void testMissingFields() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            result.addMissingField("Custom_Field__c");
            result.addMissingField("Another_Field__c");
            
            assertEquals(2, result.getMissingFields().size());
            assertTrue(result.getMissingFields().contains("Custom_Field__c"));
            assertTrue(result.hasMismatches());
        }

        @Test
        @DisplayName("ObjectComparisonResult tracks non-createable fields")
        void testNonCreateableFields() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            result.addNonCreateableField("CreatedDate");
            result.addNonCreateableField("LastModifiedDate");
            
            assertEquals(2, result.getNonCreateableFields().size());
        }

        @Test
        @DisplayName("ObjectComparisonResult tracks picklist mismatches")
        void testPicklistMismatches() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            Set<String> targetOptions = Set.of("Option1", "Option2", "Option3");
            result.addPicklistMismatch("Status", "OldStatus", targetOptions);
            result.addPicklistMismatch("Industry", "ObsoleteIndustry", Set.of("Tech", "Finance"));
            
            assertEquals(2, result.getPicklistMismatches().size());
            
            PicklistMismatch first = result.getPicklistMismatches().get(0);
            assertEquals("Status", first.getFieldName());
            assertEquals("OldStatus", first.getSourceValue());
            assertEquals(3, first.getTargetOptions().size());
            
            assertTrue(result.hasMismatches());
        }

        @Test
        @DisplayName("ObjectComparisonResult tracks RecordType mismatches")
        void testRecordTypeMismatches() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            List<RecordTypeInfo> targetOptions = Arrays.asList(
                new RecordTypeInfo("012xxx001", "Business", "Business_RT", true),
                new RecordTypeInfo("012xxx002", "Personal", "Personal_RT", false)
            );
            
            result.addRecordTypeMismatch("012oldId", targetOptions);
            
            assertEquals(1, result.getRecordTypeMismatches().size());
            
            RecordTypeMismatch mismatch = result.getRecordTypeMismatches().get(0);
            assertEquals("012oldId", mismatch.getSourceRecordTypeId());
            assertEquals(2, mismatch.getTargetOptions().size());
            
            assertTrue(result.hasMismatches());
        }

        @Test
        @DisplayName("ObjectComparisonResult tracks User mismatches")
        void testUserMismatches() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            List<UserInfo> targetOptions = Arrays.asList(
                new UserInfo("005xxx001", "user1@test.com", "User One", "user1@test.com", true),
                new UserInfo("005xxx002", "user2@test.com", "User Two", "user2@test.com", true)
            );
            
            result.addUserMismatch("005oldUser", targetOptions);
            
            assertEquals(1, result.getUserMismatches().size());
            
            UserMismatch mismatch = result.getUserMismatches().get(0);
            assertEquals("005oldUser", mismatch.getSourceUserId());
            assertEquals(2, mismatch.getTargetOptions().size());
            
            assertTrue(result.hasMismatches());
        }

        @Test
        @DisplayName("ObjectComparisonResult hasMismatches returns false when no issues")
        void testNoMismatches() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            assertFalse(result.hasMismatches());
            assertEquals("No mismatches found", result.getSummary());
        }

        @Test
        @DisplayName("ObjectComparisonResult getSummary formats correctly")
        void testGetSummary() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            result.addMissingField("Field1");
            result.addMissingField("Field2");
            result.addPicklistMismatch("Status", "Old", Set.of("New"));
            result.addRecordTypeMismatch("012old", Collections.emptyList());
            
            String summary = result.getSummary();
            
            assertTrue(summary.contains("2 missing fields"));
            assertTrue(summary.contains("1 picklist mismatch"));
            assertTrue(summary.contains("1 RecordType mismatch"));
        }

        @Test
        @DisplayName("ObjectComparisonResult stores target users and record types")
        void testSetTargetUsersAndRecordTypes() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            List<UserInfo> users = Arrays.asList(
                new UserInfo("005xxx001", "u1", "User 1", "u1@test.com", true)
            );
            List<RecordTypeInfo> rts = Arrays.asList(
                new RecordTypeInfo("012xxx001", "RT1", "RT1_Dev", true)
            );
            
            result.setTargetUsers(users);
            result.setTargetRecordTypes(rts);
            
            assertEquals(1, result.getTargetUsers().size());
            assertEquals(1, result.getTargetRecordTypes().size());
        }
    }

    // ==================== RecordTypeInfo Tests ====================

    @Nested
    @DisplayName("RecordTypeInfo Tests")
    class RecordTypeInfoTests {

        @Test
        @DisplayName("RecordTypeInfo stores all properties correctly")
        void testRecordTypeInfoProperties() {
            RecordTypeInfo rt = new RecordTypeInfo("012xxx001", "Business Account", "Business_Account", true);
            
            assertEquals("012xxx001", rt.getId());
            assertEquals("Business Account", rt.getName());
            assertEquals("Business_Account", rt.getDeveloperName());
            assertTrue(rt.isDefault());
        }

        @Test
        @DisplayName("RecordTypeInfo toString formats correctly")
        void testRecordTypeInfoToString() {
            RecordTypeInfo rt = new RecordTypeInfo("012xxx001", "Business Account", "Business_Account", false);
            
            String str = rt.toString();
            assertTrue(str.contains("Business Account"));
            assertTrue(str.contains("Business_Account"));
        }
    }

    // ==================== UserInfo Tests ====================

    @Nested
    @DisplayName("UserInfo Tests")
    class UserInfoTests {

        @Test
        @DisplayName("UserInfo stores all properties correctly")
        void testUserInfoProperties() {
            UserInfo user = new UserInfo("005xxx001", "john.doe@company.com", "John Doe", "john@company.com", true);
            
            assertEquals("005xxx001", user.getId());
            assertEquals("john.doe@company.com", user.getUsername());
            assertEquals("John Doe", user.getName());
            assertEquals("john@company.com", user.getEmail());
            assertTrue(user.isActive());
        }

        @Test
        @DisplayName("UserInfo toString formats correctly")
        void testUserInfoToString() {
            UserInfo user = new UserInfo("005xxx001", "john.doe@company.com", "John Doe", "john@company.com", true);
            
            String str = user.toString();
            assertTrue(str.contains("John Doe"));
            assertTrue(str.contains("john.doe@company.com"));
        }
    }

    // ==================== PicklistMismatch Tests ====================

    @Nested
    @DisplayName("PicklistMismatch Tests")
    class PicklistMismatchTests {

        @Test
        @DisplayName("PicklistMismatch stores all properties correctly")
        void testPicklistMismatchProperties() {
            List<String> options = Arrays.asList("Active", "Inactive", "Pending");
            PicklistMismatch mismatch = new PicklistMismatch("Status", "Deprecated", options);
            
            assertEquals("Status", mismatch.getFieldName());
            assertEquals("Deprecated", mismatch.getSourceValue());
            assertEquals(3, mismatch.getTargetOptions().size());
            assertTrue(mismatch.getTargetOptions().contains("Active"));
        }
    }

    // ==================== RecordTypeMismatch Tests ====================

    @Nested
    @DisplayName("RecordTypeMismatch Tests")
    class RecordTypeMismatchTests {

        @Test
        @DisplayName("RecordTypeMismatch stores all properties correctly")
        void testRecordTypeMismatchProperties() {
            List<RecordTypeInfo> options = Arrays.asList(
                new RecordTypeInfo("012new001", "New RT", "New_RT", true)
            );
            RecordTypeMismatch mismatch = new RecordTypeMismatch("012old001", options);
            
            assertEquals("012old001", mismatch.getSourceRecordTypeId());
            assertEquals(1, mismatch.getTargetOptions().size());
        }
    }

    // ==================== UserMismatch Tests ====================

    @Nested
    @DisplayName("UserMismatch Tests")
    class UserMismatchTests {

        @Test
        @DisplayName("UserMismatch stores all properties correctly")
        void testUserMismatchProperties() {
            List<UserInfo> options = Arrays.asList(
                new UserInfo("005new001", "new@test.com", "New User", "new@test.com", true)
            );
            UserMismatch mismatch = new UserMismatch("005old001", options);
            
            assertEquals("005old001", mismatch.getSourceUserId());
            assertEquals(1, mismatch.getTargetOptions().size());
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("analyzeBackupData handles mixed valid and invalid data")
        void testMixedData() {
            List<Map<String, String>> records = new ArrayList<>();
            
            // Record with all valid data
            Map<String, String> record1 = new HashMap<>();
            record1.put("Id", "001xxx001");
            record1.put("RecordTypeId", "012xxx001");
            record1.put("Status", "Active");
            record1.put("OwnerId", "005xxx001");
            records.add(record1);
            
            // Record with null values
            Map<String, String> record2 = new HashMap<>();
            record2.put("Id", "001xxx002");
            record2.put("RecordTypeId", null);
            record2.put("Status", null);
            record2.put("OwnerId", null);
            records.add(record2);
            
            // Record with empty values
            Map<String, String> record3 = new HashMap<>();
            record3.put("Id", "001xxx003");
            record3.put("RecordTypeId", "");
            record3.put("Status", "");
            record3.put("OwnerId", "");
            records.add(record3);
            
            // Record with valid data
            Map<String, String> record4 = new HashMap<>();
            record4.put("Id", "001xxx004");
            record4.put("RecordTypeId", "012xxx002");
            record4.put("Status", "Inactive");
            record4.put("OwnerId", "005xxx002");
            records.add(record4);
            
            BackupDataAnalysis analysis = SchemaComparer.analyzeBackupData(
                "Account", records, Set.of("Status"), true
            );
            
            // Should only have the valid values
            assertEquals(2, analysis.getRecordTypeIds().size());
            assertEquals(2, analysis.getPicklistValues().get("Status").size());
            assertEquals(2, analysis.getUserIds().size());
        }

        @Test
        @DisplayName("analyzeBackupData handles records missing expected fields")
        void testMissingFields() {
            List<Map<String, String>> records = new ArrayList<>();
            
            Map<String, String> record1 = new HashMap<>();
            record1.put("Id", "001xxx001");
            // Missing RecordTypeId, Status, OwnerId
            records.add(record1);
            
            BackupDataAnalysis analysis = SchemaComparer.analyzeBackupData(
                "Account", records, Set.of("Status"), true
            );
            
            assertTrue(analysis.getRecordTypeIds().isEmpty());
            assertTrue(analysis.getPicklistValues().isEmpty());
            assertTrue(analysis.getUserIds().isEmpty());
        }

        @Test
        @DisplayName("ObjectComparisonResult handles multiple types of mismatches")
        void testMultipleMismatchTypes() {
            ObjectComparisonResult result = new ObjectComparisonResult("Account");
            
            // Add all types of issues
            result.addMissingField("CustomField1");
            result.addMissingField("CustomField2");
            result.addNonCreateableField("SystemField1");
            result.addPicklistMismatch("Status", "OldValue", Set.of("New1", "New2"));
            result.addRecordTypeMismatch("012old", Collections.emptyList());
            result.addUserMismatch("005old", Collections.emptyList());
            
            assertTrue(result.hasMismatches());
            assertEquals(2, result.getMissingFields().size());
            assertEquals(1, result.getNonCreateableFields().size());
            assertEquals(1, result.getPicklistMismatches().size());
            assertEquals(1, result.getRecordTypeMismatches().size());
            assertEquals(1, result.getUserMismatches().size());
            
            String summary = result.getSummary();
            assertTrue(summary.contains("2 missing fields"));
            assertTrue(summary.contains("1 non-createable"));
            assertTrue(summary.contains("1 picklist"));
            assertTrue(summary.contains("1 RecordType"));
            assertTrue(summary.contains("1 User"));
        }
    }
}
