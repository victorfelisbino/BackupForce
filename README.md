# BackupForce

A desktop application for backing up and restoring Salesforce data using the Bulk API v2. Supports export to CSV files or direct database integration (Snowflake, PostgreSQL, SQL Server).

![BackupForce Screenshot](docs/screenshot.png)

## Downloads

| Platform | Download |
|----------|----------|
| Windows | [BackupForce-3.0.0-portable.zip](https://github.com/victorfelisbino/BackupForce/releases/latest) |
| macOS | [BackupForce-3.0.0-macos.zip](https://github.com/victorfelisbino/BackupForce/releases/latest) |
| Linux | [BackupForce-3.0.0-linux.tar.gz](https://github.com/victorfelisbino/BackupForce/releases/latest) |

Runtime is bundled - no Java installation required.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         BackupForce                              │
├─────────────────────────────────────────────────────────────────┤
│  UI Layer (JavaFX)                                               │
│  ├── FXML Controllers                                            │
│  └── CSS Theming                                                 │
├─────────────────────────────────────────────────────────────────┤
│  Service Layer                                                   │
│  ├── BackupService (orchestrates backup operations)              │
│  ├── RestoreService (handles data restoration)                   │
│  └── TransformationService (cross-org field mapping)             │
├─────────────────────────────────────────────────────────────────┤
│  API Layer                                                       │
│  ├── BulkV2Client (Salesforce Bulk API v2)                       │
│  ├── SalesforceClient (REST API for metadata)                    │
│  └── OAuthManager (OAuth 2.0 authentication)                     │
├─────────────────────────────────────────────────────────────────┤
│  Storage Layer                                                   │
│  ├── CsvExporter                                                 │
│  ├── DatabaseExporter (Snowflake, PostgreSQL, SQL Server)        │
│  └── BlobDownloader (Attachments, ContentVersion, Document)      │
└─────────────────────────────────────────────────────────────────┘
```

### Key Components

| Component | Description |
|-----------|-------------|
| `BulkV2Client` | HTTP client for Salesforce Bulk API v2 with connection pool recovery |
| `BackupService` | Multi-threaded backup orchestration (5 parallel workers) |
| `RestoreService` | Handles insert/update/upsert with relationship resolution |
| `TransformationService` | Maps RecordTypes, picklist values, and users between orgs |

---

## Features

### Backup
- **Bulk API v2** - Efficient export for large datasets (millions of records)
- **Field Selection** - Select specific fields per object
- **Blob Export** - Downloads `Attachment.Body`, `ContentVersion.VersionData`, `Document.Body`
- **Record Limits** - Test with limited record counts
- **Parallel Processing** - 5 concurrent worker threads

### Database Integration

| Database | Blob Storage | SSO | Notes |
|----------|-------------|-----|-------|
| Snowflake | `BINARY(8MB)` | Externalbrowser | Recommended |
| PostgreSQL | `BYTEA` | - | Standard JDBC |
| SQL Server | `VARBINARY(MAX)` | - | Standard JDBC |
| CSV | File + path column | - | Default |

### Restore
- **Insert/Update/Upsert** operations
- **Relationship Resolution** - Resolves lookup references automatically
- **Dependency Ordering** - Restores parent objects before children
- **Cross-Org Transformation** - Maps metadata between different orgs
- **Dry Run** - Preview transformations before execution

---

## Relationship Preservation

BackupForce maintains referential integrity by preserving all lookup and master-detail relationships.

### Backup Output

| File | Purpose |
|------|---------|
| `ObjectName.csv` | Record data with IDs and lookup references |
| `_backup_manifest.json` | Relationship metadata and restore order |
| `_id_mapping.json` | ID to external identifier mappings |

### Restore Process

**Same Org:**
1. Restore parent objects (Account, User)
2. Restore child objects (Contact, Opportunity)
3. Lookup IDs resolve automatically

**Different Org:**
1. Insert parent records, capture new IDs
2. Build OldId → NewId mapping
3. Transform lookup values in child records
4. Insert child records

### Manifest Structure

```json
{
  "metadata": {
    "version": "2.0",
    "backupType": "relationship-aware"
  },
  "restoreOrder": ["Account", "Contact", "Case"],
  "objects": {
    "Contact": {
      "relationshipFields": [
        { "fieldName": "AccountId", "referenceTo": ["Account"] }
      ],
      "externalIdFields": [
        { "name": "External_ID__c", "type": "string" }
      ]
    }
  }
}
```

---

## Requirements

### Pre-built Releases
- Windows 10/11, macOS 11+, or Linux (Ubuntu 20.04+, RHEL 8+)

### Building from Source
- Java 21+
- Maven 3.9+

---

## Installation

### Pre-built Release

1. Download from [Releases](https://github.com/victorfelisbino/BackupForce/releases)
2. Extract or install:
   - **Windows**: Extract `.zip` or run `.msi`
   - **macOS**: Mount `.dmg`, drag to Applications
   - **Linux**: Extract `.tar.gz` or install `.deb`/`.rpm`

### From Source

```bash
git clone https://github.com/victorfelisbino/BackupForce.git
cd BackupForce
mvn javafx:run
```

Build executable:
```powershell
.\scripts\build-portable.ps1
```

---

## Usage

### Authentication
OAuth 2.0 with Salesforce. Supports Production and Sandbox environments.

### Backup
1. Select objects from the object list
2. Configure output folder
3. Optional: Set record limit, configure database connection
4. Start backup

### Restore
1. Select backup folder containing CSV files and manifest
2. Choose operation: Insert, Update, or Upsert
3. Configure transformation mappings if restoring to different org
4. Preview with dry run, then execute

---

## Database Export

### Snowflake

```
Host: account.snowflakecomputing.com
Database: SALESFORCE_BACKUP
Schema: PUBLIC
```

Blob columns include:
- `BLOB_FILE_PATH` - Original filename
- `BLOB_SIZE` - Size in bytes

Query blobs:
```sql
SELECT Id, Name, TO_VARCHAR(Body, 'HEX') as BodyHex FROM Attachment;
```

---

## Configuration

### Memory
Default: 4GB heap. Adjust for large objects:
```bash
java -Xmx8g -jar BackupForce.jar
```

### OAuth
Configure Connected App in Salesforce Setup. See [docs/OAUTH_SETUP.md](docs/OAUTH_SETUP.md).

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| JVM launch failure | Use bundled runtime release |
| OutOfMemoryError | Increase heap: `-Xmx4g` or `-Xmx8g` |
| Connection errors | Check IP whitelist, verify API access |

---

## Development

### Project Structure

```
src/main/java/com/backupforce/
├── api/          # Salesforce API clients
├── model/        # Data models
├── service/      # Business logic
├── ui/           # JavaFX controllers
└── util/         # Utilities
```

### Build

```bash
mvn clean package           # JAR
mvn javafx:run              # Run from source
.\scripts\build-portable.ps1  # Windows executable
```

### Testing

```bash
mvn test                    # Unit tests
mvn verify -PuiTests        # UI tests (headless)
```

---

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md).

| Status | Feature |
|--------|---------|
| Complete | Bulk API v2 backup, Database export, Blob download, Data restore, Cross-org transformation |
| Planned | Scheduled backups, Incremental backups, CLI, Field history archiving |

---

## License

MIT License - see [LICENSE](LICENSE).

## Contributing

Issues and pull requests welcome.
