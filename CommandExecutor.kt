package com.example.speechrecognizer

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.speechrecognizer.data.AlarmRepository
import com.example.speechrecognizer.data.Reminder
import com.example.speechrecognizer.data.ReminderType
import com.example.speechrecognizer.utils.AlarmScheduler
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object CommandExecutor {

    private const val TAG = "COMMAND_EXECUTOR"
    private const val PREFS_NAME = "AiAssistantPrefs"

    fun handleTimer(amount: Long, unit: String, appContext: Context, chatId: String): String {
        Log.d(TAG, "==========================================")
        Log.d(TAG, "handleTimer START: amount=$amount, unit=$unit")

        val durationMillis = amount * when {
            unit.startsWith("минут") -> {
                Log.d(TAG, "Определены минуты")
                60 * 1000
            }
            unit.startsWith("час") -> {
                Log.d(TAG, "Определены часы")
                60 * 60 * 1000
            }
            unit.startsWith("секунд") -> {
                Log.d(TAG, "Определены секунды")
                1000
            }
            else -> {
                Log.e(TAG, "Неизвестная единица: $unit")
                0
            }
        }

        val response = if (durationMillis == 0L) {
            "Не поняла единицы времени."
        } else {
            "Таймер на $amount $unit запущен."
        }

        if (durationMillis > 0L) {
            try {
                val reminder = Reminder(
                    id = abs(UUID.randomUUID().hashCode()),
                    triggerAtMillis = System.currentTimeMillis() + durationMillis,
                    message = "Таймер на $amount $unit",
                    type = ReminderType.TIMER,
                    durationMillis = durationMillis
                )

                Log.d(TAG, "Создан Reminder: id=${reminder.id}, triggerAt=${Date(reminder.triggerAtMillis)}")

                val scheduler = AlarmScheduler(appContext)
                Log.d(TAG, "Вызов scheduler.schedule()...")
                scheduler.schedule(reminder)
                Log.d(TAG, "✅ scheduler.schedule() выполнен")

                val repository = AlarmRepository(appContext)
                Log.d(TAG, "Сохранение в репозиторий...")
                repository.saveReminder(reminder)
                Log.d(TAG, "✅ Сохранено в репозиторий")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка создания таймера: ${e.message}", e)
            }
        }

        saveBackgroundResponse(response, appContext, chatId)
        Log.d(TAG, "handleTimer END: $response")
        return response
    }

    fun handleReminder(task: String, time: String, dateStr: String?, appContext: Context, chatId: String): String {
        Log.d(TAG, "==========================================")
        Log.d(TAG, "handleReminder START")
        Log.d(TAG, "Параметры: task='$task', time='$time', date='$dateStr'")

        val calendar = Calendar.getInstance()
        val finalDateStr = if (!dateStr.isNullOrEmpty()) dateStr else
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

        Log.d(TAG, "Финальная дата: $finalDateStr")

        return try {
            // Парсим дату
            val dateParts = finalDateStr.split(".")
            if (dateParts.size == 3) {
                calendar.set(Calendar.YEAR, dateParts[2].toInt())
                calendar.set(Calendar.MONTH, dateParts[1].toInt() - 1)
                calendar.set(Calendar.DAY_OF_MONTH, dateParts[0].toInt())
                Log.d(TAG, "Дата установлена: ${dateParts[0]}.${dateParts[1]}.${dateParts[2]}")
            } else {
                Log.e(TAG, "Неверный формат даты: $finalDateStr")
                return "Неверный формат даты: $finalDateStr"
            }

            // Парсим время
            val timeParts = time.split(":")
            if (timeParts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                calendar.set(Calendar.MINUTE, timeParts[1].toInt())
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Log.d(TAG, "Время установлено: ${timeParts[0]}:${timeParts[1]}")
            } else {
                Log.e(TAG, "Неверный формат времени: $time")
                return "Неверный формат времени: $time"
            }

            // Проверяем, не прошло ли время
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                Log.d(TAG, "Время уже прошло, добавляем 1 день")
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            val triggerTime = calendar.timeInMillis
            Log.d(TAG, "Время срабатывания: ${Date(triggerTime)} (${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(triggerTime))})")

            // Создаем напоминание
            val reminder = Reminder(
                id = abs(UUID.randomUUID().hashCode()),
                triggerAtMillis = triggerTime,
                message = task,
                type = ReminderType.REMINDER
            )

            Log.d(TAG, "Создан Reminder: id=${reminder.id}")

            // Планируем в системе
            try {
                val scheduler = AlarmScheduler(appContext)
                Log.d(TAG, "Вызов AlarmScheduler.schedule()...")
                scheduler.schedule(reminder)
                Log.d(TAG, "✅ AlarmScheduler.schedule() выполнен успешно")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка AlarmScheduler: ${e.message}", e)
                throw e
            }

            // Сохраняем в базу
            try {
                val repository = AlarmRepository(appContext)
                Log.d(TAG, "Сохранение в AlarmRepository...")
                repository.saveReminder(reminder)
                Log.d(TAG, "✅ Сохранено в AlarmRepository")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка AlarmRepository: ${e.message}", e)
                // Пробуем отменить уже запланированный alarm
                try {
                    AlarmScheduler(appContext).cancel(reminder.id)
                } catch (cancelEx: Exception) {
                    Log.e(TAG, "Не удалось отменить alarm: ${cancelEx.message}")
                }
                throw e
            }

            val responseDate = SimpleDateFormat("d MMMM 'в' HH:mm", Locale("ru")).format(calendar.time)
            val response = "Окей, я напомню: $task $responseDate."

            Log.d(TAG, "✅ Напоминание успешно создано")
            saveBackgroundResponse(response, appContext, chatId)
            Log.d(TAG, "handleReminder END: $response")
            response

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка в handleReminder: ${e.message}", e)
            val response = "Не удалось создать напоминание: ${e.message}"
            saveBackgroundResponse(response, appContext, chatId)
            response
        }
    }

    // В CommandExecutor.kt, метод saveBackgroundResponse
    // В CommandExecutor.kt
    private fun saveBackgroundResponse(text: String, context: Context, chatId: String) {
        Log.d(TAG, "saveBackgroundResponse: '$text'")

        GlobalScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                Log.d(TAG, "Сохранение в SharedPreferences...")
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val historyStr = prefs.getString("chat_history_$chatId", null)
                val currentLocalHistory = if (!historyStr.isNullOrEmpty()) JSONArray(historyStr) else JSONArray()

                val messageObj = JSONObject().apply {
                    put("role", "assistant")
                    put("content", text)
                    put("timestamp", System.currentTimeMillis())
                }
                currentLocalHistory.put(messageObj)

                prefs.edit().putString("chat_history_$chatId", currentLocalHistory.toString()).apply()
                Log.d(TAG, "✅ SharedPreferences обновлены")

                // Обновляем превью чата и помечаем как непрочитанный
                val repository = AlarmRepository(context)
                repository.updateChatPreview(chatId, text)
                repository.markChatAsUnread(chatId)

                Log.d(TAG, "✅ Превью обновлено, чат помечен как непрочитанный")

                // Отправляем уведомление
                val chatInfo = repository.getChatById(chatId)
                if (chatInfo != null) {
                    try {
                        NotificationHelper.showNewMessageNotification(
                            context = context,
                            chatId = chatId,
                            chatTitle = chatInfo.title,
                            message = text
                        )
                        Log.d(TAG, "✅ Уведомление отправлено")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Ошибка отправки уведомления: ${e.message}", e)
                    }
                } else {
                    Log.w(TAG, "⚠ Чат не найден для уведомления: $chatId")
                }

                // Отправляем broadcast для обновления UI
                try {
                    val updateIntent = Intent("com.example.speechrecognizer.UPDATE_ITEMS").apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(updateIntent)
                    Log.d(TAG, "✅ Broadcast отправлен")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠ Ошибка отправки broadcast: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка сохранения: ${e.message}", e)
            }
        }
    }
}