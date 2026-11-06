package com.webforks.networth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryTest {

    private val today = LocalDate.of(2026, 11, 5)
    private val t = today.toEpochDay()

    @Test
    fun `forward fills gaps and back fills newly linked accounts`() {
        val obs = listOf(
            Observation("bank", t - 3, 100.0),
            Observation("bank", t - 1, 150.0),
            // 401k linked yesterday: must not look like a jump on that day.
            Observation("401k", t - 1, 1000.0),
            Observation("401k", t, 1010.0),
        )
        val series = History.dailyTotals(obs, t)
        assertEquals(listOf(1100.0, 1100.0, 1150.0, 1160.0), series.map { it.value })
        assertEquals(t - 3, series.first().epochDay)
        assertEquals(t, series.last().epochDay)
    }

    @Test
    fun `extends to today when no refresh happened today`() {
        val series = History.dailyTotals(listOf(Observation("a", t - 2, 5.0)), t)
        assertEquals(3, series.size)
        assertEquals(5.0, series.last().value, 0.0)
    }

    @Test
    fun `day change skips flat weekend days`() {
        // Thu 100 -> Fri 110, then Sat/Sun unchanged.
        val series = listOf(100.0, 110.0, 110.0, 110.0).mapIndexed { i, v -> Point(t - 3 + i, v) }
        val d = History.dayChange(series)!!
        assertEquals(10.0, d.change, 1e-9)
        assertEquals(0.1, d.percent!!, 1e-9)
    }

    @Test
    fun `day change uses yesterday when today moved`() {
        val series = listOf(100.0, 90.0).mapIndexed { i, v -> Point(t - 1 + i, v) }
        val d = History.dayChange(series)!!
        assertEquals(-10.0, d.change, 1e-9)
        assertFalse(d.isUp)
    }

    @Test
    fun `ytd starts at last close of previous year`() {
        val start = LocalDate.of(2025, 12, 31).toEpochDay()
        val series = (start - 5..t).map { Point(it, if (it <= start) 1000.0 else 1200.0) }
        val ytd = History.change(series, Period.YTD, today)!!
        assertEquals(1000.0, ytd.start, 0.0)
        assertEquals(200.0, ytd.change, 1e-9)
        assertTrue(ytd.coversFullPeriod)
    }

    @Test
    fun `period longer than history is flagged as partial`() {
        val series = (t - 10..t).map { Point(it, it.toDouble()) }
        val y1 = History.change(series, Period.Y1, today)!!
        assertFalse(y1.coversFullPeriod)
        assertEquals(10.0, y1.change, 1e-9)
    }
}
