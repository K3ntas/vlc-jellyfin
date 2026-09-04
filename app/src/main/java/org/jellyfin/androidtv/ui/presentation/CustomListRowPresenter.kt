package org.jellyfin.androidtv.ui.presentation

import android.view.View
import androidx.core.view.isVisible
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter
import androidx.recyclerview.widget.RecyclerView

open class CustomListRowPresenter @JvmOverloads constructor(
	private val topPadding: Int? = null
) : ListRowPresenter() {
	init {
		headerPresenter = CustomRowHeaderPresenter()
	}

	override fun isUsingDefaultShadow() = false

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit

	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)

		// Rows are recycled, so only fit one when this row does not already carry ours
		val grid = (holder as? ViewHolder)?.gridView
		if (grid != null && grid.itemAnimator !is CardArrivalItemAnimator) {
			grid.itemAnimator = CardArrivalItemAnimator()
			grid.addOnChildAttachStateChangeListener(ResetTransientState)
		}

		val view = holder.view?.parent as? View ?: return
		if (topPadding != null) view.setPadding(view.paddingLeft, topPadding, view.paddingRight, view.paddingBottom)

		// Hide header view when the item doesn't have one
		holder.headerViewHolder.view.isVisible = !(item is ListRow && item.headerItem == null)
	}

	/**
	 * Clears anything a previous life of a recycled card view left behind.
	 *
	 * A card whose arrival animation was cut short would come back transparent or shifted along -
	 * which reads as the card simply missing from the row. Attaching always happens before the
	 * arrival is staged, so this can never wipe an animation that is about to run. Scale is left
	 * alone: leanback drives it for the focus zoom, and a card is at worst slightly small.
	 */
	private object ResetTransientState : RecyclerView.OnChildAttachStateChangeListener {
		override fun onChildViewAttachedToWindow(view: View) {
			view.alpha = 1f
			view.translationX = 0f
			view.translationY = 0f
		}

		override fun onChildViewDetachedFromWindow(view: View) = Unit
	}
}
