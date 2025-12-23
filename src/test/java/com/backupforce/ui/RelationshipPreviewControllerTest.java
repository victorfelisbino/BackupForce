package com.backupforce.ui;

import com.backupforce.ui.RelationshipPreviewController.RelatedObjectItem;
import com.backupforce.ui.RelationshipPreviewController.SelectedRelatedObject;
import com.backupforce.ui.RelationshipPreviewController.RelationshipBackupConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RelationshipPreviewController inner classes.
 * Tests data structures used for relationship preview and selection.
 */
@DisplayName("RelationshipPreviewController Tests")
class RelationshipPreviewControllerTest {

    // ==================== RelatedObjectItem Tests ====================
    
    @Test
    @DisplayName("RelatedObjectItem should store all properties")
    void testRelatedObjectItemProperties() {
        RelatedObjectItem item = new RelatedObjectItem(
            "Contact", "Account", "AccountId", "Contacts", 1, true
        );
        
        assertEquals("Contact", item.getObjectName());
        assertEquals("Account", item.getParentObject());
        assertEquals("AccountId", item.getParentField());
        assertEquals("Contacts", item.getRelationshipName());
        assertEquals(1, item.getDepth());
        assertTrue(item.isPriority());
        assertFalse(item.isRoot());
    }
    
    @Test
    @DisplayName("RelatedObjectItem root constructor should work")
    void testRelatedObjectItemRoot() {
        RelatedObjectItem root = new RelatedObjectItem("Account");
        
        assertEquals("Account", root.getObjectName());
        assertNull(root.getParentObject());
        assertNull(root.getParentField());
        assertNull(root.getRelationshipName());
        assertEquals(0, root.getDepth());
        assertFalse(root.isPriority());
        assertTrue(root.isRoot());
    }
    
    @Test
    @DisplayName("RelatedObjectItem selection should be modifiable")
    void testRelatedObjectItemSelection() {
        RelatedObjectItem item = new RelatedObjectItem(
            "Opportunity", "Account", "AccountId", "Opportunities", 1, true
        );
        
        assertFalse(item.isSelected());
        
        item.setSelected(true);
        assertTrue(item.isSelected());
        
        item.setSelected(false);
        assertFalse(item.isSelected());
    }
    
    @Test
    @DisplayName("RelatedObjectItem selectedProperty should be observable")
    void testRelatedObjectItemSelectedProperty() {
        RelatedObjectItem item = new RelatedObjectItem(
            "Case", "Account", "AccountId", "Cases", 1, false
        );
        
        assertNotNull(item.selectedProperty());
        assertFalse(item.selectedProperty().get());
        
        item.selectedProperty().set(true);
        assertTrue(item.isSelected());
    }
    
    @Test
    @DisplayName("RelatedObjectItem toString should be descriptive")
    void testRelatedObjectItemToString() {
        RelatedObjectItem item = new RelatedObjectItem(
            "Contact", "Account", "AccountId", "Contacts", 1, true
        );
        
        String str = item.toString();
        assertTrue(str.contains("Contact"));
        assertTrue(str.contains("AccountId"));
    }
    
    @Test
    @DisplayName("RelatedObjectItem root toString should show icon")
    void testRelatedObjectItemRootToString() {
        RelatedObjectItem root = new RelatedObjectItem("Account");
        
        String str = root.toString();
        assertTrue(str.contains("Account"));
        assertTrue(str.contains("📦"));
    }
    
    @Test
    @DisplayName("RelatedObjectItem priority should show star in toString")
    void testRelatedObjectItemPriorityToString() {
        RelatedObjectItem priorityItem = new RelatedObjectItem(
            "Contact", "Account", "AccountId", "Contacts", 1, true
        );
        RelatedObjectItem nonPriorityItem = new RelatedObjectItem(
            "CustomObj__c", "Account", "Account__c", null, 1, false
        );
        
        assertTrue(priorityItem.toString().contains("★"));
        assertFalse(nonPriorityItem.toString().contains("★"));
    }
    
