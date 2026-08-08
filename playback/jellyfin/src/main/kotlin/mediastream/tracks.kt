package org.jellyfin.playback.jellyfin.mediastream

import org.jellyfin.playback.core.mediastream.MediaStreamAudioTrack
import org.jellyfin.playback.core.mediastream.MediaStreamContainer
import org.jellyfin.playback.core.mediastream.MediaStreamSubtitleTrack
import org.jellyfin.playback.core.mediastream.MediaStreamVideoTrack
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

fun MediaInfo.getMediaStreamContainer() = MediaStreamContainer(
	format = requireNotNull(mediaSource.container)
)

fun MediaInfo.getTracks() =
	mediaSource.mediaStreams
		.orEmpty()
		.mapNotNull(MediaStream::getMediaStreamTrack)

fun MediaStream.getMediaStreamTrack() = when (type) {
	MediaStreamType.AUDIO -> getAudioTrack(this)
	MediaStreamType.VIDEO -> getVideoTrack(this)
	MediaStreamType.SUBTITLE -> getSubtitleTrack(this)

	// Ignore other track types
	MediaStreamType.EMBEDDED_IMAGE,
	MediaStreamType.DATA,
	MediaStreamType.LYRIC -> null
}

private fun getAudioTrack(stream: MediaStream) = MediaStreamAudioTrack(
	codec = requireNotNull(stream.codec),
	bitrate = stream.bitRate ?: 0,
	channels = stream.channels ?: 1,
	sampleRate = stream.sampleRate ?: 0,
)

private fun getVideoTrack(stream: MediaStream) = MediaStreamVideoTrack(
	codec = requireNotNull(stream.codec),
)

private fun getSubtitleTrack(stream: MediaStream) = MediaStreamSubtitleTrack(
	codec = stream.codec ?: "unknown",
	language = stream.language,
	title = stream.title,
	isExternal = stream.isExternal == true,
	isForced = stream.isForced == true,
	isDefault = stream.isDefault == true,
)
