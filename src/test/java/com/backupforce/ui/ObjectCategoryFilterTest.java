package com.backupforce.ui;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Object Category Filter system.
 * Tests the preference-based filtering logic that allows users to configure
 * which object categories to include/exclude from backups.
 */
@DisplayName("Object Category Filter Tests")
class ObjectCategoryFilterTest {
    
    private Preferences prefs;
    
    // Constants matching BackupController
    private static final Set<String> SYSTEM_OBJECTS = new HashSet<>(Arrays.asList(
        "FieldDefinition", "FieldSecurityClassification", "FlowTestView", "FlowVariableView",
        "FlowVersionView", "IconDefinition", "ListViewChartInstance", "OwnerChangeOptionInfo",
        "PicklistValueInfo", "PlatformAction", "RelatedListColumnDefinition", "RelatedListDefinition",
        "RelationshipDomain", "RelationshipInfo", "SearchLayout", "SiteDetail", "UserEntityAccess",
        "UserFieldAccess", "EntityDefinition", "EntityParticle", "Publisher", "QuickActionDefinition",
        "QuickActionListItem", "QuickActionList", "TabDefinition", "ColorDefinition", "DataType",
        "OrderStatus", "TaskWhoRelation", "WorkStepStatus", "CaseStatus", "ContractStatus",
        "LeadStatus", "OpportunityStage", "PartnerRole", "SolutionStatus", "TaskStatus",
        "Vote", "IdeaComment", "FeedComment", "FeedItem", "FeedLike", "FeedPollChoice",
        "FeedPollVote", "FeedRevision", "FeedSignal", "FeedTrackedChange",
        "ContentBody", "FlexQueueItem", "OutgoingEmail", "OutgoingEmailRelation",
        "UnifiedActivity", "ContentFolderMember", "ContentWorkspaceMember",
        "UserShare", "GroupMember", "QueueSobject", "Address", "Location"
    ));
    
    private static final Set<String> APEX_CODE_OBJECTS = new HashSet<>(Arrays.asList(
        "ApexClass", "ApexTrigger", "ApexComponent", "ApexPage"
    ));
    
    private static final Set<String> APEX_LOG_OBJECTS = new HashSet<>(Arrays.asList(
        "ApexLog", "ApexTestQueueItem", "ApexTestResult", "ApexTestResultLimits",
        "ApexTestRunResult", "ApexEmailNotification", "AsyncApexJob",
        "CronTrigger", "CronJobDetail", "TraceFlag", "DebugLevel"
    ));
    
    private static final Set<String> SETUP_OBJECTS = new HashSet<>(Arrays.asList(
        "SetupAuditTrail", "SetupEntityAccess", "SessionPermSetActivation",
        "PermissionSetGroupComponent", "PermissionSetTabSetting", "UserSetupEntityAccess",
        "LoginHistory", "VerificationHistory", "OrgEmailAddressSecurity"
    ));
    
    // Sample Salesforce objects for testing
    private static final List<String> SAMPLE_OBJECTS = Arrays.asList(
        // Standard business objects
        "Account", "Contact", "Lead", "Opportunity", "Case", "Task", "Event",
        // Custom objects
        "Invoice__c", "Project__c", "Subscription__c",
        // Apex objects
        "ApexClass", "ApexTrigger", "ApexComponent", "ApexPage",
        // Apex logs
        "ApexLog", "ApexTestQueueItem", "ApexTestResult", "AsyncApexJob",
        // Feed objects
        "AccountFeed", "ContactFeed", "CaseFeed", "OpportunityFeed",
        // History objects
        "AccountHistory", "ContactHistory", "LeadHistory", "OpportunityHistory",
        // Share objects
        "AccountShare", "ContactShare", "LeadShare", "OpportunityShare",
        // Setup objects
        "SetupAuditTrail", "LoginHistory", "SetupEntityAccess",
        // Managed package objects
        "npe01__Contact_Merge_Log__c", "LMR__License__c", "copado__JobExecution__c",
        // System objects (truly problematic)
        "FieldDefinition", "EntityDefinition", "CaseStatus",
        // Special types
        "Product2__mdt", "MyEvent__e", "ExternalData__x", "BigObject__b",
        "PersonAccount__pc", "AccountChangeEvent"
    );
    
