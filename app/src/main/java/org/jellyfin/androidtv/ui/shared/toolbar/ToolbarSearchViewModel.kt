package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.search.SearchRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration.Companion.milliseconds

class ToolbarSearchViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {
    companion object {
        private val DEBOUNCE_DURATION = 300.milliseconds
        private val SEARCH_ITEM_TYPES = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
        private const val MAX_RESULTS = 20
    }

    private var searchJob: Job? = null

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val results = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _overlayVisible = MutableStateFlow(false)
    val overlayVisible = _overlayVisible.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery

        if (newQuery.isBlank()) {
            _results.value = emptyList()
            searchJob?.cancel()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_DURATION)
            _isLoading.value = true

            val result = searchRepository.search(
                searchTerm = newQuery.trim(),
                itemTypes = SEARCH_ITEM_TYPES,
            )

            _results.value = result.getOrNull()
                ?.take(MAX_RESULTS)
                .orEmpty()
            _isLoading.value = false
        }
    }

    fun clearSearch() {
        _query.value = ""
        _results.value = emptyList()
        searchJob?.cancel()
    }

    fun showOverlay() {
        _overlayVisible.value = true
    }

    fun hideOverlay() {
        _overlayVisible.value = false
        clearSearch()
    }

    fun onItemSelected() {
        hideOverlay()
    }
}
