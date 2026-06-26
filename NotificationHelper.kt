// Новый файл: NotificationHelper.kt
package com.example.speechrecognizer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID = "ai_messages_channel"
    private const val CHANNEL_NAME = "Сообщения от AI"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых ответах ассистента"
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNewMessageNotification(
        context: Context,
        chatId: String,
        chatTitle: String,
        message: String
    ) {
        try {
            android.util.Log.d("NotificationHelper", "=== Отправка уведомления ===")
            android.util.Log.d("NotificationHelper", "chatId: $chatId, title: $chatTitle")
            android.util.Log.d("NotificationHelper", "message: ${message.take(50)}...")

            val notificationId = chatId.hashCode()

            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("OPEN_CHAT_ID", chatId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val shortMessage = if (message.length > 100) {
                message.substring(0, 97) + "..."
            } else {
                message
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Ответ от AI: $chatTitle")
                .setContentText(shortMessage)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 300, 200, 300))
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)

            android.util.Log.d("NotificationHelper", "✅ Уведомление отправлено (id: $notificationId)")

        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "❌ Ошибка отправки уведомления: ${e.message}", e)
        }
    }
}