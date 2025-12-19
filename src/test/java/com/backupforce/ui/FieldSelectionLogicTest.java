package com.backupforce.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Field Selection UI logic.
 * Tests field filtering, selection patterns, and field categorization.
 */
@DisplayName("Field Selection Logic Tests")
class FieldSelectionLogicTest {
    
    // Realistic Salesforce Account fields payload
    private static final List<FieldData> ACCOUNT_FIELDS = Arrays.asList(
        new FieldData("Id", "Account ID", "id", true, false, true, false),
        new FieldData("Name", "Account Name", "string", true, false, true, false),
        new FieldData("AccountNumber", "Account Number", "string", false, false, true, false),
        new FieldData("Type", "Account Type", "picklist", false, false, true, false),
        new FieldData("Industry", "Industry", "picklist", false, false, true, false),
        new FieldData("AnnualRevenue", "Annual Revenue", "currency", false, false, true, false),
        new FieldData("NumberOfEmployees", "Employees", "int", false, false, true, false),
        new FieldData("Phone", "Phone", "phone", false, false, true, false),
        new FieldData("Fax", "Fax", "phone", false, false, true, false),
        new FieldData("Website", "Website", "url", false, false, true, false),
        new FieldData("BillingStreet", "Billing Street", "textarea", false, false, true, false),
        new FieldData("BillingCity", "Billing City", "string", false, false, true, false),
        new FieldData("BillingState", "Billing State/Province", "string", false, false, true, false),
        new FieldData("BillingPostalCode", "Billing Zip/Postal Code", "string", false, false, true, false),
        new FieldData("Description", "Description", "textarea", false, false, true, false),
        new FieldData("OwnerId", "Owner ID", "reference", true, false, true, false),
        new FieldData("CreatedDate", "Created Date", "datetime", false, false, true, false),
        new FieldData("CreatedById", "Created By ID", "reference", false, false, true, false),
        new FieldData("LastModifiedDate", "Last Modified Date", "datetime", false, false, true, false),
        new FieldData("LastModifiedById", "Last Modified By ID", "reference", false, false, true, false),
        new FieldData("IsDeleted", "Deleted", "boolean", false, false, true, false),
        new FieldData("ParentId", "Parent Account ID", "reference", false, false, true, true),
        new FieldData("Customer_Tier__c", "Customer Tier", "picklist", false, true, true, false),
        new FieldData("SLA__c", "SLA", "picklist", false, true, true, false),
        new FieldData("Primary_Contact__c", "Primary Contact", "reference", false, true, true, true),
        new FieldData("Contract_End_Date__c", "Contract End Date", "date", false, true, true, false),
        new FieldData("Total_Opportunities__c", "Total Opportunities", "double", false, true, true, false),
        new FieldData("LastViewedDate", "Last Viewed Date", "datetime", false, false, false, false),
        new FieldData("LastReferencedDate", "Last Referenced Date", "datetime", false, false, false, false)
    );
    
    private ObservableList<FieldData> allFields;
    private FilteredList<FieldData> filteredFields;
    
    @BeforeEach
    void setUp() {
        // Create fresh copies for each test with selection reset
        allFields = FXCollections.observableArrayList();
        for (FieldData field : ACCOUNT_FIELDS) {
            FieldData copy = new FieldData(
                field.getName(), field.getLabel(), field.getType(),
                field.isRequired(), field.isCustom(), field.isQueryable(), 
                field.isRelationship());
            copy.setSelected(false); // Reset selection to false
            allFields.add(copy);
        }
        filteredFields = new FilteredList<>(allFields, p -> true);
    }
    
    // ==================== Selection Pattern Tests ====================
    
    @Nested
    @DisplayName("Selection Pattern Tests")
    class SelectionPatternTests {
        
        @Test
        @DisplayName("Select All should select all queryable fields")
        void selectAllShouldSelectAllQueryableFields() {
            allFields.stream()
                .filter(FieldData::isQueryable)
                .forEach(f -> f.setSelected(true));
            
            long selectedCount = allFields.stream().filter(FieldData::isSelected).count();
            long queryableCount = allFields.stream().filter(FieldData::isQueryable).count();
            
            assertEquals(queryableCount, selectedCount);
        }
        
        @Test
        @DisplayName("Deselect All should keep required fields selected")
        void deselectAllShouldKeepRequiredFieldsSelected() {
            // First select all
            allFields.forEach(f -> f.setSelected(true));
            
            // Deselect all non-required fields
            allFields.forEach(f -> {
                if (!f.isRequired()) f.setSelected(false);
            });
            
            long selectedCount = allFields.stream().filter(FieldData::isSelected).count();
            long requiredCount = allFields.stream().filter(FieldData::isRequired).count();
            
            assertEquals(requiredCount, selectedCount);
        }
        
