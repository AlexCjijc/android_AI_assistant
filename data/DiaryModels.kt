package com.example.speechrecognizer.data

import java.util.UUID

val periodId: String = PeriodManager.activePeriodId

// Модель оценки за обычное занятие
data class LessonGrade(
    val id: String = UUID.randomUUID().toString(),
    val periodId: String = PeriodManager.activePeriodId, // Ссылка на период
    val subject: String,
    val grade: String,
    val dateText: String,
    val comment: String = "",
    val timestamp: Long
)

data class ExamGrade(
    val id: String = UUID.randomUUID().toString(),
    val periodId: String = PeriodManager.activePeriodId, // Ссылка на период
    val subject: String,
    val grade: String,
    val type: String,
    val dateText: String,
    val comment: String = "",
    val timestamp: Long
)
