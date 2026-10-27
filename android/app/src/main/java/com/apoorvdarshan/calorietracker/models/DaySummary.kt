package com.apoorvdarshan.calorietracker.models

import kotlinx.serialization.Serializable
import java.time.Instant

/** The AI's end-of-day summary for one day, kept so reopening it costs no new request. */
@Serializable
data class DaySummary(
    /** Local day, yyyy-MM-dd. */
    val dateKey: String,
    /** Hash of the report the text answers; a day whose data changed no longer matches it. */
    val fingerprint: String,
    val text: String,
    @Serializable(with = InstantSerializer::class)
    val generatedAt: Instant = Instant.now()
)
