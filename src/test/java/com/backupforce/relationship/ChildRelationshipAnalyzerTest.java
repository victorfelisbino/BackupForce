package com.backupforce.relationship;

import com.backupforce.relationship.ChildRelationshipAnalyzer.ChildRelationship;
import com.backupforce.relationship.ChildRelationshipAnalyzer.ObjectRelationshipInfo;
import com.backupforce.relationship.ChildRelationshipAnalyzer.RelationshipNode;
import com.backupforce.relationship.ChildRelationshipAnalyzer.RelationshipTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChildRelationshipAnalyzer inner classes.
 * Tests relationship data structures without requiring Salesforce API calls.
 */
@DisplayName("ChildRelationshipAnalyzer Tests")
class ChildRelationshipAnalyzerTest {

    // ==================== ChildRelationship Tests ====================
    
    @Test
    @DisplayName("ChildRelationship should store all properties correctly")
    void testChildRelationshipProperties() {
        ChildRelationship rel = new ChildRelationship(
            "Contact", "AccountId", "Contacts", true
        );
        
        assertEquals("Contact", rel.getChildSObject());
        assertEquals("AccountId", rel.getFieldName());
        assertEquals("Contacts", rel.getRelationshipName());
        assertTrue(rel.isCascadeDelete());
    }
    
    @Test
    @DisplayName("ChildRelationship priority should be modifiable")
    void testChildRelationshipPriority() {
        ChildRelationship rel = new ChildRelationship(
            "Opportunity", "AccountId", "Opportunities", false
        );
        
        assertFalse(rel.isPriority());
        
        rel.setPriority(true);
        assertTrue(rel.isPriority());
    }
    
    @Test
    @DisplayName("ChildRelationship toString should include all info")
    void testChildRelationshipToString() {
        ChildRelationship rel = new ChildRelationship(
            "Case", "AccountId", "Cases", true
        );
        
        String str = rel.toString();
        assertTrue(str.contains("Case"));
        assertTrue(str.contains("AccountId"));
        assertTrue(str.contains("Cases"));
        assertTrue(str.contains("true"));
    }
    
    @Test
    @DisplayName("ChildRelationship without relationship name should work")
    void testChildRelationshipNullRelationshipName() {
        ChildRelationship rel = new ChildRelationship(
            "CustomObject__c", "Account__c", null, false
        );
        
        assertEquals("CustomObject__c", rel.getChildSObject());
        assertNull(rel.getRelationshipName());
        assertFalse(rel.isCascadeDelete());
    }
    
    // ==================== ObjectRelationshipInfo Tests ====================
    
    @Test
    @DisplayName("ObjectRelationshipInfo should store object name")
    void testObjectRelationshipInfoObjectName() {
        ObjectRelationshipInfo info = new ObjectRelationshipInfo("Account");
        
        assertEquals("Account", info.getObjectName());
        assertTrue(info.getChildRelationships().isEmpty());
    }
    
    @Test
    @DisplayName("ObjectRelationshipInfo should add child relationships")
    void testObjectRelationshipInfoAddChildren() {
        ObjectRelationshipInfo info = new ObjectRelationshipInfo("Account");
        
        info.addChildRelationship(new ChildRelationship("Contact", "AccountId", "Contacts", true));
        info.addChildRelationship(new ChildRelationship("Opportunity", "AccountId", "Opportunities", false));
        info.addChildRelationship(new ChildRelationship("Case", "AccountId", "Cases", false));
        
        assertEquals(3, info.getChildRelationships().size());
    }
    
    @Test
    @DisplayName("ObjectRelationshipInfo should sort by priority")
    void testObjectRelationshipInfoSortByPriority() {
        ObjectRelationshipInfo info = new ObjectRelationshipInfo("Account");
        
        ChildRelationship nonPriority1 = new ChildRelationship("CustomObj__c", "Account__c", null, false);
        ChildRelationship priority1 = new ChildRelationship("Contact", "AccountId", "Contacts", true);
        priority1.setPriority(true);
        ChildRelationship nonPriority2 = new ChildRelationship("Task", "WhatId", "Tasks", false);
        ChildRelationship priority2 = new ChildRelationship("Opportunity", "AccountId", "Opportunities", false);
        priority2.setPriority(true);
        
        info.addChildRelationship(nonPriority1);
        info.addChildRelationship(priority1);
        info.addChildRelationship(nonPriority2);
        info.addChildRelationship(priority2);
        
        info.sortByPriority();
        
        List<ChildRelationship> sorted = info.getChildRelationships();
        assertTrue(sorted.get(0).isPriority());
        assertTrue(sorted.get(1).isPriority());
        assertFalse(sorted.get(2).isPriority());
        assertFalse(sorted.get(3).isPriority());
    }
    
