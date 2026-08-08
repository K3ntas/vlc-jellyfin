package org.jellyfin.playback.vlc

import android.content.Context
import org.jellyfin.playback.core.plugin.playbackPlugin

/**
 * Creates a VLC playback plugin that provides the VLC player backend.
 *
 * VLC is particularly useful for:
 * - Native ASS/SSA subtitle rendering with full styling support
 * - Anime content with complex subtitle formatting
 * - Wide format compatibility
 *
 * @param androidContext The Android application context
 * @param vlcPlayerOptions Configuration options for VLC
 */
fun vlcPlayerPlugin(
	androidContext: Context,
	vlcPlayerOptions: VlcPlayerOptions = VlcPlayerOptions(),
) = playbackPlugin {
	provide(VlcPlayerBackend(androidContext, vlcPlayerOptions))
}
