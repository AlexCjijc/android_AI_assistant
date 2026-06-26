package com.example.speechrecognizer.data

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale

data class ScienceNews(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val category: String,
    val dateText: String,
    val link: String,
    val imageUrl: String?
)

object NewsRepository {

    // ИСПРАВЛЕНО: Восстановлены полные боевые URL-адреса потоков, фид больше не будет пустым!
    val rssFeeds = mapOf(
        "НАУКА" to "https://elementy.ru/rss/news/russia",
        "ОБРАЗОВАНИЕ" to "https://rss.garant.ru/edu/relevant/main/",
        "ТЕХНОЛОГИИ" to "https://elementy.ru/rss/news/technology",
        "ИНФОРМАЦИОННЫЕ ТЕХНОЛОГИИ" to "https://elementy.ru/rss/news/it"
    )

    private val displayDateFormat = SimpleDateFormat("dd MMMM, HH:mm", Locale("ru"))

    // ИСПРАВЛЕНО: Категории переведены на мягкие, благородные пастельные HSV-цвета
    fun getCategoryColor(category: String): String {
        return when (category.uppercase()) {
            "ВСЕ" -> "#4C447A"                       // Фирменный фиолетовый
            "НАУКА" -> "#A3D9A5"                     // Пастельный нежно-зеленый
            "ОБРАЗОВАНИЕ" -> "#A2D2FF"                // Пастельный небесно-голубой
            "ТЕХНОЛОГИИ" -> "#FFD1A9"                // Пастельный персиково-оранжевый
            "ИНФОРМАЦИОННЫЕ ТЕХНОЛОГИИ" -> "#FFB5D4" // Пастельный нежно-розовый
            else -> "#4C447A"
        }
    }

    suspend fun parseNewsByCategory(categoryName: String, page: Int): List<ScienceNews> = coroutineScope {
        val pageSize = 4
        val skipCount = (page - 1) * pageSize

        val targets = if (categoryName == "ВСЕ") {
            rssFeeds.filter { it.key != "ВСЕ" }
        } else {
            rssFeeds.filter { it.key == categoryName }
        }

        val deferredNews = targets.map { (cat, urlString) ->
            async(Dispatchers.IO) {
                val list = mutableListOf<ScienceNews>()
                try {
                    val url = URI(urlString).toURL()
                    val input = SyndFeedInput()
                    val feed = input.build(XmlReader(url))

                    feed.entries.stream()
                        .skip(skipCount.toLong())
                        .limit(pageSize.toLong())
                        .forEach { entry ->
                            val formattedDate = entry.publishedDate?.let {
                                displayDateFormat.format(it)
                            } ?: "Недавнее"

                            val rawDesc = entry.description?.value ?: entry.title ?: ""
                            val cleanDesc = rawDesc.replace(Regex("<[^>]*>"), "")
                                .replace("&nbsp;", " ")
                                .trim()
                                .take(150) + "..."

                            // Извлечение картинки из тега enclosure
                            val imageUrl = if (entry.enclosures != null && entry.enclosures.isNotEmpty()) {
                                entry.enclosures[0].url
                            } else {
                                null
                            }

                            list.add(
                                ScienceNews(
                                    title = entry.title?.trim() ?: "Без заголовка",
                                    description = cleanDesc,
                                    category = cat,
                                    dateText = formattedDate,
                                    link = entry.link ?: "",
                                    imageUrl = imageUrl
                                )
                            )
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                list
            }
        }

        val result = deferredNews.awaitAll().flatten()
        if (categoryName == "ВСЕ") result.shuffled() else result
    }
}
