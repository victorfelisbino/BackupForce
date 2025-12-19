package com.backupforce.ui;

import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.restore.DatabaseScanner;
import com.backupforce.restore.DatabaseScanner.BackupTable;
import com.backupforce.restore.DependencyOrderer;
import com.backupforce.restore.RelationshipManager;
import com.backupforce.restore.RestoreExecutor;
import com.backupforce.restore.RestoreExecutor.RestoreOptions;
import com.backupforce.restore.RestoreExecutor.RestoreResult;
import com.backupforce.restore.RestoreExecutor.RestoreProgress;
import com.backupforce.restore.TransformationConfig;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
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
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Controller for the Data Restoration screen.
 * Allows users to restore backup data from folders or database connections back to Salesforce.
 */
public class RestoreController {
    
    private static final Logger logger = LoggerFactory.getLogger(RestoreController.class);
    
    // Source selection
    @FXML private RadioButton folderSourceRadio;
    @FXML private RadioButton databaseSourceRadio;
    @FXML private ToggleGroup sourceToggle;
    @FXML private VBox folderSelectionPane;
    @FXML private VBox databaseSelectionPane;
    @FXML private TextField restoreSourceField;
    @FXML private Button browseRestoreButton;
    @FXML private ComboBox<SavedConnection> databaseConnectionCombo;
    @FXML private Button scanSourceButton;
    
    // Objects table
    @FXML private TextField objectSearchField;
    @FXML private TableView<RestoreObject> objectsTable;
    @FXML private TableColumn<RestoreObject, Boolean> selectColumn;
    @FXML private TableColumn<RestoreObject, String> objectNameColumn;
    @FXML private TableColumn<RestoreObject, Number> recordCountColumn;
    @FXML private TableColumn<RestoreObject, String> lastBackupColumn;
    @FXML private Label objectCountLabel;
    
    // Restore options
    @FXML private RadioButton insertOnlyRadio;
    @FXML private RadioButton upsertRadio;
    @FXML private RadioButton updateOnlyRadio;
    @FXML private ToggleGroup restoreModeToggle;
    @FXML private ComboBox<String> externalIdFieldCombo;
    @FXML private CheckBox validateBeforeRestoreCheck;
    @FXML private CheckBox stopOnErrorCheck;
    @FXML private CheckBox preserveIdsCheck;
    @FXML private CheckBox dryRunCheck;
    @FXML private ComboBox<Integer> batchSizeCombo;
    
    // Transformation
    @FXML private Label transformStatusLabel;
    @FXML private Button configureTransformButton;
    @FXML private Button loadTransformButton;
    
    // Target org
    @FXML private Label connectionLabel;
    @FXML private Label targetOrgLabel;
    
    // Actions
    // Removed: backButton - only used by old restore.fxml
    @FXML private Button startRestoreButton;
    @FXML private Button stopRestoreButton;
    
    // Progress
    @FXML private ProgressBar progressBar;
    @FXML private Label progressPercentLabel;
    @FXML private Label currentObjectLabel;
    @FXML private Label recordsProcessedLabel;
    @FXML private TextArea logArea;
    
    private LoginController.ConnectionInfo connectionInfo;
    private ObservableList<RestoreObject> restoreObjects = FXCollections.observableArrayList();
    private FilteredList<RestoreObject> filteredObjects;
    private volatile boolean restoreRunning = false;
    private RestoreExecutor restoreExecutor;
    private ExecutorService executorService;
    private TransformationConfig transformationConfig;
    
