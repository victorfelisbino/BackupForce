package com.backupforce.ui;

import com.backupforce.config.AppConfig;
import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.config.JdbcHelper;
import com.backupforce.config.SSLHelper;
import com.backupforce.restore.DatabaseScanner;
import com.backupforce.restore.RestoreExecutor;
import com.sforce.soap.partner.DescribeSObjectResult;
import com.sforce.soap.partner.Field;
import com.sforce.soap.partner.PartnerConnection;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Wizard-based controller for guided data restoration.
 * Steps: 1) Choose Source → 2) Find Records → 3) Relationships → 4) Map Fields → 5) Review & Restore
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
    @FXML private VBox step1Indicator, step2Indicator, step3Indicator, step4Indicator, step5Indicator;
    @FXML private StackPane contentStack;
    @FXML private VBox step1Content, step2Content, step3Content, step4Content, step5Content;
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
    private LoginController.ConnectionInfo salesforceConnection;
    private final Map<String, String> lookupFieldByTable = new LinkedHashMap<>();
    private final Map<String, Set<String>> selectedRelatedRecordIds = new LinkedHashMap<>();
    
    // ============================================
    // STEP 4: Map Fields
    // ============================================
    @FXML private TableView<FieldMapping> fieldMappingTable;
    @FXML private TableColumn<FieldMapping, String> sourceFieldCol, destFieldCol, transformCol, sampleValueCol;
    @FXML private TableColumn<FieldMapping, String> destFieldTypeCol, mappingStatusCol;
    @FXML private Label orgMetadataStatusLabel;
    @FXML private Button refreshMetadataBtn;
    
    // Data for Step 4
    private ObservableList<FieldMapping> fieldMappings = FXCollections.observableArrayList();
    
    // Target org metadata cache
    private Map<String, DescribeSObjectResult> targetOrgMetadataCache = new HashMap<>();
    private Map<String, List<String>> targetFieldsByObject = new HashMap<>();
    private Map<String, Map<String, Field>> targetFieldDetailsByObject = new HashMap<>();
    private boolean metadataLoaded = false;
    
    // ============================================
    // STEP 5: Review & Restore
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
    
    /** Field mapping configuration */
    public static class FieldMapping {
        private final SimpleStringProperty sourceField;
        private final SimpleStringProperty destField;
        private final SimpleStringProperty transformation;
        private final SimpleStringProperty sampleValue;
        private final SimpleStringProperty destFieldType;
        private final SimpleStringProperty mappingStatus;
        private final SimpleStringProperty sourceFieldType;
        private boolean isRequired;
        private List<String> picklistValues;
        
        public FieldMapping(String sourceField, String destField, String transformation, String sampleValue) {
            this.sourceField = new SimpleStringProperty(sourceField);
            this.destField = new SimpleStringProperty(destField);
            this.transformation = new SimpleStringProperty(transformation != null ? transformation : "None");
            this.sampleValue = new SimpleStringProperty(sampleValue != null ? sampleValue : "");
            this.destFieldType = new SimpleStringProperty("");
            this.mappingStatus = new SimpleStringProperty("Unmapped");
            this.sourceFieldType = new SimpleStringProperty("");
            this.isRequired = false;
            this.picklistValues = new ArrayList<>();
        }
        
        public String getSourceField() { return sourceField.get(); }
        public void setSourceField(String value) { sourceField.set(value); }
        public SimpleStringProperty sourceFieldProperty() { return sourceField; }
        
        public String getDestField() { return destField.get(); }
        public void setDestField(String value) { destField.set(value); }
        public SimpleStringProperty destFieldProperty() { return destField; }
        
        public String getTransformation() { return transformation.get(); }
        public void setTransformation(String value) { transformation.set(value); }
        public SimpleStringProperty transformationProperty() { return transformation; }
        
        public String getSampleValue() { return sampleValue.get(); }
        public void setSampleValue(String value) { sampleValue.set(value); }
        public SimpleStringProperty sampleValueProperty() { return sampleValue; }
        
        public String getDestFieldType() { return destFieldType.get(); }
        public void setDestFieldType(String value) { destFieldType.set(value); }
        public SimpleStringProperty destFieldTypeProperty() { return destFieldType; }
        
        public String getMappingStatus() { return mappingStatus.get(); }
        public void setMappingStatus(String value) { mappingStatus.set(value); }
        public SimpleStringProperty mappingStatusProperty() { return mappingStatus; }
        
        public String getSourceFieldType() { return sourceFieldType.get(); }
        public void setSourceFieldType(String value) { sourceFieldType.set(value); }
        public SimpleStringProperty sourceFieldTypeProperty() { return sourceFieldType; }
        
        public boolean isRequired() { return isRequired; }
        public void setRequired(boolean required) { this.isRequired = required; }
        
        public List<String> getPicklistValues() { return picklistValues; }
        public void setPicklistValues(List<String> values) { this.picklistValues = values; }
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
        // Field mapping table columns
        if (sourceFieldCol != null) {
            sourceFieldCol.setCellValueFactory(data -> data.getValue().sourceFieldProperty());
            sourceFieldCol.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        FieldMapping mapping = getTableView().getItems().get(getIndex());
                        String type = mapping.getSourceFieldType();
                        setText(item + (type.isEmpty() ? "" : " (" + type + ")"));
                        setStyle("-fx-text-fill: #e6edf3;");
                    }
                }
            });
        }
        
        if (destFieldCol != null) {
            destFieldCol.setCellValueFactory(data -> data.getValue().destFieldProperty());
            destFieldCol.setCellFactory(col -> new TableCell<>() {
                private final ComboBox<String> comboBox = new ComboBox<>();
                
                {
                    comboBox.setMaxWidth(Double.MAX_VALUE);
                    comboBox.setStyle("-fx-background-color: #21262d; -fx-text-fill: #e6edf3;");
                    comboBox.setOnAction(e -> {
                        if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                            FieldMapping mapping = getTableView().getItems().get(getIndex());
                            String selected = comboBox.getValue();
                            mapping.setDestField(selected != null ? selected : "");
                            updateMappingStatus(mapping);
                        }
                    });
                }
                
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        FieldMapping mapping = getTableView().getItems().get(getIndex());
                        List<String> availableFields = getAvailableDestFields();
                        comboBox.getItems().setAll(availableFields);
                        comboBox.setValue(item);
                        setGraphic(comboBox);
                        setText(null);
                    }
                }
            });
            destFieldCol.setEditable(true);
        }
        
        if (transformCol != null) {
            transformCol.setCellValueFactory(data -> data.getValue().transformationProperty());
            // Make transformation editable with ComboBox
            transformCol.setCellFactory(col -> new TableCell<>() {
                private final ComboBox<String> comboBox = new ComboBox<>();
                
                {
                    comboBox.getItems().addAll("None", "RecordType Mapping", "User Mapping", "Value Translation", "Default Value", "Skip Field");
                    comboBox.setStyle("-fx-background-color: #21262d; -fx-text-fill: #e6edf3;");
                    comboBox.setOnAction(e -> {
                        if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                            FieldMapping mapping = getTableView().getItems().get(getIndex());
                            mapping.setTransformation(comboBox.getValue());
                        }
                    });
                }
                
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        comboBox.setValue(item);
                        setGraphic(comboBox);
                        setText(null);
                    }
                }
            });
        }
        
        if (sampleValueCol != null) {
            sampleValueCol.setCellValueFactory(data -> data.getValue().sampleValueProperty());
        }
        
        // Setup new columns if they exist in FXML
        if (destFieldTypeCol != null) {
            destFieldTypeCol.setCellValueFactory(data -> data.getValue().destFieldTypeProperty());
            destFieldTypeCol.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || item.isEmpty()) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        // Color-code by type
                        if (item.contains("Required")) {
                            setStyle("-fx-text-fill: #f85149;"); // Red for required
                        } else if (item.contains("Picklist")) {
                            setStyle("-fx-text-fill: #a371f7;"); // Purple for picklist
                        } else if (item.contains("Reference")) {
                            setStyle("-fx-text-fill: #58a6ff;"); // Blue for reference/lookup
                        } else {
                            setStyle("-fx-text-fill: #8b949e;");
                        }
                    }
                }
            });
        }
        
        if (mappingStatusCol != null) {
            mappingStatusCol.setCellValueFactory(data -> data.getValue().mappingStatusProperty());
            mappingStatusCol.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        switch (item) {
                            case "Mapped":
                                setStyle("-fx-text-fill: #3fb950;"); // Green
                                break;
                            case "Type Mismatch":
                                setStyle("-fx-text-fill: #d29922;"); // Yellow/orange warning
                                break;
                            case "Unmapped":
                                setStyle("-fx-text-fill: #8b949e;"); // Gray
                                break;
                            case "Skipped":
                                setStyle("-fx-text-fill: #6e7681;"); // Dimmer gray
                                break;
                            default:
                                setStyle("-fx-text-fill: #e6edf3;");
                        }
                    }
                }
            });
        }
        
        // Bind table to observable list
        if (fieldMappingTable != null) {
            fieldMappingTable.setItems(fieldMappings);
            fieldMappingTable.setEditable(true);
        }
    }
    
    /** Get available destination fields from org metadata */
    private List<String> getAvailableDestFields() {
        List<String> fields = new ArrayList<>();
        fields.add(""); // Empty option to skip field
        fields.add("-- Do Not Map --");
        
        // Get the current object being restored
        String objectName = getCurrentRestoreObjectName();
        if (objectName != null && targetFieldsByObject.containsKey(objectName)) {
            fields.addAll(targetFieldsByObject.get(objectName));
        }
        
        return fields;
    }
    
    /** Get the current object name being restored */
    private String getCurrentRestoreObjectName() {
        // Try to determine from selected records or object tree selection
        if (!recordsByTable.isEmpty()) {
            return recordsByTable.keySet().iterator().next();
        }
        return null;
    }
    
    /** Update the mapping status based on field selection */
    private void updateMappingStatus(FieldMapping mapping) {
        String destField = mapping.getDestField();
        if (destField == null || destField.isEmpty() || destField.equals("-- Do Not Map --")) {
            mapping.setMappingStatus("Skipped");
            mapping.setDestFieldType("");
            return;
        }
        
        String objectName = getCurrentRestoreObjectName();
        if (objectName != null && targetFieldDetailsByObject.containsKey(objectName)) {
            Map<String, Field> fieldDetails = targetFieldDetailsByObject.get(objectName);
            Field field = fieldDetails.get(destField);
            if (field != null) {
                String typeInfo = field.getType().toString();
                if (!field.isNillable() && field.isCreateable()) {
                    typeInfo += " (Required)";
                    mapping.setRequired(true);
                }
                mapping.setDestFieldType(typeInfo);
                mapping.setMappingStatus("Mapped");
                
                // Check for type compatibility
                String sourceType = mapping.getSourceFieldType().toLowerCase();
                String destType = field.getType().toString().toLowerCase();
                if (!sourceType.isEmpty() && !areTypesCompatible(sourceType, destType)) {
                    mapping.setMappingStatus("Type Mismatch");
                }
            } else {
                mapping.setMappingStatus("Unmapped");
                mapping.setDestFieldType("");
            }
        } else {
            mapping.setMappingStatus("Mapped");
        }
    }
    
    /** Check if source and destination types are compatible */
    private boolean areTypesCompatible(String sourceType, String destType) {
        // Normalize types for comparison
        sourceType = sourceType.toLowerCase();
        destType = destType.toLowerCase();
        
        // Exact match
        if (sourceType.equals(destType)) return true;
        
        // Common compatible types
        if (sourceType.contains("string") && destType.contains("string")) return true;
        if (sourceType.contains("text") && (destType.contains("string") || destType.contains("textarea"))) return true;
        if (sourceType.contains("int") && destType.contains("double")) return true;
        if (sourceType.contains("number") && (destType.contains("double") || destType.contains("currency"))) return true;
        if (sourceType.contains("bool") && destType.contains("boolean")) return true;
        if (sourceType.contains("date") && destType.contains("date")) return true;
        if (sourceType.contains("id") && destType.contains("reference")) return true;
        
        return false;
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
                prepareFieldMappings();
                break;
            case 4:
                goToStep(5);
                prepareRestoreSummary();
                break;
            case 5:
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
        step5Content.setVisible(false);
        
        // Show current step
        switch (step) {
            case 1: step1Content.setVisible(true); break;
            case 2: step2Content.setVisible(true); break;
            case 3: step3Content.setVisible(true); break;
            case 4: step4Content.setVisible(true); break;
            case 5: step5Content.setVisible(true); break;
        }
        
        updateStepIndicators();
        updateNavigation();
    }
    
    private void updateStepIndicators() {
        // Remove all style classes first
        for (VBox indicator : Arrays.asList(step1Indicator, step2Indicator, step3Indicator, step4Indicator, step5Indicator)) {
            if (indicator != null) {
                indicator.getStyleClass().removeAll("active", "completed");
            }
        }
        
        // Mark completed steps
        if (currentStep > 1 && step1Indicator != null) step1Indicator.getStyleClass().add("completed");
        if (currentStep > 2 && step2Indicator != null) step2Indicator.getStyleClass().add("completed");
        if (currentStep > 3 && step3Indicator != null) step3Indicator.getStyleClass().add("completed");
        if (currentStep > 4 && step4Indicator != null) step4Indicator.getStyleClass().add("completed");
        
        // Mark current step
        switch (currentStep) {
            case 1: if (step1Indicator != null) step1Indicator.getStyleClass().add("active"); break;
            case 2: if (step2Indicator != null) step2Indicator.getStyleClass().add("active"); break;
            case 3: if (step3Indicator != null) step3Indicator.getStyleClass().add("active"); break;
            case 4: if (step4Indicator != null) step4Indicator.getStyleClass().add("active"); break;
            case 5: if (step5Indicator != null) step5Indicator.getStyleClass().add("active"); break;
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
                nextBtn.setText("Next →");
                nextBtn.setDisable(false);
                break;
            case 4:
                nextBtn.setText("Next →");
                nextBtn.setDisable(false);
                break;
            case 5:
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
        if (selectedFolder == null) {
            showError("No folder selected");
            return;
        }
        
        showLoading(true, "Scanning Folder", "Reading CSV files from " + selectedFolder.getName());
        
        Task<List<BackupObject>> task = new Task<>() {
            @Override
            protected List<BackupObject> call() throws Exception {
                List<BackupObject> objects = new ArrayList<>();
                File[] csvFiles = selectedFolder.listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".csv"));
                
                if (csvFiles == null || csvFiles.length == 0) {
                    return objects;
                }
                
                for (File csvFile : csvFiles) {
                    try {
                        // Object name is filename without .csv extension
                        String objectName = csvFile.getName().replaceAll("\\.csv$", "");
                        long recordCount = countCsvRecords(csvFile);
                        
                        BackupObject obj = new BackupObject(objectName, objectName, recordCount, null);
                        objects.add(obj);
                        logger.info("Found CSV: {} with {} records", objectName, recordCount);
                    } catch (Exception e) {
                        logger.warn("Failed to read CSV: {}", csvFile.getName(), e);
                    }
                }
                
                return objects;
            }
        };
        
        task.setOnSucceeded(e -> {
            List<BackupObject> objects = task.getValue();
            backupObjects.clear();
            backupObjects.addAll(objects);
            showLoading(false, "", "");
            
            if (objects.isEmpty()) {
                showError("No CSV files found in selected folder");
            } else {
                sourceInfoLabel.setText("Source: " + selectedFolder.getName());
                objectCountLabel.setText(String.valueOf(objects.size()));
                statusLabel.setText("Found " + objects.size() + " CSV file(s)");
            }
        });
        
        task.setOnFailed(e -> {
            showLoading(false, "", "");
            logger.error("Failed to scan folder", task.getException());
            showError("Failed to scan folder: " + task.getException().getMessage());
        });
        
        new Thread(task).start();
    }
    
    private long countCsvRecords(File csvFile) throws Exception {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
            long count = 0;
            String line = reader.readLine(); // Skip header
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        }
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
                // Add listener to update Next button when checkbox changes
                row.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    updateSelectionCount();
                });
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
        
        // Determine limit based on query type
        // For open searches (no WHERE clause), limit to 100 to avoid SSL chunk download issues
        // With WHERE clause, use standard limits (200 or 50K if loadAll)
        int limit;
        boolean isOpenSearch = (whereClause == null || whereClause.isEmpty());
        if (isOpenSearch) {
            limit = 100;  // Very conservative limit for open searches to fit in single chunk
        } else {
            limit = loadAll ? 50000 : MAX_SEARCH_RESULTS;
        }
        
        String jdbcUrl = JdbcHelper.buildJdbcUrl(selectedConnection);
        String username = selectedConnection.getUsername();
        String password = ConnectionManager.getInstance().getDecryptedPassword(selectedConnection);
        
        // Build connection properties
        java.util.Properties props = new java.util.Properties();
        if ("Snowflake".equals(selectedConnection.getType())) {
            // For Snowflake with externalbrowser, only need username
            if (username != null) props.put("user", username);
            // Critical: Disable SSL validation for Snowflake JDBC internal HttpClient
            // Use both naming conventions for maximum compatibility
            props.put("insecureMode", "true");
            props.put("insecure_mode", "true");
            props.put("ocspFailOpen", "true");  // Allow connection even if OCSP check fails
            props.put("tracing", "OFF");
        } else {
            // For other databases, use username/password
            if (username != null) props.put("user", username);
            if (password != null) props.put("password", password);
        }
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
            String schema = selectedConnection.getSchema();
            String qualifiedName = JdbcHelper.getQualifiedTableName(selectedConnection, schema, object.getTableName());
            
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
                
                String jdbcUrl = JdbcHelper.buildJdbcUrl(selectedConnection);
                String username = selectedConnection.getUsername();
                String password = ConnectionManager.getInstance().getDecryptedPassword(selectedConnection);
                
                // Build connection properties with SSL settings for Snowflake
                java.util.Properties props = new java.util.Properties();
                if (username != null) props.put("user", username);
                if (password != null) props.put("password", password);
                if ("Snowflake".equals(selectedConnection.getType())) {
                    // Both naming conventions for compatibility
                    props.put("insecureMode", "true");
                    props.put("insecure_mode", "true");
                    props.put("ocspFailOpen", "true");
                }
                
                try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
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
                    
                    // OPTIMIZED: Batch load related records instead of N*M individual queries
                    // Collect all parent IDs once
                    List<String> allParentIds = selectedRecords.stream()
                        .map(RecordRow::getId)
                        .collect(Collectors.toList());
                    
                    // Load related records for ALL parent IDs at once per table
                    for (Map.Entry<String, String> entry : lookupFieldByTable.entrySet()) {
                        String tableName = entry.getKey();
                        String lookupField = entry.getValue();
                        
                        // Single batch query for all parent IDs (much faster than N queries)
                        List<Map<String, Object>> related = loadRelatedRecordsBatch(
                            conn, schema, tableName, lookupField, allParentIds
                        );
                        
                        if (!related.isEmpty()) {
                            recordsByTable.computeIfAbsent(tableName, k -> new ArrayList<>())
                                .addAll(related);
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
        String qualifiedName = JdbcHelper.getQualifiedTableName(selectedConnection, schema, tableName);
        
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
    
    /**
     * OPTIMIZED: Batch load related records for multiple parent IDs at once.
     * This replaces N*M individual queries with M batch queries (one per table).
     * Dramatically improves performance when dealing with many parent records.
     */
    private List<Map<String, Object>> loadRelatedRecordsBatch(Connection conn, String schema,
                                                               String tableName, String lookupField,
                                                               List<String> parentIds) throws SQLException {
        if (parentIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> records = new ArrayList<>();
        String qualifiedName = JdbcHelper.getQualifiedTableName(selectedConnection, schema, tableName);
        String quotedField = JdbcHelper.quoteIdentifier(selectedConnection, lookupField);
        
        // Build IN clause with all parent IDs
        String placeholders = String.join(",", Collections.nCopies(parentIds.size(), "?"));
        
        String query;
        switch (selectedConnection.getType()) {
            case "SQL Server":
                query = "SELECT TOP " + MAX_RECORDS_PER_TABLE + " * FROM " + qualifiedName +
                       " WHERE " + quotedField + " IN (" + placeholders + ")";
                break;
            default:
                query = "SELECT * FROM " + qualifiedName +
                       " WHERE " + quotedField + " IN (" + placeholders + ") LIMIT " + MAX_RECORDS_PER_TABLE;
                break;
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            // Bind all parent IDs to the IN clause
            for (int i = 0; i < parentIds.size(); i++) {
                stmt.setString(i + 1, parentIds.get(i));
            }
            
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
    // STEP 4: MAP FIELDS & TRANSFORMATIONS
    // ============================================
    
    private void prepareFieldMappings() {
        fieldMappings.clear();
        
        // Update org metadata status
        if (orgMetadataStatusLabel != null) {
            orgMetadataStatusLabel.setText("Loading org metadata...");
            orgMetadataStatusLabel.setStyle("-fx-text-fill: #d29922;");
        }
        
        // First, fetch target org metadata in background
        Task<Void> metadataTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                fetchTargetOrgMetadata();
                return null;
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    populateFieldMappings();
                    if (orgMetadataStatusLabel != null) {
                        if (metadataLoaded) {
                            orgMetadataStatusLabel.setText("✓ Org metadata loaded - " + targetFieldsByObject.size() + " object(s)");
                            orgMetadataStatusLabel.setStyle("-fx-text-fill: #3fb950;");
                        } else {
                            orgMetadataStatusLabel.setText("⚠ Could not load org metadata");
                            orgMetadataStatusLabel.setStyle("-fx-text-fill: #d29922;");
                        }
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    populateFieldMappings();
                    if (orgMetadataStatusLabel != null) {
                        orgMetadataStatusLabel.setText("✗ Failed to load org metadata: " + getException().getMessage());
                        orgMetadataStatusLabel.setStyle("-fx-text-fill: #f85149;");
                    }
                    logger.error("Failed to fetch org metadata", getException());
                });
            }
        };
        
        new Thread(metadataTask).start();
    }
    
    /** Fetch metadata from the target Salesforce org */
    private void fetchTargetOrgMetadata() {
        if (salesforceConnection == null || salesforceConnection.getConnection() == null) {
            logger.warn("No Salesforce connection available for metadata fetch");
            return;
        }
        
        try {
            PartnerConnection conn = salesforceConnection.getConnection();
            
            // Get the objects we're trying to restore
            Set<String> objectsToDescribe = new HashSet<>(recordsByTable.keySet());
            
            if (objectsToDescribe.isEmpty()) {
                logger.warn("No objects to describe");
                return;
            }
            
            logger.info("Fetching metadata for objects: {}", objectsToDescribe);
            
            for (String objectName : objectsToDescribe) {
                try {
                    DescribeSObjectResult describeResult = conn.describeSObject(objectName);
                    targetOrgMetadataCache.put(objectName, describeResult);
                    
                    // Extract field names and details
                    List<String> fieldNames = new ArrayList<>();
                    Map<String, Field> fieldDetails = new HashMap<>();
                    
                    for (Field field : describeResult.getFields()) {
                        // Only include createable fields (can be set on insert)
                        if (field.isCreateable() || field.isUpdateable()) {
                            String fieldName = field.getName();
                            fieldNames.add(fieldName);
                            fieldDetails.put(fieldName, field);
                        }
                    }
                    
                    // Sort field names alphabetically
                    Collections.sort(fieldNames, String.CASE_INSENSITIVE_ORDER);
                    
                    targetFieldsByObject.put(objectName, fieldNames);
                    targetFieldDetailsByObject.put(objectName, fieldDetails);
                    
                    logger.info("Loaded {} fields for object {}", fieldNames.size(), objectName);
                    
                } catch (Exception e) {
                    logger.error("Failed to describe object: {}", objectName, e);
                }
            }
            
            metadataLoaded = !targetFieldsByObject.isEmpty();
            
        } catch (Exception e) {
            logger.error("Failed to fetch target org metadata", e);
            throw new RuntimeException("Failed to fetch org metadata: " + e.getMessage(), e);
        }
    }
    
    /** Populate field mappings after metadata is loaded */
    private void populateFieldMappings() {
        fieldMappings.clear();
        
        List<RecordRow> selectedRecords = getSelectedRecords();
        if (selectedRecords.isEmpty()) {
            FieldMapping placeholder = new FieldMapping(
                "Select records first", 
                "to auto-detect fields", 
                "N/A", 
                "N/A"
            );
            fieldMappings.add(placeholder);
            return;
        }
        
        RecordRow firstRecord = selectedRecords.get(0);
        Map<String, Object> recordData = firstRecord.getData();
        String objectName = getCurrentRestoreObjectName();
        
        // Get target org fields for intelligent mapping
        Map<String, Field> targetFields = targetFieldDetailsByObject.getOrDefault(objectName, new HashMap<>());
        Set<String> targetFieldNamesLower = targetFields.keySet().stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        
        for (Map.Entry<String, Object> entry : recordData.entrySet()) {
            String sourceField = entry.getKey();
            String sampleValue = entry.getValue() != null ? entry.getValue().toString() : "";
            
            // Truncate long sample values
            if (sampleValue.length() > 50) {
                sampleValue = sampleValue.substring(0, 47) + "...";
            }
            
            // Intelligent auto-mapping
            String destField = findBestMatchingField(sourceField, targetFields);
            String transformation = "None";
            
            // Detect if transformation is needed
            if (sourceField.equalsIgnoreCase("RecordTypeId") || sourceField.equalsIgnoreCase("RecordType")) {
                transformation = "RecordType Mapping";
            } else if (sourceField.equalsIgnoreCase("OwnerId") || sourceField.equalsIgnoreCase("CreatedById") || 
                       sourceField.equalsIgnoreCase("LastModifiedById")) {
                transformation = "User Mapping";
            }
            
            FieldMapping mapping = new FieldMapping(sourceField, destField, transformation, sampleValue);
            
            // Set source field type (infer from sample value)
            mapping.setSourceFieldType(inferFieldType(sampleValue));
            
            // Set destination field details if available
            if (destField != null && !destField.isEmpty() && targetFields.containsKey(destField)) {
                Field targetField = targetFields.get(destField);
                String typeInfo = targetField.getType().toString();
                if (!targetField.isNillable() && targetField.isCreateable()) {
                    typeInfo += " (Required)";
                    mapping.setRequired(true);
                }
                mapping.setDestFieldType(typeInfo);
                mapping.setMappingStatus("Mapped");
                
                // Check type compatibility
                if (!areTypesCompatible(mapping.getSourceFieldType(), targetField.getType().toString())) {
                    mapping.setMappingStatus("Type Mismatch");
                }
            } else if (destField == null || destField.isEmpty()) {
                mapping.setMappingStatus("Unmapped");
            }
            
            fieldMappings.add(mapping);
        }
        
        // Sort mappings: mapped first, then unmapped
        fieldMappings.sort((a, b) -> {
            if (a.getMappingStatus().equals("Mapped") && !b.getMappingStatus().equals("Mapped")) return -1;
            if (!a.getMappingStatus().equals("Mapped") && b.getMappingStatus().equals("Mapped")) return 1;
            return a.getSourceField().compareToIgnoreCase(b.getSourceField());
        });
        
        logger.info("Prepared {} field mappings", fieldMappings.size());
    }
    
    /** Find the best matching target field for a source field */
    private String findBestMatchingField(String sourceField, Map<String, Field> targetFields) {
        if (targetFields.isEmpty()) {
            return sourceField; // Fall back to same name if no metadata
        }
        
        // Exact match (case-insensitive)
        for (String targetField : targetFields.keySet()) {
            if (targetField.equalsIgnoreCase(sourceField)) {
                return targetField;
            }
        }
        
        // Try without common prefixes/suffixes
        String normalizedSource = sourceField.replaceAll("__c$", "").replaceAll("__r$", "");
        for (String targetField : targetFields.keySet()) {
            String normalizedTarget = targetField.replaceAll("__c$", "").replaceAll("__r$", "");
            if (normalizedTarget.equalsIgnoreCase(normalizedSource)) {
                return targetField;
            }
        }
        
        // Skip system fields that shouldn't be mapped
        if (isSystemField(sourceField)) {
            return "";
        }
        
        return ""; // No match found
    }
    
    /** Check if a field is a system field that shouldn't be mapped */
    private boolean isSystemField(String fieldName) {
        Set<String> systemFields = Set.of(
            "Id", "ID", "CreatedDate", "CreatedById", "LastModifiedDate", "LastModifiedById",
            "SystemModstamp", "IsDeleted", "SYSTEMMODSTAMP", "ISDELETED"
        );
        return systemFields.contains(fieldName);
    }
    
    /** Infer field type from sample value */
    private String inferFieldType(String value) {
        if (value == null || value.isEmpty() || value.equals("(null)")) {
            return "";
        }
        
        // Check for ID pattern (15 or 18 char Salesforce ID)
        if (value.matches("[a-zA-Z0-9]{15}|[a-zA-Z0-9]{18}")) {
            return "id";
        }
        
        // Check for boolean
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return "boolean";
        }
        
        // Check for date/datetime patterns
        if (value.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return "datetime";
        }
        
        // Check for number
        try {
            Double.parseDouble(value);
            if (value.contains(".")) {
                return "double";
            }
            return "int";
        } catch (NumberFormatException ignored) {}
        
        return "string";
    }
    
    @FXML
    private void handleRefreshMetadata() {
        // Clear cached metadata and reload
        targetOrgMetadataCache.clear();
        targetFieldsByObject.clear();
        targetFieldDetailsByObject.clear();
        metadataLoaded = false;
        
        prepareFieldMappings();
    }
    
    @FXML
    private void handleAddFieldMapping() {
        // Add a new empty mapping row for manual entry
        FieldMapping newMapping = new FieldMapping("", "", "None", "");
        fieldMappings.add(newMapping);
        fieldMappingTable.scrollTo(newMapping);
    }
    
    @FXML
    private void handleAutoMap() {
        // Re-run auto-detection with fresh metadata
        handleRefreshMetadata();
    }
    
    @FXML
    private void handleManageTransformations() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/transformation.fxml"));
            Parent root = loader.load();
            
            // Create modal dialog
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Manage Value Transformations");
            dialogStage.setScene(new Scene(root));
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(fieldMappingTable.getScene().getWindow());
            
            // Show dialog and wait
            dialogStage.showAndWait();
            
            // Refresh field mappings if transformations were added
            populateFieldMappings();
            
        } catch (IOException e) {
            logger.error("Failed to open transformations dialog", e);
            showError("Transformations dialog not available: " + e.getMessage());
        }
    }
    
    // ============================================
    // STEP 5: REVIEW & RESTORE
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
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            LoginController loginController = loader.getController();
            
            // Create modal dialog
            Stage loginStage = new Stage();
            loginStage.setTitle("Connect to Salesforce");
            loginStage.setScene(new Scene(root));
            loginStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            loginStage.initOwner(nextBtn.getScene().getWindow());
            
            // Wait for login completion
            loginStage.showAndWait();
            
            // Check if successfully connected (LoginController sets static connectionInfo)
            LoginController.ConnectionInfo connInfo = LoginController.getLastConnectionInfo();
            if (connInfo != null && connInfo.getConnection() != null) {
                salesforceConnection = connInfo;
                String username = connInfo.getUsername();
                String instanceUrl = connInfo.getInstanceUrl();
                destinationOrgLabel.setText("Connected: " + username + " (" + instanceUrl + ")");
                destinationOrgLabel.setStyle("-fx-text-fill: green;");
                statusLabel.setText("Successfully connected to Salesforce");
                nextBtn.setDisable(false);
            } else {
                showError("Login cancelled or failed");
            }
            
        } catch (Exception e) {
            logger.error("Failed to open login dialog", e);
            showError("Failed to open login dialog: " + e.getMessage());
        }
    }
    
    private void startRestore() {
        // Validate connection
        if (salesforceConnection == null || salesforceConnection.getConnection() == null) {
            showError("Please connect to Salesforce first");
            return;
        }
        
        progressPanel.setVisible(true);
        progressPanel.setManaged(true);
        nextBtn.setDisable(true);
        backBtn.setDisable(true);
        cancelBtn.setText("Cancel Restore");
        
        // Determine restore mode
        RestoreExecutor.RestoreMode mode = insertModeRadio.isSelected() ? RestoreExecutor.RestoreMode.INSERT :
                                            upsertModeRadio.isSelected() ? RestoreExecutor.RestoreMode.UPSERT :
                                            RestoreExecutor.RestoreMode.UPDATE;
        
        logArea.appendText("Starting restore...\n");
        logArea.appendText("Mode: " + mode + "\n");
        logArea.appendText("Records to restore: " + totalRecordsNumber.getText() + "\n");
        logArea.appendText("Destination: " + salesforceConnection.getInstanceUrl() + "\n\n");
        
        Task<Void> restoreTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    RestoreExecutor executor = new RestoreExecutor(
                        salesforceConnection.getInstanceUrl(),
                        salesforceConnection.getSessionId(),
                        "62.0"
                    );
                    
                    // Set up callbacks
                    executor.setLogCallback(msg -> Platform.runLater(() -> 
                        logArea.appendText(msg + "\n")));
                    
                    executor.setProgressCallback(progress -> Platform.runLater(() -> {
                        double percent = progress.getTotalRecords() > 0 ? 
                            (double) progress.getProcessedRecords() / progress.getTotalRecords() : 0;
                        progressBar.setProgress(percent);
                        progressPercentLabel.setText(String.format("%.0f%%", percent * 100));
                        progressStatusLabel.setText("Processing: " + progress.getCurrentObject());
                    }));
                    
                    RestoreExecutor.RestoreOptions options = new RestoreExecutor.RestoreOptions();
                    options.setBatchSize(200);
                    options.setStopOnError(stopOnErrorCheck.isSelected());
                    options.setValidateBeforeRestore(true);
                    
                    // Get selected records to restore
                    List<Map<String, Object>> recordsToRestore = new ArrayList<>();
                    for (RecordRow row : searchResults) {
                        if (row.isSelected()) {
                            recordsToRestore.add(row.getData());
                        }
                    }
                    
                    if (recordsToRestore.isEmpty()) {
                        Platform.runLater(() -> logArea.appendText("No records selected for restore\n"));
                        return null;
                    }
                    
                    // Create temporary CSV for restore
                    Path tempCsv = Files.createTempFile("restore_", ".csv");
                    writeCsvForRestore(recordsToRestore, tempCsv);
                    
                    // Execute restore
                    RestoreExecutor.RestoreResult result = executor.restoreFromCsv(
                        selectedObject.getObjectName(), tempCsv, mode, options);
                    
                    // Cleanup temp file
                    Files.deleteIfExists(tempCsv);
                    
                    // Show results
                    Platform.runLater(() -> {
                        String summary = String.format("✓ Success: %d, ✗ Failed: %d", 
                            result.getSuccessCount(), result.getFailureCount());
                        logArea.appendText("\n" + summary + "\n");
                        progressBar.setProgress(1.0);
                        progressPercentLabel.setText("100%");
                        boolean isSuccess = result.getFailureCount() == 0;
                        progressStatusLabel.setText(isSuccess ? "Restore completed" : "Restore completed with errors");
                        cancelBtn.setText("Close");
                    });
                    
                } catch (Exception e) {
                    throw e;
                }
                return null;
            }
        };
        
        restoreTask.setOnFailed(e -> {
            Throwable ex = restoreTask.getException();
            logger.error("Restore failed", ex);
            logArea.appendText("\n❌ RESTORE FAILED: " + ex.getMessage() + "\n");
            progressStatusLabel.setText("Restore failed");
            cancelBtn.setText("Close");
            backBtn.setDisable(false);
        });
        
        new Thread(restoreTask).start();
    }
    
    private void writeCsvForRestore(List<Map<String, Object>> records, Path csvPath) throws IOException {
        if (records.isEmpty()) return;
        
        try (java.io.BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            // Write header
            List<String> headers = new ArrayList<>(records.get(0).keySet());
            writer.write(String.join(",", headers));
            writer.newLine();
            
            // Write data rows
            for (Map<String, Object> record : records) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    Object value = record.get(header);
                    String strValue = value != null ? value.toString() : "";
                    // Escape commas and quotes
                    if (strValue.contains(",") || strValue.contains("\"")) {
                        strValue = "\"" + strValue.replace("\"", "\"\"") + "\"";
                    }
                    values.add(strValue);
                }
                writer.write(String.join(",", values));
                writer.newLine();
            }
        }
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
     * @deprecated Use JdbcHelper.quoteIdentifier() instead
     */
    private String quoteIdentifier(String identifier) {
        return JdbcHelper.quoteIdentifier(selectedConnection, identifier);
    }
}

