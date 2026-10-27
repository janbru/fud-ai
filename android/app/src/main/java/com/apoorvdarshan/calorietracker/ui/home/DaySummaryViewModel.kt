package com.apoorvdarshan.calorietracker.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.AppLanguage
import com.apoorvdarshan.calorietracker.models.DaySummary
import com.apoorvdarshan.calorietracker.models.FastingSession
import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.models.UserProfile
import com.apoorvdarshan.calorietracker.models.WeightEntry
import com.apoorvdarshan.calorietracker.models.WorkoutSession
import com.apoorvdarshan.calorietracker.models.WorkoutWeightUnit
import com.apoorvdarshan.calorietracker.models.importedWorkoutTypeLabel
import com.apoorvdarshan.calorietracker.services.DailySummaryPolicy
import com.apoorvdarshan.calorietracker.services.ai.AiError
import com.apoorvdarshan.calorietracker.services.ai.DayReport
import com.apoorvdarshan.calorietracker.services.ai.DayReportInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

data class DaySummaryUiState(
    val loading: Boolean = true,
    val summary: DaySummary? = null,
    /** The day's data changed after [summary] was written. */
    val stale: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null
)

/**
 * Asks the AI provider the user already set up (same one, fallback and hosted access as Coach)
 * for an end-of-day summary, sending the day's raw data as JSON. The answer is kept per day, so
 * reopening it costs nothing until the data changes.
 */
class DaySummaryViewModel(
    private val container: AppContainer,
    private val date: LocalDate
) : ViewModel() {
    private val _ui = MutableStateFlow(DaySummaryUiState())
    val ui: StateFlow<DaySummaryUiState> = _ui.asStateFlow()

    private var job: Job? = null

    init {
        open()
    }

    /** Shows the saved summary, or asks for one when the day has none yet. */
    private fun open() = launchWork {
        val report = buildReport() ?: return@launchWork
        val saved = container.daySummaryRepository.current(date.toString())
        if (saved == null) {
            generate(report)
        } else {
            _ui.value = DaySummaryUiState(loading = false, summary = saved, stale = saved.fingerprint != report.fingerprint)
        }
    }

    /** Asks again, replacing the saved summary. */
    fun regenerate() = launchWork {
        val report = buildReport() ?: return@launchWork
        generate(report)
    }

    private fun launchWork(work: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val previous = _ui.value
            _ui.value = previous.copy(loading = true, error = null, errorRes = null)
            try {
                work()
            } catch (e: AiError) {
                logFailure(e)
                // Hosted quota / no-plan errors open the paywall flow; others show localized text.
                val routed = container.hostedUi.handle(e)
                _ui.value = previous.copy(loading = false, error = if (routed) null else e.userMessage(container.appContext))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logFailure(e)
                _ui.value = previous.copy(
                    loading = false,
                    error = e.localizedMessage,
                    errorRes = if (e.localizedMessage.isNullOrBlank()) R.string.day_summary_failed else null
                )
            }
        }
    }

    private suspend fun generate(report: Report) {
        val prompt = DayReport.prompt(
            json = report.json,
            energyUnit = container.prefs.energyUnit.first(),
            languageName = languageName(),
            style = container.prefs.daySummaryPrompt.first()
        )
        val reply = container.chatService.sendMessage(
            history = emptyList(),
            newUserMessage = prompt,
            profile = report.profile,
            weights = report.weights,
            bodyFats = container.bodyFatRepository.entries.first(),
            measurements = container.bodyMeasurementRepository.entries.first(),
            foods = report.foods,
            fastingSessions = report.fastingSessions,
            heightMetric = container.prefs.heightUnit.first() == "cm",
            weightMetric = report.weightMetric,
            workoutSessions = report.workoutSessions,
            workoutPlanWeightUnit = if (report.weightMetric) WorkoutWeightUnit.KG else WorkoutWeightUnit.LBS
        ).trim()
        if (reply.isEmpty()) throw AiError.InvalidResponse
        val summary = DaySummary(dateKey = date.toString(), fingerprint = report.fingerprint, text = reply)
        container.daySummaryRepository.save(summary)
        _ui.value = DaySummaryUiState(loading = false, summary = summary)
    }

    private class Report(
        val json: String,
        val fingerprint: String,
        val profile: UserProfile,
        val foods: List<FoodEntry>,
        val weights: List<WeightEntry>,
        val fastingSessions: List<FastingSession>,
        val workoutSessions: List<WorkoutSession>,
        val weightMetric: Boolean
    )

    /** Reads everything the day has; null (with the error set) when there is no profile yet. */
    private suspend fun buildReport(): Report? {
        val profile = container.profileRepository.current()
        if (profile == null) {
            _ui.value = DaySummaryUiState(loading = false, errorRes = R.string.coach_no_profile_error)
            return null
        }
        val prefs = container.prefs
        val health = container.health
        val foods = container.foodRepository.entries.first()
        val weights = container.weightRepository.entries.first()
        val fastingSessions = container.fastingRepository.sessions.first()
        val workoutState = container.workoutRepository.snapshot()
        val healthOn = prefs.healthConnectEnabled.first()
        val steps = if (healthOn && health.hasStepsRead()) health.readStepsForDay(date) else null
        val energy = if (healthOn && health.hasEnergyRead()) health.readEnergyForDay(date) else null
        val burned = energy?.let {
            DailySummaryPolicy.resolveBurnedCalories(
                measuredTotalCalories = it.totalCalories,
                externalActiveCalories = it.activeCalories,
                profileBmrCalories = profile.bmr.roundToInt()
            )
        }
        val json = DayReport.json(
            DayReportInput(
                date = date,
                profile = profile,
                foods = foods,
                optionalGoals = prefs.optionalNutrientGoals.first(),
                waterEntries = container.waterRepository.entries.first(),
                waterGoalMl = if (prefs.waterTrackingEnabled.first()) prefs.waterDailyGoalMl.first() else null,
                fastingSessions = fastingSessions,
                steps = steps,
                activeCalories = energy?.activeCalories?.takeIf { it > 0 },
                totalBurnedCalories = burned,
                workoutSessions = workoutState.completedSessions,
                importedWorkouts = container.importedHealthWorkoutRepository.workouts.first(),
                weights = weights,
                workoutTypeName = { type -> container.appContext.getString(importedWorkoutTypeLabel(type)) }
            )
        )
        return Report(
            json = json,
            fingerprint = sha256(json),
            profile = profile,
            foods = foods,
            weights = weights,
            fastingSessions = fastingSessions,
            workoutSessions = workoutState.completedSessions,
            weightMetric = prefs.weightUnit.first() == "kg"
        )
    }

    /** English name of the language the app is shown in, so the summary reads like the rest of the app. */
    private fun languageName(): String {
        val shown = container.appContext.resources.configuration.locales[0]
        val tag = AppLanguage.matchSupported(shown.toLanguageTag()) ?: "en"
        return Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun logFailure(error: Throwable) {
        // Log is a stub in JVM unit tests; logging must never change the outcome.
        runCatching { Log.w(TAG, "Day summary failed: ${error.javaClass.name}") }
    }

    class Factory(private val container: AppContainer, private val date: LocalDate) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DaySummaryViewModel(container, date) as T
    }

    private companion object {
        const val TAG = "FudAIDaySummary"
    }
}
