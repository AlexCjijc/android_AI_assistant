package com.example.speechrecognizer.data

import android.content.Context
import com.google.gson.Gson

data class StudyPeriod(
    val id: String,    // "semester_1_2025", "quarter_3_2026"
    val name: String   // "1 Семестр", "3 Четверть"
)

object PeriodManager {
    val periods = mutableListOf<StudyPeriod>()
    var activePeriodId: String = "default_period"

    private const val PREFS_NAME = "periods_prefs"
    private const val KEY_PERIODS_JSON = "periods_list_json"
    private const val KEY_ACTIVE_PERIOD_ID = "active_period_id"
    private val gson = Gson()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        activePeriodId = prefs.getString(KEY_ACTIVE_PERIOD_ID, "default_period") ?: "default_period"

        val json = prefs.getString(KEY_PERIODS_JSON, null)
        periods.clear()
        if (!json.isNullOrEmpty()) {
            periods.addAll(gson.fromJson(json, Array<StudyPeriod>::class.java))
        } else {
            // Начальные дефолтные периоды при первом запуске
            periods.add(StudyPeriod("default_period", "1 Семестр"))
            periods.add(StudyPeriod("semester_2", "2 Семестр"))
            save(context)
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_PERIODS_JSON, gson.toJson(periods))
            putString(KEY_ACTIVE_PERIOD_ID, activePeriodId)
            apply()
        }
    }

    fun addPeriod(context: Context, name: String) {
        val id = "period_${System.currentTimeMillis()}"
        periods.add(StudyPeriod(id, name))
        save(context)
    }

    fun removePeriod(context: Context, id: String) {
        if (periods.size <= 1) return // Нельзя удалить единственный период
        periods.removeAll { it.id == id }
        if (activePeriodId == id) {
            activePeriodId = periods.first().id
        }
        save(context)
    }
}
