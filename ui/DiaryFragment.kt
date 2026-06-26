package com.example.speechrecognizer.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.DiaryStorage
import com.example.speechrecognizer.data.ExamGrade
import com.example.speechrecognizer.data.LessonGrade
import com.example.speechrecognizer.data.PeriodManager
import com.example.speechrecognizer.data.ScheduleStorage
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class DiaryFragment : Fragment(R.layout.fragment_diary) {

    private lateinit var lessonAdapter: LessonGradesAdapter
    private lateinit var examAdapter: ExamGradesAdapter
    private lateinit var predictAdapter: PredictAdapter

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private lateinit var tvOverallExamGrade: TextView
    private lateinit var tvOverallPredictGrade: TextView

    private var isEditModeActive = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        com.example.speechrecognizer.data.PeriodManager.init(requireContext())
        com.example.speechrecognizer.data.DiaryStorage.init(requireContext())

        requireActivity().apply {
            findViewById<View>(R.id.fabMain)?.visibility = View.GONE
            findViewById<View>(R.id.fabAddReminder)?.visibility = View.GONE
            findViewById<View>(R.id.fabAddTimer)?.visibility = View.GONE
            findViewById<View>(R.id.notificationContainer)?.visibility = View.GONE
            findViewById<View>(R.id.btnSpeak)?.apply { visibility = View.VISIBLE; bringToFront() }
        }

        val btnBack: View = view.findViewById(R.id.btnBackDiary)
        val btnMainEdit: View = view.findViewById(R.id.btnDeleteSelectedDiary)
        val btnAddTop: View = view.findViewById(R.id.btnAddDiaryTop)
        tabLayout = view.findViewById(R.id.tabLayoutDiary)
        viewPager = view.findViewById(R.id.viewPagerDiary)

        val inflater = LayoutInflater.from(requireContext())

        val pLessons = inflater.inflate(R.layout.page_diary_lessons, null)
        val pExams = inflater.inflate(R.layout.page_diary_exams, null)
        val pPredict = inflater.inflate(R.layout.page_diary_predict, null)

        // Инициализация Вкладки 1
        val rvLessons = pLessons.findViewById<RecyclerView>(R.id.rvDiaryLessons)
        rvLessons.layoutManager = LinearLayoutManager(requireContext())

        // Инициализация Вкладки 2
        val rvExams = pExams.findViewById<RecyclerView>(R.id.rvDiaryExams)
        rvExams.layoutManager = LinearLayoutManager(requireContext())
        tvOverallExamGrade = pExams.findViewById(R.id.tvOverallExamGrade)

        // Инициализация Вкладки 3
        val rvPredict = pPredict.findViewById<RecyclerView>(R.id.rvPredictList)
        rvPredict.layoutManager = LinearLayoutManager(requireContext())
        tvOverallPredictGrade = pPredict.findViewById(R.id.tvOverallPredictGrade)

        // Инициализация адаптеров
        // СТАЛО (ИСПРАВЛЕНО):
        lessonAdapter = LessonGradesAdapter(
            { showEditLessonGradeDialog(it) },
            { DiaryStorage.removeLessonGrade(requireContext(), it); refreshUi() }
        )
        examAdapter = ExamGradesAdapter(
            { showEditExamGradeDialog(it) },
            { DiaryStorage.removeExamGrade(requireContext(), it); refreshUi() }
        )
        predictAdapter = PredictAdapter(emptyList())

        rvLessons.adapter = lessonAdapter
        rvExams.adapter = examAdapter
        rvPredict.adapter = predictAdapter

        // Связываем все 3 созданные страницы
        viewPager.adapter = DiaryPagerAdapter(pLessons, pExams, pPredict)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Занятия"
                1 -> "Итоговые / Сессия"
                else -> "Прогнозирование"
            }
        }.attach()

        refreshUi()

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        btnMainEdit.setOnClickListener {
            isEditModeActive = !isEditModeActive
            lessonAdapter.setEditMode(isEditModeActive)
            examAdapter.setEditMode(isEditModeActive)
            btnMainEdit.alpha = if (isEditModeActive) 0.5f else 1.0f
        }

        btnAddTop.setOnClickListener {
            if (viewPager.currentItem == 0) showEditLessonGradeDialog(null) else showEditExamGradeDialog(null)
        }
    }

    private fun refreshUi() {
        // 1. Оценки за занятия строго фильтруются по ТЕКУЩЕМУ активному периоду
        val filteredLessons = DiaryStorage.lessonGrades.filter { it.periodId == PeriodManager.activePeriodId }

        // 2. Итоговые оценки за сессию НЕ фильтруются — берем ВСЕ оценки из памяти
        val allExams = DiaryStorage.examGrades

        // Сортируем итоговые оценки так, чтобы оценки текущего периода были вверху,
        // а остальные группировались ниже (или по timestamp)
        val sortedExams = allExams.sortedWith(
            compareByDescending<ExamGrade> { it.periodId == PeriodManager.activePeriodId }
                .thenByDescending { it.timestamp }
        )

        // Обновляем данные в адаптерах
        lessonAdapter.updateData(filteredLessons)
        examAdapter.updateData(sortedExams) // Передаем все отсортированные итоговые оценки

        // Запускаем расчеты (в сессию передаем ВСЕ оценки, либо только текущие — настроено ниже)
        calculateExamsOverallGrade(sortedExams)
        calculatePredictions(filteredLessons)
    }

    // Расчет общего балла сессии (считает среднее по ВСЕМ выставленным итоговым оценкам за год)
    private fun calculateExamsOverallGrade(allExams: List<ExamGrade>) {
        val validGrades = allExams.mapNotNull { parseGradeToDouble(it.grade) }
        if (validGrades.isNotEmpty()) {
            val avg = validGrades.average()
            tvOverallExamGrade.text = String.format(Locale.US, "%.2f", avg)
            tvOverallExamGrade.setTextColor(getGradientColor(avg))
        } else {
            tvOverallExamGrade.text = "—"
            tvOverallExamGrade.setTextColor(Color.WHITE)
        }
    }

    // Расчет прогнозов (считает аналитику СТРОГО по оценкам выбранного в параметрах периода)
    private fun calculatePredictions(filteredLessons: List<LessonGrade>) {
        if (filteredLessons.isEmpty()) {
            predictAdapter.updateData(emptyList())
            tvOverallPredictGrade.text = "—"
            tvOverallPredictGrade.setTextColor(Color.WHITE)
            return
        }

        val groupedBySubject = filteredLessons.groupBy { it.subject }
        val predictList = mutableListOf<Pair<String, Double>>()
        val subjectAveragesForOverall = mutableListOf<Double>()

        for ((subject, grades) in groupedBySubject) {
            val numericGrades = grades.mapNotNull { parseGradeToDouble(it.grade) }
            if (numericGrades.isNotEmpty()) {
                val subjectAvg = numericGrades.average()
                predictList.add(Pair(subject, subjectAvg))
                subjectAveragesForOverall.add(subjectAvg)
            } else {
                predictList.add(Pair(subject, 0.0))
            }
        }

        predictList.sortBy { it.first }
        predictAdapter.updateData(predictList)

        if (subjectAveragesForOverall.isNotEmpty()) {
            val overallAvg = subjectAveragesForOverall.average()
            tvOverallPredictGrade.text = String.format(Locale.US, "%.2f", overallAvg)
            tvOverallPredictGrade.setTextColor(getGradientColor(overallAvg))
        } else {
            tvOverallPredictGrade.text = "—"
            tvOverallPredictGrade.setTextColor(Color.WHITE)
        }
    }



    private fun parseGradeToDouble(gradeText: String): Double? {
        val trimmed = gradeText.trim().lowercase()
        val numeric = trimmed.toDoubleOrNull()
        if (numeric != null) return numeric

        // Приводим к нижнему регистру и удаляем все точки
        val cleanInput = trimmed.lowercase().replace(".", "")

        return when (cleanInput) {
            "отл", "отлично", "5" -> 5.0
            "хор", "хорошо", "4" -> 4.0
            "уд", "удовл", "удовлетворительно", "3" -> 3.0
            "неуд", "неудовлетворительно", "2" -> 2.0
            else -> null
        }

    }

    override fun onDestroyView() {
        requireActivity().apply {
            findViewById<View>(R.id.fabMain)?.visibility = View.VISIBLE
            findViewById<View>(R.id.notificationContainer)?.visibility = View.VISIBLE
            findViewById<View>(R.id.btnSpeak)?.apply { visibility = View.VISIBLE; bringToFront() }
        }
        super.onDestroyView()
    }

    // --- ДИАЛОГ ДОБАВЛЕНИЯ/РЕДАКТИРОВАНИЯ ОЦЕНКИ ЗА ЗАНЯТИЕ ---
    private fun showEditLessonGradeDialog(grade: LessonGrade?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(if (grade == null) "Добавить оценку" else "Редактировать оценку")

        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 20) }

        val spinnerSubject = Spinner(context).apply { layoutParams = params }

