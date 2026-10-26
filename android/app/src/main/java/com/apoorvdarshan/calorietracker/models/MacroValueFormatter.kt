package com.apoorvdarshan.calorietracker.models

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

object MacroValueFormatter {
    fun string(value: Double): String {
        val rounded = round(value)
        return if (abs(rounded - value) < 0.0001) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    /** Display text with the locale's decimal and thousands separators; [string] stays ungrouped so it can seed edit fields. */
    fun withUnit(value: Double): String {
        val rounded = round(value)
        val number = if (abs(rounded - value) < 0.0001) {
            NumberFormat.getIntegerInstance(Locale.getDefault()).format(rounded.toLong())
        } else {
            NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }.format(value)
        }
        return "${number}g"
    }
}
