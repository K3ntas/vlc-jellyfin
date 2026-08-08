package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text

@Composable
fun ToolbarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocused: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        focused -> Color(0xFFCCCCCC)
        else -> Color(0xFF444444)
    }

    val textColor = when {
        focused -> Color(0xFFDDDDDD)
        else -> Color(0xFFCCCCCC)
    }

    ProvideTextStyle(
        LocalTextStyle.current.copy(
            color = textColor,
            fontSize = 14.sp,
        )
    ) {
        BasicTextField(
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .width(200.dp)
                .height(36.dp),
            value = query,
            singleLine = true,
            interactionSource = interactionSource,
            onValueChange = onQueryChange,
            keyboardActions = KeyboardActions.Default,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
                autoCorrectEnabled = true,
                showKeyboardOnFocus = true,
            ),
            textStyle = LocalTextStyle.current,
            cursorBrush = SolidColor(textColor),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(25.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(25.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (query.isEmpty()) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.lbl_search),
                            tint = textColor.copy(alpha = 0.7f)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onClear
                                )
                        ) {
                            Text(
                                text = "\u2715",
                                color = textColor,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.lbl_search) + "...",
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}
