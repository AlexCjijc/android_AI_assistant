package com.example.speechrecognizer.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.speechrecognizer.MainActivity
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.DiaryStorage
import com.example.speechrecognizer.data.LessonGrade
import com.example.speechrecognizer.data.PeriodManager
import com.example.speechrecognizer.data.ScheduleStorage
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject


class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var chartPagerAdapter: ChartPagerAdapter

    private val pickAvatarLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            val context = requireContext()
            try {
                context.contentResolver.openInputStream(selectedUri).use { inputStream ->
                    if (inputStream != null) {
                        val avatarFile = java.io.File(context.filesDir, "user_avatar_permanent.jpg")
                        avatarFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        val prefs = context.getSharedPreferences("profile_user_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putString("user_avatar_path", avatarFile.absolutePath).apply()
                        val ivAvatar: ImageView? = view?.findViewById(R.id.ivProfileAvatar)
                        ivAvatar?.setImageBitmap(android.graphics.BitmapFactory.decodeFile(avatarFile.absolutePath))
                        ivAvatar?.imageTintList = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Не удалось сохранить изображение", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        PeriodManager.init(requireContext())
        DiaryStorage.init(requireContext())

        val tvOverallGrade: TextView = view.findViewById(R.id.tvProfileOverallGrade)

        val tvCount5: TextView = view.findViewById(R.id.tvCount5)
        val tvCount4: TextView = view.findViewById(R.id.tvCount4)
        val tvCount3: TextView = view.findViewById(R.id.tvCount3)
        val tvCount2: TextView = view.findViewById(R.id.tvCount2)

        val llBestSubjects: LinearLayout = view.findViewById(R.id.llProfileBestSubjects)
        val llWeakSubjects: LinearLayout = view.findViewById(R.id.llProfileWeakSubjects)

        val tvAiAnalysis: TextView = view.findViewById(R.id.tvAiProfileAnalysis)
        val btnRefreshAi: View = view.findViewById(R.id.btnRegenerateAiAnalysis)

        // Инициализация адаптера для ViewPager2 (Свайп-графики)
        val chartPager: ViewPager2 = view.findViewById(R.id.profileProgressChartPager)
        val tvChartTitle: TextView = view.findViewById(R.id.tvCurrentChartTitle)

        chartPagerAdapter = ChartPagerAdapter()
        chartPager.adapter = chartPagerAdapter

        val allChartPages = mutableListOf<MultiChartPageData>()
        val periodsNames = PeriodManager.periods.map { it.name }

        // --- СТРАНИЦА 1: График общей успеваемости ---
        val totalPeriodPoints = mutableListOf<Pair<String, Double>>()
        PeriodManager.periods.forEach { period ->
            val periodGrades = DiaryStorage.lessonGrades.filter { it.periodId == period.id }.mapNotNull { parseGradeToDouble(it.grade) } +
                    DiaryStorage.examGrades.filter { it.periodId == period.id }.mapNotNull { parseGradeToDouble(it.grade) }
            if (periodGrades.isNotEmpty()) {
                totalPeriodPoints.add(Pair(period.name, periodGrades.average()))
            }
        }
        if (totalPeriodPoints.isNotEmpty()) {
            val overallLine = ChartLineData("Общая успеваемость", Color.parseColor("#7C4DFF"), totalPeriodPoints)
            allChartPages.add(MultiChartPageData(listOf(overallLine), periodsNames))
        }

        // --- СТРАНИЦА 2: Совмещенный график по ВСЕМ предметам ---
        val allGradesInApp = DiaryStorage.lessonGrades + DiaryStorage.examGrades.map {
            LessonGrade(id = it.id, periodId = it.periodId, subject = it.subject, grade = it.grade, dateText = it.dateText, timestamp = it.timestamp)
        }

        val groupedBySubjectName = allGradesInApp.groupBy { gradeItem ->
            if (gradeItem.subject.contains("Subject(")) {
                gradeItem.subject.substringAfter("name=").substringBefore(",")
            } else {
                gradeItem.subject
            }
        }

        val subjectColors = listOf(
            "#4CAF50", "#03A9F4", "#FF9800", "#E91E63", "#9C27B0", "#009688", "#FFEB3B"
        )

        val subjectLinesList = mutableListOf<ChartLineData>()
        var colorIndex = 0

        for ((subjectName, grades) in groupedBySubjectName) {
            if (subjectName.startsWith("Сначала добавьте") || subjectName.isBlank()) continue

            val subjectPeriodPoints = mutableListOf<Pair<String, Double>>()
            PeriodManager.periods.forEach { period ->
                val gradesInPeriod = grades.filter { it.periodId == period.id }.mapNotNull { parseGradeToDouble(it.grade) }
                if (gradesInPeriod.isNotEmpty()) {
                    subjectPeriodPoints.add(Pair(period.name, gradesInPeriod.average()))
                }
            }

            if (subjectPeriodPoints.isNotEmpty()) {
                val colorHex = subjectColors[colorIndex % subjectColors.size]
                subjectLinesList.add(ChartLineData(subjectName, Color.parseColor(colorHex), subjectPeriodPoints))
                colorIndex++
            }
        }

        if (subjectLinesList.isNotEmpty()) {
            allChartPages.add(MultiChartPageData(subjectLinesList, periodsNames))
        }

        chartPagerAdapter.updateData(allChartPages)

        chartPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvChartTitle.text = if (position == 0) {
                    "Динамика: Общая успеваемость по семестрам (Листайте ➔)"
                } else {
                    "Динамика успеваемости по учебным дисциплинам"
                }
            }
        })

        // --- 2. РАСЧЕТ И ОФОРМЛЕНИЕ ОБЩЕГО СРЕДНЕГО БАЛЛА ЗА ВСЁ ВРЕМЯ ---
        val allValidGrades = DiaryStorage.lessonGrades.mapNotNull { parseGradeToDouble(it.grade) } +
                DiaryStorage.examGrades.mapNotNull { parseGradeToDouble(it.grade) }

        if (allValidGrades.isNotEmpty()) {
            val totalAvg = allValidGrades.average()
            tvOverallGrade.text = String.format(Locale.US, "%.2f", totalAvg)
            tvOverallGrade.setTextColor(getGradientColor(totalAvg))
        } else {
            tvOverallGrade.text = "—"
            tvOverallGrade.setTextColor(Color.WHITE)
        }

        // --- 3. СБОР СТАТИСТИКИ ОЦЕНОК ---
        val gradesStrings = DiaryStorage.lessonGrades.map { it.grade.trim() } + DiaryStorage.examGrades.map { it.grade.trim() }

        fun cleanName(g: String) = g.lowercase()
        tvCount5.text = gradesStrings.count { it == "5" || cleanName(it) == "отл" || cleanName(it) == "отлично" }.toString()
        tvCount4.text = gradesStrings.count { it == "4" || cleanName(it) == "хор" || cleanName(it) == "хорошо" }.toString()
        tvCount3.text = gradesStrings.count { it == "3" || cleanName(it) == "уд" || cleanName(it) == "удовл" }.toString()
        tvCount2.text = gradesStrings.count { it == "2" || it == "1" || cleanName(it) == "неуд" }.toString()

        view.findViewById<View>(R.id.llStat5).backgroundTintList = ColorStateList.valueOf(getGradientColor(5.0))
        view.findViewById<View>(R.id.llStat4).backgroundTintList = ColorStateList.valueOf(getGradientColor(4.0))
        view.findViewById<View>(R.id.llStat3).backgroundTintList = ColorStateList.valueOf(getGradientColor(3.0))
        view.findViewById<View>(R.id.llStat2).backgroundTintList = ColorStateList.valueOf(getGradientColor(1.0))

        // --- 4. РАСПРЕДЕЛЕНИЕ ПРЕДМЕТОВ ---
        val currentPeriodLessons = DiaryStorage.lessonGrades.filter { it.periodId == PeriodManager.activePeriodId }
        val groupedBySubject = currentPeriodLessons.groupBy { it.subject }
        val subjectAverages = mutableListOf<Pair<String, Double>>()

        for ((subject, grades) in groupedBySubject) {
            val numeric = grades.mapNotNull { parseGradeToDouble(it.grade) }
            if (numeric.isNotEmpty()) subjectAverages.add(Pair(subject, numeric.average()))
        }

        llBestSubjects.removeAllViews()
        llWeakSubjects.removeAllViews()

        val bestSubjects = subjectAverages.filter { it.second >= 4.0 }.sortedByDescending { it.second }
        val weakSubjects = subjectAverages.filter { it.second < 4.0 }.sortedBy { it.second }

        renderSubjectListToLayout(llBestSubjects, bestSubjects)
        renderSubjectListToLayout(llWeakSubjects, weakSubjects)

        // --- 5. ИНИЦИАЛИЗАЦИЯ И УПРАВЛЕНИЕ АВАТАРОМ, ИМЕНЕМ ---
        val ivAvatar: ImageView = view.findViewById(R.id.ivProfileAvatar)
        val cvAvatarBtn: View = view.findViewById(R.id.cvProfileAvatarContainer)
        val tvUserName: TextView = view.findViewById(R.id.tvProfileUserName)
        val btnNameContainer: View = view.findViewById(R.id.llProfileNameContainer)
        val btnSettings: View = view.findViewById(R.id.btnProfileSettings)

        val profilePrefs = requireContext().getSharedPreferences("profile_user_prefs", android.content.Context.MODE_PRIVATE)

        val savedName = profilePrefs.getString("user_name_text", "Нажмите, чтобы ввести имя")
        tvUserName.text = savedName

        val savedAvatarPath = profilePrefs.getString("user_avatar_path", null)
        if (!savedAvatarPath.isNullOrEmpty()) {
            val file = java.io.File(savedAvatarPath)
            if (file.exists()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    ivAvatar.setImageBitmap(bitmap)
                    ivAvatar.imageTintList = null
                }
            }
        }

        cvAvatarBtn.setOnClickListener { pickAvatarLauncher.launch("image/*") }

        btnNameContainer.setOnClickListener {
            val inputEditText = EditText(requireContext()).apply {
                hint = "Введите ваше имя и фамилию"
                if (tvUserName.text.toString() != "Нажмите, чтобы ввести имя") {
                    setText(tvUserName.text.toString())
                    setSelection(text.length)
                }
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Редактировать имя")
                .setView(inputEditText)
                .setPositiveButton("Сохранить") { _, _ ->
                    val enteredName = inputEditText.text.toString().trim()
                    val finalName = if (enteredName.isNotEmpty()) enteredName else "Нажмите, чтобы ввести имя"
                    profilePrefs.edit().putString("user_name_text", finalName).apply()
                    tvUserName.text = finalName
                    Toast.makeText(requireContext(), "Имя сохранено!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        btnSettings.setOnClickListener {
            (activity as? MainActivity)?.openFragment(ParametersFragment(), R.id.navMisc)
        }

        // --- 6. ИИ-АНАЛИЗ С ИСПОЛЬЗОВАНИЕМ МОДЕЛИ ПО УМОЛЧАНИЮ ---
        val cachedAnalysis = DiaryStorage.getAiAnalysisCache(requireContext())

        if (cachedAnalysis != null) {
            tvAiAnalysis.text = cachedAnalysis
        } else {
            tvAiAnalysis.text = "Загрузка стартового ИИ-анализа..."
            loadAiAcademicAnalysis(tvAiAnalysis, isForceUpdate = false)
        }

        btnRefreshAi.setOnClickListener {
            tvAiAnalysis.text = "Перегенерация аналитики..."
            it.animate().rotationBy(360f).setDuration(500).start()
            loadAiAcademicAnalysis(tvAiAnalysis, isForceUpdate = true)
        }
    }

    // ===== МЕТОД ПОЛУЧЕНИЯ МОДЕЛИ ПО УМОЛЧАНИЮ ИЗ НАСТРОЕК =====
    private fun getDefaultModelId(): String {
        val prefs = requireContext().getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)
        val defaultModelId = prefs.getString("default_model_id", "openai/gpt-chat-latest")

        Log.d("ProfileFragment", "📱 defaultModelId из SharedPreferences: $defaultModelId")

        val activity = activity as? MainActivity
        val builtInModels = activity?.availableModels?.filter { !it.isHeader } ?: emptyList()
        val builtInIds = builtInModels.map { it.apiId }

        if (builtInIds.contains(defaultModelId)) {
            Log.d("ProfileFragment", "✅ Найдена встроенная модель: $defaultModelId")
            return defaultModelId ?: "openai/gpt-chat-latest"
        }

        val customModelsJson = prefs.getString("custom_models_list", "[]")
        val customModelsArray = JSONArray(customModelsJson)

        for (i in 0 until customModelsArray.length()) {
            val obj = customModelsArray.getJSONObject(i)
            val id = obj.getString("id")
            val apiPath = obj.getString("apiPath")

            if (id == defaultModelId) {
                Log.d("ProfileFragment", "✅ Найдена пользовательская модель: $apiPath")
                return apiPath
            }
        }

        Log.d("ProfileFragment", "⚠️ Модель не найдена, возвращаем ChatGPT")
        return "openai/gpt-chat-latest"
    }

    private fun loadAiAcademicAnalysis(tvTarget: TextView, isForceUpdate: Boolean) {
        val context = requireContext()

        if (!isForceUpdate) {
            val cached = DiaryStorage.getAiAnalysisCache(context)
            if (cached != null) {
                tvTarget.text = cached
                return
            }
        }

        val prompt = generateAcademicPrompt()

        // ===== ИСПОЛЬЗУЕМ МОДЕЛЬ ПО УМОЛЧАНИЮ =====
        val defaultModelId = getDefaultModelId()
        Log.d("ProfileFragment", "🔧 Используем модель для анализа: $defaultModelId")

        viewLifecycleOwner.lifecycleScope.launch {
            val aiResponse = com.example.speechrecognizer.data.OpenRouterClient.fetchProfileAnalysis(defaultModelId, prompt)
            if (!aiResponse.startsWith("Ошибка")) {
                DiaryStorage.saveAiAnalysisCache(context, aiResponse)
            }
            tvTarget.text = aiResponse
        }
    }

    private fun generateAcademicPrompt(): String {
        val currentPeriodName = PeriodManager.periods.firstOrNull { it.id == PeriodManager.activePeriodId }?.name ?: "Текущий семестр"
        val filteredLessons = DiaryStorage.lessonGrades.filter { it.periodId == PeriodManager.activePeriodId }
        val grouped = filteredLessons.groupBy { it.subject }

        val subjectStats = StringBuilder()
        for ((subject, grades) in grouped) {
            val cleanName = if (subject.contains("Subject(")) subject.substringAfter("name=").substringBefore(",") else subject
            val gradesList = grades.map { it.grade }.joinToString(", ")
            subjectStats.append("- $cleanName: оценки [$gradesList]\n")
        }

        val totalExams = DiaryStorage.examGrades.joinToString("; ") { "${it.subject} (${it.type})=${it.grade}" }

        return """
            Ты — ИИ-куратор студента. Сделай краткий, емкий и полезный академический анализ профиля успеваемости. 
            Условия: отвечай строго на русском языке, пиши тезисно, максимум 3-4 предложения. Дай конкретный совет, на какие предметы поднажать.
            Данные студента за период ($currentPeriodName):
            Текущие оценки по предметам за занятия:
            ${if(subjectStats.isEmpty()) "Оценок за занятия пока нет." else subjectStats.toString()}
            Итоговые экзамены и зачеты за сессию:
            ${if(totalExams.isEmpty()) "Экзаменов пока нет." else totalExams}
        """.trimIndent()
    }

    private fun renderSubjectListToLayout(layout: LinearLayout, items: List<Pair<String, Double>>) {
        if (items.isEmpty()) {
            val tvEmpty = TextView(context).apply {
                text = "Нет подходящих предметов"
                setTextColor(Color.GRAY)
                setPadding(10, 10, 10, 10)
            }
            layout.addView(tvEmpty)
            return
        }

        items.forEach { pair ->
            val itemView = layoutInflater.inflate(R.layout.item_predict_subject, layout, false)

            val cleanSubjectName = if (pair.first.contains("Subject(")) {
                pair.first.substringAfter("name=").substringBefore(",")
            } else {
                pair.first
            }

            itemView.findViewById<TextView>(R.id.tvPredictSubjectName).text = cleanSubjectName
            val tvGrade = itemView.findViewById<TextView>(R.id.tvPredictSubjectGrade)
            tvGrade.text = String.format(Locale.US, "%.2f", pair.second)
            tvGrade.backgroundTintList = ColorStateList.valueOf(getGradientColor(pair.second))

            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 4, 0, 8)
            itemView.layoutParams = params
            layout.addView(itemView)
        }
    }

    private fun parseGradeToDouble(gradeText: String): Double? {
        val trimmed = gradeText.trim().lowercase()
        val numeric = trimmed.toDoubleOrNull()
        if (numeric != null) return numeric
        return when (trimmed) {
            "отл", "отлично", "5" -> 5.0
            "хор", "хорошо", "4" -> 4.0
            "уд", "удовл", "удовлетворительно", "3" -> 3.0
            "неуд", "2" -> 2.0
            else -> null
        }
    }
}