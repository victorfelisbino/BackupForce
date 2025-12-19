package com.backupforce.ui;

/**
 * Controller for the Backup content panel.
 * This is a wrapper that provides the backup functionality within the main container.
 */
public class BackupContentController {
    
    private MainController mainController;
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Refresh backup panel data
     */
    public void refresh() {
        // Refresh backup-related data when panel is shown
    }
}