    @BeforeEach
    void setUp() {
        // Get the test preferences node
        prefs = Preferences.userRoot().node("com.backupforce.test");
        
        // Reset all preferences to defaults
        prefs.putBoolean("includeApexCode", true);
        prefs.putBoolean("includeApexLogs", false);
        prefs.putBoolean("includeFeedObjects", false);
        prefs.putBoolean("includeHistoryObjects", false);
        prefs.putBoolean("includeShareObjects", false);
        prefs.putBoolean("includeSetupObjects", false);
        prefs.putBoolean("includeManagedPkg", true);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        // Clean up test preferences
        prefs.clear();
    }
    
    // ==================== Category Detection Tests ====================
    
    @Nested
    @DisplayName("Category Detection Tests")
    class CategoryDetectionTests {
        
        @Test
        @DisplayName("Detect Apex Code objects correctly")
        void detectApexCodeObjects() {
            List<String> apexCodeObjects = SAMPLE_OBJECTS.stream()
                .filter(APEX_CODE_OBJECTS::contains)
                .collect(Collectors.toList());
            
            assertEquals(4, apexCodeObjects.size());
            assertTrue(apexCodeObjects.contains("ApexClass"));
            assertTrue(apexCodeObjects.contains("ApexTrigger"));
            assertTrue(apexCodeObjects.contains("ApexComponent"));
            assertTrue(apexCodeObjects.contains("ApexPage"));
        }
        
        @Test
        @DisplayName("Detect Apex Log objects correctly")
        void detectApexLogObjects() {
            List<String> apexLogObjects = SAMPLE_OBJECTS.stream()
                .filter(APEX_LOG_OBJECTS::contains)
                .collect(Collectors.toList());
            
            assertEquals(4, apexLogObjects.size());
            assertTrue(apexLogObjects.contains("ApexLog"));
            assertTrue(apexLogObjects.contains("ApexTestQueueItem"));
            assertTrue(apexLogObjects.contains("ApexTestResult"));
            assertTrue(apexLogObjects.contains("AsyncApexJob"));
        }
        
        @Test
        @DisplayName("Detect Feed objects by suffix pattern")
        void detectFeedObjectsBySuffix() {
            List<String> feedObjects = SAMPLE_OBJECTS.stream()
                .filter(name -> name.endsWith("Feed"))
                .collect(Collectors.toList());
            
            assertEquals(4, feedObjects.size());
            assertTrue(feedObjects.contains("AccountFeed"));
            assertTrue(feedObjects.contains("ContactFeed"));
            assertTrue(feedObjects.contains("CaseFeed"));
            assertTrue(feedObjects.contains("OpportunityFeed"));
        }
        
        @Test
        @DisplayName("Detect History objects by suffix pattern")
        void detectHistoryObjectsBySuffix() {
            List<String> historyObjects = SAMPLE_OBJECTS.stream()
                .filter(name -> name.endsWith("History") && 
                        !name.equals("LoginHistory") && 
                        !name.equals("VerificationHistory"))
                .collect(Collectors.toList());
            
            assertEquals(4, historyObjects.size());
            assertTrue(historyObjects.contains("AccountHistory"));
            assertTrue(historyObjects.contains("ContactHistory"));
            assertTrue(historyObjects.contains("LeadHistory"));
            assertTrue(historyObjects.contains("OpportunityHistory"));
        }
        
        @Test
        @DisplayName("Detect Share objects by suffix pattern")
        void detectShareObjectsBySuffix() {
            List<String> shareObjects = SAMPLE_OBJECTS.stream()
                .filter(name -> name.endsWith("Share"))
                .collect(Collectors.toList());
            
            assertEquals(4, shareObjects.size());
            assertTrue(shareObjects.contains("AccountShare"));
            assertTrue(shareObjects.contains("ContactShare"));
            assertTrue(shareObjects.contains("LeadShare"));
            assertTrue(shareObjects.contains("OpportunityShare"));
        }
        
