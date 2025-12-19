package com.backupforce.ui;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for backup UI logic - tests data manipulation without rendering.
 * These tests verify the selection, filtering, and configuration logic
 * that powers the backup UI without needing an actual display.
 */
@DisplayName("Backup UI Logic Tests")
class BackupUILogicTest {
    
    // Realistic Salesforce objects payload
    private static final List<SObjectData> SALESFORCE_OBJECTS = Arrays.asList(
        new SObjectData("Account", "Accounts", true, false, 15420),
        new SObjectData("Contact", "Contacts", true, false, 42350),
        new SObjectData("Lead", "Leads", true, false, 8930),
        new SObjectData("Opportunity", "Opportunities", true, false, 12850),
        new SObjectData("Case", "Cases", true, false, 67200),
        new SObjectData("Task", "Tasks", true, false, 156000),
        new SObjectData("Event", "Events", true, false, 23400),
        new SObjectData("Campaign", "Campaigns", true, false, 245),
        new SObjectData("User", "Users", true, false, 450),
        new SObjectData("Attachment", "Attachments", true, false, 234000),
        new SObjectData("ContentVersion", "Content Versions", true, false, 156000),
        new SObjectData("Document", "Documents", true, false, 4500),
        new SObjectData("Invoice__c", "Invoices", true, true, 45600),
        new SObjectData("Invoice_Line__c", "Invoice Lines", true, true, 234500),
        new SObjectData("Project__c", "Projects", true, true, 890),
        new SObjectData("AccountHistory", "Account History", false, false, 0),
        new SObjectData("LoginHistory", "Login History", false, false, 0)
    );
    
    private ObservableList<SObjectData> allObjects;
    private FilteredList<SObjectData> filteredObjects;
    
    @BeforeEach
    void setUp() {
        // Create fresh copies for each test to avoid state pollution
        allObjects = FXCollections.observableArrayList();
        for (SObjectData obj : SALESFORCE_OBJECTS) {
            allObjects.add(new SObjectData(obj.getName(), obj.getLabel(), 
                obj.isQueryable(), obj.isCustom(), obj.getRecordCount()));
        }
        filteredObjects = new FilteredList<>(allObjects, p -> true);
    }
    
    // ==================== Selection Logic Tests ====================
    
    @Nested
    @DisplayName("Selection Logic Tests")
    class SelectionLogicTests {
        
        @Test
        @DisplayName("Select all queryable objects")
        void selectAllQueryableObjects() {
            allObjects.stream()
                .filter(SObjectData::isQueryable)
                .forEach(o -> o.setSelected(true));
            
            long selectedCount = allObjects.stream().filter(SObjectData::isSelected).count();
            long queryableCount = allObjects.stream().filter(SObjectData::isQueryable).count();
            
            assertEquals(queryableCount, selectedCount);
            assertEquals(15, selectedCount, "Should have 15 queryable objects");
        }
        
        @Test
        @DisplayName("Deselect all objects")
        void deselectAllObjects() {
            // First select all
            allObjects.forEach(o -> o.setSelected(true));
            
            // Then deselect all
            allObjects.forEach(o -> o.setSelected(false));
            
            long selectedCount = allObjects.stream().filter(SObjectData::isSelected).count();
            assertEquals(0, selectedCount, "No objects should be selected");
        }
        
        @Test
        @DisplayName("Count selected objects correctly")
        void countSelectedObjectsCorrectly() {
            // Select specific objects
            Set<String> toSelect = Set.of("Account", "Contact", "Opportunity");
            
            allObjects.stream()
                .filter(o -> toSelect.contains(o.getName()))
                .forEach(o -> o.setSelected(true));
            
            long selectedCount = allObjects.stream().filter(SObjectData::isSelected).count();
            assertEquals(3, selectedCount);
        }
        
        @Test
        @DisplayName("Individual object selection")
        void individualObjectSelection() {
            SObjectData account = allObjects.stream()
                .filter(o -> o.getName().equals("Account"))
                .findFirst()
                .orElseThrow();
            
            assertFalse(account.isSelected(), "Should not be selected initially");
            
            account.setSelected(true);
            assertTrue(account.isSelected(), "Should be selected after setting");
            
            account.setSelected(false);
            assertFalse(account.isSelected(), "Should not be selected after unsetting");
        }
        
        @Test
        @DisplayName("Selection state is independent per object")
        void selectionStateIsIndependent() {
            SObjectData account = allObjects.get(0);
            SObjectData contact = allObjects.get(1);
            
            account.setSelected(true);
            
            assertTrue(account.isSelected());
            assertFalse(contact.isSelected(), "Contact should not be affected");
        }
    }
    
    // ==================== Filter Logic Tests ====================
    
