package com.backupforce.restore;

import com.backupforce.restore.TransformationConfig.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TransformationConfig.
 * Tests the configuration class for cross-org data transformations.
 */
@DisplayName("TransformationConfig Tests")
class TransformationConfigTest {

    @TempDir
    Path tempDir;

    // ==================== Basic Configuration Tests ====================

    @Nested
    @DisplayName("Basic Configuration Tests")
    class BasicConfigTests {

        @Test
        @DisplayName("Default constructor sets creation date")
        void testDefaultConstructor() {
            TransformationConfig config = new TransformationConfig();
            
            assertNotNull(config.getCreatedDate());
            assertNotNull(config.getLastModifiedDate());
        }

        @Test
        @DisplayName("Constructor with name sets name and dates")
        void testNameConstructor() {
            TransformationConfig config = new TransformationConfig("My Config");
            
            assertEquals("My Config", config.getName());
            assertNotNull(config.getCreatedDate());
        }

        @Test
        @DisplayName("Setters and getters work correctly")
        void testSettersAndGetters() {
            TransformationConfig config = new TransformationConfig();
            
            config.setName("Test Config");
            config.setDescription("A test configuration");
            config.setSourceOrg("Production");
            config.setTargetOrg("Sandbox");
            
            assertEquals("Test Config", config.getName());
            assertEquals("A test configuration", config.getDescription());
            assertEquals("Production", config.getSourceOrg());
            assertEquals("Sandbox", config.getTargetOrg());
        }

        @Test
        @DisplayName("Default unmapped behaviors are correct")
        void testDefaultUnmappedBehaviors() {
            TransformationConfig config = new TransformationConfig();
            
            assertEquals(UnmappedValueBehavior.KEEP_ORIGINAL, config.getDefaultUnmappedPicklistBehavior());
            assertEquals(UnmappedValueBehavior.USE_DEFAULT, config.getDefaultUnmappedRecordTypeBehavior());
            assertEquals(UnmappedValueBehavior.USE_RUNNING_USER, config.getDefaultUnmappedUserBehavior());
        }

        @Test
        @DisplayName("Unmapped behaviors can be changed")
        void testSetUnmappedBehaviors() {
            TransformationConfig config = new TransformationConfig();
            
            config.setDefaultUnmappedPicklistBehavior(UnmappedValueBehavior.SET_NULL);
            config.setDefaultUnmappedRecordTypeBehavior(UnmappedValueBehavior.FAIL);
            config.setDefaultUnmappedUserBehavior(UnmappedValueBehavior.SKIP_RECORD);
            
            assertEquals(UnmappedValueBehavior.SET_NULL, config.getDefaultUnmappedPicklistBehavior());
            assertEquals(UnmappedValueBehavior.FAIL, config.getDefaultUnmappedRecordTypeBehavior());
            assertEquals(UnmappedValueBehavior.SKIP_RECORD, config.getDefaultUnmappedUserBehavior());
        }
    }

    // ==================== User Mapping Tests ====================

    @Nested
    @DisplayName("User Mapping Tests")
    class UserMappingTests {

        @Test
        @DisplayName("addUserMapping and getMappedUser work correctly")
        void testUserMappings() {
            TransformationConfig config = new TransformationConfig();
            
            config.addUserMapping("005source001", "005target001");
            config.addUserMapping("005source002", "005target002");
            
            assertEquals("005target001", config.getMappedUser("005source001"));
            assertEquals("005target002", config.getMappedUser("005source002"));
            assertNull(config.getMappedUser("005unknown"));
        }

        @Test
        @DisplayName("getUserMappings returns all mappings")
        void testGetUserMappings() {
            TransformationConfig config = new TransformationConfig();
            
            config.addUserMapping("005source001", "005target001");
            config.addUserMapping("005source002", "005target002");
            
            Map<String, String> mappings = config.getUserMappings();
            assertEquals(2, mappings.size());
            assertTrue(mappings.containsKey("005source001"));
            assertTrue(mappings.containsKey("005source002"));
        }
    }

    // ==================== RecordType Mapping Tests ====================

    @Nested
    @DisplayName("RecordType Mapping Tests")
    class RecordTypeMappingTests {

