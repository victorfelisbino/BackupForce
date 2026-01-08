package com.backupforce.ui;

import com.backupforce.config.SSLHelper;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupForceApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(BackupForceApp.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            // Disable SSL validation globally at application startup for Snowflake connections
            SSLHelper.disableSSLValidation();
            
            // Load root application container (handles login/app transitions smoothly)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/app-root.fxml"));
            Parent root = loader.load();
            
            // Pass stage reference to AppController for window controls
            AppController appController = loader.getController();
            appController.setStage(primaryStage);
            
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/backupforce-modern.css").toExternalForm());
            
            // Use undecorated stage for custom title bar (removes ugly Windows chrome)
            primaryStage.initStyle(StageStyle.UNDECORATED);
            primaryStage.setTitle("BackupForce - Salesforce Backup Tool");
            
            // Set application icon
            try {
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/backupforce-icon.png")));
            } catch (Exception e) {
                logger.warn("Failed to load application icon", e);
            }
            
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(550);
            primaryStage.setMinHeight(650);
            
            // Get the visual bounds (screen area minus taskbar)
            Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
            primaryStage.setX(visualBounds.getMinX());
            primaryStage.setY(visualBounds.getMinY());
            primaryStage.setWidth(visualBounds.getWidth());
            primaryStage.setHeight(visualBounds.getHeight());
            
            // Tell AppController we're in "maximized" state (for toggle behavior)
            appController.setMaximizedState(true, visualBounds);
            
            primaryStage.show();
            
            logger.info("BackupForce application started");
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Set system properties to disable SSL validation for Snowflake JDBC
        // This must happen BEFORE any SSL context is initialized
        System.setProperty("javax.net.ssl.trustStore", "");
        System.setProperty("javax.net.ssl.trustStorePassword", "");
        System.setProperty("com.sun.net.ssl.checkRevocation", "false");
        
        launch(args);
    }
}
