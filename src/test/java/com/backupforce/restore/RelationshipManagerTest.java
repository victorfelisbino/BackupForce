package com.backupforce.restore;

import com.backupforce.restore.RelationshipManager.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for RelationshipManager.
 * Tests the inner data classes and file operations.
 * HTTP-dependent methods are tested in integration tests.
 */
@DisplayName("RelationshipManager Tests")
class RelationshipManagerTest {

    @TempDir
    Path tempDir;

    // ==================== ObjectMetadata Tests ====================

    @Nested
    @DisplayName("ObjectMetadata Tests")
    class ObjectMetadataTests {

        @Test
        @DisplayName("ObjectMetadata stores object name correctly")
        void testObjectName() {
            ObjectMetadata metadata = new ObjectMetadata("Account");
            assertEquals("Account", metadata.getObjectName());
        }

        @Test
        @DisplayName("ObjectMetadata stores fields correctly")
        void testAddFields() {
            ObjectMetadata metadata = new ObjectMetadata("Account");
            
            FieldInfo field1 = new FieldInfo("Name", "string", "Account Name");
            FieldInfo field2 = new FieldInfo("Industry", "picklist", "Industry");
            
            metadata.addField(field1);
            metadata.addField(field2);
            
            assertEquals(2, metadata.getFields().size());
            assertEquals("Name", metadata.getFields().get(0).getName());
            assertEquals("Industry", metadata.getFields().get(1).getName());
        }

        @Test
        @DisplayName("ObjectMetadata stores external ID fields correctly")
        void testExternalIdFields() {
            ObjectMetadata metadata = new ObjectMetadata("Account");
            
            FieldInfo extId1 = new FieldInfo("External_ID__c", "string", "External ID");
            extId1.setExternalId(true);
            
            FieldInfo extId2 = new FieldInfo("Legacy_ID__c", "string", "Legacy ID");
            extId2.setExternalId(true);
            
            metadata.addExternalIdField(extId1);
            metadata.addExternalIdField(extId2);
            
            assertEquals(2, metadata.getExternalIdFields().size());
            assertTrue(metadata.getExternalIdFields().get(0).isExternalId());
        }

        @Test
        @DisplayName("ObjectMetadata stores unique fields correctly")
        void testUniqueFields() {
            ObjectMetadata metadata = new ObjectMetadata("Account");
            
            FieldInfo unique1 = new FieldInfo("Account_Number__c", "string", "Account Number");
            unique1.setUnique(true);
            
            metadata.addUniqueField(unique1);
            
            assertEquals(1, metadata.getUniqueFields().size());
            assertTrue(metadata.getUniqueFields().get(0).isUnique());
        }

        @Test
        @DisplayName("ObjectMetadata stores relationship fields correctly")
        void testRelationshipFields() {
            ObjectMetadata metadata = new ObjectMetadata("Contact");
            
            FieldInfo accountIdField = new FieldInfo("AccountId", "reference", "Account ID");
            RelationshipField relField = new RelationshipField(
                accountIdField,
                Arrays.asList("Account"),
                "Account"
            );
            
            metadata.addRelationshipField(relField);
            
            assertEquals(1, metadata.getRelationshipFields().size());
            assertEquals("AccountId", metadata.getRelationshipFields().get(0).getFieldInfo().getName());
        }

        @Test
        @DisplayName("ObjectMetadata stores name field correctly")
        void testNameField() {
            ObjectMetadata metadata = new ObjectMetadata("Account");
            
            FieldInfo nameField = new FieldInfo("Name", "string", "Account Name");
            nameField.setNameField(true);
            
            metadata.setNameField(nameField);
            
            assertNotNull(metadata.getNameField());
            assertEquals("Name", metadata.getNameField().getName());
            assertTrue(metadata.getNameField().isNameField());
        }
    }

    // ==================== FieldInfo Tests ====================

    @Nested
    @DisplayName("FieldInfo Tests")
    class FieldInfoTests {

        @Test
        @DisplayName("FieldInfo stores basic properties correctly")
        void testBasicProperties() {
            FieldInfo field = new FieldInfo("Industry", "picklist", "Industry Type");
            
            assertEquals("Industry", field.getName());
            assertEquals("picklist", field.getType());
            assertEquals("Industry Type", field.getLabel());
        }

