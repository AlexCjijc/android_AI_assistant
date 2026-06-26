package com.example.speechrecognizer.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.speechrecognizer.AlarmReceiver
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.AlarmRepository
import com.example.speechrecognizer.data.Reminder
import com.example.speechrecognizer.data.ReminderType
import com.example.speechrecognizer.utils.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment // ДОБАВИТЬ
import androidx.viewpager2.widget.ViewPager2
import com.example.speechrecognizer.ui.ScheduleFragment
import com.example.speechrecognizer.ui.DiaryFragment
import com.google.android.material.tabs.TabLayout


class SettingsFragment : Fragment(R.layout.fragment_settings),
    EditReminderDialogFragment.OnReminderUpdatedListener,
    EditTimerDialogFragment.OnTimerUpdatedListener {

    private var isFabMenuOpen = false
    private lateinit var repository: AlarmRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var contentReminders: LinearLayout

    private lateinit var updateReceiver: android.content.BroadcastReceiver

    private lateinit var contentTimers: LinearLayout

    private val timerUpdateHandler = Handler(Looper.getMainLooper())
    private lateinit var timerUpdateRunnable: Runnable
    private val activeTimerTextViews = mutableListOf<Pair<TextView, Long>>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AlarmRepository(requireContext())
        alarmScheduler = AlarmScheduler(requireContext())

        contentReminders = view.findViewById(R.id.contentReminders)
        contentTimers = view.findViewById(R.id.contentTimers)
        val headerReminders: LinearLayout = view.findViewById(R.id.headerReminders)
        val headerTimers: LinearLayout = view.findViewById(R.id.headerTimers)
        val arrowReminders: ImageView = view.findViewById(R.id.arrowReminders)
        val arrowTimers: ImageView = view.findViewById(R.id.arrowTimers)

        // Находим локальные FAB кнопки фрагмента настроек
        val fabMain: com.google.android.material.floatingactionbutton.FloatingActionButton = view.findViewById(R.id.fabMain)
        val fabReminder: View = view.findViewById(R.id.fabAddReminder)
        val fabTimer: View = view.findViewById(R.id.fabAddTimer)

        // БЕЗОПАСНЫЙ ПОИСК: Находим кнопки навигации (с защитой от null через знаки вопроса)
        val btnSchedule: View? = view.findViewById(R.id.btnNavigateSchedule)
        val btnDiary: View? = view.findViewById(R.id.btnNavigateDiary)

        // БЕЗОПАСНЫЙ ВЫЗОВ: Ищем микрофон в Activity. Если его нет, приложение НЕ упадет
        val mainMicBtn = requireActivity().findViewById<View>(R.id.btnSpeak)
        mainMicBtn?.bringToFront()

        // Автоматическое определение ID контейнера фрагментов
        val containerId = if (requireActivity().findViewById<View>(R.id.fragment_container) != null) {
            R.id.fragment_container
        } else {
            (view.parent as? View)?.id ?: View.NO_ID
        }


        btnSchedule?.setOnClickListener {
            if (containerId != View.NO_ID) {
                parentFragmentManager.beginTransaction()
                    .replace(containerId, ScheduleFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        btnDiary?.setOnClickListener {
            if (containerId != View.NO_ID) {
                parentFragmentManager.beginTransaction()
                    .replace(containerId, DiaryFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        // Инициализация BroadcastReceiver
        updateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                loadAndDisplayItems()
            }
        }

        val filter = android.content.IntentFilter("com.example.speechrecognizer.UPDATE_ITEMS")
        val listenFlags = ContextCompat.RECEIVER_NOT_EXPORTED
        ContextCompat.registerReceiver(requireContext(), updateReceiver, filter, listenFlags)

        fabMain.setOnClickListener { toggleFabMenu(fabMain, fabReminder, fabTimer) }

        fabReminder.setOnClickListener {
            toggleFabMenu(fabMain, fabReminder, fabTimer)
            EditReminderDialogFragment.newInstance(null).show(childFragmentManager, "CreateReminder")
        }

        fabTimer.setOnClickListener {
            toggleFabMenu(fabMain, fabReminder, fabTimer)
            EditTimerDialogFragment.newInstance(null).show(childFragmentManager, "CreateTimer")
        }

        headerReminders.setOnClickListener {
            contentReminders.isVisible = !contentReminders.isVisible
            val rotationValue = if (contentReminders.isVisible) 180f else 0f
            arrowReminders.animate().rotation(rotationValue).setDuration(150).start()
        }

        headerTimers.setOnClickListener {
            contentTimers.isVisible = !contentTimers.isVisible
            val rotationValue = if (contentTimers.isVisible) 180f else 0f
            arrowTimers.animate().rotation(rotationValue).setDuration(150).start()
        }

        setupTimerUpdater()
        loadAndDisplayItems()
    }



    private fun setupTimerUpdater() {
        timerUpdateRunnable = Runnable {
            val now = System.currentTimeMillis()
            val iterator = activeTimerTextViews.iterator()
            while (iterator.hasNext()) {
                val (textView, triggerTime) = iterator.next()
                val millisRemaining = triggerTime - now
                if (millisRemaining > 0) {
                    textView.text = "Осталось: ${formatMillisToCountdown(millisRemaining)}"
                } else {
                    textView.text = "Завершен"
                    iterator.remove()
                }
            }
            timerUpdateHandler.postDelayed(timerUpdateRunnable, 1000)
        }
    }

    private fun loadAndDisplayItems() {
        contentReminders.removeAllViews()
        contentTimers.removeAllViews()
        activeTimerTextViews.clear()

        val allItems = repository.getAllReminders()

        val reminders = allItems.filter { it.type == ReminderType.REMINDER }
        populateList(contentReminders, reminders, "Активных напоминаний нет.")

        val timers = allItems.filter { it.type == ReminderType.TIMER }
        populateList(contentTimers, timers, "Активных таймеров нет.")
    }

    private fun populateList(container: LinearLayout, items: List<Reminder>, emptyMessage: String) {
        val inflater = LayoutInflater.from(requireContext())
        if (items.isEmpty()) {
            val noItemsView = TextView(requireContext()).apply { text = emptyMessage; setTextColor(resources.getColor(android.R.color.darker_gray, null)) }
            container.addView(noItemsView)
            return
        }

        items.sortedBy { it.triggerAtMillis }.forEach { item ->
            val itemView = inflater.inflate(R.layout.item_reminder_management, container, false)
            val messageText: TextView = itemView.findViewById(R.id.tvReminderMessage)
            val timeText: TextView = itemView.findViewById(R.id.tvReminderTime)
            val editButton: Button = itemView.findViewById(R.id.btnEditReminder)
            val deleteButton: Button = itemView.findViewById(R.id.btnDeleteReminder)

            if (item.type == ReminderType.TIMER && item.durationMillis != null) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(item.durationMillis)
                messageText.text = "Таймер на $minutes мин."
                activeTimerTextViews.add(Pair(timeText, item.triggerAtMillis))
            } else {
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                messageText.text = item.message
                timeText.text = "Сработает: ${sdf.format(Date(item.triggerAtMillis))}"
            }

            deleteButton.setOnClickListener {
                alarmScheduler.cancel(item.id)
                repository.deleteReminder(item.id)
                loadAndDisplayItems()
            }

            editButton.setOnClickListener {
                if (item.type == ReminderType.REMINDER) {
                    // ИСПРАВЛЕНО: Используем childFragmentManager
                    EditReminderDialogFragment.newInstance(item).show(childFragmentManager, "EditReminder")
                } else {
                    // ИСПРАВЛЕНО: Используем childFragmentManager
                    EditTimerDialogFragment.newInstance(item).show(childFragmentManager, "EditTimer")
                }
            }

            container.addView(itemView)
        }
    }

    // --- РЕАЛИЗАЦИЯ ИНТЕРФЕЙСОВ ДЛЯ РЕДАКТИРОВАНИЯ ---

    // --- РЕАЛИЗАЦИЯ ИНТЕРФЕЙСОВ ДЛЯ СОЗДАНИЯ И РЕДАКТИРОВАНИЯ ---

    override fun onReminderUpdated(updatedReminder: Reminder) {
        // Сохраняем в репозиторий и получаем объект с присвоенным ID (если он был 0)
        val savedReminder = repository.saveOrUpdate(updatedReminder)

        // Перепланируем будильник с правильным ID
        alarmScheduler.cancel(updatedReminder.id) // На случай, если это было редактирование
        alarmScheduler.schedule(savedReminder)

        loadAndDisplayItems()

        val msg = if (updatedReminder.id == 0) "Напоминание создано!" else "Напоминание обновлено!"
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onTimerUpdated(updatedTimer: Reminder) {
        val savedTimer = repository.saveOrUpdate(updatedTimer)

        alarmScheduler.cancel(updatedTimer.id)
        alarmScheduler.schedule(savedTimer)

        loadAndDisplayItems()

        val msg = if (updatedTimer.id == 0) "Таймер запущен!" else "Таймер обновлен!"
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }



    // --- Управление жизненным циклом обратного отсчета ---
    override fun onResume() {
        super.onResume()
        // Начинаем обновление таймеров, если они есть
        timerUpdateHandler.post(timerUpdateRunnable)
        // Перезагружаем элементы, чтобы убедиться, что они актуальны
        loadAndDisplayItems()
    }

    override fun onPause() {
        super.onPause()
        timerUpdateHandler.removeCallbacks(timerUpdateRunnable)
    }

    // --- Вспомогательные функции (без изменений) ---
    private fun formatMillisToCountdown(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun toggleFabMenu(main: View, reminder: View, timer: View) {
        if (!isFabMenuOpen) {
            // Показываем подпункты
            reminder.visibility = View.VISIBLE
            timer.visibility = View.VISIBLE

            // Анимируем появление (опционально)
            reminder.alpha = 0f
            reminder.animate().alpha(1f).translationY(-20f).setDuration(200).start()
            timer.alpha = 0f
            timer.animate().alpha(1f).translationY(-20f).setDuration(200).start()

            // Поворачиваем плюс
            main.animate().rotation(135f).setDuration(200).start()
        } else {
            // Прячем подпункты
            reminder.visibility = View.GONE
            timer.visibility = View.GONE

            // Возвращаем плюс в исходное состояние
            main.animate().rotation(0f).setDuration(200).start()
        }
        isFabMenuOpen = !isFabMenuOpen
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(updateReceiver)
    }


}
