# BackupForce - Portable Executable Builder
# Creates a portable app folder with native launcher

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BackupForce Portable App Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Ensure using JDK 21+
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "C:\Program Files\Java\jdk-21\bin;" + $env:Path

# Build the JAR
Write-Host "[1/3] Building JAR..." -ForegroundColor Yellow
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "      Build successful!" -ForegroundColor Green
Write-Host ""

# Create custom JRE with jlink
Write-Host "[2/3] Creating custom Java runtime..." -ForegroundColor Yellow
if (Test-Path "target\java-runtime") {
    Remove-Item -Recurse -Force "target\java-runtime"
}
jlink --add-modules java.base,java.desktop,java.logging,java.sql,java.xml,java.naming,java.prefs,jdk.crypto.ec,jdk.unsupported `
      --strip-debug `
      --no-man-pages `
      --no-header-files `
      --compress=2 `
      --output target/java-runtime
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: jlink failed!" -ForegroundColor Red
    exit 1
}
Write-Host "      Custom runtime created! (~50MB)" -ForegroundColor Green
Write-Host ""

# Create app-image (portable folder) instead of installer
Write-Host "[3/3] Creating portable application..." -ForegroundColor Yellow
if (Test-Path "target\BackupForce") {
    Remove-Item -Recurse -Force "target\BackupForce"
}
jpackage --input target `
         --name BackupForce `
         --main-jar BackupForce.jar `
         --main-class com.backupforce.Launcher `
         --type app-image `
         --dest target `
         --runtime-image target/java-runtime `
         --app-version 2.0.0 `
         --vendor "Victor Felisbino" `
         --description "Salesforce Backup Tool"
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: jpackage failed!" -ForegroundColor Red
    exit 1
}

Write-Host "      Portable app created!" -ForegroundColor Green
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "SUCCESS! Portable app created at:" -ForegroundColor Green
Write-Host "target\BackupForce\" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Launcher: target\BackupForce\BackupForce.exe" -ForegroundColor Yellow
Write-Host ""
Write-Host "Distribution:" -ForegroundColor White
Write-Host "  - Compress target\BackupForce folder to ZIP" -ForegroundColor White
Write-Host "  - Users extract and run BackupForce.exe" -ForegroundColor White
Write-Host "  - No installation needed!" -ForegroundColor White
Write-Host "  - No Java required on user's machine" -ForegroundColor White
Write-Host ""
Write-Host "Testing the executable..." -ForegroundColor Yellow
Start-Process -FilePath "target\BackupForce\BackupForce.exe"
Write-Host "Application launched!" -ForegroundColor Green
Write-Host ""
