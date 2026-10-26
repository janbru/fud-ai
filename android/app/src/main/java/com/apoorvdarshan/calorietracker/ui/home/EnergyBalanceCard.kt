package com.apoorvdarshan.calorietracker.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.services.CalorieBalanceDirection
import com.apoorvdarshan.calorietracker.ui.components.LocalEnergyUnit
import com.apoorvdarshan.calorietracker.ui.components.energyText
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import com.apoorvdarshan.calorietracker.ui.util.formattedWholeNumber

/**
 * Energy balance: what was eaten against what Health Connect says was burned, drawn as two
 * bars on one scale with the burned bar split into resting and active energy. The headline
 * is the deficit or surplus, i.e. the gap between the two bars.
 */
@Composable
fun EnergyBalanceCard(summary: HomeBurnSummary, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val headlineColor = when (summary.direction) {
        CalorieBalanceDirection.DEFICIT -> BudgetGreen
        CalorieBalanceDirection.SURPLUS -> BudgetAmber
        CalorieBalanceDirection.BALANCED -> onSurface
    }
    val headline = when (summary.direction) {
        CalorieBalanceDirection.DEFICIT ->
            stringResource(R.string.energy_balance_deficit_format, energyText(summary.differenceCalories))
        CalorieBalanceDirection.SURPLUS ->
            stringResource(R.string.energy_balance_surplus_format, energyText(summary.differenceCalories))
        CalorieBalanceDirection.BALANCED -> stringResource(R.string.energy_balance_balanced)
    }
    val description = homeBurnLineText(summary)
    val scale = maxOf(summary.eatenCalories, summary.burnedCalories, 1).toFloat()

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Text(
            stringResource(R.string.energy_balance_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            headline,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = headlineColor
        )
        Spacer(Modifier.height(14.dp))
        BalanceBar(
            label = stringResource(R.string.energy_balance_eaten),
            value = energyText(summary.eatenCalories),
            segments = listOf(
                BarSegment(
                    summary.eatenCalories / scale,
                    Brush.horizontalGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
                )
            )
        )
        Spacer(Modifier.height(12.dp))
        BalanceBar(
            label = stringResource(R.string.energy_balance_burned),
            value = energyText(summary.burnedCalories),
            segments = listOf(
                BarSegment(summary.activeCalories / scale, Brush.horizontalGradient(listOf(BudgetGreen, BudgetGreen))),
                BarSegment(
                    summary.restingCalories / scale,
                    Brush.horizontalGradient(listOf(BudgetGreen.copy(alpha = 0.4f), BudgetGreen.copy(alpha = 0.4f)))
                )
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (summary.activeCalories > 0) {
                LegendItem(BudgetGreen, stringResource(R.string.energy_balance_active), energyText(summary.activeCalories))
            }
            LegendItem(
                BudgetGreen.copy(alpha = 0.4f),
                stringResource(R.string.energy_balance_resting),
                energyText(summary.restingCalories)
            )
        }
    }
}

private class BarSegment(val fraction: Float, val brush: Brush)

/** A label and value above a rounded bar; segments fill the track left to right in order. */
@Composable
private fun BalanceBar(label: String, value: String, segments: List<BarSegment>) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 13.sp,
            color = onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(5.dp))
    // Layers are cumulative: each segment is drawn from the left edge to its own end, last
    // segment first, so earlier segments paint over the later ones.
    val ends = segments.runningFold(0f) { sum, segment -> sum + segment.fraction.coerceIn(0f, 1f) }.drop(1)
    Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(onSurface.copy(alpha = 0.08f))
    ) {
        segments.indices.reversed().forEach { index ->
            val end by animateFloatAsState(
                targetValue = ends[index].coerceIn(0f, 1f),
                animationSpec = tween(600),
                label = "balanceBar"
            )
            if (end > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(end)
                        .fillMaxHeight()
                        .background(segments[index].brush)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "$label $value",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun homeBurnLineText(summary: HomeBurnSummary): String {
    val energyUnit = LocalEnergyUnit.current
    val burned = energyUnit.displayValue(summary.burnedCalories).formattedWholeNumber()
    val difference = energyUnit.displayValue(summary.differenceCalories).formattedWholeNumber()
    return when (summary.direction) {
        CalorieBalanceDirection.DEFICIT -> stringResource(R.string.home_burn_deficit, burned, difference)
        CalorieBalanceDirection.SURPLUS -> stringResource(R.string.home_burn_surplus, burned, difference)
        CalorieBalanceDirection.BALANCED -> stringResource(R.string.home_burn_balanced, burned)
    }
}
