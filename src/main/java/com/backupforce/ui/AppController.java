package com.backupforce.ui;

import com.backupforce.ui.LoginController.ConnectionInfo;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Root application controller that manages transitions between login and main app.
 * This ensures smooth transitions without window resizing.
 */
public class AppController {
    private static final Logger logger = LoggerFactory.getLogger(AppController.class);
    
    @FXML private VBox rootVBox;
    @FXML private HBox titleBar;
    @FXML private StackPane rootPane;
    
    private Node loginView;
    private Node mainAppView;
    private LoginContentController loginController;
    private MainController mainController;
    
    // For window dragging
    private Stage stage;
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = true;
    private double prevX, prevY, prevWidth, prevHeight;
    
    @FXML
    public void initialize() {
        // Load and show login view on startup
        showLogin();
    }
    
    /**
     * Set the stage reference for window controls
     */
    public void setStage(Stage stage) {
        this.stage = stage;
        this.isMaximized = stage.isMaximized();
    }
    
    // ===== Custom Title Bar Handlers =====
    
    @FXML
    private void handleTitleBarPressed(MouseEvent event) {
        if (stage != null) {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        }
    }
    
    @FXML
    private void handleTitleBarDragged(MouseEvent event) {
        if (stage != null) {
            // If maximized, restore first then drag
            if (isMaximized) {
                isMaximized = false;
                stage.setMaximized(false);
                // Adjust position so window appears under mouse
                stage.setWidth(prevWidth > 0 ? prevWidth : 1200);
                stage.setHeight(prevHeight > 0 ? prevHeight : 800);
                xOffset = stage.getWidth() / 2;
            }
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        }
    }
    
    @FXML
    private void handleMinimize() {
        if (stage != null) {
            stage.setIconified(true);
        }
    }
    
    @FXML
    private void handleMaximize() {
        if (stage != null) {
            if (isMaximized) {
                // Restore to previous size
                stage.setMaximized(false);
                if (prevWidth > 0 && prevHeight > 0) {
                    stage.setX(prevX);
                    stage.setY(prevY);
                    stage.setWidth(prevWidth);
                    stage.setHeight(prevHeight);
                }
                isMaximized = false;
            } else {
                // Save current size and maximize
                prevX = stage.getX();
                prevY = stage.getY();
                prevWidth = stage.getWidth();
                prevHeight = stage.getHeight();
                stage.setMaximized(true);
                isMaximized = true;
            }
        }
    }
    
    @FXML
    private void handleClose() {
        logger.info("Application closing...");
        Platform.exit();
    }
    
    /**
     * Show the login view
     */
    public void showLogin() {
        try {
            if (loginView == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login-content.fxml"));
                loginView = loader.load();
                loginController = loader.getController();
                loginController.setAppController(this);
            }
            
            transitionTo(loginView);
            logger.info("Showing login view");
            
        } catch (IOException e) {
            logger.error("Failed to load login view", e);
        }
    }
    
    /**
     * Show the main application view after successful login
     */
    public void showMainApp(ConnectionInfo connectionInfo) {
        try {
            // Always create fresh main view to ensure clean state
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-app.fxml"));
            mainAppView = loader.load();
            mainController = loader.getController();
            mainController.setAppController(this);
            mainController.setConnectionInfo(connectionInfo);
            
            transitionTo(mainAppView);
            logger.info("Showing main app view");
            
        } catch (IOException e) {
            logger.error("Failed to load main app view", e);
        }
    }
    
    /**
     * Logout and return to login
     */
    public void logout() {
        // Clear main app state
        mainAppView = null;
        mainController = null;
        
        showLogin();
    }
    
    /**
     * Smooth transition between views
     */
    private void transitionTo(Node newView) {
        if (rootPane.getChildren().isEmpty()) {
            // First load - just add directly
            rootPane.getChildren().add(newView);
        } else {
            // Fade transition
            Node oldView = rootPane.getChildren().get(0);
            
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), oldView);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            
            fadeOut.setOnFinished(e -> {
                rootPane.getChildren().clear();
                newView.setOpacity(0);
                rootPane.getChildren().add(newView);
                
                FadeTransition fadeIn = new FadeTransition(Duration.millis(150), newView);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            
            fadeOut.play();
        }
    }
}
