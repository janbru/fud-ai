package com.apoorvdarshan.calorietracker.data

import com.apoorvdarshan.calorietracker.models.DaySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Local store for the AI's end-of-day summaries, one per day, newest kept. */
class DaySummaryRepository(private val prefs: PreferencesStore) {
    val summaries: Flow<List<DaySummary>> = prefs.daySummaries

    fun summary(dateKey: String): Flow<DaySummary?> =
        prefs.daySummaries.map { list -> list.firstOrNull { it.dateKey == dateKey } }

    suspend fun current(dateKey: String): DaySummary? = summary(dateKey).first()

    suspend fun save(summary: DaySummary) {
        prefs.updateDaySummaries { current ->
            (current.filter { it.dateKey != summary.dateKey } + summary)
                .sortedByDescending { it.dateKey }
                .take(MAX_KEPT)
        }
    }

    private companion object {
        const val MAX_KEPT = 60
    }
}
