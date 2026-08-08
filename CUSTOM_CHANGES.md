# VLC Jellyfin - Custom Modifications

This document describes, feature by feature, how VLC Jellyfin differs from the upstream Jellyfin
Android TV app it was derived from. For a release-by-release summary see [CHANGELOG.md](CHANGELOG.md).

---

## 1. Netflix-Style Card Focus Overlay

**Version:** v1.1.0

### Description
Replaced the default card focus behavior with a Netflix-style overlay that displays the title below the card when focused, instead of the default Leanback focus scaling.

### Changes Made
- Modified card view components to show title overlay on focus
- Added smooth focus animations
- Dark gradient overlay for better text readability

### Files Modified
- Card view components in `app/src/main/java/org/jellyfin/androidtv/ui/`

---

## 2. Netflix-Style Genre Browsing

**Version:** v1.0.0

### Description
Implemented Netflix-style horizontal genre rows for browsing content, providing a more familiar streaming service experience.

### Features
- Horizontal scrolling rows organized by genre
- Smooth navigation with D-pad
- Focus management optimized for TV remote

---

## 3. Custom Toolbar Search

### Description
Added a custom search overlay accessible from the main toolbar with a TV-optimized keyboard.

### Features
- **Split-screen layout**: Keyboard on left, results on right
- **Custom TV Keyboard**: Grid-based QWERTY keyboard (like Netflix)
  - No system keyboard popup that covers the screen
  - D-pad navigation between keys
  - Red highlight on focused keys
- **Real-time search**: 300ms debounce, filters to Movies/Series
- **Visual results**: Poster thumbnails with type badges

### New Files Created
```
app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/
├── ToolbarSearchViewModel.kt    # Search state management
├── ToolbarSearchOverlay.kt      # Full-screen search UI
├── ToolbarSearchField.kt        # Search input component
├── ToolbarSearchDropdown.kt     # Results dropdown (legacy)
└── TvKeyboard.kt                # Custom grid keyboard
```

### Files Modified
- `app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/MainToolbar.kt`
- `app/src/main/java/org/jellyfin/androidtv/di/AppModule.kt`

---

## 4. VLC Player Integration

### Description
Integrated libVLC as the video player backend for native subtitle rendering, especially for ASS/SSA subtitles used in anime.

### Why VLC?
- **Native ASS/SSA rendering**: Full styling support (fonts, colors, positioning, animations)
- **No transcoding**: Server sends original files directly
- **Wide format support**: VLC handles virtually all video/audio/subtitle formats

### Features
- VLC used for ALL video playback (enabled by default)
- All subtitle formats supported via direct play (no server transcoding)
- Hardware acceleration support
- Network caching configuration
- Font attachment support infrastructure for embedded fonts

### New Module Created
```
playback/vlc/
├── build.gradle.kts
├── src/main/AndroidManifest.xml
└── src/main/kotlin/
    ├── VlcPlayerBackend.kt      # Main VLC player implementation
    ├── VlcPlayerPlugin.kt       # Plugin registration
    ├── VlcPlayerOptions.kt      # Configuration options
    ├── VlcBackendSelector.kt    # Backend selection logic
    ├── FontAttachmentManager.kt # Font caching for subtitles
    └── support/
        └── VlcPlaySupportReport.kt
```

### Core Module Changes
```
playback/core/src/main/kotlin/
├── backend/
│   ├── BackendSelector.kt       # NEW: Backend selection interface
│   └── BackendService.kt        # Modified: Multi-backend support
├── mediastream/
│   ├── MediaStream.kt           # Added: MediaStreamSubtitleTrack
│   └── MediaStreamService.kt    # Modified: Auto-select backend
├── PlaybackManager.kt           # Modified: Multiple backends support
└── PlaybackManagerBuilder.kt    # Modified: Backend selector config
```

### App Module Changes
- `app/build.gradle.kts` - Added VLC module dependency
- `app/src/main/java/org/jellyfin/androidtv/di/PlaybackModule.kt` - Install VLC plugin
- `app/src/main/java/org/jellyfin/androidtv/preference/UserPreferences.kt` - VLC preference
- `app/src/main/java/org/jellyfin/androidtv/util/profile/deviceProfile.kt` - All subtitles direct play
- `app/src/main/java/org/jellyfin/androidtv/ui/settings/screen/customization/subtitle/SettingsSubtitlesScreen.kt` - VLC toggle

### Other Module Changes
- `playback/jellyfin/src/main/kotlin/mediastream/tracks.kt` - Fixed subtitle track extraction
- `playback/media3/exoplayer/src/main/kotlin/support/mediaStreamToFormat.kt` - Handle subtitle tracks
- `settings.gradle.kts` - Added `:playback:vlc` module
- `gradle/libs.versions.toml` - Added libVLC dependency

### User Setting
**Location:** Settings → Playback → Subtitles → "Use VLC player"

**Default:** Enabled

