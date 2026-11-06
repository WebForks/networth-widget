package com.webforks.networth.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.webforks.networth.domain.Point

/**
 * Draws a trend line into a bitmap. Widgets are RemoteViews and can't run Compose Canvas
 * drawing, so the line is rendered here and shown as an image.
 */
object Sparkline {
    fun render(points: List<Point>, widthPx: Int, heightPx: Int, color: Int): Bitmap {
        val w = widthPx.coerceIn(40, 1200)
        val h = heightPx.coerceIn(20, 600)
        val bmp = createBitmap(w, h)
        if (points.size < 2) return bmp
        val canvas = Canvas(bmp)

        val stroke = h * 0.04f + 2f
        val pad = stroke
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val first = points.first().epochDay
        val span = (points.last().epochDay - first).coerceAtLeast(1)

        fun x(p: Point) = pad + (w - 2 * pad) * ((p.epochDay - first).toFloat() / span)
        fun y(p: Point) =
            if (max == min) h / 2f
            else pad + (h - 2 * pad) * (1f - ((p.value - min) / range).toFloat())

        val line = Path().apply {
            moveTo(x(points[0]), y(points[0]))
            for (i in 1 until points.size) lineTo(x(points[i]), y(points[i]))
        }
        val fill = Path(line).apply {
            lineTo(x(points.last()), h.toFloat())
            lineTo(x(points.first()), h.toFloat())
            close()
        }

        val alpha = color and 0x00FFFFFF
        canvas.drawPath(
            fill,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    alpha or (0x40 shl 24), alpha, Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawPath(
            line,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                this.color = color
            },
        )
        return bmp
    }
}