        @Test
        @DisplayName("addRecordTypeMapping and getMappedRecordType work correctly")
        void testRecordTypeMappings() {
            TransformationConfig config = new TransformationConfig();
            
            config.addRecordTypeMapping("012source001", "012target001");
            config.addRecordTypeMapping("012source002", "012target002");
            
            assertEquals("012target001", config.getMappedRecordType("012source001"));
            assertEquals("012target002", config.getMappedRecordType("012source002"));
            assertNull(config.getMappedRecordType("012unknown"));
        }

        @Test
        @DisplayName("getRecordTypeMappings returns all mappings")
        void testGetRecordTypeMappings() {
            TransformationConfig config = new TransformationConfig();
            
            config.addRecordTypeMapping("012source001", "012target001");
            
            Map<String, String> mappings = config.getRecordTypeMappings();
            assertEquals(1, mappings.size());
        }
    }

    // ==================== Object Configuration Tests ====================

    @Nested
    @DisplayName("Object Configuration Tests")
    class ObjectConfigTests {

        @Test
        @DisplayName("getOrCreateObjectConfig creates new config if not exists")
        void testGetOrCreateObjectConfig() {
            TransformationConfig config = new TransformationConfig();
            
            ObjectTransformConfig accountConfig = config.getOrCreateObjectConfig("Account");
            assertNotNull(accountConfig);
            assertEquals("Account", accountConfig.getObjectName());
            
            // Getting again returns same instance
            ObjectTransformConfig sameConfig = config.getOrCreateObjectConfig("Account");
            assertSame(accountConfig, sameConfig);
        }

        @Test
        @DisplayName("getObjectConfig returns null if not exists")
        void testGetObjectConfigNotExists() {
            TransformationConfig config = new TransformationConfig();
            
            assertNull(config.getObjectConfig("NonExistent"));
        }

        @Test
        @DisplayName("addObjectConfig adds configuration")
        void testAddObjectConfig() {
            TransformationConfig config = new TransformationConfig();
            
            ObjectTransformConfig objConfig = new ObjectTransformConfig("Contact");
            config.addObjectConfig(objConfig);
            
            assertNotNull(config.getObjectConfig("Contact"));
            assertEquals(1, config.getObjectConfigs().size());
        }
    }

    // ==================== Persistence Tests ====================

    @Nested
    @DisplayName("Persistence Tests")
    class PersistenceTests {

        @Test
        @DisplayName("save and load roundtrip preserves data")
        void testSaveAndLoad() throws IOException {
            TransformationConfig config = new TransformationConfig("Test Config");
            config.setDescription("Test Description");
            config.setSourceOrg("Production");
            config.setTargetOrg("Sandbox");
            
            config.addUserMapping("005source001", "005target001");
            config.addRecordTypeMapping("012source001", "012target001");
            
            ObjectTransformConfig accountConfig = config.getOrCreateObjectConfig("Account");
            accountConfig.addPicklistMapping("Industry", "Tech", "Technology");
            accountConfig.excludeField("SystemField__c");
            
            // Save
            Path configPath = tempDir.resolve("config.json");
            config.save(configPath);
            
            assertTrue(Files.exists(configPath));
            
            // Load
            TransformationConfig loaded = TransformationConfig.load(configPath);
            
            assertEquals("Test Config", loaded.getName());
            assertEquals("Test Description", loaded.getDescription());
            assertEquals("Production", loaded.getSourceOrg());
            assertEquals("Sandbox", loaded.getTargetOrg());
            assertEquals("005target001", loaded.getMappedUser("005source001"));
            assertEquals("012target001", loaded.getMappedRecordType("012source001"));
            
            ObjectTransformConfig loadedAccountConfig = loaded.getObjectConfig("Account");
            assertNotNull(loadedAccountConfig);
            assertEquals("Technology", loadedAccountConfig.getMappedPicklistValue("Industry", "Tech"));
            assertTrue(loadedAccountConfig.isFieldExcluded("SystemField__c"));
        }

        @Test
        @DisplayName("saveToFile works correctly")
        void testSaveToFile() throws IOException {
            TransformationConfig config = new TransformationConfig("FileTest");
            
            File file = tempDir.resolve("file_config.json").toFile();
            config.saveToFile(file);
            
            assertTrue(file.exists());
        }

