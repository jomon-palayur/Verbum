package com.verbum.app.liturgy

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class LiturgicalSeason { ADVENT, CHRISTMAS, LENT, EASTER, ORDINARY_TIME }
enum class LiturgicalColor { GREEN, VIOLET, WHITE, RED, ROSE }
enum class SundayCycle { A, B, C }
enum class WeekdayCycle { I, II }

data class LiturgicalDay(
    val date: LocalDate,
    val title: String,
    val season: LiturgicalSeason,
    val color: LiturgicalColor,
    val key: String,
    val weekNumber: Int?,
    val weekdayCycle: WeekdayCycle,
    val sundayCycle: SundayCycle,
    val liturgicalYear: Int,
    val celebrationRank: String? = null,
)

/** Computes the General Roman Calendar's temporal cycle and common fixed celebrations. */
object LiturgicalCalendarEngine {
    fun calculate(date: LocalDate): LiturgicalDay {
        val currentAdvent = firstSundayOfAdvent(date.year)
        val liturgicalYear = if (!date.isBefore(currentAdvent)) date.year + 1 else date.year
        val easter = easterSunday(date.year)
        val season = seasonFor(date, easter)
        val weekdayCycle = if (liturgicalYear % 2 == 0) WeekdayCycle.II else WeekdayCycle.I
        val sundayCycle = when (liturgicalYear % 3) {
            1 -> SundayCycle.A
            2 -> SundayCycle.B
            else -> SundayCycle.C
        }
        val ordinaryWeek = ordinaryWeekNumber(date, easter)

        val fixedCelebration = FIXED_CELEBRATIONS[date.monthValue to date.dayOfMonth]
        if (fixedCelebration != null && canObserveFixedCelebration(date, easter, fixedCelebration)) {
            return LiturgicalDay(
                date = date,
                title = fixedCelebration.title,
                season = season,
                color = fixedCelebration.color,
                key = "FEAST_%02d_%02d".format(date.monthValue, date.dayOfMonth),
                weekNumber = ordinaryWeek,
                weekdayCycle = weekdayCycle,
                sundayCycle = sundayCycle,
                liturgicalYear = liturgicalYear,
                celebrationRank = fixedCelebration.rank,
            )
        }

        val title: String
        val key: String
        val color: LiturgicalColor
        when (season) {
            LiturgicalSeason.ADVENT -> {
                val adventWeek = ((ChronoUnit.DAYS.between(firstSundayOfAdvent(date.year), date)) / 7 + 1).toInt()
                title = "${ordinal(adventWeek)} Week of Advent, ${date.dayOfWeek.displayName()}"
                key = "ADVENT_WK${adventWeek}_${date.dayOfWeek.name.take(3)}"
                color = if (adventWeek == 3 && date.dayOfWeek == DayOfWeek.SUNDAY) LiturgicalColor.ROSE else LiturgicalColor.VIOLET
            }
            LiturgicalSeason.CHRISTMAS -> {
                title = christmasTitle(date)
                key = "CHRISTMAS_${date.monthValue}_${date.dayOfMonth}_${date.dayOfWeek.name.take(3)}"
                color = LiturgicalColor.WHITE
            }
            LiturgicalSeason.LENT -> {
                title = if (date == easter.minusDays(7)) "Palm Sunday of the Passion of the Lord" else "${ordinal(lentenWeek(date, easter))} Week of Lent, ${date.dayOfWeek.displayName()}"
                key = "LENT_WK${lentenWeek(date, easter)}_${date.dayOfWeek.name.take(3)}"
                color = when {
                    date == easter.minusDays(7) || date == easter.minusDays(2) -> LiturgicalColor.RED
                    date == easter.minusDays(3) -> LiturgicalColor.WHITE
                    date == easter.minusDays(21) -> LiturgicalColor.ROSE
                    else -> LiturgicalColor.VIOLET
                }
            }
            LiturgicalSeason.EASTER -> {
                val easterWeek = ((ChronoUnit.DAYS.between(easter, date)) / 7 + 1).toInt()
                title = if (date == easter) "Easter Sunday of the Resurrection of the Lord" else "${ordinal(easterWeek)} Week of Easter, ${date.dayOfWeek.displayName()}"
                key = if (date == easter) "EASTER_SUNDAY" else "EASTER_WK${easterWeek}_${date.dayOfWeek.name.take(3)}"
                color = if (date == easter.plusDays(49)) LiturgicalColor.RED else LiturgicalColor.WHITE
            }
            LiturgicalSeason.ORDINARY_TIME -> {
                val week = ordinaryWeek ?: 1
                title = "${date.dayOfWeek.displayName()} of the ${ordinal(week)} Week in Ordinary Time"
                key = "ORDINARY_WK${week}_${date.dayOfWeek.name.take(3)}_${weekdayCycle.name}"
                color = LiturgicalColor.GREEN
            }
        }
        return LiturgicalDay(
            date = date,
            title = title,
            season = season,
            color = color,
            key = key,
            weekNumber = ordinaryWeek,
            weekdayCycle = weekdayCycle,
            sundayCycle = sundayCycle,
            liturgicalYear = liturgicalYear,
        )
    }

