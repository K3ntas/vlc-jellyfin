# Jellyfin Netflix Android TV - Build & Installation Guide

This is a modified version of the official Jellyfin Android TV app with Netflix-style genre browsing for Movies and TV Shows libraries.

## What's Different From Official Jellyfin

- **App ID**: `org.jellyfin.androidtv.netflix` (can be installed alongside official Jellyfin)
- **App Name**: "Jellyfin Netflix" / "Jellyfin Netflix Debug"
- **Movies/TV Shows Libraries**: Display content organized by genre rows (Netflix-style) instead of the default view
- **Home Screen**: Unchanged, uses original Jellyfin layout

## Requirements

### Software Requirements

1. **Java JDK 21 or higher**
   - This project uses Java 25 (configured in `gradle/libs.versions.toml`)
   - Download from: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/

2. **Android SDK**
   - Install Android Studio or standalone SDK
   - Required SDK components:
     - Android SDK Platform 35
     - Android SDK Build-Tools
     - Android SDK Platform-Tools (for adb)

3. **Git** (for cloning and version control)

### Hardware Requirements

- Fire TV or Android TV device for testing
- Computer running Windows/macOS/Linux

## Project Setup

### 1. Clone the Repository

```bash
git clone https://github.com/jellyfin/jellyfin-androidtv.git jellyfin-androidtv-netflix
cd jellyfin-androidtv-netflix
```

### 2. Configure Android SDK Path

Create or edit `local.properties` in the project root:

```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```

On macOS/Linux:
```properties
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
```

### 3. Verify Java Version

```bash
java -version
```

Should show Java 21 or higher. If using a different version, update `gradle/libs.versions.toml`:

```toml
java-jdk = "25"  # or your installed version
```

## Building the Project

### Build Debug APK

From the project root directory:

```bash
# On Windows (Git Bash or similar)
./gradlew assembleDebug

# On Windows (Command Prompt)
gradlew.bat assembleDebug

# On macOS/Linux
./gradlew assembleDebug
```

**Build output location:**
```
app/build/outputs/apk/debug/jellyfin-androidtv-v0.0.0-dev.1-debug.apk
```

### Build Release APK

```bash
./gradlew assembleRelease
```

Note: Release builds require signing configuration.

### Clean Build

If you encounter build issues:

```bash
./gradlew clean
./gradlew assembleDebug
```

### Build with Verbose Output

```bash
./gradlew assembleDebug --info
```

## Installing on Fire TV / Android TV

### 1. Enable Developer Options on Fire TV

1. Go to **Settings** > **My Fire TV** > **About**
2. Click on **Fire TV Stick** 7 times to enable Developer Options
3. Go back to **Settings** > **My Fire TV** > **Developer Options**
4. Enable **ADB debugging**
5. Enable **Apps from Unknown Sources**

### 2. Find Fire TV IP Address

1. Go to **Settings** > **My Fire TV** > **About** > **Network**
2. Note the IP address (e.g., `<DEVICE_IP>`)

### 3. Connect via ADB

```bash
# Connect to Fire TV (replace with your IP)
adb connect <DEVICE_IP>:5555

# Verify connection
adb devices
```

**ADB location on Windows:**
```
C:\Users\YOUR_USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

### 4. Install the APK

```bash
# Install (replace path as needed)
adb install -r app/build/outputs/apk/debug/jellyfin-androidtv-v0.0.0-dev.1-debug.apk
```

The `-r` flag reinstalls the app if it already exists.

### 5. Full Installation Command (Windows)

```bash
"C:\Users\YOUR_USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\path\to\jellyfin-androidtv-netflix\app\build\outputs\apk\debug\jellyfin-androidtv-v0.0.0-dev.1-debug.apk"
```

## Quick Build & Install Script

Create a file `build_and_install.bat` (Windows):

```batch
@echo off
echo Building APK...
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Connecting to Fire TV...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" connect <DEVICE_IP>:5555

echo Installing APK...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\jellyfin-androidtv-v0.0.0-dev.1-debug.apk"

