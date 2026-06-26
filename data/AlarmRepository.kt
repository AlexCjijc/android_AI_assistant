package com.example.speechrecognizer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.util.UUID
import org.json.JSONObject

class AlarmRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
    private val remindersKey = "reminders_list"
    private val lastIdKey = "last_reminder_id" // Ключ для хранения последнего выданного ID
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllReminders(): List<Reminder> {
        val jsonString = prefs.getString(remindersKey, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Reminder>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- УНИВЕРСАЛЬНЫЙ МЕТОД СОХРАНЕНИЯ ---
    fun saveOrUpdate(reminder: Reminder): Reminder {
        val reminders = getAllReminders().toMutableList()
        var finalReminder = reminder

        if (reminder.id == 0) {
            // Генерируем новый уникальный ID
            val nextId = prefs.getInt(lastIdKey, 0) + 1
            prefs.edit().putInt(lastIdKey, nextId).apply()

            finalReminder = reminder.copy(id = nextId)
            reminders.add(finalReminder)
        } else {
            // Обновляем существующий
            reminders.removeAll { it.id == reminder.id }
            reminders.add(reminder)
        }

        prefs.edit().putString(remindersKey, json.encodeToString(reminders)).commit()
        return finalReminder // Возвращаем объект с правильным ID
    }

    fun saveReminder(reminder: Reminder) {
        val reminders = getAllReminders().toMutableList()

        // Если ID пришел 0 (из диалога), генерируем новый
        val finalReminder = if (reminder.id == 0) {
            val nextId = (reminders.maxOfOrNull { it.id } ?: 0) + 1
            reminder.copy(id = nextId)
        } else {
            // Если ID уже есть (из голоса или редактирования), удаляем старый клон
            reminders.removeAll { it.id == reminder.id }
            reminder
        }

        reminders.add(finalReminder)
        prefs.edit().putString(remindersKey, json.encodeToString(reminders)).commit()
    }

    fun deleteReminder(reminderId: Int) {
        val reminders = getAllReminders().toMutableList()
        if (reminders.removeAll { it.id == reminderId }) {
            prefs.edit().putString(remindersKey, json.encodeToString(reminders)).commit()
        }
    }

    // Внутри класса AlarmRepository
    // В AlarmRepository.kt
    // В AlarmRepository.kt
    fun getAllChatThreads(): List<ChatThread> {
        val prefs = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("chat_threads", null) ?: return emptyList()

        val list = mutableListOf<ChatThread>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ChatThread(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    title = obj.optString("title", "Новый чат"),
                    lastMessage = obj.optString("lastMessage", "Нет сообщений"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    hasUnread = obj.optBoolean("hasUnread", false), // ДОБАВЛЕНО: читаем hasUnread
                    isSelected = obj.optBoolean("isSelected", false)
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("REPO_ERROR", "Ошибка чтения чатов: ${e.message}")
        }
        return list
    }

    fun saveChatThreads(threads: List<ChatThread>) {
        val array = org.json.JSONArray()
        threads.forEach { chat ->
            val obj = org.json.JSONObject().apply {
                put("id", chat.id)
                put("title", chat.title)
                put("lastMessage", chat.lastMessage)
                put("timestamp", chat.timestamp)
                put("hasUnread", chat.hasUnread) // ДОБАВЛЕНО: сохраняем hasUnread
                put("isSelected", chat.isSelected)
            }
            array.put(obj)
        }
        context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
            .edit().putString("chat_threads", array.toString()).apply()

        android.util.Log.d("AlarmRepository", "✅ Сохранено ${threads.size} чатов, непрочитанных: ${threads.count { it.hasUnread }}")
    }

    fun createNewChat(title: String): ChatThread {
        val threads = getAllChatThreads().toMutableList()
        val newChat = ChatThread(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            timestamp = System.currentTimeMillis(),
            hasUnread = false
        )
        threads.add(0, newChat)

        val array = org.json.JSONArray()
        threads.forEach { chat ->
            array.put(org.json.JSONObject().apply {
                put("id", chat.id)
                put("title", chat.title)
                put("lastMessage", chat.lastMessage)
                put("timestamp", chat.timestamp)
                put("hasUnread", chat.hasUnread) // ДОБАВЛЕНО
                put("isSelected", chat.isSelected)
            })
        }
        context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
            .edit().putString("chat_threads", array.toString()).commit()

        return newChat
    }

    fun markChatAsUnread(chatId: String) {
        android.util.Log.d("AlarmRepository", "markChatAsUnread: $chatId")
        val chats = getAllChatThreads().toMutableList()
        val index = chats.indexOfFirst { it.id == chatId }
        if (index != -1) {
            val oldValue = chats[index].hasUnread
            chats[index] = chats[index].copy(hasUnread = true)
            saveChatThreads(chats)
            android.util.Log.d("AlarmRepository", "✅ Чат помечен как непрочитанный (было: $oldValue)")

            // Проверяем сразу после сохранения
            val savedChats = getAllChatThreads()
            val savedChat = savedChats.find { it.id == chatId }
            android.util.Log.d("AlarmRepository", "Проверка: hasUnread=${savedChat?.hasUnread}")
        } else {
            android.util.Log.w("AlarmRepository", "❌ Чат не найден: $chatId")
        }
    }

    fun markChatAsRead(chatId: String) {
        val chats = getAllChatThreads().toMutableList()
        val index = chats.indexOfFirst { it.id == chatId }
        if (index != -1) {
            chats[index] = chats[index].copy(hasUnread = false)
            saveChatThreads(chats)
        }
    }

    fun updateChatPreview(chatId: String, lastMsg: String) {
        val threads = getAllChatThreads().toMutableList()
        val index = threads.indexOfFirst { it.id == chatId }

        if (index != -1) {
            // Сохраняем текущий hasUnread и обновляем остальное
            val currentHasUnread = threads[index].hasUnread
            threads[index] = threads[index].copy(
                lastMessage = lastMsg,
                timestamp = System.currentTimeMillis()
                // hasUnread остается прежним
            )
            threads.sortByDescending { it.timestamp }
            saveChatThreads(threads)
        }
    }
    fun getChatById(chatId: String): ChatThread? {
        return getAllChatThreads().find { it.id == chatId }
    }
    fun deleteChats(chats: List<ChatThread>) {
        val all = getAllChatThreads().toMutableList()
        val prefs = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val historyPrefs = context.getSharedPreferences("AiAssistantPrefs", Context.MODE_PRIVATE)

        chats.forEach { chat ->
            all.removeAll { it.id == chat.id }
            // Удаляем сам файл истории этого чата
            historyPrefs.edit().remove("chat_history_${chat.id}").apply()
        }
        saveChatThreads(all)
    }

    // В AlarmRepository.kt
    fun getLastActiveChat(): ChatThread? {
        return getAllChatThreads().maxByOrNull { it.timestamp }
    }


}

