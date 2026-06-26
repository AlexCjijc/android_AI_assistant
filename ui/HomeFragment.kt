package com.example.speechrecognizer.ui

import android.R.attr.paddingHorizontal
import android.R.attr.paddingVertical
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.NewsRepository
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var newsAdapter: NewsAdapter
    private lateinit var gridCategories: GridLayout
    private lateinit var tvErrorPlaceholder: TextView
    private lateinit var rvNews: RecyclerView

    private var currentCategory = "ВСЕ" // Стартуем со "ВСЕ" по умолчанию
    private var currentPage = 1
    private var isLoadingPage = false
    private var canLoadMore = true

    // Список кнопок для динамического переключения подсветки активной вкладки
    private val categoryButtons = mutableMapOf<String, TextView>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gridCategories = view.findViewById(R.id.gridNewsCategories)
        tvErrorPlaceholder = view.findViewById(R.id.tvNewsErrorPlaceholder)
        rvNews = view.findViewById(R.id.rvScienceNewsList)
        val mainScrollView: NestedScrollView = view.findViewById(R.id.homeScrollView)

        rvNews.layoutManager = LinearLayoutManager(requireContext())
        newsAdapter = NewsAdapter()
        rvNews.adapter = newsAdapter

        renderTwoRowCategoryGrid()

        mainScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            val totalHeight = v.getChildAt(0).bottom
            val currentScrollPos = v.height + scrollY
            if (totalHeight - currentScrollPos < 500 && !isLoadingPage && canLoadMore) {
                currentPage++
                loadCategoryNews(currentCategory, currentPage)
            }
        })

        switchCategory("ВСЕ")
    }

    // Рендеринг двухрядной сетки табов с индивидуальной пастельной окраской
    private fun renderTwoRowCategoryGrid() {
        if (!::gridCategories.isInitialized) return

        gridCategories.removeAllViews()
        categoryButtons.clear()

        val displayCategories = NewsRepository.rssFeeds.keys.filter { it != "ВСЕ" }
        val density = resources.displayMetrics.density

        displayCategories.forEachIndexed { index, catName ->
            val tvTab = TextView(requireContext())
            tvTab.text = catName
            tvTab.textSize = 11f // Чуть уменьшили шрифт, чтобы длинный текст "ИНФОРМАЦИОННЫЕ..." гарантированно влезал
                    tvTab.gravity = android.view.Gravity.CENTER

            val pVertical = (10 * density).toInt()
            val pHorizontal = (6 * density).toInt()
            tvTab.setPadding(pHorizontal, pVertical, pHorizontal, pVertical)

            tvTab.background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_rounded)
            val colorHex = NewsRepository.getCategoryColor(catName)
            tvTab.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorHex))

            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec(index % 2, 1f)
            params.rowSpec = GridLayout.spec(index / 2, 1f)

            val marginPx = (5 * density).toInt()
            params.setMargins(marginPx, marginPx, marginPx, marginPx)
            tvTab.layoutParams = params

            tvTab.setOnClickListener { switchCategory(catName) }

            gridCategories.addView(tvTab)
            categoryButtons[catName] = tvTab
        }
        updateActiveTabHighlight()
    }

    private fun updateActiveTabHighlight() {
        categoryButtons.forEach { (name, tv) ->
            // Если выбран конкретный таб — подсвечиваем только его.
            // Если активен режим "ВСЕ" — все табы становятся активными на 100% (сброс фильтра)
            if (name == currentCategory || currentCategory == "ВСЕ") {
                tv.alpha = 1.0f
                tv.scaleX = 1.0f; tv.scaleY = 1.0f
                tv.setTextColor(Color.parseColor("#1A162E")) // Контрастный темный текст
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tv.alpha = 0.35f // Приглушаем неактивные табы
                tv.scaleX = 0.94f; tv.scaleY = 0.94f
                tv.setTextColor(Color.WHITE)
                tv.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }


    private fun switchCategory(categoryName: String) {
        // ВСТАВЛЕНО: Если нажали на уже активный таб — сбрасываем фильтр на режим "ВСЕ"
        if (currentCategory == categoryName && categoryName != "ВСЕ") {
            currentCategory = "ВСЕ"
        } else {
            currentCategory = categoryName
        }

        currentPage = 1
        canLoadMore = true
        isLoadingPage = false

        rvNews.visibility = View.GONE
        tvErrorPlaceholder.text = if (currentCategory == "ВСЕ") "Загрузка общей ленты..." else "Загрузка категории $currentCategory..."
        tvErrorPlaceholder.visibility = View.VISIBLE

        updateActiveTabHighlight()
        loadCategoryNews(currentCategory, currentPage)
    }



    private fun loadCategoryNews(category: String, page: Int) {
        if (isLoadingPage || !canLoadMore) return
        isLoadingPage = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val news = NewsRepository.parseNewsByCategory(category, page)
                if (news.isNotEmpty()) {
                    tvErrorPlaceholder.visibility = View.GONE
                    rvNews.visibility = View.VISIBLE
                    if (page == 1) newsAdapter.updateData(news) else newsAdapter.appendData(news)
                } else {
                    if (page == 1) {
                        rvNews.visibility = View.GONE
                        tvErrorPlaceholder.text = "Массив публикаций пуст."
                        tvErrorPlaceholder.visibility = View.VISIBLE
                    }
                    canLoadMore = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (page == 1) {
                    rvNews.visibility = View.GONE
                    tvErrorPlaceholder.text = "Ошибка загрузки RSS фида."
                    tvErrorPlaceholder.visibility = View.VISIBLE
                }
            } finally {
                isLoadingPage = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.apply {
            findViewById<View>(R.id.fabMain)?.visibility = View.GONE
            findViewById<View>(R.id.notificationContainer)?.visibility = View.GONE
        }
    }
}
