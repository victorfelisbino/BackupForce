package com.backupforce.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * Controller for the Preferences content panel.
 * Manages application-wide settings and user preferences.
 */
public class PreferencesContentController {
    private static final Logger logger = LoggerFactory.getLogger(PreferencesContentController.class);
    
    private static final String PREFS_NODE = "com.backupforce";
    
    // Appearance
    @FXML private ComboBox<String> themeCombo;
    @FXML private ComboBox<String> fontSizeCombo;
    
    // Backup Defaults
    @FXML private ComboBox<String> outputFormatCombo;
    @FXML private TextField defaultBackupPath;
    @FXML private Spinner<Integer> threadSpinner;
    @FXML private CheckBox includeArchivedCheck;
    @FXML private CheckBox downloadBlobsCheck;
    @FXML private CheckBox verifyAfterBackupCheck;
    
    // Object Category Filters - what to include in backups
    @FXML private CheckBox includeApexCodeCheck;       // ApexClass, ApexTrigger, ApexComponent, ApexPage
    @FXML private CheckBox includeApexLogsCheck;       // ApexLog, ApexTestResult, etc.
    @FXML private CheckBox includeFeedObjectsCheck;    // *Feed objects (Chatter feeds)
    @FXML private CheckBox includeHistoryObjectsCheck; // *History objects (field history tracking)
    @FXML private CheckBox includeShareObjectsCheck;   // *Share objects (sharing entries)
    @FXML private CheckBox includeSetupObjectsCheck;   // Setup and configuration objects
    @FXML private CheckBox includeManagedPkgCheck;     // Managed package objects (*__*)
    
    // Restore Defaults
    @FXML private ComboBox<String> restoreOpCombo;
    @FXML private Spinner<Integer> batchSpinner;
    @FXML private CheckBox validateBeforeRestoreCheck;
    
    // Notifications
    @FXML private CheckBox notifyCompleteCheck;
    @FXML private CheckBox notifyErrorCheck;
    @FXML private CheckBox playSoundCheck;
    
    // Advanced
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private ComboBox<String> logLevelCombo;
    @FXML private CheckBox enableMetricsCheck;
    
    @FXML private Button saveButton;
    
