package org.jellyfin.playback.vlc

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import org.jellyfin.playback.core.backend.BasePlayerBackend
import org.jellyfin.playback.core.mediastream.MediaStream
import org.jellyfin.playback.core.mediastream.MediaStreamSubtitleTrack
import org.jellyfin.playback.core.mediastream.PlayableMediaStream
import org.jellyfin.playback.core.mediastream.mediaStream
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.model.PositionInfo
import org.jellyfin.playback.core.queue.QueueEntry
import org.jellyfin.playback.core.ui.PlayerSubtitleView
import org.jellyfin.playback.vlc.support.VlcPlaySupportReport
import org.jellyfin.playback.core.ui.PlayerSurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * VLC-based player backend with native ASS/SSA subtitle rendering.
 * VLC renders subtitles internally onto the video surface, providing
 * full styling support for anime subtitles.
 */
class VlcPlayerBackend(
	private val context: Context,
	private val options: VlcPlayerOptions,
) : BasePlayerBackend() {

	companion object {
		// Subtitle codecs that benefit from VLC's native rendering
		val NATIVE_SUBTITLE_CODECS = setOf("ass", "ssa")
	}

	private var currentStream: PlayableMediaStream? = null
	private var surfaceView: SurfaceView? = null

	private val libVLC: LibVLC by lazy {
		val vlcOptions = buildList {
			// Hardware acceleration
			add("--android-hw-decoder")
			add("--hw-decoder-entry=mediacodec")
			when (options.hardwareAcceleration) {
				0 -> add("--no-hw-decoder")
				1 -> add("--avcodec-hw=any")
				2 -> add("--avcodec-hw=any")
			}

			// Network caching
			add("--network-caching=${options.networkCachingMs}")
			add("--file-caching=1500")
			add("--live-caching=1500")

			// Subtitle settings
			add("--subsdec-encoding=${options.subtitleEncoding}")
			options.fontDirectory?.let { fontDir ->
				add("--freetype-font=$fontDir")
			}

			// Audio settings
			if (options.enableTimeStretching) {
				add("--audio-time-stretch")
			}

			// Logging
			if (options.enableDebugLogging) {
				add("-vvv")
			} else {
				add("--quiet")
			}

			// Miscellaneous
			add("--aout=opensles")
			add("--audio-resampler=soxr")
		}

		LibVLC(context, vlcOptions)
	}

	private val mediaPlayer: MediaPlayer by lazy {
		MediaPlayer(libVLC).also { player ->
			player.setEventListener(MediaPlayerEventListener())
		}
	}

	private inner class MediaPlayerEventListener : MediaPlayer.EventListener {
		override fun onEvent(event: MediaPlayer.Event) {
			when (event.type) {
				MediaPlayer.Event.Playing -> {
					listener?.onPlayStateChange(PlayState.PLAYING)
				}
				MediaPlayer.Event.Paused -> {
					listener?.onPlayStateChange(PlayState.PAUSED)
				}
				MediaPlayer.Event.Stopped -> {
					listener?.onPlayStateChange(PlayState.STOPPED)
				}
				MediaPlayer.Event.EndReached -> {
					currentStream?.let { stream ->
						listener?.onMediaStreamEnd(stream)
					}
				}
				MediaPlayer.Event.EncounteredError -> {
					Timber.e("VLC playback error")
					listener?.onPlayStateChange(PlayState.ERROR)
				}
				MediaPlayer.Event.Vout -> {
					// Video output count changed - update video size if available
					val voutCount = event.voutCount
					if (voutCount > 0) {
						val videoWidth = mediaPlayer.currentVideoTrack?.width ?: 0
						val videoHeight = mediaPlayer.currentVideoTrack?.height ?: 0
						if (videoWidth > 0 && videoHeight > 0) {
							listener?.onVideoSizeChange(videoWidth, videoHeight)
						}
					}
				}
			}
		}
	}

	override fun supportsStream(stream: MediaStream): VlcPlaySupportReport {
		// VLC can play most media formats
		// Return a positive report with notes about ASS/SSA support
		val hasAssSubtitles = stream.tracks.any { track ->
			track is MediaStreamSubtitleTrack && track.codec.lowercase() in NATIVE_SUBTITLE_CODECS
		}

		return VlcPlaySupportReport(
			canPlay = true,
			notes = if (hasAssSubtitles) {
				listOf("VLC provides native ASS/SSA subtitle rendering")
			} else {
				emptyList()
			}
		)
	}

	override fun setSurfaceView(surfaceView: PlayerSurfaceView?) {
		val newSurface = surfaceView?.surface

		// Detach old surface
		this.surfaceView?.let { oldSurface ->
			mediaPlayer.vlcVout.detachViews()
		}

		this.surfaceView = newSurface

		// Attach new surface
		newSurface?.let { surface ->
			val vlcVout: IVLCVout = mediaPlayer.vlcVout
			vlcVout.setVideoView(surface)
			vlcVout.attachViews()
		}
	}

	override fun setSubtitleView(surfaceView: PlayerSubtitleView?) {
		// VLC renders subtitles internally onto the video surface
		// No external subtitle view needed - this is what enables ASS/SSA styling
	}

	override fun prepareItem(item: QueueEntry) {
		val stream = requireNotNull(item.mediaStream)
		val media = Media(libVLC, Uri.parse(stream.url))

		// Configure media options
		media.setHWDecoderEnabled(options.hardwareAcceleration != 0, options.hardwareAcceleration != 0)

		mediaPlayer.media = media
		media.release()
	}

	override fun playItem(item: QueueEntry) {
		val stream = requireNotNull(item.mediaStream)
		if (currentStream == stream) return

		currentStream = stream
		prepareItem(item)

		Timber.i("VLC playing ${stream.url}")
		mediaPlayer.play()
	}

	override fun play() {
		if (!mediaPlayer.isPlaying) {
			mediaPlayer.play()
		}
	}

	override fun pause() {
		if (mediaPlayer.isPlaying) {
			mediaPlayer.pause()
		}
	}

	override fun stop() {
		mediaPlayer.stop()
		currentStream = null
	}

	override fun seekTo(position: Duration) {
		if (mediaPlayer.isSeekable) {
			mediaPlayer.time = position.inWholeMilliseconds
		} else {
			Timber.w("VLC: Trying to seek but media is not seekable")
		}
	}

	override fun setScrubbing(scrubbing: Boolean) {
		// VLC doesn't have a specific scrubbing mode like ExoPlayer
		// Seeking during scrubbing is handled by seekTo()
	}

	override fun setSpeed(speed: Float) {
		mediaPlayer.rate = speed
	}

	override fun getPositionInfo(): PositionInfo {
		val currentTime = mediaPlayer.time.coerceAtLeast(0)
		val duration = mediaPlayer.length.coerceAtLeast(0)

		return PositionInfo(
			active = currentTime.milliseconds,
			buffer = duration.milliseconds, // VLC doesn't expose buffer position easily
			duration = duration.milliseconds,
		)
	}

	/**
	 * Release VLC resources when the backend is no longer needed.
	 */
	fun release() {
		mediaPlayer.release()
		libVLC.release()
	}
}
