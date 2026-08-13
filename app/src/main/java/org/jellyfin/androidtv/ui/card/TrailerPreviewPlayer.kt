package org.jellyfin.androidtv.ui.card

import android.content.Context
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

/**
 * A single muted player shared by every card on screen.
 *
 * Browsing is the most performance-sensitive thing this app does, and a player per card would
 * undo that outright: each instance allocates codecs and a render thread. One instance is created
 * lazily on the first preview, moved between cards as focus changes, and released when browsing
 * stops.
 */
object TrailerPreviewPlayer {
	private var player: ExoPlayer? = null

	/** The card currently showing a preview, so a late callback cannot steal the surface. */
	private var owner: Any? = null

	/**
	 * Starts [url] on [surface], taking ownership away from whichever card had it.
	 *
	 * [token] identifies the requesting card: the dwell timer resolves asynchronously, so a
	 * request that lands after focus has already moved on must be ignored rather than played.
	 */
	fun play(context: Context, token: Any, surface: TextureView, url: String, onStarted: () -> Unit) {
		owner = token

		val instance = player ?: ExoPlayer.Builder(context.applicationContext).build().also {
			// Previews are silent - a card that starts talking while browsing is hostile
			it.volume = 0f
			it.repeatMode = Player.REPEAT_MODE_ONE
			player = it
		}

		instance.addListener(object : Player.Listener {
			override fun onPlaybackStateChanged(state: Int) {
				if (state == Player.STATE_READY && owner === token) onStarted()
			}
		})

		runCatching {
			instance.setVideoTextureView(surface)
			instance.setMediaItem(MediaItem.fromUri(url))
			instance.prepare()
			instance.play()
		}.onFailure { error ->
			Timber.w(error, "Could not start trailer preview")
			stop(token)
		}
	}

	/** Stops the preview if [token] still owns it, leaving a later card's preview alone. */
	fun stop(token: Any) {
		if (owner !== token) return

		owner = null
		player?.run {
			stop()
			clearMediaItems()
			clearVideoSurface()
		}
	}

	/** Frees the codecs. Call when leaving a browsing screen entirely. */
	fun release() {
		owner = null
		player?.release()
		player = null
	}
}
