package org.jellyfin.androidtv.ui.shared.toolbar

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.TextureView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import timber.log.Timber

/**
 * A small VLC instance for previewing an episode in place.
 *
 * Deliberately not the ExoPlayer behind the card trailer previews. Trailers come from YouTube and
 * are plain H.264, which ExoPlayer handles anywhere; a library episode is whatever was put in it,
 * and playing those is the reason this app carries VLC at all. Using anything else here would show
 * a black rectangle on exactly the files the app exists for.
 *
 * Renders into a TextureView rather than the SurfaceView the full-screen player uses. The preview
 * sits inside a scrolling list, and a SurfaceView would punch its own hole through the window -
 * ignoring the list's bounds and the panel's rounded corners. A TextureView is an ordinary view and
 * behaves itself.
 *
 * One instance, moved between rows as focus moves, released when previewing stops.
 */
object EpisodePreviewPlayer {
	/** Enough buffer for a preview to start without stuttering on a home network. */
	private const val NETWORK_CACHING_MS = 1500

	private var libVlc: LibVLC? = null
	private var player: MediaPlayer? = null

	/** The row currently owning the preview, so a stale callback cannot steal the surface. */
	private var owner: Any? = null

	/** VLC ignores a seek issued before playback begins, so it is applied on the first frame. */
	private var pendingSeekMs = 0L

	fun play(context: Context, token: Any, surface: TextureView, url: String, startPositionMs: Long) {
		stopPlayback()

		owner = token
		pendingSeekMs = startPositionMs

		// Attaching before the view has been laid out hands VLC a window of nothing to draw into,
		// and the panel stays black however well playback is going. Wait for the texture.
		if (surface.isAvailable) {
			start(context, token, surface, url)
		} else {
			surface.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
				override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
					if (owner === token) start(context, token, surface, url)
				}

				override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
					runCatching { player?.vlcVout?.setWindowSize(width, height) }
				}

				override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true

				override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
			}
		}
	}

	private fun start(context: Context, token: Any, surface: TextureView, url: String) {
		runCatching {
			val vlc = libVlc ?: create(context).also { libVlc = it }
			val instance = player ?: MediaPlayer(vlc).also {
				it.setEventListener(::onEvent)
				player = it
			}

			instance.vlcVout.apply {
				setVideoView(surface)
				setWindowSize(surface.width.coerceAtLeast(1), surface.height.coerceAtLeast(1))
				attachViews()
			}

			// A preview that talks over the interface would be hostile while moving along a list
			instance.volume = 0

			val media = Media(vlc, Uri.parse(url)).apply {
				setHWDecoderEnabled(true, false)
				addOption(":network-caching=$NETWORK_CACHING_MS")
				// Subtitles are noise at this size and cost decode work
				addOption(":no-sub-autodetect-file")
			}

			instance.media = media
			media.release()
			instance.play()
		}.onFailure { error ->
			Timber.w(error, "Could not start episode preview")
			stop(token)
		}
	}

	/** Stops the preview if [token] still owns it, leaving a later row's preview alone. */
	fun stop(token: Any) {
		if (owner !== token) return

		owner = null
		stopPlayback()
	}

	/** Frees the decoder and the VLC instance. Call when previewing is finished with entirely. */
	fun release() {
		owner = null
		stopPlayback()

		player?.release()
		player = null
		libVlc?.release()
		libVlc = null
	}

	/**
	 * LibVLC appends its own options to the list it is given, so the list has to be mutable -
	 * handing it an immutable one throws from the constructor and the preview never starts.
	 */
	private fun create(context: Context) = LibVLC(
		context.applicationContext,
		arrayListOf(
			"--quiet",
			"--aout=opensles",
			"--network-caching=$NETWORK_CACHING_MS",
		),
	)

	private fun stopPlayback() {
		pendingSeekMs = 0L

		player?.let { instance ->
			runCatching {
				instance.stop()
				instance.vlcVout.detachViews()
			}.onFailure { Timber.d(it, "Episode preview did not stop cleanly") }
		}
	}

	private fun onEvent(event: MediaPlayer.Event) {
		when (event.type) {
			MediaPlayer.Event.Playing -> {
				val seek = pendingSeekMs
				if (seek <= 0L) return

				pendingSeekMs = 0L
				runCatching { player?.time = seek }
			}

			// Worth a line: a preview that cannot open its file looks identical to one still
			// buffering, and both look like a bug in the panel
			MediaPlayer.Event.EncounteredError -> Timber.w("Episode preview could not play the file")

			else -> Unit
		}
	}
}
