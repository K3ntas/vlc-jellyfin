package org.jellyfin.androidtv.data.repository

import org.jellyfin.sdk.model.api.ItemFields

object ItemRepository {
	/**
	 * Full field set. Use for playback queues and detail screens, which read chapters,
	 * media streams and trickplay data directly off the item.
	 */
	val itemFields = setOf(
		ItemFields.CAN_DELETE,
		ItemFields.CHANNEL_INFO,
		ItemFields.CHAPTERS,
		ItemFields.CHILD_COUNT,
		ItemFields.CUMULATIVE_RUN_TIME_TICKS,
		ItemFields.DATE_CREATED,
		ItemFields.DISPLAY_PREFERENCES_ID,
		ItemFields.GENRES,
		ItemFields.ITEM_COUNTS,
		ItemFields.MEDIA_SOURCE_COUNT,
		ItemFields.MEDIA_SOURCES,
		ItemFields.MEDIA_STREAMS,
		ItemFields.OVERVIEW,
		ItemFields.PATH,
		ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
		ItemFields.TAGLINES,
		ItemFields.TRICKPLAY,
	)

	/**
	 * Reduced field set for card rows.
	 *
	 * Browse rows only ever render a poster, a title and a count badge, but the full set above
	 * also pulls CHAPTERS, MEDIA_STREAMS and TRICKPLAY for every item. Those are the largest
	 * parts of the response by far - a single movie can carry hundreds of chapter entries and one
	 * entry per audio and subtitle track - and a library view builds dozens of rows at a time.
	 *
	 * Dropping them here is safe because nothing reached from a card reads them off the row item:
	 * clicking a card opens the detail screen, which re-fetches via userLibraryApi.getItem, and
	 * the play actions call retrieveAndPlay(id), which re-fetches by id before handing the item to
	 * the player. MEDIA_SOURCES stays because the add-to-queue path in SdkPlaybackHelper uses it
	 * to decide whether an item is playable on its own.
	 */
	val cardItemFields = setOf(
		ItemFields.CHANNEL_INFO,
		ItemFields.CHILD_COUNT,
		ItemFields.CUMULATIVE_RUN_TIME_TICKS,
		ItemFields.ITEM_COUNTS,
		ItemFields.MEDIA_SOURCES,
		ItemFields.OVERVIEW,
		ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
	)
}
