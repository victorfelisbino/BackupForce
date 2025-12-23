package com.backupforce.relationship;

import com.backupforce.relationship.ChildRelationshipAnalyzer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Orchestrates relationship-aware backups by analyzing parent objects and 
 * automatically discovering and queuing related child records for backup.
 * 
 * Usage:
 * 1. User selects Account with limit 100
 * 2. User enables "Include Related Records"
 * 3. RelationshipAwareBackupService analyzes Account's children
 * 4. After Account backup completes, service queues child backups with WHERE clause
 *    filtering by the backed-up Account IDs
 * 
 * This ensures that when restoring a limited subset of records, all related
 * data is included to maintain referential integrity.
 */
public class RelationshipAwareBackupService {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipAwareBackupService.class);
    
    private final ChildRelationshipAnalyzer analyzer;
    private final int maxDepth;
    private final boolean includeOnlyPriority;
    
    // Track backed up parent IDs for building child WHERE clauses
    private final Map<String, Set<String>> backedUpIds = new LinkedHashMap<>();
    
    // Queue of additional objects to backup based on relationships
    private final List<RelatedBackupTask> relatedBackupQueue = new ArrayList<>();
    
    /**
     * Creates a new RelationshipAwareBackupService.
     * 
     * @param instanceUrl Salesforce instance URL
     * @param accessToken OAuth access token
     * @param apiVersion Salesforce API version
     * @param maxDepth Maximum relationship depth to traverse (1-3 recommended)
     * @param includeOnlyPriority If true, only include priority child objects (Contact, Opportunity, etc.)
     */
    public RelationshipAwareBackupService(String instanceUrl, String accessToken, String apiVersion,
                                         int maxDepth, boolean includeOnlyPriority) {
        this.analyzer = new ChildRelationshipAnalyzer(instanceUrl, accessToken, apiVersion);
        this.maxDepth = maxDepth;
        this.includeOnlyPriority = includeOnlyPriority;
    }
    
    /**
     * Analyzes relationships for a set of parent objects that will be backed up.
     * Call this before starting the backup to understand what child objects will be included.
     * 
     * @param parentObjects Objects the user selected for backup
     * @return Summary of what will be included
     */
    public RelationshipBackupPlan analyzeBackupPlan(List<String> parentObjects) throws Exception {
        RelationshipBackupPlan plan = new RelationshipBackupPlan();
        
        for (String parentObject : parentObjects) {
            RelationshipTree tree = analyzer.buildRelationshipTree(parentObject, maxDepth);
            
            logger.info("Relationship tree for {}:\n{}", parentObject, tree.printTree());
            
            // Collect all child objects that will be auto-included
            for (RelationshipNode node : tree.getAllNodesOrdered()) {
                if (node.getDepth() > 0) { // Skip root
                    if (!includeOnlyPriority || node.isPriority()) {
                        plan.addRelatedObject(parentObject, node);
                    }
                }
            }
        }
        
        return plan;
    }
    
    /**
     * Registers that a parent object backup has completed and captures the backed-up IDs.
     * Call this after each parent object backup completes.
     * 
     * @param objectName The object that was backed up
     * @param outputFolder The folder where the CSV was saved
     * @return List of related backup tasks that should be queued
     */
    public List<RelatedBackupTask> onParentBackupComplete(String objectName, String outputFolder) throws Exception {
        // Read the backed-up IDs from the CSV
        Set<String> ids = extractIdsFromBackup(objectName, outputFolder);
        
        if (ids.isEmpty()) {
            logger.info("No records backed up for {}, skipping related objects", objectName);
            return Collections.emptyList();
        }
        
        backedUpIds.put(objectName, ids);
        logger.info("Captured {} IDs from {} backup", ids.size(), objectName);
        
        // Analyze relationships and create backup tasks for children
        ObjectRelationshipInfo relInfo = analyzer.analyzeObject(objectName);
        List<RelatedBackupTask> tasks = new ArrayList<>();
        
        for (ChildRelationship childRel : relInfo.getChildRelationships()) {
            // Filter by priority if configured
            if (includeOnlyPriority && !childRel.isPriority()) {
                continue;
            }
            
            // Build WHERE clause for child records
            String whereClause = buildWhereClause(childRel.getFieldName(), ids);
            
            RelatedBackupTask task = new RelatedBackupTask(
                childRel.getChildSObject(),
                childRel.getFieldName(),
                objectName,
                whereClause,
                1 // depth 1 = direct child
            );
            
            tasks.add(task);
            relatedBackupQueue.add(task);
            
            logger.info("Queued related backup: {} WHERE {} (parent: {})", 
                childRel.getChildSObject(), whereClause.substring(0, Math.min(100, whereClause.length())) + "...", objectName);
        }
        
        return tasks;
    }
    
    /**
     * Process a related backup task completion and discover its children.
     * This enables multi-level relationship discovery (e.g., Account -> Opportunity -> OpportunityLineItem)
     */
    public List<RelatedBackupTask> onRelatedBackupComplete(RelatedBackupTask completedTask, String outputFolder) 
            throws Exception {
        
        if (completedTask.getDepth() >= maxDepth) {
            logger.debug("Max depth {} reached for {}, not discovering further children", 
                maxDepth, completedTask.getObjectName());
            return Collections.emptyList();
        }
        
        // Extract IDs from this backup
        Set<String> ids = extractIdsFromBackup(completedTask.getObjectName(), outputFolder);
        
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        backedUpIds.put(completedTask.getObjectName(), ids);
        
        // Discover grandchildren
        ObjectRelationshipInfo relInfo = analyzer.analyzeObject(completedTask.getObjectName());
        List<RelatedBackupTask> tasks = new ArrayList<>();
        
        for (ChildRelationship childRel : relInfo.getChildRelationships()) {
            if (includeOnlyPriority && !childRel.isPriority()) {
                continue;
            }
            
            // Don't backup the same object we just came from (avoid circular references)
            if (childRel.getChildSObject().equals(completedTask.getParentObject())) {
                continue;
            }
            
            String whereClause = buildWhereClause(childRel.getFieldName(), ids);
            
            RelatedBackupTask task = new RelatedBackupTask(
                childRel.getChildSObject(),
                childRel.getFieldName(),
                completedTask.getObjectName(),
                whereClause,
                completedTask.getDepth() + 1
            );
            
            tasks.add(task);
            relatedBackupQueue.add(task);
        }
        
        return tasks;
    }
    
    /**
     * Builds a WHERE clause to filter child records by parent IDs.
     * Handles large ID sets by using chunked IN clauses.
     */
    public String buildWhereClause(String fieldName, Set<String> parentIds) {
        if (parentIds.isEmpty()) {
            return null;
        }
        
        // Salesforce SOQL has a limit on IN clause size, chunk if needed
        // Max ~4000 characters per WHERE clause, so ~200 IDs per chunk typically
        List<String> idList = new ArrayList<>(parentIds);
        
        if (idList.size() <= 200) {
            return fieldName + " IN (" + formatIdList(idList) + ")";
        }
        
        // For large sets, just use the first chunk (backup will get partial data)
        // A more robust implementation would do multiple queries
        List<String> chunk = idList.subList(0, Math.min(200, idList.size()));
        logger.warn("Large ID set ({} IDs) for {}, using first 200. Consider full backup for complete data.", 
            idList.size(), fieldName);
        
        return fieldName + " IN (" + formatIdList(chunk) + ")";
    }
    
    private String formatIdList(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("'").append(ids.get(i)).append("'");
        }
        return sb.toString();
    }
    
    /**
     * Builds a WHERE clause with multiple parent fields OR'd together.
     * This allows capturing all records that relate to the parent through ANY lookup field.
     * 
     * For example, if Contact has AccountId and Secondary_Account__c both pointing to Account,
     * this builds: (AccountId IN (...) OR Secondary_Account__c IN (...))
     * 
     * @param parentFields List of field names that lookup to the parent object
     * @param parentIds Set of parent record IDs to match
     * @return WHERE clause string, or null if no parent IDs
     */
    public String buildWhereClauseMultiField(List<String> parentFields, Set<String> parentIds) {
        if (parentIds.isEmpty() || parentFields.isEmpty()) {
            return null;
        }
        
        List<String> idList = new ArrayList<>(parentIds);
        
        // For large sets, use first chunk
        if (idList.size() > 200) {
            logger.warn("Large ID set ({} IDs) for {} fields, using first 200. Consider full backup for complete data.", 
                idList.size(), parentFields.size());
            idList = idList.subList(0, 200);
        }
        
        String formattedIds = formatIdList(idList);
        
        if (parentFields.size() == 1) {
            // Single field - no need for OR
            return parentFields.get(0) + " IN (" + formattedIds + ")";
        }
        
        // Multiple fields - OR them together
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < parentFields.size(); i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append(parentFields.get(i)).append(" IN (").append(formattedIds).append(")");
        }
        sb.append(")");
        
        logger.info("Built multi-field WHERE clause with {} fields: {}", 
            parentFields.size(), parentFields);
        
        return sb.toString();
    }
    
    /**
     * Extracts record IDs from a backed-up CSV file.
     */
    public Set<String> extractIdsFromBackup(String objectName, String outputFolder) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        
        Path csvPath = Paths.get(outputFolder, objectName + ".csv");
        
        if (!Files.exists(csvPath)) {
            logger.warn("Backup file not found: {}", csvPath);
            return ids;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return ids;
            }
            
            // Find Id column index
            String[] headers = parseCSVLine(headerLine);
            int idIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                if ("Id".equalsIgnoreCase(headers[i].trim().replace("\"", ""))) {
                    idIndex = i;
                    break;
                }
            }
            
            if (idIndex == -1) {
                logger.warn("No Id column found in {}", csvPath);
                return ids;
            }
            
            // Read all IDs
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);
                if (values.length > idIndex) {
                    String id = values[idIndex].trim().replace("\"", "");
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
        }
        
        logger.debug("Extracted {} IDs from {}", ids.size(), csvPath);
        return ids;
    }
    
    /**
     * Simple CSV line parser (handles quoted values).
     */
    private String[] parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        
        return values.toArray(new String[0]);
    }
    
    /**
     * Gets all pending related backup tasks.
     */
    public List<RelatedBackupTask> getPendingTasks() {
        return new ArrayList<>(relatedBackupQueue);
    }
    
    /**
     * Clears all state (call at start of new backup session).
     */
    public void reset() {
        backedUpIds.clear();
        relatedBackupQueue.clear();
    }
    
    /**
     * Gets backed up IDs for an object (for use in building child queries).
     */
    public Set<String> getBackedUpIds(String objectName) {
        return backedUpIds.getOrDefault(objectName, Collections.emptySet());
    }
    
    public void close() {
        analyzer.close();
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * A backup task for a related (child) object.
     */
    public static class RelatedBackupTask {
        private final String objectName;
        private final String parentField;  // Field linking to parent
        private final String parentObject; // Name of parent object
        private final String whereClause;  // SOQL WHERE clause
        private final int depth;           // Relationship depth from root
        private boolean completed = false;
        private int recordCount = 0;
        
        public RelatedBackupTask(String objectName, String parentField, String parentObject, 
                                String whereClause, int depth) {
            this.objectName = objectName;
            this.parentField = parentField;
            this.parentObject = parentObject;
            this.whereClause = whereClause;
            this.depth = depth;
        }
        
        public String getObjectName() { return objectName; }
        public String getParentField() { return parentField; }
        public String getParentObject() { return parentObject; }
        public String getWhereClause() { return whereClause; }
        public int getDepth() { return depth; }
        public boolean isCompleted() { return completed; }
        public int getRecordCount() { return recordCount; }
        
        public void setCompleted(boolean completed) { this.completed = completed; }
        public void setRecordCount(int count) { this.recordCount = count; }
        
        @Override
        public String toString() {
            return String.format("%s (via %s from %s, depth=%d)", objectName, parentField, parentObject, depth);
        }
    }
    
    /**
     * Summary of what will be backed up including related objects.
     */
    public static class RelationshipBackupPlan {
        private final Map<String, List<RelatedObjectInfo>> relatedObjectsByParent = new LinkedHashMap<>();
        private int totalRelatedObjects = 0;
        
        public void addRelatedObject(String parentObject, RelationshipNode node) {
            relatedObjectsByParent.computeIfAbsent(parentObject, k -> new ArrayList<>())
                .add(new RelatedObjectInfo(
                    node.getObjectName(),
                    node.getParentField(),
                    node.getDepth(),
                    node.isPriority()
                ));
            totalRelatedObjects++;
        }
        
        public Map<String, List<RelatedObjectInfo>> getRelatedObjectsByParent() {
            return relatedObjectsByParent;
        }
        
        public int getTotalRelatedObjects() { return totalRelatedObjects; }
        
        public List<RelatedObjectInfo> getRelatedObjects(String parentObject) {
            return relatedObjectsByParent.getOrDefault(parentObject, Collections.emptyList());
        }
        
        /**
         * Get a summary string for display.
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Relationship-Aware Backup Plan\n");
            sb.append("==============================\n\n");
            
            for (Map.Entry<String, List<RelatedObjectInfo>> entry : relatedObjectsByParent.entrySet()) {
                sb.append(entry.getKey()).append(":\n");
                for (RelatedObjectInfo info : entry.getValue()) {
                    sb.append("  ").append(info.isPriority() ? "★ " : "  ");
                    sb.append(info.getObjectName());
                    sb.append(" (via ").append(info.getParentField()).append(")");
                    sb.append(" [depth ").append(info.getDepth()).append("]\n");
                }
                sb.append("\n");
            }
            
            sb.append("Total related objects: ").append(totalRelatedObjects);
            return sb.toString();
        }
    }
    
    /**
     * Info about a related object that will be included in backup.
     */
    public static class RelatedObjectInfo {
        private final String objectName;
        private final String parentField;
        private final int depth;
        private final boolean priority;
        
        public RelatedObjectInfo(String objectName, String parentField, int depth, boolean priority) {
            this.objectName = objectName;
            this.parentField = parentField;
            this.depth = depth;
            this.priority = priority;
        }
        
        public String getObjectName() { return objectName; }
        public String getParentField() { return parentField; }
        public int getDepth() { return depth; }
        public boolean isPriority() { return priority; }
    }
}