echo Done!
pause
```

Or `build_and_install.sh` (macOS/Linux):

```bash
#!/bin/bash
echo "Building APK..."
./gradlew assembleDebug || { echo "Build failed!"; exit 1; }

echo "Connecting to Fire TV..."
adb connect <DEVICE_IP>:5555

echo "Installing APK..."
adb install -r app/build/outputs/apk/debug/jellyfin-androidtv-v0.0.0-dev.1-debug.apk

echo "Done!"
```

## Uninstalling

```bash
adb uninstall org.jellyfin.androidtv.netflix.debug
```

## Troubleshooting

### Build Issues

**"SDK location not found"**
- Create `local.properties` with correct SDK path

**"Java version mismatch"**
- Update `java-jdk` in `gradle/libs.versions.toml` to match your installed Java version

**"Gradle wrapper not found"**
- Run: `gradle wrapper` to regenerate wrapper files

### ADB Issues

**"Device not found"**
- Ensure Fire TV and computer are on the same network
- Restart ADB: `adb kill-server && adb start-server`
- Re-enable ADB debugging on Fire TV

**"Connection refused"**
- Check if ADB debugging is enabled on Fire TV
- Try restarting Fire TV

**"Multiple devices"**
- Specify device: `adb -s <DEVICE_IP>:5555 install -r app.apk`

### App Issues

**App crashes on launch**
- Check logcat: `adb logcat | grep -i jellyfin`
- Ensure Jellyfin server is accessible

**Genre rows not loading**
- Check network connectivity
- Verify Jellyfin server has content with genres assigned

## Project Structure

Key modified files for Netflix-style view:

```
app/src/main/java/org/jellyfin/androidtv/ui/browsing/
├── BrowseViewFragment.java          # Modified: Netflix view for Movies/TV Shows
├── NetflixGenreRowsHelper.kt        # NEW: Helper to load genre rows
└── EnhancedBrowseFragment.java      # Base fragment (unchanged)

app/src/main/java/org/jellyfin/androidtv/ui/home/
├── HomeFragmentNetflixGenreRows.kt  # NEW: Genre rows component (unused in current version)
└── HomeRowsFragment.kt              # Home screen (unchanged)

app/build.gradle.kts                  # Modified: Changed applicationId
app/src/main/res/values/strings.xml   # Modified: Changed app name
gradle/libs.versions.toml             # Modified: Java version
```

## Updating From Upstream

To pull updates from official Jellyfin repo:

```bash
# Add upstream remote (one time)
git remote add upstream https://github.com/jellyfin/jellyfin-androidtv.git

# Fetch and merge updates
git fetch upstream
git merge upstream/master

# Resolve any conflicts, then rebuild
./gradlew assembleDebug
```

## Version Control & Releasing

### After Making Changes That Work

**IMPORTANT**: After implementing a feature or fix that works correctly, always create a new version:

1. **Test thoroughly** on Fire TV to confirm everything works

2. **Stage and commit your changes**:
   ```bash
   git add .
   git commit -m "v1.x.x: Description of changes"
   ```

3. **Create a version tag**:
   ```bash
   git tag v1.0.0  # Use appropriate version number
   ```

4. **Push to repository**:
   ```bash
   git push origin master
   git push origin --tags
   ```

### Version Numbering

- **Major version** (1.x.x): Breaking changes or major feature additions
- **Minor version** (x.1.x): New features, improvements
- **Patch version** (x.x.1): Bug fixes, small tweaks

### Example Workflow

```bash
# After testing and confirming feature works
git add .
git commit -m "v1.1.0: Added Netflix-style genre browsing for Movies and TV Shows"
git tag v1.1.0
git push origin master
git push origin --tags
```

### Reverting to Previous Version

If something breaks, you can revert to a previous working version:

```bash
# List all tags
git tag -l

# Checkout a specific version
git checkout v1.0.0

# Or reset to a previous version (WARNING: discards changes)
git reset --hard v1.0.0
```

## License

This project is based on Jellyfin Android TV, licensed under GPL-2.0.
See LICENSE file for details.
