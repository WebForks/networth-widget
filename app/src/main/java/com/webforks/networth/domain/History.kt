package com.webforks.networth.domain

import java.time.LocalDate

/** A point on a value-over-time chart. */
data class Point(val epochDay: Long, val value: Double)

enum class Period(val label: String) {
    D1("1D"), W1("1W"), M1("1M"), M3("3M"), YTD("YTD"), Y1("1Y"), ALL("All");

    /** First day of the window that ends on [today]. */
    fun startDay(today: LocalDate): LocalDate = when (this) {
        D1 -> today.minusDays(1)
        W1 -> today.minusWeeks(1)
        M1 -> today.minusMonths(1)
        M3 -> today.minusMonths(3)
        // Change since the close of the last day of the previous year.
        YTD -> LocalDate.of(today.year, 1, 1).minusDays(1)
        Y1 -> today.minusYears(1)
        ALL -> LocalDate.MIN
    }
}

data class PeriodChange(
    val period: Period,
    val start: Double,
    val end: Double,
    val points: List<Point>,
    /** False when tracking began after the period's start, so the change covers less time. */
    val coversFullPeriod: Boolean,
) {
    val change: Double get() = end - start
    val percent: Double? get() = if (start == 0.0) null else change / kotlin.math.abs(start)
    val isUp: Boolean get() = change >= 0
}

/** A raw stored observation: one series (account, holding, ...) on one day. */
data class Observation(val seriesId: String, val epochDay: Long, val value: Double)

object History {

    /**
     * Sums several series into one daily total from the earliest observation to [today].
     *
     * Each series carries its last value forward across days with no data (weekends, missed
     * refreshes). Before a series' first observation, its first value is carried backward.
     * That way linking a new account doesn't look like a sudden jump in net worth.
     */
    fun dailyTotals(observations: List<Observation>, today: Long): List<Point> {
        if (observations.isEmpty()) return emptyList()
        val bySeries = observations.groupBy { it.seriesId }
            .mapValues { (_, obs) -> obs.sortedBy { it.epochDay } }
        val firstDay = observations.minOf { it.epochDay }
        val lastDay = maxOf(today, observations.maxOf { it.epochDay })

        val totals = DoubleArray((lastDay - firstDay + 1).toInt())
        for ((_, obs) in bySeries) {
            var idx = 0
            var current = obs.first().value
            for (d in firstDay..lastDay) {
                while (idx < obs.size && obs[idx].epochDay <= d) {
                    current = obs[idx].value
                    idx++
                }
                totals[(d - firstDay).toInt()] += current
            }
        }
        return totals.mapIndexed { i, v -> Point(firstDay + i, v) }
    }

    /** Change over [period], using a series from [dailyTotals]. */
    fun change(series: List<Point>, period: Period, today: LocalDate): PeriodChange? {
        if (series.isEmpty()) return null
        val end = series.last()

        if (period == Period.D1) return dayChange(series)

        val wantedStart = period.startDay(today).let {
            if (it == LocalDate.MIN) Long.MIN_VALUE else it.toEpochDay()
        }
        val window = series.filter { it.epochDay >= wantedStart }
        val startPoint = window.firstOrNull() ?: end
        return PeriodChange(
            period = period,
            start = startPoint.value,
            end = end.value,
            points = window.ifEmpty { listOf(end) },
            coversFullPeriod = period == Period.ALL || series.first().epochDay <= wantedStart,
        )
    }

    /**
     * The most recent day-over-day move. On weekends and holidays nothing moves, so this
     * steps back to the last day the total actually changed, the way brokerage apps show
     * Friday's move on Saturday.
     */
    fun dayChange(series: List<Point>): PeriodChange? {
        if (series.isEmpty()) return null
        val end = series.last()
        var i = series.lastIndex
        // Find the last day whose value differs from the day before it.
        while (i > 0 && series[i - 1].value == series[i].value) i--
        val startIdx = (i - 1).coerceAtLeast(0)
        val start = series[startIdx]
        return PeriodChange(
            period = Period.D1,
            start = start.value,
            end = end.value,
            points = series.subList(startIdx, series.size),
            coversFullPeriod = series.size > 1,
        )
    }
}