        @Test
        @DisplayName("loadFromFile works correctly")
        void testLoadFromFile() throws IOException {
            TransformationConfig config = new TransformationConfig("FileLoadTest");
            
            File file = tempDir.resolve("load_test.json").toFile();
            config.saveToFile(file);
            
            TransformationConfig loaded = TransformationConfig.loadFromFile(file);
            assertEquals("FileLoadTest", loaded.getName());
        }

        @Test
        @DisplayName("loadFromBackupFolder returns null if not exists")
        void testLoadFromBackupFolderNotExists() throws IOException {
            TransformationConfig loaded = TransformationConfig.loadFromBackupFolder(tempDir.toString());
            assertNull(loaded);
        }

        @Test
        @DisplayName("loadFromBackupFolder loads config if exists")
        void testLoadFromBackupFolder() throws IOException {
            TransformationConfig config = new TransformationConfig("BackupFolderTest");
            config.save(tempDir.resolve("_transformation_config.json"));
            
            TransformationConfig loaded = TransformationConfig.loadFromBackupFolder(tempDir.toString());
            assertNotNull(loaded);
            assertEquals("BackupFolderTest", loaded.getName());
        }
    }

    // ==================== ObjectTransformConfig Tests ====================

    @Nested
    @DisplayName("ObjectTransformConfig Tests")
    class ObjectTransformConfigTests {

        @Test
        @DisplayName("ObjectTransformConfig stores object name correctly")
        void testObjectName() {
            ObjectTransformConfig config = new ObjectTransformConfig("Account");
            assertEquals("Account", config.getObjectName());
        }

        @Test
        @DisplayName("RecordType mappings work correctly")
        void testRecordTypeMappings() {
            ObjectTransformConfig config = new ObjectTransformConfig("Account");
            
            config.addRecordTypeMapping("012source", "012target");
            config.setDefaultRecordTypeId("012default");
            config.setUnmappedRecordTypeBehavior(UnmappedValueBehavior.USE_DEFAULT);
            
            assertEquals("012target", config.getMappedRecordType("012source"));
            assertEquals("012default", config.getDefaultRecordTypeId());
            assertEquals(UnmappedValueBehavior.USE_DEFAULT, config.getUnmappedRecordTypeBehavior());
        }

        @Test
        @DisplayName("Picklist mappings work correctly")
        void testPicklistMappings() {
            ObjectTransformConfig config = new ObjectTransformConfig("Account");
            
            config.addPicklistMapping("Industry", "Tech", "Technology");
            config.addPicklistMapping("Industry", "Fin", "Finance");
            config.addPicklistMapping("Type", "Prospect", "Prospective Customer");
            
            assertEquals("Technology", config.getMappedPicklistValue("Industry", "Tech"));
            assertEquals("Finance", config.getMappedPicklistValue("Industry", "Fin"));
            assertEquals("Prospective Customer", config.getMappedPicklistValue("Type", "Prospect"));
            assertNull(config.getMappedPicklistValue("Industry", "Unknown"));
            assertNull(config.getMappedPicklistValue("NonExistentField", "Value"));
        }

        @Test
        @DisplayName("Default picklist values work correctly")
        void testDefaultPicklistValues() {
            ObjectTransformConfig config = new ObjectTransformConfig("Lead");
            
            config.setDefaultPicklistValue("Status", "Open - Not Contacted");
            config.setUnmappedPicklistBehavior(UnmappedValueBehavior.USE_DEFAULT);
            
            assertEquals("Open - Not Contacted", config.getDefaultPicklistValue("Status"));
            assertEquals(UnmappedValueBehavior.USE_DEFAULT, config.getUnmappedPicklistBehavior());
        }

        @Test
        @DisplayName("Field name mappings work correctly")
        void testFieldNameMappings() {
            ObjectTransformConfig config = new ObjectTransformConfig("Account");
            
            config.addFieldNameMapping("OldField__c", "NewField__c");
            config.addFieldNameMapping("LegacyId__c", "Legacy_ID__c");
            
            assertEquals("NewField__c", config.getMappedFieldName("OldField__c"));
            assertEquals("Legacy_ID__c", config.getMappedFieldName("LegacyId__c"));
            assertEquals("UnmappedField", config.getMappedFieldName("UnmappedField")); // Returns original
        }

