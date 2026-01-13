package com.backupforce.ui;

import com.backupforce.config.BackupHistory;
import com.backupforce.config.BackupHistory.BackupRun;
import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.ui.LoginController.ConnectionInfo;
import com.sforce.soap.partner.DescribeGlobalResult;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Controller for the Dashboard content panel
 */
public class DashboardContentController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardContentController.class);
    private static final Preferences prefs = Preferences.userNodeForPackage(DashboardContentController.class);
    
    @FXML private Region statusDot;
    @FXML private Label statusLabel;
    @FXML private Label lastBackupLabel;
    @FXML private Label totalBackupsLabel;
    @FXML private Label objectsAvailableLabel;
    @FXML private Label lastDataBackupLabel;
    @FXML private Label lastBackupRowsLabel;
    @FXML private Label backupObjectsLabel;
    @FXML private Label backupRowsLabel;
    @FXML private Label lastBackupBadge;
    @FXML private Label restoreSourcesLabel;
    @FXML private Label lastRestoreRowsLabel;
    @FXML private Label lastDataRestoreLabel;
    @FXML private Label noConnectionsLabel;
    @FXML private HBox connectionsContainer;
    @FXML private VBox dataBackupCard;
    @FXML private VBox dataRestoreCard;
    @FXML private VBox configBackupCard;
    @FXML private VBox metadataBackupCard;
    
    // New backup summary fields
    @FXML private VBox backupHealthCard;
    @FXML private Label backupHealthLabel;
    @FXML private VBox lastBackupSummaryPanel;
    @FXML private Label lastBackupSummaryIcon;
    @FXML private Label lastBackupSummaryTitle;
    @FXML private Label lastBackupSummaryDetails;
    @FXML private Button verifyLastBackupButton;
    @FXML private Label summaryObjectsLabel;
    @FXML private Label summaryRecordsLabel;
    @FXML private Label summarySizeLabel;
    @FXML private Label summaryDurationLabel;
    @FXML private Label summaryDestinationLabel;
    
    private MainController mainController;
    private BackupRun lastBackupRun;
    
    @FXML
    public void initialize() {
        // Setup card hover effects
        setupCardHoverEffect(dataBackupCard);
        setupCardHoverEffect(dataRestoreCard);
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Refresh dashboard data
     */
    public void refresh() {
        loadRecentActivity();
        loadSavedConnections();
        loadDataBackupMetrics();
        loadBackupHistorySummary();
        fetchObjectCount();
        updateConnectionStatus();
    }
    
    private void updateConnectionStatus() {
        ConnectionInfo connInfo = mainController != null ? mainController.getConnectionInfo() : null;
        if (connInfo != null && statusLabel != null) {
            statusLabel.setText("Connected to Salesforce");
            if (statusDot != null) {
                statusDot.getStyleClass().removeAll("status-dot-disconnected");
                if (!statusDot.getStyleClass().contains("status-dot-connected")) {
                    statusDot.getStyleClass().add("status-dot-connected");
                }
            }
        }
    }
    
    private void loadRecentActivity() {
        try {
            String lastBackup = prefs.get("lastBackupDate", "No backups yet");
            int totalBackups = prefs.getInt("totalBackups", 0);
            
            if (lastBackupLabel != null) {
                lastBackupLabel.setText("Last backup: " + lastBackup);
            }
            if (totalBackupsLabel != null) {
                totalBackupsLabel.setText(totalBackups + " total backups");
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
                
                if (connectionsContainer != null) {
                    connectionsContainer.getChildren().removeIf(node -> node instanceof VBox);
                    
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
        chip.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8; -fx-border-color: #30363d; " +
                     "-fx-border-radius: 8; -fx-padding: 12; -fx-min-width: 180;");
        
        Label iconLabel = new Label(getIconForDatabaseType(conn.getType()));
        iconLabel.setStyle("-fx-font-size: 20px;");
        
        Label nameLabel = new Label(conn.getName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #e6edf3;");
        
        Label typeLabel = new Label(conn.getType());
        typeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8b949e;");
        
        chip.getChildren().addAll(iconLabel, nameLabel, typeLabel);
        return chip;
    }
    
    private void loadDataBackupMetrics() {
        int objectCount = prefs.getInt("availableObjectCount", 0);
        String lastDataBackup = prefs.get("lastDataBackupDate", null);
        long lastRowCount = prefs.getLong("lastBackupRowCount", 0);
        
        if (objectsAvailableLabel != null) {
            objectsAvailableLabel.setText(objectCount > 0 ? String.valueOf(objectCount) : "--");
        }
        
        if (lastBackupRowsLabel != null) {
            lastBackupRowsLabel.setText(lastRowCount > 0 ? formatRowCount(lastRowCount) : "--");
        }
        
        if (lastDataBackupLabel != null) {
            lastDataBackupLabel.setText(lastDataBackup != null ? lastDataBackup : "Never");
        }
        
        if (backupObjectsLabel != null) {
            backupObjectsLabel.setText(objectCount > 0 ? String.valueOf(objectCount) : "--");
        }
        
        if (backupRowsLabel != null) {
            backupRowsLabel.setText(lastRowCount > 0 ? formatRowCount(lastRowCount) : "--");
        }
    }
    
    /**
     * Load backup history summary from BackupHistory
     */
    private void loadBackupHistorySummary() {
        try {
            BackupHistory history = BackupHistory.getInstance();
            List<BackupRun> allBackups = history.getHistory();
            
            if (allBackups.isEmpty()) {
                // No backups yet
                if (backupHealthLabel != null) {
                    backupHealthLabel.setText("No backups");
                    backupHealthLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #8b949e;");
                }
                if (lastBackupSummaryPanel != null) {
                    lastBackupSummaryPanel.setVisible(false);
                    lastBackupSummaryPanel.setManaged(false);
                }
                return;
            }
            
            // Get the most recent backup
            lastBackupRun = allBackups.get(0);
            
            // Update backup health indicator
            updateBackupHealthStatus(lastBackupRun);
            
            // Show and populate the summary panel
            if (lastBackupSummaryPanel != null) {
                lastBackupSummaryPanel.setVisible(true);
                lastBackupSummaryPanel.setManaged(true);
                
                populateBackupSummary(lastBackupRun);
            }
            
            // Update stats labels with real data
            if (lastBackupRowsLabel != null) {
                lastBackupRowsLabel.setText(formatRowCount(lastBackupRun.getTotalRecords()));
            }
            
            if (lastDataBackupLabel != null && lastBackupRun.getStartTime() != null) {
                lastDataBackupLabel.setText(formatRelativeTime(lastBackupRun.getStartTime()));
            }
            
            // Store for prefs too
            prefs.putLong("lastBackupRowCount", lastBackupRun.getTotalRecords());
            
        } catch (Exception e) {
            logger.warn("Failed to load backup history summary", e);
        }
    }
    
    private void updateBackupHealthStatus(BackupRun run) {
        if (backupHealthLabel == null) return;
        
        String status = run.getStatus();
        if ("COMPLETED".equals(status)) {
            int failed = run.getFailedObjects();
            if (failed == 0) {
                backupHealthLabel.setText("✓ Healthy");
                backupHealthLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #28a745;");
            } else {
                backupHealthLabel.setText("⚠ " + failed + " failed");
                backupHealthLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #ffc107;");
            }
        } else if ("FAILED".equals(status)) {
            backupHealthLabel.setText("✗ Failed");
            backupHealthLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #dc3545;");
        } else if ("RUNNING".equals(status)) {
            backupHealthLabel.setText("⏳ Running");
            backupHealthLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #58a6ff;");
        } else if ("CANCELLED".equals(status)) {
            backupHealthLabel.setText("⏹ Cancelled");
            backupHealthLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #8b949e;");
        } else {
            backupHealthLabel.setText("--");
            backupHealthLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #8b949e;");
        }
    }
    
    private void populateBackupSummary(BackupRun run) {
        // Icon and title based on status
        String status = run.getStatus();
        if ("COMPLETED".equals(status)) {
            if (lastBackupSummaryIcon != null) {
                int failed = run.getFailedObjects();
                if (failed == 0) {
                    lastBackupSummaryIcon.setText("✓");
                    lastBackupSummaryIcon.setStyle("-fx-font-size: 20px; -fx-text-fill: #28a745;");
                } else {
                    lastBackupSummaryIcon.setText("⚠");
                    lastBackupSummaryIcon.setStyle("-fx-font-size: 20px; -fx-text-fill: #ffc107;");
                }
            }
            if (lastBackupSummaryTitle != null) {
                int failed = run.getFailedObjects();
                if (failed == 0) {
                    lastBackupSummaryTitle.setText("Last Backup Successful");
                } else {
                    lastBackupSummaryTitle.setText("Last Backup Completed with Warnings");
                }
            }
        } else if ("FAILED".equals(status)) {
            if (lastBackupSummaryIcon != null) {
                lastBackupSummaryIcon.setText("✗");
                lastBackupSummaryIcon.setStyle("-fx-font-size: 20px; -fx-text-fill: #dc3545;");
            }
            if (lastBackupSummaryTitle != null) {
                lastBackupSummaryTitle.setText("Last Backup Failed");
            }
        } else {
            if (lastBackupSummaryIcon != null) {
                lastBackupSummaryIcon.setText("ℹ");
                lastBackupSummaryIcon.setStyle("-fx-font-size: 20px; -fx-text-fill: #58a6ff;");
            }
            if (lastBackupSummaryTitle != null) {
                lastBackupSummaryTitle.setText("Last Backup: " + status);
            }
        }
        
        // Details line
        if (lastBackupSummaryDetails != null && run.getStartTime() != null) {
            String when = formatRelativeTime(run.getStartTime());
            String user = run.getUsername() != null ? run.getUsername() : "Unknown";
            lastBackupSummaryDetails.setText(when + " • " + user);
        }
        
        // Stats
        if (summaryObjectsLabel != null) {
            summaryObjectsLabel.setText(String.valueOf(run.getCompletedObjects()));
        }
        if (summaryRecordsLabel != null) {
            summaryRecordsLabel.setText(formatRowCount(run.getTotalRecords()));
        }
        if (summarySizeLabel != null) {
            summarySizeLabel.setText(run.getFormattedSize());
        }
        if (summaryDurationLabel != null) {
            summaryDurationLabel.setText(run.getDuration());
        }
        if (summaryDestinationLabel != null) {
            String dest = run.getDestination() != null ? run.getDestination() : "CSV";
            summaryDestinationLabel.setText(dest);
        }
    }
    
    private String formatRelativeTime(String isoDateTime) {
        try {
            LocalDateTime time = LocalDateTime.parse(isoDateTime);
            LocalDateTime now = LocalDateTime.now();
            
            long minutes = ChronoUnit.MINUTES.between(time, now);
            long hours = ChronoUnit.HOURS.between(time, now);
            long days = ChronoUnit.DAYS.between(time, now);
            
            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + " min ago";
            if (hours < 24) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            if (days < 7) return days + " day" + (days > 1 ? "s" : "") + " ago";
            
            return time.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
        } catch (Exception e) {
            return isoDateTime;
        }
    }
    
    @FXML
    private void handleVerifyLastBackup() {
        if (mainController != null && lastBackupRun != null) {
            // Navigate to backup page and trigger verification
            mainController.navigateToBackup();
            // The backup controller will have the verify functionality
        }
    }
    
    private void fetchObjectCount() {
        ConnectionInfo connInfo = mainController != null ? mainController.getConnectionInfo() : null;
        if (connInfo == null) return;
        
        if (objectsAvailableLabel != null) {
            objectsAvailableLabel.setText("...");
        }
        
        Task<Integer> fetchTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                DescribeGlobalResult dgr = connInfo.getConnection().describeGlobal();
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
                if (backupObjectsLabel != null) {
                    backupObjectsLabel.setText(String.valueOf(count));
                }
                prefs.putInt("availableObjectCount", count);
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
        
        new Thread(fetchTask).start();
    }
    
    private String formatRowCount(long count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        } else if (count >= 1_000) {
            return String.format("%.1fK", count / 1_000.0);
        }
        return NumberFormat.getInstance().format(count);
    }
    
    private void setupCardHoverEffect(VBox card) {
        if (card == null) return;
        
        String normalStyle = "-fx-background-color: #161b22; -fx-background-radius: 12; " +
                           "-fx-border-color: #30363d; -fx-border-radius: 12;";
        String hoverStyle = "-fx-background-color: #1c2128; -fx-background-radius: 12; " +
                          "-fx-border-color: #58a6ff; -fx-border-radius: 12;";
        
        card.setOnMouseEntered(e -> card.setStyle(hoverStyle));
        card.setOnMouseExited(e -> card.setStyle(normalStyle));
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
    
    // Navigation handlers - delegate to main controller
    @FXML
    private void handleDataBackup() {
        if (mainController != null) {
            mainController.navigateToBackup();
        }
    }
    
    @FXML
    private void handleDataRestore() {
        if (mainController != null) {
            mainController.navigateToRestore();
        }
    }
    
    @FXML
    private void handleAddConnection() {
        if (mainController != null) {
            mainController.navigateToConnections();
        }
    }
}
