package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.EnergyUnit
import com.apoorvdarshan.calorietracker.models.FastingSession
import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.models.FoodSource
import com.apoorvdarshan.calorietracker.models.MealType
import com.apoorvdarshan.calorietracker.models.UserProfile
import com.apoorvdarshan.calorietracker.models.WaterEntry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class DayReportTest {
    private val day = LocalDate.of(2026, 10, 26)

    private fun food(
        name: String,
        at: String,
        calories: Int,
        protein: Double = 0.0,
        calcium: Double? = null,
        mealType: MealType = MealType.LUNCH
    ) = FoodEntry(
        name = name,
        calories = calories,
        protein = protein,
        carbs = 10.0,
        fat = 5.0,
        timestamp = Instant.parse(at),
        source = FoodSource.MANUAL,
        mealType = mealType,
        calcium = calcium
    )

    private fun input(
        foods: List<FoodEntry>,
        waterGoalMl: Int? = null,
        water: List<WaterEntry> = emptyList(),
        fasting: List<FastingSession> = emptyList(),
        steps: Int? = null
    ) = DayReportInput(
        date = day,
        profile = UserProfile(),
        foods = foods,
        waterEntries = water,
        waterGoalMl = waterGoalMl,
        fastingSessions = fasting,
        steps = steps,
        zone = ZoneOffset.UTC
    )

    private fun report(input: DayReportInput): JsonObject = JsonParser.parseString(DayReport.json(input)).asJsonObject

    @Test
    fun onlyTheDaysMealsAreListedAndTotalled() {
        val json = report(
            input(
                listOf(
                    food("Oats", "2026-10-26T08:00:00Z", 300, protein = 10.0, mealType = MealType.BREAKFAST),
                    food("Salad", "2026-10-26T12:30:00Z", 450, protein = 20.0),
                    food("Yesterday's pizza", "2026-10-25T19:00:00Z", 900, protein = 30.0),
                    food("Tomorrow's toast", "2026-10-27T08:00:00Z", 200)
                )
            )
        )
        val meals = json.getAsJsonArray("meals")
        assertEquals(2, meals.size())
        assertEquals("Oats", meals[0].asJsonObject["name"].asString)
        assertEquals("08:00", meals[0].asJsonObject["time"].asString)
        assertEquals("breakfast", meals[0].asJsonObject["meal_type"].asString)
        val totals = json.getAsJsonObject("totals")
        assertEquals(750, totals["kcal"].asInt)
        assertEquals(30.0, totals["protein_g"].asDouble, 0.001)
    }

    @Test
    fun mealNutrientsListOnlyWhatWasRecorded() {
        val json = report(
            input(
                listOf(
                    food("Yoghurt", "2026-10-26T09:00:00Z", 150, calcium = 240.0),
                    food("Rice", "2026-10-26T13:00:00Z", 200)
                )
            )
        )
        val meals = json.getAsJsonArray("meals")
        assertEquals(240.0, meals[0].asJsonObject.getAsJsonObject("nutrients")["calcium_mg"].asDouble, 0.001)
        assertFalse(meals[1].asJsonObject.has("nutrients"))
        val calcium = json.getAsJsonArray("nutrients").map { it.asJsonObject }.single { it["name"].asString == "Calcium" }
        assertEquals(240.0, calcium["total"].asDouble, 0.001)
        assertEquals("mg", calcium["unit"].asString)
    }

    @Test
    fun previousDaysCoverTheWeekBeforeInOrder() {
        val json = report(
            input(
                listOf(
                    food("Today", "2026-10-26T12:00:00Z", 500),
                    food("Two days ago", "2026-10-24T12:00:00Z", 1800, protein = 90.0)
                )
            )
        )
        val previous = json.getAsJsonArray("previous_days").map { it.asJsonObject }
        assertEquals(7, previous.size)
        assertEquals("2026-10-19", previous.first()["date"].asString)
        assertEquals("2026-10-25", previous.last()["date"].asString)
        val twoDaysAgo = previous.single { it["date"].asString == "2026-10-24" }
        assertTrue(twoDaysAgo["logged"].asBoolean)
        assertEquals(1800, twoDaysAgo["kcal"].asInt)
        assertFalse(previous.last()["logged"].asBoolean)
        assertTrue(previous.last()["kcal"].isJsonNull)
    }

    @Test
    fun untrackedSectionsAreLeftOutInsteadOfReportedAsZero() {
        val json = report(input(listOf(food("Lunch", "2026-10-26T12:00:00Z", 500))))
        assertFalse(json.has("water"))
        assertFalse(json.has("fasting"))
        assertFalse(json.has("activity"))
        assertFalse(json.has("weight_kg"))
    }

    @Test
    fun trackedSectionsAreIncluded() {
        val json = report(
            input(
                foods = listOf(food("Lunch", "2026-10-26T12:00:00Z", 500)),
                waterGoalMl = 2000,
                water = listOf(
                    WaterEntry(date = Instant.parse("2026-10-26T10:00:00Z"), milliliters = 500),
                    WaterEntry(date = Instant.parse("2026-10-25T10:00:00Z"), milliliters = 700)
                ),
                fasting = listOf(
                    FastingSession(
                        startedAt = Instant.parse("2026-10-25T20:00:00Z"),
                        endedAt = Instant.parse("2026-10-26T12:00:00Z"),
                        goalMinutes = 16 * 60
                    )
                ),
                steps = 8421
            )
        )
        val water = json.getAsJsonObject("water")
        assertEquals(500, water["total_ml"].asInt)
        assertEquals(2000, water["goal_ml"].asInt)
        val fast = json.getAsJsonArray("fasting")[0].asJsonObject
        assertEquals(16 * 60, fast["duration_minutes"].asInt)
        assertTrue(fast["goal_reached"].asBoolean)
        assertEquals(8421, json.getAsJsonObject("activity")["steps"].asInt)
    }

    @Test
    fun promptCarriesTheJsonLanguageAndEnergyUnit() {
        val kcal = DayReport.prompt("{\"date\":\"2026-10-26\"}", EnergyUnit.KCAL, "Dutch")
        assertTrue(kcal.contains("Reply in Dutch"))
        assertTrue(kcal.endsWith("{\"date\":\"2026-10-26\"}"))
        assertFalse(kcal.contains("kilojoules"))
        assertTrue(DayReport.prompt("{}", EnergyUnit.KJ, "English").contains("kilojoules"))
    }

    @Test
    fun theDefaultStyleAsksForHeadingsInTheReplyLanguage() {
        val prompt = DayReport.prompt("{}", EnergyUnit.KCAL, "Dutch")
        assertTrue(prompt.contains(DayReport.DEFAULT_STYLE))
        assertTrue(DayReport.DEFAULT_STYLE.contains("headings translated into the reply language"))
    }

    @Test
    fun aCustomStyleReplacesTheDefaultButKeepsTheFixedParts() {
        val prompt = DayReport.prompt("{\"date\":\"x\"}", EnergyUnit.KCAL, "German", style = "  Two sentences, blunt.  ")
        assertTrue(prompt.contains("Two sentences, blunt."))
        assertFalse(prompt.contains(DayReport.DEFAULT_STYLE))
        assertTrue(prompt.contains("Reply in German"))
        assertTrue(prompt.contains("Do not call tools"))
        assertTrue(prompt.endsWith("{\"date\":\"x\"}"))
        assertTrue(DayReport.prompt("{}", EnergyUnit.KCAL, "German", style = "   ").contains(DayReport.DEFAULT_STYLE))
    }

    @Test
    fun aDayIsCompleteWhenPastOrTodayFromTheEvening() {
        val today = LocalDate.of(2026, 10, 27)
        assertTrue(DayReport.isComplete(today.minusDays(1), today, LocalTime.of(7, 0)))
        assertFalse(DayReport.isComplete(today, today, LocalTime.of(17, 59)))
        assertTrue(DayReport.isComplete(today, today, LocalTime.of(18, 0)))
        assertFalse(DayReport.isComplete(today.plusDays(1), today, LocalTime.of(23, 0)))
    }
}
