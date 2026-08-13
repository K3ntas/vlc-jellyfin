package org.jellyfin.androidtv.ui.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.data.social.TopGenre
import org.jellyfin.androidtv.ui.base.Text

/**
 * The RATINGS histogram from the web profile: one bar per score from 1 to 10.
 *
 * [distribution] is the ten-element array the server sends, where index 0 is a rating of 1.
 */
@Composable
fun RatingsBarChart(
	distribution: List<Int>,
	barColor: Color,
	labelColor: Color,
	modifier: Modifier = Modifier,
) {
	val max = (distribution.maxOrNull() ?: 0).coerceAtLeast(1)

	Column(modifier = modifier.fillMaxWidth()) {
		Canvas(
			modifier = Modifier
				.fillMaxWidth()
				.height(90.dp)
		) {
			val slot = size.width / 10f
			val barWidth = slot * 0.62f
			val gap = (slot - barWidth) / 2f

			distribution.take(10).forEachIndexed { index, count ->
				// Keep a sliver visible for empty scores so the axis still reads as ten columns
				val fraction = count.toFloat() / max
				val barHeight = (size.height * fraction).coerceAtLeast(2f)

				drawRect(
					color = barColor,
					topLeft = Offset(index * slot + gap, size.height - barHeight),
					size = Size(barWidth, barHeight),
				)
			}
		}

		Row(
			horizontalArrangement = Arrangement.SpaceBetween,
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 4.dp)
		) {
			(1..10).forEach { score ->
				Text(text = score.toString(), color = labelColor, fontSize = 10.sp)
			}
		}
	}
}

/**
 * The TASTE donut. Percentages come from the server already computed, so this only has to lay
 * them out around the ring and label the largest slice in the middle.
 */
@Composable
fun TasteDonut(
	genres: List<TopGenre>,
	palette: List<Color>,
	textColor: Color,
	labelColor: Color,
	modifier: Modifier = Modifier,
) {
	if (genres.isEmpty()) return

	val top = genres.first()

	Column(modifier = modifier.fillMaxWidth()) {
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.fillMaxWidth()
				.height(150.dp)
		) {
			Canvas(modifier = Modifier.size(140.dp)) {
				val thickness = 26f
				val inset = thickness / 2f
				var startAngle = -90f

				genres.forEachIndexed { index, genre ->
					val sweep = (genre.percent / 100.0 * 360.0).toFloat()

					drawArc(
						color = palette[index % palette.size],
						startAngle = startAngle,
						sweepAngle = sweep,
						useCenter = false,
						topLeft = Offset(inset, inset),
						size = Size(size.width - thickness, size.height - thickness),
						style = Stroke(width = thickness),
					)

					startAngle += sweep
				}
			}

			Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Text(text = top.name, color = labelColor, fontSize = 12.sp)
				Text(
					text = "${top.percent.toInt()}%",
					color = textColor,
					fontSize = 22.sp,
					fontWeight = FontWeight.Bold,
				)
			}
		}

		genres.take(5).forEachIndexed { index, genre ->
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 2.dp)
			) {
				Box(
					modifier = Modifier
						.size(9.dp)
						.let { it }
				) {
					Canvas(modifier = Modifier.size(9.dp)) {
						drawRect(color = palette[index % palette.size], size = size)
					}
				}

				Text(
					text = "  ${genre.name}",
					color = textColor,
					fontSize = 12.sp,
					modifier = Modifier.fillMaxWidth(0.7f),
				)

				Text(
					text = String.format("%.1f%%", genre.percent),
					color = labelColor,
					fontSize = 12.sp,
				)
			}
		}
	}
}

/** The green star row the web profile puts under posters and beside review titles. */
@Composable
fun StarRating(rating: Int, color: Color, modifier: Modifier = Modifier, size: Int = 13) {
	// Ratings are out of ten but shown as five stars, as on the web
	val filled = (rating + 1) / 2

	Text(
		text = buildString {
			repeat(5) { index -> append(if (index < filled) '★' else '☆') }
		},
		color = color,
		fontSize = size.sp,
		modifier = modifier,
	)
}
