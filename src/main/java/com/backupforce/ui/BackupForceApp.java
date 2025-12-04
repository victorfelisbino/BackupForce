package com.backupforce.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupForceApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(BackupForceApp.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            
            Scene scene = new Scene(root, 500, 400);
            // scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            primaryStage.setTitle("BackupForce - Salesforce Backup Tool");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
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
