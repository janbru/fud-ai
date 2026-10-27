package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.EnergyUnit
import com.apoorvdarshan.calorietracker.models.FastingSession
import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.models.HomeTopNutrient
import com.apoorvdarshan.calorietracker.models.ImportedHealthWorkout
import com.apoorvdarshan.calorietracker.models.InflammationBalance
import com.apoorvdarshan.calorietracker.models.MealType
import com.apoorvdarshan.calorietracker.models.OptionalNutrientGoals
import com.apoorvdarshan.calorietracker.models.UserProfile
import com.apoorvdarshan.calorietracker.models.WaterEntry
import com.apoorvdarshan.calorietracker.models.WeightEntry
import com.apoorvdarshan.calorietracker.models.WorkoutSession
import com.google.gson.GsonBuilder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Everything [DayReport] reads about one day. Plain values, so the report is testable on the JVM. */
internal data class DayReportInput(
    val date: LocalDate,
    val profile: UserProfile,
    /** The whole food log; the report picks the day and the days before it. */
    val foods: List<FoodEntry>,
    val optionalGoals: OptionalNutrientGoals = OptionalNutrientGoals.Default,
    val waterEntries: List<WaterEntry> = emptyList(),
    /** Null when water tracking is off, so the report leaves water out instead of reporting zero. */
    val waterGoalMl: Int? = null,
    val fastingSessions: List<FastingSession> = emptyList(),
    val steps: Int? = null,
    val activeCalories: Int? = null,
    val totalBurnedCalories: Int? = null,
    val workoutSessions: List<WorkoutSession> = emptyList(),
    val importedWorkouts: List<ImportedHealthWorkout> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    /** Names a Health Connect exercise type for the model; the type ids alone mean nothing to it. */
    val workoutTypeName: (Int) -> String = { "workout" },
    val zone: ZoneId = ZoneId.systemDefault()
)

/**
 * The raw record of one day that goes to the AI for the end-of-day summary: totals against goals,
 * every meal with its nutrients, water, fasting, activity, and the days before for context.
 * Model input, so it is not localized and energy stays kcal.
 */
internal object DayReport {
    private const val CONTEXT_DAYS = 7

    /** Nutrients that already have their own field on a meal. */
    private val MacroKeys = setOf(HomeTopNutrient.PROTEIN, HomeTopNutrient.CARBS, HomeTopNutrient.FAT)

    private val gson = GsonBuilder().serializeNulls().create()

    fun json(input: DayReportInput): String = gson.toJson(payload(input))

    fun payload(input: DayReportInput): Map<String, Any?> {
        val zone = input.zone
        val dayFoods = foodsOn(input.foods, input.date, zone)
        val profile = input.profile
        val report = linkedMapOf<String, Any?>(
            "date" to input.date.toString(),
            "weekday" to input.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            "goal" to CoachContext.goalEnglish(profile.goal),
            "targets" to linkedMapOf(
                "kcal" to profile.effectiveCalories,
                "protein_g" to profile.effectiveProtein,
                "carbs_g" to profile.effectiveCarbs,
                "fat_g" to profile.effectiveFat
            ),
            "totals" to linkedMapOf(
                "kcal" to dayFoods.sumOf { it.calories },
                "protein_g" to round1(dayFoods.sumOf { it.protein }),
                "carbs_g" to round1(dayFoods.sumOf { it.carbs }),
                "fat_g" to round1(dayFoods.sumOf { it.fat })
            ),
            "nutrients" to nutrients(dayFoods, input),
            "meals" to dayFoods.sortedBy { it.timestamp }.map { meal(it, zone) }
        )
        inflammationSection(dayFoods, input)?.let { report["inflammation"] = it }
        waterSection(input)?.let { report["water"] = it }
        fastingSection(input)?.let { report["fasting"] = it }
        activitySection(input)?.let { report["activity"] = it }
        weightOn(input)?.let { report["weight_kg"] = it }
        report["previous_days"] = previousDays(input)
        return report
    }

    /**
     * The part of the prompt the user can rewrite in Settings: length, tone and layout. Everything
     * around it (language, data rules, energy unit, the JSON) stays fixed so an edit cannot break them.
     */
    val DEFAULT_STYLE: String = """
        Keep it to one phone screen: about 120 words at most, no tables.
        Do not recap numbers I can already see; mention one only when it makes a point. Focus on the reasons behind how the day went and what that means for my goal.
        Use exactly these three short sections, with the headings translated into the reply language:
        1. **The day**: 1-2 sentences on why the day went the way it did.
        2. **Do better**: 2-3 bullets, each a cause and its consequence, then the specific change to make. Name the foods, meals or habits involved.
        3. **Tomorrow**: one concrete focus, one sentence.
    """.trimIndent()

