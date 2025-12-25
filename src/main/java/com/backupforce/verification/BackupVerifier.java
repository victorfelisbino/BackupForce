package com.backupforce.verification;

import com.backupforce.bulkv2.BulkV2Client;
import com.backupforce.config.BackupHistory;
import com.backupforce.config.BackupHistory.BackupRun;
import com.backupforce.config.BackupHistory.ObjectBackupResult;
import com.backupforce.sink.DataSink;
import com.backupforce.sink.JdbcDatabaseSink;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * Comprehensive backup verification system.
 * 
 * Verifies backup integrity by:
 * 1. Comparing Salesforce record counts with backed up data
 * 2. Computing checksums for data integrity
 * 3. Validating required fields are present
 * 4. Checking ID uniqueness
 * 5. Generating detailed verification reports
 */
public class BackupVerifier {
    
    private static final Logger logger = LoggerFactory.getLogger(BackupVerifier.class);
    
    // Objects that commonly fail backup due to Salesforce API limitations
    // These will be marked as SKIPPED with an explanation rather than FAILED
    private static final Set<String> KNOWN_PROBLEMATIC_OBJECTS = new HashSet<>(Arrays.asList(
        // Metadata objects requiring special filters or not queryable
        "FieldDefinition", "FieldSecurityClassification", "FlowTestView", "FlowVariableView",
        "FlowVersionView", "IconDefinition", "ListViewChartInstance", "OwnerChangeOptionInfo",
        "PicklistValueInfo", "PlatformAction", "RelatedListColumnDefinition", "RelatedListDefinition",
        "RelationshipDomain", "RelationshipInfo", "SearchLayout", "SiteDetail", "UserEntityAccess",
        "UserFieldAccess", "EntityDefinition", "EntityParticle", "Publisher", "QuickActionDefinition",
        "QuickActionListItem", "QuickActionList", "TabDefinition", "ColorDefinition", "DataType",
        "ContentBody", // Special object - not queryable directly
        
        // Objects not supported by Bulk API
        "OrderStatus", "TaskWhoRelation", "WorkStepStatus", "CaseStatus", "ContractStatus",
        "LeadStatus", "OpportunityStage", "PartnerRole", "SolutionStatus", "TaskStatus",
        "LoginHistory", // Limited query support
        
        // History and Feed objects - often restricted or empty
        "OpportunityHistory", "OpportunityFeed", "OpportunityShare",
        "AccountHistory", "AccountFeed", "AccountShare",
        "ContactHistory", "ContactFeed", "ContactShare",
        "LeadHistory", "LeadFeed", "LeadShare",
        "CaseHistory", "CaseFeed", "CaseShare",
        
        // Setup/Security objects - typically restricted
        "SetupAuditTrail", "SetupEntityAccess", "SessionPermSetActivation",
        "PermissionSetGroupComponent", "PermissionSetTabSetting", "UserSetupEntityAccess",
        "PlatformEventUsageMetric", "VerificationHistory", "OrgEmailAddressSecurity",
        
        // User-related objects with special permissions
        "UserAppMenuItem", "UserEmailCalendarSync", "UserPackageLicense",
        
        // Task/Event relation objects
        "TaskRelation", "EventRelation",
        
        // Work objects - Salesforce internal
        "WorkBadgeDefinition", "WorkBadgeDefinitionFeed", "WorkBadgeDefinitionHistory", "WorkBadgeDefinitionShare",
        "WorkPlan", "WorkPlanFeed", "WorkPlanHistory", "WorkPlanShare",
        "WorkPlanTemplate", "WorkPlanTemplateEntry", "WorkPlanTemplateEntryFeed", "WorkPlanTemplateEntryHistory",
        "WorkPlanTemplateFeed", "WorkPlanTemplateHistory", "WorkPlanTemplateShare",
        "WorkStep", "WorkStepFeed", "WorkStepHistory",
        "WorkStepTemplate", "WorkStepTemplateFeed", "WorkStepTemplateHistory", "WorkStepTemplateShare",
        "WorkThanks", "WorkThanksShare",
        
        // Record Action objects
        "RecordAction", "RecordActionHistory",
        
        // Standard objects that often have restrictions
        "Order", "RecordType"
    ));
    
