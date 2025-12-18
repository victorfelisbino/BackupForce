package com.backupforce.restore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for DependencyOrderer with mocked RelationshipManager.
 * Tests various scenarios to ensure data integrity during restore ordering.
 */
@DisplayName("DependencyOrderer with Mocked RelationshipManager")
@ExtendWith(MockitoExtension.class)
class DependencyOrdererMockedTest {
    
    @Mock
    private RelationshipManager mockRelationshipManager;
    
    private DependencyOrderer orderer;
    
    @BeforeEach
    void setUp() {
        orderer = new DependencyOrderer(mockRelationshipManager);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Creates a mock ObjectMetadata with no relationship fields (leaf object)
     */
    private RelationshipManager.ObjectMetadata createLeafMetadata(String objectName) {
        return new RelationshipManager.ObjectMetadata(objectName);
    }
    
    /**
     * Creates a mock ObjectMetadata with required lookup to parent
     */
    private RelationshipManager.ObjectMetadata createChildMetadata(String objectName, String... parentObjects) {
        RelationshipManager.ObjectMetadata metadata = new RelationshipManager.ObjectMetadata(objectName);
        
        for (String parent : parentObjects) {
            RelationshipManager.FieldInfo fieldInfo = new RelationshipManager.FieldInfo(
                parent + "Id", "reference", parent + " Lookup"
            );
            fieldInfo.setRequired(true);
            
            RelationshipManager.RelationshipField relField = new RelationshipManager.RelationshipField(
                fieldInfo, 
                Collections.singletonList(parent),
                parent
            );
            
            metadata.addRelationshipField(relField);
        }
        
        return metadata;
    }
    
    /**
     * Creates a mock ObjectMetadata with optional lookup (not required)
     */
    private RelationshipManager.ObjectMetadata createOptionalLookupMetadata(String objectName, String parentObject) {
        RelationshipManager.ObjectMetadata metadata = new RelationshipManager.ObjectMetadata(objectName);
        
        RelationshipManager.FieldInfo fieldInfo = new RelationshipManager.FieldInfo(
            parentObject + "Id", "reference", parentObject + " Lookup"
        );
        fieldInfo.setRequired(false); // Optional lookup
        
        RelationshipManager.RelationshipField relField = new RelationshipManager.RelationshipField(
            fieldInfo, 
            Collections.singletonList(parentObject),
            parentObject
        );
        
        metadata.addRelationshipField(relField);
        return metadata;
    }
    
    /**
     * Creates metadata with self-reference (same object lookup)
     */
    private RelationshipManager.ObjectMetadata createSelfReferenceMetadata(String objectName) {
        RelationshipManager.ObjectMetadata metadata = new RelationshipManager.ObjectMetadata(objectName);
        
        RelationshipManager.FieldInfo fieldInfo = new RelationshipManager.FieldInfo(
            "ParentId", "reference", "Parent " + objectName
        );
        fieldInfo.setRequired(false);
        
        RelationshipManager.RelationshipField relField = new RelationshipManager.RelationshipField(
            fieldInfo, 
            Collections.singletonList(objectName), // Self-reference
            "Parent"
        );
        
        metadata.addRelationshipField(relField);
        return metadata;
    }
    
    // ==================== Simple Dependency Chain Tests ====================
    
    @Nested
    @DisplayName("Simple Dependency Chains")
    class SimpleDependencyChainTests {
        
        @Test
        @DisplayName("Single object with no dependencies")
        void testSingleObjectNoDependencies() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            
            List<String> result = orderer.orderForRestore(Set.of("Account"));
            
            assertEquals(1, result.size());
            assertEquals("Account", result.get(0));
        }
        
        @Test
        @DisplayName("Two objects: Contact depends on Account")
        void testSimpleParentChild() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account"));
            
            List<String> result = orderer.orderForRestore(Set.of("Account", "Contact"));
            
            assertEquals(2, result.size());
            assertTrue(result.indexOf("Account") < result.indexOf("Contact"),
                "Account must come before Contact");
        }
        