    /** The instruction that wraps [json] when it is sent to the model; a blank [style] means the default. */
    fun prompt(json: String, energyUnit: EnergyUnit, languageName: String, style: String = ""): String = buildString {
        appendLine("The day is over. Here is the complete raw record of my day as JSON. Write my end-of-day summary.")
        appendLine()
        appendLine("Reply in $languageName, in Markdown.")
        appendLine(style.trim().ifEmpty { DEFAULT_STYLE })
        appendLine()
        appendLine("Rules:")
        appendLine("- Use only this data. Do not call tools; everything you need is below.")
        appendLine("- \"nutrients\" lists the day's totals against targets. A micronutrient total only counts meals where the value was recorded, so a low or missing value can mean it was not recorded. Say so instead of calling it a deficiency.")
        appendLine("- \"inflammation\" is a rough estimate from the recorded nutrients, modelled on research diet scores. It is not a measurement of my body. Mention it at most briefly and only when it helps explain the day, tied to specific foods. Make no medical claims.")
        appendLine("- \"previous_days\" is for comparison only. Do not summarize those days.")
        appendLine("- Missing \"water\", \"fasting\" or \"activity\" sections mean that was not tracked. Do not scold me for it.")
        appendLine("- Do not invent meals, numbers or medical claims.")
        energyUnit.aiReplyInstruction()?.let { appendLine("- $it") }
        appendLine()
        append(json)
    }

    // MARK: - Sections

    private fun foodsOn(foods: List<FoodEntry>, date: LocalDate, zone: ZoneId): List<FoodEntry> =
        foods.filter { it.timestamp.atZone(zone).toLocalDate() == date }

