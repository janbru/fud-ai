package com.apoorvdarshan.calorietracker.ui.home

import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.models.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class WeekBudgetTest {
    private val zone = ZoneId.of("UTC")
    // Wednesday; the Monday-based week runs 2026-10-19 .. 2026-10-25.
    private val wednesday = LocalDate.of(2026, 10, 21)

    private fun entry(date: LocalDate, kcal: Int) = FoodEntry(
        name = "x",
        calories = kcal,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant(),
        source = FoodSource.TEXT_INPUT
    )

    private fun compute(entries: List<FoodEntry>, selected: LocalDate = wednesday, today: LocalDate = wednesday) =
        WeekBudget.compute(entries, 2000, selected, today, DayOfWeek.MONDAY, zone)

    @Test
    fun budgetIsSevenDaysOfTheGoal() {
        val budget = compute(emptyList())
        assertEquals(14_000, budget.budget)
        assertEquals(LocalDate.of(2026, 10, 19), budget.days.first().date)
        assertEquals(LocalDate.of(2026, 10, 25), budget.days.last().date)
    }

    @Test
    fun underEatingEarlyInTheWeekCreatesRoomForATreat() {
        val entries = listOf(
            entry(LocalDate.of(2026, 10, 19), 1800),
            entry(LocalDate.of(2026, 10, 20), 1800),
            entry(wednesday, 1800)
        )
        val budget = compute(entries)
        assertEquals(5_400, budget.consumed)
        assertEquals(8_600, budget.remaining)
        assertEquals(5, budget.daysLeft) // Wed..Sun, today included
        assertEquals(1_720, budget.averagePerRemainingDay)
        // 8600 - 5 * 2000 = -1400: eating the goal every day would break the budget.
        assertEquals(-1_400, budget.extraRoom)
    }

    @Test
    fun eatingBelowTheGoalIsNotEnoughWhenFewDaysAreLeft() {
        val entries = listOf(
            entry(LocalDate.of(2026, 10, 19), 1500),
            entry(LocalDate.of(2026, 10, 20), 1500),
            entry(wednesday, 1500)
        )
        val budget = compute(entries)
        // 14000 - 4500 = 9500 remaining, but 5 days at the goal need 10000.
        assertEquals(9_500, budget.remaining)
        assertEquals(-500, budget.extraRoom)
    }

    @Test
    fun roomIsPositiveWhenDaysLeftNeedLessThanTheRemainder() {
        val entries = listOf(
            entry(LocalDate.of(2026, 10, 19), 1000),
            entry(LocalDate.of(2026, 10, 20), 1000)
        )
        val budget = compute(entries, today = LocalDate.of(2026, 10, 21))
        // 14000 - 2000 = 12000 remaining, 5 days left need 10000 -> 2000 spare.
        assertEquals(2_000, budget.extraRoom)
    }

    @Test
    fun entriesOutsideTheWeekAreIgnored() {
        val entries = listOf(
            entry(LocalDate.of(2026, 10, 18), 3000),
            entry(LocalDate.of(2026, 10, 26), 3000),
            entry(wednesday, 500)
        )
        assertEquals(500, compute(entries).consumed)
    }

    @Test
    fun aFinishedWeekHasNoRemainingDays() {
        val budget = compute(emptyList(), selected = LocalDate.of(2026, 10, 14), today = wednesday)
        assertEquals(0, budget.daysLeft)
        assertNull(budget.averagePerRemainingDay)
        assertTrue(budget.days.none { it.isFuture })
    }

    @Test
    fun sundayStartShiftsTheWeek() {
        val budget = WeekBudget.compute(emptyList(), 2000, wednesday, wednesday, DayOfWeek.SUNDAY, zone)
        assertEquals(LocalDate.of(2026, 10, 18), budget.days.first().date)
    }
}
