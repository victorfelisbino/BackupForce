package com.backupforce.ui;

import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.sink.DataSink;
import com.backupforce.sink.DataSinkFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Controller for the Connections content panel.
 * Manages database connections for backup destinations.
 */
public class ConnectionsContentController {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionsContentController.class);
    
    @FXML private TableView<SavedConnection> connectionsTable;
    @FXML private TableColumn<SavedConnection, String> nameColumn;
    @FXML private TableColumn<SavedConnection, String> typeColumn;
    @FXML private TableColumn<SavedConnection, String> databaseColumn;
    @FXML private TableColumn<SavedConnection, String> schemaColumn;
    @FXML private TableColumn<SavedConnection, String> usernameColumn;
    @FXML private TableColumn<SavedConnection, String> statusColumn;
    @FXML private TableColumn<SavedConnection, Void> actionsColumn;
    @FXML private Button addConnectionButton;
    
    private MainController mainController;
    private ObservableList<SavedConnection> connections;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    @FXML
    public void initialize() {
        setupTableColumns();
        connections = FXCollections.observableArrayList();
        connectionsTable.setItems(connections);
    }
    
    private void setupTableColumns() {
        nameColumn.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getName()));
        
        typeColumn.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getType()));
        
        databaseColumn.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getDatabase()));
        
        schemaColumn.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getSchema()));
        
        usernameColumn.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getUsername()));
        
        statusColumn.setCellValueFactory(data -> 
            new SimpleStringProperty("Ready"));
        
        // Actions column with Edit/Delete buttons
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final Button testBtn = new Button("Test");
            private final HBox buttons = new HBox(6, testBtn, editBtn, deleteBtn);
            
            {
                editBtn.getStyleClass().addAll("btn", "btn-ghost", "btn-sm");
                deleteBtn.getStyleClass().addAll("btn", "btn-ghost", "btn-sm");
                testBtn.getStyleClass().addAll("btn", "btn-ghost", "btn-sm");
                
                editBtn.setOnAction(e -> handleEditConnection(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDeleteConnection(getTableView().getItems().get(getIndex())));
                testBtn.setOnAction(e -> handleTestConnection(getTableView().getItems().get(getIndex())));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });
    }

    /**
     * Refresh connections list
     */
    public void refresh() {
        logger.info("Refreshing connections list");
        connections.clear();
        connections.addAll(ConnectionManager.getInstance().getConnections());
    }
    
    @FXML
    private void handleAddConnection() {
        openDatabaseSettingsDialog(null);
    }
    
    private void handleEditConnection(SavedConnection connection) {
        openDatabaseSettingsDialog(connection);
    }
    
    private void handleDeleteConnection(SavedConnection connection) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Connection");
        confirm.setHeaderText("Delete \"" + connection.getName() + "\"?");
        confirm.setContentText("This action cannot be undone.");
        
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                ConnectionManager.getInstance().deleteConnection(connection.getId());
                refresh();
                logger.info("Deleted connection: {}", connection.getName());
            }
        });
    }
    
    private void handleTestConnection(SavedConnection connection) {
        // Show testing progress
        Alert testing = new Alert(Alert.AlertType.INFORMATION);
        testing.setTitle("Testing Connection");
        testing.setHeaderText("Testing " + connection.getName());
        testing.setContentText("Attempting to connect to " + connection.getType() + " database...");
        testing.show();
        
        // Run test in background thread
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            boolean success = false;
            String message = "";
            
            try {
                // Get decrypted password from connection manager
                ConnectionManager connMgr = ConnectionManager.getInstance();
                String password = connMgr.getDecryptedPassword(connection);
                
                // Try to establish connection by creating a sink via factory
                DataSink sink = createTestSink(connection, password);
                if (sink != null) {
                    success = true;
                    message = "Connection successful";
                } else {
                    message = "Could not create connection - unsupported database type: " + connection.getType();
                }
            } catch (Exception e) {
                message = "Connection failed: " + e.getMessage();
                logger.error("Connection test failed for {}", connection.getName(), e);
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            final String finalMessage = message + "\n\nTime: " + elapsed + "ms";
            final boolean finalSuccess = success;
            
            // Update UI on FX thread
            javafx.application.Platform.runLater(() -> {
                testing.close();
                
                Alert result = new Alert(finalSuccess ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                result.setTitle(finalSuccess ? "Connection Successful" : "Connection Failed");
                result.setHeaderText(connection.getName() + " (" + connection.getType() + ")");
                result.setContentText(finalMessage);
                result.showAndWait();
            });
        }).start();
    }
    
    private DataSink createTestSink(SavedConnection connection, String password) {
        try {
            String type = connection.getType();
            switch (type) {
                case "Snowflake":
                    return DataSinkFactory.createSnowflakeSink(
                        connection.getAccount(), 
                        connection.getWarehouse(),
                        connection.getDatabase(), 
                        connection.getSchema(),
                        connection.getUsername(), 
                        password
                    );
                case "PostgreSQL":
                    int pgPort = 5432;
                    try {
                        if (connection.getPort() != null) pgPort = Integer.parseInt(connection.getPort());
                    } catch (NumberFormatException ignored) {}
                    return DataSinkFactory.createPostgresSink(
                        connection.getHost(), pgPort,
                        connection.getDatabase(), 
                        connection.getSchema(),
                        connection.getUsername(), 
                        password
                    );
                case "SQL Server":
                    String server = connection.getHost();
                    if (connection.getPort() != null && !connection.getPort().isEmpty()) {
                        server += ":" + connection.getPort();
                    }
                    return DataSinkFactory.createSqlServerSink(
                        server,
                        connection.getDatabase(),
                        connection.getUsername(), 
                        password
                    );
                default:
                    logger.warn("Unsupported database type for testing: {}", type);
                    return null;
            }
        } catch (Exception e) {
            logger.error("Failed to create test sink", e);
            return null;
        }
    }
    
    private void openDatabaseSettingsDialog(SavedConnection connectionToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/database-settings.fxml"));
            Parent root = loader.load();
            
            DatabaseSettingsController controller = loader.getController();
            if (connectionToEdit != null) {
                controller.loadConnection(connectionToEdit.getId());
            }
            
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/backupforce-modern.css").toExternalForm());
            
            Stage dialog = new Stage();
            dialog.setTitle(connectionToEdit == null ? "Add Connection" : "Edit Connection");
            dialog.setScene(scene);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setResizable(true);
            dialog.setWidth(600);
            dialog.setHeight(700);
            
            dialog.showAndWait();
            
            // Refresh after dialog closes
            refresh();
            
        } catch (IOException e) {
            logger.error("Failed to open database settings dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText("Failed to open database settings: " + e.getMessage());
            alert.showAndWait();
        }
    }
}
