package org.jellyfin.playback.core.backend

import androidx.core.view.doOnDetach
import org.jellyfin.playback.core.mediastream.MediaStream
import org.jellyfin.playback.core.mediastream.PlayableMediaStream
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.ui.PlayerSubtitleView
import org.jellyfin.playback.core.ui.PlayerSurfaceView
import timber.log.Timber

/**
 * Service keeping track of the current playback backend and its related surface view.
 */
class BackendService(
	private val backends: List<PlayerBackend> = emptyList(),
	private val backendSelector: BackendSelector = DefaultBackendSelector(),
) {
	private var _backend: PlayerBackend? = null
	val backend get() = _backend

	private var listeners = mutableListOf<PlayerBackendEventListener>()
	private var _surfaceView: PlayerSurfaceView? = null
	private var _subtitleView: PlayerSubtitleView? = null

	/**
	 * Select the best backend for the given stream using the backend selector.
	 */
	fun selectBackendForStream(stream: MediaStream): PlayerBackend {
		val selected = backendSelector.selectBackend(stream, backends)
			?: backends.firstOrNull()
			?: error("No backends available")
		Timber.d("Selected backend ${selected::class.simpleName} for stream")
		return selected
	}

	/**
	 * Switch to a specific backend, stopping the current one if different.
	 */
	fun switchBackend(backend: PlayerBackend) {
		if (_backend == backend) return

		_backend?.stop()
		_backend?.setListener(null)
		_backend?.setSurfaceView(null)
		_backend?.setSubtitleView(null)

		_backend = backend.apply {
			_surfaceView?.let(::setSurfaceView)
			_subtitleView?.let(::setSubtitleView)
			setListener(BackendEventListener())
		}

		Timber.i("Switched to backend: ${backend::class.simpleName}")
	}

	/**
	 * Switch to the best backend for the given stream.
	 */
	fun switchToBackendForStream(stream: MediaStream) {
		val backend = selectBackendForStream(stream)
		switchBackend(backend)
	}

	fun attachSurfaceView(surfaceView: PlayerSurfaceView) {
		// Remove existing surface view
		if (_surfaceView != null) {
			_backend?.setSurfaceView(null)
		}

		// Apply new surface view
		_surfaceView = surfaceView.apply {
			_backend?.setSurfaceView(surfaceView)

			// Automatically detach
			doOnDetach {
				if (surfaceView == _surfaceView) {
					_surfaceView = null
					_backend?.setSurfaceView(null)
				}
			}
		}
	}

	fun attachSubtitleView(subtitleView: PlayerSubtitleView) {
		// Remove existing surface view
		if (_subtitleView != null) {
			_backend?.setSubtitleView(null)
		}

		// Apply new surface view
		_subtitleView = subtitleView.apply {
			_backend?.setSubtitleView(subtitleView)

			// Automatically detach
			doOnDetach {
				if (subtitleView == _subtitleView) {
					_subtitleView = null
					_backend?.setSubtitleView(null)
				}
			}
		}
	}

	fun addListener(listener: PlayerBackendEventListener) {
		listeners.add(listener)
	}

	fun removeListener(listener: PlayerBackendEventListener) {
		listeners.remove(listener)
	}

	inner class BackendEventListener : PlayerBackendEventListener {
		private fun <T> callListeners(
			body: PlayerBackendEventListener.() -> T
		): List<T> = listeners.map { listener -> listener.body() }

		override fun onPlayStateChange(state: PlayState) {
			callListeners { onPlayStateChange(state) }
		}

		override fun onVideoSizeChange(width: Int, height: Int) {
			callListeners { onVideoSizeChange(width, height) }
		}

		override fun onMediaStreamEnd(mediaStream: PlayableMediaStream) {
			callListeners { onMediaStreamEnd(mediaStream) }
		}
	}
}
