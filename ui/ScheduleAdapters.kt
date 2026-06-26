package com.example.speechrecognizer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.speechrecognizer.R
import com.example.speechrecognizer.data.Exam
import com.example.speechrecognizer.data.Lesson

// ==========================================
// 1. АДАПТЕР ЗАНЯТИЙ (ГРУППИРОВКА ПО ДНЯМ)
// ==========================================
class LessonsAdapter(
    private var groupedItems: Map<String, List<Lesson>>,
    private val onEditClick: (Lesson) -> Unit,
    private val onDeleteClick: (Lesson) -> Unit
) : RecyclerView.Adapter<LessonsAdapter.LessonGroupViewHolder>() {

    private var isEditMode: Boolean = false

    fun updateData(newGroupedItems: Map<String, List<Lesson>>) {
        groupedItems = newGroupedItems
        notifyDataSetChanged()
    }

    fun setEditMode(active: Boolean) {
        isEditMode = active
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonGroupViewHolder {
        // ИСПРАВЛЕНО: Здесь должен быть ТОЛЬКО надув макета карточки дня
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_day, parent, false)
        return LessonGroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: LessonGroupViewHolder, position: Int) {
        val dayName = groupedItems.keys.elementAt(position)
        val lessons = groupedItems[dayName] ?: emptyList()
        holder.bind(dayName, lessons, isEditMode, onEditClick, onDeleteClick)
    }

    override fun getItemCount(): Int = groupedItems.size

    class LessonGroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDayName: TextView = view.findViewById(R.id.tvDayName)
        private val llSubLessonsContainer: LinearLayout = view.findViewById(R.id.llSubLessonsContainer)

        fun bind(dayName: String, lessons: List<Lesson>, isEditMode: Boolean, onEdit: (Lesson) -> Unit, onDelete: (Lesson) -> Unit) {
            tvDayName.text = dayName
            llSubLessonsContainer.removeAllViews()

            val inflater = LayoutInflater.from(itemView.context)

            // Перебираем пары текущего дня недели
            lessons.forEach { lesson ->
                val rowView = inflater.inflate(R.layout.row_lesson_item, llSubLessonsContainer, false)

                val tvTime = rowView.findViewById<TextView>(R.id.tvLessonTime)
                val tvDetails = rowView.findViewById<TextView>(R.id.tvLessonDetails)
                val btnEdit = rowView.findViewById<ImageView>(R.id.btnItemEdit)
                val btnDelete = rowView.findViewById<ImageView>(R.id.btnItemDelete)

                val periodMarker = if (lesson.period.isNotEmpty()) " (${lesson.period})" else ""
                val weekMarker = when(lesson.weekType) {
                    com.example.speechrecognizer.data.WeekType.CHISLITEL -> " [Числ.]"
                    com.example.speechrecognizer.data.WeekType.ZNAMENATEL -> " [Знамен.]"
                    else -> ""
                }

                tvTime.text = lesson.time
// ИСПРАВЛЕНО: Выводим название предмета, а доп. информацию переносим на новую строку или пишем через запятую
                tvDetails.text = "${lesson.subject}$periodMarker$weekMarker"

                val controlVisibility = if (isEditMode) View.VISIBLE else View.GONE
                btnEdit.visibility = controlVisibility
                btnDelete.visibility = controlVisibility

                btnEdit.setOnClickListener { onEdit(lesson) }
                btnDelete.setOnClickListener { onDelete(lesson) }

                llSubLessonsContainer.addView(rowView)
            }
        }
    }
}

// ==========================================
// 2. АДАПТЕР ЭКЗАМЕНОВ (ГРУППИРОВКА ПО ДАТАМ)
// ==========================================
class ExamsAdapter(
    private var groupedItems: Map<String, List<Exam>>,
    private val onEditClick: (Exam) -> Unit,
    private val onDeleteClick: (Exam) -> Unit
) : RecyclerView.Adapter<ExamsAdapter.ExamGroupViewHolder>() {

    private var isEditMode: Boolean = false

    fun updateData(newGroupedItems: Map<String, List<Exam>>) {
        groupedItems = newGroupedItems
        notifyDataSetChanged()
    }

    fun setEditMode(active: Boolean) {
        isEditMode = active
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamGroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_day, parent, false) // переиспользуем контейнер дня
        return ExamGroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamGroupViewHolder, position: Int) {
        val dateKey = groupedItems.keys.elementAt(position)
        val exams = groupedItems[dateKey] ?: emptyList()
        holder.bind(dateKey, exams, isEditMode, onEditClick, onDeleteClick)
    }

    override fun getItemCount(): Int = groupedItems.size

    class ExamGroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDateTitle: TextView = view.findViewById(R.id.tvDayName)
        private val llSubLessonsContainer: LinearLayout = view.findViewById(R.id.llSubLessonsContainer)

        fun bind(dateText: String, exams: List<Exam>, isEditMode: Boolean, onEdit: (Exam) -> Unit, onDelete: (Exam) -> Unit) {
            // Отображаем один раз дату и день недели: "15 мая 2026 (Пятница)"
            val dayOfWeek = exams.firstOrNull()?.dayOfWeek ?: ""
            tvDateTitle.text = if (dayOfWeek.isNotEmpty()) "$dateText ($dayOfWeek)" else dateText
            llSubLessonsContainer.removeAllViews()

            val inflater = LayoutInflater.from(itemView.context)

            exams.forEach { exam ->
                val rowView = inflater.inflate(R.layout.row_lesson_item, llSubLessonsContainer, false)

                val tvTime = rowView.findViewById<TextView>(R.id.tvLessonTime)
                val tvDetails = rowView.findViewById<TextView>(R.id.tvLessonDetails)
                val btnEdit = rowView.findViewById<ImageView>(R.id.btnItemEdit)
                val btnDelete = rowView.findViewById<ImageView>(R.id.btnItemDelete)

                tvTime.text = exam.time
                // Было: tvDetails.text = exam.details

// СТАЛО (ИСПРАВЛЕНО): Очищаем вывод деталей экзамена от строк-дубликатов объектов Subject
                val rawDetails = exam.details
                val cleanDetails = if (rawDetails.contains("Subject( ")) {
                    val subjectPart = rawDetails.substringAfter("name=").substringBefore(",")
                    val extraPart = if (rawDetails.contains(",")) rawDetails.substringAfterLast(")") else ""
                    if (extraPart.isNotEmpty()) "$subjectPart, $extraPart" else subjectPart
                } else {
                    rawDetails
                }

                tvDetails.text = cleanDetails
 

                val controlVisibility = if (isEditMode) View.VISIBLE else View.GONE
                btnEdit.visibility = controlVisibility
                btnDelete.visibility = controlVisibility

                btnEdit.setOnClickListener { onEdit(exam) }
                btnDelete.setOnClickListener { onDelete(exam) }

                llSubLessonsContainer.addView(rowView)
            }
        }
    }

    // ==========================================
// 3. АДАПТЕР СТРАНИЦ ДЛЯ ВЫВОДА ВАНЯТИЙ И ЭКЗАМЕНОВ (VIEWPAGER2)
// ==========================================
    class SchedulePagerAdapter(
        private val lessonsView: View,
        private val examsView: View,
        private val allLessonsView: View // Добавлен третий экран
    ) : RecyclerView.Adapter<SchedulePagerAdapter.PageViewHolder>() {

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = when(viewType) {
                0 -> lessonsView
                1 -> examsView
                else -> allLessonsView
            }
            view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {}
        override fun getItemCount(): Int = 3 // ИСПРАВЛЕНО
        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }


}
