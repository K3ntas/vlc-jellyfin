package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class AppTheme(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * The default dark theme
	 */
	DARK(R.string.pref_theme_dark),

	/**
	 * The "classic" emerald theme
	 */
	EMERALD(R.string.pref_theme_emerald),

	/**
	 * A theme with a more muted accent color, inspired by CTalvio's Monochromic CSS theme for Jellyfin Web
	 */
	MUTED_PURPLE(R.string.pref_theme_muted_purple),

	/**
	 * Slate and indigo, after lscambo13's ElegantFin CSS theme for Jellyfin Web. Rebuilt natively
	 * here, since the TV app cannot load web CSS.
	 */
	ELEGANTFIN(R.string.pref_theme_elegantfin),
}
