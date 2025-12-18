package com.backupforce.ui;

import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.restore.RestoreExecutor;
import com.backupforce.restore.RestoreExecutor.RestoreOptions;
import com.backupforce.restore.RestoreExecutor.RestoreResult;
import com.backupforce.restore.RestoreExecutor.RestoreProgress;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    @FXML private CheckBox validateBeforeRestoreCheck;
    @FXML private CheckBox stopOnErrorCheck;
    @FXML private CheckBox preserveIdsCheck;
    @FXML private ComboBox<Integer> batchSizeCombo;
    
    // Target org
    @FXML private Label connectionLabel;
    @FXML private Label targetOrgLabel;
    
    // Actions
    @FXML private Button backButton;
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
                connectionLabel.setText("✓ Connected");
                connectionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #4CAF50;");
                targetOrgLabel.setText(connInfo.getUsername() + " @ " + connInfo.getInstanceUrl());
            } else {
                connectionLabel.setText("Not Connected");
                connectionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #f44336;");
                targetOrgLabel.setText("No org connected - return to backup screen to connect");
            }
        });
    }
    
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/backup.fxml"));
            Parent root = loader.load();
            
            BackupController controller = loader.getController();
            if (connectionInfo != null) {
                controller.setConnectionInfo(connectionInfo);
            }
            
            Scene scene = new Scene(root, 1100, 750);
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen();
            
        } catch (IOException e) {
            logger.error("Error returning to backup screen", e);
            showError("Failed to return to backup screen: " + e.getMessage());
        }
    }
    
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/connections.fxml"));
            Parent root = loader.load();
            
            ConnectionsController controller = loader.getController();
            controller.setConnectionInfo(connectionInfo);
            
            Scene scene = new Scene(root, 820, 560);
            Stage stage = (Stage) databaseConnectionCombo.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen();
            
        } catch (IOException e) {
            logger.error("Error opening connections screen", e);
            showError("Failed to open connections: " + e.getMessage());
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
        
        // Find CSV and JSON files
        File[] backupFiles = sourceDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".csv") || name.toLowerCase().endsWith(".json")
        );
        
        if (backupFiles == null || backupFiles.length == 0) {
            logMessage("No backup files found in folder");
            showError("No CSV or JSON backup files found in selected folder");
            return;
        }
        
        for (File file : backupFiles) {
            String objectName = file.getName().replaceAll("\\.(csv|json)$", "");
            long recordCount = countRecordsInFile(file);
            String lastModified = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            try {
                lastModified = Files.getLastModifiedTime(file.toPath())
                    .toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (IOException e) {
                // Use default
            }
            
            restoreObjects.add(new RestoreObject(objectName, recordCount, lastModified, file.getAbsolutePath()));
        }
        
        updateObjectCount();
        startRestoreButton.setDisable(restoreObjects.isEmpty());
        logMessage("Found " + restoreObjects.size() + " backup files");
    }
    
    private void scanDatabaseSource() {
        SavedConnection selected = databaseConnectionCombo.getValue();
        if (selected == null) {
            showError("Please select a database connection");
            return;
        }
        
        logMessage("Scanning database: " + selected.getName());
        
        // TODO: Implement database scanning - query for available backup tables
        // For now, show placeholder
        logMessage("Database scanning will be implemented in a future release");
        showInfo("Database Restore", "Database scanning feature coming soon!\n\n" +
                "This will query your database for available backup tables and their record counts.");
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
        
        // Run restore in background thread
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                int totalObjects = objects.size();
                int completedObjects = 0;
                int totalSuccess = 0;
                int totalFailed = 0;
                
                for (RestoreObject obj : objects) {
                    if (!restoreRunning) {
                        logMessage("⏹ Restore cancelled by user");
                        break;
                    }
                    
                    logMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    logMessage("Restoring: " + obj.getName());
                    
                    try {
                        Path csvPath = Path.of(obj.getFilePath());
                        RestoreResult result = restoreExecutor.restoreFromCsv(
                            obj.getName(), csvPath, mode, options
                        );
                        
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
    
    // ==================== RestoreObject Model ====================
    
    public static class RestoreObject {
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleLongProperty recordCount = new SimpleLongProperty();
        private final SimpleStringProperty lastBackup = new SimpleStringProperty();
        private final String filePath;
        
        public RestoreObject(String name, long recordCount, String lastBackup, String filePath) {
            this.name.set(name);
            this.recordCount.set(recordCount);
            this.lastBackup.set(lastBackup);
            this.filePath = filePath;
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
    }
}
