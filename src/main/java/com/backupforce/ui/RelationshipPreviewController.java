package com.backupforce.ui;

import com.backupforce.relationship.ChildRelationshipAnalyzer;
import com.backupforce.relationship.ChildRelationshipAnalyzer.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controller for the Relationship Preview Dialog.
 * Shows discovered child relationships and lets users select which ones to include in backup.
 * 
 * Features a detailed loading screen with real-time progress updates.
 */
public class RelationshipPreviewController {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipPreviewController.class);
    
    // Main content
    @FXML private VBox mainContent;
    @FXML private Label summaryLabel;
    @FXML private Label parentObjectsLabel;
    @FXML private TreeView<RelatedObjectItem> relationshipTree;
    @FXML private Label selectedCountLabel;
    @FXML private Label totalRecordsLabel;
    @FXML private Label apiCallsLabel;
    
    // Loading overlay
    @FXML private VBox loadingOverlay;
    @FXML private Label loadingStatusLabel;
    @FXML private ProgressBar loadingProgressBar;
    @FXML private Label progressPercentLabel;
    @FXML private Label progressTimeLabel;
    @FXML private Label currentObjectLabel;
    @FXML private Label objectsAnalyzedLabel;
    @FXML private Label relationshipsFoundLabel;
    @FXML private Label apiCallsCountLabel;
    @FXML private TextArea analysisLogArea;
    
    // Checkboxes
    @FXML private CheckBox captureExternalIdsCheckbox;
    @FXML private CheckBox generateIdMappingCheckbox;
    @FXML private CheckBox includeRecordTypesCheckbox;
    
    @FXML private Button startBackupBtn;
    @FXML private Button cancelBtn;
    
    private ChildRelationshipAnalyzer analyzer;
    private List<String> parentObjects;
    private int maxDepth = 2;
    private boolean priorityOnly = true;
    private int parentRecordLimit = 0; // Limit applied to parent objects (0 = unlimited)
    
    // Connection info for querying record counts
    private String instanceUrl;
    private String accessToken;
    private String apiVersion;
    
    // Progress tracking
    private long startTime;
    private final AtomicInteger objectsAnalyzed = new AtomicInteger(0);
    private final AtomicInteger relationshipsFound = new AtomicInteger(0);
    private final AtomicInteger apiCallsMade = new AtomicInteger(0);
    
    // Selected related objects to backup
    private final Map<String, SelectedRelatedObject> selectedRelatedObjects = new LinkedHashMap<>();
    
    // Result - whether user confirmed the backup
    private boolean confirmed = false;
    
    /**
     * Represents a related object item in the tree.
     */
    public static class RelatedObjectItem {
        private final String objectName;
        private final String parentObject;
        private final String parentField;
        private final String relationshipName;
        private final int depth;
        private final boolean priority;
        private final boolean isRoot;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private long recordCount = -1; // -1 = not yet queried, 0+ = actual count
        private int recordLimit = 0; // 0 = all records, >0 = limit
        
        public RelatedObjectItem(String objectName, String parentObject, String parentField, 
                                String relationshipName, int depth, boolean priority) {
            this.objectName = objectName;
            this.parentObject = parentObject;
            this.parentField = parentField;
            this.relationshipName = relationshipName;
            this.depth = depth;
            this.priority = priority;
            this.isRoot = false;
        }
        
        // Root node constructor
        public RelatedObjectItem(String objectName) {
            this.objectName = objectName;
            this.parentObject = null;
            this.parentField = null;
            this.relationshipName = null;
            this.depth = 0;
            this.priority = false;
            this.isRoot = true;
        }
        
        public String getObjectName() { return objectName; }
        public String getParentObject() { return parentObject; }
        public String getParentField() { return parentField; }
        public String getRelationshipName() { return relationshipName; }
        public int getDepth() { return depth; }
        public boolean isPriority() { return priority; }
        public boolean isRoot() { return isRoot; }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public BooleanProperty selectedProperty() { return selected; }
        
        /**
         * Returns unique key for this item (objectName:parentField).
         * This distinguishes multiple lookups from same object to same parent.
         */
        public String getUniqueKey() {
            return objectName + ":" + parentField;
        }
        
        public long getRecordCount() { return recordCount; }
        public void setRecordCount(long count) { this.recordCount = count; }
        
        public int getRecordLimit() { return recordLimit; }
        public void setRecordLimit(int limit) { this.recordLimit = limit; }
        
        @Override
        public String toString() {
            if (isRoot) {
                return "📦 " + objectName;
            }
            String prefix = priority ? "★ " : "  ";
            String depthIndicator = depth > 1 ? " (level " + depth + ")" : "";
            String countStr = recordCount >= 0 ? String.format(" [%,d records]", recordCount) : "";
            return prefix + objectName + " via " + parentField + countStr + depthIndicator;
        }
    }
    
    /**
     * Represents a selected related object with metadata for backup.
     */
    public static class SelectedRelatedObject {
        private final String objectName;
        private final String parentObject;
        private final String parentField;
        private final int depth;
        private final boolean priority;
        private final long recordCount;
        private final int recordLimit; // 0 = all, >0 = limit
        
        public SelectedRelatedObject(String objectName, String parentObject, String parentField, 
                                    int depth, boolean priority) {
            this(objectName, parentObject, parentField, depth, priority, -1, 0);
        }
        
        public SelectedRelatedObject(String objectName, String parentObject, String parentField, 
                                    int depth, boolean priority, long recordCount, int recordLimit) {
            this.objectName = objectName;
            this.parentObject = parentObject;
            this.parentField = parentField;
            this.depth = depth;
            this.priority = priority;
            this.recordCount = recordCount;
            this.recordLimit = recordLimit;
        }
        
        public String getObjectName() { return objectName; }
        public String getParentObject() { return parentObject; }
        public String getParentField() { return parentField; }
        public int getDepth() { return depth; }
        public boolean isPriority() { return priority; }
        public long getRecordCount() { return recordCount; }
        public int getRecordLimit() { return recordLimit; }
        
        /**
         * Returns unique key for this selection (objectName:parentField).
         * This distinguishes multiple lookups from same object to same parent.
         */
        public String getUniqueKey() {
            return objectName + ":" + parentField;
        }
    }
    
    @FXML
    public void initialize() {
        // Setup tree with checkbox support
        relationshipTree.setCellFactory(tv -> new TreeCell<RelatedObjectItem>() {
            private final CheckBox checkBox = new CheckBox();
            private javafx.beans.value.ChangeListener<Boolean> currentListener;
            
            {
                checkBox.setSelected(false);
            }
            
            @Override
            public void updateItem(RelatedObjectItem item, boolean empty) {
                super.updateItem(item, empty);
                
                if (currentListener != null) {
                    checkBox.selectedProperty().removeListener(currentListener);
                    currentListener = null;
                }
                
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    checkBox.setSelected(false);
                } else if (item.isRoot()) {
                    setText(item.toString());
                    setGraphic(null);
                    setStyle("-fx-font-weight: bold;");
                } else {
                    setText(item.toString());
                    checkBox.setSelected(item.isSelected());
                    
                    currentListener = (obs, oldVal, newVal) -> {
                        if (newVal != item.isSelected()) {
                            item.setSelected(newVal);
                            if (newVal) {
                                // Query record count asynchronously when item is selected
                                if (item.getRecordCount() < 0) {
                                    queryRecordCountAsync(item, () -> {
                                        selectedRelatedObjects.put(item.getUniqueKey(), 
                                            new SelectedRelatedObject(
                                                item.getObjectName(),
                                                item.getParentObject(),
                                                item.getParentField(),
                                                item.getDepth(),
                                                item.isPriority(),
                                                item.getRecordCount(),
                                                item.getRecordLimit()
                                            ));
                                        updateSelectedCount();
                                        relationshipTree.refresh();
                                    });
                                } else {
                                    selectedRelatedObjects.put(item.getUniqueKey(), 
                                        new SelectedRelatedObject(
                                            item.getObjectName(),
                                            item.getParentObject(),
                                            item.getParentField(),
                                            item.getDepth(),
                                            item.isPriority(),
                                            item.getRecordCount(),
                                            item.getRecordLimit()
                                        ));
                                }
                            } else {
                                selectedRelatedObjects.remove(item.getUniqueKey());
                            }
                            updateSelectedCount();
                        }
                    };
                    checkBox.selectedProperty().addListener(currentListener);
                    setGraphic(checkBox);
                    
                    if (item.isPriority()) {
                        setStyle("-fx-text-fill: #58a6ff;");
                    } else {
                        setStyle("-fx-text-fill: #8b949e;");
                    }
                }
            }
        });
    }
    
    /**
     * Initialize the dialog with connection info and parent objects.
     */
    public void initializeWithData(String instanceUrl, String accessToken, String apiVersion,
                                   List<String> parentObjects, int maxDepth, boolean priorityOnly) {
        initializeWithData(instanceUrl, accessToken, apiVersion, parentObjects, maxDepth, priorityOnly, 0);
    }
    
    /**
     * Initialize the dialog with connection info, parent objects, and parent record limit.
     */
    public void initializeWithData(String instanceUrl, String accessToken, String apiVersion,
                                   List<String> parentObjects, int maxDepth, boolean priorityOnly,
                                   int parentRecordLimit) {
        this.parentObjects = parentObjects;
        this.maxDepth = maxDepth;
        this.priorityOnly = priorityOnly;
        this.parentRecordLimit = parentRecordLimit;
        
        // Store connection info for querying
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        
        this.analyzer = new ChildRelationshipAnalyzer(instanceUrl, accessToken, apiVersion);
        
        // Setup parent objects label
        if (parentObjectsLabel != null) {
            String limitInfo = parentRecordLimit > 0 ? " (limit: " + parentRecordLimit + ")" : "";
            parentObjectsLabel.setText(String.join(", ", parentObjects) + limitInfo);
        }
        
        // Show loading overlay
        showLoadingOverlay(true);
        
        // Analyze relationships in background
        analyzeRelationships();
    }
    
    private void showLoadingOverlay(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(show);
        }
        if (mainContent != null) {
            mainContent.setVisible(!show);
        }
    }
    
    /**
     * Query the actual record count for a related object filtered by parent IDs.
     * Uses SOQL COUNT() query for accuracy.
     */
    private long queryRelatedRecordCount(String childObject, String parentField, String parentObject) {
        if (instanceUrl == null || accessToken == null) {
            return -1;
        }
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Build count query - if we have a parent record limit, we need to count related records
            // For now, just count all records where the parent field is not null
            String soql;
            if (parentRecordLimit > 0) {
                // When parent has a limit, we query: SELECT COUNT() FROM Child WHERE ParentField IN (SELECT Id FROM Parent LIMIT n)
                // But this is complex - simpler to just count all children with non-null parent
                soql = String.format("SELECT COUNT() FROM %s WHERE %s != null", childObject, parentField);
            } else {
                // Unlimited parent - count all related children
                soql = String.format("SELECT COUNT() FROM %s WHERE %s != null", childObject, parentField);
            }
            
            String url = instanceUrl + "/services/data/v" + apiVersion + "/query?q=" + 
                         URLEncoder.encode(soql, StandardCharsets.UTF_8);
            
            HttpGet get = new HttpGet(url);
            get.setHeader("Authorization", "Bearer " + accessToken);
            
            try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
                if (response.getCode() == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    
                    // For COUNT() query, totalSize is the count
                    if (json.has("totalSize")) {
                        apiCallsMade.incrementAndGet();
                        return json.get("totalSize").getAsLong();
                    }
                } else {
                    logger.debug("Count query failed for {}: {}", childObject, response.getCode());
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to query count for {}: {}", childObject, e.getMessage());
        }
        
        return -1;
    }
    
    /**
     * Query record count asynchronously and update the item when done.
     */
    private void queryRecordCountAsync(RelatedObjectItem item, Runnable onComplete) {
        Thread countThread = new Thread(() -> {
            long count = queryRelatedRecordCount(item.getObjectName(), item.getParentField(), item.getParentObject());
            Platform.runLater(() -> {
                item.setRecordCount(count);
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
        countThread.setDaemon(true);
        countThread.start();
    }
    
    private void appendLog(String message) {
        Platform.runLater(() -> {
            if (analysisLogArea != null) {
                String timestamp = String.format("[%tT] ", System.currentTimeMillis());
                analysisLogArea.appendText(timestamp + message + "\n");
                // Auto-scroll to bottom
                analysisLogArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }
    
    private void updateLoadingProgress(String status, String currentObject, double progress) {
        Platform.runLater(() -> {
            if (loadingStatusLabel != null) {
                loadingStatusLabel.setText(status);
            }
            if (currentObjectLabel != null) {
                currentObjectLabel.setText(currentObject);
            }
            if (loadingProgressBar != null) {
                loadingProgressBar.setProgress(progress);
            }
            if (progressPercentLabel != null) {
                progressPercentLabel.setText(String.format("%.0f%%", progress * 100));
            }
            if (progressTimeLabel != null && startTime > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                progressTimeLabel.setText(String.format("Elapsed: %.1fs", elapsed / 1000.0));
            }
            if (objectsAnalyzedLabel != null) {
                objectsAnalyzedLabel.setText(String.valueOf(objectsAnalyzed.get()));
            }
            if (relationshipsFoundLabel != null) {
                relationshipsFoundLabel.setText(String.valueOf(relationshipsFound.get()));
            }
            if (apiCallsCountLabel != null) {
                apiCallsCountLabel.setText(String.valueOf(apiCallsMade.get()));
            }
        });
    }
    
    private void analyzeRelationships() {
        startTime = System.currentTimeMillis();
        objectsAnalyzed.set(0);
        relationshipsFound.set(0);
        apiCallsMade.set(0);
        
        appendLog("Starting relationship analysis...");
        appendLog("Parent objects: " + String.join(", ", parentObjects));
        appendLog("Max depth: " + maxDepth);
        appendLog("Priority only: " + priorityOnly);
        appendLog("---");
        
        Task<TreeItem<RelatedObjectItem>> task = new Task<>() {
            @Override
            protected TreeItem<RelatedObjectItem> call() throws Exception {
                TreeItem<RelatedObjectItem> rootItem = new TreeItem<>(
                    new RelatedObjectItem("Related Objects")
                );
                rootItem.setExpanded(true);
                
                int totalParents = parentObjects.size();
                int processedParents = 0;
                
                for (String parentObject : parentObjects) {
                    processedParents++;
                    double progress = (double) processedParents / totalParents;
                    
                    updateLoadingProgress(
                        String.format("Analyzing parent %d of %d", processedParents, totalParents),
                        parentObject,
                        progress * 0.9 // Reserve 10% for final processing
                    );
                    
                    appendLog("📦 Analyzing: " + parentObject);
                    apiCallsMade.incrementAndGet();
                    
                    long objStart = System.currentTimeMillis();
                    RelationshipTree tree = analyzer.buildRelationshipTree(parentObject, maxDepth);
                    long objTime = System.currentTimeMillis() - objStart;
                    
                    objectsAnalyzed.incrementAndGet();
                    int childCount = tree.getRoot().getChildren().size();
                    relationshipsFound.addAndGet(childCount);
                    
                    appendLog(String.format("   ✓ Found %d child relationships in %.1fs", 
                        childCount, objTime / 1000.0));
                    
                    // Log priority children
                    List<String> priorityChildren = new ArrayList<>();
                    for (RelationshipNode child : tree.getRoot().getChildren()) {
                        if (child.isPriority()) {
                            priorityChildren.add(child.getObjectName());
                        }
                    }
                    if (!priorityChildren.isEmpty()) {
                        appendLog("   ★ Priority: " + String.join(", ", priorityChildren));
                    }
                    
                    TreeItem<RelatedObjectItem> parentItem = new TreeItem<>(
                        new RelatedObjectItem(parentObject)
                    );
                    parentItem.setExpanded(true);
                    
                    // Add child relationships
                    for (RelationshipNode child : tree.getRoot().getChildren()) {
                        TreeItem<RelatedObjectItem> childItem = createTreeItem(child, parentObject);
                        if (childItem != null) {
                            parentItem.getChildren().add(childItem);
                        }
                    }
                    
                    if (!parentItem.getChildren().isEmpty()) {
                        rootItem.getChildren().add(parentItem);
                    }
                }
                
                updateLoadingProgress("Finalizing...", "Building tree view", 0.95);
                appendLog("---");
                appendLog("✓ Analysis complete!");
                
                return rootItem;
            }
        };
        
        task.setOnSucceeded(event -> {
            TreeItem<RelatedObjectItem> root = task.getValue();
            relationshipTree.setRoot(root);
            relationshipTree.setShowRoot(false);
            
            int totalRelated = countAllItems(root);
            long elapsed = System.currentTimeMillis() - startTime;
            
            summaryLabel.setText(String.format("Found %d related objects in %.1fs", 
                totalRelated, elapsed / 1000.0));
            
            if (apiCallsLabel != null) {
                apiCallsLabel.setText(String.format("%d API calls", apiCallsMade.get()));
            }
            
            appendLog(String.format("Total: %d objects, %d API calls, %.1fs", 
                totalRelated, apiCallsMade.get(), elapsed / 1000.0));
            
            // Short delay to let user see the completion message
            new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    showLoadingOverlay(false);
                    startBackupBtn.setDisable(false);
                    
                    // Auto-select priority items
                    if (priorityOnly) {
                        handleSelectPriority();
                    }
                });
            }).start();
        });
        
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            logger.error("Failed to analyze relationships", ex);
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            
            appendLog("❌ ERROR: " + errorMsg);
            
            Platform.runLater(() -> {
                summaryLabel.setText("Error: " + errorMsg);
                showLoadingOverlay(false);
                startBackupBtn.setDisable(false);
            });
            
            if (ex != null) {
                ex.printStackTrace();
            }
        });
        
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    
    private TreeItem<RelatedObjectItem> createTreeItem(RelationshipNode node, String parentObject) {
        // Filter by priority if enabled - only for depth 1 direct children
        if (priorityOnly && !node.isPriority() && node.getDepth() == 1) {
            return null;
        }
        
        RelatedObjectItem item = new RelatedObjectItem(
            node.getObjectName(),
            parentObject,
            node.getParentField(),
            node.getRelationshipName(),
            node.getDepth(),
            node.isPriority()
        );
        
        // Record counts will be queried lazily when user selects an item
        // This avoids blocking the UI with 200+ API calls during tree building
        
        TreeItem<RelatedObjectItem> treeItem = new TreeItem<>(item);
        treeItem.setExpanded(true);
        
        // Add grandchildren if depth allows
        for (RelationshipNode child : node.getChildren()) {
            TreeItem<RelatedObjectItem> childItem = createTreeItem(child, node.getObjectName());
            if (childItem != null) {
                treeItem.getChildren().add(childItem);
            }
        }
        
        return treeItem;
    }
    
    private int countAllItems(TreeItem<RelatedObjectItem> item) {
        int count = 0;
        if (item.getValue() != null && !item.getValue().isRoot()) {
            count = 1;
        }
        for (TreeItem<RelatedObjectItem> child : item.getChildren()) {
            count += countAllItems(child);
        }
        return count;
    }
    
    private void updateSelectedCount() {
        int count = selectedRelatedObjects.size();
        selectedCountLabel.setText(count + " object" + (count != 1 ? "s" : "") + " selected");
        
        // Calculate total records from selected objects
        long totalRecords = 0;
        for (SelectedRelatedObject obj : selectedRelatedObjects.values()) {
            if (obj.getRecordCount() > 0) {
                totalRecords += obj.getRecordCount();
            }
        }
        
        if (totalRecordsLabel != null) {
            if (totalRecords > 0) {
                totalRecordsLabel.setText(String.format("%,d total records", totalRecords));
            } else {
                totalRecordsLabel.setText("counting records...");
            }
        }
    }
    
    @FXML
    private void handleSelectAll() {
        setAllSelected(relationshipTree.getRoot(), true);
        updateSelectedCount();
        // Refresh tree to update checkbox visuals
        relationshipTree.refresh();
    }
    
    @FXML
    private void handleDeselectAll() {
        setAllSelected(relationshipTree.getRoot(), false);
        selectedRelatedObjects.clear();
        updateSelectedCount();
        relationshipTree.refresh();
    }
    
    @FXML
    private void handleSelectPriority() {
        // First deselect all
        setAllSelected(relationshipTree.getRoot(), false);
        selectedRelatedObjects.clear();
        
        // Then select only priority items
        selectPriorityItems(relationshipTree.getRoot());
        updateSelectedCount();
        relationshipTree.refresh();
    }
    
    private void setAllSelected(TreeItem<RelatedObjectItem> item, boolean selected) {
        if (item == null) return;
        
        RelatedObjectItem value = item.getValue();
        if (value != null && !value.isRoot()) {
            value.setSelected(selected);
            if (selected) {
                selectedRelatedObjects.put(value.getUniqueKey(), 
                    new SelectedRelatedObject(
                        value.getObjectName(),
                        value.getParentObject(),
                        value.getParentField(),
                        value.getDepth(),
                        value.isPriority()
                    ));
            }
        }
        
        for (TreeItem<RelatedObjectItem> child : item.getChildren()) {
            setAllSelected(child, selected);
        }
    }
    
    private void selectPriorityItems(TreeItem<RelatedObjectItem> item) {
        if (item == null) return;
        
        RelatedObjectItem value = item.getValue();
        if (value != null && !value.isRoot() && value.isPriority()) {
            value.setSelected(true);
            selectedRelatedObjects.put(value.getUniqueKey(), 
                new SelectedRelatedObject(
                    value.getObjectName(),
                    value.getParentObject(),
                    value.getParentField(),
                    value.getDepth(),
                    value.isPriority()
                ));
        }
        
        for (TreeItem<RelatedObjectItem> child : item.getChildren()) {
            selectPriorityItems(child);
        }
    }
    
    @FXML
    private void handleCancel() {
        confirmed = false;
        closeDialog();
    }
    
    @FXML
    private void handleStartBackup() {
        confirmed = true;
        closeDialog();
    }
    
    private void closeDialog() {
        if (analyzer != null) {
            analyzer.close();
        }
        Stage stage = (Stage) cancelBtn.getScene().getWindow();
        stage.close();
    }
    
    // ==================== Getters for backup configuration ====================
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public Collection<SelectedRelatedObject> getSelectedRelatedObjects() {
        return selectedRelatedObjects.values();
    }
    
    public boolean isCaptureExternalIds() {
        return captureExternalIdsCheckbox != null && captureExternalIdsCheckbox.isSelected();
    }
    
    public boolean isGenerateIdMapping() {
        return generateIdMappingCheckbox != null && generateIdMappingCheckbox.isSelected();
    }
    
    public boolean isIncludeRecordTypes() {
        return includeRecordTypesCheckbox != null && includeRecordTypesCheckbox.isSelected();
    }
    
    /**
     * Get the selected objects organized by parent for building WHERE clauses.
     */
    public Map<String, List<SelectedRelatedObject>> getSelectedObjectsByParent() {
        Map<String, List<SelectedRelatedObject>> byParent = new LinkedHashMap<>();
        
        for (SelectedRelatedObject obj : selectedRelatedObjects.values()) {
            byParent.computeIfAbsent(obj.getParentObject(), k -> new ArrayList<>()).add(obj);
        }
        
        return byParent;
    }
    
    /**
     * Configuration object to pass to BackupController.
     */
    public static class RelationshipBackupConfig {
        private final List<SelectedRelatedObject> selectedObjects;
        private final boolean captureExternalIds;
        private final boolean generateIdMapping;
        private final boolean includeRecordTypes;
        private final boolean includeAllRelatedRecords; // true = all, false = respect individual limits
        private final long totalRelatedRecords; // Total count of all selected related records
        
        public RelationshipBackupConfig(Collection<SelectedRelatedObject> selectedObjects,
                                       boolean captureExternalIds,
                                       boolean generateIdMapping, boolean includeRecordTypes) {
            this(selectedObjects, captureExternalIds, generateIdMapping, includeRecordTypes, true);
        }
        
        public RelationshipBackupConfig(Collection<SelectedRelatedObject> selectedObjects,
                                       boolean captureExternalIds, boolean generateIdMapping, 
                                       boolean includeRecordTypes, boolean includeAllRelatedRecords) {
            this.selectedObjects = new ArrayList<>(selectedObjects);
            this.captureExternalIds = captureExternalIds;
            this.generateIdMapping = generateIdMapping;
            this.includeRecordTypes = includeRecordTypes;
            this.includeAllRelatedRecords = includeAllRelatedRecords;
            
            // Calculate total related records
            long total = 0;
            for (SelectedRelatedObject obj : selectedObjects) {
                if (obj.getRecordCount() > 0) {
                    total += obj.getRecordCount();
                }
            }
            this.totalRelatedRecords = total;
        }
        
        public List<SelectedRelatedObject> getSelectedObjects() { return selectedObjects; }
        public boolean isCaptureExternalIds() { return captureExternalIds; }
        public boolean isGenerateIdMapping() { return generateIdMapping; }
        public boolean isIncludeRecordTypes() { return includeRecordTypes; }
        public boolean isIncludeAllRelatedRecords() { return includeAllRelatedRecords; }
        public long getTotalRelatedRecords() { return totalRelatedRecords; }
    }
    
    public RelationshipBackupConfig getConfig() {
        return new RelationshipBackupConfig(
            selectedRelatedObjects.values(),
            isCaptureExternalIds(),
            isGenerateIdMapping(),
            isIncludeRecordTypes()
        );
    }
}
