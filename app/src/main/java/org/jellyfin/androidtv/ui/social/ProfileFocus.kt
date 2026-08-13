package org.jellyfin.androidtv.ui.social

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * Makes a card reachable with the D-pad and shows where focus is.
 *
 * Compose gives nothing focus by default, so without this the remote has nowhere to move and the
 * screen reads as unusable even though it has content. The border colour follows the profile's own
 * accent so focus matches whatever theme the user picked.
 */
@Composable
fun Modifier.profileFocusable(
	accent: Color,
	cornerRadius: Int = 8,
	onClick: (() -> Unit)? = null,
): Modifier {
	var focused by remember { mutableStateOf(false) }
	val interactionSource = remember { MutableInteractionSource() }
	val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius.dp) }

	// A focused card lifts and glows rather than only gaining an outline. Spring rather than a
	// fixed duration so repeated D-pad presses feel continuous instead of queued.
	val scale by animateFloatAsState(
		targetValue = if (focused) 1.06f else 1f,
		animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
		label = "focusScale",
	)

	val glow by animateFloatAsState(
		targetValue = if (focused) 1f else 0f,
		animationSpec = tween(durationMillis = 180),
		label = "focusGlow",
	)

	return this
		.graphicsLayer {
			scaleX = scale
			scaleY = scale
			shadowElevation = glow * 18f
			this.shape = shape
			clip = false
		}
		.onFocusChanged { focused = it.isFocused }
		.focusable(interactionSource = interactionSource)
		.then(
			if (onClick != null) {
				Modifier.onKeyEvent { event ->
					val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter
					if (isSelect && event.type == KeyEventType.KeyUp) {
						onClick()
						true
					} else {
						false
					}
				}
			} else {
				Modifier
			}
		)
		.border(
			width = (glow * 3f).dp,
			color = accent.copy(alpha = glow),
			shape = shape,
		)
}
