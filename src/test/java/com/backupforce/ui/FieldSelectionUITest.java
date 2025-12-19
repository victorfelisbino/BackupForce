package com.backupforce.ui;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI tests for Field Selection functionality.
 * Uses mock field data to test the field selection table without requiring Salesforce API.
 */
@DisplayName("Field Selection UI Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FieldSelectionUITest extends JavaFxTestBase {
    
    // Realistic Salesforce Account field payloads
    private static final List<MockFieldItem> ACCOUNT_FIELDS = Arrays.asList(
        new MockFieldItem("Id", "Account ID", "id", true, false, true),
        new MockFieldItem("Name", "Account Name", "string", true, false, true),
        new MockFieldItem("AccountNumber", "Account Number", "string", false, false, true),
        new MockFieldItem("Type", "Account Type", "picklist", false, false, true),
        new MockFieldItem("Industry", "Industry", "picklist", false, false, true),
        new MockFieldItem("AnnualRevenue", "Annual Revenue", "currency", false, false, true),
        new MockFieldItem("NumberOfEmployees", "Employees", "int", false, false, true),
        new MockFieldItem("Phone", "Phone", "phone", false, false, true),
        new MockFieldItem("Fax", "Fax", "phone", false, false, true),
        new MockFieldItem("Website", "Website", "url", false, false, true),
        new MockFieldItem("BillingStreet", "Billing Street", "textarea", false, false, true),
        new MockFieldItem("BillingCity", "Billing City", "string", false, false, true),
        new MockFieldItem("BillingState", "Billing State/Province", "string", false, false, true),
        new MockFieldItem("BillingPostalCode", "Billing Zip/Postal Code", "string", false, false, true),
        new MockFieldItem("BillingCountry", "Billing Country", "string", false, false, true),
        new MockFieldItem("ShippingStreet", "Shipping Street", "textarea", false, false, true),
        new MockFieldItem("ShippingCity", "Shipping City", "string", false, false, true),
        new MockFieldItem("Description", "Description", "textarea", false, false, true),
        new MockFieldItem("OwnerId", "Owner ID", "reference", true, false, true),
        new MockFieldItem("CreatedDate", "Created Date", "datetime", false, false, true),
        new MockFieldItem("CreatedById", "Created By ID", "reference", false, false, true),
        new MockFieldItem("LastModifiedDate", "Last Modified Date", "datetime", false, false, true),
        new MockFieldItem("LastModifiedById", "Last Modified By ID", "reference", false, false, true),
        new MockFieldItem("IsDeleted", "Deleted", "boolean", false, false, true),
        new MockFieldItem("ParentId", "Parent Account ID", "reference", false, false, true),
        // Custom fields
        new MockFieldItem("Customer_Tier__c", "Customer Tier", "picklist", false, true, true),
        new MockFieldItem("SLA__c", "SLA", "picklist", false, true, true),
        new MockFieldItem("Primary_Contact__c", "Primary Contact", "reference", false, true, true),
        new MockFieldItem("Contract_End_Date__c", "Contract End Date", "date", false, true, true),
        new MockFieldItem("Total_Opportunities__c", "Total Opportunities", "double", false, true, true),
        // Non-queryable fields
        new MockFieldItem("LastViewedDate", "Last Viewed Date", "datetime", false, false, false),
        new MockFieldItem("LastReferencedDate", "Last Referenced Date", "datetime", false, false, false)
    );
    
    // Contact fields with different characteristics
    private static final List<MockFieldItem> CONTACT_FIELDS = Arrays.asList(
        new MockFieldItem("Id", "Contact ID", "id", true, false, true),
        new MockFieldItem("FirstName", "First Name", "string", false, false, true),
        new MockFieldItem("LastName", "Last Name", "string", true, false, true),
        new MockFieldItem("Email", "Email", "email", false, false, true),
        new MockFieldItem("Phone", "Phone", "phone", false, false, true),
        new MockFieldItem("MobilePhone", "Mobile", "phone", false, false, true),
        new MockFieldItem("AccountId", "Account ID", "reference", false, false, true),
        new MockFieldItem("Title", "Title", "string", false, false, true),
        new MockFieldItem("Department", "Department", "string", false, false, true),
        new MockFieldItem("MailingStreet", "Mailing Street", "textarea", false, false, true),
        new MockFieldItem("MailingCity", "Mailing City", "string", false, false, true),
        new MockFieldItem("Birthdate", "Birthdate", "date", false, false, true),
        new MockFieldItem("LeadSource", "Lead Source", "picklist", false, false, true),
        new MockFieldItem("Preferred_Language__c", "Preferred Language", "picklist", false, true, true),
        new MockFieldItem("NPS_Score__c", "NPS Score", "double", false, true, true)
    );
    
    private TableView<MockFieldItem> fieldTable;
    private ObservableList<MockFieldItem> allFields;
    private TextField searchField;
    private Label selectionCountLabel;
    
    @Override
    protected Scene createTestScene() throws Exception {
        // Create a mock field selection UI
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setId("fieldSelectionContent");
        
        // Initialize fields list
        allFields = FXCollections.observableArrayList(ACCOUNT_FIELDS);
        
        // Search bar
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchField = new TextField();
        searchField.setId("fieldSearchField");
        searchField.setPromptText("Search fields...");
        searchField.setPrefWidth(300);
        
        selectionCountLabel = new Label("0 of 0 fields selected");
        selectionCountLabel.setId("selectionCountLabel");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        searchBox.getChildren().addAll(searchField, spacer, selectionCountLabel);
        
        // Field table
        fieldTable = new TableView<>();
        fieldTable.setId("fieldTable");
        fieldTable.setEditable(true);
        VBox.setVgrow(fieldTable, Priority.ALWAYS);
        
        // Checkbox column
        TableColumn<MockFieldItem, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);
        
        // Name column
        TableColumn<MockFieldItem, String> nameCol = new TableColumn<>("API Name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        nameCol.setPrefWidth(180);
        
        // Label column
        TableColumn<MockFieldItem, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLabel()));
        labelCol.setPrefWidth(180);
        
        // Type column
        TableColumn<MockFieldItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType()));
        typeCol.setPrefWidth(100);
        
        // Info column
        TableColumn<MockFieldItem, String> infoCol = new TableColumn<>("Info");
        infoCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getInfo()));
        infoCol.setPrefWidth(120);
        
        fieldTable.getColumns().addAll(selectCol, nameCol, labelCol, typeCol, infoCol);
        fieldTable.setItems(allFields);
        
        // Button bar
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        Button selectAllBtn = new Button("Select All");
        selectAllBtn.setId("selectAllBtn");
        selectAllBtn.setOnAction(e -> {
            allFields.forEach(f -> f.setSelected(true));
            updateSelectionCount();
        });
        
        Button deselectAllBtn = new Button("Deselect All");
        deselectAllBtn.setId("deselectAllBtn");
        deselectAllBtn.setOnAction(e -> {
            allFields.forEach(f -> {
                if (!f.isRequired()) f.setSelected(false);
            });
            updateSelectionCount();
        });
        
        Button standardFieldsBtn = new Button("Standard Fields");
        standardFieldsBtn.setId("standardFieldsBtn");
        standardFieldsBtn.setOnAction(e -> {
            allFields.forEach(f -> f.setSelected(!f.isCustom() || f.isRequired()));
            updateSelectionCount();
        });
        
        Button customFieldsBtn = new Button("Custom Fields Only");
        customFieldsBtn.setId("customFieldsBtn");
        customFieldsBtn.setOnAction(e -> {
            allFields.forEach(f -> f.setSelected(f.isCustom() || f.isRequired()));
            updateSelectionCount();
        });
        
        Button essentialFieldsBtn = new Button("Essential Only");
        essentialFieldsBtn.setId("essentialFieldsBtn");
        essentialFieldsBtn.setOnAction(e -> {
            Set<String> essential = Set.of("Id", "Name", "CreatedDate", "LastModifiedDate", "CreatedById", "LastModifiedById", "OwnerId");
            allFields.forEach(f -> f.setSelected(essential.contains(f.getName()) || f.isRequired()));
            updateSelectionCount();
        });
        
        buttonBox.getChildren().addAll(selectAllBtn, deselectAllBtn, standardFieldsBtn, customFieldsBtn, essentialFieldsBtn);
        
        // Setup search filter
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterFields(newVal));
        
        // Setup selection count updates
        allFields.forEach(f -> f.selectedProperty().addListener((obs, oldVal, newVal) -> updateSelectionCount()));
        
        content.getChildren().addAll(searchBox, fieldTable, buttonBox);
        
        updateSelectionCount();
        
        return new Scene(content, 800, 600);
    }
    
    private void filterFields(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            fieldTable.setItems(allFields);
        } else {
            String lowerSearch = searchText.toLowerCase();
            ObservableList<MockFieldItem> filtered = allFields.filtered(f ->
                f.getName().toLowerCase().contains(lowerSearch) ||
                f.getLabel().toLowerCase().contains(lowerSearch) ||
                f.getType().toLowerCase().contains(lowerSearch)
            );
            fieldTable.setItems(filtered);
        }
    }
    
    private void updateSelectionCount() {
        long selected = allFields.stream().filter(MockFieldItem::isSelected).count();
        long total = allFields.stream().filter(MockFieldItem::isQueryable).count();
        runOnFxThread(() -> selectionCountLabel.setText(String.format("%d of %d fields selected", selected, total)));
    }
    
    // ==================== Initial State Tests ====================
    
    @Nested
    @DisplayName("Initial State Tests")
    class InitialStateTests {
        
        @Test
        @DisplayName("Field table should be visible with fields")
        void fieldTableShouldBeVisibleWithFields() {
            assertNodeVisible("fieldTable", "Field table");
            
            TableView<?> table = findById("fieldTable");
            assertTrue(table.getItems().size() > 0, "Table should have fields");
        }
        
        @Test
        @DisplayName("Should display correct number of Account fields")
        void shouldDisplayCorrectNumberOfFields() {
            assertEquals(ACCOUNT_FIELDS.size(), getTableRowCount("fieldTable"));
        }
        
        @Test
        @DisplayName("Search field should be empty initially")
        void searchFieldShouldBeEmptyInitially() {
            TextField search = findById("fieldSearchField");
            assertTrue(search.getText().isEmpty(), "Search should be empty");
        }
        
        @Test
        @DisplayName("All buttons should be visible")
        void allButtonsShouldBeVisible() {
            assertNodeVisible("selectAllBtn", "Select All button");
            assertNodeVisible("deselectAllBtn", "Deselect All button");
            assertNodeVisible("standardFieldsBtn", "Standard Fields button");
            assertNodeVisible("customFieldsBtn", "Custom Fields button");
            assertNodeVisible("essentialFieldsBtn", "Essential Fields button");
        }
        
        @Test
        @DisplayName("Table should have correct columns")
        void tableShouldHaveCorrectColumns() {
            TableView<MockFieldItem> table = findById("fieldTable");
            assertEquals(5, table.getColumns().size(), "Should have 5 columns");
            assertEquals("Select", table.getColumns().get(0).getText());
            assertEquals("API Name", table.getColumns().get(1).getText());
            assertEquals("Label", table.getColumns().get(2).getText());
            assertEquals("Type", table.getColumns().get(3).getText());
            assertEquals("Info", table.getColumns().get(4).getText());
        }
    }
    
    // ==================== Selection Tests ====================
    
    @Nested
    @DisplayName("Selection Tests")
    class SelectionTests {
        
        @Test
        @DisplayName("Select All should select all fields")
        void selectAllShouldSelectAllFields() {
            clickOn("#selectAllBtn");
            waitForFxEvents();
            
            long selectedCount = allFields.stream().filter(MockFieldItem::isSelected).count();
            assertEquals(allFields.size(), selectedCount, "All fields should be selected");
        }
        
        @Test
        @DisplayName("Deselect All should deselect non-required fields")
        void deselectAllShouldDeselectNonRequiredFields() {
            // First select all
            clickOn("#selectAllBtn");
            waitForFxEvents();
            
            // Then deselect all
            clickOn("#deselectAllBtn");
            waitForFxEvents();
            
            // Only required fields should remain selected
            long selectedCount = allFields.stream().filter(MockFieldItem::isSelected).count();
            long requiredCount = allFields.stream().filter(MockFieldItem::isRequired).count();
            assertEquals(requiredCount, selectedCount, "Only required fields should remain selected");
        }
        
        @Test
        @DisplayName("Standard Fields should select only standard fields")
        void standardFieldsShouldSelectOnlyStandard() {
            clickOn("#standardFieldsBtn");
            waitForFxEvents();
            
            // All standard fields should be selected
            boolean allStandardSelected = allFields.stream()
                .filter(f -> !f.isCustom())
                .allMatch(MockFieldItem::isSelected);
            assertTrue(allStandardSelected, "All standard fields should be selected");
            
            // Custom fields (except required) should not be selected
            boolean noCustomSelected = allFields.stream()
                .filter(f -> f.isCustom() && !f.isRequired())
                .noneMatch(MockFieldItem::isSelected);
            assertTrue(noCustomSelected, "Custom fields should not be selected");
        }
        
        @Test
        @DisplayName("Custom Fields should select only custom fields")
        void customFieldsShouldSelectOnlyCustom() {
            // First deselect all
            clickOn("#deselectAllBtn");
            waitForFxEvents();
            
            clickOn("#customFieldsBtn");
            waitForFxEvents();
            
            // All custom fields should be selected
            boolean allCustomSelected = allFields.stream()
                .filter(MockFieldItem::isCustom)
                .allMatch(MockFieldItem::isSelected);
            assertTrue(allCustomSelected, "All custom fields should be selected");
        }
        
        @Test
        @DisplayName("Essential Fields should select only essential fields")
        void essentialFieldsShouldSelectOnlyEssential() {
            clickOn("#essentialFieldsBtn");
            waitForFxEvents();
            
            Set<String> essential = Set.of("Id", "Name", "CreatedDate", "LastModifiedDate", "CreatedById", "LastModifiedById", "OwnerId");
            
            // Essential fields should be selected
            boolean essentialSelected = allFields.stream()
                .filter(f -> essential.contains(f.getName()))
                .allMatch(MockFieldItem::isSelected);
            assertTrue(essentialSelected, "Essential fields should be selected");
            
            // Non-essential, non-required fields should not be selected
            long selectedNonEssential = allFields.stream()
                .filter(f -> !essential.contains(f.getName()) && !f.isRequired())
                .filter(MockFieldItem::isSelected)
                .count();
            assertEquals(0, selectedNonEssential, "Non-essential fields should not be selected");
        }
        
        @Test
        @DisplayName("Selection count should update correctly")
        void selectionCountShouldUpdateCorrectly() {
            clickOn("#selectAllBtn");
            waitForFxEvents();
            
            Label countLabel = findById("selectionCountLabel");
            assertTrue(countLabel.getText().contains(String.valueOf(ACCOUNT_FIELDS.size())), 
                "Count should show all fields selected");
            
            clickOn("#deselectAllBtn");
            waitForFxEvents();
            
            long requiredCount = allFields.stream().filter(MockFieldItem::isRequired).count();
            assertTrue(countLabel.getText().contains(String.valueOf(requiredCount)),
                "Count should show only required fields selected");
        }
    }
    
    // ==================== Search/Filter Tests ====================
    
    @Nested
    @DisplayName("Search and Filter Tests")
    class SearchFilterTests {
        
        @Test
        @DisplayName("Search by field name should filter results")
        void searchByNameShouldFilterResults() {
            clickOn("#fieldSearchField").write("Phone");
            waitForFxEvents();
            
            TableView<MockFieldItem> table = findById("fieldTable");
            // Should show Phone and MobilePhone
            assertTrue(table.getItems().size() > 0, "Should have matching results");
            assertTrue(table.getItems().stream().allMatch(f -> 
                f.getName().toLowerCase().contains("phone") || 
                f.getLabel().toLowerCase().contains("phone")),
                "All results should match search term");
        }
        
        @Test
        @DisplayName("Search by field label should filter results")
        void searchByLabelShouldFilterResults() {
            clickOn("#fieldSearchField").write("Billing");
            waitForFxEvents();
            
            TableView<MockFieldItem> table = findById("fieldTable");
            assertTrue(table.getItems().size() > 0, "Should have billing address fields");
            assertTrue(table.getItems().stream().allMatch(f -> 
                f.getLabel().toLowerCase().contains("billing")),
                "All results should be billing fields");
        }
        
        @Test
        @DisplayName("Search by field type should filter results")
        void searchByTypeShouldFilterResults() {
            clickOn("#fieldSearchField").write("reference");
            waitForFxEvents();
            
            TableView<MockFieldItem> table = findById("fieldTable");
            assertTrue(table.getItems().size() > 0, "Should have reference fields");
            assertTrue(table.getItems().stream().allMatch(f -> 
                f.getType().toLowerCase().contains("reference")),
                "All results should be reference type");
        }
        
        @Test
        @DisplayName("Search should be case insensitive")
        void searchShouldBeCaseInsensitive() {
            clickOn("#fieldSearchField").write("PHONE");
            waitForFxEvents();
            
            TableView<MockFieldItem> table = findById("fieldTable");
            int upperCaseResults = table.getItems().size();
            
            // Clear and search lowercase
            runOnFxThread(() -> searchField.clear());
            waitForFxEvents();
            
            clickOn("#fieldSearchField").write("phone");
            waitForFxEvents();
            
            assertEquals(upperCaseResults, table.getItems().size(), "Case should not matter");
        }
        
        @Test
        @DisplayName("Empty search should show all fields")
        void emptySearchShouldShowAllFields() {
            clickOn("#fieldSearchField").write("test");
            waitForFxEvents();
            
            // Clear search
            runOnFxThread(() -> searchField.clear());
            waitForFxEvents();
            
            TableView<MockFieldItem> table = findById("fieldTable");
            assertEquals(ACCOUNT_FIELDS.size(), table.getItems().size(), "Should show all fields");
        }
        
        @Test
        @DisplayName("Non-matching search should show no results")
        void nonMatchingSearchShouldShowNoResults() {
            clickOn("#fieldSearchField").write("xyznonexistent123");
            waitForFxEvents();
            
            TableView<MockFieldItem> table = findById("fieldTable");
            assertEquals(0, table.getItems().size(), "Should show no results");
        }
    }
    
    // ==================== Field Type Tests with Real Payloads ====================
    
    @Nested
    @DisplayName("Field Type Tests")
    class FieldTypeTests {
        
        @Test
        @DisplayName("Should identify required fields correctly")
        void shouldIdentifyRequiredFieldsCorrectly() {
            long requiredCount = allFields.stream().filter(MockFieldItem::isRequired).count();
            assertTrue(requiredCount > 0, "Should have required fields");
            
            // Id and Name are typically required
            assertTrue(allFields.stream().anyMatch(f -> f.getName().equals("Id") && f.isRequired()),
                "Id should be required");
        }
        
        @Test
        @DisplayName("Should identify custom fields correctly")
        void shouldIdentifyCustomFieldsCorrectly() {
            long customCount = allFields.stream().filter(MockFieldItem::isCustom).count();
            assertEquals(5, customCount, "Should have 5 custom fields (ending in __c)");
            
            // All custom fields should end with __c
            assertTrue(allFields.stream()
                .filter(MockFieldItem::isCustom)
                .allMatch(f -> f.getName().endsWith("__c")),
                "Custom fields should end with __c");
        }
        
        @Test
        @DisplayName("Should have various field types")
        void shouldHaveVariousFieldTypes() {
            Set<String> types = new HashSet<>();
            allFields.forEach(f -> types.add(f.getType()));
            
            assertTrue(types.contains("string"), "Should have string fields");
            assertTrue(types.contains("reference"), "Should have reference fields");
            assertTrue(types.contains("datetime"), "Should have datetime fields");
            assertTrue(types.contains("picklist"), "Should have picklist fields");
        }
    }
    
    // ==================== Negative Tests ====================
    
    @Nested
    @DisplayName("Negative Tests")
    class NegativeTests {
        
        @Test
        @DisplayName("Should handle special characters in search")
        void shouldHandleSpecialCharsInSearch() {
            String[] specialSearches = {"<script>", "'; DROP TABLE", "\\n\\r", "😀"};
            
            for (String search : specialSearches) {
                runOnFxThread(() -> searchField.clear());
                waitForFxEvents();
                
                clickOn("#fieldSearchField").write(search);
                waitForFxEvents();
                
                // Should not crash, just show no results
                TableView<MockFieldItem> table = findById("fieldTable");
                assertNotNull(table.getItems(), "Table should still have items list");
            }
        }
        
        @Test
        @DisplayName("Should handle rapid button clicks")
        void shouldHandleRapidButtonClicks() {
            for (int i = 0; i < 10; i++) {
                clickOn("#selectAllBtn");
                clickOn("#deselectAllBtn");
            }
            waitForFxEvents();
            
            // Should not crash and selection should be consistent
            long requiredCount = allFields.stream().filter(MockFieldItem::isRequired).count();
            long selectedCount = allFields.stream().filter(MockFieldItem::isSelected).count();
            assertEquals(requiredCount, selectedCount, "Should have only required fields selected after rapid toggles");
        }
        
        @Test
        @DisplayName("Should maintain selection after filtering")
        void shouldMaintainSelectionAfterFiltering() {
            // Select all
            clickOn("#selectAllBtn");
            waitForFxEvents();
            
            // Filter to show only some fields
            clickOn("#fieldSearchField").write("Phone");
            waitForFxEvents();
            
            // Clear filter
            runOnFxThread(() -> searchField.clear());
            waitForFxEvents();
            
            // All fields should still be selected
            long selectedCount = allFields.stream().filter(MockFieldItem::isSelected).count();
            assertEquals(allFields.size(), selectedCount, "Selection should be maintained after filtering");
        }
    }
    
    // ==================== Helper Classes ====================
    
    public static class MockFieldItem {
        private final StringProperty name;
        private final StringProperty label;
        private final StringProperty type;
        private final BooleanProperty selected;
        private final boolean required;
        private final boolean custom;
        private final boolean queryable;
        
        public MockFieldItem(String name, String label, String type, boolean required, boolean custom, boolean queryable) {
            this.name = new SimpleStringProperty(name);
            this.label = new SimpleStringProperty(label);
            this.type = new SimpleStringProperty(type);
            this.required = required;
            this.custom = custom;
            this.queryable = queryable;
            this.selected = new SimpleBooleanProperty(required); // Required fields start selected
        }
        
        public String getName() { return name.get(); }
        public StringProperty nameProperty() { return name; }
        
        public String getLabel() { return label.get(); }
        public StringProperty labelProperty() { return label; }
        
        public String getType() { return type.get(); }
        public StringProperty typeProperty() { return type; }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public BooleanProperty selectedProperty() { return selected; }
        
        public boolean isRequired() { return required; }
        public boolean isCustom() { return custom; }
        public boolean isQueryable() { return queryable; }
        
        public String getInfo() {
            List<String> info = new ArrayList<>();
            if (required) info.add("Required");
            if (custom) info.add("Custom");
            if (!queryable) info.add("Non-queryable");
            return String.join(", ", info);
        }
    }
}
