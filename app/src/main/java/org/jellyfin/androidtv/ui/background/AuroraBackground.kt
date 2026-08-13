package org.jellyfin.androidtv.ui.background

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * A slow aurora drawn behind the interface, tinted by whatever is on screen.
 *
 * This exists for the case where there is no backdrop to show - otherwise the app falls back to
 * flat black, which is the one moment it looks unfinished. Two soft bands drift across each other
 * at different rates so the motion never visibly loops.
 *
 * Rendered on the GPU from a fragment shader, so it costs a fill of the screen and no allocation
 * per frame. AGSL needs API 33, and callers fall back to the flat background below that.
 */
private const val AURORA_SHADER = """
uniform float2 uSize;
uniform float uTime;
uniform half3 uAccent;

// Cheap value noise, enough to keep the bands from looking like plain sine waves
float wave(float x, float t, float f, float s) {
    return sin(x * f + t * s) * 0.5 + sin(x * f * 1.7 - t * s * 0.6) * 0.3;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;

    // Two bands crossing the screen, each riding its own drift
    float upper = 0.42 + wave(uv.x, uTime, 3.1, 0.35) * 0.10;
    float lower = 0.68 + wave(uv.x, uTime, 2.3, -0.27) * 0.13;

    float a1 = smoothstep(0.30, 0.0, abs(uv.y - upper));
    float a2 = smoothstep(0.24, 0.0, abs(uv.y - lower));

    // Fade out towards the top so the bands feel lit from below rather than floating
    float lift = smoothstep(0.0, 0.85, uv.y);
    float alpha = clamp((a1 * 0.55 + a2 * 0.40) * lift, 0.0, 1.0) * 0.5;

    // Premultiplied, which is what the canvas expects
    return half4(uAccent * alpha, alpha);
}
"""

/** True when this device can render [AuroraBackground] at all. */
val auroraSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AuroraBackground(
	accent: Color,
	modifier: Modifier = Modifier,
) {
	val shader = remember { RuntimeShader(AURORA_SHADER) }
	val brush = remember(shader) { ShaderBrush(shader) }

	// One long cycle rather than a per-frame clock, so nothing accumulates and the loop is seamless
	val transition = rememberInfiniteTransition(label = "Aurora")
	val time by transition.animateFloat(
		initialValue = 0f,
		targetValue = (2 * Math.PI).toFloat(),
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 48_000, easing = LinearEasing),
			repeatMode = RepeatMode.Restart,
		),
		label = "AuroraTime",
	)

	Box(
		modifier = modifier.drawBehind {
			shader.setFloatUniform("uSize", size.width, size.height)
			shader.setFloatUniform("uTime", time)
			shader.setFloatUniform("uAccent", accent.red, accent.green, accent.blue)

			drawRect(brush = brush)
		}
	)
}
