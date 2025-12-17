# BackupForce

[![Download](https://img.shields.io/github/v/release/victorfelisbino/BackupForce?label=Download&style=for-the-badge)](https://github.com/victorfelisbino/BackupForce/releases/latest)
[![License](https://img.shields.io/badge/License-Free-green?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows-blue?style=for-the-badge)](https://github.com/victorfelisbino/BackupForce/releases)

A powerful desktop application for backing up Salesforce data to CSV files or databases. Features a modern Windows 11 dark theme UI.

![BackupForce Screenshot](docs/screenshot.png)

---

## 🚀 Quick Download

**[⬇️ Download BackupForce v2.0.0](https://github.com/victorfelisbino/BackupForce/releases/latest)**

1. Download `BackupForce-2.0.0-portable.zip`
2. Extract to any folder
3. Run `BackupForce.exe`

**No Java installation required** - runtime is bundled!

---

## ✨ Features

### Data Export
- **Bulk API v2** - Fast, efficient data extraction for millions of records
- **Blob Downloads** - Automatically downloads Attachment.Body, ContentVersion.VersionData, Document.Body
- **Record Limits** - Test exports with a limited number of records
- **Multi-threaded** - Parallel processing for faster backups

### Database Support
| Database | Blob Storage | Status |
|----------|-------------|--------|
| **Snowflake** | BINARY(8MB) | ✅ Recommended |
| **PostgreSQL** | BYTEA | ✅ Supported |
| **SQL Server** | VARBINARY(MAX) | ✅ Supported |
| **CSV Files** | File + Path column | ✅ Supported |

### User Experience
- **Windows 11 Dark Theme** - Modern Fluent Design UI
- **OAuth Authentication** - Secure Salesforce login
- **Object Selection** - Search and filter 2500+ objects
- **Progress Tracking** - Real-time progress and logging
- **Memory Monitoring** - Automatic warnings for large objects

---

## 📋 Requirements

### For Running (Portable .exe)
- Windows 10/11
- No additional software needed!

### For Building from Source
- Java 11+
- Maven 3.6+

---

## 🔧 Installation

### Option 1: Portable Executable (Recommended)

1. **Download** the latest release from [Releases](https://github.com/victorfelisbino/BackupForce/releases)
2. **Extract** `BackupForce-2.0.0-portable.zip` to any folder
3. **Run** `BackupForce.exe`

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

Free to use. Created by Victor Felisbino.

---

## 🤝 Contributing

Issues and pull requests are welcome!

---

<p align="center">
  <b>Made with ❤️ for the Salesforce community</b>
</p>
