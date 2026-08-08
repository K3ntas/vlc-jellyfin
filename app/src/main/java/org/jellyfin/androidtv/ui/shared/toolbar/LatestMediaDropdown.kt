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
import androidx.compose.foundation.lazy.itemsIndexed
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
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

@Composable
fun LatestMediaDropdown(
    visible: Boolean,
    onDismiss: () -> Unit,
    items: List<LatestMediaItem>,
    isLoading: Boolean,
    error: String?,
    onItemClick: (BaseItemDto) -> Unit,
    api: ApiClient,
    cleanTitle: (String?) -> String,
) {
    if (!visible) return

    val firstItemFocusRequester = remember { FocusRequester() }

    // Request focus on first item when items are loaded
    LaunchedEffect(visible, items) {
        if (visible && items.isNotEmpty()) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus requester might not be attached yet
            }
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
                .width(350.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
        ) {
            Column {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Latest Media",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading...",
                                color = Color.Gray,
                            )
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = error,
                                color = Color(0xFFFF5252),
                            )
                        }
                    }
                    items.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recent media found",
                                color = Color.Gray,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.height(400.dp),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            itemsIndexed(items) { index, mediaItem ->
                                LatestMediaItemRow(
                                    item = mediaItem,
                                    api = api,
                                    cleanTitle = cleanTitle,
                                    onClick = {
                                        onItemClick(mediaItem.item)
                                        onDismiss()
                                    },
                                    focusRequester = if (index == 0) firstItemFocusRequester else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestMediaItemRow(
    item: LatestMediaItem,
    api: ApiClient,
    cleanTitle: (String?) -> String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val imageUrl = remember(item.item) {
        item.item.itemImages[ImageType.PRIMARY]?.getUrl(api, maxHeight = 96)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = if (isFocused) Color(0xFF3A3A3A) else Color(0xFF252525)
    val borderColor = if (isFocused) Color(0xFF00A4DC) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .selectable(
                selected = false,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
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

        // Content
        Column(
            modifier = Modifier.weight(1f),
        ) {
            // Title
            Text(
                text = cleanTitle(item.item.name),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Type badge
                TypeBadge(type = item.displayType)

                // Year
                item.item.productionYear?.let { year ->
                    Text(
                        text = year.toString(),
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }

                // Time ago
                if (item.timeAgo.isNotEmpty()) {
                    Text(
                        text = item.timeAgo,
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: MediaType) {
    val (backgroundColor, textColor, label) = when (type) {
        MediaType.MOVIE -> Triple(Color(0xFF1E3A5F), Color(0xFF2196F3), "Movie")
        MediaType.SERIES -> Triple(Color(0xFF1B4332), Color(0xFF4CAF50), "Series")
        MediaType.ANIME -> Triple(Color(0xFF4A1259), Color(0xFF9C27B0), "Anime")
        MediaType.OTHER -> Triple(Color(0xFF424242), Color(0xFF9E9E9E), "Other")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
