package org.jellyfin.androidtv.ui.presentation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.ratings.UserRatingDetail
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Presenter for displaying review cards in a horizontal row.
 */
class ReviewCardPresenter(
    private val onReviewClick: ((UserRatingDetail) -> Unit)? = null,
    private val onUserClick: ((String, String) -> Unit)? = null, // userId, username
    private val onLikeClick: ((UserRatingDetail, Boolean) -> Unit)? = null,
    private val onCommentClick: ((UserRatingDetail) -> Unit)? = null,
    private val currentUserId: String? = null
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review_card, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val holder = viewHolder as ReviewViewHolder
        val review = item as? UserRatingDetail ?: return
        holder.bind(review)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Clean up if needed
    }

    inner class ReviewViewHolder(view: View) : ViewHolder(view) {
        private val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        private val tvAvatarInitial: TextView = view.findViewById(R.id.tvAvatarInitial)
        private val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        private val tvRating: TextView = view.findViewById(R.id.tvRating)
        private val tvReviewText: TextView = view.findViewById(R.id.tvReviewText)
        private val tvShowMore: TextView = view.findViewById(R.id.tvShowMore)
        private val btnLike: LinearLayout = view.findViewById(R.id.btnLike)
        private val btnDislike: LinearLayout = view.findViewById(R.id.btnDislike)
        private val btnComments: LinearLayout = view.findViewById(R.id.btnComments)
        private val tvLikeCount: TextView = view.findViewById(R.id.tvLikeCount)
        private val tvDislikeCount: TextView = view.findViewById(R.id.tvDislikeCount)
        private val tvCommentCount: TextView = view.findViewById(R.id.tvCommentCount)

        private var currentReview: UserRatingDetail? = null

        fun bind(review: UserRatingDetail) {
            currentReview = review

            // Avatar initial
            tvAvatarInitial.text = review.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            ivAvatar.visibility = View.GONE
            tvAvatarInitial.visibility = View.VISIBLE

            // Username and date
            tvUsername.text = review.username
            tvDate.text = formatDate(review.createdAt)

            // Rating
            tvRating.text = "${review.rating}/10"

            // Review text
            if (review.hasReview && !review.reviewText.isNullOrBlank()) {
                tvReviewText.text = review.reviewText
                tvReviewText.visibility = View.VISIBLE

                // Show "Show more" if text is long
                tvReviewText.post {
                    if (tvReviewText.lineCount > 4) {
                        tvShowMore.visibility = View.VISIBLE
                    } else {
                        tvShowMore.visibility = View.GONE
                    }
                }
            } else {
                tvReviewText.visibility = View.GONE
                tvShowMore.visibility = View.GONE
            }

            // Like/dislike/comment counts
            tvLikeCount.text = review.likeCount.toString()
            tvDislikeCount.text = review.dislikeCount.toString()
            tvCommentCount.text = review.commentCount.toString()

            // Highlight user's vote
            updateVoteHighlight(review.userLiked)

            // Click listeners
            view.setOnClickListener {
                onReviewClick?.invoke(review)
            }

            tvUsername.setOnClickListener {
                onUserClick?.invoke(review.userId, review.username)
            }

            tvAvatarInitial.setOnClickListener {
                onUserClick?.invoke(review.userId, review.username)
            }

            val isOwnReview = currentUserId != null && review.userId == currentUserId

            btnLike.setOnClickListener {
                if (!isOwnReview) {
                    onLikeClick?.invoke(review, true)
                }
            }

            btnDislike.setOnClickListener {
                if (!isOwnReview) {
                    onLikeClick?.invoke(review, false)
                }
            }

            btnComments.setOnClickListener {
                onCommentClick?.invoke(review)
            }

            // Dim like/dislike for own review
            if (isOwnReview) {
                btnLike.alpha = 0.5f
                btnDislike.alpha = 0.5f
            } else {
                btnLike.alpha = 1.0f
                btnDislike.alpha = 1.0f
            }

            // Focus handling
            view.setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.05f else 1.0f
                v.scaleY = if (hasFocus) 1.05f else 1.0f
            }
        }

        private fun updateVoteHighlight(userLiked: Boolean?) {
            when (userLiked) {
                true -> {
                    tvLikeCount.setTextColor(Color.parseColor("#4CAF50"))
                    tvDislikeCount.setTextColor(Color.parseColor("#888888"))
                }
                false -> {
                    tvLikeCount.setTextColor(Color.parseColor("#888888"))
                    tvDislikeCount.setTextColor(Color.parseColor("#F44336"))
                }
                null -> {
                    tvLikeCount.setTextColor(Color.parseColor("#888888"))
                    tvDislikeCount.setTextColor(Color.parseColor("#888888"))
                }
            }
        }

        private fun formatDate(isoDate: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val cleanDate = isoDate.substringBefore('.').substringBefore('Z')
                val date = inputFormat.parse(cleanDate) ?: return isoDate.substringBefore('T')

                val now = System.currentTimeMillis()
                val diff = now - date.time
                val days = diff / (1000 * 60 * 60 * 24)

                when {
                    days == 0L -> "Today"
                    days == 1L -> "Yesterday"
                    days < 7 -> "${days}d ago"
                    else -> {
                        val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                        outputFormat.format(date)
                    }
                }
            } catch (e: Exception) {
                isoDate.substringBefore('T')
            }
        }
    }
}
