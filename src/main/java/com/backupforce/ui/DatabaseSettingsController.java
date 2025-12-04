package com.backupforce.ui;

import com.backupforce.sink.DataSink;
import com.backupforce.sink.DataSinkFactory;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.prefs.Preferences;

public class DatabaseSettingsController {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSettingsController.class);
    private static final String PREFS_NODE = "com.backupforce.database";
    
    @FXML private ComboBox<DatabaseType> databaseTypeCombo;
    @FXML private GridPane settingsGrid;
    @FXML private CheckBox rememberCheckBox;
    @FXML private Label statusLabel;
    
    private Map<String, TextField> fieldMap = new HashMap<>();
    private Map<String, ComboBox<String>> comboMap = new HashMap<>();
    private CheckBox ssoCheckBox;
    private DatabaseConnectionInfo connectionInfo;
    private boolean saved = false;
    private Connection activeConnection = null; // Store authenticated connection for reuse
    
    public void initialize() {
        setupDatabaseTypes();
        databaseTypeCombo.setOnAction(e -> updateFieldsForDatabase());
        loadSavedCredentials();
        statusLabel.setText("");
    }
    
    private void setupDatabaseTypes() {
        databaseTypeCombo.setItems(FXCollections.observableArrayList(
            new DatabaseType("Snowflake", Arrays.asList("Account", "Warehouse", "Database", "Schema", "Username", "Password")),
            new DatabaseType("SQL Server", Arrays.asList("Server", "Database", "Username", "Password")),
            new DatabaseType("PostgreSQL", Arrays.asList("Host", "Port", "Database", "Schema", "Username", "Password"))
        ));
        databaseTypeCombo.getSelectionModel().selectFirst();
        updateFieldsForDatabase();
    }
    
    private void updateFieldsForDatabase() {
        DatabaseType dbType = databaseTypeCombo.getValue();
        if (dbType == null) return;
        
        // Save the current SSO checkbox state and field values before clearing
        boolean ssoWasSelected = ssoCheckBox != null && ssoCheckBox.isSelected();
        Map<String, String> savedValues = new HashMap<>();
        for (Map.Entry<String, TextField> entry : fieldMap.entrySet()) {
            savedValues.put(entry.getKey(), entry.getValue().getText());
        }
        
        settingsGrid.getChildren().clear();
        fieldMap.clear();
        comboMap.clear();
        
        int row = 0;
        
        // Add SSO checkbox for Snowflake
        if (dbType.name.equals("Snowflake")) {
            ssoCheckBox = new CheckBox("Use Single Sign-On (Okta/SAML/SSO)");
            ssoCheckBox.setSelected(ssoWasSelected); // Restore previous state
            ssoCheckBox.setStyle("-fx-font-weight: bold;");
            ssoCheckBox.setOnAction(e -> {
                System.out.println("SSO checkbox clicked: " + ssoCheckBox.isSelected());
                updateFieldsForDatabase();
            });
            GridPane.setColumnIndex(ssoCheckBox, 0);
            GridPane.setColumnSpan(ssoCheckBox, 2);
            GridPane.setRowIndex(ssoCheckBox, row);
            settingsGrid.getChildren().add(ssoCheckBox);
            row++;
        }
        
        boolean isSnowflakeSSO = dbType.name.equals("Snowflake") && ssoCheckBox != null && ssoCheckBox.isSelected();
        
        for (String fieldName : dbType.fields) {
            // Skip only Password field when SSO is enabled (Username is still needed for SSO)
            if (isSnowflakeSSO && fieldName.equals("Password")) {
                continue;
            }
            
            Label label = new Label(fieldName + ":");
            GridPane.setColumnIndex(label, 0);
            GridPane.setRowIndex(label, row);
            
            // For Snowflake Account with SSO, add Connect button
            if (isSnowflakeSSO && fieldName.equals("Account")) {
                TextField textField = new TextField();
                textField.setPromptText(getPromptText(dbType.name, fieldName));
                // Restore saved value
                if (savedValues.containsKey(fieldName)) {
                    textField.setText(savedValues.get(fieldName));
                }
                
                Button connectButton = new Button("Connect to Snowflake");
                connectButton.setStyle("-fx-font-weight: bold; -fx-background-color: #1976d2; -fx-text-fill: white;");
                connectButton.setOnAction(e -> connectToSnowflake(textField.getText(), true));
                
                HBox hbox = new HBox(5);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(textField, connectButton);
                
                GridPane.setColumnIndex(hbox, 1);
                GridPane.setRowIndex(hbox, row);
                
                settingsGrid.getChildren().addAll(label, hbox);
                fieldMap.put(fieldName, textField);
            }
            // For Snowflake Database, Schema, and Warehouse - use ComboBox (populated after connection)
            else if (dbType.name.equals("Snowflake") && (fieldName.equals("Database") || fieldName.equals("Schema") || fieldName.equals("Warehouse"))) {
                ComboBox<String> comboBox = new ComboBox<>();
                comboBox.setEditable(true);
                comboBox.setPrefWidth(250);
                comboBox.setPromptText("Connect first to load " + fieldName.toLowerCase() + "s");
                comboBox.setDisable(true); // Disabled until connection is made
                
                GridPane.setColumnIndex(comboBox, 1);
                GridPane.setRowIndex(comboBox, row);
                
                settingsGrid.getChildren().addAll(label, comboBox);
                comboMap.put(fieldName, comboBox);
                
                // Setup cascading: when warehouse changes, reload databases
                if (fieldName.equals("Warehouse")) {
                    comboBox.valueProperty().addListener((obs, old, newVal) -> {
                        if (newVal != null && !newVal.equals(old)) {
                            loadDatabases();
                        }
                    });
                }
                // When database changes, reload schemas
                else if (fieldName.equals("Database")) {
                    comboBox.valueProperty().addListener((obs, old, newVal) -> {
                        if (newVal != null && !newVal.equals(old)) {
                            loadSchemas();
                        }
                    });
                }
                
                // Create a hidden TextField for compatibility with existing code
                TextField hiddenField = new TextField();
                comboBox.valueProperty().addListener((obs, old, newVal) -> {
                    if (newVal != null) hiddenField.setText(newVal);
                });
                comboBox.getEditor().textProperty().addListener((obs, old, newVal) -> {
                    hiddenField.setText(newVal);
                });
                fieldMap.put(fieldName, hiddenField);
            } else {
                TextField textField;
                if (fieldName.equals("Password")) {
                    textField = new PasswordField();
                } else {
                    textField = new TextField();
                }
                
                textField.setPromptText(getPromptText(dbType.name, fieldName));
                // Restore saved value
                if (savedValues.containsKey(fieldName)) {
                    textField.setText(savedValues.get(fieldName));
                }
                
                GridPane.setColumnIndex(textField, 1);
                GridPane.setRowIndex(textField, row);
                
                settingsGrid.getChildren().addAll(label, textField);
                fieldMap.put(fieldName, textField);
            }
            row++;
        }
        
        // Try to load saved values for this database type (but don't overwrite user-entered values)
        if (savedValues.isEmpty()) {
            loadFieldValues(dbType.name);
        }
        
        // For Snowflake with username/password, add Connect button after all fields
        if (dbType.name.equals("Snowflake") && !isSnowflakeSSO) {
            Button connectButton = new Button("Connect to Snowflake");
            connectButton.setStyle("-fx-font-weight: bold; -fx-background-color: #1976d2; -fx-text-fill: white; -fx-padding: 8 16 8 16;");
            connectButton.setOnAction(e -> {
                String account = fieldMap.get("Account").getText().trim();
                connectToSnowflake(account, false);
            });
            
            GridPane.setColumnIndex(connectButton, 1);
            GridPane.setRowIndex(connectButton, row);
            settingsGrid.getChildren().add(connectButton);
            row++;
        }
    }
    
    private String getPromptText(String dbType, String field) {
        if (dbType.equals("Snowflake")) {
            switch (field) {
                case "Account": return "loves (from URL: loves.snowflakecomputing.com)";
                case "Warehouse": return "COMPUTE_WH";
                case "Database": return "SALESFORCE_BACKUP";
                case "Schema": return "PUBLIC";
                case "Username": return "your.email@loves.com";
            }
        } else if (dbType.equals("PostgreSQL")) {
            switch (field) {
                case "Host": return "localhost";
                case "Port": return "5432";
                case "Database": return "salesforce";
                case "Schema": return "public";
            }
        } else if (dbType.equals("SQL Server")) {
            switch (field) {
                case "Server": return "localhost:1433";
                case "Database": return "SalesforceBackup";
            }
        }
        return "";
    }
    
    private void loadSavedCredentials() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
            
            if (prefs.getBoolean("remember", false)) {
                String savedDbType = prefs.get("databaseType", "Snowflake");
                
                // Select the saved database type
                for (DatabaseType dbType : databaseTypeCombo.getItems()) {
                    if (dbType.name.equals(savedDbType)) {
                        databaseTypeCombo.getSelectionModel().select(dbType);
                        break;
                    }
                }
                
                rememberCheckBox.setSelected(true);
            }
        } catch (Exception e) {
            logger.error("Error loading saved credentials", e);
        }
    }
    
    private void loadFieldValues(String dbType) {
        try {
            Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
            
            if (prefs.getBoolean("remember", false) && dbType.equals(prefs.get("databaseType", ""))) {
                for (Map.Entry<String, TextField> entry : fieldMap.entrySet()) {
                    String key = entry.getKey().toLowerCase();
                    String value = prefs.get(key, "");
                    
                    if (key.equals("password") && !value.isEmpty()) {
                        value = new String(Base64.getDecoder().decode(value));
                    }
                    
                    entry.getValue().setText(value);
                    
                    // Also update ComboBox if it exists
                    ComboBox<String> combo = comboMap.get(entry.getKey());
                    if (combo != null && !value.isEmpty()) {
                        combo.setValue(value);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error loading field values", e);
        }
    }
    
    
    private void connectToSnowflake(String account, boolean useSSO) {
        if (account == null || account.trim().isEmpty()) {
            statusLabel.setText("Please enter your Snowflake account name first");
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            return;
        }
        
        // Get username - required for both SSO and password auth
        String username = fieldMap.get("Username").getText().trim();
        if (username.isEmpty()) {
            statusLabel.setText("Please enter your Username (email)");
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            return;
        }
        
        String password = null;
        if (!useSSO) {
            password = fieldMap.get("Password").getText().trim();
            if (password.isEmpty()) {
                statusLabel.setText("Please enter your Password");
                statusLabel.setStyle("-fx-text-fill: #c62828;");
                return;
            }
        }
        
        statusLabel.setText(useSSO ? "Connecting with SSO (browser will open)..." : "Connecting to Snowflake...");
        statusLabel.setStyle("-fx-text-fill: #1976d2;");
        
        final String finalUsername = username;
        final String finalPassword = password;
        
        Task<Connection> connectTask = new Task<Connection>() {
            @Override
            protected Connection call() throws Exception {
                String url = String.format("jdbc:snowflake://%s.snowflakecomputing.com", account.trim());
                Properties props = new Properties();
                props.put("user", finalUsername);
                
                if (useSSO) {
                    props.put("authenticator", "externalbrowser");
                } else {
                    props.put("password", finalPassword);
                }
                
                // Establish and return the connection
                return DriverManager.getConnection(url, props);
            }
        };
        
        connectTask.setOnSucceeded(e -> {
            activeConnection = connectTask.getValue();
            statusLabel.setText("✓ Connected successfully! Loading warehouses...");
            statusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
            
            // Enable the dropdowns
            comboMap.get("Warehouse").setDisable(false);
            comboMap.get("Database").setDisable(false);
            comboMap.get("Schema").setDisable(false);
            
            // Auto-load warehouses
            loadWarehouses();
        });
        
        connectTask.setOnFailed(e -> {
            Throwable ex = connectTask.getException();
            logger.error("Snowflake connection failed", ex);
            String errorMsg = ex.getMessage();
            if (errorMsg != null && errorMsg.contains("User cancelled")) {
                statusLabel.setText("✗ Connection cancelled by user");
            } else {
                statusLabel.setText("✗ Connection failed: " + (errorMsg != null ? errorMsg : ex.getClass().getSimpleName()));
            }
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            activeConnection = null;
        });
        
        new Thread(connectTask).start();
    }
    
    private void loadWarehouses() {
        loadSnowflakeResource("Warehouse", "SHOW WAREHOUSES", comboMap.get("Warehouse"));
    }
    
    private void loadDatabases() {
        String warehouse = comboMap.get("Warehouse").getValue();
        if (warehouse == null || warehouse.trim().isEmpty()) {
            return;
        }
        
        // Set warehouse context and load databases
        Task<Void> setWarehouseTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (Statement stmt = activeConnection.createStatement()) {
                    stmt.execute("USE WAREHOUSE " + warehouse);
                }
                return null;
            }
        };
        
        setWarehouseTask.setOnSucceeded(e -> {
            loadSnowflakeResource("Database", "SHOW DATABASES", comboMap.get("Database"));
        });
        
        setWarehouseTask.setOnFailed(e -> {
            logger.error("Failed to set warehouse", e.getSource().getException());
        });
        
        new Thread(setWarehouseTask).start();
    }
    
    private void loadSchemas() {
        String database = comboMap.get("Database").getValue();
        if (database == null || database.trim().isEmpty()) {
            return;
        }
        
        loadSnowflakeResource("Schema", "SHOW SCHEMAS IN DATABASE " + database, comboMap.get("Schema"));
    }
    
    private void loadSnowflakeResource(String resourceType, String query, ComboBox<String> comboBox) {
        if (activeConnection == null) {
            statusLabel.setText("Please connect to Snowflake first");
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            return;
        }
        
        statusLabel.setText("Loading " + resourceType.toLowerCase() + "s...");
        statusLabel.setStyle("-fx-text-fill: #1976d2;");
        
        Task<List<String>> loadTask = new Task<List<String>>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> items = new ArrayList<>();
                try (Statement stmt = activeConnection.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    while (rs.next()) {
                        items.add(rs.getString("name"));
                    }
                }
                return items;
            }
        };
        
        loadTask.setOnSucceeded(e -> {
            List<String> items = loadTask.getValue();
            Platform.runLater(() -> {
                comboBox.setItems(FXCollections.observableArrayList(items));
                if (!items.isEmpty()) {
                    comboBox.getSelectionModel().selectFirst();
                }
                statusLabel.setText(String.format("✓ Loaded %d %s(s)", items.size(), resourceType.toLowerCase()));
                statusLabel.setStyle("-fx-text-fill: #2e7d32;");
            });
        });
        
        loadTask.setOnFailed(e -> {
            Throwable ex = loadTask.getException();
            logger.error("Failed to load " + resourceType, ex);
            Platform.runLater(() -> {
                statusLabel.setText("✗ Failed to load " + resourceType.toLowerCase() + "s: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #c62828;");
            });
        });
        
        new Thread(loadTask).start();
    }
    
    @FXML
    private void handleTestConnection() {
        if (!validateFields()) {
            return;
        }
        
        statusLabel.setText("Testing connection...");
        statusLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold;");
        
        Task<Boolean> testTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                DataSink sink = createDataSink();
                return sink.testConnection();
            }
        };
        
        testTask.setOnSucceeded(e -> {
            if (testTask.getValue()) {
                statusLabel.setText("✓ Connection successful!");
                statusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
            } else {
                statusLabel.setText("✗ Connection failed. Please check your credentials.");
                statusLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
            }
        });
        
        testTask.setOnFailed(e -> {
            Throwable ex = testTask.getException();
            logger.error("Connection test failed", ex);
            statusLabel.setText("✗ Error: " + ex.getMessage());
            statusLabel.setStyle("-fx-text-fill: #c62828;");
        });
        
        new Thread(testTask).start();
    }
    
    @FXML
    private void handleSave() {
        if (!validateFields()) {
            return;
        }
        
        DatabaseType dbType = databaseTypeCombo.getValue();
        Map<String, String> fields = new HashMap<>();
        
        for (Map.Entry<String, TextField> entry : fieldMap.entrySet()) {
            fields.put(entry.getKey(), entry.getValue().getText().trim());
        }
        
        connectionInfo = new DatabaseConnectionInfo(dbType.name, fields);
        
        if (rememberCheckBox.isSelected()) {
            saveCredentials();
        } else {
            clearSavedCredentials();
        }
        
        saved = true;
        closeDialog();
    }
    
    @FXML
    private void handleCancel() {
        closeDialog();
    }
    
    private boolean validateFields() {
        for (Map.Entry<String, TextField> entry : fieldMap.entrySet()) {
            if (entry.getValue().getText().trim().isEmpty()) {
                showError(entry.getKey() + " is required");
                return false;
            }
        }
        return true;
    }
    
    private void saveCredentials() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
            DatabaseType dbType = databaseTypeCombo.getValue();
            
            prefs.put("databaseType", dbType.name);
            
            for (Map.Entry<String, TextField> entry : fieldMap.entrySet()) {
                String key = entry.getKey().toLowerCase();
                String value = entry.getValue().getText().trim();
                
                if (key.equals("password")) {
                    value = Base64.getEncoder().encodeToString(value.getBytes());
                }
                
                prefs.put(key, value);
            }
            
            prefs.putBoolean("remember", true);
            prefs.flush();
            
            logger.info("Database credentials saved for {}", dbType.name);
        } catch (Exception e) {
            logger.error("Error saving credentials", e);
        }
    }
    
    private void clearSavedCredentials() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
            prefs.clear();
            prefs.flush();
            logger.info("Database credentials cleared");
        } catch (Exception e) {
            logger.error("Error clearing credentials", e);
        }
    }
    
    private DataSink createDataSink() {
        DatabaseType dbType = databaseTypeCombo.getValue();
        
        switch (dbType.name) {
            case "Snowflake":
                boolean useSSO = ssoCheckBox != null && ssoCheckBox.isSelected();
                if (useSSO) {
                    return DataSinkFactory.createSnowflakeSinkWithSSO(
                        fieldMap.get("Account").getText().trim(),
                        fieldMap.get("Warehouse").getText().trim(),
                        fieldMap.get("Database").getText().trim(),
                        fieldMap.get("Schema").getText().trim()
                    );
                } else {
                    return DataSinkFactory.createSnowflakeSink(
                        fieldMap.get("Account").getText().trim(),
                        fieldMap.get("Warehouse").getText().trim(),
                        fieldMap.get("Database").getText().trim(),
                        fieldMap.get("Schema").getText().trim(),
                        fieldMap.get("Username").getText().trim(),
                        fieldMap.get("Password").getText()
                    );
                }
            
            case "SQL Server":
                return DataSinkFactory.createSqlServerSink(
                    fieldMap.get("Server").getText().trim(),
                    fieldMap.get("Database").getText().trim(),
                    fieldMap.get("Username").getText().trim(),
                    fieldMap.get("Password").getText()
                );
            
            case "PostgreSQL":
                return DataSinkFactory.createPostgresSink(
                    fieldMap.get("Host").getText().trim(),
                    Integer.parseInt(fieldMap.get("Port").getText().trim()),
                    fieldMap.get("Database").getText().trim(),
                    fieldMap.get("Schema").getText().trim(),
                    fieldMap.get("Username").getText().trim(),
                    fieldMap.get("Password").getText()
                );
            
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType.name);
        }
    }
    
    private void showError(String message) {
        statusLabel.setText("✗ " + message);
        statusLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
    }
    
    private void closeDialog() {
        // Clean up active connection
        if (activeConnection != null) {
            try {
                activeConnection.close();
            } catch (SQLException e) {
                logger.error("Error closing connection", e);
            }
            activeConnection = null;
        }
        
        Stage stage = (Stage) settingsGrid.getScene().getWindow();
        stage.close();
    }
    
    public DatabaseConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }
    
    public boolean isSaved() {
        return saved;
    }
    
    public static class DatabaseType {
        private final String name;
        private final List<String> fields;
        
        public DatabaseType(String name, List<String> fields) {
            this.name = name;
            this.fields = fields;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    public static class DatabaseConnectionInfo {
        private final String databaseType;
        private final Map<String, String> fields;
        
        public DatabaseConnectionInfo(String databaseType, Map<String, String> fields) {
            this.databaseType = databaseType;
            this.fields = fields;
        }
        
        public String getDatabaseType() { return databaseType; }
        public Map<String, String> getFields() { return fields; }
        public String getField(String name) { return fields.get(name); }
    }
}
