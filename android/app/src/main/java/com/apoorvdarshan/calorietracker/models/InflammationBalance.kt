package com.apoorvdarshan.calorietracker.models

import kotlin.math.min

enum class InflammationLean { ANTI, NEUTRAL, PRO }

enum class InflammationDirection { ANTI, PRO }

/** One nutrient's share in the balance. [ratio] is its energy-adjusted intake against its reference, 0 and up. */
data class InflammationComponent(
    val nutrient: HomeTopNutrient,
    val direction: InflammationDirection,
    val amount: Double,
    val reference: Double,
    val ratio: Double
)

/**
 * A rough read on how inflammatory one day of eating leans, from the nutrients the app already records.
 *
 * It is modelled on the research diet scores (Dietary Inflammatory Index, Empirical Dietary Inflammatory
 * Pattern), which weigh nutrients or food groups by how they relate to CRP, IL-6 and TNF-alpha. It is a
 * simplification, not a validated index and not a measurement of the body: only nutrients that were
 * recorded count, and a missing value is left out instead of being treated as zero.
 *
 * Choices that follow the evidence:
 * - Intake is judged per calorie against the user's own goals, as the DII does per 1000 kcal.
 * - Omega-6 and total polyunsaturated fat are not counted as pro-inflammatory; trials and cohorts do not
 *   show that linoleic acid raises inflammatory markers.
 * - The pro-inflammatory side is limited to added sugar, saturated fat and trans fat.
 *
 * [score] runs from -1 (strongly anti-inflammatory) to +1 (strongly pro-inflammatory).
 */
data class InflammationBalance(
    val score: Double,
    val lean: InflammationLean,
    /** Every assessed nutrient, strongest influence first within its direction. */
    val components: List<InflammationComponent>,
    val assessedCount: Int,
    val possibleCount: Int
) {
    /** Anti-inflammatory nutrients that reached most of their reference. */
    val helping: List<InflammationComponent>
        get() = components.filter { it.direction == InflammationDirection.ANTI && it.ratio >= HELPING_FROM }
            .sortedByDescending { it.ratio }

    /** Anti-inflammatory nutrients that were assessed and fell short. */
    val lacking: List<InflammationComponent>
        get() = components.filter { it.direction == InflammationDirection.ANTI && it.ratio < LACKING_BELOW }
            .sortedBy { it.ratio }

    /** Pro-inflammatory nutrients that reached most of their limit. */
    val pushing: List<InflammationComponent>
        get() = components.filter { it.direction == InflammationDirection.PRO && it.ratio >= PUSHING_FROM }
            .sortedByDescending { it.ratio }

    private class Spec(
        val nutrient: HomeTopNutrient,
        val direction: InflammationDirection,
        val read: (FoodEntry) -> Double?,
        val reference: (OptionalNutrientGoals) -> Double
    )

    companion object {
        const val HELPING_FROM = 0.75
        const val LACKING_BELOW = 0.5
        const val PUSHING_FROM = 0.75

        /** The day's energy has to be this high before the balance means anything. */
        const val MIN_KCAL = 500

        /** Enough anti-inflammatory nutrients and one limit nutrient must be recorded, or the read would be a guess. */
        private const val MIN_ANTI_ASSESSED = 3
        private const val MIN_PRO_ASSESSED = 1

        private const val NEUTRAL_BAND = 0.25

        /** Trans fat has a goal of zero, so its ratio is taken against this fixed amount in grams. */
        private const val TRANS_FAT_REFERENCE_G = 2.0

        private val specs = listOf(
            Spec(HomeTopNutrient.FIBER, InflammationDirection.ANTI, { it.fiber }, { it.fiber.toDouble() }),
            Spec(HomeTopNutrient.OMEGA3, InflammationDirection.ANTI, { it.omega3 }, { it.omega3.toDouble() }),
            Spec(HomeTopNutrient.VITAMIN_C, InflammationDirection.ANTI, { it.vitaminC }, { it.vitaminC.toDouble() }),
            Spec(HomeTopNutrient.VITAMIN_E, InflammationDirection.ANTI, { it.vitaminE }, { it.vitaminE.toDouble() }),
            Spec(HomeTopNutrient.VITAMIN_A, InflammationDirection.ANTI, { it.vitaminA }, { it.vitaminA.toDouble() }),
            Spec(HomeTopNutrient.VITAMIN_D, InflammationDirection.ANTI, { it.vitaminD }, { it.vitaminD.toDouble() }),
            Spec(HomeTopNutrient.MAGNESIUM, InflammationDirection.ANTI, { it.magnesium }, { it.magnesium.toDouble() }),
            Spec(HomeTopNutrient.ZINC, InflammationDirection.ANTI, { it.zinc }, { it.zinc.toDouble() }),
            Spec(HomeTopNutrient.FOLATE, InflammationDirection.ANTI, { it.folate }, { it.folate.toDouble() }),
            Spec(HomeTopNutrient.ADDED_SUGAR, InflammationDirection.PRO, { it.addedSugar }, { it.addedSugar.toDouble() }),
            Spec(HomeTopNutrient.SATURATED_FAT, InflammationDirection.PRO, { it.saturatedFat }, { it.saturatedFat.toDouble() }),
            Spec(HomeTopNutrient.TRANS_FAT, InflammationDirection.PRO, { it.transFat }, { TRANS_FAT_REFERENCE_G })
        )

        /**
         * The balance of [entries], or null when too little was logged or recorded to say anything.
         * [targetCalories] is what the goals in [goals] are sized for.
         */
        fun evaluate(
            entries: List<FoodEntry>,
            goals: OptionalNutrientGoals,
            targetCalories: Int
        ): InflammationBalance? {
            val kcal = entries.sumOf { it.calories }
            if (kcal < MIN_KCAL || targetCalories <= 0) return null

            val components = specs.mapNotNull { spec ->
                val recorded = entries.mapNotNull(spec.read)
                if (recorded.isEmpty()) return@mapNotNull null
                val reference = spec.reference(goals)
                if (!reference.isFinite() || reference <= 0.0) return@mapNotNull null
                val amount = recorded.sum()
                // Per calorie against the goal's own density, so a small eater is not penalised for eating less.
                val ratio = (amount / kcal) / (reference / targetCalories)
                InflammationComponent(spec.nutrient, spec.direction, amount, reference, ratio)
            }

            val anti = components.filter { it.direction == InflammationDirection.ANTI }
            val pro = components.filter { it.direction == InflammationDirection.PRO }
            if (anti.size < MIN_ANTI_ASSESSED || pro.size < MIN_PRO_ASSESSED) return null

            val antiCover = anti.map { min(it.ratio, 1.0) }.average()
            val proLoad = pro.map { min(it.ratio, 1.0) }.average()
            val score = (proLoad - antiCover).coerceIn(-1.0, 1.0)
            val lean = when {
                score <= -NEUTRAL_BAND -> InflammationLean.ANTI
                score >= NEUTRAL_BAND -> InflammationLean.PRO
                else -> InflammationLean.NEUTRAL
            }
            return InflammationBalance(score, lean, components, components.size, specs.size)
        }
    }
}
