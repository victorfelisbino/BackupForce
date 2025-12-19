package com.backupforce.ui;

import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.ui.LoginController.ConnectionInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class ConnectionsController {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionsController.class);
    
    @FXML private Button backButton;
    @FXML private Label statusLabel;
    @FXML private Label formTitle;
    @FXML private Label testResultLabel;
    @FXML private Label noSavedLabel;
    
    @FXML private VBox formPanel;
    @FXML private VBox savedConnectionsList;
    @FXML private GridPane formGrid;
    @FXML private HBox nameRow;
    @FXML private HBox actionButtons;
    
    @FXML private HBox snowflakeTypeCard;
    @FXML private HBox sqlServerTypeCard;
    @FXML private HBox postgresTypeCard;
    
    @FXML private TextField connectionNameField;
    @FXML private CheckBox ssoCheckBox;
    @FXML private CheckBox recreateTablesCheckBox;
    @FXML private Separator optionsSeparator;
    @FXML private Button deleteButton;
    @FXML private Button testButton;
    @FXML private Button saveButton;
    
    private ConnectionInfo connectionInfo;
    private String selectedType = null;
    private String editingConnectionId = null;
    private boolean returnToBackup = false;
    private Map<String, TextField> fieldMap = new HashMap<>();
    private Map<String, ComboBox<String>> comboMap = new HashMap<>();
    private Connection snowflakeConnection = null;
    private Button connectButton = null;
    
    @FXML
    public void initialize() {
        loadSavedConnections();
        setupTypeCardHovers();
    }
    
    @FXML
    private void handleSsoToggle() {
        // Rebuild form when SSO checkbox is toggled (preserve existing values)
        if (selectedType != null && selectedType.equals("Snowflake")) {
            // Save current account value if exists
            String currentAccount = fieldMap.containsKey("Account") ? fieldMap.get("Account").getText() : "";
            
            buildFormForType("Snowflake", false); // Don't reset fields
            
            // Restore account value
            if (fieldMap.containsKey("Account") && !currentAccount.isEmpty()) {
                fieldMap.get("Account").setText(currentAccount);
            }
        }
    }
    
    public void setConnectionInfo(ConnectionInfo connectionInfo) {
        this.connectionInfo = connectionInfo;
    }
    
    public void setReturnToBackup(boolean returnToBackup) {
        this.returnToBackup = returnToBackup;
    }
    
    private void setupTypeCardHovers() {
        setupCardHover(snowflakeTypeCard);
        setupCardHover(sqlServerTypeCard);
        setupCardHover(postgresTypeCard);
    }
    
    private void setupCardHover(HBox card) {
        card.setOnMouseEntered(e -> {
            if (!card.getStyleClass().contains("connection-type-card-selected")) {
                card.setStyle("-fx-background-color: #333;");
            }
        });
        card.setOnMouseExited(e -> {
            if (!card.getStyleClass().contains("connection-type-card-selected")) {
                card.setStyle("");
            }
        });
    }
    
    private void loadSavedConnections() {
        savedConnectionsList.getChildren().clear();
        
        List<SavedConnection> connections = ConnectionManager.getInstance().getConnections();
        
        if (connections.isEmpty()) {
            savedConnectionsList.getChildren().add(noSavedLabel);
            noSavedLabel.setVisible(true);
            noSavedLabel.setManaged(true);
        } else {
            noSavedLabel.setVisible(false);
            noSavedLabel.setManaged(false);
            
            for (SavedConnection conn : connections) {
                HBox card = createSavedConnectionCard(conn);
                savedConnectionsList.getChildren().add(card);
            }
        }
    }
    
    private HBox createSavedConnectionCard(SavedConnection conn) {
        HBox card = new HBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("saved-connection-card");
        
        // Icon based on type
        Label icon = new Label(getIconForType(conn.getType()));
        icon.getStyleClass().add("saved-conn-icon");
        
        VBox info = new VBox(1);
        Label name = new Label(conn.getName());
        name.getStyleClass().add("saved-conn-name");
        Label type = new Label(conn.getType());
        type.getStyleClass().add("saved-conn-type");
        info.getChildren().addAll(name, type);
        
        card.getChildren().addAll(icon, info);
        
        card.setOnMouseClicked(e -> editConnection(conn));
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #383838;"));
        card.setOnMouseExited(e -> card.setStyle(""));
        
        return card;
    }
    
    private String getIconForType(String type) {
        switch (type) {
            case "Snowflake": return "❄";
            case "SQL Server": return "S";
            case "PostgreSQL": return "P";
            default: return "●";
        }
    }
    
    private void clearTypeSelection() {
        snowflakeTypeCard.getStyleClass().remove("connection-type-card-selected");
        sqlServerTypeCard.getStyleClass().remove("connection-type-card-selected");
        postgresTypeCard.getStyleClass().remove("connection-type-card-selected");
        snowflakeTypeCard.setStyle("");
        sqlServerTypeCard.setStyle("");
        postgresTypeCard.setStyle("");
    }
    
    @FXML
    private void selectSnowflake() {
        clearTypeSelection();
        snowflakeTypeCard.getStyleClass().add("connection-type-card-selected");
        selectedType = "Snowflake";
        editingConnectionId = null;
        buildFormForType("Snowflake");
        showFormElements(true);
        deleteButton.setVisible(false);
        deleteButton.setManaged(false);
    }
    
    @FXML
    private void selectSqlServer() {
        clearTypeSelection();
        sqlServerTypeCard.getStyleClass().add("connection-type-card-selected");
        selectedType = "SQL Server";
        editingConnectionId = null;
        buildFormForType("SQL Server");
        showFormElements(true);
        deleteButton.setVisible(false);
        deleteButton.setManaged(false);
    }
    
    @FXML
    private void selectPostgres() {
        clearTypeSelection();
        postgresTypeCard.getStyleClass().add("connection-type-card-selected");
        selectedType = "PostgreSQL";
        editingConnectionId = null;
        buildFormForType("PostgreSQL");
        showFormElements(true);
        deleteButton.setVisible(false);
        deleteButton.setManaged(false);
    }
    
    private void editConnection(SavedConnection conn) {
        clearTypeSelection();
        selectedType = conn.getType();
        editingConnectionId = conn.getId();
        
        // Highlight the correct type
        switch (conn.getType()) {
            case "Snowflake":
                snowflakeTypeCard.getStyleClass().add("connection-type-card-selected");
                // Restore SSO state before building form
                ssoCheckBox.setSelected(conn.isUseSso());
                break;
            case "SQL Server":
                sqlServerTypeCard.getStyleClass().add("connection-type-card-selected");
                break;
            case "PostgreSQL":
                postgresTypeCard.getStyleClass().add("connection-type-card-selected");
                break;
        }
        
        showFormElements(true);
        buildFormForType(conn.getType(), false); // Don't reset SSO checkbox
        
        // Populate fields
        connectionNameField.setText(conn.getName());
        Map<String, String> props = conn.getProperties();
        
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (fieldMap.containsKey(entry.getKey())) {
                fieldMap.get(entry.getKey()).setText(entry.getValue());
            }
            // Also populate combo boxes (for Snowflake warehouse/database/schema)
            if (comboMap.containsKey(entry.getKey())) {
                comboMap.get(entry.getKey()).setValue(entry.getValue());
            }
        }
        
        // Show delete button when editing
        deleteButton.setVisible(true);
        deleteButton.setManaged(true);
        
        formTitle.setText("Edit " + conn.getName());
    }
    
    private void showFormElements(boolean show) {
        nameRow.setVisible(show);
        nameRow.setManaged(show);
        actionButtons.setVisible(show);
        actionButtons.setManaged(show);
        optionsSeparator.setVisible(show);
        optionsSeparator.setManaged(show);
        recreateTablesCheckBox.setVisible(show);
        recreateTablesCheckBox.setManaged(show);
        
        // SSO only for Snowflake
        boolean isSnowflake = "Snowflake".equals(selectedType);
        ssoCheckBox.setVisible(show && isSnowflake);
        ssoCheckBox.setManaged(show && isSnowflake);
        
        if (!show) {
            formTitle.setText("Select a connection type");
        }
    }
    
    private void buildFormForType(String type) {
        buildFormForType(type, true);
    }
    
    private void buildFormForType(String type, boolean resetFields) {
        formGrid.getChildren().clear();
        fieldMap.clear();
        comboMap.clear();
        testResultLabel.setText("");
        
        // Clear local reference when switching types - session stays cached
        clearLocalSnowflakeConnection();
        
        if (resetFields) {
            connectionNameField.clear();
            ssoCheckBox.setSelected(false);
        }
        
        formTitle.setText(editingConnectionId != null ? "Edit Connection" : "New " + type + " Connection");
        
        if (type.equals("Snowflake")) {
            buildSnowflakeForm(resetFields);
        } else {
            buildStandardForm(type, resetFields);
        }
    }
    
    private void buildSnowflakeForm(boolean resetFields) {
        int row = 0;
        
        // Account field (always a TextField)
        Label accountLabel = new Label("Account:");
        accountLabel.getStyleClass().add("field-label-compact");
        accountLabel.setMinWidth(120);
        GridPane.setColumnIndex(accountLabel, 0);
        GridPane.setRowIndex(accountLabel, row);
        
        TextField accountField = new TextField();
        accountField.setPromptText("e.g., xy12345.us-east-1");
        accountField.getStyleClass().add("text-field-compact");
        GridPane.setColumnIndex(accountField, 1);
        GridPane.setRowIndex(accountField, row);
        formGrid.getChildren().addAll(accountLabel, accountField);
        fieldMap.put("Account", accountField);
        row++;
        
        // Username (only if not SSO)
        if (!ssoCheckBox.isSelected()) {
            Label usernameLabel = new Label("Username:");
            usernameLabel.getStyleClass().add("field-label-compact");
            usernameLabel.setMinWidth(120);
            GridPane.setColumnIndex(usernameLabel, 0);
            GridPane.setRowIndex(usernameLabel, row);
            
            TextField usernameField = new TextField();
            usernameField.setPromptText("Your Snowflake username");
            usernameField.getStyleClass().add("text-field-compact");
            GridPane.setColumnIndex(usernameField, 1);
            GridPane.setRowIndex(usernameField, row);
            formGrid.getChildren().addAll(usernameLabel, usernameField);
            fieldMap.put("Username", usernameField);
            row++;
            
            // Password
            Label passwordLabel = new Label("Password:");
            passwordLabel.getStyleClass().add("field-label-compact");
            passwordLabel.setMinWidth(120);
            GridPane.setColumnIndex(passwordLabel, 0);
            GridPane.setRowIndex(passwordLabel, row);
            
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("Enter password");
            passwordField.getStyleClass().add("text-field-compact");
            GridPane.setColumnIndex(passwordField, 1);
            GridPane.setRowIndex(passwordField, row);
            formGrid.getChildren().addAll(passwordLabel, passwordField);
            fieldMap.put("Password", passwordField);
            row++;
        }
        
        // Connect button
        connectButton = new Button("Connect to Load Options");
        connectButton.getStyleClass().add("secondary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setOnAction(e -> handleSnowflakeConnect());
        GridPane.setColumnIndex(connectButton, 0);
        GridPane.setColumnSpan(connectButton, 2);
        GridPane.setRowIndex(connectButton, row);
        formGrid.getChildren().add(connectButton);
        row++;
        
        // Warehouse dropdown
        Label warehouseLabel = new Label("Warehouse:");
        warehouseLabel.getStyleClass().add("field-label-compact");
        warehouseLabel.setMinWidth(120);
        GridPane.setColumnIndex(warehouseLabel, 0);
        GridPane.setRowIndex(warehouseLabel, row);
        
        ComboBox<String> warehouseCombo = new ComboBox<>();
        warehouseCombo.setPromptText("Connect to load warehouses");
        warehouseCombo.setMaxWidth(Double.MAX_VALUE);
        warehouseCombo.getStyleClass().add("text-field-compact");
        warehouseCombo.setEditable(true);
        GridPane.setColumnIndex(warehouseCombo, 1);
        GridPane.setRowIndex(warehouseCombo, row);
        formGrid.getChildren().addAll(warehouseLabel, warehouseCombo);
        comboMap.put("Warehouse", warehouseCombo);
        row++;
        
        // Database dropdown
        Label databaseLabel = new Label("Database:");
        databaseLabel.getStyleClass().add("field-label-compact");
        databaseLabel.setMinWidth(120);
        GridPane.setColumnIndex(databaseLabel, 0);
        GridPane.setRowIndex(databaseLabel, row);
        
        ComboBox<String> databaseCombo = new ComboBox<>();
        databaseCombo.setPromptText("Connect to load databases");
        databaseCombo.setMaxWidth(Double.MAX_VALUE);
        databaseCombo.getStyleClass().add("text-field-compact");
        databaseCombo.setEditable(true);
        // When database changes, reload schemas
        databaseCombo.setOnAction(e -> {
            String selectedDb = databaseCombo.getValue();
            if (selectedDb != null && !selectedDb.isEmpty() && snowflakeConnection != null) {
                loadSnowflakeSchemas(selectedDb);
            }
        });
        GridPane.setColumnIndex(databaseCombo, 1);
        GridPane.setRowIndex(databaseCombo, row);
        formGrid.getChildren().addAll(databaseLabel, databaseCombo);
        comboMap.put("Database", databaseCombo);
        row++;
        
        // Schema dropdown
        Label schemaLabel = new Label("Schema:");
        schemaLabel.getStyleClass().add("field-label-compact");
        schemaLabel.setMinWidth(120);
        GridPane.setColumnIndex(schemaLabel, 0);
        GridPane.setRowIndex(schemaLabel, row);
        
        ComboBox<String> schemaCombo = new ComboBox<>();
        schemaCombo.setPromptText("Select database first");
        schemaCombo.setMaxWidth(Double.MAX_VALUE);
        schemaCombo.getStyleClass().add("text-field-compact");
        schemaCombo.setEditable(true);
        GridPane.setColumnIndex(schemaCombo, 1);
        GridPane.setRowIndex(schemaCombo, row);
        formGrid.getChildren().addAll(schemaLabel, schemaCombo);
        comboMap.put("Schema", schemaCombo);
        
        // Check for cached session and auto-connect if available
        Platform.runLater(this::checkCachedSessionAndAutoConnect);
    }
    
    /**
     * Check if there's a cached Snowflake session and auto-connect if available
     */
    private void checkCachedSessionAndAutoConnect() {
        String account = getFieldValue("Account");
        if (account.isEmpty()) {
            return;
        }
        
        String cacheKey = editingConnectionId != null ? editingConnectionId : "temp_" + account;
        ConnectionManager.CachedSession cachedSession = ConnectionManager.getInstance().getCachedSession(cacheKey);
        
        if (cachedSession != null) {
            snowflakeConnection = cachedSession.getConnection();
            logger.info("Auto-connecting with cached Snowflake session for: {}", cacheKey);
            
            connectButton.setText("✓ Connected (cached)");
            connectButton.setDisable(true);
            testResultLabel.setText("Using cached session - Loading options...");
            testResultLabel.setStyle("-fx-text-fill: #4ec94e;");
            
            // Load warehouses and databases
            loadSnowflakeOptions();
        }
    }
    
    private void buildStandardForm(String type, boolean resetFields) {
        List<String> fields;
        switch (type) {
            case "SQL Server":
                fields = Arrays.asList("Server", "Database", "Username", "Password");
                break;
            case "PostgreSQL":
                fields = Arrays.asList("Host", "Port", "Database", "Schema", "Username", "Password");
                break;
            default:
                return;
        }
        
        int row = 0;
        for (String fieldName : fields) {
            Label label = new Label(fieldName + ":");
            label.getStyleClass().add("field-label-compact");
            label.setMinWidth(120);
            GridPane.setColumnIndex(label, 0);
            GridPane.setRowIndex(label, row);
            
            TextField textField = new TextField();
            textField.setPromptText(getPromptText(type, fieldName));
            textField.getStyleClass().add("text-field-compact");
            
            if (fieldName.equals("Password")) {
                PasswordField passField = new PasswordField();
                passField.setPromptText("Enter password");
                passField.getStyleClass().add("text-field-compact");
                GridPane.setColumnIndex(passField, 1);
                GridPane.setRowIndex(passField, row);
                formGrid.getChildren().addAll(label, passField);
                fieldMap.put(fieldName, passField);
            } else {
                GridPane.setColumnIndex(textField, 1);
                GridPane.setRowIndex(textField, row);
                formGrid.getChildren().addAll(label, textField);
                fieldMap.put(fieldName, textField);
            }
            
            row++;
        }
    }
    
    private void handleSnowflakeConnect() {
        String account = getFieldValue("Account");
        if (account.isEmpty()) {
            testResultLabel.setText("Please enter your Snowflake account");
            testResultLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }
        
        // Check for cached session first (using account as cache key for new connections)
        String cacheKey = editingConnectionId != null ? editingConnectionId : "temp_" + account;
        ConnectionManager.CachedSession cachedSession = ConnectionManager.getInstance().getCachedSession(cacheKey);
        
        if (cachedSession != null) {
            // Use cached connection
            snowflakeConnection = cachedSession.getConnection();
            logger.info("Using cached Snowflake session for: {}", account);
            Platform.runLater(() -> {
                connectButton.setText("✓ Connected (cached)");
                connectButton.setDisable(true);
                testResultLabel.setText("Using cached session - Loading options...");
                testResultLabel.setStyle("-fx-text-fill: #4ec94e;");
                loadSnowflakeOptions();
            });
            return;
        }
        
        connectButton.setDisable(true);
        connectButton.setText("Connecting...");
        testResultLabel.setText("Connecting to Snowflake...");
        testResultLabel.setStyle("-fx-text-fill: #adb5bd;");
        
        Task<Connection> connectTask = new Task<>() {
            @Override
            protected Connection call() throws Exception {
                String jdbcUrl;
                String username = "";
                String password = "";
                
                if (ssoCheckBox.isSelected()) {
                    jdbcUrl = String.format("jdbc:snowflake://%s.snowflakecomputing.com/?authenticator=externalbrowser", account);
                } else {
                    username = getFieldValue("Username");
                    password = getFieldValue("Password");
                    if (username.isEmpty() || password.isEmpty()) {
                        throw new Exception("Username and password required");
                    }
                    jdbcUrl = String.format("jdbc:snowflake://%s.snowflakecomputing.com/", account);
                }
                
                return DriverManager.getConnection(jdbcUrl, username, password);
            }
        };
        
        connectTask.setOnSucceeded(e -> {
            snowflakeConnection = connectTask.getValue();
            
            // Cache the session for reuse (use final copy of account from outer scope)
            String connCacheKey = editingConnectionId != null ? editingConnectionId : "temp_" + account;
            ConnectionManager.getInstance().cacheSession(connCacheKey, snowflakeConnection);
            logger.info("Cached Snowflake session for: {}", connCacheKey);
            
            Platform.runLater(() -> {
                connectButton.setText("✓ Connected");
                connectButton.setDisable(true);
                testResultLabel.setText("Loading warehouses and databases...");
                testResultLabel.setStyle("-fx-text-fill: #4ec94e;");
                
                // Load warehouses and databases
                loadSnowflakeOptions();
            });
        });
        
        connectTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                connectButton.setDisable(false);
                connectButton.setText("Connect to Load Options");
                String error = connectTask.getException() != null ? connectTask.getException().getMessage() : "Connection failed";
                testResultLabel.setText("✗ " + error);
                testResultLabel.setStyle("-fx-text-fill: #e74c3c;");
            });
        });
        
        Thread thread = new Thread(connectTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    private void loadSnowflakeOptions() {
        Task<Map<String, List<String>>> loadTask = new Task<>() {
            @Override
            protected Map<String, List<String>> call() throws Exception {
                Map<String, List<String>> options = new HashMap<>();
                
                // Load warehouses
                List<String> warehouses = new ArrayList<>();
                try (Statement stmt = snowflakeConnection.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW WAREHOUSES")) {
                    while (rs.next()) {
                        warehouses.add(rs.getString("name"));
                    }
                }
                options.put("warehouses", warehouses);
                
                // Load databases
                List<String> databases = new ArrayList<>();
                try (Statement stmt = snowflakeConnection.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
                    while (rs.next()) {
                        databases.add(rs.getString("name"));
                    }
                }
                options.put("databases", databases);
                
                return options;
            }
        };
        
        loadTask.setOnSucceeded(e -> {
            Map<String, List<String>> options = loadTask.getValue();
            Platform.runLater(() -> {
                ComboBox<String> warehouseCombo = comboMap.get("Warehouse");
                ComboBox<String> databaseCombo = comboMap.get("Database");
                
                if (warehouseCombo != null) {
                    warehouseCombo.setItems(FXCollections.observableArrayList(options.get("warehouses")));
                    warehouseCombo.setPromptText("Select warehouse");
                }
                
                if (databaseCombo != null) {
                    databaseCombo.setItems(FXCollections.observableArrayList(options.get("databases")));
                    databaseCombo.setPromptText("Select database");
                }
                
                testResultLabel.setText("✓ Options loaded. Select warehouse, database, and schema.");
                testResultLabel.setStyle("-fx-text-fill: #4ec94e;");
            });
        });
        
        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                String error = loadTask.getException() != null ? loadTask.getException().getMessage() : "Failed to load options";
                testResultLabel.setText("✗ " + error);
                testResultLabel.setStyle("-fx-text-fill: #e74c3c;");
            });
        });
        
        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    private void loadSnowflakeSchemas(String database) {
        ComboBox<String> schemaCombo = comboMap.get("Schema");
        if (schemaCombo == null) return;
        
        schemaCombo.setPromptText("Loading schemas...");
        schemaCombo.getItems().clear();
        
        Task<List<String>> loadTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> schemas = new ArrayList<>();
                try (Statement stmt = snowflakeConnection.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW SCHEMAS IN DATABASE \"" + database + "\"")) {
                    while (rs.next()) {
                        schemas.add(rs.getString("name"));
                    }
                }
                return schemas;
            }
        };
        
        loadTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                schemaCombo.setItems(FXCollections.observableArrayList(loadTask.getValue()));
                schemaCombo.setPromptText("Select schema");
            });
        });
        
        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                schemaCombo.setPromptText("Failed to load schemas");
            });
        });
        
        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    /**
     * Clear the local reference to the Snowflake connection.
     * The actual connection is kept in the session cache for reuse.
     */
    private void clearLocalSnowflakeConnection() {
        // Just clear the local reference - the connection is cached in ConnectionManager
        snowflakeConnection = null;
    }
    
    /**
     * Invalidate and close a cached Snowflake session
     */
    private void invalidateSnowflakeSession(String connectionId) {
        if (connectionId != null) {
            ConnectionManager.getInstance().invalidateSession(connectionId);
        }
        snowflakeConnection = null;
    }
    
    private String getPromptText(String dbType, String field) {
        if (dbType.equals("Snowflake")) {
            switch (field) {
                case "Account": return "e.g., xy12345.us-east-1";
                case "Warehouse": return "e.g., COMPUTE_WH";
                case "Database": return "e.g., SALESFORCE_BACKUP";
                case "Schema": return "e.g., PUBLIC";
                case "Username": return "Your Snowflake username";
            }
        } else if (dbType.equals("SQL Server")) {
            switch (field) {
                case "Server": return "e.g., localhost\\SQLEXPRESS";
                case "Database": return "e.g., SalesforceBackup";
                case "Username": return "SQL Server username";
            }
        } else if (dbType.equals("PostgreSQL")) {
            switch (field) {
                case "Host": return "e.g., localhost";
                case "Port": return "5432";
                case "Database": return "e.g., salesforce_backup";
                case "Schema": return "e.g., public";
                case "Username": return "PostgreSQL username";
            }
        }
        return "";
    }
    
    @FXML
    private void handleTestConnection() {
        if (selectedType == null) return;
        
        testResultLabel.setText("Testing connection...");
        testResultLabel.setStyle("-fx-text-fill: #888;");
        testButton.setDisable(true);
        
        Task<Boolean> testTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                String jdbcUrl = buildJdbcUrl();
                String username = fieldMap.containsKey("Username") ? fieldMap.get("Username").getText() : "";
                String password = fieldMap.containsKey("Password") ? fieldMap.get("Password").getText() : "";
                
                try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                    return conn.isValid(5);
                }
            }
        };
        
        testTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                testButton.setDisable(false);
                if (testTask.getValue()) {
                    testResultLabel.setText("✓ Connection successful!");
                    testResultLabel.setStyle("-fx-text-fill: #4ec94e;");
                } else {
                    testResultLabel.setText("✗ Connection failed");
                    testResultLabel.setStyle("-fx-text-fill: #e74c3c;");
                }
            });
        });
        
        testTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                testButton.setDisable(false);
                String error = testTask.getException() != null ? testTask.getException().getMessage() : "Unknown error";
                testResultLabel.setText("✗ " + error);
                testResultLabel.setStyle("-fx-text-fill: #e74c3c;");
            });
        });
        
        Thread thread = new Thread(testTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    private String buildJdbcUrl() {
        switch (selectedType) {
            case "Snowflake":
                String account = getFieldValue("Account");
                String warehouse = getComboValue("Warehouse");
                String database = getComboValue("Database");
                String schema = getComboValue("Schema");
                if (ssoCheckBox.isSelected()) {
                    // SSO mode - use external browser authentication
                    return String.format("jdbc:snowflake://%s.snowflakecomputing.com/?warehouse=%s&db=%s&schema=%s&authenticator=externalbrowser",
                        account, warehouse, database, schema);
                } else {
                    // Standard authentication with all fields
                    return String.format("jdbc:snowflake://%s.snowflakecomputing.com/?warehouse=%s&db=%s&schema=%s",
                        account, warehouse, database, schema);
                }
                    
            case "SQL Server":
                String server = getFieldValue("Server");
                String db = getFieldValue("Database");
                return String.format("jdbc:sqlserver://%s;databaseName=%s;encrypt=true;trustServerCertificate=true",
                    server, db);
                    
            case "PostgreSQL":
                String host = getFieldValue("Host");
                String port = getFieldValue("Port");
                String pgDb = getFieldValue("Database");
                return String.format("jdbc:postgresql://%s:%s/%s", host, port, pgDb);
                
            default:
                return "";
        }
    }
    
    private String getFieldValue(String fieldName) {
        TextField field = fieldMap.get(fieldName);
        return field != null ? field.getText() : "";
    }
    
    private String getComboValue(String comboName) {
        ComboBox<String> combo = comboMap.get(comboName);
        if (combo != null) {
            String value = combo.getValue();
            // Also check editor for editable comboboxes
            if ((value == null || value.isEmpty()) && combo.isEditable() && combo.getEditor() != null) {
                value = combo.getEditor().getText();
            }
            return value != null ? value : "";
        }
        return "";
    }
    
    @FXML
    private void handleSave() {
        String name = connectionNameField.getText().trim();
        if (name.isEmpty()) {
            testResultLabel.setText("Please enter a connection name");
            testResultLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }
        
        // Validate required fields
        for (Map.Entry<String, TextField> entry : fieldMap.entrySet()) {
            if (entry.getValue().getText().trim().isEmpty() && !entry.getKey().equals("Password")) {
                testResultLabel.setText("Please fill in " + entry.getKey());
                testResultLabel.setStyle("-fx-text-fill: #e74c3c;");
                return;
            }
        }
        
        // Validate combo boxes for Snowflake
        if ("Snowflake".equals(selectedType)) {
            for (String comboName : Arrays.asList("Warehouse", "Database", "Schema")) {
                String value = getComboValue(comboName);
                if (value.isEmpty()) {
                    testResultLabel.setText("Please select " + comboName);
                    testResultLabel.setStyle("-fx-text-fill: #e74c3c;");
                    return;
                }
            }
        }
        
        // Build properties map
        Map<String, String> props = new HashMap<>();
        for (Map.Entry<String, TextField> entry : fieldMap.entrySet()) {
            props.put(entry.getKey(), entry.getValue().getText());
        }
        
        // Add combo values
        for (Map.Entry<String, ComboBox<String>> entry : comboMap.entrySet()) {
            props.put(entry.getKey(), getComboValue(entry.getKey()));
        }
        
        props.put("recreateTables", String.valueOf(recreateTablesCheckBox.isSelected()));
        props.put("useSSO", String.valueOf(ssoCheckBox.isSelected()));
        
        ConnectionManager manager = ConnectionManager.getInstance();
        
        if (editingConnectionId != null) {
            // Update existing
            manager.updateConnection(editingConnectionId, name, selectedType, props);
            logger.info("Updated connection: {}", name);
        } else {
            // Create new
            manager.saveConnection(name, selectedType, props);
            logger.info("Created new connection: {}", name);
        }
        
        testResultLabel.setText("✓ Connection saved!");
        testResultLabel.setStyle("-fx-text-fill: #4ec94e;");
        
        // Refresh the saved connections list
        loadSavedConnections();
        
        // Clear form after save
        Platform.runLater(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                clearTypeSelection();
                showFormElements(false);
                formGrid.getChildren().clear();
                fieldMap.clear();
                editingConnectionId = null;
            });
        });
    }
    
    @FXML
    private void handleDelete() {
        if (editingConnectionId == null) return;
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Connection");
        confirm.setHeaderText("Delete this connection?");
        confirm.setContentText("This action cannot be undone.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                ConnectionManager.getInstance().deleteConnection(editingConnectionId);
                logger.info("Deleted connection: {}", editingConnectionId);
                
                // Invalidate the cached session for this deleted connection
                invalidateSnowflakeSession(editingConnectionId);
                loadSavedConnections();
                clearTypeSelection();
                showFormElements(false);
                formGrid.getChildren().clear();
                fieldMap.clear();
                comboMap.clear();
                editingConnectionId = null;
            }
        });
    }
    
    @FXML
    private void handleBack() {
        // Just clear local reference - session stays cached for next time
        clearLocalSnowflakeConnection();
        try {
            if (returnToBackup) {
                // Return to backup screen
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/backup.fxml"));
                Parent root = loader.load();
                
                BackupController controller = loader.getController();
                controller.setConnectionInfo(connectionInfo);
                
                Scene scene = new Scene(root, 1100, 750);
                Stage stage = (Stage) backButton.getScene().getWindow();
                stage.setScene(scene);
                stage.setResizable(true);
                stage.setMinWidth(950);
                stage.setMinHeight(650);
                stage.setMaximized(true);
            } else {
                // Return to home screen
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/home.fxml"));
                Parent root = loader.load();
                
                HomeController controller = loader.getController();
                controller.setConnectionInfo(connectionInfo);
                
                Scene scene = new Scene(root, 1100, 750);
                Stage stage = (Stage) backButton.getScene().getWindow();
                stage.setScene(scene);
                stage.setResizable(true);
                stage.setMinWidth(950);
                stage.setMinHeight(650);
                stage.setMaximized(true);
            }
        } catch (Exception e) {
            logger.error("Failed to navigate back", e);
        }
    }
}
