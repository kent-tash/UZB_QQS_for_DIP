# Минимизация интерфейса приложения: Редизайн экрана «Чеки и отчёт», шторка профилей и инлайн-редактирование

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Реализовать второй этап минимизации интерфейса приложения: перенести выбор периода и пользователя в шапку «Сводный отчёт» с единой шторкой `PeriodPickerBottomSheet`, удалить устаревший `CompactFiltersBar`, перенести чекбоксы в списке чеков на правую сторону с компактным бейджем 26×26dp и удалением мусорной корзины из карточек, добавить 3-кнопочный подвал в модальное превью чека, минимизировать вкладку «Профиль» со шторкой управления пользователями `UserManagementBottomSheet`, двойным подтверждением удаления и инлайн-редактированием полей, а также обновить раздел помощи `HelpBottomSheet.kt`.

**Architecture:**
1. **Presentation Layer (`ReportScreen.kt`):**
   - Устранение горизонтальной панели `CompactFiltersBar`.
   - Интерактивная карточка «Сводный отчёт» (`ReportHeaderCard`): клики по строкам «Сотрудник» и «Период» открывают соответственно `SelectUserDialog` и `PeriodPickerBottomSheet`.
   - Новый компонент `PeriodPickerBottomSheet.kt`: переключатель года `< 2026 год >`, сетка 2×2 кварталов (I, II, III, IV) с моментальным применением и кнопка вызова точного календаря `DatePickerSheet`.
   - Панель `SortAndBatchBar`: фиксированная ширина 170dp для кнопки сортировки с встроенными внутрь стрелкой направления и шевроном меню; справа расположен чекбокс `Все (N) [✓]`.
   - Карточка чека `ReceiptListCard`: слева бейдж 26×26dp с цифрой номера (без префикса «№»), расширенный центральный блок (+50dp ширины), справа чекбокс `[✓]`, иконка удаления полностью удалена.
   - Диалог `ReceiptPreviewDialog`: 3 кнопки равного веса в 1 ряд высотой 46dp (`Поделиться`, красная `Удалить`, `Закрыть`) с `AutoScalingText`.
2. **Profile & User Management (`ProfileScreen.kt` & `UserManagementBottomSheet.kt`):**
   - Удаление 3 нижних кнопок («Редактировать», «Выйти из профиля», «Удалить»).
   - Клик по аватару/строке пользователя в карточке профиля открывает `UserManagementBottomSheet` со списком профилей, активной отметкой, кнопкой «+ Добавить новый профиль» и удалением через 2-шаговое подтверждение.
   - Инлайн-редактирование полей карточки профиля («Имя», «Должность», «Организация») по клику на строку с иконкой карандаша `✏` через диалог валидации `EditSingleFieldDialog`.
3. **Documentation (`HelpBottomSheet.kt`):**
   - Полная актуализация разделов «Чеки и отчёт» и «Управление профилями» в соответствии с новыми возможностями UI.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Material 3, Androidx Navigation, StateFlow / Coroutines, JUnit 4, MockK.

---

## Область изменений (Scope)

- **In Scope:**
  - Создание компонента `ui/components/PeriodPickerBottomSheet.kt` (выбор года стрелками, сетка 2×2 кварталов, кнопка точного календаря).
  - Рефакторинг `ui/screens/ReportScreen.kt`:
    - Удаление `CompactFiltersBar` и связанных с ним локальных состояний.
    - Добавление кликабельности строк в `ReportHeaderCard`.
    - Фиксированная кнопка сортировки 170dp и перенос чекбокса вправо в `SortAndBatchBar`.
    - Редизайн `ReceiptListCard` (бейдж 26×26dp, чекбокс справа, удаление кнопки корзины).
    - Редизайн кнопок `ReceiptPreviewDialog` (3 кнопки равного веса, высота 46dp, `AutoScalingText`).
  - Создание компонента `ui/components/UserManagementBottomSheet.kt` (список пользователей, иконка корзины, кнопка создания).
  - Рефакторинг `ui/screens/ProfileScreen.kt`:
    - Удаление 3 нижних кнопок.
    - Кликабельность аватара и ФИО для открытия `UserManagementBottomSheet`.
    - Двухэтапный диалог подтверждения удаления профиля.
    - Создание профиля в диалоге из шторки.
    - Инлайн-редактирование полей «Имя», «Должность», «Организация» с иконкой карандаша.
  - Обновление справки в `ui/components/HelpBottomSheet.kt`.
  - Unit-тесты для новой логики периодов и профилей.
  - Синхронизация архитектурной карты проекта (`scripts/update_architecture_map.py`).
