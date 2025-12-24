#!/bin/bash
# BackupForce Mac Build Script
# Run this on a Mac to create a native .app bundle

set -e  # Exit on error

echo "🍎 BackupForce Mac Build Script"
echo "================================"
echo ""

# Check if running on Mac
if [[ "$OSTYPE" != "darwin"* ]]; then
    echo "❌ This script must be run on macOS"
    exit 1
fi

# Check Java version
echo "[1/5] Checking Java version..."
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install JDK 14+ from:"
    echo "   https://adoptium.net/"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 14 ]; then
    echo "❌ Java 14+ required for jpackage. Found: $JAVA_VERSION"
    echo "   Download from: https://adoptium.net/"
    exit 1
fi
echo "✅ Java $JAVA_VERSION detected"

# Check Maven
echo ""
echo "[2/5] Checking Maven..."
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Install with: brew install maven"
    exit 1
fi
echo "✅ Maven detected"

# Build JAR
echo ""
echo "[3/5] Building JAR with Maven..."
mvn clean package -DskipTests -q
if [ ! -f "target/BackupForce.jar" ]; then
    echo "❌ Build failed - BackupForce.jar not found"
    exit 1
fi
echo "✅ JAR built successfully"

# Create app bundle with jpackage
echo ""
echo "[4/5] Creating Mac .app bundle..."

# Clean previous build
rm -rf build/BackupForce.app

# Create app bundle
jpackage \
  --input target \
  --name BackupForce \
  --main-jar BackupForce.jar \
  --main-class com.backupforce.Launcher \
  --type app-image \
  --dest build \
  --app-version 1.0.0 \
  --vendor "BackupForce" \
  --description "Salesforce Backup Tool" \
  --java-options '--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED' \
  --java-options '-Xmx4g'

if [ ! -d "build/BackupForce.app" ]; then
    echo "❌ jpackage failed to create .app bundle"
    exit 1
fi
echo "✅ Mac app bundle created"

# Optional: Create DMG
echo ""
echo "[5/5] Creating DMG installer (optional)..."
read -p "Create .dmg installer? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    jpackage \
      --input target \
      --name BackupForce \
      --main-jar BackupForce.jar \
      --main-class com.backupforce.Launcher \
      --type dmg \
      --dest build \
      --app-version 1.0.0 \
      --vendor "BackupForce" \
      --description "Salesforce Backup Tool" \
      --java-options '--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED' \
      --java-options '-Xmx4g'
    
    if [ -f build/BackupForce-1.0.0.dmg ]; then
        echo "✅ DMG installer created: build/BackupForce-1.0.0.dmg"
    fi
else
    echo "⏭️  Skipping DMG creation"
fi

echo ""
echo "🎉 Build Complete!"
echo ""
echo "📦 Output:"
echo "   Mac App:  build/BackupForce.app"
if [ -f build/BackupForce-1.0.0.dmg ]; then
    echo "   Installer: build/BackupForce-1.0.0.dmg"
fi
echo ""
echo "🚀 To run: open build/BackupForce.app"
echo "📤 To distribute: Share BackupForce.app or BackupForce-1.0.0.dmg"
echo ""
echo "⚠️  Note: Users need macOS 10.13+ to run the app"
