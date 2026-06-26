package com.example.speechrecognizer.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // ВСТАВЛЕНО
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.ScienceNews

class NewsAdapter(
    private var items: List<ScienceNews> = emptyList()
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    fun updateData(newItems: List<ScienceNews>) { this.items = newItems; notifyDataSetChanged() }
    fun appendData(newItems: List<ScienceNews>) {
        val start = items.size
        val list = items.toMutableList().apply { addAll(newItems) }
        this.items = list
        notifyItemRangeInserted(start, newItems.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_science_news, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    class NewsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvCategory: TextView = view.findViewById(R.id.tvNewsCategory)
        private val tvDate: TextView = view.findViewById(R.id.tvNewsDate)
        private val tvTitle: TextView = view.findViewById(R.id.tvNewsTitle)
        private val tvDescription: TextView = view.findViewById(R.id.tvNewsDescription)
        private val ivCover: ImageView = view.findViewById(R.id.ivNewsCover)
        private val cvImageContainer: View = view.findViewById(R.id.cvNewsImageContainer)

        fun bind(news: ScienceNews) {
            tvCategory.text = news.category
            tvDate.text = news.dateText
            tvTitle.text = news.title
            tvDescription.text = news.description

            // Асинхронно загружаем изображение обложки через Glide (ВСТАВЛЕНО)
            if (!news.imageUrl.isNullOrEmpty()) {
                cvImageContainer.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(news.imageUrl)
                    .centerCrop()
                    .into(ivCover)
            } else {
                cvImageContainer.visibility = View.GONE // Сворачиваем контейнер, если картинки нет
            }

            val categoryColor = when (news.category) {
                "ВСЕ" -> "#4C447A"                       // Фирменный фиолетовый
                "НАУКА" -> "#A3D9A5"                     // Пастельный нежно-зеленый
                "ОБРАЗОВАНИЕ" -> "#A2D2FF"                // Пастельный небесно-голубой
                "ТЕХНОЛОГИИ" -> "#FFD1A9"                // Пастельный персиково-оранжевый
                "ИНФОРМАЦИОННЫЕ ТЕХНОЛОГИИ" -> "#FFB5D4" // Пастельный нежно-розовый
                else -> "#4C447A"
            }
            tvCategory.backgroundTintList = ColorStateList.valueOf(Color.parseColor(categoryColor))

            itemView.setOnClickListener {
                if (news.link.isNotEmpty()) {
                    itemView.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(news.link)))
                }
            }
        }
    }
}
