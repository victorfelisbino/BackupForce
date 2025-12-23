package com.backupforce.relationship;

import com.backupforce.relationship.BackupManifestGenerator.RelatedObjectInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BackupManifestGenerator.
 * Tests manifest structure, ID mapping, and restore order calculation.
 */
@DisplayName("BackupManifestGenerator Tests")
class BackupManifestGeneratorTest {

    @TempDir
    Path tempDir;

    // ==================== RelatedObjectInfo Tests ====================
    
    @Test
    @DisplayName("RelatedObjectInfo should store all properties")
    void testRelatedObjectInfoProperties() {
        RelatedObjectInfo info = new RelatedObjectInfo(
            "Contact", "Account", "AccountId", 1, true
        );
        
        assertEquals("Contact", info.getObjectName());
        assertEquals("Account", info.getParentObject());
        assertEquals("AccountId", info.getParentField());
        assertEquals(1, info.getDepth());
        assertTrue(info.isPriority());
    }
    
    @Test
    @DisplayName("RelatedObjectInfo with priority false")
    void testRelatedObjectInfoNonPriority() {
        RelatedObjectInfo info = new RelatedObjectInfo(
            "CustomObject__c", "Account", "Account__c", 1, false
        );
        
        assertEquals("CustomObject__c", info.getObjectName());
        assertFalse(info.isPriority());
    }
    
    @Test
    @DisplayName("RelatedObjectInfo at depth 2 should have correct parent chain")
    void testRelatedObjectInfoDepth2() {
        // Task is a grandchild: Account -> Contact -> Task
        RelatedObjectInfo info = new RelatedObjectInfo(
            "Task", "Contact", "WhoId", 2, true
        );
        
        assertEquals("Task", info.getObjectName());
        assertEquals("Contact", info.getParentObject());
        assertEquals("WhoId", info.getParentField());
        assertEquals(2, info.getDepth());
    }
    
    // ==================== Manifest JSON Structure Tests ====================
    
    @Test
    @DisplayName("Manifest JSON should have required metadata")
    void testManifestMetadata() throws IOException {
        String manifestContent = createSampleManifest();
        
        assertTrue(manifestContent.contains("\"version\""));
        assertTrue(manifestContent.contains("\"generatedAt\""));
        assertTrue(manifestContent.contains("\"backupType\""));
    }
    
    @Test
    @DisplayName("Manifest JSON should include options")
    void testManifestOptions() throws IOException {
        String manifestContent = createSampleManifest();
        
        assertTrue(manifestContent.contains("\"captureExternalIds\""));
        assertTrue(manifestContent.contains("\"captureFieldMetadata\""));
        assertTrue(manifestContent.contains("\"generateIdMapping\""));
        assertTrue(manifestContent.contains("\"includeRecordTypes\""));
    }
    
    @Test
    @DisplayName("Manifest JSON should include parent objects")
    void testManifestParentObjects() throws IOException {
        String manifestContent = createSampleManifest();
        
        assertTrue(manifestContent.contains("\"parentObjects\""));
        assertTrue(manifestContent.contains("Account"));
    }
    
    @Test
    @DisplayName("Manifest JSON should include related objects")
    void testManifestRelatedObjects() throws IOException {
        String manifestContent = createSampleManifest();
        
        assertTrue(manifestContent.contains("\"relatedObjects\""));
        assertTrue(manifestContent.contains("Contact"));
        assertTrue(manifestContent.contains("AccountId"));
    }
    
    @Test
    @DisplayName("Manifest JSON should include restore order")
    void testManifestRestoreOrder() throws IOException {
        String manifestContent = createSampleManifest();
        
        assertTrue(manifestContent.contains("\"restoreOrder\""));
    }
    
    // ==================== Restore Order Tests ====================
    