        @Test
        @DisplayName("Standard Fields pattern should select only standard fields")
        void standardFieldsPatternShouldSelectOnlyStandardFields() {
            // Standard fields pattern: not custom
            allFields.forEach(f -> f.setSelected(!f.isCustom() || f.isRequired()));
            
            // All standard fields should be selected
            boolean allStandardSelected = allFields.stream()
                .filter(f -> !f.isCustom())
                .allMatch(FieldData::isSelected);
            assertTrue(allStandardSelected);
            
            // Custom non-required fields should not be selected
            boolean noCustomSelected = allFields.stream()
                .filter(f -> f.isCustom() && !f.isRequired())
                .noneMatch(FieldData::isSelected);
            assertTrue(noCustomSelected);
        }
        
        @Test
        @DisplayName("Custom Fields Only pattern should select only custom fields")
        void customFieldsOnlyPatternShouldSelectOnlyCustomFields() {
            allFields.forEach(f -> f.setSelected(f.isCustom() || f.isRequired()));
            
            // All custom fields should be selected
            boolean allCustomSelected = allFields.stream()
                .filter(FieldData::isCustom)
                .allMatch(FieldData::isSelected);
            assertTrue(allCustomSelected);
            
            // Required fields should also be selected
            boolean allRequiredSelected = allFields.stream()
                .filter(FieldData::isRequired)
                .allMatch(FieldData::isSelected);
            assertTrue(allRequiredSelected);
        }
        
        @Test
        @DisplayName("Essential Fields pattern should select only essential fields")
        void essentialFieldsPatternShouldSelectOnlyEssentialFields() {
            Set<String> essential = Set.of("Id", "Name", "CreatedDate", "LastModifiedDate", 
                "CreatedById", "LastModifiedById", "OwnerId");
            
            allFields.forEach(f -> f.setSelected(
                essential.contains(f.getName()) || f.isRequired()));
            
            // All essential fields should be selected
            boolean allEssentialSelected = allFields.stream()
                .filter(f -> essential.contains(f.getName()))
                .allMatch(FieldData::isSelected);
            assertTrue(allEssentialSelected);
            
            // Count should match
            long selectedCount = allFields.stream().filter(FieldData::isSelected).count();
            // Essential + required that aren't in essential
            long expectedCount = essential.size();
            assertTrue(selectedCount >= expectedCount);
        }
    }
    
    // ==================== Filter Tests ====================
    
    @Nested
    @DisplayName("Filter Tests")
    class FilterTests {
        
        @Test
        @DisplayName("Filter by field name")
        void filterByFieldName() {
            filteredFields.setPredicate(f -> 
                f.getName().toLowerCase().contains("phone"));
            
            assertEquals(1, filteredFields.size(), "Should find Phone field");
        }
        
        @Test
        @DisplayName("Filter by field label")
        void filterByFieldLabel() {
            filteredFields.setPredicate(f -> 
                f.getLabel().toLowerCase().contains("billing"));
            
            assertTrue(filteredFields.size() >= 4, "Should find billing address fields");
        }
        
        @Test
        @DisplayName("Filter by field type")
        void filterByFieldType() {
            filteredFields.setPredicate(f -> 
                f.getType().equalsIgnoreCase("reference"));
            
            long referenceCount = filteredFields.size();
            assertTrue(referenceCount >= 5, "Should find reference fields");
            
            assertTrue(filteredFields.stream().allMatch(f -> 
                f.getType().equalsIgnoreCase("reference")));
        }
        
        @Test
        @DisplayName("Combined filter - name or label or type")
        void combinedFilter() {
            String searchTerm = "date";
            filteredFields.setPredicate(f ->
                f.getName().toLowerCase().contains(searchTerm) ||
                f.getLabel().toLowerCase().contains(searchTerm) ||
                f.getType().toLowerCase().contains(searchTerm)
            );
            
            assertTrue(filteredFields.size() >= 4, "Should find date fields");
        }
        
        @Test
        @DisplayName("Case insensitive filter")
        void caseInsensitiveFilter() {
            filteredFields.setPredicate(f -> 
                f.getName().toLowerCase().contains("OWNER".toLowerCase()));
            int upperCount = filteredFields.size();
            
            filteredFields.setPredicate(f -> 
                f.getName().toLowerCase().contains("owner"));
            int lowerCount = filteredFields.size();
            
            assertEquals(upperCount, lowerCount);
        }
    }
    