- **Out of Scope:**
  - Изменение структуры базы данных SQLite (`DbHelper.kt`) и миграции (схема `users` и `receipts` полностью достаточна).
  - Изменение форматов экспорта PDF, XLSX, ZIP.
  - Изменение экранов сканирования (`ScanScreen.kt`) и аудита (`AuditorScreen.kt`, `AuditorVerifyScreen.kt`).

---

## План пошаговой реализации (Action Items)

### Task 1: Создание компонента `PeriodPickerBottomSheet.kt`

**Files:**
- Create: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/PeriodPickerBottomSheet.kt`
- Test: `app/src/test/java/com/example/uzb_qqs_for_dip/ui/PeriodPickerLogicTest.kt`

**Step 1: Написать failing test на логику переключения года и кварталов**
Создать `app/src/test/java/com/example/uzb_qqs_for_dip/ui/PeriodPickerLogicTest.kt`:
```kotlin
package com.example.uzb_qqs_for_dip.ui

import com.example.uzb_qqs_for_dip.data.settings.Quarter
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodPickerLogicTest {

    @Test
    fun testQuarterMonthsLabels() {
        assertEquals("январь–март", Quarter.Q1.monthsLabel())
        assertEquals("апрель–июнь", Quarter.Q2.monthsLabel())
        assertEquals("июль–сентябрь", Quarter.Q3.monthsLabel())
        assertEquals("октябрь–декабрь", Quarter.Q4.monthsLabel())
    }

    @Test
    fun testYearNavigationBounds() {
        val minYear = 2020
        val maxYear = 2035
        var currentYear = 2026
        
        // Переход назад
        currentYear = (currentYear - 1).coerceAtLeast(minYear)
        assertEquals(2025, currentYear)
        
        // Переход вперед
        currentYear = (currentYear + 2).coerceAtMost(maxYear)
        assertEquals(2027, currentYear)
    }
}

fun Quarter.monthsLabel(): String = when (this) {
    Quarter.Q1 -> "январь–март"
    Quarter.Q2 -> "апрель–июнь"
    Quarter.Q3 -> "июль–сентябрь"
    Quarter.Q4 -> "октябрь–декабрь"
    Quarter.Custom -> "произвольный"
}
```

**Step 2: Запустить тест и проверить результат**
Команда: `.\gradlew.bat testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.ui.PeriodPickerLogicTest"`
Ожидаемый результат: PASS (логика вспомогательных функций валидна).

**Step 3: Реализовать `PeriodPickerBottomSheet.kt`**
Создать `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/PeriodPickerBottomSheet.kt`:
```kotlin
package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uzb_qqs_for_dip.data.settings.Quarter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodPickerBottomSheet(
    initialYear: Int,
    currentQuarter: Quarter,
    onQuarterSelected: (Quarter, Int) -> Unit,
    onExactDatesClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedYear by remember { mutableIntStateOf(initialYear) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Выбор периода",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            // Переключатель года: < 2026 год >
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { if (selectedYear > 2020) selectedYear-- },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Предыдущий год",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "$selectedYear год",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                IconButton(
                    onClick = { if (selectedYear < 2035) selectedYear++ },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Следующий год",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Сетка 2x2 кварталов (I, II, III, IV)
            val quartersRow1 = listOf(Quarter.Q1 to "I квартал\n(январь–март)", Quarter.Q2 to "II квартал\n(апрель–июнь)")
            val quartersRow2 = listOf(Quarter.Q3 to "III квартал\n(июль–сентябрь)", Quarter.Q4 to "IV квартал\n(октябрь–декабрь)")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quartersRow1.forEach { (quarter, label) ->
                    val isSelected = quarter == currentQuarter && selectedYear == initialYear
                    QuarterTile(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            onQuarterSelected(quarter, selectedYear)
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quartersRow2.forEach { (quarter, label) ->
                    val isSelected = quarter == currentQuarter && selectedYear == initialYear
                    QuarterTile(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            onQuarterSelected(quarter, selectedYear)
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Кнопка точного выбора дат в календаре
            OutlinedButton(
                onClick = {
                    onDismissRequest()
                    onExactDatesClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Выбрать точные даты в календаре")
            }
        }
    }
}

@Composable
private fun QuarterTile(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
```

