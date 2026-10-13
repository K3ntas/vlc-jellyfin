package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/** How the focused card is outlined. Independent of the focus glow, which can be added to any. */
enum class FocusFrameStyle(
	override val nameRes: Int,
) : PreferenceEnum {
	/** Whatever the app theme draws: the colour-drifting frame, or ElegantFin's two-tone one. */
	THEME_DEFAULT(R.string.pref_focus_frame_theme_default),

	/**
	 * Google TV's focus ring: a white line outside the card with a thin gap of page background
	 * between it and the artwork, so it never has to contrast with the poster itself.
	 */
	GOOGLE_TV_RING(R.string.pref_focus_frame_google_tv),

	/** A white line directly around the outside of the card, covering none of the artwork. */
	HUGGING_RING(R.string.pref_focus_frame_hugging),

	/** No outline, for use with the glow on its own or the zoom alone. */
	NONE(R.string.pref_focus_frame_none),
}