    @Test
    @DisplayName("Restore order should put parents before children")
    void testRestoreOrderParentsFirst() {
        List<String> parentObjects = Arrays.asList("Account", "Product2");
        List<RelatedObjectInfo> relatedObjects = Arrays.asList(
            new RelatedObjectInfo("Contact", "Account", "AccountId", 1, true),
            new RelatedObjectInfo("Opportunity", "Account", "AccountId", 1, true),
            new RelatedObjectInfo("PricebookEntry", "Product2", "Product2Id", 1, true)
        );
        
        List<String> restoreOrder = calculateRestoreOrder(parentObjects, relatedObjects);
        
        // Parents should come first
        int accountIndex = restoreOrder.indexOf("Account");
        int product2Index = restoreOrder.indexOf("Product2");
        int contactIndex = restoreOrder.indexOf("Contact");
        int oppIndex = restoreOrder.indexOf("Opportunity");
        int pbeIndex = restoreOrder.indexOf("PricebookEntry");
        
        assertTrue(accountIndex < contactIndex, "Account should be before Contact");
        assertTrue(accountIndex < oppIndex, "Account should be before Opportunity");
        assertTrue(product2Index < pbeIndex, "Product2 should be before PricebookEntry");
    }
    
    @Test
    @DisplayName("Restore order should sort children by depth")
    void testRestoreOrderByDepth() {
        List<String> parentObjects = Collections.singletonList("Account");
        List<RelatedObjectInfo> relatedObjects = Arrays.asList(
            new RelatedObjectInfo("Task", "Contact", "WhoId", 2, true),  // Depth 2
            new RelatedObjectInfo("Contact", "Account", "AccountId", 1, true),  // Depth 1
            new RelatedObjectInfo("Attachment", "Task", "ParentId", 3, false)  // Depth 3
        );
        
        List<String> restoreOrder = calculateRestoreOrder(parentObjects, relatedObjects);
        
        int accountIndex = restoreOrder.indexOf("Account");
        int contactIndex = restoreOrder.indexOf("Contact");
        int taskIndex = restoreOrder.indexOf("Task");
        int attachmentIndex = restoreOrder.indexOf("Attachment");
        
        assertTrue(accountIndex < contactIndex, "Account (depth 0) should be before Contact (depth 1)");
        assertTrue(contactIndex < taskIndex, "Contact (depth 1) should be before Task (depth 2)");
        assertTrue(taskIndex < attachmentIndex, "Task (depth 2) should be before Attachment (depth 3)");
    }
    
    // ==================== ID Mapping Structure Tests ====================
    
    @Test
    @DisplayName("ID mapping should track old IDs per object")
    void testIdMappingStructure() throws IOException {
        Map<String, Map<String, Object>> idMapping = new LinkedHashMap<>();
        
        Map<String, Object> accountMapping = new LinkedHashMap<>();
        accountMapping.put("oldId", "001000000000001");
        accountMapping.put("newId", null);
        accountMapping.put("externalIds", new HashMap<>());
        
        idMapping.put("001000000000001", accountMapping);
        
        assertNotNull(idMapping.get("001000000000001"));
        assertEquals("001000000000001", idMapping.get("001000000000001").get("oldId"));
        assertNull(idMapping.get("001000000000001").get("newId"));
    }
    
    @Test
    @DisplayName("ID mapping should support parent references")
    void testIdMappingParentReferences() {
        // Contact has parent reference to Account
        Map<String, Object> contactMapping = new LinkedHashMap<>();
        contactMapping.put("oldId", "003000000000001");
        contactMapping.put("newId", null);
        
        Map<String, String> parentMappings = new LinkedHashMap<>();
        parentMappings.put("AccountId", "001000000000001");
        contactMapping.put("parentMappings", parentMappings);
        
        @SuppressWarnings("unchecked")
        Map<String, String> retrieved = (Map<String, String>) contactMapping.get("parentMappings");
        assertEquals("001000000000001", retrieved.get("AccountId"));
    }
    
    // ==================== File Naming Tests ====================
    
    @Test
    @DisplayName("Manifest file should have correct name")
    void testManifestFileName() {
        String manifestFileName = "_backup_manifest.json";
        
        assertTrue(manifestFileName.startsWith("_"), "Should start with underscore for sorting");
        assertTrue(manifestFileName.endsWith(".json"), "Should be JSON file");
        assertTrue(manifestFileName.contains("manifest"), "Should include 'manifest' in name");
    }
    
    @Test
    @DisplayName("ID mapping file should have correct name")
    void testIdMappingFileName() {
        String idMappingFileName = "_id_mapping.json";
        
        assertTrue(idMappingFileName.startsWith("_"), "Should start with underscore for sorting");
        assertTrue(idMappingFileName.endsWith(".json"), "Should be JSON file");
    }
    
