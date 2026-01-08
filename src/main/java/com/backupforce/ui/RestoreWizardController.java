package com.backupforce.ui;

import com.backupforce.config.AppConfig;
import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.config.JdbcHelper;
import com.backupforce.config.SSLHelper;
import com.backupforce.restore.DatabaseScanner;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Wizard-based controller for guided data restoration.
 * Steps: 1) Choose Source → 2) Find Records → 3) Relationships → 4) Review & Restore
 */
public class RestoreWizardController {
    
    private static final Logger logger = LoggerFactory.getLogger(RestoreWizardController.class);
    
    // Use centralized config - no more hardcoded magic numbers
    private static final int MAX_SEARCH_RESULTS = AppConfig.MAX_SEARCH_RESULTS;
    private static final int MAX_RECORDS_PER_TABLE = AppConfig.MAX_RECORDS_PER_TABLE;
    
    // ============================================
    // WIZARD STATE
    // ============================================
    private int currentStep = 1;
    private enum SourceType { NONE, DATABASE, FOLDER }
    private SourceType sourceType = SourceType.NONE;
    private SavedConnection selectedConnection;
    private File selectedFolder;
    
    // Step indicators
    @FXML private VBox step1Indicator, step2Indicator, step3Indicator, step4Indicator;
    @FXML private StackPane contentStack;
    @FXML private VBox step1Content, step2Content, step3Content, step4Content;
    @FXML private VBox loadingOverlay;
    @FXML private Label loadingLabel, loadingDetailLabel;
    
    // Navigation
    @FXML private Button backBtn, nextBtn, cancelBtn;
    @FXML private Label statusLabel;
    
    // ============================================
    // STEP 1: Source Selection
    // ============================================
    @FXML private VBox databaseCard, folderCard;
    @FXML private ComboBox<SavedConnection> databaseCombo;
    @FXML private Button browseFolderBtn;
    @FXML private Label step1StatusLabel;
    
    // ============================================
    // STEP 2: Find Records
    // ============================================
    @FXML private Label sourceInfoLabel, selectedObjectLabel, recordCountLabel, objectCountLabel, selectionLabel;
    @FXML private TextField objectFilterField, whereClauseField;
    @FXML private CheckBox loadAllRecordsCheck;
    @FXML private ListView<BackupObject> objectListView;
    @FXML private TableView<RecordRow> resultsTable;
    @FXML private TableColumn<RecordRow, Boolean> selectCol;
    @FXML private TableColumn<RecordRow, String> nameCol, idCol, createdCol;
    @FXML private Button searchBtn;
    
    // Data for Step 2
    private ObservableList<BackupObject> backupObjects = FXCollections.observableArrayList();
    private FilteredList<BackupObject> filteredObjects;
    private ObservableList<RecordRow> searchResults = FXCollections.observableArrayList();
    private BackupObject selectedObject;
    
    // ============================================
    // STEP 3: Relationships
    // ============================================
    @FXML private Label parentSummaryLabel, relatedCountLabel, totalInMemoryLabel, detailsTitle;
    @FXML private TreeView<TreeNodeItem> relationshipTree;
    @FXML private TableView<FieldValue> detailsTable;
    @FXML private TableColumn<FieldValue, String> fieldNameCol, fieldValueCol;
    
    // Data for Step 3
    private final Map<String, List<Map<String, Object>>> recordsByTable = new LinkedHashMap<>();
    private final Map<String, String> lookupFieldByTable = new LinkedHashMap<>();
    private final Map<String, Set<String>> selectedRelatedRecordIds = new LinkedHashMap<>();
    
    // ============================================
    // STEP 4: Review & Restore
    // ============================================
    @FXML private Label totalRecordsNumber, totalObjectsNumber, apiCallsNumber, destinationOrgLabel;
    @FXML private RadioButton insertModeRadio, upsertModeRadio, updateModeRadio;
    @FXML private CheckBox useIdMappingCheck, stopOnErrorCheck, previewOnlyCheck;
    @FXML private VBox warningPanel, progressPanel;
    @FXML private Label warningText, progressPercentLabel, progressStatusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TextArea logArea;
    
    // ============================================
    // INNER CLASSES
    // ============================================
    
    /** Represents a backup object/table */
    public static class BackupObject {
        private final String tableName;
        private final String objectName;
        private final long recordCount;
        private final String schema;
        
        public BackupObject(String tableName, String objectName, long recordCount, String schema) {
            this.tableName = tableName;
            this.objectName = objectName;
            this.recordCount = recordCount;
            this.schema = schema;
        }
        