    @Test
    @DisplayName("RelatedObjectItem at depth > 1 should show level indicator")
    void testRelatedObjectItemDepthIndicator() {
        RelatedObjectItem depth2 = new RelatedObjectItem(
            "Task", "Contact", "WhoId", "Tasks", 2, true
        );
        
        String str = depth2.toString();
        assertTrue(str.contains("level 2"));
    }
    
    // ==================== SelectedRelatedObject Tests ====================
    
    @Test
    @DisplayName("SelectedRelatedObject should store all properties")
    void testSelectedRelatedObjectProperties() {
        SelectedRelatedObject selected = new SelectedRelatedObject(
            "Contact", "Account", "AccountId", 1, true
        );
        
        assertEquals("Contact", selected.getObjectName());
        assertEquals("Account", selected.getParentObject());
        assertEquals("AccountId", selected.getParentField());
        assertEquals(1, selected.getDepth());
        assertTrue(selected.isPriority());
    }
    
    @Test
    @DisplayName("SelectedRelatedObject non-priority should be flagged correctly")
    void testSelectedRelatedObjectNonPriority() {
        SelectedRelatedObject selected = new SelectedRelatedObject(
            "CustomObj__c", "Account", "Account__c", 1, false
        );
        
        assertEquals("CustomObj__c", selected.getObjectName());
        assertFalse(selected.isPriority());
    }
    
    @Test
    @DisplayName("SelectedRelatedObject at depth 2 should have correct parent chain")
    void testSelectedRelatedObjectDepth2() {
        // Task is a grandchild: Account -> Contact -> Task
        SelectedRelatedObject selected = new SelectedRelatedObject(
            "Task", "Contact", "WhoId", 2, true
        );
        
        assertEquals("Task", selected.getObjectName());
        assertEquals("Contact", selected.getParentObject());
        assertEquals("WhoId", selected.getParentField());
        assertEquals(2, selected.getDepth());
    }
    
    // ==================== RelationshipBackupConfig Tests ====================
    
    @Test
    @DisplayName("RelationshipBackupConfig should store selected objects")
    void testRelationshipBackupConfigSelections() {
        List<SelectedRelatedObject> selections = Arrays.asList(
            new SelectedRelatedObject("Contact", "Account", "AccountId", 1, true),
            new SelectedRelatedObject("Opportunity", "Account", "AccountId", 1, true)
        );
        
        RelationshipBackupConfig config = new RelationshipBackupConfig(
            selections, true, true, true
        );
        
        assertEquals(2, config.getSelectedObjects().size());
        assertEquals("Contact", config.getSelectedObjects().get(0).getObjectName());
        assertEquals("Opportunity", config.getSelectedObjects().get(1).getObjectName());
    }
    
    @Test
    @DisplayName("RelationshipBackupConfig should store all options")
    void testRelationshipBackupConfigOptions() {
        List<SelectedRelatedObject> selections = Collections.emptyList();
        
        RelationshipBackupConfig config = new RelationshipBackupConfig(
            selections, true, true, false
        );
        
        assertTrue(config.isCaptureExternalIds());
        assertTrue(config.isGenerateIdMapping());
        assertFalse(config.isIncludeRecordTypes());
    }
    
    @Test
    @DisplayName("RelationshipBackupConfig with all options false")
    void testRelationshipBackupConfigAllFalse() {
        RelationshipBackupConfig config = new RelationshipBackupConfig(
            Collections.emptyList(), false, false, false
        );
        
        assertFalse(config.isCaptureExternalIds());
        assertFalse(config.isGenerateIdMapping());
        assertFalse(config.isIncludeRecordTypes());
    }
    
    // ==================== Complex Scenario Tests ====================
    
    @Test
    @DisplayName("Multiple related objects at different depths")
    void testMultipleDepths() {
        List<SelectedRelatedObject> selections = new ArrayList<>();
        selections.add(new SelectedRelatedObject("Contact", "Account", "AccountId", 1, true));
        selections.add(new SelectedRelatedObject("Task", "Contact", "WhoId", 2, true));
        selections.add(new SelectedRelatedObject("EmailMessage", "Task", "ParentId", 3, false));
        
        // Verify depth ordering
        assertEquals(1, selections.get(0).getDepth());
        assertEquals(2, selections.get(1).getDepth());
        assertEquals(3, selections.get(2).getDepth());
        
        // Count by depth
        long depth1Count = selections.stream().filter(s -> s.getDepth() == 1).count();
        long depth2Count = selections.stream().filter(s -> s.getDepth() == 2).count();
        long depth3Count = selections.stream().filter(s -> s.getDepth() == 3).count();
        
        assertEquals(1, depth1Count);
        assertEquals(1, depth2Count);
        assertEquals(1, depth3Count);
    }
    
