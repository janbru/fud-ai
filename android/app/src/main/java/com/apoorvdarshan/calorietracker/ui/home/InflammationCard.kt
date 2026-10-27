package com.apoorvdarshan.calorietracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.InflammationBalance
import com.apoorvdarshan.calorietracker.models.InflammationComponent
import com.apoorvdarshan.calorietracker.models.InflammationDirection
import com.apoorvdarshan.calorietracker.models.InflammationLean
import kotlin.math.min

@Composable
private fun InflammationLean.label(): String = stringResource(
    when (this) {
        InflammationLean.ANTI -> R.string.inflammation_lean_anti
        InflammationLean.NEUTRAL -> R.string.inflammation_lean_neutral
        InflammationLean.PRO -> R.string.inflammation_lean_pro
    }
)

@Composable
private fun InflammationLean.color(): Color = when (this) {
    InflammationLean.ANTI -> BudgetGreen
    InflammationLean.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    InflammationLean.PRO -> BudgetAmber
}

@Composable
private fun List<InflammationComponent>.names(): String =
    map { stringResource(it.nutrient.displayNameRes) }.joinToString(", ")

/** A track from anti- to pro-inflammatory with a marker at the day's score. */
@Composable
private fun LeanTrack(score: Double, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(modifier.fillMaxWidth().height(14.dp)) {
        val markerSize = 14.dp
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.horizontalGradient(listOf(BudgetGreen.copy(alpha = 0.55f), onSurface.copy(alpha = 0.12f), BudgetAmber.copy(alpha = 0.55f))))
        )
        val fraction = ((score + 1.0) / 2.0).toFloat().coerceIn(0f, 1f)
        Box(
            Modifier
                .offset(x = (maxWidth - markerSize) * fraction)
                .size(markerSize)
                .clip(CircleShape)
                .background(onSurface)
        )
    }
}

/** Home entry point: how the selected day's logged nutrients lean, opening the details. */
@Composable
fun InflammationCard(balance: InflammationBalance, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val detail = listOfNotNull(
        balance.helping.take(3).takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.inflammation_card_helping, it.names()) },
        balance.pushing.take(3).takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.inflammation_card_pushing, it.names()) }
            ?: balance.lacking.take(3).takeIf { it.isNotEmpty() }
                ?.let { stringResource(R.string.inflammation_card_low, it.names()) }
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.inflammation_title), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(balance.lean.label(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = balance.lean.color())
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = onSurface.copy(alpha = 0.4f))
        }
        Spacer(Modifier.height(14.dp))
        LeanTrack(balance.score)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.inflammation_track_anti), fontSize = 11.sp, color = onSurface.copy(alpha = 0.5f))
            Text(stringResource(R.string.inflammation_track_pro), fontSize = 11.sp, color = onSurface.copy(alpha = 0.5f))
        }
        detail.forEach {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 13.sp, color = onSurface.copy(alpha = 0.7f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InflammationSheet(balance: InflammationBalance, onDismiss: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetSurface = if (isDark) MaterialTheme.colorScheme.surface else Color(0xFFFAF3EE)
    val onSurface = MaterialTheme.colorScheme.onSurface

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = sheetSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.inflammation_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(balance.lean.label(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = balance.lean.color())
                Spacer(Modifier.height(12.dp))
                LeanTrack(balance.score)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.inflammation_coverage, balance.assessedCount, balance.possibleCount),
                    fontSize = 12.sp,
                    color = onSurface.copy(alpha = 0.55f)
                )

                ComponentSection(
                    stringResource(R.string.inflammation_section_anti),
                    balance.components.filter { it.direction == InflammationDirection.ANTI }.sortedByDescending { it.ratio },
                    BudgetGreen
                )
                ComponentSection(
                    stringResource(R.string.inflammation_section_pro),
                    balance.components.filter { it.direction == InflammationDirection.PRO }.sortedByDescending { it.ratio },
                    BudgetAmber
                )

                SectionTitle(stringResource(R.string.inflammation_about_title))
                Body(stringResource(R.string.inflammation_about_body))

                SectionTitle(stringResource(R.string.inflammation_research_title))
                listOf(
                    R.string.inflammation_research_pattern,
                    R.string.inflammation_research_omega3,
                    R.string.inflammation_research_fiber,
                    R.string.inflammation_research_omega6,
                    R.string.inflammation_research_limits
                ).forEach { Body(stringResource(it), Modifier.padding(bottom = 8.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = modifier)
}

@Composable
private fun ComponentSection(title: String, components: List<InflammationComponent>, color: Color) {
    if (components.isEmpty()) return
    SectionTitle(title)
    components.forEach { component ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(component.nutrient.displayNameRes), fontSize = 13.sp, modifier = Modifier.width(110.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(min(component.ratio, 1.0).toFloat())
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}
