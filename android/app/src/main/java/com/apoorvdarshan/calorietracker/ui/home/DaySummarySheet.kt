package com.apoorvdarshan.calorietracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.ui.coach.MarkdownText
import com.apoorvdarshan.calorietracker.ui.components.FudGlassPrimaryButton
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import java.time.LocalDate

/** Home entry point: asks the AI for a summary of a finished day and what to do better. */
@Composable
fun DaySummaryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = AppColors.Calorie,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.day_summary_title), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.day_summary_card_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySummarySheet(container: AppContainer, date: LocalDate, onDismiss: () -> Unit) {
    val vm: DaySummaryViewModel = viewModel(
        key = "day-summary-$date",
        factory = DaySummaryViewModel.Factory(container, date)
    )
    val ui by vm.ui.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetSurface = if (isDark) MaterialTheme.colorScheme.surface else Color(0xFFFAF3EE)

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
                    stringResource(R.string.day_summary_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            }
            Spacer(Modifier.height(8.dp))

            val summary = ui.summary
            val error = ui.error ?: ui.errorRes?.let { stringResource(it) }
            when {
                ui.loading -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.Calorie)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.day_summary_loading),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                summary != null -> Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (ui.stale) {
                        Text(
                            stringResource(R.string.day_summary_stale),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    MarkdownText(content = summary.text, color = MaterialTheme.colorScheme.onSurface)
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(error, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(20.dp))
                    FudGlassPrimaryButton(
                        text = stringResource(
                            if (ui.stale) R.string.day_summary_update else R.string.day_summary_regenerate
                        ),
                        onClick = vm::regenerate
                    )
                }
                else -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                    Text(error.orEmpty(), fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    FudGlassPrimaryButton(text = stringResource(R.string.action_retry), onClick = vm::regenerate)
                }
            }
        }
    }
}