        @Test
        @DisplayName("FieldInfo stores all boolean flags correctly")
        void testBooleanFlags() {
            FieldInfo field = new FieldInfo("External_ID__c", "string", "External ID");
            
            // Set all flags
            field.setExternalId(true);
            field.setIdLookup(true);
            field.setUnique(true);
            field.setNameField(false);
            field.setRequired(true);
            field.setNillable(false);
            field.setCreateable(true);
            
            assertTrue(field.isExternalId());
            assertTrue(field.isIdLookup());
            assertTrue(field.isUnique());
            assertFalse(field.isNameField());
            assertTrue(field.isRequired());
            assertFalse(field.isNillable());
            assertTrue(field.isCreateable());
        }

        @Test
        @DisplayName("FieldInfo stores length correctly")
        void testLength() {
            FieldInfo field = new FieldInfo("Description", "textarea", "Description");
            field.setLength(32000);
            
            assertEquals(32000, field.getLength());
        }

        @Test
        @DisplayName("FieldInfo defaults to false for boolean flags")
        void testDefaultBooleanValues() {
            FieldInfo field = new FieldInfo("TestField", "string", "Test");
            
            assertFalse(field.isExternalId());
            assertFalse(field.isIdLookup());
            assertFalse(field.isUnique());
            assertFalse(field.isNameField());
            assertFalse(field.isRequired());
            assertFalse(field.isNillable());
            assertFalse(field.isCreateable());
            assertEquals(0, field.getLength());
        }
    }

    // ==================== RelationshipField Tests ====================

    @Nested
    @DisplayName("RelationshipField Tests")
    class RelationshipFieldTests {

        @Test
        @DisplayName("RelationshipField stores single reference correctly")
        void testSingleReference() {
            FieldInfo field = new FieldInfo("AccountId", "reference", "Account ID");
            RelationshipField relField = new RelationshipField(
                field,
                Arrays.asList("Account"),
                "Account"
            );
            
            assertEquals("AccountId", relField.getFieldInfo().getName());
            assertEquals(1, relField.getReferenceTo().size());
            assertEquals("Account", relField.getReferenceTo().get(0));
            assertEquals("Account", relField.getRelationshipName());
            assertFalse(relField.isPolymorphic());
        }

        @Test
        @DisplayName("RelationshipField identifies polymorphic lookup correctly")
        void testPolymorphicLookup() {
            FieldInfo field = new FieldInfo("WhatId", "reference", "Related To");
            RelationshipField relField = new RelationshipField(
                field,
                Arrays.asList("Account", "Opportunity", "Campaign"),
                "What"
            );
            
            assertEquals(3, relField.getReferenceTo().size());
            assertTrue(relField.isPolymorphic());
        }

        @Test
        @DisplayName("RelationshipField handles null relationship name")
        void testNullRelationshipName() {
            FieldInfo field = new FieldInfo("CustomLookup__c", "reference", "Custom Lookup");
            RelationshipField relField = new RelationshipField(
                field,
                Arrays.asList("Custom_Object__c"),
                null
            );
            
            assertNull(relField.getRelationshipName());
        }
    }

    // ==================== BackupRelationshipConfig Tests ====================

    @Nested
    @DisplayName("BackupRelationshipConfig Tests")
    class BackupRelationshipConfigTests {

        @Test
        @DisplayName("BackupRelationshipConfig stores object configs correctly")
        void testObjectConfigs() {
            BackupRelationshipConfig config = new BackupRelationshipConfig();
            
            ObjectBackupConfig accountConfig = new ObjectBackupConfig("Account");
            ObjectBackupConfig contactConfig = new ObjectBackupConfig("Contact");
            
            config.addObjectConfig(accountConfig);
            config.addObjectConfig(contactConfig);
            
            assertEquals(2, config.getObjects().size());
            assertNotNull(config.getObjectConfig("Account"));
            assertNotNull(config.getObjectConfig("Contact"));
            assertNull(config.getObjectConfig("NonExistent"));
        }

        @Test
        @DisplayName("BackupRelationshipConfig stores metadata correctly")
        void testMetadata() {
            BackupRelationshipConfig config = new BackupRelationshipConfig();
            
            config.setBackupDate("2025-12-18T10:00:00Z");
            config.setSourceOrg("Production");
            
            assertEquals("2025-12-18T10:00:00Z", config.getBackupDate());
            assertEquals("Production", config.getSourceOrg());
        }
    }

    // ==================== ObjectBackupConfig Tests ====================

    @Nested
    @DisplayName("ObjectBackupConfig Tests")
    class ObjectBackupConfigTests {