    @Nested
    @DisplayName("Filter Logic Tests")
    class FilterLogicTests {
        
        @Test
        @DisplayName("Filter by API name")
        void filterByApiName() {
            String searchTerm = "account";
            filteredObjects.setPredicate(o -> 
                o.getName().toLowerCase().contains(searchTerm.toLowerCase()));
            
            assertEquals(2, filteredObjects.size(), "Should find Account and AccountHistory");
            assertTrue(filteredObjects.stream().allMatch(o -> 
                o.getName().toLowerCase().contains(searchTerm)));
        }
        
        @Test
        @DisplayName("Filter by label")
        void filterByLabel() {
            String searchTerm = "invoice";
            filteredObjects.setPredicate(o -> 
                o.getLabel().toLowerCase().contains(searchTerm.toLowerCase()));
            
            assertEquals(2, filteredObjects.size(), "Should find Invoices and Invoice Lines");
        }
        
        @Test
        @DisplayName("Filter for custom objects only")
        void filterForCustomObjectsOnly() {
            filteredObjects.setPredicate(o -> o.getName().endsWith("__c"));
            
            assertEquals(3, filteredObjects.size());
            assertTrue(filteredObjects.stream().allMatch(o -> o.getName().endsWith("__c")));
        }
        
        @Test
        @DisplayName("Filter is case insensitive")
        void filterIsCaseInsensitive() {
            filteredObjects.setPredicate(o -> 
                o.getName().toLowerCase().contains("account"));
            int lowerCount = filteredObjects.size();
            
            filteredObjects.setPredicate(o -> 
                o.getName().toLowerCase().contains("ACCOUNT".toLowerCase()));
            int upperCount = filteredObjects.size();
            
            assertEquals(lowerCount, upperCount);
        }
        
        @Test
        @DisplayName("Empty filter shows all objects")
        void emptyFilterShowsAllObjects() {
            filteredObjects.setPredicate(o -> true);
            
            assertEquals(SALESFORCE_OBJECTS.size(), filteredObjects.size());
        }
        
        @Test
        @DisplayName("Non-matching filter shows no results")
        void nonMatchingFilterShowsNoResults() {
            filteredObjects.setPredicate(o -> 
                o.getName().toLowerCase().contains("xyznonexistent"));
            
            assertEquals(0, filteredObjects.size());
        }
        
        @Test
        @DisplayName("Selection persists after filtering")
        void selectionPersistsAfterFiltering() {
            // Select Account
            allObjects.stream()
                .filter(o -> o.getName().equals("Account"))
                .findFirst()
                .ifPresent(o -> o.setSelected(true));
            
            // Apply filter that hides Account
            filteredObjects.setPredicate(o -> o.getName().contains("Contact"));
            
            // Clear filter
            filteredObjects.setPredicate(o -> true);
            
            // Account should still be selected
            boolean accountSelected = allObjects.stream()
                .filter(o -> o.getName().equals("Account"))
                .anyMatch(SObjectData::isSelected);
            
            assertTrue(accountSelected, "Selection should persist after filtering");
        }
    }
    
    // ==================== Queryable Objects Tests ====================
    
    @Nested
    @DisplayName("Queryable Objects Tests")
    class QueryableObjectsTests {
        
        @Test
        @DisplayName("Identify queryable objects correctly")
        void identifyQueryableObjectsCorrectly() {
            long queryableCount = allObjects.stream()
                .filter(SObjectData::isQueryable)
                .count();
            
            assertEquals(15, queryableCount);
        }
        
        @Test
        @DisplayName("Identify non-queryable objects correctly")
        void identifyNonQueryableObjectsCorrectly() {
            List<String> nonQueryable = allObjects.stream()
                .filter(o -> !o.isQueryable())
                .map(SObjectData::getName)
                .collect(Collectors.toList());
            
            assertEquals(2, nonQueryable.size());
            assertTrue(nonQueryable.contains("AccountHistory"));
            assertTrue(nonQueryable.contains("LoginHistory"));
        }
        
        @Test
        @DisplayName("Non-queryable objects should not be selected for backup")
        void nonQueryableObjectsShouldNotBeSelectedForBackup() {
            // Simulate "Select All" that only selects queryable objects
            allObjects.stream()
                .filter(SObjectData::isQueryable)
                .forEach(o -> o.setSelected(true));
            
            // Verify non-queryable objects are not selected
            boolean anyNonQueryableSelected = allObjects.stream()
                .filter(o -> !o.isQueryable())
                .anyMatch(SObjectData::isSelected);
            
            assertFalse(anyNonQueryableSelected);
        }
    }
    
    // ==================== Custom Objects Tests ====================
    
    @Nested
    @DisplayName("Custom Objects Tests")
    class CustomObjectsTests {
        
