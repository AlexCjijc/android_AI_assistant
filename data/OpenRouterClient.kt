package com.example.speechrecognizer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object OpenRouterClient {

    private val client = OkHttpClient()
    private const val API_KEY = "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"

    // Переводим функцию на работу через корутины suspend fun с возвратом строки результата
    suspend fun fetchProfileAnalysis(modelId: String, promptContent: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Строго формируем JSON-тело по стандартам OpenRouter
                val rootJson = JSONObject().apply {
                    put("model", modelId)

                    val messagesArray = JSONArray()
                    val apiMsg = JSONObject().apply {
                        put("role", "user")
                        put("content", promptContent)
                    }
                    messagesArray.put(apiMsg)
                    put("messages", messagesArray)
                }

                // Заголовки и эндпоинт полностью из вашей рабочей версии
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("HTTP-Referer", "https://localhost")
                    .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""

                    // Защита от HTML ответов провайдеров
                    if (bodyString.trim().startsWith("<!DOCTYPE")) {
                        return@withContext "Ошибка: Сервер вернул некорректный ответ HTML. Попробуйте обновить позже."
                    }

                    if (response.isSuccessful) {
                        val content = JSONObject(bodyString)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")

                        // Очищаем markdown-разметку (решетки, звездочки) как в вашем коде
                        content.replace(Regex("[#*]"), "").trim()
                    } else {
                        "Ошибка сервера аналитики: ${response.code}"
                    }
                }
            } catch (e: Exception) {
                "Ошибка сети: ${e.localizedMessage ?: "проверьте интернет-соединение"}"
            }
        }
    }
}
