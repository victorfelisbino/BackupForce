package com.backupforce.ui;

import com.backupforce.bulkv2.BulkV2Client;
import com.sforce.soap.partner.DescribeGlobalResult;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BackupController {
    private static final Logger logger = LoggerFactory.getLogger(BackupController.class);
    
    // Objects known to cause memory issues due to large binary data
    private static final Set<String> LARGE_OBJECTS = new HashSet<>(Arrays.asList(
        "Attachment", "ContentVersion", "Document", "StaticResource"
    ));

    @FXML private TableView<SObjectItem> objectTable;
    @FXML private TableColumn<SObjectItem, Boolean> selectColumn;
    @FXML private TableColumn<SObjectItem, String> nameColumn;
    @FXML private TableColumn<SObjectItem, String> labelColumn;
    @FXML private TableColumn<SObjectItem, String> statusColumn;
    
    @FXML private TextField outputFolderField;
    @FXML private Button browseButton;
    @FXML private Button selectAllButton;
    @FXML private Button deselectAllButton;
    @FXML private Button startBackupButton;
    @FXML private Button stopBackupButton;
    
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private TextArea logArea;
    
    @FXML private Label connectionLabel;
    @FXML private TextField searchField;
    @FXML private Label selectionCountLabel;

    private LoginController.ConnectionInfo connectionInfo;
    private ObservableList<SObjectItem> allObjects;
    private ObservableList<SObjectItem> filteredObjects;
    private BackupTask currentBackupTask;
    private Thread backupThread;

    @FXML
    public void initialize() {
        // Setup table columns
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setEditable(true);
        
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        labelColumn.setCellValueFactory(cellData -> cellData.getValue().labelProperty());
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        
        objectTable.setEditable(true);
        
        // Set default output folder
        String userHome = System.getProperty("user.home");
        outputFolderField.setText(userHome + "\\BackupForce\\backup");
        
        // Setup search filter
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterObjects(newValue));
        
        stopBackupButton.setDisable(true);
        progressBar.setProgress(0);
        
        // Initialize selection counter
        updateSelectionCount();
    }

    public void setConnectionInfo(LoginController.ConnectionInfo connInfo) {
        this.connectionInfo = connInfo;
        connectionLabel.setText("Connected to: " + connInfo.getInstanceUrl() + " as " + connInfo.getUsername());
        loadObjects();
    }

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
                        items.add(new SObjectItem(sobject.getName(), sobject.getLabel()));
                    }
                }
                
                // Sort by name
                items.sort(Comparator.comparing(SObjectItem::getName));
                
                return items;
            }
        };

        loadTask.setOnSucceeded(event -> {
            allObjects = FXCollections.observableArrayList(loadTask.getValue());
            filteredObjects = FXCollections.observableArrayList(allObjects);
            
            // Add listener to each item's selected property
            for (SObjectItem item : allObjects) {
                item.selectedProperty().addListener((obs, oldVal, newVal) -> updateSelectionCount());
            }
            
            objectTable.setItems(filteredObjects);
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
            filteredObjects.setAll(allObjects);
        } else {
            String search = searchText.toLowerCase();
            List<SObjectItem> filtered = allObjects.stream()
                .filter(item -> item.getName().toLowerCase().contains(search) 
                             || item.getLabel().toLowerCase().contains(search))
                .collect(Collectors.toList());
            filteredObjects.setAll(filtered);
        }
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

    @FXML
    private void handleSelectAll() {
        if (filteredObjects != null) {
            filteredObjects.forEach(item -> item.setSelected(true));
            objectTable.refresh();
            updateSelectionCount();
        }
    }

    @FXML
    private void handleDeselectAll() {
        if (filteredObjects != null) {
            filteredObjects.forEach(item -> item.setSelected(false));
            objectTable.refresh();
            updateSelectionCount();
        }
    }

    @FXML
    private void handleStartBackup() {
        List<SObjectItem> selectedObjects = allObjects.stream()
            .filter(SObjectItem::isSelected)
            .collect(Collectors.toList());
        
        if (selectedObjects.isEmpty()) {
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
        
        String outputFolder = outputFolderField.getText().trim();
        if (outputFolder.isEmpty()) {
            showError("Please specify an output folder");
            return;
        }
        
        // Create output directory if it doesn't exist
        File outputDir = new File(outputFolder);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // Disable controls
        startBackupButton.setDisable(true);
        stopBackupButton.setDisable(false);
        objectTable.setDisable(true);
        browseButton.setDisable(true);
        selectAllButton.setDisable(true);
        deselectAllButton.setDisable(true);
        
        logArea.clear();
        progressBar.setProgress(0);
        progressLabel.setText("Starting backup...");
        
        logMessage("=".repeat(60));
        logMessage("BACKUP STARTED");
        logMessage("Objects to backup: " + selectedObjects.size());
        logMessage("Output folder: " + outputFolder);
        logMessage("=".repeat(60));
        
        // Reset all statuses
        allObjects.forEach(item -> item.setStatus(""));
        
        // Start backup
        currentBackupTask = new BackupTask(selectedObjects, outputFolder);
        
        currentBackupTask.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                startBackupButton.setDisable(false);
                stopBackupButton.setDisable(true);
                objectTable.setDisable(false);
                browseButton.setDisable(false);
                selectAllButton.setDisable(false);
                deselectAllButton.setDisable(false);
            });
        });
        
        currentBackupTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                startBackupButton.setDisable(false);
                stopBackupButton.setDisable(true);
                objectTable.setDisable(false);
                browseButton.setDisable(false);
                selectAllButton.setDisable(false);
                deselectAllButton.setDisable(false);
                progressLabel.setText("Backup failed");
                logMessage("Backup failed: " + currentBackupTask.getException().getMessage());
            });
        });
        
        currentBackupTask.setOnCancelled(event -> {
            Platform.runLater(() -> {
                startBackupButton.setDisable(false);
                stopBackupButton.setDisable(true);
                objectTable.setDisable(false);
                browseButton.setDisable(false);
                selectAllButton.setDisable(false);
                deselectAllButton.setDisable(false);
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

    private void logMessage(String message) {
        logger.info(message);
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(message + "\n");
                logArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Backup Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for backup task
    private class BackupTask extends Task<Void> {
        private final List<SObjectItem> objects;
        private final String outputFolder;
        private volatile boolean cancelled = false;
        private ExecutorService executor;

        public BackupTask(List<SObjectItem> objects, String outputFolder) {
            this.objects = objects;
            this.outputFolder = outputFolder;
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
            logMessage("Starting parallel backup with 10 threads...");
            logMessage("");
            
            AtomicInteger completed = new AtomicInteger(0);
            AtomicInteger successful = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            int totalObjects = objects.size();
            long startTime = System.currentTimeMillis();
            
            executor = Executors.newFixedThreadPool(10);
            
            for (SObjectItem item : objects) {
                if (cancelled) {
                    break;
                }
                
                executor.submit(() -> {
                    if (cancelled) return;
                    
                    String objectName = item.getName();
                    
                    // Log memory status for large objects
                    if (LARGE_OBJECTS.contains(objectName)) {
                        Runtime runtime = Runtime.getRuntime();
                        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
                        long maxMemory = runtime.maxMemory() / 1024 / 1024;
                        logMessage(String.format("[%s] WARNING: Large object backup starting (Memory: %d/%d MB)",
                            objectName, usedMemory, maxMemory));
                    } else {
                        logMessage("[" + objectName + "] Starting backup...");
                    }
                    
                    try {
                        Platform.runLater(() -> item.setStatus("Processing..."));
                        
                        long objectStart = System.currentTimeMillis();
                        bulkClient.queryObject(objectName, outputFolder);
                        long objectTime = System.currentTimeMillis() - objectStart;
                        
                        successful.incrementAndGet();
                        Platform.runLater(() -> item.setStatus("✓ Completed"));
                        logMessage("[" + objectName + "] ✓ Completed in " + objectTime / 1000.0 + "s");
                        
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
                        Platform.runLater(() -> item.setStatus("✗ Out of Memory"));
                        logMessage("[" + objectName + "] ✗ OUT OF MEMORY ERROR!");
                        logMessage("[" + objectName + "] This object is too large. Try:");
                        logMessage("[" + objectName + "]   • Backup this object individually");
                        logMessage("[" + objectName + "]   • Increase heap size: mvn javafx:run -Dexec.args='-Xmx4g'");
                        logger.error("Out of memory backing up " + objectName, oom);
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        Platform.runLater(() -> item.setStatus("✗ Failed"));
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        logMessage("[" + objectName + "] ✗ Failed: " + errorMsg);
                        logger.error("Backup failed for " + objectName, e);
                        
                    } finally {
                        int completedCount = completed.incrementAndGet();
                        double progress = (double) completedCount / totalObjects;
                        
                        Platform.runLater(() -> {
                            progressBar.setProgress(progress);
                            progressLabel.setText(String.format("Progress: %d/%d (%d successful, %d failed) - %.1f%%",
                                completedCount, totalObjects, successful.get(), failed.get(), progress * 100));
                        });
                        
                        // Log progress updates more frequently
                        if (completedCount % 5 == 0 || completedCount == totalObjects) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double avgTimePerObject = (double) elapsed / completedCount;
                            long remaining = (long) ((totalObjects - completedCount) * avgTimePerObject);
                            
                            logMessage("");
                            logMessage(String.format("--- Progress Update: %d/%d completed (%.1f%%) ---",
                                completedCount, totalObjects, progress * 100));
                            logMessage(String.format("    Successful: %d | Failed: %d", successful.get(), failed.get()));
                            logMessage(String.format("    Elapsed: %ds | ETA: %ds",
                                elapsed / 1000, remaining / 1000));
                            logMessage("");
                        }
                    }
                });
            }
            
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
            
            bulkClient.close();
            
            long totalTime = System.currentTimeMillis() - startTime;
            
            Platform.runLater(() -> {
                logMessage("=".repeat(60));
                logMessage("Backup completed!");
                logMessage("Total objects: " + totalObjects);
                logMessage("Successful: " + successful.get());
                logMessage("Failed: " + failed.get());
                logMessage("Total time: " + totalTime / 1000 + " seconds");
                logMessage("Output folder: " + outputFolder);
                logMessage("=".repeat(60));
                
                if (!cancelled) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Backup Complete");
                    alert.setHeaderText("Backup Finished Successfully");
                    alert.setContentText(String.format(
                        "Backed up %d objects in %d seconds\n\nOutput: %s",
                        successful.get(), totalTime / 1000, outputFolder
                    ));
                    alert.showAndWait();
                }
            });
            
            return null;
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
                startBackupButton.setDisable(false);
                stopBackupButton.setDisable(true);
                objectTable.setDisable(false);
                browseButton.setDisable(false);
                selectAllButton.setDisable(false);
                deselectAllButton.setDisable(false);
                progressLabel.setText("Backup cancelled");
                logMessage("Backup cancelled by user");
            });
            
            return super.cancel(mayInterruptIfRunning);
        }
    }

    // Model class for SObject items
    public static class SObjectItem {
        private final SimpleStringProperty name;
        private final SimpleStringProperty label;
        private final SimpleStringProperty status;
        private boolean selected;

        public SObjectItem(String name, String label) {
            this.name = new SimpleStringProperty(name);
            this.label = new SimpleStringProperty(label);
            this.status = new SimpleStringProperty("");
            this.selected = false;
        }

        public String getName() { return name.get(); }
        public SimpleStringProperty nameProperty() { return name; }
        
        public String getLabel() { return label.get(); }
        public SimpleStringProperty labelProperty() { return label; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public SimpleStringProperty statusProperty() { return status; }
        
        public boolean isSelected() { return selected; }
        public void setSelected(boolean value) { this.selected = value; }
        public javafx.beans.property.BooleanProperty selectedProperty() {
            return new javafx.beans.property.SimpleBooleanProperty(selected) {
                @Override
                public void set(boolean newValue) {
                    super.set(newValue);
                    selected = newValue;
                }
            };
        }
    }
}
