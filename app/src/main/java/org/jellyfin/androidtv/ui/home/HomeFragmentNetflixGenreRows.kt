package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Netflix-style home fragment row that displays media organized by genre.
 * Fetches all genres and creates horizontal scrolling rows for each genre.
 */
class HomeFragmentNetflixGenreRows(
    private val lifecycleScope: CoroutineScope,
    private val includeItemTypes: Set<BaseItemKind> = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
) : HomeFragmentRow, KoinComponent {

    private val api by inject<ApiClient>()

    override fun addToRowsAdapter(
        context: Context,
        cardPresenter: CardPresenter,
        rowsAdapter: MutableObjectAdapter<Row>
    ) {
        lifecycleScope.launch {
            loadGenreRows(context, cardPresenter, rowsAdapter)
        }
    }

    private suspend fun loadGenreRows(
        context: Context,
        cardPresenter: CardPresenter,
        rowsAdapter: MutableObjectAdapter<Row>
    ) {
        try {
            // Fetch all genres
            val genresResponse = withContext(Dispatchers.IO) {
                api.genresApi.getGenres(
                    sortBy = setOf(ItemSortBy.SORT_NAME),
                    includeItemTypes = includeItemTypes
                ).content
            }

            val genres = genresResponse.items
            Timber.d("Loaded ${genres.size} genres for Netflix view")

            // Add a row for each genre (limit to top 15 for performance)
            val genresToShow = genres.take(15)

            withContext(Dispatchers.Main) {
                for (genre in genresToShow) {
                    val genreName = genre.name ?: continue

                    // Create query for items in this genre, sorted randomly for variety
                    val itemsRequest = GetItemsRequest(
                        sortBy = setOf(ItemSortBy.RANDOM),
                        sortOrder = setOf(SortOrder.DESCENDING),
                        includeItemTypes = includeItemTypes,
                        genres = setOf(genreName),
                        recursive = true,
                        fields = ItemRepository.itemFields,
                        limit = 30,
                        imageTypeLimit = 1
                    )

                    val browseRowDef = BrowseRowDef(genreName, itemsRequest, 30)
                    val header = HeaderItem(genreName)

                    val rowAdapter = ItemRowAdapter(
                        context,
                        browseRowDef.query,
                        browseRowDef.chunkSize,
                        false,
                        true,
                        cardPresenter,
                        rowsAdapter,
                        browseRowDef.queryType
                    )

                    val row = ListRow(header, rowAdapter)
                    rowAdapter.setRow(row)
                    rowAdapter.Retrieve()
                    rowsAdapter.add(row)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Netflix genre rows")
        }
    }
}
