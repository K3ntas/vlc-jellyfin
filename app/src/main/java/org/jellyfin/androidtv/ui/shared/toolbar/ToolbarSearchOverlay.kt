package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

@Composable
fun ToolbarSearchOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (BaseItemDto) -> Unit,
    api: ApiClient,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF2101010))
                    .onKeyEvent { event ->
                        if (event.key == Key.Escape || event.key == Key.Back) {
                            onDismiss()
                            true
                        } else false
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Left side - Search input and keyboard
                    SearchInputSection(
                        query = query,
                        onQueryChange = onQueryChange,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    )

                    // Right side - Results
                    SearchResultsSection(
                        results = results,
                        isLoading = isLoading,
                        onItemClick = onItemClick,
                        api = api,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputSection(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Title
        Text(
            text = stringResource(R.string.lbl_search),
            fontSize = 24.sp,
            color = Color.White,
        )

        Spacer(Modifier.height(16.dp))

        // Search query display box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(12.dp))

                if (query.isEmpty()) {
                    Text(
                        text = "Type to search...",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                } else {
                    Text(
                        text = query,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Custom TV keyboard
        TvKeyboard(
            onKeyPress = { key -> onQueryChange(query + key) },
            onBackspace = { if (query.isNotEmpty()) onQueryChange(query.dropLast(1)) },
            onClear = { onQueryChange("") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SearchResultsSection(
    results: List<BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (BaseItemDto) -> Unit,
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Results header
        Text(
            text = when {
                isLoading -> "Searching..."
                results.isEmpty() -> "Results"
                else -> "Results (${results.size})"
            },
            fontSize = 20.sp,
            color = Color.White,
        )

        Spacer(Modifier.height(12.dp))

        // Results container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.loading),
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                ImageVector.vectorResource(R.drawable.ic_search),
                                contentDescription = null,
                                tint = Color(0xFF444444),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Search for movies and series",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
        Color(0xFFE50914) // Netflix red when focused
    } else {
        Color.Transparent
    }

    val imageUrl = remember(item) {
        item.itemImages[ImageType.PRIMARY]?.getUrl(api, maxHeight = 100)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Poster image
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

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = item.name ?: "",
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 2,
            )

            Spacer(Modifier.height(6.dp))

            // Type and year
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val (badgeColor, badgeText) = when (item.type) {
                    BaseItemKind.MOVIE -> Color(0xFF4CAF50) to "Movie"
                    BaseItemKind.SERIES -> Color(0xFF2196F3) to "Series"
                    else -> Color(0xFF757575) to item.type.name
                }

                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }

                item.productionYear?.let { year ->
                    Text(
                        text = year.toString(),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
