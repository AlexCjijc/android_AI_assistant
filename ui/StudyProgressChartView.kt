package com.example.speechrecognizer.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

// Структура линии для мульти-графика
data class ChartLineData(
    val label: String,                     // Название линии (например, "Физика")
    val color: Int,                        // Цвет линии
    val points: List<Pair<String, Double>> // Точки: <Название_Периода, Оценка>
)

class StudyProgressChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        textSize = 28f
    }

    private var linesList = listOf<ChartLineData>()
    private var allPeriodsNames = listOf<String>()

    fun setMultiLinesData(lines: List<ChartLineData>, periods: List<String>) {
        this.linesList = lines
        this.allPeriodsNames = periods
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (linesList.isEmpty() || allPeriodsNames.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Недостаточно данных для построения", width / 2f, height / 2f, textPaint)
            return
        }

        val paddingLeft = 90f
        val paddingRight = 60f
        val paddingTop = 100f // Увеличили отступ сверху для легенды
        val paddingBottom = 70f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        // 1. Рисуем сетку (оценки 3, 4, 5)
        val gridPaint = Paint().apply { color = Color.parseColor("#12FFFFFF"); strokeWidth = 2f }
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 3..5) {
            val y = paddingTop + chartHeight - ((i - 1) / 4f) * chartHeight
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
            canvas.drawText(i.toString(), paddingLeft - 20f, y + 10f, textPaint)
        }

        // Рисуем подписи периодов по оси X
        textPaint.textAlign = Paint.Align.CENTER
        val stepX = if (allPeriodsNames.size > 1) chartWidth / (allPeriodsNames.size - 1) else chartWidth
        allPeriodsNames.forEachIndexed { index, periodName ->
            val x = paddingLeft + index * stepX
            canvas.drawText(periodName, x, height - 15f, textPaint)
        }

        // 2. Отрисовка легенды (подписи предметов сверху)
        var legendX = paddingLeft
        var legendY = 40f
        val legendPaint = Paint(textPaint).apply { textAlign = Paint.Align.LEFT; color = Color.WHITE; textSize = 26f }

        linesList.forEach { line ->
            pointPaint.color = line.color
            canvas.drawCircle(legendX + 15f, legendY - 10f, 10f, pointPaint)
            canvas.drawText(line.label, legendX + 35f, legendY, legendPaint)
            legendX += legendPaint.measureText(line.label) + 70f // Сдвиг для следующего элемента
            if (legendX > width - paddingRight) { // Перенос строки легенды, если предметов много
                legendX = paddingLeft
                legendY += 35f
            }
        }

        // 3. Отрисовка самих графиков
        linesList.forEach { line ->
            val path = Path()
            var isFirst = true
            val pointsCoordinates = mutableListOf<PointF>()

            allPeriodsNames.forEachIndexed { pIndex, periodName ->
                // Ищем точку для конкретного периода
                val pointData = line.points.firstOrNull { it.first == periodName }
                if (pointData != null) {
                    val x = paddingLeft + pIndex * stepX
                    val gradeValue = pointData.second.coerceIn(1.0, 5.0)
                    val normalizedY = (gradeValue - 1.0) / 4.0
                    val y = paddingTop + chartHeight - (normalizedY * chartHeight).toFloat()

                    pointsCoordinates.add(PointF(x, y))

                    if (isFirst) {
                        path.moveTo(x, y)
                        isFirst = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
            }

            // Рисуем цветную линию предмета
            linePaint.color = line.color
            canvas.drawPath(path, linePaint)

            // Рисуем круглые узлы (точки)
            pointPaint.color = line.color
            pointsCoordinates.forEach { point ->
                canvas.drawCircle(point.x, point.y, 10f, pointPaint)
            }
        }
    }
}
