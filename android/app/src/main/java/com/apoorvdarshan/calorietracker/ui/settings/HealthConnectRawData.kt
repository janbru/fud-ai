package com.apoorvdarshan.calorietracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.services.health.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collapsible reference view of the raw records Health Connect returns, for debugging sync
 * issues. Loads only while expanded and reloads when Refresh is tapped.
 */
@Composable
internal fun HealthConnectRawDataRows(health: HealthConnectManager) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var dump by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(expanded, reloadTick) {
        if (!expanded) return@LaunchedEffect
        dump = null
        dump = withContext(Dispatchers.IO) {
            runCatching { health.readRawDump() }.getOrElse { "Failed: ${it.javaClass.simpleName}: ${it.message}" }
        }
    }

    SettingRow(
        stringResource(R.string.settings_health_raw_data),
        stringResource(if (expanded) R.string.settings_health_raw_data_hide else R.string.settings_health_raw_data_show),
    ) { expanded = !expanded }

    if (expanded) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val text = dump
            if (text == null) {
                CircularProgressIndicator(Modifier.padding(8.dp), strokeWidth = 2.dp)
            } else {
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { reloadTick++ }) {
                        Text(stringResource(R.string.settings_health_raw_data_refresh))
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                        Text(stringResource(R.string.settings_health_raw_data_copy))
                    }
                }
            }
        }
    }
}
