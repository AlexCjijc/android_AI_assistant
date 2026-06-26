package com.example.speechrecognizer.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.core.view.isVisible
import com.example.speechrecognizer.MainActivity
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.AlarmRepository
import com.example.speechrecognizer.data.ChatThread
import androidx.activity.addCallback


class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatListAdapter
    private lateinit var repository: AlarmRepository
    private var isSelectionMode = false

    // BroadcastReceiver для обновления списка
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.speechrecognizer.UPDATE_ITEMS") {
                android.util.Log.d("ChatListFragment", "📡 Получен broadcast UPDATE_ITEMS")
                refreshChatList()
            }
        }
    }

    private var isReceiverRegistered = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = AlarmRepository(requireContext())
        recyclerView = view.findViewById(R.id.rvChatList)

        super.onViewCreated(view, savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val chats = repository.getAllChatThreads().toMutableList()

        adapter = ChatListAdapter(chats,
            { chat ->
                // При открытии чата снимаем флаг непрочитанных
                if (chat.hasUnread) {
                    android.util.Log.d("ChatListFragment", "Открытие непрочитанного чата: ${chat.title}")

                    // 1. Сначала обновляем репозиторий
                    repository.markChatAsRead(chat.id)

                    // 2. Обновляем объект в списке адаптера
                    chat.hasUnread = false

                    // 3. Обновляем отображение в адаптере
                    val position = adapter.items.indexOf(chat)
                    if (position != -1) {
                        adapter.notifyItemChanged(position)
                    }

                    // 4. Обновляем счетчик уведомлений
                    val unreadCount = adapter.items.count { it.hasUnread }
                    (activity as? MainActivity)?.setNotificationCount(unreadCount)

                    android.util.Log.d("ChatListFragment", "✅ Чат помечен как прочитанный, осталось непрочитанных: $unreadCount")
                }

                // Открываем чат
                (requireActivity() as MainActivity).openFragment(
                    ChatFragment.newInstance(chat.id),
                    R.id.navChats
                )
            },
            { pos ->
                if (pos != -1) enterSelectionMode(pos)
                else exitSelectionMode()
            },
            { chat -> showEditTitleDialog(chat) }
        )
        recyclerView.adapter = adapter

        // Drag & Drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (!adapter.isSelectionMode) return false

                val from = vh.adapterPosition
                val to = target.adapterPosition
                java.util.Collections.swap(adapter.items, from, to)
                adapter.notifyItemMoved(from, to)
                repository.saveChatThreads(adapter.items)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
            override fun isLongPressDragEnabled(): Boolean = adapter.isSelectionMode
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Кнопка создания чата
        view.findViewById<FloatingActionButton>(R.id.btnAddChat).setOnClickListener {
            val newChat = repository.createNewChat("Новый чат")
            (requireActivity() as MainActivity).openFragment(
                ChatFragment.newInstance(newChat.id),
                R.id.navChats
            )
        }

        // Обработка кнопки Назад
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (adapter.isSelectionMode) {
                exitSelectionMode()
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }
    }

    // Регистрируем receiver при показе фрагмента
    // В ChatListFragment.kt
    override fun onResume() {
        super.onResume()

        android.util.Log.d("ChatListFragment", "========== onResume ==========")

        // Регистрируем receiver
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter("com.example.speechrecognizer.UPDATE_ITEMS")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requireContext().registerReceiver(
                        updateReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    requireContext().registerReceiver(updateReceiver, filter)
                }
                isReceiverRegistered = true
                android.util.Log.d("ChatListFragment", "✅ Receiver зарегистрирован")
            } catch (e: Exception) {
                android.util.Log.e("ChatListFragment", "❌ Ошибка регистрации receiver: ${e.message}")
            }
        }

        // ВСЕГДА обновляем список при возвращении
        refreshChatList()

        // Обновляем счетчик уведомлений
        (activity as? MainActivity)?.let { mainActivity ->
            val unreadCount = repository.getAllChatThreads().count { it.hasUnread }
            mainActivity.setNotificationCount(unreadCount)
            android.util.Log.d("ChatListFragment", "Счетчик уведомлений: $unreadCount")
        }
    }

    private fun refreshChatList() {
        if (!::adapter.isInitialized) {
            android.util.Log.w("ChatListFragment", "⚠ Адаптер не инициализирован")
            return
        }

        android.util.Log.d("ChatListFragment", "========== refreshChatList ==========")

        // НАПРЯМУЮ читаем из репозитория
        val updatedChats = repository.getAllChatThreads()

        android.util.Log.d("ChatListFragment", "Всего чатов: ${updatedChats.size}")

        // Логируем КАЖДЫЙ чат
        updatedChats.forEachIndexed { index, chat ->
            android.util.Log.d("ChatListFragment", "  [$index] ${chat.title} | hasUnread=${chat.hasUnread} | lastMsg=${chat.lastMessage.take(40)}")
        }

        val unreadCount = updatedChats.count { it.hasUnread }
        android.util.Log.d("ChatListFragment", "Непрочитанных: $unreadCount")

        // Обновляем адаптер
        adapter.items.clear()
        adapter.items.addAll(updatedChats)
        adapter.notifyDataSetChanged()

        android.util.Log.d("ChatListFragment", "Адаптер обновлен, items=${adapter.items.size}")
    }

    // Отписываемся при скрытии фрагмента
    override fun onPause() {
        super.onPause()

        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(updateReceiver)
                isReceiverRegistered = false
                android.util.Log.d("ChatListFragment", "✅ Receiver отписан")
            } catch (e: Exception) {
                android.util.Log.e("ChatListFragment", "❌ Ошибка отписки receiver: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // На всякий случай отписываемся
        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(updateReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                // Уже отписан
            }
        }
    }



    private fun enterSelectionMode(pos: Int) {
        if (pos == -1) {
            exitSelectionMode()
            return
        }

        this.isSelectionMode = true
        adapter.isSelectionMode = true

        if (pos >= 0 && pos < adapter.items.size) {
            adapter.items[pos].isSelected = true
        }
        adapter.notifyDataSetChanged()

        val actions = requireActivity().findViewById<View>(R.id.llSelectionActions)
        actions?.visibility = View.VISIBLE

        requireActivity().findViewById<View>(R.id.btnCancelSelection)?.setOnClickListener {
            exitSelectionMode()
        }

        requireActivity().findViewById<View>(R.id.btnDeleteSelected)?.setOnClickListener {
            val selectedCount = adapter.items.count { it.isSelected }
            if (selectedCount == 0) return@setOnClickListener

            val messageText = "Выбрано чатов: $selectedCount. Все сообщения внутри них будут стерты навсегда."

            (requireActivity() as MainActivity).showConfirmDialog(
                title = "Удалить чаты?",
                message = messageText
            ) {
                val toDelete = adapter.items.filter { it.isSelected }
                repository.deleteChats(toDelete)
                adapter.items.removeAll(toDelete)
                adapter.notifyDataSetChanged()
                exitSelectionMode()
            }
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        adapter.isSelectionMode = false

        adapter.items.forEach { it.isSelected = false }
        adapter.notifyDataSetChanged()

        val actions = view?.findViewById<View>(R.id.llSelectionActions)
        actions?.visibility = View.GONE
    }

    private fun showEditTitleDialog(chat: ChatThread) {
        val dialog = android.app.Dialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_edit_title_dialog, null)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

        val etInput = view.findViewById<EditText>(R.id.etNewTitle)
        etInput.setText(chat.title)
        etInput.requestFocus()
        etInput.setSelection(chat.title.length)

        view.findViewById<View>(R.id.btnSaveTitle).setOnClickListener {
            val newTitle = etInput.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                chat.title = newTitle
                repository.saveChatThreads(adapter.items)
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
        }

        view.findViewById<View>(R.id.btnCancelEdit).setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        dialog.show()
    }
}