    @Test
    @DisplayName("ObjectRelationshipInfo should find relationships by child name")
    void testGetRelationshipsForChild() {
        ObjectRelationshipInfo info = new ObjectRelationshipInfo("Account");
        
        info.addChildRelationship(new ChildRelationship("Contact", "AccountId", "Contacts", true));
        info.addChildRelationship(new ChildRelationship("Contact", "ParentAccountId", null, false)); // Another Contact field
        info.addChildRelationship(new ChildRelationship("Opportunity", "AccountId", "Opportunities", false));
        
        List<ChildRelationship> contactRels = info.getRelationshipsForChild("Contact");
        assertEquals(2, contactRels.size());
        
        List<ChildRelationship> oppRels = info.getRelationshipsForChild("Opportunity");
        assertEquals(1, oppRels.size());
        
        List<ChildRelationship> caseRels = info.getRelationshipsForChild("Case");
        assertEquals(0, caseRels.size());
    }
    
    // ==================== RelationshipNode Tests ====================
    
    @Test
    @DisplayName("RelationshipNode should store all properties")
    void testRelationshipNodeProperties() {
        RelationshipNode node = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        
        assertEquals("Contact", node.getObjectName());
        assertEquals("AccountId", node.getParentField());
        assertEquals("Contacts", node.getRelationshipName());
        assertEquals(1, node.getDepth());
        assertFalse(node.hasChildren());
    }
    
    @Test
    @DisplayName("RelationshipNode root should have null parent field")
    void testRelationshipNodeRoot() {
        RelationshipNode root = new RelationshipNode("Account", null, null, 0);
        
        assertEquals("Account", root.getObjectName());
        assertNull(root.getParentField());
        assertNull(root.getRelationshipName());
        assertEquals(0, root.getDepth());
    }
    
    @Test
    @DisplayName("RelationshipNode should add children correctly")
    void testRelationshipNodeChildren() {
        RelationshipNode parent = new RelationshipNode("Account", null, null, 0);
        RelationshipNode child1 = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        RelationshipNode child2 = new RelationshipNode("Opportunity", "AccountId", "Opportunities", 1);
        
        parent.addChild(child1);
        parent.addChild(child2);
        
        assertTrue(parent.hasChildren());
        assertEquals(2, parent.getChildren().size());
        assertEquals("Contact", parent.getChildren().get(0).getObjectName());
        assertEquals("Opportunity", parent.getChildren().get(1).getObjectName());
    }
    
    @Test
    @DisplayName("RelationshipNode should track cascade delete")
    void testRelationshipNodeCascadeDelete() {
        RelationshipNode node = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        
        assertFalse(node.isCascadeDelete());
        
        node.setCascadeDelete(true);
        assertTrue(node.isCascadeDelete());
    }
    
    @Test
    @DisplayName("RelationshipNode should track priority")
    void testRelationshipNodePriority() {
        RelationshipNode node = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        
        assertFalse(node.isPriority());
        
        node.setPriority(true);
        assertTrue(node.isPriority());
    }
    
    @Test
    @DisplayName("RelationshipNode depth should track hierarchy correctly")
    void testRelationshipNodeDepth() {
        RelationshipNode root = new RelationshipNode("Account", null, null, 0);
        RelationshipNode level1 = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        RelationshipNode level2 = new RelationshipNode("Task", "WhoId", "Tasks", 2);
        
        root.addChild(level1);
        level1.addChild(level2);
        
        assertEquals(0, root.getDepth());
        assertEquals(1, level1.getDepth());
        assertEquals(2, level2.getDepth());
    }
    
    // ==================== RelationshipTree Tests ====================
    
    @Test
    @DisplayName("RelationshipTree should create root node")
    void testRelationshipTreeRoot() {
        RelationshipTree tree = new RelationshipTree("Account");
        
        assertNotNull(tree.getRoot());
        assertEquals("Account", tree.getRoot().getObjectName());
        assertEquals(0, tree.getRoot().getDepth());
    }
    
