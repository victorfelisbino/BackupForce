package com.backupforce.ui;

import com.backupforce.ui.BackupController.SObjectItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Field Selection feature.
 * Tests the SObjectItem field selection functionality.
 */
@DisplayName("Field Selection Tests")
class FieldSelectionTest {
    
    private SObjectItem item;
    
    @BeforeEach
    void setUp() {
        item = new SObjectItem("Account", "Account");
    }
    
    @Test
    @DisplayName("New SObjectItem has null selectedFields (all fields)")
    void testDefaultFieldSelection() {
        assertNull(item.getSelectedFields(), "New item should have null selectedFields (meaning all fields)");
        assertFalse(item.hasCustomFieldSelection(), "New item should not have custom field selection");
        assertEquals("All fields", item.getFieldSelectionInfo(), "Default info should be 'All fields'");
    }
    
    @Test
    @DisplayName("Setting selected fields updates state")
    void testSetSelectedFields() {
        Set<String> fields = new HashSet<>();
        fields.add("Id");
        fields.add("Name");
        fields.add("CreatedDate");
        
        item.setSelectedFields(fields);
        
        assertNotNull(item.getSelectedFields(), "Selected fields should not be null after setting");
        assertEquals(3, item.getSelectedFields().size(), "Should have 3 fields");
        assertTrue(item.hasCustomFieldSelection(), "Should have custom field selection");
    }
    
    @Test
    @DisplayName("Resetting to null means all fields")
    void testResetFieldSelection() {
        Set<String> fields = new HashSet<>();
        fields.add("Id");
        fields.add("Name");
        
        item.setSelectedFields(fields);
        assertTrue(item.hasCustomFieldSelection(), "Should have custom selection");
        
        item.setSelectedFields(null);
        assertNull(item.getSelectedFields(), "Fields should be null after reset");
        assertFalse(item.hasCustomFieldSelection(), "Should not have custom selection after reset");
        assertEquals("All fields", item.getFieldSelectionInfo(), "Info should show 'All fields'");
    }
    
    @Test
    @DisplayName("Field selection info shows count")
    void testFieldSelectionInfoWithCount() {
        Set<String> fields = new HashSet<>();
        fields.add("Id");
        fields.add("Name");
        fields.add("CreatedDate");
        fields.add("LastModifiedDate");
        fields.add("OwnerId");
        
        item.setSelectedFields(fields);
        item.setTotalFieldCount(50);
        
        assertEquals("5 of 50 fields", item.getFieldSelectionInfo(), "Info should show field count ratio");
    }
    
    @Test
    @DisplayName("Selected fields are preserved during selection toggle")
    void testFieldsPersistDuringSelection() {
        Set<String> fields = new HashSet<>();
        fields.add("Id");
        fields.add("Name");
        
        item.setSelectedFields(fields);
        
        // Toggle selection (object selection, not field selection)
        item.setSelected(true);
        assertTrue(item.isSelected(), "Item should be selected");
        
        // Fields should still be set
        assertNotNull(item.getSelectedFields(), "Fields should persist");
        assertEquals(2, item.getSelectedFields().size(), "Field count should persist");
        
        item.setSelected(false);
        assertFalse(item.isSelected(), "Item should be deselected");
        
        // Fields should still be set
        assertNotNull(item.getSelectedFields(), "Fields should persist after deselection");
    }
    
    @Test
    @DisplayName("Different objects can have different field selections")
    void testMultipleObjectsWithDifferentFields() {
        SObjectItem contact = new SObjectItem("Contact", "Contact");
        SObjectItem opportunity = new SObjectItem("Opportunity", "Opportunity");
        
        Set<String> accountFields = Set.of("Id", "Name", "Industry");
        Set<String> contactFields = Set.of("Id", "FirstName", "LastName", "Email");
        // Leave opportunity as all fields
        
        item.setSelectedFields(new HashSet<>(accountFields));
        contact.setSelectedFields(new HashSet<>(contactFields));
        
        assertEquals(3, item.getSelectedFields().size(), "Account should have 3 fields");
        assertEquals(4, contact.getSelectedFields().size(), "Contact should have 4 fields");
        assertNull(opportunity.getSelectedFields(), "Opportunity should have all fields");
        
        assertTrue(item.getSelectedFields().contains("Industry"), "Account should have Industry field");
        assertTrue(contact.getSelectedFields().contains("Email"), "Contact should have Email field");
        assertFalse(item.getSelectedFields().contains("Email"), "Account should not have Email field");
    }
    
    @Test
    @DisplayName("Empty field set is different from null")
    void testEmptyVsNullFieldSelection() {
        // Null means all fields
        item.setSelectedFields(null);
        assertFalse(item.hasCustomFieldSelection(), "Null should mean no custom selection");
        
        // Empty set means no fields (edge case)
        item.setSelectedFields(new HashSet<>());
        assertTrue(item.hasCustomFieldSelection(), "Empty set is a custom selection");
        assertTrue(item.getSelectedFields().isEmpty(), "Empty set should have 0 fields");
    }
    
    @Test
    @DisplayName("Field selection works with disabled items")
    void testFieldSelectionWithDisabledItem() {
        Set<String> fields = Set.of("Id", "Name");
        
        item.setSelectedFields(new HashSet<>(fields));
        item.setDisabled(true);
        
        // Disabled items should still keep their field selection
        assertNotNull(item.getSelectedFields(), "Disabled item should keep field selection");
        assertEquals(2, item.getSelectedFields().size(), "Field count should persist");
        assertTrue(item.hasCustomFieldSelection(), "Should still have custom selection");
    }
}
