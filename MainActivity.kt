package com.example.speechrecognizer

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.speechrecognizer.data.AlarmRepository
import com.example.speechrecognizer.ui.ChatFragment
import com.example.speechrecognizer.ui.ChatListFragment
import com.example.speechrecognizer.ui.HomeFragment
import com.example.speechrecognizer.ui.ProfileFragment
import com.example.speechrecognizer.ui.SettingsFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray


class MainActivity : AppCompatActivity() {

    data class AIModel(
        val name: String,
        val apiId: String,
        val isHeader: Boolean = false
    )

    private lateinit var repository: AlarmRepository

    val availableModels = listOf(
        AIModel("Текстовые модели:", "", true), // Заголовок
        AIModel("baidu", "baidu/cobuddy:free"),
        AIModel("DeepSeek v4", "deepseek/deepseek-v4-flash"),
        AIModel("z-ai", "z-ai/glm-4.5-air:free"),
        AIModel("ChatGPT", "openai/gpt-chat-latest"),
        AIModel("Minimax", "minimax/minimax-m2.5:free"),
        AIModel("inclusionai", "inclusionai/ring-2.6-1t"),
    )

    private var selectedAiModel = availableModels[1] // По умолчанию baidu

    private lateinit var inputLayout: ConstraintLayout

    data class NavIconSet(
        val imageView: ImageView,
        val activeResId: Int,
        val inactiveResId: Int
    )

    private lateinit var btnChatMenu: ImageView
    private lateinit var chatActionContainer: View
    private lateinit var btnSelectModel: View
    private val navConfig = mutableMapOf<Int, NavIconSet>()
    private lateinit var notificationContainer: View
    private lateinit var tvNotificationBadge: TextView

    // ===== МЕТОД ДЛЯ ПОЛУЧЕНИЯ СКРЫТЫХ МОДЕЛЕЙ =====
    private fun getHiddenBuiltInModels(): List<String> {
        val prefs = getSharedPreferences("ai_models_prefs", MODE_PRIVATE)
        val hiddenModelsJson = prefs.getString("hidden_builtin_models", "[]")
        val hiddenArray = JSONArray(hiddenModelsJson)
        val hiddenIds = mutableListOf<String>()
        for (i in 0 until hiddenArray.length()) {
            hiddenIds.add(hiddenArray.getString(i))
        }
        return hiddenIds
    }

    // ===== МЕТОД ДЛЯ ПОЛУЧЕНИЯ ВИДИМЫХ ВСТРОЕННЫХ МОДЕЛЕЙ =====
    fun getVisibleBuiltInModels(): List<AIModel> {
        val hiddenIds = getHiddenBuiltInModels()
        return availableModels.filter { !it.isHeader && it.apiId !in hiddenIds }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ДОБАВЛЕНО: Создаем канал уведомлений
        NotificationHelper.createNotificationChannel(this)

        // Инициализация персистентного кэша хранилищ на старте приложения
        com.example.speechrecognizer.data.PeriodManager.init(applicationContext)
        com.example.speechrecognizer.data.ScheduleStorage.init(applicationContext)
        com.example.speechrecognizer.data.DiaryStorage.init(applicationContext)

        repository = AlarmRepository(this)

        // 1. Инициализация всех View компонентов
        inputLayout = findViewById(R.id.inputLayout)
        notificationContainer = findViewById(R.id.notificationContainer)
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge)
        val navHome: LinearLayout = findViewById(R.id.navHome)
        val navChats: LinearLayout = findViewById(R.id.navChats)
        val navMisc: LinearLayout = findViewById(R.id.navMisc)
        val navProfile: LinearLayout = findViewById(R.id.navProfile)
        btnChatMenu = findViewById(R.id.btnChatMenu)
        chatActionContainer = findViewById(R.id.chatActionContainer)
        btnSelectModel = findViewById(R.id.btnSelectModel)

