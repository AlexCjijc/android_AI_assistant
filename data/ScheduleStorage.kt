package com.example.speechrecognizer.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Locale

object ScheduleStorage {

    val lessonsList = mutableListOf<Lesson>()
    val examsList = mutableListOf<Exam>()

    // Список объектов предметов для всех учебных периодов
    val subjectsListAll = mutableListOf<Subject>()

    // Динамический геттер возвращает только предметы активного на данный момент периода
    val subjectsList: List<Subject>
        get() = subjectsListAll.filter { it.periodId == PeriodManager.activePeriodId }

    // Свойство получения только названий предметов для спиннеров в диалогах
    val subjectsNamesList: List<String>
        get() = subjectsList.map { it.name }.ifEmpty { listOf("Сначала добавьте предметы в параметры!") }

    var isDateBasedMode = false

    private const val PREFS_NAME = "schedule_prefs"
    private const val KEY_LESSONS = "lessons_json"
    private const val KEY_EXAMS = "exams_json"
    private const val KEY_SUBJECTS = "subjects_objects_json" // Изменили ключ для JSON массива объектов
    private const val KEY_MODE = "schedule_mode_is_date"

    private val daysOrder = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    private val examDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
    private val rangeDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    private val gson = Gson()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDateBasedMode = prefs.getBoolean(KEY_MODE, false)

        val lessonsJson = prefs.getString(KEY_LESSONS, null)
        val examsJson = prefs.getString(KEY_EXAMS, null)
        val subjectsJson = prefs.getString(KEY_SUBJECTS, null)

        lessonsList.clear()
        examsList.clear()
        subjectsListAll.clear()

        try {
            if (!lessonsJson.isNullOrEmpty()) {
                lessonsList.addAll(gson.fromJson(lessonsJson, Array<Lesson>::class.java))
            } else {
                lessonsList.addAll(listOf(
                    Lesson(dayOfWeek = "Среда", time = "10:10-11:40", subject = "Математика", period = "37", isDateMode = false)
                ))
            }

            if (!examsJson.isNullOrEmpty()) {
                examsList.addAll(gson.fromJson(examsJson, Array<Exam>::class.java))
            }

            // ИСПРАВЛЕНО: Безопасно парсим список объектов Subject
            if (!subjectsJson.isNullOrEmpty()) {
                val type = object : TypeToken<List<Subject>>() {}.type
                val loadedList: List<Subject> = gson.fromJson(subjectsJson, type)
                subjectsListAll.addAll(loadedList)
            } else {
                // Дефолтный стартовый набор предметов, если база полностью пуста
                subjectsListAll.addAll(listOf(
                    Subject(periodId = "default_period", name = "Математика", topic = "Матрицы и интегралы"),
                    Subject(periodId = "default_period", name = "Патентоведение", topic = "Интеллектуальная собственность"),
                    Subject(periodId = "default_period", name = "Инженерная экология", topic = "Защита гидросферы"),
                    Subject(periodId = "default_period", name = "Физика", topic = "Квантовая механика")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sortAll()
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_LESSONS, gson.toJson(lessonsList))
            putString(KEY_EXAMS, gson.toJson(examsList))
            putString(KEY_SUBJECTS, gson.toJson(subjectsListAll)) // ИСПРАВЛЕНО: сохраняем массив объектов
            apply()
        }
    }

    fun toggleScheduleMode(context: Context): Boolean {
        isDateBasedMode = !isDateBasedMode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MODE, isDateBasedMode).apply()
        return isDateBasedMode
    }

    fun isLessonInDateRange(lessonPeriod: String, startOfWeek: Long, endOfWeek: Long): Boolean {
        if (lessonPeriod.isEmpty()) return true
        return try {
            val parts = lessonPeriod.split("-")
            if (parts.size != 2) return true

            val dateStart = rangeDateFormat.parse(parts[0].trim())?.time ?: 0L
            val dateEnd = rangeDateFormat.parse(parts[1].trim())?.time ?: Long.MAX_VALUE

            dateStart <= endOfWeek && dateEnd >= startOfWeek
        } catch (e: Exception) {
            true
        }
    }

    // ИСПРАВЛЕНО: Добавление предмета с поддержкой названия и темы строго в текущий период
    fun addSubject(context: Context, name: String, topic: String) {
        if (name.isEmpty()) return
        // Проверяем уникальность названия внутри ТЕКУЩЕГО учебного периода
        if (subjectsList.none { it.name.lowercase() == name.lowercase() }) {
            subjectsListAll.add(Subject(
                id = java.util.UUID.randomUUID().toString(),
                periodId = PeriodManager.activePeriodId,
                name = name,
                topic = topic
            ))
            subjectsListAll.sortBy { it.name }
            save(context)
        }
    }

    // ИСПРАВЛЕНО: Удаление предмета из общего списка по его уникальному ID
    fun removeSubject(context: Context, subjectId: String) {
        subjectsListAll.removeAll { it.id == subjectId }
        save(context)
    }

    // Полная очистка всей базы данных приложения
    fun clearAll(context: Context) {
        lessonsList.clear()
        examsList.clear()
        subjectsListAll.clear()
        save(context)
    }

    // Очистка расписания текущего активного периода
    fun clearCurrentPeriodSchedule(context: Context) {
        lessonsList.removeAll { it.periodId == PeriodManager.activePeriodId }
        examsList.removeAll { it.periodId == PeriodManager.activePeriodId }
        save(context)
    }

    fun addLesson(context: Context, lesson: Lesson) { lessonsList.add(lesson); sortLessons(); save(context) }
    fun addExam(context: Context, exam: Exam) { examsList.add(exam); sortExams(); save(context) }
    fun removeLesson(context: Context, lesson: Lesson) { lessonsList.remove(lesson); save(context) }
    fun removeExam(context: Context, exam: Exam) { examsList.remove(exam); save(context) }

    fun updateLessonAt(context: Context, index: Int, lesson: Lesson) {
        if (index in lessonsList.indices) { lessonsList[index] = lesson; sortLessons(); save(context) }
    }
    fun updateExamAt(context: Context, index: Int, exam: Exam) {
        if (index in examsList.indices) { examsList[index] = exam; sortExams(); save(context) }
    }

    fun sortAll() { sortLessons(); sortExams() }
    private fun sortLessons() { lessonsList.sortWith(compareBy({ daysOrder.indexOf(it.dayOfWeek) }, { it.time })) }
    private fun sortExams() { examsList.sortWith(compareBy { try { examDateFormat.parse(it.date)?.time ?: 0L } catch (e: Exception) { 0L } }) }
}