        @Test
        @DisplayName("Three-level chain: OpportunityLineItem -> Opportunity -> Account")
        void testThreeLevelChain() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Opportunity"))
                .thenReturn(createChildMetadata("Opportunity", "Account"));
            when(mockRelationshipManager.describeObject("OpportunityLineItem"))
                .thenReturn(createChildMetadata("OpportunityLineItem", "Opportunity"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Account", "Opportunity", "OpportunityLineItem"));
            
            assertEquals(3, result.size());
            assertTrue(result.indexOf("Account") < result.indexOf("Opportunity"),
                "Account must come before Opportunity");
            assertTrue(result.indexOf("Opportunity") < result.indexOf("OpportunityLineItem"),
                "Opportunity must come before OpportunityLineItem");
        }
        
        @Test
        @DisplayName("Deep chain: 5 levels of dependencies")
        void testDeepChain() throws Exception {
            // Level1 <- Level2 <- Level3 <- Level4 <- Level5
            when(mockRelationshipManager.describeObject("Level1"))
                .thenReturn(createLeafMetadata("Level1"));
            when(mockRelationshipManager.describeObject("Level2"))
                .thenReturn(createChildMetadata("Level2", "Level1"));
            when(mockRelationshipManager.describeObject("Level3"))
                .thenReturn(createChildMetadata("Level3", "Level2"));
            when(mockRelationshipManager.describeObject("Level4"))
                .thenReturn(createChildMetadata("Level4", "Level3"));
            when(mockRelationshipManager.describeObject("Level5"))
                .thenReturn(createChildMetadata("Level5", "Level4"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Level1", "Level2", "Level3", "Level4", "Level5"));
            
            assertEquals(5, result.size());
            
            // Verify strict ordering
            for (int i = 0; i < 4; i++) {
                String current = "Level" + (i + 1);
                String next = "Level" + (i + 2);
                assertTrue(result.indexOf(current) < result.indexOf(next),
                    current + " must come before " + next);
            }
        }
    }
    
    // ==================== Complex Graph Tests ====================
    
    @Nested
    @DisplayName("Complex Dependency Graphs")
    class ComplexGraphTests {
        
        @Test
        @DisplayName("Diamond dependency: D depends on B and C, both depend on A")
        void testDiamondDependency() throws Exception {
            // A (root)
            // B <- A, C <- A
            // D <- B, D <- C
            when(mockRelationshipManager.describeObject("A"))
                .thenReturn(createLeafMetadata("A"));
            when(mockRelationshipManager.describeObject("B"))
                .thenReturn(createChildMetadata("B", "A"));
            when(mockRelationshipManager.describeObject("C"))
                .thenReturn(createChildMetadata("C", "A"));
            when(mockRelationshipManager.describeObject("D"))
                .thenReturn(createChildMetadata("D", "B", "C"));
            
            List<String> result = orderer.orderForRestore(Set.of("A", "B", "C", "D"));
            
            assertEquals(4, result.size());
            
            // A must come first
            assertEquals("A", result.get(0));
            
            // B and C must come before D
            assertTrue(result.indexOf("B") < result.indexOf("D"));
            assertTrue(result.indexOf("C") < result.indexOf("D"));
            
            // D must be last
            assertEquals("D", result.get(3));
        }
        
        @Test
        @DisplayName("Multiple parents: Contact depends on Account and RecordType")
        void testMultipleParents() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("RecordType"))
                .thenReturn(createLeafMetadata("RecordType"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account", "RecordType"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Account", "RecordType", "Contact"));
            
            assertEquals(3, result.size());
            
            // Contact must be last
            assertEquals("Contact", result.get(2));
            
            // Both parents must come before Contact
            assertTrue(result.indexOf("Account") < result.indexOf("Contact"));
            assertTrue(result.indexOf("RecordType") < result.indexOf("Contact"));
        }
        
        @Test
        @DisplayName("Salesforce-like hierarchy: Account, Contact, Opportunity, OpportunityContactRole")
        void testSalesforceHierarchy() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account"));
            when(mockRelationshipManager.describeObject("Opportunity"))
                .thenReturn(createChildMetadata("Opportunity", "Account"));
            when(mockRelationshipManager.describeObject("OpportunityContactRole"))
                .thenReturn(createChildMetadata("OpportunityContactRole", "Contact", "Opportunity"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Account", "Contact", "Opportunity", "OpportunityContactRole"));
            
            assertEquals(4, result.size());
            
            // Account must be first
            assertEquals("Account", result.get(0));
            
            // Contact and Opportunity after Account
            assertTrue(result.indexOf("Account") < result.indexOf("Contact"));
            assertTrue(result.indexOf("Account") < result.indexOf("Opportunity"));
            
            // OCR must be last
            assertEquals("OpportunityContactRole", result.get(3));
        }
        
        @Test
        @DisplayName("Parallel independent branches")
        void testParallelBranches() throws Exception {
            // Two independent branches:
            // Branch 1: A1 <- B1 <- C1
            // Branch 2: A2 <- B2 <- C2
            when(mockRelationshipManager.describeObject("A1"))
                .thenReturn(createLeafMetadata("A1"));
            when(mockRelationshipManager.describeObject("B1"))
                .thenReturn(createChildMetadata("B1", "A1"));
            when(mockRelationshipManager.describeObject("C1"))
                .thenReturn(createChildMetadata("C1", "B1"));
            when(mockRelationshipManager.describeObject("A2"))
                .thenReturn(createLeafMetadata("A2"));
            when(mockRelationshipManager.describeObject("B2"))
                .thenReturn(createChildMetadata("B2", "A2"));
            when(mockRelationshipManager.describeObject("C2"))
                .thenReturn(createChildMetadata("C2", "B2"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("A1", "B1", "C1", "A2", "B2", "C2"));
            
            assertEquals(6, result.size());
            
            // Verify branch 1 ordering
            assertTrue(result.indexOf("A1") < result.indexOf("B1"));
            assertTrue(result.indexOf("B1") < result.indexOf("C1"));
            
            // Verify branch 2 ordering
            assertTrue(result.indexOf("A2") < result.indexOf("B2"));
            assertTrue(result.indexOf("B2") < result.indexOf("C2"));
        }
    }
    
    // ==================== Circular Dependency Tests ====================
    
    @Nested
    @DisplayName("Circular Dependency Handling")
    class CircularDependencyTests {
        
        @Test
        @DisplayName("Simple circular: A <-> B")
        void testSimpleCircular() throws Exception {
            // Both A and B depend on each other (should handle gracefully)
            when(mockRelationshipManager.describeObject("A"))
                .thenReturn(createChildMetadata("A", "B"));
            when(mockRelationshipManager.describeObject("B"))
                .thenReturn(createChildMetadata("B", "A"));
            
            // Should not throw, should return both objects
            List<String> result = orderer.orderForRestore(Set.of("A", "B"));
            
            assertEquals(2, result.size());
            assertTrue(result.contains("A"));
            assertTrue(result.contains("B"));
        }
        
        @Test
        @DisplayName("Circular chain: A -> B -> C -> A")
        void testCircularChain() throws Exception {
            when(mockRelationshipManager.describeObject("A"))
                .thenReturn(createChildMetadata("A", "C"));
            when(mockRelationshipManager.describeObject("B"))
                .thenReturn(createChildMetadata("B", "A"));
            when(mockRelationshipManager.describeObject("C"))
                .thenReturn(createChildMetadata("C", "B"));
            
            // Should not throw
            List<String> result = orderer.orderForRestore(Set.of("A", "B", "C"));
            
            assertEquals(3, result.size());
            assertTrue(result.containsAll(Set.of("A", "B", "C")));
        }
        
        @Test
        @DisplayName("Self-reference does not cause issues")
        void testSelfReference() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createSelfReferenceMetadata("Account"));
            
            List<String> result = orderer.orderForRestore(Set.of("Account"));
            
            assertEquals(1, result.size());
            assertEquals("Account", result.get(0));
        }
    }
    
