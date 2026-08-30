package org.jellyfin.androidtv.ui.presentation

import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * Opens a space in a row and settles a newly arrived card into it.
 *
 * Rows are drawn from cache and then reconciled with the server, so cards genuinely appear a
 * moment after the row does. Left alone they blink into existence and the row looks like it
 * glitched.
 *
 * The push has to be staged by hand. Leanback's grid lays its children out at their final
 * positions immediately and does not animate them, so an arriving card that merely slides in from
 * the side travels straight over cards already sitting where it is going - which reads as landing
 * on top of them rather than making room. Instead the cards after the insertion point are put back
 * where they were and animated forward, so the gap visibly opens, while the new card fades up in
 * the space rather than crossing anything.
 */
class CardArrivalItemAnimator : DefaultItemAnimator() {
	private companion object {
		const val ARRIVE_MS = 420L

		/** Slightly longer, so the space finishes opening just as the card settles into it. */
		const val SHIFT_MS = 480L

		const val ENTRY_SCALE = 0.8f
	}

	/** Everything currently offset by hand, so a cancelled row never keeps a card out of place. */
	private val shifted = mutableSetOf<View>()

	init {
		addDuration = ARRIVE_MS
		moveDuration = SHIFT_MS
		removeDuration = 200L
		changeDuration = 200L
	}

	override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
		val view = holder.itemView

		openSpaceFor(holder)

		view.alpha = 0f
		view.scaleX = ENTRY_SCALE
		view.scaleY = ENTRY_SCALE

		dispatchAddStarting(holder)

		view.animate()
			.alpha(1f)
			.scaleX(1f)
			.scaleY(1f)
			.setDuration(addDuration)
			.setInterpolator(DecelerateInterpolator(2f))
			.withEndAction {
				view.resetEntry()
				dispatchAddFinished(holder)
				if (!isRunning) dispatchAnimationsFinished()
			}
			.start()

		return true
	}

	/**
	 * Slides everything after [holder] forward from where it used to sit.
	 *
	 * The layout has already moved these cards along, so they are first put back by one card's
	 * width and then animated to zero - which looks like the row being pushed. Offsets accumulate
	 * rather than overwrite, because several cards can arrive in the same pass and each one pushes
	 * the same neighbours again.
	 */
	private fun openSpaceFor(holder: RecyclerView.ViewHolder) {
		val parent = holder.itemView.parent as? RecyclerView ?: return
		val insertedAt = holder.bindingAdapterPosition
		if (insertedAt == RecyclerView.NO_POSITION) return

		val step = holder.itemView.width + holder.itemView.horizontalMargins()
		if (step <= 0) return

		repeat(parent.childCount) { index ->
			val child = parent.getChildAt(index) ?: return@repeat
			if (child === holder.itemView) return@repeat

			val position = parent.getChildViewHolder(child)?.bindingAdapterPosition ?: return@repeat
			if (position == RecyclerView.NO_POSITION || position < insertedAt) return@repeat

			child.animate().cancel()
			child.translationX -= step
			shifted += child

			child.animate()
				.translationX(0f)
				.setDuration(SHIFT_MS)
				.setInterpolator(DecelerateInterpolator(2f))
				.withEndAction {
					child.translationX = 0f
					shifted -= child
				}
				.start()
		}
	}

	override fun endAnimation(item: RecyclerView.ViewHolder) {
		item.itemView.resetEntry()
		releaseShift(item.itemView)
		super.endAnimation(item)
	}

	override fun endAnimations() {
		shifted.toList().forEach(::releaseShift)
		super.endAnimations()
	}

	override fun isRunning(): Boolean = shifted.isNotEmpty() || super.isRunning()

	private fun releaseShift(view: View) {
		view.animate().cancel()
		view.translationX = 0f
		shifted -= view
	}

	/** Cards are recycled, so a cancelled entrance must not leave one shrunk or transparent. */
	private fun View.resetEntry() {
		alpha = 1f
		scaleX = 1f
		scaleY = 1f
	}

	private fun View.horizontalMargins(): Int {
		val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
		return params.leftMargin + params.rightMargin
	}
}
