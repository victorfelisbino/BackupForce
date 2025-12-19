package com.backupforce.ui;

import com.backupforce.config.ConnectionManager;
import com.backupforce.config.ConnectionManager.SavedConnection;
import com.backupforce.ui.LoginController.ConnectionInfo;
import com.sforce.soap.partner.DescribeGlobalResult;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.List;
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
    
    private MainController mainController;
    
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
        loadDataRestoreMetrics();
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
    
    private void loadDataRestoreMetrics() {
        int sourceCount = prefs.getInt("restoreSourceCount", 0);
        long lastRestoreRows = prefs.getLong("lastRestoreRowCount", 0);
        String lastRestore = prefs.get("lastRestoreDate", null);
        
        if (restoreSourcesLabel != null) {
            restoreSourcesLabel.setText(sourceCount > 0 ? String.valueOf(sourceCount) : "--");
        }
        
        if (lastRestoreRowsLabel != null) {
            lastRestoreRowsLabel.setText(lastRestoreRows > 0 ? formatRowCount(lastRestoreRows) : "--");
        }
        
        if (lastDataRestoreLabel != null) {
            lastDataRestoreLabel.setText(lastRestore != null ? lastRestore : "No restores yet");
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
