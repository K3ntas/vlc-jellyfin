# Changelog

Everything here is a change relative to upstream
[jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv).

## v1.5.0 — Performance and card focus

The app was noticeably sluggish on low-power boxes: slow to load media, and slow to move through
menus while loading. This release is mostly about that. Measured on a Xiaomi TV Box S (3rd Gen)
after the changes: 3.1% janky frames, GPU time flat at 11–15ms across all percentiles, zero slow
bitmap uploads.

**Backdrop loading was the single biggest cost.** `BackgroundService` runs on every card selection,
and it was doing three expensive things at once: requesting backdrops at their *original*
resolution, decoding *every* backdrop an item has when only one is ever shown, and doing this with
no debounce — so moving along a row started a fresh full-size decode for every card passed over.
Backdrops are now debounced by 350ms, downscaled server-side to half the screen dimensions, and the
first one is displayed before the rest are decoded. The downscale costs nothing visually because
these images are blurred behind the UI.

**Browse queries no longer request data that cards never draw.** The shared field set pulled
`CHAPTERS`, `MEDIA_STREAMS` and `TRICKPLAY` for every item in every row — a single movie can carry
hundreds of chapter entries and one stream entry per audio and subtitle track. Row queries now use
a reduced `ItemRepository.cardItemFields`. This is safe because every path that reaches the player
re-fetches the full item by id, and card clicks open the detail screen, which re-fetches too.
`MEDIA_SOURCES` is deliberately retained — the add-to-queue path reads it off the row item.

**Genre rows load in batches.** Opening a library built 25 rows and fired all 25 queries
simultaneously, each `RANDOM`-sorted and therefore unindexable server-side. Rows are now added to
the adapter immediately so the library is navigable at once, then filled four at a time from the
top.

**The hourly channel worker** was pulling the same heavy fields to build Android TV home tiles that
use only an image, a title and an overview. It now uses the reduced set.

**Release builds are properly configured.** A signing config was added so release APKs sideload
without setup. Debug builds set `debuggable=true`, which holds back ART optimisations across the
whole app; building release is a large win on its own. Minification stays off deliberately —
`proguard-rules.pro` has never been exercised and the app leans on reflection through Koin,
kotlinx.serialization and leanback, so enabling R8 needs its own testing pass.

**Animated card focus frame.** The focused card now draws a frame that drifts slowly around the
colour wheel, taking 12 seconds per full cycle and starting from a random hue each time a card
takes focus. This replaces relying on leanback's scale alone, which was hard to follow. The frame's
corner radius is derived from the theme's `cardRounding` minus half the stroke width, so its outer
edge lands exactly on the card's clipped outline instead of curving away and leaving the artwork's
corners bare.

## v1.4.0 — User reviews

Review integration with inline cards and social features.

## v1.3.0 — Latest media dropdown

A latest-media dropdown in the main toolbar.

## v1.2.0 — Community ratings

Integration with the Jellyfin Ratings plugin: always-visible interactive stars on movie, series and
episode detail screens, with D-pad navigation, hover preview, click to rate, and click-again to
remove. Shows the community average, total count, and your own rating.

## v1.1.0 — Netflix-style card focus overlay

Replaced the default leanback focus behaviour with a Netflix-style overlay showing the title below
the card on focus, over a dark gradient for readability.

## v1.0.0 — Netflix-style genre browsing and VLC playback

Libraries are browsed as horizontal per-genre rows rather than a flat grid, with focus management
tuned for a TV remote.

libVLC became the playback backend, chosen for native ASS/SSA subtitle rendering with full styling
— fonts, colours, positioning, animations — which matters for anime. All subtitle formats are
marked direct play in the device profile, so the server sends original files and never transcodes
for subtitles.

A custom TV search overlay replaced the system keyboard: a grid QWERTY keyboard built for D-pad
navigation, with the keyboard on the left and live results on the right, debounced at 300ms.
