# BackupForce

A JavaFX desktop application for backing up Salesforce data using Bulk API v2.

## Features

- **User-friendly GUI** - Simple login and backup interface
- **Bulk API v2** - Fast, efficient data extraction for large datasets
- **Object Selection** - Choose which Salesforce objects to backup
- **Multi-threaded** - Parallel processing for faster backups
- **Memory Monitoring** - Automatic monitoring for large objects (Attachments, ContentVersion, etc.)
- **Progress Tracking** - Real-time progress indicators and detailed logging
- **Credential Storage** - Optional "Remember Me" feature for convenience

## Requirements

- **Java 11 or higher** - [Download Java](https://www.oracle.com/java/technologies/downloads/)
- **Salesforce Account** - With API access enabled

## Building from Source

### Prerequisites
- Maven 3.6+
- Java 11+

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/victorfelisbino/backupforce-javafx.git
cd backupforce-javafx
```

2. Build the executable JAR:
```bash
mvn clean package
```

This creates `BackupForce.jar` in the `target/` directory.

## Running the Application

### Option 1: Using Maven (for development)
```bash
mvn javafx:run
```

### Option 2: Using the JAR file
After building, the executable JAR is located at `target/BackupForce.jar`

**Windows:**
```cmd
java -jar target\BackupForce.jar
```

**Mac/Linux:**
```bash
java -jar target/BackupForce.jar
```

### Option 3: Using the launcher script (Windows)
Copy `BackupForce.bat` to the same directory as `BackupForce.jar` and double-click it.

## Usage

1. **Login**
   - Enter your Salesforce username
   - Enter your password
   - Enter your security token (if required by your org)
   - Select environment (Production or Sandbox)
   - Click "Login"

2. **Select Objects**
   - Use the search box to filter objects
   - Check/uncheck objects you want to backup
   - Use "Select All" or "Deselect All" for bulk selection
   - View selection count at the bottom

3. **Start Backup**
   - Choose output folder
   - Click "Start Backup"
   - Monitor progress in the log panel
   - Large objects (Attachments, ContentVersion) will show memory warnings

## Distribution

To distribute the application:

1. Build the JAR: `mvn clean package`
2. Copy `target/BackupForce.jar` to your distribution folder
3. Include `BackupForce.bat` (for Windows users)
4. Package or upload to your website

### Creating a Windows Installer (Optional)

For a more professional distribution, you can use tools like:
- **jpackage** (Java 14+) - Creates native Windows installers
- **Launch4j** - Wraps JAR in a Windows .exe
- **Install4j** - Professional installer creation tool

## Security Notes

- Credentials are stored in Windows Registry using Java Preferences API
- Credentials are Base64 encoded (NOT encrypted)
- For production use, consider implementing proper encryption
- Never commit `myconfig.properties` if it contains credentials

## Troubleshooting

### OutOfMemoryError with large objects
- The app monitors memory for Attachment, ContentVersion, Document, and StaticResource
- If you see memory warnings, try:
  - Increasing Java heap: `java -Xmx4g -jar BackupForce.jar`
  - Backing up large objects separately
  - Reducing thread count (modify `BackupController.java`)

### Module errors
- Ensure you're using Java 11+
- JavaFX modules are bundled in the fat JAR

### Connection errors
- Verify your Salesforce credentials
- Check if security token is required
- Ensure your IP is not restricted

## License

Private - All Rights Reserved

## Author

Victor Felisbino
