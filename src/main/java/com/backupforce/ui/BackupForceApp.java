package com.backupforce.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupForceApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(BackupForceApp.class);

    @Override
    public void start(Stage primaryStage) {
        try {
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
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(550);
            primaryStage.setMinHeight(650);
            primaryStage.setMaximized(true);
            primaryStage.show();
            
            logger.info("BackupForce application started");
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
