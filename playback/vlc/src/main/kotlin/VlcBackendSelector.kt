package org.jellyfin.playback.vlc

import org.jellyfin.playback.core.backend.BackendSelector
import org.jellyfin.playback.core.backend.PlayerBackend
import org.jellyfin.playback.core.mediastream.MediaStream
import timber.log.Timber

/**
 * Backend selector that uses VLC for all video content when enabled.
 *
 * VLC provides:
 * - Native ASS/SSA subtitle rendering with full styling
 * - Wide format compatibility
 * - Better subtitle support overall
 *
 * @param enabled Whether VLC should be used for all video content
 */
class VlcBackendSelector(
	private val enabled: Boolean = true,
) : BackendSelector {

	override fun selectBackend(stream: MediaStream, backends: List<PlayerBackend>): PlayerBackend? {
		if (!enabled || backends.isEmpty()) {
			return backends.firstOrNull()
		}

		// When enabled, always use VLC for video content
		val vlcBackend = backends.filterIsInstance<VlcPlayerBackend>().firstOrNull()
		return if (vlcBackend != null) {
			Timber.i("VlcBackendSelector: Using VLC player")
			vlcBackend
		} else {
			Timber.w("VlcBackendSelector: VLC backend not available, using default")
			backends.firstOrNull()
		}
	}
}
