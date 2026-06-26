package com.example.speechrecognizer.data

import kotlinx.serialization.Serializable

// НОВОЕ: Enum для различения типов
enum class ReminderType {
    REMINDER, TIMER
}

@Serializable
data class Reminder(
    val id: Int,
    val triggerAtMillis: Long,
    val message: String,
    val type: ReminderType,
    val durationMillis: Long? = null, // Для хранения исходной длительности таймера
    val isRepeating: Boolean = false
)
