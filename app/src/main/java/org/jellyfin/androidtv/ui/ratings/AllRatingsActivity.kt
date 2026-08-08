package org.jellyfin.androidtv.ui.ratings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.ratings.RatingsRepository
import org.jellyfin.androidtv.data.ratings.UserRatingDetail
import org.jellyfin.sdk.model.UUID
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Locale

class AllRatingsActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_ITEM_NAME = "item_name"

        fun createIntent(context: Context, itemId: UUID, itemName: String): Intent {
            return Intent(context, AllRatingsActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId.toString())
                putExtra(EXTRA_ITEM_NAME, itemName)
            }
        }
    }

    private val ratingsRepository: RatingsRepository by inject()
    private val sessionRepository: SessionRepository by inject()

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: TextView
    private lateinit var emptyView: TextView
    private lateinit var itemNameView: TextView
    private lateinit var adapter: RatingsAdapter

    private var itemId: UUID? = null
    private var itemName: String = ""
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_ratings)

        val itemIdStr = intent.getStringExtra(EXTRA_ITEM_ID) ?: run {
            finish()
            return
        }
        itemId = UUID.fromString(itemIdStr)
        itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: ""

        // Get current user ID
        currentUserId = sessionRepository.currentSession.value?.userId?.toString()

        recyclerView = findViewById(R.id.rvRatings)
        loadingView = findViewById(R.id.tvLoading)
        emptyView = findViewById(R.id.tvEmptyState)
        itemNameView = findViewById(R.id.tvItemName)

        itemNameView.text = itemName

        setupRecyclerView()
        loadRatings()
    }

    private fun setupRecyclerView() {
        adapter = RatingsAdapter(currentUserId) { rating ->
            openReviewDetail(rating)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadRatings() {
        showLoading()

        lifecycleScope.launch {
            val id = itemId ?: return@launch
            val ratings = ratingsRepository.getDetailedRatings(id)

            if (ratings.isNullOrEmpty()) {
                showEmpty()
            } else {
                showRatings(ratings)
            }
        }
    }

    private fun openReviewDetail(rating: UserRatingDetail) {
        val id = itemId ?: return
        val intent = ReviewDetailActivity.createIntent(
            this,
            id,
            rating,
            currentUserId
        )
        startActivity(intent)
    }

    private fun showLoading() {
        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun showEmpty() {
        loadingView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun showRatings(ratings: List<UserRatingDetail>) {
        loadingView.visibility = View.GONE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        adapter.submitList(ratings)
    }

    override fun onResume() {
        super.onResume()
        loadRatings()
    }

    // RecyclerView Adapter
    inner class RatingsAdapter(
        private val currentUserId: String?,
        private val onItemClick: (UserRatingDetail) -> Unit
    ) : RecyclerView.Adapter<RatingsAdapter.ViewHolder>() {

        private var items: List<UserRatingDetail> = emptyList()

        fun submitList(newItems: List<UserRatingDetail>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_rating, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
            private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
            private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
            private val tvReviewIndicator: TextView = itemView.findViewById(R.id.tvReviewIndicator)
            private val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
            private val tvDislikeCount: TextView = itemView.findViewById(R.id.tvDislikeCount)
            private val tvEditIndicator: TextView = itemView.findViewById(R.id.tvEditIndicator)
            private val likeContainer: View = itemView.findViewById(R.id.likeContainer)

            fun bind(rating: UserRatingDetail) {
                tvUsername.text = rating.username
                tvRating.text = "${rating.rating}/10"
                tvDate.text = formatDate(rating.createdAt)

                // Show review indicator if has review
                tvReviewIndicator.visibility = if (rating.hasReview) View.VISIBLE else View.GONE

                // Always show like/dislike counts
                likeContainer.visibility = View.VISIBLE
                tvLikeCount.text = rating.likeCount.toString()
                tvDislikeCount.text = rating.dislikeCount.toString()

                // Show edit indicator for own rating
                val isOwnRating = currentUserId != null && rating.userId == currentUserId
                tvEditIndicator.visibility = if (isOwnRating) View.VISIBLE else View.GONE

                itemView.setOnClickListener {
                    onItemClick(rating)
                }

                // Focus handling for TV
                itemView.setOnFocusChangeListener { view, hasFocus ->
                    view.alpha = if (hasFocus) 1.0f else 0.8f
                    view.scaleX = if (hasFocus) 1.02f else 1.0f
                    view.scaleY = if (hasFocus) 1.02f else 1.0f
                }
            }

            private fun formatDate(isoDate: String): String {
                return try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    val cleanDate = isoDate.substringBefore('.').substringBefore('Z')
                    val date = inputFormat.parse(cleanDate)
                    date?.let { outputFormat.format(it) } ?: isoDate.substringBefore('T')
                } catch (e: Exception) {
                    isoDate.substringBefore('T')
                }
            }
        }
    }
}
