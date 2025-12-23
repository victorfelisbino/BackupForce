package com.backupforce.relationship;

import com.backupforce.relationship.RelationshipAwareBackupService.RelatedBackupTask;
import com.backupforce.relationship.RelationshipAwareBackupService.RelationshipBackupPlan;
import com.backupforce.relationship.RelationshipAwareBackupService.RelatedObjectInfo;
import com.backupforce.relationship.ChildRelationshipAnalyzer.RelationshipNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RelationshipAwareBackupService inner classes.
 * Tests backup task structures and plan generation without API calls.
 */
@DisplayName("RelationshipAwareBackupService Tests")
class RelationshipAwareBackupServiceTest {

    @TempDir
    Path tempDir;

    // ==================== RelatedBackupTask Tests ====================
    
    @Test
    @DisplayName("RelatedBackupTask should store all properties")
    void testRelatedBackupTaskProperties() {
        RelatedBackupTask task = new RelatedBackupTask(
            "Contact", "AccountId", "Account",
            "AccountId IN ('001xxx','001yyy')", 1
        );
        
        assertEquals("Contact", task.getObjectName());
        assertEquals("AccountId", task.getParentField());
        assertEquals("Account", task.getParentObject());
        assertEquals("AccountId IN ('001xxx','001yyy')", task.getWhereClause());
        assertEquals(1, task.getDepth());
        assertFalse(task.isCompleted());
        assertEquals(0, task.getRecordCount());
    }
    
    @Test
    @DisplayName("RelatedBackupTask completion should be trackable")
    void testRelatedBackupTaskCompletion() {
        RelatedBackupTask task = new RelatedBackupTask(
            "Opportunity", "AccountId", "Account",
            "AccountId IN ('001xxx')", 1
        );
        
        assertFalse(task.isCompleted());
        
        task.setCompleted(true);
        task.setRecordCount(50);
        
        assertTrue(task.isCompleted());
        assertEquals(50, task.getRecordCount());
    }
    
    @Test
    @DisplayName("RelatedBackupTask toString should be descriptive")
    void testRelatedBackupTaskToString() {
        RelatedBackupTask task = new RelatedBackupTask(
            "Case", "AccountId", "Account",
            "AccountId IN ('001xxx')", 1
        );
        
        String str = task.toString();
        assertTrue(str.contains("Case"));
        assertTrue(str.contains("AccountId"));
        assertTrue(str.contains("Account"));
        assertTrue(str.contains("depth=1"));
    }
    
    @Test
    @DisplayName("RelatedBackupTask at depth 2 should represent grandchild")
    void testRelatedBackupTaskDepth2() {
        RelatedBackupTask task = new RelatedBackupTask(
            "Task", "WhoId", "Contact",
            "WhoId IN ('003xxx','003yyy')", 2
        );
        
        assertEquals("Task", task.getObjectName());
        assertEquals("Contact", task.getParentObject());
        assertEquals(2, task.getDepth());
    }
    
    // ==================== RelationshipBackupPlan Tests ====================
    
    @Test
    @DisplayName("RelationshipBackupPlan should start empty")
    void testRelationshipBackupPlanEmpty() {
        RelationshipBackupPlan plan = new RelationshipBackupPlan();
        
        assertEquals(0, plan.getTotalRelatedObjects());
        assertTrue(plan.getRelatedObjectsByParent().isEmpty());
    }
    
    @Test
    @DisplayName("RelationshipBackupPlan should add related objects")
    void testRelationshipBackupPlanAddObjects() {
        RelationshipBackupPlan plan = new RelationshipBackupPlan();
        
        RelationshipNode contact = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        contact.setPriority(true);
        
        RelationshipNode opp = new RelationshipNode("Opportunity", "AccountId", "Opportunities", 1);
        opp.setPriority(true);
        
        plan.addRelatedObject("Account", contact);
        plan.addRelatedObject("Account", opp);
        
        assertEquals(2, plan.getTotalRelatedObjects());
        assertEquals(1, plan.getRelatedObjectsByParent().size());
        assertEquals(2, plan.getRelatedObjects("Account").size());
    }
    
