package org.jellyfin.playback.vlc

/**
 * Configuration options for the VLC player backend.
 */
data class VlcPlayerOptions(
	/**
	 * Enable hardware acceleration for video decoding.
	 * Set to -1 for automatic, 0 for disabled, 1 for decoding, 2 for full.
	 */
	val hardwareAcceleration: Int = -1,

	/**
	 * Enable verbose logging from VLC.
	 */
	val enableDebugLogging: Boolean = false,

	/**
	 * Network caching in milliseconds.
	 */
	val networkCachingMs: Int = 1500,

	/**
	 * Directory for font attachments (for ASS/SSA subtitles).
	 */
	val fontDirectory: String? = null,

	/**
	 * Subtitle text encoding.
	 */
	val subtitleEncoding: String = "UTF-8",

	/**
	 * Enable time stretching for audio when changing playback speed.
	 */
	val enableTimeStretching: Boolean = true,
)
