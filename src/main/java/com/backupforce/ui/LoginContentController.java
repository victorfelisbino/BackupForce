package com.backupforce.ui;

import com.backupforce.auth.SalesforceOAuthServer;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectorConfig;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

/**
 * Controller for the login content panel.
 * This controller is embedded in the AppController's root container
 * to avoid scene changes during login-to-app transitions.
 */
public class LoginContentController {
    private static final Logger logger = LoggerFactory.getLogger(LoginContentController.class);
    private static final Preferences prefs = Preferences.userNodeForPackage(LoginContentController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField tokenField;
    @FXML private ComboBox<String> environmentCombo;
    @FXML private CheckBox rememberCredentialsCheckBox;
    @FXML private Button loginButton;
    @FXML private Button oauthButton;
    @FXML private Button cancelButton;
    @FXML private Label statusLabel;
    @FXML private Label timeoutLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private ListView<String> savedCredentialsList;
    @FXML private VBox savedCredentialsSection;
    @FXML private Label environmentUrlLabel;
    
    private boolean isLoggingIn = false;
    private SalesforceOAuthServer currentOAuthServer = null;
    private Timeline countdownTimeline = null;
    
    // Reference to parent AppController for navigation
    private AppController appController;

    public void setAppController(AppController appController) {
        this.appController = appController;
    }

    @FXML
    public void initialize() {
        environmentCombo.getItems().addAll(
            "Production (login.salesforce.com)",
            "Sandbox (test.salesforce.com)"
        );
        environmentCombo.getSelectionModel().selectFirst();
        progressIndicator.setVisible(false);
        
        // Update URL label when environment changes
        environmentCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateEnvironmentLabel();
        });
        
        // Load saved credentials list
        loadSavedCredentialsList();
        
        // Load most recent credentials
        loadSavedCredentials();
        