    fun easterSunday(year: Int): LocalDate {
        require(year >= 1583) { "Gregorian Easter is defined here for Gregorian calendar years from 1583 onward" }
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    fun firstSundayOfAdvent(year: Int): LocalDate =
        LocalDate.of(year, 11, 27).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    private fun seasonFor(date: LocalDate, easter: LocalDate): LiturgicalSeason {
        val advent = firstSundayOfAdvent(date.year)
        if (!date.isBefore(advent) && date.isBefore(LocalDate.of(date.year, 12, 25))) {
            return LiturgicalSeason.ADVENT
        }
        val baptism = baptismOfTheLord(date.year)
        if (date.monthValue == 1 && !date.isAfter(baptism)) return LiturgicalSeason.CHRISTMAS
        if (date.monthValue == 12 && date.dayOfMonth >= 25) return LiturgicalSeason.CHRISTMAS
        if (!date.isBefore(easter.minusDays(46)) && date.isBefore(easter)) return LiturgicalSeason.LENT
        if (!date.isBefore(easter) && !date.isAfter(easter.plusDays(49))) return LiturgicalSeason.EASTER
        return LiturgicalSeason.ORDINARY_TIME
    }

    private fun ordinaryWeekNumber(date: LocalDate, easter: LocalDate): Int? {
        if (seasonFor(date, easter) != LiturgicalSeason.ORDINARY_TIME) return null
        val baptism = baptismOfTheLord(date.year)
        val firstWeekMonday = baptism.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val ashWednesday = easter.minusDays(46)
        if (date.isBefore(ashWednesday)) {
            val base = (ChronoUnit.DAYS.between(firstWeekMonday, date) / 7).toInt() + 1
            return base + if (date.dayOfWeek == DayOfWeek.SUNDAY) 1 else 0
        }
        val pentecost = easter.plusDays(49)
        val postEasterMonday = pentecost.plusDays(1)
        if (!date.isBefore(postEasterMonday)) {
            val lastOrdinarySunday = firstSundayOfAdvent(date.year).minusDays(7)
            val mondayAfterLastOrdinarySunday = lastOrdinarySunday.plusDays(1)
            val remainingWeeks = (ChronoUnit.DAYS.between(postEasterMonday, mondayAfterLastOrdinarySunday) / 7).toInt() + 1
            val firstWeekAfterPentecost = 35 - remainingWeeks
            val base = firstWeekAfterPentecost + (ChronoUnit.DAYS.between(postEasterMonday, date) / 7).toInt()
            return base + if (date.dayOfWeek == DayOfWeek.SUNDAY) 1 else 0
        }
        return null
    }

    private fun baptismOfTheLord(year: Int): LocalDate =
        LocalDate.of(year, 1, 6).with(TemporalAdjusters.next(DayOfWeek.SUNDAY))

    private fun lentenWeek(date: LocalDate, easter: LocalDate): Int {
        val firstSundayOfLent = easter.minusDays(42)
        return ((ChronoUnit.DAYS.between(firstSundayOfLent, date)) / 7 + 1).toInt().coerceAtLeast(1)
    }

    private fun christmasTitle(date: LocalDate): String = when {
        date.monthValue == 12 && date.dayOfMonth == 25 -> "The Nativity of the Lord"
        date.monthValue == 1 && date.dayOfMonth == 1 -> "Mary, the Holy Mother of God"
        date.monthValue == 1 -> "Christmas Week, ${date.dayOfWeek.displayName()}"
        else -> "The Nativity of the Lord"
    }

    private fun canObserveFixedCelebration(date: LocalDate, easter: LocalDate, celebration: FixedCelebration): Boolean {
        if (date in easter.minusDays(7)..easter.plusDays(7)) return false
        if (date.dayOfWeek == DayOfWeek.SUNDAY && celebration.rank != "SOLEMNITY") return false
        if (date.dayOfWeek == DayOfWeek.SUNDAY && seasonFor(date, easter) in setOf(LiturgicalSeason.ADVENT, LiturgicalSeason.LENT, LiturgicalSeason.EASTER)) return false
        if (date.monthValue in 11..12 && date >= firstSundayOfAdvent(date.year) && celebration.rank != "SOLEMNITY") return false
        return true
    }

    private fun DayOfWeek.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

    private fun ordinal(value: Int): String = when (value % 100) {
        11, 12, 13 -> "${value}th"
        else -> when (value % 10) {
            1 -> "${value}st"
            2 -> "${value}nd"
            3 -> "${value}rd"
            else -> "${value}th"
        }
    }

    private data class FixedCelebration(val title: String, val color: LiturgicalColor, val rank: String)

    private val FIXED_CELEBRATIONS = mapOf(
        (1 to 1) to FixedCelebration("Mary, the Holy Mother of God", LiturgicalColor.WHITE, "SOLEMNITY"),
        (1 to 6) to FixedCelebration("The Epiphany of the Lord", LiturgicalColor.WHITE, "SOLEMNITY"),
        (2 to 2) to FixedCelebration("The Presentation of the Lord", LiturgicalColor.WHITE, "FEAST"),
        (3 to 19) to FixedCelebration("Saint Joseph, Spouse of the Blessed Virgin Mary", LiturgicalColor.WHITE, "SOLEMNITY"),
        (3 to 25) to FixedCelebration("The Annunciation of the Lord", LiturgicalColor.WHITE, "SOLEMNITY"),
        (6 to 24) to FixedCelebration("The Nativity of Saint John the Baptist", LiturgicalColor.WHITE, "SOLEMNITY"),
        (6 to 29) to FixedCelebration("Saints Peter and Paul, Apostles", LiturgicalColor.RED, "SOLEMNITY"),
        (8 to 6) to FixedCelebration("The Transfiguration of the Lord", LiturgicalColor.WHITE, "FEAST"),
        (8 to 15) to FixedCelebration("The Assumption of the Blessed Virgin Mary", LiturgicalColor.WHITE, "SOLEMNITY"),
        (9 to 14) to FixedCelebration("The Exaltation of the Holy Cross", LiturgicalColor.RED, "FEAST"),
        (10 to 28) to FixedCelebration("Saints Simon and Jude, Apostles", LiturgicalColor.RED, "FEAST"),
        (11 to 1) to FixedCelebration("All Saints", LiturgicalColor.WHITE, "SOLEMNITY"),
        (11 to 30) to FixedCelebration("Saint Andrew, Apostle", LiturgicalColor.RED, "FEAST"),
        (12 to 8) to FixedCelebration("The Immaculate Conception of the Blessed Virgin Mary", LiturgicalColor.WHITE, "SOLEMNITY"),
        (12 to 25) to FixedCelebration("The Nativity of the Lord", LiturgicalColor.WHITE, "SOLEMNITY"),
        (12 to 26) to FixedCelebration("Saint Stephen, First Martyr", LiturgicalColor.RED, "FEAST"),
        (12 to 27) to FixedCelebration("Saint John, Apostle and Evangelist", LiturgicalColor.WHITE, "FEAST"),
        (12 to 28) to FixedCelebration("The Holy Innocents, Martyrs", LiturgicalColor.RED, "FEAST"),
    )
}
