package com.backupforce.ui;

import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectorConfig;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.prefs.Preferences;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private static final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField tokenField;
    @FXML private ComboBox<String> environmentCombo;
    @FXML private CheckBox rememberCredentialsCheckBox;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator progressIndicator;
    
    private boolean isLoggingIn = false;

    @FXML
    public void initialize() {
        environmentCombo.getItems().addAll(
            "Production (login.salesforce.com)",
            "Sandbox (test.salesforce.com)"
        );
        environmentCombo.getSelectionModel().selectFirst();
        progressIndicator.setVisible(false);
        
        // Load saved credentials
        loadSavedCredentials();
    }
    
    private void loadSavedCredentials() {
        try {
            String savedUsername = prefs.get("username", "");
            String savedPassword = prefs.get("password", "");
            String savedToken = prefs.get("token", "");
            int savedEnv = prefs.getInt("environment", 0);
            
            if (!savedUsername.isEmpty()) {
                usernameField.setText(savedUsername);
                // Decode password from base64 (basic obfuscation)
                if (!savedPassword.isEmpty()) {
                    passwordField.setText(new String(Base64.getDecoder().decode(savedPassword)));
                }
                if (!savedToken.isEmpty()) {
                    tokenField.setText(new String(Base64.getDecoder().decode(savedToken)));
                }
                environmentCombo.getSelectionModel().select(savedEnv);
                rememberCredentialsCheckBox.setSelected(true);
                logger.info("Loaded saved credentials for user: {}", savedUsername);
            }
        } catch (Exception e) {
            logger.warn("Failed to load saved credentials", e);
        }
    }
    
    private void saveCredentials(String username, String password, String token, int environment) {
        try {
            if (rememberCredentialsCheckBox.isSelected()) {
                prefs.put("username", username);
                // Base64 encode for basic obfuscation (not encryption, just to avoid plain text)
                prefs.put("password", Base64.getEncoder().encodeToString(password.getBytes()));
                prefs.put("token", Base64.getEncoder().encodeToString(token.getBytes()));
                prefs.putInt("environment", environment);
                logger.info("Saved credentials for user: {}", username);
            } else {
                // Clear saved credentials if unchecked
                clearSavedCredentials();
            }
        } catch (Exception e) {
            logger.error("Failed to save credentials", e);
        }
    }
    
    private void clearSavedCredentials() {
        prefs.remove("username");
        prefs.remove("password");
        prefs.remove("token");
        prefs.remove("environment");
        logger.info("Cleared saved credentials");
    }

    @FXML
    private void handleLogin() {
        if (isLoggingIn) return;
        
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String token = tokenField.getText().trim();
        
        // Validate input
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username and password are required");
            return;
        }
        
        // Combine password and token (token is optional if IP is whitelisted)
        String fullPassword = token.isEmpty() ? password : password + token;
        
        String serverUrl = environmentCombo.getSelectionModel().getSelectedIndex() == 0 
            ? "https://login.salesforce.com" 
            : "https://test.salesforce.com";

        isLoggingIn = true;
        loginButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("Authenticating...");

        Task<ConnectionInfo> loginTask = new Task<ConnectionInfo>() {
            @Override
            protected ConnectionInfo call() throws Exception {
                // Authenticate using Partner API (SOAP)
                ConnectorConfig partnerConfig = new ConnectorConfig();
                partnerConfig.setUsername(username);
                partnerConfig.setPassword(fullPassword);
                partnerConfig.setAuthEndpoint(serverUrl + "/services/Soap/u/62.0");
                
                PartnerConnection connection = new PartnerConnection(partnerConfig);
                
                // Extract session info for REST API
                String sessionId = partnerConfig.getSessionId();
                String instanceUrl = partnerConfig.getServiceEndpoint();
                // Remove the SOAP endpoint part to get base instance URL
                instanceUrl = instanceUrl.substring(0, instanceUrl.indexOf("/services"));
                
                return new ConnectionInfo(connection, sessionId, instanceUrl, username, fullPassword, serverUrl);
            }
        };
        
        loginTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                ConnectionInfo connInfo = loginTask.getValue();
                logger.info("Successfully authenticated to Salesforce");
                statusLabel.setText("Authentication successful!");
                
                // Save credentials if remember is checked
                saveCredentials(username, password, token, environmentCombo.getSelectionModel().getSelectedIndex());
            
                // Open main backup window
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/backup.fxml"));
                    Parent root = loader.load();
                    
                    BackupController controller = loader.getController();
                    controller.setConnectionInfo(connInfo);
                    
                    Scene scene = new Scene(root, 900, 700);
                    // scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                    
                    Stage stage = (Stage) loginButton.getScene().getWindow();
                    stage.setScene(scene);
                    stage.setResizable(true);
                    stage.centerOnScreen();
                } catch (Exception ex) {
                    logger.error("Failed to load backup window", ex);
                    statusLabel.setText("Failed to open backup window: " + ex.getMessage());
                }
            });
        });

        loginTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                isLoggingIn = false;
                loginButton.setDisable(false);
                progressIndicator.setVisible(false);
                Throwable exception = loginTask.getException();
                logger.error("Authentication failed", exception);
                
                // Extract detailed error message
                String errorMessage = extractErrorMessage(exception);
                statusLabel.setText("Login failed");
                
                // Show detailed error dialog
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Authentication Failed");
                alert.setHeaderText("Could not connect to Salesforce");
                alert.setContentText(errorMessage);
                alert.setResizable(true);
                alert.getDialogPane().setPrefWidth(500);
                alert.showAndWait();
            });
        });

        Thread loginThread = new Thread(loginTask);
        loginThread.setDaemon(true);
        loginThread.start();
    }
    
    private String extractErrorMessage(Throwable exception) {
        if (exception == null) {
            return "Unknown error occurred";
        }
        
        String message = exception.getMessage();
        if (message == null) {
            message = exception.getClass().getSimpleName();
        }
        
        // Common error patterns and user-friendly messages
        if (message.contains("INVALID_LOGIN")) {
            return "Invalid username, password, or security token.\n\n" +
                   "Tips:\n" +
                   "• Verify your username and password are correct\n" +
                   "• If your IP is not whitelisted, append your security token to your password\n" +
                   "• Get your security token from: Setup → My Personal Information → Reset Security Token";
        } else if (message.contains("PasswordLockedException")) {
            return "Your account has been locked due to too many failed login attempts.\n\n" +
                   "Please contact your Salesforce administrator or wait 24 hours.";
        } else if (message.contains("UnknownHostException") || message.contains("Connection refused")) {
            return "Cannot connect to Salesforce servers.\n\n" +
                   "Please check your internet connection and try again.";
        } else if (message.contains("SocketTimeoutException")) {
            return "Connection timed out.\n\n" +
                   "Please check your internet connection and try again.";
        } else if (message.contains("SSLException")) {
            return "Secure connection failed.\n\n" +
                   "This may be caused by network security settings or proxy configuration.";
        } else if (message.contains("API_DISABLED_FOR_ORG")) {
            return "API access is disabled for your organization.\n\n" +
                   "Please contact your Salesforce administrator to enable API access.";
        } else if (message.contains("INVALID_SESSION_ID")) {
            return "Session expired. Please try logging in again.";
        } else {
            // Return the actual error message with some context
            return "Authentication error:\n\n" + message + "\n\n" +
                   "If this problem persists, verify your credentials and network connection.";
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Authentication Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Inner class to hold connection information
    public static class ConnectionInfo {
        private final PartnerConnection connection;
        private final String sessionId;
        private final String instanceUrl;
        private final String username;
        private final String password;
        private final String serverUrl;

        public ConnectionInfo(PartnerConnection connection, String sessionId, String instanceUrl,
                            String username, String password, String serverUrl) {
            this.connection = connection;
            this.sessionId = sessionId;
            this.instanceUrl = instanceUrl;
            this.username = username;
            this.password = password;
            this.serverUrl = serverUrl;
        }

        public PartnerConnection getConnection() { return connection; }
        public String getSessionId() { return sessionId; }
        public String getInstanceUrl() { return instanceUrl; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getServerUrl() { return serverUrl; }
    }
}
