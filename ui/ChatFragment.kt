package com.example.speechrecognizer.ui

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.example.speechrecognizer.AlarmReceiver
import com.example.speechrecognizer.MainActivity
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.AlarmRepository
import com.example.speechrecognizer.data.Reminder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.*
import com.example.speechrecognizer.data.ReminderType
import com.example.speechrecognizer.utils.AlarmScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.GlobalScope

import androidx.activity.addCallback
import com.example.speechrecognizer.CommandExecutor
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException
import com.example.speechrecognizer.NotificationHelper


@Serializable data class AiRequest(val user_id: String, val question: String)
@Serializable data class AiResponse(val answer: String? = null, val error: String? = null)
@Serializable data class ResetRequest(val user_id: String)

private const val BASE_URL = "http://192.168.3.129:5000"

class ChatFragment : Fragment(R.layout.fragment_chat), TextToSpeech.OnInitListener {


    private val apiKey = "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"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    val config = OpenAIConfig(
        token = "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",
        host = OpenAIHost("https://openrouter.ai"),
        headers = mapOf(
            "HTTP-Referer" to "https://my-app.com",
            "X-Title" to "My AI App"
        )
    )
    val openRouterClient = OpenAI(config)

    private var isAiThinking = false // Предохранитель от бесконечных циклов и двойных запросов
    private var skipAutoResponseForCommand = false // Защита от перехвата команд методом loadChatHistory
    private var aiGenerationJob: Job? = null // Для хранения и отмены текущего запроса ИИ

    private var currentProcessingJob: Job? = null


    private val selectedMessages = mutableSetOf<View>() // Для хранения выделенных вью
    private var isSelectionMode = false

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    private var chatId: String = "default_chat" // ID текущего чата

    private var currentModelId: String = "openai/gpt-chat-latest" // ИЗМЕНЕНО: теперь загружается из настроек

    // Список для хранения истории диалога
    private val chatContext = mutableListOf<JSONObject>()

    private var lastDisplayedDate: String? = null

    private lateinit var llSelectionActions: View

    private var pulseAnimation: Animation? = null



    private lateinit var dialogueContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnSpeak: FloatingActionButton

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var tts: TextToSpeech
    private lateinit var userId: String
    private lateinit var repository: AlarmRepository

    private var forwardedText: String? = null
    private lateinit var cvForwardPreview: View
    private lateinit var tvForwardContent: TextView

    private lateinit var alarmScheduler: AlarmScheduler

    private val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val PREFS_NAME = "AiAssistantPrefs"
        private const val USER_ID_KEY = "UserId"

        fun newInstance(chatId: String) = ChatFragment().apply {
            arguments = Bundle().apply { putString("CHAT_ID", chatId) }
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

    private lateinit var btnDeleteSelected: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = arguments?.getString("CHAT_ID") ?: "default_chat"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Снимаем флаг непрочитанных при открытии чата
        val appContext = requireContext().applicationContext
        val alarmRepo = AlarmRepository(appContext)
        alarmRepo.markChatAsRead(chatId)

        // Отправляем broadcast для обновления списка чатов
        val updateIntent = Intent("com.example.speechrecognizer.UPDATE_ITEMS").apply {
            setPackage(requireContext().packageName)
        }
        requireContext().sendBroadcast(updateIntent)

        Log.d("ChatFragment", "Чат $chatId открыт, флаг непрочитанных снят")

        // 1. Инициализация UI элементов из макета самого фрагмента
        dialogueContainer = view.findViewById(R.id.dialogueContainer)
        scrollView = view.findViewById(R.id.scrollView)
        cvForwardPreview = view.findViewById(R.id.cvForwardPreview)
        tvForwardContent = view.findViewById(R.id.tvForwardContent)

        view.findViewById<ImageView>(R.id.btnCancelForward)?.setOnClickListener {
            cancelForwarding()
        }

        // 2. МГНОВЕННАЯ И ПРИОРИТЕТНАЯ НАСТРОЙКА КНОПОК ПАНЕЛИ ВЫДЕЛЕНИЯ ACTIVITY
        val currentActivity = activity
        if (currentActivity != null) {
            etMessage = currentActivity.findViewById(R.id.etMessage)
            btnSend = currentActivity.findViewById(R.id.btnSend)
            btnSpeak = currentActivity.findViewById(R.id.btnSpeak)

            // --- НАСТРОЙКА ПАНЕЛИ ВЫДЕЛЕНИЯ ИЗ МАКЕТА ФРАГМЕНТА ---
            llSelectionActions = view.findViewById(R.id.llSelectionActions)
            btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected)
            val btnCancelSelection = view.findViewById<View>(R.id.btnCancelSelection)

            llSelectionActions.apply {
                isClickable = true
                isFocusable = true
            }

            btnDeleteSelected.setOnClickListener {
                val count = selectedMessages.size
                if (count > 0) {
                    val messageText = if (count == 1) "Удалить выбранное сообщение?" else "Удалить выбранные сообщения ($count шт.)?"
                    showConfirmDialog(
                        title = "Удаление",
                        message = "$messageText Они исчезнут из чата и памяти ассистента."
                    ) {
                        deleteSelectedMessages()
                    }
                }
            }

            btnCancelSelection?.setOnClickListener {
                exitSelectionMode()
            }

            // Кнопка визуального возврата назад
            currentActivity.findViewById<View>(R.id.btnBackText)?.setOnClickListener {
                handleBackNavigation()
            }
        }

