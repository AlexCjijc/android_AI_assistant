package com.example.speechrecognizer.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.ExamGrade
import com.example.speechrecognizer.data.LessonGrade
import java.util.Locale
import android.graphics.Color
import com.example.speechrecognizer.data.PeriodManager



// Общий интерфейс для элементов списка сессии
interface ExamListItem

// Объект-заголовок
data class ExamHeaderItem(val title: String) : ExamListItem

// Ссылка на саму оценку
data class ExamDataItem(val grade: com.example.speechrecognizer.data.ExamGrade) : ExamListItem

// Общий интерфейс для элементов списка занятий
interface LessonListItem

// Объект-заголовок даты
data class LessonHeaderItem(val dateText: String) : LessonListItem

// Ссылка на саму оценку за занятие
data class LessonDataItem(val grade: com.example.speechrecognizer.data.LessonGrade) : LessonListItem


class LessonGradesAdapter(
    private val onEdit: (LessonGrade) -> Unit,
    private val onDelete: (LessonGrade) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<LessonListItem>()
    private var isEditMode = false

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    // Метод группирует оценки за занятия по датам и строит плоский список с заголовками
    fun updateData(rawGrades: List<LessonGrade>) {
        items.clear()

        // Группируем оценки по тексту даты.
        // Так как DiaryStorage уже сортирует по timestamp DESC, порядок дней будет правильным.
        val groupedByDate = rawGrades.groupBy { it.dateText }

        for ((date, grades) in groupedByDate) {
            // Добавляем мини-заголовок даты
            items.add(LessonHeaderItem(date))
            // Добавляем все оценки, полученные в этот день
            items.addAll(grades.map { LessonDataItem(it) })
        }
        notifyDataSetChanged()
    }

    fun setEditMode(active: Boolean) {
        isEditMode = active
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is LessonHeaderItem -> VIEW_TYPE_HEADER
            is LessonDataItem -> VIEW_TYPE_ITEM
            else -> throw IllegalArgumentException("Unknown item type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_diary_date_header, parent, false)
            LessonHeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_diary_grade, parent, false)
            LessonGradeViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LessonHeaderItem -> (holder as LessonHeaderViewHolder).bind(item)
            is LessonDataItem -> (holder as LessonGradeViewHolder).bind(item.grade, isEditMode, onEdit, onDelete)
        }
    }

    override fun getItemCount(): Int = items.size

    // ViewHolder для мини-заголовка даты
    class LessonHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvDateHeaderTitle)
        fun bind(item: LessonHeaderItem) {
            tvTitle.text = item.dateText
        }
    }

    // ViewHolder для самой оценки за занятие
    class LessonGradeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGrade: TextView = view.findViewById(R.id.tvGradeValue)
        val tvSubject: TextView = view.findViewById(R.id.tvGradeSubject)
        val tvDate: TextView = view.findViewById(R.id.tvGradeDate) // Поле даты внутри элемента
        val tvComment: TextView = view.findViewById(R.id.tvGradeComment)
        val btnEdit: ImageView = view.findViewById(R.id.btnEditGrade)
        val btnDelete: ImageView = view.findViewById(R.id.btnDeleteGrade)

        fun bind(item: LessonGrade, isEditMode: Boolean, onEdit: (LessonGrade) -> Unit, onDelete: (LessonGrade) -> Unit) {
            tvGrade.text = item.grade
            tvSubject.text = item.subject
            tvComment.text = item.comment
            tvComment.visibility = if (item.comment.isNotEmpty()) View.VISIBLE else View.GONE

            // ИСПРАВЛЕНО: Скрываем дублирующееся поле даты внутри карточки,
            // так как теперь есть красивый мини-заголовок сверху группы.
            // Создаем вспомогательную функцию внутри DiaryAdapters.kt или прямо в методе bind:
            fun cleanSubjectName(rawName: String): String {
                return if (rawName.contains("Subject(")) {
                    rawName.substringAfter("name=").substringBefore(",")
                } else {
                    rawName
                }
            }

// Применение в LessonGradeViewHolder внутри bind():
            tvSubject.text = cleanSubjectName(item.subject)

            tvDate.visibility = View.GONE

            setGradeBackground(tvGrade, item.grade)

            val visibility = if (isEditMode) View.VISIBLE else View.GONE
            btnEdit.visibility = visibility; btnDelete.visibility = visibility
            btnEdit.setOnClickListener { onEdit(item) }; btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
class ExamGradesAdapter(
    private val onEdit: (ExamGrade) -> Unit,
    private val onDelete: (ExamGrade) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ExamListItem>()
    private var isEditMode = false

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    // Метод принимает сырой список оценок, группирует их по семестрам и строит плоский список с заголовками
    fun updateData(rawGrades: List<ExamGrade>) {
        items.clear()

        // Группируем оценки по id периода
        val groupedByPeriod = rawGrades.groupBy { it.periodId }

        // Сначала выводим текущий активный период (семестр), чтобы он был сверху
        val activePeriodId = PeriodManager.activePeriodId

// ИСПРАВЛЕНО: вызвано правильное имя метода 'groupedSubjectId'
        val activePeriodGrades = groupedSubjectId(groupedByPeriod[activePeriodId])

        if (activePeriodGrades.isNotEmpty()) {
            val periodName = PeriodManager.periods.firstOrNull { it.id == activePeriodId }?.name ?: "Текущий период"
            items.add(ExamHeaderItem(periodName))
            items.addAll(activePeriodGrades.map { ExamDataItem(it) })
        }


        // Затем выводим все остальные семестры по порядку
        for ((periodId, grades) in groupedByPeriod) {
            if (periodId == activePeriodId) continue // Текущий уже добавили

            val sortedGrades = groupedSubjectId(grades)
            if (sortedGrades.isNotEmpty()) {
                val periodName = PeriodManager.periods.firstOrNull { it.id == periodId }?.name ?: "Прошлые периоды"
                items.add(ExamHeaderItem(periodName))
                items.addAll(sortedGrades.map { ExamDataItem(it) })
            }
        }
        notifyDataSetChanged()
    }



    private fun groupedSubjectId(list: List<ExamGrade>?): List<ExamGrade> {
        return list?.sortedByDescending { it.timestamp } ?: emptyList()
    }

    fun setEditMode(active: Boolean) {
        isEditMode = active
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ExamHeaderItem -> VIEW_TYPE_HEADER
            is ExamDataItem -> VIEW_TYPE_ITEM
            else -> throw IllegalArgumentException("Unknown type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_diary_period_header, parent, false)
            ExamHeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_diary_grade, parent, false)
            ExamGradeViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ExamHeaderItem -> (holder as ExamHeaderViewHolder).bind(item)
            is ExamDataItem -> (holder as ExamGradeViewHolder).bind(item.grade, isEditMode, onEdit, onDelete)
        }
    }

    override fun getItemCount(): Int = items.size

    // ViewHolder для заголовка семестра
    class ExamHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvPeriodHeaderTitle)
        fun bind(item: ExamHeaderItem) {
            tvTitle.text = item.title
        }
    }

    // ViewHolder для оценки (чистый вывод без названия семестра внутри плашки)
    class ExamGradeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGrade: TextView = view.findViewById(R.id.tvGradeValue)
        val tvSubject: TextView = view.findViewById(R.id.tvGradeSubject)
        val tvDate: TextView = view.findViewById(R.id.tvGradeDate)
        val tvComment: TextView = view.findViewById(R.id.tvGradeComment)
        val btnEdit: ImageView = view.findViewById(R.id.btnEditGrade)
        val btnDelete: ImageView = view.findViewById(R.id.btnDeleteGrade)

        fun bind(item: ExamGrade, isEditMode: Boolean, onEdit: (ExamGrade) -> Unit, onDelete: (ExamGrade) -> Unit) {
            tvGrade.text = item.grade

            // Создаем вспомогательную функцию внутри DiaryAdapters.kt или прямо в методе bind:
            fun cleanSubjectName(rawName: String): String {
                return if (rawName.contains("Subject(")) {
                    rawName.substringAfter("name=").substringBefore(",")
                } else {
                    rawName
                }
            }

// Применение в ExamGradeViewHolder внутри bind():
            tvSubject.text = "${cleanSubjectName(item.subject)} (${item.type})"


            tvDate.text = item.dateText
            tvComment.text = item.comment
            tvComment.visibility = if (item.comment.isNotEmpty()) View.VISIBLE else View.GONE

            setGradeBackground(tvGrade, item.grade)

            val visibility = if (isEditMode) View.VISIBLE else View.GONE
            btnEdit.visibility = visibility; btnDelete.visibility = visibility
            btnEdit.setOnClickListener { onEdit(item) }; btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}


