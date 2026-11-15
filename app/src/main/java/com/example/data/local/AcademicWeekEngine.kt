package com.example.data.local

import com.example.data.model.AcademicWeekStatus
import com.example.data.model.SystemSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object AcademicWeekEngine {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayIso(): String = isoFormat.format(Date())

    fun parseIsoDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            isoFormat.parse(dateStr.trim().take(10))
        } catch (_: Exception) {
            null
        }
    }

    fun getMondayOfWeek(date: Date = Date()): Calendar {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val diffToMonday = when (day) {
            Calendar.SUNDAY -> -6
            else -> Calendar.MONDAY - day
        }
        cal.add(Calendar.DAY_OF_YEAR, diffToMonday)
        return cal
    }

    fun calculateAcademicWeek(
        settings: SystemSettings,
        selectedTerm: String = settings.activeTerm,
        maxWeeks: Int = 12,
        referenceDate: Date = Date()
    ): AcademicWeekStatus {
        val isHistorical = selectedTerm != "ALL" &&
            !selectedTerm.equals(settings.activeTerm, ignoreCase = true)

        val currentMonday = getMondayOfWeek(referenceDate)
        val overrideStr = settings.manualWeekOverride.trim().lowercase()

        if (overrideStr.isNotEmpty() && overrideStr != "none" && overrideStr != "null") {
            if (overrideStr == "end of term" || overrideStr == "vacation" || overrideStr == "end_of_term") {
                return AcademicWeekStatus(
                    weekNumber = maxWeeks,
                    isEndOfTerm = true,
                    label = "End of Term",
                    isHistorical = isHistorical
                )
            }
            val forced = overrideStr.toIntOrNull()
            if (forced != null) {
                val clamped = forced.coerceIn(1, maxWeeks)
                return AcademicWeekStatus(
                    weekNumber = clamped,
                    isEndOfTerm = false,
                    label = "Week $clamped",
                    isHistorical = isHistorical
                )
            }
        }

        val parsedStart = parseIsoDate(settings.termStartDate)
        val startMonday = if (parsedStart != null) getMondayOfWeek(parsedStart) else currentMonday
        val msPerWeek = 7.0 * 24.0 * 60.0 * 60.0 * 1000.0
        val diffWeeks = ((currentMonday.timeInMillis - startMonday.timeInMillis) / msPerWeek).roundToInt()

        return when {
            diffWeeks < 0 -> AcademicWeekStatus(
                weekNumber = 1,
                isEndOfTerm = false,
                label = "Week 1",
                isHistorical = isHistorical
            )
            diffWeeks >= maxWeeks -> AcademicWeekStatus(
                weekNumber = maxWeeks,
                isEndOfTerm = true,
                label = "End of Term",
                isHistorical = isHistorical
            )
            else -> {
                val active = diffWeeks + 1
                AcademicWeekStatus(
                    weekNumber = active,
                    isEndOfTerm = false,
                    label = "Week $active",
                    isHistorical = isHistorical
                )
            }
        }
    }

    fun getAnchorStartDateForWeek(targetWeek: Int, referenceDate: Date = Date()): String {
        val currentMonday = getMondayOfWeek(referenceDate)
        val offset = (targetWeek - 1).coerceAtLeast(0)
        currentMonday.add(Calendar.DAY_OF_YEAR, -(offset * 7))
        return isoFormat.format(currentMonday.time)
    }

    fun formatWeekLabel(weekNumber: Int): String = when {
        weekNumber > 12 -> "End of Term"
        weekNumber < 1 -> "Week 1"
        else -> "Week $weekNumber"
    }

    fun currentWeekRangeBounds(referenceDate: Date = Date()): Pair<String, String> {
        val monday = getMondayOfWeek(referenceDate)
        val sunday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
        return isoFormat.format(monday.time) to isoFormat.format(sunday.time)
    }

    fun currentMonthRangeBounds(referenceDate: Date = Date()): Pair<String, String> {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = isoFormat.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = isoFormat.format(cal.time)
        return start to end
    }
}
