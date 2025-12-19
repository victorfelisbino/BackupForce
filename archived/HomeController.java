package com.backupforce.ui;

import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.ui.LoginController.ConnectionInfo;
import com.sforce.soap.partner.DescribeGlobalResult;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.prefs.Preferences;

public class HomeController {
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);
    private static final Preferences prefs = Preferences.userNodeForPackage(HomeController.class);
    
    // Application info
    private static final String APP_VERSION = "2.0.0";
    private static final String DEVELOPER_NAME = "Victor Felisbino";
    private static final String DEVELOPER_EMAIL = "victor.felisbino@loves.com";
    private static final String GITHUB_URL = "https://github.com/victorfelisbino/BackupForce";

    @FXML private Label usernameLabel;
    @FXML private Label instanceLabel;
    @FXML private Label statusLabel;
    @FXML private Label lastBackupLabel;
    @FXML private Label totalBackupsLabel;
    @FXML private Label noConnectionsLabel;
    @FXML private Label objectsAvailableLabel;
    @FXML private Label lastDataBackupLabel;
    @FXML private Label lastBackupRowsLabel;
    
    @FXML private VBox dataBackupCard;
    @FXML private VBox dataRestoreCard;
    @FXML private VBox configBackupCard;
    @FXML private VBox metadataBackupCard;
    @FXML private HBox connectionsContainer;
    @FXML private MenuButton settingsMenuButton;
    
    // Restore card labels
    @FXML private Label restoreSourcesLabel;
    @FXML private Label lastRestoreRowsLabel;
    @FXML private Label lastDataRestoreLabel;
    
    private ConnectionInfo connectionInfo;
    
    @FXML
    public void initialize() {
        // Add hover effects to active cards
        setupCardHoverEffect(dataBackupCard);
        setupCardHoverEffect(dataRestoreCard);
        
        // Load recent activity stats
        loadRecentActivity();
        
        // Load saved database connections
        loadSavedConnections();
        
        // Load data backup card metrics
        loadDataBackupMetrics();
        
        // Load data restore card metrics
        loadDataRestoreMetrics();
    }
    
    private void loadDataBackupMetrics() {
        // Load from preferences
        int objectCount = prefs.getInt("availableObjectCount", 0);
        String lastDataBackup = prefs.get("lastDataBackupDate", null);
        long lastRowCount = prefs.getLong("lastBackupRowCount", 0);
        String destination = prefs.get("lastBackupDestination", "");
        int lastObjectCount = prefs.getInt("lastBackupObjectCount", 0);
        
        // Show available objects count
        if (objectsAvailableLabel != null) {
            objectsAvailableLabel.setText(objectCount > 0 ? String.valueOf(objectCount) : "--");
        }
        
        // Show last backup row count
        if (lastBackupRowsLabel != null) {
            if (lastRowCount > 0) {
                lastBackupRowsLabel.setText(formatRowCount(lastRowCount));
            } else {
                lastBackupRowsLabel.setText("--");
            }
        }
        
        // Show last backup info (timestamp + destination)
        if (lastDataBackupLabel != null) {
            if (lastDataBackup != null) {
                String shortDest = shortenDestination(destination);
                lastDataBackupLabel.setText(String.format("%s • %d obj → %s", 
                    lastDataBackup, lastObjectCount, shortDest));
            } else {
                lastDataBackupLabel.setText("No backups yet");
            }
        }
    }
    
    private String formatRowCount(long count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        } else if (count >= 1_000) {
            return String.format("%.1fK", count / 1_000.0);
        } else {
            return String.valueOf(count);
        }
    }
    
    private String shortenDestination(String destination) {
        if (destination == null || destination.isEmpty()) {
            return "CSV";
        }
        // If it's a file path, just show the folder name
        if (destination.contains("\\") || destination.contains("/")) {
            String[] parts = destination.replace("\\", "/").split("/");
            return parts[parts.length - 1];
        }
        // If it's a database connection, truncate if too long
        if (destination.length() > 15) {
            return destination.substring(0, 12) + "...";
        }
        return destination;
    }
    
    private void loadDataRestoreMetrics() {
        // Load from preferences
        String lastRestoreDate = prefs.get("lastRestoreDate", null);
        long lastRestoreRows = prefs.getLong("lastRestoreRowCount", 0);
        int restoreSourceCount = prefs.getInt("restoreSourceCount", 0);
        
        // Show restore sources count
        if (restoreSourcesLabel != null) {
            restoreSourcesLabel.setText(restoreSourceCount > 0 ? String.valueOf(restoreSourceCount) : "--");
        }
        
        // Show last restore row count
        if (lastRestoreRowsLabel != null) {
            if (lastRestoreRows > 0) {
                lastRestoreRowsLabel.setText(formatRowCount(lastRestoreRows));
            } else {
                lastRestoreRowsLabel.setText("--");
            }
        }
        
        // Show last restore info
        if (lastDataRestoreLabel != null) {
            if (lastRestoreDate != null) {
                lastDataRestoreLabel.setText(lastRestoreDate);
            } else {
                lastDataRestoreLabel.setText("No restores yet");
            }
        }
    }
    
    private void setupCardHoverEffect(VBox card) {
        String normalStyle = card.getStyle();
        String hoverStyle = normalStyle.replace("-fx-border-color: #3f3f3f", "-fx-border-color: #0078d4")
                                       .replace("-fx-background-color: #2b2b2b", "-fx-background-color: #323232");
        
        card.setOnMouseEntered(e -> card.setStyle(hoverStyle));
        card.setOnMouseExited(e -> card.setStyle(normalStyle));
    }
    
    private void loadRecentActivity() {
        try {
            String lastBackup = prefs.get("lastBackupDate", "No backups yet");
            int totalBackups = prefs.getInt("totalBackups", 0);
            
            if (lastBackupLabel != null) {
                lastBackupLabel.setText(lastBackup);
            }
            if (totalBackupsLabel != null) {
                totalBackupsLabel.setText(String.valueOf(totalBackups));
            }
        } catch (Exception e) {
            logger.warn("Failed to load recent activity", e);
        }
    }
    
    private void loadSavedConnections() {
        try {
            List<SavedConnection> connections = ConnectionManager.getInstance().getConnections();
            
            if (connections.isEmpty()) {
                if (noConnectionsLabel != null) {
                    noConnectionsLabel.setVisible(true);
                    noConnectionsLabel.setManaged(true);
                }
            } else {
                if (noConnectionsLabel != null) {
                    noConnectionsLabel.setVisible(false);
                    noConnectionsLabel.setManaged(false);
                }
                
                // Clear existing connection chips (except the label)
                if (connectionsContainer != null) {
                    connectionsContainer.getChildren().removeIf(node -> node instanceof VBox);
                    
                    // Add connection chips
                    for (SavedConnection conn : connections) {
                        VBox chip = createConnectionChip(conn);
                        connectionsContainer.getChildren().add(chip);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load saved connections", e);
        }
    }
    
    private VBox createConnectionChip(SavedConnection conn) {
        VBox chip = new VBox(4);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("connection-chip");
        
        // Database type icon
        String icon = getIconForDatabaseType(conn.getType());
        
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 16px;");
        Label nameLabel = new Label(conn.getName());
        nameLabel.getStyleClass().add("connection-chip-name");
        header.getChildren().addAll(iconLabel, nameLabel);
        
        Label typeLabel = new Label(conn.getType());
        typeLabel.getStyleClass().add("connection-chip-type");
        
        chip.getChildren().addAll(header, typeLabel);
        
        // Click to edit
        chip.setOnMouseClicked(e -> handleEditConnection(conn));
        
        return chip;
    }
    
    private void handleEditConnection(SavedConnection conn) {
        logger.info("Editing connection: {}", conn.getName());
        openDatabaseSettings(conn.getId());
    }
    
    @FXML
    private void handleDatabaseConnections() {
        openDatabaseSettings(null);
    }
    
    @FXML
    private void handlePreferences() {
        // TODO: Implement preferences dialog
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Preferences");
        alert.setHeaderText("Preferences");
        alert.setContentText("Preferences settings coming soon!");
        styleAlert(alert);
        alert.showAndWait();
    }
    
    @FXML
    private void handleAbout() {
        showAboutDialog();
    }
    
    @FXML
    private void handleAddConnection() {
        openDatabaseSettings(null);
    }
    
    private void showAboutDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("About BackupForce");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(settingsMenuButton.getScene().getWindow());
        
        // Create content
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30, 40, 30, 40));
        content.setStyle("-fx-background-color: #1a1a1a;");
        
        // App icon/logo
        Label logoLabel = new Label("📦");
        logoLabel.setStyle("-fx-font-size: 64px;");
        
        // App name
        Label appName = new Label("BackupForce");
        appName.setStyle("-fx-font-size: 28px; -fx-font-weight: 700; -fx-text-fill: #f0f0f0;");
        
        // Version
        Label versionLabel = new Label("Version " + APP_VERSION);
        versionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #808080;");
        
        // Description
        Label descLabel = new Label("A powerful Salesforce data backup tool with support for\nCSV exports and direct database integration.");
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #a0a0a0; -fx-text-alignment: center;");
        descLabel.setWrapText(true);
        
        // Separator
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333333;");
        
        // Developer info
        VBox devInfo = new VBox(8);
        devInfo.setAlignment(Pos.CENTER);
        
        Label developedBy = new Label("Developed by");
        developedBy.setStyle("-fx-font-size: 12px; -fx-text-fill: #808080;");
        
        Label developerName = new Label(DEVELOPER_NAME);
        developerName.setStyle("-fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: #f0f0f0;");
        
        Label emailLabel = new Label("✉ " + DEVELOPER_EMAIL);
        emailLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2a8bd8; -fx-cursor: hand;");
        emailLabel.setOnMouseClicked(e -> openUrl("mailto:" + DEVELOPER_EMAIL));
        emailLabel.setOnMouseEntered(e -> emailLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #4fc3f7; -fx-cursor: hand; -fx-underline: true;"));
        emailLabel.setOnMouseExited(e -> emailLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2a8bd8; -fx-cursor: hand;"));
        
        Hyperlink githubLink = new Hyperlink("🔗 View on GitHub");
        githubLink.setStyle("-fx-font-size: 13px; -fx-text-fill: #2a8bd8;");
        githubLink.setOnAction(e -> openUrl(GITHUB_URL));
        
        devInfo.getChildren().addAll(developedBy, developerName, emailLabel, githubLink);
        
        // Tech info
        Label techLabel = new Label("Built with Java 21 • JavaFX 21 • Apache HttpClient 5");
        techLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #606060; -fx-padding: 10 0 0 0;");
        
        // Copyright
        Label copyright = new Label("© 2025 " + DEVELOPER_NAME + ". All rights reserved.");
        copyright.setStyle("-fx-font-size: 11px; -fx-text-fill: #505050;");
        
        content.getChildren().addAll(logoLabel, appName, versionLabel, descLabel, sep, devInfo, techLabel, copyright);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("-fx-background-color: #1a1a1a;");
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setStyle(
            "-fx-background-color: #2a8bd8; -fx-text-fill: white; -fx-font-weight: 600; -fx-padding: 8 24; -fx-background-radius: 6;"
        );
        
        dialog.showAndWait();
    }
    
    private void openUrl(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            logger.error("Failed to open URL: {}", url, e);
        }
    }
    
    private void styleAlert(Alert alert) {
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #1a1a1a;");
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: #e0e0e0;");
        dialogPane.lookup(".header-panel").setStyle("-fx-background-color: #252525;");
        dialogPane.lookup(".header-panel .label").setStyle("-fx-text-fill: #f0f0f0;");
    }
    
    private void openDatabaseSettings(String connectionId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/connections.fxml"));
            Parent root = loader.load();
            
            ConnectionsController controller = loader.getController();
            controller.setConnectionInfo(connectionInfo);
            
            // Navigate to connections screen (same window, not modal)
            Scene scene = new Scene(root, 1100, 750);
            Stage stage = (Stage) settingsMenuButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(950);
            stage.setMinHeight(650);
            stage.centerOnScreen();
            
        } catch (Exception e) {
            logger.error("Failed to open connections screen", e);
            if (statusLabel != null) {
                statusLabel.setText("Error opening connections: " + e.getMessage());
            }
        }
    }
    
    public void setConnectionInfo(ConnectionInfo connectionInfo) {
        this.connectionInfo = connectionInfo;
        
        // Update UI with connection info
        if (connectionInfo != null) {
            if (usernameLabel != null) {
                usernameLabel.setText(connectionInfo.getUsername());
            }
            if (instanceLabel != null) {
                instanceLabel.setText(connectionInfo.getInstanceUrl());
            }
            if (statusLabel != null) {
                statusLabel.setText("Connected to Salesforce");
            }
            logger.info("Home page loaded for user: {}", connectionInfo.getUsername());
            
            // Fetch available objects count from Salesforce
            fetchObjectCount();
        }
    }
    
    private void fetchObjectCount() {
        if (objectsAvailableLabel != null) {
            objectsAvailableLabel.setText("...");
        }
        
        Task<Integer> fetchTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                DescribeGlobalResult dgr = connectionInfo.getConnection().describeGlobal();
                DescribeGlobalSObjectResult[] sobjects = dgr.getSobjects();
                
                int queryableCount = 0;
                for (DescribeGlobalSObjectResult sobject : sobjects) {
                    if (sobject.isQueryable()) {
                        queryableCount++;
                    }
                }
                return queryableCount;
            }
        };
        
        fetchTask.setOnSucceeded(e -> {
            int count = fetchTask.getValue();
            Platform.runLater(() -> {
                if (objectsAvailableLabel != null) {
                    objectsAvailableLabel.setText(String.valueOf(count));
                }
                // Save to preferences for next time
                prefs.putInt("availableObjectCount", count);
                logger.info("Loaded {} queryable objects from Salesforce", count);
            });
        });
        
        fetchTask.setOnFailed(e -> {
            logger.warn("Failed to fetch object count", fetchTask.getException());
            Platform.runLater(() -> {
                if (objectsAvailableLabel != null) {
                    objectsAvailableLabel.setText("--");
                }
            });
        });
        
        Thread thread = new Thread(fetchTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    @FXML
    private void handleDataBackup(MouseEvent event) {
        logger.info("User selected Data Backup");
        navigateToBackup("data");
    }
    
    @FXML
    private void handleDataRestore(MouseEvent event) {
        logger.info("User selected Data Restore");
        navigateToRestore();
    }
    
    @FXML
    private void handleConfigBackup(MouseEvent event) {
        logger.info("User selected Configuration Backup");
        navigateToBackup("config");
    }
    
    @FXML
    private void handleMetadataBackup(MouseEvent event) {
        logger.info("User selected Metadata Backup");
        navigateToBackup("metadata");
    }
    
    private void navigateToBackup(String backupType) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/backup.fxml"));
            Parent root = loader.load();
            
            BackupController controller = loader.getController();
            controller.setConnectionInfo(connectionInfo);
            controller.setBackupType(backupType);
            
            Scene scene = new Scene(root, 1100, 750);
            
            Stage stage = (Stage) dataBackupCard.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(950);
            stage.setMinHeight(650);
            stage.setMaximized(true);
            
        } catch (Exception e) {
            logger.error("Failed to navigate to backup screen", e);
            if (statusLabel != null) {
                statusLabel.setText("Error: " + e.getMessage());
            }
        }
    }
    
    private void navigateToRestore() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/restore.fxml"));
            Parent root = loader.load();
            
            RestoreController controller = loader.getController();
            controller.setConnectionInfo(connectionInfo);
            
            Scene scene = new Scene(root, 1100, 750);
            
            Stage stage = (Stage) dataRestoreCard.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(950);
            stage.setMinHeight(650);
            stage.setMaximized(true);
            
        } catch (Exception e) {
            logger.error("Failed to navigate to restore screen", e);
            if (statusLabel != null) {
                statusLabel.setText("Error: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void handleLogout() {
        logger.info("User logged out");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            
            Scene scene = new Scene(root, 600, 750);
            
            Stage stage = (Stage) usernameLabel.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(550);
            stage.setMinHeight(650);
            stage.setMaximized(true);
            
        } catch (Exception e) {
            logger.error("Failed to navigate to login screen", e);
        }
    }
    
    private String getIconForDatabaseType(String type) {
        if (type == null) return "🗄️";
        String lowerType = type.toLowerCase();
        if (lowerType.equals("snowflake")) {
            return "❄️";
        } else if (lowerType.equals("sqlserver") || lowerType.equals("sql server")) {
            return "🔷";
        } else if (lowerType.equals("postgresql")) {
            return "🐘";
        }
        return "🗄️";
    }
}
