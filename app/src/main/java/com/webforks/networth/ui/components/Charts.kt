package com.webforks.networth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webforks.networth.domain.Format
import com.webforks.networth.domain.Period
import com.webforks.networth.domain.PeriodChange
import com.webforks.networth.domain.Point
import com.webforks.networth.ui.theme.trendColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Line chart with press-and-drag scrubbing. [onScrub] gets the point under the finger, or
 * null when the finger lifts, so the header can show that day's value.
 */
@Composable
fun LineChart(
    points: List<Point>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    baseline: Double? = null,
    onScrub: (Point?) -> Unit = {},
) {
    var scrubX by remember { mutableStateOf<Float?>(null) }
    val guide = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Value chart" }
            .pointerInput(points) {
                if (points.size < 2) return@pointerInput
                fun pick(x: Float): Point {
                    val frac = (x / size.width).coerceIn(0f, 1f)
                    val first = points.first().epochDay
                    val day = first + ((points.last().epochDay - first) * frac).toLong()
                    return points.minBy { kotlin.math.abs(it.epochDay - day) }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    scrubX = down.position.x
                    onScrub(pick(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        scrubX = change.position.x
                        onScrub(pick(change.position.x))
                        change.consume()
                    }
                    scrubX = null
                    onScrub(null)
                }
            },
    ) {
        if (points.size < 2) {
            // Not enough history yet: flat guide line so the space isn't empty.
            drawLine(guide, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
            return@Canvas
        }
        val stroke = 2.5.dp.toPx()
        val values = points.map { it.value } + listOfNotNull(baseline)
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val first = points.first().epochDay
        val span = (points.last().epochDay - first).coerceAtLeast(1).toFloat()
        val top = stroke
        val usable = size.height - 2 * stroke
        fun x(p: Point) = size.width * ((p.epochDay - first) / span)
        fun y(v: Double) = if (max == min) size.height / 2 else top + usable * (1f - ((v - min) / range).toFloat())

        baseline?.let {
            drawLine(
                guide, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
        }

        val line = Path().apply {
            moveTo(x(points[0]), y(points[0].value))
            points.drop(1).forEach { lineTo(x(it), y(it.value)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(points.last()), size.height)
            lineTo(x(points.first()), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        scrubX?.let { sx ->
            val frac = (sx / size.width).coerceIn(0f, 1f)
            val day = first + ((points.last().epochDay - first) * frac).toLong()
            val p = points.minBy { kotlin.math.abs(it.epochDay - day) }
            val px = x(p)
            drawLine(onSurface.copy(alpha = 0.4f), Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(px, y(p.value)))
        }
    }
}

@Composable
fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Period.entries.forEach { p ->
            FilterChip(selected = p == selected, onClick = { onSelect(p) }, label = { Text(p.label) })
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

/** Big number + change line; shows the scrubbed day instead while the chart is touched. */
@Composable
fun ValueHeader(
    title: String,
    value: Double,
    change: PeriodChange?,
    scrub: Point?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            Format.money(scrub?.value ?: value),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            scrub != null && change != null -> {
                val diff = scrub.value - change.start
                ChangeText(
                    diff,
                    if (change.start == 0.0) null else diff / kotlin.math.abs(change.start),
                    LocalDate.ofEpochDay(scrub.epochDay).format(dateFmt),
                )
            }
            change != null -> ChangeText(change.change, change.percent, periodCaption(change))
            else -> Text("No history yet", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun periodCaption(change: PeriodChange): String {
    val base = when (change.period) {
        Period.D1 -> "Today"
        Period.W1 -> "Past week"
        Period.M1 -> "Past month"
        Period.M3 -> "Past 3 months"
        Period.YTD -> "Year to date"
        Period.Y1 -> "Past year"
        Period.ALL -> "All time"
    }
    return if (change.coversFullPeriod) base else "$base · since tracking began"
}

@Composable
fun ChangeText(change: Double, percent: Double?, caption: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val up = change >= 0
        Text(
            "${if (up) "▲" else "▼"} ${Format.signedMoney(change)} (${Format.percent(percent)})",
            color = trendColor(up),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (caption != null) {
            Text(
                "  $caption",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Chart + period chips + header, the block used at the top of every detail screen. */
@Composable
fun PerformanceSection(
    title: String,
    series: List<Point>,
    changeFor: (Period) -> PeriodChange?,
    period: Period,
    onPeriod: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrub by remember { mutableStateOf<Point?>(null) }
    val change = changeFor(period)
    Column(modifier) {
        ValueHeader(title, series.lastOrNull()?.value ?: 0.0, change, scrub, Modifier.padding(horizontal = 16.dp))
        Box(Modifier.padding(vertical = 12.dp)) {
            LineChart(
                points = change?.points.orEmpty(),
                color = trendColor(change?.isUp ?: true),
                baseline = change?.start,
                onScrub = { scrub = it },
            )
        }
        PeriodSelector(period, onPeriod, Modifier.padding(horizontal = 16.dp))
    }
}

/** Compact change for list rows: "+$12.34 (+0.5%)". */
@Composable
fun SmallChange(change: PeriodChange?, modifier: Modifier = Modifier) {
    if (change == null) return
    Text(
        "${Format.signedMoney(change.change)} (${Format.percent(change.percent)})",
        color = trendColor(change.isUp),
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}
