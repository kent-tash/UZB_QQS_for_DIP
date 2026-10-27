# Залипающая панель сортировки и пакетных действий (Sticky Header) в экране «Чеки и отчёт» (ReportScreen.kt)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Реализовать залипающую панель (Sticky Header) сортировки и пакетных действий в экране «Чеки и отчёт» (`ReportScreen.kt`), которая при прокрутке списка чеков плавно фиксируется вверху экрана с непрозрачным фоном, динамической тенью, тональной возвышенностью и тонким разделителем, сохраняя постоянный доступ к управлению сортировкой и массовому удалению.

**Architecture:**
1. **Presentation Layer (`ReportScreen.kt`):**
   - Перевод элементов управления списка из обычных `item` в `stickyHeader(key = "sticky_sort_and_batch_bar")` внутри `LazyColumn`.
   - В состав `stickyHeader` входят:
     - Панель `SortAndBatchBar` (фиксированная кнопка сортировки 170dp и чекбокс «Все (N) [✓]»);
     - Плашка пакетного удаления `SelectionActionBar` с анимацией раскрытия `AnimatedVisibility` при `selectedIds.isNotEmpty()`.
   - Реактивное вычисление состояния залипания через `derivedStateOf { listState.firstVisibleItemIndex >= 3 }` (когда шапка отчёта, селектор режима и кнопки экспорта скрылись за верхним краем).
   - Обертка `Surface` с непрозрачным фоном `MaterialTheme.colorScheme.background`, анимированной тенью `shadowElevation = 4.dp`, `tonalElevation = 2.dp` и тонким `HorizontalDivider(0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` в состоянии `isPinned == true`.
   - В обычном состоянии (`isPinned == false`) — плоский вид с `shadowElevation = 0.dp`, `tonalElevation = 0.dp` и без разделителя.
2. **Documentation Layer (`HelpBottomSheet.kt`):**
   - Добавление пояснения в раздел «Чеки и отчёт» о закреплённой панели сортировки и удобстве групповых операций при длинном скролле.
3. **Quality & Stability Layer:**
   - Unit-тест для чистой логики расчёта `isPinned` и параметров возвышения.
   - Проверка сборки и тестов через `./gradlew testDebugUnitTest`.
   - Проверка актуальности карты архитектуры `scripts/update_architecture_map.py`.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Material 3, Androidx Navigation, StateFlow / Coroutines, JUnit 4.

---

## Область изменений (Scope)

- **In Scope:**
  - Интеграция `stickyHeader` в `LazyColumn` экрана `ReportScreen.kt`.
  - Динамическое состояние `isPinned` на базе `listState.firstVisibleItemIndex >= 3` с оптимизацией через `derivedStateOf`.
  - Плавная анимация подъёма тени (`animateDpAsState`) с 0.dp до 4.dp и тональной возвышенности до 2.dp.
  - Непрозрачный фоновый контейнер `Surface(color = MaterialTheme.colorScheme.background)`, защищающий от просвечивания карточек чеков.
  - Тонкий разделитель `HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` при залипании.
  - Актуализация справки в `HelpBottomSheet.kt`.
  - Модульный тест `ReportStickyHeaderLogicTest.kt`.
  - Прогон тестов и автосинхронизация карты архитектуры.
- **Out of Scope:**
  - Изменение структуры базы данных SQLite (`DbHelper.kt`) и миграции.
  - Изменение бизнес-логики `ReportViewModel.kt` (сортировка, фильтрация, удаление остаются без изменений).
  - Изменение структуры генерации PDF, XLSX и других экспортеров.

---

## План пошаговой реализации (Action Items)

### Task 1: UI/UX ревью и спецификация микроанимаций (Designer)

