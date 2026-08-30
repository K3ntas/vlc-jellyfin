package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** One line in the dropdown: either a film, or a series standing in for its episodes. */
data class RecentlyWatchedEntry(
	/** Movie id, or series id when this is a series. */
	val id: UUID,
	val title: String,
	val subtitle: String,
	/** The item whose artwork represents this line - the episode itself for a series. */
	val artworkItem: BaseItemDto,
	val isSeries: Boolean,
	/** What plays when a film is pressed. Null for a series, which expands instead. */
	val playItem: BaseItemDto?,
	val playPositionMs: Long,
)

/** The two episodes offered once a series is expanded. Either may be missing. */
data class SeriesEpisodes(
	val current: EpisodeChoice?,
	val next: EpisodeChoice?,
	val loading: Boolean = false,
)

data class EpisodeChoice(
	val item: BaseItemDto,
	val label: String,
	val detail: String,
	val positionMs: Long,
)

/**
 * Backs the "recently watched" dropdown in the toolbar.
 *
 * Two queries rather than one: Jellyfin offers no single "recently played" list covering both the
 * half-finished and the finished. Resume items carry a playback position but drop out the moment
 * something is completed, while the played filter carries the completed but never the in-progress.
 * Both are needed here, because a finished episode is exactly what makes a next episode exist to
 * offer.
 */
