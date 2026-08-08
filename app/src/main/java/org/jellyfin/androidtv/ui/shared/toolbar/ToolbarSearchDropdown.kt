package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Badge
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.popover.Popover
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

@Composable
fun ToolbarSearchDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    results: List<BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (BaseItemDto) -> Unit,
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    Popover(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        alignment = Alignment.TopCenter,
        offset = DpOffset(0.dp, 8.dp),
        shape = RoundedCornerShape(10.dp),
        backgroundColor = Color(0xFF1A1A1A),
        modifier = modifier.width(350.dp),
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.loading),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
            results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.lbl_no_items),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(results, key = { it.id }) { item ->
                        SearchResultItem(
                            item = item,
                            onClick = { onItemClick(item) },
                            api = api,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    item: BaseItemDto,
    onClick: () -> Unit,
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = if (focused) {
        JellyfinTheme.colorScheme.listButtonFocused
    } else {
        Color.Transparent
    }

    val imageUrl = remember(item) {
        item.itemImages[ImageType.PRIMARY]?.getUrl(api, maxHeight = 90)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 90.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    url = imageUrl,
                    blurHash = item.itemImages[ImageType.PRIMARY]?.blurHash,
                    aspectRatio = 2f / 3f,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.name ?: "",
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 2,
            )

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (badgeColor, badgeText) = when (item.type) {
                    BaseItemKind.MOVIE -> Color(0xFF4CAF50) to "Movie"
                    BaseItemKind.SERIES -> Color(0xFF2196F3) to stringResource(R.string.lbl_series)
                    else -> JellyfinTheme.colorScheme.button to (item.type.name)
                }

                Badge(
                    shape = RoundedCornerShape(4.dp),
                    containerColor = badgeColor
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }

                item.productionYear?.let { year ->
                    Text(
                        text = year.toString(),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