        @Test
        @DisplayName("Excluded fields work correctly")
        void testExcludedFields() {
            ObjectTransformConfig config = new ObjectTransformConfig("Account");
            
            config.excludeField("SystemField__c");
            config.excludeField("InternalField__c");
            
            assertTrue(config.isFieldExcluded("SystemField__c"));
            assertTrue(config.isFieldExcluded("InternalField__c"));
            assertFalse(config.isFieldExcluded("NormalField__c"));
            
            assertEquals(2, config.getExcludedFields().size());
        }

        @Test
        @DisplayName("Value transformations work correctly")
        void testValueTransformations() {
            ObjectTransformConfig config = new ObjectTransformConfig("Account");
            
            ValueTransformation transform = new ValueTransformation("Name", TransformationType.PREFIX);
            transform.setPattern("MIGRATED_");
            
            config.addValueTransformation("Name", transform);
            
            Map<String, ValueTransformation> transforms = config.getValueTransformations();
            assertEquals(1, transforms.size());
            assertEquals(TransformationType.PREFIX, transforms.get("Name").getType());
        }

        @Test
        @DisplayName("Object-level user mappings work correctly")
        void testObjectUserMappings() {
            ObjectTransformConfig config = new ObjectTransformConfig("Contact");
            
            config.addUserMapping("005source", "005target");
            config.setUnmappedUserBehavior(UnmappedValueBehavior.USE_RUNNING_USER);
            
            assertEquals("005target", config.getMappedUser("005source"));
            assertNull(config.getMappedUser("005unknown"));
            assertEquals(UnmappedValueBehavior.USE_RUNNING_USER, config.getUnmappedUserBehavior());
        }
    }

    // ==================== ValueTransformation Tests ====================

    @Nested
    @DisplayName("ValueTransformation Tests")
    class ValueTransformationTests {

        @Test
        @DisplayName("ValueTransformation default constructor works")
        void testDefaultConstructor() {
            ValueTransformation transform = new ValueTransformation();
            assertNull(transform.getFieldName());
            assertNull(transform.getType());
        }

        @Test
        @DisplayName("ValueTransformation constructor with parameters works")
        void testConstructorWithParams() {
            ValueTransformation transform = new ValueTransformation("Name", TransformationType.UPPERCASE);
            
            assertEquals("Name", transform.getFieldName());
            assertEquals(TransformationType.UPPERCASE, transform.getType());
        }

        @Test
        @DisplayName("All setters and getters work correctly")
        void testSettersAndGetters() {
            ValueTransformation transform = new ValueTransformation();
            
            transform.setFieldName("Description");
            transform.setType(TransformationType.REGEX_REPLACE);
            transform.setPattern("\\d+");
            transform.setReplacement("###");
            transform.setCondition("field != null");
            
            Map<String, String> lookupTable = new HashMap<>();
            lookupTable.put("A", "Alpha");
            lookupTable.put("B", "Beta");
            transform.setLookupTable(lookupTable);
            
            assertEquals("Description", transform.getFieldName());
            assertEquals(TransformationType.REGEX_REPLACE, transform.getType());
            assertEquals("\\d+", transform.getPattern());
            assertEquals("###", transform.getReplacement());
            assertEquals("field != null", transform.getCondition());
            assertEquals(2, transform.getLookupTable().size());
            assertEquals("Alpha", transform.getLookupTable().get("A"));
        }
    }

    // ==================== Enum Tests ====================

    @Nested
    @DisplayName("Enum Tests")
    class EnumTests {

        @Test
        @DisplayName("UnmappedValueBehavior enum has all values")
        void testUnmappedValueBehaviorEnum() {
            assertEquals(6, UnmappedValueBehavior.values().length);
            assertNotNull(UnmappedValueBehavior.KEEP_ORIGINAL);
            assertNotNull(UnmappedValueBehavior.USE_DEFAULT);
            assertNotNull(UnmappedValueBehavior.SET_NULL);
            assertNotNull(UnmappedValueBehavior.USE_RUNNING_USER);
            assertNotNull(UnmappedValueBehavior.SKIP_RECORD);
            assertNotNull(UnmappedValueBehavior.FAIL);
        }

