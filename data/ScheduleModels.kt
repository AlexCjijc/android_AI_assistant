package com.example.speechrecognizer.data

import java.util.UUID

enum class WeekType { CHISLITEL, ZNAMENATEL, BOTH }

data class Lesson(
    val id: String = UUID.randomUUID().toString(),
    val periodId: String = PeriodManager.activePeriodId,
    val dayOfWeek: String,
    val time: String,
    val subject: String,
    val weekType: WeekType = WeekType.BOTH,
    val period: String = "",
    val isDateMode: Boolean = false // ВСТАВЛЕНО: маркер режима создания занятия
)

data class Exam(
    val id: String = UUID.randomUUID().toString(),
    val periodId: String = PeriodManager.activePeriodId,
    val date: String,
    val dayOfWeek: String,
    val time: String,
    val details: String
)