    private fun meal(food: FoodEntry, zone: ZoneId): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "time" to food.timestamp.atZone(zone).toLocalTime().withSecond(0).withNano(0).toString(),
            "meal_type" to mealTypeName(food.mealType),
            "name" to food.name,
            "kcal" to food.calories,
            "protein_g" to round1(food.protein),
            "carbs_g" to round1(food.carbs),
            "fat_g" to round1(food.fat)
        )
        food.servingSizeGrams?.takeIf { it.isFinite() && it > 0.0 }?.let { map["serving_size_g"] = round1(it) }
        food.customNote?.takeIf { it.isNotBlank() }?.let { map["note"] = it }
        if (food.ingredients.isNotEmpty()) {
            map["ingredients"] = food.ingredients.map {
                linkedMapOf("name" to it.name, "grams" to round1(it.grams), "kcal" to it.calories)
            }
        }
        // Only what was recorded for this meal, so an absent key reads as "not recorded".
        val recorded = linkedMapOf<String, Any?>()
        for (nutrient in HomeTopNutrient.values()) {
            if (nutrient in MacroKeys) continue
            val value = nutrient.current(listOf(food))
            if (value.isFinite() && value > 0.0) recorded[key(nutrient)] = round2(value)
        }
        if (recorded.isNotEmpty()) map["nutrients"] = recorded
        return map
    }

    private fun nutrients(dayFoods: List<FoodEntry>, input: DayReportInput): List<Map<String, Any?>> =
        HomeTopNutrient.values()
            .filter { it !in MacroKeys }
            .mapNotNull { nutrient ->
                val total = nutrient.current(dayFoods)
                if (!total.isFinite() || total <= 0.0) return@mapNotNull null
                val goal = nutrient.goal(input.profile, input.optionalGoals)
                linkedMapOf<String, Any?>(
                    "name" to nutrient.displayName,
                    "unit" to nutrient.unit,
                    "total" to round2(total),
                    "target" to goal,
                    "percent_of_target" to if (goal > 0) (total / goal * 100).roundToInt() else null
                )
            }

    /** Left out when too little was recorded to estimate; see [InflammationBalance]. */
    private fun inflammationSection(dayFoods: List<FoodEntry>, input: DayReportInput): Map<String, Any?>? {
        val balance = InflammationBalance.evaluate(
            dayFoods,
            input.optionalGoals,
            input.profile.effectiveCalories
        ) ?: return null
        return linkedMapOf(
            "leans" to balance.lean.name.lowercase(Locale.ENGLISH),
            "score" to round2(balance.score),
            "nutrients_assessed" to "${balance.assessedCount} of ${balance.possibleCount}",
            "helping" to balance.helping.map { it.nutrient.displayName },
            "lacking" to balance.lacking.map { it.nutrient.displayName },
            "pushing_up" to balance.pushing.map { it.nutrient.displayName }
        )
    }

    private fun waterSection(input: DayReportInput): Map<String, Any?>? {
        val goal = input.waterGoalMl ?: return null
        val total = input.waterEntries
            .filter { it.date.atZone(input.zone).toLocalDate() == input.date }
            .sumOf { it.milliliters }
        return linkedMapOf("total_ml" to total, "goal_ml" to goal)
    }

    private fun fastingSection(input: DayReportInput): List<Map<String, Any?>>? {
        val dayStart = input.date.atStartOfDay(input.zone).toInstant()
        val dayEnd = input.date.plusDays(1).atStartOfDay(input.zone).toInstant()
        val sessions = input.fastingSessions
            .filter { (it.endedAt ?: dayEnd) > dayStart && it.startedAt < dayEnd }
            .sortedBy { it.startedAt }
        if (sessions.isEmpty()) return null
        return sessions.map { session ->
            val end = session.endedAt
            val minutes = Duration.between(session.startedAt, end ?: dayEnd).toMinutes().coerceAtLeast(0)
            linkedMapOf<String, Any?>(
                "started_at" to LocalDateTime.ofInstant(session.startedAt, input.zone).withSecond(0).withNano(0).toString(),
                "ended_at" to end?.let { LocalDateTime.ofInstant(it, input.zone).withSecond(0).withNano(0).toString() },
                "duration_minutes" to minutes,
                "goal_minutes" to session.goalMinutes,
                "goal_reached" to (minutes >= session.goalMinutes)
            )
        }
    }

    private fun activitySection(input: DayReportInput): Map<String, Any?>? {
        val key = input.date.toString()
        val strength = input.workoutSessions.filter { it.diaryDateKey == key }.sortedBy { it.startedAt }
        val imported = input.importedWorkouts.filter { it.diaryDateKey == key }.sortedBy { it.startedAt }
        if (input.steps == null && input.activeCalories == null && input.totalBurnedCalories == null &&
            strength.isEmpty() && imported.isEmpty()
        ) return null
        val activity = linkedMapOf<String, Any?>()
        input.steps?.let { activity["steps"] = it }
        input.totalBurnedCalories?.let { activity["total_burned_kcal"] = it }
        input.activeCalories?.let { activity["active_burned_kcal"] = it }
        if (strength.isNotEmpty()) {
            activity["strength_workouts"] = strength.map { session ->
                linkedMapOf<String, Any?>(
                    "duration_minutes" to session.durationMinutes,
                    "performed_sets" to session.performedSetCount,
                    "exercises" to session.exercises.map { exercise ->
                        linkedMapOf<String, Any?>(
                            "name" to exercise.name,
                            "target_muscles" to exercise.targetMuscles,
                            "sets" to exercise.sets.filter { it.isPerformed }.map { set ->
                                linkedMapOf<String, Any?>("reps" to set.reps.toIntOrNull()).apply {
                                    if (set.weight.isNotEmpty()) {
                                        put("weight", set.weight)
                                        put("weight_unit", set.weightUnit.storageValue)
                                    }
                                }
                            }
                        )
                    }
                ).apply { session.caloriesBurned?.let { put("estimated_kcal", it) } }
            }
        }
        if (imported.isNotEmpty()) {
            activity["recorded_workouts"] = imported.map { workout ->
                linkedMapOf<String, Any?>(
                    "type" to input.workoutTypeName(workout.exerciseType),
                    "source" to workout.sourceSummary,
                    "duration_minutes" to workout.durationMinutes
                ).apply {
                    workout.activeCalories?.let { put("active_kcal", it) }
                    workout.distanceMeters?.let { put("distance_m", it.roundToInt()) }
                }
            }
        }
        return activity
    }

    private fun weightOn(input: DayReportInput): Double? =
        input.weights
            .filter { it.date.atZone(input.zone).toLocalDate() == input.date }
            .maxByOrNull { it.date }
            ?.weightKg
            ?.let(::round1)

    private fun previousDays(input: DayReportInput): List<Map<String, Any?>> =
        (CONTEXT_DAYS downTo 1).map { back ->
            val day = input.date.minusDays(back.toLong())
            val foods = foodsOn(input.foods, day, input.zone)
            linkedMapOf<String, Any?>(
                "date" to day.toString(),
                "logged" to foods.isNotEmpty(),
                "kcal" to foods.takeIf { it.isNotEmpty() }?.sumOf { it.calories },
                "protein_g" to foods.takeIf { it.isNotEmpty() }?.let { round1(it.sumOf { f -> f.protein }) }
            )
        }

    // MARK: - Helpers

    private fun key(nutrient: HomeTopNutrient): String = "${nutrient.storageKey}_${nutrient.unit}"

    private fun mealTypeName(mealType: MealType): String = mealType.name.lowercase(Locale.ENGLISH)

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    /** The day can be summarized once it is over: any past day, or today from the evening on. */
    fun isComplete(date: LocalDate, today: LocalDate, now: LocalTime): Boolean =
        date.isBefore(today) || (date == today && now >= EVENING_FROM)

    private val EVENING_FROM: LocalTime = LocalTime.of(18, 0)
}
