package com.backupforce.ui;

/**
 * Controller for the Restore content panel.
 * This is a wrapper that provides the restore functionality within the main container.
 */
public class RestoreContentController {
    
    private MainController mainController;
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Refresh restore panel data
     */
    public void refresh() {
        // Refresh restore-related data when panel is shown
    }
}