        // 2. Настройка конфигурации переключения иконок нижнего меню (активная/неактивная)
        navConfig[R.id.navHome] = NavIconSet(
            navHome.getChildAt(0) as ImageView,
            R.drawable.ic_home_active,
            R.drawable.ic_home_inactive
        )
        navConfig[R.id.navChats] = NavIconSet(
            navChats.getChildAt(0) as ImageView,
            R.drawable.ic_chat_active,
            R.drawable.ic_chat_inactive
        )
        navConfig[R.id.navMisc] = NavIconSet(
            navMisc.getChildAt(0) as ImageView,
            R.drawable.ic_settings_active,
            R.drawable.ic_settings_inactive
        )
        navConfig[R.id.navProfile] = NavIconSet(
            navProfile.getChildAt(0) as ImageView,
            R.drawable.ic_profile_active,
            R.drawable.ic_profile_inactive
        )

        // 3. Динамический расчет высоты отступов под системную клавиатуру
        ViewCompat.setOnApplyWindowInsetsListener(inputLayout) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomMargin = if (imeInsets.bottom > 0) imeInsets.bottom - systemBars.bottom - 120 else 60
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = bottomMargin
            view.layoutParams = params
            insets
        }

        // --- 4. ЕДИНЫЕ СЛУШАТЕЛИ КЛИКОВ ---

        // Клик по иконке Домой открывает HomeFragment с RSS-лентой новостей
        navHome.setOnClickListener {
            openFragment(HomeFragment(), R.id.navHome)
        }

        // Клик по иконке Профиля открывает аналитику
        navProfile.setOnClickListener {
            openFragment(ProfileFragment(), R.id.navProfile)
        }

        // Клик по шестеренке открывает системные настройки
        navMisc.setOnClickListener {
            openFragment(SettingsFragment(), R.id.navMisc)
        }

        // Кнопка выбора активной текстовой ИИ-модели в шапке
        (btnSelectModel as TextView).text = selectedAiModel.name
        btnSelectModel.setOnClickListener { showAiSelectionMenu() }

        // Клик по иконке Чатов открывает историю переписок
        navChats.setOnClickListener {
            val allThreads = repository.getAllChatThreads()
            if (allThreads.isEmpty()) {
                val newChat = repository.createNewChat("Новый чат")
                openFragment(ChatFragment.newInstance(newChat.id), R.id.navChats)
            } else {
                openFragment(ChatListFragment(), R.id.navChats)
            }
        }

        // Логика перехвата удержания Floating микрофона для быстрого старта ИИ-ассистента
        val btnSpeak: FloatingActionButton = findViewById(R.id.btnSpeak)
        val clickInterceptor: View = findViewById(R.id.clickInterceptor)

        clickInterceptor.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Создаем чат мгновенно при фиксации долгого тапа
                val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val newChat = repository.createNewChat("Чат $timeLabel")

                // Инжектируем флаг авто-старта прослушивания SpeechRecognizer
                val chatFrag = ChatFragment.newInstance(newChat.id).apply {
                    arguments = (arguments ?: Bundle()).apply {
                        putBoolean("START_LISTENING", true)
                    }
                }

                // Открываем фрагмент чата
                openFragment(chatFrag, R.id.navChats)

                // Срабатывание тактильного отклика смартфона
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                true
            } else {
                false
            }
        }

        // ===== ЗАГРУЗКА МОДЕЛИ ПО УМОЛЧАНИЮ ИЗ НАСТРОЕК =====
        updateDefaultModel()

        // --- 5. ЗАПУСК ПЕРВОГО ЭКРАНА ---
        if (savedInstanceState == null) {
            // Проверяем, открыто ли из уведомления
            val openChatId = intent?.getStringExtra("OPEN_CHAT_ID")
            if (openChatId != null) {
                // Открываем чат из уведомления
                openFragment(ChatFragment.newInstance(openChatId), R.id.navChats)
                // Снимаем флаг непрочитанных
                repository.markChatAsRead(openChatId)
            } else {
                // Ищем последний активный чат
                val lastChat = repository.getLastActiveChat()
                if (lastChat != null) {
                    // Открываем последний чат
                    openFragment(ChatFragment.newInstance(lastChat.id), R.id.navChats)
                } else {
                    // Если чатов нет, открываем домашний экран
                    openFragment(HomeFragment(), R.id.navHome)
                }
            }
        }

        // ДОБАВЛЕНО: Обновляем счетчик уведомлений
        updateUnreadBadge()
    }

    // ДОБАВЛЕНО: Обработка нового Intent (если приложение уже запущено)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val openChatId = intent.getStringExtra("OPEN_CHAT_ID")
        if (openChatId != null) {
            // Снимаем флаг непрочитанных
            repository.markChatAsRead(openChatId)

            // Открываем чат
            openFragment(ChatFragment.newInstance(openChatId), R.id.navChats)
        }
    }

    // ДОБАВЛЕНО: Обновление счетчика при возвращении в приложение
    override fun onResume() {
        super.onResume()
        updateUnreadBadge()
    }

    // ДОБАВЛЕНО: Метод обновления счетчика непрочитанных
    private fun updateUnreadBadge() {
        val unreadCount = repository.getAllChatThreads().count { it.hasUnread }
        setNotificationCount(unreadCount)
    }

    fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.layout_confirm_dialog, null)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            attributes.windowAnimations = android.R.style.Animation_Dialog
        }

        view.findViewById<TextView>(R.id.tvConfirmTitle).text = title
        view.findViewById<TextView>(R.id.tvConfirmMessage).text = message

        view.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAiSelectionMenu() {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.layout_model_picker, null)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        val density = resources.displayMetrics.density

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setGravity(android.view.Gravity.TOP or android.view.Gravity.START)

            val params = attributes
            val density = resources.displayMetrics.density

            // Ширина меню (180dp)
            val menuWidthPx = (180 * density).toInt()
            params.width = menuWidthPx

            val location = IntArray(2)
            btnSelectModel.getLocationOnScreen(location)

            // Позиция X
            params.x = location[0] - (30 * density).toInt()

            // Позиция Y
            params.y = location[1] + btnSelectModel.height - (30 * density).toInt()

            // Легкое затемнение фона
            params.dimAmount = 0.2f

            attributes = params
        }

        val listContainer = view.findViewById<LinearLayout>(R.id.modelListContainer)
        listContainer.removeAllViews()

        // Добавляем заголовок
        val header = TextView(this).apply {
            text = "Текстовые:"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            gravity = android.view.Gravity.LEFT
            setPadding(0, 0, 0, 16)
            isAllCaps = true
        }
        listContainer.addView(header)

        // Получаем ВСЕ доступные модели (ТОЛЬКО ВИДИМЫЕ встроенные + пользовательские)
        val allModels = getAllAvailableModels()

        allModels.forEachIndexed { index, model ->
            val btn = layoutInflater.inflate(R.layout.item_model_button, listContainer, false) as androidx.appcompat.widget.AppCompatButton
            btn.text = model.name

            // Подсветка активной модели
            if (model.apiId == selectedAiModel.apiId) {
                btn.setTextColor(android.graphics.Color.parseColor("#7C4DFF"))
                btn.setTypeface(null, android.graphics.Typeface.BOLD)
            }

            btn.setOnClickListener {
                updateAiModel(model)
                dialog.dismiss()
            }
            listContainer.addView(btn)

            // Разделитель между моделями
            if (index < allModels.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(android.graphics.Color.parseColor("#2A244D"))
                }
                listContainer.addView(divider)
            }
        }

        dialog.show()
    }

    private fun updateAiModel(model: AIModel) {
        selectedAiModel = model
        (btnSelectModel as TextView).text = model.name
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is ChatFragment) {
            fragment.updateSelectedModel(model.apiId)
        }
    }

    fun openFragment(fragment: Fragment, selectedNavId: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        updateNavSelection(selectedNavId)

        // Определяем, находимся ли мы в режиме ЧАТА
        val isChatting = fragment is ChatFragment

        // Управляем видимостью панели ввода и меню чата
        inputLayout.isVisible = isChatting
        chatActionContainer.isVisible = isChatting

        findViewById<View>(R.id.clickInterceptor).isVisible = !isChatting

        // Управляем кнопкой НАЗАД (видна ТОЛЬКО в чате)
        val btnBack = findViewById<View>(R.id.btnBack)
        val btnBackText = findViewById<View>(R.id.btnBackText)
        btnBack.isVisible = isChatting
        btnBackText.isVisible = isChatting

        // Управляем уведомлениями (только в настройках)
        notificationContainer.isVisible = fragment is SettingsFragment

        // Настройка меню чата (три точки)
        if (isChatting) {
            btnChatMenu.setOnClickListener {
                if (fragment.isAdded) {
                    (fragment as ChatFragment).showChatMenu()
                }
            }
        }

        android.util.Log.d("NAV_DEBUG", "Текущий фрагмент: ${fragment::class.java.simpleName}, Видимость чата: $isChatting")

        updateNavSelection(selectedNavId)

        // ДОБАВЛЕНО: Обновляем счетчик непрочитанных
        updateUnreadBadge()
    }

    private fun updateNavSelection(selectedNavId: Int) {
        for ((id, iconSet) in navConfig) {
            if (id == selectedNavId) {
                iconSet.imageView.setImageResource(iconSet.activeResId)
            } else {
                iconSet.imageView.setImageResource(iconSet.inactiveResId)
            }
        }
    }

    fun setNotificationCount(count: Int) {
        if (count > 0) {
            tvNotificationBadge.isVisible = true
            tvNotificationBadge.text = if (count > 9) "9+" else count.toString()
        } else {
            tvNotificationBadge.isVisible = false
        }
    }

    fun updateDefaultModel() {
        val prefs = getSharedPreferences("ai_models_prefs", MODE_PRIVATE)
        val defaultModelId = prefs.getString("default_model_id", "openai/gpt-chat-latest")

        android.util.Log.d("MainActivity", "🔄 updateDefaultModel: defaultModelId = $defaultModelId")

        // Поиск ТОЛЬКО среди ВИДИМЫХ встроенных моделей
        val visibleBuiltInModels = getVisibleBuiltInModels()
        var foundModel = visibleBuiltInModels.firstOrNull { it.apiId == defaultModelId }

        // Если не нашли, ищем в пользовательских
        if (foundModel == null) {
            val customModelsJson = prefs.getString("custom_models_list", "[]")
            android.util.Log.d("MainActivity", "📱 Пользовательские модели JSON: $customModelsJson")
            val customModelsArray = org.json.JSONArray(customModelsJson)
            for (i in 0 until customModelsArray.length()) {
                val obj = customModelsArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val apiPath = obj.getString("apiPath")
                android.util.Log.d("MainActivity", "📱 Проверяем модель: id=$id, name=$name, apiPath=$apiPath")

                if (id == defaultModelId) {
                    foundModel = AIModel(name, apiPath)
                    android.util.Log.d("MainActivity", "✅ Найдена пользовательская модель: $name -> $apiPath")
                    break
                }
            }
        }

        if (foundModel != null) {
            selectedAiModel = foundModel
            (btnSelectModel as TextView).text = foundModel.name
            android.util.Log.d("MainActivity", "✅ Модель по умолчанию установлена: ${foundModel.name} (${foundModel.apiId})")

            // Обновляем открытый фрагмент чата
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (fragment is ChatFragment) {
                fragment.updateSelectedModel(foundModel.apiId)
                android.util.Log.d("MainActivity", "✅ Модель обновлена в ChatFragment")
            }
        } else {
            // Если модель по умолчанию не найдена (возможно скрыта), выбираем первую видимую
            if (visibleBuiltInModels.isNotEmpty()) {
                val fallbackModel = visibleBuiltInModels.first()
                selectedAiModel = fallbackModel
                (btnSelectModel as TextView).text = fallbackModel.name
                android.util.Log.d("MainActivity", "⚠️ Модель по умолчанию скрыта, используем: ${fallbackModel.name}")

                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (fragment is ChatFragment) {
                    fragment.updateSelectedModel(fallbackModel.apiId)
                }
            } else {
                android.util.Log.d("MainActivity", "⚠️ Нет доступных моделей")
            }
        }
    }

    fun getAllAvailableModels(): List<AIModel> {
        val prefs = getSharedPreferences("ai_models_prefs", MODE_PRIVATE)
        val customModelsJson = prefs.getString("custom_models_list", "[]")
        val customModelsArray = org.json.JSONArray(customModelsJson)

        val allModels = mutableListOf<AIModel>()

        // Добавляем ТОЛЬКО ВИДИМЫЕ встроенные модели (без заголовка и без скрытых)
        allModels.addAll(getVisibleBuiltInModels())

        // Добавляем пользовательские модели
        for (i in 0 until customModelsArray.length()) {
            val obj = customModelsArray.getJSONObject(i)
            allModels.add(AIModel(obj.getString("name"), obj.getString("apiPath")))
        }

        android.util.Log.d("MainActivity", "getAllAvailableModels: найдено ${allModels.size} моделей")
        return allModels
    }
}