    private MainController mainController;
    private Preferences prefs;
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    @FXML
    public void initialize() {
        prefs = Preferences.userRoot().node(PREFS_NODE);
        
        // Populate ComboBox items
        themeCombo.getItems().addAll("Dark (Default)", "Light", "System");
        fontSizeCombo.getItems().addAll("Small (12px)", "Medium (14px)", "Large (16px)");
        outputFormatCombo.getItems().addAll("CSV", "JSON", "Parquet");
        restoreOpCombo.getItems().addAll("Insert Only", "Upsert", "Update Only");
        logLevelCombo.getItems().addAll("ERROR", "WARN", "INFO", "DEBUG");
        
        // Setup spinners with value factories
        // Parallel threads: 1-15 (Salesforce allows 100 concurrent Bulk API jobs per org, we recommend max 15)
        threadSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 15, 15));
        batchSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 2000, 200));
        timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(30, 600, 120));
        
        loadPreferences();
    }
    
    /**
     * Load preferences from storage
     */
    public void loadPreferences() {
        try {
            // Appearance
            themeCombo.setValue(prefs.get("theme", "Dark (Default)"));
            fontSizeCombo.setValue(prefs.get("fontSize", "Medium (14px)"));
            
            // Backup Defaults
            outputFormatCombo.setValue(prefs.get("outputFormat", "CSV"));
            defaultBackupPath.setText(prefs.get("defaultBackupPath", System.getProperty("user.home") + File.separator + "BackupForce"));
            threadSpinner.getValueFactory().setValue(prefs.getInt("threads", 15));
            includeArchivedCheck.setSelected(prefs.getBoolean("includeArchived", false));
            downloadBlobsCheck.setSelected(prefs.getBoolean("downloadBlobs", true));
            verifyAfterBackupCheck.setSelected(prefs.getBoolean("verifyAfterBackup", true));
            
            // Object Category Filters - defaults favor more complete backups
            includeApexCodeCheck.setSelected(prefs.getBoolean("includeApexCode", true));          // ON by default - your code is valuable
            includeApexLogsCheck.setSelected(prefs.getBoolean("includeApexLogs", false));         // OFF by default - transient data
            includeFeedObjectsCheck.setSelected(prefs.getBoolean("includeFeedObjects", false));   // OFF by default - can be very large
            includeHistoryObjectsCheck.setSelected(prefs.getBoolean("includeHistoryObjects", false)); // OFF by default - can be large
            includeShareObjectsCheck.setSelected(prefs.getBoolean("includeShareObjects", false)); // OFF by default - auto-managed
            includeSetupObjectsCheck.setSelected(prefs.getBoolean("includeSetupObjects", false)); // OFF by default - use Metadata API
            includeManagedPkgCheck.setSelected(prefs.getBoolean("includeManagedPkg", true));      // ON by default - include installed packages
            
            // Restore Defaults
            restoreOpCombo.setValue(prefs.get("restoreOp", "Insert Only"));
            batchSpinner.getValueFactory().setValue(prefs.getInt("batchSize", 200));
            validateBeforeRestoreCheck.setSelected(prefs.getBoolean("validateBeforeRestore", true));
            
            // Notifications
            notifyCompleteCheck.setSelected(prefs.getBoolean("notifyComplete", true));
            notifyErrorCheck.setSelected(prefs.getBoolean("notifyError", true));
            playSoundCheck.setSelected(prefs.getBoolean("playSound", false));
            
            // Advanced
            timeoutSpinner.getValueFactory().setValue(prefs.getInt("apiTimeout", 120));
            logLevelCombo.setValue(prefs.get("logLevel", "INFO"));
            enableMetricsCheck.setSelected(prefs.getBoolean("enableMetrics", true));
            
            logger.info("Loaded preferences");
        } catch (Exception e) {
            logger.error("Failed to load preferences", e);
        }
    }
    
    @FXML
    private void handleSavePreferences() {
        try {
            // Appearance
            prefs.put("theme", themeCombo.getValue());
            prefs.put("fontSize", fontSizeCombo.getValue());
            
            // Backup Defaults
            prefs.put("outputFormat", outputFormatCombo.getValue());
            prefs.put("defaultBackupPath", defaultBackupPath.getText());
            prefs.putInt("threads", threadSpinner.getValue());
            prefs.putBoolean("includeArchived", includeArchivedCheck.isSelected());
            prefs.putBoolean("downloadBlobs", downloadBlobsCheck.isSelected());
            prefs.putBoolean("verifyAfterBackup", verifyAfterBackupCheck.isSelected());
            
            // Object Category Filters
            prefs.putBoolean("includeApexCode", includeApexCodeCheck.isSelected());
            prefs.putBoolean("includeApexLogs", includeApexLogsCheck.isSelected());
            prefs.putBoolean("includeFeedObjects", includeFeedObjectsCheck.isSelected());
            prefs.putBoolean("includeHistoryObjects", includeHistoryObjectsCheck.isSelected());
            prefs.putBoolean("includeShareObjects", includeShareObjectsCheck.isSelected());
            prefs.putBoolean("includeSetupObjects", includeSetupObjectsCheck.isSelected());
            prefs.putBoolean("includeManagedPkg", includeManagedPkgCheck.isSelected());
            
            // Restore Defaults
            prefs.put("restoreOp", restoreOpCombo.getValue());
            prefs.putInt("batchSize", batchSpinner.getValue());
            prefs.putBoolean("validateBeforeRestore", validateBeforeRestoreCheck.isSelected());
            
            // Notifications
            prefs.putBoolean("notifyComplete", notifyCompleteCheck.isSelected());
            prefs.putBoolean("notifyError", notifyErrorCheck.isSelected());
            prefs.putBoolean("playSound", playSoundCheck.isSelected());
            
            // Advanced
            prefs.putInt("apiTimeout", timeoutSpinner.getValue());
            prefs.put("logLevel", logLevelCombo.getValue());
            prefs.putBoolean("enableMetrics", enableMetricsCheck.isSelected());
            
            prefs.flush();
            
            logger.info("Saved preferences");
            showSuccess("Preferences saved successfully.");
            
        } catch (Exception e) {
            logger.error("Failed to save preferences", e);
            showError("Failed to save preferences: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleBrowseBackupPath() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Default Backup Location");
        
        String currentPath = defaultBackupPath.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File dir = new File(currentPath);
            if (dir.exists()) {
                chooser.setInitialDirectory(dir);
            }
        }
        
        Stage stage = (Stage) defaultBackupPath.getScene().getWindow();
        File selectedDir = chooser.showDialog(stage);
        
        if (selectedDir != null) {
            defaultBackupPath.setText(selectedDir.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleResetDefaults() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset Preferences");
        confirm.setHeaderText("Reset to Default Settings?");
        confirm.setContentText("This will reset all preferences to their default values. Continue?");
        
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    prefs.clear();
                    loadPreferences();
                    logger.info("Reset preferences to defaults");
                    showSuccess("Preferences reset to defaults.");
                } catch (Exception e) {
                    logger.error("Failed to reset preferences", e);
                    showError("Failed to reset preferences: " + e.getMessage());
                }
            }
        });
    }
    
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
