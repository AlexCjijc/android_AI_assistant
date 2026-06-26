package com.example.speechrecognizer.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.speechrecognizer.AlarmReceiver
import com.example.speechrecognizer.data.Reminder

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: Reminder) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_MESSAGE", reminder.message)
            putExtra("NOTIFICATION_ID", reminder.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (reminder.isRepeating) {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } else {
            // Проверка для Android 12 (API 31) и выше
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    // Если разрешения нет, используем обычный (не точный) метод
                    // Либо перенаправьте пользователя в настройки
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                // Для старых версий Android
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }


    fun cancel(reminderId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }


}
