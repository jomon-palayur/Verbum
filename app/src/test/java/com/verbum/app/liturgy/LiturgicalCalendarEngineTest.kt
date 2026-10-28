package com.verbum.app.liturgy

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LiturgicalCalendarEngineTest {
    @Test fun gregorianEasterMatchesPublishedDates() {
        assertEquals(LocalDate.of(2025, 4, 20), LiturgicalCalendarEngine.easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), LiturgicalCalendarEngine.easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), LiturgicalCalendarEngine.easterSunday(2027))
    }

    @Test fun feastOfSimonAndJudeHasExpectedLiturgicalProperties() {
        val day = LiturgicalCalendarEngine.calculate(LocalDate.of(2026, 10, 28))
        assertEquals("Saints Simon and Jude, Apostles", day.title)
        assertEquals(LiturgicalColor.RED, day.color)
        assertEquals("FEAST_10_28", day.key)
        assertEquals(WeekdayCycle.II, day.weekdayCycle)
        assertEquals(SundayCycle.A, day.sundayCycle)
    }

    @Test fun cycleChangesAtFirstSundayOfAdvent() {
        val dayBefore = LiturgicalCalendarEngine.calculate(LocalDate.of(2026, 11, 28))
        val firstSunday = LiturgicalCalendarEngine.calculate(LocalDate.of(2026, 11, 29))
        assertEquals(2026, dayBefore.liturgicalYear)
        assertEquals(2027, firstSunday.liturgicalYear)
        assertEquals(SundayCycle.B, firstSunday.sundayCycle)
        assertEquals(LiturgicalSeason.ADVENT, firstSunday.season)
        assertEquals(LiturgicalColor.VIOLET, firstSunday.color)
    }

    @Test fun ordinaryTimeResumesWithCorrectWeekAfterPentecost() {
        val monday = LiturgicalCalendarEngine.calculate(LocalDate.of(2026, 6, 1))
        assertEquals(LiturgicalSeason.ORDINARY_TIME, monday.season)
        assertEquals(9, monday.weekNumber)
        assertEquals("ORDINARY_WK9_MON_II", monday.key)
    }

    @Test fun ordinarySundayTakesPrecedenceOverApostleFeast() {
        val day = LiturgicalCalendarEngine.calculate(LocalDate.of(2029, 10, 28))
        assertEquals(LiturgicalSeason.ORDINARY_TIME, day.season)
        assertEquals("ORDINARY_WK30_SUN_I", day.key)
    }

    @Test fun roseIsUsedOnGaudeteAndLaetareSundays() {
        val gaudete = LiturgicalCalendarEngine.calculate(LocalDate.of(2026, 12, 13))
        val laetare = LiturgicalCalendarEngine.calculate(LocalDate.of(2026, 3, 15))
        assertEquals(LiturgicalColor.ROSE, gaudete.color)
        assertEquals(LiturgicalColor.ROSE, laetare.color)
    }
}
