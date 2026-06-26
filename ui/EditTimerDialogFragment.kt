package com.example.speechrecognizer.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.Reminder
import com.example.speechrecognizer.data.ReminderType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class EditTimerDialogFragment : DialogFragment() {

    interface OnTimerUpdatedListener {
        fun onTimerUpdated(updatedTimer: Reminder)
    }

    private var listener: OnTimerUpdatedListener? = null
    private lateinit var timer: Reminder

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Проверяем родительский фрагмент на реализацию интерфейса
        listener = parentFragment as? OnTimerUpdatedListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Достаем данные. Если их нет (null в newInstance) — создаем новый Reminder с типом TIMER
        val reminderJson = arguments?.getString(ARG_TIMER)

        timer = if (reminderJson != null) {
            Json.decodeFromString<Reminder>(reminderJson)
        } else {
            // Инициализация для НОВОГО таймера
            val defaultDuration = TimeUnit.MINUTES.toMillis(5) // По умолчанию 5 минут
            Reminder(
                id = 0,
                type = ReminderType.TIMER,
                message = "Таймер на 5 мин.",
                triggerAtMillis = System.currentTimeMillis() + defaultDuration,
                durationMillis = defaultDuration
            )
        }


        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_edit_timer, null)
        val tvTitle: TextView = view.findViewById(R.id.tvTimerDialogTitle)

        val numberPicker: NumberPicker = view.findViewById(R.id.numberPickerMinutes)
        val btnSave: Button = view.findViewById(R.id.btnSaveTimer)

        // Настройка заголовка диалога
        tvTitle.text = if (timer.id == 0) "Новый таймер" else "Редактировать таймер"

        // Настройка NumberPicker
        numberPicker.minValue = 1
        numberPicker.maxValue = 180 // Максимум 3 часа

        // Устанавливаем текущее значение из объекта
        val currentMinutes = TimeUnit.MILLISECONDS.toMinutes(timer.durationMillis ?: 60000).toInt()
        numberPicker.value = if (currentMinutes < 1) 1 else currentMinutes

        btnSave.setOnClickListener {
            val newMinutes = numberPicker.value
            val newDurationMillis = TimeUnit.MINUTES.toMillis(newMinutes.toLong())

            // Копируем объект: обновляем время срабатывания от текущего момента
            val updatedTimer = timer.copy(
                durationMillis = newDurationMillis,
                triggerAtMillis = System.currentTimeMillis() + newDurationMillis,
                message = "Таймер на $newMinutes мин."
            )

            listener?.onTimerUpdated(updatedTimer)
            dismiss()
        }

        builder.setView(view)
        return builder.create()
    }

    companion object {
        private const val ARG_TIMER = "timer_arg"

        // Метод теперь принимает nullable Reminder?
        fun newInstance(timer: Reminder?): EditTimerDialogFragment {
            val fragment = EditTimerDialogFragment()
            val args = Bundle()
            // Кладем строку в аргументы только если объект не null
            timer?.let {
                args.putString(ARG_TIMER, Json.encodeToString(it))
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onStart() {
        super.onStart()
        // Делаем фон прозрачным для поддержки закругленных углов в XML
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
