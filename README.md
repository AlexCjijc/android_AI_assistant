 — это многофункциональное Android‑приложение на Kotlin, объединяющее голосовое распознавание, AI‑чат с нейросетями, систему напоминаний и таймеров, дневник успеваемости, расписание занятий и аналитику прогресса. Приложение работает с локальным хранилищем и внешними AI‑API (OpenRouter), поддерживает голосовой ввод, синтез речи и уведомления даже в режиме "Не беспокоить".
<img width="1080" height="2340" alt="Screenshot_20260615_134434_ -  " src="https://github.com/user-attachments/assets/550a1683-6f21-493c-9390-d5700e1d351f" />
<img width="1080" height="2340" alt="Screenshot_20260615_134432_ -  " src="https://github.com/user-attachments/assets/cf681778-8cd3-49d8-940a-75d5ba017a81" />
<img width="1080" height="2340" alt="Screenshot_20260615_134429_ -  " src="https://github.com/user-attachments/assets/a068fa9e-5522-4144-9360-0a47d18666ad" />
<img width="1080" height="2340" alt="Screenshot_20260615_134443_ -  " src="https://github.com/user-attachments/assets/b263b472-d043-4aa2-9abf-f10bd0353e81" />
📌 Основные возможности
🤖 AI‑ассистент

    Голосовой ввод (распознавание речи) и текстовый чат

    Поддержка множества моделей через OpenRouter:

        Встроенные: ChatGPT, DeepSeek, Baidu, MiniMax и др.

        Пользовательские модели (можно добавлять через настройки)

    Контекстный диалог с сохранением истории переписки

    TTS (синтез речи) для озвучивания ответов

    Умное распознавание команд:

        Напоминания — "напомни выключить чайник в 14:30"

        Таймеры — "таймер на 5 минут"

        Будильники — "разбуди в 7 утра"

        Сброс контекста — "забудь", "очисти экран"

⏰ Напоминания и таймеры

    Создание напоминаний с указанием даты/времени

    Таймеры с визуальным обратным отсчётом

    Push‑уведомления с звуком даже в режиме DND

    Управление (редактирование, удаление) через интерфейс

📚 Дневник успеваемости

    Ведение оценок за занятия и экзамены

    Группировка по учебным периодам (семестры/четверти)

    Автоматический расчёт среднего балла

    Прогнозирование итоговых оценок по предметам

    AI‑анализ успеваемости с рекомендациями

📅 Расписание занятий

    Создание пар с привязкой к дням недели и времени

    Поддержка чётных/нечётных недель (числитель/знаменатель)

    Режимы: по номерам недель или по календарным датам

    Экзамены и зачёты с группировкой по датам

    Полное расписание (все пары периода)

📊 Профиль и аналитика

    Аватар и имя пользователя

    Статистика оценок (количество 5, 4, 3, 2)

    Графики успеваемости по семестрам и предметам

    Список сильных и слабых предметов

    AI‑анализ успеваемости (с кэшированием)

📰 Новостная лента

    RSS‑потоки по категориям: Наука, Образование, Технологии, IT

    Открытие статей в браузере

    Пагинация и фильтрация по категориям

🛠️ Технологический стек

    Язык: Kotlin

    UI: XML, ViewBinding, RecyclerView, ViewPager2

    Архитектура: MVVM (фрагменты + репозитории)

    База данных: SharedPreferences (локальное хранение)

    Сериализация: kotlinx.serialization, Gson

    Сеть: OkHttp, Ktor Client

    AI API: OpenRouter (ChatGPT, DeepSeek, Baidu и др.)

    Распознавание речи: Android SpeechRecognizer

    Синтез речи: TextToSpeech (TTS)

    Уведомления: AlarmManager + BroadcastReceiver

    Графики: кастомный View (Canvas)

    RSS: ROME (RomeTools)

    Изображения: Glide

🚀 Установка и запуск
1. Клонирование репозитория
bash

git clone https://github.com/yourusername/SpeechRecognizer.git
cd SpeechRecognizer

2. Открытие в Android Studio

    Откройте папку проекта в Android Studio

    Дождитесь синхронизации Gradle

3. Настройка API ключей

В файле data/OpenRouterClient.kt и ui/ChatFragment.kt замените API ключ:
kotlin

private const val API_KEY = "ваш_ключ_openrouter"

4. Сборка и запуск

    Подключите Android‑устройство или эмулятор

    Нажмите Run (▶) в Android Studio

📁 Структура проекта
text