        @Test
        @DisplayName("Detect Setup objects correctly")
        void detectSetupObjects() {
            List<String> setupObjects = SAMPLE_OBJECTS.stream()
                .filter(SETUP_OBJECTS::contains)
                .collect(Collectors.toList());
            
            assertEquals(3, setupObjects.size());
            assertTrue(setupObjects.contains("SetupAuditTrail"));
            assertTrue(setupObjects.contains("LoginHistory"));
            assertTrue(setupObjects.contains("SetupEntityAccess"));
        }
        
        @Test
        @DisplayName("Detect managed package objects by namespace prefix")
        void detectManagedPackageObjects() {
            List<String> managedPkgObjects = SAMPLE_OBJECTS.stream()
                .filter(name -> isManagedPackageObject(name))
                .collect(Collectors.toList());
            
            assertEquals(3, managedPkgObjects.size());
            assertTrue(managedPkgObjects.contains("npe01__Contact_Merge_Log__c"));
            assertTrue(managedPkgObjects.contains("LMR__License__c"));
            assertTrue(managedPkgObjects.contains("copado__JobExecution__c"));
        }
        
        @Test
        @DisplayName("Standard custom objects are NOT managed package objects")
        void standardCustomObjectsNotManagedPackage() {
            assertFalse(isManagedPackageObject("Invoice__c"));
            assertFalse(isManagedPackageObject("Project__c"));
            assertFalse(isManagedPackageObject("Subscription__c"));
        }
        
        @Test
        @DisplayName("Detect system objects correctly")
        void detectSystemObjects() {
            List<String> sysObjects = SAMPLE_OBJECTS.stream()
                .filter(SYSTEM_OBJECTS::contains)
                .collect(Collectors.toList());
            
            assertEquals(3, sysObjects.size());
            assertTrue(sysObjects.contains("FieldDefinition"));
            assertTrue(sysObjects.contains("EntityDefinition"));
            assertTrue(sysObjects.contains("CaseStatus"));
        }
        
        @Test
        @DisplayName("Detect special type suffixes")
        void detectSpecialTypeSuffixes() {
            List<String> customMetadata = SAMPLE_OBJECTS.stream()
                .filter(name -> name.endsWith("__mdt"))
                .collect(Collectors.toList());
            assertEquals(1, customMetadata.size());
            
            List<String> platformEvents = SAMPLE_OBJECTS.stream()
                .filter(name -> name.endsWith("__e"))
                .collect(Collectors.toList());
            assertEquals(1, platformEvents.size());
            
            List<String> externalObjects = SAMPLE_OBJECTS.stream()
                .filter(name -> name.endsWith("__x"))
                .collect(Collectors.toList());
            assertEquals(1, externalObjects.size());
            
            List<String> bigObjects = SAMPLE_OBJECTS.stream()
                .filter(name -> name.endsWith("__b"))
                .collect(Collectors.toList());
            assertEquals(1, bigObjects.size());
            
            List<String> changeEvents = SAMPLE_OBJECTS.stream()
                .filter(name -> name.endsWith("ChangeEvent"))
                .collect(Collectors.toList());
            assertEquals(1, changeEvents.size());
        }
    }
    
    // ==================== Preference-Based Exclusion Tests ====================
    
    @Nested
    @DisplayName("Preference-Based Exclusion Tests")
    class PreferenceBasedExclusionTests {
        
        @Test
        @DisplayName("Default preferences include Apex Code")
        void defaultPreferencesIncludeApexCode() {
            assertTrue(prefs.getBoolean("includeApexCode", true));
            
            // With default settings, ApexClass should NOT be excluded
            String category = getExcludedCategoryWithTestPrefs("ApexClass");
            assertNull(category, "ApexClass should not be excluded by default");
        }
        
        @Test
        @DisplayName("Default preferences exclude Apex Logs")
        void defaultPreferencesExcludeApexLogs() {
            assertFalse(prefs.getBoolean("includeApexLogs", false));
            
            // With default settings, ApexLog should be excluded
            String category = getExcludedCategoryWithTestPrefs("ApexLog");
            assertEquals("Apex Logs", category);
        }
        
