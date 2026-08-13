package org.jellyfin.androidtv.ui.card

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.view.TextureView
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

/**
 * A single player shared by every card on screen.
 *
 * Browsing is the most performance-sensitive thing this app does, and a player per card would
 * undo that outright: each instance allocates codecs and a render thread. One instance is created
 * lazily on the first preview, moved between cards as focus changes, and released when browsing
 * stops.
 */
object TrailerPreviewPlayer {
	/** Same length as the card's video fade, so sound and picture come up together. */
	private const val AUDIO_FADE_MS = 400L

	private var player: ExoPlayer? = null

	/** The card currently showing a preview, so a late callback cannot steal the surface. */
	private var owner: Any? = null

	private var surface: TextureView? = null
	private var onStarted: (() -> Unit)? = null

	/** Kept so the crop can be recomputed when the card resizes around the video. */
	private var lastVideoSize: VideoSize? = null

	/**
	 * The card grows to widescreen once a preview starts, which leaves the transform calculated
	 * for the old bounds badly wrong. Recomputing on every layout pass keeps the picture correct
	 * throughout the animation instead of only at its end.
	 */
	private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
		lastVideoSize?.let(::applyCenterCrop)
	}

	/**
	 * One listener for the life of the player. Registering per playback would leave a callback
	 * behind on every card the user passes, so the whole stack would fire again on the next one.
	 */
	private var volumeFade: ValueAnimator? = null

	private val listener = object : Player.Listener {
		override fun onPlaybackStateChanged(state: Int) {
			if (state != Player.STATE_READY) return

			onStarted?.invoke()
			fadeInAudio()
		}

		override fun onVideoSizeChanged(videoSize: VideoSize) {
			lastVideoSize = videoSize
			applyCenterCrop(videoSize)
		}

		override fun onPlayerError(error: PlaybackException) {
			// Extracted urls are signed and can expire or be refused mid-playback. Dropping the
			// preview is the right outcome; the artwork is still underneath.
			Timber.d(error, "Trailer preview failed")
			owner?.let(::stop)
		}
	}

	/**
	 * Starts [url] on [surface], taking ownership away from whichever card had it.
	 *
	 * [token] identifies the requesting card: the dwell timer resolves asynchronously, so a
	 * request that lands after focus has already moved on must be ignored rather than played.
	 */
	fun play(context: Context, token: Any, surface: TextureView, url: String, onStarted: () -> Unit) {
		owner = token
		this.surface?.removeOnLayoutChangeListener(layoutListener)
		this.surface = surface.also { it.addOnLayoutChangeListener(layoutListener) }
		this.onStarted = { if (owner === token) onStarted() }
		lastVideoSize = null

		val instance = player ?: ExoPlayer.Builder(context.applicationContext).build().also {
			it.repeatMode = Player.REPEAT_MODE_ONE
			it.addListener(listener)
			player = it
		}

		// Silent until the first frame is ready, then faded up in step with the video. Arriving at
		// full volume the instant a card decides to play is startling while browsing.
		volumeFade?.cancel()
		instance.volume = 0f

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

	/**
	 * How far through the trailer [token]'s preview is, 0 to 1, or 0 when it does not own the
	 * player or the duration is not yet known.
	 */
	fun progressOf(token: Any): Float {
		if (owner !== token) return 0f

		val instance = player ?: return 0f
		val duration = instance.duration
		if (duration <= 0L) return 0f

		return (instance.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
	}

	/** Matches the video's fade so picture and sound arrive together. */
	private fun fadeInAudio() {
		volumeFade?.cancel()

		volumeFade = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = AUDIO_FADE_MS
			addUpdateListener { animation -> player?.volume = animation.animatedValue as Float }
			start()
		}
	}

	/** Stops the preview if [token] still owns it, leaving a later card's preview alone. */
	fun stop(token: Any) {
		if (owner !== token) return

		owner = null
		surface?.removeOnLayoutChangeListener(layoutListener)
		surface = null
		onStarted = null
		lastVideoSize = null

		volumeFade?.cancel()
		volumeFade = null

		player?.run {
			stop()
			clearMediaItems()
			clearVideoSurface()
		}
	}

	/** Frees the codecs. Call when leaving a browsing screen entirely. */
	fun release() {
		owner = null
		surface?.removeOnLayoutChangeListener(layoutListener)
		surface = null
		onStarted = null
		lastVideoSize = null

		volumeFade?.cancel()
		volumeFade = null

		player?.run {
			removeListener(listener)
			release()
		}
		player = null
	}

	/**
	 * Fills the card with the video rather than stretching it to fit.
	 *
	 * A TextureView has no scale type - it simply distorts its surface to whatever bounds it is
	 * given, and trailers are widescreen while most cards are portrait posters. Scaling up the
	 * overflowing axis and letting it spill past the clip keeps faces the right shape, which
	 * matters far more at this size than seeing the full frame.
	 */
	private fun applyCenterCrop(videoSize: VideoSize) {
		val view = surface ?: return

		val viewWidth = view.width.toFloat()
		val viewHeight = view.height.toFloat()
		if (viewWidth <= 0f || viewHeight <= 0f) return
		if (videoSize.width == 0 || videoSize.height == 0) return

		val videoAspect =
			videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
		val viewAspect = viewWidth / viewHeight

		val matrix = Matrix()
		if (videoAspect > viewAspect) {
			matrix.setScale(videoAspect / viewAspect, 1f, viewWidth / 2f, viewHeight / 2f)
		} else {
			matrix.setScale(1f, viewAspect / videoAspect, viewWidth / 2f, viewHeight / 2f)
		}

		view.setTransform(matrix)
	}
}