app/src/main/java/com/example/speechrecognizer/
├── AlarmReceiver.kt              # Приёмник будильников
├── MainActivity.kt               # Главная активность (навигация)
├── NotificationHelper.kt         # Управление уведомлениями
├── CommandExecutor.kt            # Исполнение голосовых команд
├── data/                         # Модели и хранилища
│   ├── AlarmRepository.kt        # Репозиторий напоминаний
│   ├── Reminder.kt               # Модель напоминания
│   ├── ChatThread.kt             # Модель чата
│   ├── DiaryModels.kt            # Модели дневника
│   ├── DiaryStorage.kt           # Хранилище дневника
│   ├── ScheduleModels.kt         # Модели расписания
│   ├── ScheduleStorage.kt        # Хранилище расписания
│   ├── Subject.kt                # Модель предмета
│   ├── PeriodManager.kt          # Управление периодами
│   ├── NewsRepository.kt         # RSS‑новости
│   └── OpenRouterClient.kt       # Клиент OpenRouter API
├── ui/                           # UI‑фрагменты и адаптеры
│   ├── ChatFragment.kt           # Чат с AI
│   ├── ChatListFragment.kt       # Список чатов
│   ├── ChatListAdapter.kt        # Адаптер чатов
│   ├── DiaryFragment.kt          # Дневник успеваемости
│   ├── DiaryAdapters.kt          # Адаптеры дневника
│   ├── ScheduleFragment.kt       # Расписание занятий
│   ├── ScheduleAdapters.kt       # Адаптеры расписания
│   ├── HomeFragment.kt           # Главная (новости)
│   ├── NewsAdapter.kt            # Адаптер новостей
│   ├── ProfileFragment.kt        # Профиль и аналитика
│   ├── ParametersFragment.kt     # Настройки (предметы, модели)
│   ├── SettingsFragment.kt       # Напоминания и таймеры
│   ├── EditReminderDialogFragment.kt  # Диалог напоминаний
│   ├── EditTimerDialogFragment.kt     # Диалог таймеров
│   ├── StudyProgressChartView.kt      # Кастомный график
│   └── ChartPagerAdapter.kt      # Адаптер графиков
├── utils/                        # Утилиты
│   └── AlarmScheduler.kt         # Планировщик будильников
└── res/                          # Ресурсы (layouts, drawable, values)

🗄️ Хранилище данных

Все данные хранятся локально в SharedPreferences:
Напоминания

    Ключ: AlarmPrefs/reminders_list

    Формат: JSON‑массив объектов Reminder

Чаты

    Ключ: ChatPrefs/chat_threads (список чатов)

    История: AiAssistantPrefs/chat_history_{chatId}

Дневник

    Ключ: diary_grades_prefs/lesson_grades_json и exam_grades_json

    Формат: JSON‑массивы объектов LessonGrade/ExamGrade

Расписание

    Ключ: schedule_prefs/lessons_json, exams_json, subjects_objects_json

    Формат: JSON‑массивы объектов Lesson, Exam, Subject

Периоды

    Ключ: periods_prefs/periods_list_json, active_period_id

    Формат: JSON‑массив объектов StudyPeriod

AI модели

    Ключ: ai_models_prefs/default_model_id, custom_models_list, hidden_builtin_models

🔐 Разрешения

Приложение запрашивает следующие разрешения:

    RECORD_AUDIO — голосовой ввод

    POST_NOTIFICATIONS (Android 13+) — уведомления

    SCHEDULE_EXACT_ALARM (Android 12+) — точные будильники

🧪 Особенности реализации
Будильники и напоминания

    Используется AlarmManager.setExactAndAllowWhileIdle() для точного срабатывания

    Звук будильника воспроизводится через MediaPlayer с USAGE_ALARM

    Уведомления с CATEGORY_ALARM и setBypassDnd(true) обходят режим DND

    Автоматическая остановка звука через 10 секунд

AI‑чат

    Контекст хранится в виде JSON‑массива сообщений

    Умная классификация команд через AI (отдельный запрос к модели)

    Поддержка Markdown и LaTeX (через Markwon + JLatexMath)

    Отмена генерации ответа (кнопка "✕" во время обработки)

Графики успеваемости

    Кастомный View на Canvas

    Мультилинейные графики с цветовой дифференциацией

    Легенда предметов

    Пастельная цветовая схема для оценок (HSV)

📋 Команды AI‑ассистента
Команда	Пример	Действие
Напоминание	"напомни купить хлеб в 19:00"	Создаёт напоминание
Таймер	"таймер на 10 минут"	Запускает таймер
Будильник	"разбуди в 7 утра"	Открывает приложение "Часы"
Сброс контекста	"забудь историю"	Очищает память AI
Очистка чата	"очисти экран"	Скрывает сообщения
Полный сброс	"удалить всё"	Полная очистка
🔌 Добавление пользовательских AI моделей

В настройках (Параметры → Нейросети):

    Нажмите ➕

    Введите название модели и API‑путь (например, openai/gpt-4o)

    Модель появится в списке и в меню выбора в чате<img width="1080" height="2340" alt="Screenshot_20260615_134440_ -  " src="https://github.com/user-attachments/assets/6e2ec331-c60f-487f-b449-89f5c5f55992" />