        @Test
        @DisplayName("ObjectBackupConfig stores object name correctly")
        void testObjectName() {
            ObjectBackupConfig config = new ObjectBackupConfig("Account");
            assertEquals("Account", config.getObjectName());
        }

        @Test
        @DisplayName("ObjectBackupConfig stores external key strategy correctly")
        void testExternalKeyStrategy() {
            ObjectBackupConfig config = new ObjectBackupConfig("Account");
            
            ExternalKeyStrategy strategy = new ExternalKeyStrategy("Account");
            strategy.setPrimaryKeyField("External_ID__c");
            strategy.setKeyType(ExternalKeyType.EXTERNAL_ID);
            strategy.setSupportsUpsert(true);
            
            config.setExternalKeyStrategy(strategy);
            
            assertNotNull(config.getExternalKeyStrategy());
            assertEquals("External_ID__c", config.getExternalKeyStrategy().getPrimaryKeyField());
        }

        @Test
        @DisplayName("ObjectBackupConfig stores relationship mappings correctly")
        void testRelationshipMappings() {
            ObjectBackupConfig config = new ObjectBackupConfig("Contact");
            
            RelationshipMapping mapping1 = new RelationshipMapping(
                "AccountId",
                Arrays.asList("Account"),
                "Account"
            );
            RelationshipMapping mapping2 = new RelationshipMapping(
                "ReportsToId",
                Arrays.asList("Contact"),
                "ReportsTo"
            );
            
            config.addRelationshipMapping(mapping1);
            config.addRelationshipMapping(mapping2);
            
            assertEquals(2, config.getRelationshipMappings().size());
        }
    }

    // ==================== RelationshipMapping Tests ====================

    @Nested
    @DisplayName("RelationshipMapping Tests")
    class RelationshipMappingTests {

        @Test
        @DisplayName("RelationshipMapping stores all properties correctly")
        void testProperties() {
            RelationshipMapping mapping = new RelationshipMapping(
                "AccountId",
                Arrays.asList("Account"),
                "Account"
            );
            
            assertEquals("AccountId", mapping.getFieldName());
            assertEquals(1, mapping.getReferenceTo().size());
            assertEquals("Account", mapping.getReferenceTo().get(0));
            assertEquals("Account", mapping.getRelationshipName());
        }

        @Test
        @DisplayName("RelationshipMapping stores external ID field correctly")
        void testExternalIdField() {
            RelationshipMapping mapping = new RelationshipMapping(
                "AccountId",
                Arrays.asList("Account"),
                "Account"
            );
            
            assertNull(mapping.getExternalIdField());
            
            mapping.setExternalIdField("Account_External_ID__c");
            assertEquals("Account_External_ID__c", mapping.getExternalIdField());
        }
    }

    // ==================== ExternalKeyStrategy Tests ====================

    @Nested
    @DisplayName("ExternalKeyStrategy Tests")
    class ExternalKeyStrategyTests {

        @Test
        @DisplayName("ExternalKeyStrategy with External ID type")
        void testExternalIdStrategy() {
            ExternalKeyStrategy strategy = new ExternalKeyStrategy("Account");
            strategy.setPrimaryKeyField("External_ID__c");
            strategy.setKeyType(ExternalKeyType.EXTERNAL_ID);
            strategy.setSupportsUpsert(true);
            
            assertEquals("Account", strategy.getObjectName());
            assertEquals("External_ID__c", strategy.getPrimaryKeyField());
            assertEquals(ExternalKeyType.EXTERNAL_ID, strategy.getKeyType());
            assertTrue(strategy.isSupportsUpsert());
        }

        @Test
        @DisplayName("ExternalKeyStrategy with Unique Field type")
        void testUniqueFieldStrategy() {
            ExternalKeyStrategy strategy = new ExternalKeyStrategy("Account");
            strategy.setPrimaryKeyField("Account_Number__c");
            strategy.setKeyType(ExternalKeyType.UNIQUE_FIELD);
            strategy.setSupportsUpsert(false);
            
            assertEquals(ExternalKeyType.UNIQUE_FIELD, strategy.getKeyType());
            assertFalse(strategy.isSupportsUpsert());
        }

