package org.jellyfin.playback.vlc.support

import org.jellyfin.playback.core.support.PlaySupportReport

/**
 * VLC play support report indicating whether VLC can play the media.
 */
data class VlcPlaySupportReport(
	override val canPlay: Boolean,
	val notes: List<String> = emptyList(),
) : PlaySupportReport