    @Test
    @DisplayName("Priority objects should be identifiable")
    void testPriorityFiltering() {
        List<SelectedRelatedObject> selections = Arrays.asList(
            new SelectedRelatedObject("Contact", "Account", "AccountId", 1, true),
            new SelectedRelatedObject("Opportunity", "Account", "AccountId", 1, true),
            new SelectedRelatedObject("Task", "Account", "WhatId", 1, false),
            new SelectedRelatedObject("Note", "Account", "ParentId", 1, false)
        );
        
        long priorityCount = selections.stream().filter(SelectedRelatedObject::isPriority).count();
        long nonPriorityCount = selections.stream().filter(s -> !s.isPriority()).count();
        
        assertEquals(2, priorityCount);
        assertEquals(2, nonPriorityCount);
    }
    
    @Test
    @DisplayName("Group selected objects by parent")
    void testGroupByParent() {
        List<SelectedRelatedObject> selections = Arrays.asList(
            new SelectedRelatedObject("Contact", "Account", "AccountId", 1, true),
            new SelectedRelatedObject("Opportunity", "Account", "AccountId", 1, true),
            new SelectedRelatedObject("Case", "Account", "AccountId", 1, true),
            new SelectedRelatedObject("Task", "Contact", "WhoId", 2, true),
            new SelectedRelatedObject("Event", "Contact", "WhoId", 2, true)
        );
        
        // Group by parent object
        Map<String, List<SelectedRelatedObject>> byParent = new LinkedHashMap<>();
        for (SelectedRelatedObject obj : selections) {
            byParent.computeIfAbsent(obj.getParentObject(), k -> new ArrayList<>()).add(obj);
        }
        
        assertEquals(2, byParent.size());
        assertEquals(3, byParent.get("Account").size());
        assertEquals(2, byParent.get("Contact").size());
    }
    
    @Test
    @DisplayName("Sort related objects by depth then name")
    void testSortByDepthThenName() {
        List<SelectedRelatedObject> selections = new ArrayList<>();
        selections.add(new SelectedRelatedObject("Task", "Contact", "WhoId", 2, true));
        selections.add(new SelectedRelatedObject("Contact", "Account", "AccountId", 1, true));
        selections.add(new SelectedRelatedObject("Opportunity", "Account", "AccountId", 1, true));
        selections.add(new SelectedRelatedObject("Event", "Contact", "WhoId", 2, true));
        
        // Sort by depth, then by name
        selections.sort(Comparator
            .comparingInt(SelectedRelatedObject::getDepth)
            .thenComparing(SelectedRelatedObject::getObjectName)
        );
        
        assertEquals("Contact", selections.get(0).getObjectName());
        assertEquals("Opportunity", selections.get(1).getObjectName());
        assertEquals("Event", selections.get(2).getObjectName());
        assertEquals("Task", selections.get(3).getObjectName());
    }
    
    @Test
    @DisplayName("Empty selection list should be valid")
    void testEmptySelections() {
        RelationshipBackupConfig config = new RelationshipBackupConfig(
            Collections.emptyList(), true, true, true
        );
        
        assertTrue(config.getSelectedObjects().isEmpty());
    }
    
    @Test
    @DisplayName("Large selection list should be handled")
    void testLargeSelectionList() {
        List<SelectedRelatedObject> selections = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            selections.add(new SelectedRelatedObject(
                "Object" + i, "Account", "AccountId", 1, i % 2 == 0
            ));
        }
        
        RelationshipBackupConfig config = new RelationshipBackupConfig(
            selections, true, true, true
        );
        
        assertEquals(100, config.getSelectedObjects().size());
        
        long priorityCount = config.getSelectedObjects().stream()
            .filter(SelectedRelatedObject::isPriority).count();
        assertEquals(50, priorityCount);
    }
}