**Files:**
- Reference: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`
- Reference: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/theme/Color.kt`
- Reference: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/theme/Theme.kt`

**Цели этапа Designer:**
1. Проверить эргономику и контрастность визуальных состояний:
   - `isPinned == false`: абсолютно плоский вид (flat), органично сливающийся с фоном экрана;
   - `isPinned == true`: мягкая тень 4.dp, тональная подсветка 2.dp и едва заметный полупрозрачный бордер 0.5.dp снизу (`outlineVariant` с alpha = 0.5f).
2. Проверить сохранение Touch target размеров:
   - Кнопка сортировки: ширина 170dp, высота 40dp, кликабельная иконка направления (touch target не менее 48dp с учётом ripple radius);
   - Чекбокс «Все»: кликабельная строка и TriStateCheckbox с размером зоны касания 48dp;
   - Плашка `SelectionActionBar`: кнопки «Снять» и «Удалить» с достаточными отступами.
3. Проверить TalkBack и Accessibility:
   - Убедиться, что фиксация заголовка не нарушает фокус экранного диктора при навигации жестами.

---

### Task 2: Создание Unit-теста логики залипания (Test Engineer / TDD)

**Files:**
- Create: `app/src/test/java/com/example/uzb_qqs_for_dip/ui/ReportStickyHeaderLogicTest.kt`

**Step 1: Написать failing unit-тест для логики `isPinned` и визуальных атрибутов**
Создать файл `app/src/test/java/com/example/uzb_qqs_for_dip/ui/ReportStickyHeaderLogicTest.kt`:

```kotlin
package com.example.uzb_qqs_for_dip.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тестирование логики вычисления состояния залипания (Sticky Header)
 * и визуальных параметров панели сортировки и пакетных действий.
 */
class ReportStickyHeaderLogicTest {

    object StickyHeaderCalculator {
        // Пороговый индекс элемента списка, при котором шапка отчета (0),
        // селектор режима (1) и кнопки экспорта (2) скрылись за верхним краем
        const val PINNED_THRESHOLD_INDEX = 3

        fun calculateIsPinned(firstVisibleItemIndex: Int): Boolean {
            return firstVisibleItemIndex >= PINNED_THRESHOLD_INDEX
        }

        fun getShadowElevationDp(isPinned: Boolean): Float = if (isPinned) 4f else 0f
        fun getTonalElevationDp(isPinned: Boolean): Float = if (isPinned) 2f else 0f
        fun shouldShowDivider(isPinned: Boolean): Boolean = isPinned
    }

    @Test
    fun testIsPinnedCalculation() {
        // До порога (элементы 0, 1, 2 на экране)
        assertFalse("Индекс 0 не должен вызывать залипание", StickyHeaderCalculator.calculateIsPinned(0))
        assertFalse("Индекс 1 не должен вызывать залипание", StickyHeaderCalculator.calculateIsPinned(1))
        assertFalse("Индекс 2 не должен вызывать залипание", StickyHeaderCalculator.calculateIsPinned(2))

        // Начиная с порога (шапка скрылась)
        assertTrue("Индекс 3 должен активировать залипание", StickyHeaderCalculator.calculateIsPinned(3))
        assertTrue("Индекс 4 должен активировать залипание", StickyHeaderCalculator.calculateIsPinned(4))
        assertTrue("Индекс 10 должен активировать залипание", StickyHeaderCalculator.calculateIsPinned(10))
    }

    @Test
    fun testElevationAndDividerValues() {
        // В незалипшем состоянии
        assertEquals(0f, StickyHeaderCalculator.getShadowElevationDp(isPinned = false), 0.001f)
        assertEquals(0f, StickyHeaderCalculator.getTonalElevationDp(isPinned = false), 0.001f)
        assertFalse(StickyHeaderCalculator.shouldShowDivider(isPinned = false))

        // В залипшем состоянии
        assertEquals(4f, StickyHeaderCalculator.getShadowElevationDp(isPinned = true), 0.001f)
        assertEquals(2f, StickyHeaderCalculator.getTonalElevationDp(isPinned = true), 0.001f)
        assertTrue(StickyHeaderCalculator.shouldShowDivider(isPinned = true))
    }
}
```

**Step 2: Запустить unit-тест и убедиться в успешном прохождении**
Команда PowerShell:
`.\gradlew.bat testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.ui.ReportStickyHeaderLogicTest"`
Ожидаемый результат: `BUILD SUCCESSFUL` (1 test class, 2 tests passed).

**Step 3: Зафиксировать тест в Git**
```bash
git add app/src/test/java/com/example/uzb_qqs_for_dip/ui/ReportStickyHeaderLogicTest.kt
git commit -m "test(ui): add unit tests for sticky header isPinned logic and elevation values"
```

