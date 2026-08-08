package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text

private val KEYBOARD_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
    listOf("Z", "X", "C", "V", "B", "N", "M"),
)

private const val KEY_SPACE = "SPACE"
private const val KEY_BACKSPACE = "DEL"
private const val KEY_CLEAR = "CLEAR"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvKeyboard(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
) {
    val firstKeyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            firstKeyFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Letter/number rows
        KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEachIndexed { keyIndex, key ->
                    val focusRequester = if (rowIndex == 0 && keyIndex == 0) {
                        firstKeyFocusRequester
                    } else {
                        remember { FocusRequester() }
                    }

                    KeyboardKey(
                        label = key,
                        onClick = { onKeyPress(key.lowercase()) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Bottom row with space, backspace, clear
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            KeyboardKey(
                label = KEY_BACKSPACE,
                onClick = onBackspace,
                modifier = Modifier.weight(1.5f),
                isSpecial = true
            )

            KeyboardKey(
                label = KEY_SPACE,
                displayLabel = "Space",
                onClick = { onKeyPress(" ") },
                modifier = Modifier.weight(3f),
                isSpecial = true
            )

            KeyboardKey(
                label = KEY_CLEAR,
                onClick = onClear,
                modifier = Modifier.weight(1.5f),
                isSpecial = true
            )
        }
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    displayLabel: String? = null,
    isSpecial: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = when {
        focused -> Color(0xFFE50914) // Netflix red when focused
        isSpecial -> Color(0xFF3D3D3D)
        else -> Color(0xFF2A2A2A)
    }

    val textColor = Color.White

    Box(
        modifier = modifier
            .height(48.dp)
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (label) {
                KEY_BACKSPACE -> "⌫"
                else -> displayLabel ?: label
            },
            color = textColor,
            fontSize = when {
                label == KEY_BACKSPACE -> 22.sp
                isSpecial -> 14.sp
                else -> 18.sp
            }
        )
    }
}
