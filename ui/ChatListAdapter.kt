package com.example.speechrecognizer.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.ChatThread
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatListAdapter(
    val items: MutableList<ChatThread>,
    private val onChatClick: (ChatThread) -> Unit,
    private val onLongClick: (Int) -> Unit,
    private val onEditClick: (ChatThread) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    var isSelectionMode = false

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvChatTitle)
        val tvLastMsg: TextView = view.findViewById(R.id.tvChatLastMsg)
        val tvTime: TextView = view.findViewById(R.id.tvChatTime)
        val ivCheck: ImageView = view.findViewById(R.id.ivSelectionCircle)
        val btnEdit: ImageView = view.findViewById(R.id.btnEditChat)
        val card: CardView = view.findViewById(R.id.chatCard)
        val indicatorUnread: View = view.findViewById(R.id.indicatorUnread)

        private val density = view.context.resources.displayMetrics.density
        private val cornerRadiusPx = 20f * density
        private val strokeWidthPx = (3f * density).toInt()

        init {
            // Программно создаем красную точку для индикатора
            indicatorUnread.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFF4B4B.toInt())
            }

            // Инициализация базовых параметров CardView
            card.apply {
                cardElevation = 3f * density
                radius = cornerRadiusPx
                setCardBackgroundColor(0xFF252041.toInt())
                preventCornerOverlap = false
                useCompatPadding = false
            }

            // Слушатель обычного клика по элементу
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val chat = items[pos]

                if (isSelectionMode) {
                    chat.isSelected = !chat.isSelected
                    notifyItemChanged(pos)
                    val anySelected = items.any { it.isSelected }
                    if (!anySelected) {
                        onLongClick(-1) // Сигнал для выхода из режима выделения
                    }
                } else {
                    if (chat.hasUnread) {
                        chat.hasUnread = false
                        notifyItemChanged(pos)
                    }
                    onChatClick(chat)
                }
            }

            // Слушатель долгого нажатия
            itemView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && !isSelectionMode) {
                    onLongClick(pos)
                    itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                true
            }

            // Слушатель клика по кнопке карандаша
            btnEdit.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEditClick(items[pos])
                }
            }
        }

        fun bind(chat: ChatThread) {
            tvTitle.text = chat.title
            tvLastMsg.text = chat.lastMessage.ifEmpty { "Нет сообщений" }

            // Форматирование времени
            val chatTime = chat.timestamp
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val fullDateFormat = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())

            tvTime.text = when {
                android.text.format.DateUtils.isToday(chatTime) -> "Сегодня ${timeFormat.format(Date(chatTime))}"
                isYesterday(chatTime) -> "Вчера ${timeFormat.format(Date(chatTime))}"
                else -> fullDateFormat.format(Date(chatTime))
            }

            // Оформление прочитанного/непрочитанного состояния
            if (chat.hasUnread) {
                indicatorUnread.visibility = View.VISIBLE
                tvTitle.setTextColor(0xFFFFFFFF.toInt())
                tvTitle.setTypeface(tvTitle.typeface, Typeface.BOLD)
                tvLastMsg.setTextColor(0xFFD0D0D0.toInt())
                card.setCardBackgroundColor(0xFF2D1F5E.toInt())
            } else {
                indicatorUnread.visibility = View.GONE
                tvTitle.setTextColor(0xFFCCCCCC.toInt())
                tvTitle.setTypeface(tvTitle.typeface, Typeface.NORMAL)
                tvLastMsg.setTextColor(0xFF999999.toInt())
                card.setCardBackgroundColor(0xFF252041.toInt())
            }

            // Логика выделения (актуальное состояние элемента)
            val isSelected = isSelectionMode && chat.isSelected

            // Галочка и карандаш появляются одновременно только при выделении чата
            ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            btnEdit.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Настройка обводки и прозрачности для выбранной карточки
            if (isSelected) {
                card.foreground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setStroke(strokeWidthPx, 0xFF7C4DFF.toInt())
                    cornerRadius = cornerRadiusPx
                }
                card.cardElevation = 6f * density
                card.alpha = 0.95f
            } else {
                card.foreground = null
                card.cardElevation = 3f * density
                card.alpha = 1.0f
            }
        }
    }

    private fun isYesterday(time: Long): Boolean {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterdayMidnight = midnight - android.text.format.DateUtils.DAY_IN_MILLIS
        return time in yesterdayMidnight until midnight
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_thread, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ChatThread>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun markAsRead(chatId: String) {
        val index = items.indexOfFirst { it.id == chatId }
        if (index != -1) {
            items[index].hasUnread = false
            notifyItemChanged(index)
        }
    }

    fun markAsUnread(chatId: String) {
        val index = items.indexOfFirst { it.id == chatId }
        if (index != -1) {
            items[index].hasUnread = true
            notifyItemChanged(index)
        }
    }

    fun getUnreadCount(): Int = items.count { it.hasUnread }
}