class RecentlyWatchedViewModel(
	private val api: ApiClient,
) : ViewModel() {
	private companion object {
		const val TICKS_PER_MS = 10_000L
		const val CACHE_VALIDITY_MS = 30_000L

		/** Enough to cover whatever anyone is actually part-way through. */
		const val MAX_ENTRIES = 12
	}

	private val _entries = MutableStateFlow<List<RecentlyWatchedEntry>>(emptyList())
	val entries: StateFlow<List<RecentlyWatchedEntry>> = _entries.asStateFlow()

	private val _isLoading = MutableStateFlow(false)
	val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

	private val _error = MutableStateFlow<String?>(null)
	val error: StateFlow<String?> = _error.asStateFlow()

	private val _dropdownVisible = MutableStateFlow(false)
	val dropdownVisible: StateFlow<Boolean> = _dropdownVisible.asStateFlow()

	private val _expandedSeries = MutableStateFlow<UUID?>(null)
	val expandedSeries: StateFlow<UUID?> = _expandedSeries.asStateFlow()

	private val _episodes = MutableStateFlow<Map<UUID, SeriesEpisodes>>(emptyMap())
	val episodes: StateFlow<Map<UUID, SeriesEpisodes>> = _episodes.asStateFlow()

	private var lastFetchTime = 0L

	fun showDropdown() {
		_dropdownVisible.value = true
		if (System.currentTimeMillis() - lastFetchTime > CACHE_VALIDITY_MS) load()
	}

	fun hideDropdown() {
		_dropdownVisible.value = false
		_expandedSeries.value = null
	}

	fun toggleDropdown() {
		if (_dropdownVisible.value) hideDropdown() else showDropdown()
	}

	/** Opens a series, or closes it if it is already the open one. */
	fun toggleSeries(entry: RecentlyWatchedEntry) {
		if (_expandedSeries.value == entry.id) {
			_expandedSeries.value = null
			return
		}

		_expandedSeries.value = entry.id

		// Next-up costs a request per series, so it waits until one is actually opened
		if (_episodes.value[entry.id] == null) loadEpisodes(entry)
	}

	fun load() {
		viewModelScope.launch {
			_isLoading.value = true
			_error.value = null

			try {
				val recent = fetchRecent()
				_entries.value = groupByShow(recent)
				lastFetchTime = System.currentTimeMillis()
				Timber.d("Recently watched: %d entries", _entries.value.size)
			} catch (error: Exception) {
				Timber.e(error, "Failed to load recently watched")
				_error.value = "Failed to load"
			} finally {
				_isLoading.value = false
			}
		}
	}

	private suspend fun fetchRecent(): List<BaseItemDto> = withContext(Dispatchers.IO) {
		val resume = async {
			runCatching {
				api.itemsApi.getResumeItems(
					fields = ItemRepository.cardItemFields,
					imageTypeLimit = 1,
					limit = 40,
					mediaTypes = listOf(MediaType.VIDEO),
					includeItemTypes = listOf(BaseItemKind.EPISODE, BaseItemKind.MOVIE),
				).content.items
			}.getOrDefault(emptyList())
		}

		val played = async {
			runCatching {
				api.itemsApi.getItems(
					GetItemsRequest(
						sortBy = listOf(ItemSortBy.DATE_PLAYED),
						sortOrder = listOf(SortOrder.DESCENDING),
						filters = listOf(ItemFilter.IS_PLAYED),
						includeItemTypes = listOf(BaseItemKind.EPISODE, BaseItemKind.MOVIE),
						recursive = true,
						limit = 60,
						fields = ItemRepository.cardItemFields,
						imageTypeLimit = 1,
						enableTotalRecordCount = false,
					)
				).content.items
			}.getOrDefault(emptyList())
		}

		(resume.await() + played.await())
			.distinctBy { it.id }
			.sortedByDescending { it.userData?.lastPlayedDate }
	}

	/**
	 * Collapses episodes onto their series, keeping the most recently played one as the line's
	 * stand-in. Films pass through as themselves.
	 */
	private fun groupByShow(items: List<BaseItemDto>): List<RecentlyWatchedEntry> {
		val seen = mutableSetOf<UUID>()
		val entries = mutableListOf<RecentlyWatchedEntry>()

		for (item in items) {
			val seriesId = item.seriesId

			if (item.type == BaseItemKind.EPISODE && seriesId != null) {
				if (!seen.add(seriesId)) continue

				entries += RecentlyWatchedEntry(
					id = seriesId,
					title = item.seriesName ?: item.name.orEmpty(),
					subtitle = listOf(episodeCode(item), progressLabel(item))
						.filter { it.isNotEmpty() }
						.joinToString("  ·  "),
					artworkItem = item,
					isSeries = true,
					playItem = null,
					playPositionMs = positionMs(item),
				)
			} else if (item.type == BaseItemKind.MOVIE) {
				if (!seen.add(item.id)) continue

				entries += RecentlyWatchedEntry(
					id = item.id,
					title = item.name.orEmpty(),
					subtitle = progressLabel(item),
					artworkItem = item,
					isSeries = false,
					playItem = item,
					playPositionMs = positionMs(item),
				)
			}

			if (entries.size >= MAX_ENTRIES) break
		}

		return entries
	}

	private fun loadEpisodes(entry: RecentlyWatchedEntry) {
		val current = entry.artworkItem

		// The episode already in hand is shown straight away; only next-up has to be fetched
		_episodes.value += entry.id to SeriesEpisodes(
			current = EpisodeChoice(
				item = current,
				label = labelFor(current),
				detail = progressLabel(current),
				positionMs = positionMs(current),
			),
			next = null,
			loading = true,
		)

		viewModelScope.launch {
			val next = withContext(Dispatchers.IO) { findNextEpisode(entry.id, current) }

			val existing = _episodes.value[entry.id]

			_episodes.value += entry.id to SeriesEpisodes(
				current = existing?.current,
				next = next?.let {
					EpisodeChoice(
						item = it,
						label = labelFor(it),
						detail = if (positionMs(it) > 0) progressLabel(it) else "Next episode",
						positionMs = positionMs(it),
					)
				},
				loading = false,
			)
		}
	}

	/**
	 * Finds the episode that follows [current].
	 *
	 * Not next-up, which answers a different question: it returns the episode a series is *on*,
	 * so while an episode is part-watched it hands back that same episode - the line already
	 * shown - and the list would claim nothing follows. Walking the season from the current
	 * episode gives the real successor whether or not the current one has been finished.
	 * Next-up is still the fallback, since it copes with a season boundary.
	 */
	private suspend fun findNextEpisode(seriesId: UUID, current: BaseItemDto): BaseItemDto? {
		val following = runCatching {
			api.tvShowsApi.getEpisodes(
				seriesId = seriesId,
				startItemId = current.id,
				limit = 2,
				fields = ItemRepository.cardItemFields,
				imageTypeLimit = 1,
			).content.items
		}.getOrDefault(emptyList())
			.firstOrNull { it.id != current.id }

		if (following != null) return following

		// End of the season, or episodes the server cannot order because the files carry no
		// numbering. Next-up can still cross into the following season.
		return runCatching {
			api.tvShowsApi.getNextUp(
				seriesId = seriesId,
				limit = 2,
				fields = ItemRepository.cardItemFields,
				imageTypeLimit = 1,
			).content.items
		}.getOrDefault(emptyList())
			.firstOrNull { it.id != current.id }
	}

	private fun labelFor(item: BaseItemDto): String = listOf(episodeCode(item), item.name.orEmpty())
		.filter { it.isNotEmpty() }
		.joinToString("  ")

	private fun episodeCode(item: BaseItemDto): String {
		val season = item.parentIndexNumber
		val episode = item.indexNumber

		return when {
			season != null && episode != null -> "S%02dE%02d".format(season, episode)
			episode != null -> "E%02d".format(episode)
			else -> ""
		}
	}

	/** "Resume 23:14" while part-way through, otherwise how long ago it was finished. */
	private fun progressLabel(item: BaseItemDto): String {
		val position = positionMs(item)
		if (position > 0) return "Resume ${formatDuration(position)}"

		val watched = item.userData?.played == true
		val ago = timeAgo(item.userData?.lastPlayedDate)

		return when {
			watched && ago.isNotEmpty() -> "Watched $ago"
			watched -> "Watched"
			else -> ago
		}
	}

	private fun positionMs(item: BaseItemDto): Long =
		(item.userData?.playbackPositionTicks ?: 0L) / TICKS_PER_MS

	private fun formatDuration(millis: Long): String {
		val totalSeconds = millis / 1000
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
		else "%d:%02d".format(minutes, seconds)
	}

	private fun timeAgo(dateTime: LocalDateTime?): String {
		if (dateTime == null) return ""

		val now = LocalDateTime.now()
		val minutes = ChronoUnit.MINUTES.between(dateTime, now)
		val hours = ChronoUnit.HOURS.between(dateTime, now)
		val days = ChronoUnit.DAYS.between(dateTime, now)

		return when {
			minutes < 1 -> "just now"
			minutes < 60 -> "${minutes}m ago"
			hours < 24 -> "${hours}h ago"
			else -> "${days}d ago"
		}
	}
}
