package com.backupforce.ui;

import com.backupforce.restore.*;
import com.backupforce.restore.SchemaComparer.*;
import com.backupforce.restore.TransformationConfig.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Controller for the Cross-Org Data Transformation UI.
 * Manages schema comparison and mapping configuration.
 */
public class TransformationController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(TransformationController.class);
    
    // Header controls
    @FXML private Button btnAnalyze;
    @FXML private Button btnSaveMappings;
    @FXML private Button btnLoadMappings;
    
    // Info banner
    @FXML private HBox infoBanner;
    @FXML private Label lblInfoMessage;
    
    // RecordType tab
    @FXML private ComboBox<String> cmbRecordTypeObject;
    @FXML private TableView<RecordTypeMappingRow> tblRecordTypeMappings;
    @FXML private TableColumn<RecordTypeMappingRow, String> colRtSourceId;
    @FXML private TableColumn<RecordTypeMappingRow, String> colRtSourceName;
    @FXML private TableColumn<RecordTypeMappingRow, String> colRtTargetMapping;
    @FXML private TableColumn<RecordTypeMappingRow, String> colRtStatus;
    @FXML private ComboBox<UnmappedValueBehavior> cmbRtUnmappedBehavior;
    @FXML private Label lblRtUnmappedHelp;
    
    // User tab
    @FXML private CheckBox chkUseRunningUser;
    @FXML private TableView<UserMappingRow> tblUserMappings;
    @FXML private TableColumn<UserMappingRow, String> colUserSourceId;
    @FXML private TableColumn<UserMappingRow, String> colUserSourceName;
    @FXML private TableColumn<UserMappingRow, String> colUserTargetMapping;
    @FXML private TableColumn<UserMappingRow, String> colUserStatus;
    @FXML private ComboBox<UnmappedValueBehavior> cmbUserUnmappedBehavior;
    
    // Picklist tab
    @FXML private ComboBox<String> cmbPicklistObject;
    @FXML private ComboBox<String> cmbPicklistField;
    @FXML private TableView<PicklistMappingRow> tblPicklistMappings;
    @FXML private TableColumn<PicklistMappingRow, String> colPlSourceValue;
    @FXML private TableColumn<PicklistMappingRow, String> colPlTargetMapping;
    @FXML private TableColumn<PicklistMappingRow, String> colPlStatus;
    @FXML private ComboBox<UnmappedValueBehavior> cmbPlUnmappedBehavior;
    @FXML private TextField txtPlDefaultValue;
    
    // Field tab
    @FXML private ComboBox<String> cmbFieldObject;
    @FXML private TableView<FieldMappingRow> tblFieldMappings;
    @FXML private TableColumn<FieldMappingRow, String> colFieldSource;
    @FXML private TableColumn<FieldMappingRow, String> colFieldTarget;
    @FXML private TableColumn<FieldMappingRow, String> colFieldExcluded;
    @FXML private TableColumn<FieldMappingRow, Void> colFieldActions;
    @FXML private TextArea txtMissingFields;
    
    // Value transforms tab
    @FXML private ComboBox<String> cmbTransformObject;
    @FXML private ComboBox<String> cmbTransformField;
    @FXML private TableView<ValueTransformRow> tblValueTransforms;
    @FXML private TableColumn<ValueTransformRow, String> colTransformField;
    @FXML private TableColumn<ValueTransformRow, String> colTransformType;
    @FXML private TableColumn<ValueTransformRow, String> colTransformPattern;
    @FXML private TableColumn<ValueTransformRow, String> colTransformReplacement;
    @FXML private TableColumn<ValueTransformRow, Void> colTransformActions;
    
    // Summary tab
    @FXML private Label lblObjectsAnalyzed;
    @FXML private Label lblRtMismatches;
    @FXML private Label lblUserMismatches;
    @FXML private Label lblPicklistMismatches;
    @FXML private Label lblMissingFields;
    @FXML private Label lblMappingsConfigured;
    @FXML private TextArea txtValidationResults;
    
    // Status bar
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label lblStatus;
    
    // Data structures
    private TransformationConfig config = new TransformationConfig();
    private Map<String, ObjectComparisonResult> comparisonResults = new LinkedHashMap<>();
    private Map<String, List<RecordTypeInfo>> targetRecordTypes = new LinkedHashMap<>();
    private List<UserInfo> targetUsers = new ArrayList<>();
    private Map<String, Map<String, List<String>>> targetPicklistValues = new LinkedHashMap<>();
    
    // State
    private String instanceUrl;
    private String accessToken;
    private String apiVersion;
    private File backupDirectory;
    private Set<String> selectedObjects = new LinkedHashSet<>();
    private Consumer<TransformationConfig> onApplyCallback;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupRecordTypeTable();
        setupUserTable();
        setupPicklistTable();
        setupFieldTable();
        setupValueTransformTable();
        setupBehaviorComboBoxes();
    }
    
    private void setupRecordTypeTable() {
        colRtSourceId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sourceId));
        colRtSourceName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sourceName));
        colRtStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        
        // Target mapping column with ComboBox
        colRtTargetMapping.setCellFactory(column -> new ComboBoxTableCell<>());
        colRtTargetMapping.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().targetId != null ? data.getValue().getTargetDisplayName() : ""));
        colRtTargetMapping.setOnEditCommit(event -> {
            RecordTypeMappingRow row = event.getRowValue();
            String selected = event.getNewValue();
            if (selected != null && !selected.isEmpty()) {
                // Find the RecordTypeInfo by display name
                String objectName = cmbRecordTypeObject.getValue();
                if (objectName != null && targetRecordTypes.containsKey(objectName)) {
                    for (RecordTypeInfo rt : targetRecordTypes.get(objectName)) {
                        if (rt.toString().equals(selected)) {
                            row.targetId = rt.getId();
                            row.targetName = rt.getName();
                            updateConfigRecordTypeMapping(objectName, row.sourceId, rt.getId());
                            break;
                        }
                    }
                }
            }
            tblRecordTypeMappings.refresh();
            updateSummary();
        });
        
        tblRecordTypeMappings.setEditable(true);
    }
    
    private void setupUserTable() {
        colUserSourceId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sourceId));
        colUserSourceName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sourceName));
        colUserStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        
        colUserTargetMapping.setCellFactory(column -> new ComboBoxTableCell<>());
        colUserTargetMapping.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().targetId != null ? data.getValue().getTargetDisplayName() : ""));
        colUserTargetMapping.setOnEditCommit(event -> {
            UserMappingRow row = event.getRowValue();
            String selected = event.getNewValue();
            if (selected != null && !selected.isEmpty()) {
                for (UserInfo user : targetUsers) {
                    if (user.toString().equals(selected)) {
                        row.targetId = user.getId();
                        row.targetName = user.getName();
                        updateConfigUserMapping(row.sourceId, user.getId());
                        break;
                    }
                }
            }
            tblUserMappings.refresh();
            updateSummary();
        });
        
        tblUserMappings.setEditable(true);
    }
    
    private void setupPicklistTable() {
        colPlSourceValue.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sourceValue));
        colPlStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        
        colPlTargetMapping.setCellFactory(column -> new ComboBoxTableCell<>());
        colPlTargetMapping.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().targetValue != null ? data.getValue().targetValue : ""));
        colPlTargetMapping.setOnEditCommit(event -> {
            PicklistMappingRow row = event.getRowValue();
            row.targetValue = event.getNewValue();
            
            String objectName = cmbPicklistObject.getValue();
            String fieldName = cmbPicklistField.getValue();
            if (objectName != null && fieldName != null) {
                updateConfigPicklistMapping(objectName, fieldName, row.sourceValue, row.targetValue);
            }
            tblPicklistMappings.refresh();
            updateSummary();
        });
        
        tblPicklistMappings.setEditable(true);
    }
    
    private void setupFieldTable() {
        colFieldSource.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sourceField));
        colFieldTarget.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().targetField));
        colFieldExcluded.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().excluded ? "Yes" : "No"));
        
        // Actions column with delete button
        colFieldActions.setCellFactory(column -> new TableCell<>() {
            private final Button deleteBtn = new Button("🗑");
            {
                deleteBtn.setOnAction(e -> {
                    FieldMappingRow row = getTableView().getItems().get(getIndex());
                    getTableView().getItems().remove(row);
                    String objectName = cmbFieldObject.getValue();
                    if (objectName != null) {
                        removeFieldMapping(objectName, row.sourceField);
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });
    }
    
    private void setupValueTransformTable() {
        colTransformField.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fieldName));
        colTransformType.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().type != null ? data.getValue().type.name() : ""));
        colTransformPattern.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().pattern));
        colTransformReplacement.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().replacement));
        
        // Actions column with delete button
        colTransformActions.setCellFactory(column -> new TableCell<>() {
            private final Button deleteBtn = new Button("🗑");
            {
                deleteBtn.setOnAction(e -> {
                    ValueTransformRow row = getTableView().getItems().get(getIndex());
                    getTableView().getItems().remove(row);
                    String objectName = cmbTransformObject.getValue();
                    if (objectName != null) {
                        removeValueTransform(objectName, row.fieldName);
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });
    }
    
    private void setupBehaviorComboBoxes() {
        // RecordType unmapped behavior
        cmbRtUnmappedBehavior.setItems(FXCollections.observableArrayList(
            UnmappedValueBehavior.KEEP_ORIGINAL,
            UnmappedValueBehavior.USE_DEFAULT,
            UnmappedValueBehavior.SET_NULL,
            UnmappedValueBehavior.SKIP_RECORD,
            UnmappedValueBehavior.FAIL
        ));
        cmbRtUnmappedBehavior.setValue(UnmappedValueBehavior.KEEP_ORIGINAL);
        cmbRtUnmappedBehavior.setOnAction(e -> updateRtUnmappedHelp());
        
        // User unmapped behavior
        cmbUserUnmappedBehavior.setItems(FXCollections.observableArrayList(
            UnmappedValueBehavior.USE_RUNNING_USER,
            UnmappedValueBehavior.KEEP_ORIGINAL,
            UnmappedValueBehavior.SKIP_RECORD,
            UnmappedValueBehavior.FAIL
        ));
        cmbUserUnmappedBehavior.setValue(UnmappedValueBehavior.USE_RUNNING_USER);
        
        // Picklist unmapped behavior
        cmbPlUnmappedBehavior.setItems(FXCollections.observableArrayList(
            UnmappedValueBehavior.KEEP_ORIGINAL,
            UnmappedValueBehavior.USE_DEFAULT,
            UnmappedValueBehavior.SET_NULL,
            UnmappedValueBehavior.SKIP_RECORD,
            UnmappedValueBehavior.FAIL
        ));
        cmbPlUnmappedBehavior.setValue(UnmappedValueBehavior.KEEP_ORIGINAL);
    }
    
    private void updateRtUnmappedHelp() {
        UnmappedValueBehavior behavior = cmbRtUnmappedBehavior.getValue();
        if (behavior == null) return;
        
        switch (behavior) {
            case KEEP_ORIGINAL:
                lblRtUnmappedHelp.setText("Uses the original RecordType ID (may fail if not found)");
                break;
            case USE_DEFAULT:
                lblRtUnmappedHelp.setText("Uses the default RecordType for this object");
                break;
            case SET_NULL:
                lblRtUnmappedHelp.setText("Sets RecordTypeId to null (uses Master type)");
                break;
            case SKIP_RECORD:
                lblRtUnmappedHelp.setText("Skips records with unmapped RecordTypes");
                break;
            case FAIL:
                lblRtUnmappedHelp.setText("Stops restore if unmapped RecordType found");
                break;
            default:
                lblRtUnmappedHelp.setText("");
                break;
        }
    }
    
    // ==================== Configuration Methods ====================
    
    public void setConnectionDetails(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
    }
    
    public void setBackupDirectory(File backupDirectory) {
        this.backupDirectory = backupDirectory;
    }
    
    public void setSelectedObjects(Set<String> objects) {
        this.selectedObjects = objects != null ? objects : new LinkedHashSet<>();
    }
    
    public void setConnectionInfo(LoginController.ConnectionInfo connInfo) {
        if (connInfo != null) {
            this.instanceUrl = connInfo.getInstanceUrl();
            this.accessToken = connInfo.getSessionId();
            // Use default API version since ConnectionInfo doesn't store it
            this.apiVersion = "60.0";
        }
    }
    
    public void setTransformationConfig(TransformationConfig config) {
        if (config != null) {
            this.config = config;
            // TODO: Populate UI with existing config
        }
    }
    
    public void setOnApply(Consumer<TransformationConfig> callback) {
        this.onApplyCallback = callback;
    }
    
    public void setOnApplyCallback(Consumer<TransformationConfig> callback) {
        this.onApplyCallback = callback;
    }
    
    public TransformationConfig getConfig() {
        return config;
    }
    
    // ==================== Event Handlers ====================
    
    @FXML
    private void handleAnalyzeSchema() {
        if (instanceUrl == null || accessToken == null) {
            showError("Not connected to Salesforce. Please login first.");
            return;
        }
        
        if (backupDirectory == null || !backupDirectory.exists()) {
            showError("No backup directory selected. Please select a backup to restore.");
            return;
        }
        
        setLoading(true, "Analyzing schema differences...");
        
        CompletableFuture.runAsync(() -> {
            try {
                analyzeBackupData();
            } catch (Exception e) {
                logger.error("Error analyzing schema", e);
                Platform.runLater(() -> {
                    showError("Error analyzing schema: " + e.getMessage());
                    setLoading(false, "Analysis failed.");
                });
            }
        });
    }
    
    private void analyzeBackupData() throws Exception {
        SchemaComparer comparer = new SchemaComparer(instanceUrl, accessToken, apiVersion);
        comparer.setLogCallback(msg -> Platform.runLater(() -> lblStatus.setText(msg)));
        
        comparisonResults.clear();
        Set<String> allUserIds = new LinkedHashSet<>();
        
        // Find all CSV files in backup directory
        File[] csvFiles = backupDirectory.listFiles((dir, name) -> name.endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            Platform.runLater(() -> showError("No CSV files found in backup directory."));
            return;
        }
        
        // Filter to only selected objects if specified
        List<File> filesToAnalyze = new ArrayList<>();
        for (File csvFile : csvFiles) {
            String objectName = csvFile.getName().replace(".csv", "");
            if (selectedObjects.isEmpty() || selectedObjects.contains(objectName)) {
                filesToAnalyze.add(csvFile);
            }
        }
        
        if (filesToAnalyze.isEmpty()) {
            Platform.runLater(() -> showError("No matching CSV files found for selected objects."));
            return;
        }
        
        int totalObjects = 0;
        int totalRtMismatches = 0;
        int totalPicklistMismatches = 0;
        int totalMissingFields = 0;
        
        for (File csvFile : filesToAnalyze) {
            String objectName = csvFile.getName().replace(".csv", "");
            
            // Skip relationship files
            if (objectName.endsWith("_relationships")) {
                continue;
            }
            
            Platform.runLater(() -> lblStatus.setText("Analyzing " + objectName + "..."));
            
            // Read backup data and analyze
            // (In real implementation, read CSV and extract metadata)
            // For now, using placeholder logic
            
            try {
                // Describe object to get target metadata
                ObjectComparisonResult result = comparer.compareObject(
                    objectName,
                    Set.of(), // Would extract from CSV headers
                    Map.of(), // Would extract picklist values
                    Set.of(), // Would extract RecordType IDs
                    Set.of()  // Would extract User IDs
                );
                
                comparisonResults.put(objectName, result);
                
                if (result.getTargetRecordTypes() != null && !result.getTargetRecordTypes().isEmpty()) {
                    targetRecordTypes.put(objectName, result.getTargetRecordTypes());
                }
                
                allUserIds.addAll(result.getUserMismatches().stream()
                    .map(UserMismatch::getSourceUserId).collect(Collectors.toList()));
                
                totalObjects++;
                totalRtMismatches += result.getRecordTypeMismatches().size();
                totalPicklistMismatches += result.getPicklistMismatches().size();
                totalMissingFields += result.getMissingFields().size();
                
            } catch (Exception e) {
                logger.warn("Could not analyze {}: {}", objectName, e.getMessage());
            }
        }
        
        // Get all active users for mapping suggestions
        if (!allUserIds.isEmpty()) {
            ObjectComparisonResult firstResult = comparisonResults.values().iterator().next();
            if (firstResult != null && firstResult.getTargetUsers() != null) {
                targetUsers = firstResult.getTargetUsers();
            }
        }
        
        final int fTotalObjects = totalObjects;
        final int fTotalRt = totalRtMismatches;
        final int fTotalPl = totalPicklistMismatches;
        final int fTotalMissing = totalMissingFields;
        
        Platform.runLater(() -> {
            populateObjectDropdowns();
            updateSummary();
            lblObjectsAnalyzed.setText(String.valueOf(fTotalObjects));
            lblRtMismatches.setText(String.valueOf(fTotalRt));
            lblPicklistMismatches.setText(String.valueOf(fTotalPl));
            lblMissingFields.setText(String.valueOf(fTotalMissing));
            
            setLoading(false, "Analysis complete. " + fTotalObjects + " objects analyzed.");
            
            if (fTotalRt + fTotalPl + fTotalMissing > 0) {
                showInfo("Schema mismatches detected. Please configure mappings before restoring.");
            } else {
                showInfo("No schema mismatches found. Backup is compatible with target org.");
            }
        });
        
        comparer.close();
    }
    
    private void populateObjectDropdowns() {
        ObservableList<String> objects = FXCollections.observableArrayList(comparisonResults.keySet());
        cmbRecordTypeObject.setItems(objects);
        cmbPicklistObject.setItems(objects);
        cmbFieldObject.setItems(objects);
        cmbTransformObject.setItems(objects);
    }
    
    @FXML
    private void handleRecordTypeObjectChange() {
        String objectName = cmbRecordTypeObject.getValue();
        if (objectName == null) return;
        
        ObjectComparisonResult result = comparisonResults.get(objectName);
        if (result == null) return;
        
        ObservableList<RecordTypeMappingRow> rows = FXCollections.observableArrayList();
        
        for (RecordTypeMismatch mismatch : result.getRecordTypeMismatches()) {
            RecordTypeMappingRow row = new RecordTypeMappingRow();
            row.sourceId = mismatch.getSourceRecordTypeId();
            row.sourceName = "Unknown"; // Would need to query source org
            row.targetOptions = mismatch.getTargetOptions();
            
            // Check if already mapped
            ObjectTransformConfig objConfig = config.getObjectConfig(objectName);
            if (objConfig != null && objConfig.getRecordTypeMappings().containsKey(row.sourceId)) {
                row.targetId = objConfig.getRecordTypeMappings().get(row.sourceId);
            }
            
            rows.add(row);
        }
        
        tblRecordTypeMappings.setItems(rows);
    }
    
    @FXML
    private void handlePicklistObjectChange() {
        String objectName = cmbPicklistObject.getValue();
        if (objectName == null) return;
        
        ObjectComparisonResult result = comparisonResults.get(objectName);
        if (result == null) return;
        
        // Get unique field names with picklist mismatches
        Set<String> picklistFields = new LinkedHashSet<>();
        for (PicklistMismatch mismatch : result.getPicklistMismatches()) {
            picklistFields.add(mismatch.getFieldName());
        }
        
        cmbPicklistField.setItems(FXCollections.observableArrayList(picklistFields));
    }
    
    @FXML
    private void handlePicklistFieldChange() {
        String objectName = cmbPicklistObject.getValue();
        String fieldName = cmbPicklistField.getValue();
        if (objectName == null || fieldName == null) return;
        
        ObjectComparisonResult result = comparisonResults.get(objectName);
        if (result == null) return;
        
        ObservableList<PicklistMappingRow> rows = FXCollections.observableArrayList();
        
        for (PicklistMismatch mismatch : result.getPicklistMismatches()) {
            if (!mismatch.getFieldName().equals(fieldName)) continue;
            
            PicklistMappingRow row = new PicklistMappingRow();
            row.sourceValue = mismatch.getSourceValue();
            row.targetOptions = mismatch.getTargetOptions();
            
            // Check if already mapped
            ObjectTransformConfig objConfig = config.getObjectConfig(objectName);
            if (objConfig != null) {
                Map<String, String> fieldMappings = objConfig.getPicklistMappings().get(fieldName);
                if (fieldMappings != null && fieldMappings.containsKey(row.sourceValue)) {
                    row.targetValue = fieldMappings.get(row.sourceValue);
                }
            }
            
            rows.add(row);
        }
        
        tblPicklistMappings.setItems(rows);
    }
    
    @FXML
    private void handleFieldObjectChange() {
        String objectName = cmbFieldObject.getValue();
        if (objectName == null) return;
        
        ObjectComparisonResult result = comparisonResults.get(objectName);
        if (result == null) return;
        
        // Show missing fields
        if (!result.getMissingFields().isEmpty()) {
            txtMissingFields.setText(String.join("\n", result.getMissingFields()));
        } else {
            txtMissingFields.setText("");
        }
        
        // Show existing field mappings
        ObjectTransformConfig objConfig = config.getObjectConfig(objectName);
        if (objConfig != null) {
            ObservableList<FieldMappingRow> rows = FXCollections.observableArrayList();
            
            for (Map.Entry<String, String> entry : objConfig.getFieldNameMappings().entrySet()) {
                FieldMappingRow row = new FieldMappingRow();
                row.sourceField = entry.getKey();
                row.targetField = entry.getValue();
                row.excluded = false;
                rows.add(row);
            }
            
            for (String excludedField : objConfig.getExcludedFields()) {
                FieldMappingRow row = new FieldMappingRow();
                row.sourceField = excludedField;
                row.targetField = "";
                row.excluded = true;
                rows.add(row);
            }
            
            tblFieldMappings.setItems(rows);
        } else {
            tblFieldMappings.setItems(FXCollections.observableArrayList());
        }
    }
    
    @FXML
    private void handleTransformObjectChange() {
        String objectName = cmbTransformObject.getValue();
        if (objectName == null) return;
        
        ObjectComparisonResult result = comparisonResults.get(objectName);
        if (result != null) {
            // Populate field dropdown
            // Would need to get all fields from backup or describe result
        }
        
        // Show existing transforms
        ObjectTransformConfig objConfig = config.getObjectConfig(objectName);
        if (objConfig != null) {
            ObservableList<ValueTransformRow> rows = FXCollections.observableArrayList();
            
            for (Map.Entry<String, ValueTransformation> entry : objConfig.getValueTransformations().entrySet()) {
                ValueTransformRow row = new ValueTransformRow();
                row.fieldName = entry.getKey();
                row.type = entry.getValue().getType();
                row.pattern = entry.getValue().getPattern();
                row.replacement = entry.getValue().getReplacement();
                rows.add(row);
            }
            
            tblValueTransforms.setItems(rows);
        } else {
            tblValueTransforms.setItems(FXCollections.observableArrayList());
        }
    }
    
    @FXML
    private void handleAutoSuggestRecordTypes() {
        String objectName = cmbRecordTypeObject.getValue();
        if (objectName == null) {
            showError("Please select an object first.");
            return;
        }
        
        // Get source RecordTypes (would need to be from backup metadata)
        // For now, just suggest based on target options
        List<RecordTypeInfo> targetRts = targetRecordTypes.get(objectName);
        if (targetRts == null || targetRts.isEmpty()) {
            showInfo("No target RecordTypes available for suggestions.");
            return;
        }
        
        int suggested = 0;
        for (RecordTypeMappingRow row : tblRecordTypeMappings.getItems()) {
            if (row.targetId == null && row.targetOptions != null && !row.targetOptions.isEmpty()) {
                // Auto-select first option as suggestion
                RecordTypeInfo firstOption = row.targetOptions.get(0);
                row.targetId = firstOption.getId();
                row.targetName = firstOption.getName();
                updateConfigRecordTypeMapping(objectName, row.sourceId, firstOption.getId());
                suggested++;
            }
        }
        
        tblRecordTypeMappings.refresh();
        updateSummary();
        showInfo("Suggested " + suggested + " RecordType mappings. Please review and adjust.");
    }
    
    @FXML
    private void handleAutoSuggestUsers() {
        if (targetUsers.isEmpty()) {
            showInfo("No target users available for suggestions.");
            return;
        }
        
        // Use the suggestion algorithm from DataTransformer
        int suggested = 0;
        for (UserMappingRow row : tblUserMappings.getItems()) {
            if (row.targetId == null && row.targetOptions != null) {
                // Find best match by email or name similarity
                for (UserInfo target : row.targetOptions) {
                    if (row.sourceName != null && 
                        target.getName().equalsIgnoreCase(row.sourceName)) {
                        row.targetId = target.getId();
                        row.targetName = target.getName();
                        updateConfigUserMapping(row.sourceId, target.getId());
                        suggested++;
                        break;
                    }
                }
            }
        }
        
        tblUserMappings.refresh();
        updateSummary();
        showInfo("Suggested " + suggested + " User mappings. Please review and adjust.");
    }
    
    @FXML
    private void handleAutoSuggestPicklists() {
        String objectName = cmbPicklistObject.getValue();
        String fieldName = cmbPicklistField.getValue();
        if (objectName == null || fieldName == null) {
            showError("Please select an object and field first.");
            return;
        }
        
        int suggested = 0;
        for (PicklistMappingRow row : tblPicklistMappings.getItems()) {
            if (row.targetValue == null && row.targetOptions != null) {
                // Find exact or similar match
                String source = row.sourceValue.toLowerCase();
                for (String target : row.targetOptions) {
                    if (target.equalsIgnoreCase(row.sourceValue)) {
                        row.targetValue = target;
                        updateConfigPicklistMapping(objectName, fieldName, row.sourceValue, target);
                        suggested++;
                        break;
                    }
                }
            }
        }
        
        tblPicklistMappings.refresh();
        updateSummary();
        showInfo("Suggested " + suggested + " Picklist mappings. Please review and adjust.");
    }
    
    @FXML
    private void handleUseRunningUserChange() {
        if (chkUseRunningUser.isSelected()) {
            cmbUserUnmappedBehavior.setValue(UnmappedValueBehavior.USE_RUNNING_USER);
            cmbUserUnmappedBehavior.setDisable(true);
        } else {
            cmbUserUnmappedBehavior.setDisable(false);
        }
    }
    
    @FXML
    private void handleAddFieldMapping() {
        String objectName = cmbFieldObject.getValue();
        if (objectName == null) {
            showError("Please select an object first.");
            return;
        }
        
        // Show dialog to add field mapping
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Field Mapping");
        dialog.setHeaderText("Enter source field name:");
        dialog.setContentText("Source Field:");
        
        dialog.showAndWait().ifPresent(sourceField -> {
            TextInputDialog targetDialog = new TextInputDialog();
            targetDialog.setTitle("Add Field Mapping");
            targetDialog.setHeaderText("Enter target field name (leave empty to exclude):");
            targetDialog.setContentText("Target Field:");
            
            targetDialog.showAndWait().ifPresent(targetField -> {
                FieldMappingRow row = new FieldMappingRow();
                row.sourceField = sourceField;
                row.targetField = targetField;
                row.excluded = targetField == null || targetField.isEmpty();
                
                tblFieldMappings.getItems().add(row);
                
                ObjectTransformConfig objConfig = config.getOrCreateObjectConfig(objectName);
                if (row.excluded) {
                    objConfig.getExcludedFields().add(sourceField);
                } else {
                    objConfig.getFieldNameMappings().put(sourceField, targetField);
                }
                
                updateSummary();
            });
        });
    }
    
    @FXML
    private void handleAddTransform() {
        String objectName = cmbTransformObject.getValue();
        String fieldName = cmbTransformField.getValue();
        if (objectName == null || fieldName == null) {
            showError("Please select an object and field first.");
            return;
        }
        
        // Show dialog for transform type
        ChoiceDialog<TransformationType> dialog = new ChoiceDialog<>(
            TransformationType.REGEX_REPLACE,
            TransformationType.values()
        );
        dialog.setTitle("Add Value Transform");
        dialog.setHeaderText("Select transformation type:");
        
        dialog.showAndWait().ifPresent(type -> {
            TextInputDialog patternDialog = new TextInputDialog();
            patternDialog.setTitle("Add Value Transform");
            patternDialog.setHeaderText("Enter pattern/value:");
            patternDialog.setContentText("Pattern:");
            
            patternDialog.showAndWait().ifPresent(pattern -> {
                TextInputDialog replaceDialog = new TextInputDialog();
                replaceDialog.setTitle("Add Value Transform");
                replaceDialog.setHeaderText("Enter replacement:");
                replaceDialog.setContentText("Replacement:");
                
                replaceDialog.showAndWait().ifPresent(replacement -> {
                    ValueTransformRow row = new ValueTransformRow();
                    row.fieldName = fieldName;
                    row.type = type;
                    row.pattern = pattern;
                    row.replacement = replacement;
                    
                    tblValueTransforms.getItems().add(row);
                    
                    ObjectTransformConfig objConfig = config.getOrCreateObjectConfig(objectName);
                    ValueTransformation transform = new ValueTransformation();
                    transform.setType(type);
                    transform.setPattern(pattern);
                    transform.setReplacement(replacement);
                    objConfig.getValueTransformations().put(fieldName, transform);
                    
                    updateSummary();
                });
            });
        });
    }
    
    @FXML
    private void handleSaveMappings() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Transformation Mappings");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        fileChooser.setInitialFileName("transformation-config.json");
        
        File file = fileChooser.showSaveDialog(getStage());
        if (file != null) {
            try {
                config.saveToFile(file);
                showInfo("Mappings saved to " + file.getName());
            } catch (Exception e) {
                showError("Error saving mappings: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void handleLoadMappings() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Transformation Mappings");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        
        File file = fileChooser.showOpenDialog(getStage());
        if (file != null) {
            try {
                config = TransformationConfig.loadFromFile(file);
                showInfo("Mappings loaded from " + file.getName());
                updateSummary();
            } catch (Exception e) {
                showError("Error loading mappings: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void handleValidateMappings() {
        StringBuilder validation = new StringBuilder();
        int warnings = 0;
        int errors = 0;
        
        for (Map.Entry<String, ObjectComparisonResult> entry : comparisonResults.entrySet()) {
            String objectName = entry.getKey();
            ObjectComparisonResult result = entry.getValue();
            
            // Check unmapped RecordTypes
            for (RecordTypeMismatch mismatch : result.getRecordTypeMismatches()) {
                ObjectTransformConfig objConfig = config.getObjectConfig(objectName);
                if (objConfig == null || 
                    !objConfig.getRecordTypeMappings().containsKey(mismatch.getSourceRecordTypeId())) {
                    
                    UnmappedValueBehavior behavior = cmbRtUnmappedBehavior.getValue();
                    if (behavior == UnmappedValueBehavior.FAIL) {
                        validation.append("❌ ERROR: Unmapped RecordType in ")
                                  .append(objectName).append(": ")
                                  .append(mismatch.getSourceRecordTypeId()).append("\n");
                        errors++;
                    } else {
                        validation.append("⚠️ WARNING: Unmapped RecordType in ")
                                  .append(objectName).append("\n");
                        warnings++;
                    }
                }
            }
            
            // Check unmapped picklist values
            for (PicklistMismatch mismatch : result.getPicklistMismatches()) {
                ObjectTransformConfig objConfig = config.getObjectConfig(objectName);
                boolean isMapped = false;
                
                if (objConfig != null) {
                    Map<String, String> fieldMappings = objConfig.getPicklistMappings().get(mismatch.getFieldName());
                    isMapped = fieldMappings != null && fieldMappings.containsKey(mismatch.getSourceValue());
                }
                
                if (!isMapped) {
                    validation.append("⚠️ WARNING: Unmapped picklist value '")
                              .append(mismatch.getSourceValue())
                              .append("' in ").append(objectName).append(".")
                              .append(mismatch.getFieldName()).append("\n");
                    warnings++;
                }
            }
        }
        
        if (errors == 0 && warnings == 0) {
            validation.append("✅ All mappings validated successfully!\n\n");
            validation.append("The transformation config is ready for restore.");
        } else {
            validation.insert(0, "Validation Results:\n==================\n");
            validation.append("\nSummary: ").append(errors).append(" errors, ")
                      .append(warnings).append(" warnings\n");
            
            if (errors > 0) {
                validation.append("\n⛔ Cannot proceed with FAIL behavior - fix errors first.");
            }
        }
        
        txtValidationResults.setText(validation.toString());
    }
    
    @FXML
    private void handleApplyMappings() {
        // Update config with behavior settings
        for (String objectName : comparisonResults.keySet()) {
            ObjectTransformConfig objConfig = config.getOrCreateObjectConfig(objectName);
            objConfig.setUnmappedRecordTypeBehavior(cmbRtUnmappedBehavior.getValue());
            objConfig.setUnmappedUserBehavior(cmbUserUnmappedBehavior.getValue());
            objConfig.setUnmappedPicklistBehavior(cmbPlUnmappedBehavior.getValue());
        }
        
        if (onApplyCallback != null) {
            onApplyCallback.accept(config);
        }
        
        // Close the window
        Stage stage = getStage();
        if (stage != null) {
            stage.close();
        }
    }
    
    @FXML
    private void hideInfoBanner() {
        infoBanner.setVisible(false);
        infoBanner.setManaged(false);
    }
    
    // ==================== Helper Methods ====================
    
    private void updateConfigRecordTypeMapping(String objectName, String sourceId, String targetId) {
        ObjectTransformConfig objConfig = config.getOrCreateObjectConfig(objectName);
        objConfig.getRecordTypeMappings().put(sourceId, targetId);
    }
    
    private void updateConfigUserMapping(String sourceId, String targetId) {
        config.getUserMappings().put(sourceId, targetId);
    }
    
    private void updateConfigPicklistMapping(String objectName, String fieldName, 
                                              String sourceValue, String targetValue) {
        ObjectTransformConfig objConfig = config.getOrCreateObjectConfig(objectName);
        objConfig.getPicklistMappings()
                 .computeIfAbsent(fieldName, k -> new LinkedHashMap<>())
                 .put(sourceValue, targetValue);
    }
    
    private void removeFieldMapping(String objectName, String sourceField) {
        ObjectTransformConfig objConfig = config.getObjectConfig(objectName);
        if (objConfig != null) {
            objConfig.getFieldNameMappings().remove(sourceField);
            objConfig.getExcludedFields().remove(sourceField);
        }
    }
    
    private void removeValueTransform(String objectName, String fieldName) {
        ObjectTransformConfig objConfig = config.getObjectConfig(objectName);
        if (objConfig != null) {
            objConfig.getValueTransformations().remove(fieldName);
        }
    }
    
    private void updateSummary() {
        int totalMappings = 0;
        
        // Count all configured mappings
        totalMappings += config.getUserMappings().size();
        totalMappings += config.getRecordTypeMappings().size();
        
        for (ObjectTransformConfig objConfig : config.getObjectConfigs().values()) {
            totalMappings += objConfig.getRecordTypeMappings().size();
            totalMappings += objConfig.getUserMappings().size();
            totalMappings += objConfig.getFieldNameMappings().size();
            totalMappings += objConfig.getExcludedFields().size();
            totalMappings += objConfig.getValueTransformations().size();
            
            for (Map<String, String> picklistMap : objConfig.getPicklistMappings().values()) {
                totalMappings += picklistMap.size();
            }
        }
        
        lblMappingsConfigured.setText(String.valueOf(totalMappings));
    }
    
    private void setLoading(boolean loading, String message) {
        progressIndicator.setVisible(loading);
        progressIndicator.setManaged(loading);
        lblStatus.setText(message);
        btnAnalyze.setDisable(loading);
    }
    
    private void showInfo(String message) {
        infoBanner.getStyleClass().removeAll("error-banner");
        infoBanner.getStyleClass().add("info-banner");
        lblInfoMessage.setText(message);
        infoBanner.setVisible(true);
        infoBanner.setManaged(true);
    }
    
    private void showError(String message) {
        infoBanner.getStyleClass().removeAll("info-banner");
        infoBanner.getStyleClass().add("error-banner");
        lblInfoMessage.setText(message);
        infoBanner.setVisible(true);
        infoBanner.setManaged(true);
    }
    
    private Stage getStage() {
        if (btnAnalyze != null && btnAnalyze.getScene() != null) {
            return (Stage) btnAnalyze.getScene().getWindow();
        }
        return null;
    }
    
    // ==================== Row Data Classes ====================
    
    public static class RecordTypeMappingRow {
        String sourceId;
        String sourceName;
        String targetId;
        String targetName;
        List<RecordTypeInfo> targetOptions;
        
        String getStatus() {
            return targetId != null ? "✓ Mapped" : "⚠ Unmapped";
        }
        
        String getTargetDisplayName() {
            return targetName != null ? targetName : targetId;
        }
    }
    
    public static class UserMappingRow {
        String sourceId;
        String sourceName;
        String sourceEmail;
        String targetId;
        String targetName;
        List<UserInfo> targetOptions;
        
        String getStatus() {
            return targetId != null ? "✓ Mapped" : "⚠ Unmapped";
        }
        
        String getTargetDisplayName() {
            return targetName != null ? targetName : targetId;
        }
    }
    
    public static class PicklistMappingRow {
        String sourceValue;
        String targetValue;
        List<String> targetOptions;
        
        String getStatus() {
            return targetValue != null ? "✓ Mapped" : "⚠ Unmapped";
        }
    }
    
    public static class FieldMappingRow {
        String sourceField;
        String targetField;
        boolean excluded;
    }
    
    public static class ValueTransformRow {
        String fieldName;
        TransformationType type;
        String pattern;
        String replacement;
    }
}