        public String getTableName() { return tableName; }
        public String getObjectName() { return objectName; }
        public long getRecordCount() { return recordCount; }
        public String getSchema() { return schema; }
        
        @Override
        public String toString() {
            return String.format("%s (%,d)", objectName, recordCount);
        }
    }
    
    /** Represents a record row in search results */
    public static class RecordRow {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final Map<String, Object> data;
        private final String id;
        private final String name;
        private final String created;
        
        public RecordRow(Map<String, Object> data) {
            this.data = data;
            this.id = getFieldValue(data, "ID", "Id", "id");
            this.name = getFieldValue(data, "NAME", "Name", "SUBJECT", "TITLE", "CASENUMBER");
            this.created = getFieldValue(data, "CREATEDDATE", "CreatedDate", "createddate");
        }
        
        private String getFieldValue(Map<String, Object> data, String... keys) {
            for (String key : keys) {
                Object val = data.get(key);
                if (val != null) return val.toString();
            }
            return "";
        }
        
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean val) { selected.set(val); }
        public Map<String, Object> getData() { return data; }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getCreated() { return created; }
    }
    
    /** Tree node for relationship tree */
    public static class TreeNodeItem {
        public enum NodeType { PARENT, TABLE, RECORD }
        private final NodeType nodeType;
        private final String tableName;
        private final String displayText;
        private final String recordId;
        private final Map<String, Object> recordData;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private int recordCount;
        
        // Parent/Table node
        public TreeNodeItem(NodeType type, String tableName, String displayText, int recordCount) {
            this.nodeType = type;
            this.tableName = tableName;
            this.displayText = displayText;
            this.recordCount = recordCount;
            this.recordId = null;
            this.recordData = null;
        }
        
        // Record node
        public TreeNodeItem(String tableName, String recordId, String displayText, Map<String, Object> data) {
            this.nodeType = NodeType.RECORD;
            this.tableName = tableName;
            this.recordId = recordId;
            this.displayText = displayText;
            this.recordData = data;
        }
        
        public NodeType getNodeType() { return nodeType; }
        public String getTableName() { return tableName; }
        public String getRecordId() { return recordId; }
        public Map<String, Object> getRecordData() { return recordData; }
        public int getRecordCount() { return recordCount; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean val) { selected.set(val); }
        public BooleanProperty selectedProperty() { return selected; }
        
        @Override
        public String toString() {
            switch (nodeType) {
                case PARENT: return "📦 " + displayText;
                case TABLE: return String.format("📁 %s [%,d]", displayText, recordCount);
                case RECORD: return "📄 " + displayText;
                default: return displayText;
            }
        }
    }
    
    /** Field-value pair for details table */
    public static class FieldValue {
        private final String field;
        private final String value;
        public FieldValue(String field, String value) {
            this.field = field;
            this.value = value;
        }
        public String getField() { return field; }
        public String getValue() { return value; }
    }
    
    // ============================================
    // INITIALIZATION
    // ============================================
    
    @FXML
    public void initialize() {
        setupStep1();
        setupStep2();
        setupStep3();
        setupStep4();
        updateStepIndicators();
        updateNavigation();
    }
    
    private void setupStep1() {
        // Load saved database connections
        List<SavedConnection> connections = ConnectionManager.getInstance().getConnections();
        databaseCombo.getItems().setAll(connections);
        databaseCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(SavedConnection c) {
                return c == null ? "" : c.getName() + " (" + c.getType() + ")";
            }
            @Override
            public SavedConnection fromString(String s) { return null; }
        });
        
        // Selection listener
        databaseCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedConnection = newVal;
                sourceType = SourceType.DATABASE;
                updateSourceCardStyles();
                step1StatusLabel.setText("✓ " + newVal.getName() + " selected");
                updateNavigation();
            }
        });
    }
    
    private void setupStep2() {
        // Object list filter
        filteredObjects = new FilteredList<>(backupObjects, p -> true);
        objectListView.setItems(filteredObjects);
        
        objectFilterField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal.toLowerCase();
            filteredObjects.setPredicate(obj -> 
                filter.isEmpty() || obj.getObjectName().toLowerCase().contains(filter)
            );
        });
        
        // Object selection
        objectListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedObject = newVal;
                selectedObjectLabel.setText("🔍 " + newVal.getObjectName());
                recordCountLabel.setText(String.format("%,d records in backup", newVal.getRecordCount()));
                searchResults.clear();
            }
        });
        
        // Results table setup
        selectCol.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectCol.setCellFactory(col -> new CheckBoxTableCell<>());
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));
        createdCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getCreated().length() > 10 ? 
            data.getValue().getCreated().substring(0, 10) : data.getValue().getCreated()
        ));
        
        resultsTable.setItems(searchResults);
        resultsTable.setEditable(true);
        
        // Update selection count when records are selected
        searchResults.addListener((javafx.collections.ListChangeListener<RecordRow>) change -> {
            updateSelectionCount();
        });
    }
    
    private void setupStep3() {
        // Details table columns
        if (fieldNameCol != null) {
            fieldNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getField()));
        }
        if (fieldValueCol != null) {
            fieldValueCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValue()));
        }
        
        // Tree selection → show details
        if (relationshipTree != null) {
            relationshipTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.getValue() != null) {
                    showRecordDetails(newVal.getValue());
                }
            });
            
            // Tree cell factory with checkboxes
            relationshipTree.setCellFactory(tv -> new TreeCell<TreeNodeItem>() {
                private final CheckBox checkBox = new CheckBox();
                
                @Override
                protected void updateItem(TreeNodeItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                    } else {
                        setText(item.toString());
                        switch (item.getNodeType()) {
                            case PARENT:
                                setGraphic(null);
                                setStyle("-fx-font-weight: bold; -fx-text-fill: #58a6ff;");
                                break;
                            case TABLE:
                                setGraphic(null);
                                setStyle("-fx-font-weight: 600; -fx-text-fill: #3fb950;");
                                break;
                            case RECORD:
                                checkBox.setSelected(item.isSelected());
                                checkBox.setOnAction(e -> {
                                    item.setSelected(checkBox.isSelected());
                                    updateRelatedRecordSelection(item, checkBox.isSelected());
                                    updateRelatedCounts();
                                });
                                setGraphic(checkBox);
                                setStyle(item.isSelected() ? "-fx-text-fill: #e6edf3;" : "-fx-text-fill: #8b949e;");
                                break;
                        }
                    }
                }
            });
        }
    }
    
    private void setupStep4() {
        // Will be populated when we reach step 4
    }
    
    // ============================================
    // STEP NAVIGATION
    // ============================================
    
    @FXML
    private void handleNext() {
        switch (currentStep) {
            case 1:
                if (sourceType != SourceType.NONE) {
                    goToStep(2);
                    loadBackupObjects();
                }
                break;
            case 2:
                List<RecordRow> selected = getSelectedRecords();
                if (!selected.isEmpty()) {
                    goToStep(3);
                    loadRelationships(selected);
                }
                break;
            case 3:
                goToStep(4);
                prepareRestoreSummary();
                break;
            case 4:
                startRestore();
                break;
        }
    }
    
    @FXML
    private void handleBack() {
        if (currentStep > 1) {
            goToStep(currentStep - 1);
        }
    }
    
    @FXML
    private void handleCancel() {
        // Return to dashboard or close
        // For now, reset to step 1
        goToStep(1);
        resetWizard();
    }
    
    private void goToStep(int step) {
        currentStep = step;
        
        // Hide all step content
        step1Content.setVisible(false);
        step2Content.setVisible(false);
        step3Content.setVisible(false);
        step4Content.setVisible(false);
        
        // Show current step
        switch (step) {
            case 1: step1Content.setVisible(true); break;
            case 2: step2Content.setVisible(true); break;
            case 3: step3Content.setVisible(true); break;
            case 4: step4Content.setVisible(true); break;
        }
        
        updateStepIndicators();
        updateNavigation();
    }
    
    private void updateStepIndicators() {
        // Remove all style classes first
        for (VBox indicator : Arrays.asList(step1Indicator, step2Indicator, step3Indicator, step4Indicator)) {
            if (indicator != null) {
                indicator.getStyleClass().removeAll("active", "completed");
            }
        }
        
        // Mark completed steps
        if (currentStep > 1 && step1Indicator != null) step1Indicator.getStyleClass().add("completed");
        if (currentStep > 2 && step2Indicator != null) step2Indicator.getStyleClass().add("completed");
        if (currentStep > 3 && step3Indicator != null) step3Indicator.getStyleClass().add("completed");
        
        // Mark current step
        switch (currentStep) {
            case 1: if (step1Indicator != null) step1Indicator.getStyleClass().add("active"); break;
            case 2: if (step2Indicator != null) step2Indicator.getStyleClass().add("active"); break;
            case 3: if (step3Indicator != null) step3Indicator.getStyleClass().add("active"); break;
            case 4: if (step4Indicator != null) step4Indicator.getStyleClass().add("active"); break;
        }
    }
    
    private void updateNavigation() {
        // Back button
        backBtn.setVisible(currentStep > 1);
        
        // Next button text and state
        switch (currentStep) {
            case 1:
                nextBtn.setText("Next →");
                nextBtn.setDisable(sourceType == SourceType.NONE);
                break;
            case 2:
                nextBtn.setText("Next →");
                nextBtn.setDisable(getSelectedRecords().isEmpty());
                break;
            case 3:
                nextBtn.setText("Review →");
                nextBtn.setDisable(false);
                break;
            case 4:
                nextBtn.setText("🚀 Start Restore");
                nextBtn.setDisable(false);
                break;
        }
    }
    
    private void resetWizard() {
        sourceType = SourceType.NONE;
        selectedConnection = null;
        selectedFolder = null;
        backupObjects.clear();
        searchResults.clear();
        recordsByTable.clear();
        selectedRelatedRecordIds.clear();
        step1StatusLabel.setText("");
        updateSourceCardStyles();
    }
    
    // ============================================
    // STEP 1: SOURCE SELECTION
    // ============================================
    
    @FXML
    private void handleSelectDatabase() {
        databaseCard.getStyleClass().add("selected");
        folderCard.getStyleClass().remove("selected");
    }
    
    @FXML
    private void handleSelectFolder() {
        folderCard.getStyleClass().add("selected");
        databaseCard.getStyleClass().remove("selected");
    }
    
    @FXML
    private void handleBrowseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Folder");
        File folder = chooser.showDialog(browseFolderBtn.getScene().getWindow());
        if (folder != null) {
            selectedFolder = folder;
            sourceType = SourceType.FOLDER;
            browseFolderBtn.setText(folder.getName());
            step1StatusLabel.setText("✓ Folder selected: " + folder.getAbsolutePath());
            updateSourceCardStyles();
            updateNavigation();
        }
    }
    
    private void updateSourceCardStyles() {
        databaseCard.getStyleClass().remove("selected");
        folderCard.getStyleClass().remove("selected");
        
        if (sourceType == SourceType.DATABASE) {
            databaseCard.getStyleClass().add("selected");
        } else if (sourceType == SourceType.FOLDER) {
            folderCard.getStyleClass().add("selected");
        }
    }
    
    // ============================================
    // STEP 2: FIND RECORDS
    // ============================================
    
    private void loadBackupObjects() {
        if (sourceType == SourceType.DATABASE && selectedConnection != null) {
            loadDatabaseObjects();
        } else if (sourceType == SourceType.FOLDER && selectedFolder != null) {
            loadFolderObjects();
        }
    }
    
    private void loadDatabaseObjects() {
        showLoading(true, "Scanning database...", "Connecting to " + selectedConnection.getName());
        
        Task<List<DatabaseScanner.BackupTable>> task = new Task<>() {
            @Override
            protected List<DatabaseScanner.BackupTable> call() throws Exception {
                DatabaseScanner scanner = new DatabaseScanner(selectedConnection);
                return scanner.scanForBackupTables();
            }
        };
        
        task.setOnSucceeded(e -> {
            showLoading(false, "", "");
            List<DatabaseScanner.BackupTable> tables = task.getValue();
            backupObjects.clear();
            for (DatabaseScanner.BackupTable table : tables) {
                backupObjects.add(new BackupObject(
                    table.getTableName(),
                    table.getObjectName(),
                    table.getRecordCount(),
                    table.getSchema()
                ));
            }
            objectCountLabel.setText(backupObjects.size() + " objects");
            sourceInfoLabel.setText("Database: " + selectedConnection.getName());
        });
        
        task.setOnFailed(e -> {
            showLoading(false, "", "");
            logger.error("Failed to scan database", task.getException());
            showError("Failed to scan database: " + task.getException().getMessage());
        });
        
        new Thread(task).start();
    }
    
    private void loadFolderObjects() {
        // TODO: Implement folder scanning
        showError("Folder restore not yet implemented in wizard mode");
    }
    
    @FXML
    private void handleSearch() {
        if (selectedObject == null) {
            showError("Please select an object first");
            return;
        }
        
        String whereClause = whereClauseField.getText().trim();
        boolean loadAll = loadAllRecordsCheck != null && loadAllRecordsCheck.isSelected();
        searchRecords(selectedObject, whereClause, loadAll);
    }
    
    private void searchRecords(BackupObject object, String whereClause, boolean loadAll) {
        showLoading(true, "Searching...", "Querying " + object.getObjectName());
        
        Task<List<Map<String, Object>>> task = new Task<>() {
            @Override
            protected List<Map<String, Object>> call() throws Exception {
                return queryRecordsFromDatabase(object, whereClause, loadAll);
            }
        };
        
        task.setOnSucceeded(e -> {
            showLoading(false, "", "");
            List<Map<String, Object>> records = task.getValue();
            searchResults.clear();
            for (Map<String, Object> record : records) {
                RecordRow row = new RecordRow(record);
                // Auto-select all if loading all records
                if (loadAll) {
                    row.setSelected(true);
                }
                searchResults.add(row);
            }
            recordCountLabel.setText(String.format("Showing %,d of %,d records", 
                records.size(), object.getRecordCount()));
            updateSelectionCount();
        });
        
        task.setOnFailed(e -> {
            showLoading(false, "", "");
            logger.error("Search failed", task.getException());
            showError("Search failed: " + task.getException().getMessage());
        });
        
        new Thread(task).start();
    }
    
    private List<Map<String, Object>> queryRecordsFromDatabase(BackupObject object, String whereClause, boolean loadAll) 
            throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();
        
        // Use higher limit if loading all (50K), otherwise default (200)
        int limit = loadAll ? 50000 : MAX_SEARCH_RESULTS;
        
        String jdbcUrl = buildJdbcUrl(selectedConnection);
        String username = selectedConnection.getUsername();
        String password = ConnectionManager.getInstance().getDecryptedPassword(selectedConnection);
        
        // For Snowflake, set connection properties to disable SSL validation
        java.util.Properties props = new java.util.Properties();
        props.put("user", username);
        props.put("password", password);
        if ("Snowflake".equals(selectedConnection.getType())) {
            props.put("insecure_mode", "true");
        }
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
            String schema = selectedConnection.getSchema();
            String qualifiedName = getQualifiedTableName(schema, object.getTableName());
            
            StringBuilder sql = new StringBuilder();
            
            // Build SELECT with LIMIT
            switch (selectedConnection.getType()) {
                case "SQL Server":
                    sql.append("SELECT TOP ").append(limit).append(" * FROM ").append(qualifiedName);
                    break;
                default:
                    sql.append("SELECT * FROM ").append(qualifiedName);
                    break;
            }
            
            // Add WHERE clause if provided
            if (whereClause != null && !whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
            
            // Add LIMIT for non-SQL Server
            if (!"SQL Server".equals(selectedConnection.getType())) {
                sql.append(" LIMIT ").append(limit);
            }
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql.toString())) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> record = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        record.put(meta.getColumnName(i).toUpperCase(), rs.getObject(i));
                    }
                    records.add(record);
                }
            }
        }
        
        return records;
    }
    
    @FXML
    private void handleExampleName() {
        whereClauseField.setText("NAME LIKE '%Acme%'");
    }
    
    @FXML
    private void handleExampleDate() {
        whereClauseField.setText("CREATEDDATE >= '2024-01-01'");
    }
    
    @FXML
    private void handleExampleId() {
        whereClauseField.setText("ID = '001XXXXXXXXXXXX'");
    }
    
    @FXML
    private void handleSelectAllRecords() {
        for (RecordRow row : searchResults) {
            row.setSelected(true);
        }
        resultsTable.refresh();
        updateSelectionCount();
        updateNavigation();
    }
    
    @FXML
    private void handleClearSelection() {
        for (RecordRow row : searchResults) {
            row.setSelected(false);
        }
        resultsTable.refresh();
        updateSelectionCount();
        updateNavigation();
    }
    
    private List<RecordRow> getSelectedRecords() {
        return searchResults.stream()
            .filter(RecordRow::isSelected)
            .collect(Collectors.toList());
    }
    
    private void updateSelectionCount() {
        int count = (int) searchResults.stream().filter(RecordRow::isSelected).count();
        selectionLabel.setText(count + " records selected");
        updateNavigation();
    }
    
    // ============================================
    // STEP 3: RELATIONSHIPS
    // ============================================
    
    private void loadRelationships(List<RecordRow> selectedRecords) {
        showLoading(true, "Loading relationships...", "Finding related records");
        
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                recordsByTable.clear();
                lookupFieldByTable.clear();
                selectedRelatedRecordIds.clear();
                
                String jdbcUrl = buildJdbcUrl(selectedConnection);
                String username = selectedConnection.getUsername();
                String password = ConnectionManager.getInstance().getDecryptedPassword(selectedConnection);
                
                try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                    String schema = selectedConnection.getSchema();
                    
                    // Load column metadata
                    Map<String, Set<String>> tableColumns = loadAllColumnMetadata(conn, schema);
                    
                    // Find related tables for the selected object
                    String parentObjectName = selectedObject.getObjectName();
                    
                    for (BackupObject obj : backupObjects) {
                        if (obj.getTableName().equalsIgnoreCase(selectedObject.getTableName())) continue;
                        
                        Set<String> cols = tableColumns.getOrDefault(
                            obj.getTableName().toUpperCase(),
                            tableColumns.getOrDefault(obj.getTableName(), Set.of())
                        );
                        
                        String lookupField = findLookupField(cols, parentObjectName);
                        if (lookupField != null) {
                            lookupFieldByTable.put(obj.getTableName(), lookupField);
                        }
                    }
                    
                    // Load related records for each selected parent record
                    for (RecordRow parent : selectedRecords) {
                        String parentId = parent.getId();
                        
                        for (Map.Entry<String, String> entry : lookupFieldByTable.entrySet()) {
                            String tableName = entry.getKey();
                            String lookupField = entry.getValue();
                            
                            List<Map<String, Object>> related = loadRelatedRecords(
                                conn, schema, tableName, lookupField, parentId
                            );
                            
                            if (!related.isEmpty()) {
                                recordsByTable.computeIfAbsent(tableName, k -> new ArrayList<>())
                                    .addAll(related);
                            }
                        }
                    }
                }
                
                return null;
            }
        };
        
        task.setOnSucceeded(e -> {
            showLoading(false, "", "");
            buildRelationshipTree(selectedRecords);
            
            int totalRecords = recordsByTable.values().stream().mapToInt(List::size).sum();
            parentSummaryLabel.setText(selectedRecords.size() + " " + selectedObject.getObjectName() + " selected");
            totalInMemoryLabel.setText(totalRecords + " related records in memory");
        });
        
        task.setOnFailed(e -> {
            showLoading(false, "", "");
            logger.error("Failed to load relationships", task.getException());
        });
        
        new Thread(task).start();
    }
    
    private List<Map<String, Object>> loadRelatedRecords(Connection conn, String schema,
                                                          String tableName, String lookupField,
                                                          String parentId) throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();
        String qualifiedName = getQualifiedTableName(schema, tableName);
        
        String query;
        switch (selectedConnection.getType()) {
            case "SQL Server":
                query = "SELECT TOP " + MAX_RECORDS_PER_TABLE + " * FROM " + qualifiedName +
                       " WHERE " + quoteIdentifier(lookupField) + " = ?";
                break;
            default:
                query = "SELECT * FROM " + qualifiedName +
                       " WHERE " + quoteIdentifier(lookupField) + " = ? LIMIT " + MAX_RECORDS_PER_TABLE;
                break;
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, parentId);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> record = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        record.put(meta.getColumnName(i).toUpperCase(), rs.getObject(i));
                    }
                    records.add(record);
                }
            }
        }
        
        return records;
    }
    
    private void buildRelationshipTree(List<RecordRow> selectedParents) {
        TreeItem<TreeNodeItem> root = new TreeItem<>(
            new TreeNodeItem(TreeNodeItem.NodeType.PARENT, selectedObject.getTableName(),
                selectedObject.getObjectName() + " (" + selectedParents.size() + " selected)", 
                selectedParents.size())
        );
        root.setExpanded(true);
        
        // Add related tables
        for (Map.Entry<String, List<Map<String, Object>>> entry : recordsByTable.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> records = entry.getValue();
            
            String objectName = backupObjects.stream()
                .filter(o -> o.getTableName().equalsIgnoreCase(tableName))
                .map(BackupObject::getObjectName)
                .findFirst().orElse(tableName);
            
            TreeItem<TreeNodeItem> tableNode = new TreeItem<>(
                new TreeNodeItem(TreeNodeItem.NodeType.TABLE, tableName, objectName, records.size())
            );
            
            for (Map<String, Object> record : records) {
                String id = getRecordId(record);
                String name = getRecordDisplayName(record);
                TreeItem<TreeNodeItem> recordNode = new TreeItem<>(
                    new TreeNodeItem(tableName, id, name, record)
                );
                tableNode.getChildren().add(recordNode);
            }
            
            root.getChildren().add(tableNode);
        }
        
        relationshipTree.setRoot(root);
    }
    
    private void showRecordDetails(TreeNodeItem item) {
        if (detailsTable == null) return;
        detailsTable.getItems().clear();
        
        Map<String, Object> data = item.getRecordData();
        if (data == null) {
            if (item.getNodeType() == TreeNodeItem.NodeType.TABLE) {
                detailsTitle.setText("📁 " + item.toString().substring(2));
            }
            return;
        }
        
        detailsTitle.setText("📄 " + item.toString().substring(2));
        
        // Sort fields
        List<Map.Entry<String, Object>> sorted = new ArrayList<>(data.entrySet());
        sorted.sort((a, b) -> {
            if (a.getKey().equals("ID")) return -1;
            if (b.getKey().equals("ID")) return 1;
            if (a.getKey().equals("NAME")) return -1;
            if (b.getKey().equals("NAME")) return 1;
            return a.getKey().compareTo(b.getKey());
        });
        
        for (Map.Entry<String, Object> field : sorted) {
            String val = field.getValue() != null ? field.getValue().toString() : "(null)";
            detailsTable.getItems().add(new FieldValue(field.getKey(), val));
        }
    }
    
    private void updateRelatedRecordSelection(TreeNodeItem item, boolean selected) {
        selectedRelatedRecordIds.computeIfAbsent(item.getTableName(), k -> new HashSet<>());
        if (selected) {
            selectedRelatedRecordIds.get(item.getTableName()).add(item.getRecordId());
        } else {
            selectedRelatedRecordIds.get(item.getTableName()).remove(item.getRecordId());
        }
    }
    
    private void updateRelatedCounts() {
        int total = selectedRelatedRecordIds.values().stream().mapToInt(Set::size).sum();
        relatedCountLabel.setText(total + " related records selected");
    }
    
    @FXML
    private void handleExpandAll() {
        if (relationshipTree.getRoot() != null) {
            expandAll(relationshipTree.getRoot(), true);
        }
    }
    
    @FXML
    private void handleCollapseAll() {
        if (relationshipTree.getRoot() != null) {
            for (TreeItem<TreeNodeItem> child : relationshipTree.getRoot().getChildren()) {
                child.setExpanded(false);
            }
        }
    }
    
    private void expandAll(TreeItem<?> item, boolean expand) {
        item.setExpanded(expand);
        for (TreeItem<?> child : item.getChildren()) {
            expandAll(child, expand);
        }
    }
    
    @FXML
    private void handleSelectAllRelated() {
        setAllRelatedSelected(true);
    }
    
    @FXML
    private void handleDeselectAllRelated() {
        setAllRelatedSelected(false);
    }
    
    private void setAllRelatedSelected(boolean selected) {
        if (relationshipTree.getRoot() == null) return;
        
        for (TreeItem<TreeNodeItem> tableNode : relationshipTree.getRoot().getChildren()) {
            for (TreeItem<TreeNodeItem> recordNode : tableNode.getChildren()) {
                TreeNodeItem item = recordNode.getValue();
                if (item != null && item.getNodeType() == TreeNodeItem.NodeType.RECORD) {
                    item.setSelected(selected);
                    updateRelatedRecordSelection(item, selected);
                }
            }
        }
        
        relationshipTree.refresh();
        updateRelatedCounts();
    }
    
    @FXML
    private void handleCopyDetails() {
        if (detailsTable != null && !detailsTable.getItems().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (FieldValue fv : detailsTable.getItems()) {
                sb.append(fv.getField()).append(": ").append(fv.getValue()).append("\n");
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(content);
            
            String original = detailsTitle.getText();
            detailsTitle.setText("✅ Copied!");
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> detailsTitle.setText(original));
            }).start();
        }
    }
    
    // ============================================
    // STEP 4: REVIEW & RESTORE
    // ============================================
    
    private void prepareRestoreSummary() {
        int parentCount = getSelectedRecords().size();
        int relatedCount = selectedRelatedRecordIds.values().stream().mapToInt(Set::size).sum();
        int totalRecords = parentCount + relatedCount;
        int objectCount = 1 + (int) selectedRelatedRecordIds.values().stream().filter(s -> !s.isEmpty()).count();
        int apiCalls = (int) Math.ceil(totalRecords / 200.0);
        
        totalRecordsNumber.setText(String.valueOf(totalRecords));
        totalObjectsNumber.setText(String.valueOf(objectCount));
        apiCallsNumber.setText(String.valueOf(apiCalls));
        
        // Warning for large restores
        if (totalRecords > 1000) {
            warningPanel.setVisible(true);
            warningPanel.setManaged(true);
            warningText.setText("You are about to restore " + totalRecords + " records. " +
                "This may take several minutes and consume significant API calls.");
        } else {
            warningPanel.setVisible(false);
            warningPanel.setManaged(false);
        }
    }
    
    @FXML
    private void handleConnectOrg() {
        // TODO: Open Salesforce login
        showError("Salesforce connection will be implemented");
    }
    
    private void startRestore() {
        // TODO: Implement actual restore
        progressPanel.setVisible(true);
        progressPanel.setManaged(true);
        nextBtn.setDisable(true);
        
        logArea.appendText("Starting restore...\n");
        logArea.appendText("Mode: " + (insertModeRadio.isSelected() ? "Insert" : 
                          upsertModeRadio.isSelected() ? "Upsert" : "Update") + "\n");
        logArea.appendText("Records to restore: " + totalRecordsNumber.getText() + "\n");
        
        // Simulate progress for now
        progressBar.setProgress(0.5);
        progressPercentLabel.setText("50%");
        progressStatusLabel.setText("Restore simulation - not yet implemented");
    }
    
    // ============================================
    // UTILITY METHODS
    // ============================================
    
    private void showLoading(boolean show, String title, String detail) {
        Platform.runLater(() -> {
            loadingOverlay.setVisible(show);
            if (show) {
                loadingLabel.setText(title);
                loadingDetailLabel.setText(detail);
            }
        });
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private String getRecordId(Map<String, Object> record) {
        Object id = record.get("ID");
        if (id == null) id = record.get("Id");
        return id != null ? id.toString() : "";
    }
    
    private String getRecordDisplayName(Map<String, Object> record) {
        String[] fields = {"NAME", "SUBJECT", "TITLE", "CASENUMBER", "FIRSTNAME", "LASTNAME"};
        for (String field : fields) {
            Object val = record.get(field);
            if (val != null && !val.toString().isEmpty()) {
                if (field.equals("FIRSTNAME") || field.equals("LASTNAME")) {
                    Object first = record.get("FIRSTNAME");
                    Object last = record.get("LASTNAME");
                    if (first != null && last != null) {
                        return first + " " + last;
                    }
                }
                return val.toString();
            }
        }
        return getRecordId(record);
    }
    
    private Map<String, Set<String>> loadAllColumnMetadata(Connection conn, String schema) throws SQLException {
        Map<String, Set<String>> tableColumns = new HashMap<>();
        String query;
        String targetSchema;
        
        switch (selectedConnection.getType()) {
            case "Snowflake":
                targetSchema = schema != null ? schema.toUpperCase() : "PUBLIC";
                query = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ?";
                break;
            case "PostgreSQL":
                targetSchema = schema != null ? schema : "public";
                query = "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = ?";
                break;
            case "SQL Server":
                targetSchema = schema != null ? schema : "dbo";
                query = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ?";
                break;
            default:
                return tableColumns;
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, targetSchema);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString(1).toUpperCase();
                    String colName = rs.getString(2).toUpperCase();
                    tableColumns.computeIfAbsent(tableName, k -> new HashSet<>()).add(colName);
                }
            }
        }
        
        return tableColumns;
    }
    
    private String findLookupField(Set<String> columns, String parentObjectName) {
        String parent = parentObjectName.toUpperCase();
        String[] patterns = {parent + "ID", parent + "_ID", "PARENT" + parent + "ID"};
        
        for (String p : patterns) {
            if (columns.contains(p)) return p;
        }
        
        for (String col : columns) {
            if (col.endsWith("ID") && col.contains(parent)) return col;
        }
        
        return null;
    }
    
    /**
     * @deprecated Use JdbcHelper.buildJdbcUrl() instead
     */
    private String buildJdbcUrl(SavedConnection conn) {
        return JdbcHelper.buildJdbcUrl(conn);
    }
    
    /**
     * @deprecated Use JdbcHelper.getQualifiedTableName() instead
     */
    private String getQualifiedTableName(String schema, String tableName) {
        return JdbcHelper.getQualifiedTableName(selectedConnection, schema, tableName);
    }
    
    /**
     * @deprecated Use JdbcHelper.quoteIdentifier() instead
     */
    private String quoteIdentifier(String identifier) {
        return JdbcHelper.quoteIdentifier(selectedConnection, identifier);
    }
}