    @Test
    @DisplayName("RelationshipTree should find nodes at depth")
    void testRelationshipTreeNodesAtDepth() {
        RelationshipTree tree = new RelationshipTree("Account");
        
        RelationshipNode contact = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        RelationshipNode opportunity = new RelationshipNode("Opportunity", "AccountId", "Opportunities", 1);
        RelationshipNode task = new RelationshipNode("Task", "WhoId", "Tasks", 2);
        
        tree.getRoot().addChild(contact);
        tree.getRoot().addChild(opportunity);
        contact.addChild(task);
        
        List<RelationshipNode> depth0 = tree.getNodesAtDepth(0);
        assertEquals(1, depth0.size());
        assertEquals("Account", depth0.get(0).getObjectName());
        
        List<RelationshipNode> depth1 = tree.getNodesAtDepth(1);
        assertEquals(2, depth1.size());
        
        List<RelationshipNode> depth2 = tree.getNodesAtDepth(2);
        assertEquals(1, depth2.size());
        assertEquals("Task", depth2.get(0).getObjectName());
    }
    
    @Test
    @DisplayName("RelationshipTree should return all nodes ordered by depth")
    void testRelationshipTreeAllNodesOrdered() {
        RelationshipTree tree = new RelationshipTree("Account");
        
        RelationshipNode contact = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        RelationshipNode task = new RelationshipNode("Task", "WhoId", "Tasks", 2);
        
        tree.getRoot().addChild(contact);
        contact.addChild(task);
        
        List<RelationshipNode> allNodes = tree.getAllNodesOrdered();
        
        assertEquals(3, allNodes.size());
        assertEquals(0, allNodes.get(0).getDepth());
        assertEquals(1, allNodes.get(1).getDepth());
        assertEquals(2, allNodes.get(2).getDepth());
    }
    
    @Test
    @DisplayName("RelationshipTree printTree should not throw")
    void testRelationshipTreePrint() {
        RelationshipTree tree = new RelationshipTree("Account");
        
        RelationshipNode contact = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        contact.setPriority(true);
        tree.getRoot().addChild(contact);
        
        String output = tree.printTree();
        assertNotNull(output);
        assertTrue(output.contains("Account"));
        assertTrue(output.contains("Contact"));
    }
    
    // ==================== Relationship Scenario Tests ====================
    
    @Test
    @DisplayName("Complex hierarchy should maintain correct parent-child relationships")
    void testComplexHierarchy() {
        // Create a realistic hierarchy: Account -> Contact -> Task
        //                              Account -> Opportunity -> OpportunityLineItem
        RelationshipTree tree = new RelationshipTree("Account");
        
        RelationshipNode contact = new RelationshipNode("Contact", "AccountId", "Contacts", 1);
        contact.setPriority(true);
        RelationshipNode opportunity = new RelationshipNode("Opportunity", "AccountId", "Opportunities", 1);
        opportunity.setPriority(true);
        
        RelationshipNode task = new RelationshipNode("Task", "WhoId", "Tasks", 2);
        RelationshipNode lineItem = new RelationshipNode("OpportunityLineItem", "OpportunityId", "OpportunityLineItems", 2);
        lineItem.setPriority(true);
        
        tree.getRoot().addChild(contact);
        tree.getRoot().addChild(opportunity);
        contact.addChild(task);
        opportunity.addChild(lineItem);
        
        // Verify structure
        assertEquals(2, tree.getRoot().getChildren().size());
        assertEquals(1, contact.getChildren().size());
        assertEquals(1, opportunity.getChildren().size());
        
        // Verify all nodes
        List<RelationshipNode> allNodes = tree.getAllNodesOrdered();
        assertEquals(5, allNodes.size());
        
        // Verify priority count
        long priorityCount = allNodes.stream().filter(RelationshipNode::isPriority).count();
        assertEquals(3, priorityCount);
    }
    
    @Test
    @DisplayName("Multiple children at same level should all be accessible")
    void testMultipleChildrenAtSameLevel() {
        RelationshipTree tree = new RelationshipTree("Account");
        
        String[] childObjects = {"Contact", "Opportunity", "Case", "Task", "Note", "Attachment"};
        
        for (String obj : childObjects) {
            RelationshipNode child = new RelationshipNode(obj, "AccountId", obj + "s", 1);
            tree.getRoot().addChild(child);
        }
        
        assertEquals(6, tree.getRoot().getChildren().size());
        
        List<RelationshipNode> depth1Nodes = tree.getNodesAtDepth(1);
        assertEquals(6, depth1Nodes.size());
    }
}
