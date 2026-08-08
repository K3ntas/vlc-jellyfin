package org.jellyfin.androidtv.ui.presentation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.ratings.RatingStats
import org.jellyfin.androidtv.data.ratings.UserRatingDetail
import org.jellyfin.androidtv.ui.DetailRowView
import org.jellyfin.androidtv.ui.itemdetail.MyDetailsOverviewRow
import org.jellyfin.androidtv.util.InfoLayoutHelper
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

class MyDetailsOverviewRowPresenter(
	private val markdownRenderer: MarkdownRenderer,
) : RowPresenter() {
	class ViewHolder(
		private val detailRowView: DetailRowView,
		private val markdownRenderer: MarkdownRenderer,
	) : RowPresenter.ViewHolder(detailRowView) {
		private val binding get() = detailRowView.binding

		// Star button references
		private val starButtons: List<TextView> by lazy {
			listOf(
				binding.star1, binding.star2, binding.star3, binding.star4, binding.star5,
				binding.star6, binding.star7, binding.star8, binding.star9, binding.star10
			)
		}

		private var currentStats: RatingStats? = null
		private var focusedRating = 0
		private var onRatingClickListener: ((rating: Int) -> Unit)? = null
		private var onAllRatingsClickListener: (() -> Unit)? = null
		private var itemId: UUID? = null
		private var itemName: String? = null
		private var starsSetup = false

		fun setItem(row: MyDetailsOverviewRow) {
			setTitle(row.item.name)

			InfoLayoutHelper.addInfoRow(view.context, row.item, row.item.mediaSources?.getOrNull(row.selectedMediaSourceIndex), binding.fdMainInfoRow, false)
			binding.fdGenreRow.text = row.item.genres?.joinToString(" / ")

			binding.infoTitle1.text = row.infoItem1?.label
			binding.infoValue1.text = row.infoItem1?.value

			binding.infoTitle2.text = row.infoItem2?.label
			binding.infoValue2.text = row.infoItem2?.value

			binding.infoTitle3.text = row.infoItem3?.label
			binding.infoValue3.text = row.infoItem3?.value

			binding.mainImage.load(row.imageDrawable, null, null, 1.0, 0)

			setSummary(row.summary)

			if (row.item.type == BaseItemKind.PERSON) {
				binding.fdSummaryText.maxLines = 9
				binding.fdGenreRow.isVisible = false
			}

			binding.fdButtonRow.removeAllViews()
			for (button in row.actions) {
				val parent = button.parent
				if (parent is ViewGroup) parent.removeView(button)

				binding.fdButtonRow.addView(button)
			}

			// Store item ID and show/hide rating section based on item type
			itemId = row.item.id
			val showRating = row.item.type == BaseItemKind.MOVIE ||
				row.item.type == BaseItemKind.SERIES ||
				row.item.type == BaseItemKind.EPISODE

			binding.communityRatingContainer.isVisible = showRating

			if (showRating && !starsSetup) {
				setupStarButtons()
				starsSetup = true
			}
		}

			private fun setupStarButtons() {
			Timber.d("Setting up star buttons")
			starButtons.forEachIndexed { index, button ->
				val rating = index + 1

				// Handle click for touch
				button.setOnClickListener {
					Timber.d("Star $rating clicked via OnClickListener!")
					onRatingClickListener?.invoke(rating)
				}

				// Handle D-pad center/enter for TV remote
				button.setOnKeyListener { _, keyCode, event ->
					if (event.action == android.view.KeyEvent.ACTION_UP &&
						(keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
						 keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
						 keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
						Timber.d("Star $rating clicked via KeyListener!")
						onRatingClickListener?.invoke(rating)
						true
					} else {
						false
					}
				}

				button.setOnFocusChangeListener { _, hasFocus ->
					if (hasFocus) {
						focusedRating = rating
						updateStarDisplay()
					} else if (focusedRating == rating) {
						// Only reset if we're losing focus from this specific button
						// Check if no star is focused after a small delay
						button.post {
							val anyStarFocused = starButtons.any { it.isFocused }
							if (!anyStarFocused) {
								focusedRating = 0
								updateStarDisplay()
							}
						}
					}
				}
			}
		}

		private fun updateStarDisplay() {
			val stats = currentStats
			val userRating = stats?.userRating ?: 0
			val avgRating = stats?.averageRating?.toInt() ?: 0

			starButtons.forEachIndexed { index, button ->
				val rating = index + 1

				val isFilled = when {
					// When hovering, show filled up to hovered rating
					focusedRating > 0 -> rating <= focusedRating
					// When user has rated, show their rating
					userRating > 0 -> rating <= userRating
					// Otherwise show average rating in dimmer color
					else -> rating <= avgRating
				}

				val isUserRated = userRating > 0 && rating <= userRating && focusedRating == 0
				val isAvgDisplay = focusedRating == 0 && userRating == 0 && rating <= avgRating

				button.text = if (isFilled) "★" else "☆"

				val color = when {
					focusedRating > 0 && rating <= focusedRating -> Color.parseColor("#FFD700") // Gold on hover
					isUserRated -> Color.parseColor("#FFD700") // Gold for user rating
					isAvgDisplay -> Color.parseColor("#B8860B") // Dark gold for average
					else -> Color.parseColor("#666666") // Gray for empty
				}
				button.setTextColor(color)
			}
		}

		fun setTitle(title: String?) {
			binding.fdTitle.text = title
		}

		fun setSummary(summary: String?) {
			binding.fdSummaryText.text = summary?.let { markdownRenderer.toMarkdownSpanned(it) }
		}

		fun setInfoValue3(text: String?) {
			binding.infoValue3.text = text
		}

		fun setOnRatingClickListener(listener: ((rating: Int) -> Unit)?) {
			Timber.d("Setting rating click listener: ${listener != null}")
			onRatingClickListener = listener
		}

		fun setOnAllRatingsClickListener(listener: (() -> Unit)?) {
			Timber.d("Setting all ratings click listener: ${listener != null}")
			onAllRatingsClickListener = listener

			// Setup click listener on communityRatingValue
			binding.communityRatingValue.setOnClickListener {
				Timber.d("All ratings clicked!")
				onAllRatingsClickListener?.invoke()
			}

			// Handle D-pad center/enter for TV remote
			binding.communityRatingValue.setOnKeyListener { _, keyCode, event ->
				if (event.action == android.view.KeyEvent.ACTION_UP &&
					(keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
					 keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
					 keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
					Timber.d("All ratings clicked via key!")
					onAllRatingsClickListener?.invoke()
					true
				} else {
					false
				}
			}
		}

		fun getItemId(): UUID? = itemId

		fun getItemName(): String? = itemName

		fun setItemName(name: String?) {
			itemName = name
		}

		fun setCommunityRating(stats: RatingStats?) {
			Timber.d("setCommunityRating called with stats: $stats")
			currentStats = stats
			focusedRating = 0

			// A null result means the ratings plugin answered nothing - either it is not installed
			// on this server or it could not be reached. Either way the stars would be ten dead
			// D-pad targets that silently do nothing, so hide the section entirely. A server that
			// does have the plugin returns stats with totalRatings = 0 instead of null, which is
			// what "No ratings yet" below is for.
			binding.communityRatingContainer.isVisible = stats != null
			if (stats == null) return

			updateStarDisplay()

			// Update the text info - format like plugin: "6.0/10 - 1 rating"
			if (stats != null && stats.totalRatings > 0) {
				val ratingText = if (stats.totalRatings == 1) "rating" else "ratings"
				binding.communityRatingValue.text = "%.1f/10 - %d %s".format(stats.averageRating, stats.totalRatings, ratingText)
			} else {
				binding.communityRatingValue.text = "No ratings yet"
			}

			// User's rating - format like plugin: "Your rating: 6/10 (click to remove)"
			if (stats?.userRating != null) {
				binding.userRatingText.isVisible = true
				binding.userRatingText.text = "Your rating: ${stats.userRating}/10 (click to remove)"
			} else {
				binding.userRatingText.isVisible = false
			}
		}

		private var onReviewClickListener: ((UserRatingDetail) -> Unit)? = null
		private var onReviewLikeListener: ((UserRatingDetail, Boolean) -> Unit)? = null
		private var onReviewCommentListener: ((UserRatingDetail) -> Unit)? = null

		fun setOnReviewClickListener(listener: ((UserRatingDetail) -> Unit)?) {
			onReviewClickListener = listener
		}

		fun setOnReviewLikeListener(listener: ((UserRatingDetail, Boolean) -> Unit)?) {
			onReviewLikeListener = listener
		}

		fun setOnReviewCommentListener(listener: ((UserRatingDetail) -> Unit)?) {
			onReviewCommentListener = listener
		}

		fun setReviews(reviews: List<UserRatingDetail>?) {
			val container = binding.reviewsContainer
			val scrollView = binding.reviewsScrollView
			val titleView = binding.reviewsTitle

			container.removeAllViews()

			if (reviews.isNullOrEmpty()) {
				scrollView.isVisible = false
				titleView.isVisible = false
				return
			}

			// Show title with count
			titleView.text = "User Reviews (${reviews.size})"
			titleView.isVisible = true
			scrollView.isVisible = true
			val inflater = LayoutInflater.from(view.context)

			for (review in reviews.take(5)) { // Limit to 5 reviews
				val cardView = inflater.inflate(R.layout.item_review_card_compact, container, false)

				// Avatar initial
				cardView.findViewById<TextView>(R.id.tvAvatarInitial).text =
					review.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

				// Username
				cardView.findViewById<TextView>(R.id.tvUsername).text = review.username

				// Rating
				cardView.findViewById<TextView>(R.id.tvRating).text = "${review.rating}/10"

				// Review text
				val reviewTextView = cardView.findViewById<TextView>(R.id.tvReviewText)
				if (!review.reviewText.isNullOrBlank()) {
					reviewTextView.text = review.reviewText
					reviewTextView.isVisible = true
				} else {
					reviewTextView.isVisible = false
				}

				// Like/dislike/comment buttons with click handlers
				val likeButton = cardView.findViewById<android.widget.Button>(R.id.tvLikeCount)
				val dislikeButton = cardView.findViewById<android.widget.Button>(R.id.tvDislikeCount)
				val commentButton = cardView.findViewById<android.widget.Button>(R.id.tvCommentCount)

				likeButton.text = "👍 ${review.likeCount}"
				dislikeButton.text = "👎 ${review.dislikeCount}"
				commentButton.text = "💬 ${review.commentCount}"

				// Like button click - Button handles D-pad automatically
				likeButton.setOnClickListener {
					Timber.d("Like clicked for review by ${review.username}")
					onReviewLikeListener?.invoke(review, true)
				}

				// Dislike button click
				dislikeButton.setOnClickListener {
					Timber.d("Dislike clicked for review by ${review.username}")
					onReviewLikeListener?.invoke(review, false)
				}

				// Comment button click
				commentButton.setOnClickListener {
					Timber.d("Comment clicked for review by ${review.username}")
					onReviewCommentListener?.invoke(review)
				}

				// Focus handling for buttons
				val focusListener = View.OnFocusChangeListener { v, hasFocus ->
					v.scaleX = if (hasFocus) 1.1f else 1.0f
					v.scaleY = if (hasFocus) 1.1f else 1.0f
				}
				likeButton.onFocusChangeListener = focusListener
				dislikeButton.onFocusChangeListener = focusListener
				commentButton.onFocusChangeListener = focusListener

				container.addView(cardView)
			}
		}
	}

	var viewHolder: ViewHolder? = null
		private set

	init {
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val view = DetailRowView(parent.context)
		viewHolder = ViewHolder(view, markdownRenderer)
		return viewHolder!!
	}

	override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(viewHolder, item)
		if (item !is MyDetailsOverviewRow) return
		if (viewHolder !is ViewHolder) return

		viewHolder.setItem(item)
	}

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit
}