class DiaryPagerAdapter(
    private val v1: View,
    private val v2: View,
    private val v3: View // Добавлено
) : RecyclerView.Adapter<DiaryPagerAdapter.PagerViewHolder>() {
    override fun getItemViewType(position: Int): Int = position
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder {
        val view = when(viewType) {
            0 -> v1
            1 -> v2
            else -> v3
        }
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return PagerViewHolder(view)
    }
    override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {}
    override fun getItemCount(): Int = 3 // Изменено на 3
    class PagerViewHolder(view: View) : RecyclerView.ViewHolder(view)
}

// Функция динамической смены фонового цвета плашки оценки
fun getGradientColor(grade: Double): Int {
    // Ограничиваем оценку диапазоном от 1.0 до 5.0
    val clammed = grade.coerceIn(1.0, 5.0)

    val hue: Float
    var saturation = 0.55f // Базовая нежная пастельная насыщенность
    var value = 0.90f      // Базовая чистая яркость

    // Нелинейный расчет тона (Hue) для четкого разделения цветов по баллам
    when {
        clammed >= 4.0 -> {
            // Интервал от 4.0 (желто-салатовый, 75°) до 5.0 (зеленый, 120°)
            val t = clammed - 4.0
            hue = (75.0 + t * 45.0).toFloat()
        }
        clammed >= 3.0 -> {
            // Интервал от 3.0 (оранжевый, 35°) до 4.0 (желто-салатовый, 75°)
            val t = clammed - 3.0
            hue = (35.0 + t * 40.0).toFloat()
        }
        clammed >= 2.0 -> {
            // Интервал от 2.0 (кораллово-красный, 15°) до 3.0 (оранжевый, 35°)
            val t = clammed - 2.0
            hue = (15.0 + t * 20.0).toFloat()
        }
        else -> {
            // Интервал от 1.0 (бордовый, ~4°) до 2.0 (красный, 15°)
            // Принудительно конвертируем 't' во Float, чтобы избежать конфликта типов
            val t = (clammed - 1.0).toFloat()

            hue = (4.0f + t * 11.0f)

            // СЕКРЕТ БОРДОВОГО: Все расчеты теперь строго во Float
            saturation = (0.75f - t * 0.20f).coerceIn(0.55f, 0.75f)
            value = (0.65f + t * 0.25f).coerceIn(0.65f, 0.90f)
        }

    }

    // Возвращаем мягкий пастельный цвет с идеальным разделением оттенков
    return android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
}


