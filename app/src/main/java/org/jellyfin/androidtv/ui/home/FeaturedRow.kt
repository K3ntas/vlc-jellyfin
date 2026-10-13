package org.jellyfin.androidtv.ui.home

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.ViewFeaturedBannerBinding
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest

/** The featured banner's row: a handful of items with backdrops, shown one at a time. */
class FeaturedRow(val items: List<BaseItemDto>) : Row()

/**
 * Picks what the banner features: random films and shows that have a backdrop to show behind
 * them. Random rather than latest, so the top of home is different on each visit - the latest are
 * already a row of their own further down.
 */
suspend fun loadFeaturedItems(api: ApiClient): List<BaseItemDto> =
	api.itemsApi.getItems(
		GetItemsRequest(
			includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			recursive = true,
			sortBy = setOf(ItemSortBy.RANDOM),
			imageTypes = setOf(ImageType.BACKDROP),
			fields = setOf(ItemFields.OVERVIEW, ItemFields.GENRES),
			enableImageTypes = setOf(ImageType.LOGO, ImageType.BACKDROP, ImageType.PRIMARY),
			imageTypeLimit = 1,
			limit = FEATURED_COUNT,
			enableTotalRecordCount = false,
		)
	).content.items

private const val FEATURED_COUNT = 8

/** Long enough to read the overview, short enough that sitting on it still shows a few. */
private const val FEATURED_ADVANCE_MS = 9_000L

/**
 * Presents the banner. Left and right step through the featured items, OK opens the one shown,
 * and while the banner keeps focus it moves on by itself.
 *
 * The picture behind it is the app's own backdrop, set to the featured item whenever the banner
 * has focus. Nothing here animates per frame; changing item swaps some text and one logo.
 */
class FeaturedRowPresenter(
	private val api: ApiClient,
	private val backgroundService: BackgroundService,
	private val navigationRepository: NavigationRepository,
) : RowPresenter() {
	init {
		headerPresenter = null
		selectEffectEnabled = false
	}

	override fun isUsingDefaultSelectEffect() = false

	override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
		val binding = ViewFeaturedBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(vh, item)
		(vh as ViewHolder).bind((item as FeaturedRow).items)
	}

	override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
		(vh as ViewHolder).unbind()
		super.onUnbindRowViewHolder(vh)
	}

	inner class ViewHolder(private val binding: ViewFeaturedBannerBinding) : RowPresenter.ViewHolder(binding.root) {
		private var items: List<BaseItemDto> = emptyList()
		private var index = 0
		private val handler = Handler(Looper.getMainLooper())
		private val advance = Runnable { show(index + 1) }

		init {
			binding.root.setOnFocusChangeListener { _, hasFocus ->
				if (hasFocus) {
					applyBackground()
					scheduleAdvance()
				} else {
					handler.removeCallbacks(advance)
				}
			}

			binding.root.setOnKeyListener { _, keyCode, event -> onKey(keyCode, event) }
		}

		fun bind(items: List<BaseItemDto>) {
			this.items = items
			show(0)
		}

		fun unbind() {
			handler.removeCallbacks(advance)
		}

		private fun current(): BaseItemDto? = items.getOrNull(index)

		/** Puts the featured item behind the banner; called whenever the banner is selected. */
		fun applyBackground() {
			current()?.let(backgroundService::setBackground)
		}

		private fun onKey(keyCode: Int, event: KeyEvent): Boolean {
			val handled = when (keyCode) {
				KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
				KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> true
				else -> false
			}
			if (!handled || event.action != KeyEvent.ACTION_DOWN) return handled

			when (keyCode) {
				KeyEvent.KEYCODE_DPAD_LEFT -> show(index - 1)
				KeyEvent.KEYCODE_DPAD_RIGHT -> show(index + 1)
				else -> current()?.let { navigationRepository.navigate(Destinations.itemDetails(it.id)) }
			}
			return true
		}

		private fun show(position: Int) {
			if (items.isEmpty()) {
				binding.root.isVisible = false
				return
			}

			index = Math.floorMod(position, items.size)
			val item = items[index]

			val logo = item.itemImages[ImageType.LOGO]?.getUrl(api, maxHeight = 220)
			binding.featuredLogo.isVisible = logo != null
			binding.featuredTitle.isVisible = logo == null
			if (logo != null) binding.featuredLogo.load(logo, null, null, 1.0, 0)
			binding.featuredTitle.text = item.name

			binding.featuredMeta.text = metaLine(item)
			binding.featuredOverview.text = item.overview.orEmpty()
			binding.featuredOverview.isVisible = !item.overview.isNullOrBlank()
			binding.featuredDots.text = items.indices.joinToString(" ") { if (it == index) "●" else "○" }

			// A short fade on the text, so changing item reads as a change rather than a glitch
			binding.featuredContent.alpha = 0.4f
			binding.featuredContent.animate().alpha(1f).setDuration(220L).start()

			if (binding.root.hasFocus()) {
				backgroundService.setBackground(item)
				scheduleAdvance()
			}
		}

		private fun scheduleAdvance() {
			handler.removeCallbacks(advance)
			if (items.size > 1) handler.postDelayed(advance, FEATURED_ADVANCE_MS)
		}

		private fun metaLine(item: BaseItemDto): String = buildList {
			item.productionYear?.let { add(it.toString()) }
			item.communityRating?.let { add("★ %.1f".format(it)) }
			item.officialRating?.let { add(it) }
			item.runTimeTicks?.takeIf { it > 0 }?.let { ticks ->
				val minutes = ticks / 600_000_000L
				add(if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m")
			}
			if (item.type == BaseItemKind.SERIES) add("Series")
			item.genres?.take(3)?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" • ")) }
		}.joinToString("   •   ")
	}
}
