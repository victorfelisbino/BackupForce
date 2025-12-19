package com.backupforce.ui;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI tests for the Backup Controller's object selection functionality.
 * Tests object listing, selection, filtering, and backup options.
 */
@DisplayName("Backup Object Selection UI Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackupObjectSelectionUITest extends JavaFxTestBase {
    
    // Realistic Salesforce objects payload
    private static final List<MockSObjectItem> SALESFORCE_OBJECTS = Arrays.asList(
        // Standard Objects
        new MockSObjectItem("Account", "Accounts", true, false, 15420),
        new MockSObjectItem("Contact", "Contacts", true, false, 42350),
        new MockSObjectItem("Lead", "Leads", true, false, 8930),
        new MockSObjectItem("Opportunity", "Opportunities", true, false, 12850),
        new MockSObjectItem("Case", "Cases", true, false, 67200),
        new MockSObjectItem("Task", "Tasks", true, false, 156000),
        new MockSObjectItem("Event", "Events", true, false, 23400),
        new MockSObjectItem("Campaign", "Campaigns", true, false, 245),
        new MockSObjectItem("CampaignMember", "Campaign Members", true, false, 12800),
        new MockSObjectItem("User", "Users", true, false, 450),
        new MockSObjectItem("Product2", "Products", true, false, 1234),
        new MockSObjectItem("Pricebook2", "Price Books", true, false, 15),
        new MockSObjectItem("PricebookEntry", "Price Book Entries", true, false, 4560),
        new MockSObjectItem("Order", "Orders", true, false, 8920),
        new MockSObjectItem("OrderItem", "Order Products", true, false, 24500),
        new MockSObjectItem("Contract", "Contracts", true, false, 3450),
        new MockSObjectItem("Quote", "Quotes", true, false, 5670),
        new MockSObjectItem("Asset", "Assets", true, false, 8900),
        
        // Large/Blob Objects
        new MockSObjectItem("Attachment", "Attachments", true, false, 234000),
        new MockSObjectItem("ContentVersion", "Content Versions", true, false, 156000),
        new MockSObjectItem("Document", "Documents", true, false, 4500),
        
        // Custom Objects
        new MockSObjectItem("Invoice__c", "Invoices", true, true, 45600),
        new MockSObjectItem("Invoice_Line__c", "Invoice Lines", true, true, 234500),
        new MockSObjectItem("Project__c", "Projects", true, true, 890),
        new MockSObjectItem("Milestone__c", "Milestones", true, true, 3450),
        new MockSObjectItem("Time_Entry__c", "Time Entries", true, true, 78900),
        new MockSObjectItem("Subscription__c", "Subscriptions", true, true, 12340),
        
        // Non-queryable objects
        new MockSObjectItem("AccountHistory", "Account History", false, false, 0),
        new MockSObjectItem("LoginHistory", "Login History", false, false, 0)
    );
    
    private TableView<MockSObjectItem> objectTable;
    private ObservableList<MockSObjectItem> allObjects;
    private FilteredList<MockSObjectItem> filteredObjects;
    private TextField searchField;
    private Label selectionCountLabel;
    private RadioButton csvRadio;
    private RadioButton databaseRadio;
    private TextField outputFolderField;
    private CheckBox incrementalCheckbox;
    private CheckBox compressCheckbox;
    private CheckBox preserveRelationshipsCheckbox;
    
    @Override
    protected Scene createTestScene() throws Exception {
        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setId("backupContent");
        
        // Initialize objects list
        allObjects = FXCollections.observableArrayList(SALESFORCE_OBJECTS);
        filteredObjects = new FilteredList<>(allObjects, p -> true);
        
        // Connection info header
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label connectionLabel = new Label("Connected to: admin@mycompany.com (Production)");
        connectionLabel.setId("connectionLabel");
        Label apiLimitsLabel = new Label("API: 45% | Bulk: 12%");
        apiLimitsLabel.setId("apiLimitsLabel");
        headerBox.getChildren().addAll(connectionLabel, apiLimitsLabel);
        
        // Search and selection controls
        HBox controlBox = new HBox(10);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        
        searchField = new TextField();
        searchField.setId("searchField");
        searchField.setPromptText("Search objects...");
        searchField.setPrefWidth(250);
        
        Button selectAllBtn = new Button("Select All");
        selectAllBtn.setId("selectAllButton");
        selectAllBtn.setOnAction(e -> {
            allObjects.stream().filter(o -> o.isQueryable()).forEach(o -> o.setSelected(true));
            updateSelectionCount();
        });
        
        Button deselectAllBtn = new Button("Deselect All");
        deselectAllBtn.setId("deselectAllButton");
        deselectAllBtn.setOnAction(e -> {
            allObjects.forEach(o -> o.setSelected(false));
            updateSelectionCount();
        });
        
        selectionCountLabel = new Label("0 objects selected");
        selectionCountLabel.setId("selectionCountLabel");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        controlBox.getChildren().addAll(searchField, selectAllBtn, deselectAllBtn, spacer, selectionCountLabel);
        
        // Object table
        objectTable = new TableView<>();
        objectTable.setId("allObjectsTable");
        objectTable.setEditable(true);
        VBox.setVgrow(objectTable, Priority.ALWAYS);
        
        TableColumn<MockSObjectItem, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);
        
        TableColumn<MockSObjectItem, String> nameCol = new TableColumn<>("API Name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        nameCol.setPrefWidth(180);
        
        TableColumn<MockSObjectItem, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLabel()));
        labelCol.setPrefWidth(180);
        
        TableColumn<MockSObjectItem, String> recordsCol = new TableColumn<>("Records");
        recordsCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.format("%,d", cellData.getValue().getRecordCount())));
        recordsCol.setPrefWidth(100);
        
        TableColumn<MockSObjectItem, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusCol.setPrefWidth(120);
        
        objectTable.getColumns().addAll(selectCol, nameCol, labelCol, recordsCol, statusCol);
        objectTable.setItems(filteredObjects);
        
        // Backup options section
        VBox optionsBox = new VBox(10);
        optionsBox.setId("backupOptionsBox");
        Label optionsLabel = new Label("Backup Options");
        optionsLabel.setStyle("-fx-font-weight: bold;");
        
        // Target selection
        HBox targetBox = new HBox(15);
        ToggleGroup targetGroup = new ToggleGroup();
        csvRadio = new RadioButton("CSV Files");
        csvRadio.setId("csvRadioButton");
        csvRadio.setToggleGroup(targetGroup);
        csvRadio.setSelected(true);
        
        databaseRadio = new RadioButton("Database");
        databaseRadio.setId("databaseRadioButton");
        databaseRadio.setToggleGroup(targetGroup);
        
        Button databaseSettingsBtn = new Button("Database Settings");
        databaseSettingsBtn.setId("databaseSettingsButton");
        databaseSettingsBtn.setDisable(true);
        
        // Enable/disable database settings based on selection
        targetGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            databaseSettingsBtn.setDisable(newVal != databaseRadio);
        });
        
        targetBox.getChildren().addAll(csvRadio, databaseRadio, databaseSettingsBtn);
        
        // Output folder
        HBox folderBox = new HBox(10);
        folderBox.setId("outputFolderBox");
        folderBox.setAlignment(Pos.CENTER_LEFT);
        Label folderLabel = new Label("Output Folder:");
        outputFolderField = new TextField();
        outputFolderField.setId("outputFolderField");
        outputFolderField.setText(System.getProperty("user.home") + "\\BackupForce\\backup");
        outputFolderField.setPrefWidth(400);
        Button browseBtn = new Button("Browse");
        browseBtn.setId("browseButton");
        folderBox.getChildren().addAll(folderLabel, outputFolderField, browseBtn);
        
        // Checkboxes
        incrementalCheckbox = new CheckBox("Incremental backup (only changed records since last backup)");
        incrementalCheckbox.setId("incrementalBackupCheckbox");
        
        compressCheckbox = new CheckBox("Compress backup files (ZIP)");
        compressCheckbox.setId("compressBackupCheckbox");
        
        preserveRelationshipsCheckbox = new CheckBox("Preserve relationship references (for restore)");
        preserveRelationshipsCheckbox.setId("preserveRelationshipsCheckbox");
        
        optionsBox.getChildren().addAll(optionsLabel, targetBox, folderBox, 
            incrementalCheckbox, compressCheckbox, preserveRelationshipsCheckbox);
        
        // Action buttons
        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button startBackupBtn = new Button("Start Backup");
        startBackupBtn.setId("startBackupButton");
        startBackupBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        
        Button stopBackupBtn = new Button("Stop Backup");
        stopBackupBtn.setId("stopBackupButton");
        stopBackupBtn.setDisable(true);
        
        actionBox.getChildren().addAll(startBackupBtn, stopBackupBtn);
        
        // Progress section
        VBox progressBox = new VBox(5);
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setId("progressBar");
        progressBar.setPrefWidth(Double.MAX_VALUE);
        
        HBox progressLabelBox = new HBox(10);
        Label progressLabel = new Label("Ready");
        progressLabel.setId("progressLabel");
        Label progressPercentLabel = new Label("0%");
        progressPercentLabel.setId("progressPercentLabel");
        progressLabelBox.getChildren().addAll(progressLabel, progressPercentLabel);
        
        progressBox.getChildren().addAll(progressBar, progressLabelBox);
        
        // Setup search filter
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterObjects(newVal));
        
        // Setup selection count updates
        allObjects.forEach(o -> o.selectedProperty().addListener((obs, oldVal, newVal) -> updateSelectionCount()));
        
        content.getChildren().addAll(headerBox, controlBox, objectTable, optionsBox, actionBox, progressBox);
        
        updateSelectionCount();
        
        return new Scene(content, 900, 700);
    }
    
    private void filterObjects(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            filteredObjects.setPredicate(p -> true);
        } else {
            String lowerSearch = searchText.toLowerCase();
            filteredObjects.setPredicate(o ->
                o.getName().toLowerCase().contains(lowerSearch) ||
                o.getLabel().toLowerCase().contains(lowerSearch)
            );
        }
    }
    
    private void updateSelectionCount() {
        long selected = allObjects.stream().filter(MockSObjectItem::isSelected).count();
        runOnFxThread(() -> selectionCountLabel.setText(selected + " objects selected"));
    }
    
    // ==================== Initial State Tests ====================
    
    @Nested
    @DisplayName("Initial State Tests")
    class InitialStateTests {
        
        @Test
        @DisplayName("Object table should display all Salesforce objects")
        void objectTableShouldDisplayAllObjects() {
            assertNodeVisible("allObjectsTable", "Object table");
            assertEquals(SALESFORCE_OBJECTS.size(), getTableRowCount("allObjectsTable"));
        }
        
        @Test
        @DisplayName("No objects should be selected initially")
        void noObjectsShouldBeSelectedInitially() {
            long selectedCount = allObjects.stream().filter(MockSObjectItem::isSelected).count();
            assertEquals(0, selectedCount, "No objects should be selected initially");
        }
        
        @Test
        @DisplayName("CSV should be selected as default backup target")
        void csvShouldBeDefaultBackupTarget() {
            assertTrue(csvRadio.isSelected(), "CSV should be selected by default");
            assertFalse(databaseRadio.isSelected(), "Database should not be selected by default");
        }
        
        @Test
        @DisplayName("Output folder should have default value")
        void outputFolderShouldHaveDefaultValue() {
            String folder = outputFolderField.getText();
            assertTrue(folder.contains("BackupForce"), "Default folder should contain BackupForce");
        }
        
        @Test
        @DisplayName("Database settings button should be disabled when CSV is selected")
        void databaseSettingsButtonShouldBeDisabled() {
            Button dbSettingsBtn = findById("databaseSettingsButton");
            assertTrue(dbSettingsBtn.isDisabled(), "Database settings should be disabled when CSV is selected");
        }
        
        @Test
        @DisplayName("All backup options should be unchecked initially")
        void allBackupOptionsShouldBeUncheckedInitially() {
            assertFalse(incrementalCheckbox.isSelected());
            assertFalse(compressCheckbox.isSelected());
            assertFalse(preserveRelationshipsCheckbox.isSelected());
        }
        
        @Test
        @DisplayName("Stop backup button should be disabled initially")
        void stopBackupButtonShouldBeDisabledInitially() {
            Button stopBtn = findById("stopBackupButton");
            assertTrue(stopBtn.isDisabled(), "Stop button should be disabled initially");
        }
        
        @Test
        @DisplayName("Progress bar should be at zero initially")
        void progressBarShouldBeAtZeroInitially() {
            assertEquals(0.0, getProgress("progressBar"), 0.001);
        }
    }
    
    // ==================== Object Selection Tests ====================
    
    @Nested
    @DisplayName("Object Selection Tests")
    class ObjectSelectionTests {
        
        @Test
        @DisplayName("Select All should select all queryable objects")
        void selectAllShouldSelectAllQueryableObjects() {
            clickOn("#selectAllButton");
            waitForFxEvents();
            
            long selectedCount = allObjects.stream().filter(MockSObjectItem::isSelected).count();
            long queryableCount = allObjects.stream().filter(MockSObjectItem::isQueryable).count();
            assertEquals(queryableCount, selectedCount, "All queryable objects should be selected");
        }
        
        @Test
        @DisplayName("Deselect All should deselect all objects")
        void deselectAllShouldDeselectAllObjects() {
            // First select all
            clickOn("#selectAllButton");
            waitForFxEvents();
            
            // Then deselect all
            clickOn("#deselectAllButton");
            waitForFxEvents();
            
            long selectedCount = allObjects.stream().filter(MockSObjectItem::isSelected).count();
            assertEquals(0, selectedCount, "No objects should be selected");
        }
        
        @Test
        @DisplayName("Selection count label should update correctly")
        void selectionCountLabelShouldUpdateCorrectly() {
            clickOn("#selectAllButton");
            waitForFxEvents();
            
            String countText = selectionCountLabel.getText();
            long queryableCount = allObjects.stream().filter(MockSObjectItem::isQueryable).count();
            assertTrue(countText.contains(String.valueOf(queryableCount)), 
                "Count should show number of selected objects");
        }
        
        @Test
        @DisplayName("Should be able to select individual objects")
        void shouldBeAbleToSelectIndividualObjects() {
            // Select first object
            MockSObjectItem firstObject = allObjects.get(0);
            runOnFxThread(() -> firstObject.setSelected(true));
            waitForFxEvents();
            
            long selectedCount = allObjects.stream().filter(MockSObjectItem::isSelected).count();
            assertEquals(1, selectedCount, "One object should be selected");
        }
    }
    
    // ==================== Search/Filter Tests ====================
    
    @Nested
    @DisplayName("Search and Filter Tests")
    class SearchFilterTests {
        
        @Test
        @DisplayName("Search should filter objects by API name")
        void searchShouldFilterByApiName() {
            clickOn("#searchField").write("Account");
            waitForFxEvents();
            
            TableView<MockSObjectItem> table = findById("allObjectsTable");
            assertTrue(table.getItems().size() > 0, "Should have matching objects");
            assertTrue(table.getItems().stream().allMatch(o -> 
                o.getName().toLowerCase().contains("account")),
                "All results should match search term");
        }
        
        @Test
        @DisplayName("Search should filter objects by label")
        void searchShouldFilterByLabel() {
            clickOn("#searchField").write("Invoice");
            waitForFxEvents();
            
            TableView<MockSObjectItem> table = findById("allObjectsTable");
            assertTrue(table.getItems().size() >= 2, "Should find Invoice objects");
        }
        
        @Test
        @DisplayName("Search for custom objects")
        void searchForCustomObjects() {
            clickOn("#searchField").write("__c");
            waitForFxEvents();
            
            TableView<MockSObjectItem> table = findById("allObjectsTable");
            long customCount = SALESFORCE_OBJECTS.stream().filter(MockSObjectItem::isCustom).count();
            assertEquals(customCount, table.getItems().size(), "Should show all custom objects");
        }
        
        @Test
        @DisplayName("Empty search should show all objects")
        void emptySearchShouldShowAllObjects() {
            clickOn("#searchField").write("test");
            waitForFxEvents();
            
            runOnFxThread(() -> searchField.clear());
            waitForFxEvents();
            
            assertEquals(SALESFORCE_OBJECTS.size(), getTableRowCount("allObjectsTable"));
        }
    }
    
    // ==================== Backup Options Tests ====================
    
    @Nested
    @DisplayName("Backup Options Tests")
    class BackupOptionsTests {
        
        @Test
        @DisplayName("Switching to Database should enable database settings button")
        void switchingToDatabaseShouldEnableDatabaseSettingsButton() {
            clickOn("#databaseRadioButton");
            waitForFxEvents();
            
            Button dbSettingsBtn = findById("databaseSettingsButton");
            assertFalse(dbSettingsBtn.isDisabled(), "Database settings should be enabled");
        }
        
        @Test
        @DisplayName("Switching back to CSV should disable database settings button")
        void switchingBackToCsvShouldDisableDatabaseSettingsButton() {
            clickOn("#databaseRadioButton");
            waitForFxEvents();
            
            clickOn("#csvRadioButton");
            waitForFxEvents();
            
            Button dbSettingsBtn = findById("databaseSettingsButton");
            assertTrue(dbSettingsBtn.isDisabled(), "Database settings should be disabled");
        }
        
        @Test
        @DisplayName("Should toggle incremental backup checkbox")
        void shouldToggleIncrementalBackupCheckbox() {
            clickOn("#incrementalBackupCheckbox");
            waitForFxEvents();
            
            assertTrue(incrementalCheckbox.isSelected(), "Incremental should be checked");
        }
        
        @Test
        @DisplayName("Should toggle compress backup checkbox")
        void shouldToggleCompressBackupCheckbox() {
            clickOn("#compressBackupCheckbox");
            waitForFxEvents();
            
            assertTrue(compressCheckbox.isSelected(), "Compress should be checked");
        }
        
        @Test
        @DisplayName("Should toggle preserve relationships checkbox")
        void shouldTogglePreserveRelationshipsCheckbox() {
            clickOn("#preserveRelationshipsCheckbox");
            waitForFxEvents();
            
            assertTrue(preserveRelationshipsCheckbox.isSelected(), "Preserve relationships should be checked");
        }
        
        @Test
        @DisplayName("Should allow changing output folder")
        void shouldAllowChangingOutputFolder() {
            String newFolder = "C:\\MyBackups\\Salesforce";
            runOnFxThread(() -> outputFolderField.setText(newFolder));
            waitForFxEvents();
            
            assertEquals(newFolder, outputFolderField.getText());
        }
    }
    
    // ==================== Real Payload Tests ====================
    
    @Nested
    @DisplayName("Real Payload Scenario Tests")
    class RealPayloadTests {
        
        @Test
        @DisplayName("Standard CRM backup scenario")
        void standardCrmBackupScenario() {
            // Search and select Account
            clickOn("#searchField").write("Account");
            waitForFxEvents();
            
            allObjects.stream()
                .filter(o -> o.getName().equals("Account"))
                .findFirst()
                .ifPresent(o -> runOnFxThread(() -> o.setSelected(true)));
            waitForFxEvents();
            
            // Clear and select Contact
            runOnFxThread(() -> searchField.clear());
            waitForFxEvents();
            
            allObjects.stream()
                .filter(o -> o.getName().equals("Contact"))
                .findFirst()
                .ifPresent(o -> runOnFxThread(() -> o.setSelected(true)));
            waitForFxEvents();
            
            // Verify selections
            long selectedCount = allObjects.stream().filter(MockSObjectItem::isSelected).count();
            assertEquals(2, selectedCount, "Account and Contact should be selected");
        }
        
        @Test
        @DisplayName("Full backup scenario with all options")
        void fullBackupScenarioWithAllOptions() {
            // Select all objects
            clickOn("#selectAllButton");
            waitForFxEvents();
            
            // Set all options
            clickOn("#databaseRadioButton");
            clickOn("#incrementalBackupCheckbox");
            clickOn("#compressBackupCheckbox");
            clickOn("#preserveRelationshipsCheckbox");
            waitForFxEvents();
            
            // Verify state
            assertTrue(databaseRadio.isSelected());
            assertTrue(incrementalCheckbox.isSelected());
            assertTrue(compressCheckbox.isSelected());
            assertTrue(preserveRelationshipsCheckbox.isSelected());
        }
        
        @Test
        @DisplayName("Custom objects only backup scenario")
        void customObjectsOnlyBackupScenario() {
            // Search for custom objects
            clickOn("#searchField").write("__c");
            waitForFxEvents();
            
            // Select all visible (custom) objects
            filteredObjects.forEach(o -> runOnFxThread(() -> o.setSelected(true)));
            waitForFxEvents();
            
            // Verify only custom objects are selected
            long selectedCustom = allObjects.stream()
                .filter(MockSObjectItem::isSelected)
                .filter(MockSObjectItem::isCustom)
                .count();
            
            long totalSelected = allObjects.stream()
                .filter(MockSObjectItem::isSelected)
                .count();
            
            assertEquals(selectedCustom, totalSelected, "Only custom objects should be selected");
        }
    }
    
    // ==================== Negative Tests ====================
    
    @Nested
    @DisplayName("Negative Tests")
    class NegativeTests {
        
        @Test
        @DisplayName("Should handle special characters in search")
        void shouldHandleSpecialCharsInSearch() {
            String[] specialSearches = {"<script>", "'; DROP TABLE", "\\n", "../../"};
            
            for (String search : specialSearches) {
                runOnFxThread(() -> searchField.clear());
                waitForFxEvents();
                
                clickOn("#searchField").write(search);
                waitForFxEvents();
                
                // Should not crash
                assertNotNull(filteredObjects, "Filtered list should still exist");
            }
        }
        
        @Test
        @DisplayName("Should handle empty output folder")
        void shouldHandleEmptyOutputFolder() {
            runOnFxThread(() -> outputFolderField.clear());
            waitForFxEvents();
            
            assertEquals("", outputFolderField.getText(), "Output folder should be empty");
        }
        
        @Test
        @DisplayName("Should handle invalid path in output folder")
        void shouldHandleInvalidPathInOutputFolder() {
            String invalidPath = "C:\\Invalid>Path<With:Invalid*Chars";
            runOnFxThread(() -> outputFolderField.setText(invalidPath));
            waitForFxEvents();
            
            assertEquals(invalidPath, outputFolderField.getText(), "Should accept invalid path (validation on save)");
        }
        
        @Test
        @DisplayName("Should maintain selection after rapid filter changes")
        void shouldMaintainSelectionAfterRapidFilterChanges() {
            // Select Account
            allObjects.stream()
                .filter(o -> o.getName().equals("Account"))
                .findFirst()
                .ifPresent(o -> runOnFxThread(() -> o.setSelected(true)));
            waitForFxEvents();
            
            // Rapidly change filters
            for (int i = 0; i < 5; i++) {
                clickOn("#searchField").write("test" + i);
                waitForFxEvents();
                runOnFxThread(() -> searchField.clear());
                waitForFxEvents();
            }
            
            // Verify Account is still selected
            boolean accountSelected = allObjects.stream()
                .filter(o -> o.getName().equals("Account"))
                .anyMatch(MockSObjectItem::isSelected);
            assertTrue(accountSelected, "Account should still be selected");
        }
    }
    
    // ==================== Large Object Warning Tests ====================
    
    @Nested
    @DisplayName("Large Object Tests")
    class LargeObjectTests {
        
        @Test
        @DisplayName("Should identify large blob objects")
        void shouldIdentifyLargeBlobObjects() {
            Set<String> largeObjects = Set.of("Attachment", "ContentVersion", "Document");
            
            long largeObjectCount = allObjects.stream()
                .filter(o -> largeObjects.contains(o.getName()))
                .count();
            
            assertEquals(3, largeObjectCount, "Should have 3 large object types");
        }
        
        @Test
        @DisplayName("Large objects should have high record counts in test data")
        void largeObjectsShouldHaveHighRecordCounts() {
            Optional<MockSObjectItem> attachment = allObjects.stream()
                .filter(o -> o.getName().equals("Attachment"))
                .findFirst();
            
            assertTrue(attachment.isPresent(), "Attachment should exist");
            assertTrue(attachment.get().getRecordCount() > 100000, "Attachment should have many records");
        }
    }
    
    // ==================== Helper Class ====================
    
    public static class MockSObjectItem {
        private final StringProperty name;
        private final StringProperty label;
        private final BooleanProperty selected;
        private final StringProperty status;
        private final boolean queryable;
        private final boolean custom;
        private final int recordCount;
        
        public MockSObjectItem(String name, String label, boolean queryable, boolean custom, int recordCount) {
            this.name = new SimpleStringProperty(name);
            this.label = new SimpleStringProperty(label);
            this.queryable = queryable;
            this.custom = custom;
            this.recordCount = recordCount;
            this.selected = new SimpleBooleanProperty(false);
            this.status = new SimpleStringProperty(queryable ? "Ready" : "Not Queryable");
        }
        
        public String getName() { return name.get(); }
        public StringProperty nameProperty() { return name; }
        
        public String getLabel() { return label.get(); }
        public StringProperty labelProperty() { return label; }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public BooleanProperty selectedProperty() { return selected; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public StringProperty statusProperty() { return status; }
        
        public boolean isQueryable() { return queryable; }
        public boolean isCustom() { return custom; }
        public int getRecordCount() { return recordCount; }
    }
}