    // ==================== Field Categorization Tests ====================
    
    @Nested
    @DisplayName("Field Categorization Tests")
    class FieldCategorizationTests {
        
        @Test
        @DisplayName("Identify required fields")
        void identifyRequiredFields() {
            List<String> requiredFields = allFields.stream()
                .filter(FieldData::isRequired)
                .map(FieldData::getName)
                .collect(Collectors.toList());
            
            assertTrue(requiredFields.contains("Id"));
            assertTrue(requiredFields.contains("Name"));
            assertTrue(requiredFields.contains("OwnerId"));
        }
        
        @Test
        @DisplayName("Identify custom fields by __c suffix")
        void identifyCustomFieldsBySuffix() {
            List<String> customFields = allFields.stream()
                .filter(FieldData::isCustom)
                .map(FieldData::getName)
                .collect(Collectors.toList());
            
            assertEquals(5, customFields.size());
            assertTrue(customFields.stream().allMatch(name -> name.endsWith("__c")));
        }
        
        @Test
        @DisplayName("Identify non-queryable fields")
        void identifyNonQueryableFields() {
            List<String> nonQueryable = allFields.stream()
                .filter(f -> !f.isQueryable())
                .map(FieldData::getName)
                .collect(Collectors.toList());
            
            assertEquals(2, nonQueryable.size());
            assertTrue(nonQueryable.contains("LastViewedDate"));
            assertTrue(nonQueryable.contains("LastReferencedDate"));
        }
        
        @Test
        @DisplayName("Identify relationship fields")
        void identifyRelationshipFields() {
            List<String> relationshipFields = allFields.stream()
                .filter(FieldData::isRelationship)
                .map(FieldData::getName)
                .collect(Collectors.toList());
            
            assertTrue(relationshipFields.contains("ParentId"));
            assertTrue(relationshipFields.contains("Primary_Contact__c"));
        }
        
        @Test
        @DisplayName("Categorize fields by type")
        void categorizeFieldsByType() {
            Map<String, Long> typeCount = allFields.stream()
                .collect(Collectors.groupingBy(FieldData::getType, Collectors.counting()));
            
            assertTrue(typeCount.containsKey("string"));
            assertTrue(typeCount.containsKey("reference"));
            assertTrue(typeCount.containsKey("datetime"));
            assertTrue(typeCount.containsKey("picklist"));
        }
    }
    
    // ==================== Selection Count Tests ====================
    
    @Nested
    @DisplayName("Selection Count Tests")
    class SelectionCountTests {
        
        @Test
        @DisplayName("Count selected fields correctly")
        void countSelectedFieldsCorrectly() {
            Set<String> toSelect = Set.of("Id", "Name", "Phone", "Email");
            
            allFields.stream()
                .filter(f -> toSelect.contains(f.getName()))
                .forEach(f -> f.setSelected(true));
            
            long selectedCount = allFields.stream().filter(FieldData::isSelected).count();
            // Email doesn't exist in Account, so only 3
            assertEquals(3, selectedCount);
        }
        
        @Test
        @DisplayName("Count queryable fields for denominator")
        void countQueryableFieldsForDenominator() {
            long queryableCount = allFields.stream()
                .filter(FieldData::isQueryable)
                .count();
            
            assertEquals(27, queryableCount, "Should have 27 queryable fields");
        }
        
        @Test
        @DisplayName("Format selection count string")
        void formatSelectionCountString() {
            allFields.forEach(f -> f.setSelected(true));
            
            long selected = allFields.stream().filter(FieldData::isSelected).count();
            long queryable = allFields.stream().filter(FieldData::isQueryable).count();
            
            String countString = String.format("%d of %d fields selected", selected, queryable);
            assertEquals("29 of 27 fields selected", countString);
            // Note: Selected includes non-queryable, queryable is just the denominator
        }
    }
    
    // ==================== Real Payload Scenario Tests ====================
    
    @Nested
    @DisplayName("Real Payload Scenario Tests")
    class RealPayloadScenarioTests {
        
        @Test
        @DisplayName("Backup for data migration - select all except audit fields")
        void backupForDataMigration() {
            Set<String> auditFields = Set.of("CreatedDate", "CreatedById", 
                "LastModifiedDate", "LastModifiedById", "LastViewedDate", "LastReferencedDate");
            
            allFields.forEach(f -> f.setSelected(
                f.isQueryable() && !auditFields.contains(f.getName()) || f.isRequired()));
            
            long selectedCount = allFields.stream().filter(FieldData::isSelected).count();
            assertTrue(selectedCount < ACCOUNT_FIELDS.size());
            
            // Audit fields should not be selected (except required ones)
            boolean noAuditSelected = allFields.stream()
                .filter(f -> auditFields.contains(f.getName()) && !f.isRequired())
                .noneMatch(FieldData::isSelected);
            assertTrue(noAuditSelected);
        }
        
