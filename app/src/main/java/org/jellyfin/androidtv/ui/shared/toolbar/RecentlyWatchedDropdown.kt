package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/**
 * The toolbar's "continue watching" list.
 *
 * Films are a single line that plays on press. A series is a line that opens instead, offering the
 * episode it was left on and the one after it - which is the whole point of the feature, since
 * knowing a show was watched is useless without a way to carry on with it.
 */
@Composable
fun RecentlyWatchedDropdown(
	visible: Boolean,
	onDismiss: () -> Unit,
	entries: List<RecentlyWatchedEntry>,
	episodes: Map<UUID, SeriesEpisodes>,
	expandedSeries: UUID?,
	isLoading: Boolean,
	error: String?,
	onEntryClick: (RecentlyWatchedEntry) -> Unit,
	onEpisodeClick: (EpisodeChoice) -> Unit,
	api: ApiClient,
) {
	if (!visible) return

	val firstItemFocusRequester = remember { FocusRequester() }

	LaunchedEffect(visible, entries) {
		if (visible && entries.isNotEmpty()) {
			runCatching { firstItemFocusRequester.requestFocus() }
		}
	}

	Popup(
		alignment = Alignment.TopCenter,
		onDismissRequest = onDismiss,
		properties = PopupProperties(focusable = true),
	) {
		Box(
			modifier = Modifier
				.padding(top = 60.dp)
				.width(400.dp)
				.clip(RoundedCornerShape(12.dp))
				.background(Color(0xFF1A1A1A))
				.border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
		) {
			Column {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.background(Color(0xFF252525))
						.padding(horizontal = 16.dp, vertical = 12.dp)
				) {
					Text(
						text = "Continue Watching",
						color = Color.White,
						fontSize = 16.sp,
						fontWeight = FontWeight.Bold,
					)
				}

				when {
					isLoading && entries.isEmpty() -> Message("Loading...", Color.Gray)
					error != null -> Message(error, Color(0xFFFF5252))
					entries.isEmpty() -> Message("Nothing watched yet", Color.Gray)

					else -> LazyColumn(
						modifier = Modifier.height(400.dp),
						contentPadding = PaddingValues(8.dp),
						verticalArrangement = Arrangement.spacedBy(4.dp),
					) {
						entries.forEachIndexed { index, entry ->
							val expanded = expandedSeries == entry.id

							item(key = entry.id) {
								EntryRow(
									entry = entry,
									expanded = expanded,
									api = api,
									onClick = { onEntryClick(entry) },
									focusRequester = if (index == 0) firstItemFocusRequester else null,
								)
							}

							if (expanded) {
								val shown = episodes[entry.id]

								if (shown?.current != null) {
									item(key = "${entry.id}-current") {
										EpisodeRow(shown.current, onClick = { onEpisodeClick(shown.current) })
									}
								}

								when {
									shown?.next != null -> item(key = "${entry.id}-next") {
										EpisodeRow(shown.next, onClick = { onEpisodeClick(shown.next) })
									}

									shown?.loading == true -> item(key = "${entry.id}-loading") {
										EpisodeNote("Looking for the next episode...")
									}

									// Nothing follows: the show is finished, or this was the finale
									else -> item(key = "${entry.id}-none") {
										EpisodeNote("No next episode")
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun Message(text: String, color: Color) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(32.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(text = text, color = color)
	}
}

@Composable
private fun EntryRow(
	entry: RecentlyWatchedEntry,
	expanded: Boolean,
	api: ApiClient,
	onClick: () -> Unit,
	focusRequester: FocusRequester?,
) {
	val imageUrl = remember(entry.artworkItem) {
		entry.artworkItem.itemImages[ImageType.PRIMARY]?.getUrl(api, maxHeight = 96)
	}

	FocusableRow(
		onClick = onClick,
		focusRequester = focusRequester,
	) { focused ->
		AsyncImage(
			model = imageUrl,
			contentDescription = null,
			modifier = Modifier
				.size(width = 40.dp, height = 60.dp)
				.clip(RoundedCornerShape(4.dp))
				.background(Color(0xFF333333)),
			contentScale = ContentScale.Crop,
		)

		Spacer(modifier = Modifier.width(12.dp))

		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = entry.title,
				color = Color.White,
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			Spacer(modifier = Modifier.height(4.dp))

			Text(
				text = entry.subtitle,
				color = if (focused) Color(0xFFBBBBBB) else Color.Gray,
				fontSize = 12.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}

		// Only series open; a film has nowhere to go but playback
		if (entry.isSeries) {
			Text(
				text = if (expanded) "▾" else "▸",
				color = Color.Gray,
				fontSize = 14.sp,
				modifier = Modifier.padding(start = 8.dp),
			)
		}
	}
}

@Composable
private fun EpisodeRow(
	choice: EpisodeChoice,
	onClick: () -> Unit,
) {
	FocusableRow(
		onClick = onClick,
		focusRequester = null,
		startPadding = 32.dp,
		background = Color(0xFF1F1F1F),
	) { _ ->
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = choice.label,
				color = Color.White,
				fontSize = 13.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			Spacer(modifier = Modifier.height(2.dp))

			Text(
				text = choice.detail,
				color = Color(0xFF00A4DC),
				fontSize = 11.sp,
			)
		}
	}
}

@Composable
private fun EpisodeNote(text: String) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 40.dp, top = 6.dp, bottom = 10.dp),
	) {
		Text(text = text, color = Color.Gray, fontSize = 11.sp)
	}
}

/**
 * A row that takes D-pad focus and fires on centre, matching the rest of the toolbar dropdowns.
 * The focused flag is handed to the content so it can respond without tracking focus itself.
 */
@Composable
private fun FocusableRow(
	onClick: () -> Unit,
	focusRequester: FocusRequester?,
	startPadding: androidx.compose.ui.unit.Dp = 0.dp,
	background: Color = Color(0xFF252525),
	content: @Composable androidx.compose.foundation.layout.RowScope.(focused: Boolean) -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = startPadding)
			.clip(RoundedCornerShape(8.dp))
			.background(if (isFocused) Color(0xFF3A3A3A) else background)
			.border(
				2.dp,
				if (isFocused) Color(0xFF00A4DC) else Color.Transparent,
				RoundedCornerShape(8.dp),
			)
			.selectable(
				selected = false,
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.focusable(interactionSource = interactionSource)
			.onKeyEvent { keyEvent ->
				val isSelect = keyEvent.key == Key.DirectionCenter ||
					keyEvent.key == Key.Enter ||
					keyEvent.key == Key.NumPadEnter

				if (isSelect) {
					onClick()
					true
				} else {
					false
				}
			}
			.padding(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		content(isFocused)
	}
}
