package com.example.speechrecognizer.data

import android.content.Context
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale

object DiaryStorage {
    val lessonGrades = mutableListOf<LessonGrade>()
    val examGrades = mutableListOf<ExamGrade>()

    private const val PREFS_NAME = "diary_grades_prefs"
    private const val KEY_LESSON_GRADES = "lesson_grades_json"
    private const val KEY_EXAM_GRADES = "exam_grades_json"

    private const val KEY_AI_ANALYSIS_CACHE = "ai_analysis_text_cache"

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lessonJson = prefs.getString(KEY_LESSON_GRADES, null)
        val examJson = prefs.getString(KEY_EXAM_GRADES, null)

        lessonGrades.clear()
        examGrades.clear()

        if (!lessonJson.isNullOrEmpty()) {
            lessonGrades.addAll(gson.fromJson(lessonJson, Array<LessonGrade>::class.java))
        }
        if (!examJson.isNullOrEmpty()) {
            examGrades.addAll(gson.fromJson(examJson, Array<ExamGrade>::class.java))
        }
        sortAll()
    }

    private fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_LESSON_GRADES, gson.toJson(lessonGrades))
            putString(KEY_EXAM_GRADES, gson.toJson(examGrades))
            apply()
        }
    }

    fun addLessonGrade(context: Context, grade: LessonGrade) {
        lessonGrades.add(grade)
        sortLessonGrades()
        save(context)
    }

    fun addExamGrade(context: Context, grade: ExamGrade) {
        examGrades.add(grade)
        sortExamGrades()
        save(context)
    }

    fun removeLessonGrade(context: Context, grade: LessonGrade) {
        lessonGrades.remove(grade)
        save(context)
    }

    fun removeExamGrade(context: Context, grade: ExamGrade) {
        examGrades.remove(grade)
        save(context)
    }

    fun updateLessonGradeAt(context: Context, index: Int, grade: LessonGrade) {
        if (index in lessonGrades.indices) {
            lessonGrades[index] = grade
            sortLessonGrades()
            save(context)
        }
    }

    fun updateExamGradeAt(context: Context, index: Int, grade: ExamGrade) {
        if (index in examGrades.indices) {
            examGrades[index] = grade
            sortExamGrades()
            save(context)
        }
    }

    fun clearAllGrades(context: Context) {
        lessonGrades.clear()
        examGrades.clear()
        save(context) // Перезаписывает пустые списки в SharedPreferences
    }

    fun clearCurrentPeriodGrades(context: Context) {
        lessonGrades.removeAll { it.periodId == PeriodManager.activePeriodId }
        examGrades.removeAll { it.periodId == PeriodManager.activePeriodId }
        save(context)
    }

    // Метод сохранения полученного кэша на диск
    fun saveAiAnalysisCache(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AI_ANALYSIS_CACHE, text).apply()
    }

    // Метод чтения кэша с диска
    fun getAiAnalysisCache(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AI_ANALYSIS_CACHE, null)
    }



    private fun sortAll() { sortLessonGrades(); sortExamGrades() }
    private fun sortLessonGrades() { lessonGrades.sortByDescending { it.timestamp } }
    private fun sortExamGrades() { examGrades.sortByDescending { it.timestamp } }
}
