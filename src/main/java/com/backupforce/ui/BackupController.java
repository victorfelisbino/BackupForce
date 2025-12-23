package com.backupforce.ui;

import com.backupforce.bulkv2.BulkV2Client;
import com.backupforce.bulkv2.BulkV2Client.ApiLimits;
import com.backupforce.config.BackupHistory;
import com.backupforce.config.BackupHistory.BackupRun;
import com.backupforce.config.BackupHistory.ObjectBackupResult;
import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.relationship.BackupManifestGenerator;
import com.backupforce.relationship.BackupManifestGenerator.RelatedObjectInfo;
import com.backupforce.relationship.ChildRelationshipAnalyzer;
import com.backupforce.relationship.ChildRelationshipAnalyzer.*;
import com.backupforce.relationship.RelationshipAwareBackupService;
import com.backupforce.relationship.RelationshipAwareBackupService.*;
import com.backupforce.ui.RelationshipPreviewController.RelationshipBackupConfig;
import com.backupforce.ui.RelationshipPreviewController.SelectedRelatedObject;
import com.backupforce.sink.DataSink;
import com.backupforce.sink.DataSinkFactory;
import com.backupforce.ui.DatabaseSettingsController.DatabaseConnectionInfo;
import com.sforce.soap.partner.DescribeGlobalResult;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleLongProperty;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class BackupController {
    private static final Logger logger = LoggerFactory.getLogger(BackupController.class);
    
    // Objects known to cause memory issues due to large binary data
    private static final Set<String> LARGE_OBJECTS = new HashSet<>(Arrays.asList(
        "Attachment", "ContentVersion", "Document", "StaticResource"
    ));
    
    // Rate-limited logging to prevent UI thread saturation
    private final ConcurrentLinkedQueue<String> pendingLogMessages = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService logFlushScheduler;
    private volatile long lastUiUpdate = 0;
    private static final long UI_UPDATE_THROTTLE_MS = 100; // Throttle UI updates to max 10/sec per object

    // Selection Table (before backup)
    @FXML private TableView<SObjectItem> allObjectsTable;
    @FXML private TableColumn<SObjectItem, Boolean> allSelectColumn;
    @FXML private TableColumn<SObjectItem, String> allNameColumn;
    @FXML private TableColumn<SObjectItem, String> allLabelColumn;
    @FXML private TableColumn<SObjectItem, Void> allFieldsColumn;
    @FXML private TableColumn<SObjectItem, String> allStatusColumn;
    
    // All Objects Status Tab (during backup)
    @FXML private TableView<SObjectItem> allStatusTable;
    @FXML private TableColumn<SObjectItem, String> statusAllNameColumn;
    @FXML private TableColumn<SObjectItem, String> statusAllLabelColumn;
    @FXML private TableColumn<SObjectItem, String> statusAllStatusColumn;
    
    // In Progress Tab
    @FXML private TableView<SObjectItem> inProgressTable;
    @FXML private TableColumn<SObjectItem, String> progressNameColumn;
    @FXML private TableColumn<SObjectItem, String> progressStatusColumn;
    
    // Completed Tab
    @FXML private TableView<SObjectItem> completedTable;
    @FXML private TableColumn<SObjectItem, String> completedNameColumn;
    @FXML private TableColumn<SObjectItem, String> completedRecordsColumn;
    @FXML private TableColumn<SObjectItem, String> completedSizeColumn;
    @FXML private TableColumn<SObjectItem, String> completedTimeColumn;
    
    // Errors Tab
    @FXML private TableView<SObjectItem> errorsTable;
    @FXML private TableColumn<SObjectItem, String> errorNameColumn;
    @FXML private TableColumn<SObjectItem, String> errorMessageColumn;
    
    @FXML private TabPane statusTabPane;
    // Removed: backButton - only used by old backup.fxml
    
    @FXML private RadioButton csvRadioButton;
    @FXML private RadioButton databaseRadioButton;
    @FXML private ToggleGroup backupTargetGroup;
    @FXML private Button databaseSettingsButton;
    @FXML private VBox outputFolderBox;
    
    // Connection selector components
    @FXML private VBox connectionSelectorBox;
    @FXML private ComboBox<SavedConnection> connectionCombo;
    @FXML private HBox connectionInfoBox;
    @FXML private Label connectionTypeLabel;
    @FXML private Label connectionDetailsLabel;
    @FXML private Label connectionStatusIcon;
    
    @FXML private TextField outputFolderField;
    @FXML private TextField recordLimitField;
    @FXML private CheckBox customWhereCheckbox;
    @FXML private TextArea customWhereField;
    @FXML private CheckBox incrementalBackupCheckbox;
    @FXML private CheckBox compressBackupCheckbox;
    @FXML private CheckBox preserveRelationshipsCheckbox;
    @FXML private CheckBox includeRelatedRecordsCheckbox;
    @FXML private ComboBox<String> relationshipDepthCombo;
    @FXML private CheckBox priorityObjectsOnlyCheckbox;
    @FXML private Button previewRelationshipsButton;
    @FXML private Label lastBackupLabel;
    @FXML private Button browseButton;
    @FXML private Button selectAllButton;
    @FXML private Button deselectAllButton;
    @FXML private Button startBackupButton;
    @FXML private Button stopBackupButton;
    @FXML private Button exportResultsButton;
    
    // Removed: openRestoreButton - only used by old backup.fxml
    
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Label progressPercentLabel;
    @FXML private TextArea logArea;
    
    @FXML private Label connectionLabel;
    @FXML private Label apiLimitsLabel;
    @FXML private TextField searchField;
    @FXML private Label selectionCountLabel;
    // Removed: logoutButton - only used by old backup.fxml

    
    private LoginController.ConnectionInfo connectionInfo;
    private DatabaseSettingsController.DatabaseConnectionInfo databaseConnectionInfo;
    private SavedConnection currentSavedConnection;  // Track the currently applied saved connection for session caching
    private String backupType = "data"; // "data", "config", or "metadata"
    private ObservableList<SObjectItem> allObjects;
    private FilteredList<SObjectItem> filteredObjects;
    private FilteredList<SObjectItem> inProgressObjects;
    private FilteredList<SObjectItem> completedObjects;
    private FilteredList<SObjectItem> errorObjects;
    private BackupTask currentBackupTask;
    private Thread backupThread;
    private BackupRun currentBackupRun;
    private RelationshipBackupConfig relationshipBackupConfig;

    @FXML
    public void initialize() {
        setupAllObjectsTable();
        setupAllStatusTable();
        setupInProgressTable();
        setupCompletedTable();
        setupErrorsTable();
        setupConnectionSelector();
        
        // Set default output folder
        String userHome = System.getProperty("user.home");
        outputFolderField.setText(userHome + "\\BackupForce\\backup");
        
        // Setup backup target listeners
        csvRadioButton.setSelected(true);
        backupTargetGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == csvRadioButton) {
                databaseSettingsButton.setDisable(true);
                outputFolderBox.setVisible(true);
                outputFolderBox.setManaged(true);
                connectionSelectorBox.setVisible(false);
                connectionSelectorBox.setManaged(false);
            } else {
                databaseSettingsButton.setDisable(false);
                outputFolderBox.setVisible(false);
                outputFolderBox.setManaged(false);
                connectionSelectorBox.setVisible(true);
                connectionSelectorBox.setManaged(true);
                loadSavedConnections();
            }
        });
        
        // Setup search filter
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterObjects(newValue));
        
        stopBackupButton.setDisable(true);
        progressBar.setProgress(0);
        
        // Setup incremental backup checkbox listener
        if (incrementalBackupCheckbox != null) {
            incrementalBackupCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                updateLastBackupLabel();
            });
        }
        
        // Setup "Include Related Records" checkbox and depth selector
        setupRelationshipAwareBackupControls();
        
        // Initialize selection counter
        updateSelectionCount();
    }
    
    private void setupAllObjectsTable() {
        allSelectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        // Custom checkbox cell factory to disable checkboxes for unsupported objects
        allSelectColumn.setCellFactory(column -> new TableCell<SObjectItem, Boolean>() {
            private final javafx.scene.control.CheckBox checkBox = new javafx.scene.control.CheckBox();
            
            {
                checkBox.setOnAction(e -> {
                    SObjectItem item = getTableRow().getItem();
                    if (item != null && !item.isDisabled()) {
                        item.setSelected(checkBox.isSelected());
                        updateSelectionCount();
                    }
                });
            }
            
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    SObjectItem sObjectItem = getTableRow().getItem();
                    if (sObjectItem != null) {
                        checkBox.setSelected(sObjectItem.isSelected());
                        checkBox.setDisable(sObjectItem.isDisabled());
                        setGraphic(checkBox);
                    }
                }
            }
        });
        allSelectColumn.setEditable(true);
        
        allNameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        allLabelColumn.setCellValueFactory(cellData -> cellData.getValue().labelProperty());
        allStatusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        
        // Fields column - gear icon button for each row
        allFieldsColumn.setCellFactory(column -> new TableCell<SObjectItem, Void>() {
            private final Button gearButton = new Button("⚙");
            private final Tooltip tooltip = new Tooltip();
            
            {
                // Style the gear button with high visibility
                gearButton.setStyle("-fx-padding: 4 10; -fx-font-size: 18px; -fx-text-fill: #adb5bd; " +
                    "-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 4; -fx-cursor: hand;");
                gearButton.setTooltip(tooltip);
                tooltip.setShowDelay(javafx.util.Duration.millis(200));
                
                gearButton.setOnAction(e -> {
                    SObjectItem item = getTableRow().getItem();
                    if (item != null) {
                        openFieldSelectionDialog(item);
                    }
                });
                
                // Hover effect
                gearButton.setOnMouseEntered(e -> {
                    SObjectItem item = getTableRow().getItem();
                    if (item != null && item.hasCustomFieldSelection()) {
                        gearButton.setStyle("-fx-padding: 4 10; -fx-font-size: 18px; -fx-text-fill: #4caf50; " +
                            "-fx-background-color: rgba(76,175,80,0.3); -fx-background-radius: 4; -fx-cursor: hand;");
                    } else {
                        gearButton.setStyle("-fx-padding: 4 10; -fx-font-size: 18px; -fx-text-fill: #fff; " +
                            "-fx-background-color: rgba(255,255,255,0.2); -fx-background-radius: 4; -fx-cursor: hand;");
                    }
                });
                gearButton.setOnMouseExited(e -> {
                    SObjectItem item = getTableRow().getItem();
                    if (item != null && item.hasCustomFieldSelection()) {
                        gearButton.setStyle("-fx-padding: 4 10; -fx-font-size: 18px; -fx-text-fill: #4caf50; " +
                            "-fx-background-color: rgba(76,175,80,0.15); -fx-background-radius: 4; -fx-cursor: hand;");
                    } else {
                        gearButton.setStyle("-fx-padding: 4 10; -fx-font-size: 18px; -fx-text-fill: #adb5bd; " +
                            "-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 4; -fx-cursor: hand;");
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    SObjectItem sObjectItem = getTableRow().getItem();
                    if (sObjectItem != null) {
                        // Highlight if custom fields are configured
                        if (sObjectItem.hasCustomFieldSelection()) {
                            gearButton.setStyle("-fx-padding: 4 10; -fx-font-size: 18px; -fx-text-fill: #4caf50; " +
                                "-fx-background-color: rgba(76,175,80,0.15); -fx-background-radius: 4; -fx-cursor: hand;");
                            tooltip.setText("✓ Custom fields configured\n" + sObjectItem.getFieldSelectionInfo() + 
                                "\n\nClick to modify field selection");
                        } else {
                            gearButton.setStyle("-fx-padding: 4 10; -fx-font-size: 18px; -fx-text-fill: #adb5bd; " +
                                "-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 4; -fx-cursor: hand;");
                            tooltip.setText("Configure which fields to backup\n(Currently: All fields)");
                        }
                        setGraphic(gearButton);
                        setAlignment(javafx.geometry.Pos.CENTER);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        
        // Color coding for status
        allStatusColumn.setCellFactory(column -> new TableCell<SObjectItem, String>() {
            private final Label label = new Label();
            {
                label.setWrapText(true);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setPrefHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                setGraphic(label);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    label.setText(null);
                    label.setStyle("");
                } else {
                    label.setText(item);
                    if (item.startsWith("✓")) {
                        label.setStyle("-fx-text-fill: #4caf50; -fx-font-weight: bold;");
                    } else if (item.startsWith("✗")) {
                        label.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
                    } else if (item.startsWith("⊘")) {
                        label.setStyle("-fx-text-fill: #ffa726; -fx-font-weight: bold;");
                    } else if (item.startsWith("\u2298")) {
                        label.setStyle("-fx-text-fill: #8892a6; -fx-font-style: italic;");
                    } else if (item.contains("Processing") || item.contains("Creating") || item.contains("Downloading")) {
                        label.setStyle("-fx-text-fill: #4a9eff;");
                    } else {
                        label.setStyle("");
                    }
                }
            }
        });
        
        // Gray out disabled rows
        allObjectsTable.setRowFactory(tv -> new TableRow<SObjectItem>() {
            @Override
            protected void updateItem(SObjectItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (item.isDisabled()) {
                    setStyle("-fx-opacity: 0.5; -fx-text-fill: gray;");
                } else {
                    setStyle("");
                }
            }
        });
        
        allObjectsTable.setEditable(true);
    }
    
    private void setupAllStatusTable() {
        statusAllNameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        statusAllLabelColumn.setCellValueFactory(cellData -> cellData.getValue().labelProperty());
        statusAllStatusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        
        // Color coding for status
        statusAllStatusColumn.setCellFactory(column -> new TableCell<SObjectItem, String>() {
            private final Label label = new Label();
            {
                label.setWrapText(true);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setPrefHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                setGraphic(label);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    label.setText(null);
                    label.setStyle("");
                } else {
                    label.setText(item);
                    if (item.startsWith("✓")) {
                        label.setStyle("-fx-text-fill: #4caf50; -fx-font-weight: bold;");
                    } else if (item.startsWith("✗")) {
                        label.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
                    } else if (item.startsWith("⊘")) {
                        label.setStyle("-fx-text-fill: #ffa726; -fx-font-weight: bold;");
                    } else if (item.startsWith("\u2298")) {
                        label.setStyle("-fx-text-fill: #8892a6; -fx-font-style: italic;");
                    } else if (item.contains("Processing") || item.contains("Creating") || item.contains("Downloading")) {
                        label.setStyle("-fx-text-fill: #4a9eff; -fx-font-weight: 500;");
                    } else {
                        label.setStyle("");
                    }
                }
            }
        });
    }
    
    private void setupInProgressTable() {
        progressNameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        progressStatusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        
        progressStatusColumn.setCellFactory(column -> new TableCell<SObjectItem, String>() {
            private final Label label = new Label();
            {
                label.setWrapText(true);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setPrefHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                setGraphic(label);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    label.setText(null);
                    label.setStyle("");
                } else {
                    label.setText(item);
                    label.setStyle("-fx-text-fill: #4a9eff;");
                }
            }
        });
    }
    
    private void setupCompletedTable() {
        completedNameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        completedRecordsColumn.setCellValueFactory(cellData -> cellData.getValue().recordCountProperty());
        completedSizeColumn.setCellValueFactory(cellData -> cellData.getValue().fileSizeProperty());
        completedTimeColumn.setCellValueFactory(cellData -> cellData.getValue().durationProperty());
        
        // Green styling for completed items
        completedTable.setRowFactory(tv -> new TableRow<SObjectItem>() {
            @Override
            protected void updateItem(SObjectItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    setStyle("");
                }
            }
        });
    }
    
    private void setupErrorsTable() {
        errorNameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        errorMessageColumn.setCellValueFactory(cellData -> cellData.getValue().errorMessageProperty());
        
        // Enable text wrapping for error messages
        errorMessageColumn.setCellFactory(column -> new TableCell<SObjectItem, String>() {
            private final Label label = new Label();
            {
                label.setWrapText(true);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setPrefHeight(Region.USE_COMPUTED_SIZE);
                setGraphic(label);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    label.setText(null);
                } else {
                    label.setText(item);
                }
            }
        });
        
        // Red styling for error items
        errorsTable.setRowFactory(tv -> new TableRow<SObjectItem>() {
            @Override
            protected void updateItem(SObjectItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    setStyle("");
                }
            }
        });
    }
    
    private void setupConnectionSelector() {
        // Setup ComboBox to display connection names
        connectionCombo.setConverter(new StringConverter<SavedConnection>() {
            @Override
            public String toString(SavedConnection connection) {
                return connection != null ? connection.getDisplayName() : "";
            }
            
            @Override
            public SavedConnection fromString(String string) {
                return null; // Not needed for display-only
            }
        });
        
        // Listen for connection selection changes
        connectionCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                applyConnection(newVal);
            }
        });
    }
    
    /**
     * Setup the "Include Related Records" checkbox and depth selector.
     * When enabled, the backup will automatically include child objects
     * (Contacts, Opportunities, etc.) for any backed-up parent records.
     */
    private void setupRelationshipAwareBackupControls() {
        if (includeRelatedRecordsCheckbox == null) {
            return; // UI elements not present in this FXML
        }
        
        // Set default selection for depth combo
        if (relationshipDepthCombo != null) {
            relationshipDepthCombo.getSelectionModel().selectFirst();
        }
        
        // Enable/disable depth and priority checkboxes based on main checkbox
        includeRelatedRecordsCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (relationshipDepthCombo != null) {
                relationshipDepthCombo.setDisable(!newVal);
            }
            if (priorityObjectsOnlyCheckbox != null) {
                priorityObjectsOnlyCheckbox.setDisable(!newVal);
            }
            if (previewRelationshipsButton != null) {
                previewRelationshipsButton.setDisable(!newVal);
            }
            
            // Log the setting change
            if (newVal) {
                logger.info("Include Related Records enabled - will auto-backup child objects");
            } else {
                logger.info("Include Related Records disabled");
            }
        });
        
        // Also enable "Include Related Records" when a record limit is set
        if (recordLimitField != null) {
            recordLimitField.textProperty().addListener((obs, oldVal, newVal) -> {
                try {
                    int limit = Integer.parseInt(newVal.trim());
                    if (limit > 0 && includeRelatedRecordsCheckbox != null && !includeRelatedRecordsCheckbox.isSelected()) {
                        // Show hint that they might want to enable related records
                        logger.debug("Record limit {} set - consider enabling 'Include Related Records'", limit);
                    }
                } catch (NumberFormatException ignored) {
                    // Not a valid number, ignore
                }
            });
        }
    }
    
    /**
     * Handle the "Preview" button click - show the relationship preview dialog.
     */
    @FXML
    private void handlePreviewRelationships() {
        logger.info("Preview Relationships button clicked");
        
        // DEBUG: Show visible confirmation that button was clicked
        System.out.println("=== PREVIEW BUTTON CLICKED ===");
        System.out.println("connectionInfo: " + (connectionInfo != null ? "SET" : "NULL"));
        
        if (connectionInfo == null) {
            logger.warn("Preview clicked but not connected to Salesforce");
            showError("Please connect to Salesforce first");
            return;
        }
        
        System.out.println("Instance URL: " + connectionInfo.getInstanceUrl());
        
        List<SObjectItem> selectedObjects = allObjects.stream()
            .filter(SObjectItem::isSelected)
            .filter(item -> !item.isDisabled())
            .collect(Collectors.toList());
        
        logger.info("Selected objects for preview: {}", selectedObjects.size());
        System.out.println("Selected objects count: " + selectedObjects.size());
        
        if (selectedObjects.isEmpty()) {
            showError("Please select at least one object to preview relationships");
            return;
        }
        
        try {
            logger.info("Loading relationship preview dialog...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/relationship-preview-dialog.fxml"));
            Parent root = loader.load();
            
            RelationshipPreviewController controller = loader.getController();
            
            List<String> parentObjects = selectedObjects.stream()
                .map(SObjectItem::getName)
                .collect(Collectors.toList());
            
            controller.initializeWithData(
                connectionInfo.getInstanceUrl(),
                connectionInfo.getSessionId(),
                "62.0",
                parentObjects,
                getRelationshipDepth(),
                isPriorityObjectsOnly()
            );
            
            Stage dialog = new Stage();
            dialog.setTitle("Relationship-Aware Backup Preview");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(startBackupButton.getScene().getWindow());
            
            Scene scene = new Scene(root);
            dialog.setScene(scene);
            dialog.showAndWait();
            
            // Store the config if user confirmed
            if (controller.isConfirmed()) {
                this.relationshipBackupConfig = controller.getConfig();
                logMessage("Relationship backup configured: " + 
                    controller.getSelectedRelatedObjects().size() + " related objects selected");
            }
            
        } catch (Exception e) {
            logger.error("Failed to show relationship preview", e);
            showError("Failed to load relationship preview: " + e.getMessage());
        }
    }
    
    /**
     * Gets the relationship depth from the combo box selection.
     * @return Depth value (1, 2, or 3)
     */
    private int getRelationshipDepth() {
        if (relationshipDepthCombo == null || relationshipDepthCombo.getValue() == null) {
            return 1; // Default to direct children only
        }
        String selected = relationshipDepthCombo.getValue();
        if (selected.startsWith("1")) return 1;
        if (selected.startsWith("2")) return 2;
        if (selected.startsWith("3")) return 3;
        return 1;
    }
    
    /**
     * Checks if only priority child objects should be included.
     */
    private boolean isPriorityObjectsOnly() {
        return priorityObjectsOnlyCheckbox == null || priorityObjectsOnlyCheckbox.isSelected();
    }
    
    /**
     * Checks if related records should be included in the backup.
     */
    private boolean isIncludeRelatedRecords() {
        return includeRelatedRecordsCheckbox != null && includeRelatedRecordsCheckbox.isSelected();
    }
    
    private void loadSavedConnections() {
        try {
            logger.info("Loading saved connections...");
            List<SavedConnection> connections = ConnectionManager.getInstance().getConnections();
            logger.info("Found {} saved connections", connections.size());
            
            connectionCombo.setItems(FXCollections.observableArrayList(connections));
            
            // Select the last used connection
            SavedConnection lastUsed = ConnectionManager.getInstance().getLastUsedConnection();
            if (lastUsed != null) {
                logger.info("Selecting last used connection: {}", lastUsed.getName());
                connectionCombo.setValue(lastUsed);
            } else if (!connections.isEmpty()) {
                logger.info("Selecting first connection: {}", connections.get(0).getName());
                connectionCombo.setValue(connections.get(0));
            }
            logger.info("Connection list loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to load saved connections", e);
            showAlert("Error", "Failed to load saved connections: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleRefreshConnections() {
        SavedConnection currentSelection = connectionCombo.getValue();
        loadSavedConnections();
        
        // Try to restore the previous selection
        if (currentSelection != null) {
            connectionCombo.setValue(currentSelection);
        }
    }
    
    private void applyConnection(SavedConnection connection) {
        logger.info("Applying connection: {} (ID: {})", connection.getName(), connection.getId());
        
        // Show testing indicator
        connectionInfoBox.setVisible(true);
        connectionInfoBox.setManaged(true);
        connectionTypeLabel.setText(connection.getType());
        connectionDetailsLabel.setText("Checking session...");
        connectionStatusIcon.setText("○");
        connectionStatusIcon.setStyle("-fx-font-size: 18px; -fx-text-fill: #888;");
        
        // Check for cached session first (avoids re-authentication for SSO)
        logger.debug("Checking for cached session with ID: {}", connection.getId());
        ConnectionManager.CachedSession cachedSession = ConnectionManager.getInstance().getCachedSession(connection.getId());
        if (cachedSession != null) {
            logger.info("Found cached session for connection: {} (ID: {})", connection.getName(), connection.getId());
            // Verify the cached connection is still valid
            try {
                if (cachedSession.getConnection().isValid(5)) {
                    logger.info("Cached session is valid - using it for: {}", connection.getName());
                    applyValidatedConnection(connection);
                    return;
                } else {
                    logger.info("Cached session connection is closed/invalid for: {}", connection.getName());
                    ConnectionManager.getInstance().invalidateSession(connection.getId());
                }
            } catch (Exception e) {
                logger.warn("Error checking cached session validity: {}", e.getMessage());
                ConnectionManager.getInstance().invalidateSession(connection.getId());
            }
        } else {
            logger.info("No cached session found for connection: {} (ID: {})", connection.getName(), connection.getId());
        }
        
        // Update status
        connectionDetailsLabel.setText("Authenticating...");
        
        // Validate the connection in background
        Task<java.sql.Connection> validateTask = new Task<>() {
            @Override
            protected java.sql.Connection call() throws Exception {
                String jdbcUrl = buildJdbcUrl(connection);
                logger.info("Connecting with JDBC URL: {}", jdbcUrl.replaceAll("password=[^&]*", "password=***"));
                String username = connection.isUseSso() ? "" : connection.getUsername();
                String password = connection.isUseSso() ? "" : ConnectionManager.getInstance().getDecryptedPassword(connection);
                
                java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, username, password);
                if (conn.isValid(10)) {
                    return conn;
                } else {
                    conn.close();
                    throw new Exception("Connection validation failed");
                }
            }
        };
        
        validateTask.setOnSucceeded(e -> {
            java.sql.Connection conn = validateTask.getValue();
            logger.info("Connection authenticated successfully: {}", connection.getName());
            
            // Cache the session for future use
            ConnectionManager.getInstance().cacheSession(connection.getId(), conn);
            logger.info("Session cached for connection: {} (ID: {})", connection.getName(), connection.getId());
            
            // Connection is valid - apply it
            applyValidatedConnection(connection);
        });
        
        validateTask.setOnFailed(e -> {
            String error = validateTask.getException() != null 
                ? validateTask.getException().getMessage() 
                : "Unknown error";
            logger.error("Connection error for {}: {}", connection.getName(), error);
            showConnectionError(connection, error);
        });
        
        Thread thread = new Thread(validateTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    private String buildJdbcUrl(SavedConnection connection) {
        switch (connection.getType()) {
            case "Snowflake":
                String account = connection.getAccount();
                String warehouse = connection.getWarehouse();
                String database = connection.getDatabase();
                String schema = connection.getSchema();
                if (connection.isUseSso()) {
                    return String.format("jdbc:snowflake://%s.snowflakecomputing.com/?warehouse=%s&db=%s&schema=%s&authenticator=externalbrowser",
                        account, warehouse, database, schema);
                } else {
                    return String.format("jdbc:snowflake://%s.snowflakecomputing.com/?warehouse=%s&db=%s&schema=%s",
                        account, warehouse, database, schema);
                }
                    
            case "SQL Server":
                String server = connection.getHost();
                String db = connection.getDatabase();
                return String.format("jdbc:sqlserver://%s;databaseName=%s;encrypt=true;trustServerCertificate=true",
                    server, db);
                    
            case "PostgreSQL":
                String host = connection.getHost();
                String port = connection.getPort() != null ? connection.getPort() : "5432";
                String pgDb = connection.getDatabase();
                return String.format("jdbc:postgresql://%s:%s/%s", host, port, pgDb);
                
            default:
                return "";
        }
    }
    
    private void showConnectionError(SavedConnection connection, String error) {
        Platform.runLater(() -> {
            connectionStatusIcon.setText("✗");
            connectionStatusIcon.setStyle("-fx-font-size: 18px; -fx-text-fill: #e74c3c;");
            connectionDetailsLabel.setText("Connection failed - click to configure");
            
            // Clear the database connection info and saved connection since it's invalid
            databaseConnectionInfo = null;
            currentSavedConnection = null;
            
            // Show alert with option to configure
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection Failed");
            alert.setHeaderText("Cannot connect to " + connection.getName());
            alert.setContentText(error + "\n\nWould you like to configure this connection?");
            
            ButtonType configureBtn = new ButtonType("Configure");
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(configureBtn, cancelBtn);
            
            alert.showAndWait().ifPresent(response -> {
                if (response == configureBtn) {
                    handleDatabaseSettings();
                }
            });
        });
    }
    
    private void applyValidatedConnection(SavedConnection connection) {
        try {
            // Store the SavedConnection for session caching
            this.currentSavedConnection = connection;
            
            // Convert SavedConnection to DatabaseConnectionInfo
            Map<String, String> fields = new HashMap<>();
            
            if ("Snowflake".equalsIgnoreCase(connection.getType())) {
                fields.put("Account", connection.getAccount());
                fields.put("Warehouse", connection.getWarehouse());
                fields.put("Database", connection.getDatabase());
                fields.put("Schema", connection.getSchema());
                fields.put("Username", connection.getUsername());
                fields.put("Password", ConnectionManager.getInstance().getDecryptedPassword(connection));
            } else {
                // For other database types
                fields.put("Host", connection.getHost());
                if (connection.getPort() != null && !connection.getPort().isEmpty()) {
                    fields.put("Port", connection.getPort());
                }
                fields.put("Database", connection.getDatabase());
                fields.put("Schema", connection.getSchema());
                fields.put("Username", connection.getUsername());
                fields.put("Password", ConnectionManager.getInstance().getDecryptedPassword(connection));
            }
            
            databaseConnectionInfo = new DatabaseConnectionInfo(
                connection.getType(),
                fields,
                connection.isUseSso(),
                true // recreate tables - can be configured
            );
            
            // Update the connection info display
            connectionInfoBox.setVisible(true);
            connectionInfoBox.setManaged(true);
            connectionTypeLabel.setText(connection.getType());
            connectionDetailsLabel.setText(
                String.format("%s.%s", 
                    connection.getDatabase() != null ? connection.getDatabase() : "N/A",
                    connection.getSchema() != null ? connection.getSchema() : "N/A")
            );
            connectionStatusIcon.setText("●");
            connectionStatusIcon.setStyle("-fx-font-size: 18px; -fx-text-fill: #4CAF50;");
            
            // Mark this as the last used connection
            ConnectionManager.getInstance().setLastUsedConnection(connection.getId());
            
            // Update the main connection label
            updateConnectionLabel();
            
            logger.info("Applied connection: {}", connection.getDisplayName());
        } catch (Exception e) {
            logger.error("Failed to apply connection", e);
            showAlert("Error", "Failed to apply connection: " + e.getMessage());
            connectionInfoBox.setVisible(false);
            connectionInfoBox.setManaged(false);
        }
    }

    public void setConnectionInfo(LoginController.ConnectionInfo connInfo) {
        this.connectionInfo = connInfo;
        updateConnectionLabel();
        loadObjects();
        fetchApiLimits();
        updateLastBackupLabel();
    }
    
    public void setBackupType(String backupType) {
        this.backupType = backupType;
        logger.info("Backup type set to: {}", backupType);
        // TODO: Update UI based on backup type (data, config, metadata)
    }
    
    public String getBackupType() {
        return backupType;
    }
    
    private void updateConnectionLabel() {
        // connectionLabel may be null in the content-only FXML (loaded by MainController)
        if (connectionLabel == null) {
            return;
        }
        
        StringBuilder labelText = new StringBuilder();
        labelText.append("Salesforce: ").append(connectionInfo.getUsername());
        labelText.append(" @ ").append(connectionInfo.getInstanceUrl());
        
        // Add database info if configured
        if (databaseConnectionInfo != null) {
            Map<String, String> fields = databaseConnectionInfo.getFields();
            labelText.append(" → Database: ");
            
            if ("Snowflake".equals(databaseConnectionInfo.getDatabaseType())) {
                String account = fields.get("Account");
                String database = fields.get("Database");
                String schema = fields.get("Schema");
                labelText.append(account != null ? account : "?");
                labelText.append(".");
                labelText.append(database != null ? database : "?");
                labelText.append(".");
                labelText.append(schema != null ? schema : "?");
            } else {
                // For other database types, show database and schema
                String database = fields.get("Database");
                String schema = fields.get("Schema");
                if (database != null) {
                    labelText.append(database);
                    if (schema != null) {
                        labelText.append(".").append(schema);
                    }
                } else {
                    labelText.append(databaseConnectionInfo.getDatabaseType());
                }
            }
        }
        
        connectionLabel.setText(labelText.toString());
    }

    // Known objects that are not supported by Bulk API
    private static final Set<String> KNOWN_UNSUPPORTED = new HashSet<>(Arrays.asList(
        "AcceptedEventRelation", "ActivityHistory", "AggregateResult", "AttachedContentDocument",
        "CaseStatus", "CombinedAttachment", "ContractStatus", "DeclinedEventRelation",
        "EventRelation", "Name", "NoteAndAttachment", "OpenActivity", "OwnedContentDocument",
        "PartnerRole", "RecentlyViewed", "SolutionStatus", "TaskPriority", "TaskStatus",
        "UndecidedEventRelation", "UserRecordAccess"
    ));
    
    private void loadObjects() {
        Task<List<SObjectItem>> loadTask = new Task<List<SObjectItem>>() {
            @Override
            protected List<SObjectItem> call() throws Exception {
                updateMessage("Loading Salesforce objects...");
                
                DescribeGlobalResult dgr = connectionInfo.getConnection().describeGlobal();
                DescribeGlobalSObjectResult[] sobjects = dgr.getSobjects();
                
                List<SObjectItem> items = new ArrayList<>();
                for (DescribeGlobalSObjectResult sobject : sobjects) {
                    if (sobject.isQueryable()) {
                        SObjectItem item = new SObjectItem(sobject.getName(), sobject.getLabel());
                        
                        // Check if object is known to be unsupported by Bulk API
                        if (KNOWN_UNSUPPORTED.contains(sobject.getName())) {
                            item.setDisabled(true);
                            item.setStatus("⊘ Not supported by Bulk API");
                        }
                        
                        items.add(item);
                    }
                }
                
                // Sort by name
                items.sort(Comparator.comparing(SObjectItem::getName));
                
                return items;
            }
        };

        loadTask.setOnSucceeded(event -> {
            allObjects = FXCollections.observableArrayList(loadTask.getValue());
            
            // Create filtered lists for each tab
            filteredObjects = new FilteredList<>(allObjects, p -> true);
            inProgressObjects = new FilteredList<>(allObjects, 
                item -> item.getStatus().contains("Processing") || 
                        item.getStatus().contains("Creating") || 
                        item.getStatus().contains("Downloading"));
            completedObjects = new FilteredList<>(allObjects, 
                item -> item.getStatus().startsWith("✓"));
            errorObjects = new FilteredList<>(allObjects, 
                item -> item.getStatus().startsWith("✗"));
            
            // Add listener to each item's selected property and status property
            for (SObjectItem item : allObjects) {
                item.selectedProperty().addListener((obs, oldVal, newVal) -> updateSelectionCount());
                item.statusProperty().addListener((obs, oldVal, newVal) -> updateFilteredLists());
            }
            
            // Set items to tables
            allObjectsTable.setItems(filteredObjects);
            allStatusTable.setItems(allObjects);
            inProgressTable.setItems(inProgressObjects);
            completedTable.setItems(completedObjects);
            errorsTable.setItems(errorObjects);
            
            logMessage("Loaded " + allObjects.size() + " queryable objects");
            progressLabel.setText("Ready");
            updateSelectionCount();
        });

        loadTask.setOnFailed(event -> {
            logger.error("Failed to load objects", loadTask.getException());
            showError("Failed to load objects: " + loadTask.getException().getMessage());
        });

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void filterObjects(String searchText) {
        if (allObjects == null) return;
        
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredObjects.setPredicate(p -> true);
        } else {
            String search = searchText.toLowerCase();
            filteredObjects.setPredicate(item -> 
                item.getName().toLowerCase().contains(search) || 
                item.getLabel().toLowerCase().contains(search));
        }
    }
    
    private void updateFilteredLists() {
        if (inProgressObjects == null || completedObjects == null || errorObjects == null) {
            return;
        }
        
        // Force re-evaluation by setting predicate to null first, then to the actual predicate
        inProgressObjects.setPredicate(null);
        inProgressObjects.setPredicate(item -> {
            String status = item.getStatus();
            return status != null && (status.contains("Processing") || 
                                     status.contains("Creating") || 
                                     status.contains("Downloading") ||
                                     status.contains("records"));
        });
        
        completedObjects.setPredicate(null);
        completedObjects.setPredicate(item -> {
            String status = item.getStatus();
            return status != null && status.startsWith("✓");
        });
        
        errorObjects.setPredicate(null);
        errorObjects.setPredicate(item -> {
            String status = item.getStatus();
            return status != null && (status.startsWith("✗") || status.startsWith("⊘"));
        });
        
        // Update tab labels with counts
        Platform.runLater(() -> {
            if (statusTabPane != null && statusTabPane.getTabs().size() >= 4) {
                statusTabPane.getTabs().get(0).setText("All (" + allObjects.size() + ")");
                statusTabPane.getTabs().get(1).setText("In Progress (" + inProgressObjects.size() + ")");
                statusTabPane.getTabs().get(2).setText("Completed (" + completedObjects.size() + ")");
                statusTabPane.getTabs().get(3).setText("Errors (" + errorObjects.size() + ")");
            }
        });
    }
    
    private void updateSelectionCount() {
        if (allObjects == null) {
            if (selectionCountLabel != null) {
                selectionCountLabel.setText("0 selected");
            }
            return;
        }
        
        long count = allObjects.stream().filter(SObjectItem::isSelected).count();
        if (selectionCountLabel != null) {
            selectionCountLabel.setText(count + " selected");
        }
    }
    
    /**
     * Update the last backup label to show when incremental backup will start from
     */
    private void updateLastBackupLabel() {
        if (lastBackupLabel == null || connectionInfo == null) {
            return;
        }
        
        if (incrementalBackupCheckbox != null && incrementalBackupCheckbox.isSelected()) {
            // Check backup history for last successful backup
            Optional<BackupRun> lastBackup = BackupHistory.getInstance()
                    .getHistoryForUser(connectionInfo.getUsername())
                    .stream()
                    .filter(r -> "COMPLETED".equals(r.getStatus()))
                    .findFirst();
            
            if (lastBackup.isPresent()) {
                String startTime = lastBackup.get().getStartTime();
                try {
                    // Parse and format nicely
                    LocalDateTime dt = LocalDateTime.parse(startTime);
                    String formatted = dt.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"));
                    lastBackupLabel.setText("(since " + formatted + ")");
                    lastBackupLabel.setStyle("-fx-text-fill: #4CAF50;"); // Green
                } catch (Exception e) {
                    lastBackupLabel.setText("(since " + startTime.substring(0, 10) + ")");
                }
            } else {
                lastBackupLabel.setText("(no previous backup - will do full backup)");
                lastBackupLabel.setStyle("-fx-text-fill: #FF9800;"); // Orange warning
            }
        } else {
            lastBackupLabel.setText("");
        }
    }
    
    /**
     * Fetch Salesforce API limits and display in the header
     */
    private void fetchApiLimits() {
        if (apiLimitsLabel == null || connectionInfo == null) {
            return;
        }
        
        // Fetch limits in background to avoid blocking UI
        Task<ApiLimits> task = new Task<ApiLimits>() {
            @Override
            protected ApiLimits call() throws Exception {
                try (BulkV2Client client = new BulkV2Client(
                        connectionInfo.getInstanceUrl(),
                        connectionInfo.getSessionId(),
                        "62.0")) {
                    return client.getApiLimits();
                }
            }
        };
        
        task.setOnSucceeded(e -> {
            ApiLimits limits = task.getValue();
            if (limits != null) {
                String text = String.format("API: %,d/%,d (%.0f%%) | Bulk: %,d/%,d",
                    limits.dailyApiRequestsUsed, limits.dailyApiRequestsMax,
                    limits.getDailyApiPercentUsed(),
                    limits.bulkApiJobsUsed, limits.bulkApiJobsMax);
                apiLimitsLabel.setText(text);
                
                // Color code based on usage
                if (limits.getDailyApiPercentUsed() > 80) {
                    apiLimitsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #f44336; -fx-padding: 0 10;"); // Red
                } else if (limits.getDailyApiPercentUsed() > 50) {
                    apiLimitsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #FF9800; -fx-padding: 0 10;"); // Orange
                } else {
                    apiLimitsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #4CAF50; -fx-padding: 0 10;"); // Green
                }
            }
        });
        
        task.setOnFailed(e -> {
            logger.warn("Failed to fetch API limits", task.getException());
            apiLimitsLabel.setText("");
        });
        
        new Thread(task).start();
    }

    @FXML
    private void handleBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Folder");
        
        File currentDir = new File(outputFolderField.getText());
        if (currentDir.exists()) {
            chooser.setInitialDirectory(currentDir);
        }
        
        Stage stage = (Stage) browseButton.getScene().getWindow();
        File selectedDir = chooser.showDialog(stage);
        
        if (selectedDir != null) {
            outputFolderField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    // ==================== Database Settings ====================

    @FXML
    private void handleDatabaseSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/database-settings.fxml"));
            Parent root = loader.load();
            
            DatabaseSettingsController controller = loader.getController();
            
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/backupforce-modern.css").toExternalForm());
            
            Stage dialog = new Stage();
            dialog.setTitle("Database Connection Settings");
            dialog.setScene(scene);
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.initOwner(databaseSettingsButton.getScene().getWindow());
            dialog.setResizable(true);
            dialog.setWidth(600);
            dialog.setHeight(700);
            
            dialog.showAndWait();
            
            // Refresh database connection dropdown after dialog closes
            loadSavedConnections();
            
        } catch (IOException e) {
            logger.error("Error opening database settings dialog", e);
            showError("Failed to open database settings: " + e.getMessage());
        }
    }

    private DataSink createDataSinkFromConfig(DatabaseConnectionInfo config) {
        Map<String, String> fields = config.getFields();
        logger.info("Creating DataSink from config. Database type: {}, Fields: {}", config.getDatabaseType(), fields.keySet());
        logger.info("Recreate tables option: {}", config.isRecreateTables());
        
        // Check for cached session first to avoid re-authentication (especially for SSO)
        java.sql.Connection cachedConnection = null;
        if (currentSavedConnection != null) {
            ConnectionManager.CachedSession cachedSession = ConnectionManager.getInstance()
                .getCachedSession(currentSavedConnection.getId());
            if (cachedSession != null) {
                try {
                    if (cachedSession.getConnection().isValid(5)) {
                        cachedConnection = cachedSession.getConnection();
                        logger.info("Using cached database session for: {} (ID: {})", 
                            currentSavedConnection.getName(), currentSavedConnection.getId());
                    }
                } catch (Exception e) {
                    logger.warn("Cached session validation failed: {}", e.getMessage());
                    ConnectionManager.getInstance().invalidateSession(currentSavedConnection.getId());
                }
            }
        }
        
        DataSink sink;
        switch (config.getDatabaseType()) {
            case "Snowflake":
                String account = fields.get("Account");
                String warehouse = fields.get("Warehouse");
                String database = fields.get("Database");
                String schema = fields.get("Schema");
                String username = fields.get("Username");
                String password = fields.get("Password");
                
                logger.info("Snowflake config - Account: {}, Warehouse: {}, Database: {}, Schema: {}, Username: {}, Password: {}", 
                    account, warehouse, database, schema, username, password != null ? "***" : "NULL");
                
                if (account == null || warehouse == null || database == null || schema == null) {
                    throw new IllegalArgumentException("Missing required Snowflake parameters. Account=" + account + 
                        ", Warehouse=" + warehouse + ", Database=" + database + ", Schema=" + schema);
                }
                
                // Use cached connection if available, otherwise create new
                if (cachedConnection != null) {
                    sink = DataSinkFactory.createSnowflakeSinkWithExistingConnection(
                        cachedConnection, database, schema, warehouse);
                } else {
                    // For SSO, password can be null - this will trigger browser auth
                    sink = DataSinkFactory.createSnowflakeSink(account, warehouse, database, schema, username, password);
                }
                break;
            case "SQL Server":
                if (cachedConnection != null) {
                    sink = DataSinkFactory.createSqlServerSinkWithExistingConnection(
                        cachedConnection, fields.get("Server"), fields.get("Database"));
                } else {
                    sink = DataSinkFactory.createSqlServerSink(
                        fields.get("Server"), 
                        fields.get("Database"),
                        fields.get("Username"), 
                        fields.get("Password")
                    );
                }
                break;
            case "PostgreSQL":
                if (cachedConnection != null) {
                    sink = DataSinkFactory.createPostgresSinkWithExistingConnection(
                        cachedConnection, 
                        fields.get("Host"),
                        Integer.parseInt(fields.getOrDefault("Port", "5432")),
                        fields.get("Database"), 
                        fields.get("Schema"));
                } else {
                    sink = DataSinkFactory.createPostgresSink(
                        fields.get("Host"), 
                        Integer.parseInt(fields.getOrDefault("Port", "5432")),
                        fields.get("Database"), 
                        fields.get("Schema"),
                        fields.get("Username"), 
                        fields.get("Password")
                    );
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported database type: " + config.getDatabaseType());
        }
        
        // Set recreate tables option
        sink.setRecreateTables(config.isRecreateTables());
        return sink;
    }

    @FXML
    private void handleCustomWhereToggle() {
        if (customWhereField != null && customWhereCheckbox != null) {
            customWhereField.setDisable(!customWhereCheckbox.isSelected());
            if (!customWhereCheckbox.isSelected()) {
                customWhereField.clear();
            }
        }
    }

    @FXML
    private void handleSelectAll() {
        if (filteredObjects != null) {
            filteredObjects.forEach(item -> item.setSelected(true));
            allObjectsTable.refresh();
            updateSelectionCount();
        }
    }

    @FXML
    private void handleDeselectAll() {
        if (filteredObjects != null) {
            filteredObjects.forEach(item -> item.setSelected(false));
            allObjectsTable.refresh();
            updateSelectionCount();
        }
    }
    
    private void openFieldSelectionDialog(SObjectItem item) {
        if (connectionInfo == null) {
            showError("Not connected to Salesforce");
            return;
        }
        
        FieldSelectionDialog dialog = new FieldSelectionDialog(
            item.getName(),
            connectionInfo.getInstanceUrl(),
            connectionInfo.getSessionId(),
            "62.0",
            item.getSelectedFields()
        );
        
        Optional<Set<String>> result = dialog.showAndWait();
        if (result.isPresent()) {
            Set<String> selectedFields = result.get();
            item.setSelectedFields(selectedFields);
            item.setTotalFieldCount(selectedFields.size()); // Will be updated with actual total
            
            logMessage(String.format("[%s] Field selection configured: %d fields selected", 
                item.getName(), selectedFields.size()));
            
            allObjectsTable.refresh();
        }
    }

    @FXML
    private void handleStartBackup() {
        try {
            handleStartBackupInternal();
        } catch (Exception e) {
            logger.error("Unexpected error starting backup", e);
            showError("Failed to start backup: " + e.getMessage());
        }
    }
    
    private void handleStartBackupInternal() {
        logger.info("=== handleStartBackupInternal() called ===");
        System.out.println("=== handleStartBackupInternal() called ===");
        
        List<SObjectItem> selectedObjects = allObjects.stream()
            .filter(SObjectItem::isSelected)
            .filter(item -> !item.isDisabled())
            .collect(Collectors.toList());
        
        logger.info("Selected objects count: {}", selectedObjects.size());
        
        if (selectedObjects.isEmpty()) {
            logger.warn("No objects selected - aborting backup");
            showError("Please select at least one object to backup");
            return;
        }
        
        // Check for potentially problematic objects
        List<String> largeObjectsSelected = selectedObjects.stream()
            .map(SObjectItem::getName)
            .filter(LARGE_OBJECTS::contains)
            .collect(Collectors.toList());
        
        if (!largeObjectsSelected.isEmpty()) {
            Alert warning = new Alert(Alert.AlertType.WARNING);
            warning.setTitle("Large Objects Detected");
            warning.setHeaderText("Warning: Memory-Intensive Objects Selected");
            warning.setContentText(
                "The following objects contain large binary data and may cause memory issues:\n\n" +
                String.join("\n", largeObjectsSelected) + "\n\n" +
                "Recommendations:\n" +
                "• Backup these objects individually\n" +
                "• Increase Java heap size if needed (-Xmx flag)\n" +
                "• Monitor the log panel for out of memory errors\n\n" +
                "Continue with backup?"
            );
            warning.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            warning.setResizable(true);
            warning.getDialogPane().setPrefWidth(500);
            
            if (warning.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
        }
        
        // Validate backup target
        String outputFolder;
        String displayFolder;
        DataSink dataSink;
        if (csvRadioButton.isSelected()) {
            outputFolder = outputFolderField.getText().trim();
            if (outputFolder.isEmpty()) {
                showError("Please specify an output folder");
                return;
            }
            
            // Create output directory if it doesn't exist
            File outputDir = new File(outputFolder);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            dataSink = DataSinkFactory.createCsvFileSink(outputFolder);
            displayFolder = outputFolder;
        } else {
            logger.info("Database backup selected. databaseConnectionInfo: {}", 
                databaseConnectionInfo != null ? databaseConnectionInfo.getDatabaseType() : "NULL");
            
            if (databaseConnectionInfo == null) {
                logger.warn("databaseConnectionInfo is null - cannot proceed with database backup");
                showError("Please configure database settings first");
                return;
            }
            
            try {
                logger.info("Creating data sink from config...");
                dataSink = createDataSinkFromConfig(databaseConnectionInfo);
                logger.info("Data sink created successfully: {}", dataSink.getDisplayName());
            } catch (Exception e) {
                logger.error("Failed to create database sink", e);
                showError("Failed to connect to database: " + e.getMessage());
                return;
            }
            // For database backup, create temp folder for CSV files from Bulk API
            outputFolder = System.getProperty("java.io.tmpdir") + File.separator + "backupforce_" + System.currentTimeMillis();
            File tempDir = new File(outputFolder);
            tempDir.mkdirs();
            displayFolder = "Database: " + dataSink.getDisplayName();
        }
        
        // Disable controls
        startBackupButton.setDisable(true);
        stopBackupButton.setDisable(false);
        allObjectsTable.setDisable(true);
        browseButton.setDisable(true);
        selectAllButton.setDisable(true);
        deselectAllButton.setDisable(true);
        csvRadioButton.setDisable(true);
        databaseRadioButton.setDisable(true);
        databaseSettingsButton.setDisable(true);
        
        logArea.clear();
        progressBar.setProgress(0);
        progressLabel.setText("Starting backup...");
        
        // Switch to tab view
        Platform.runLater(() -> {
            allObjectsTable.setVisible(false);
            allObjectsTable.setManaged(false);
            statusTabPane.setVisible(true);
            statusTabPane.setManaged(true);
        });
        
        logMessage("=".repeat(60));
        logMessage("BACKUP STARTED");
        logMessage("Objects to backup: " + selectedObjects.size());
        logMessage("Output: " + displayFolder);
        logMessage("=".repeat(60));
        
        // Start tracking backup history
        String backupTypeLabel = csvRadioButton.isSelected() ? "CSV" : 
            (databaseConnectionInfo != null ? databaseConnectionInfo.getDatabaseType() : "Database");
        boolean isIncremental = incrementalBackupCheckbox != null && incrementalBackupCheckbox.isSelected();
        currentBackupRun = BackupHistory.getInstance().startBackup(
            connectionInfo.getUsername(),
            isIncremental ? "INCREMENTAL" : "FULL",
            backupTypeLabel,
            displayFolder,
            selectedObjects.size()
        );
        
        // Reset all statuses and extra fields
        allObjects.forEach(item -> {
            item.setStatus("");
            item.setRecordCount("");
            item.setFileSize("");
            item.setDuration("");
            item.setErrorMessage("");
        });
        
        // Refresh filtered lists
        updateFilteredLists();
        
        // Parse record limit
        int recordLimit = 0;
        try {
            String limitText = recordLimitField.getText().trim();
            if (!limitText.isEmpty() && !limitText.equals("0")) {
                recordLimit = Integer.parseInt(limitText);
                logMessage("Record limit set to: " + recordLimit);
            }
        } catch (NumberFormatException e) {
            // Ignore invalid input, use 0 (no limit)
        }
        
        // Check preserve relationships option
        boolean preserveRelationships = preserveRelationshipsCheckbox != null && preserveRelationshipsCheckbox.isSelected();
        if (preserveRelationships) {
            logMessage("Relationship preservation enabled - will capture external IDs for restore");
        }
        
        // Check include related records option
        boolean includeRelated = isIncludeRelatedRecords();
        int relDepth = getRelationshipDepth();
        boolean priorityOnly = isPriorityObjectsOnly();
        
        if (includeRelated && recordLimit > 0) {
            logMessage("Include Related Records enabled (depth: " + relDepth + 
                      ", priority only: " + priorityOnly + ")");
            logMessage("After backing up selected objects, child records will be automatically included");
        }
        
        // Start backup
        currentBackupTask = new BackupTask(selectedObjects, outputFolder, displayFolder, dataSink, 
                                          recordLimit, preserveRelationships, includeRelated, relDepth, priorityOnly);
        
        currentBackupTask.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                startBackupButton.setDisable(false);
                stopBackupButton.setDisable(true);
                allObjectsTable.setDisable(false);
                browseButton.setDisable(false);
                selectAllButton.setDisable(false);
                deselectAllButton.setDisable(false);
                csvRadioButton.setDisable(false);
                databaseRadioButton.setDisable(false);
                databaseSettingsButton.setDisable(false);
                exportResultsButton.setDisable(false);
            });
        });
        
        currentBackupTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                startBackupButton.setDisable(false);
                stopBackupButton.setDisable(true);
                allObjectsTable.setDisable(false);
                browseButton.setDisable(false);
                selectAllButton.setDisable(false);
                deselectAllButton.setDisable(false);
                csvRadioButton.setDisable(false);
                databaseRadioButton.setDisable(false);
                databaseSettingsButton.setDisable(false);
                exportResultsButton.setDisable(false);
                progressLabel.setText("Backup failed");
                logMessage("Backup failed: " + currentBackupTask.getException().getMessage());
            });
        });
        
        currentBackupTask.setOnCancelled(event -> {
            Platform.runLater(() -> {
                startBackupButton.setDisable(false);
                stopBackupButton.setDisable(true);
                allObjectsTable.setDisable(false);
                browseButton.setDisable(false);
                selectAllButton.setDisable(false);
                deselectAllButton.setDisable(false);
                csvRadioButton.setDisable(false);
                databaseRadioButton.setDisable(false);
                databaseSettingsButton.setDisable(false);
                exportResultsButton.setDisable(false);
                progressLabel.setText("Backup cancelled");
            });
        });
        
        backupThread = new Thread(currentBackupTask);
        backupThread.setDaemon(true);
        backupThread.start();
    }

    @FXML
    private void handleStopBackup() {
        if (currentBackupTask != null) {
            currentBackupTask.cancel();
            logMessage("Stopping backup...");
        }
    }
    
    @FXML
    private void handleExportResults() {
        List<SObjectItem> completedItems = allObjects.stream()
            .filter(item -> item.getStatus().contains("Completed") || item.getStatus().contains("Failed") || item.getStatus().contains("Not Supported"))
            .collect(Collectors.toList());
        
        if (completedItems.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Results");
            alert.setHeaderText("No backup results to export");
            alert.setContentText("Please run a backup first.");
            alert.showAndWait();
            return;
        }
        
        // Generate table in different formats
        String markdownTable = generateMarkdownTable(completedItems);
        String plainTextTable = generatePlainTextTable(completedItems);
        String csvTable = generateCSVTable(completedItems);
        
        // Create a dialog with tabs for different formats
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Export Backup Results");
        dialog.setHeaderText("Choose format and copy to clipboard");
        
        TabPane tabPane = new TabPane();
        
        // Markdown tab (good for Teams, Slack)
        Tab markdownTab = new Tab("Markdown (Teams/Slack)");
        TextArea markdownArea = new TextArea(markdownTable);
        markdownArea.setEditable(false);
        markdownArea.setWrapText(false);
        markdownArea.setPrefSize(600, 400);
        markdownArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        Button copyMarkdownBtn = new Button("Copy to Clipboard");
        copyMarkdownBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(markdownTable);
            clipboard.setContent(content);
            copyMarkdownBtn.setText("✓ Copied!");
            Platform.runLater(() -> {
                try { Thread.sleep(2000); } catch (Exception ex) {}
                copyMarkdownBtn.setText("Copy to Clipboard");
            });
        });
        VBox markdownBox = new VBox(10, markdownArea, copyMarkdownBtn);
        markdownBox.setPadding(new javafx.geometry.Insets(10));
        markdownTab.setContent(markdownBox);
        
        // Plain text tab
        Tab plainTab = new Tab("Plain Text");
        TextArea plainArea = new TextArea(plainTextTable);
        plainArea.setEditable(false);
        plainArea.setWrapText(false);
        plainArea.setPrefSize(600, 400);
        plainArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        Button copyPlainBtn = new Button("Copy to Clipboard");
        copyPlainBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(plainTextTable);
            clipboard.setContent(content);
            copyPlainBtn.setText("✓ Copied!");
            Platform.runLater(() -> {
                try { Thread.sleep(2000); } catch (Exception ex) {}
                copyPlainBtn.setText("Copy to Clipboard");
            });
        });
        VBox plainBox = new VBox(10, plainArea, copyPlainBtn);
        plainBox.setPadding(new javafx.geometry.Insets(10));
        plainTab.setContent(plainBox);
        
        // CSV tab
        Tab csvTab = new Tab("CSV");
        TextArea csvArea = new TextArea(csvTable);
        csvArea.setEditable(false);
        csvArea.setWrapText(false);
        csvArea.setPrefSize(600, 400);
        csvArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        Button copyCsvBtn = new Button("Copy to Clipboard");
        copyCsvBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(csvTable);
            clipboard.setContent(content);
            copyCsvBtn.setText("✓ Copied!");
            Platform.runLater(() -> {
                try { Thread.sleep(2000); } catch (Exception ex) {}
                copyCsvBtn.setText("Copy to Clipboard");
            });
        });
        VBox csvBox = new VBox(10, csvArea, copyCsvBtn);
        csvBox.setPadding(new javafx.geometry.Insets(10));
        csvTab.setContent(csvBox);
        
        tabPane.getTabs().addAll(markdownTab, plainTab, csvTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
    
    private String generateMarkdownTable(List<SObjectItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Object Name | Status | Records | Size | Duration |\n");
        sb.append("|-------------|--------|---------|------|----------|\n");
        
        for (SObjectItem item : items) {
            sb.append(String.format("| %s | %s | %s | %s | %s |\n",
                item.getName(),
                item.getStatus().replace("|", "\\|"),
                item.getRecordCount().isEmpty() ? "N/A" : item.getRecordCount(),
                item.getFileSize().isEmpty() ? "N/A" : item.getFileSize(),
                item.getDuration().isEmpty() ? "N/A" : item.getDuration()
            ));
        }
        
        return sb.toString();
    }
    
    private String generatePlainTextTable(List<SObjectItem> items) {
        StringBuilder sb = new StringBuilder();
        
        // Calculate column widths
        int nameWidth = Math.max(20, items.stream().mapToInt(i -> i.getName().length()).max().orElse(20));
        int statusWidth = Math.max(15, items.stream().mapToInt(i -> i.getStatus().length()).max().orElse(15));
        
        String format = "%-" + nameWidth + "s  %-" + statusWidth + "s  %-12s  %-10s  %-10s\n";
        String separator = "-".repeat(nameWidth) + "  " + "-".repeat(statusWidth) + "  " + "-".repeat(12) + "  " + "-".repeat(10) + "  " + "-".repeat(10) + "\n";
        
        sb.append(String.format(format, "Object Name", "Status", "Records", "Size", "Duration"));
        sb.append(separator);
        
        for (SObjectItem item : items) {
            sb.append(String.format(format,
                item.getName(),
                item.getStatus(),
                item.getRecordCount().isEmpty() ? "N/A" : item.getRecordCount(),
                item.getFileSize().isEmpty() ? "N/A" : item.getFileSize(),
                item.getDuration().isEmpty() ? "N/A" : item.getDuration()
            ));
        }
        
        return sb.toString();
    }
    
    private String generateCSVTable(List<SObjectItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("Object Name,Status,Records,Size,Duration\n");
        
        for (SObjectItem item : items) {
            sb.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                item.getName(),
                item.getStatus().replace("\"", "\"\""),
                item.getRecordCount().isEmpty() ? "N/A" : item.getRecordCount(),
                item.getFileSize().isEmpty() ? "N/A" : item.getFileSize(),
                item.getDuration().isEmpty() ? "N/A" : item.getDuration()
            ));
        }
        
        return sb.toString();
    }

    /**
     * Log message with batching to prevent UI thread saturation.
     * Messages are queued and flushed to UI every 200ms.
     */
    private void logMessage(String message) {
        logger.info(message);
        pendingLogMessages.add(message);
    }
    
    /**
     * Start the log flush scheduler - call when backup starts
     */
    private void startLogFlushScheduler() {
        if (logFlushScheduler != null && !logFlushScheduler.isShutdown()) {
            return;
        }
        logFlushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogFlushScheduler");
            t.setDaemon(true);
            return t;
        });
        logFlushScheduler.scheduleAtFixedRate(this::flushLogMessages, 200, 200, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Stop the log flush scheduler - call when backup ends
     */
    private void stopLogFlushScheduler() {
        if (logFlushScheduler != null) {
            logFlushScheduler.shutdown();
            try {
                logFlushScheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Final flush
            flushLogMessages();
        }
    }
    
    /**
     * Flush pending log messages to UI (called on timer)
     */
    private void flushLogMessages() {
        if (pendingLogMessages.isEmpty()) {
            return;
        }
        
        StringBuilder batch = new StringBuilder();
        String msg;
        int count = 0;
        final int MAX_BATCH = 50; // Limit batch size to prevent very long strings
        
        while ((msg = pendingLogMessages.poll()) != null && count < MAX_BATCH) {
            batch.append(msg).append("\n");
            count++;
        }
        
        if (batch.length() > 0) {
            final String batchText = batch.toString();
            Platform.runLater(() -> {
                if (logArea != null) {
                    logArea.appendText(batchText);
                    // Only scroll occasionally, not every batch
                    logArea.setScrollTop(Double.MAX_VALUE);
                }
            });
        }
    }
    
    /**
     * Create a throttled status callback that limits UI updates
     */
    private BulkV2Client.ProgressCallback createThrottledCallback(SObjectItem item) {
        final long[] lastUpdate = {0};
        return (status) -> {
            long now = System.currentTimeMillis();
            // Only update UI every 100ms per object
            if (now - lastUpdate[0] >= UI_UPDATE_THROTTLE_MS) {
                lastUpdate[0] = now;
                Platform.runLater(() -> item.setStatus(status));
            }
        };
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Backup Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for backup task
    private class BackupTask extends Task<Void> {
        private final List<SObjectItem> objects;
        private final String outputFolder;
        private final String displayFolder;
        private final DataSink dataSink;
        private final int recordLimit;
        private final boolean preserveRelationships;
        private final boolean includeRelatedRecords;
        private final int relationshipDepth;
        private final boolean priorityObjectsOnly;
        private volatile boolean cancelled = false;
        private ExecutorService executor;

        public BackupTask(List<SObjectItem> objects, String outputFolder, String displayFolder, 
                         DataSink dataSink, int recordLimit, boolean preserveRelationships,
                         boolean includeRelatedRecords, int relationshipDepth, boolean priorityObjectsOnly) {
            this.objects = objects;
            this.outputFolder = outputFolder;
            this.displayFolder = displayFolder;
            this.dataSink = dataSink;
            this.recordLimit = recordLimit;
            this.preserveRelationships = preserveRelationships;
            this.includeRelatedRecords = includeRelatedRecords;
            this.relationshipDepth = relationshipDepth;
            this.priorityObjectsOnly = priorityObjectsOnly;
        }

        @Override
        protected Void call() throws Exception {
            logMessage("Initializing Bulk API client...");
            
            BulkV2Client bulkClient = new BulkV2Client(
                connectionInfo.getInstanceUrl(),
                connectionInfo.getSessionId(),
                "62.0"
            );
            
            logMessage("Connected to Salesforce: " + connectionInfo.getInstanceUrl());
            
            // Connect to data sink
            if (dataSink != null && !dataSink.getType().equals("CSV")) {
                try {
                    logMessage("Connecting to " + dataSink.getDisplayName() + "...");
                    dataSink.connect();
                    logMessage("Successfully connected to " + dataSink.getDisplayName());
                } catch (Exception e) {
                    logMessage("ERROR: Failed to connect to " + dataSink.getDisplayName() + ": " + e.getMessage());
                    throw new RuntimeException("Failed to connect to backup destination", e);
                }
            }
            
            logMessage("Starting parallel backup with 5 threads...");
            logMessage("");
            
            // Start the log flush scheduler for batched UI updates
            startLogFlushScheduler();
            
            AtomicInteger completed = new AtomicInteger(0);
            AtomicInteger successful = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            AtomicLong totalRecords = new AtomicLong(0);
            int totalObjects = objects.size();
            long startTime = System.currentTimeMillis();
            
            // Use 5 threads to reduce memory pressure and UI event flooding
            executor = Executors.newFixedThreadPool(5);
            
            for (SObjectItem item : objects) {
                if (cancelled) {
                    break;
                }
                
                executor.submit(() -> {
                    if (cancelled) return;
                    
                    String objectName = item.getName();
                    
                    // Only log for large objects
                    if (LARGE_OBJECTS.contains(objectName)) {
                        Runtime runtime = Runtime.getRuntime();
                        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
                        long maxMemory = runtime.maxMemory() / 1024 / 1024;
                        logMessage(String.format("[%s] WARNING: Large object - Memory: %d/%d MB",
                            objectName, usedMemory, maxMemory));
                    }
                    
                    try {
                        Platform.runLater(() -> item.setStatus("Processing..."));
                        
                        // Check if this is a known problematic object
                        if (com.backupforce.bulkv2.BulkV2Client.isProblematicObject(objectName)) {
                            logMessage(String.format("[%s] ⚠ Known Bulk API limitation - may require special filters or fail", objectName));
                        }
                        
                        long objectStart = System.currentTimeMillis();
                        
                        // Check if doing incremental backup
                        String whereClause = null;
                        
                        // For CSV backups, check if incremental mode is enabled
                        if (dataSink == null || dataSink.getType().equals("CSV")) {
                            if (incrementalBackupCheckbox != null && incrementalBackupCheckbox.isSelected()) {
                                if (!supportsLastModifiedDate(objectName)) {
                                    Platform.runLater(() -> item.setStatus("Full backup - no LastModifiedDate"));
                                    logMessage(String.format("[%s] Full backup - object does not support incremental", objectName));
                                } else {
                                    // Get last successful backup from history
                                    Optional<ObjectBackupResult> lastBackup = BackupHistory.getInstance()
                                            .getLastSuccessfulBackup(connectionInfo.getUsername(), objectName);
                                    
                                    if (lastBackup.isPresent() && lastBackup.get().getLastModifiedDate() != null) {
                                        String lastModified = lastBackup.get().getLastModifiedDate();
                                        whereClause = "LastModifiedDate > " + lastModified;
                                        String displayDate = lastModified.length() > 10 ? lastModified.substring(0, 10) : lastModified;
                                        Platform.runLater(() -> item.setStatus("Incremental since " + displayDate));
                                        logMessage(String.format("[%s] Incremental backup - records modified after %s", objectName, displayDate));
                                    } else {
                                        Platform.runLater(() -> item.setStatus("Full backup - first time"));
                                        logMessage(String.format("[%s] Full backup - no previous backup found", objectName));
                                    }
                                }
                            }
                        } else if (dataSink != null && !dataSink.getType().equals("CSV")) {
                            // Check if recreate tables is enabled - if so, always do full backup
                            com.backupforce.sink.JdbcDatabaseSink jdbcSink = (com.backupforce.sink.JdbcDatabaseSink) dataSink;
                            
                            if (jdbcSink.isRecreateTables()) {
                                // Full reload mode - no incremental, will drop and recreate table
                                Platform.runLater(() -> item.setStatus("Full backup - recreate mode"));
                                logMessage(String.format("[%s] Full backup - recreate tables mode enabled", objectName));
                            } else if (!supportsLastModifiedDate(objectName)) {
                                // Object doesn't support LastModifiedDate field (History, __mdt, etc.)
                                Platform.runLater(() -> item.setStatus("Full backup - no LastModifiedDate"));
                                logMessage(String.format("[%s] Full backup - object does not support incremental (no LastModifiedDate)", objectName));
                            } else {
                                // Check if table exists and get last backup timestamp for incremental
                                try {
                                    com.backupforce.sink.dialect.SnowflakeDialect dialect = new com.backupforce.sink.dialect.SnowflakeDialect();
                                    String tableName = dialect.sanitizeTableName(objectName);
                                    java.sql.Timestamp lastBackup = jdbcSink.getLastBackupTimestamp(tableName);
                                    
                                    if (lastBackup != null) {
                                        // Format timestamp for SOQL (ISO 8601)
                                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                                        String formattedDate = sdf.format(lastBackup);
                                        whereClause = "LastModifiedDate > " + formattedDate;
                                        
                                        Platform.runLater(() -> item.setStatus("Delta backup since " + formattedDate.substring(0, 10)));
                                        logMessage(String.format("[%s] Incremental backup - querying records modified after %s", objectName, formattedDate));
                                    } else {
                                        Platform.runLater(() -> item.setStatus("Full backup - first time"));
                                        logMessage(String.format("[%s] Full backup - table exists but empty", objectName));
                                    }
                                } catch (Exception e) {
                                    // Table doesn't exist or error checking - do full backup
                                    Platform.runLater(() -> item.setStatus("Full backup - new table"));
                                    logMessage(String.format("[%s] Full backup - creating new table", objectName));
                                }
                            }
                        }
                        
                        // Add custom WHERE clause if specified
                        if (customWhereCheckbox != null && customWhereCheckbox.isSelected() 
                                && customWhereField != null && customWhereField.getText() != null 
                                && !customWhereField.getText().trim().isEmpty()) {
                            String customWhere = customWhereField.getText().trim();
                            // Remove leading WHERE if user included it
                            if (customWhere.toUpperCase().startsWith("WHERE ")) {
                                customWhere = customWhere.substring(6).trim();
                            }
                            
                            if (whereClause != null) {
                                // Combine incremental WHERE with custom WHERE
                                whereClause = "(" + whereClause + ") AND (" + customWhere + ")";
                                logMessage(String.format("[%s] Combined incremental + custom WHERE: %s", objectName, whereClause));
                            } else {
                                whereClause = customWhere;
                                logMessage(String.format("[%s] Custom WHERE clause: %s", objectName, whereClause));
                            }
                        }
                        
                        // Step 1: Query object using Bulk API (writes CSV file)
                        // Pass selected fields if configured (null = all fields)
                        Set<String> selectedFields = item.getSelectedFields();
                        if (selectedFields != null) {
                            logMessage(String.format("[%s] Using custom field selection: %d fields", 
                                objectName, selectedFields.size()));
                        }
                        
                        // Use throttled callback to prevent UI thread saturation
                        BulkV2Client.ProgressCallback throttledCallback = createThrottledCallback(item);
                        bulkClient.queryObject(objectName, outputFolder, whereClause, recordLimit, selectedFields, throttledCallback);
                        
                        // Step 1.5: Download blobs for objects with blob fields
                        // Download for both CSV and database backups - store files in _blobs folder
                        // For database backups, the blob file path will be stored in BLOB_FILE_PATH column
                        String blobField = getBlobFieldName(objectName);
                        if (blobField != null) {
                            try {
                                Platform.runLater(() -> item.setStatus("Downloading blob files..."));
                                int blobCount = bulkClient.downloadBlobs(objectName, outputFolder, blobField, recordLimit, throttledCallback);
                                if (blobCount > 0) {
                                    logMessage(String.format("[%s] Downloaded %,d blob files", objectName, blobCount));
                                }
                            } catch (Exception blobEx) {
                                logMessage(String.format("[%s] WARNING: Failed to download blobs: %s", 
                                    objectName, blobEx.getMessage()));
                                logger.warn("Failed to download blobs for {}", objectName, blobEx);
                            }
                        }
                        
                        // Step 2: If using database sink, write to database
                        logger.info("DataSink check for {}: dataSink={}, type={}", 
                            objectName, dataSink != null ? dataSink.getDisplayName() : "null",
                            dataSink != null ? dataSink.getType() : "N/A");
                        if (dataSink != null && !dataSink.getType().equals("CSV")) {
                            logMessage(String.format("[%s] Writing to %s...", objectName, dataSink.getDisplayName()));
                            Platform.runLater(() -> item.setStatus("Writing to database..."));
                            
                            File csvFile = new File(outputFolder, objectName + ".csv");
                            if (csvFile.exists()) {
                                try (java.io.FileReader reader = new java.io.FileReader(csvFile)) {
                                    // For now, we skip prepareSink since we don't have field metadata
                                    // The JdbcDatabaseSink will auto-create tables from CSV headers
                                    String backupId = String.valueOf(System.currentTimeMillis());
                                    
                                    // Create throttled callback for database writes (different interface)
                                    final long[] lastDbUpdate = {0};
                                    DataSink.ProgressCallback dbCallback = (status) -> {
                                        long now = System.currentTimeMillis();
                                        if (now - lastDbUpdate[0] >= UI_UPDATE_THROTTLE_MS) {
                                            lastDbUpdate[0] = now;
                                            Platform.runLater(() -> item.setStatus(status));
                                        }
                                    };
                                    int recordsWritten = dataSink.writeData(objectName, reader, backupId, dbCallback);
                                    
                                    // Get CSV record count for comparison (count records properly using CSVParser)
                                    int csvRecords = 0;
                                    try (java.io.FileReader countReader = new java.io.FileReader(csvFile);
                                         org.apache.commons.csv.CSVParser countParser = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(countReader)) {
                                        for (org.apache.commons.csv.CSVRecord ignored : countParser) {
                                            csvRecords++;
                                        }
                                    }
                                    
                                    if (recordsWritten < csvRecords) {
                                        logMessage(String.format("[%s] ⚠ WARNING: CSV has %d records but only %d written to database (missing %d)", 
                                            objectName, csvRecords, recordsWritten, csvRecords - recordsWritten));
                                    } else {
                                        logMessage(String.format("[%s] ✓ Wrote %d records to %s", 
                                            objectName, recordsWritten, dataSink.getDisplayName()));
                                    }
                                } catch (Exception dbEx) {
                                    logMessage(String.format("[%s] WARNING: Failed to write to database: %s", 
                                        objectName, dbEx.getMessage()));
                                    logger.warn("Failed to write {} to database", objectName, dbEx);
                                }
                            }
                        }
                        
                        long objectTime = System.currentTimeMillis() - objectStart;
                        
                        successful.incrementAndGet();
                        
                        // Get file info
                        File csvFile = new File(outputFolder, objectName + ".csv");
                        long fileSize = csvFile.exists() ? csvFile.length() : 0;
                        long recordCount = 0;
                        
                        // Count records from CSV (subtract 1 for header)
                        if (csvFile.exists()) {
                            try {
                                recordCount = Files.lines(Paths.get(csvFile.getPath())).count() - 1;
                            } catch (Exception ex) {
                                // Ignore count errors
                            }
                        }
                        
                        final long finalRecordCount = recordCount;
                        totalRecords.addAndGet(recordCount);
                        final String formattedSize = formatFileSize(fileSize);
                        final String formattedDuration = formatDuration(objectTime);
                        
                        // Record in backup history
                        if (currentBackupRun != null) {
                            ObjectBackupResult objResult = new ObjectBackupResult(objectName);
                            objResult.setStatus("COMPLETED");
                            objResult.setRecordCount(finalRecordCount);
                            objResult.setByteCount(fileSize);
                            objResult.setDurationMs(objectTime);
                            objResult.setLastModifiedDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                            currentBackupRun.getObjectResults().add(objResult);
                        }
                        
                        Platform.runLater(() -> {
                            item.setStatus("✓ Completed");
                            item.setRecordCount(String.format("%,d", finalRecordCount));
                            item.setFileSize(formattedSize);
                            item.setDuration(formattedDuration);
                        });
                        
                        // Only log completion for large objects or slow backups
                        if (LARGE_OBJECTS.contains(objectName) || objectTime > 5000) {
                            logMessage("[" + objectName + "] ✓ Completed in " + objectTime / 1000.0 + "s - " + 
                                      finalRecordCount + " records, " + formattedSize);
                        }
                        
                        // Check memory after large object backup
                        if (LARGE_OBJECTS.contains(objectName)) {
                            Runtime runtime = Runtime.getRuntime();
                            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
                            long maxMemory = runtime.maxMemory() / 1024 / 1024;
                            double percentUsed = (usedMemory * 100.0) / maxMemory;
                            logMessage(String.format("[%s] Memory after backup: %d/%d MB (%.1f%% used)",
                                objectName, usedMemory, maxMemory, percentUsed));
                            
                            if (percentUsed > 80) {
                                logMessage("[WARNING] High memory usage detected! Suggesting garbage collection.");
                                System.gc();
                            }
                        }
                        
                    } catch (OutOfMemoryError oom) {
                        failed.incrementAndGet();
                        String errorText = "OUT OF MEMORY - Try increasing heap size: java -Xmx4g -jar BackupForce.jar";
                        Platform.runLater(() -> {
                            item.setStatus("✗ Out of Memory");
                            item.setErrorMessage(errorText);
                        });
                        logMessage("✗ FAILED: " + objectName + " - OUT OF MEMORY");
                        logMessage("  → Try increasing heap size: java -Xmx4g -jar BackupForce.jar");
                        logger.error("Out of memory backing up " + objectName, oom);
                        
                        // Force garbage collection after OOM to try to recover
                        System.gc();
                        
                    } catch (Exception e) {
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        
                        // Check if this is a connection pool error that we can recover from
                        if (errorMsg.contains("Connection pool shut down") || 
                            errorMsg.contains("Pool closed") ||
                            errorMsg.contains("shut down")) {
                            // Try to reconnect and retry this object
                            logMessage("⚠ Connection pool error on " + objectName + " - reconnecting and retrying...");
                            Platform.runLater(() -> item.setStatus("Reconnecting..."));
                            
                            try {
                                // Force the BulkV2Client to reconnect
                                bulkClient.forceReconnect();
                                
                                // Brief pause after reconnect
                                Thread.sleep(500);
                                
                                // Retry the query
                                BulkV2Client.ProgressCallback retryCallback = createThrottledCallback(item);
                                bulkClient.queryObject(objectName, outputFolder, null, 0, null, retryCallback);
                                
                                successful.incrementAndGet();
                                Platform.runLater(() -> item.setStatus("✓ Completed (retry)"));
                                logMessage("[" + objectName + "] ✓ Completed after reconnect");
                                
                            } catch (Exception retryEx) {
                                // Retry failed - mark as failed but don't stop the whole backup
                                failed.incrementAndGet();
                                String retryError = retryEx.getMessage() != null ? retryEx.getMessage() : retryEx.getClass().getSimpleName();
                                Platform.runLater(() -> {
                                    item.setStatus("✗ Failed (retry)");
                                    item.setErrorMessage("Retry failed: " + retryError);
                                });
                                logMessage("✗ FAILED: " + objectName + " - retry also failed: " + retryError);
                                logger.error("Retry failed for " + objectName, retryEx);
                            }
                        } else if (errorMsg.contains("not supported by the Bulk API") || 
                            errorMsg.contains("INVALIDENTITY") ||
                            errorMsg.contains("Object not supported by Bulk API")) {
                            // Don't count as failed - it's just not supported
                            Platform.runLater(() -> {
                                item.setStatus("⊘ Not Supported");
                                item.setErrorMessage("Object not supported by Bulk API");
                            });
                            logMessage("⊘ SKIPPED: " + objectName + " - Not supported by Bulk API");
                        } else if (errorMsg.contains("Implementation restriction") || 
                                   errorMsg.contains("requires a filter")) {
                            // Object requires specific filter - not a real failure
                            Platform.runLater(() -> {
                                item.setStatus("⊘ Requires Filter");
                                item.setErrorMessage("Object requires specific WHERE filter");
                            });
                            logMessage("⊘ SKIPPED: " + objectName + " - Requires specific filter (use WHERE clause)");
                        } else if (errorMsg.contains("EXCEEDED_ID_LIMIT") || 
                                   errorMsg.contains("does not support queryMore")) {
                            // Object doesn't support pagination
                            Platform.runLater(() -> {
                                item.setStatus("⊘ No Pagination");
                                item.setErrorMessage("Object doesn't support Bulk API pagination");
                            });
                            logMessage("⊘ SKIPPED: " + objectName + " - Doesn't support Bulk API pagination");
                        } else if (errorMsg.contains("EXTERNAL_OBJECT_EXCEPTION") || 
                                   errorMsg.contains("Transient queries")) {
                            // External object not supported
                            Platform.runLater(() -> {
                                item.setStatus("⊘ External Object");
                                item.setErrorMessage("External objects not supported");
                            });
                            logMessage("⊘ SKIPPED: " + objectName + " - External object (not supported)");
                        } else if (errorMsg.contains("Cannot serialize") || 
                                   errorMsg.contains("CSV format")) {
                            // CSV serialization issue
                            Platform.runLater(() -> {
                                item.setStatus("⊘ CSV Error");
                                item.setErrorMessage("Cannot export to CSV format");
                            });
                            logMessage("⊘ SKIPPED: " + objectName + " - Cannot serialize to CSV");
                        } else if (errorMsg.contains("MALFORMED_QUERY") && 
                                   errorMsg.contains("reified column")) {
                            // Metadata object requiring reified filter
                            Platform.runLater(() -> {
                                item.setStatus("⊘ Metadata Object");
                                item.setErrorMessage("Metadata object requires special filter");
                            });
                            logMessage("⊘ SKIPPED: " + objectName + " - Metadata object (requires special filter)");
                        } else {
                            // Actual failure
                            failed.incrementAndGet();
                            
                            // Extract meaningful error message
                            String cleanError = errorMsg;
                            if (errorMsg.contains("Failed to create query job:")) {
                                cleanError = errorMsg.substring(errorMsg.indexOf(":") + 1).trim();
                            }
                            
                            // Record failed object in backup history
                            if (currentBackupRun != null) {
                                ObjectBackupResult objResult = new ObjectBackupResult(objectName);
                                objResult.setStatus("FAILED");
                                objResult.setErrorMessage(cleanError);
                                currentBackupRun.getObjectResults().add(objResult);
                            }
                            
                            final String finalError = cleanError;
                            Platform.runLater(() -> {
                                item.setStatus("✗ Failed");
                                item.setErrorMessage(finalError);
                            });
                            logMessage("✗ FAILED: " + objectName + " - " + cleanError);
                        }
                        logger.error("Backup failed for " + objectName, e);
                        
                    } finally {
                        int completedCount = completed.incrementAndGet();
                        double progress = (double) completedCount / totalObjects;
                        
                        Platform.runLater(() -> {
                            progressBar.setProgress(progress);
                            progressLabel.setText(String.format("Progress: %d/%d (%d successful, %d failed)",
                                completedCount, totalObjects, successful.get(), failed.get()));
                            // Update the percentage label
                            if (progressPercentLabel != null) {
                                progressPercentLabel.setText(String.format("%.0f%%", progress * 100));
                            }
                        });
                        
                        // Status bar shows progress, only log milestones
                        if (completedCount % 100 == 0 || completedCount == totalObjects) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            logMessage(String.format("Progress: %d/%d (%.1f%%) - %d successful, %d failed",
                                completedCount, totalObjects, progress * 100, successful.get(), failed.get()));
                        }
                    }
                });
            }
            
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
            
            // ==================== RELATIONSHIP-AWARE BACKUP ====================
            // After backing up parent objects, fetch related child records
            if (includeRelatedRecords && recordLimit > 0 && !cancelled) {
                processRelatedRecordsBackup(bulkClient, outputFolder, totalRecords, successful, failed);
            }
            
            bulkClient.close();
            
            // Disconnect from data sink
            if (dataSink != null && !dataSink.getType().equals("CSV")) {
                try {
                    logMessage("Disconnecting from " + dataSink.getDisplayName() + "...");
                    dataSink.disconnect();
                } catch (Exception e) {
                    logger.warn("Error disconnecting from data sink", e);
                }
            }
            
            // Compress CSV files to ZIP if enabled (only for CSV backups)
            String finalOutputInfo = displayFolder;
            if (compressBackupCheckbox != null && compressBackupCheckbox.isSelected() && 
                (dataSink == null || dataSink.getType().equals("CSV"))) {
                try {
                    Platform.runLater(() -> logMessage("Compressing backup to ZIP..."));
                    String zipPath = compressCsvFilesToZip(outputFolder);
                    if (zipPath != null) {
                        finalOutputInfo = zipPath;
                        Platform.runLater(() -> logMessage("✓ Compressed to: " + zipPath));
                    }
                } catch (Exception e) {
                    logger.warn("Failed to compress backup", e);
                    Platform.runLater(() -> logMessage("WARNING: Compression failed: " + e.getMessage()));
                }
            }
            
            // Generate relationship metadata for restore (only for CSV backups)
            if (preserveRelationships && (dataSink == null || dataSink.getType().equals("CSV"))) {
                try {
                    Platform.runLater(() -> logMessage("Generating relationship metadata for restore..."));
                    generateRelationshipMetadata(outputFolder, objects);
                    Platform.runLater(() -> logMessage("✓ Relationship metadata saved"));
                } catch (Exception e) {
                    logger.warn("Failed to generate relationship metadata", e);
                    Platform.runLater(() -> logMessage("WARNING: Relationship metadata generation failed: " + e.getMessage()));
                }
            }
            
            final String displayOutput = finalOutputInfo;
            
            long totalTime = System.currentTimeMillis() - startTime;
            
            final long finalTotalRecords = totalRecords.get();
            
            Platform.runLater(() -> {
                logMessage("=".repeat(60));
                logMessage("Backup completed!");
                logMessage("Total objects: " + totalObjects);
                logMessage("Successful: " + successful.get());
                logMessage("Failed: " + failed.get());
                logMessage("Total records: " + String.format("%,d", finalTotalRecords));
                logMessage("Total time: " + totalTime / 1000 + " seconds");
                logMessage("Output: " + displayOutput);
                logMessage("=".repeat(60));
                
                // Save backup stats for home page display
                if (!cancelled) {
                    saveBackupStats(successful.get(), finalTotalRecords, displayOutput);
                    
                    // Complete backup history record
                    if (currentBackupRun != null) {
                        boolean success = failed.get() == 0;
                        BackupHistory.getInstance().completeBackup(currentBackupRun, success);
                        currentBackupRun = null;
                    }
                } else {
                    // Cancel backup history record
                    if (currentBackupRun != null) {
                        BackupHistory.getInstance().cancelBackup(currentBackupRun);
                        currentBackupRun = null;
                    }
                }
                
                // Reset UI for next backup
                resetUIForNewBackup();
                
                if (!cancelled) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Backup Complete");
                    alert.setHeaderText("Backup Finished Successfully");
                    alert.setContentText(String.format(
                        "Backed up %d objects (%,d records) in %d seconds\n\nOutput: %s",
                        successful.get(), finalTotalRecords, totalTime / 1000, displayOutput
                    ));
                    alert.showAndWait();
                }
            });
            
            // Stop the log flush scheduler
            stopLogFlushScheduler();
            
            return null;
        }
        
        /**
         * Process relationship-aware backup: after backing up parent objects,
         * automatically fetch and backup related child records.
         */
        private void processRelatedRecordsBackup(BulkV2Client bulkClient, String outputFolder,
                                                 AtomicLong totalRecords, AtomicInteger successful, 
                                                 AtomicInteger failed) {
            try {
                Platform.runLater(() -> {
                    logMessage("");
                    logMessage("=".repeat(60));
                    logMessage("RELATIONSHIP-AWARE BACKUP: Fetching related child records...");
                    logMessage("=".repeat(60));
                });
                
                RelationshipAwareBackupService relService = new RelationshipAwareBackupService(
                    connectionInfo.getInstanceUrl(),
                    connectionInfo.getSessionId(),
                    "62.0",
                    relationshipDepth,
                    priorityObjectsOnly
                );
                
                // Check if we have user-selected configuration from the preview dialog
                boolean useUserConfig = relationshipBackupConfig != null && 
                    relationshipBackupConfig.getSelectedObjects() != null &&
                    !relationshipBackupConfig.getSelectedObjects().isEmpty();
                
                if (useUserConfig) {
                    Platform.runLater(() -> logMessage("Using user-selected related objects from preview (" + 
                        relationshipBackupConfig.getSelectedObjects().size() + " objects)"));
                }
                
                // Track objects we've already processed to avoid duplicates
                Set<String> processedObjects = new HashSet<>();
                for (SObjectItem item : objects) {
                    processedObjects.add(item.getName());
                }
                
                // Process each backed-up parent object
                List<RelatedBackupTask> pendingTasks = new ArrayList<>();
                
                for (SObjectItem parentItem : objects) {
                    if (cancelled) break;
                    
                    String parentObject = parentItem.getName();
                    
                    // Check if this object actually has records in the backup
                    java.nio.file.Path csvPath = java.nio.file.Paths.get(outputFolder, parentObject + ".csv");
                    if (!java.nio.file.Files.exists(csvPath)) {
                        continue;
                    }
                    
                    // Extract parent IDs from the CSV for WHERE clause building
                    Set<String> parentIds = relService.extractIdsFromBackup(parentObject, outputFolder);
                    if (parentIds.isEmpty()) {
                        Platform.runLater(() -> logMessage("No records found in " + parentObject + " backup"));
                        continue;
                    }
                    
                    Platform.runLater(() -> logMessage("Analyzing relationships for: " + parentObject));
                    
                    if (useUserConfig) {
                        // Use user-selected objects directly - build tasks from selection
                        logger.info("Processing parent object: {} with {} parent IDs", parentObject, parentIds.size());
                        
                        // Group selected objects by child object name to consolidate multiple lookup fields
                        // e.g., Contact via AccountId AND Contact via Secondary_Account__c -> single query with OR
                        Map<String, List<String>> childToParentFields = new LinkedHashMap<>();
                        Map<String, Integer> childToDepth = new HashMap<>();
                        
                        for (RelationshipPreviewController.SelectedRelatedObject selected : 
                                relationshipBackupConfig.getSelectedObjects()) {
                            
                            // Only process children of this parent object
                            if (!selected.getParentObject().equals(parentObject)) {
                                continue;
                            }
                            
                            // Group by child object name
                            String childName = selected.getObjectName();
                            childToParentFields
                                .computeIfAbsent(childName, k -> new ArrayList<>())
                                .add(selected.getParentField());
                            childToDepth.put(childName, selected.getDepth());
                        }
                        
                        // Now create ONE task per child object with combined WHERE clause
                        for (Map.Entry<String, List<String>> entry : childToParentFields.entrySet()) {
                            String childName = entry.getKey();
                            List<String> parentFields = entry.getValue();
                            
                            // Skip if already processed
                            if (processedObjects.contains(childName)) {
                                logger.info("Skipping {} - already processed", childName);
                                continue;
                            }
                            
                            // Build WHERE clause with all parent fields OR'd together
                            String whereClause = relService.buildWhereClauseMultiField(parentFields, parentIds);
                            logger.info("Building task for {} with {} fields {} and WHERE: {}", 
                                childName, parentFields.size(), parentFields,
                                whereClause != null ? whereClause.substring(0, Math.min(200, whereClause.length())) : "null");
                            
                            RelatedBackupTask task = new RelatedBackupTask(
                                childName,
                                String.join(",", parentFields), // Store all fields for reference
                                parentObject,
                                whereClause,
                                childToDepth.get(childName)
                            );
                            
                            pendingTasks.add(task);
                        }
                    } else {
                        // Auto-discover relationships
                        List<RelatedBackupTask> childTasks = relService.onParentBackupComplete(parentObject, outputFolder);
                        
                        for (RelatedBackupTask task : childTasks) {
                            if (!processedObjects.contains(task.getObjectName())) {
                                pendingTasks.add(task);
                            }
                        }
                    }
                }
                
                if (pendingTasks.isEmpty()) {
                    Platform.runLater(() -> logMessage("No related child objects found to backup"));
                    relService.close();
                    return;
                }
                
                Platform.runLater(() -> logMessage("Found " + pendingTasks.size() + " related objects to backup"));
                
                // Process each related backup task
                int relatedCount = 0;
                for (RelatedBackupTask task : pendingTasks) {
                    if (cancelled) break;
                    
                    // Skip duplicates
                    if (processedObjects.contains(task.getObjectName())) {
                        continue;
                    }
                    processedObjects.add(task.getObjectName());
                    
                    String childObject = task.getObjectName();
                    String whereClause = task.getWhereClause();
                    
                    final String logWhereClause = whereClause != null && whereClause.length() > 100 
                        ? whereClause.substring(0, 100) + "..." : whereClause;
                    Platform.runLater(() -> {
                        logMessage(String.format("  ↳ Backing up %s (related to %s via %s)...",
                            childObject, task.getParentObject(), task.getParentField()));
                        logMessage(String.format("    WHERE: %s", logWhereClause));
                    });
                    
                    try {
                        // Create a display item for the child object
                        SObjectItem childItem = new SObjectItem(childObject, childObject + " (related)");
                        Platform.runLater(() -> {
                            allObjects.add(childItem);
                            childItem.setStatus("⟳ Backing up...");
                        });
                        
                        long itemStart = System.currentTimeMillis();
                        
                        // Query the child object with WHERE clause to filter by parent IDs
                        // Use throttled callback
                        BulkV2Client.ProgressCallback childCallback = createThrottledCallback(childItem);
                        bulkClient.queryObject(childObject, outputFolder, whereClause, 0, null, childCallback);
                        
                        // Read record count from result file
                        java.nio.file.Path resultPath = java.nio.file.Paths.get(outputFolder, childObject + ".csv");
                        if (java.nio.file.Files.exists(resultPath)) {
                            long lineCount = java.nio.file.Files.lines(resultPath).count() - 1; // Subtract header
                            long fileSize = java.nio.file.Files.size(resultPath);
                            long duration = System.currentTimeMillis() - itemStart;
                            
                            totalRecords.addAndGet(lineCount);
                            relatedCount++;
                            successful.incrementAndGet();
                            
                            final long fLineCount = lineCount;
                            Platform.runLater(() -> {
                                childItem.setStatus("✓ Completed");
                                childItem.setRecordCount(String.format("%,d", fLineCount));
                                childItem.setFileSize(formatFileSize(fileSize));
                                childItem.setDuration(formatDuration(duration));
                                logMessage(String.format("    ✓ %s: %,d records", childObject, fLineCount));
                            });
                        }
                        
                        task.setCompleted(true);
                        
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        Platform.runLater(() -> logMessage("    ✗ " + childObject + ": " + e.getMessage()));
                        logger.warn("Failed to backup related object: " + childObject, e);
                    }
                }
                
                final int fRelatedCount = relatedCount;
                Platform.runLater(() -> {
                    logMessage("");
                    logMessage("Relationship-aware backup completed: " + fRelatedCount + " related objects backed up");
                });
                
                // Generate comprehensive manifest for restore/migration
                generateComprehensiveManifest(outputFolder, objects, pendingTasks);
                
                relService.close();
                
            } catch (Exception e) {
                logger.error("Error in relationship-aware backup", e);
                Platform.runLater(() -> logMessage("WARNING: Relationship backup failed: " + e.getMessage()));
            }
        }
        
        /**
         * Generate a comprehensive manifest file for restore/migration support.
         * This manifest contains everything needed to restore data to a different org.
         */
        private void generateComprehensiveManifest(String outputFolder, List<SObjectItem> parents,
                                                   List<RelatedBackupTask> relatedTasks) {
            try {
                BackupManifestGenerator manifestGen = new BackupManifestGenerator(
                    connectionInfo.getInstanceUrl(),
                    connectionInfo.getSessionId(),
                    "62.0"
                );
                
                // Determine options from config or defaults
                boolean captureExternalIds = true;
                boolean captureFieldMetadata = true; // Always capture field metadata
                boolean generateIdMappingOpt = true;
                boolean includeRecordTypes = true;
                
                if (relationshipBackupConfig != null) {
                    captureExternalIds = relationshipBackupConfig.isCaptureExternalIds();
                    generateIdMappingOpt = relationshipBackupConfig.isGenerateIdMapping();
                    includeRecordTypes = relationshipBackupConfig.isIncludeRecordTypes();
                }
                
                // Configure the manifest generator
                manifestGen.setCaptureExternalIds(captureExternalIds);
                manifestGen.setCaptureFieldMetadata(captureFieldMetadata);
                manifestGen.setGenerateIdMapping(generateIdMappingOpt);
                manifestGen.setIncludeRecordTypes(includeRecordTypes);
                
                // Build list of objects to include in manifest
                List<String> allBackedUpObjects = new ArrayList<>();
                List<String> parentObjectNames = new ArrayList<>();
                for (SObjectItem parent : parents) {
                    allBackedUpObjects.add(parent.getName());
                    parentObjectNames.add(parent.getName());
                }
                
                // Related objects with their parent info
                List<RelatedObjectInfo> relatedObjectInfos = new ArrayList<>();
                for (RelatedBackupTask task : relatedTasks) {
                    if (task.isCompleted()) {
                        allBackedUpObjects.add(task.getObjectName());
                        relatedObjectInfos.add(new RelatedObjectInfo(
                            task.getObjectName(),
                            task.getParentObject(), 
                            task.getParentField(),
                            task.getDepth(),
                            false // priority is not tracked in RelatedBackupTask
                        ));
                    }
                }
                
                Platform.runLater(() -> logMessage("Generating comprehensive backup manifest..."));
                
                // Generate the main manifest
                manifestGen.generateManifest(
                    outputFolder,
                    allBackedUpObjects,
                    parentObjectNames,
                    relatedObjectInfos
                );
                
                manifestGen.close();
                
                Platform.runLater(() -> logMessage("✓ Comprehensive backup manifest saved"));
                
            } catch (Exception e) {
                logger.warn("Failed to generate comprehensive manifest", e);
                // Fall back to simple manifest
                generateRelationshipManifest(outputFolder, parents, relatedTasks);
            }
        }
        
        /**
         * Generate a simple manifest file documenting the relationship-aware backup.
         * (Fallback if comprehensive manifest fails)
         */
        private void generateRelationshipManifest(String outputFolder, List<SObjectItem> parents,
                                                  List<RelatedBackupTask> relatedTasks) {
            try {
                java.nio.file.Path manifestPath = java.nio.file.Paths.get(outputFolder, "_relationship_manifest.json");
                
                com.google.gson.JsonObject manifest = new com.google.gson.JsonObject();
                manifest.addProperty("generatedAt", java.time.Instant.now().toString());
                manifest.addProperty("backupType", "relationship-aware");
                manifest.addProperty("relationshipDepth", relationshipDepth);
                manifest.addProperty("priorityObjectsOnly", priorityObjectsOnly);
                
                // Parent objects
                com.google.gson.JsonArray parentsArray = new com.google.gson.JsonArray();
                for (SObjectItem parent : parents) {
                    parentsArray.add(parent.getName());
                }
                manifest.add("parentObjects", parentsArray);
                
                // Related objects with their parent info
                com.google.gson.JsonArray relatedArray = new com.google.gson.JsonArray();
                for (RelatedBackupTask task : relatedTasks) {
                    if (task.isCompleted()) {
                        com.google.gson.JsonObject relObj = new com.google.gson.JsonObject();
                        relObj.addProperty("object", task.getObjectName());
                        relObj.addProperty("parentObject", task.getParentObject());
                        relObj.addProperty("parentField", task.getParentField());
                        relObj.addProperty("depth", task.getDepth());
                        relatedArray.add(relObj);
                    }
                }
                manifest.add("relatedObjects", relatedArray);
                
                // Restore instructions
                com.google.gson.JsonObject restoreInfo = new com.google.gson.JsonObject();
                restoreInfo.addProperty("restoreOrder", "Parent objects first, then related objects");
                restoreInfo.addProperty("note", "Related objects were filtered to only include records linked to backed-up parents");
                manifest.add("restoreInstructions", restoreInfo);
                
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                java.nio.file.Files.writeString(manifestPath, gson.toJson(manifest));
                
                Platform.runLater(() -> logMessage("✓ Relationship manifest saved"));
                
            } catch (Exception e) {
                logger.warn("Failed to generate relationship manifest", e);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            
            // Force shutdown the executor
            if (executor != null && !executor.isShutdown()) {
                logMessage("Cancelling backup - shutting down tasks...");
                executor.shutdownNow();
            }
            
            // Re-enable controls immediately
            Platform.runLater(() -> {
                resetUIForNewBackup();
                progressLabel.setText("Backup cancelled");
                logMessage("Backup cancelled by user");
            });
            
            return super.cancel(mayInterruptIfRunning);
        }
    }
    
    private void resetUIForNewBackup() {
        startBackupButton.setDisable(false);
        stopBackupButton.setDisable(true);
        allObjectsTable.setDisable(false);
        browseButton.setDisable(false);
        selectAllButton.setDisable(false);
        deselectAllButton.setDisable(false);
        csvRadioButton.setDisable(false);
        databaseRadioButton.setDisable(false);
        databaseSettingsButton.setDisable(csvRadioButton.isSelected());
        outputFolderBox.setDisable(databaseRadioButton.isSelected());
        progressBar.setProgress(0);
        progressLabel.setText("Ready to start backup");
        
        // Switch back to "All Objects" tab
        statusTabPane.getSelectionModel().select(0);
    }
    
    private void saveBackupStats(int objectCount, long rowCount, String destination) {
        try {
            Preferences prefs = Preferences.userNodeForPackage(BackupController.class);
            
            // Format the timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
            
            // Save current backup as last backup
            prefs.put("lastDataBackupDate", timestamp);
            prefs.putInt("lastBackupObjectCount", objectCount);
            prefs.putLong("lastBackupRowCount", rowCount);
            prefs.put("lastBackupDestination", destination);
            
            // Update totals
            int totalBackups = prefs.getInt("totalBackups", 0);
            prefs.putInt("totalBackups", totalBackups + 1);
            prefs.put("lastBackupDate", timestamp);
            
            logger.info("Saved backup stats: {} objects, {} rows to {}", objectCount, rowCount, destination);
        } catch (Exception e) {
            logger.warn("Failed to save backup stats", e);
        }
    }
    
    /**
     * Compress all CSV files in the output folder to a single ZIP file
     * @param outputFolder The folder containing CSV files
     * @return Path to the created ZIP file, or null if no files were compressed
     */
    private String compressCsvFilesToZip(String outputFolder) throws IOException {
        File folder = new File(outputFolder);
        if (!folder.exists() || !folder.isDirectory()) {
            return null;
        }
        
        File[] csvFiles = folder.listFiles((dir, name) -> name.endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            return null;
        }
        
        // Create ZIP file name with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String zipFileName = "backup_" + timestamp + ".zip";
        File zipFile = new File(folder, zipFileName);
        
        long totalUncompressed = 0;
        long totalCompressed = 0;
        
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.BufferedOutputStream(new java.io.FileOutputStream(zipFile)))) {
            
            // Set compression level
            zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);
            
            byte[] buffer = new byte[8192];
            
            for (File csvFile : csvFiles) {
                totalUncompressed += csvFile.length();
                
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(csvFile.getName());
                zos.putNextEntry(entry);
                
                try (java.io.FileInputStream fis = new java.io.FileInputStream(csvFile)) {
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }
                
                zos.closeEntry();
            }
        }
        
        totalCompressed = zipFile.length();
        
        // Delete original CSV files after successful compression
        for (File csvFile : csvFiles) {
            if (!csvFile.delete()) {
                logger.warn("Failed to delete CSV file after compression: {}", csvFile.getName());
            }
        }
        
        // Log compression ratio
        double ratio = totalUncompressed > 0 ? (1.0 - (double) totalCompressed / totalUncompressed) * 100 : 0;
        logger.info("Compressed {} files: {} MB -> {} MB (saved {:.1f}%)", 
            csvFiles.length, 
            totalUncompressed / (1024 * 1024),
            totalCompressed / (1024 * 1024),
            ratio);
        
        return zipFile.getAbsolutePath();
    }
    
    /**
     * Generates relationship metadata file for data restoration.
     * This captures external IDs and relationship mappings so data can be
     * restored without relying on Salesforce IDs.
     */
    private void generateRelationshipMetadata(String outputFolder, List<SObjectItem> objects) {
        try {
            com.backupforce.restore.RelationshipManager relManager = new com.backupforce.restore.RelationshipManager(
                connectionInfo.getInstanceUrl(),
                connectionInfo.getSessionId(),
                "62.0"
            );
            
            // Get object names that were backed up
            List<String> objectNames = objects.stream()
                .map(SObjectItem::getName)
                .collect(java.util.stream.Collectors.toList());
            
            // Generate and save relationship config
            com.backupforce.restore.RelationshipManager.BackupRelationshipConfig config = 
                relManager.generateBackupConfig(objectNames);
            
            // Add backup metadata
            config.setBackupDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            config.setSourceOrg(connectionInfo.getUsername() + " @ " + connectionInfo.getInstanceUrl());
            
            relManager.saveRelationshipMetadata(outputFolder, config);
            
            // Log summary
            int objectsWithRelationships = 0;
            int totalRelationships = 0;
            for (com.backupforce.restore.RelationshipManager.ObjectBackupConfig objConfig : config.getObjects().values()) {
                if (!objConfig.getRelationshipMappings().isEmpty()) {
                    objectsWithRelationships++;
                    totalRelationships += objConfig.getRelationshipMappings().size();
                }
            }
            
            logger.info("Relationship metadata: {} objects with {} total relationship fields", 
                objectsWithRelationships, totalRelationships);
            
            relManager.close();
            
        } catch (Exception e) {
            logger.error("Failed to generate relationship metadata", e);
            throw new RuntimeException("Failed to generate relationship metadata: " + e.getMessage(), e);
        }
    }

    // Utility methods
    private static String getBlobFieldName(String objectName) {
        // Map of objects that have blob fields and their blob field names
        switch (objectName) {
            case "Document":
            case "Attachment":
            case "StaticResource":
            case "ApexClass":  // ApexClass Body is text but sometimes treated as blob
            case "ApexTrigger":
            case "ApexPage":
            case "ApexComponent":
                return "Body";
            case "ContentVersion":
                return "VersionData";
            case "ContentNote":
                return "Content";
            case "Folder":
                return null; // Folders don't have blobs
            case "ContentDocument":
                return null; // ContentDocument doesn't have blob - use ContentVersion instead
            case "EmailMessage":
                return null; // Has TextBody and HtmlBody but not binary blobs
            case "EmailTemplate":
                return null; // Has Body but it's text, not binary
            case "FeedItem":
                return null; // FeedItem content is in ContentVersion via FeedAttachment
            case "EventLogFile":
                return "LogFile"; // Event Monitoring logs
            case "MobileApplicationDetail":
                return "ApplicationBinary";
            default:
                return null;
        }
    }
    
    /**
     * Determines whether an object should download blobs even if not explicitly listed.
     * Some custom objects may have blob fields that we discover dynamically.
     */
    private static boolean shouldDownloadBlobs(String objectName) {
        // Always download blobs for known blob objects
        if (getBlobFieldName(objectName) != null) {
            return true;
        }
        // Could add dynamic detection here in the future
        return false;
    }
    
    /**
     * Determines if an object supports the LastModifiedDate field for incremental backups.
     * 
     * Objects that do NOT support LastModifiedDate include:
     * - History objects (e.g., AccountHistory, OpportunityHistory, *__History)
     * - Custom Metadata Types (__mdt)
     * - Some system objects
     * 
     * @param objectName The Salesforce object API name
     * @return true if the object supports LastModifiedDate, false otherwise
     */
    private static boolean supportsLastModifiedDate(String objectName) {
        // History objects don't have LastModifiedDate - they use CreatedDate instead
        if (objectName.endsWith("History") || objectName.endsWith("__History")) {
            return false;
        }
        
        // Custom Metadata Types (__mdt) don't support LastModifiedDate in SOQL queries via Bulk API
        if (objectName.endsWith("__mdt")) {
            return false;
        }
        
        // Share objects often don't have LastModifiedDate
        if (objectName.endsWith("Share") || objectName.endsWith("__Share")) {
            return false;
        }
        
        // Feed objects may not support incremental well
        if (objectName.endsWith("Feed")) {
            return false;
        }
        
        // ChangeEvent objects for Change Data Capture don't support LastModifiedDate
        if (objectName.endsWith("ChangeEvent") || objectName.endsWith("__ChangeEvent")) {
            return false;
        }
        
        // By default, assume object supports LastModifiedDate
        return true;
    }
    
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    private static String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return String.format("%dm %ds", minutes, seconds);
        long hours = minutes / 60;
        minutes = minutes % 60;
        return String.format("%dh %dm", hours, minutes);
    }

    // Model class for SObject items
    public static class SObjectItem {
        private final SimpleStringProperty name;
        private final SimpleStringProperty label;
        private final SimpleStringProperty status;
        private final SimpleStringProperty recordCount;
        private final SimpleStringProperty fileSize;
        private final SimpleStringProperty duration;
        private final SimpleStringProperty errorMessage;
        private boolean selected;
        private boolean disabled;
        private Set<String> selectedFields; // null = all fields
        private int totalFieldCount = 0;

        public SObjectItem(String name, String label) {
            this.name = new SimpleStringProperty(name);
            this.label = new SimpleStringProperty(label);
            this.status = new SimpleStringProperty("");
            this.recordCount = new SimpleStringProperty("");
            this.fileSize = new SimpleStringProperty("");
            this.duration = new SimpleStringProperty("");
            this.errorMessage = new SimpleStringProperty("");
            this.selected = false;
            this.disabled = false;
            this.selectedFields = null; // null means all fields
        }

        public String getName() { return name.get(); }
        public SimpleStringProperty nameProperty() { return name; }
        
        public String getLabel() { return label.get(); }
        public SimpleStringProperty labelProperty() { return label; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public SimpleStringProperty statusProperty() { return status; }
        
        public String getRecordCount() { return recordCount.get(); }
        public void setRecordCount(String value) { recordCount.set(value); }
        public SimpleStringProperty recordCountProperty() { return recordCount; }
        
        public String getFileSize() { return fileSize.get(); }
        public void setFileSize(String value) { fileSize.set(value); }
        public SimpleStringProperty fileSizeProperty() { return fileSize; }
        
        public String getDuration() { return duration.get(); }
        public void setDuration(String value) { duration.set(value); }
        public SimpleStringProperty durationProperty() { return duration; }
        
        public String getErrorMessage() { return errorMessage.get(); }
        public void setErrorMessage(String value) { errorMessage.set(value); }
        public SimpleStringProperty errorMessageProperty() { return errorMessage; }
        
        public boolean isSelected() { return selected; }
        public void setSelected(boolean value) { 
            if (!disabled) {
                this.selected = value;
            }
        }
        public javafx.beans.property.BooleanProperty selectedProperty() {
            return new javafx.beans.property.SimpleBooleanProperty(selected) {
                @Override
                public void set(boolean newValue) {
                    super.set(newValue);
                    if (!disabled) {
                        selected = newValue;
                    }
                }
            };
        }
        
        public boolean isDisabled() { return disabled; }
        public void setDisabled(boolean value) { this.disabled = value; }
        
        /**
         * Get selected fields for this object. 
         * @return Set of field names, or null if all fields should be backed up
         */
        public Set<String> getSelectedFields() { return selectedFields; }
        
        /**
         * Set which fields to backup for this object.
         * @param fields Set of field names, or null to backup all fields
         */
        public void setSelectedFields(Set<String> fields) { this.selectedFields = fields; }
        
        /**
         * Check if custom field selection has been configured
         */
        public boolean hasCustomFieldSelection() { return selectedFields != null; }
        
        public int getTotalFieldCount() { return totalFieldCount; }
        public void setTotalFieldCount(int count) { this.totalFieldCount = count; }
        
        /**
         * Get a display string showing field selection status
         */
        public String getFieldSelectionInfo() {
            if (selectedFields == null) {
                return "All fields";
            }
            return selectedFields.size() + " of " + totalFieldCount + " fields";
        }
    }
    
    // Old navigation methods removed - navigation now handled by MainController sidebar
}
