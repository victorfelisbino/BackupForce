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
            
            // Get list of tables in the database
            List<String> tables = objectsToVerify;
            if (tables == null || tables.isEmpty()) {
                result.addWarning("No objects specified for verification");
                result.setOverallStatus(VerificationStatus.WARNING);
                return result;
            }
            
            log("Verifying " + tables.size() + " objects against database");
            
            int verified = 0;
            int warnings = 0;
            int errors = 0;
            
            for (String objectName : tables) {
                log("Verifying " + objectName + "...");
                
                try {
                    ObjectVerificationResult objResult = new ObjectVerificationResult(objectName);
                    
                    // Get database record count
                    long dbCount = jdbcSink.getTableRowCount(objectName);
                    objResult.setBackupRecordCount(dbCount);
                    
                    if (dbCount < 0) {
                        objResult.setStatus(VerificationStatus.FAILED);
                        objResult.setMessage("Table not found in database");
                        errors++;
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
                    objResult.setStatus(VerificationStatus.FAILED);
                    objResult.setMessage("Error: " + e.getMessage());
                    result.addObjectResult(objResult);
                    errors++;
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
                    tables.size(), verified, warnings, errors));
            
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
            long totalBackupRecords = result.getObjectResults().stream()
                    .mapToLong(ObjectVerificationResult::getBackupRecordCount).sum();
            long totalSfRecords = result.getObjectResults().stream()
                    .mapToLong(ObjectVerificationResult::getSalesforceRecordCount).sum();
            long totalBytes = result.getObjectResults().stream()
                    .mapToLong(ObjectVerificationResult::getFileSizeBytes).sum();
            int matchCount = (int) result.getObjectResults().stream()
                    .filter(ObjectVerificationResult::isCountMatch).count();
            
            sb.append("STATISTICS\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append(String.format("Total Objects Verified: %d\n", result.getObjectResults().size()));
            sb.append(String.format("Objects with Matching Counts: %d / %d (%.1f%%)\n", 
                    matchCount, result.getObjectResults().size(),
                    (matchCount * 100.0) / result.getObjectResults().size()));
            sb.append(String.format("Total Records in Backup: %,d\n", totalBackupRecords));
            sb.append(String.format("Total Records in Salesforce: %,d\n", totalSfRecords));
            sb.append(String.format("Total Backup Size: %s\n", formatSize(totalBytes)));
            sb.append("\n");
        }
        
        // Detailed results
        sb.append("OBJECT DETAILS\n");
        sb.append("───────────────────────────────────────────────────────────────\n");
        sb.append(String.format("%-30s %-10s %-12s %-12s %-8s\n", 
                "Object", "Status", "Backup", "Salesforce", "Match"));
        sb.append("───────────────────────────────────────────────────────────────\n");
        
        // Sort by status (errors first, then warnings, then passed)
        List<ObjectVerificationResult> sorted = result.getObjectResults().stream()
                .sorted((a, b) -> a.getStatus().compareTo(b.getStatus()))
                .collect(Collectors.toList());
        
        for (ObjectVerificationResult obj : sorted) {
            sb.append(String.format("%-30s %-10s %,12d %,12d %-8s\n",
                    truncate(obj.getObjectName(), 30),
                    obj.getStatus(),
                    obj.getBackupRecordCount(),
                    obj.getSalesforceRecordCount(),
                    obj.isCountMatch() ? "✓" : "✗"));
            
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
        if (result.getOverallStatus() == VerificationStatus.PASSED) {
            int total = result.getObjectResults().size();
            long records = result.getObjectResults().stream()
                    .mapToLong(ObjectVerificationResult::getBackupRecordCount).sum();
            return String.format(
                    "✓ HIGH CONFIDENCE: All %d objects verified successfully.\n" +
                    "  %,d records backed up and verified against Salesforce.\n" +
                    "  This backup is complete and trustworthy.\n", total, records);
        } else if (result.getOverallStatus() == VerificationStatus.WARNING) {
            int matched = (int) result.getObjectResults().stream()
                    .filter(ObjectVerificationResult::isCountMatch).count();
            int total = result.getObjectResults().size();
            return String.format(
                    "⚠ MODERATE CONFIDENCE: %d/%d objects fully verified.\n" +
                    "  Some minor discrepancies found - review warnings above.\n" +
                    "  Discrepancies may be due to records added/deleted during backup.\n", 
                    matched, total);
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
            case FAILED: return "✗";
            default: return "?";
        }
    }
    
    // ========== Inner Classes ==========
    
    public enum VerificationStatus {
        PASSED,
        WARNING,
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
