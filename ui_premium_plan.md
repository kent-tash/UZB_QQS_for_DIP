# План обновления UI/UX (Premium Upgrade v2)

Данный план содержит пошаговые инструкции для Исполнителя по обновлению внешнего вида приложения. Следуй шагам последовательно.

## 1. Тёмная тема (текст)
**Цель:** Обеспечить читаемость текста на темном фоне экрана авторизации.
**Файл:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/AuthScreen.kt`
- Найди компоненты `Text` с содержимым `"QQS Сканер"` и `"Выберите пользователя"`.
- Добавь в их параметры явно цвет из темы: `color = MaterialTheme.colorScheme.onSurface`.
- Убедись, что текст описания (например, `"Учёт фискальных чеков Узбекистана"`) использует `color = MaterialTheme.colorScheme.onSurfaceVariant`.

## 2. Сетка кнопок 2x2 (Экспорт)
**Цель:** Заменить разрозненные кнопки экспорта на аккуратную и переиспользуемую сетку.
**Файл 1 (создать/обновить):** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/ExportActionGrid.kt`
- Создай новый Composable-компонент `ExportActionGrid(onOpen: () -> Unit, onPrint: () -> Unit, onSave: () -> Unit, onShare: () -> Unit, isSaving: Boolean, saveProgress: Float, isSaveEnabled: Boolean = true, isOpenPrintEnabled: Boolean = true)`.
- Реализуй внутри сетку 2x2. Можно использовать `Column` с двумя вложенными `Row` (каждому `Row` дать `horizontalArrangement = Arrangement.spacedBy(10.dp)` и кнопкам `Modifier.weight(1f)`).
- Верхний ряд: кнопки "Сохранить" (использовать `SaveProgressButton`) и "Поделиться" (`OutlinedButton`).
- Нижний ряд: кнопки "Открыть" (`OutlinedButton`) и "Печать" (`Button`).
- Текст кнопки печати должен быть строго `"Печать"` (а не "Печать / PDF").
- Для всех кнопок в сетке задать скругление `shape = RoundedCornerShape(14.dp)`.

**Файл 2:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`
- Найди блок с кнопками "Открыть", "Печать / PDF", "Сохранить", "Поделиться" (примерно строки 299-340).
- Замени весь этот блок вызовом созданного `ExportActionGrid`.

**Файл 3:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReceiptsScreen.kt`
- Выполни аналогичную замену разрозненных кнопок экспорта на `ExportActionGrid`.

## 3. Фон и границы (Контраст)
**Цель:** Улучшить контраст светлой и темной тем, выделить карточки.
**Файл:** `app/src/main/java/com/example/uzb_qqs_for_dip/ui/theme/Color.kt`
- Измени значения цветов для лучшего контраста:
  - `val Background = Color(0xFFF3F4F6)`
  - `val Surface = Color.White` (или `Color(0xFFFFFFFF)`)
  - `val DarkBackground = Color(0xFF121212)`
  - `val DarkSurface = Color(0xFF1E1E1E)`

**Глобально (Карточки):**
- В файлах `AuthScreen.kt`, `ReportScreen.kt`, `ReceiptsScreen.kt` и компонентах (например, `ReceiptCardRenderer.kt` или `ReportPreviewCard`), где используются `Card`:
- Убедись, что цвета карточек заданы как `colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`.
- (Опционально) Замени `Card` на `OutlinedCard` с тонкой рамкой: `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` для более премиального вида.

## 4. Скругления и анимации
**Цель:** Добавить плавности и современный вид элементам интерфейса.
- **Скругления:**
  - В карточках (профили, чеки, превью отчетов) увеличь `RoundedCornerShape` до `16.dp` или `20.dp`. (В `AuthScreen` уже стоит 14/16.dp, обнови до 16.dp/20.dp по дизайну).
- **Анимации (`ReportScreen.kt`):**
  - Найди контейнер, внутри которого условно появляется панель пакетного удаления (`SelectionActionBar`).
  - Добавь к модификатору этого родительского контейнера модификатор `Modifier.animateContentSize()`. Это заставит панель появляться и исчезать плавно, без резких скачков интерфейса.

---
**Примечание для Исполнителя:** Убедитесь, что приложение успешно компилируется после всех изменений, особенно после извлечения логики кнопок в новый компонент `ExportActionGrid`.
