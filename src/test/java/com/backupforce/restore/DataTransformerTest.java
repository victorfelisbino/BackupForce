package com.backupforce.restore;

import com.backupforce.restore.TransformationConfig.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the DataTransformer class.
 * Tests cross-org data transformation including RecordType, User, and Picklist mappings.
 */
@DisplayName("DataTransformer Tests")
class DataTransformerTest {
    
    private TransformationConfig config;
    private DataTransformer transformer;
    
    @BeforeEach
    void setUp() {
        config = new TransformationConfig("Test Config");
    }
    
    // ==================== RecordType Transformation Tests ====================
    
    @Nested
    @DisplayName("RecordType Mapping Tests")
    class RecordTypeMappingTests {
        
        @Test
        @DisplayName("Should map RecordTypeId using global mapping")
        void shouldMapRecordTypeIdGlobally() {
            config.addRecordTypeMapping("012000000000001AAA", "012000000000002BBB");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012000000000001AAA", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals(1, result.size());
            assertEquals("012000000000002BBB", result.get(0).get("RecordTypeId"));
        }
        
        @Test
        @DisplayName("Should map RecordTypeId using object-specific mapping")
        void shouldMapRecordTypeIdPerObject() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addRecordTypeMapping("012000000000001AAA", "012000000000003CCC");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012000000000001AAA", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("012000000000003CCC", result.get(0).get("RecordTypeId"));
        }
        
        @Test
        @DisplayName("Should prefer object mapping over global mapping")
        void shouldPreferObjectMappingOverGlobal() {
            config.addRecordTypeMapping("012000000000001AAA", "012000000000002BBB");
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addRecordTypeMapping("012000000000001AAA", "012000000000003CCC");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012000000000001AAA", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("012000000000003CCC", result.get(0).get("RecordTypeId"));
        }
        
