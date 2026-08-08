package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class LatestMediaItem(
    val item: BaseItemDto,
    val displayType: MediaType,
    val timeAgo: String,
)

enum class MediaType {
    MOVIE,
    SERIES,
    ANIME,
    OTHER
}

class LatestMediaViewModel(
    private val api: ApiClient,
) : ViewModel() {

    private val _items = MutableStateFlow<List<LatestMediaItem>>(emptyList())
    val items: StateFlow<List<LatestMediaItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _dropdownVisible = MutableStateFlow(false)
    val dropdownVisible: StateFlow<Boolean> = _dropdownVisible.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var lastFetchTime: Long = 0
    private val cacheValidityMs = 30_000L // 30 seconds cache

    fun showDropdown() {
        _dropdownVisible.value = true
        // Refresh if cache is stale
        if (System.currentTimeMillis() - lastFetchTime > cacheValidityMs) {
            loadLatestMedia()
        }
    }

    fun hideDropdown() {
        _dropdownVisible.value = false
    }

    fun toggleDropdown() {
        if (_dropdownVisible.value) {
            hideDropdown()
        } else {
            showDropdown()
        }
    }

    fun loadLatestMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val request = GetItemsRequest(
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    limit = 50,
                    fields = listOf(
                        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        ItemFields.GENRES,
                        ItemFields.DATE_CREATED
                    ),
                    imageTypeLimit = 1,
                    enableTotalRecordCount = false,
                )

                val response = withContext(Dispatchers.IO) {
                    api.itemsApi.getItems(request).content
                }

                val items = response.items.map { item ->
                    LatestMediaItem(
                        item = item,
                        displayType = determineMediaType(item),
                        timeAgo = formatTimeAgo(item.dateCreated)
                    )
                }

                _items.value = items
                lastFetchTime = System.currentTimeMillis()
                Timber.d("Loaded ${items.size} latest media items")

            } catch (e: Exception) {
                Timber.e(e, "Failed to load latest media")
                _error.value = "Failed to load"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun determineMediaType(item: BaseItemDto): MediaType {
        val genres = item.genres ?: emptyList()
        val isAnime = genres.any {
            it.equals("anime", ignoreCase = true) ||
            it.equals("animation", ignoreCase = true)
        }

        return when (item.type) {
            BaseItemKind.MOVIE -> if (isAnime) MediaType.ANIME else MediaType.MOVIE
            BaseItemKind.SERIES -> if (isAnime) MediaType.ANIME else MediaType.SERIES
            else -> MediaType.OTHER
        }
    }

    private fun formatTimeAgo(dateTime: LocalDateTime?): String {
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

    fun cleanTitle(title: String?): String {
        if (title.isNullOrBlank()) return "Unknown"
        return title
            .replace(Regex("""\s*\[imdbid[-:]?tt\d+\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[tt\d+\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(tt\d+\)""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