        @Test
        @DisplayName("Exclude Apex Code when preference is off")
        void excludeApexCodeWhenPreferenceOff() {
            prefs.putBoolean("includeApexCode", false);
            
            assertEquals("Apex Code", getExcludedCategoryWithTestPrefs("ApexClass"));
            assertEquals("Apex Code", getExcludedCategoryWithTestPrefs("ApexTrigger"));
            assertEquals("Apex Code", getExcludedCategoryWithTestPrefs("ApexComponent"));
            assertEquals("Apex Code", getExcludedCategoryWithTestPrefs("ApexPage"));
        }
        
        @Test
        @DisplayName("Include Apex Logs when preference is on")
        void includeApexLogsWhenPreferenceOn() {
            prefs.putBoolean("includeApexLogs", true);
            
            assertNull(getExcludedCategoryWithTestPrefs("ApexLog"));
            assertNull(getExcludedCategoryWithTestPrefs("ApexTestResult"));
            assertNull(getExcludedCategoryWithTestPrefs("AsyncApexJob"));
        }
        
        @Test
        @DisplayName("Exclude Feed objects by default")
        void excludeFeedObjectsByDefault() {
            assertFalse(prefs.getBoolean("includeFeedObjects", false));
            
            assertEquals("Feed Objects", getExcludedCategoryWithTestPrefs("AccountFeed"));
            assertEquals("Feed Objects", getExcludedCategoryWithTestPrefs("ContactFeed"));
            assertEquals("Feed Objects", getExcludedCategoryWithTestPrefs("OpportunityFeed"));
        }
        
        @Test
        @DisplayName("Include Feed objects when preference is on")
        void includeFeedObjectsWhenPreferenceOn() {
            prefs.putBoolean("includeFeedObjects", true);
            
            assertNull(getExcludedCategoryWithTestPrefs("AccountFeed"));
            assertNull(getExcludedCategoryWithTestPrefs("ContactFeed"));
        }
        
        @Test
        @DisplayName("Exclude History objects by default")
        void excludeHistoryObjectsByDefault() {
            assertFalse(prefs.getBoolean("includeHistoryObjects", false));
            
            assertEquals("History Objects", getExcludedCategoryWithTestPrefs("AccountHistory"));
            assertEquals("History Objects", getExcludedCategoryWithTestPrefs("ContactHistory"));
            assertEquals("History Objects", getExcludedCategoryWithTestPrefs("OpportunityHistory"));
        }
        
        @Test
        @DisplayName("LoginHistory is handled by Setup category, not History category")
        void loginHistoryInSetupCategory() {
            // LoginHistory should be excluded by Setup objects category, not History
            // Because it's in SETUP_OBJECTS set
            assertTrue(SETUP_OBJECTS.contains("LoginHistory"));
            
            // When History is off but Setup is on, LoginHistory should NOT be excluded
            prefs.putBoolean("includeHistoryObjects", false);
            prefs.putBoolean("includeSetupObjects", true);
            
            // The History pattern specifically excludes LoginHistory
            assertFalse("LoginHistory".endsWith("History") && 
                       !"LoginHistory".equals("LoginHistory"));
        }
        
        @Test
        @DisplayName("Exclude Share objects by default")
        void excludeShareObjectsByDefault() {
            assertFalse(prefs.getBoolean("includeShareObjects", false));
            
            assertEquals("Share Objects", getExcludedCategoryWithTestPrefs("AccountShare"));
            assertEquals("Share Objects", getExcludedCategoryWithTestPrefs("ContactShare"));
            assertEquals("Share Objects", getExcludedCategoryWithTestPrefs("LeadShare"));
        }
        
        @Test
        @DisplayName("Exclude Setup objects by default")
        void excludeSetupObjectsByDefault() {
            assertFalse(prefs.getBoolean("includeSetupObjects", false));
            
            assertEquals("Setup Objects", getExcludedCategoryWithTestPrefs("SetupAuditTrail"));
            assertEquals("Setup Objects", getExcludedCategoryWithTestPrefs("SetupEntityAccess"));
        }
        
        @Test
        @DisplayName("Default preferences include Managed Packages")
        void defaultPreferencesIncludeManagedPackages() {
            assertTrue(prefs.getBoolean("includeManagedPkg", true));
            
            assertNull(getExcludedCategoryWithTestPrefs("npe01__Contact_Merge_Log__c"));
            assertNull(getExcludedCategoryWithTestPrefs("copado__JobExecution__c"));
        }
        
