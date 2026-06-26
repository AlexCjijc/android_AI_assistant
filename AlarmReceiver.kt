package com.example.speechrecognizer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes // <-- НОВОЕ
import android.media.MediaPlayer
import android.media.RingtoneManager // <-- НОВОЕ
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: "Напоминание"
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", 0)

        val mediaPlayer = android.media.MediaPlayer().apply {
            setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM) // Поток будильника (игнорирует беззвучный режим)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isLooping = true // Зацикливаем, чтобы не прервалось раньше 10 секунд
            prepare()
            start()
        }

        // Останавливаем звук через 10 секунд
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 10000) // 10000 мс = 10 секунд


        // Показываем уведомление со звуком
        sendNotification(context, "Напоминание", message, notificationId)


    }

    private fun sendNotification(context: Context, title: String, message: String, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // <-- НОВОЕ: Получаем стандартный звук будильника
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Для Android 8.0+ требуется канал уведомлений. Настраиваем звук здесь.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // <-- НОВОЕ: Атрибуты звука для будильника
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                "critical_reminder_channel",
                "Важные напоминания",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Звучит даже в режиме Не беспокоить"
                setSound(alarmSound, audioAttributes)
                // Разрешаем обход режима DND
                setBypassDnd(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Интент, который откроет приложение при нажатии на уведомление
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Создаем уведомление
        val builder = NotificationCompat.Builder(context, "critical_reminder_channel")
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Указываем, что это будильник
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Замените на свою иконку, если есть
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Высокий приоритет для старых версий
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // <-- НОВОЕ: Устанавливаем звук напрямую для версий Android < 8.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(alarmSound)
        }

        // Показываем уведомление
        notificationManager.notify(notificationId, builder.build())
    }
}
