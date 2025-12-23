package com.backupforce.relationship;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Analyzes child relationships for Salesforce objects to enable relationship-aware backups.
 * 
 * When backing up a parent object (e.g., Account) with a record limit, this analyzer
 * discovers all child objects (Contacts, Opportunities, Cases, etc.) that should also
 * be included to maintain data integrity during restore.
 * 
 * Example: If you backup 100 Accounts, this will identify that you also need:
 * - Contacts where AccountId IN (:accountIds)
 * - Opportunities where AccountId IN (:accountIds)  
 * - Cases where AccountId IN (:accountIds)
 * - etc.
 */
public class ChildRelationshipAnalyzer implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(ChildRelationshipAnalyzer.class);
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final CloseableHttpClient httpClient;
    
    // Cache for object metadata
    private final Map<String, ObjectRelationshipInfo> metadataCache = new HashMap<>();
    
    // Objects that should not be auto-included (system objects, share objects, etc.)
    private static final Set<String> EXCLUDED_CHILD_OBJECTS = new HashSet<>(Arrays.asList(
        // History and tracking objects
        "AccountHistory", "ContactHistory", "OpportunityHistory", "CaseHistory", "LeadHistory",
        // Share objects (security-related)
        "AccountShare", "ContactShare", "OpportunityShare", "CaseShare", "LeadShare",
        // Feed objects
        "AccountFeed", "ContactFeed", "OpportunityFeed", "CaseFeed", "LeadFeed",
        // Team members (often not needed in backup)
        "AccountTeamMember", "OpportunityTeamMember", "CaseTeamMember",
        // Tag objects
        "AccountTag", "ContactTag", "OpportunityTag", "CaseTag",
        // Change events
        "AccountChangeEvent", "ContactChangeEvent", "OpportunityChangeEvent"
    ));
    
    // Common child objects that are typically wanted in backups
    private static final Set<String> PRIORITY_CHILD_OBJECTS = new HashSet<>(Arrays.asList(
        "Contact", "Opportunity", "Case", "Task", "Event", "Note", "Attachment",
        "OpportunityLineItem", "OpportunityContactRole", "CaseComment",
        "Contract", "Order", "OrderItem", "Quote", "QuoteLineItem",
        "Asset", "Entitlement", "ServiceContract"
    ));
    
    public ChildRelationshipAnalyzer(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.httpClient = HttpClients.createDefault();
    }
    
    /**
     * Analyzes a parent object and returns all its child relationships.
     * 
     * @param objectName The parent object API name (e.g., "Account")
     * @return ObjectRelationshipInfo containing all child relationships
     */
    public ObjectRelationshipInfo analyzeObject(String objectName) throws IOException, ParseException {
        if (metadataCache.containsKey(objectName)) {
            return metadataCache.get(objectName);
        }
        
        String url = String.format("%s/services/data/v%s/sobjects/%s/describe", instanceUrl, apiVersion, objectName);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + accessToken);
        
        try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (response.getCode() >= 400) {
                throw new IOException("Failed to describe object " + objectName + ": " + responseBody);
            }
            
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            ObjectRelationshipInfo info = parseChildRelationships(objectName, json);
            metadataCache.put(objectName, info);
            
            logger.info("Analyzed {}: found {} child relationships", objectName, info.getChildRelationships().size());
            
            return info;
        }
    }
    
    private ObjectRelationshipInfo parseChildRelationships(String objectName, JsonObject json) {
        ObjectRelationshipInfo info = new ObjectRelationshipInfo(objectName);
        
        // Parse childRelationships array from describe result
        if (json.has("childRelationships") && json.get("childRelationships").isJsonArray()) {
            JsonArray childRelationships = json.getAsJsonArray("childRelationships");
            
            for (int i = 0; i < childRelationships.size(); i++) {
                JsonObject rel = childRelationships.get(i).getAsJsonObject();
                
                String childSObject = rel.has("childSObject") ? rel.get("childSObject").getAsString() : null;
                String relationshipName = rel.has("relationshipName") && !rel.get("relationshipName").isJsonNull() 
                    ? rel.get("relationshipName").getAsString() : null;
                String fieldName = rel.has("field") ? rel.get("field").getAsString() : null;
                boolean cascadeDelete = rel.has("cascadeDelete") && rel.get("cascadeDelete").getAsBoolean();
                
                if (childSObject != null && fieldName != null) {
                    // Skip excluded objects
                    if (isExcludedObject(childSObject)) {
                        continue;
                    }
                    
                    ChildRelationship childRel = new ChildRelationship(
                        childSObject, 
                        fieldName, 
                        relationshipName,
                        cascadeDelete
                    );
                    
                    // Mark priority objects
                    childRel.setPriority(PRIORITY_CHILD_OBJECTS.contains(childSObject));
                    
                    info.addChildRelationship(childRel);
                    
                    logger.info("{} -> {} via {} (cascade: {}, priority: {})", 
                        objectName, childSObject, fieldName, cascadeDelete, childRel.isPriority());
                }
            }
        }
        
        // Sort by priority (priority objects first)
        info.sortByPriority();
        
        return info;
    }
    
    private boolean isExcludedObject(String objectName) {
        // Exclude specific objects
        if (EXCLUDED_CHILD_OBJECTS.contains(objectName)) {
            return true;
        }
        
        // Exclude objects ending with common suffixes
        String[] excludedSuffixes = {
            "History", "Share", "Feed", "ChangeEvent", "Tag", "__Tag"
        };
        
        for (String suffix : excludedSuffixes) {
            if (objectName.endsWith(suffix)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Recursively discovers all child relationships up to a specified depth.
     * Only recurses into PRIORITY child objects to avoid excessive API calls.
     * 
     * @param rootObject The root parent object (e.g., "Account")
     * @param maxDepth Maximum depth to traverse (1 = direct children only, 2 = children + grandchildren, etc.)
     * @return RelationshipTree containing the complete hierarchy
     */
    public RelationshipTree buildRelationshipTree(String rootObject, int maxDepth) throws IOException, ParseException {
        RelationshipTree tree = new RelationshipTree(rootObject);
        buildTreeRecursive(tree.getRoot(), maxDepth, new HashSet<>());
        return tree;
    }
    
    private void buildTreeRecursive(RelationshipNode node, int remainingDepth, Set<String> visited) 
            throws IOException, ParseException {
        
        if (remainingDepth <= 0 || visited.contains(node.getObjectName())) {
            return;
        }
        
        visited.add(node.getObjectName());
        
        ObjectRelationshipInfo info = analyzeObject(node.getObjectName());
        
        for (ChildRelationship childRel : info.getChildRelationships()) {
            String childObject = childRel.getChildSObject();
            
            // Skip if already visited (prevent circular references)
            if (visited.contains(childObject)) {
                continue;
            }
            
            RelationshipNode childNode = new RelationshipNode(
                childObject,
                childRel.getFieldName(),
                childRel.getRelationshipName(),
                node.getDepth() + 1
            );
            childNode.setCascadeDelete(childRel.isCascadeDelete());
            childNode.setPriority(childRel.isPriority());
            
            node.addChild(childNode);
            
            // Only recursively process PRIORITY children to avoid excessive API calls
            // Non-priority objects at depth 1 don't need their children analyzed
            if (childRel.isPriority()) {
                buildTreeRecursive(childNode, remainingDepth - 1, new HashSet<>(visited));
            }
        }
    }
    
    /**
     * Gets the SOQL WHERE clause to filter child records by parent IDs.
     * 
     * @param childRelationship The child relationship info
     * @param parentIds Set of parent record IDs to filter by
     * @return WHERE clause string (e.g., "AccountId IN ('001xxx','001yyy')")
     */
    public String buildChildWhereClause(ChildRelationship childRelationship, Set<String> parentIds) {
        if (parentIds.isEmpty()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(childRelationship.getFieldName());
        sb.append(" IN (");
        
        boolean first = true;
        for (String id : parentIds) {
            if (!first) {
                sb.append(",");
            }
            sb.append("'").append(id).append("'");
            first = false;
        }
        
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * Gets priority child relationships for an object (commonly needed in backups).
     */
    public List<ChildRelationship> getPriorityChildren(String objectName) throws IOException, ParseException {
        ObjectRelationshipInfo info = analyzeObject(objectName);
        List<ChildRelationship> priority = new ArrayList<>();
        
        for (ChildRelationship rel : info.getChildRelationships()) {
            if (rel.isPriority()) {
                priority.add(rel);
            }
        }
        
        return priority;
    }
    
    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.warn("Error closing HTTP client", e);
        }
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Holds relationship information for a single object.
     */
    public static class ObjectRelationshipInfo {
        private final String objectName;
        private final List<ChildRelationship> childRelationships = new ArrayList<>();
        
        public ObjectRelationshipInfo(String objectName) {
            this.objectName = objectName;
        }
        
        public String getObjectName() { return objectName; }
        public List<ChildRelationship> getChildRelationships() { return childRelationships; }
        
        public void addChildRelationship(ChildRelationship rel) {
            childRelationships.add(rel);
        }
        
        public void sortByPriority() {
            childRelationships.sort((a, b) -> Boolean.compare(b.isPriority(), a.isPriority()));
        }
        
        /**
         * Get child relationships by child object name
         */
        public List<ChildRelationship> getRelationshipsForChild(String childObjectName) {
            List<ChildRelationship> matches = new ArrayList<>();
            for (ChildRelationship rel : childRelationships) {
                if (rel.getChildSObject().equals(childObjectName)) {
                    matches.add(rel);
                }
            }
            return matches;
        }
    }
    
    /**
     * Represents a single parent-child relationship.
     */
    public static class ChildRelationship {
        private final String childSObject;
        private final String fieldName;      // Field on child that references parent (e.g., AccountId)
        private final String relationshipName; // SOQL relationship name (e.g., Contacts)
        private final boolean cascadeDelete;
        private boolean priority;
        
        public ChildRelationship(String childSObject, String fieldName, String relationshipName, boolean cascadeDelete) {
            this.childSObject = childSObject;
            this.fieldName = fieldName;
            this.relationshipName = relationshipName;
            this.cascadeDelete = cascadeDelete;
        }
        
        public String getChildSObject() { return childSObject; }
        public String getFieldName() { return fieldName; }
        public String getRelationshipName() { return relationshipName; }
        public boolean isCascadeDelete() { return cascadeDelete; }
        public boolean isPriority() { return priority; }
        public void setPriority(boolean priority) { this.priority = priority; }
        
        @Override
        public String toString() {
            return String.format("%s.%s (relationship: %s, cascade: %s)", 
                childSObject, fieldName, relationshipName, cascadeDelete);
        }
    }
    
    /**
     * Represents the complete relationship tree for an object hierarchy.
     */
    public static class RelationshipTree {
        private final RelationshipNode root;
        
        public RelationshipTree(String rootObjectName) {
            this.root = new RelationshipNode(rootObjectName, null, null, 0);
        }
        
        public RelationshipNode getRoot() { return root; }
        
        /**
         * Get all objects in the tree at a specific depth.
         */
        public List<RelationshipNode> getNodesAtDepth(int depth) {
            List<RelationshipNode> nodes = new ArrayList<>();
            collectNodesAtDepth(root, depth, nodes);
            return nodes;
        }
        
        private void collectNodesAtDepth(RelationshipNode node, int targetDepth, List<RelationshipNode> result) {
            if (node.getDepth() == targetDepth) {
                result.add(node);
            }
            for (RelationshipNode child : node.getChildren()) {
                collectNodesAtDepth(child, targetDepth, result);
            }
        }
        
        /**
         * Get all objects in the tree as a flat list, ordered by depth (parents first).
         */
        public List<RelationshipNode> getAllNodesOrdered() {
            List<RelationshipNode> all = new ArrayList<>();
            collectAllNodes(root, all);
            all.sort(Comparator.comparingInt(RelationshipNode::getDepth));
            return all;
        }
        
        private void collectAllNodes(RelationshipNode node, List<RelationshipNode> result) {
            result.add(node);
            for (RelationshipNode child : node.getChildren()) {
                collectAllNodes(child, result);
            }
        }
        
        /**
         * Print the tree for debugging.
         */
        public String printTree() {
            StringBuilder sb = new StringBuilder();
            printNode(root, sb, "", true);
            return sb.toString();
        }
        
        private void printNode(RelationshipNode node, StringBuilder sb, String prefix, boolean isLast) {
            sb.append(prefix);
            sb.append(isLast ? "└── " : "├── ");
            sb.append(node.getObjectName());
            if (node.getParentField() != null) {
                sb.append(" (via ").append(node.getParentField()).append(")");
            }
            if (node.isPriority()) {
                sb.append(" ★");
            }
            sb.append("\n");
            
            List<RelationshipNode> children = node.getChildren();
            for (int i = 0; i < children.size(); i++) {
                printNode(children.get(i), sb, prefix + (isLast ? "    " : "│   "), i == children.size() - 1);
            }
        }
    }
    
    /**
     * Represents a node in the relationship tree.
     */
    public static class RelationshipNode {
        private final String objectName;
        private final String parentField;    // Field that references parent
        private final String relationshipName;
        private final int depth;
        private final List<RelationshipNode> children = new ArrayList<>();
        private boolean cascadeDelete;
        private boolean priority;
        
        public RelationshipNode(String objectName, String parentField, String relationshipName, int depth) {
            this.objectName = objectName;
            this.parentField = parentField;
            this.relationshipName = relationshipName;
            this.depth = depth;
        }
        
        public String getObjectName() { return objectName; }
        public String getParentField() { return parentField; }
        public String getRelationshipName() { return relationshipName; }
        public int getDepth() { return depth; }
        public List<RelationshipNode> getChildren() { return children; }
        public boolean isCascadeDelete() { return cascadeDelete; }
        public boolean isPriority() { return priority; }
        
        public void setCascadeDelete(boolean cascadeDelete) { this.cascadeDelete = cascadeDelete; }
        public void setPriority(boolean priority) { this.priority = priority; }
        
        public void addChild(RelationshipNode child) {
            children.add(child);
        }
        
        public boolean hasChildren() {
            return !children.isEmpty();
        }
    }
}