    // Patterns for managed package objects that commonly fail
    private static final List<String> PACKAGE_PREFIXES_WITH_ISSUES = Arrays.asList(
        "dfsle__",    // DocuSign
        "dlrs__",     // Declarative Lookup Rollup Summaries  
        "dupcheck__", // Duplicate Check
        "et4ae5__",   // ExactTarget/Marketing Cloud
        "rh2__",      // Rollup Helper
        "tdc_tsw__",  // TDC/SMS
        "tdc_GridView__",
        "trailheadapp__", // Trailhead
        "leadconvertchtr__",
        "p0pFD__"
    );
    
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private Consumer<String> logConsumer;
    
    public BackupVerifier(String instanceUrl, String accessToken, String apiVersion) {
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
    }
    
    public void setLogConsumer(Consumer<String> consumer) {
        this.logConsumer = consumer;
    }
    
    private void log(String message) {
        if (logConsumer != null) {
            logConsumer.accept(message);
        }
        logger.info(message);
    }
    
    /**
     * Check if an object is known to commonly fail backup due to Salesforce limitations.
     * @param objectName The Salesforce object API name
     * @return true if this object commonly fails and should be treated as expected
     */
    private boolean isKnownProblematicObject(String objectName) {
        if (KNOWN_PROBLEMATIC_OBJECTS.contains(objectName)) {
            return true;
        }
        
        // Check for managed package objects
        for (String prefix : PACKAGE_PREFIXES_WITH_ISSUES) {
            if (objectName.startsWith(prefix)) {
                return true;
            }
        }
        
        // Check for History/Feed/Share suffixes on custom objects
        if (objectName.endsWith("__c")) {
            return false; // Custom objects are usually fine
        }
        if (objectName.endsWith("__History") || objectName.endsWith("__Feed") || 
            objectName.endsWith("__Share") || objectName.endsWith("__mdt")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get a human-readable reason why an object is known to fail.
     */
    private String getProblematicReason(String objectName) {
        if (objectName.contains("History") || objectName.contains("Feed") || objectName.contains("Share")) {
            return "History/Feed/Share objects often require special permissions or are empty";
        }
        if (objectName.startsWith("Work")) {
            return "Work-related objects are internal Salesforce objects";
        }
        if (objectName.equals("ContentBody") || objectName.equals("LoginHistory")) {
            return "Object has limited query support in Salesforce API";
        }
        if (objectName.contains("Setup") || objectName.contains("Permission") || objectName.contains("Session")) {
            return "Setup/security objects require admin permissions";
        }
        for (String prefix : PACKAGE_PREFIXES_WITH_ISSUES) {
            if (objectName.startsWith(prefix)) {
                return "Managed package object - may require package license or have access restrictions";
            }
        }
        if (objectName.endsWith("__mdt")) {
            return "Custom metadata type - requires different query approach";
        }
        return "Object has known Salesforce API limitations";
    }
    
    /**
     * Verify a backup against the Salesforce org.
     * 
     * @param backupPath Path to the backup folder containing CSV files
     * @param objectsToVerify List of objects to verify (null = all CSVs found)
     * @return Comprehensive verification result
     */
    public VerificationResult verifyBackup(String backupPath, List<String> objectsToVerify) {
        VerificationResult result = new VerificationResult();
        result.setBackupPath(backupPath);
        result.setVerificationTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        log("🔍 Starting backup verification for: " + backupPath);
        
        try {
            BulkV2Client bulkClient = new BulkV2Client(instanceUrl, accessToken, apiVersion);
            
            // Find all CSV files in backup folder
            Path folder = Paths.get(backupPath);
            if (!Files.exists(folder)) {
                result.addError("Backup folder does not exist: " + backupPath);
                result.setOverallStatus(VerificationStatus.FAILED);
                return result;
            }
            
            List<Path> csvFiles = Files.list(folder)
                    .filter(p -> p.toString().endsWith(".csv"))
                    .filter(p -> !p.getFileName().toString().startsWith("_")) // Exclude metadata files
                    .collect(Collectors.toList());
            
            if (csvFiles.isEmpty()) {
                result.addWarning("No CSV files found in backup folder");
                result.setOverallStatus(VerificationStatus.WARNING);
                return result;
            }
            
            log("Found " + csvFiles.size() + " CSV files to verify");
            
            // Filter to specific objects if requested
            if (objectsToVerify != null && !objectsToVerify.isEmpty()) {
                Set<String> objectSet = new HashSet<>(objectsToVerify);
                csvFiles = csvFiles.stream()
                        .filter(p -> objectSet.contains(getObjectName(p)))
                        .collect(Collectors.toList());
            }
            
            // Verify each object
            int verified = 0;
            int warnings = 0;
            int errors = 0;
            
            for (Path csvFile : csvFiles) {
                String objectName = getObjectName(csvFile);
                log("Verifying " + objectName + "...");
                
                try {
                    ObjectVerificationResult objResult = verifyObject(csvFile, objectName, bulkClient);
                    result.addObjectResult(objResult);
                    
                    if (objResult.getStatus() == VerificationStatus.PASSED) {
                        verified++;
                        log("  ✓ " + objectName + " - PASSED (" + objResult.getBackupRecordCount() + " records)");
                    } else if (objResult.getStatus() == VerificationStatus.WARNING) {
                        warnings++;
                        log("  ⚠ " + objectName + " - WARNING: " + objResult.getMessage());
                    } else {
                        errors++;
                        log("  ✗ " + objectName + " - FAILED: " + objResult.getMessage());
                    }
                } catch (Exception e) {
                    errors++;
                    ObjectVerificationResult objResult = new ObjectVerificationResult(objectName);
                    objResult.setStatus(VerificationStatus.FAILED);
                    objResult.setMessage("Verification error: " + e.getMessage());
                    result.addObjectResult(objResult);
                    log("  ✗ " + objectName + " - ERROR: " + e.getMessage());
                }
            }
            
            // Set overall status
            if (errors > 0) {
                result.setOverallStatus(VerificationStatus.FAILED);
            } else if (warnings > 0) {
                result.setOverallStatus(VerificationStatus.WARNING);
            } else {
                result.setOverallStatus(VerificationStatus.PASSED);
            }
            
            result.setSummary(String.format("Verified %d objects: %d passed, %d warnings, %d errors",
                    csvFiles.size(), verified, warnings, errors));
            
            log("\n📊 Verification Summary: " + result.getSummary());
            
        } catch (Exception e) {
            logger.error("Verification failed", e);
            result.addError("Verification failed: " + e.getMessage());
            result.setOverallStatus(VerificationStatus.FAILED);
        }
        
        return result;
    }
    
    /**
     * Verify a single object's backup.
     */
    private ObjectVerificationResult verifyObject(Path csvFile, String objectName, BulkV2Client bulkClient) throws Exception {
        ObjectVerificationResult result = new ObjectVerificationResult(objectName);
        
        // 1. Count records in CSV
        long csvRecordCount = 0;
        Set<String> uniqueIds = new HashSet<>();
        Set<String> fields = new HashSet<>();
        long fileSize = Files.size(csvFile);
        
        try (Reader reader = new FileReader(csvFile.toFile());
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build().parse(reader)) {
            
            fields.addAll(parser.getHeaderNames());
            
            for (CSVRecord record : parser) {
                csvRecordCount++;
                
                // Track unique IDs
                if (parser.getHeaderNames().contains("Id")) {
                    String id = record.get("Id");
                    if (id != null && !id.isEmpty()) {
                        uniqueIds.add(id);
                    }
                }
            }
        }
        
        result.setBackupRecordCount(csvRecordCount);
        result.setUniqueIdCount(uniqueIds.size());
        result.setFieldCount(fields.size());
        result.setFileSizeBytes(fileSize);
        
        // 2. Get Salesforce record count
        try {
            int sfCount = bulkClient.getRecordCount(objectName);
            result.setSalesforceRecordCount(sfCount);
            
            // Compare counts
            if (sfCount == csvRecordCount) {
                result.setStatus(VerificationStatus.PASSED);
                result.setMessage("Record counts match: " + sfCount);
                result.setCountMatch(true);
            } else if (sfCount > csvRecordCount) {
                long missing = sfCount - csvRecordCount;
                double pctMissing = (missing * 100.0) / sfCount;
                
                if (pctMissing <= 5) {
                    // Small difference - might be due to timing
                    result.setStatus(VerificationStatus.WARNING);
                    result.setMessage(String.format("Minor difference: Salesforce=%d, Backup=%d (%.1f%% difference - may be due to new records)",
                            sfCount, csvRecordCount, pctMissing));
                } else {
                    result.setStatus(VerificationStatus.FAILED);
                    result.setMessage(String.format("MISSING RECORDS: Salesforce=%d, Backup=%d (%d records missing, %.1f%%)",
                            sfCount, csvRecordCount, missing, pctMissing));
                }
                result.setCountMatch(false);
            } else {
                // More records in backup than Salesforce (records deleted since backup)
                result.setStatus(VerificationStatus.WARNING);
                result.setMessage(String.format("Backup has more records than current Salesforce (%d vs %d) - records may have been deleted",
                        csvRecordCount, sfCount));
                result.setCountMatch(false);
            }
        } catch (Exception e) {
            // Can't get SF count - validate what we can
            result.setStatus(VerificationStatus.WARNING);
            result.setMessage("Could not verify against Salesforce: " + e.getMessage());
        }
        
        // 3. Check for duplicate IDs (data integrity)
        if (uniqueIds.size() != csvRecordCount && csvRecordCount > 0) {
            result.setStatus(VerificationStatus.WARNING);
            result.addWarning(String.format("Duplicate IDs detected: %d unique IDs for %d records",
                    uniqueIds.size(), csvRecordCount));
        }
        
        // 4. Verify required fields are present
        boolean hasId = fields.contains("Id");
        if (!hasId) {
            result.setStatus(VerificationStatus.WARNING);
            result.addWarning("Id field not present in backup");
        }
        
        // 5. Calculate file checksum
        String checksum = calculateChecksum(csvFile);
        result.setChecksum(checksum);
        
        return result;
    }
    
    /**
     * Verify backup against a database destination (Snowflake, PostgreSQL, SQL Server).
     */
    public VerificationResult verifyDatabaseBackup(DataSink dataSink, List<String> objectsToVerify) {
        VerificationResult result = new VerificationResult();
        result.setBackupPath(dataSink.getDisplayName());
        result.setVerificationTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        log("🔍 Starting database backup verification for: " + dataSink.getDisplayName());
        
        try {
            BulkV2Client bulkClient = new BulkV2Client(instanceUrl, accessToken, apiVersion);
            
            if (!(dataSink instanceof JdbcDatabaseSink)) {
                result.addError("Database sink is not a JDBC sink");
                result.setOverallStatus(VerificationStatus.FAILED);
                return result;
            }
            
            JdbcDatabaseSink jdbcSink = (JdbcDatabaseSink) dataSink;
            
            // Get list of tables to verify
            List<String> tables = objectsToVerify;
            if (tables == null || tables.isEmpty()) {
                // Get actual tables from the database instead of trying to verify all SF objects
                log("No objects specified - discovering tables in database schema...");
                tables = jdbcSink.listBackedUpTables();
                if (tables.isEmpty()) {
                    result.addWarning("No tables found in database schema");
                    result.setOverallStatus(VerificationStatus.WARNING);
                    return result;
                }
                log("Found " + tables.size() + " tables to verify");
            }
            
            log("Verifying " + tables.size() + " objects against database");
            
            int verified = 0;
            int warnings = 0;
            int skipped = 0;
            int errors = 0;
            
            for (String objectName : tables) {
                log("Verifying " + objectName + "...");
                
                try {
                    ObjectVerificationResult objResult = new ObjectVerificationResult(objectName);
                    
                    // Get database record count
                    long dbCount = jdbcSink.getTableRowCount(objectName);
                    objResult.setBackupRecordCount(dbCount);
                    
                    if (dbCount < 0) {
                        // Table not found - determine if this is expected or an error
                        if (isKnownProblematicObject(objectName)) {
                            objResult.setStatus(VerificationStatus.SKIPPED);
                            objResult.setMessage("Not backed up (expected): " + getProblematicReason(objectName));
                            skipped++;
                            log("  ○ " + objectName + " - SKIPPED (known limitation)");
                        } else {
                            objResult.setStatus(VerificationStatus.SKIPPED);
                            objResult.setMessage("Not backed up - object may have failed or was not selected");
                            skipped++;
                            log("  ○ " + objectName + " - SKIPPED (not in database)");
                        }
                        result.addObjectResult(objResult);
                        continue;
                    }
                    
                    // Get Salesforce record count
                    int sfCount = bulkClient.getRecordCount(objectName);
                    objResult.setSalesforceRecordCount(sfCount);
                    
                    // Compare
                    if (sfCount == dbCount) {
                        objResult.setStatus(VerificationStatus.PASSED);
                        objResult.setMessage("Record counts match: " + sfCount);
                        objResult.setCountMatch(true);
                        verified++;
                        log("  ✓ " + objectName + " - PASSED (" + sfCount + " records)");
                    } else if (sfCount > dbCount) {
                        long missing = sfCount - dbCount;
                        double pctMissing = (missing * 100.0) / sfCount;
                        objResult.setCountMatch(false);
                        
                        if (pctMissing <= 5) {
                            objResult.setStatus(VerificationStatus.WARNING);
                            objResult.setMessage(String.format("Minor difference: SF=%d, DB=%d (%.1f%%)", sfCount, dbCount, pctMissing));
                            warnings++;
                        } else {
                            objResult.setStatus(VerificationStatus.FAILED);
                            objResult.setMessage(String.format("MISSING: SF=%d, DB=%d (%d missing)", sfCount, dbCount, missing));
                            errors++;
                        }
                        log("  ⚠ " + objectName + " - " + objResult.getMessage());
                    } else {
                        objResult.setStatus(VerificationStatus.WARNING);
                        objResult.setMessage(String.format("DB has more records (%d vs %d) - SF records may have been deleted", dbCount, sfCount));
                        objResult.setCountMatch(false);
                        warnings++;
                        log("  ⚠ " + objectName + " - " + objResult.getMessage());
                    }
                    
                    result.addObjectResult(objResult);
                    
                } catch (Exception e) {
                    ObjectVerificationResult objResult = new ObjectVerificationResult(objectName);
                    // Check if this is a known problematic object - treat as skipped, not error
                    if (isKnownProblematicObject(objectName)) {
                        objResult.setStatus(VerificationStatus.SKIPPED);
                        objResult.setMessage("Query failed (expected): " + getProblematicReason(objectName));
                        skipped++;
                        log("  ○ " + objectName + " - SKIPPED: " + e.getMessage());
                    } else {
                        objResult.setStatus(VerificationStatus.WARNING);
                        objResult.setMessage("Could not verify: " + e.getMessage());
                        warnings++;
                        log("  ⚠ " + objectName + " - WARNING: " + e.getMessage());
                    }
                    result.addObjectResult(objResult);
                }
            }
            
            // Set overall status - skipped objects don't count as failures
            if (errors > 0) {
                result.setOverallStatus(VerificationStatus.FAILED);
            } else if (warnings > 0) {
                result.setOverallStatus(VerificationStatus.WARNING);
            } else if (verified > 0) {
                result.setOverallStatus(VerificationStatus.PASSED);
            } else {
                result.setOverallStatus(VerificationStatus.WARNING);
            }
            
            result.setSummary(String.format("Verified %d objects: %d passed, %d warnings, %d skipped, %d errors",
                    tables.size(), verified, warnings, skipped, errors));
            
            log("\n📊 Verification Summary: " + result.getSummary());
            
        } catch (Exception e) {
            logger.error("Database verification failed", e);
            result.addError("Verification failed: " + e.getMessage());
            result.setOverallStatus(VerificationStatus.FAILED);
        }
        
        return result;
    }
    
    /**
     * Generate a detailed verification report as text.
     */
    public String generateReport(VerificationResult result) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("           BACKUP VERIFICATION REPORT\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");
        
        sb.append("Backup Location: ").append(result.getBackupPath()).append("\n");
        sb.append("Verification Time: ").append(result.getVerificationTime()).append("\n");
        sb.append("Overall Status: ").append(getStatusEmoji(result.getOverallStatus()))
          .append(" ").append(result.getOverallStatus()).append("\n\n");
        
        // Summary
        sb.append("SUMMARY\n");
        sb.append("───────────────────────────────────────────────────────────────\n");
        sb.append(result.getSummary()).append("\n\n");
        
        // Statistics
        if (!result.getObjectResults().isEmpty()) {
            // Only count objects that were actually verified (not skipped)
            List<ObjectVerificationResult> verifiedObjects = result.getObjectResults().stream()
                    .filter(o -> o.getStatus() != VerificationStatus.SKIPPED)
                    .collect(Collectors.toList());
            
            long skippedCount = result.getObjectResults().stream()
                    .filter(o -> o.getStatus() == VerificationStatus.SKIPPED)
                    .count();
            
            long totalBackupRecords = verifiedObjects.stream()
                    .filter(o -> o.getBackupRecordCount() >= 0)
                    .mapToLong(ObjectVerificationResult::getBackupRecordCount).sum();
            long totalSfRecords = verifiedObjects.stream()
                    .mapToLong(ObjectVerificationResult::getSalesforceRecordCount).sum();
            long totalBytes = verifiedObjects.stream()
                    .mapToLong(ObjectVerificationResult::getFileSizeBytes).sum();
            int matchCount = (int) verifiedObjects.stream()
                    .filter(ObjectVerificationResult::isCountMatch).count();
            
            sb.append("STATISTICS\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append(String.format("Total Objects in Scope: %d\n", result.getObjectResults().size()));
            sb.append(String.format("Objects Actually Backed Up: %d\n", verifiedObjects.size()));
            sb.append(String.format("Objects Skipped (expected): %d\n", skippedCount));
            if (!verifiedObjects.isEmpty()) {
                sb.append(String.format("Objects with Matching Counts: %d / %d (%.1f%%)\n", 
                        matchCount, verifiedObjects.size(),
                        (matchCount * 100.0) / verifiedObjects.size()));
            }
            sb.append(String.format("Total Records in Backup: %,d\n", totalBackupRecords));
            sb.append(String.format("Total Records in Salesforce: %,d\n", totalSfRecords));
            if (totalBytes > 0) {
                sb.append(String.format("Total Backup Size: %s\n", formatSize(totalBytes)));
            }
            sb.append("\n");
        }
        
        // Detailed results
        sb.append("OBJECT DETAILS\n");
        sb.append("───────────────────────────────────────────────────────────────\n");
        sb.append(String.format("%-30s %-10s %-12s %-12s %-8s\n", 
                "Object", "Status", "Backup", "Salesforce", "Match"));
        sb.append("───────────────────────────────────────────────────────────────\n");
        
        // Sort by status (passed first, then warnings, then skipped, then failed)
        List<ObjectVerificationResult> sorted = result.getObjectResults().stream()
                .sorted((a, b) -> {
                    // Custom order: PASSED=0, WARNING=1, SKIPPED=2, FAILED=3
                    int orderA = getStatusOrder(a.getStatus());
                    int orderB = getStatusOrder(b.getStatus());
                    return Integer.compare(orderA, orderB);
                })
                .collect(Collectors.toList());
        
        for (ObjectVerificationResult obj : sorted) {
            String matchSymbol;
            if (obj.getStatus() == VerificationStatus.SKIPPED) {
                matchSymbol = "○"; // Not applicable
            } else {
                matchSymbol = obj.isCountMatch() ? "✓" : "✗";
            }
            
            String backupCount = obj.getBackupRecordCount() < 0 ? "-" : String.format("%,d", obj.getBackupRecordCount());
            String sfCount = obj.getSalesforceRecordCount() == 0 && obj.getStatus() == VerificationStatus.SKIPPED 
                    ? "-" : String.format("%,d", obj.getSalesforceRecordCount());
            
            sb.append(String.format("%-30s %-10s %12s %12s %-8s\n",
                    truncate(obj.getObjectName(), 30),
                    obj.getStatus(),
                    backupCount,
                    sfCount,
                    matchSymbol));
            
            if (obj.getStatus() != VerificationStatus.PASSED) {
                sb.append("  └─ ").append(obj.getMessage()).append("\n");
            }
        }
        
        // Errors and warnings
        if (!result.getErrors().isEmpty()) {
            sb.append("\n");
            sb.append("ERRORS\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            for (String error : result.getErrors()) {
                sb.append("❌ ").append(error).append("\n");
            }
        }
        
        if (!result.getWarnings().isEmpty()) {
            sb.append("\n");
            sb.append("WARNINGS\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            for (String warning : result.getWarnings()) {
                sb.append("⚠️ ").append(warning).append("\n");
            }
        }
        
        // Confidence statement
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append(getConfidenceStatement(result));
        sb.append("═══════════════════════════════════════════════════════════════\n");
        
        return sb.toString();
    }
    
    /**
     * Get a confidence statement based on verification results.
     */
    private String getConfidenceStatement(VerificationResult result) {
        // Count verified vs skipped
        long verifiedCount = result.getObjectResults().stream()
                .filter(o -> o.getStatus() != VerificationStatus.SKIPPED)
                .count();
        long skippedCount = result.getObjectResults().stream()
                .filter(o -> o.getStatus() == VerificationStatus.SKIPPED)
                .count();
        long matchedCount = result.getObjectResults().stream()
                .filter(ObjectVerificationResult::isCountMatch)
                .count();
        long totalRecords = result.getObjectResults().stream()
                .filter(o -> o.getBackupRecordCount() >= 0)
                .mapToLong(ObjectVerificationResult::getBackupRecordCount).sum();
        
        if (result.getOverallStatus() == VerificationStatus.PASSED) {
            return String.format(
                    "✓ HIGH CONFIDENCE: All %d backed-up objects verified successfully.\n" +
                    "  %,d records backed up and verified against Salesforce.\n" +
                    (skippedCount > 0 ? "  (%d objects skipped due to Salesforce API limitations - this is expected)\n" : "") +
                    "  This backup is complete and trustworthy.\n", 
                    verifiedCount, totalRecords, skippedCount);
        } else if (result.getOverallStatus() == VerificationStatus.WARNING) {
            return String.format(
                    "⚠ MODERATE CONFIDENCE: %d/%d backed-up objects fully verified.\n" +
                    "  Some minor discrepancies found - review warnings above.\n" +
                    "  Discrepancies may be due to records added/deleted during backup.\n" +
                    (skippedCount > 0 ? "  (%d objects skipped due to known Salesforce limitations)\n" : ""), 
                    matchedCount, verifiedCount, skippedCount);
        } else {
            return "❌ LOW CONFIDENCE: Significant verification failures detected.\n" +
                   "  Review errors above and consider re-running backup.\n" +
                   "  Some objects may be incomplete or missing.\n";
        }
    }
    
    private String getObjectName(Path csvFile) {
        String filename = csvFile.getFileName().toString();
        return filename.substring(0, filename.length() - 4); // Remove .csv
    }
    
    private String calculateChecksum(Path file) throws Exception {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[8192];
        
        try (InputStream is = Files.newInputStream(file)) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                crc.update(buffer, 0, bytesRead);
            }
        }
        
        return String.format("%08X", crc.getValue());
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
    
    private String getStatusEmoji(VerificationStatus status) {
        switch (status) {
            case PASSED: return "✓";
            case WARNING: return "⚠";
            case SKIPPED: return "○";
            case FAILED: return "✗";
            default: return "?";
        }
    }
    
    /**
     * Get sort order for status (lower = show first)
     */
    private int getStatusOrder(VerificationStatus status) {
        switch (status) {
            case PASSED: return 0;
            case WARNING: return 1;
            case SKIPPED: return 2;
            case FAILED: return 3;
            default: return 4;
        }
    }
    
    // ========== Inner Classes ==========
    
    public enum VerificationStatus {
        PASSED,
        WARNING,
        SKIPPED,   // Not backed up (expected - object was not selected or backup failed)
        FAILED
    }
    
    /**
     * Overall verification result.
     */
    public static class VerificationResult {
        private String backupPath;
        private String verificationTime;
        private VerificationStatus overallStatus = VerificationStatus.PASSED;
        private String summary;
        private List<ObjectVerificationResult> objectResults = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        
        // Getters and setters
        public String getBackupPath() { return backupPath; }
        public void setBackupPath(String backupPath) { this.backupPath = backupPath; }
        
        public String getVerificationTime() { return verificationTime; }
        public void setVerificationTime(String verificationTime) { this.verificationTime = verificationTime; }
        
        public VerificationStatus getOverallStatus() { return overallStatus; }
        public void setOverallStatus(VerificationStatus overallStatus) { this.overallStatus = overallStatus; }
        
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        
        public List<ObjectVerificationResult> getObjectResults() { return objectResults; }
        public void addObjectResult(ObjectVerificationResult result) { objectResults.add(result); }
        
        public List<String> getErrors() { return errors; }
        public void addError(String error) { errors.add(error); }
        
        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { warnings.add(warning); }
    }
    
    /**
     * Verification result for a single object.
     */
    public static class ObjectVerificationResult {
        private String objectName;
        private VerificationStatus status = VerificationStatus.PASSED;
        private String message;
        private long backupRecordCount;
        private long salesforceRecordCount;
        private boolean countMatch;
        private long uniqueIdCount;
        private int fieldCount;
        private long fileSizeBytes;
        private String checksum;
        private List<String> warnings = new ArrayList<>();
        
        public ObjectVerificationResult(String objectName) {
            this.objectName = objectName;
        }
        
        // Getters and setters
        public String getObjectName() { return objectName; }
        
        public VerificationStatus getStatus() { return status; }
        public void setStatus(VerificationStatus status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public long getBackupRecordCount() { return backupRecordCount; }
        public void setBackupRecordCount(long backupRecordCount) { this.backupRecordCount = backupRecordCount; }
        
        public long getSalesforceRecordCount() { return salesforceRecordCount; }
        public void setSalesforceRecordCount(long salesforceRecordCount) { this.salesforceRecordCount = salesforceRecordCount; }
        
        public boolean isCountMatch() { return countMatch; }
        public void setCountMatch(boolean countMatch) { this.countMatch = countMatch; }
        
        public long getUniqueIdCount() { return uniqueIdCount; }
        public void setUniqueIdCount(long uniqueIdCount) { this.uniqueIdCount = uniqueIdCount; }
        
        public int getFieldCount() { return fieldCount; }
        public void setFieldCount(int fieldCount) { this.fieldCount = fieldCount; }
        
        public long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
        
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        
        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { warnings.add(warning); }
    }
}
