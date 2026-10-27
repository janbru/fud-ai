package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InflammationBalanceTest {
    private val goals = OptionalNutrientGoals.Default

    private fun food(
        calories: Int = 2000,
        fiber: Double? = null,
        omega3: Double? = null,
        vitaminC: Double? = null,
        magnesium: Double? = null,
        addedSugar: Double? = null,
        saturatedFat: Double? = null,
        polyunsaturatedFat: Double? = null
    ) = FoodEntry(
        name = "meal",
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 10.0,
        source = FoodSource.MANUAL,
        fiber = fiber,
        omega3 = omega3,
        vitaminC = vitaminC,
        magnesium = magnesium,
        addedSugar = addedSugar,
        saturatedFat = saturatedFat,
        polyunsaturatedFat = polyunsaturatedFat
    )

    @Test
    fun plantRichLowSugarDayLeansAnti() {
        val balance = InflammationBalance.evaluate(
            listOf(food(fiber = 32.0, omega3 = 2.0, vitaminC = 100.0, magnesium = 400.0, addedSugar = 5.0, saturatedFat = 8.0)),
            goals,
            targetCalories = 2000
        )!!
        assertEquals(InflammationLean.ANTI, balance.lean)
        assertTrue(balance.score < -0.25)
        assertTrue(balance.helping.any { it.nutrient == HomeTopNutrient.FIBER })
        assertTrue(balance.pushing.isEmpty())
    }

    @Test
    fun sugaryFatDayWithLittleFiberLeansPro() {
        val balance = InflammationBalance.evaluate(
            listOf(food(fiber = 6.0, omega3 = 0.1, vitaminC = 10.0, magnesium = 80.0, addedSugar = 60.0, saturatedFat = 35.0)),
            goals,
            targetCalories = 2000
        )!!
        assertEquals(InflammationLean.PRO, balance.lean)
        assertEquals(
            setOf(HomeTopNutrient.ADDED_SUGAR, HomeTopNutrient.SATURATED_FAT),
            balance.pushing.map { it.nutrient }.toSet()
        )
        assertTrue(balance.lacking.any { it.nutrient == HomeTopNutrient.FIBER })
    }

    @Test
    fun intakeIsJudgedPerCalorieAgainstTheGoal() {
        // Half the calories of the target with half the fiber is on target, not a shortfall.
        val balance = InflammationBalance.evaluate(
            listOf(food(calories = 1000, fiber = 15.0, omega3 = 1.0, vitaminC = 45.0, addedSugar = 5.0)),
            goals,
            targetCalories = 2000
        )!!
        assertEquals(1.0, balance.components.single { it.nutrient == HomeTopNutrient.FIBER }.ratio, 0.001)
    }

    @Test
    fun unrecordedNutrientsAreLeftOutInsteadOfCountedAsZero() {
        val balance = InflammationBalance.evaluate(
            listOf(food(fiber = 30.0, omega3 = 2.0, vitaminC = 90.0, addedSugar = 10.0)),
            goals,
            targetCalories = 2000
        )!!
        assertEquals(4, balance.assessedCount)
        assertTrue(balance.components.none { it.nutrient == HomeTopNutrient.MAGNESIUM })
    }

    @Test
    fun valuesAreSummedAcrossMealsAndOnlyRecordedMealsCount() {
        val balance = InflammationBalance.evaluate(
            listOf(
                food(calories = 1000, fiber = 10.0, omega3 = 1.0, vitaminC = 50.0, addedSugar = 10.0),
                food(calories = 1000, fiber = 20.0, vitaminC = null, addedSugar = null)
            ),
            goals,
            targetCalories = 2000
        )!!
        assertEquals(30.0, balance.components.single { it.nutrient == HomeTopNutrient.FIBER }.amount, 0.001)
        assertEquals(10.0, balance.components.single { it.nutrient == HomeTopNutrient.ADDED_SUGAR }.amount, 0.001)
    }

    @Test
    fun omega6RichPolyunsaturatedFatIsNotCountedAsInflammatory() {
        val base = food(fiber = 30.0, omega3 = 2.0, vitaminC = 90.0, addedSugar = 10.0)
        val withPufa = base.copy(polyunsaturatedFat = 60.0)
        val a = InflammationBalance.evaluate(listOf(base), goals, 2000)!!
        val b = InflammationBalance.evaluate(listOf(withPufa), goals, 2000)!!
        assertEquals(a.score, b.score, 0.0)
    }

    @Test
    fun tooLittleDataGivesNoBalance() {
        // Too few calories.
        assertNull(InflammationBalance.evaluate(listOf(food(calories = 300, fiber = 10.0, omega3 = 1.0, vitaminC = 50.0, addedSugar = 5.0)), goals, 2000))
        // Only two anti-inflammatory nutrients recorded.
        assertNull(InflammationBalance.evaluate(listOf(food(fiber = 10.0, omega3 = 1.0, addedSugar = 5.0)), goals, 2000))
        // No limit nutrient recorded.
        assertNull(InflammationBalance.evaluate(listOf(food(fiber = 10.0, omega3 = 1.0, vitaminC = 50.0)), goals, 2000))
        assertNull(InflammationBalance.evaluate(emptyList(), goals, 2000))
    }
}
