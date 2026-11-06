package com.webforks.networth.domain

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object Format {
    private val usd = NumberFormat.getCurrencyInstance(Locale.US)
    private val usdWhole = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }

    fun money(value: Double, whole: Boolean = false): String =
        (if (whole) usdWhole else usd).format(value)

    /** "+$1,234.56" / "−$12.00" */
    fun signedMoney(value: Double, whole: Boolean = false): String {
        val sign = if (value < 0) "−" else "+"
        return sign + money(abs(value), whole)
    }

    fun percent(value: Double?): String =
        if (value == null) "—" else String.format(Locale.US, "%s%.2f%%", if (value < 0) "−" else "+", abs(value) * 100)

    /** "$1.2M", "$845K": for tight spaces like chart axes. */
    fun compact(value: Double): String {
        val a = abs(value)
        val sign = if (value < 0) "−" else ""
        return when {
            a >= 1_000_000 -> String.format(Locale.US, "%s$%.2fM", sign, a / 1_000_000)
            a >= 10_000 -> String.format(Locale.US, "%s$%.0fK", sign, a / 1_000)
            a >= 1_000 -> String.format(Locale.US, "%s$%.1fK", sign, a / 1_000)
            else -> String.format(Locale.US, "%s$%.0f", sign, a)
        }
    }

    fun shares(q: Double): String =
        if (q == q.toLong().toDouble()) q.toLong().toString() else String.format(Locale.US, "%.4f", q).trimEnd('0')

    fun ago(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
        if (timestamp == null) return "never"
        val mins = (now - timestamp) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 48 * 60 -> "${mins / 60}h ago"
            else -> "${mins / (24 * 60)}d ago"
        }
    }

    const val HIDDEN = "••••••"
}
