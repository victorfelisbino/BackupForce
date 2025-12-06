package com.backupforce.ui;

import com.backupforce.bulkv2.BulkV2Client;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    // Selection Table (before backup)
    @FXML private TableView<SObjectItem> allObjectsTable;
    @FXML private TableColumn<SObjectItem, Boolean> allSelectColumn;
    @FXML private TableColumn<SObjectItem, String> allNameColumn;
    @FXML private TableColumn<SObjectItem, String> allLabelColumn;
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
    
    @FXML private RadioButton csvRadioButton;
    @FXML private RadioButton databaseRadioButton;
    @FXML private ToggleGroup backupTargetGroup;
    @FXML private Button databaseSettingsButton;
    @FXML private VBox outputFolderBox;
    
    @FXML private TextField outputFolderField;
    @FXML private Button browseButton;
    @FXML private Button selectAllButton;
    @FXML private Button deselectAllButton;
    @FXML private Button startBackupButton;
    @FXML private Button stopBackupButton;
    @FXML private Button exportResultsButton;
    
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Label progressPercentLabel;
    @FXML private TextArea logArea;
    
    @FXML private Label connectionLabel;
    @FXML private TextField searchField;
    @FXML private Label selectionCountLabel;
    @FXML private Button logoutButton;

    
    private LoginController.ConnectionInfo connectionInfo;
    private DatabaseSettingsController.DatabaseConnectionInfo databaseConnectionInfo;
    private ObservableList<SObjectItem> allObjects;
    private FilteredList<SObjectItem> filteredObjects;
    private FilteredList<SObjectItem> inProgressObjects;
    private FilteredList<SObjectItem> completedObjects;
    private FilteredList<SObjectItem> errorObjects;
    private BackupTask currentBackupTask;
    private Thread backupThread;

    @FXML
    public void initialize() {
        setupAllObjectsTable();
        setupAllStatusTable();
        setupInProgressTable();
        setupCompletedTable();
        setupErrorsTable();
        
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
            } else {
                databaseSettingsButton.setDisable(false);
                outputFolderBox.setVisible(false);
                outputFolderBox.setManaged(false);
            }
        });
        
        // Setup search filter
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterObjects(newValue));
        
        stopBackupButton.setDisable(true);
        progressBar.setProgress(0);
        
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

    public void setConnectionInfo(LoginController.ConnectionInfo connInfo) {
        this.connectionInfo = connInfo;
        updateConnectionLabel();
        loadObjects();
    }
    
    private void updateConnectionLabel() {
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
    private void handleDatabaseSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/database-settings.fxml"));
            Parent root = loader.load();
            
            DatabaseSettingsController controller = loader.getController();
            
            Stage dialog = new Stage();
            dialog.setTitle("Database Settings");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(databaseSettingsButton.getScene().getWindow());
            
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/modern-styles.css").toExternalForm());
            dialog.setScene(scene);
            dialog.setResizable(false);
            
            dialog.showAndWait();
            
            if (controller.isSaved()) {
                databaseConnectionInfo = controller.getConnectionInfo();
                logger.info("Database settings saved: {}", databaseConnectionInfo.getDatabaseType());
                
                // Update connection label to show database info
                updateConnectionLabel();
            }
        } catch (IOException e) {
            logger.error("Error opening database settings", e);
            showError("Failed to open database settings: " + e.getMessage());
        }
    }

    private DataSink createDataSinkFromConfig(DatabaseConnectionInfo config) {
        Map<String, String> fields = config.getFields();
        logger.info("Creating DataSink from config. Database type: {}, Fields: {}", config.getDatabaseType(), fields.keySet());
        logger.info("Recreate tables option: {}", config.isRecreateTables());
        
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
                
                // For SSO, password can be null
                sink = DataSinkFactory.createSnowflakeSink(account, warehouse, database, schema, username, password);
                break;
            case "SQL Server":
                sink = DataSinkFactory.createSqlServerSink(
                    fields.get("Server"), 
                    fields.get("Database"),
                    fields.get("Username"), 
                    fields.get("Password")
                );
                break;
            case "PostgreSQL":
                sink = DataSinkFactory.createPostgresSink(
                    fields.get("Host"), 
                    Integer.parseInt(fields.getOrDefault("Port", "5432")),
                    fields.get("Database"), 
                    fields.get("Schema"),
                    fields.get("Username"), 
                    fields.get("Password")
                );
                break;
            default:
                throw new IllegalArgumentException("Unsupported database type: " + config.getDatabaseType());
        }
        
        // Set recreate tables option
        sink.setRecreateTables(config.isRecreateTables());
        return sink;
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

    @FXML
    private void handleStartBackup() {
        List<SObjectItem> selectedObjects = allObjects.stream()
            .filter(SObjectItem::isSelected)
            .filter(item -> !item.isDisabled())
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
            if (databaseConnectionInfo == null) {
                showError("Please configure database settings first");
                return;
            }
            
            dataSink = createDataSinkFromConfig(databaseConnectionInfo);
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
        
        // Start backup
        currentBackupTask = new BackupTask(selectedObjects, outputFolder, displayFolder, dataSink);
        
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
        private final String displayFolder;
        private final DataSink dataSink;
        private volatile boolean cancelled = false;
        private ExecutorService executor;

        public BackupTask(List<SObjectItem> objects, String outputFolder, String displayFolder, DataSink dataSink) {
            this.objects = objects;
            this.outputFolder = outputFolder;
            this.displayFolder = displayFolder;
            this.dataSink = dataSink;
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
                        
                        long objectStart = System.currentTimeMillis();
                        
                        // Check if doing incremental backup
                        String whereClause = null;
                        if (dataSink != null && !dataSink.getType().equals("CSV")) {
                            // Check if table exists and get last backup timestamp
                            try {
                                com.backupforce.sink.JdbcDatabaseSink jdbcSink = (com.backupforce.sink.JdbcDatabaseSink) dataSink;
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
                        
                        // Step 1: Query object using Bulk API (writes CSV file)
                        bulkClient.queryObject(objectName, outputFolder, whereClause, (status) -> {
                            // Update status in real-time
                            Platform.runLater(() -> item.setStatus(status));
                        });
                        
                        // Step 1.5: Download blobs for objects with blob fields (CSV backup only)
                        if (dataSink == null || dataSink.getType().equals("CSV")) {
                            String blobField = getBlobFieldName(objectName);
                            if (blobField != null) {
                                try {
                                    Platform.runLater(() -> item.setStatus("Downloading blob files..."));
                                    bulkClient.downloadBlobs(objectName, outputFolder, blobField, (status) -> {
                                        Platform.runLater(() -> item.setStatus(status));
                                    });
                                } catch (Exception blobEx) {
                                    logMessage(String.format("[%s] WARNING: Failed to download blobs: %s", 
                                        objectName, blobEx.getMessage()));
                                    logger.warn("Failed to download blobs for {}", objectName, blobEx);
                                }
                            }
                        }
                        
                        // Step 2: If using database sink, write to database
                        if (dataSink != null && !dataSink.getType().equals("CSV")) {
                            Platform.runLater(() -> item.setStatus("Writing to database..."));
                            
                            File csvFile = new File(outputFolder, objectName + ".csv");
                            if (csvFile.exists()) {
                                try (java.io.FileReader reader = new java.io.FileReader(csvFile)) {
                                    // For now, we skip prepareSink since we don't have field metadata
                                    // The JdbcDatabaseSink will auto-create tables from CSV headers
                                    String backupId = String.valueOf(System.currentTimeMillis());
                                    
                                    int recordsWritten = dataSink.writeData(objectName, reader, backupId, (status) -> {
                                        Platform.runLater(() -> item.setStatus(status));
                                    });
                                    
                                    logMessage(String.format("[%s] Wrote %d records to %s", 
                                        objectName, recordsWritten, dataSink.getDisplayName()));
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
                        final String formattedSize = formatFileSize(fileSize);
                        final String formattedDuration = formatDuration(objectTime);
                        
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
                    } catch (Exception e) {
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        
                        // Check if object is unsupported (not a real failure)
                        if (errorMsg.contains("not supported by the Bulk API") || 
                            errorMsg.contains("INVALIDENTITY")) {
                            // Don't count as failed - it's just not supported
                            Platform.runLater(() -> {
                                item.setStatus("⊘ Not Supported");
                                item.setErrorMessage("Object not supported by Bulk API");
                            });
                            logMessage("⊘ SKIPPED: " + objectName + " - Not supported by Bulk API");
                        } else {
                            // Actual failure
                            failed.incrementAndGet();
                            
                            // Extract meaningful error message
                            String cleanError = errorMsg;
                            if (errorMsg.contains("Failed to create query job:")) {
                                cleanError = errorMsg.substring(errorMsg.indexOf(":") + 1).trim();
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
                            progressLabel.setText(String.format("Progress: %d/%d (%d successful, %d failed) - %.1f%%",
                                completedCount, totalObjects, successful.get(), failed.get(), progress * 100));
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
            
            long totalTime = System.currentTimeMillis() - startTime;
            
            Platform.runLater(() -> {
                logMessage("=".repeat(60));
                logMessage("Backup completed!");
                logMessage("Total objects: " + totalObjects);
                logMessage("Successful: " + successful.get());
                logMessage("Failed: " + failed.get());
                logMessage("Total time: " + totalTime / 1000 + " seconds");
                logMessage("Output: " + displayFolder);
                logMessage("=".repeat(60));
                
                // Reset UI for next backup
                resetUIForNewBackup();
                
                if (!cancelled) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Backup Complete");
                    alert.setHeaderText("Backup Finished Successfully");
                    alert.setContentText(String.format(
                        "Backed up %d objects in %d seconds\n\nOutput: %s",
                        successful.get(), totalTime / 1000, displayFolder
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
    
    // Utility methods
    private static String getBlobFieldName(String objectName) {
        // Map of objects that have blob fields and their blob field names
        switch (objectName) {
            case "Document":
            case "Attachment":
                return "Body";
            case "ContentVersion":
                return "VersionData";
            case "StaticResource":
                return "Body";
            case "EmailMessage":
                return null; // Has TextBody and HtmlBody but not blobs
            default:
                return null;
        }
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
    }
    
    @FXML
    private void handleLogout() {
        logger.info("User initiated logout");
        
        // Stop any running backup
        if (currentBackupTask != null && !currentBackupTask.isDone()) {
            handleStopBackup();
        }
        
        // Close current window
        Stage stage = (Stage) logoutButton.getScene().getWindow();
        stage.close();
        
        // Open login window
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            
            Stage loginStage = new Stage();
            loginStage.setTitle("BackupForce - Salesforce Backup Tool");
            loginStage.setScene(new Scene(root, 600, 750));
            loginStage.setResizable(true);
            loginStage.setMinWidth(500);
            loginStage.setMinHeight(650);
            loginStage.show();
            
            logger.info("Returned to login screen");
        } catch (IOException e) {
            logger.error("Failed to load login screen", e);
            showError("Failed to return to login: " + e.getMessage());
        }
    }
}