    // ==================== Priority Objects Tests ====================
    
    @Nested
    @DisplayName("Priority Object Handling")
    class PriorityObjectTests {
        
        @Test
        @DisplayName("User object comes first")
        void testUserPriority() throws Exception {
            when(mockRelationshipManager.describeObject("User"))
                .thenReturn(createLeafMetadata("User"));
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createLeafMetadata("Contact"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Account", "Contact", "User"));
            
            assertEquals("User", result.get(0), "User should be first as priority object");
        }
        
        @Test
        @DisplayName("RecordType comes early")
        void testRecordTypePriority() throws Exception {
            when(mockRelationshipManager.describeObject("RecordType"))
                .thenReturn(createLeafMetadata("RecordType"));
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("CustomObject__c"))
                .thenReturn(createLeafMetadata("CustomObject__c"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Account", "CustomObject__c", "RecordType"));
            
            assertEquals("RecordType", result.get(0), "RecordType should be first as priority object");
        }
        
        @Test
        @DisplayName("Multiple priority objects come before regular objects")
        void testMultiplePriorityObjects() throws Exception {
            when(mockRelationshipManager.describeObject("User"))
                .thenReturn(createLeafMetadata("User"));
            when(mockRelationshipManager.describeObject("RecordType"))
                .thenReturn(createLeafMetadata("RecordType"));
            when(mockRelationshipManager.describeObject("Profile"))
                .thenReturn(createLeafMetadata("Profile"));
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Account", "User", "RecordType", "Profile"));
            
            // All priority objects should come before Account
            int accountIndex = result.indexOf("Account");
            assertTrue(result.indexOf("User") < accountIndex);
            assertTrue(result.indexOf("RecordType") < accountIndex);
            assertTrue(result.indexOf("Profile") < accountIndex);
        }
    }
    
    // ==================== Data Integrity Scenarios ====================
    
    @Nested
    @DisplayName("Data Integrity Scenarios")
    class DataIntegrityScenarios {
        
        @Test
        @DisplayName("Order validation passes for correctly ordered list")
        void testValidateCorrectOrder() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account"));
            
            List<String> correctOrder = Arrays.asList("Account", "Contact");
            List<String> violations = orderer.validateOrder(correctOrder);
            
            assertTrue(violations.isEmpty(), "No violations for correct order");
        }
        
        @Test
        @DisplayName("Order validation detects violation")
        void testValidateIncorrectOrder() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account"));
            
            List<String> incorrectOrder = Arrays.asList("Contact", "Account");
            List<String> violations = orderer.validateOrder(incorrectOrder);
            
            assertFalse(violations.isEmpty(), "Should detect violation");
            assertTrue(violations.get(0).contains("Contact"), 
                "Violation should mention Contact");
        }
        
