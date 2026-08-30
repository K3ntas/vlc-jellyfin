package org.jellyfin.androidtv.util

import org.jellyfin.sdk.model.api.BaseItemDto

/** Season and episode numbers, either of which may be unknown. */
data class EpisodeNumbering(val season: Int?, val episode: Int?)

/**
 * Works out how to label an episode when the server could not.
 *
 * Jellyfin numbers episodes from the filename, and a file it cannot parse is left with no index at
 * all and its bare filename as the title - which is how a season ends up displayed as a column of
 * "E21_ltpro_hd". The numbers are usually still sitting there in the name, so they are read back
 * out here rather than being lost.
 *
 * Everything the server did manage to work out wins: this only fills gaps, and never overrides a
 * real index or a real episode title.
 */
object EpisodeNaming {
	private val SEASON_EPISODE = Regex("""[Ss](\d{1,2})[ ._-]*[Ee](\d{1,4})""")
	private val CROSS_FORMAT = Regex("""(?<!\d)(\d{1,2})[xX](\d{1,4})(?!\d)""")
	private val EPISODE_ONLY = Regex("""(?<![A-Za-z0-9])[Ee](?:p|pisode)?[ ._-]?(\d{1,4})(?!\d)""")
	private val SEASON_ONLY = Regex("""(?<![A-Za-z0-9])[Ss](?:eason)?[ ._-]?(\d{1,2})(?!\d)""")

	/**
	 * A title that is really a filename: no spaces, and carrying the underscores, dots or dashes
	 * that separate the parts of one. Worth hiding once its numbers have been read out, since it
	 * says nothing a viewer wants.
	 */
	private val FILENAME_LIKE = Regex("""^[^\s]*[._-][^\s]*$""")

	fun numbering(item: BaseItemDto): EpisodeNumbering {
		val name = item.name.orEmpty()

		val parsed = SEASON_EPISODE.find(name)?.let {
			EpisodeNumbering(it.groupValues[1].toIntOrNull(), it.groupValues[2].toIntOrNull())
		} ?: CROSS_FORMAT.find(name)?.let {
			EpisodeNumbering(it.groupValues[1].toIntOrNull(), it.groupValues[2].toIntOrNull())
		} ?: EpisodeNumbering(null, EPISODE_ONLY.find(name)?.groupValues?.get(1)?.toIntOrNull())

		return EpisodeNumbering(
			season = item.parentIndexNumber ?: parsed.season ?: seasonFromContext(item),
			episode = item.indexNumber ?: parsed.episode,
		)
	}

	/** "S02E21", "E21", or empty when there is nothing to go on. */
	fun code(item: BaseItemDto): String {
		val (season, episode) = numbering(item)

		return when {
			season != null && episode != null -> "S%02dE%02d".format(season, episode)
			episode != null -> "E%02d".format(episode)
			else -> ""
		}
	}

	/**
	 * The episode's own title, or empty when the "title" is only a filename. Callers fall back to
	 * the code, which carries more meaning than the file ever did.
	 */
	fun title(item: BaseItemDto): String {
		val name = item.name.orEmpty().trim()

		if (name.isEmpty()) return ""
		if (FILENAME_LIKE.matches(name)) return ""

		return name
	}

	/** "S02E21  Anasazi", or just "S02E21" when the file never carried a title. */
	fun label(item: BaseItemDto): String {
		val parts = listOf(code(item), title(item)).filter { it.isNotEmpty() }

		return when {
			parts.isNotEmpty() -> parts.joinToString("  ")
			// Nothing parsed and nothing worth showing - the raw name beats an empty row
			else -> item.name.orEmpty()
		}
	}

	/** The season number as named on the season or the series, for files that omit it. */
	private fun seasonFromContext(item: BaseItemDto): Int? {
		val seasonName = item.seasonName.orEmpty()
		SEASON_ONLY.find(seasonName)?.let { return it.groupValues[1].toIntOrNull() }
		seasonName.trim().toIntOrNull()?.let { return it }

		return SEASON_ONLY.find(item.seriesName.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
	}
}