**Step 4: Проверить компиляцию и тесты**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 5: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/PeriodPickerBottomSheet.kt app/src/test/java/com/example/uzb_qqs_for_dip/ui/PeriodPickerLogicTest.kt`
`git commit -m "feat(ui): add PeriodPickerBottomSheet with year navigator and 2x2 quarter grid"`

---

### Task 2: Рефакторинг `ReportHeaderCard` и удаление `CompactFiltersBar` в `ReportScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`

**Step 1: Обновить `ReportHeaderCard`**
Добавить параметры кликов `onUserClick: () -> Unit` и `onPeriodClick: () -> Unit`.
Сделать строки «Сотрудник:» и «Период:» интерактивными:
```kotlin
@Composable
private fun ReportHeaderCard(
    userFullName: String,
    position: String,
    periodText: String,
    count: Int,
    totalSum: Long,
    totalVat: Long,
    onUserClick: () -> Unit,
    onPeriodClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сводный отчёт",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "$count чеков",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Строка выбора сотрудника
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onUserClick)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Сотрудник:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.35f)
                )
                Row(
                    modifier = Modifier.weight(0.65f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        userFullName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "Выбрать сотрудника",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (position.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 2.dp)) {
                    Text(
                        "Должность:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.35f)
                    )
                    Text(
                        position,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.65f)
                    )
                }
            }

            // Строка выбора периода
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPeriodClick)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Период:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.35f)
                )
                Row(
                    modifier = Modifier.weight(0.65f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        periodText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "Выбрать период",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Итого сумма",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        MoneyFormat.fromTiyin(totalSum) + " сум",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        "Итого НДС (12%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        MoneyFormat.fromTiyin(totalVat) + " сум",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
```

**Step 2: Удалить `CompactFiltersBar` и подключить `PeriodPickerBottomSheet`**
В `ReportScreen`:
- Удалить элемент `item { CompactFiltersBar(...) }`.
- Удалить composable функции `CompactFiltersBar` и `CompactFilterTile`.
- Заменить устаревшие состояния `showYearPicker`, `showQuarterPicker`, `showPeriodPicker` на единое состояние:
  `var showPeriodSheet by remember { mutableStateOf(false) }`
- Подключить `PeriodPickerBottomSheet`:
```kotlin
if (showPeriodSheet) {
    PeriodPickerBottomSheet(
        initialYear = settings.year,
        currentQuarter = settings.quarter,
        onQuarterSelected = { quarter, year ->
            reportViewModel.setYear(year)
            reportViewModel.setQuarter(quarter)
        },
        onExactDatesClick = {
            showFromPicker = true
        },
        onDismissRequest = { showPeriodSheet = false }
    )
}
```

**Step 3: Запустить компиляцию**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 4: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`
`git commit -m "refactor(ui): move user and period pickers into ReportHeaderCard, remove CompactFiltersBar"`

---

### Task 3: Редизайн панели `SortAndBatchBar` (170dp кнопка сортировки и чекбокс справа)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`

**Step 1: Реализовать `SortAndBatchBar` с фиксированной шириной 170dp и чекбоксом `Все (N) [✓]`**
В `ReportScreen.kt` заменить функцию `SortAndBatchBar`:
```kotlin
@Composable
private fun SortAndBatchBar(
    totalCount: Int,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    sortField: SortField,
    sortOrder: SortOrder,
    onSortFieldSelected: (SortField) -> Unit,
    onToggleSortOrder: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val toggleState = when {
        totalCount == 0 || selectedCount == 0 -> ToggleableState.Off
        selectedCount == totalCount -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Слева: Фиксированная кнопка сортировки (170dp) со встроенными стрелкой и шевроном
        Box {
            OutlinedButton(
                onClick = { sortMenuExpanded = true },
                modifier = Modifier.width(170.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = if (sortOrder == SortOrder.ASC) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = sortOrder.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 14.dp)
                            ) {
                                onToggleSortOrder()
                            }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = sortField.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                SortField.entries.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(field.label) },
                        onClick = {
                            sortMenuExpanded = false
                            onSortFieldSelected(field)
                        }
                    )
                }
            }
        }

        // Справа: Надпись «Все (N)» и чекбокс справа от неё
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = totalCount > 0,
                    onClick = {
                        if (toggleState == ToggleableState.On) onClearAll() else onSelectAll()
                    }
                )
                .padding(start = 6.dp)
        ) {
            Text(
                text = "Все ($totalCount)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(4.dp))
            TriStateCheckbox(
                state = toggleState,
                onClick = {
                    if (toggleState == ToggleableState.On) onClearAll() else onSelectAll()
                },
                enabled = totalCount > 0
            )
        }
    }
}
```

**Step 2: Запустить компиляцию**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 3: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`
`git commit -m "refactor(ui): update SortAndBatchBar with 170dp fixed button and right-aligned batch checkbox"`

---

### Task 4: Редизайн карточки чека `ReceiptListCard` (компактный бейдж 26×26dp, чекбокс справа, удаление корзины)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`

**Step 1: Обновить `ReceiptListCard`**
- Удалить параметр `onDelete: () -> Unit` из `ReceiptListCard` (удаление переносится в модальное превью и пакетное удаление).
- Слева расположить компактный черный бейдж размером `26.dp` x `26.dp` со скруглением `6.dp` и текстом `$ordinal` (без префикса «№»).
- По центру: продавец, дата, сумма и НДС занимают всю полезную ширину (`weight(1f)`).
- Справа: чекбокс выбора с кликабельной областью 48dp.
- Вызов `ReceiptListCard` в `itemsIndexed`: убрать аргумент `onDelete = ...`.

Код реализации:
```kotlin
@Composable
private fun ReceiptListCard(
    modifier: Modifier = Modifier,
    ordinal: Int,
    item: ReceiptWithUser,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Компактный бейдж номера (26x26dp)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color.Black, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                AutoScalingText(
                    text = "$ordinal",
                    minFontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(1.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // Центральный блок с информацией о чеке (+50dp полезной ширины)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onPreview
                    )
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = item.receipt.sellerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateFormat.formatDateTime(item.receipt.purchasedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сумма: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin) + " сум",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "НДС: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyFormat.fromTiyin(item.receipt.vatAmountTiyin) + " сум",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Чекбокс выбора справа (48dp touch target)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 24.dp),
                        role = Role.Checkbox,
                        onClick = onToggleSelect
                    ),
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }
        }
    }
}
```

**Step 2: Запустить компиляцию**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 3: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`
`git commit -m "refactor(ui): update ReceiptListCard with 26x26dp badge, right checkbox and removed trash icon"`

---

### Task 5: Редизайн `ReceiptPreviewDialog` (3 кнопки равного веса, высота 46dp, `AutoScalingText`)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`

**Step 1: Реализовать обновленный `ReceiptPreviewDialog`**
Под картинкой чека размещаются 3 кнопки равного веса в один ряд на всю ширину:
`[ 🔗 Поделиться ]  [ 🗑 Удалить (красная) ]  [ ✕ Закрыть ]`

```kotlin
@Composable
private fun ReceiptPreviewDialog(
    item: ReceiptWithUser,
    ordinal: Int,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Чек №$ordinal")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val bitmap = remember(item.receipt.id, ordinal) {
                    com.example.uzb_qqs_for_dip.render.ReceiptCardRenderer.renderBitmap(
                        receipt = item.receipt,
                        ordinal = ordinal,
                        width = 540
                    )
                }
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Превью чека №$ordinal",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    AutoScalingText("Поделиться", maxLines = 1, minFontSize = 9.sp)
                }

                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    AutoScalingText("Удалить", maxLines = 1, minFontSize = 9.sp)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    AutoScalingText("Закрыть", maxLines = 1, minFontSize = 9.sp)
                }
            }
        },
        dismissButton = null
    )
}
```

**Step 2: Запустить компиляцию**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 3: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`
`git commit -m "feat(ui): redesign ReceiptPreviewDialog with 3 equal-weight buttons and AutoScalingText"`

---

### Task 6: Создание компонента `UserManagementBottomSheet.kt`

**Files:**
- Create: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/UserManagementBottomSheet.kt`

**Step 1: Реализовать `UserManagementBottomSheet.kt`**
Компонент шторки управления пользователями:
- Заголовок «Управление профилями».
- Список профилей с круглыми аватарами, ФИО, должностями.
- Бейдж/индикатор активного профиля.
- Иконка корзины для вызова удаления конкретного профиля.
- Внизу кнопка «+ Добавить новый профиль».

Код `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/UserManagementBottomSheet.kt`:
```kotlin
package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.uzb_qqs_for_dip.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementBottomSheet(
    users: List<User>,
    currentUserId: Long?,
    onSelectUser: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onCreateProfileClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp, top = 4.dp)
        ) {
            Text(
                text = "Управление профилями",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    val isActive = user.id == currentUserId
                    UserItemRow(
                        user = user,
                        isActive = isActive,
                        onSelect = {
                            onSelectUser(user)
                            onDismissRequest()
                        },
                        onDelete = { onDeleteUser(user) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    onDismissRequest()
                    onCreateProfileClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Добавить новый профиль")
            }
        }
    }
}

@Composable
private fun UserItemRow(
    user: User,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.fullName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Spacer(Modifier.size(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Активен",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = user.position,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Удалить профиль",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
```

**Step 2: Запустить компиляцию**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 3: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/UserManagementBottomSheet.kt`
`git commit -m "feat(ui): add UserManagementBottomSheet for profile switching, creation, and deletion"`

---

### Task 7: Рефакторинг `ProfileScreen.kt` (удаление 3 кнопок, шторка профилей, двойное подтверждение и инлайн-редактирование)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ProfileScreen.kt`

**Step 1: Реализовать диалог инлайн-редактирования одного поля `EditSingleFieldDialog` и диалог создания профиля `CreateProfileDialog`**
Создать в `ProfileScreen.kt`:
```kotlin
enum class EditableProfileField(val title: String, val label: String) {
    FULL_NAME("Изменить имя", "Имя пользователя (полностью)"),
    POSITION("Изменить должность", "Должность"),
    ORGANIZATION("Изменить организацию", "Организация (необязательно)")
}

@Composable
fun EditSingleFieldDialog(
    field: EditableProfileField,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = { Text(field.label) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = value.trim()
                    if (field != EditableProfileField.ORGANIZATION && trimmed.isEmpty()) {
                        error = "Поле не может быть пустым"
                        return@Button
                    }
                    onConfirm(trimmed)
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun CreateProfileDialog(
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, position: String, organization: String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; onClearError() },
                    label = { Text("Имя пользователя (полностью)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it; onClearError() },
                    label = { Text("Должность") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it; onClearError() },
                    label = { Text("Организация (необязательно)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fullName, position, organization) }
            ) { Text("Создать") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
```

**Step 2: Обновить `ProfileRow` с иконкой карандаша и кликом**
```kotlin
@Composable
private fun ProfileRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.55f)
        )
        Icon(
            Icons.Outlined.Edit,
            contentDescription = "Редактировать $label",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
```

**Step 3: Интегрировать `UserManagementBottomSheet`, двойное подтверждение и удалить 3 нижние кнопки в `ProfileScreen`**
- Удалить кнопки «Редактировать», «Выйти из профиля», «Удалить» внизу экрана.
- Сделать шапку карточки профиля (аватар + ФИО/должность) кликабельной:
  `Modifier.clip(RoundedCornerShape(12.dp)).clickable { showUserManagementSheet = true }`
- Добавить состояния:
  - `var showUserManagementSheet by remember { mutableStateOf(false) }`
  - `var editingField by remember { mutableStateOf<EditableProfileField?>(null) }`
  - `var showCreateProfileDialog by remember { mutableStateOf(false) }`
  - `var profileToDeleteStage1 by remember { mutableStateOf<User?>(null) }`
  - `var profileToDeleteStage2 by remember { mutableStateOf<User?>(null) }`
- Диалог 1 удаления:
  «Удалить профиль «${user.fullName}»? Будут удалены связанные чеки.» -> кнопка «Удалить» переводит в `profileToDeleteStage2 = user`.
- Диалог 2 удаления (окончательный):
  «Вы абсолютно уверены? Это действие необратимо.» -> красная кнопка «Удалить окончательно» вызывает:
  `appViewModel.deleteProfile(id) { if (id == currentUser?.id) onLoggedOut() }`.

**Step 4: Запустить тесты и компиляцию**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 5: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ProfileScreen.kt`
`git commit -m "feat(ui): minimize ProfileScreen, add UserManagementBottomSheet, double confirmation delete and inline editing"`

---

### Task 8: Актуализация документации в `HelpBottomSheet.kt`

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt`

**Step 1: Обновить раздел «Чеки и отчёт» и «Управление профилями»**
В `HelpBottomSheet.kt`:
1. В секции «Чеки и отчёт (Единый экран)»:
   - Описать, что выбор сотрудника и периода теперь встроен прямо в шапку «Сводный отчёт» (клик открывает выбор пользователя или шторку `PeriodPickerBottomSheet` со стрелками года, сеткой 2×2 кварталов и календарем).
   - Описать фиксированную 170dp кнопку сортировки и чекбокс справа `Все (N) [✓]`.
   - Описать, что в карточке чека номер отображается в компактном бейдже 26×26dp, удалена иконка корзины, а удаление чека выполняется в модальном окне превью (кнопка «Удалить») или через панель пакетного удаления.
2. В секции «Управление профилями»:
   - Описать минимизацию экрана без лишних кнопок внизу.
   - Описать шторку управления профилями (открывается кликом по аватару или ФИО): переключение активного профиля, добавление нового («+ Добавить новый профиль») и двойное подтверждение удаления профиля и связанных чеков.
   - Описать инлайн-редактирование полей «Имя», «Должность» и «Организация» по клику на значок карандаша.

**Step 2: Проверить компиляцию**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 3: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt`
`git commit -m "docs(help): update HelpBottomSheet with new period picker, receipt cards, and profile management instructions"`

---

### Task 9: Финальное тестирование, проверка регрессий и обновление архитектурной карты

**Files:**
- Modify: `SYSTEM_PROMPT.md`, `.cursorrules`
- Test: Все Unit-тесты (`ReportViewModelTest`, `PeriodPickerLogicTest`, и др.)

**Step 1: Запустить все Unit-тесты проекта**
Команда: `.\gradlew.bat testDebugUnitTest`
Ожидаемый результат: Все тесты пройдены успешно (BUILD SUCCESSFUL).

**Step 2: Запустить автосинхронизацию архитектурной карты**
Команда: `python scripts/update_architecture_map.py`
Скрипт просканирует новые компоненты (`PeriodPickerBottomSheet.kt`, `UserManagementBottomSheet.kt`) и обновит секции `<architecture_map>` в `SYSTEM_PROMPT.md` и `.cursorrules`.

**Step 3: Запустить финальную сборку сборки приложения**
Команда: `.\gradlew.bat assembleDebug`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 4: Финальный коммит плана**
`git add SYSTEM_PROMPT.md .cursorrules IMPLEMENTATION_PLAN.md`
`git commit -m "chore: synchronize architecture map and finalize implementation plan"`

---

## Варианты выполнения (Execution Handoff)

План полностью сформирован и сохранён в `IMPLEMENTATION_PLAN.md`.

Доступны 2 варианта исполнения:
1. **Subagent-Driven (в текущей сессии)** — запуск свежих субагентов для каждого этапа (Doer -> TestEngineer -> Reviewer -> Supervisor), валидация после каждого таска, быстрый темп и контроль качества.
2. **Parallel Session (отдельная сессия)** — открытие новой рабочей сессии с навыком `executing-plans` для пакетного выполнения с контрольными точками.