        @Test
        @DisplayName("ExternalKeyStrategy with composite key fields")
        void testCompositeKeyFields() {
            ExternalKeyStrategy strategy = new ExternalKeyStrategy("CustomObject__c");
            strategy.setPrimaryKeyField("Name");
            strategy.setKeyType(ExternalKeyType.NAME_BASED);
            strategy.addCompositeKeyField("Name");
            strategy.addCompositeKeyField("Account__c");
            strategy.addCompositeKeyField("Type__c");
            
            assertEquals(3, strategy.getCompositeKeyFields().size());
            assertTrue(strategy.getCompositeKeyFields().contains("Name"));
            assertTrue(strategy.getCompositeKeyFields().contains("Account__c"));
        }

        @Test
        @DisplayName("ExternalKeyType enum values exist")
        void testExternalKeyTypeEnum() {
            assertEquals(4, ExternalKeyType.values().length);
            assertNotNull(ExternalKeyType.EXTERNAL_ID);
            assertNotNull(ExternalKeyType.UNIQUE_FIELD);
            assertNotNull(ExternalKeyType.NAME_BASED);
            assertNotNull(ExternalKeyType.SALESFORCE_ID);
        }
    }

    // ==================== File Operations Tests ====================

    @Nested
    @DisplayName("File Operations Tests")
    class FileOperationsTests {

        @Test
        @DisplayName("saveRelationshipMetadata creates valid JSON file")
        void testSaveRelationshipMetadata() throws IOException {
            // Create a config
            BackupRelationshipConfig config = new BackupRelationshipConfig();
            config.setBackupDate("2025-12-18T10:00:00Z");
            config.setSourceOrg("TestOrg");
            
            ObjectBackupConfig objConfig = new ObjectBackupConfig("Account");
            ExternalKeyStrategy strategy = new ExternalKeyStrategy("Account");
            strategy.setPrimaryKeyField("External_ID__c");
            strategy.setKeyType(ExternalKeyType.EXTERNAL_ID);
            objConfig.setExternalKeyStrategy(strategy);
            
            RelationshipMapping mapping = new RelationshipMapping(
                "ParentId", Arrays.asList("Account"), "Parent"
            );
            objConfig.addRelationshipMapping(mapping);
            
            config.addObjectConfig(objConfig);
            
            // Use the manager to save (need dummy credentials for constructor)
            RelationshipManager manager = new RelationshipManager("https://test.salesforce.com", "dummy", "60.0");
            try {
                manager.saveRelationshipMetadata(tempDir.toString(), config);
                
                // Verify file exists
                Path metadataPath = tempDir.resolve("_relationship_metadata.json");
                assertTrue(Files.exists(metadataPath));
                
                // Verify content
                String content = Files.readString(metadataPath);
                assertTrue(content.contains("Account"));
                assertTrue(content.contains("External_ID__c"));
                assertTrue(content.contains("ParentId"));
                assertTrue(content.contains("2025-12-18T10:00:00Z"));
            } finally {
                manager.close();
            }
        }

        @Test
        @DisplayName("loadRelationshipMetadata reads valid JSON file")
        void testLoadRelationshipMetadata() throws IOException {
            // Create a valid JSON file
            String json = "{\n" +
                "  \"objects\": {\n" +
                "    \"Account\": {\n" +
                "      \"objectName\": \"Account\",\n" +
                "      \"externalKeyStrategy\": {\n" +
                "        \"objectName\": \"Account\",\n" +
                "        \"primaryKeyField\": \"External_ID__c\",\n" +
                "        \"keyType\": \"EXTERNAL_ID\",\n" +
                "        \"supportsUpsert\": true,\n" +
                "        \"compositeKeyFields\": []\n" +
                "      },\n" +
                "      \"relationshipMappings\": []\n" +
                "    }\n" +
                "  },\n" +
                "  \"backupDate\": \"2025-12-18\",\n" +
                "  \"sourceOrg\": \"Production\"\n" +
                "}";
            
            Path metadataPath = tempDir.resolve("_relationship_metadata.json");
            Files.writeString(metadataPath, json);
            
            RelationshipManager manager = new RelationshipManager("https://test.salesforce.com", "dummy", "60.0");
            try {
                BackupRelationshipConfig config = manager.loadRelationshipMetadata(tempDir.toString());
                
                assertNotNull(config);
                assertEquals("2025-12-18", config.getBackupDate());
                assertEquals("Production", config.getSourceOrg());
                assertNotNull(config.getObjectConfig("Account"));
            } finally {
                manager.close();
            }
        }

