package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.androidtv.util.apiclient.EmptyResponse
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Number of genre rows fetched at the same time.
 *
 * Every row is a RANDOM-sorted query, which the server cannot answer from an index, so firing all
 * of them at once both saturates the server and leaves the UI thread parsing responses back to
 * back while the user is trying to move around.
 */
private const val GENRE_ROW_LOAD_CONCURRENCY = 4

/**
 * Runs [ItemRowAdapter.Retrieve] and suspends until the adapter reports it has finished, so
 * callers can limit how many rows load at once.
 */
private suspend fun ItemRowAdapter.retrieveAndAwait(lifecycle: Lifecycle) =
    suspendCancellableCoroutine { continuation ->
        setRetrieveFinishedListener(object : EmptyResponse(lifecycle) {
            // The adapter also notifies on later paging retrievals, so only the first one counts.
            override fun onResponse() {
                if (!continuation.isCompleted) continuation.resume(Unit)
            }

            override fun onError(exception: Exception) {
                if (!continuation.isCompleted) continuation.resume(Unit)
            }
        })

        Retrieve()
    }

/**
 * Netflix-style browse fragment that displays library content organized by genre.
 * Extends RowsSupportFragment directly like HomeRowsFragment for reliable row loading.
 */
class NetflixBrowseFragment : RowsSupportFragment(), View.OnKeyListener {

    private val api by inject<ApiClient>()
    private val backgroundService by inject<BackgroundService>()
    private val itemLauncher by inject<ItemLauncher>()
    private val keyProcessor by inject<KeyProcessor>()

    private var mFolder: BaseItemDto? = null
    private var itemType: BaseItemKind = BaseItemKind.MOVIE
    private var currentItem: BaseRowItem? = null
    private var currentRow: ListRow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse folder from arguments
        arguments?.getString(Extras.Folder)?.let { folderJson ->
            mFolder = Json.decodeFromString(BaseItemDto.serializer(), folderJson)
        }

        // Determine item type based on collection type
        itemType = when (mFolder?.collectionType) {
            CollectionType.MOVIES -> BaseItemKind.MOVIE
            CollectionType.TVSHOWS -> BaseItemKind.SERIES
            else -> BaseItemKind.MOVIE
        }

        // Set up adapter - exactly like HomeRowsFragment
        adapter = MutableObjectAdapter<Row>(PositionableListRowPresenter())

        // Load genre rows asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            loadNetflixGenreRows()
        }

        // Set up click listener
        onItemViewClickedListener = OnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item !is BaseRowItem) return@OnItemViewClickedListener
            if (row !is ListRow) return@OnItemViewClickedListener
            @Suppress("UNCHECKED_CAST")
            itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())
        }

        // Set up selection listener
        onItemViewSelectedListener = OnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item !is BaseRowItem) {
                currentItem = null
                backgroundService.clearBackgrounds()
            } else {
                currentItem = item
                currentRow = row as? ListRow

                val itemRowAdapter = (row as? ListRow)?.adapter as? ItemRowAdapter
                itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

                backgroundService.setBackground(item.baseItem)
            }
        }
    }

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action != KeyEvent.ACTION_UP) return false
        return keyProcessor.handleKey(keyCode, currentItem, activity)
    }

    private suspend fun loadNetflixGenreRows() {
        val folder = mFolder ?: return
        val parentId = folder.id

        try {
            val includeTypes = setOf(itemType)

            // Fetch all genres for this library
            val genresResponse = api.genresApi.getGenres(
                parentId = parentId,
                sortBy = setOf(ItemSortBy.SORT_NAME),
                includeItemTypes = includeTypes
            ).content

            val genres = genresResponse.items
            Timber.d("Netflix view: Loaded ${genres.size} genres for ${folder.name}")

            // Limit to top 25 genres for performance
            val genresToShow = genres.take(25)

            // Add rows on main thread - exactly like HomeRowsFragment
            withContext(Dispatchers.Main) {
                // Use CardPresenter(false) to hide info area (no ratings, clean cards)
                val cardPresenter = CardPresenter(false)
                val rowsAdapter = adapter as MutableObjectAdapter<Row>

                // Put every row in place first so the library is navigable while it fills in
                val rowAdapters = genresToShow.mapNotNull { genre ->
                    val genreName = genre.name ?: return@mapNotNull null

                    // Create query for items in this genre
                    val itemsRequest = GetItemsRequest(
                        parentId = parentId,
                        sortBy = setOf(ItemSortBy.RANDOM),
                        sortOrder = setOf(SortOrder.DESCENDING),
                        includeItemTypes = includeTypes,
                        genres = setOf(genreName),
                        recursive = true,
                        fields = ItemRepository.cardItemFields,
                        limit = 50,
                        imageTypeLimit = 1
                    )

                    val browseRowDef = BrowseRowDef(genreName, itemsRequest, 50)
                    val header = HeaderItem(genreName)

                    val rowAdapter = ItemRowAdapter(
                        requireContext(),
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
                    rowsAdapter.add(row)
                    rowAdapter
                }

                Timber.d("Netflix view: Added ${rowAdapters.size} genre rows")

                // Then fill them a few at a time, top row first
                val loadSlots = Semaphore(GENRE_ROW_LOAD_CONCURRENCY)
                for (rowAdapter in rowAdapters) {
                    launch { loadSlots.withPermit { rowAdapter.retrieveAndAwait(lifecycle) } }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Netflix view: Failed to load genre rows")
        }
    }
}