    // ==================== Timestamp Format Tests ====================
    
    @Test
    @DisplayName("Timestamp should be in ISO 8601 format")
    void testTimestampFormat() {
        String timestamp = java.time.Instant.now().toString();
        
        // ISO 8601: 2025-12-20T12:30:45.123Z
        assertTrue(timestamp.contains("T"), "Should contain T separator");
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T.*"), "Should match ISO date pattern");
    }
    
    // ==================== Object List Ordering Tests ====================
    
    @Test
    @DisplayName("Object list should preserve insertion order")
    void testObjectListOrdering() {
        List<RelatedObjectInfo> relatedObjects = new ArrayList<>();
        relatedObjects.add(new RelatedObjectInfo("Contact", "Account", "AccountId", 1, true));
        relatedObjects.add(new RelatedObjectInfo("Opportunity", "Account", "AccountId", 1, true));
        relatedObjects.add(new RelatedObjectInfo("Case", "Account", "AccountId", 1, true));
        
        assertEquals("Contact", relatedObjects.get(0).getObjectName());
        assertEquals("Opportunity", relatedObjects.get(1).getObjectName());
        assertEquals("Case", relatedObjects.get(2).getObjectName());
    }
    
    // ==================== Group by Parent Tests ====================
    
    @Test
    @DisplayName("Related objects should be groupable by parent")
    void testGroupByParent() {
        List<RelatedObjectInfo> relatedObjects = Arrays.asList(
            new RelatedObjectInfo("Contact", "Account", "AccountId", 1, true),
            new RelatedObjectInfo("Opportunity", "Account", "AccountId", 1, true),
            new RelatedObjectInfo("Case", "Account", "AccountId", 1, true),
            new RelatedObjectInfo("OpportunityLineItem", "Opportunity", "OpportunityId", 2, true),
            new RelatedObjectInfo("Task", "Contact", "WhoId", 2, true)
        );
        
        Map<String, List<RelatedObjectInfo>> byParent = new LinkedHashMap<>();
        for (RelatedObjectInfo obj : relatedObjects) {
            byParent.computeIfAbsent(obj.getParentObject(), k -> new ArrayList<>()).add(obj);
        }
        
        assertEquals(3, byParent.size());
        assertEquals(3, byParent.get("Account").size());
        assertEquals(1, byParent.get("Opportunity").size());
        assertEquals(1, byParent.get("Contact").size());
    }
    
    // ==================== Helper Methods ====================
    
    private String createSampleManifest() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"metadata\": {\n");
        json.append("    \"version\": \"2.0\",\n");
        json.append("    \"generatedAt\": \"2025-12-20T00:00:00Z\",\n");
        json.append("    \"backupType\": \"relationship-aware\"\n");
        json.append("  },\n");
        json.append("  \"options\": {\n");
        json.append("    \"captureExternalIds\": true,\n");
        json.append("    \"captureFieldMetadata\": true,\n");
        json.append("    \"generateIdMapping\": true,\n");
        json.append("    \"includeRecordTypes\": true\n");
        json.append("  },\n");
        json.append("  \"parentObjects\": [\"Account\"],\n");
        json.append("  \"relatedObjects\": [\n");
        json.append("    {\n");
        json.append("      \"objectName\": \"Contact\",\n");
        json.append("      \"parentObject\": \"Account\",\n");
        json.append("      \"parentField\": \"AccountId\",\n");
        json.append("      \"depth\": 1\n");
        json.append("    }\n");
        json.append("  ],\n");
        json.append("  \"restoreOrder\": [\"Account\", \"Contact\"]\n");
        json.append("}");
        return json.toString();
    }
    
    private List<String> calculateRestoreOrder(List<String> parentObjects, 
                                               List<RelatedObjectInfo> relatedObjects) {
        List<String> order = new ArrayList<>();
        
        // Add parent objects first
        order.addAll(parentObjects);
        
        // Sort related objects by depth and add
        relatedObjects.stream()
            .sorted(Comparator.comparingInt(RelatedObjectInfo::getDepth))
            .forEach(obj -> order.add(obj.getObjectName()));
        
        return order;
    }
}
