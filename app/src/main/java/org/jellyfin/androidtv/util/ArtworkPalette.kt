package org.jellyfin.androidtv.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.palette.graphics.Palette

/**
 * Pulls an accent colour out of artwork so the interface can pick up the colour of whatever the
 * user is looking at.
 *
 * The point is that a focus glow or a background wash reads as a response to the poster rather
 * than as a decoration running on its own timer. Colours are chosen for use *behind* or *around*
 * white content, so vibrant swatches are preferred and the result is floored to a usable
 * brightness - a poster that is mostly black would otherwise produce an accent nobody can see.
 */
object ArtworkPalette {
	/** Posters are revisited constantly while browsing, so results are worth keeping. */
	private const val CACHE_SIZE = 64

	/** Palette downsamples internally; this only bounds what is handed to it. */
	private const val SAMPLE_SIZE = 96

	/** Below this a colour disappears against a dark interface. */
	private const val MIN_VALUE = 0.55f

	/** Above this the colour reads as white and stops being an accent at all. */
	private const val MAX_SATURATION_FLOOR = 0.35f

	private val cache = LruCache<String, Int>(CACHE_SIZE)

	/** Returned when artwork is missing or yields nothing usable. */
	const val FALLBACK = 0xFF4A9EFF.toInt()

	/**
	 * Reads the accent for [key] from cache, or extracts it from [drawable] and stores it. Null
	 * when the artwork could not be read, which callers should treat as "not yet" rather than as a
	 * colour.
	 *
	 * Failures are deliberately not cached. Focus can land on a card before its image has finished
	 * loading, and storing the miss would leave that item wearing the fallback colour for the rest
	 * of the session even though the artwork arrives a moment later.
	 *
	 * Runs on the calling thread and is cheap enough for a background one, but must not be called
	 * from the main thread on a miss.
	 */
	fun accentFor(key: String, drawable: Drawable?): Int? {
		cache.get(key)?.let { return it }

		val accent = extract(drawable) ?: return null
		cache.put(key, accent)
		return accent
	}

	/** Cached value only, for callers that cannot afford to block. Null when not yet known. */
	fun cached(key: String): Int? = cache.get(key)

	private fun extract(drawable: Drawable?): Int? {
		if (drawable == null) return null

		val sample = runCatching { sample(drawable) }.getOrNull() ?: return null

		val palette = runCatching {
			Palette.from(sample).clearFilters().maximumColorCount(16).generate()
		}.getOrNull()

		if (sample !== (drawable as? BitmapDrawable)?.bitmap) sample.recycle()

		val swatch = palette?.let {
			it.vibrantSwatch
				?: it.lightVibrantSwatch
				?: it.darkVibrantSwatch
				?: it.dominantSwatch
		} ?: return null

		return liven(swatch.rgb)
	}

	/**
	 * Produces a small bitmap to analyse.
	 *
	 * The image loader does not necessarily hand back a plain BitmapDrawable - crossfades and
	 * placeholders arrive wrapped in other drawables - so anything that is not already a bitmap is
	 * simply drawn into one. That covers every case at the cost of one small canvas.
	 */
	private fun sample(drawable: Drawable): Bitmap? {
		val bitmap = (drawable as? BitmapDrawable)?.bitmap
		if (bitmap != null) {
			if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
			return scale(bitmap)
		}

		val width = drawable.intrinsicWidth
		val height = drawable.intrinsicHeight
		if (width <= 0 || height <= 0) return null

		val factor = SAMPLE_SIZE.toFloat() / maxOf(width, height)
		val target = Bitmap.createBitmap(
			(width * factor).toInt().coerceIn(1, SAMPLE_SIZE),
			(height * factor).toInt().coerceIn(1, SAMPLE_SIZE),
			Bitmap.Config.ARGB_8888,
		)

		// Never resize the drawable that is on screen: it is still being drawn, on another thread.
		// A copy shares the underlying bitmap but carries its own bounds.
		val copy = drawable.constantState?.newDrawable()?.mutate() ?: return null

		val canvas = Canvas(target)
		copy.setBounds(0, 0, canvas.width, canvas.height)
		copy.draw(canvas)

		return target
	}

	private fun scale(source: Bitmap): Bitmap {
		val largest = maxOf(source.width, source.height)
		if (largest <= SAMPLE_SIZE) return source

		val factor = SAMPLE_SIZE.toFloat() / largest
		return Bitmap.createScaledBitmap(
			source,
			(source.width * factor).toInt().coerceAtLeast(1),
			(source.height * factor).toInt().coerceAtLeast(1),
			true,
		)
	}

	/** Lifts a swatch into a range that stays visible against dark chrome. */
	private fun liven(color: Int): Int {
		val hsv = FloatArray(3)
		Color.colorToHSV(color, hsv)

		// A nearly grey poster still deserves a hint of its own colour rather than a flat default
		hsv[1] = hsv[1].coerceAtLeast(MAX_SATURATION_FLOOR)
		hsv[2] = hsv[2].coerceAtLeast(MIN_VALUE)

		return Color.HSVToColor(hsv)
	}
}
