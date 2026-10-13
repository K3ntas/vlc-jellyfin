@file:JvmName("ElegantStyle")

package org.jellyfin.androidtv.util

import android.content.Context
import android.util.TypedValue
import org.jellyfin.androidtv.R

/**
 * Whether the current theme asks for the ElegantFin presentation.
 *
 * Read from the theme rather than the preference, so it always matches what the activity is
 * actually drawn with - the preference can change before the activity is recreated.
 */
fun Context.isElegantStyle(): Boolean {
	val value = TypedValue()
	return theme.resolveAttribute(R.attr.elegantStyle, value, true) &&
		value.type == TypedValue.TYPE_INT_BOOLEAN && value.data != 0
}

/** The theme's resting card border colour, or 0 when the theme draws none. */
fun Context.cardRestingBorderColor(): Int {
	val value = TypedValue()
	if (!theme.resolveAttribute(R.attr.cardRestingBorderColor, value, true)) return 0
	return if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) value.data else 0
}
