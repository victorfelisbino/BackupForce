package com.backupforce.ui;

import com.backupforce.ui.LoginController.ConnectionInfo;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main controller that manages the application shell and content navigation.
 * This provides a single-window architecture where content is swapped in the center area.
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    @FXML private BorderPane rootContainer;
    @FXML private VBox sidebar;
    @FXML private StackPane contentArea;
    @FXML private Label usernameLabel;
    @FXML private Label instanceLabel;
    
    // Navigation items
    @FXML private HBox navDashboard;
    @FXML private HBox navBackup;
    @FXML private HBox navRestore;
    @FXML private HBox navSchedule;
    @FXML private HBox navConnections;
    @FXML private HBox navPreferences;
    @FXML private HBox navAbout;
    
    private ConnectionInfo connectionInfo;
    private HBox activeNavItem;
    
    // Reference to parent AppController for logout
    private AppController appController;
    
    public void setAppController(AppController appController) {
        this.appController = appController;
    }
    
    // Content controllers (cached for state preservation)
    private Node dashboardContent;
    private Node backupContent;
    private Node restoreContent;
    private Node scheduleContent;
    private Node connectionsContent;
    private Node preferencesContent;
    
    // Controllers
    private DashboardContentController dashboardController;
    private BackupController backupController;
    private RestoreController restoreController;
    private ScheduleController scheduleController;
    private ConnectionsContentController connectionsController;
    private PreferencesContentController preferencesController;
    
    @FXML
    public void initialize() {
        activeNavItem = navDashboard;
    }
    
    /**
     * Initialize the main container with connection info and load dashboard
     */
    public void setConnectionInfo(ConnectionInfo connectionInfo) {
        this.connectionInfo = connectionInfo;
        
        // Update user info in sidebar
        if (connectionInfo != null) {
            if (usernameLabel != null) {
                usernameLabel.setText(connectionInfo.getUsername());
            }
            if (instanceLabel != null) {
                String url = connectionInfo.getInstanceUrl();
                // Extract just the domain for display
                if (url != null && url.contains("://")) {
                    url = url.substring(url.indexOf("://") + 3);
                    if (url.contains("/")) {
                        url = url.substring(0, url.indexOf("/"));
                    }
                }
                instanceLabel.setText(url != null ? url : "Connected");
            }
        }
        
        // Load dashboard as initial content
        navigateToDashboard();
    }
    
    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }
    
    /**
     * Navigate to Dashboard
     */
    @FXML
    public void navigateToDashboard() {
        setActiveNav(navDashboard);
        try {
            if (dashboardContent == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard-content.fxml"));
                dashboardContent = loader.load();
                dashboardController = loader.getController();
                dashboardController.setMainController(this);
            }
            dashboardController.refresh();
            setContent(dashboardContent);
            logger.info("Navigated to Dashboard");
        } catch (IOException e) {
            logger.error("Failed to load dashboard", e);
            showError("Failed to load dashboard: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to Backup
     */
    @FXML
    public void navigateToBackup() {
        setActiveNav(navBackup);
        try {
            if (backupContent == null) {
                // Load the content-only FXML (no sidebar, no navigation handlers)
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/backup-content.fxml"));
                backupContent = loader.load();
                backupController = loader.getController();
                backupController.setConnectionInfo(connectionInfo);
            }
            setContent(backupContent);
            logger.info("Navigated to Backup");
        } catch (IOException e) {
            logger.error("Failed to load backup", e);
            showError("Failed to load backup: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to Restore
     */
    @FXML
    public void navigateToRestore() {
        setActiveNav(navRestore);
        try {
            if (restoreContent == null) {
                // Load the content-only FXML (no sidebar, no navigation handlers)
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/restore-content.fxml"));
                restoreContent = loader.load();
                restoreController = loader.getController();
                restoreController.setConnectionInfo(connectionInfo);
            }
            setContent(restoreContent);
            logger.info("Navigated to Restore");
        } catch (IOException e) {
            logger.error("Failed to load restore", e);
            showError("Failed to load restore: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to Schedule
     */
    @FXML
    public void navigateToSchedule() {
        setActiveNav(navSchedule);
        try {
            if (scheduleContent == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/schedule-content.fxml"));
                scheduleContent = loader.load();
                scheduleController = loader.getController();
            }
            setContent(scheduleContent);
            logger.info("Navigated to Schedules");
        } catch (IOException e) {
            logger.error("Failed to load schedules", e);
            showError("Failed to load schedules: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to Connections
     */
    @FXML
    public void navigateToConnections() {
        setActiveNav(navConnections);
        try {
            if (connectionsContent == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/connections-content.fxml"));
                connectionsContent = loader.load();
                connectionsController = loader.getController();
                connectionsController.setMainController(this);
            }
            connectionsController.refresh();
            setContent(connectionsContent);
            logger.info("Navigated to Connections");
        } catch (IOException e) {
            logger.error("Failed to load connections", e);
            showError("Failed to load connections: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to Preferences
     */
    @FXML
    public void navigateToPreferences() {
        setActiveNav(navPreferences);
        try {
            if (preferencesContent == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/preferences-content.fxml"));
                preferencesContent = loader.load();
                preferencesController = loader.getController();
                preferencesController.setMainController(this);
            }
            preferencesController.loadPreferences();
            setContent(preferencesContent);
            logger.info("Navigated to Preferences");
        } catch (IOException e) {
            logger.error("Failed to load preferences", e);
            showError("Failed to load preferences: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to About
     */
    @FXML
    public void navigateToAbout() {
        setActiveNav(navAbout);
        showAboutDialog();
    }
    
    /**
     * Handle logout
     */
    @FXML
    public void handleLogout() {
        logger.info("User logged out");
        
        // Use AppController for smooth transition back to login
        if (appController != null) {
            appController.logout();
        } else {
            // Fallback: load login scene directly (old behavior)
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
                Parent root = loader.load();
                
                Scene scene = new Scene(root);
                Stage stage = (Stage) rootContainer.getScene().getWindow();
                stage.setScene(scene);
                stage.setMaximized(true);
                
            } catch (IOException e) {
                logger.error("Failed to logout", e);
                showError("Failed to return to login: " + e.getMessage());
            }
        }
    }
    
    /**
     * Set the active navigation item (updates visual state)
     */
    private void setActiveNav(HBox navItem) {
        if (activeNavItem != null) {
            activeNavItem.getStyleClass().remove("nav-item-active");
        }
        if (navItem != null) {
            if (!navItem.getStyleClass().contains("nav-item-active")) {
                navItem.getStyleClass().add("nav-item-active");
            }
            activeNavItem = navItem;
        }
    }
    
    /**
     * Set the content in the main content area
     */
    private void setContent(Node content) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(content);
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About BackupForce");
        alert.setHeaderText("BackupForce v2.3.0");
        alert.setContentText(
            "Salesforce Backup & Restore Tool\n\n" +
            "Developed by Victor Felisbino\n" +
            "© 2024 All rights reserved.\n\n" +
            "A professional tool for backing up and restoring\n" +
            "Salesforce data to CSV files or databases."
        );
        alert.showAndWait();
    }
}
