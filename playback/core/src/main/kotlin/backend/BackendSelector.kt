package org.jellyfin.playback.core.backend

import org.jellyfin.playback.core.mediastream.MediaStream

/**
 * Interface for selecting the appropriate player backend based on the media stream.
 */
fun interface BackendSelector {
	/**
	 * Select the best backend for the given media stream.
	 *
	 * @param stream The media stream to be played
	 * @param backends The available backends to choose from
	 * @return The selected backend, or null to use the default
	 */
	fun selectBackend(stream: MediaStream, backends: List<PlayerBackend>): PlayerBackend?
}

/**
 * Default backend selector that always returns the first backend.
 */
class DefaultBackendSelector : BackendSelector {
	override fun selectBackend(stream: MediaStream, backends: List<PlayerBackend>): PlayerBackend? {
		return backends.firstOrNull()
	}
}
