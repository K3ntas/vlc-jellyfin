package org.jellyfin.androidtv.ui.presentation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
 * positions immediately, so an arriving card that merely slides in from the side travels straight
 * over cards already sitting where it is going - which reads as landing on top of them rather than
 * making room. Instead the cards after the insertion point are put back where they were and
 * animated forward, so the gap visibly opens, while the new card fades up in the space rather than
 * crossing anything.
 *
 * Every animation here is tracked, because a card left mid-entrance is an invisible card: views
 * are recycled, and a row that never reports its animations finished keeps hold of them.
 */
class CardArrivalItemAnimator : DefaultItemAnimator() {
	private companion object {
		const val ARRIVE_MS = 420L

		/** Slightly longer, so the space finishes opening just as the card settles into it. */
		const val SHIFT_MS = 480L

		const val ENTRY_SCALE = 0.8f
	}

	/** Arrivals waiting for [runPendingAnimations] to decide whether they get an entrance. */
	private val pendingAdds = mutableListOf<RecyclerView.ViewHolder>()

	/** Entrances in flight. Whoever removes a holder first owns ending it, so it ends once. */
	private val entries = mutableMapOf<RecyclerView.ViewHolder, Animator>()

	/** Sibling pushes, keyed by view so a replaced or interrupted one is always cleaned up. */
	private val shifts = mutableMapOf<View, Animator>()

	init {
		addDuration = ARRIVE_MS
		moveDuration = SHIFT_MS
		removeDuration = 200L
		changeDuration = 200L
	}

	/**
	 * Held back rather than started here: whether this is an arrival or a row painting for the
	 * first time cannot be told until the whole batch is known.
	 */
	override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
		endAnimation(holder)

		// Set now, not when the animation starts. The runner is posted to the next frame, so a
		// card left at full alpha would be drawn once at its final size before fading in.
		holder.itemView.alpha = 0f
		holder.itemView.scaleX = ENTRY_SCALE
		holder.itemView.scaleY = ENTRY_SCALE

		pendingAdds += holder
		return true
	}

	override fun runPendingAnimations() {
		if (pendingAdds.isNotEmpty()) {
			val arrivals = pendingAdds.toList()
			pendingAdds.clear()

			val parent = arrivals.first().itemView.parent as? RecyclerView
			val arriving = arrivals.mapTo(mutableSetOf()) { it.itemView }

			// A row painting for the first time has no line to push - every card on screen is
			// arriving at once - and animating it is what left rows half empty, because each
			// entrance was interrupted by the next fill and the card stayed mid-fade. Only a row
			// that already has cards of its own gets the arrival treatment.
			val settled = parent != null && (0 until parent.childCount)
				.mapNotNull(parent::getChildAt)
				.any { it !in arriving }

			if (settled && parent != null) {
				arrivals.forEach { openSpaceFor(it, parent, arriving) }
				arrivals.forEach(::startEntry)
			} else {
				arrivals.forEach { holder ->
					holder.itemView.resetEntry()
					dispatchAddStarting(holder)
					dispatchAddFinished(holder)
				}
			}
		}

		super.runPendingAnimations()
		finishIfIdle()
	}

	private fun startEntry(holder: RecyclerView.ViewHolder) {
		val view = holder.itemView

		dispatchAddStarting(holder)

		val animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = ARRIVE_MS
			interpolator = DecelerateInterpolator(2f)

			addUpdateListener {
				val fraction = it.animatedValue as Float
				view.alpha = fraction
				view.scaleX = ENTRY_SCALE + (1f - ENTRY_SCALE) * fraction
				view.scaleY = view.scaleX
			}

			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					// Ended by hand already if it is no longer listed, and it must not report twice
					if (entries.remove(holder) == null) return

					view.resetEntry()
					dispatchAddFinished(holder)
					finishIfIdle()
				}
			})
		}

		entries[holder] = animator
		animator.start()
	}

	/**
	 * Slides everything after [holder] forward from where it used to sit.
	 *
	 * Cards that are themselves arriving are left alone - they fade in where they belong, and
	 * pushing them would drag them across the row on their way in.
	 */
	private fun openSpaceFor(
		holder: RecyclerView.ViewHolder,
		parent: RecyclerView,
		arriving: Set<View>,
	) {
		val insertedAt = holder.bindingAdapterPosition
		if (insertedAt == RecyclerView.NO_POSITION) return

		val step = holder.itemView.width + holder.itemView.horizontalMargins()
		if (step <= 0) return

		repeat(parent.childCount) { index ->
			val child = parent.getChildAt(index) ?: return@repeat
			if (child in arriving) return@repeat

			val position = parent.getChildViewHolder(child)?.bindingAdapterPosition ?: return@repeat
			if (position == RecyclerView.NO_POSITION || position < insertedAt) return@repeat

			pushFrom(child, step)
		}
	}

	/**
	 * Layout has already moved [view] along, so it is put back by [step] and walked forward, which
	 * is what reads as the line being pushed. Offsets accumulate rather than overwrite, because
	 * several cards can arrive in one pass and each one pushes the same neighbours again.
	 */
	private fun pushFrom(view: View, step: Int) {
		shifts.remove(view)?.cancel()
		view.translationX -= step

		val animator = ValueAnimator.ofFloat(view.translationX, 0f).apply {
			duration = SHIFT_MS
			interpolator = DecelerateInterpolator(2f)

			addUpdateListener { view.translationX = it.animatedValue as Float }

			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (shifts[view] !== animation) return

					shifts -= view
					view.translationX = 0f
					finishIfIdle()
				}
			})
		}

		shifts[view] = animator
		animator.start()
	}

	/**
	 * Handed back to the default animator, which drives translation itself. Our push is dropped
	 * first so the two are never writing the same view's position at once.
	 */
	override fun animateMove(
		holder: RecyclerView.ViewHolder,
		fromX: Int,
		fromY: Int,
		toX: Int,
		toY: Int,
	): Boolean {
		shifts.remove(holder.itemView)?.cancel()
		return super.animateMove(holder, fromX, fromY, toX, toY)
	}

	override fun endAnimation(item: RecyclerView.ViewHolder) {
		if (pendingAdds.remove(item)) {
			item.itemView.resetEntry()
			dispatchAddStarting(item)
			dispatchAddFinished(item)
		}

		entries.remove(item)?.let { animator ->
			animator.cancel()
			item.itemView.resetEntry()
			dispatchAddFinished(item)
		}

		endShift(item.itemView)

		super.endAnimation(item)
		finishIfIdle()
	}

	override fun endAnimations() {
		pendingAdds.toList().forEach(::endAnimation)
		entries.keys.toList().forEach(::endAnimation)
		shifts.keys.toList().forEach(::endShift)

		super.endAnimations()
		dispatchAnimationsFinished()
	}

	override fun isRunning(): Boolean = pendingAdds.isNotEmpty() ||
		entries.isNotEmpty() ||
		shifts.isNotEmpty() ||
		super.isRunning()

	private fun endShift(view: View) {
		shifts.remove(view)?.cancel()
		view.translationX = 0f
	}

	private fun finishIfIdle() {
		if (!isRunning) dispatchAnimationsFinished()
	}

	/** Cards are recycled, so an interrupted entrance must not leave one shrunk or transparent. */
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
