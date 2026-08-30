package org.jellyfin.androidtv.ui.card

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.trailer.YouTubeStreamResolver
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Decides when a focused card should start previewing a trailer, and where that trailer comes
 * from.
 *
 * Resolution is deliberately deferred until the dwell timer fires: browse rows use the reduced
 * [org.jellyfin.androidtv.data.repository.ItemRepository.cardItemFields], which does not carry
 * trailer counts, and asking for every row would undo that saving. One focused card after ten
 * seconds is a single request instead.
 *
 * Only one preview may run at a time - the previous request is cancelled as soon as focus moves,
 * so scrolling a row never queues work.
 */
class TrailerPreviewController @JvmOverloads constructor(
	private val api: ApiClient,
	private val youTubeStreamResolver: YouTubeStreamResolver,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
	companion object {
		/** Long enough that passing over cards never triggers a request. */
		val DWELL: Duration = 2.seconds

		/**
		 * Items can list dozens of remote trailers, and each extraction is a network round trip.
		 * The first few are the ones that are actually trailers; walking the whole list would just
		 * spend a card's worth of time proving the rest are not playable either.
		 */
		const val MAX_TRAILER_ATTEMPTS = 3
	}

	private var pending: Job? = null

	/**
	 * Call when a card gains focus. [onResolved] receives a playable url once the dwell has
	 * elapsed and a trailer was found; it is never called if focus moves away first.
	 */
	fun onFocused(item: BaseItemDto?, onResolved: (String) -> Unit) {
		cancel()

		val id = item?.id ?: return

		pending = scope.launch {
			delay(DWELL)

			val url = resolve(id, item)
			if (url == null) {
				Timber.d("No trailer available for %s", item.name)
				return@launch
			}

			onResolved(url)
		}
	}

	/** Call when focus leaves the card, or the view detaches. */
	fun cancel() {
		pending?.cancel()
		pending = null
	}

	/**
	 * Prefers a trailer held by the server, which streams like any other item and never breaks.
	 * Falls back to an external trailer url, which needs resolving before it can be played.
	 *
	 * In practice the fallback is the normal path: libraries populated from online metadata carry
	 * youtube links and no local trailer files at all.
	 */
	private suspend fun resolve(id: UUID, item: BaseItemDto): String? = withContext(Dispatchers.IO) {
		// A trailer held by the server streams like any other item and never breaks, so try it first
		val local = runCatching { api.userLibraryApi.getLocalTrailers(id).content }.getOrNull()

		local?.firstOrNull()?.id?.let { trailerId ->
			// queryParameters, not path parameters - the latter drops the query string entirely
			return@withContext api.createUrl(
				"/Videos/$trailerId/stream",
				queryParameters = mapOf(
					"static" to true,
					"api_key" to api.accessToken.orEmpty(),
				),
			)
		}

		// Browse rows are fetched with the reduced card field set, which carries no trailer data,
		// so the row copy of the item cannot answer this. Re-read the full item - one request, for
		// one card, only once the dwell has already elapsed.
		val full = runCatching { api.userLibraryApi.getItem(id).content }.getOrNull() ?: item
		val remote = full.remoteTrailers.orEmpty().mapNotNull { it.url }

		Timber.d(
			"Trailer resolve for %s: local=%d remote=%d",
			item.name,
			local?.size ?: 0,
			remote.size,
		)

		// A youtube watch url is a web page, not a stream, so it has to be extracted before any
		// player can open it. Later entries are tried when one fails - a pulled or region locked
		// video should not cost the card its preview when other trailers are listed.
		remote.filter { youTubeStreamResolver.canResolve(it) }
			.take(MAX_TRAILER_ATTEMPTS)
			.firstNotNullOfOrNull { youTubeStreamResolver.resolve(it) }
	}
}
