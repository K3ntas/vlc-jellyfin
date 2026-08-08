package org.jellyfin.androidtv.ui.browsing

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

/**
 * Helper class to add Netflix-style genre rows to library browsing views.
 * Fetches genres from the Jellyfin API and creates horizontal scrolling rows for each genre.
 */
object NetflixGenreRowsHelper {

    /**
     * Add genre-based rows to a library browse view.
     *
     * @param context Android context
     * @param scope CoroutineScope for async operations
     * @param api Jellyfin API client
     * @param parentId The library/folder ID to fetch genres for
     * @param itemType The type of items (MOVIE or SERIES)
     * @param cardPresenter CardPresenter for rendering items
     * @param rowsAdapter The adapter to add rows to
     * @param insertPosition Position to insert rows (default: at end)
     */
    fun addGenreRows(
        context: Context,
        scope: CoroutineScope,
        api: ApiClient,
        parentId: UUID?,
        itemType: BaseItemKind,
        cardPresenter: CardPresenter,
        rowsAdapter: MutableObjectAdapter<Row>,
        insertPosition: Int = -1
    ) {
        scope.launch {
            loadGenreRows(context, api, parentId, itemType, cardPresenter, rowsAdapter, insertPosition)
        }
    }

    private suspend fun loadGenreRows(
        context: Context,
        api: ApiClient,
        parentId: UUID?,
        itemType: BaseItemKind,
        cardPresenter: CardPresenter,
        rowsAdapter: MutableObjectAdapter<Row>,
        insertPosition: Int
    ) {
        try {
            val includeTypes = setOf(itemType)

            // Fetch all genres for this library
            val genresResponse = withContext(Dispatchers.IO) {
                api.genresApi.getGenres(
                    parentId = parentId,
                    sortBy = setOf(ItemSortBy.SORT_NAME),
                    includeItemTypes = includeTypes
                ).content
            }

            val genres = genresResponse.items
            Timber.d("Loaded ${genres.size} genres for Netflix view in library $parentId")

            // Limit to top 20 genres for performance
            val genresToShow = genres.take(20)

            withContext(Dispatchers.Main) {
                var currentPosition = if (insertPosition < 0) rowsAdapter.size() else insertPosition

                for (genre in genresToShow) {
                    val genreName = genre.name ?: continue

                    // Create query for items in this genre
                    val itemsRequest = GetItemsRequest(
                        parentId = parentId,
                        sortBy = setOf(ItemSortBy.RANDOM),
                        sortOrder = setOf(SortOrder.DESCENDING),
                        includeItemTypes = includeTypes,
                        genres = setOf(genreName),
                        recursive = true,
                        fields = ItemRepository.itemFields,
                        limit = 50,
                        imageTypeLimit = 1
                    )

                    val browseRowDef = BrowseRowDef(genreName, itemsRequest, 50)
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

                    if (insertPosition < 0) {
                        // Insert before the last row (Views row)
                        val viewsRowIndex = rowsAdapter.size() - 1
                        if (viewsRowIndex > 0) {
                            rowsAdapter.add(viewsRowIndex, row)
                        } else {
                            rowsAdapter.add(row)
                        }
                    } else {
                        rowsAdapter.add(currentPosition, row)
                        currentPosition++
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Netflix genre rows for library")
        }
    }
}
