package com.helga.android

import com.helga.android.data.model.PlanPeriods
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PlanPeriodsTest {

    private val monday = DayOfWeek.MONDAY
    private val length = 7

    /** 2026-09-21 ist ein Montag, 2026-09-27 der zugehörige Sonntag. */
    private val mo = LocalDate.of(2026, 9, 21)
    private val di = LocalDate.of(2026, 9, 22)
    private val mi = LocalDate.of(2026, 9, 23)
    private val so = LocalDate.of(2026, 9, 27)

    @Test
    fun `regulaerer Zeitraum laeuft vom Einkaufstag bis zum Vortag des naechsten`() {
        val period = PlanPeriods.periodAt(LocalDate.of(2026, 9, 24), monday, length)
        assertEquals(mo, period.start)
        assertEquals(so, period.end)
        assertEquals(7, period.dayCount)
    }

    @Test
    fun `Einkaufstag Donnerstag verschiebt den Rasterstart`() {
        val period = PlanPeriods.periodAt(LocalDate.of(2026, 9, 24), DayOfWeek.THURSDAY, length)
        assertEquals(LocalDate.of(2026, 9, 24), period.start)
        assertEquals(LocalDate.of(2026, 9, 30), period.end)
    }

    @Test
    fun `Verkuerzung auf Mo-Di laesst die Folgewoche Mi-So laufen und danach wieder Mo-So`() {
        val overrides = PlanPeriods.withOverride(
            overrides = emptyMap(),
            currentStart = mo,
            newStart = mo,
            newEnd = di,
            anchor = monday,
            length = length,
            today = mo,
        )
        assertEquals(mapOf(mo to di), overrides)

        val current = PlanPeriods.periodFor(0, mo, monday, length, overrides)
        assertEquals(mo to di, current.start to current.end)

        val next = PlanPeriods.periodFor(1, mo, monday, length, overrides)
        assertEquals(mi to so, next.start to next.end)

        val afterNext = PlanPeriods.periodFor(2, mo, monday, length, overrides)
        assertEquals(LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 4), afterNext.start to afterNext.end)
    }

    @Test
    fun `Verlaengerung auf zwei Wochen laesst den Rhythmus danach wieder einrasten`() {
        val ende = LocalDate.of(2026, 9, 29) // Dienstag der Folgewoche
        val overrides = PlanPeriods.withOverride(emptyMap(), mo, mo, ende, monday, length, mo)

        val current = PlanPeriods.periodFor(0, mo, monday, length, overrides)
        assertEquals(mo to ende, current.start to current.end)
        assertEquals(9, current.dayCount)

        // Folgezeitraum füllt nur bis zur nächsten regulären Grenze auf …
        val next = PlanPeriods.periodFor(1, mo, monday, length, overrides)
        assertEquals(LocalDate.of(2026, 9, 30) to LocalDate.of(2026, 10, 4), next.start to next.end)

        // … danach läuft der gewohnte Montag-Rhythmus weiter.
        val afterNext = PlanPeriods.periodFor(2, mo, monday, length, overrides)
        assertEquals(LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 10, 11), afterNext.start to afterNext.end)
    }

    @Test
    fun `verschobener Start verkuerzt den vorhergehenden Zeitraum`() {
        val overrides = PlanPeriods.withOverride(emptyMap(), mo, mi, so, monday, length, mo)
        val previousStart = mo.minusDays(7)
        assertEquals(di, overrides[previousStart])

        val previous = PlanPeriods.periodFor(-1, mi, monday, length, overrides)
        assertEquals(previousStart to di, previous.start to previous.end)
        val current = PlanPeriods.periodFor(0, mi, monday, length, overrides)
        assertEquals(mi to so, current.start to current.end)
    }

    @Test
    fun `Ausnahme die dem Raster entspricht wird nicht gespeichert`() {
        val overrides = PlanPeriods.withOverride(emptyMap(), mo, mo, so, monday, length, mo)
        assertEquals(emptyMap<LocalDate, LocalDate>(), overrides)
    }

    @Test
    fun `Rueckwaerts- und Vorwaertsnavigation sind zueinander invers`() {
        val overrides = mapOf(mo to di)
        val forward = PlanPeriods.periodFor(3, mo, monday, length, overrides)
        val back = PlanPeriods.periodFor(0, mo, monday, length, overrides)
        assertEquals(back, PlanPeriods.periodAt(back.start, monday, length, overrides))
        assertEquals(forward, PlanPeriods.periodAt(forward.end, monday, length, overrides))
    }

    @Test
    fun `Kodierung und Dekodierung der Ausnahmen sind verlustfrei`() {
        val overrides = mapOf(mo to di, LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 10, 8))
        assertEquals(overrides, PlanPeriods.decodeOverrides(PlanPeriods.encodeOverrides(overrides)))
        assertEquals(emptyMap<LocalDate, LocalDate>(), PlanPeriods.decodeOverrides(""))
        assertEquals(emptyMap<LocalDate, LocalDate>(), PlanPeriods.decodeOverrides("kaputt;auch>kaputt"))
    }
}
