# BackupForce

[![Download](https://img.shields.io/github/v/release/victorfelisbino/BackupForce?label=Download&style=for-the-badge)](https://github.com/victorfelisbino/BackupForce/releases/latest)
[![License](https://img.shields.io/badge/License-Free-green?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows%20|%20macOS%20|%20Linux-blue?style=for-the-badge)](https://github.com/victorfelisbino/BackupForce/releases)

A powerful desktop application for backing up Salesforce data to CSV files or databases. Features a modern Windows 11 dark theme UI.

![BackupForce Screenshot](docs/screenshot.png)

---

## 🚀 Quick Download

**[⬇️ Download BackupForce v2.2.0](https://github.com/victorfelisbino/BackupForce/releases/latest)**

### Windows
- `BackupForce-2.2.0.msi` - Installer
- `BackupForce-2.2.0-portable.zip` - Portable

### macOS
- `BackupForce-2.2.0.dmg` - Disk Image
- `BackupForce-2.2.0-macos.zip` - Portable

### Linux
- `BackupForce-2.2.0.deb` - Debian/Ubuntu
- `BackupForce-2.2.0.rpm` - Fedora/RHEL
- `BackupForce-2.2.0-linux.tar.gz` - Universal

**No Java installation required** - runtime is bundled!

---

## ✨ Features

### Data Export
- **Bulk API v2** - Fast, efficient data extraction for millions of records
- **Field Selection** - Choose exactly which fields to export for each object
- **Blob Downloads** - Automatically downloads Attachment.Body, ContentVersion.VersionData, Document.Body
- **Record Limits** - Test exports with a limited number of records
- **Multi-threaded** - Parallel processing for faster backups

### Database Support
| Database | Blob Storage | SSO Support | Status |
|----------|-------------|-------------|--------|
| **Snowflake** | BINARY(8MB) | ✅ Externalbrowser | ✅ Recommended |
| **PostgreSQL** | BYTEA | — | ✅ Supported |
| **SQL Server** | VARBINARY(MAX) | — | ✅ Supported |
| **CSV Files** | File + Path column | — | ✅ Supported |

### User Experience
- **Windows 11 Dark Theme** - Modern Fluent Design UI
- **OAuth Authentication** - Secure Salesforce login
- **Session Caching** - SSO sessions cached for 30 minutes (no repeated browser popups)
- **Object Selection** - Search and filter 2500+ objects
- **Progress Tracking** - Real-time progress and logging
- **Memory Monitoring** - Automatic warnings for large objects

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

Free to use. Created by Victor Felisbino.

---

## 🤝 Contributing

Issues and pull requests are welcome!

---

<p align="center">
  <b>Made with ❤️ for the Salesforce community</b>
</p>
