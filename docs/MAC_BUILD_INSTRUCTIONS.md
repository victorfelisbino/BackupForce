# Building BackupForce for Mac

## Quick Start (Easiest)

### Option 1: Just Use the JAR
The Windows-built JAR works on Mac! No compilation needed:

1. Copy `BackupForce-Portable/BackupForce.jar` to your Mac
2. Open Terminal and run:
   ```bash
   java -jar BackupForce.jar
   ```

**Requirements**: Java 11+ installed on Mac
- Download from: https://adoptium.net/

---

## Native Mac App (Advanced)

### Option 2: Build Native .app Bundle
For a double-clickable Mac application:

### Prerequisites (on Mac)
1. **Install JDK 14+** (required for jpackage):
   ```bash
   brew install openjdk@21
   ```

2. **Install Maven**:
   ```bash
   brew install maven
   ```

### Build Steps

1. **Copy the project** to your Mac (or clone from git)

2. **Make build script executable**:
   ```bash
   chmod +x build-mac.sh
   ```

3. **Run the build**:
   ```bash
   ./build-mac.sh
   ```

4. **Output**:
   - `build/BackupForce.app` - Double-clickable Mac application
   - `build/BackupForce-1.0.0.dmg` - Installer (if you choose to create it)

### What You Get

**BackupForce.app** contains:
- Bundled Java runtime (no Java installation needed!)
- All JavaFX libraries
- Native Mac launcher
- Professional Mac app icon (if you add one)

### Distribution

**To share with other Mac users:**

1. **For a single user**: 
   - Copy `BackupForce.app` to Applications folder
   - Or run directly by double-clicking

2. **For multiple users**:
   - Distribute the `.dmg` installer
   - Users drag BackupForce to Applications

### Security Note

macOS Gatekeeper may block the app because it's not signed. Users need to:

1. Right-click `BackupForce.app`
2. Select "Open"
3. Click "Open" in the security dialog

**For production**, you should:
- Join Apple Developer Program ($99/year)
- Code sign the app
- Notarize with Apple

---

## Troubleshooting

### "command not found: java"
Install Java 11+:
```bash
brew install openjdk@11
```

### "jpackage: command not found"
jpackage is only in JDK 14+:
```bash
brew install openjdk@21
```

### "Cannot find Main class"
Make sure the JAR has the correct Main-Class in MANIFEST.MF:
```
Main-Class: com.backupforce.Launcher
```

### OAuth Port Issues on Mac
The multi-port fallback (1717, 8888, 3000, 8080, 9090) works on Mac too!

If all ports fail:
1. Check Mac Firewall: System Preferences → Security & Privacy → Firewall
2. Allow Java to accept incoming connections
3. Or temporarily disable firewall for testing

---

## Platform Differences

### What Works the Same
✅ OAuth authentication  
✅ Salesforce API calls  
✅ Database connections  
✅ File I/O  
✅ JavaFX UI  

### Mac-Specific Considerations
- **Menu bar**: Mac apps use the system menu bar (top of screen)
- **Keyboard shortcuts**: Cmd instead of Ctrl
- **File paths**: Use `/` instead of `\`
- **Look & feel**: JavaFX adapts to Mac automatically

### The JAR handles all of this automatically! 🎉

---

## Build Comparison

| Method | Size | Requires Java | Native Look | Easy Distribution |
|--------|------|---------------|-------------|-------------------|
| JAR file | ~20 MB | Yes (11+) | Good | Very Easy |
| .app bundle | ~200 MB | No (bundled) | Better | Easy |
| .dmg installer | ~200 MB | No (bundled) | Better | Professional |

---

## Quick Reference

```bash
# Build JAR only (works everywhere)
mvn clean package -DskipTests

# Run JAR on Mac
java -jar target/BackupForce.jar

# Build native Mac app
./build-mac.sh

# Run native app
open build/BackupForce.app
```

---

**Questions?** The JAR file is the easiest option and works perfectly on Mac!