        @Test
        @DisplayName("Should keep original value when unmapped and KEEP_ORIGINAL behavior")
        void shouldKeepOriginalWhenUnmapped() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.setUnmappedRecordTypeBehavior(UnmappedValueBehavior.KEEP_ORIGINAL);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012UNMAPPED", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("012UNMAPPED", result.get(0).get("RecordTypeId"));
        }
        
        @Test
        @DisplayName("Should set null when unmapped and SET_NULL behavior")
        void shouldSetNullWhenUnmapped() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.setUnmappedRecordTypeBehavior(UnmappedValueBehavior.SET_NULL);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012UNMAPPED", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertNull(result.get(0).get("RecordTypeId"));
        }
        
        @Test
        @DisplayName("Should skip record when unmapped and SKIP_RECORD behavior")
        void shouldSkipRecordWhenUnmapped() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.setUnmappedRecordTypeBehavior(UnmappedValueBehavior.SKIP_RECORD);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012UNMAPPED", "Name", "Test"),
                createRecord("Id", "001yyy", "RecordTypeId", "012MAPPED", "Name", "Test2")
            );
            
            // Add mapping for second record
            objConfig.addRecordTypeMapping("012MAPPED", "012TARGET");
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals(1, result.size());
            assertEquals("012TARGET", result.get(0).get("RecordTypeId"));
        }
        
        @Test
        @DisplayName("Should throw exception when unmapped and FAIL behavior")
        void shouldThrowExceptionWhenUnmapped() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.setUnmappedRecordTypeBehavior(UnmappedValueBehavior.FAIL);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012UNMAPPED", "Name", "Test")
            );
            
            assertThrows(DataTransformer.DataTransformationException.class, 
                () -> transformer.transformRecords("Account", records, null));
        }
        
        @Test
        @DisplayName("Should use default RecordType when unmapped and USE_DEFAULT behavior")
        void shouldUseDefaultWhenUnmapped() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.setUnmappedRecordTypeBehavior(UnmappedValueBehavior.USE_DEFAULT);
            objConfig.setDefaultRecordTypeId("012DEFAULT");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012UNMAPPED", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("012DEFAULT", result.get(0).get("RecordTypeId"));
        }
    }
    
    // ==================== User Mapping Tests ====================
    
    @Nested
    @DisplayName("User Mapping Tests")
    class UserMappingTests {
        
        @Test
        @DisplayName("Should map OwnerId using global mapping")
        void shouldMapOwnerIdGlobally() {
            config.addUserMapping("005SourceUser", "005TargetUser");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "OwnerId", "005SourceUser", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("005TargetUser", result.get(0).get("OwnerId"));
        }
        
        @Test
        @DisplayName("Should map CreatedById and LastModifiedById")
        void shouldMapAuditUserFields() {
            config.addUserMapping("005SourceUser", "005TargetUser");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", 
                             "OwnerId", "005SourceUser",
                             "CreatedById", "005SourceUser",
                             "LastModifiedById", "005SourceUser",
                             "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("005TargetUser", result.get(0).get("OwnerId"));
            assertEquals("005TargetUser", result.get(0).get("CreatedById"));
            assertEquals("005TargetUser", result.get(0).get("LastModifiedById"));
        }
        
        @Test
        @DisplayName("Should use running user when unmapped and USE_RUNNING_USER behavior")
        void shouldUseRunningUserWhenUnmapped() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.setUnmappedUserBehavior(UnmappedValueBehavior.USE_RUNNING_USER);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "OwnerId", "005UnmappedUser", "Name", "Test")
            );
            
            String runningUserId = "005RunningUser";
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, runningUserId);
            
            assertEquals(runningUserId, result.get(0).get("OwnerId"));
        }
        
        @Test
        @DisplayName("Should prefer object-level user mapping over global")
        void shouldPreferObjectUserMappingOverGlobal() {
            config.addUserMapping("005SourceUser", "005GlobalTarget");
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addUserMapping("005SourceUser", "005ObjectTarget");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "OwnerId", "005SourceUser", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("005ObjectTarget", result.get(0).get("OwnerId"));
        }
    }
    
    // ==================== Picklist Mapping Tests ====================
    
    @Nested
    @DisplayName("Picklist Mapping Tests")
    class PicklistMappingTests {
        
        @Test
        @DisplayName("Should map picklist values")
        void shouldMapPicklistValues() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addPicklistMapping("Industry", "Tech", "Technology");
            objConfig.addPicklistMapping("Industry", "Fin", "Financial Services");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Industry", "Tech", "Name", "Test1"),
                createRecord("Id", "001yyy", "Industry", "Fin", "Name", "Test2")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("Technology", result.get(0).get("Industry"));
            assertEquals("Financial Services", result.get(1).get("Industry"));
        }
        
        @Test
        @DisplayName("Should keep unmapped picklist values with KEEP_ORIGINAL behavior")
        void shouldKeepUnmappedPicklistValues() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addPicklistMapping("Industry", "Tech", "Technology");
            objConfig.setUnmappedPicklistBehavior(UnmappedValueBehavior.KEEP_ORIGINAL);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Industry", "UnmappedValue", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("UnmappedValue", result.get(0).get("Industry"));
        }
        
        @Test
        @DisplayName("Should set null for unmapped picklist with SET_NULL behavior")
        void shouldSetNullForUnmappedPicklist() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addPicklistMapping("Industry", "Tech", "Technology");
            objConfig.setUnmappedPicklistBehavior(UnmappedValueBehavior.SET_NULL);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Industry", "UnmappedValue", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertNull(result.get(0).get("Industry"));
        }
        
        @Test
        @DisplayName("Should use default picklist value when unmapped")
        void shouldUseDefaultPicklistValue() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addPicklistMapping("Industry", "Tech", "Technology");
            objConfig.setUnmappedPicklistBehavior(UnmappedValueBehavior.USE_DEFAULT);
            objConfig.setDefaultPicklistValue("Industry", "Other");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Industry", "UnmappedValue", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("Other", result.get(0).get("Industry"));
        }
    }
    
    // ==================== Field Mapping Tests ====================
    
    @Nested
    @DisplayName("Field Mapping Tests")
    class FieldMappingTests {
        
        @Test
        @DisplayName("Should rename fields")
        void shouldRenameFields() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addFieldNameMapping("Old_Field__c", "New_Field__c");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Old_Field__c", "Value", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertTrue(result.get(0).containsKey("New_Field__c"));
            assertFalse(result.get(0).containsKey("Old_Field__c"));
            assertEquals("Value", result.get(0).get("New_Field__c"));
        }
        
        @Test
        @DisplayName("Should exclude specified fields")
        void shouldExcludeFields() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.excludeField("SystemField__c");
            objConfig.excludeField("InternalCode__c");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", 
                             "Name", "Test", 
                             "SystemField__c", "system", 
                             "InternalCode__c", "internal",
                             "KeepField__c", "keep")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertFalse(result.get(0).containsKey("SystemField__c"));
            assertFalse(result.get(0).containsKey("InternalCode__c"));
            assertTrue(result.get(0).containsKey("KeepField__c"));
        }
        
        @Test
        @DisplayName("Should skip internal reference columns")
        void shouldSkipInternalReferenceColumns() {
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", 
                             "Name", "Test",
                             "_ref_Account.OwnerId", "005xxx",
                             "_rel_Contact.AccountId", "001yyy")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertFalse(result.get(0).containsKey("_ref_Account.OwnerId"));
            assertFalse(result.get(0).containsKey("_rel_Contact.AccountId"));
        }
    }
    
    // ==================== Value Transformation Tests ====================
    
    @Nested
    @DisplayName("Value Transformation Tests")
    class ValueTransformationTests {
        
        @Test
        @DisplayName("Should apply regex replacement")
        void shouldApplyRegexReplacement() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.REGEX_REPLACE);
            transform.setPattern("\\d{4}");
            transform.setReplacement("XXXX");
            objConfig.addValueTransformation("Phone", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Phone", "1234-5678-9012", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("XXXX-XXXX-XXXX", result.get(0).get("Phone"));
        }
        
        @Test
        @DisplayName("Should add prefix to values")
        void shouldAddPrefix() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.PREFIX);
            transform.setReplacement("MIGRATED_");
            objConfig.addValueTransformation("External_Id__c", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "External_Id__c", "ABC123", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("MIGRATED_ABC123", result.get(0).get("External_Id__c"));
        }
        
        @Test
        @DisplayName("Should add suffix to values")
        void shouldAddSuffix() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.SUFFIX);
            transform.setReplacement("_COPIED");
            objConfig.addValueTransformation("Name", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Name", "Test Account")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("Test Account_COPIED", result.get(0).get("Name"));
        }
        
        @Test
        @DisplayName("Should convert to uppercase")
        void shouldConvertToUppercase() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.UPPERCASE);
            objConfig.addValueTransformation("Code__c", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Code__c", "abc123", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("ABC123", result.get(0).get("Code__c"));
        }
        
        @Test
        @DisplayName("Should convert to lowercase")
        void shouldConvertToLowercase() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.LOWERCASE);
            objConfig.addValueTransformation("Email__c", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Email__c", "TEST@EXAMPLE.COM", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("test@example.com", result.get(0).get("Email__c"));
        }
        
        @Test
        @DisplayName("Should trim whitespace")
        void shouldTrimWhitespace() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.TRIM);
            objConfig.addValueTransformation("Name", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Name", "  Test Account  ")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("Test Account", result.get(0).get("Name"));
        }
        
        @Test
        @DisplayName("Should replace with constant value")
        void shouldReplaceWithConstant() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.CONSTANT);
            transform.setReplacement("MIGRATED");
            objConfig.addValueTransformation("Status__c", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Status__c", "Active", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("MIGRATED", result.get(0).get("Status__c"));
        }
        
        @Test
        @DisplayName("Should apply conditional transformation")
        void shouldApplyConditionalTransformation() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.SUFFIX);
            transform.setReplacement(" (VIP)");
            transform.setCondition("Premium");
            objConfig.addValueTransformation("Name", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Name", "Premium Customer"),
                createRecord("Id", "001yyy", "Name", "Standard Customer")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("Premium Customer (VIP)", result.get(0).get("Name"));
            assertEquals("Standard Customer", result.get(1).get("Name"));
        }
        
        @Test
        @DisplayName("Should apply lookup transformation")
        void shouldApplyLookupTransformation() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            ValueTransformation transform = new ValueTransformation();
            transform.setType(TransformationType.LOOKUP);
            Map<String, String> lookup = new HashMap<>();
            lookup.put("US", "United States");
            lookup.put("UK", "United Kingdom");
            lookup.put("DE", "Germany");
            transform.setLookupTable(lookup);
            objConfig.addValueTransformation("Country", transform);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Country", "US", "Name", "Test1"),
                createRecord("Id", "001yyy", "Country", "UK", "Name", "Test2"),
                createRecord("Id", "001zzz", "Country", "FR", "Name", "Test3")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals("United States", result.get(0).get("Country"));
            assertEquals("United Kingdom", result.get(1).get("Country"));
            assertEquals("FR", result.get(2).get("Country")); // No mapping, keep original
        }
    }
    
    // ==================== Statistics Tests ====================
    
    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {
        
        @Test
        @DisplayName("Should track transformation statistics")
        void shouldTrackTransformationStatistics() {
            config.addUserMapping("005Source", "005Target");
            config.addRecordTypeMapping("012Source", "012Target");
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.addPicklistMapping("Industry", "Tech", "Technology");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", 
                             "OwnerId", "005Source",
                             "RecordTypeId", "012Source",
                             "Industry", "Tech",
                             "Name", "Test")
            );
            
            transformer.transformRecords("Account", records, null);
            
            DataTransformer.TransformationStatistics stats = transformer.getStatistics();
            
            assertEquals(1, stats.getTransformedRecords());
            assertEquals(0, stats.getSkippedRecords());
            assertEquals(1, stats.getUserMappingsApplied());
            assertEquals(1, stats.getRecordTypeMappingsApplied());
            assertEquals(1, stats.getPicklistMappingsApplied());
        }
        
        @Test
        @DisplayName("Should track skipped records")
        void shouldTrackSkippedRecords() {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig("Account");
            objConfig.setUnmappedRecordTypeBehavior(UnmappedValueBehavior.SKIP_RECORD);
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "RecordTypeId", "012Unmapped1", "Name", "Test1"),
                createRecord("Id", "001yyy", "RecordTypeId", "012Unmapped2", "Name", "Test2")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals(0, result.size());
            
            DataTransformer.TransformationStatistics stats = transformer.getStatistics();
            assertEquals(0, stats.getTransformedRecords());
            assertEquals(2, stats.getSkippedRecords());
        }
        
        @Test
        @DisplayName("Should reset statistics")
        void shouldResetStatistics() {
            config.addUserMapping("005Source", "005Target");
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "OwnerId", "005Source", "Name", "Test")
            );
            
            transformer.transformRecords("Account", records, null);
            transformer.resetStatistics();
            
            DataTransformer.TransformationStatistics stats = transformer.getStatistics();
            assertEquals(0, stats.getTransformedRecords());
            assertEquals(0, stats.getTotalMappingsApplied());
        }
    }
    
    // ==================== Suggestion Algorithm Tests ====================
    
    @Nested
    @DisplayName("Mapping Suggestion Tests")
    class MappingSuggestionTests {
        
        @Test
        @DisplayName("Should suggest picklist mappings based on similarity")
        void shouldSuggestPicklistMappings() {
            Set<String> sourceValues = Set.of("Technology", "Financial Svcs", "Hlthcare");
            Set<String> targetValues = Set.of("Technology", "Financial Services", "Healthcare", "Retail");
            
            Map<String, String> suggestions = DataTransformer.suggestPicklistMappings(sourceValues, targetValues);
            
            assertEquals("Technology", suggestions.get("Technology"));
            assertEquals("Financial Services", suggestions.get("Financial Svcs"));
            assertEquals("Healthcare", suggestions.get("Hlthcare"));
        }
        
        @Test
        @DisplayName("Should not suggest low-similarity matches")
        void shouldNotSuggestLowSimilarityMatches() {
            Set<String> sourceValues = Set.of("ABC", "XYZ");
            Set<String> targetValues = Set.of("DEF", "GHI");
            
            Map<String, String> suggestions = DataTransformer.suggestPicklistMappings(sourceValues, targetValues);
            
            assertTrue(suggestions.isEmpty());
        }
    }
    
    // ==================== Edge Cases ====================
    
    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Should handle null values")
        void shouldHandleNullValues() {
            transformer = new DataTransformer(config);
            
            Map<String, String> record = new LinkedHashMap<>();
            record.put("Id", "001xxx");
            record.put("Name", null);
            record.put("Industry", null);
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", List.of(record), null);
            
            assertEquals(1, result.size());
            assertNull(result.get(0).get("Name"));
            assertNull(result.get(0).get("Industry"));
        }
        
        @Test
        @DisplayName("Should handle empty string values")
        void shouldHandleEmptyStringValues() {
            transformer = new DataTransformer(config);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Name", "", "Industry", "")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals(1, result.size());
            assertNull(result.get(0).get("Name"));
            assertNull(result.get(0).get("Industry"));
        }
        
        @Test
        @DisplayName("Should handle empty record list")
        void shouldHandleEmptyRecordList() {
            transformer = new DataTransformer(config);
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", List.of(), null);
            
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("Should handle null config")
        void shouldHandleNullConfig() {
            transformer = new DataTransformer(null);
            
            List<Map<String, String>> records = List.of(
                createRecord("Id", "001xxx", "Name", "Test")
            );
            
            List<Map<String, Object>> result = transformer.transformRecords("Account", records, null);
            
            assertEquals(1, result.size());
            assertEquals("Test", result.get(0).get("Name"));
        }
    }
    
    // ==================== Helper Methods ====================
    
    private Map<String, String> createRecord(String... keyValues) {
        Map<String, String> record = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            record.put(keyValues[i], keyValues[i + 1]);
        }
        return record;
    }
}
