package org.jellyfin.androidtv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.ratings.RatingStats
import org.jellyfin.androidtv.data.ratings.RatingsRepository
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.centerGlyphVertically
import org.jellyfin.sdk.model.UUID
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Popup window for rating items using the custom ratings plugin.
 *
 * Shows a row of 10 star buttons that the user can navigate with D-pad.
 */
class RatingPopup(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val anchorView: View,
    private val posLeft: Int,
    private val posTop: Int,
) : KoinComponent {

    private val ratingsRepository: RatingsRepository by inject()

    private val popup: PopupWindow
    private val titleView: TextView
    private val subtitleView: TextView
    private val starsContainer: LinearLayout
    private val loadingView: TextView
    private val reviewLabel: TextView
    private val reviewInput: EditText
    private val charCountView: TextView
    private val submitButton: Button
    private val hintText: TextView
    private val starButtons = mutableListOf<TextView>()

    private var itemId: UUID? = null
    private var currentStats: RatingStats? = null
    private var focusedRating = 0
    private var selectedRating = 0
    private var existingReview: String? = null

    private var onRatingChanged: ((RatingStats?) -> Unit)? = null

    companion object {
        private const val MAX_REVIEW_LENGTH = 2000
    }

    init {
        val layout = LayoutInflater.from(context).inflate(R.layout.rating_popup, null)
        val width = Utils.convertDpToPixel(context, 500)
        val height = Utils.convertDpToPixel(context, 420)

        popup = PopupWindow(layout, width, height).apply {
            isFocusable = true
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        titleView = layout.findViewById(R.id.rating_title)
        subtitleView = layout.findViewById(R.id.rating_subtitle)
        starsContainer = layout.findViewById(R.id.stars_container)
        loadingView = layout.findViewById(R.id.loading_text)
        reviewLabel = layout.findViewById(R.id.review_label)
        reviewInput = layout.findViewById(R.id.review_input)
        charCountView = layout.findViewById(R.id.char_count)
        submitButton = layout.findViewById(R.id.submit_button)
        hintText = layout.findViewById(R.id.hint_text)

        // Create 10 star buttons
        for (i in 1..10) {
            val starButton = createStarButton(i)
            starButtons.add(starButton)
            starsContainer.addView(starButton)
        }

        // Setup review input character counter
        reviewInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCharCount()
            }
        })

        // Setup submit button
        submitButton.setOnClickListener {
            submitRatingWithReview()
        }

        // Handle back key to dismiss
        layout.isFocusableInTouchMode = true
        layout.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss()
                true
            } else {
                false
            }
        }
    }

    private fun updateCharCount() {
        val count = reviewInput.text?.length ?: 0
        charCountView.text = context.getString(R.string.lbl_char_count, count)
        charCountView.setTextColor(
            if (count > MAX_REVIEW_LENGTH) Color.parseColor("#FF5252") else Color.parseColor("#666666")
        )
    }

    private fun showReviewSection() {
        reviewLabel.visibility = View.VISIBLE
        reviewInput.visibility = View.VISIBLE
        charCountView.visibility = View.VISIBLE
        submitButton.visibility = View.VISIBLE
        hintText.visibility = View.GONE
        updateCharCount()

        // Pre-fill existing review if any
        existingReview?.let { reviewInput.setText(it) }
    }

    private fun hideReviewSection() {
        reviewLabel.visibility = View.GONE
        reviewInput.visibility = View.GONE
        charCountView.visibility = View.GONE
        submitButton.visibility = View.GONE
        hintText.visibility = View.VISIBLE
    }

    private fun createStarButton(rating: Int): TextView {
        val size = Utils.convertDpToPixel(context, 40)
        val button = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = Utils.convertDpToPixel(context, 4)
            }
            gravity = Gravity.CENTER
            textSize = 24f
            text = "\u2606" // Empty star
            includeFontPadding = false
            // The star glyph draws low in its line box, which left the focus background looking
            // top-heavy. Measured against the filled star, the taller of the two shapes shown.
            centerGlyphVertically("★")
            setTextColor(Color.parseColor("#666666"))
            isFocusable = true
            isFocusableInTouchMode = true
            background = ContextCompat.getDrawable(context, R.drawable.button_focusable_background)

            setOnClickListener {
                onStarClicked(rating)
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    focusedRating = rating
                    updateStarDisplay()
                }
            }
        }
        return button
    }

    private fun onStarClicked(rating: Int) {
        // If clicking the same rating that user already has, delete it
        if (currentStats?.userRating == rating && selectedRating == 0) {
            deleteRating()
            return
        }

        // Select this rating and show review section
        selectedRating = rating
        updateStarDisplay()
        showReviewSection()

        // Update subtitle to show selected rating
        subtitleView.text = "Rate: $rating/10"
    }

    private fun submitRatingWithReview() {
        if (selectedRating == 0) return

        val id = itemId ?: return
        val reviewText = reviewInput.text?.toString()?.trim()

        timber.log.Timber.i("SUBMIT: rating=$selectedRating, reviewText='$reviewText', reviewLength=${reviewText?.length ?: 0}")

        // Validate review length
        if ((reviewText?.length ?: 0) > MAX_REVIEW_LENGTH) {
            Utils.showToast(context, R.string.msg_review_too_long)
            return
        }

        submitButton.isEnabled = false

        lifecycleOwner.lifecycleScope.launch {
            timber.log.Timber.i("SUBMIT: calling ratingsRepository.submitRating($id, $selectedRating, '$reviewText')")
            val result = ratingsRepository.submitRating(id, selectedRating, reviewText)
            if (result) {
                Utils.showToast(context, R.string.msg_rating_submitted)
                // Refresh stats
                val newStats = ratingsRepository.getRatingStats(id, forceRefresh = true)
                currentStats = newStats
                selectedRating = 0
                focusedRating = 0
                updateStarDisplay()
                hideReviewSection()
                onRatingChanged?.invoke(newStats)
                dismiss()
            } else {
                Utils.showToast(context, R.string.msg_rating_error)
                submitButton.isEnabled = true
            }
        }
    }

    fun setContent(itemId: UUID, itemName: String, preSelectedRating: Int = 0, onRatingChanged: ((RatingStats?) -> Unit)? = null) {
        this.itemId = itemId
        this.onRatingChanged = onRatingChanged
        this.selectedRating = preSelectedRating
        this.existingReview = null
        titleView.text = context.getString(R.string.lbl_rate)
        subtitleView.text = itemName

        loadingView.visibility = View.VISIBLE
        starsContainer.visibility = View.GONE
        hideReviewSection()
        reviewInput.setText("")

        loadRatings()
    }

    private fun loadRatings() {
        val id = itemId ?: return

        lifecycleOwner.lifecycleScope.launch {
            val stats = ratingsRepository.getRatingStats(id)
            currentStats = stats
            loadingView.visibility = View.GONE
            starsContainer.visibility = View.VISIBLE

            // If pre-selected rating is set, use it and show review section
            if (selectedRating > 0) {
                focusedRating = selectedRating
                updateStarDisplay()
                subtitleView.text = "Rate: $selectedRating/10"
                showReviewSection()
                // Focus the submit button for easy access
                submitButton.requestFocus()
            } else {
                // Set initial focus based on user rating or start from 1
                focusedRating = stats?.userRating ?: 1
                updateStarDisplay()
                // Focus the appropriate star
                starButtons.getOrNull(focusedRating - 1)?.requestFocus()
            }

            // Load existing review if user has rated
            if (stats?.userRating != null) {
                loadExistingReview(id)
            }
        }
    }

    private fun loadExistingReview(itemId: UUID) {
        lifecycleOwner.lifecycleScope.launch {
            val details = ratingsRepository.getDetailedRatings(itemId)
            // Find current user's review - we need to get the user ID somehow
            // For now, we'll check if there's a rating with hasReview=true that matches
            // the user's rating
            val userRating = currentStats?.userRating
            details?.find { it.rating == userRating && it.hasReview }?.let {
                existingReview = it.reviewText
            }
        }
    }

    private fun updateStarDisplay() {
        for (i in 0 until 10) {
            val rating = i + 1
            val button = starButtons[i]

            val isFilled = when {
                selectedRating > 0 -> rating <= selectedRating
                focusedRating > 0 -> rating <= focusedRating
                currentStats?.userRating != null -> rating <= (currentStats?.userRating ?: 0)
                else -> false
            }

            val isAverageDisplay = currentStats != null && focusedRating == 0 && selectedRating == 0 &&
                currentStats?.userRating == null && rating <= (currentStats?.averageRating?.toInt() ?: 0)

            button.text = if (isFilled || isAverageDisplay) "\u2605" else "\u2606" // Filled or empty star

            val color = when {
                selectedRating > 0 && rating <= selectedRating -> Color.parseColor("#FFD700") // Gold for selected
                focusedRating > 0 && rating <= focusedRating -> Color.parseColor("#FFD700") // Gold for hover
                isFilled -> Color.parseColor("#FFD700") // Gold for user rating
                isAverageDisplay -> Color.parseColor("#B8860B") // Dark gold for average
                else -> Color.parseColor("#666666")
            }
            button.setTextColor(color)
        }

        // Update subtitle with rating info (only if not in selection mode)
        if (selectedRating == 0) {
            val stats = currentStats
            subtitleView.text = when {
                focusedRating > 0 -> "Rate: $focusedRating/10"
                stats?.userRating != null -> "Your rating: ${stats.userRating}/10"
                stats != null && stats.totalRatings > 0 ->
                    "Average: ${"%.1f".format(stats.averageRating)}/10 (${stats.totalRatings} ratings)"
                else -> "No ratings yet"
            }
        }
    }

    private fun deleteRating() {
        val id = itemId ?: return

        lifecycleOwner.lifecycleScope.launch {
            val success = ratingsRepository.deleteRating(id)
            if (success) {
                Utils.showToast(context, R.string.msg_rating_deleted)
                // Refresh stats
                val newStats = ratingsRepository.getRatingStats(id, forceRefresh = true)
                currentStats = newStats
                focusedRating = 0
                updateStarDisplay()
                onRatingChanged?.invoke(newStats)
            } else {
                Utils.showToast(context, R.string.msg_rating_error)
            }
        }
    }

    fun show() {
        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, posLeft, posTop)
        // Focus the first star or user's current rating
        val focusIndex = (currentStats?.userRating ?: 1) - 1
        starButtons.getOrNull(focusIndex.coerceIn(0, 9))?.requestFocus()
    }

    fun dismiss() {
        popup.dismiss()
    }

    val isShowing: Boolean
        get() = popup.isShowing
}
