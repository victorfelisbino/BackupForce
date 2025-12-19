# BackupForce - Portable Windows Executable Builder (PowerShell)
# Creates a portable app-image with .exe launcher (no installer needed)
# Developed by Victor Felisbino

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BackupForce Portable App Builder" -ForegroundColor Cyan
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

# Step 2: Locate JavaFX SDK
Write-Host "[2/4] Locating JavaFX modules..." -ForegroundColor Yellow

# Find JavaFX jmods in Maven repository
$jfxVersion = "17.0.2"
$m2Repo = "$env:USERPROFILE\.m2\repository\org\openjfx"
$jfxModules = @("javafx.base", "javafx.controls", "javafx.fxml", "javafx.graphics")

# Check if we have JavaFX jmods available
$jfxPath = ""
$javafxSdkPath = "C:\javafx-sdk-$jfxVersion\lib"
if (Test-Path $javafxSdkPath) {
    $jfxPath = $javafxSdkPath
    Write-Host "      Found JavaFX SDK at $jfxPath" -ForegroundColor Green
} else {
    Write-Host "      JavaFX SDK not found, using jpackage without custom runtime" -ForegroundColor Yellow
}
Write-Host ""

# Step 3: Create portable app-image with jpackage
Write-Host "[3/4] Creating portable Windows app..." -ForegroundColor Yellow
try {
    # IMPORTANT: Use a SEPARATE directory for jpackage output to prevent recursive folder issues
    # jpackage creates a folder named after the app (BackupForce) inside --dest
    # Using --input target and --dest target caused infinite recursion (target/BackupForce/app/BackupForce/...)
    
    $distDir = "dist"
    
    # Clean the destination directory completely (prevents recursive folder issues)
    if (Test-Path $distDir) {
        Write-Host "      Cleaning previous build..." -ForegroundColor Gray
        # Use .NET method with long path support for safety
        [System.IO.Directory]::Delete("\\?\$((Get-Location).Path)\$distDir", $true) 2>$null
        Remove-Item $distDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    
    # Copy JAR to a clean input directory (separate from target to avoid jpackage picking up old outputs)
    $inputDir = "build-temp"
    if (Test-Path $inputDir) {
        Remove-Item $inputDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
    Copy-Item "target\BackupForce.jar" $inputDir -Force
    
    # For JavaFX apps, let jpackage use default JDK runtime (includes everything needed)
    # The fat JAR already includes JavaFX classes
    jpackage --input $inputDir `
             --name BackupForce `
             --main-jar BackupForce.jar `
             --main-class com.backupforce.Launcher `
             --type app-image `
             --dest $distDir `
             --app-version 3.0.0 `
             --vendor "Victor Felisbino" `
             --description "Salesforce Backup Tool with Nature-Inspired UI" `
             --java-options "-Xmx512m" `
             --java-options "--enable-native-access=ALL-UNNAMED" `
             --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED"
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
    Write-Host "      App image created!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: jpackage failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "SUCCESS! Portable app created at:" -ForegroundColor Green
Write-Host "dist\BackupForce\" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Distribution Details:" -ForegroundColor Yellow
Write-Host "  - Portable folder with BackupForce.exe" -ForegroundColor White
Write-Host "  - Includes bundled Java runtime" -ForegroundColor White
Write-Host "  - No Java required on user's machine" -ForegroundColor White
Write-Host "  - Just copy folder and run!" -ForegroundColor White
Write-Host "  - ZIP this folder for distribution" -ForegroundColor White
Write-Host ""
Write-Host "To run: dist\BackupForce\BackupForce.exe" -ForegroundColor Cyan
Write-Host ""

# Optionally create a ZIP file
$createZip = Read-Host "Create ZIP file for distribution? (y/n)"
if ($createZip -eq "y" -or $createZip -eq "Y") {
    Write-Host "Creating ZIP file..." -ForegroundColor Yellow
    $zipPath = "releases\BackupForce-3.0.0-portable.zip"
    New-Item -ItemType Directory -Path "releases" -Force | Out-Null
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }
    Compress-Archive -Path "dist\BackupForce\*" -DestinationPath $zipPath
    Write-Host "ZIP created: $zipPath" -ForegroundColor Green
}