    @Test
    @DisplayName("RelationshipBackupPlan should group by parent")
    void testRelationshipBackupPlanGroupByParent() {
        RelationshipBackupPlan plan = new RelationshipBackupPlan();
        
        RelationshipNode contact = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        RelationshipNode oppLineItem = new RelationshipNode("OpportunityLineItem", "OpportunityId", "LineItems", 1);
        
        plan.addRelatedObject("Account", contact);
        plan.addRelatedObject("Opportunity", oppLineItem);
        
        assertEquals(2, plan.getTotalRelatedObjects());
        assertEquals(2, plan.getRelatedObjectsByParent().size());
        assertEquals(1, plan.getRelatedObjects("Account").size());
        assertEquals(1, plan.getRelatedObjects("Opportunity").size());
    }
    
    @Test
    @DisplayName("RelationshipBackupPlan getSummary should be descriptive")
    void testRelationshipBackupPlanSummary() {
        RelationshipBackupPlan plan = new RelationshipBackupPlan();
        
        RelationshipNode contact = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        contact.setPriority(true);
        
        plan.addRelatedObject("Account", contact);
        
        String summary = plan.getSummary();
        assertTrue(summary.contains("Account"));
        assertTrue(summary.contains("Contact"));
        assertTrue(summary.contains("AccountId"));
        assertTrue(summary.contains("Total related objects: 1"));
    }
    
    @Test
    @DisplayName("RelationshipBackupPlan should return empty list for unknown parent")
    void testRelationshipBackupPlanUnknownParent() {
        RelationshipBackupPlan plan = new RelationshipBackupPlan();
        
        List<RelatedObjectInfo> result = plan.getRelatedObjects("NonExistent");
        assertTrue(result.isEmpty());
    }
    
    // ==================== RelatedObjectInfo Tests ====================
    
    @Test
    @DisplayName("RelatedObjectInfo should store all properties")
    void testRelatedObjectInfoProperties() {
        RelatedObjectInfo info = new RelatedObjectInfo(
            "Contact", "AccountId", 1, true
        );
        
        assertEquals("Contact", info.getObjectName());
        assertEquals("AccountId", info.getParentField());
        assertEquals(1, info.getDepth());
        assertTrue(info.isPriority());
    }
    
    @Test
    @DisplayName("RelatedObjectInfo non-priority should be flagged correctly")
    void testRelatedObjectInfoNonPriority() {
        RelatedObjectInfo info = new RelatedObjectInfo(
            "CustomObject__c", "Account__c", 1, false
        );
        
        assertEquals("CustomObject__c", info.getObjectName());
        assertFalse(info.isPriority());
    }
    
    @Test
    @DisplayName("RelatedObjectInfo at different depths")
    void testRelatedObjectInfoDepths() {
        RelatedObjectInfo depth1 = new RelatedObjectInfo("Contact", "AccountId", 1, true);
        RelatedObjectInfo depth2 = new RelatedObjectInfo("Task", "WhoId", 2, true);
        RelatedObjectInfo depth3 = new RelatedObjectInfo("Attachment", "ParentId", 3, false);
        
        assertEquals(1, depth1.getDepth());
        assertEquals(2, depth2.getDepth());
        assertEquals(3, depth3.getDepth());
    }
    
    // ==================== CSV Parsing Tests (simulated) ====================
    
    @Test
    @DisplayName("CSV with IDs should be parseable for ID extraction")
    void testCsvIdExtraction() throws IOException {
        // Create a mock CSV file
        String csvContent = 
            "\"Id\",\"Name\",\"AccountId\"\n" +
            "\"001000000000001\",\"Account 1\",\"\"\n" +
            "\"001000000000002\",\"Account 2\",\"\"\n" +
            "\"001000000000003\",\"Account 3\",\"\"";
        
        Path csvFile = tempDir.resolve("Account.csv");
        Files.writeString(csvFile, csvContent);
        
        // Simulate ID extraction logic
        Set<String> ids = extractIdsFromCsv(csvFile);
        
        assertEquals(3, ids.size());
        assertTrue(ids.contains("001000000000001"));
        assertTrue(ids.contains("001000000000002"));
        assertTrue(ids.contains("001000000000003"));
    }
    
    @Test
    @DisplayName("CSV with missing ID column should return empty set")
    void testCsvNoIdColumn() throws IOException {
        String csvContent = 
            "\"Name\",\"Industry\"\n" +
            "\"Account 1\",\"Technology\"\n";
        
        Path csvFile = tempDir.resolve("Account.csv");
        Files.writeString(csvFile, csvContent);
        
        Set<String> ids = extractIdsFromCsv(csvFile);
        assertTrue(ids.isEmpty());
    }
    
