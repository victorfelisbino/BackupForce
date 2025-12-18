package com.backupforce.restore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Determines the optimal order to restore Salesforce objects based on their relationships.
 * Parent objects must be restored before child objects to ensure lookup fields can be populated.
 * 
 * Uses topological sorting to order objects by their dependencies.
 */
public class DependencyOrderer {
    
    private static final Logger logger = LoggerFactory.getLogger(DependencyOrderer.class);
    
    private final RelationshipManager relationshipManager;
    private Consumer<String> logCallback;
    
    // Standard Salesforce objects that should be restored first
    private static final Set<String> PRIORITY_OBJECTS = Set.of(
        "User", "RecordType", "BusinessHours", "Organization",
        "UserRole", "Profile", "PermissionSet", "Group"
    );
    
    // Objects that commonly have no dependencies
    private static final Set<String> LEAF_OBJECTS = Set.of(
        "Attachment", "ContentVersion", "ContentDocument", "ContentDocumentLink",
        "Note", "Task", "Event", "EmailMessage", "FeedItem", "FeedComment"
    );
    
    public DependencyOrderer(RelationshipManager relationshipManager) {
        this.relationshipManager = relationshipManager;
    }
    
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }
    
    private void log(String message) {
        logger.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }
    
    /**
     * Orders objects for restoration based on their relationships.
     * Parent objects appear before child objects.
     * 
     * @param objectNames Set of object names to order
     * @return List of objects in optimal restore order
     */
    public List<String> orderForRestore(Set<String> objectNames) throws IOException {
        log("Analyzing dependencies for " + objectNames.size() + " objects...");
        
        // Build dependency graph
        Map<String, Set<String>> dependencies = buildDependencyGraph(objectNames);
        
        // Topological sort
        List<String> ordered = topologicalSort(dependencies, objectNames);
        
        log("Dependency analysis complete. Restore order determined.");
        
        return ordered;
    }
    
    /**
     * Builds a dependency graph where each object maps to the objects it depends on.
     * An object depends on another if it has a lookup/master-detail relationship to it.
     */
    private Map<String, Set<String>> buildDependencyGraph(Set<String> objectNames) throws IOException {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        
        for (String objectName : objectNames) {
            Set<String> deps = new LinkedHashSet<>();
            
            try {
                RelationshipManager.ObjectMetadata metadata = relationshipManager.describeObject(objectName);
                
                for (RelationshipManager.RelationshipField relField : metadata.getRelationshipFields()) {
                    // Skip if this is an optional relationship (not required)
                    if (!relField.getFieldInfo().isRequired()) {
                        continue;
                    }
                    
                    // Add dependencies for required lookup/master-detail fields
                    for (String refObject : relField.getReferenceTo()) {
                        // Only add as dependency if we're also restoring this object
                        if (objectNames.contains(refObject) && !refObject.equals(objectName)) {
                            deps.add(refObject);
                            logger.debug("{} depends on {} (via {})", 
                                        objectName, refObject, relField.getFieldInfo().getName());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not analyze dependencies for {}: {}", objectName, e.getMessage());
            }
            
            dependencies.put(objectName, deps);
        }
        
        return dependencies;
    }
    
    /**
     * Performs topological sort on the dependency graph.
     * Returns objects in order where dependencies come before dependents.
     */
    private List<String> topologicalSort(Map<String, Set<String>> dependencies, Set<String> objectNames) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> currentPath = new HashSet<>();
        
        // Process priority objects first
        for (String obj : PRIORITY_OBJECTS) {
            if (objectNames.contains(obj) && !visited.contains(obj)) {
                visitNode(obj, dependencies, visited, currentPath, result);
            }
        }
        
        // Process remaining objects in alphabetical order
        List<String> remaining = objectNames.stream()
            .filter(obj -> !visited.contains(obj))
            .sorted()
            .collect(Collectors.toList());
        
        for (String obj : remaining) {
            if (!visited.contains(obj)) {
                visitNode(obj, dependencies, visited, currentPath, result);
            }
        }
        
        return result;
    }
    
    /**
     * DFS visit for topological sort
     */
    private void visitNode(String node, Map<String, Set<String>> dependencies,
                           Set<String> visited, Set<String> currentPath, List<String> result) {
        
        if (currentPath.contains(node)) {
            // Circular dependency detected - log warning but continue
            logger.warn("Circular dependency detected involving: {}", node);
            return;
        }
        
        if (visited.contains(node)) {
            return;
        }
        
        currentPath.add(node);
        
        // Visit all dependencies first
        Set<String> deps = dependencies.getOrDefault(node, Collections.emptySet());
        for (String dep : deps) {
            visitNode(dep, dependencies, visited, currentPath, result);
        }
        
        currentPath.remove(node);
        visited.add(node);
        result.add(node);
    }
    
    /**
     * Gets dependency information for display purposes.
     * Returns a map of object name -> list of objects it depends on.
     */
    public Map<String, List<String>> getDependencyInfo(Set<String> objectNames) throws IOException {
        Map<String, Set<String>> deps = buildDependencyGraph(objectNames);
        Map<String, List<String>> info = new LinkedHashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : deps.entrySet()) {
            info.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        
        return info;
    }
    
    /**
     * Checks if the given object order respects all dependencies.
     * Returns a list of violations if any.
     */
    public List<String> validateOrder(List<String> orderedObjects) throws IOException {
        List<String> violations = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        Set<String> objectSet = new HashSet<>(orderedObjects);
        
        Map<String, Set<String>> dependencies = buildDependencyGraph(objectSet);
        
        for (String object : orderedObjects) {
            Set<String> deps = dependencies.getOrDefault(object, Collections.emptySet());
            
            for (String dep : deps) {
                if (!processed.contains(dep) && objectSet.contains(dep)) {
                    violations.add(object + " depends on " + dep + " but " + dep + " comes later");
                }
            }
            
            processed.add(object);
        }
        
        return violations;
    }
    
    /**
     * Groups objects into batches that can be processed in parallel.
     * Objects in the same batch have no dependencies on each other.
     */
    public List<Set<String>> groupForParallelProcessing(Set<String> objectNames) throws IOException {
        List<Set<String>> groups = new ArrayList<>();
        Set<String> remaining = new HashSet<>(objectNames);
        Map<String, Set<String>> dependencies = buildDependencyGraph(objectNames);
        
        while (!remaining.isEmpty()) {
            Set<String> currentGroup = new LinkedHashSet<>();
            
            // Find all objects with no unprocessed dependencies
            for (String obj : new ArrayList<>(remaining)) {
                Set<String> deps = dependencies.getOrDefault(obj, Collections.emptySet());
                boolean allDepsSatisfied = true;
                
                for (String dep : deps) {
                    if (remaining.contains(dep)) {
                        allDepsSatisfied = false;
                        break;
                    }
                }
                
                if (allDepsSatisfied) {
                    currentGroup.add(obj);
                }
            }
            
            if (currentGroup.isEmpty()) {
                // Circular dependency - add remaining objects to final group
                logger.warn("Could not resolve all dependencies, adding remaining objects: {}", remaining);
                currentGroup.addAll(remaining);
            }
            
            groups.add(currentGroup);
            remaining.removeAll(currentGroup);
        }
        
        return groups;
    }
}
