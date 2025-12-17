# 🎯 BackupForce - Distribution Options

## Quick Comparison

| Method | File Type | Size | Java Required | Best For |
|--------|-----------|------|---------------|----------|
| **Native Installer** | `.exe` installer | 60-80MB | ❌ No | Public distribution |
| **VBS Launcher** | `.vbs` script | 20KB | ✅ Yes (Java 11+) | Quick personal use |
| **JAR File** | `.jar` | 20MB | ✅ Yes (Java 11+) | Developers |
| **Portable EXE** | `.exe` folder | 60MB | ❌ No | USB/portable |

---

## 🚀 Option 1: VBS Launcher (Available Now!)

**Perfect for: Quick setup with current Java 11**

### Build & Run:
```powershell
.\build-simple.ps1
```

Then double-click: **`BackupForce.vbs`**

### What it does:
- Runs `javaw.exe -jar BackupForce.jar`
- No console window
- Clean, simple launch

### Distribution:
Share these 2 files:
1. `BackupForce.jar` (20MB)
2. `BackupForce.vbs` (1KB)

Users need: Java 11+ installed

---

## 🎯 Option 2: Native Installer (Recommended for Public)

**Perfect for: Sharing with non-technical users**

### Prerequisites:
1. **Upgrade to JDK 17+**
   - Download: https://adoptium.net/temurin/releases/
   - Install JDK 17 (LTS)
   - Verify: `java -version` shows 17+

### Build:
```powershell
.\build-exe.ps1
```

### Result:
`target\installer\BackupForce-2.0.0.exe` (60-80MB)

### What it does:
- Professional Windows installer
- Bundles Java runtime (no Java needed!)
- Start Menu shortcuts
- Desktop shortcut
- Uninstaller included
- Digital signature ready

### Distribution:
Share single file: `BackupForce-2.0.0.exe`
Users just run installer - works on ANY Windows machine!

---

## 📦 Quick Action Plan

### Right Now (Java 11):
```powershell
# Build simple launcher
.\build-simple.ps1

# Launch app
.\BackupForce.vbs
```
✅ **Works immediately!**

### For Public Release:
```powershell
# 1. Install JDK 17 from https://adoptium.net/
# 2. Build native installer
.\build-exe.ps1

# 3. Distribute
# Upload target\installer\BackupForce-2.0.0.exe to GitHub Releases
```
✅ **Professional distribution!**

---

## 🔧 Upgrade to JDK 17 (Optional but Recommended)

### Why upgrade?
- ✅ Create native installers (jpackage)
- ✅ Better JavaFX performance
- ✅ Latest security updates
- ✅ Long-term support (LTS)

### How to upgrade:
1. **Download JDK 17**
   - https://adoptium.net/temurin/releases/
   - Choose: Windows x64, JDK 17 (LTS)

2. **Install** (default location is fine)

3. **Verify**
   ```powershell
   java -version
   # Should show: "openjdk version 17..."
   ```

4. **Build native exe**
   ```powershell
   .\build-exe.ps1
   ```

### Don't want to upgrade?
No problem! Use VBS launcher - works perfectly with Java 11.

---

## 📝 File Details

### VBS Launcher (`BackupForce.vbs`)
```vbscript
Set oShell = CreateObject("WScript.Shell")
oShell.Run "javaw.exe -jar BackupForce.jar", 0, false
```
- Launches without console window
- 1KB file size
- Requires Java on PATH

### Native Installer Benefits
- ✅ No Java installation needed
- ✅ Professional user experience
- ✅ Automatic updates possible
- ✅ Windows app registration
- ✅ Clean uninstall

---

## 🎨 Adding Custom Icon (Optional)

Create `icon.ico` (256x256 recommended):
1. Convert PNG to ICO: https://convertio.co/png-ico/
2. Save as `src/main/resources/icon.ico`
3. Rebuild

Native installer will use your icon!

---

## 📊 Distribution Size Comparison

**VBS Launcher Distribution:**
- `BackupForce.jar`: 20MB
- `BackupForce.vbs`: 1KB
- **Total: ~20MB**
- Requires: Java 11+ on user's machine

**Native Installer Distribution:**
- `BackupForce-2.0.0.exe`: 70MB
- **Total: 70MB**
- Requires: Nothing! Java bundled

**Trade-off:** 50MB larger file = No dependencies ✅

---

## ✅ Current Recommendation

### For You (Development):
```powershell
.\build-simple.ps1  # Quick VBS launcher
```

### For Public Release:
```powershell
# Upgrade to JDK 17, then:
.\build-exe.ps1     # Native installer
```

### For GitHub Release:
Upload `BackupForce-2.0.0.exe` as release asset
Users download and install - zero friction!

---

**Developed by Victor Felisbino • BackupForce v2.0**