        @Test
        @DisplayName("All objects included - no data loss")
        void testNoDataLoss() throws Exception {
            Set<String> inputObjects = new LinkedHashSet<>();
            for (int i = 0; i < 20; i++) {
                String name = "Object" + i;
                inputObjects.add(name);
                when(mockRelationshipManager.describeObject(name))
                    .thenReturn(createLeafMetadata(name));
            }
            
            List<String> result = orderer.orderForRestore(inputObjects);
            
            assertEquals(20, result.size(), "All objects must be included");
            assertTrue(result.containsAll(inputObjects), "No objects should be lost");
        }
        
        @Test
        @DisplayName("No duplicates in output")
        void testNoDuplicates() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account"));
            when(mockRelationshipManager.describeObject("Opportunity"))
                .thenReturn(createChildMetadata("Opportunity", "Account"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Account", "Contact", "Opportunity"));
            
            Set<String> resultSet = new HashSet<>(result);
            assertEquals(result.size(), resultSet.size(), "No duplicates should exist");
        }
        
        @Test
        @DisplayName("Empty input returns empty output")
        void testEmptyInput() throws Exception {
            List<String> result = orderer.orderForRestore(Collections.emptySet());
            
            assertTrue(result.isEmpty(), "Empty input should return empty output");
        }
        
        @Test
        @DisplayName("Parallel processing groups respect dependencies")
        void testParallelGroups() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account"));
            when(mockRelationshipManager.describeObject("Opportunity"))
                .thenReturn(createChildMetadata("Opportunity", "Account"));
            when(mockRelationshipManager.describeObject("Lead"))
                .thenReturn(createLeafMetadata("Lead"));
            
            List<Set<String>> groups = orderer.groupForParallelProcessing(
                Set.of("Account", "Contact", "Opportunity", "Lead"));
            
            assertFalse(groups.isEmpty());
            
            // First group should contain objects with no dependencies
            Set<String> firstGroup = groups.get(0);
            assertTrue(firstGroup.contains("Account") || firstGroup.contains("Lead"),
                "First group should contain root objects");
            
            // Contact and Opportunity should not be in first group with Account
            if (firstGroup.contains("Account")) {
                assertFalse(firstGroup.contains("Contact"));
                assertFalse(firstGroup.contains("Opportunity"));
            }
        }
    }
    
    // ==================== Edge Cases ====================
    
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Object name case sensitivity preserved")
        void testCaseSensitivity() throws Exception {
            when(mockRelationshipManager.describeObject("Custom_Object__c"))
                .thenReturn(createLeafMetadata("Custom_Object__c"));
            
            List<String> result = orderer.orderForRestore(Set.of("Custom_Object__c"));
            
            assertEquals("Custom_Object__c", result.get(0));
        }
        
        @Test
        @DisplayName("Optional lookups don't create hard dependencies")
        void testOptionalLookups() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Task"))
                .thenReturn(createOptionalLookupMetadata("Task", "Account"));
            
            // Task has optional lookup to Account - order should be flexible
            List<String> result = orderer.orderForRestore(Set.of("Account", "Task"));
            
