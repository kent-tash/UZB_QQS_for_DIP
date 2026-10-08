# План исправления UI/UX (UI Fixes v2)

Этот документ содержит детальный план для Исполнителя (Agent/Executor) по реализации необходимых исправлений UI/UX и связанных логических улучшений.

## Задача 1: Обновление HelpDialog.kt (Справка)
**Файл:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpDialog.kt` (или там, где он расположен)

**Действия:**
1. Добавьте в начало диалога кликабельные контакты для связи:
   - Telegram: [t.me/dis_night](https://t.me/dis_night)
   - Email: [i@dis-night.ru](mailto:i@dis-night.ru)
   - Используйте `ClickableText` или `Text` с `Modifier.clickable` (с `UriHandler` для перехода).
2. Напишите краткую и понятную инструкцию по каждой вкладке приложения, используя форматирование (жирный текст для названий вкладок):
   - **Добавить:** Инструкция по добавлению чеков (сканирование QR, фото, ручной ввод).
   - **Чеки:** Инструкция по просмотру, фильтрации и управлению добавленными чеками.
   - **Отчёт:** Инструкция по формированию и выгрузке отчетов.
   - **Аудит:** Инструкция по проверке пользователей и просмотру статистики аудитора.
   - **Профиль:** Инструкция по управлению аккаунтом, настройкам и бэкапам.

## Задача 2: Динамический TopAppBar в AppNavHost.kt
**Файл:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/navigation/AppNavHost.kt`

**Действия:**
1. В `TopAppBar` (внутри `Scaffold` основного экрана) замените жестко заданный `Text("UZB QQS")` на динамический заголовок.
   - Определите текущую вкладку: `val currentTitle = tabs.find { it.route == currentRoute }?.title ?: "UZB QQS"`
   - Используйте `currentTitle` в качестве текста заголовка.
2. Сделайте заголовок кликабельным:
   - Добавьте `Modifier.clickable` к компоненту заголовка (или к `TopAppBar`).
   - При клике вызывайте callback или отправляйте event (через SharedViewModel или локальный state), который заставит активный `LazyColumn` / `LazyVerticalGrid` проскроллиться наверх.
   - *Опционально:* Если реализация глобального скролла через стейт покажется избыточно сложной, достаточно просто сделать динамическое название вкладки, а ивенты скролла прокинуть как-нибудь позже. Главное - динамический заголовок.

## Задача 3: Удаление дублирующихся заголовков на экранах
**Файлы:**
- `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`
- `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/AuditorScreen.kt`
- `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReceiptsScreen.kt`
- `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ScanScreen.kt`

**Действия:**
1. Проверьте каждый из этих экранов на наличие собственных `TopAppBar` или крупных заголовков (например, `Text("Отчёт", style = MaterialTheme.typography.headlineMedium)` в начале экрана).
2. Полностью **удалите** эти внутренние заголовки и локальные `TopAppBar`, так как теперь глобальный `TopAppBar` из `AppNavHost` будет показывать название вкладки.
3. Это устранит дублирование кнопки "?" и уберет лишние гигантские отступы сверху.

## Задача 4: Единый скролл во вкладке "Аудит"
**Файл:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/AuditorScreen.kt`

**Действия:**
1. Переработайте структуру экрана так, чтобы статистика и список пользователей находились в едином контейнере прокрутки.
2. Замените текущую структуру (где статистика фиксирована сверху, а ниже идет `LazyColumn`) на единый `LazyColumn`.
3. Поместите блок со статистикой и кнопками управления внутрь `item { ... }` на самом верху `LazyColumn`.
4. Список пользователей разместите ниже с помощью `items(users) { ... }`.
5. Теперь весь экран "Аудит" должен скроллиться как единое целое.

## Задача 5: Цветовая дифференциация кнопок
**Файлы:**
- `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ScanScreen.kt`
- `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/ExportActionGrid.kt` (или там, где находятся кнопки экспорта)

**Действия:**
1. В `ScanScreen.kt` найдите 3 главные кнопки добавления (QR, Фото, Ручной ввод).
   - Раскрасьте их в разные цвета для визуального разделения.
   - Используйте:
     - `ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)`
     - `ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)`
     - `ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)`
2. В компоненте `ExportActionGrid` (кнопки PDF, Excel и т.д.) сделайте 4 кнопки экспорта/отчетов разных гармоничных оттенков. Можно использовать тона из `MaterialTheme.colorScheme` (например, `primary`, `secondary`, `tertiary`, `primaryContainer`, либо задать кастомные цвета, если тема это позволяет).

## Задача 6: Обновление логики формирования отчетов
**Файл:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportViewModel.kt`

**Действия:**
1. Найдите методы `generate` (используемый для открытия/печати) и `shareReport` (Поделиться).
2. Измените их флоу работы, добавив предварительную синхронизацию:
   - В самом начале установите `_isSaving = true` (или аналогичный стейт для показа прогресс-бара).
   - Вызовите обновление базы данных: `container.receiptRepository.refresh()` (или аналогичный метод обновления).
   - Вызовите метод `syncWithOfd()` для загрузки свежих данных, отображая прогресс пользователю.
   - Только ПОСЛЕ успешного завершения сихронизации, вызывайте логику генерации PDF/Excel файла.
   - По завершении генерации (и открытия/шаринга) сбросьте флаг `_isSaving = false`.

## Задача 7: Исправление кнопок в окне бэкапа
**Файл:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ProfileScreen.kt`

**Действия:**
1. Найдите диалоговое окно (AlertDialog или Dialog), которое спрашивает пользователя о действии при слиянии бэкапов.
2. Выровняйте кнопки в ряду:
   - Поместите их в `Row` с `Modifier.fillMaxWidth()`.
   - Задайте кнопкам одинаковый вес: `Modifier.weight(1f)`.
   - Добавьте отступы между кнопками (например, `Spacer(modifier = Modifier.width(8.dp))`).
3. Перекрасьте кнопку "Полностью заменить" (или эквивалентную деструктивную кнопку) в красный/предупреждающий цвет:
   - `colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)`

---
**Указания для Исполнителя:**
Выполняйте задачи последовательно. После выполнения каждого блока проверяйте отсутствие ошибок компиляции и сохранение работоспособности UI. По завершении задач сообщите о результатах.