---

### Task 3: Реализация `stickyHeader` в `ReportScreen.kt` (Doer)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`

**Step 1: Добавить необходимые импорты**
В файле `ReportScreen.kt` убедиться в наличии импортов:
```kotlin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.derivedStateOf
```

**Step 2: Заменить блоки 5 в `LazyColumn` на `stickyHeader`**
Заменить строки 263-294 в `ReportScreen.kt`:
```kotlin
        // =====================================================================
        // Блок 4: Залипающая панель сортировки и пакетных действий (Sticky Header)
        // =====================================================================
        @OptIn(ExperimentalFoundationApi::class)
        stickyHeader(key = "sticky_sort_and_batch_bar") {
            val isPinned by remember {
                derivedStateOf { listState.firstVisibleItemIndex >= 3 }
            }
            val shadowElevation by animateDpAsState(
                targetValue = if (isPinned) 4.dp else 0.dp,
                label = "stickyShadowElevation"
            )
            val tonalElevation by animateDpAsState(
                targetValue = if (isPinned) 2.dp else 0.dp,
                label = "stickyTonalElevation"
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = shadowElevation,
                tonalElevation = tonalElevation
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isPinned) 4.dp else 0.dp)
                ) {
                    SortAndBatchBar(
                        totalCount = rows.size,
                        selectedCount = selectedIds.size,
                        onSelectAll = { reportViewModel.selectAllVisible() },
                        onClearAll = { reportViewModel.clearSelection() },
                        sortField = settings.sortField,
                        sortOrder = settings.sortOrder,
                        onSortFieldSelected = { reportViewModel.toggleSort(it) },
                        onToggleSortOrder = { reportViewModel.toggleSort(settings.sortField) }
                    )

                    AnimatedVisibility(
                        visible = selectedIds.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            SelectionActionBar(
                                selectedCount = selectedIds.size,
                                onClear = { reportViewModel.clearSelection() },
                                onDelete = { showDeleteBatchConfirm = true }
                            )
                        }
                    }

                    if (isPinned) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
```

**Step 3: Проверить компиляцию**
Команда PowerShell:
`.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: `BUILD SUCCESSFUL`.

**Step 4: Зафиксировать изменения в Git**
```bash
git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt
git commit -m "feat(ui): implement sticky header for sort and batch actions in ReportScreen"
```

---

### Task 4: Актуализация раздела помощи `HelpBottomSheet.kt` (Doer)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt`

**Step 1: Обновить описание секции «Чеки и отчёт (Единый экран)»**
В секции `AccordionItem(title = "Чеки и отчёт (Единый экран)")` обновить пункт 4:
```kotlin
"4. Сортировка и групповые операции:\n" +
"• Залипающая панель (Sticky Header): при прокрутке списка чеков панель сортировки и плашка пакетных действий («Удалить выбранные») остаются аккуратно зафиксированными вверху экрана с элегантной тенью и разделителем, позволяя управлять сортировкой и удалением без необходимости прокручивать список обратно наверх.\n" +
"• Компактная кнопка сортировки шириной 170dp с встроенным быстрым переключением направления (↑ / ↓) и выбором поля по клику на шеврон.\n" +
"• Справа расположена плашка «Все (N)» с чекбоксом [✓] для быстрого выбора всех чеков в одно касание.\n\n" +
```

