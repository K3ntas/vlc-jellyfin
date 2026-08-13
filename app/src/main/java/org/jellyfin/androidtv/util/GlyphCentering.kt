package org.jellyfin.androidtv.util

import android.graphics.Rect
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Centres a TextView's drawn glyph inside the view, rather than centring its line box.
 *
 * `gravity="center"` positions the *line box* - the ascent-to-descent band the font reserves for
 * any character. Where a glyph actually paints inside that band is its own business, and symbols
 * like ★ sit well off centre: the descent below them is empty space no part of the star occupies.
 * A background drawn across the whole view therefore looks unbalanced even though the text is,
 * strictly speaking, centred.
 *
 * This measures the glyph's real ink and pads the opposite edge by twice the error, which lands
 * the ink on the view's centre line. Reading the offset from the font rather than hard-coding a
 * value keeps it correct across devices, whose system fonts differ.
 *
 * The view needs headroom over its line height to absorb the padding. Too tight and the glyph only
 * moves part of the way, which looks like the correction simply did not work - the rating stars are
 * 40dp around 26sp text for exactly this reason.
 */
fun TextView.centerGlyphVertically(glyph: String = text.toString()) {
	if (glyph.isEmpty()) return

	val ink = Rect()
	paint.getTextBounds(glyph, 0, glyph.length, ink)
	if (ink.isEmpty) return

	val metrics = paint.fontMetrics

	// Which band the layout centres depends on whether the font's own padding is in play
	val lineTop = if (includeFontPadding) metrics.top else metrics.ascent
	val lineBottom = if (includeFontPadding) metrics.bottom else metrics.descent

	// Both are measured from the baseline, so they subtract directly
	val inkCentre = (ink.top + ink.bottom) / 2f
	val lineCentre = (lineTop + lineBottom) / 2f
	val shift = ((inkCentre - lineCentre) * 2).roundToInt()

	when {
		// Ink sits below centre, so squeeze the bottom to lift it
		shift > 0 -> setPadding(paddingLeft, 0, paddingRight, shift)
		shift < 0 -> setPadding(paddingLeft, -shift, paddingRight, 0)
		else -> setPadding(paddingLeft, 0, paddingRight, 0)
	}
}
