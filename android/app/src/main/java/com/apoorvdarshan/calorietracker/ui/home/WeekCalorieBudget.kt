package com.apoorvdarshan.calorietracker.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.ui.components.energyText
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * "Calorie week budget": the daily goal times seven, spent freely across the week.
 * Lets the user see how many calories are left this week and whether there is room
 * for a day or two above the daily goal. All values are kcal.
 */
data class WeekBudgetDay(val date: LocalDate, val calories: Int, val isToday: Boolean, val isFuture: Boolean)

data class WeekBudget(
    val days: List<WeekBudgetDay>,
    val dailyGoal: Int,
    val budget: Int,
    val consumed: Int,
    /** Days still to eat in this week, today included. Zero for a finished week. */
    val daysLeft: Int
) {
    val remaining: Int get() = budget - consumed

    /** What each remaining day may average to finish exactly on budget. */
    val averagePerRemainingDay: Int? get() = if (daysLeft > 0) (remaining.toDouble() / daysLeft).roundToInt() else null

    /** Calories that can go above the daily goal on the remaining days and still hit the budget. */
    val extraRoom: Int get() = remaining - dailyGoal * daysLeft

    companion object {
        fun compute(
            entries: List<FoodEntry>,
            dailyGoal: Int,
            selectedDate: LocalDate,
            today: LocalDate,
            firstDayOfWeek: DayOfWeek,
            zone: ZoneId = ZoneId.systemDefault()
        ): WeekBudget {
            val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val weekEnd = weekStart.plusDays(6)
            val byDay = entries
                .groupBy { it.timestamp.atZone(zone).toLocalDate() }
                .filterKeys { it in weekStart..weekEnd }
                .mapValues { (_, list) -> list.sumOf { it.calories } }
            val days = (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                WeekBudgetDay(
                    date = date,
                    calories = byDay[date] ?: 0,
                    isToday = date == today,
                    isFuture = date.isAfter(today)
                )
            }
            return WeekBudget(
                days = days,
                dailyGoal = dailyGoal,
                budget = dailyGoal * 7,
                consumed = days.sumOf { it.calories },
                daysLeft = days.count { !it.date.isBefore(today) }
            )
        }
    }
}

private val BudgetGreen = Color(0xFF7ED957)
private val BudgetAmber = Color(0xFFFFD54F)

@Composable
fun WeekBudgetCard(budget: WeekBudget, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val over = budget.remaining < 0
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.week_budget_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.week_budget_used_format, energyText(budget.consumed), energyText(budget.budget)),
                fontSize = 12.sp,
                color = onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (over) {
                stringResource(R.string.week_budget_over_format, energyText(-budget.remaining))
            } else {
                stringResource(R.string.week_budget_left_format, energyText(budget.remaining))
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (over) Color(0xFFE53935) else AppColors.Calorie
        )
        Spacer(Modifier.height(12.dp))
        WeekBudgetBars(budget)
        budget.averagePerRemainingDay?.let { average ->
            if (!over) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.week_budget_average_format, energyText(average)),
                    fontSize = 13.sp,
                    color = onSurface.copy(alpha = 0.7f)
                )
                if (budget.extraRoom > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.week_budget_room_format, energyText(budget.extraRoom)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/** Seven bars against the daily goal line: green up to the goal, amber above it. */
@Composable
private fun WeekBudgetBars(budget: WeekBudget) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val barArea = 72.dp
    val scale = max(budget.dailyGoal * 1.5f, (budget.days.maxOf { it.calories }).toFloat()).coerceAtLeast(1f)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            budget.days.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barArea),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val total = day.calories / scale
                        val withinGoal = minOf(day.calories, budget.dailyGoal) / scale
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(total.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(BudgetAmber)
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(withinGoal.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(BudgetGreen)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(narrowDayRes(day.date.dayOfWeek)),
                        fontSize = 12.sp,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.isToday) AppColors.Calorie else onSurface.copy(alpha = if (day.isFuture) 0.35f else 0.6f)
                    )
                }
            }
        }
        // Daily goal line across all bars (the bar area sits at the top of each column).
        val lineColor = onSurface.copy(alpha = 0.35f)
        Canvas(Modifier.fillMaxWidth().height(barArea)) {
            val y = size.height * (1f - (budget.dailyGoal / scale).coerceIn(0f, 1f))
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
            )
        }
    }
}
