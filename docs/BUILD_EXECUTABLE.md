# 🚀 Creating Native Executable for BackupForce

## Quick Start - Build Native .exe

Run one of these scripts:

### **Option 1: PowerShell (Recommended)**
```powershell
.\build-exe.ps1
```

### **Option 2: Batch File**
```cmd
build-exe.bat
```

This creates: `target\installer\BackupForce-2.0.0.exe`

---

## ✅ What You Get

- **Single `.exe` installer** (~60-80MB)
- **No Java required** - Bundles custom JRE
- **Professional installer** - Installs to Program Files
- **Start Menu shortcuts** - Easy access
- **Desktop shortcut** - One-click launch
- **Uninstaller included** - Clean removal

---

## 📋 Requirements

### To Build the Executable:
- **JDK 14 or higher** (for jpackage/jlink tools)
- Check version: `java -version`

### To Run the Executable:
- **Nothing!** Users don't need Java installed

---

## 🏗️ Build Process Explained

### 1️⃣ **Build JAR** (`mvn clean package`)
Compiles and packages application with all dependencies

### 2️⃣ **Create Custom JRE** (`jlink`)
Creates minimal Java runtime with only needed modules:
- `java.base` - Core Java
- `java.desktop` - JavaFX support
- `java.sql` - Database support
- `jdk.crypto.ec` - Encryption
- Result: ~50MB (vs 300MB+ full JDK)

### 3️⃣ **Package Executable** (`jpackage`)
Bundles JAR + Custom JRE into native Windows installer:
- Creates `.exe` installer
- Includes launcher executable
- Adds registry entries
- Creates uninstaller

---

## 🎯 Alternative: Launch4j (Lightweight .exe)

If you want a simple `.exe` wrapper (requires Java on target machine):

### Install Launch4j:
Download from: https://launch4j.sourceforge.net/

### Create Configuration:
```xml
<!-- launch4j-config.xml -->
<launch4jConfig>
  <headerType>gui</headerType>
  <jar>BackupForce.jar</jar>
  <outfile>BackupForce.exe</outfile>
  <icon>icon.ico</icon>
  <classPath>
    <mainClass>com.backupforce.Launcher</mainClass>
  </classPath>
  <jre>
    <minVersion>11</minVersion>
    <maxHeapSize>2048</maxHeapSize>
  </jre>
  <versionInfo>
    <fileVersion>2.0.0.0</fileVersion>
    <txtFileVersion>2.0.0</txtFileVersion>
    <productVersion>2.0.0.0</productVersion>
    <txtProductVersion>2.0.0</txtProductVersion>
    <companyName>Victor Felisbino</companyName>
    <fileDescription>Salesforce Backup Tool</fileDescription>
    <productName>BackupForce</productName>
    <internalName>BackupForce</internalName>
    <originalFilename>BackupForce.exe</originalFilename>
  </versionInfo>
</launch4jConfig>
```

### Build:
```cmd
launch4jc launch4j-config.xml
```

**Pros:** Small exe (~2MB)
**Cons:** Requires Java 11+ on user's machine

---

## 🎨 Adding an Icon

Create an icon file at `src/main/resources/icon.ico`:

### Quick Icon Creation:
1. Use online converter: https://convertio.co/png-ico/
2. Upload a PNG (256x256 recommended)
3. Save as `icon.ico`
4. Place in `src/main/resources/`

The build scripts will automatically use it!

---

## 📦 Distribution Options

### **Option A: Single Installer (Recommended)**
- File: `BackupForce-2.0.0.exe`
- Size: ~60-80MB
- Distribution: Upload to GitHub Releases / Share directly
- User Experience: Download → Install → Run

### **Option B: Portable Version**
After building, grab from:
```
target/BackupForce/BackupForce.exe
```
- No installation required
- Can run from USB drive
- Size: ~60MB (folder with all files)

### **Option C: JAR + Java Bundler**
Use existing `BackupForce.jar`:
- Size: ~20MB
- Requires Java 11+ on target machine
- Run with: `java -jar BackupForce.jar`

---

## 🔧 Troubleshooting

### "jpackage not found"
**Solution:** Update to JDK 14+
```powershell
java -version  # Must show version 14 or higher
```
Download from: https://adoptium.net/

### "jlink failed"
**Solution:** Verify JavaFX modules
```powershell
java --list-modules | Select-String javafx
```

### Build takes long time
**Normal!** First build: 2-5 minutes
- jlink creates custom JRE (~2 min)
- jpackage bundles everything (~2 min)
- Subsequent builds reuse JRE (faster)

### Large file size
**Expected!** Self-contained app includes:
- Your application: ~20MB
- Custom Java runtime: ~50MB
- Total: ~70MB

Trade-off: Users don't need Java installed ✅

---

## 📝 Manual Build Commands

If scripts don't work, run manually:

```powershell
# 1. Build JAR
mvn clean package -DskipTests

# 2. Create custom JRE
jlink --add-modules java.base,java.desktop,java.logging,java.sql,java.xml,java.naming,java.prefs,jdk.crypto.ec,jdk.unsupported --strip-debug --no-man-pages --no-header-files --compress=2 --output target/java-runtime

# 3. Create installer
jpackage --input target --name BackupForce --main-jar BackupForce.jar --main-class com.backupforce.Launcher --type exe --dest target/installer --runtime-image target/java-runtime --app-version 2.0.0 --vendor "Victor Felisbino" --win-dir-chooser --win-menu --win-shortcut
```

---

## 🎉 Success!

After building, test the installer:
```powershell
.\target\installer\BackupForce-2.0.0.exe
```

Distribute to users - they just run the installer!

---

**Developed by Victor Felisbino • BackupForce v2.0**