        // 3. Системный жест Назад на смартфоне
        currentActivity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                isEnabled = false
                handleBackNavigation()
            }
        }

        // 4. Инициализация анимаций и фоновых сервисов
        pulseAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
        alarmScheduler = AlarmScheduler(requireContext())
        repository = AlarmRepository(requireContext())
        userId = getOrCreateUserId()

        // ===== ЗАГРУЗКА МОДЕЛИ ПО УМОЛЧАНИЮ ИЗ НАСТРОЕК =====
        currentModelId = getDefaultModelId()
        Log.d("ChatFragment", "🔧 Модель по умолчанию: $currentModelId")

        checkPermissions()

        try {
            tts = TextToSpeech(requireContext().applicationContext, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setupSpeechRecognizer()
        setupButtonTouchListener()
        setupTextInputListener()
        loadChatHistory()

        // 5. Логика автоматического старта прослушивания
        val shouldListen = arguments?.getBoolean("START_LISTENING") ?: false
        if (shouldListen) {
            arguments?.putBoolean("START_LISTENING", false)
            view.postDelayed({
                if (isAdded) startVoiceRecognition()
            }, 600)
        }
    }

    // ===== МЕТОД ДЛЯ ПОЛУЧЕНИЯ ВИДИМЫХ ВСТРОЕННЫХ МОДЕЛЕЙ =====
    private fun getVisibleBuiltInModels(): List<MainActivity.AIModel> {
        val prefs = requireContext().getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)
        val hiddenModelsJson = prefs.getString("hidden_builtin_models", "[]")
        val hiddenArray = JSONArray(hiddenModelsJson)
        val hiddenIds = mutableListOf<String>()

        for (i in 0 until hiddenArray.length()) {
            hiddenIds.add(hiddenArray.getString(i))
        }

        val activity = activity as? MainActivity
        val allBuiltIn = activity?.availableModels?.filter { !it.isHeader } ?: emptyList()
        return allBuiltIn.filter { it.apiId !in hiddenIds }
    }

    // ===== МЕТОД ПОЛУЧЕНИЯ МОДЕЛИ ПО УМОЛЧАНИЮ ИЗ НАСТРОЕК =====
    private fun getDefaultModelId(): String {
        val prefs = requireContext().getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)
        val defaultModelId = prefs.getString("default_model_id", "openai/gpt-chat-latest")

        Log.d("ChatFragment", "📱 defaultModelId из SharedPreferences: $defaultModelId")

        // Получаем ТОЛЬКО ВИДИМЫЕ встроенные модели
        val visibleBuiltInModels = getVisibleBuiltInModels()
        val visibleBuiltInIds = visibleBuiltInModels.map { it.apiId }

        Log.d("ChatFragment", "📱 Видимые встроенные модели: $visibleBuiltInIds")

        if (visibleBuiltInIds.contains(defaultModelId)) {
            Log.d("ChatFragment", "✅ Найдена видимая встроенная модель: $defaultModelId")
            return defaultModelId ?: "openai/gpt-chat-latest"
        }

        val customModelsJson = prefs.getString("custom_models_list", "[]")
        val customModelsArray = JSONArray(customModelsJson)
        Log.d("ChatFragment", "📱 Пользовательские модели JSON: $customModelsJson")

        for (i in 0 until customModelsArray.length()) {
            val obj = customModelsArray.getJSONObject(i)
            val id = obj.getString("id")
            val apiPath = obj.getString("apiPath")
            Log.d("ChatFragment", "📱 Проверяем модель: id=$id, apiPath=$apiPath")

            if (id == defaultModelId) {
                Log.d("ChatFragment", "✅ Найдена пользовательская модель: $apiPath")
                return apiPath
            }
        }

        // Если модель по умолчанию скрыта или не найдена, выбираем первую видимую
        if (visibleBuiltInModels.isNotEmpty()) {
            val fallbackModel = visibleBuiltInModels.first()
            Log.d("ChatFragment", "⚠️ Модель по умолчанию скрыта или не найдена, используем: ${fallbackModel.name}")
            return fallbackModel.apiId
        }

        Log.d("ChatFragment", "⚠️ Нет доступных моделей, возвращаем ChatGPT")
        return "openai/gpt-chat-latest"
    }

    private fun handleBackNavigation() {
        (activity as? MainActivity)?.openFragment(ChatListFragment(), R.id.navChats)
    }

    private fun saveChatHistory() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        chatContext.forEach { array.put(it) }

        prefs.edit().putString("chat_history_$chatId", array.toString()).apply()
        updateLastMessageInThread()
    }

    private fun loadChatHistory() {
        chatContext.clear()
        lastDisplayedDate = null
        dialogueContainer.removeAllViews()

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString("chat_history_$chatId", null) ?: return

        try {
            val array = JSONArray(historyStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val role = obj.optString("role")
                val content = obj.optString("content")
                val time = obj.optLong("timestamp")
                val isHidden = obj.optBoolean("is_hidden", false)

                chatContext.add(obj)

                if (!isHidden) {
                    if (role == "system_info") {
                        val sysView = LayoutInflater.from(requireContext())
                            .inflate(R.layout.item_chat_system, dialogueContainer, false)
                        sysView.findViewById<TextView>(R.id.tvSystemMessage).text = content
                        dialogueContainer.addView(sysView)
                    } else {
                        addMessageToChat(content, role == "user", time, addToContext = false)
                    }
                }
            }
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateLastMessageInThread() {
        val lastMsg = chatContext.lastOrNull { it.optString("role") != "system_info" }?.optString("content") ?: ""
        repository.updateChatPreview(chatId, lastMsg)
    }

    private fun setupTextInputListener() {
        btnSend.setOnClickListener { sendMessageFromInput() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessageFromInput()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    private fun sendMessageFromInput() {
        val userInput = etMessage.text.toString().trim()

        if (userInput.isNotEmpty() || forwardedText != null) {
            val finalInput = if (forwardedText != null) {
                "Контекст пересланного сообщения: \"$forwardedText\"\nВопрос/Дополнение: $userInput"
            } else {
                userInput
            }

            generateAIResponse(finalInput)

            etMessage.text.clear()
            cancelForwarding()
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requireActivity().currentFocus?.let {
            inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun addMessageToChat(message: String, isUser: Boolean, timestamp: Long = System.currentTimeMillis(), addToContext: Boolean = true) {
        val context = context ?: return

        if (addToContext) {
            val role = if (isUser) "user" else "assistant"
            val messageObj = JSONObject().apply {
                put("role", role)
                put("content", message)
                put("timestamp", timestamp)
            }
            chatContext.add(messageObj)
            saveChatHistory()
        }

        val dateStr = dateFormat.format(Date(timestamp))
        if (dateStr != lastDisplayedDate) {
            val dateLayout = LayoutInflater.from(context).inflate(R.layout.item_chat_date, dialogueContainer, false)
            dateLayout.findViewById<TextView>(R.id.tvDateHeader).text = when(dateStr) {
                dateFormat.format(Date()) -> "Сегодня"
                dateFormat.format(Date(System.currentTimeMillis() - 86400000)) -> "Вчера"
                else -> dateStr
            }
            dialogueContainer.addView(dateLayout)
            lastDisplayedDate = dateStr
        }

        val layoutRes = if (isUser) R.layout.item_chat_user else R.layout.item_chat_ai
        val messageView = LayoutInflater.from(context).inflate(layoutRes, dialogueContainer, false)

        val tvMessage = messageView.findViewById<TextView>(R.id.tvMessageText)
        val tvTime = messageView.findViewById<TextView>(R.id.tvTime)
        val cardContainer = messageView.findViewById<View>(R.id.cardContainer)

        val markwon = Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(JLatexMathPlugin.create(tvMessage.textSize) { builder ->
                builder.inlinesEnabled(true)
                builder.theme().apply {
                    textColor(tvMessage.currentTextColor)
                    backgroundProvider { ColorDrawable(0) }
                }
            })
            .build()

        var normalizedMessage = message

        normalizedMessage = normalizedMessage.replace(Regex("\\\\\\(([\\s\\S]*?)\\\\\\)")) { matchResult ->
            "$$" + matchResult.groupValues[1].trim() + "$$"
        }

        normalizedMessage = normalizedMessage.replace(Regex("(?<!\\$)\\$(?!\\$)([^\\$\\n]+?)\\$(?!\\$)")) { matchResult ->
            "$$" + matchResult.groupValues[1].trim() + "$$"
        }

        normalizedMessage = normalizedMessage.replace(Regex("\\\\\\[\\s*([\\s\\S]*?)\\s*\\\\\\]")) { matchResult ->
            val formulaContent = matchResult.groupValues[1].replace("\n", " ").trim()
            "\n$$\n$formulaContent\n$$\n"
        }

        markwon.setMarkdown(tvMessage, normalizedMessage)
        tvTime.text = timeFormat.format(Date(timestamp))

        dialogueContainer.addView(messageView)
        scrollView.post { scrollView.smoothScrollTo(0, dialogueContainer.bottom) }

        tvMessage.movementMethod = null
        tvMessage.isClickable = false
        tvMessage.isLongClickable = false

        tvMessage.setOnTouchListener { _, event ->
            cardContainer.onTouchEvent(event)
            true
        }

        cardContainer.setOnLongClickListener {
            if (!isSelectionMode) {
                enterSelectionMode(messageView)
            }
            true
        }

        cardContainer.setOnClickListener {
            if (isSelectionMode) {
                toggleMessageSelection(messageView)
            } else {
                showSingleMessageMenu(cardContainer, message, isUser)
            }
        }

        messageView.tag = timestamp
    }

    private fun showSingleMessageMenu(anchorView: View, text: String, isUser: Boolean) {
        val context = context ?: return
        val dialog = android.app.Dialog(context)
        val menuView = layoutInflater.inflate(R.layout.layout_single_message_menu, null)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(menuView)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorX = location[0]

            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
            val anchorY = location[1] - statusBarHeight

            setGravity(android.view.Gravity.TOP or android.view.Gravity.START)

            val params = attributes

            val menuWidthPx = dpToPx(160)
            params.width = menuWidthPx

            if (isUser) {
                params.x = anchorX + anchorView.width - menuWidthPx
            } else {
                params.x = anchorX + dpToPx(12)
            }

            params.y = anchorY + (anchorView.height * 0.7).toInt()

            params.windowAnimations = android.R.style.Animation_Dialog

            attributes = params
        }

        var rootMessageView: View? = anchorView
        while (rootMessageView != null && rootMessageView.tag == null) {
            rootMessageView = rootMessageView.parent as? View
        }

        if (rootMessageView != null) {
            setupMenuButtons(menuView, dialog, text, rootMessageView)
        } else {
            setupMenuButtons(menuView, dialog, text, anchorView)
        }

        dialog.show()
    }

    private fun setupMenuButtons(view: View, dialog: android.app.Dialog, text: String, anchorView: View) {
        view.findViewById<View>(R.id.btnCopy).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Chat", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Скопировано", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnForward).setOnClickListener {
            forwardedText = text
            tvForwardContent.text = text
            cvForwardPreview.visibility = View.VISIBLE

            etMessage.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT)

            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnDelete).setOnClickListener {
            dialog.dismiss()
            showConfirmDialog("Удалить сообщение?", "Оно исчезнет из чата и памяти ассистента.") {
                deleteSingleMessage(anchorView)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun cancelForwarding() {
        forwardedText = null
        cvForwardPreview.visibility = View.GONE
    }

    private fun deleteSingleMessage(view: View) {
        val timestamp = view.tag as Long

        val iterator = chatContext.iterator()
        while (iterator.hasNext()) {
            val obj = iterator.next()
            if (obj.optLong("timestamp") == timestamp) {
                iterator.remove()
                break
            }
        }

        saveChatHistory()
        dialogueContainer.removeView(view)
    }

    private fun enterSelectionMode(firstView: View) {
        isSelectionMode = true
        toggleMessageSelection(firstView)
        llSelectionActions.visibility = View.VISIBLE
    }

    private fun toggleMessageSelection(view: View) {
        val cardView = view.findViewById<CardView>(R.id.cardContainer)
        val tickIcon = view.findViewById<ImageView>(R.id.ivSelectedTick)

        if (selectedMessages.contains(view)) {
            selectedMessages.remove(view)
            cardView.foreground = null
            tickIcon.visibility = View.GONE
        } else {
            selectedMessages.add(view)
            cardView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_message_selected)
            tickIcon.visibility = View.VISIBLE
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        val selectionActions = requireActivity().findViewById<View>(R.id.llSelectionActions)

        if (selectedMessages.isEmpty()) {
            isSelectionMode = false
            selectionActions.visibility = View.GONE
        } else {
            isSelectionMode = true
            selectionActions.visibility = View.VISIBLE
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false

        selectedMessages.forEach { view ->
            val cardView = view.findViewById<CardView>(R.id.cardContainer)
            val tickIcon = view.findViewById<ImageView>(R.id.ivSelectedTick)
            cardView?.foreground = null
            tickIcon?.visibility = View.GONE
        }

        selectedMessages.clear()
        val selectionActions = requireActivity().findViewById<View>(R.id.llSelectionActions)
        selectionActions.visibility = View.GONE
    }

    private fun deleteSelectedMessages() {
        val viewsToRemove = selectedMessages.toList()

        viewsToRemove.forEach { view ->
            val timestamp = view.tag as? Long ?: return@forEach

            val iterator = chatContext.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().optLong("timestamp") == timestamp) {
                    iterator.remove()
                    break
                }
            }

            dialogueContainer.removeView(view)
        }

        saveChatHistory()
        exitSelectionMode()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun getOrCreateUserId(): String {
        val prefs: SharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(USER_ID_KEY, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(USER_ID_KEY, id).apply()
        }
        return id
    }

    private suspend fun classifyIntent(userInput: String, modelId: String): JSONObject? {
        return withContext(Dispatchers.IO + NonCancellable) {
            try {
                val okHttpClient = OkHttpClient()

                val dateSdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val todayDigits = dateSdf.format(Date())
                val tomorrowDigits = dateSdf.format(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))

                val systemPrompt = """
            Ты — классификатор команд ассистента. Твоя задача — строго переводить фразы пользователя в JSON.
            Сегодняшняя дата: $todayDigits.
            Завтрашняя дата: $tomorrowDigits.
            
            ДОСТУПНЫЕ ТИПЫ И ПРАВИЛА:
            1. ALARM: (time) — Установка будильника. Время всегда в формате HH:mm.
            2. TIMER: (value, unit) — Таймер. value — число, unit — "минут", "часов" или "секунд".
            3. REMINDER: (task, time) — Напоминание. task — текст действия, time — HH:mm.
            
            4. DELETE: (target) — Удаление. 
               target может быть временем ("08:00") или текстом из напоминания ("купить хлеб").
               Пример: "удали напомнилку про хлеб" -> {"type":"DELETE", "target":"хлеб"}
               Пример: "отмени таймер на 19:00" -> {"type":"DELETE", "target":"19:00"}
            
            5. EDIT: (target, value) — Изменение существующего. 
               target — что меняем (время или текст), value — на какое НОВОЕ время меняем (HH:mm или кол-во минут).
               Пример: "переставь будильник с 7 на 8 утра" -> {"type":"EDIT", "target":"07:00", "value":"08:00"}
               Пример: "измени таймер 5 минут на 10" -> {"type":"EDIT", "target":"5", "value":"10"}
            
            6. ACTION_RESET_CONTEXT: Сброс памяти (команды: забудь, сбрось контекст, начни заново).
            7. ACTION_DELETE_CHAT: Очистка экрана (команды: очисти экран, убери сообщения).
            8. ACTION_FULL_RESET: Полное удаление всего.
            9. CHAT: Если фраза не является командой управления.
            
            ПРАВИЛА ДЛЯ ДАТ:
            - Поле "date" ОБЯЗАТЕЛЬНО для REMINDER и ALARM.
            - Если пользователь говорит "завтра", используй $tomorrowDigits.
            - Если дата не указана, используй текущую: $todayDigits.
            - Формат даты СТРОГО dd.MM.yyyy.
        
            ПРАВИЛА ОТВЕТА:
            - Отвечай ТОЛЬКО чистым JSON.
            - Не добавляй пояснений.
            - Если время указано словами ("в полдень", "вечером"), конвертируй в числа ("12:00", "18:00").
            
            ПРИМЕРЫ:
            "измени напомнилку покормить кота на 20:00" -> {"type":"EDIT", "target":"покормить кота", "value":"20:00"}
            "удали таймер на 10 минут" -> {"type":"DELETE", "target":"10"}
            "напомни выключить плиту в 14:30" -> {"type":"REMINDER", "task":"выключить плиту", "time":"14:30"}
            "напомни покормить кота завтра в 9 утра" -> {"type":"REMINDER", "task":"покормить кота", "time":"09:00", "date":"$tomorrowDigits"}
            "разбуди в 7" -> {"type":"ALARM", "time":"07:00", "date":"$todayDigits"}
        """.trimIndent()

                val rootJson = JSONObject().apply {
                    put("model", modelId)
                    put("temperature", 0)
                    val messagesArray = JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                        put(JSONObject().apply { put("role", "user"); put("content", userInput) })
                    }
                    put("messages", messagesArray)
                }

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "https://localhost")
                    .post(rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val bodyString = response.body?.string() ?: ""

                    if (response.isSuccessful && !bodyString.trim().startsWith("<!DOCTYPE")) {
                        val content = JSONObject(bodyString)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")

                        val cleanJson = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1)
                        JSONObject(cleanJson)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun generateAIResponse(userInput: String) {
        Log.d("AI_NETWORK_DEBUG", "==================================================")
        Log.d("AI_NETWORK_DEBUG", "[1] НАЧАЛО ПРОЦЕССА. Пользователь ввёл: \"$userInput\"")

        addMessageToChat(userInput, true)

        val appContext = requireContext().applicationContext
        val currentChatId = this.chatId
        val modelIdSnapshot = this.currentModelId

        Log.d("AI_NETWORK_DEBUG", "[2] Контекст захвачен. ChatID: $currentChatId, Текущая модель: $modelIdSnapshot")

        var processingView: View? = null
        var btnCancelProcessing: ImageView? = null

        if (isAdded && view != null) {
            try {
                processingView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_chat_ai, dialogueContainer, false)
                val tvMessage = processingView.findViewById<TextView>(R.id.tvMessageText)
                tvMessage.text = "Обрабатываем запрос"

                btnCancelProcessing = processingView.findViewById<ImageView>(R.id.btnCancelGeneration)
                btnCancelProcessing?.visibility = View.VISIBLE

                dialogueContainer.addView(processingView)
                scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            } catch (e: Exception) {
                Log.e("AI_NETWORK_DEBUG", "Ошибка создания индикатора: ${e.message}")
            }
        }

        val processingAnimationJob = lifecycleScope.launch {
            if (processingView == null) return@launch
            val tvMessage = processingView.findViewById<TextView>(R.id.tvMessageText) ?: return@launch
            var dotCount = 0
            while (isActive) {
                dotCount = (dotCount % 3) + 1
                if (tvMessage.isAttachedToWindow) {
                    tvMessage.text = "Обрабатываем запрос" + ".".repeat(dotCount)
                }
                delay(500)
            }
        }

        skipAutoResponseForCommand = true

        val processingJob = GlobalScope.launch(Dispatchers.Main) {
            try {
                val intentJson = withContext(Dispatchers.IO) {
                    classifyIntent(userInput, modelIdSnapshot)
                }

                if (!isActive) {
                    return@launch
                }

                processingAnimationJob.cancel()
                if (isAdded && view != null && processingView != null) {
                    try {
                        dialogueContainer.removeView(processingView)
                    } catch (e: Exception) {
                        Log.e("AI_NETWORK_DEBUG", "Ошибка удаления индикатора: ${e.message}")
                    }
                }

                if (intentJson == null) {
                    skipAutoResponseForCommand = false
                    if (isAdded && view != null) {
                        getAiResponseFromServer(userInput)
                    } else {
                        generateAiResponseInBackground(userInput, appContext, currentChatId)
                    }
                    return@launch
                }

                val type = intentJson.optString("type", "CHAT")

                when (type) {
                    "REMINDER" -> {
                        val task = intentJson.optString("task", "")
                        val time = intentJson.optString("time", "")
                        val dateStr = intentJson.optString("date", null)

                        val response = withContext(Dispatchers.IO + NonCancellable) {
                            CommandExecutor.handleReminder(
                                task = task,
                                time = time,
                                dateStr = dateStr,
                                appContext = appContext,
                                chatId = currentChatId
                            )
                        }

                        skipAutoResponseForCommand = false

                        if (isAdded && view != null && currentChatId == chatId) {
                            val memoryObj = JSONObject().apply {
                                put("role", "assistant")
                                put("content", response)
                                put("timestamp", System.currentTimeMillis())
                            }
                            chatContext.add(memoryObj)
                            addMessageToChat(response, false, addToContext = false)
                            if (::tts.isInitialized) {
                                tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "")
                            }
                        }
                    }

                    "TIMER" -> {
                        val amount = intentJson.optLong("value", 0L)
                        val unit = intentJson.optString("unit", "")

                        val response = withContext(Dispatchers.IO + NonCancellable) {
                            CommandExecutor.handleTimer(
                                amount = amount,
                                unit = unit,
                                appContext = appContext,
                                chatId = currentChatId
                            )
                        }

                        skipAutoResponseForCommand = false

                        if (isAdded && view != null && currentChatId == chatId) {
                            val memoryObj = JSONObject().apply {
                                put("role", "assistant")
                                put("content", response)
                                put("timestamp", System.currentTimeMillis())
                            }
                            chatContext.add(memoryObj)
                            addMessageToChat(response, false, addToContext = false)
                            if (::tts.isInitialized) {
                                tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "")
                            }
                        }
                    }

                    "ALARM" -> {
                        val time = intentJson.optString("time", "")
                        val response = handleSetAlarm(time)

                        skipAutoResponseForCommand = false

                        if (isAdded && view != null) {
                            val memoryObj = JSONObject().apply {
                                put("role", "assistant")
                                put("content", response)
                                put("timestamp", System.currentTimeMillis())
                            }
                            chatContext.add(memoryObj)
                            addMessageToChat(response, false, addToContext = false)
                            if (::tts.isInitialized) {
                                tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "")
                            }
                        }
                    }

                    "ACTION_RESET_CONTEXT" -> {
                        skipAutoResponseForCommand = false
                        if (isAdded && view != null) resetContextOnly()
                    }
                    "ACTION_DELETE_CHAT" -> {
                        skipAutoResponseForCommand = false
                        if (isAdded && view != null) deleteChatOnly()
                    }
                    "ACTION_FULL_RESET" -> {
                        skipAutoResponseForCommand = false
                        if (isAdded && view != null) fullReset()
                    }

                    else -> {
                        skipAutoResponseForCommand = false
                        if (isAdded && view != null) {
                            getAiResponseFromServer(userInput)
                        } else {
                            generateAiResponseInBackground(userInput, appContext, currentChatId)
                        }
                    }
                }

            } catch (e: CancellationException) {
                processingAnimationJob.cancel()
                if (isAdded && view != null && processingView != null) {
                    dialogueContainer.removeView(processingView)
                }
                skipAutoResponseForCommand = false
            } catch (e: Exception) {
                processingAnimationJob.cancel()
                if (isAdded && view != null && processingView != null) {
                    dialogueContainer.removeView(processingView)
                }
                skipAutoResponseForCommand = false
                if (isAdded && view != null) {
                    val errorMsg = "Извините, произошла ошибка при обработке запроса."
                    addMessageToChat(errorMsg, false)
                }
            }
        }

        currentProcessingJob = processingJob

        btnCancelProcessing?.setOnClickListener {
            if (processingJob.isActive) {
                processingJob.cancel()
            }
            processingAnimationJob.cancel()
            if (isAdded && view != null && processingView != null) {
                dialogueContainer.removeView(processingView)
            }
            skipAutoResponseForCommand = false
            isAiThinking = false
            Toast.makeText(appContext, "Обработка отменена", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateSelectedModel(modelId: String) {
        this.currentModelId = modelId
        Log.d("ChatFragment", "Модель изменена на: $modelId")
    }

    private fun getAiResponseFromServer(userInput: String) {
        if (isAiThinking) {
            Log.w("AI_NETWORK_DEBUG", "AI уже думает, игнорируем повторный запрос")
            return
        }
        isAiThinking = true

        val appContext = requireContext().applicationContext
        val currentChatId = this.chatId

        var thinkingMessageView: View? = null
        var btnCancelGen: ImageView? = null

        if (isAdded && view != null) {
            try {
                thinkingMessageView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_chat_ai, dialogueContainer, false)
                val tvMessage = thinkingMessageView.findViewById<TextView>(R.id.tvMessageText)
                val tvTime = thinkingMessageView.findViewById<TextView>(R.id.tvTime)

                tvMessage.text = "Готовим ответ"
                tvTime?.visibility = View.GONE

                btnCancelGen = thinkingMessageView.findViewById<ImageView>(R.id.btnCancelGeneration)
                btnCancelGen?.visibility = View.VISIBLE

                dialogueContainer.addView(thinkingMessageView)
                scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            } catch (e: Exception) {
                Log.e("AI_NETWORK_DEBUG", "Ошибка создания индикатора: ${e.message}")
            }
        }

        val animationJob = if (thinkingMessageView != null) {
            lifecycleScope.launch {
                val tvMessage = thinkingMessageView.findViewById<TextView>(R.id.tvMessageText) ?: return@launch
                var dotCount = 0
                while (isActive) {
                    dotCount = (dotCount % 3) + 1
                    if (tvMessage.isAttachedToWindow) {
                        tvMessage.text = "Готовим ответ" + ".".repeat(dotCount)
                    }
                    delay(500)
                }
            }
        } else null

        val generationJob = GlobalScope.launch(Dispatchers.Main) {
            try {
                val result = withContext(Dispatchers.IO + NonCancellable) {
                    try {
                        val contextCopy = synchronized(chatContext) {
                            chatContext.toList()
                        }

                        val rootJson = JSONObject().apply {
                            put("model", currentModelId)
                            val messagesArray = JSONArray()

                            val lastResetIndex = contextCopy.indexOfLast {
                                it.optString("role") == "system_info" &&
                                        (it.optString("content").contains("Контекст очищен", ignoreCase = true) ||
                                                it.optString("content").contains("Контекст и чат очищены", ignoreCase = true))
                            }

                            val actualMessages = if (lastResetIndex != -1) {
                                contextCopy.subList(lastResetIndex + 1, contextCopy.size)
                            } else {
                                contextCopy
                            }

                            actualMessages.forEach { msg ->
                                val role = msg.optString("role")
                                if (role == "user" || role == "assistant") {
                                    messagesArray.put(JSONObject().apply {
                                        put("role", role)
                                        put("content", msg.optString("content"))
                                    })
                                }
                            }
                            put("messages", messagesArray)
                        }

                        val request = Request.Builder()
                            .url("https://openrouter.ai/api/v1/chat/completions")
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("HTTP-Referer", "https://localhost")
                            .post(rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()

                        okHttpClient.newCall(request).execute().use { response ->
                            val bodyString = response.body?.string() ?: ""

                            if (bodyString.trim().startsWith("<!DOCTYPE")) {
                                return@withContext "Ошибка: Сервер вернул некорректный ответ."
                            }

                            if (response.isSuccessful) {
                                JSONObject(bodyString)
                                    .getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content")
                                    .trim()
                            } else {
                                "Ошибка: ${response.code}"
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "Ошибка сети: ${e.localizedMessage}"
                    }
                }

                if (!isActive) {
                    return@launch
                }

                animationJob?.cancel()
                if (isAdded && view != null && thinkingMessageView != null) {
                    try {
                        dialogueContainer.removeView(thinkingMessageView)
                    } catch (e: Exception) {
                        Log.e("AI_NETWORK_DEBUG", "Ошибка удаления индикатора: ${e.message}")
                    }
                }

                if (isAdded && view != null && currentChatId == chatId) {
                    if (!result.startsWith("Ошибка:")) {
                        addMessageToChat(result, false)
                        if (::tts.isInitialized) {
                            try {
                                tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, "")
                            } catch (e: Exception) {
                                Log.e("AI_NETWORK_DEBUG", "Ошибка TTS: ${e.message}")
                            }
                        }
                    } else {
                        addMessageToChat(result, false, addToContext = true)
                    }
                } else {
                    saveResponseToHistory(result, appContext, currentChatId)
                }

            } catch (e: CancellationException) {
                animationJob?.cancel()
                if (isAdded && view != null && thinkingMessageView != null) {
                    dialogueContainer.removeView(thinkingMessageView)
                }
            } catch (e: Exception) {
                animationJob?.cancel()
                if (isAdded && view != null && thinkingMessageView != null) {
                    dialogueContainer.removeView(thinkingMessageView)
                }
                if (isAdded && view != null) {
                    addMessageToChat("Извините, произошла ошибка при генерации ответа.", false)
                }
            } finally {
                isAiThinking = false
            }
        }

        currentProcessingJob = generationJob

        btnCancelGen?.setOnClickListener {
            if (generationJob.isActive) {
                generationJob.cancel()
            }
            animationJob?.cancel()
            if (isAdded && view != null && thinkingMessageView != null) {
                dialogueContainer.removeView(thinkingMessageView)
            }
            isAiThinking = false
            Toast.makeText(appContext, "Генерация ответа отменена", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAiResponseInBackground(userInput: String, appContext: Context, chatId: String) {
        GlobalScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                val contextCopy = synchronized(chatContext) {
                    chatContext.toList()
                }

                val rootJson = JSONObject().apply {
                    put("model", currentModelId)
                    val messagesArray = JSONArray()

                    val lastResetIndex = contextCopy.indexOfLast {
                        it.optString("role") == "system_info" &&
                                (it.optString("content").contains("Контекст очищен", ignoreCase = true) ||
                                        it.optString("content").contains("Контекст и чат очищены", ignoreCase = true))
                    }

                    val actualMessages = if (lastResetIndex != -1) {
                        contextCopy.subList(lastResetIndex + 1, contextCopy.size)
                    } else {
                        contextCopy
                    }

                    actualMessages.forEach { msg ->
                        val role = msg.optString("role")
                        if (role == "user" || role == "assistant") {
                            messagesArray.put(JSONObject().apply {
                                put("role", role)
                                put("content", msg.optString("content"))
                            })
                        }
                    }
                    put("messages", messagesArray)
                }

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "https://localhost")
                    .post(rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val result = okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""

                    if (bodyString.trim().startsWith("<!DOCTYPE")) {
                        return@use "Ошибка: Сервер вернул некорректный ответ."
                    }

                    if (response.isSuccessful) {
                        JSONObject(bodyString)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } else {
                        "Ошибка: ${response.code}"
                    }
                }

                val prefs = appContext.getSharedPreferences("AiAssistantPrefs", Context.MODE_PRIVATE)
                val historyKey = "chat_history_$chatId"
                val historyStr = prefs.getString(historyKey, null)
                val historyArray = if (!historyStr.isNullOrEmpty()) {
                    JSONArray(historyStr)
                } else {
                    JSONArray()
                }

                val assistantMsg = JSONObject().apply {
                    put("role", "assistant")
                    put("content", result)
                    put("timestamp", System.currentTimeMillis())
                }
                historyArray.put(assistantMsg)
                prefs.edit().putString(historyKey, historyArray.toString()).apply()

                val repository = AlarmRepository(appContext)
                repository.updateChatPreview(chatId, result)
                repository.markChatAsUnread(chatId)

                val chatInfo = repository.getChatById(chatId)
                if (chatInfo != null) {
                    try {
                        NotificationHelper.showNewMessageNotification(
                            context = appContext,
                            chatId = chatId,
                            chatTitle = chatInfo.title,
                            message = result
                        )
                    } catch (e: Exception) {
                        Log.e("AI_NETWORK_DEBUG", "Ошибка уведомления: ${e.message}")
                    }
                }

                val updateIntent = Intent("com.example.speechrecognizer.UPDATE_ITEMS").apply {
                    setPackage(appContext.packageName)
                }
                appContext.sendBroadcast(updateIntent)

            } catch (e: Exception) {
                Log.e("AI_NETWORK_DEBUG", "Ошибка фоновой генерации: ${e.message}", e)
            }
        }
    }

    private fun saveResponseToHistory(text: String, context: Context, chatId: String) {
        GlobalScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                Log.d("AI_NETWORK_DEBUG", "[SAVE_HISTORY] Сохранение ответа в историю: ${text.take(50)}...")

                val prefs = context.getSharedPreferences("AiAssistantPrefs", Context.MODE_PRIVATE)
                val historyKey = "chat_history_$chatId"
                val historyStr = prefs.getString(historyKey, null)
                val historyArray = if (!historyStr.isNullOrEmpty()) {
                    JSONArray(historyStr)
                } else {
                    JSONArray()
                }

                val messageObj = JSONObject().apply {
                    put("role", "assistant")
                    put("content", text)
                    put("timestamp", System.currentTimeMillis())
                }
                historyArray.put(messageObj)

                prefs.edit().putString(historyKey, historyArray.toString()).apply()

                val repository = AlarmRepository(context)
                repository.updateChatPreview(chatId, text)
                repository.markChatAsUnread(chatId)

                val chatInfo = repository.getChatById(chatId)
                if (chatInfo != null) {
                    try {
                        NotificationHelper.showNewMessageNotification(
                            context = context,
                            chatId = chatId,
                            chatTitle = chatInfo.title,
                            message = text
                        )
                    } catch (e: Exception) {
                        Log.e("AI_NETWORK_DEBUG", "Ошибка уведомления: ${e.message}")
                    }
                }

                val updateIntent = Intent("com.example.speechrecognizer.UPDATE_ITEMS").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(updateIntent)

            } catch (e: Exception) {
                Log.e("AI_NETWORK_DEBUG", "Ошибка сохранения ответа: ${e.message}", e)
            }
        }
    }

    fun showChatMenu() {
        val dialog = android.app.Dialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_chat_menu_sheet, null)

        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(android.view.Gravity.TOP or android.view.Gravity.END)

            val params = attributes
            val widthInDp = 220
            val metrics = context.resources.displayMetrics
            params.width = (widthInDp * metrics.density).toInt()
            params.x = 60
            params.y = 120
            attributes = params
        }

        view.findViewById<View>(R.id.btnMenuResetContext).setOnClickListener {
            dialog.dismiss()
            showConfirmDialog("Сброс контекста", "ИИ забудет историю текущего диалога.") {
                resetContextOnly()
            }
        }

        view.findViewById<View>(R.id.btnMenuDeleteChat).setOnClickListener {
            dialog.dismiss()
            showConfirmDialog("Очистка экрана", "Все сообщения будут скрыты, но ИИ продолжит их помнить.") {
                deleteChatOnly()
            }
        }

        view.findViewById<View>(R.id.btnMenuFullReset).setOnClickListener {
            dialog.dismiss()
            showConfirmDialog("Удалить всё", "История сообщений и память ИИ будут полностью стерты.") {
                fullReset()
            }
        }

        dialog.show()
    }

    private fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        val dialog = android.app.Dialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_confirm_dialog, null)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<TextView>(R.id.tvConfirmTitle).text = title
        view.findViewById<TextView>(R.id.tvConfirmMessage).text = message

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addSystemMessage(text: String) {
        val lastMsg = chatContext.lastOrNull()?.optString("content")
        if (lastMsg == text) return

        val currentTime = System.currentTimeMillis()

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_chat_system, dialogueContainer, false)
        view.findViewById<TextView>(R.id.tvSystemMessage).text = text
        dialogueContainer.addView(view)

        chatContext.add(JSONObject().apply {
            put("role", "system_info")
            put("content", text)
            put("timestamp", currentTime)
            put("is_hidden", false)
        })
        saveChatHistory()

        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun resetContextOnly() {
        addSystemMessage("Контекст очищен")
    }

    private fun deleteChatOnly() {
        for (i in 0 until chatContext.size) {
            chatContext[i].put("is_hidden", true)
        }
        saveChatHistory()
        dialogueContainer.removeAllViews()
        lastDisplayedDate = null
        addSystemMessage("Чат очищен")
    }

    private fun fullReset() {
        chatContext.clear()
        saveChatHistory()
        dialogueContainer.removeAllViews()
        lastDisplayedDate = null
        addSystemMessage("Контекст и чат очищены")
    }

    private fun handleSetAlarm(time: String): String {
        try {
            val (hour, minute) = time.split(":").map { it.toInt() }
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Будильник от ассистента")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
                return "Открываю приложение 'Часы', чтобы установить будильник на $time."
            } else {
                return "Не могу найти приложение 'Часы'."
            }
        } catch (e: Exception) {
            return "Не удалось распознать время для будильника."
        }
    }

    private fun getCalendarForTime(time: String): Calendar {
        val timeParts = time.split(":").map { it.toInt() }
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timeParts[0])
            set(Calendar.MINUTE, timeParts[1])
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                startVisualFeedback()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                stopVisualFeedback()
            }
            override fun onError(error: Int) {
                stopVisualFeedback()
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    Toast.makeText(requireContext(), getErrorText(error), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResults(results: Bundle?) {
                stopVisualFeedback()
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)?.let {
                    generateAIResponse(it)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startVisualFeedback() {
        if (!isAdded) return
        val mainBtnSpeak = requireActivity().findViewById<FloatingActionButton>(R.id.btnSpeak)
        mainBtnSpeak.jumpDrawablesToCurrentState()
        val pulse = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
        mainBtnSpeak.startAnimation(pulse)
        mainBtnSpeak.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun stopVisualFeedback() {
        if (!isAdded) return
        val mainBtnSpeak = requireActivity().findViewById<FloatingActionButton>(R.id.btnSpeak)
        mainBtnSpeak.clearAnimation()
    }

    fun startVoiceRecognition() {
        if (::speechRecognizer.isInitialized) {
            try {
                speechRecognizer.startListening(speechRecognizerIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopVoiceRecognition() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
        }
        stopVisualFeedback()
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи аудио"
            SpeechRecognizer.ERROR_CLIENT -> "Ошибка на стороне клиента"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Недостаточно разрешений"
            SpeechRecognizer.ERROR_NETWORK -> "Сетевая ошибка"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Тайм-аут сети"
            SpeechRecognizer.ERROR_NO_MATCH -> "Ничего не распознано"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Сервис распознавания занят"
            SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Время ожидания речи истекло"
            else -> "Неизвестная ошибка распознавания"
        }
    }

    private fun setupButtonTouchListener() {
        btnSpeak.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (::tts.isInitialized && tts.isSpeaking) {
                        tts.stop()
                    }
                    speechRecognizer.startListening(speechRecognizerIntent)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    speechRecognizer.stopListening()
                    true
                }
                else -> false
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("ru"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(requireContext(), "Этот язык не поддерживается", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Ошибка инициализации синтезатора речи!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }
}