package com.example.speechrecognizer.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.speechrecognizer.MainActivity
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.DiaryStorage
import com.example.speechrecognizer.data.PeriodManager
import com.example.speechrecognizer.data.ScheduleStorage
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ParametersFragment : Fragment(R.layout.fragment_parameters) {

    private lateinit var llSubjectsContainer: LinearLayout
    private lateinit var llModelsContainer: LinearLayout
    private lateinit var etNewSubjectName: EditText
    private lateinit var etNewSubjectTopic: EditText
    private lateinit var tabLayoutPeriods: TabLayout

    // Элементы управления формой редактирования
    private lateinit var tvSubjectFormTitle: TextView
    private lateinit var tvEditingSubjectId: TextView
    private lateinit var btnAddSubject: ImageView

    private var isSubjectsListExpanded = true
    private var isModelsExpanded = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().apply {
            findViewById<View>(R.id.fabMain)?.visibility = View.GONE
            findViewById<View>(R.id.fabAddReminder)?.visibility = View.GONE
            findViewById<View>(R.id.fabAddTimer)?.visibility = View.GONE
            findViewById<View>(R.id.notificationContainer)?.visibility = View.GONE
        }

        PeriodManager.init(requireContext())

        val btnBack: View = view.findViewById(R.id.btnBackParameters)
        val btnToggleMode: View = view.findViewById(R.id.btnToggleScheduleType)
        val tvModeHint: TextView = view.findViewById(R.id.tvCurrentScheduleTypeHint)
        val switchMode: androidx.appcompat.widget.SwitchCompat = view.findViewById(R.id.switchScheduleMode)

        val btnClearSchedule: View = view.findViewById(R.id.btnClearAllSchedule)
        val btnClearGrades: View = view.findViewById(R.id.btnClearAllGrades)

        val btnCreatePeriod: View = view.findViewById(R.id.btnCreateNewPeriod)
        val btnDeletePeriod: View = view.findViewById(R.id.btnDeleteCurrentPeriod)
        tabLayoutPeriods = view.findViewById(R.id.tabLayoutPeriods)

        // Инициализация формы ввода предметов
        etNewSubjectName = view.findViewById(R.id.etNewSubjectName)
        etNewSubjectTopic = view.findViewById(R.id.etNewSubjectTopic)
        tvSubjectFormTitle = view.findViewById(R.id.tvSubjectFormTitle)
        tvEditingSubjectId = view.findViewById(R.id.tvEditingSubjectId)
        btnAddSubject = view.findViewById(R.id.btnAddSubject)
        llSubjectsContainer = view.findViewById(R.id.llSubjectsContainer)

        // Инициализация нейросетевых элементов
        llModelsContainer = view.findViewById(R.id.llModelsContainer)
        val btnAddModel: View = view.findViewById(R.id.btnAddAiModel)
        val tvInstructionLink: TextView = view.findViewById(R.id.tvInstructionLink)
        val btnToggleModelsExpand: View = view.findViewById(R.id.btnToggleModelsExpand)
        val ivModelsArrow: ImageView = view.findViewById(R.id.ivModelsExpandArrow)

        // Инициализация сворачивания предметов
        val btnToggleExpand: View = view.findViewById(R.id.btnToggleSubjectsExpand)
        val ivExpandArrow: ImageView = view.findViewById(R.id.ivSubjectsExpandArrow)
        val llRootContainer: ViewGroup = view.findViewById(R.id.llParametersRootContainer)

        switchMode.isChecked = ScheduleStorage.isDateBasedMode
        updateTextHint(tvModeHint)

        setupPeriodTabs()

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        btnToggleMode.setOnClickListener {
            val isDateMode = ScheduleStorage.toggleScheduleMode(requireContext())
            switchMode.isChecked = isDateMode
            updateTextHint(tvModeHint)
        }

        // Анимация сворачивания/разворачивания списка предметов
        btnToggleExpand.setOnClickListener {
            android.transition.TransitionManager.beginDelayedTransition(llRootContainer, android.transition.AutoTransition())
            if (isSubjectsListExpanded) {
                llSubjectsContainer.visibility = View.GONE
                ivExpandArrow.rotation = -90f
            } else {
                llSubjectsContainer.visibility = View.VISIBLE
                ivExpandArrow.rotation = 0f
            }
            isSubjectsListExpanded = !isSubjectsListExpanded
        }

        // Анимация сворачивания/разворачивания списка нейросетей
        btnToggleModelsExpand.setOnClickListener {
            android.transition.TransitionManager.beginDelayedTransition(llRootContainer, android.transition.AutoTransition())
            if (isModelsExpanded) {
                llModelsContainer.visibility = View.GONE
                ivModelsArrow.rotation = -90f
            } else {
                llModelsContainer.visibility = View.VISIBLE
                ivModelsArrow.rotation = 0f
            }
            isModelsExpanded = !isModelsExpanded
        }

        // Кнопка сохранения формы предмета
        btnAddSubject.setOnClickListener {
            val name = etNewSubjectName.text.toString().trim()
            val topic = etNewSubjectTopic.text.toString().trim()
            val editingId = tvEditingSubjectId.text.toString()

            if (name.isEmpty()) return@setOnClickListener

            if (editingId.isNotEmpty()) {
                val subjectToUpdate = ScheduleStorage.subjectsListAll.firstOrNull { it.id == editingId }
                if (subjectToUpdate != null) {
                    val updatedSubject = subjectToUpdate.copy(name = name, topic = topic)
                    val globalIndex = ScheduleStorage.subjectsListAll.indexOf(subjectToUpdate)
                    if (globalIndex != -1) {
                        ScheduleStorage.subjectsListAll[globalIndex] = updatedSubject
                        ScheduleStorage.save(requireContext())
                        Toast.makeText(requireContext(), "Предмет изменен!", Toast.LENGTH_SHORT).show()
                    }
                }
                resetSubjectForm()
            } else {
                if (ScheduleStorage.subjectsList.any { it.name.lowercase() == name.lowercase() }) {
                    Toast.makeText(requireContext(), "Предмет с таким именем уже есть!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                ScheduleStorage.addSubject(requireContext(), name, topic)
                Toast.makeText(requireContext(), "Предмет добавлен!", Toast.LENGTH_SHORT).show()
            }

            etNewSubjectName.text.clear()
            etNewSubjectTopic.text.clear()
            renderSubjectsList()
        }

        btnCreatePeriod.setOnClickListener {
            val input = EditText(requireContext()).apply { hint = "Например: 3 Четверть или 2 Семестр" }
            AlertDialog.Builder(requireContext())
                .setTitle("Создать учебный период")
                .setView(input)
                .setPositiveButton("Создать") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        PeriodManager.addPeriod(requireContext(), name)
                        setupPeriodTabs()
                        Toast.makeText(requireContext(), "Период создан!", Toast.LENGTH_SHORT).show()
                    }
                }.setNegativeButton("Отмена", null).show()
        }

        btnDeletePeriod.setOnClickListener {
            if (PeriodManager.periods.size <= 1) {
                Toast.makeText(requireContext(), "Нельзя удалить единственный период!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentPeriodName = PeriodManager.periods.firstOrNull { it.id == PeriodManager.activePeriodId }?.name ?: ""
            AlertDialog.Builder(requireContext())
                .setTitle("Удалить период")
                .setMessage("Вы действительно хотите удалить '$currentPeriodName'? Все связанные данные периода сотрутся.")
                .setPositiveButton("Удалить") { _, _ ->
                    PeriodManager.removePeriod(requireContext(), PeriodManager.activePeriodId)
                    setupPeriodTabs()
                    Toast.makeText(requireContext(), "Период удален", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Отмена", null).show()
        }

        btnClearSchedule.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Очистить расписание периода")
                .setMessage("Удалить все пары и экзамены за текущий период?")
                .setPositiveButton("Удалить") { _, _ ->
                    ScheduleStorage.clearCurrentPeriodSchedule(requireContext())
                    Toast.makeText(requireContext(), "Расписание периода очищено", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Отмена", null).show()
        }

        btnClearGrades.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Очистить оценки периода")
                .setMessage("Удалить все оценки дневника за текущий период?")
                .setPositiveButton("Удалить") { _, _ ->
                    DiaryStorage.clearCurrentPeriodGrades(requireContext())
                    Toast.makeText(requireContext(), "Оценки периода удалены", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Отмена", null).show()
        }

        // --- ЛОГИКА УПРАВЛЕНИЯ НЕЙРОСЕТЯМИ ---
        btnAddModel.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_add_ai_model, null)
            val etName = dialogView.findViewById<EditText>(R.id.etModelName)
            val etPath = dialogView.findViewById<EditText>(R.id.etModelPath)
            AlertDialog.Builder(requireContext())
                .setTitle("Добавить нейросеть")
                .setView(dialogView)
                .setPositiveButton("Добавить") { _, _ ->
                    val name = etName.text.toString().trim()
                    val path = etPath.text.toString().trim()
                    if (name.isNotEmpty() && path.isNotEmpty()) {
                        val prefs = requireContext().getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)
                        val modelsJson = prefs.getString("custom_models_list", "[]")
                        val modelsArray = JSONArray(modelsJson)
                        val newId = UUID.randomUUID().toString()
                        val newModel = JSONObject().apply {
                            put("id", newId)
                            put("name", name)
                            put("apiPath", path)
                        }
                        modelsArray.put(newModel)
                        prefs.edit().putString("custom_models_list", modelsArray.toString()).apply()

                        Log.d("ParametersFragment", "✅ Модель добавлена: id=$newId, name=$name, apiPath=$path")

                        renderModelsList()
                        Toast.makeText(requireContext(), "Модель добавлена", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Заполните оба поля", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        tvInstructionLink.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://docs.google.com/document/d/1hS9Gi_k7ixlnKD9OCOp6LutHecuq5XDw5Ua929XHdUU/edit?usp=sharing"))
            startActivity(intent)
        }

        renderModelsList()
    }

    private fun getVisibleBuiltInModels(): List<MainActivity.AIModel> {
        val prefs = requireContext().getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)
        val hiddenModelsJson = prefs.getString("hidden_builtin_models", "[]")
        val hiddenArray = JSONArray(hiddenModelsJson)
        val hiddenIds = mutableListOf<String>()

        for (i in 0 until hiddenArray.length()) {
            hiddenIds.add(hiddenArray.getString(i))
        }

        val allBuiltIn = (activity as? MainActivity)?.availableModels?.filter { !it.isHeader } ?: emptyList()
        return allBuiltIn.filter { it.apiId !in hiddenIds }
    }

    private fun renderModelsList() {
        if (!::llModelsContainer.isInitialized) return
        llModelsContainer.removeAllViews()

        val context = requireContext()
        val prefs = context.getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)

        // Получаем пользовательские модели
        val customModelsJson = prefs.getString("custom_models_list", "[]")
        val customModelsArray = JSONArray(customModelsJson)

        // Получаем ВИДИМЫЕ встроенные модели (без скрытых)
        val visibleBuiltInModels = getVisibleBuiltInModels()

        // Получаем ID модели по умолчанию
        val defaultModelId = prefs.getString("default_model_id", "openai/gpt-chat-latest")

        Log.d("ParametersFragment", "📱 visibleBuiltInModels.size = ${visibleBuiltInModels.size}")
        Log.d("ParametersFragment", "📱 customModelsArray.length() = ${customModelsArray.length()}")
        Log.d("ParametersFragment", "📱 defaultModelId = $defaultModelId")

        // Если нет НИКАКИХ моделей
        if (visibleBuiltInModels.isEmpty() && customModelsArray.length() == 0) {
            val tvEmpty = TextView(context).apply {
                text = "Нет доступных нейросетей.\nНажмите ➕ чтобы добавить."
                setTextColor(Color.GRAY)
                setPadding(20, 20, 20, 20)
                gravity = Gravity.CENTER
            }
            llModelsContainer.addView(tvEmpty)
            return
        }

        // Отображаем заголовок "ВСТРОЕННЫЕ МОДЕЛИ"
        if (visibleBuiltInModels.isNotEmpty()) {
            val headerBuiltIn = TextView(context).apply {
                text = "⭐ ВСТРОЕННЫЕ МОДЕЛИ"
                textSize = 12f
                setTextColor(Color.parseColor("#7C4DFF"))
                gravity = Gravity.LEFT
                setPadding(12, 16, 12, 8)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            llModelsContainer.addView(headerBuiltIn)

            visibleBuiltInModels.forEach { model ->
                addModelRowToContainer(
                    id = model.apiId,
                    name = model.name,
                    apiPath = model.apiId,
                    isDefault = (model.apiId == defaultModelId),
                    isCustom = false
                )
            }
        }

        // Отображаем заголовок "ПОЛЬЗОВАТЕЛЬСКИЕ МОДЕЛИ"
        if (customModelsArray.length() > 0) {
            val headerCustom = TextView(context).apply {
                text = "📱 ПОЛЬЗОВАТЕЛЬСКИЕ МОДЕЛИ"
                textSize = 12f
                setTextColor(Color.parseColor("#80FFFFFF"))
                gravity = Gravity.LEFT
                setPadding(12, 24, 12, 8)
            }
            llModelsContainer.addView(headerCustom)

            for (i in 0 until customModelsArray.length()) {
                val modelObj = customModelsArray.getJSONObject(i)
                val id = modelObj.getString("id")
                val name = modelObj.getString("name")
                val apiPath = modelObj.getString("apiPath")

                Log.d("ParametersFragment", "📱 Отображаем пользовательскую модель: id=$id, name=$name, apiPath=$apiPath")

                addModelRowToContainer(
                    id = id,
                    name = name,
                    apiPath = apiPath,
                    isDefault = (id == defaultModelId),
                    isCustom = true
                )
            }
        }

        // Кнопка "Восстановить все скрытые модели" (если есть скрытые)
        val hiddenJson = prefs.getString("hidden_builtin_models", "[]")
        val hiddenArray = JSONArray(hiddenJson)

        if (hiddenArray.length() > 0) {
            val btnRestore = TextView(context).apply {
                text = "↺ Восстановить все скрытые встроенные модели"
                textSize = 13f
                setTextColor(Color.parseColor("#7C4DFF"))
                gravity = Gravity.CENTER
                setPadding(12, 16, 12, 16)
                setOnClickListener {
                    prefs.edit().remove("hidden_builtin_models").apply()
                    renderModelsList()
                    Toast.makeText(context, "Все скрытые модели восстановлены", Toast.LENGTH_SHORT).show()
                }
            }
            llModelsContainer.addView(btnRestore)
        }
    }

    private fun addModelRowToContainer(id: String, name: String, apiPath: String, isDefault: Boolean, isCustom: Boolean) {
        val context = requireContext()
        val prefs = context.getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 12, 16)
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_input_rounded)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#252041"))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 4, 0, 8)
            layoutParams = params
        }

        // Контейнер с текстом
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvName = TextView(context).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvPath = TextView(context).apply {
            text = apiPath
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 11f
            setPadding(0, 4, 0, 0)
        }

        textContainer.addView(tvName)
        textContainer.addView(tvPath)

        // Кнопка "По умолчанию" (звездочка)
        val btnDefault = ImageView(context).apply {
            setImageResource(if (isDefault) android.R.drawable.star_on else android.R.drawable.star_off)
            val size = (28 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(4, 0, 8, 0)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            setOnClickListener {
                val modelIdToSave = if (isCustom) id else apiPath
                prefs.edit().putString("default_model_id", modelIdToSave).apply()
                Log.d("ParametersFragment", "⭐ Модель по умолчанию установлена: $name -> $modelIdToSave")
                Toast.makeText(context, "Модель '$name' теперь по умолчанию", Toast.LENGTH_SHORT).show()
                renderModelsList()
                (activity as? MainActivity)?.updateDefaultModel()
            }
        }

        row.addView(textContainer)
        row.addView(btnDefault)

        // Кнопка удаления (ДЛЯ ВСЕХ МОДЕЛЕЙ, кроме последней встроенной)
        val canDelete = if (isCustom) {
            true // Пользовательские можно удалять всегда
        } else {
            // Встроенные модели: проверяем, чтобы после удаления осталась хотя бы одна видимая
            val visibleBuiltInModels = getVisibleBuiltInModels()
            visibleBuiltInModels.size > 1
        }

        if (canDelete) {
            val btnDelete = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setPadding(12, 12, 12, 12)

                setOnClickListener {
                    if (isCustom) {
                        // Удаляем пользовательскую модель
                        val currentJson = prefs.getString("custom_models_list", "[]")
                        val array = JSONArray(currentJson)
                        val newArray = JSONArray()

                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (obj.getString("id") != id) {
                                newArray.put(obj)
                            }
                        }

                        prefs.edit().putString("custom_models_list", newArray.toString()).apply()
                        Log.d("ParametersFragment", "🗑 Пользовательская модель удалена: $name")

                        if (isDefault) {
                            // Если удалили модель по умолчанию, ставим первую доступную видимую
                            val firstVisible = getVisibleBuiltInModels().firstOrNull()
                            prefs.edit().putString("default_model_id", firstVisible?.apiId ?: "openai/gpt-chat-latest").apply()
                            Log.d("ParametersFragment", "⭐ Новая модель по умолчанию: ${firstVisible?.name}")
                        }

                        renderModelsList()
                        Toast.makeText(context, "Модель '$name' удалена", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.updateDefaultModel()

                    } else {
                        // Скрываем встроенную модель (добавляем в черный список)
                        val hiddenModelsJson = prefs.getString("hidden_builtin_models", "[]")
                        val hiddenArray = JSONArray(hiddenModelsJson)

                        // Добавляем ID модели в черный список, если его там нет
                        var alreadyHidden = false
                        for (i in 0 until hiddenArray.length()) {
                            if (hiddenArray.getString(i) == id) {
                                alreadyHidden = true
                                break
                            }
                        }

                        if (!alreadyHidden) {
                            hiddenArray.put(id)
                            prefs.edit().putString("hidden_builtin_models", hiddenArray.toString()).apply()
                            Log.d("ParametersFragment", "🗑 Встроенная модель скрыта: $name")
                        }

                        if (isDefault) {
                            // Если скрыли модель по умолчанию, ставим первую видимую
                            val firstVisible = getVisibleBuiltInModels().firstOrNull()
                            prefs.edit().putString("default_model_id", firstVisible?.apiId ?: "openai/gpt-chat-latest").apply()
                            Log.d("ParametersFragment", "⭐ Новая модель по умолчанию: ${firstVisible?.name}")
                        }

                        renderModelsList()
                        Toast.makeText(context, "Модель '$name' скрыта из списка", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.updateDefaultModel()
                    }
                }
            }
            row.addView(btnDelete)
        } else {
            // Если удалить нельзя (последняя встроенная модель), показываем текст
            val infoText = TextView(context).apply {
                text = "Нельзя удалить"
                setTextColor(Color.parseColor("#505050"))
                textSize = 10f
                setPadding(8, 0, 0, 0)
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(infoText)
        }

        llModelsContainer.addView(row)
    }

    private fun setupPeriodTabs() {
        tabLayoutPeriods.removeAllTabs()
        PeriodManager.periods.forEach { period ->
            val tab = tabLayoutPeriods.newTab().apply { text = period.name }
            tabLayoutPeriods.addTab(tab)
        }

        val activeIndex = PeriodManager.periods.indexOfFirst { it.id == PeriodManager.activePeriodId }
        if (activeIndex != -1) tabLayoutPeriods.getTabAt(activeIndex)?.select()

        resetSubjectForm()
        renderSubjectsList()

        tabLayoutPeriods.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val position = it.position
                    if (position in PeriodManager.periods.indices) {
                        PeriodManager.activePeriodId = PeriodManager.periods[position].id
                        PeriodManager.save(requireContext())
                        resetSubjectForm()
                        renderSubjectsList()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun resetSubjectForm() {
        tvSubjectFormTitle.text = "Добавить предмет в текущий период:"
        tvEditingSubjectId.text = ""
        btnAddSubject.setImageResource(android.R.drawable.ic_input_add)
        btnAddSubject.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))
    }

    private fun renderSubjectsList() {
        if (!::llSubjectsContainer.isInitialized) return
        llSubjectsContainer.removeAllViews()
        val context = requireContext()
        val currentSubjects = ScheduleStorage.subjectsList

        if (currentSubjects.isEmpty()) {
            val tvEmpty = TextView(context)
            tvEmpty.text = "В этом периоде предметов еще нет"
            tvEmpty.setTextColor(Color.GRAY)
            tvEmpty.setPadding(20, 20, 20, 20)
            llSubjectsContainer.addView(tvEmpty)
            return
        }

        currentSubjects.forEach { subjectObj ->
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(20, 16, 12, 16)
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = ContextCompat.getDrawable(context, R.drawable.bg_input_rounded)
            row.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#252041"))

            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowParams.setMargins(0, 4, 0, 8)
            row.layoutParams = rowParams

            val textContainer = LinearLayout(context)
            textContainer.orientation = LinearLayout.VERTICAL
            textContainer.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val tvName = TextView(context)
            tvName.text = subjectObj.name
            tvName.setTextColor(Color.WHITE)
            tvName.textSize = 16f
            tvName.setTypeface(null, android.graphics.Typeface.BOLD)
            textContainer.addView(tvName)

            val tvTopic = TextView(context)
            tvTopic.text = if (subjectObj.topic.isNotEmpty()) "Тема: ${subjectObj.topic}" else "Тема не указана"
            tvTopic.setTextColor(Color.parseColor("#80FFFFFF"))
            tvTopic.textSize = 13f
            tvTopic.setPadding(0, 4, 0, 0)
            textContainer.addView(tvTopic)

            val btnEdit = ImageView(context)
            btnEdit.setImageResource(android.R.drawable.ic_menu_edit)
            val density = context.resources.displayMetrics.density
            val sizeInDp = (24 * density).toInt()
            val btnParams = LinearLayout.LayoutParams(sizeInDp, sizeInDp)
            btnParams.setMargins(4, 0, 4, 0)
            btnEdit.layoutParams = btnParams
            btnEdit.scaleType = ImageView.ScaleType.CENTER_INSIDE
            btnEdit.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)

            btnEdit.setOnClickListener {
                val inputEditText = EditText(context).apply {
                    hint = "Введите новую тему"
                    setText(subjectObj.topic)
                    setSelection(text.length)
                }

                AlertDialog.Builder(context)
                    .setTitle("Редактировать тему")
                    .setMessage("Предмет: ${subjectObj.name}")
                    .setView(inputEditText)
                    .setPositiveButton("Сохранить") { _, _ ->
                        val newTopic = inputEditText.text.toString().trim()
                        val globalIndex = ScheduleStorage.subjectsListAll.indexOfFirst { it.id == subjectObj.id }
                        if (globalIndex != -1) {
                            ScheduleStorage.subjectsListAll[globalIndex] = subjectObj.copy(topic = newTopic)
                            ScheduleStorage.save(context)
                            renderSubjectsList()
                            Toast.makeText(context, "Тема обновлена!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }

            val btnDelete = ImageView(context)
            btnDelete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            btnDelete.setPadding(12, 12, 12, 12)
            btnDelete.setOnClickListener {
                ScheduleStorage.removeSubject(context, subjectObj.id)
                renderSubjectsList()
            }

            row.addView(textContainer)
            row.addView(btnEdit)
            row.addView(btnDelete)
            llSubjectsContainer.addView(row)
        }
    }

    private fun updateTextHint(textView: TextView) {
        textView.text = if (ScheduleStorage.isDateBasedMode) "По точным датам (календарь)" else "По номерам недель (цифры)"
    }

    override fun onDestroyView() {
        requireActivity().apply {
            findViewById<View>(R.id.fabMain)?.visibility = View.VISIBLE
            findViewById<View>(R.id.notificationContainer)?.visibility = View.VISIBLE
            findViewById<View>(R.id.btnSpeak)?.apply { visibility = View.VISIBLE; bringToFront() }
        }
        super.onDestroyView()
    }
}