    @Test
    @DisplayName("Empty CSV should return empty set")
    void testEmptyCsv() throws IOException {
        Path csvFile = tempDir.resolve("Account.csv");
        Files.writeString(csvFile, "");
        
        Set<String> ids = extractIdsFromCsv(csvFile);
        assertTrue(ids.isEmpty());
    }
    
    // ==================== WHERE Clause Building Tests ====================
    
    @Test
    @DisplayName("WHERE clause should format IDs correctly")
    void testWhereClauseFormatting() {
        Set<String> ids = new LinkedHashSet<>(Arrays.asList(
            "001000000000001",
            "001000000000002",
            "001000000000003"
        ));
        
        String whereClause = buildWhereClause("AccountId", ids);
        
        assertNotNull(whereClause);
        assertTrue(whereClause.startsWith("AccountId IN ("));
        assertTrue(whereClause.contains("'001000000000001'"));
        assertTrue(whereClause.contains("'001000000000002'"));
        assertTrue(whereClause.contains("'001000000000003'"));
        assertTrue(whereClause.endsWith(")"));
    }
    
    @Test
    @DisplayName("WHERE clause with single ID")
    void testWhereClauseSingleId() {
        Set<String> ids = Collections.singleton("001000000000001");
        
        String whereClause = buildWhereClause("AccountId", ids);
        
        assertEquals("AccountId IN ('001000000000001')", whereClause);
    }
    
    @Test
    @DisplayName("WHERE clause with empty IDs should return null")
    void testWhereClauseEmptyIds() {
        Set<String> ids = Collections.emptySet();
        
        String whereClause = buildWhereClause("AccountId", ids);
        
        assertNull(whereClause);
    }
    
    // ==================== Depth Tracking Tests ====================
    
    @Test
    @DisplayName("Tasks should maintain correct depth chain")
    void testDepthChain() {
        // Simulate: Account -> Contact -> Task
        RelatedBackupTask contactTask = new RelatedBackupTask(
            "Contact", "AccountId", "Account",
            "AccountId IN ('001xxx')", 1
        );
        
        // When Contact backup completes, Task is depth 2
        RelatedBackupTask taskTask = new RelatedBackupTask(
            "Task", "WhoId", "Contact",
            "WhoId IN ('003xxx')", 2  // contactTask.getDepth() + 1
        );
        
        assertEquals(1, contactTask.getDepth());
        assertEquals(2, taskTask.getDepth());
        assertEquals("Contact", taskTask.getParentObject());
    }
    
    @Test
    @DisplayName("Multiple tasks at same depth should be supported")
    void testMultipleTasksSameDepth() {
        List<RelatedBackupTask> tasks = new ArrayList<>();
        
        tasks.add(new RelatedBackupTask("Contact", "AccountId", "Account", "AccountId IN ('001xxx')", 1));
        tasks.add(new RelatedBackupTask("Opportunity", "AccountId", "Account", "AccountId IN ('001xxx')", 1));
        tasks.add(new RelatedBackupTask("Case", "AccountId", "Account", "AccountId IN ('001xxx')", 1));
        
        assertEquals(3, tasks.size());
        assertTrue(tasks.stream().allMatch(t -> t.getDepth() == 1));
        assertTrue(tasks.stream().allMatch(t -> t.getParentObject().equals("Account")));
    }
    
    // ==================== Helper Methods ====================
    
    private Set<String> extractIdsFromCsv(Path csvFile) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        
        if (!Files.exists(csvFile)) {
            return ids;
        }
        
        List<String> lines = Files.readAllLines(csvFile);
        if (lines.isEmpty()) {
            return ids;
        }
        
        // Parse header
        String header = lines.get(0);
        String[] headers = header.split(",");
        int idIndex = -1;
        for (int i = 0; i < headers.length; i++) {
            if ("\"Id\"".equals(headers[i]) || "Id".equals(headers[i])) {
                idIndex = i;
                break;
            }
        }
        
        if (idIndex == -1) {
            return ids;
        }
        
        // Parse data rows
        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(",");
            if (values.length > idIndex) {
                String id = values[idIndex].replace("\"", "").trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        
        return ids;
    }
    
    private String buildWhereClause(String fieldName, Set<String> parentIds) {
        if (parentIds.isEmpty()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(fieldName).append(" IN (");
        
        boolean first = true;
        for (String id : parentIds) {
            if (!first) sb.append(",");
            sb.append("'").append(id).append("'");
            first = false;
        }
        
        sb.append(")");
        return sb.toString();
    }
}
