package com.example.speechrecognizer.data

import java.util.UUID

data class Subject(
    val id: String = UUID.randomUUID().toString(),
    val periodId: String,  // Привязка к семестру/четверти
    val name: String,      // Название предмета (например, "Физика")
    val topic: String = "" // Тема или дополнительная информация
)