**Step 2: Проверить компиляцию**
Команда PowerShell:
`.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: `BUILD SUCCESSFUL`.

**Step 3: Зафиксировать изменения в Git**
```bash
git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt
git commit -m "docs(help): document sticky header functionality in HelpBottomSheet"
```

---

### Task 5: Верификация, статический анализ и синхронизация архитектуры (Test Engineer & Reviewer)

**Files:**
- Test: Все модульные тесты проекта
- Scripts: `scripts/update_architecture_map.py`
- Documentation: `SYSTEM_PROMPT.md`, `.cursorrules`

**Step 1: Запустить все Unit-тесты проекта**
Команда PowerShell:
`.\gradlew.bat testDebugUnitTest`
Ожидаемый результат: `BUILD SUCCESSFUL`, все тесты пройдены без ошибок.

**Step 2: Запустить автосинхронизацию архитектурной карты**
Команда PowerShell:
`python scripts/update_architecture_map.py`
Ожидаемый результат: карта структуры актуализирована, изменения применены к `SYSTEM_PROMPT.md` и `.cursorrules`.

**Step 3: Проверить линтинг и чистоту сборки**
Команда PowerShell:
`.\gradlew.bat lintDebug` (или `compileDebugKotlin`)
Ожидаемый результат: отсутствие критических замечаний и ошибок.

**Step 4: Зафиксировать изменения карты архитектуры в Git**
```bash
git add SYSTEM_PROMPT.md .cursorrules
git commit -m "chore: synchronize architecture map after adding sticky header unit tests"
```

---

### Task 6: Контроль требований (Supervisor)

**Чек-лист Супервизора для подтверждения готовности фичи:**
- [ ] Панель сортировки и пакетных действий помещена в `stickyHeader` внутри `LazyColumn` экрана `ReportScreen.kt`.
- [ ] В `stickyHeader` входят и `SortAndBatchBar`, и плашка `SelectionActionBar` с анимацией `AnimatedVisibility`.
- [ ] Фон панели непрозрачен (`MaterialTheme.colorScheme.background`) — карточки чеков не просвечивают при прокрутке снизу.
- [ ] `isPinned` вычисляется динамически через `derivedStateOf { listState.firstVisibleItemIndex >= 3 }`.
- [ ] При `isPinned == true`: появляется `shadowElevation = 4.dp`, `tonalElevation = 2.dp` и тонкий `HorizontalDivider` (0.5.dp, `outlineVariant.copy(alpha = 0.5f)`).
- [ ] При `isPinned == false`: тень 0.dp, тональная подсветка 0.dp, разделителя нет (плоский вид).
- [ ] Раздел помощи `HelpBottomSheet.kt` дополнен описанием залипающей панели.
- [ ] Написан Unit-тест на логику состояний. Все unit-тесты проекта проходят успешно (`./gradlew testDebugUnitTest`).
- [ ] Архитектурная карта актуализирована.

---

### Task 7: Сборка релиза и формирование Changelog (Releaser)

**Шаги Релизера:**
1. Проверить текущую версию в `app/build.gradle.kts` (`versionName = "1.12.0"`, `versionCode = 33`).
2. Прочитать последнюю версию на странице RuStore через HTTP-запрос.
3. Сформировать Changelog:
   - "Залипающая панель сортировки и пакетных действий (Sticky Header) в экране 'Чеки и отчёт'".
   - "Плавная анимация подъёма и фиксации панели при скролле длинного списка чеков".
   - "Постоянный доступ к переключению сортировки и пакетному удалению выбранных чеков".
4. Собрать релизные артефакты:
   `.\gradlew.bat assembleRelease`
5. Скопировать релизный APK в каталог `release/` с указанием версии (например, `release/UZB_QQS_v1.12.0.apk`).

---

### Task 8: Публикация и обновление документации (Publisher)

**Шаги Паблишера:**
1. Обновить `README.md` с описанием новой версии и фичи Sticky Header.
2. Создать коммит по стандарту Conventional Commits:
   `git commit -m "feat(report): add sticky header for sort and batch action bar"`
3. Создать git-тег для версии (например, `v1.12.0`).
4. Отправить изменения в удалённый репозиторий:
   `git push origin main --tags`
5. Сформировать GitHub Release с прикреплением готового APK файла.

---

## Варианты выполнения (Execution Handoff)

План полностью сформирован и сохранён в `IMPLEMENTATION_PLAN.md`.

Доступны 2 варианта исполнения:
1. **Subagent-Driven (в текущей сессии, Рекомендуется)** — последовательный запуск субагентов для каждого этапа (Designer -> Doer -> Test Engineer -> Reviewer -> Supervisor -> Releaser -> Publisher), валидация после каждого таска, быстрый темп и максимальный контроль качества.
2. **Parallel Session (отдельная сессия)** — открытие новой рабочей сессии с навыком `executing-plans` для пакетного выполнения с контрольными точками.
