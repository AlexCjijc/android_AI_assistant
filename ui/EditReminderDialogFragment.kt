package com.example.speechrecognizer.ui

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.Reminder
import com.example.speechrecognizer.data.ReminderType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class EditReminderDialogFragment : DialogFragment() {

    interface OnReminderUpdatedListener {
        fun onReminderUpdated(updatedReminder: Reminder)
    }

    private var listener: OnReminderUpdatedListener? = null
    private lateinit var reminder: Reminder
    private val calendar: Calendar = Calendar.getInstance()
    private val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Проверяем родительский фрагмент на реализацию интерфейса
        listener = parentFragment as? OnReminderUpdatedListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Пытаемся достать данные. Если их нет (передали null), создаем новый объект
        val reminderJson = arguments?.getString(ARG_REMINDER)


        reminder = if (reminderJson != null) {
            Json.decodeFromString(reminderJson)
        } else {
            // Инициализация для НОВОГО напоминания
            Reminder(
                id = 0, // 0 обычно сигнализирует БД о необходимости автоинкремента
                type = ReminderType.REMINDER,
                message = "",
                triggerAtMillis = System.currentTimeMillis() + 60000 // По умолчанию через 1 минуту
            )
        }

        calendar.timeInMillis = reminder.triggerAtMillis

        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_edit_reminder, null)

        val tvTitle: TextView = view.findViewById(R.id.tvReminderDialogTitle)
        val etMessage: EditText = view.findViewById(R.id.etReminderMessage)
        val btnPickDate: Button = view.findViewById(R.id.btnPickDate)
        val btnPickTime: Button = view.findViewById(R.id.btnPickTime)
        val tvSelectedDateTime: TextView = view.findViewById(R.id.tvSelectedDateTime)
        val btnSave: Button = view.findViewById(R.id.btnSaveReminder)

        // Устанавливаем заголовок в зависимости от режима
        tvTitle.text = if (reminder.id == 0) "Новое напоминание" else "Редактировать напоминание"

        etMessage.setText(reminder.message)
        updateDateTimeLabel(tvSelectedDateTime)

        btnPickDate.setOnClickListener {
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                updateDateTimeLabel(tvSelectedDateTime)
            }, currentYear, currentMonth, currentDay).show()
        }

        btnPickTime.setOnClickListener {
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            TimePickerDialog(requireContext(), { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                updateDateTimeLabel(tvSelectedDateTime)
            }, currentHour, currentMinute, true).show()
        }

        btnSave.setOnClickListener {
            val updatedReminder = reminder.copy(
                message = etMessage.text.toString(),
                triggerAtMillis = calendar.timeInMillis
            )
            listener?.onReminderUpdated(updatedReminder)
            dismiss()
        }

        builder.setView(view)
        return builder.create()
    }

    private fun updateDateTimeLabel(tv: TextView) {
        val formattedDate = sdf.format(calendar.time)
        tv.text = "Выбрано: $formattedDate"
    }

    companion object {
        private const val ARG_REMINDER = "reminder_arg"

        // Метод теперь принимает nullable Reminder?
        fun newInstance(reminder: Reminder?): EditReminderDialogFragment {
            val fragment = EditReminderDialogFragment()
            val args = Bundle()
            // Сериализуем только если объект не null
            reminder?.let {
                args.putString(ARG_REMINDER, Json.encodeToString(it))
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onStart() {
        super.onStart()
        // Делаем фон диалога прозрачным, если используете закругленные углы в XML
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