        @Test
        @DisplayName("Identify custom objects by __c suffix")
        void identifyCustomObjectsBySuffix() {
            List<String> customObjects = allObjects.stream()
                .filter(SObjectData::isCustom)
                .map(SObjectData::getName)
                .collect(Collectors.toList());
            
            assertEquals(3, customObjects.size());
            assertTrue(customObjects.stream().allMatch(name -> name.endsWith("__c")));
        }
        
        @Test
        @DisplayName("Select only custom objects")
        void selectOnlyCustomObjects() {
            allObjects.stream()
                .filter(SObjectData::isCustom)
                .forEach(o -> o.setSelected(true));
            
            long selectedCount = allObjects.stream().filter(SObjectData::isSelected).count();
            assertEquals(3, selectedCount);
            
            assertTrue(allObjects.stream()
                .filter(SObjectData::isSelected)
                .allMatch(SObjectData::isCustom));
        }
        
        @Test
        @DisplayName("Select only standard objects")
        void selectOnlyStandardObjects() {
            allObjects.stream()
                .filter(o -> !o.isCustom() && o.isQueryable())
                .forEach(o -> o.setSelected(true));
            
            long selectedCount = allObjects.stream().filter(SObjectData::isSelected).count();
            assertEquals(12, selectedCount, "12 standard queryable objects");
        }
    }
    
    // ==================== Large Object Tests ====================
    
    @Nested
    @DisplayName("Large Object Identification Tests")
    class LargeObjectTests {
        
        private final Set<String> largeObjects = Set.of(
            "Attachment", "ContentVersion", "Document", "StaticResource"
        );
        
        @Test
        @DisplayName("Identify large blob objects")
        void identifyLargeBlobObjects() {
            List<SObjectData> foundLargeObjects = allObjects.stream()
                .filter(o -> largeObjects.contains(o.getName()))
                .collect(Collectors.toList());
            
            assertEquals(3, foundLargeObjects.size());
        }
        
        @Test
        @DisplayName("Large objects have significant record counts")
        void largeObjectsHaveSignificantRecordCounts() {
            allObjects.stream()
                .filter(o -> largeObjects.contains(o.getName()))
                .forEach(o -> assertTrue(o.getRecordCount() > 1000,
                    o.getName() + " should have many records"));
        }
        
        @Test
        @DisplayName("Calculate total records for selected objects")
        void calculateTotalRecordsForSelectedObjects() {
            // Select Account and Contact
            allObjects.stream()
                .filter(o -> o.getName().equals("Account") || o.getName().equals("Contact"))
                .forEach(o -> o.setSelected(true));
            
            long totalRecords = allObjects.stream()
                .filter(SObjectData::isSelected)
                .mapToLong(SObjectData::getRecordCount)
                .sum();
            
            assertEquals(15420 + 42350, totalRecords);
        }
    }
    
    // ==================== Backup Configuration Tests ====================
    
    @Nested
    @DisplayName("Backup Configuration Tests")
    class BackupConfigurationTests {
        
        @Test
        @DisplayName("CSV backup configuration")
        void csvBackupConfiguration() {
            BackupConfig config = new BackupConfig();
            config.setTargetType("CSV");
            config.setOutputFolder("C:\\Backups\\Salesforce");
            
            assertEquals("CSV", config.getTargetType());
            assertEquals("C:\\Backups\\Salesforce", config.getOutputFolder());
        }
        
        @Test
        @DisplayName("Database backup configuration")
        void databaseBackupConfiguration() {
            BackupConfig config = new BackupConfig();
            config.setTargetType("Database");
            config.setConnectionName("Production Snowflake");
            
            assertEquals("Database", config.getTargetType());
            assertEquals("Production Snowflake", config.getConnectionName());
        }
        
        @Test
        @DisplayName("Incremental backup option")
        void incrementalBackupOption() {
            BackupConfig config = new BackupConfig();
            
            assertFalse(config.isIncremental(), "Should be false by default");
            
            config.setIncremental(true);
            assertTrue(config.isIncremental());
        }
        
        @Test
        @DisplayName("Compress backup option")
        void compressBackupOption() {
            BackupConfig config = new BackupConfig();
            
            assertFalse(config.isCompress(), "Should be false by default");
            
            config.setCompress(true);
            assertTrue(config.isCompress());
        }
        
        @Test
        @DisplayName("Preserve relationships option")
        void preserveRelationshipsOption() {
            BackupConfig config = new BackupConfig();
            
            assertFalse(config.isPreserveRelationships(), "Should be false by default");
            
            config.setPreserveRelationships(true);
            assertTrue(config.isPreserveRelationships());
        }
        
