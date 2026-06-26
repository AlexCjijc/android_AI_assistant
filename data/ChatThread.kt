package com.example.speechrecognizer.data

data class ChatThread(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String,
    var lastMessage: String = "Нет сообщений",
    var timestamp: Long = System.currentTimeMillis(),
    var hasUnread: Boolean = false,  // ВАЖНО: это поле должно быть
    var isSelected: Boolean = false // Для режима выделения
)