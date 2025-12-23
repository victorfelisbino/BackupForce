# BackupForce

[![Download](https://img.shields.io/github/v/release/victorfelisbino/BackupForce?label=Download&style=for-the-badge)](https://github.com/victorfelisbino/BackupForce/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Windows%20|%20macOS%20|%20Linux-blue?style=for-the-badge)](https://github.com/victorfelisbino/BackupForce/releases)
[![Java](https://img.shields.io/badge/Java-21+-orange?style=for-the-badge)](https://openjdk.org/)
[![Salesforce](https://img.shields.io/badge/Salesforce-Bulk%20API%20v2-00A1E0?style=for-the-badge)](https://developer.salesforce.com/)

A powerful desktop application for backing up Salesforce data to CSV files or databases. Features a beautiful **GitHub-inspired dark theme** with a calming nature background for stress-free data operations.

![BackupForce Screenshot](docs/screenshot.png)

---

## 🚀 Quick Download

**[⬇️ Download BackupForce v3.0.0](https://github.com/victorfelisbino/BackupForce/releases/latest)**

### Windows
- `BackupForce-3.0.0-portable.zip` - Portable (Recommended)

### macOS
- `BackupForce-3.0.0-macos.zip` - Portable

### Linux
- `BackupForce-3.0.0-linux.tar.gz` - Universal

**No Java installation required** - runtime is bundled!

---

## ✨ Features

### Data Export
- **Bulk API v2** - Fast, efficient data extraction for millions of records
- **Field Selection** - Choose exactly which fields to export for each object
- **Blob Downloads** - Automatically downloads Attachment.Body, ContentVersion.VersionData, Document.Body
- **Record Limits** - Test exports with a limited number of records
- **Multi-threaded** - Parallel processing for faster backups
- **Relationship Preservation** - Maintains all lookup/master-detail relationships for restore

### Database Support
| Database | Blob Storage | SSO Support | Status |
|----------|-------------|-------------|--------|
| **Snowflake** | BINARY(8MB) | ✅ Externalbrowser | ✅ Recommended |
| **PostgreSQL** | BYTEA | — | ✅ Supported |
| **SQL Server** | VARBINARY(MAX) | — | ✅ Supported |
| **CSV Files** | File + Path column | — | ✅ Supported |

### Data Restore
- **Bulk API v2 Restore** - Efficiently restore millions of records back to Salesforce
- **Insert/Update/Upsert** - Choose the right operation for your data
- **CSV Source** - Restore from backup CSV files
- **Relationship Resolution** - Automatically resolve lookup references
- **Dependency Ordering** - Restore parent objects before children
- **Cross-Org Transformation** - Map RecordTypes, picklist values, and users between different orgs
- **Dry Run Preview** - Preview data transformations before actual restore
- **Save/Load Mappings** - Persist transformation configs as JSON files

### User Experience
- **GitHub Dark Theme** - Beautiful GitHub-inspired dark UI with semi-transparent panels
- **Nature Background** - Calming mountain landscape for stress-free data operations
- **Custom Title Bar** - Sleek integrated title bar matching the theme
- **OAuth Authentication** - Secure Salesforce login
- **Session Caching** - SSO sessions cached for 30 minutes (no repeated browser popups)
- **Object Selection** - Search and filter 2500+ objects
- **Progress Tracking** - Real-time progress and logging
- **Memory Monitoring** - Automatic warnings for large objects

---

## 🗺️ Roadmap

See the full [Development Roadmap](docs/ROADMAP.md) for upcoming features.

### Completed ✅
| Feature | Status |
|---------|--------|
| Bulk API v2 Backup | ✅ Complete |
| Database Export (Snowflake, PostgreSQL, SQL Server) | ✅ Complete |
| Blob/Attachment Download | ✅ Complete |
| Data Restoration with Bulk API v2 | ✅ Complete |
| Relationship Resolution | ✅ Complete |
| Dependency Ordering | ✅ Complete |
| Cross-Org Transformation | ✅ Complete |
| Multi-Platform Native Builds | ✅ Complete |

### Coming Soon 🔥
| Feature | Description |
|---------|-------------|
| **Relationship-Aware Backup** | Set a limit on Account, preserve relationships → auto-download related Contacts, Cases, Opportunities |
| **Cascade Delete** | Delete an Account → automatically delete all related child records |
| **Duplicate Handling** | Smart duplicate detection with merge/skip/update options |
| **Related Record Creation** | Import Accounts → auto-create Opportunities with default values |

### Planned ⏳
| Feature | Description |
|---------|-------------|
| Scheduled Backups | Daily/weekly automated backups with notifications |
| Incremental Backups | Only backup changed records since last run |
| CLI Version | Command-line interface for CI/CD automation |
| Field History Archiving | Export field history beyond 24-month limit |
| Data Masking | GDPR/HIPAA compliant data anonymization |
| Metadata Backup | Export classes, triggers, flows with Git integration |

---

## 📋 Requirements

### For Running (Pre-built Releases)
- **Windows**: Windows 10/11
- **macOS**: macOS 11+ (Big Sur or later)
- **Linux**: Ubuntu 20.04+, Debian 10+, Fedora 36+, RHEL 8+
- No additional software needed!

### For Building from Source
- Java 21+
- Maven 3.9+

---

## 🔧 Installation

### Option 1: Download Pre-built (Recommended)

1. **Download** the latest release for your platform from [Releases](https://github.com/victorfelisbino/BackupForce/releases)
2. **Install or Extract**:
   - **Windows**: Run `.msi` installer or extract portable `.zip`
   - **macOS**: Mount `.dmg` and drag to Applications
   - **Linux**: Install `.deb`/`.rpm` or extract `.tar.gz`
3. **Run** BackupForce

### Option 2: Build from Source

```bash
# Clone the repository
git clone https://github.com/victorfelisbino/BackupForce.git
cd BackupForce

# Build and run
mvn javafx:run

# Or build portable executable
.\build-portable.ps1
```

---

## 📖 Usage

### 1. Login to Salesforce
- Click "Login with OAuth" for secure authentication
- Browser will open for Salesforce authorization
- Supports Production and Sandbox environments

### 2. Select Objects
- Browse or search 2500+ Salesforce objects
- Check objects to backup (Account, Contact, Opportunity, etc.)
- Use "Select All" or "Deselect All" for bulk selection

### 3. Configure Export
- **Output Folder**: Where CSV/blob files are saved
- **Record Limit**: Optional limit for testing (leave blank for all records)
- **Database Export**: Configure Snowflake/PostgreSQL/SQL Server connection

### 4. Start Backup
- Click "Start Backup"
- Monitor progress in the log panel
- Blobs are downloaded and stored alongside data

---

## 🔗 How Relationship Preservation Works

BackupForce preserves all Salesforce relationships (lookups and master-detail) so you can restore data with referential integrity intact. Here's how it works:

### What Gets Backed Up

Every record includes:
- **Salesforce ID** - The 18-character record ID (always included)
- **All Lookup Fields** - Fields like `AccountId`, `ContactId`, `ParentId` that reference other records
- **External ID Fields** - Custom fields marked as External ID (when available)
- **Name/Unique Fields** - Fields that can uniquely identify records

### Backup Output Files

| File | Purpose |
|------|---------|
| `ObjectName.csv` | All record data including IDs and lookup references |
| `_backup_manifest.json` | Metadata about relationships, restore order, and field info |
| `_id_mapping.json` | Maps Salesforce IDs to external identifiers for cross-org restore |

### Restore Scenarios

#### Same Org Restore
The simplest case - lookup field values (like `AccountId`) still point to existing records:
1. Restore parent objects first (Account, User, etc.)
2. Restore child objects (Contact, Opportunity, etc.)
3. Lookup IDs work automatically since records exist

#### Different Org / Sandbox Refresh
When restoring to a different org, IDs won't match. BackupForce handles this:

1. **With External IDs** (Best)
   - Use Upsert operation with External ID field
   - Records are matched by External ID, not Salesforce ID
   - Lookup fields are resolved using parent External IDs

2. **Without External IDs** (Still Works!)
   - Restore parent objects first (following `restoreOrder` in manifest)
   - Build an OldId → NewId mapping as records are inserted
   - Update lookup fields in child records using the mapping
   - BackupForce's restore feature handles this automatically

### Example: Backing Up Account Hierarchy

```
Account (parent)
  └── Contact (child, has AccountId lookup)
      └── Case (grandchild, has ContactId lookup)
```

**During Backup:**
- Account.csv: Contains Account IDs
- Contact.csv: Contains ContactId AND the AccountId lookup value
- Case.csv: Contains CaseId AND the ContactId lookup value
- `_backup_manifest.json`: Documents the relationships and restore order

**During Restore:**
1. Insert Accounts → Get new Account IDs
2. Map OldAccountId → NewAccountId
3. Update Contact.csv lookup values
4. Insert Contacts → Get new Contact IDs
5. Map OldContactId → NewContactId
6. Update Case.csv lookup values
7. Insert Cases

### Relationship-Aware Backup Option

When enabled, BackupForce can automatically:
1. **Discover child relationships** for your selected objects
2. **Auto-include related records** with WHERE filters
3. **Preserve the exact subset** of related data

Example: Back up 100 Accounts → automatically includes only the Contacts, Cases, and Opportunities related to those specific 100 Accounts.

### Manifest Example

```json
{
  "metadata": {
    "version": "2.0",
    "backupType": "relationship-aware"
  },
  "restoreOrder": ["Account", "Contact", "Case", "Opportunity"],
  "objects": {
    "Contact": {
      "relationshipFields": [
        {
          "fieldName": "AccountId",
          "referenceTo": ["Account"]
        }
      ],
      "externalIdFields": [
        { "name": "External_ID__c", "type": "string" }
      ]
    }
  }
}
```

---

## 🗄️ Database Export

### Snowflake Configuration
```
Host: your-account.snowflakecomputing.com
Database: SALESFORCE_BACKUP
Schema: PUBLIC
Username: your_user
Password: your_password
```

Blob fields are stored as `BINARY(8388608)` with additional columns:
- `BLOB_FILE_PATH` - Original file name
- `BLOB_SIZE` - File size in bytes

### Viewing Blobs in Snowflake
```sql
-- View as hex
SELECT Id, Name, TO_VARCHAR(Body, 'HEX') as BodyHex FROM Attachment;

-- Get file info
SELECT Id, Name, BLOB_FILE_PATH, BLOB_SIZE FROM Attachment;
```

---

## 🔒 Security

- OAuth 2.0 authentication (no password storage)
- Credentials stored securely in Windows Registry
- All data transmitted over HTTPS
- [VirusTotal Scan](https://www.virustotal.com/gui/file/72a63cc03f44c80292fd7d2d25106d18e737d5c4fefd386b459bf9eb18228a04) - 1/66 (Microsoft ML false positive, common for unsigned apps)

---

## 🐛 Troubleshooting

### "Failed to launch JVM"
- Use the latest release with bundled runtime
- Or install Java 11+ and run with Maven

### OutOfMemoryError
```bash
java -Xmx4g -jar BackupForce.jar
```

### Connection Errors
- Verify Salesforce credentials
- Check if your IP is whitelisted
- Ensure API access is enabled for your user

---

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.

Created and maintained by Victor Felisbino.

---

## 🤝 Contributing

Issues and pull requests are welcome!

---

<p align="center">
  <b>Made with ❤️ for the Salesforce community</b>
</p>