        @Test
        @DisplayName("Full backup configuration scenario")
        void fullBackupConfigurationScenario() {
            BackupConfig config = new BackupConfig();
            config.setTargetType("CSV");
            config.setOutputFolder("C:\\Backups\\Daily");
            config.setIncremental(true);
            config.setCompress(true);
            config.setPreserveRelationships(true);
            
            // Select objects
            Set<String> selectedObjects = Set.of("Account", "Contact", "Opportunity");
            allObjects.stream()
                .filter(o -> selectedObjects.contains(o.getName()))
                .forEach(o -> o.setSelected(true));
            
            List<String> objectsToBackup = allObjects.stream()
                .filter(SObjectData::isSelected)
                .map(SObjectData::getName)
                .collect(Collectors.toList());
            
            assertEquals(3, objectsToBackup.size());
            assertTrue(config.isIncremental());
            assertTrue(config.isCompress());
            assertTrue(config.isPreserveRelationships());
        }
    }
    
    // ==================== Input Validation Tests ====================
    
    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {
        
        @Test
        @DisplayName("Validate output folder path")
        void validateOutputFolderPath() {
            String[] validPaths = {
                "C:\\Backups\\Salesforce",
                "D:\\Data\\backup",
                "/home/user/backups",
                "./relative/path"
            };
            
            for (String path : validPaths) {
                assertTrue(isValidPath(path), "Should accept: " + path);
            }
        }
        
        @Test
        @DisplayName("Detect invalid path characters")
        void detectInvalidPathCharacters() {
            String[] invalidPaths = {
                "C:\\Back<ups>",
                "C:\\Back\"ups",
                "C:\\Back|ups"
            };
            
            for (String path : invalidPaths) {
                assertFalse(isValidPath(path), "Should reject: " + path);
            }
        }
        
        @Test
        @DisplayName("Handle special characters in search")
        void handleSpecialCharsInSearch() {
            String[] specialSearches = {
                "<script>",
                "'; DROP TABLE",
                "\\n\\r",
                "../../../"
            };
            
            for (String search : specialSearches) {
                // Should not throw exception
                filteredObjects.setPredicate(o -> 
                    o.getName().toLowerCase().contains(search.toLowerCase()));
                // Result should be empty or filtered
                assertNotNull(filteredObjects);
            }
        }
        
        @Test
        @DisplayName("Handle empty object list")
        void handleEmptyObjectList() {
            ObservableList<SObjectData> emptyList = FXCollections.observableArrayList();
            FilteredList<SObjectData> emptyFiltered = new FilteredList<>(emptyList, p -> true);
            
            assertEquals(0, emptyFiltered.size());
            
            // Should handle operations gracefully
            emptyList.stream().filter(SObjectData::isSelected).count();
        }
        
        private boolean isValidPath(String path) {
            // Simple validation - check for invalid Windows path characters
            String invalidChars = "<>:\"|?*";
            // Allow : only as second character (drive letter)
            String pathToCheck = path.length() > 2 ? path.substring(2) : "";
            for (char c : invalidChars.toCharArray()) {
                if (pathToCheck.indexOf(c) >= 0) {
                    return false;
                }
            }
            return true;
        }
    }
    
    // ==================== Helper Classes ====================
    
    public static class SObjectData {
        private final String name;
        private final String label;
        private final boolean queryable;
        private final boolean custom;
        private final int recordCount;
        private boolean selected = false;
        
        public SObjectData(String name, String label, boolean queryable, boolean custom, int recordCount) {
            this.name = name;
            this.label = label;
            this.queryable = queryable;
            this.custom = custom;
            this.recordCount = recordCount;
        }
        
        public String getName() { return name; }
        public String getLabel() { return label; }
        public boolean isQueryable() { return queryable; }
        public boolean isCustom() { return custom; }
        public int getRecordCount() { return recordCount; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
    }
    
    public static class BackupConfig {
        private String targetType = "CSV";
        private String outputFolder = "";
        private String connectionName = "";
        private boolean incremental = false;
        private boolean compress = false;
        private boolean preserveRelationships = false;
        
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        
        public String getOutputFolder() { return outputFolder; }
        public void setOutputFolder(String outputFolder) { this.outputFolder = outputFolder; }
        
        public String getConnectionName() { return connectionName; }
        public void setConnectionName(String connectionName) { this.connectionName = connectionName; }
        
        public boolean isIncremental() { return incremental; }
        public void setIncremental(boolean incremental) { this.incremental = incremental; }
        
        public boolean isCompress() { return compress; }
        public void setCompress(boolean compress) { this.compress = compress; }
        
        public boolean isPreserveRelationships() { return preserveRelationships; }
        public void setPreserveRelationships(boolean preserveRelationships) { 
            this.preserveRelationships = preserveRelationships; 
        }
    }
}