        @Test
        @DisplayName("loadRelationshipMetadata returns null when file doesn't exist")
        void testLoadNonExistentFile() throws IOException {
            RelationshipManager manager = new RelationshipManager("https://test.salesforce.com", "dummy", "60.0");
            try {
                BackupRelationshipConfig config = manager.loadRelationshipMetadata(tempDir.toString());
                assertNull(config);
            } finally {
                manager.close();
            }
        }
    }

    // ==================== getAdditionalBackupColumns Tests ====================

    @Nested
    @DisplayName("getAdditionalBackupColumns Tests")
    class AdditionalBackupColumnsTests {

        @Test
        @DisplayName("getAdditionalBackupColumns returns correct columns for relationship fields")
        void testAdditionalColumns() {
            ObjectMetadata metadata = new ObjectMetadata("Contact");
            
            // Add relationship fields
            FieldInfo accountField = new FieldInfo("AccountId", "reference", "Account ID");
            RelationshipField accountRel = new RelationshipField(
                accountField,
                Arrays.asList("Account"),
                "Account"
            );
            
            FieldInfo reportsToField = new FieldInfo("ReportsToId", "reference", "Reports To");
            RelationshipField reportsToRel = new RelationshipField(
                reportsToField,
                Arrays.asList("Contact"),
                "ReportsTo"
            );
            
            metadata.addRelationshipField(accountRel);
            metadata.addRelationshipField(reportsToRel);
            
            RelationshipManager manager = new RelationshipManager("https://test.salesforce.com", "dummy", "60.0");
            try {
                List<String> additionalColumns = manager.getAdditionalBackupColumns(metadata);
                
                assertEquals(4, additionalColumns.size());
                assertTrue(additionalColumns.contains("_rel_Account_ExternalId"));
                assertTrue(additionalColumns.contains("_rel_Account_Name"));
                assertTrue(additionalColumns.contains("_rel_ReportsTo_ExternalId"));
                assertTrue(additionalColumns.contains("_rel_ReportsTo_Name"));
            } finally {
                manager.close();
            }
        }

        @Test
        @DisplayName("getAdditionalBackupColumns handles null relationship name")
        void testNullRelationshipName() {
            ObjectMetadata metadata = new ObjectMetadata("Custom__c");
            
            FieldInfo lookupField = new FieldInfo("CustomLookup__c", "reference", "Custom Lookup");
            RelationshipField relField = new RelationshipField(
                lookupField,
                Arrays.asList("Other__c"),
                null
            );
            
            metadata.addRelationshipField(relField);
            
            RelationshipManager manager = new RelationshipManager("https://test.salesforce.com", "dummy", "60.0");
            try {
                List<String> additionalColumns = manager.getAdditionalBackupColumns(metadata);
                
                assertEquals(2, additionalColumns.size());
                // When relationshipName is null, uses field name minus "Id"
                assertTrue(additionalColumns.contains("_rel_CustomLookup__c_ExternalId"));
                assertTrue(additionalColumns.contains("_rel_CustomLookup__c_Name"));
            } finally {
                manager.close();
            }
        }

        @Test
        @DisplayName("getAdditionalBackupColumns returns empty list for no relationships")
        void testNoRelationships() {
            ObjectMetadata metadata = new ObjectMetadata("Task");
            // No relationship fields added
            
            RelationshipManager manager = new RelationshipManager("https://test.salesforce.com", "dummy", "60.0");
            try {
                List<String> additionalColumns = manager.getAdditionalBackupColumns(metadata);
                assertTrue(additionalColumns.isEmpty());
            } finally {
                manager.close();
            }
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("ObjectMetadata with empty collections")
        void testEmptyCollections() {
            ObjectMetadata metadata = new ObjectMetadata("Empty");
            
            assertTrue(metadata.getFields().isEmpty());
            assertTrue(metadata.getExternalIdFields().isEmpty());
            assertTrue(metadata.getUniqueFields().isEmpty());
            assertTrue(metadata.getRelationshipFields().isEmpty());
            assertNull(metadata.getNameField());
        }

        @Test
        @DisplayName("BackupRelationshipConfig with no objects")
        void testEmptyConfig() {
            BackupRelationshipConfig config = new BackupRelationshipConfig();
            
            assertTrue(config.getObjects().isEmpty());
            assertNull(config.getObjectConfig("Any"));
        }

        @Test
        @DisplayName("RelationshipManager close() doesn't throw")
        void testClose() {
            RelationshipManager manager = new RelationshipManager("https://test.salesforce.com", "dummy", "60.0");
            assertDoesNotThrow(() -> manager.close());
        }
    }
}