            assertEquals(2, result.size());
            // No strict ordering required for optional lookups
        }
        
        @Test
        @DisplayName("External object dependency ignored if not in restore set")
        void testExternalDependencyIgnored() throws Exception {
            // Contact depends on Account, but Account is not in the restore set
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account"));
            
            List<String> result = orderer.orderForRestore(Set.of("Contact"));
            
            assertEquals(1, result.size());
            assertEquals("Contact", result.get(0));
        }
        
        @Test
        @DisplayName("Metadata fetch error handled gracefully")
        void testMetadataErrorHandling() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Problem"))
                .thenThrow(new IOException("API Error"));
            
            // Should not throw, should include Problem with no dependencies
            List<String> result = orderer.orderForRestore(Set.of("Account", "Problem"));
            
            assertEquals(2, result.size());
            assertTrue(result.contains("Account"));
            assertTrue(result.contains("Problem"));
        }
    }
    
    // ==================== Complex Real-World Scenarios ====================
    
    @Nested
    @DisplayName("Real-World Salesforce Scenarios")
    class RealWorldScenarios {
        
        @Test
        @DisplayName("Full CRM data model: Users, Accounts, Contacts, Opportunities, Cases, Tasks")
        void testFullCrmModel() throws Exception {
            // Setup realistic Salesforce data model
            when(mockRelationshipManager.describeObject("User"))
                .thenReturn(createLeafMetadata("User"));
            when(mockRelationshipManager.describeObject("RecordType"))
                .thenReturn(createLeafMetadata("RecordType"));
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Contact"))
                .thenReturn(createChildMetadata("Contact", "Account"));
            when(mockRelationshipManager.describeObject("Opportunity"))
                .thenReturn(createChildMetadata("Opportunity", "Account"));
            when(mockRelationshipManager.describeObject("Case"))
                .thenReturn(createChildMetadata("Case", "Account", "Contact"));
            when(mockRelationshipManager.describeObject("Task"))
                .thenReturn(createOptionalLookupMetadata("Task", "Account"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("User", "RecordType", "Account", "Contact", "Opportunity", "Case", "Task"));
            
            assertEquals(7, result.size());
            
            // User and RecordType should be early (priority objects)
            assertTrue(result.indexOf("User") < result.indexOf("Account"));
            assertTrue(result.indexOf("RecordType") < result.indexOf("Account"));
            
            // Account before its children
            assertTrue(result.indexOf("Account") < result.indexOf("Contact"));
            assertTrue(result.indexOf("Account") < result.indexOf("Opportunity"));
            
            // Case depends on both Account and Contact
            assertTrue(result.indexOf("Account") < result.indexOf("Case"));
            assertTrue(result.indexOf("Contact") < result.indexOf("Case"));
        }
        
        @Test
        @DisplayName("Product/Pricebook hierarchy: Product2, PricebookEntry, Pricebook2")
        void testProductHierarchy() throws Exception {
            when(mockRelationshipManager.describeObject("Product2"))
                .thenReturn(createLeafMetadata("Product2"));
            when(mockRelationshipManager.describeObject("Pricebook2"))
                .thenReturn(createLeafMetadata("Pricebook2"));
            when(mockRelationshipManager.describeObject("PricebookEntry"))
                .thenReturn(createChildMetadata("PricebookEntry", "Product2", "Pricebook2"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Product2", "Pricebook2", "PricebookEntry"));
            
            assertEquals(3, result.size());
            
            // PricebookEntry must be last
            assertEquals("PricebookEntry", result.get(2));
            
            // Both Product2 and Pricebook2 before PricebookEntry
            assertTrue(result.indexOf("Product2") < result.indexOf("PricebookEntry"));
            assertTrue(result.indexOf("Pricebook2") < result.indexOf("PricebookEntry"));
        }
        
        @Test
        @DisplayName("Quote workflow: Account, Opportunity, Quote, QuoteLineItem")
        void testQuoteWorkflow() throws Exception {
            when(mockRelationshipManager.describeObject("Account"))
                .thenReturn(createLeafMetadata("Account"));
            when(mockRelationshipManager.describeObject("Opportunity"))
                .thenReturn(createChildMetadata("Opportunity", "Account"));
            when(mockRelationshipManager.describeObject("Quote"))
                .thenReturn(createChildMetadata("Quote", "Opportunity"));
            when(mockRelationshipManager.describeObject("QuoteLineItem"))
                .thenReturn(createChildMetadata("QuoteLineItem", "Quote"));
            
            List<String> result = orderer.orderForRestore(
                Set.of("Account", "Opportunity", "Quote", "QuoteLineItem"));
            
            // Verify strict ordering: Account -> Opportunity -> Quote -> QuoteLineItem
            assertEquals(0, result.indexOf("Account"));
            assertEquals(1, result.indexOf("Opportunity"));
            assertEquals(2, result.indexOf("Quote"));
            assertEquals(3, result.indexOf("QuoteLineItem"));
        }
    }
}