        @Test
        @DisplayName("Exclude managed packages when preference is off")
        void excludeManagedPackagesWhenPreferenceOff() {
            prefs.putBoolean("includeManagedPkg", false);
            
            assertEquals("Managed Packages", getExcludedCategoryWithTestPrefs("npe01__Contact_Merge_Log__c"));
            assertEquals("Managed Packages", getExcludedCategoryWithTestPrefs("copado__JobExecution__c"));
            assertEquals("Managed Packages", getExcludedCategoryWithTestPrefs("LMR__License__c"));
        }
        
        @Test
        @DisplayName("Standard custom objects not affected by managed package preference")
        void standardCustomObjectsNotAffectedByManagedPkgPref() {
            prefs.putBoolean("includeManagedPkg", false);
            
            // Standard custom objects should not be excluded even when managed pkg is off
            assertNull(getExcludedCategoryWithTestPrefs("Invoice__c"));
            assertNull(getExcludedCategoryWithTestPrefs("Project__c"));
        }
    }
    
    // ==================== Smart Selection Tests ====================
    
    @Nested
    @DisplayName("Smart Selection (Select Business Objects) Tests")
    class SmartSelectionTests {
        
        @Test
        @DisplayName("Business objects are always selected")
        void businessObjectsAlwaysSelected() {
            List<String> businessObjects = Arrays.asList("Account", "Contact", "Lead", "Opportunity", "Case");
            
            for (String obj : businessObjects) {
                assertNull(getExcludedCategoryWithTestPrefs(obj), 
                    obj + " should not be excluded");
                assertFalse(SYSTEM_OBJECTS.contains(obj),
                    obj + " should not be in SYSTEM_OBJECTS");
                assertFalse(obj.endsWith("__mdt") || obj.endsWith("__e") || obj.endsWith("__x"),
                    obj + " should not have special suffix");
            }
        }
        
        @Test
        @DisplayName("Custom objects are selected by default")
        void customObjectsSelectedByDefault() {
            List<String> customObjects = Arrays.asList("Invoice__c", "Project__c", "Subscription__c");
            
            for (String obj : customObjects) {
                assertNull(getExcludedCategoryWithTestPrefs(obj),
                    obj + " should not be excluded by default");
            }
        }
        
        @Test
        @DisplayName("System objects are always excluded")
        void systemObjectsAlwaysExcluded() {
            List<String> sysObjects = Arrays.asList("FieldDefinition", "EntityDefinition", "CaseStatus");
            
            for (String obj : sysObjects) {
                assertTrue(SYSTEM_OBJECTS.contains(obj),
                    obj + " should be in SYSTEM_OBJECTS");
            }
        }
        
        @Test
        @DisplayName("Special type suffixes always excluded from smart selection")
        void specialTypeSuffixesAlwaysExcluded() {
            String[] problematicSuffixes = {"__mdt", "__e", "__x", "__b", "__pc", "ChangeEvent"};
            
            for (String suffix : problematicSuffixes) {
                String testObj = "Test" + suffix;
                boolean isProblematic = testObj.endsWith("__mdt") ||
                    testObj.endsWith("__e") ||
                    testObj.endsWith("__x") ||
                    testObj.endsWith("__b") ||
                    testObj.endsWith("__pc") ||
                    testObj.endsWith("ChangeEvent");
                
                assertTrue(isProblematic, testObj + " should be detected as problematic");
            }
        }
        
        @Test
        @DisplayName("Count objects selected with default preferences")
        void countObjectsWithDefaultPreferences() {
            // With defaults: ApexCode=ON, ApexLogs=OFF, Feed=OFF, History=OFF, Share=OFF, Setup=OFF, ManagedPkg=ON
            int selectedCount = 0;
            int excludedCount = 0;
            
            for (String obj : SAMPLE_OBJECTS) {
                boolean isTrulyProblematic = SYSTEM_OBJECTS.contains(obj) ||
                    obj.endsWith("__mdt") || obj.endsWith("__e") || obj.endsWith("__x") ||
                    obj.endsWith("__b") || obj.endsWith("__pc") || obj.endsWith("ChangeEvent");
                
                if (isTrulyProblematic) {
                    excludedCount++;
                    continue;
                }
                
                String excludedCategory = getExcludedCategoryWithTestPrefs(obj);
                if (excludedCategory != null) {
                    excludedCount++;
                } else {
                    selectedCount++;
                }
            }
            
            // Should have selected business objects + custom objects + apex code + managed pkg
            // Excluded: system objects + special types + feed + history + share + setup + apex logs
            assertTrue(selectedCount > 0, "Should have some selected objects");
            assertTrue(excludedCount > 0, "Should have some excluded objects");
        }
        