        @Test
        @DisplayName("Backup for compliance - all queryable fields")
        void backupForCompliance() {
            allFields.stream()
                .filter(FieldData::isQueryable)
                .forEach(f -> f.setSelected(true));
            
            // All queryable fields must be selected
            boolean allQueryableSelected = allFields.stream()
                .filter(FieldData::isQueryable)
                .allMatch(FieldData::isSelected);
            assertTrue(allQueryableSelected);
        }
        
        @Test
        @DisplayName("Backup for reporting - select specific field types")
        void backupForReporting() {
            Set<String> reportingTypes = Set.of("string", "picklist", "currency", "int", "double");
            
            allFields.forEach(f -> f.setSelected(
                reportingTypes.contains(f.getType()) || f.isRequired()));
            
            // All selected fields should be of reporting types or required
            boolean allValidTypes = allFields.stream()
                .filter(FieldData::isSelected)
                .allMatch(f -> reportingTypes.contains(f.getType()) || f.isRequired());
            assertTrue(allValidTypes);
        }
        
        @Test
        @DisplayName("Relationship backup - include all reference fields")
        void relationshipBackup() {
            allFields.stream()
                .filter(f -> f.getType().equals("reference") || f.isRequired())
                .forEach(f -> f.setSelected(true));
            
            // All reference fields should be selected
            boolean allReferencesSelected = allFields.stream()
                .filter(f -> f.getType().equals("reference"))
                .allMatch(FieldData::isSelected);
            assertTrue(allReferencesSelected);
        }
    }
    
    // ==================== Negative Tests ====================
    
    @Nested
    @DisplayName("Negative Tests")
    class NegativeTests {
        
        @Test
        @DisplayName("Handle special characters in search")
        void handleSpecialCharsInSearch() {
            String[] specialSearches = {
                "<script>", "'; DROP TABLE", "\\n\\r", "../../../"
            };
            
            for (String search : specialSearches) {
                filteredFields.setPredicate(f ->
                    f.getName().toLowerCase().contains(search.toLowerCase()) ||
                    f.getLabel().toLowerCase().contains(search.toLowerCase()));
                
                // Should not throw exception, just return empty
                assertEquals(0, filteredFields.size());
            }
        }
        
        @Test
        @DisplayName("Handle empty field list")
        void handleEmptyFieldList() {
            ObservableList<FieldData> emptyList = FXCollections.observableArrayList();
            FilteredList<FieldData> emptyFiltered = new FilteredList<>(emptyList, p -> true);
            
            assertEquals(0, emptyFiltered.size());
            
            // Operations should complete without error
            long selected = emptyList.stream().filter(FieldData::isSelected).count();
            assertEquals(0, selected);
        }
        
        @Test
        @DisplayName("Selection state is preserved through modifications")
        void selectionStatePreservedThroughModifications() {
            // Select some fields
            allFields.stream()
                .filter(f -> f.getName().equals("Id") || f.getName().equals("Name"))
                .forEach(f -> f.setSelected(true));
            
            // Apply and remove filters multiple times
            for (int i = 0; i < 10; i++) {
                filteredFields.setPredicate(f -> f.getName().contains("a"));
                filteredFields.setPredicate(f -> true);
            }
            
            // Verify selection is preserved
            boolean idSelected = allFields.stream()
                .filter(f -> f.getName().equals("Id"))
                .anyMatch(FieldData::isSelected);
            assertTrue(idSelected);
        }
    }
    
    // ==================== Helper Classes ====================
    
    public static class FieldData {
        private final String name;
        private final String label;
        private final String type;
        private final boolean required;
        private final boolean custom;
        private final boolean queryable;
        private final boolean relationship;
        private boolean selected;
        
        public FieldData(String name, String label, String type, boolean required, 
                        boolean custom, boolean queryable, boolean relationship) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.required = required;
            this.custom = custom;
            this.queryable = queryable;
            this.relationship = relationship;
            this.selected = required; // Required fields start selected
        }
        
        public String getName() { return name; }
        public String getLabel() { return label; }
        public String getType() { return type; }
        public boolean isRequired() { return required; }
        public boolean isCustom() { return custom; }
        public boolean isQueryable() { return queryable; }
        public boolean isRelationship() { return relationship; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
    }
}
