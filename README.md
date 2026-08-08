<h1 align="center">VLC Jellyfin</h1>
<h3 align="center">A Netflix-style Android TV client for Jellyfin, with VLC playback</h3>

---

An Android TV client for [Jellyfin](https://jellyfin.org) with **libVLC and its codecs built into
the app**, wrapped in a Netflix-style interface.

## Plays anything, subtitles just work

The reason this exists. The stock client leans on ExoPlayer and the device's own decoders, so
anything unusual falls back to the server transcoding it — and ASS/SSA subtitles, the kind anime
releases use, get burned in or flattened and lose their styling.

This client ships libVLC inside the APK, so playback does not depend on what your TV box happens to
support:

- **Any format plays** — VLC's own decoders handle it, not the box's limited hardware list
- **ASS/SSA subtitles render natively** with full styling: fonts, colours, positioning, animations
- **No subtitle transcoding, ever** — every subtitle format is marked direct play in the device
  profile, so the server sends the original file untouched
- **No burn-in, no re-encoding** — your server stops doing work it should never have needed to do

In practice: point it at an anime library and it plays, with subtitles exactly as the release
intended, on hardware that would otherwise choke or force a transcode.

## The rest of it

- **Genre rows** — libraries are browsed as horizontal rows per genre rather than a flat grid
- **Netflix-style cards** — title overlay on focus, rating badges, and an animated focus frame that
  drifts around the colour wheel so the selected card is always obvious
- **Custom TV search** — a grid keyboard built for a D-pad instead of the system keyboard overlay
- **Community ratings** — inline star rating on detail screens, talking to
  [jellyfin-plugin-ratings](https://github.com/K3ntas/jellyfin-plugin-ratings), the companion
  server-side plugin
- **Tuned for low-power boxes** — browse queries request only what a card draws, genre rows load in
  batches instead of all at once, and backdrops are debounced and downscaled. See
  [CHANGELOG.md](CHANGELOG.md) for the detail

See [CUSTOM_CHANGES.md](CUSTOM_CHANGES.md) for the detail on each feature and
[BUILD_GUIDE.md](BUILD_GUIDE.md) for how to build and deploy.

## Building

Requires JDK 21 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleRelease
```

Build release rather than debug — debug builds run significantly slower on TV hardware.

Release builds are signed with the standard Android debug keystore (`~/.android/debug.keystore`)
so they sideload without any setup. If you have never run Android Studio you will not have that
file, and the APK is left unsigned instead; sign it yourself, or create the keystore with
`keytool -genkey -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey
-keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`.

Then install it with `adb install -r app/build/outputs/apk/release/*.apk`.

## Licence and attribution

Licensed under the **GNU General Public License v2.0** — see [LICENSE](LICENSE).

This project began as a fork of [jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv),
© the Jellyfin project contributors, and remains a derivative work under the same licence. It is an
independent personal project: it is not affiliated with, endorsed by, or supported by the Jellyfin
project, VideoLAN, or Netflix. Please do not report issues with this client to them.
