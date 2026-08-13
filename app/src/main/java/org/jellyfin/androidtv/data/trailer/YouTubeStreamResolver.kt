package org.jellyfin.androidtv.data.trailer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import timber.log.Timber
import okhttp3.Request as OkHttpRequest

/**
 * Turns a youtube watch url into a url a player can actually open.
 *
 * Jellyfin stores trailers as `RemoteTrailers` entries pointing at youtube, and a youtube watch
 * page is not a media stream - handing one to a player does nothing at all. Extracting the
 * underlying stream is therefore not an optimisation here, it is the whole feature: a library with
 * no local trailers has no previews without it.
 *
 * Streams are picked for a muted thumbnail rather than for quality. The lowest resolution on offer
 * is both the fastest to start and the kindest to a card a few hundred pixels wide.
 */
class YouTubeStreamResolver(
	private val okHttpClient: OkHttpClient,
) {
	private companion object {
		/**
		 * Extracted urls are signed and time limited. Well under youtube's own expiry, so a cached
		 * url is never handed out after it has gone stale.
		 */
		const val CACHE_TTL_MS = 30 * 60 * 1000L

		/** Enough to cover a row the user is scrubbing back and forth over. */
		const val CACHE_SIZE = 32

		/**
		 * Youtube varies what it serves by client. A desktop browser agent is the case the
		 * extractor is tested hardest against.
		 */
		const val USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
	}

	private class Cached(val url: String, val expiresAt: Long)

	private val cache = object : LinkedHashMap<String, Cached>(0, 0.75f, true) {
		override fun removeEldestEntry(eldest: Map.Entry<String, Cached>) = size > CACHE_SIZE
	}

	@Volatile
	private var initialised = false

	/** True for urls this resolver stands a chance with, so callers can skip the work otherwise. */
	fun canResolve(url: String): Boolean = runCatching {
		ServiceList.YouTube.streamLHFactory.onAcceptUrl(url)
	}.getOrDefault(false)

	/**
	 * Returns a directly playable stream url for [watchUrl], or null when extraction fails.
	 *
	 * Failure is expected rather than exceptional - youtube changes its player regularly, videos
	 * get region locked or pulled, and any of that should quietly mean "no preview" instead of
	 * disturbing browsing.
	 */
	suspend fun resolve(watchUrl: String): String? = withContext(Dispatchers.IO) {
		cached(watchUrl)?.let { return@withContext it }

		ensureInitialised()

		val info = runCatching { StreamInfo.getInfo(ServiceList.YouTube, watchUrl) }
			.onFailure { Timber.d(it, "Could not extract trailer stream from %s", watchUrl) }
			.getOrNull() ?: return@withContext null

		val stream = pickStream(info)
		if (stream == null) {
			Timber.d("No usable stream for %s", watchUrl)
			return@withContext null
		}

		Timber.d("Resolved trailer %s to %s (%s)", watchUrl, stream.resolution, stream.format?.name)

		stream.content?.also { url ->
			synchronized(cache) {
				cache[watchUrl] = Cached(url, System.currentTimeMillis() + CACHE_TTL_MS)
			}
		}
	}

	private fun cached(watchUrl: String): String? = synchronized(cache) {
		val entry = cache[watchUrl] ?: return null

		if (entry.expiresAt <= System.currentTimeMillis()) {
			cache.remove(watchUrl)
			return null
		}

		entry.url
	}

	/**
	 * Muxed streams first: they are a single file a player opens with no assembly, which is all a
	 * silent preview needs. Youtube offers few of them, so video-only is kept as a fallback - and
	 * for a muted preview dropping the audio track costs nothing anyway.
	 */
	private fun pickStream(info: StreamInfo): VideoStream? {
		val muxed = info.videoStreams.orEmpty().filter { it.content?.isNotEmpty() == true }
		val videoOnly = info.videoOnlyStreams.orEmpty().filter { it.content?.isNotEmpty() == true }

		return muxed.minByOrNull { it.height() } ?: videoOnly.minByOrNull { it.height() }
	}

	/** "360p", "1080p60" and friends, as a number that can be compared. */
	private fun VideoStream.height(): Int =
		resolution?.takeWhile { it.isDigit() }?.toIntOrNull() ?: Int.MAX_VALUE

	/**
	 * The extractor holds its downloader in a static, so this runs once for the process.
	 */
	private fun ensureInitialised() {
		if (initialised) return

		synchronized(this) {
			if (initialised) return

			NewPipe.init(OkHttpDownloader(okHttpClient, USER_AGENT))
			initialised = true
		}
	}

	/**
	 * Bridges the extractor onto the app's shared http client, so trailer lookups reuse the
	 * existing connection pool rather than standing up a second one.
	 */
	private class OkHttpDownloader(
		private val client: OkHttpClient,
		private val userAgent: String,
	) : Downloader() {
		override fun execute(request: Request): Response {
			val body = request.dataToSend()?.toRequestBody()

			val builder = OkHttpRequest.Builder()
				.method(request.httpMethod(), body)
				.url(request.url())
				.addHeader("User-Agent", userAgent)

			request.headers().forEach { (name, values) ->
				// An empty value list is how the extractor asks for a header to be dropped
				builder.removeHeader(name)
				values.forEach { value -> builder.addHeader(name, value) }
			}

			val response = client.newCall(builder.build()).execute()

			// Youtube answers a rate limited client with a captcha page rather than the video
			if (response.code == 429) {
				response.close()
				throw ReCaptchaException("reCaptcha challenge requested", request.url())
			}

			return response.use {
				Response(
					it.code,
					it.message,
					it.headers.toMultimap(),
					it.body?.string(),
					it.request.url.toString(),
				)
			}
		}
	}
}