// Перегрузка функции для обратной совместимости со старыми адаптерами (Lesson/Exam)
fun setGradeBackground(tvGrade: TextView, gradeText: String) {
    val gradeNumber = gradeText.trim().toDoubleOrNull()

    val colorInt = if (gradeNumber != null) {
        getGradientColor(gradeNumber) // Наш пастельный HSV-градиент
    } else {
        android.graphics.Color.parseColor("#4C447A") // Фиолетовый для «Зачет»
    }

    // Безопасно меняем цвет скругленного фона, не ломая углы
    tvGrade.backgroundTintList = ColorStateList.valueOf(colorInt)
}

class PredictAdapter(
    private var items: List<Pair<String, Double>>
) : RecyclerView.Adapter<PredictAdapter.PredictViewHolder>() {

    fun updateData(newItems: List<Pair<String, Double>>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PredictViewHolder {
        // Обратите внимание на имя XML: исправлено под вашу структуру (item_predict_subject или item_pregict_subject)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_predict_subject, parent, false)
        return PredictViewHolder(view)
    }

    // --- ВОТ ЭТОТ МЕТОД ИЗМЕНИЛСЯ (ВСТАВЛЯТЬ СЮДА) ---
    override fun onBindViewHolder(holder: PredictViewHolder, position: Int) {
        val (subject, avgGrade) = items[position]

        // ИСПРАВЛЕНО: Безопасное извлечение чистого имени из системного мусора Subject(...)
        val cleanSubjectName = if (subject.contains("Subject(")) {
            subject.substringAfter("name=").substringBefore(",")
        } else {
            subject
        }

        holder.tvSubject.text = cleanSubjectName

        if (avgGrade > 0.0) {
            holder.tvGrade.text = String.format(Locale.US, "%.2f", avgGrade)
            val pastelColor = getGradientColor(avgGrade)
            holder.tvGrade.backgroundTintList = ColorStateList.valueOf(pastelColor)
            holder.tvGrade.setTextColor(android.graphics.Color.WHITE)
        } else {
            holder.tvGrade.text = "—"
            val defaultColor = android.graphics.Color.parseColor("#4C447A")
            holder.tvGrade.backgroundTintList = ColorStateList.valueOf(defaultColor)
            holder.tvGrade.setTextColor(android.graphics.Color.WHITE)
        }
    }


    override fun getItemCount(): Int = items.size

    class PredictViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvPredictSubjectName)
        val tvGrade: TextView = view.findViewById(R.id.tvPredictSubjectGrade)
    }


}

