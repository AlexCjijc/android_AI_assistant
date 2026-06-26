package com.example.speechrecognizer.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

data class MultiChartPageData(
    val lines: List<ChartLineData>,
    val periods: List<String>
)

class ChartPagerAdapter(
    private var pages: List<MultiChartPageData> = emptyList()
) : RecyclerView.Adapter<ChartPagerAdapter.ChartViewHolder>() {

    fun updateData(newPages: List<MultiChartPageData>) {
        this.pages = newPages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val chartView = StudyProgressChartView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return ChartViewHolder(chartView)
    }

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        val page = pages[position]
        holder.chartView.setMultiLinesData(page.lines, page.periods)
    }

    override fun getItemCount(): Int = pages.size

    class ChartViewHolder(val chartView: StudyProgressChartView) : RecyclerView.ViewHolder(chartView)
}
