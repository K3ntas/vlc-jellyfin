package org.jellyfin.androidtv.ui.ratings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.ratings.RatingsRepository
import org.jellyfin.androidtv.data.ratings.UserRatingDetail
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.sdk.model.UUID
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Locale

class ReviewDetailActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_USERNAME = "username"
        private const val EXTRA_RATING = "rating"
        private const val EXTRA_CREATED_AT = "created_at"
        private const val EXTRA_REVIEW_TEXT = "review_text"
        private const val EXTRA_HAS_REVIEW = "has_review"
        private const val EXTRA_LIKE_COUNT = "like_count"
        private const val EXTRA_DISLIKE_COUNT = "dislike_count"
        private const val EXTRA_USER_LIKED = "user_liked"
        private const val EXTRA_CURRENT_USER_ID = "current_user_id"

        fun createIntent(
            context: Context,
            itemId: UUID,
            rating: UserRatingDetail,
            currentUserId: String?
        ): Intent {
            return Intent(context, ReviewDetailActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId.toString())
                putExtra(EXTRA_USER_ID, rating.userId)
                putExtra(EXTRA_USERNAME, rating.username)
                putExtra(EXTRA_RATING, rating.rating)
                putExtra(EXTRA_CREATED_AT, rating.createdAt)
                putExtra(EXTRA_REVIEW_TEXT, rating.reviewText)
                putExtra(EXTRA_HAS_REVIEW, rating.hasReview)
                putExtra(EXTRA_LIKE_COUNT, rating.likeCount)
                putExtra(EXTRA_DISLIKE_COUNT, rating.dislikeCount)
                rating.userLiked?.let { putExtra(EXTRA_USER_LIKED, it) }
                currentUserId?.let { putExtra(EXTRA_CURRENT_USER_ID, it) }
            }
        }
    }

    private val ratingsRepository: RatingsRepository by inject()

    private lateinit var tvUsername: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvReviewText: TextView
    private lateinit var tvNoReview: TextView
    private lateinit var btnLike: LinearLayout
    private lateinit var btnDislike: LinearLayout
    private lateinit var tvLikeCount: TextView
    private lateinit var tvDislikeCount: TextView
    private lateinit var tvLikeIcon: TextView
    private lateinit var tvDislikeIcon: TextView
    private lateinit var tvOwnReviewNotice: TextView
    private lateinit var likeDislikeContainer: LinearLayout

    private var itemId: UUID? = null
    private var reviewerUserId: String = ""
    private var currentUserId: String? = null
    private var hasReview: Boolean = false

    private var likeCount: Int = 0
    private var dislikeCount: Int = 0
    private var userLiked: Boolean? = null
    private var isOwnReview: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_detail)

        initViews()
        loadIntentData()
        setupListeners()
        updateDisplay()
    }

    private fun initViews() {
        tvUsername = findViewById(R.id.tvUsername)
        tvDate = findViewById(R.id.tvDate)
        tvRating = findViewById(R.id.tvRating)
        tvReviewText = findViewById(R.id.tvReviewText)
        tvNoReview = findViewById(R.id.tvNoReview)
        btnLike = findViewById(R.id.btnLike)
        btnDislike = findViewById(R.id.btnDislike)
        tvLikeCount = findViewById(R.id.tvLikeCount)
        tvDislikeCount = findViewById(R.id.tvDislikeCount)
        tvLikeIcon = findViewById(R.id.tvLikeIcon)
        tvDislikeIcon = findViewById(R.id.tvDislikeIcon)
        tvOwnReviewNotice = findViewById(R.id.tvOwnReviewNotice)
        likeDislikeContainer = findViewById(R.id.likeDislikeContainer)
    }

    private fun loadIntentData() {
        val itemIdStr = intent.getStringExtra(EXTRA_ITEM_ID) ?: run {
            finish()
            return
        }
        itemId = UUID.fromString(itemIdStr)
        reviewerUserId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        currentUserId = intent.getStringExtra(EXTRA_CURRENT_USER_ID)

        tvUsername.text = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        tvRating.text = "${intent.getIntExtra(EXTRA_RATING, 0)}/10"
        tvDate.text = formatDate(intent.getStringExtra(EXTRA_CREATED_AT) ?: "")

        hasReview = intent.getBooleanExtra(EXTRA_HAS_REVIEW, false)
        val reviewText = intent.getStringExtra(EXTRA_REVIEW_TEXT)

        if (hasReview && !reviewText.isNullOrBlank()) {
            tvReviewText.text = reviewText
            tvReviewText.visibility = View.VISIBLE
            tvNoReview.visibility = View.GONE
        } else {
            tvReviewText.visibility = View.GONE
            tvNoReview.visibility = View.VISIBLE
        }

        likeCount = intent.getIntExtra(EXTRA_LIKE_COUNT, 0)
        dislikeCount = intent.getIntExtra(EXTRA_DISLIKE_COUNT, 0)
        userLiked = if (intent.hasExtra(EXTRA_USER_LIKED)) {
            intent.getBooleanExtra(EXTRA_USER_LIKED, false)
        } else {
            null
        }

        isOwnReview = currentUserId != null && reviewerUserId == currentUserId
    }

    private fun setupListeners() {
        btnLike.setOnClickListener {
            if (!isOwnReview && hasReview) {
                submitLike(true)
            }
        }

        btnDislike.setOnClickListener {
            if (!isOwnReview && hasReview) {
                submitLike(false)
            }
        }

        // Disable like/dislike for own review or if no review exists
        if (isOwnReview) {
            btnLike.alpha = 0.5f
            btnDislike.alpha = 0.5f
            tvOwnReviewNotice.visibility = View.VISIBLE
        } else if (!hasReview) {
            likeDislikeContainer.visibility = View.GONE
        }
    }

    private fun updateDisplay() {
        tvLikeCount.text = likeCount.toString()
        tvDislikeCount.text = dislikeCount.toString()

        // Update button highlighting based on user's vote
        when (userLiked) {
            true -> {
                tvLikeCount.setTextColor(Color.parseColor("#4CAF50"))
                tvDislikeCount.setTextColor(Color.WHITE)
            }
            false -> {
                tvLikeCount.setTextColor(Color.WHITE)
                tvDislikeCount.setTextColor(Color.parseColor("#F44336"))
            }
            null -> {
                tvLikeCount.setTextColor(Color.WHITE)
                tvDislikeCount.setTextColor(Color.WHITE)
            }
        }
    }

    private fun submitLike(isLike: Boolean) {
        btnLike.isEnabled = false
        btnDislike.isEnabled = false

        lifecycleScope.launch {
            val id = itemId ?: return@launch

            val response = ratingsRepository.likeReview(reviewerUserId, id, isLike)

            if (response != null) {
                likeCount = response.likeCount
                dislikeCount = response.dislikeCount
                userLiked = response.userLiked
                updateDisplay()
            } else {
                Utils.showToast(this@ReviewDetailActivity, R.string.msg_like_error)
            }

            btnLike.isEnabled = true
            btnDislike.isEnabled = true
        }
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val cleanDate = isoDate.substringBefore('.').substringBefore('Z')
            val date = inputFormat.parse(cleanDate)
            date?.let { outputFormat.format(it) } ?: isoDate.substringBefore('T')
        } catch (e: Exception) {
            isoDate.substringBefore('T')
        }
    }
}