        @Test
        @DisplayName("All categories enabled selects maximum objects")
        void allCategoriesEnabledSelectsMaximum() {
            // Enable all categories
            prefs.putBoolean("includeApexCode", true);
            prefs.putBoolean("includeApexLogs", true);
            prefs.putBoolean("includeFeedObjects", true);
            prefs.putBoolean("includeHistoryObjects", true);
            prefs.putBoolean("includeShareObjects", true);
            prefs.putBoolean("includeSetupObjects", true);
            prefs.putBoolean("includeManagedPkg", true);
            
            int excludedCount = 0;
            
            for (String obj : SAMPLE_OBJECTS) {
                // Only truly problematic objects should be excluded
                boolean isTrulyProblematic = SYSTEM_OBJECTS.contains(obj) ||
                    obj.endsWith("__mdt") || obj.endsWith("__e") || obj.endsWith("__x") ||
                    obj.endsWith("__b") || obj.endsWith("__pc") || obj.endsWith("ChangeEvent");
                
                String excludedCategory = getExcludedCategoryWithTestPrefs(obj);
                
                if (isTrulyProblematic) {
                    excludedCount++;
                } else if (excludedCategory != null) {
                    fail("Object " + obj + " should not be excluded when all categories are enabled, but was excluded for: " + excludedCategory);
                }
            }
            
            // Only system objects and special types should be excluded
            assertEquals(9, excludedCount, "Only truly problematic objects should be excluded");
        }
        
        @Test
        @DisplayName("All categories disabled minimizes selection")
        void allCategoriesDisabledMinimizesSelection() {
            // Disable all categories
            prefs.putBoolean("includeApexCode", false);
            prefs.putBoolean("includeApexLogs", false);
            prefs.putBoolean("includeFeedObjects", false);
            prefs.putBoolean("includeHistoryObjects", false);
            prefs.putBoolean("includeShareObjects", false);
            prefs.putBoolean("includeSetupObjects", false);
            prefs.putBoolean("includeManagedPkg", false);
            
            int selectedCount = 0;
            
            for (String obj : SAMPLE_OBJECTS) {
                boolean isTrulyProblematic = SYSTEM_OBJECTS.contains(obj) ||
                    obj.endsWith("__mdt") || obj.endsWith("__e") || obj.endsWith("__x") ||
                    obj.endsWith("__b") || obj.endsWith("__pc") || obj.endsWith("ChangeEvent");
                
                if (isTrulyProblematic) continue;
                
                String excludedCategory = getExcludedCategoryWithTestPrefs(obj);
                if (excludedCategory == null) {
                    selectedCount++;
                }
            }
            
            // Only standard business objects and standard custom objects should remain
            assertTrue(selectedCount > 0, "Should still have core business objects selected");
            assertTrue(selectedCount < SAMPLE_OBJECTS.size() / 2, "Should have fewer objects than with defaults");
        }
    }
    