// ИСПРАВЛЕНО: Проверяем пустой список по subjectsNamesList
        val isListEmpty = ScheduleStorage.subjectsNamesList.isEmpty() || ScheduleStorage.subjectsNamesList.first().startsWith("Сначала")

// ИСПРАВЛЕНО: Передаем subjectsNamesList (строки текста), а не subjectsList (объекты)
        val subjectsList = if (isListEmpty) listOf("Сначала добавьте предметы в параметры!") else ScheduleStorage.subjectsNamesList

        spinnerSubject.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectsList).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

// ИСПРАВЛЕНО: индекс старого предмета ищем строго внутри строкового списка subjectsNamesList
        grade?.let { spinnerSubject.setSelection(ScheduleStorage.subjectsNamesList.indexOf(it.subject)) }


        val etGrade = EditText(context).apply { layoutParams = params; hint = "Оценка (например: 5, Зачет)"; setText(grade?.grade ?: "") }

        val cal = Calendar.getInstance()
        var selectedDate = grade?.dateText ?: SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time)
        var selectedTimestamp = grade?.timestamp ?: cal.timeInMillis

        val btnDate = Button(context).apply {
            layoutParams = params
            text = "Дата: $selectedDate"
            setOnClickListener {
                DatePickerDialog(context, { _, y, m, d ->
                    val selectedCal = Calendar.getInstance().apply { set(y, m, d) }
                    selectedDate = String.format("%02d.%02d.%04d", d, m + 1, y)
                    selectedTimestamp = selectedCal.timeInMillis
                    text = "Дата: $selectedDate"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        val etComment = EditText(context).apply { layoutParams = params; hint = "Комментарий (опционально)"; setText(grade?.comment ?: "") }

        layout.addView(TextView(context).apply { text = "Предмет:" })
        layout.addView(spinnerSubject)
        layout.addView(TextView(context).apply { text = "Данные успеваемости:" })
        layout.addView(etGrade)
        layout.addView(btnDate)
        layout.addView(etComment)
        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { dialog, _ ->
            if (isListEmpty) {
                Toast.makeText(context, "Не удалось сохранить: список предметов пуст!", Toast.LENGTH_LONG).show()
                dialog.dismiss()
                return@setPositiveButton
            }

            val chosenSubject = spinnerSubject.selectedItem.toString()
            val newGrade = LessonGrade(
                id = grade?.id ?: UUID.randomUUID().toString(),
                subject = chosenSubject,
                grade = etGrade.text.toString().trim(),
                dateText = selectedDate,
                comment = etComment.text.toString().trim(),
                timestamp = selectedTimestamp
            )

            val index = DiaryStorage.lessonGrades.indexOfFirst { it.id == grade?.id }
            if (index == -1) {
                DiaryStorage.addLessonGrade(requireContext(), newGrade)
            } else {
                DiaryStorage.updateLessonGradeAt(requireContext(), index, newGrade)
            }
            refreshUi()
        }
        builder.setNegativeButton("Отмена", null).show()
    }

    // --- ДИАЛОГ ДОБАВЛЕНИЯ/РЕДАКТИРОВАНИЯ ОЦЕНКИ ЗА СЕССИЮ ---
    private fun showEditExamGradeDialog(grade: ExamGrade?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(if (grade == null) "Добавить итоговую" else "Редактировать")

        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 20) }

        val spinnerSubject = Spinner(context).apply { layoutParams = params }

// ИСПРАВЛЕНО: Проверяем пустой список по subjectsNamesList
        val isListEmpty = ScheduleStorage.subjectsNamesList.isEmpty() || ScheduleStorage.subjectsNamesList.first().startsWith("Сначала")

// ИСПРАВЛЕНО: Передаем subjectsNamesList (строки текста), а не subjectsList (объекты)
        val subjectsList = if (isListEmpty) listOf("Сначала добавьте предметы в параметры!") else ScheduleStorage.subjectsNamesList

        spinnerSubject.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectsList).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

// ИСПРАВЛЕНО: индекс старого предмета ищем строго внутри строкового списка subjectsNamesList
        grade?.let { spinnerSubject.setSelection(ScheduleStorage.subjectsNamesList.indexOf(it.subject)) }


        val etGrade = EditText(context).apply { layoutParams = params; hint = "Оценка / Зачет"; setText(grade?.grade ?: "") }

        val spinnerType = Spinner(context).apply { layoutParams = params }
        val options = listOf("Экзамен", "Зачет", "Полугодие", "Четверть")
        spinnerType.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        grade?.let { spinnerType.setSelection(options.indexOf(it.type)) }

        val cal = Calendar.getInstance()
        var selectedDate = grade?.dateText ?: SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time)
        var selectedTimestamp = grade?.timestamp ?: cal.timeInMillis

        val btnDate = Button(context).apply {
            layoutParams = params; text = "Дата: $selectedDate"
            setOnClickListener {
                DatePickerDialog(context, { _, y, m, d ->
                    val selectedCal = Calendar.getInstance().apply { set(y, m, d) }
                    selectedDate = String.format("%02d.%02d.%04d", d, m + 1, y)
                    selectedTimestamp = selectedCal.timeInMillis
                    text = "Дата: $selectedDate"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        val etComment = EditText(context).apply { layoutParams = params; hint = "Комментарий (опционально)"; setText(grade?.comment ?: "") }

        layout.addView(TextView(context).apply { text = "Предмет:" })
        layout.addView(spinnerSubject)
        layout.addView(TextView(context).apply { text = "Тип аттестации и оценка:" })
        layout.addView(spinnerType)
        layout.addView(etGrade)
        layout.addView(btnDate)
        layout.addView(etComment)
        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { dialog, _ ->
            if (isListEmpty) {
                Toast.makeText(context, "Не удалось сохранить: список предметов пуст!", Toast.LENGTH_LONG).show()
                dialog.dismiss()
                return@setPositiveButton
            }

            val chosenSubject = spinnerSubject.selectedItem.toString()
            val newGrade = ExamGrade(
                id = grade?.id ?: UUID.randomUUID().toString(),
                subject = chosenSubject,
                grade = etGrade.text.toString().trim(),
                type = spinnerType.selectedItem.toString(),
                dateText = selectedDate,
                comment = etComment.text.toString().trim(),
                timestamp = selectedTimestamp
            )

            val index = DiaryStorage.examGrades.indexOfFirst { it.id == grade?.id }
            if (index == -1) {
                DiaryStorage.addExamGrade(requireContext(), newGrade)
            } else {
                DiaryStorage.updateExamGradeAt(requireContext(), index, newGrade)
            }
            refreshUi()
        }
        builder.setNegativeButton("Отмена", null).show()
    }
}