        @Test
        @DisplayName("TransformationType enum has all values")
        void testTransformationTypeEnum() {
            assertEquals(10, TransformationType.values().length);
            assertNotNull(TransformationType.REGEX_REPLACE);
            assertNotNull(TransformationType.PREFIX);
            assertNotNull(TransformationType.SUFFIX);
            assertNotNull(TransformationType.TRIM);
            assertNotNull(TransformationType.UPPERCASE);
            assertNotNull(TransformationType.LOWERCASE);
            assertNotNull(TransformationType.CONSTANT);
            assertNotNull(TransformationType.FORMULA);
            assertNotNull(TransformationType.LOOKUP);
            assertNotNull(TransformationType.CONCATENATE);
        }
    }

    // ==================== Complex Scenarios ====================

    @Nested
    @DisplayName("Complex Scenarios Tests")
    class ComplexScenariosTests {

        @Test
        @DisplayName("Full config with multiple objects serializes correctly")
        void testFullConfigSerialization() throws IOException {
            TransformationConfig config = new TransformationConfig("Full Migration Config");
            config.setSourceOrg("Production");
            config.setTargetOrg("Developer Sandbox");
            
            // Global mappings
            config.addUserMapping("005prod001", "005sandbox001");
            config.addRecordTypeMapping("012prod001", "012sandbox001");
            
            // Account config
            ObjectTransformConfig accountConfig = config.getOrCreateObjectConfig("Account");
            accountConfig.addPicklistMapping("Industry", "Tech", "Technology");
            accountConfig.addPicklistMapping("Industry", "Fin", "Financial Services");
            accountConfig.addFieldNameMapping("OldName__c", "NewName__c");
            accountConfig.excludeField("Internal__c");
            accountConfig.setDefaultRecordTypeId("012default");
            
            // Contact config
            ObjectTransformConfig contactConfig = config.getOrCreateObjectConfig("Contact");
            contactConfig.addUserMapping("005prod002", "005sandbox002");
            
            ValueTransformation prefixTransform = new ValueTransformation("Email", TransformationType.PREFIX);
            prefixTransform.setPattern("test_");
            contactConfig.addValueTransformation("Email", prefixTransform);
            
            // Save and reload
            Path configPath = tempDir.resolve("full_config.json");
            config.save(configPath);
            
            TransformationConfig loaded = TransformationConfig.load(configPath);
            
            // Verify global
            assertEquals("Full Migration Config", loaded.getName());
            assertEquals("005sandbox001", loaded.getMappedUser("005prod001"));
            assertEquals("012sandbox001", loaded.getMappedRecordType("012prod001"));
            
            // Verify Account
            ObjectTransformConfig loadedAccount = loaded.getObjectConfig("Account");
            assertNotNull(loadedAccount);
            assertEquals("Technology", loadedAccount.getMappedPicklistValue("Industry", "Tech"));
            assertEquals("NewName__c", loadedAccount.getMappedFieldName("OldName__c"));
            assertTrue(loadedAccount.isFieldExcluded("Internal__c"));
            
            // Verify Contact
            ObjectTransformConfig loadedContact = loaded.getObjectConfig("Contact");
            assertNotNull(loadedContact);
            assertEquals("005sandbox002", loadedContact.getMappedUser("005prod002"));
            assertEquals(TransformationType.PREFIX, loadedContact.getValueTransformations().get("Email").getType());
        }

        @Test
        @DisplayName("Empty config serializes correctly")
        void testEmptyConfigSerialization() throws IOException {
            TransformationConfig config = new TransformationConfig();
            
            Path configPath = tempDir.resolve("empty_config.json");
            config.save(configPath);
            
            TransformationConfig loaded = TransformationConfig.load(configPath);
            
            assertNotNull(loaded);
            assertTrue(loaded.getUserMappings().isEmpty());
            assertTrue(loaded.getRecordTypeMappings().isEmpty());
            assertTrue(loaded.getObjectConfigs().isEmpty());
        }
    }
}
