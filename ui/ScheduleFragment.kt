package com.example.speechrecognizer.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.Exam
import com.example.speechrecognizer.data.Lesson
import com.example.speechrecognizer.data.ScheduleStorage
import com.example.speechrecognizer.data.WeekType
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.widget.Toast

import com.example.speechrecognizer.data.PeriodManager


class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private lateinit var lessonsAdapter: LessonsAdapter
    private lateinit var examsAdapter: ExamsAdapter
    private lateinit var allLessonsAdapter: LessonsAdapter // Новый адаптер для 3-й вкладки

    private lateinit var tvCurrentWeekStatus: TextView
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private var isEditModeActive = false
    private var currentWeekFilter = WeekType.CHISLITEL
    private var weekOffset = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().apply {
            findViewById<View>(R.id.fabMain)?.visibility = View.GONE
            findViewById<View>(R.id.fabAddReminder)?.visibility = View.GONE
            findViewById<View>(R.id.fabAddTimer)?.visibility = View.GONE
            findViewById<View>(R.id.notificationContainer)?.visibility = View.GONE
            findViewById<View>(R.id.btnSpeak)?.apply { visibility = View.VISIBLE; bringToFront() }
        }

        val btnBack: View = view.findViewById(R.id.btnBackSchedule)
        val btnMainEdit: View = view.findViewById(R.id.btnEditSchedule)
        val btnAddTop: View = view.findViewById(R.id.btnAddScheduleTop)
        tabLayout = view.findViewById(R.id.tabLayoutSchedule)
        viewPager = view.findViewById(R.id.viewPagerSchedule)

        val inflater = LayoutInflater.from(requireContext())
        val pageLessons = inflater.inflate(R.layout.page_schedule_lessons, null)
        val pageExams = inflater.inflate(R.layout.page_schedule_exams, null)
        val pageAllLessons = inflater.inflate(R.layout.page_schedule_all, null) // 3-я страница


        // Настройка 1-й вкладки
        val rvLessons = pageLessons.findViewById<RecyclerView>(R.id.rvLessonsList)
        tvCurrentWeekStatus = pageLessons.findViewById(R.id.tvCurrentWeekStatus)
        val btnPrevWeek: View = pageLessons.findViewById(R.id.btnPrevWeek)
        val btnNextWeek: View = pageLessons.findViewById(R.id.btnNextWeek)
        rvLessons.layoutManager = LinearLayoutManager(requireContext())

        // Настройка 2-й вкладки
        val rvExams = pageExams.findViewById<RecyclerView>(R.id.rvExamsList)
        rvExams.layoutManager = LinearLayoutManager(requireContext())

        // Настройка 3-й вкладки
        val rvAllLessons = pageAllLessons.findViewById<RecyclerView>(R.id.rvAllLessonsList)
        rvAllLessons.layoutManager = LinearLayoutManager(requireContext())

        // Инициализация адаптеров
        lessonsAdapter = LessonsAdapter(emptyMap(), { showEditLessonDialog(it) }, { ScheduleStorage.removeLesson(requireContext(), it); refreshUiData() })
        examsAdapter = ExamsAdapter(emptyMap(), { showEditExamDialog(it) }, { ScheduleStorage.removeExam(requireContext(), it); refreshUiData() })

        // 3-й адаптер работает на изменение аналогично первому
        allLessonsAdapter = LessonsAdapter(emptyMap(), { showEditLessonDialog(it) }, { ScheduleStorage.removeLesson(requireContext(), it); refreshUiData() })

        rvLessons.adapter = lessonsAdapter
        rvExams.adapter = examsAdapter
        rvAllLessons.adapter = allLessonsAdapter

        // Передаем три страницы в Pager
        viewPager.adapter =
            ExamsAdapter.SchedulePagerAdapter(pageLessons, pageExams, pageAllLessons)

        // Связываем 3 таба со свайпами
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "Занятия"
                1 -> "Экзамены / зачеты"
                else -> "Полное расписание"
            }
        }.attach()

        updateWeekSelectorHeader()

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        btnMainEdit.setOnClickListener {
            isEditModeActive = !isEditModeActive
            lessonsAdapter.setEditMode(isEditModeActive)
            examsAdapter.setEditMode(isEditModeActive)
            allLessonsAdapter.setEditMode(isEditModeActive) // Активируем карандаш и на 3 вкладке
            btnMainEdit.alpha = if (isEditModeActive) 0.5f else 1.0f
        }

        val toggleWeekRunnable = Runnable {
            weekOffset++
            currentWeekFilter = if (currentWeekFilter == WeekType.CHISLITEL) WeekType.ZNAMENATEL else WeekType.CHISLITEL
            updateWeekSelectorHeader()
        }
        btnPrevWeek.setOnClickListener {
            weekOffset-- // Корректно уменьшаем смещение недели
            // Инвертируем тип недели на противоположный
            currentWeekFilter = if (currentWeekFilter == WeekType.CHISLITEL) WeekType.ZNAMENATEL else WeekType.CHISLITEL
            updateWeekSelectorHeader() // Перерисовываем заголовок и списки
        }

        btnNextWeek.setOnClickListener {
            weekOffset++ // Корректно увеличиваем смещение недели
            // Инвертируем тип недели на противоположный
            currentWeekFilter = if (currentWeekFilter == WeekType.CHISLITEL) WeekType.ZNAMENATEL else WeekType.CHISLITEL
            updateWeekSelectorHeader() // Перерисовываем заголовок и списки
        }

        btnAddTop.setOnClickListener {
            if (viewPager.currentItem == 1) showEditExamDialog(null) else showEditLessonDialog(null)
        }
    }

    private fun updateWeekSelectorHeader() {
        val calendar = Calendar.getInstance(Locale("ru"))
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset)

        val currentWeekNumber = getAcademicWeekNumber(calendar)
        val weekName = if (currentWeekFilter == WeekType.CHISLITEL) "Числитель" else "Знаменатель"

        if (ScheduleStorage.isDateBasedMode) {
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val startStr = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(calendar.time)
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            val endStr = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(calendar.time)
            tvCurrentWeekStatus.text = "$weekName ($startStr - $endStr)"
        } else {
            tvCurrentWeekStatus.text = "$weekName ($currentWeekNumber неделя)"
        }
        refreshUiData()
    }

    private fun getAcademicWeekNumber(calendar: Calendar): Int {
        val targetCal = calendar.clone() as Calendar
        val currentYear = targetCal.get(Calendar.YEAR)
        val startYear = if (targetCal.get(Calendar.MONTH) >= Calendar.SEPTEMBER) currentYear else currentYear - 1
        val sept1 = Calendar.getInstance().apply { set(Calendar.YEAR, startYear); set(Calendar.MONTH, Calendar.SEPTEMBER); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
        val diff = targetCal.timeInMillis - sept1.timeInMillis
        val weeks = (diff / (1000 * 60 * 60 * 24 * 7)).toInt() + 1
        return if (weeks <= 0) 1 else weeks
    }

    private fun refreshUiData() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset)
        val currentWeekNumber = getAcademicWeekNumber(calendar)

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
        val startOfWeekMs = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59)
        val endOfWeekMs = calendar.timeInMillis

        // 1-я ВКЛАДКА: Текущие занятия недели (с учетом выбранного периода)
        val filteredLessons = ScheduleStorage.lessonsList.filter { lesson ->
            // Проверка 1: Принадлежит ли пара ТЕКУЩЕМУ активному семестру/четверти
            if (lesson.periodId != PeriodManager.activePeriodId) return@filter false

            if (lesson.isDateMode != ScheduleStorage.isDateBasedMode) return@filter false
            val isCorrectWeekType = lesson.weekType == WeekType.BOTH || lesson.weekType == currentWeekFilter

            if (ScheduleStorage.isDateBasedMode) {
                isCorrectWeekType && ScheduleStorage.isLessonInDateRange(lesson.period, startOfWeekMs, endOfWeekMs)
            } else {
                val isCorrectWeekNumber = if (lesson.period.isNotEmpty()) {
                    val parts = lesson.period.split("-")
                    if (parts.size == 2) {
                        currentWeekNumber in (parts[0].trim().toIntOrNull() ?: 0)..(parts[1].trim().toIntOrNull() ?: Int.MAX_VALUE)
                    } else lesson.period.trim() == currentWeekNumber.toString()
                } else true
                isCorrectWeekType && isCorrectWeekNumber
            }
        }
        lessonsAdapter.updateData(filteredLessons.groupBy { it.dayOfWeek })

        // 2-я ВКЛАДКА: Экзамены (фильтруем по текущему активному периоду)
        val filteredExams = ScheduleStorage.examsList.filter { exam ->
            exam.periodId == PeriodManager.activePeriodId
        }
        examsAdapter.updateData(filteredExams.groupBy { it.date })

        // 3-я ВКЛАДКА: Полное расписание (выводим ВСЕ пары текущего периода без привязки к неделе на экране)
        val allPeriodLessons = ScheduleStorage.lessonsList.filter { lesson ->
            lesson.periodId == PeriodManager.activePeriodId && lesson.isDateMode == ScheduleStorage.isDateBasedMode
        }
        allLessonsAdapter.updateData(allPeriodLessons.groupBy { it.dayOfWeek })
    }



    override fun onDestroyView() {
        requireActivity().apply {
            findViewById<View>(R.id.fabMain)?.visibility = View.VISIBLE
            findViewById<View>(R.id.notificationContainer)?.visibility = View.VISIBLE
            findViewById<View>(R.id.btnSpeak)?.apply { visibility = View.VISIBLE; bringToFront() }
        }
        super.onDestroyView()
    }

    private fun showEditLessonDialog(lesson: Lesson?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(if (lesson == null) "Добавить занятие" else "Редактировать занятие")

        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }
        val elementParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 20) }

        // 1. Выбор дня недели
        val spinnerDay = Spinner(context).apply { layoutParams = elementParams }
        val days = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
        spinnerDay.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, days).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        lesson?.let { spinnerDay.setSelection(days.indexOf(it.dayOfWeek)) }

        // 2. Выбор периодичности (Числ/Знамен)
        val spinnerWeekType = Spinner(context).apply { layoutParams = elementParams }
        val weekTypesOptions = listOf("Обе недели", "Только Числитель", "Только Знаменатель")
        spinnerWeekType.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, weekTypesOptions).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        lesson?.let {
            val selIdx = when(it.weekType) { WeekType.BOTH -> 0; WeekType.CHISLITEL -> 1; WeekType.ZNAMENATEL -> 2 }
            spinnerWeekType.setSelection(selIdx)
        }

        // 3. Выбор времени (интервал пары)
        var selectedTimeRange = lesson?.time ?: "10:10-11:40"
        val btnTimePicker = Button(context).apply {
            layoutParams = elementParams
            text = "Выбрать время: $selectedTimeRange"
            setOnClickListener {
                android.app.TimePickerDialog(context, { _, hourStart, minStart ->
                    val startTimeStr = String.format(Locale.US, "%02d:%02d", hourStart, minStart)
                    android.app.TimePickerDialog(context, { _, hourEnd, minEnd ->
                        val endTimeStr = String.format(Locale.US, "%02d:%02d", hourEnd, minEnd)
                        selectedTimeRange = "$startTimeStr-$endTimeStr"
                        text = "Выбрать время: $selectedTimeRange"
                    }, hourStart + 1, minStart + 30, true).show()
                }, 10, 10, true).show()
            }
        }

        // 4. Поля ввода числового диапазона недель "С ... По ..."
        val llWeekRangeContainer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = elementParams }
        val initialParts = lesson?.period?.split("-") ?: emptyList()
        val startWeekVal = if (initialParts.isNotEmpty()) initialParts[0].trim() else ""
        val endWeekVal = if (initialParts.size == 2) initialParts[1].trim() else ""

        val etStartWeek = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = "С недели"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(startWeekVal)
        }
        val etEndWeek = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = "По неделю"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(endWeekVal)
        }
        llWeekRangeContainer.addView(etStartWeek)
        llWeekRangeContainer.addView(TextView(context).apply { text = " — "; setPadding(10, 0, 10, 0); gravity = android.view.Gravity.CENTER })
        llWeekRangeContainer.addView(etEndWeek)

        // 5. Кнопка календаря для дат
        var finalDatePeriodValue = if (ScheduleStorage.isDateBasedMode) lesson?.period ?: "" else ""
        val btnDatePeriodPicker = Button(context).apply {
            layoutParams = elementParams
            text = if (finalDatePeriodValue.isNotEmpty()) "Период дат: $finalDatePeriodValue" else "Выбрать диапазон дат"
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(context, { _, year, month, day ->
                    val startStr = String.format(Locale.US, "%02d.%02d.%04d", day, month + 1, year)
                    DatePickerDialog(context, { _, yEnd, mEnd, dEnd ->
                        val endStr = String.format(Locale.US, "%02d.%02d.%04d", dEnd, mEnd + 1, yEnd)
                        finalDatePeriodValue = "$startStr-$endStr"
                        text = "Период дат: $finalDatePeriodValue"
                    }, year, month, day + 7).apply { setTitle("Выберите дату окончания") }.show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply { setTitle("Выберите дату начала") }.show()
            }
        }

        // 6. Выпадающий список (Spinner) предметов
        val spinnerSubject = Spinner(context).apply { layoutParams = elementParams }
        val isListEmpty = ScheduleStorage.subjectsNamesList.isEmpty() || ScheduleStorage.subjectsNamesList.first().startsWith("Сначала")

        // ИСПРАВЛЕНО: Передаем subjectsNamesList (строки), а не subjectsList (объекты), чтобы спиннер выводил текст, а не хэш-коды
        val subjectAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, ScheduleStorage.subjectsNamesList).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSubject.adapter = subjectAdapter

        // Если это редактирование, находим старый предмет в Spinner
        lesson?.let { oldLesson ->
            val exactMatch = ScheduleStorage.subjectsNamesList.firstOrNull { oldLesson.subject.contains(it) }
            // ИСПРАВЛЕНО: индекс ищем строго в subjectsNamesList для исключения ошибки Type Inference Failed
            exactMatch?.let { spinnerSubject.setSelection(ScheduleStorage.subjectsNamesList.indexOf(it)) }
        }

        // 7. ВСТАВЛЕНО: Текстовое поле ввода дополнительной информации (Преподаватель, Кабинет)
        val etLessonDetails = EditText(context).apply {
            layoutParams = elementParams
            hint = "Преподаватель, аудитория (например: доц. Ступин, а.308)"
            val oldSubjectText = lesson?.subject ?: ""
            setText(if (oldSubjectText.contains(",")) oldSubjectText.substringAfter(",").trim() else "")
        }

        // Сборка слоев UI на экране диалога
        layout.addView(TextView(context).apply { text = "День недели:" })
        layout.addView(spinnerDay)
        layout.addView(TextView(context).apply { text = "Периодичность:" })
        layout.addView(spinnerWeekType)
        layout.addView(btnTimePicker)

        if (ScheduleStorage.isDateBasedMode) {
            layout.addView(btnDatePeriodPicker)
        } else {
            layout.addView(TextView(context).apply { text = "Период недель (числа):" })
            layout.addView(llWeekRangeContainer)
        }

        layout.addView(TextView(context).apply { text = "Учебный предмет:" })
        layout.addView(spinnerSubject)
        layout.addView(TextView(context).apply { text = "Дополнительная информация:" })
        layout.addView(etLessonDetails)
        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { dialog, _ ->
            if (isListEmpty) {
                Toast.makeText(context, "Не удалось сохранить: список предметов пуст!", Toast.LENGTH_LONG).show()
                dialog.dismiss()
                return@setPositiveButton
            }

            val day = spinnerDay.selectedItem.toString()
            val subj = spinnerSubject.selectedItem.toString()
            val details = etLessonDetails.text.toString().trim()

            // Объединяем предмет и доп. информацию через запятую
            val finalSubjectText = if (details.isNotEmpty()) "$subj, $details" else subj

            val periodText = if (ScheduleStorage.isDateBasedMode) {
                finalDatePeriodValue
            } else {
                val sW = etStartWeek.text.toString().trim()
                val eW = etEndWeek.text.toString().trim()
                if (sW.isNotEmpty() && eW.isNotEmpty()) "$sW-$eW" else if (sW.isNotEmpty()) sW else ""
            }

            val selectedWType = when (spinnerWeekType.selectedItemPosition) {
                1 -> WeekType.CHISLITEL
                2 -> WeekType.ZNAMENATEL
                else -> WeekType.BOTH
            }

            // Собираем готовый объект Lesson с привязкой к активному ID периода
            val newLesson = Lesson(
                id = lesson?.id ?: java.util.UUID.randomUUID().toString(),
                periodId = PeriodManager.activePeriodId, // Сохраняем привязку к семестру
                dayOfWeek = day,
                time = selectedTimeRange,
                subject = finalSubjectText,
                period = periodText,
                isDateMode = ScheduleStorage.isDateBasedMode,
                weekType = selectedWType
            )

            // Ищем индекс по ID и обновляем базу данных
            val index = ScheduleStorage.lessonsList.indexOfFirst { it.id == lesson?.id }
            if (index == -1) {
                ScheduleStorage.addLesson(requireContext(), newLesson)
            } else {
                ScheduleStorage.updateLessonAt(requireContext(), index, newLesson)
            }

            // Метод обновления UI, который мы переписывали под семестры в прошлых шагах
            refreshUiData()
        }
        builder.setNegativeButton("Отмена", null).show()
    }




    private fun showEditExamDialog(exam: Exam?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(if (exam == null) "Добавить экзамен/зачет" else "Редактировать")

        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }
        val elementParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 20) }

        val calendar = Calendar.getInstance()
        var selectedDate = exam?.date ?: "15 мая 2026"
        var selectedDayOfWeek = exam?.dayOfWeek ?: "Пятница"
        var selectedTime = exam?.time ?: "12:00"

        // 1. Выбор даты с календаря
        val btnDatePicker = Button(context).apply {
            layoutParams = elementParams
            text = "Дата: $selectedDate ($selectedDayOfWeek)"
            setOnClickListener {
                DatePickerDialog(context, { _, year, month, dayOfMonth ->
                    val selectedCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                    selectedDate = SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(selectedCal.time)
                    selectedDayOfWeek = SimpleDateFormat("EEEE", Locale("ru")).format(selectedCal.time)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
                    text = "Дата: $selectedDate ($selectedDayOfWeek)"
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        // 2. Выбор времени
        val btnTimePicker = Button(context).apply {
            layoutParams = elementParams
            text = "Время: $selectedTime"
            setOnClickListener {
                TimePickerDialog(context, { _, hourOfDay, minute ->
                    selectedTime = String.format("%02d:%02d", hourOfDay, minute)
                    text = "Время: $selectedTime"
                }, 12, 0, true).show()
            }
        }

        // 3. ИСПРАВЛЕНО: Выпадающий список (Spinner) предметов вместо EditText поля ввода деталей
        val spinnerSubject = Spinner(context).apply { layoutParams = elementParams }
        val isListEmpty = ScheduleStorage.subjectsNamesList.isEmpty() || ScheduleStorage.subjectsNamesList.first().startsWith("Сначала")

// ИСПРАВЛЕНО: Передаем только имена (список строк), а не объекты предметов
        val subjectsList = if (isListEmpty) listOf("Сначала добавьте предметы в параметры") else ScheduleStorage.subjectsNamesList
        val subjectAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectsList).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSubject.adapter = subjectAdapter

// Если это редактирование, находим старый предмет в Spinner
        exam?.let { oldExam ->
            // ИСПРАВЛЕНО: ищем совпадение по subjectsNamesList
            val exactMatch = ScheduleStorage.subjectsNamesList.firstOrNull { oldExam.details.contains(it) }
            exactMatch?.let { spinnerSubject.setSelection(ScheduleStorage.subjectsNamesList.indexOf(it)) }
        }



        // 4. Поле для ввода типа контроля (Экзамен/Зачет/Пересдача)
        val etExamTypeHint = EditText(context).apply {
            layoutParams = elementParams
            hint = "Тип контроля (например: экз, зач, комиссия)"
            // Извлекаем старый тип, отсекая название предмета, если оно там было
            val oldDetails = exam?.details ?: ""
            setText(oldDetails.substringAfter(",").trim())
        }

        layout.addView(TextView(context).apply { text = "Выберите дату и время:" })
        layout.addView(btnDatePicker)
        layout.addView(btnTimePicker)
        layout.addView(TextView(context).apply { text = "Учебный предмет:" })
        layout.addView(spinnerSubject)
        layout.addView(TextView(context).apply { text = "Дополнительная информация:" })
        layout.addView(etExamTypeHint)
        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { _, _ ->
            val subj = if (ScheduleStorage.subjectsList.isNotEmpty()) spinnerSubject.selectedItem.toString() else "Без названия"
            val typeHint = etExamTypeHint.text.toString().trim()

            // Формируем финальное описание: Предмет + дополнительный тип контроля (например: "Математика, экз. а.410 - пересдача")
            val finalDetailsText = if (typeHint.isNotEmpty()) "$subj, $typeHint" else subj

            if (exam == null) {
                ScheduleStorage.addExam(requireContext(), Exam(date = selectedDate, dayOfWeek = selectedDayOfWeek, time = selectedTime, details = finalDetailsText))
            } else {
                val index = ScheduleStorage.examsList.indexOf(exam)
                if (index != -1) {
                    val updatedExam = exam.copy(date = selectedDate, dayOfWeek = selectedDayOfWeek, time = selectedTime, details = finalDetailsText)
                    ScheduleStorage.updateExamAt(requireContext(), index, updatedExam)
                }
            }
            refreshUiData()
        }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

}