    @FXML
    public void initialize() {
        logger.info("Initializing RestoreController");
        
        // Setup source toggle listener
        sourceToggle.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isFolder = folderSourceRadio.isSelected();
            folderSelectionPane.setVisible(isFolder);
            folderSelectionPane.setManaged(isFolder);
            databaseSelectionPane.setVisible(!isFolder);
            databaseSelectionPane.setManaged(!isFolder);
        });
        
        // Setup objects table
        setupObjectsTable();
        
        // Setup search filter
        filteredObjects = new FilteredList<>(restoreObjects, p -> true);
        objectsTable.setItems(filteredObjects);
        
        objectSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal.toLowerCase().trim();
            filteredObjects.setPredicate(obj -> 
                filter.isEmpty() || obj.getName().toLowerCase().contains(filter)
            );
            updateObjectCount();
        });
        
        // Load database connections
        loadDatabaseConnections();
        
        // Setup batch size options
        batchSizeCombo.setItems(FXCollections.observableArrayList(50, 100, 200, 500, 1000, 2000));
        batchSizeCombo.setValue(200);
        
        // Setup external ID field combo - enable only when upsert mode is selected
        restoreModeToggle.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isUpsert = upsertRadio.isSelected();
            externalIdFieldCombo.setDisable(!isUpsert);
            if (!isUpsert) {
                externalIdFieldCombo.getSelectionModel().clearSelection();
            }
        });
        
        // Populate external ID fields when object is selected
        objectsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && connectionInfo != null) {
                loadExternalIdFieldsForObject(newVal.getName());
            }
        });
        
        logMessage("Data Restoration screen ready");
    }
    
    private void setupObjectsTable() {
        // Checkbox column
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectColumn.setCellFactory(col -> new CheckBoxTableCell<>());
        
        // Object name column
        objectNameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        
        // Record count column
        recordCountColumn.setCellValueFactory(cellData -> cellData.getValue().recordCountProperty());
        
        // Last backup column
        lastBackupColumn.setCellValueFactory(cellData -> cellData.getValue().lastBackupProperty());
        
        objectsTable.setEditable(true);
    }
    
    /**
     * Loads external ID fields for the selected object from Salesforce metadata.
     * External ID fields can be used for upsert operations to match existing records.
     */
    private void loadExternalIdFieldsForObject(String objectName) {
        externalIdFieldCombo.getItems().clear();
        externalIdFieldCombo.setPromptText("Loading...");
        
        Task<List<String>> loadTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> externalIdFields = new ArrayList<>();
                
                // Always include Id as an option (for preserve IDs scenario)
                externalIdFields.add("Id");
                
                // Query Salesforce for external ID fields on this object
                try {
                    RelationshipManager relManager = new RelationshipManager(
                        connectionInfo.getInstanceUrl(),
                        connectionInfo.getSessionId(),
                        "v59.0"
                    );
                    RelationshipManager.ObjectMetadata metadata = relManager.describeObject(objectName);
                    
                    for (RelationshipManager.FieldInfo field : metadata.getExternalIdFields()) {
                        if (!field.getName().equals("Id")) {
                            externalIdFields.add(field.getName());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load external ID fields for {}: {}", objectName, e.getMessage());
                }
                
                return externalIdFields;
            }
        };
        
        loadTask.setOnSucceeded(event -> {
            List<String> fields = loadTask.getValue();
            externalIdFieldCombo.getItems().setAll(fields);
            if (!fields.isEmpty()) {
                externalIdFieldCombo.setValue(fields.get(0)); // Default to Id
            }
            externalIdFieldCombo.setPromptText("Select field");
        });
        
        loadTask.setOnFailed(event -> {
            logger.error("Failed to load external ID fields", loadTask.getException());
            externalIdFieldCombo.getItems().add("Id");
            externalIdFieldCombo.setValue("Id");
            externalIdFieldCombo.setPromptText("Select field");
        });
        
        executorService().submit(loadTask);
    }
    
    private ExecutorService executorService() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newCachedThreadPool();
        }
        return executorService;
    }
    
    private void loadDatabaseConnections() {
        try {
            List<SavedConnection> connections = ConnectionManager.getInstance().getConnections();
            databaseConnectionCombo.setItems(FXCollections.observableArrayList(connections));
            
            databaseConnectionCombo.setConverter(new StringConverter<SavedConnection>() {
                @Override
                public String toString(SavedConnection conn) {
                    if (conn == null) return "";
                    return conn.getName() + " (" + conn.getType() + ")";
                }
                
                @Override
                public SavedConnection fromString(String string) {
                    return null;
                }
            });
        } catch (Exception e) {
            logger.error("Failed to load database connections", e);
        }
    }
    
    public void setConnectionInfo(LoginController.ConnectionInfo connInfo) {
        this.connectionInfo = connInfo;
        
        Platform.runLater(() -> {
            if (connInfo != null) {
                // connectionLabel may be null in content-only FXML (loaded by MainController)
                if (connectionLabel != null) {
                    connectionLabel.setText("✓ Connected");
                    connectionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #4CAF50;");
                }
                if (targetOrgLabel != null) {
                    targetOrgLabel.setText(connInfo.getUsername() + " @ " + connInfo.getInstanceUrl());
                }
            } else {
                if (connectionLabel != null) {
                    connectionLabel.setText("Not Connected");
                    connectionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #f44336;");
                }
                if (targetOrgLabel != null) {
                    targetOrgLabel.setText("No org connected - return to backup screen to connect");
                }
            }
        });
    }
    
    // Old handleBack() removed - navigation now handled by MainController sidebar
    
    @FXML
    private void handleBrowseRestore() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Folder to Restore");
        
        Stage stage = (Stage) browseRestoreButton.getScene().getWindow();
        File selectedDir = chooser.showDialog(stage);
        
        if (selectedDir != null) {
            restoreSourceField.setText(selectedDir.getAbsolutePath());
            logMessage("Selected folder: " + selectedDir.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleManageConnections() {
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
            dialog.initOwner(databaseConnectionCombo.getScene().getWindow());
            dialog.setResizable(true);
            dialog.setWidth(600);
            dialog.setHeight(700);
            
            dialog.showAndWait();
            
            // Refresh database connection dropdown after dialog closes
            loadDatabaseConnections();
            
        } catch (IOException e) {
            logger.error("Error opening database settings dialog", e);
            showError("Failed to open database settings: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleScanSource() {
        restoreObjects.clear();
        
        if (folderSourceRadio.isSelected()) {
            scanFolderSource();
        } else {
            scanDatabaseSource();
        }
    }
    
    private void scanFolderSource() {
        String sourcePath = restoreSourceField.getText();
        if (sourcePath == null || sourcePath.trim().isEmpty()) {
            showError("Please select a backup folder first");
            return;
        }
        
        File sourceDir = new File(sourcePath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            showError("Invalid backup folder: " + sourcePath);
            return;
        }
        
        logMessage("Scanning folder: " + sourcePath);
        startRestoreButton.setDisable(true);
        scanSourceButton.setDisable(true);
        
        // Show scanning indicator
        progressBar.setProgress(-1); // Indeterminate initially
        currentObjectLabel.setText("Scanning...");
        recordsProcessedLabel.setText("0 files scanned");
        
        // Find CSV and JSON files
        File[] backupFiles = sourceDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".csv") || name.toLowerCase().endsWith(".json")
        );
        
        if (backupFiles == null || backupFiles.length == 0) {
            logMessage("No backup files found in folder");
            showError("No CSV or JSON backup files found in selected folder");
            return;
        }
        
        // Create background task to scan files
        Task<List<RestoreObject>> scanTask = new Task<>() {
            @Override
            protected List<RestoreObject> call() throws Exception {
                List<RestoreObject> objects = new ArrayList<>();
                int total = backupFiles.length;
                int processed = 0;
                
                for (File file : backupFiles) {
                    if (isCancelled()) break;
                    
                    String objectName = file.getName().replaceAll("\\.(csv|json)$", "");
                    
                    // Use fast line counting for large files
                    long recordCount = countRecordsInFileFast(file);
                    
                    String lastModified = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    try {
                        lastModified = Files.getLastModifiedTime(file.toPath())
                            .toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    } catch (IOException e) {
                        // Use default
                    }
                    
                    objects.add(new RestoreObject(objectName, recordCount, lastModified, file.getAbsolutePath()));
                    
                    processed++;
                    int current = processed;
                    String fileName = objectName;
                    // Update UI more frequently for better feedback
                    Platform.runLater(() -> {
                        recordsProcessedLabel.setText(current + " / " + total + " files scanned");
                        currentObjectLabel.setText("Scanning: " + fileName);
                    });
                    updateProgress(processed, total);
                }
                
                return objects;
            }
        };
        
        // Bind progress bar to task progress
        progressBar.progressProperty().bind(scanTask.progressProperty());
        
        scanTask.setOnSucceeded(event -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            currentObjectLabel.setText("Scan complete");
            scanSourceButton.setDisable(false);
            
            List<RestoreObject> objects = scanTask.getValue();
            restoreObjects.addAll(objects);
            updateObjectCount();
            startRestoreButton.setDisable(restoreObjects.isEmpty());
            logMessage("Found " + restoreObjects.size() + " backup files");
        });
        
        scanTask.setOnFailed(event -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            currentObjectLabel.setText("Scan failed");
            scanSourceButton.setDisable(false);
            
            logMessage("Error scanning folder: " + scanTask.getException().getMessage());
            logger.error("Folder scan failed", scanTask.getException());
        });
        
        Thread thread = new Thread(scanTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    private void scanDatabaseSource() {
        SavedConnection selected = databaseConnectionCombo.getValue();
        if (selected == null) {
            showError("Please select a database connection");
            return;
        }
        
        logMessage("Scanning database: " + selected.getName());
        startRestoreButton.setDisable(true);
        
        // Create a background task to scan the database
        Task<List<BackupTable>> scanTask = new Task<>() {
            @Override
            protected List<BackupTable> call() throws Exception {
                DatabaseScanner scanner = new DatabaseScanner(selected);
                scanner.setLogConsumer(msg -> Platform.runLater(() -> logMessage(msg)));
                return scanner.scanForBackupTables();
            }
        };
        
        scanTask.setOnSucceeded(event -> {
            List<BackupTable> tables = scanTask.getValue();
            
            if (tables.isEmpty()) {
                logMessage("No Salesforce backup tables found in database");
                showInfo("No Backup Tables", 
                    "No Salesforce backup tables were found in the selected database.\n\n" +
                    "Tables must have an 'Id' column and at least two other common Salesforce " +
                    "fields (Name, CreatedDate, LastModifiedDate, etc.) to be recognized.");
            } else {
                // Add tables to the restore objects list with database source flag
                for (BackupTable table : tables) {
                    // Store the qualified table name as the source path
                    String sourcePath = table.getSchema() + "." + table.getTableName();
                    restoreObjects.add(new RestoreObject(
                        table.getObjectName(),
                        table.getRecordCount(),
                        table.getLastModified(),
                        sourcePath,
                        true,  // isDatabaseSource = true
                        selected  // Store the connection for later use
                    ));
                }
                
                updateObjectCount();
                startRestoreButton.setDisable(restoreObjects.isEmpty());
                logMessage("Found " + tables.size() + " backup tables ready for restore");
            }
        });
        
        scanTask.setOnFailed(event -> {
            Throwable error = scanTask.getException();
            logger.error("Database scan failed", error);
            logMessage("ERROR: Database scan failed - " + error.getMessage());
            showError("Failed to scan database: " + error.getMessage());
        });
        
        Thread thread = new Thread(scanTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    private long countRecordsInFile(File file) {
        try {
            if (file.getName().toLowerCase().endsWith(".csv")) {
                // Count lines in CSV (minus header)
                long lines = Files.lines(file.toPath()).count();
                return Math.max(0, lines - 1);
            } else {
                // For JSON, this is more complex - return estimate
                return file.length() / 500; // rough estimate
            }
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Fast line counting using buffered byte reading.
     * For very large files (>50MB), uses file size estimation to avoid long waits.
     */
    private long countRecordsInFileFast(File file) {
        if (file.getName().toLowerCase().endsWith(".json")) {
            // For JSON, return rough estimate based on file size
            return file.length() / 500;
        }
        
        long fileSize = file.length();
        
        // For very large files (>50MB), use estimation to avoid long waits
        // Average CSV row is ~200-500 bytes depending on content
        if (fileSize > 50 * 1024 * 1024) { // 50MB
            // Sample first 1MB to estimate average line length
            long avgLineLength = estimateAverageLineLength(file, 1024 * 1024);
            if (avgLineLength > 0) {
                return Math.max(0, (fileSize / avgLineLength) - 1);
            }
            // Fallback: assume 300 bytes per line
            return Math.max(0, (fileSize / 300) - 1);
        }
        
        // For smaller files, count newlines efficiently
        long count = 0;
        try (java.io.BufferedInputStream bis = new java.io.BufferedInputStream(
                new java.io.FileInputStream(file), 65536)) {
            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n') {
                        count++;
                    }
                }
            }
        } catch (IOException e) {
            return 0;
        }
        
        // Subtract 1 for header row
        return Math.max(0, count - 1);
    }
    
    /**
     * Sample the first N bytes of a file to estimate average line length.
     */
    private long estimateAverageLineLength(File file, int sampleSize) {
        long lines = 0;
        long bytesRead = 0;
        
        try (java.io.BufferedInputStream bis = new java.io.BufferedInputStream(
                new java.io.FileInputStream(file), 65536)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = bis.read(buffer)) != -1 && bytesRead < sampleSize) {
                int limit = (int) Math.min(read, sampleSize - bytesRead);
                for (int i = 0; i < limit; i++) {
                    if (buffer[i] == '\n') {
                        lines++;
                    }
                }
                bytesRead += limit;
            }
        } catch (IOException e) {
            return 0;
        }
        
        if (lines == 0) return 0;
        return bytesRead / lines;
    }
    
    private void updateObjectCount() {
        int total = restoreObjects.size();
        int filtered = filteredObjects.size();
        int selected = (int) restoreObjects.stream().filter(RestoreObject::isSelected).count();
        
        if (total == filtered) {
            objectCountLabel.setText(selected + " / " + total + " selected");
        } else {
            objectCountLabel.setText(filtered + " shown, " + selected + " selected");
        }
    }
    
    @FXML
    private void handleSelectAll() {
        filteredObjects.forEach(obj -> obj.setSelected(true));
        updateObjectCount();
    }
    
    @FXML
    private void handleDeselectAll() {
        filteredObjects.forEach(obj -> obj.setSelected(false));
        updateObjectCount();
    }
    
    @FXML
    private void handleConfigureTransformations() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/transformation.fxml"));
            Parent root = loader.load();
            
            TransformationController controller = loader.getController();
            controller.setConnectionInfo(connectionInfo);
            
            // Pass the backup directory
            String sourcePath = restoreSourceField.getText();
            if (sourcePath != null && !sourcePath.trim().isEmpty()) {
                File backupDir = new File(sourcePath);
                if (backupDir.exists() && backupDir.isDirectory()) {
                    controller.setBackupDirectory(backupDir);
                }
            }
            
            // Pass the selected objects
            Set<String> selectedObjectNames = restoreObjects.stream()
                .filter(RestoreObject::isSelected)
                .map(RestoreObject::getName)
                .collect(java.util.stream.Collectors.toSet());
            controller.setSelectedObjects(selectedObjectNames);
            
            // Pass existing config if we have one
            if (transformationConfig != null) {
                controller.setTransformationConfig(transformationConfig);
            }
            
            // Set callback to receive the config when user applies
            controller.setOnApply(config -> {
                this.transformationConfig = config;
                updateTransformationStatus();
            });
            
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Configure Cross-Org Transformations");
            dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialogStage.initOwner(configureTransformButton.getScene().getWindow());
            dialogStage.setScene(new Scene(root, 1000, 700));
            dialogStage.showAndWait();
            
        } catch (Exception e) {
            logger.error("Failed to open transformation dialog", e);
            showError("Failed to open transformation configuration: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleLoadTransformConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Transformation Config");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        
        File file = fileChooser.showOpenDialog(loadTransformButton.getScene().getWindow());
        if (file != null) {
            try {
                transformationConfig = TransformationConfig.loadFromFile(file);
                updateTransformationStatus();
                logMessage("Loaded transformation config: " + file.getName());
            } catch (Exception e) {
                logger.error("Failed to load transformation config", e);
                showError("Failed to load config: " + e.getMessage());
            }
        }
    }
    
    private void updateTransformationStatus() {
        if (transformationConfig == null) {
            transformStatusLabel.setText("Not configured");
            transformStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
        } else {
            int mappings = transformationConfig.getUserMappings().size() + 
                          transformationConfig.getRecordTypeMappings().size() +
                          transformationConfig.getObjectConfigs().size();
            transformStatusLabel.setText("✓ " + mappings + " mappings configured");
            transformStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #4CAF50;");
        }
    }
    
    @FXML
    private void handleStartRestore() {
        List<RestoreObject> selectedObjects = restoreObjects.stream()
            .filter(RestoreObject::isSelected)
            .collect(java.util.stream.Collectors.toList());
        
        if (selectedObjects.isEmpty()) {
            showError("Please select at least one object to restore");
            return;
        }
        
        if (connectionInfo == null) {
            showError("No Salesforce connection. Please return to backup screen and connect first.");
            return;
        }
        
        // Confirmation dialog
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Restore");
        alert.setHeaderText("⚠️ Data Restoration Warning");
        
        String mode = insertOnlyRadio.isSelected() ? "INSERT" : 
                     upsertRadio.isSelected() ? "UPSERT" : "UPDATE";
        
        String targetInfo = connectionInfo.getUsername() + " @ " + connectionInfo.getInstanceUrl();
        alert.setContentText("You are about to " + mode + " data into your Salesforce org.\n\n" +
                "Objects: " + selectedObjects.size() + "\n" +
                "Target: " + targetInfo + "\n\n" +
                "This operation may modify existing data. Continue?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            startRestore(selectedObjects);
        }
    }
    
    private void startRestore(List<RestoreObject> objects) {
        restoreRunning = true;
        startRestoreButton.setDisable(true);
        stopRestoreButton.setDisable(false);
        
        logMessage("=== Starting Data Restoration ===");
        
        RestoreExecutor.RestoreMode mode = insertOnlyRadio.isSelected() ? 
            RestoreExecutor.RestoreMode.INSERT : 
            upsertRadio.isSelected() ? RestoreExecutor.RestoreMode.UPSERT : 
            RestoreExecutor.RestoreMode.UPDATE;
        
        logMessage("Mode: " + mode);
        logMessage("Objects to restore: " + objects.size());
        
        // Create restore executor
        restoreExecutor = new RestoreExecutor(
            connectionInfo.getInstanceUrl(),
            connectionInfo.getSessionId(),
            "60.0"
        );
        
        // Set up callbacks
        restoreExecutor.setLogCallback(this::logMessage);
        restoreExecutor.setProgressCallback(progress -> {
            Platform.runLater(() -> {
                double percent = progress.getPercentComplete() / 100.0;
                progressBar.setProgress(percent);
                progressPercentLabel.setText(String.format("%.1f%%", progress.getPercentComplete()));
                currentObjectLabel.setText(progress.getCurrentObject());
                recordsProcessedLabel.setText(String.format("%d / %d (✓ %d, ✗ %d)",
                    progress.getProcessedRecords(), progress.getTotalRecords(),
                    progress.getSuccessCount(), progress.getFailureCount()));
            });
        });
        
        // Create restore options
        RestoreOptions options = new RestoreOptions();
        options.setBatchSize(batchSizeCombo.getValue());
        options.setStopOnError(stopOnErrorCheck.isSelected());
        options.setValidateBeforeRestore(validateBeforeRestoreCheck.isSelected());
        options.setResolveRelationships(true); // Always resolve relationships
        options.setPreserveIds(preserveIdsCheck.isSelected());
        options.setDryRun(dryRunCheck.isSelected());
        
        // Set external ID field for upsert operations
        if (upsertRadio.isSelected() && externalIdFieldCombo.getValue() != null) {
            options.setExternalIdField(externalIdFieldCombo.getValue());
            logMessage("Using external ID field: " + externalIdFieldCombo.getValue());
        }
        
        if (preserveIdsCheck.isSelected()) {
            logMessage("Preserve IDs enabled - will attempt to use original Salesforce IDs");
        }
        
        // Order objects by dependency (parents first)
        logMessage("Analyzing object dependencies...");
        List<RestoreObject> orderedObjects = orderObjectsByDependency(objects);
        
        if (dryRunCheck.isSelected()) {
            logMessage("🔍 DRY RUN MODE - Generating preview...");
            showDryRunPreview(orderedObjects, options, mode);
            return;
        }
        logMessage("Restore order: " + orderedObjects.stream()
            .map(RestoreObject::getName)
            .collect(Collectors.joining(" → ")));
        
        // Run restore in background thread
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                int totalObjects = orderedObjects.size();
                int completedObjects = 0;
                int totalSuccess = 0;
                int totalFailed = 0;
                
                for (RestoreObject obj : orderedObjects) {
                    if (!restoreRunning) {
                        logMessage("⏹ Restore cancelled by user");
                        break;
                    }
                    
                    logMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    logMessage("Restoring: " + obj.getName());
                    
                    try {
                        RestoreResult result;
                        
                        if (obj.isDatabaseSource()) {
                            // Restore from database source
                            logMessage("Source: Database table " + obj.getTableName());
                            SavedConnection dbConn = obj.getDatabaseConnection();
                            if (dbConn == null) {
                                throw new IOException("Database connection not available for " + obj.getName());
                            }
                            
                            // Read data from database
                            DatabaseScanner scanner = new DatabaseScanner(dbConn);
                            List<Map<String, Object>> dbRecords = scanner.readTableData(obj.getTableName(), 0);
                            logMessage("Read " + dbRecords.size() + " records from database");
                            
                            // Use the new restoreFromDatabase method
                            result = restoreExecutor.restoreFromDatabase(
                                obj.getName(), dbRecords, mode, options
                            );
                        } else {
                            // Restore from CSV file
                            Path csvPath = Path.of(obj.getFilePath());
                            result = restoreExecutor.restoreFromCsv(
                                obj.getName(), csvPath, mode, options
                            );
                        }
                        
                        totalSuccess += result.getSuccessCount();
                        totalFailed += result.getFailureCount();
                        
                        if (!result.getErrors().isEmpty()) {
                            int errorLimit = Math.min(5, result.getErrors().size());
                            for (int i = 0; i < errorLimit; i++) {
                                logMessage("  ⚠ " + result.getErrors().get(i));
                            }
                            if (result.getErrors().size() > 5) {
                                logMessage("  ... and " + (result.getErrors().size() - 5) + " more errors");
                            }
                        }
                        
                        logMessage(String.format("%s: ✓ %d success, ✗ %d failed",
                            obj.getName(), result.getSuccessCount(), result.getFailureCount()));
                        
                    } catch (Exception e) {
                        logger.error("Error restoring " + obj.getName(), e);
                        logMessage("❌ Error: " + e.getMessage());
                        totalFailed++;
                        
                        if (options.isStopOnError()) {
                            break;
                        }
                    }
                    
                    completedObjects++;
                    final int completed = completedObjects;
                    Platform.runLater(() -> {
                        double objectProgress = (double) completed / totalObjects;
                        progressBar.setProgress(objectProgress);
                        progressPercentLabel.setText(String.format("%.0f%%", objectProgress * 100));
                    });
                }
                
                logMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                logMessage("=== Restore Complete ===");
                logMessage(String.format("Total: ✓ %d success, ✗ %d failed", totalSuccess, totalFailed));
                
            } catch (Exception e) {
                logger.error("Restore failed", e);
                logMessage("❌ Restore failed: " + e.getMessage());
            } finally {
                restoreExecutor.close();
                restoreRunning = false;
                
                Platform.runLater(() -> {
                    startRestoreButton.setDisable(false);
                    stopRestoreButton.setDisable(true);
                    progressBar.setProgress(1.0);
                });
            }
        });
    }
    
    @FXML
    private void handleStopRestore() {
        if (restoreRunning) {
            restoreRunning = false;
            if (restoreExecutor != null) {
                restoreExecutor.cancel();
            }
            logMessage("⏹ Restore stopped by user");
            startRestoreButton.setDisable(false);
            stopRestoreButton.setDisable(true);
        }
    }
    
    private void showDryRunPreview(List<RestoreObject> objects, RestoreOptions options, 
                                   RestoreExecutor.RestoreMode mode) {
        logMessage("Generating preview for " + objects.size() + " objects...");
        
        // Create preview dialog
        Stage previewStage = new Stage();
        previewStage.setTitle("🔍 Dry Run Preview - What Will Be Imported");
        previewStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
        previewStage.initOwner(startRestoreButton.getScene().getWindow());
        
        // Create main container with dark theme
        VBox mainContainer = new VBox(15);
        mainContainer.getStyleClass().add("main-container");
        mainContainer.setStyle("-fx-background-color: #1a1a1a; -fx-padding: 20;");
        
        // Header
        Label headerLabel = new Label("Preview: Data to be " + mode.name() + "ed into Salesforce");
        headerLabel.getStyleClass().add("section-title");
        
        Label infoLabel = new Label("This is a dry run. No data will be modified. Review the data below before proceeding.");
        infoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #4FC3F7;");
        
        // Object selector
        ComboBox<String> objectSelector = new ComboBox<>();
        objectSelector.getStyleClass().add("modern-combo");
        objectSelector.setPromptText("Select object to preview...");
        objectSelector.setMinWidth(300);
        for (RestoreObject obj : objects) {
            objectSelector.getItems().add(obj.getName() + " (" + obj.getRecordCount() + " records)");
        }
        
        // Data table (shows first 100 rows)
        TableView<Map<String, String>> previewTable = new TableView<>();
        previewTable.getStyleClass().add("modern-table");
        previewTable.setMinHeight(400);
        previewTable.setPlaceholder(new Label("Select an object to preview data"));
        VBox.setVgrow(previewTable, javafx.scene.layout.Priority.ALWAYS);
        
        // Summary area
        TextArea summaryArea = new TextArea();
        summaryArea.getStyleClass().add("modern-text-area");
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        summaryArea.setPrefHeight(150);
        
        // Object selector listener
        objectSelector.setOnAction(e -> {
            String selected = objectSelector.getValue();
            if (selected == null) return;
            
            int idx = objectSelector.getSelectionModel().getSelectedIndex();
            RestoreObject obj = objects.get(idx);
            
            loadPreviewData(obj, previewTable, summaryArea, options);
        });
        
        // Buttons
        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(15);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        
        Button proceedButton = new Button("✓ Proceed with Restore");
        proceedButton.getStyleClass().add("success-button");
        proceedButton.setOnAction(e -> {
            previewStage.close();
            dryRunCheck.setSelected(false); // Uncheck dry run
            startRestore(objects); // Actually do the restore
        });
        
        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("secondary-button");
        cancelButton.setOnAction(e -> {
            previewStage.close();
            logMessage("Dry run preview closed. No changes made.");
            restoreRunning = false;
            startRestoreButton.setDisable(false);
            stopRestoreButton.setDisable(true);
        });
        
        buttonBox.getChildren().addAll(cancelButton, proceedButton);
        
        // Build summary
        StringBuilder summary = new StringBuilder();
        summary.append("=== DRY RUN SUMMARY ===\n");
        summary.append("Mode: ").append(mode).append("\n");
        summary.append("Objects: ").append(objects.size()).append("\n");
        summary.append("Order: ").append(objects.stream()
            .map(RestoreObject::getName)
            .collect(Collectors.joining(" → "))).append("\n\n");
        
        long totalRecords = objects.stream().mapToLong(RestoreObject::getRecordCount).sum();
        summary.append("Total Records: ").append(String.format("%,d", totalRecords)).append("\n\n");
        
        if (transformationConfig != null) {
            int mappings = transformationConfig.getUserMappings().size() + 
                          transformationConfig.getRecordTypeMappings().size();
            summary.append("Transformations: ").append(mappings).append(" mappings configured\n");
        } else {
            summary.append("Transformations: None configured\n");
        }
        
        summaryArea.setText(summary.toString());
        
        // Summary label with styling
        Label summaryLabel = new Label("Summary:");
        summaryLabel.getStyleClass().add("field-label");
        
        mainContainer.getChildren().addAll(
            headerLabel, infoLabel, objectSelector, previewTable, 
            summaryLabel, summaryArea, buttonBox
        );
        
        Scene scene = new Scene(mainContainer, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/css/backupforce-modern.css").toExternalForm());
        previewStage.setScene(scene);
        previewStage.show();
        
        // Auto-select first object
        if (!objectSelector.getItems().isEmpty()) {
            objectSelector.getSelectionModel().select(0);
        }
    }
    
    private void loadPreviewData(RestoreObject obj, TableView<Map<String, String>> table, 
                                  TextArea summaryArea, RestoreOptions options) {
        table.getColumns().clear();
        table.getItems().clear();
        
        try {
            List<String> headers;
            List<Map<String, String>> rows = new ArrayList<>();
            
            if (obj.isDatabaseSource()) {
                // Load preview data from database
                SavedConnection dbConn = obj.getDatabaseConnection();
                if (dbConn == null) {
                    summaryArea.appendText("\nDatabase connection not available for " + obj.getName());
                    return;
                }
                
                DatabaseScanner scanner = new DatabaseScanner(dbConn);
                List<Map<String, Object>> dbRecords = scanner.readTableData(obj.getTableName(), 100);
                
                if (dbRecords.isEmpty()) {
                    summaryArea.appendText("\nNo data in " + obj.getName());
                    return;
                }
                
                // Extract headers from first record
                headers = new ArrayList<>(dbRecords.get(0).keySet());
                
                // Convert to string map for display
                for (Map<String, Object> dbRecord : dbRecords) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String header : headers) {
                        Object value = dbRecord.get(header);
                        String strValue = value != null ? value.toString() : "";
                        
                        // Apply transformations for preview if configured
                        if (transformationConfig != null) {
                            strValue = applyPreviewTransformation(obj.getName(), header, strValue);
                        }
                        
                        row.put(header, strValue);
                    }
                    rows.add(row);
                }
                
                summaryArea.appendText("\n\n" + obj.getName() + ": " + obj.getRecordCount() + " records (from database)");
                if (obj.getRecordCount() > 100) {
                    summaryArea.appendText(" (showing first 100)");
                }
                summaryArea.appendText("\nFields: " + headers.size());
                
            } else {
                // Load preview data from CSV file
                Path csvPath = Path.of(obj.getFilePath());
                List<String> lines = Files.readAllLines(csvPath);
                
                if (lines.isEmpty()) {
                    summaryArea.appendText("\nNo data in " + obj.getName());
                    return;
                }
                
                // Parse header
                String[] headerArray = parseCSVLine(lines.get(0));
                headers = Arrays.asList(headerArray);
                
                // Load first 100 rows
                int rowLimit = Math.min(lines.size() - 1, 100);
                for (int r = 1; r <= rowLimit; r++) {
                    String[] values = parseCSVLine(lines.get(r));
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int c = 0; c < Math.min(values.length, headerArray.length); c++) {
                        String value = values[c];
                        
                        // Apply transformations for preview if configured
                        if (transformationConfig != null) {
                            value = applyPreviewTransformation(obj.getName(), headerArray[c], value);
                        }
                        
                        row.put(headerArray[c], value);
                    }
                    rows.add(row);
                }
                
                summaryArea.appendText("\n\n" + obj.getName() + ": " + (lines.size() - 1) + " records");
                if (lines.size() > 101) {
                    summaryArea.appendText(" (showing first 100)");
                }
                summaryArea.appendText("\nFields: " + headers.size());
            }
            
            // Create columns (limit to 15 columns for readability)
            int colLimit = Math.min(headers.size(), 15);
            for (int i = 0; i < colLimit; i++) {
                final String headerName = headers.get(i);
                TableColumn<Map<String, String>, String> col = new TableColumn<>(headerName);
                col.setCellValueFactory(data -> 
                    new SimpleStringProperty(data.getValue().getOrDefault(headerName, "")));
                col.setPrefWidth(120);
                table.getColumns().add(col);
            }
            
            if (headers.size() > 15) {
                TableColumn<Map<String, String>, String> moreCol = new TableColumn<>("... +" + (headers.size() - 15) + " more");
                moreCol.setCellValueFactory(data -> new SimpleStringProperty("..."));
                moreCol.setPrefWidth(80);
                table.getColumns().add(moreCol);
            }
            
            // Add rows to table
            table.getItems().addAll(rows);
            
        } catch (Exception e) {
            summaryArea.appendText("\nError loading " + obj.getName() + ": " + e.getMessage());
            logger.error("Error loading preview data for " + obj.getName(), e);
        }
    }
    
    private String[] parseCSVLine(String line) {
        // Simple CSV parsing (handles basic cases)
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        
        return values.toArray(new String[0]);
    }
    
    private String applyPreviewTransformation(String objectName, String fieldName, String value) {
        if (value == null || value.isEmpty() || transformationConfig == null) {
            return value;
        }
        
        // Check for RecordTypeId mapping
        if ("RecordTypeId".equalsIgnoreCase(fieldName)) {
            String mapped = transformationConfig.getRecordTypeMappings().get(value);
            if (mapped != null) {
                return mapped + " ⟵ " + value;
            }
        }
        
        // Check for user field mapping
        if (fieldName.toLowerCase().contains("ownerid") || fieldName.toLowerCase().contains("userid")) {
            String mapped = transformationConfig.getUserMappings().get(value);
            if (mapped != null) {
                return mapped + " ⟵ " + value;
            }
        }
        
        // Check for picklist/field mappings in object config
        TransformationConfig.ObjectTransformConfig objConfig = 
            transformationConfig.getObjectConfigs().get(objectName);
        if (objConfig != null) {
            Map<String, String> picklistMappings = objConfig.getPicklistMappings().get(fieldName);
            if (picklistMappings != null && picklistMappings.containsKey(value)) {
                return picklistMappings.get(value) + " ⟵ " + value;
            }
        }
        
        return value;
    }
    
    private void logMessage(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> {
            logArea.appendText("[" + timestamp + "] " + message + "\n");
        });
        logger.info(message);
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
    
    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Orders objects by dependency for restoration.
     * Parent objects are restored before children to ensure lookup fields can be populated.
     */
    private List<RestoreObject> orderObjectsByDependency(List<RestoreObject> objects) {
        if (connectionInfo == null) {
            return objects; // Can't order without connection
        }
        
        try {
            // Create dependency orderer
            RelationshipManager relationshipManager = new RelationshipManager(
                connectionInfo.getInstanceUrl(),
                connectionInfo.getSessionId(),
                "60.0"
            );
            DependencyOrderer orderer = new DependencyOrderer(relationshipManager);
            orderer.setLogCallback(this::logMessage);
            
            // Get object names
            Set<String> objectNames = objects.stream()
                .map(RestoreObject::getName)
                .collect(Collectors.toSet());
            
            // Get ordered list
            List<String> orderedNames = orderer.orderForRestore(objectNames);
            
            // Map names back to RestoreObject instances
            Map<String, RestoreObject> objectMap = objects.stream()
                .collect(Collectors.toMap(RestoreObject::getName, o -> o));
            
            List<RestoreObject> ordered = new ArrayList<>();
            for (String name : orderedNames) {
                RestoreObject obj = objectMap.get(name);
                if (obj != null) {
                    ordered.add(obj);
                }
            }
            
            return ordered;
            
        } catch (Exception e) {
            logger.warn("Could not determine dependency order, using original order: {}", e.getMessage());
            logMessage("Warning: Could not analyze dependencies, using default order");
            return objects;
        }
    }
    
    // ==================== RestoreObject Model ====================
    
    public static class RestoreObject {
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleLongProperty recordCount = new SimpleLongProperty();
        private final SimpleStringProperty lastBackup = new SimpleStringProperty();
        private final String filePath; // For file sources, this is the file path; for DB sources, it's "schema.table"
        private final boolean isDatabaseSource;
        private SavedConnection databaseConnection; // Only set for database sources
        
        public RestoreObject(String name, long recordCount, String lastBackup, String filePath) {
            this(name, recordCount, lastBackup, filePath, false, null);
        }
        
        public RestoreObject(String name, long recordCount, String lastBackup, String sourcePath, 
                           boolean isDatabaseSource, SavedConnection connection) {
            this.name.set(name);
            this.recordCount.set(recordCount);
            this.lastBackup.set(lastBackup);
            this.filePath = sourcePath;
            this.isDatabaseSource = isDatabaseSource;
            this.databaseConnection = connection;
        }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public SimpleBooleanProperty selectedProperty() { return selected; }
        
        public String getName() { return name.get(); }
        public SimpleStringProperty nameProperty() { return name; }
        
        public long getRecordCount() { return recordCount.get(); }
        public SimpleLongProperty recordCountProperty() { return recordCount; }
        
        public String getLastBackup() { return lastBackup.get(); }
        public SimpleStringProperty lastBackupProperty() { return lastBackup; }
        
        public String getFilePath() { return filePath; }
        
        public boolean isDatabaseSource() { return isDatabaseSource; }
        
        public SavedConnection getDatabaseConnection() { return databaseConnection; }
        
        /**
         * For database sources, extracts the table name from the source path (schema.table format)
         */
        public String getTableName() {
            if (isDatabaseSource && filePath != null && filePath.contains(".")) {
                return filePath.substring(filePath.lastIndexOf('.') + 1);
            }
            return filePath;
        }
    }
}
