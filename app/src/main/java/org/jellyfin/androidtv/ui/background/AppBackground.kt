package org.jellyfin.androidtv.ui.background

import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.koin.compose.koinInject

@Composable
private fun AppThemeBackground() {
	val context = LocalContext.current
	val themeBackground = remember(context.theme) {
		val attributes = context.theme.obtainStyledAttributes(intArrayOf(R.attr.defaultBackground))
		val drawable = attributes.getDrawable(0)
		attributes.recycle()

		if (drawable is ColorDrawable) drawable.toBitmap(1, 1).asImageBitmap()
		else drawable?.toBitmap()?.asImageBitmap()
	}

	if (themeBackground != null) {
		Image(
			bitmap = themeBackground,
			contentDescription = null,
			alignment = Alignment.Center,
			contentScale = ContentScale.Crop,
			modifier = Modifier.fillMaxSize()
		)
	} else {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black)
		)
	}
}

/**
 * A slow push and drift across the backdrop.
 *
 * A still image behind the interface reads as a screenshot; the same image barely moving reads as
 * alive. Kept well under a percent of movement per second, and deliberately not looped back to its
 * start - it reverses, so there is never a jump.
 */
@Composable
private fun Modifier.backdropDrift(): Modifier {
	val transition = rememberInfiniteTransition(label = "BackdropDrift")

	val progress by transition.animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 28_000, easing = LinearEasing),
			repeatMode = RepeatMode.Reverse,
		),
		label = "BackdropDriftProgress",
	)

	return graphicsLayer {
		val zoom = 1f + progress * 0.11f
		scaleX = zoom
		scaleY = zoom
		// A touch of pan so it is not purely a zoom, which on its own can look like a mistake
		translationX = progress * size.width * 0.025f
		translationY = progress * size.height * -0.018f
	}
}

@Composable
fun AppBackground() {
	val backgroundService = koinInject<BackgroundService>()
	val currentBackground by backgroundService.currentBackground.collectAsState()
	val blurBackground by backgroundService.blurBackground.collectAsState()
	val enabled by backgroundService.enabled.collectAsState()
	val accentColor by backgroundService.accent.collectAsState()

	val accent = remember(accentColor) { Color(accentColor) }

	if (enabled) {
		AnimatedContent(
			targetState = currentBackground,
			transitionSpec = {
				val duration = (BackgroundService.TRANSITION_DURATION.inWholeMilliseconds / 2).toInt()
				fadeIn(tween(durationMillis = duration)) togetherWith fadeOut(snap(delayMillis = duration))
			},
			label = "BackgroundTransition",
		) { background ->
			if (background != null) {
				Box(modifier = Modifier.fillMaxSize()) {
					Image(
						bitmap = background,
						contentDescription = null,
						alignment = Alignment.Center,
						contentScale = ContentScale.Crop,
						colorFilter = ColorFilter.tint(colorResource(R.color.background_filter), BlendMode.SrcAtop),
						modifier = Modifier
							.fillMaxSize()
							.backdropDrift()
							.then(if (blurBackground) Modifier.blur(10.dp) else Modifier)
					)

					// Ties the whole screen to the colour of what is playing, without touching the
					// legibility of anything drawn on top of it
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								Brush.verticalGradient(
									listOf(Color.Transparent, accent.copy(alpha = 0.16f)),
								)
							)
					)
				}
			} else {
				Box(modifier = Modifier.fillMaxSize()) {
					AppThemeBackground()

					// Nothing to show behind the interface, which is the one place it looked bare
					if (auroraSupported) {
						AuroraBackground(accent = accent, modifier = Modifier.fillMaxSize())
					}
				}
			}
		}
	}
}