When enabled:
- VLC is used for all video playback
- All subtitle formats are set to direct play
- No transcoding for any subtitle type

---

## 5. Custom Community Ratings Plugin Integration

### Description
Integrated support for the Jellyfin Ratings Plugin, which provides a custom community rating system (1-10 scale). Users can rate movies, series, and episodes directly from the detail screen using inline interactive stars (like the plugin's web UI).

### Features
- **Always-visible rating stars** on detail screens (Movies, Series, Episodes)
- **Inline interactive stars** - no separate popup or button needed
- **D-pad navigation** between star buttons
- **Hover preview** - shows rating preview as you navigate
- **Click to rate** - press OK/Enter on a star to submit that rating
- **Click same rating to delete** - removes your rating
- **Real-time updates** - shows average rating and total count
- **User rating display** - shows your current rating if you've rated

### How it Works
1. Navigate to any Movie, Series, or Episode detail screen
2. Rating stars are always visible on the left side panel
3. Navigate to stars with D-pad (they highlight on focus)
4. Press OK/Enter on a star to submit that rating
5. Press OK/Enter on your current rating to delete it
6. Updated statistics display immediately

### Visual Guide
- **Gray empty stars (☆)** = No ratings yet
- **Dark gold filled stars (★)** = Community average rating
- **Bright gold filled stars (★)** = Your rating or hover preview
- **Text below stars** = "X.X (N ratings)" or "No ratings yet"
- **"Your rating: N"** = Displayed when you have rated

### API Endpoints Used
- `GET /Ratings/Items/{itemId}/Stats` - Get rating statistics
- `POST /Ratings/Items/{itemId}/Rating?rating=N` - Submit a rating
- `DELETE /Ratings/Items/{itemId}/Rating` - Delete your rating

### New Files Created
```
app/src/main/java/org/jellyfin/androidtv/data/ratings/
├── RatingsModels.kt        # Data classes (RatingStats, UserRating, etc.)
└── RatingsRepository.kt    # API calls to ratings plugin

app/src/main/java/org/jellyfin/androidtv/ui/
└── RatingPopup.kt          # Popup component (kept for compatibility)

app/src/main/res/
├── layout/rating_popup.xml       # Rating popup layout
└── drawable/
    ├── popup_background.xml      # Dark popup background
    └── button_focusable_background.xml  # Star button states
```

### Files Modified
- `app/src/main/java/org/jellyfin/androidtv/di/AppModule.kt` - Register RatingsRepository
- `app/src/main/java/org/jellyfin/androidtv/ui/itemdetail/FullDetailsFragment.java` - Inline rating handling
- `app/src/main/java/org/jellyfin/androidtv/ui/itemdetail/FullDetailsFragmentHelper.kt` - Rating API functions
- `app/src/main/java/org/jellyfin/androidtv/ui/presentation/MyDetailsOverviewRowPresenter.kt` - Interactive star buttons
- `app/src/main/res/layout/view_row_details.xml` - 10 interactive star buttons in layout
- `app/src/main/res/values/strings.xml` - Rating strings

### Requirements
- Jellyfin Ratings Plugin must be installed on the server
- Plugin: [K3ntas/jellyfin-plugin-ratings](https://github.com/K3ntas/jellyfin-plugin-ratings) —
  the companion server-side project this client's rating UI talks to

---

## 6. Development Scripts

### Description
Batch scripts for easy deployment during development.

### Files Created
```
├── deploy-emulator.bat    # Deploy to Android TV emulator
├── deploy-firestick.bat   # Deploy to an Android TV device over adb
└── start-emulator.bat     # Start Android TV emulator
```

---

## Dependencies Added

### gradle/libs.versions.toml
```toml
vlc = "3.6.0"
vlc-android = { module = "org.videolan.android:libvlc-all", version.ref = "vlc" }
```

---

## Build Instructions

```bash
# Build debug APK
./gradlew assembleDebug

# Deploy to Fire Stick
adb connect <DEVICE_IP>:5555
adb install -r app/build/outputs/apk/debug/jellyfin-androidtv-v0.0.0-dev.1-debug.apk

# Or use batch scripts
deploy-firestick.bat
deploy-emulator.bat
```

---

## Architecture Overview

### Playback Backend Selection Flow
```
1. User plays video
2. JellyfinMediaStreamResolver gets stream info from server
3. MediaStreamService receives the stream
4. BackendService.selectBackendForStream() called
5. VlcBackendSelector checks if VLC is enabled
   - If enabled: Returns VlcPlayerBackend
   - If disabled: Returns ExoPlayerBackend (default)
6. Selected backend plays the video
```

### Device Profile Flow (Server Communication)
```
1. App sends DeviceProfile to Jellyfin server
2. Profile includes supported subtitle formats
3. When VLC enabled: All formats marked as "direct play"
4. Server sends original stream without transcoding
5. VLC renders subtitles natively
```
