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
    private String ssoToken = null;
    
    public void initialize() {
        setupDatabaseTypes();
        databaseTypeCombo.setOnAction(e -> updateFieldsForDatabase());
        loadSavedCredentials();
        statusLabel.setText("");
    }
    
    private void setupDatabaseTypes() {
        databaseTypeCombo.setItems(FXCollections.observableArrayList(
            new DatabaseType("Snowflake", Arrays.asList("Account", "Username", "Password", "Warehouse", "Database", "Schema")),
            new DatabaseType("SQL Server", Arrays.asList("Server", "Database", "Username", "Password")),
            new DatabaseType("PostgreSQL", Arrays.asList("Host", "Port", "Database", "Schema", "Username", "Password"))
        ));
        databaseTypeCombo.getSelectionModel().selectFirst();
        updateFieldsForDatabase();
    }
    
    private void updateFieldsForDatabase() {
        DatabaseType dbType = databaseTypeCombo.getValue();
        if (dbType == null) return;
        
        settingsGrid.getChildren().clear();
        fieldMap.clear();
        comboMap.clear();
        
        int row = 0;
        
        // Add SSO checkbox for Snowflake
        if (dbType.name.equals("Snowflake")) {
            ssoCheckBox = new CheckBox("Use Single Sign-On (SSO)");
            ssoCheckBox.setStyle("-fx-font-weight: bold;");
            ssoCheckBox.setOnAction(e -> updateFieldsForDatabase());
            GridPane.setColumnIndex(ssoCheckBox, 0);
            GridPane.setColumnSpan(ssoCheckBox, 2);
            GridPane.setRowIndex(ssoCheckBox, row);
            settingsGrid.getChildren().add(ssoCheckBox);
            row++;
        }
        
        boolean isSnowflakeSSO = dbType.name.equals("Snowflake") && ssoCheckBox != null && ssoCheckBox.isSelected();
        
        for (String fieldName : dbType.fields) {
            // Skip Username and Password fields when SSO is enabled
            if (isSnowflakeSSO && (fieldName.equals("Username") || fieldName.equals("Password"))) {
                continue;
            }
            
            Label label = new Label(fieldName + ":");
            GridPane.setColumnIndex(label, 0);
            GridPane.setRowIndex(label, row);
            
            // For Snowflake Account with SSO, add login button
            if (isSnowflakeSSO && fieldName.equals("Account")) {
                TextField textField = new TextField();
                textField.setPromptText(getPromptText(dbType.name, fieldName));
                
                Button ssoLoginButton = new Button("Login with SSO");
                ssoLoginButton.setStyle("-fx-font-weight: bold; -fx-background-color: #1976d2; -fx-text-fill: white;");
                ssoLoginButton.setOnAction(e -> authenticateWithSSO(textField.getText()));
                
                HBox hbox = new HBox(5);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(textField, ssoLoginButton);
                
                GridPane.setColumnIndex(hbox, 1);
                GridPane.setRowIndex(hbox, row);
                
                settingsGrid.getChildren().addAll(label, hbox);
                fieldMap.put(fieldName, textField);
            }
            // For Snowflake Database and Schema, use ComboBox with refresh button
            else if (dbType.name.equals("Snowflake") && (fieldName.equals("Database") || fieldName.equals("Schema") || fieldName.equals("Warehouse"))) {
                HBox hbox = new HBox(5);
                hbox.setAlignment(Pos.CENTER_LEFT);
                
                ComboBox<String> comboBox = new ComboBox<>();
                comboBox.setEditable(true);
                comboBox.setPrefWidth(200);
                comboBox.setPromptText(getPromptText(dbType.name, fieldName));
                
                Button refreshButton = new Button("↻");
                refreshButton.setStyle("-fx-font-size: 14px; -fx-padding: 4 8 4 8;");
                refreshButton.setTooltip(new Tooltip("Load " + fieldName.toLowerCase() + "s from Snowflake"));
                refreshButton.setOnAction(e -> loadSnowflakeOptions(fieldName, comboBox));
                
                hbox.getChildren().addAll(comboBox, refreshButton);
                GridPane.setColumnIndex(hbox, 1);
                GridPane.setRowIndex(hbox, row);
                
                settingsGrid.getChildren().addAll(label, hbox);
                comboMap.put(fieldName, comboBox);
                
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
                GridPane.setColumnIndex(textField, 1);
                GridPane.setRowIndex(textField, row);
                
                settingsGrid.getChildren().addAll(label, textField);
                fieldMap.put(fieldName, textField);
            }
            row++;
        }
        
        // Try to load saved values for this database type
        loadFieldValues(dbType.name);
    }
    
    private String getPromptText(String dbType, String field) {
        if (dbType.equals("Snowflake")) {
            switch (field) {
                case "Account": return "mycompany";
                case "Warehouse": return "COMPUTE_WH";
                case "Database": return "SALESFORCE_BACKUP";
                case "Schema": return "PUBLIC";
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
    
    private void loadSnowflakeOptions(String fieldName, ComboBox<String> comboBox) {
        // Get connection parameters
        String account = fieldMap.get("Account").getText().trim();
        
        if (account.isEmpty()) {
            statusLabel.setText("Please enter Account first");
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            return;
        }
        
        boolean useSSO = ssoCheckBox != null && ssoCheckBox.isSelected();
        String username = null;
        String password = null;
        
        if (!useSSO) {
            username = fieldMap.get("Username").getText().trim();
            password = fieldMap.get("Password").getText().trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("Please enter Username and Password first");
                statusLabel.setStyle("-fx-text-fill: #c62828;");
                return;
            }
        } else if (ssoToken == null) {
            statusLabel.setText("Please login with SSO first");
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            return;
        }
        
        statusLabel.setText("Loading " + fieldName.toLowerCase() + "s from Snowflake...");
        statusLabel.setStyle("-fx-text-fill: #1976d2;");
        
        final String finalUsername = username;
        final String finalPassword = password;
        final boolean finalUseSSO = useSSO;
        
        Task<List<String>> loadTask = new Task<List<String>>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> items = new ArrayList<>();
                
                String url = String.format("jdbc:snowflake://%s.snowflakecomputing.com", account);
                Properties props = new Properties();
                
                if (finalUseSSO && ssoToken != null) {
                    // Use externalbrowser for SSO - it will reuse the authenticated session
                    props.put("authenticator", "externalbrowser");
                } else {
                    props.put("user", finalUsername);
                    props.put("password", finalPassword);
                }
                
                try (Connection conn = DriverManager.getConnection(url, props)) {
                    if (fieldName.equals("Warehouse")) {
                        // Load warehouses
                        try (Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery("SHOW WAREHOUSES")) {
                            while (rs.next()) {
                                items.add(rs.getString("name"));
                            }
                        }
                    } else if (fieldName.equals("Database")) {
                        // Load databases
                        try (Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
                            while (rs.next()) {
                                items.add(rs.getString("name"));
                            }
                        }
                    } else if (fieldName.equals("Schema")) {
                        // Load schemas from selected database
                        String database = fieldMap.get("Database").getText().trim();
                        if (database.isEmpty()) {
                            throw new Exception("Please select a database first");
                        }
                        try (Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery("SHOW SCHEMAS IN DATABASE " + database)) {
                            while (rs.next()) {
                                items.add(rs.getString("name"));
                            }
                        }
                    }
                }
                
                return items;
            }
        };
        
        loadTask.setOnSucceeded(e -> {
            List<String> items = loadTask.getValue();
            comboBox.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText(String.format("✓ Loaded %d %s(s)", items.size(), fieldName.toLowerCase()));
            statusLabel.setStyle("-fx-text-fill: #2e7d32;");
            
            if (!items.isEmpty()) {
                comboBox.getSelectionModel().selectFirst();
            }
        });
        
        loadTask.setOnFailed(e -> {
            Throwable ex = loadTask.getException();
            logger.error("Failed to load " + fieldName, ex);
            statusLabel.setText("✗ Failed to load: " + ex.getMessage());
            statusLabel.setStyle("-fx-text-fill: #c62828;");
        });
        
        new Thread(loadTask).start();
    }
    
    private void authenticateWithSSO(String account) {
        if (account == null || account.trim().isEmpty()) {
            statusLabel.setText("Please enter your Snowflake account name first");
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            return;
        }
        
        statusLabel.setText("Testing SSO connection (browser will open)...");
        statusLabel.setStyle("-fx-text-fill: #1976d2;");
        
        Task<Boolean> authTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                // Use Snowflake's external browser authenticator
                String url = String.format("jdbc:snowflake://%s.snowflakecomputing.com", account.trim());
                Properties props = new Properties();
                props.put("authenticator", "externalbrowser");
                
                // This will open the user's default browser for SSO authentication
                // and establish a connection to verify it works
                try (Connection conn = DriverManager.getConnection(url, props)) {
                    // Test the connection by running a simple query
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT CURRENT_USER()")) {
                        return rs.next();
                    }
                }
            }
        };
        
        authTask.setOnSucceeded(e -> {
            if (authTask.getValue()) {
                ssoToken = "authenticated"; // Just a flag to indicate SSO is set up
                statusLabel.setText("✓ SSO authentication successful! You can now use this account.");
                statusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
            } else {
                statusLabel.setText("✗ SSO authentication failed");
                statusLabel.setStyle("-fx-text-fill: #c62828;");
                ssoToken = null;
            }
        });
        
        authTask.setOnFailed(e -> {
            Throwable ex = authTask.getException();
            logger.error("SSO authentication failed", ex);
            String errorMsg = ex.getMessage();
            if (errorMsg != null && errorMsg.contains("User cancelled")) {
                statusLabel.setText("✗ SSO cancelled by user");
            } else {
                statusLabel.setText("✗ SSO failed: " + (errorMsg != null ? errorMsg : ex.getClass().getSimpleName()));
            }
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            ssoToken = null;
        });
        
        new Thread(authTask).start();
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
