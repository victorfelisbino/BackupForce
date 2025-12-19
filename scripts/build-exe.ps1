# BackupForce - Native Windows Executable Builder (PowerShell)
# Developed by Victor Felisbino

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BackupForce Native Executable Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Clean and build the JAR
Write-Host "[1/3] Building application JAR..." -ForegroundColor Yellow
try {
    mvn clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    Write-Host "      Build successful!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Build failed!" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 2: Create custom JRE with jlink
Write-Host "[2/3] Creating custom Java runtime..." -ForegroundColor Yellow
try {
    jlink --add-modules java.base,java.desktop,java.logging,java.sql,java.xml,java.naming,java.prefs,jdk.crypto.ec,jdk.unsupported `
          --strip-debug `
          --no-man-pages `
          --no-header-files `
          --compress=2 `
          --output target/java-runtime
    if ($LASTEXITCODE -ne 0) { throw "jlink failed" }
    Write-Host "      Custom runtime created! (~50MB)" -ForegroundColor Green
} catch {
    Write-Host "ERROR: jlink failed! Make sure you're using JDK 14+" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 3: Create native installer with jpackage
Write-Host "[3/3] Creating Windows executable..." -ForegroundColor Yellow
try {
    jpackage --input target `
             --name BackupForce `
             --main-jar BackupForce.jar `
             --main-class com.backupforce.Launcher `
             --type exe `
             --dest target/installer `
             --runtime-image target/java-runtime `
             --app-version 3.0.0 `
             --vendor "Victor Felisbino" `
             --description "Salesforce Backup Tool - Professional Dark Theme" `
             --win-dir-chooser `
             --win-menu `
             --win-shortcut `
             --win-menu-group BackupForce
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
} catch {
    Write-Host "ERROR: jpackage failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "SUCCESS! Executable created at:" -ForegroundColor Green
Write-Host "target\installer\BackupForce-3.0.0.exe" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Distribution Details:" -ForegroundColor Yellow
Write-Host "  - Single .exe installer (~60-80MB)" -ForegroundColor White
Write-Host "  - No Java required on user's machine" -ForegroundColor White
Write-Host "  - Installs to Program Files" -ForegroundColor White
Write-Host "  - Creates Start Menu shortcuts" -ForegroundColor White
Write-Host "  - Creates Desktop shortcut" -ForegroundColor White
Write-Host ""