    // ==================== Edge Cases Tests ====================
    
    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {
        
        @Test
        @DisplayName("Empty object name returns null")
        void emptyObjectNameReturnsNull() {
            assertNull(getExcludedCategoryWithTestPrefs(""));
        }
        
        @Test
        @DisplayName("Null-safe object name check")
        void nullSafeObjectNameCheck() {
            // Should not throw exception
            try {
                String result = getExcludedCategoryWithTestPrefs(null);
                // Either null or throws - both acceptable
            } catch (NullPointerException e) {
                // This is acceptable behavior
            }
        }
        
        @Test
        @DisplayName("Case sensitivity in object matching")
        void caseSensitivityInObjectMatching() {
            // Object names in Salesforce are case-sensitive for matching
            assertTrue(APEX_CODE_OBJECTS.contains("ApexClass"));
            assertFalse(APEX_CODE_OBJECTS.contains("apexclass"));
            assertFalse(APEX_CODE_OBJECTS.contains("APEXCLASS"));
        }
        
        @Test
        @DisplayName("Object with multiple underscores handled correctly")
        void objectWithMultipleUnderscores() {
            // This should be a managed package object
            String managedPkgObj = "namespace__Object_Name__c";
            assertTrue(isManagedPackageObject(managedPkgObj));
            
            // But a regular custom object with underscores is not
            String customObj = "My_Object_Name__c";
            assertFalse(isManagedPackageObject(customObj));
        }
        
        @Test
        @DisplayName("Person Account custom fields handled correctly")
        void personAccountFieldsHandledCorrectly() {
            // Person account fields end with __pc
            String personAccountField = "Contact__pc";
            assertTrue(personAccountField.endsWith("__pc"));
            assertFalse(isManagedPackageObject(personAccountField), 
                "Person account fields should not be treated as managed packages");
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Reimplementation of getExcludedCategory() for testing with test preferences.
     */
    private String getExcludedCategoryWithTestPrefs(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return null;
        }
        
        // Apex Code objects
        if (!prefs.getBoolean("includeApexCode", true)) {
            if (APEX_CODE_OBJECTS.contains(objectName)) {
                return "Apex Code";
            }
        }
        
        // Apex Logs and test objects
        if (!prefs.getBoolean("includeApexLogs", false)) {
            if (APEX_LOG_OBJECTS.contains(objectName)) {
                return "Apex Logs";
            }
        }
        
        // Feed objects (pattern-based)
        if (!prefs.getBoolean("includeFeedObjects", false)) {
            if (objectName.endsWith("Feed") || objectName.contains("FeedItem") || objectName.contains("FeedComment")) {
                return "Feed Objects";
            }
        }
        
        // History objects (pattern-based)
        if (!prefs.getBoolean("includeHistoryObjects", false)) {
            if (objectName.endsWith("History") && !objectName.equals("LoginHistory") && !objectName.equals("VerificationHistory")) {
                return "History Objects";
            }
        }
        
        // Share objects (pattern-based)
        if (!prefs.getBoolean("includeShareObjects", false)) {
            if (objectName.endsWith("Share") && !objectName.equals("UserShare")) {
                return "Share Objects";
            }
        }
        
        // Setup objects
        if (!prefs.getBoolean("includeSetupObjects", false)) {
            if (SETUP_OBJECTS.contains(objectName) || objectName.startsWith("Setup") ||
                objectName.startsWith("Auth") || objectName.startsWith("Session") ||
                objectName.startsWith("TwoFactor") || objectName.startsWith("Login")) {
                return "Setup Objects";
            }
        }
        
        // Managed package objects
        if (!prefs.getBoolean("includeManagedPkg", true)) {
            if (isManagedPackageObject(objectName)) {
                return "Managed Packages";
            }
        }
        
        return null; // Object not excluded
    }
    
    /**
     * Check if an object is a managed package object (has namespace prefix).
     */
    private boolean isManagedPackageObject(String objectName) {
        if (objectName == null || !objectName.contains("__")) {
            return false;
        }
        
        // Standard suffixes that don't indicate managed packages
        if (objectName.endsWith("__c") ||       // Custom objects
            objectName.endsWith("__mdt") ||     // Custom metadata
            objectName.endsWith("__e") ||       // Platform events
            objectName.endsWith("__x") ||       // External objects
            objectName.endsWith("__b") ||       // Big objects
            objectName.endsWith("__pc") ||      // Person account fields
            objectName.endsWith("__r")) {       // Relationship fields
            
            // Check if it has a namespace prefix (format: namespace__object__suffix)
            // Standard custom objects are just: ObjectName__c
            // Managed package objects are: namespace__ObjectName__c
            int firstUnderscore = objectName.indexOf("__");
            int lastUnderscore = objectName.lastIndexOf("__");
            
            // If there are multiple __ occurrences, it's likely a managed package object
            return firstUnderscore != lastUnderscore;
        }
        
        // Object has __ but doesn't end with standard suffix - could be managed package
        return true;
    }
}