        // Set initial URL label
        updateEnvironmentLabel();
    }
    
    private void updateEnvironmentLabel() {
        String env = environmentCombo.getSelectionModel().getSelectedItem();
        if (env != null) {
            String url = env.contains("Sandbox") ? "https://test.salesforce.com" : "https://login.salesforce.com";
            environmentUrlLabel.setText("OAuth will use: " + url);
        }
    }
    
    private void loadSavedCredentialsList() {
        try {
            String[] keys = prefs.keys();
            java.util.Set<String> usernames = new java.util.HashSet<>();
            
            for (String key : keys) {
                if (key.startsWith("cred_")) {
                    String username = key.substring(5);
                    usernames.add(username);
                }
            }
            
            if (usernames.isEmpty()) {
                savedCredentialsSection.setVisible(false);
                savedCredentialsSection.setManaged(false);
            } else {
                savedCredentialsList.getItems().clear();
                savedCredentialsList.getItems().addAll(usernames.stream().sorted().collect(java.util.stream.Collectors.toList()));
                
                savedCredentialsList.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2) {
                        String selected = savedCredentialsList.getSelectionModel().getSelectedItem();
                        if (selected != null) {
                            loadCredentialsForUser(selected);
                        }
                    }
                });
                
                savedCredentialsSection.setVisible(true);
                savedCredentialsSection.setManaged(true);
            }
        } catch (Exception e) {
            logger.warn("Failed to load saved credentials list", e);
            savedCredentialsSection.setVisible(false);
            savedCredentialsSection.setManaged(false);
        }
    }
    
    private void loadCredentialsForUser(String username) {
        try {
            String credData = prefs.get("cred_" + username, "");
            if (!credData.isEmpty()) {
                String[] parts = credData.split("\\|");
                if (parts.length >= 3) {
                    usernameField.setText(username);
                    passwordField.setText(new String(Base64.getDecoder().decode(parts[0])));
                    tokenField.setText(new String(Base64.getDecoder().decode(parts[1])));
                    environmentCombo.getSelectionModel().select(Integer.parseInt(parts[2]));
                    rememberCredentialsCheckBox.setSelected(true);
                    logger.info("Loaded credentials for user: {}", username);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load credentials for user: {}", username, e);
        }
    }
    
    private void loadSavedCredentials() {
        try {
            String savedUsername = prefs.get("username", "");
            String savedPassword = prefs.get("password", "");
            String savedToken = prefs.get("token", "");
            int savedEnv = prefs.getInt("environment", 0);
            
            if (!savedUsername.isEmpty()) {
                usernameField.setText(savedUsername);
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
                String credData = Base64.getEncoder().encodeToString(password.getBytes()) + "|" +
                                 Base64.getEncoder().encodeToString(token.getBytes()) + "|" +
                                 environment;
                prefs.put("cred_" + username, credData);
                
                prefs.put("username", username);
                prefs.put("password", Base64.getEncoder().encodeToString(password.getBytes()));
                prefs.put("token", Base64.getEncoder().encodeToString(token.getBytes()));
                prefs.putInt("environment", environment);
                logger.info("Saved credentials for user: {}", username);
            } else {
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
        
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username and password are required");
            return;
        }
        
        String fullPassword = token.isEmpty() ? password : password + token;
        
        String serverUrl = environmentCombo.getSelectionModel().getSelectedIndex() == 0 
            ? "https://login.salesforce.com" 
            : "https://test.salesforce.com";

        isLoggingIn = true;
        loginButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("Authenticating...");

        Task<LoginController.ConnectionInfo> loginTask = new Task<>() {
            @Override
            protected LoginController.ConnectionInfo call() throws Exception {
                ConnectorConfig partnerConfig = new ConnectorConfig();
                partnerConfig.setUsername(username);
                partnerConfig.setPassword(fullPassword);
                partnerConfig.setAuthEndpoint(serverUrl + "/services/Soap/u/62.0");
                
                PartnerConnection connection = new PartnerConnection(partnerConfig);
                
                String sessionId = partnerConfig.getSessionId();
                String instanceUrl = partnerConfig.getServiceEndpoint();
                instanceUrl = instanceUrl.substring(0, instanceUrl.indexOf("/services"));
                
                return new LoginController.ConnectionInfo(connection, sessionId, instanceUrl, username, fullPassword, serverUrl);
            }
        };
        
        loginTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                LoginController.ConnectionInfo connInfo = loginTask.getValue();
                logger.info("Successfully authenticated to Salesforce");
                statusLabel.setText("Authentication successful!");
                
                saveCredentials(username, password, token, environmentCombo.getSelectionModel().getSelectedIndex());
                
                // Navigate to main app via AppController
                if (appController != null) {
                    appController.showMainApp(connInfo);
                } else {
                    logger.error("AppController not set - cannot navigate to main app");
                    statusLabel.setText("Navigation error - please restart the application");
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
                
                String errorMessage = extractErrorMessage(exception);
                statusLabel.setText("Login failed");
                
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
            return "Authentication error:\n\n" + message + "\n\n" +
                   "If this problem persists, verify your credentials and network connection.";
        }
    }
    
    @FXML
    private void handleOAuthLogin() {
        logger.info("OAuth login button clicked!");
        
        if (isLoggingIn) {
            logger.info("Already logging in, ignoring click");
            return;
        }
        
        try {
            startOAuthProcess();
        } catch (Throwable e) {
            logger.error("Failed to start OAuth process", e);
            e.printStackTrace();
            showError("Failed to start OAuth: " + e.getMessage());
        }
    }
    
    private void startOAuthProcess() {
        logger.info("Starting OAuth login process...");
        
        final int TIMEOUT_SECONDS = 180;
        final AtomicInteger remainingSeconds = new AtomicInteger(TIMEOUT_SECONDS);
        
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            int remaining = remainingSeconds.decrementAndGet();
            int minutes = remaining / 60;
            int seconds = remaining % 60;
            timeoutLabel.setText(String.format("Time remaining: %d:%02d", minutes, seconds));
            
            if (remaining <= 30) {
                timeoutLabel.setStyle("-fx-text-fill: #e74c3c;");
            }
        }));
        countdownTimeline.setCycleCount(TIMEOUT_SECONDS);
        
        Task<Void> oauthTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    String environment = environmentCombo.getSelectionModel().getSelectedItem();
                    String loginUrl = environment.contains("Sandbox") ? 
                        "https://test.salesforce.com" : "https://login.salesforce.com";
                    
                    currentOAuthServer = new SalesforceOAuthServer();
                    currentOAuthServer.setTimeout(TIMEOUT_SECONDS);
                    
                    // Try silent refresh first (no browser)
                    updateMessage("Checking for saved session...");
                    SalesforceOAuthServer.OAuthResult result = currentOAuthServer.authenticateWithSilentRefresh(loginUrl, true);
                    
                    // If silent failed, browser was attempted
                    if (!result.isSuccess() && !result.isCancelled()) {
                        updateMessage("Opening browser for authentication...\nComplete login in browser, then return here.");
                    }
                    
                    if (result.isCancelled()) {
                        Platform.runLater(() -> {
                            statusLabel.setText("Login cancelled");
                            statusLabel.setStyle("-fx-text-fill: #888;");
                        });
                        return null;
                    }
                    
                    if (!result.isSuccess()) {
                        Platform.runLater(() -> showError(result.error));
                        return null;
                    }
                    
                    updateMessage("Creating session...");
                    
                    ConnectorConfig config = new ConnectorConfig();
                    config.setSessionId(result.accessToken);
                    config.setServiceEndpoint(result.instanceUrl + "/services/Soap/u/62.0");
                    
                    PartnerConnection connection = new PartnerConnection(config);
                    
                    String username = connection.getUserInfo().getUserName();
                    logger.info("OAuth login successful for user: {}", username);
                    
                    // Store the refresh token for next time (silent login)
                    if (result.refreshToken != null) {
                        com.backupforce.auth.TokenStorage.getInstance().storeToken(
                            loginUrl, username, result.refreshToken, result.instanceUrl
                        );
                    }
                    
                    updateMessage("Success! Loading main window...");
                    
                    final LoginController.ConnectionInfo connInfo = new LoginController.ConnectionInfo(
                        connection,
                        result.accessToken,
                        result.instanceUrl,
                        username,
                        null,
                        loginUrl
                    );
                    
                    // Navigate to main app via AppController
                    Platform.runLater(() -> {
                        if (appController != null) {
                            appController.showMainApp(connInfo);
                        } else {
                            logger.error("AppController not set - cannot navigate to main app");
                            showError("Navigation error - please restart the application");
                        }
                    });
                    
                } catch (Exception e) {
                    logger.error("OAuth login failed", e);
                    Platform.runLater(() -> showError(
                        "OAuth authentication failed:\n\n" + e.getMessage() + "\n\n" +
                        "This uses browser-based OAuth (no Connected App needed).\n" +
                        "If the browser didn't open, try again."
                    ));
                } finally {
                    currentOAuthServer = null;
                }
                return null;
            }
        };
        
        oauthTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            Platform.runLater(() -> statusLabel.setText(newMsg));
        });
        
        oauthTask.setOnRunning(e -> {
            isLoggingIn = true;
            progressIndicator.setVisible(true);
            loginButton.setDisable(true);
            oauthButton.setDisable(true);
            cancelButton.setVisible(true);
            cancelButton.setManaged(true);
            timeoutLabel.setVisible(true);
            timeoutLabel.setManaged(true);
            timeoutLabel.setStyle("-fx-text-fill: #888;");
            timeoutLabel.setText("Time remaining: 3:00");
            countdownTimeline.play();
        });
        
        Runnable resetUI = () -> {
            isLoggingIn = false;
            progressIndicator.setVisible(false);
            loginButton.setDisable(false);
            oauthButton.setDisable(false);
            cancelButton.setVisible(false);
            cancelButton.setManaged(false);
            timeoutLabel.setVisible(false);
            timeoutLabel.setManaged(false);
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
        };
        
        oauthTask.setOnSucceeded(e -> resetUI.run());
        oauthTask.setOnFailed(e -> {
            resetUI.run();
            Throwable ex = oauthTask.getException();
            logger.error("OAuth task failed with exception", ex);
            statusLabel.setText("Login failed: " + (ex != null ? ex.getMessage() : "unknown error"));
        });
        oauthTask.setOnCancelled(e -> {
            resetUI.run();
            statusLabel.setText("Login cancelled");
        });
        
        Thread oauthThread = new Thread(oauthTask);
        oauthThread.setDaemon(true);
        oauthThread.setUncaughtExceptionHandler((t, ex) -> {
            logger.error("Uncaught exception in OAuth thread", ex);
        });
        oauthThread.start();
    }
    
    @FXML
    private void handleCancelOAuth() {
        logger.info("User cancelled OAuth flow");
        
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        
        if (currentOAuthServer != null) {
            currentOAuthServer.cancel();
            currentOAuthServer = null;
        }
        
        isLoggingIn = false;
        progressIndicator.setVisible(false);
        loginButton.setDisable(false);
        oauthButton.setDisable(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        timeoutLabel.setVisible(false);
        timeoutLabel.setManaged(false);
        statusLabel.setText("Login cancelled");
        statusLabel.setStyle("-fx-text-fill: #888;");
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
}
