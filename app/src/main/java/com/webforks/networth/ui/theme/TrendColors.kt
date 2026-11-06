package com.webforks.networth.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Gain/loss colors, tuned to stay readable on both light and dark surfaces. */
object TrendColors {
    val UpLight = Color(0xFF12813A)
    val UpDark = Color(0xFF3DD68C)
    val DownLight = Color(0xFFC62828)
    val DownDark = Color(0xFFFF6B6B)

    fun up(dark: Boolean) = if (dark) UpDark else UpLight
    fun down(dark: Boolean) = if (dark) DownDark else DownLight
    fun of(isUp: Boolean, dark: Boolean) = if (isUp) up(dark) else down(dark)
    fun argb(isUp: Boolean, dark: Boolean) = of(isUp, dark).toArgb()
}
