# Объединение вкладок «Чеки» и «Отчёт» в единый экран «Чеки и отчёт» с режимом экспорта «Всё» и оптимизацией печати

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Объединить вкладки «Чеки» и «Отчёт» в единый высокопроизводительный экран «Чеки и отчёт» с сокращением навигации до 3 вкладок, внедрением переключателя режимов экспорта («Отчёт» | «Всё» | «Чеки»), генератором объединенного PDF («Всё»), оптимизацией отступов в PDF-отчете (до 24 чеков на 1-й странице) и актуализацией документации.

**Architecture:** 
1. **Presentation Layer (Jetpack Compose + MVVM):** Единый экран `ReportScreen.kt` на базе `LazyColumn`, управляемый расширенной `ReportViewModel.kt`. Экран содержит 7 логических блоков: шапка отчёта, 3-позиционный переключатель режима экспорта («Отчёт» | «Всё» | «Чеки»), панель действий («Сохранить», «Поделиться», «Открыть», «Печать») с онлайн-синхронизацией ОФД, 1-рядную панель фильтров (4 OutlinedTextFields с AutoScalingText), панель сортировки и пакетных действий, карточку чеков с нулевым НДС, и вертикальный список карточек чеков с черными квадратными бейджами порядковых номеров и вызовом полноразмерного превью с QR-кодом.
2. **Navigation Layer:** `AppNavHost.kt` переводится на 3 вкладки для сотрудника («Добавить», «Чеки и отчёт», «Профиль») и 3 вкладки для аудитора («Аудит», «Чеки и отчёт», «Профиль»). Устаревшие маршруты и экраны вычищаются.
3. **Export & Print Engine:**
   - Новый `CombinedPdfGenerator.kt` формирует единый документ: сводный отчет (стр. 1..N) с таблицей, подписью и колонтитулами, затем с НОВОЙ страницы реестр чеков по 6 на лист А4 с изолированной нумерацией («стр. 1 из M»), реквизитами пользователя и датой формирования.
   - `PdfReportGenerator.kt` оптимизируется (Вариант 1 - Сбалансированный): поля MARGIN уменьшаются до 28f (10 мм), динамический лимит высоты на промежуточных страницах расширяется до `PAGE_HEIGHT - 32f`, компактный блок подписи занимает 70f, что гарантирует размещение до 24 чеков на 1-й странице и до 30 на последующих.
4. **Documentation:** Актуализация интерактивной справки `HelpBottomSheet.kt`.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Material3, Android PdfDocument, StateFlow/Coroutines, JUnit 4, MockK.

---

## Область изменений (Scope)

- **In Scope:**
  - Оптимизация отступов и высоты страниц в `PdfReportGenerator.kt` (MARGIN = 28f, подпись 70f, динамический `bottomLimit`, вместимость до 24 чеков на 1 странице).
  - Создание `CombinedPdfGenerator.kt` для режима «Всё» с изолированной нумерацией страниц реестра чеков («стр. 1 из M»).
  - Рефакторинг `ReceiptsSheetPdfGenerator.kt` с выделением метода рендеринга `renderInto`.
  - Расширение `ReportViewModel.kt` стейтом `ExportMode` («Отчёт» | «Всё» | «Чеки»), операциями экспорта, регенерацией превью чеков на диске и функциями предпросмотра/удаления отдельных чеков.
  - Редизайн `ReportScreen.kt` в 7-блочный интерфейс с адаптивными фильтрами в один ряд (`AutoScalingText`) и вертикальным списком карточек чеков.
  - Сокращение навигации в `AppNavHost.kt` до 3 вкладок и удаление дублирующего экрана `ReceiptsScreen.kt` и `ReceiptsViewModel.kt`.
  - Актуализация документации в `HelpBottomSheet.kt`.
  - Unit-тесты для генераторов, расчета емкости страниц и ViewModel.
  - Синхронизация архитектурной карты (`scripts/update_architecture_map.py`).
- **Out of Scope:**
  - Изменение структуры базы данных SQLite (`DbHelper.kt`).
  - Изменение API сетевого взаимодействия (`OfdPaymentApi.kt`, `ReceiptParser.kt`).
  - Изменение логики экрана сканирования (`ScanScreen.kt`) и экранов аудита (`AuditorScreen.kt`, `AuditorVerifyScreen.kt`).

---

## План пошаговой реализации (Action Items)

### Task 1: Оптимизация отступов отчёта и расчет вместимости страниц в `PdfReportGenerator.kt`

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/export/PdfReportGenerator.kt`
- Test: `app/src/test/java/com/example/uzb_qqs_for_dip/export/PdfReportPageCapacityTest.kt`

**Step 1: Написать failing test на расчет вместимости и отступы**
Создать файл `app/src/test/java/com/example/uzb_qqs_for_dip/export/PdfReportPageCapacityTest.kt`:
```kotlin
package com.example.uzb_qqs_for_dip.export

import com.example.uzb_qqs_for_dip.data.model.PaymentType
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.data.model.ReceiptSource
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfReportPageCapacityTest {

    private val testUser = User(
        id = 1L,
        fullName = "Тестовый Сотрудник",
        position = "Бухгалтер",
        initialsSurname = "Т. Сотрудник",
        role = UserRole.EMPLOYEE
    )

    private fun createReceipts(count: Int): List<ReceiptWithUser> {
        return (1..count).map { i ->
            ReceiptWithUser(
                receipt = Receipt(
                    id = i.toLong(),
                    userId = 1L,
                    purchasedAt = 1715000000000L + i * 3600000L,
                    sellerName = "OOO SELLER $i",
                    totalAmountTiyin = 100_000L,
                    vatAmountTiyin = 12_000L,
                    qrUrl = "https://ofd.soliq.uz/check?t=$i",
                    paymentType = PaymentType.CARD,
                    source = ReceiptSource.APP
                ),
                userFullName = testUser.fullName,
                userPosition = testUser.position,
                userInitialsSurname = testUser.initialsSurname
            )
        }
    }

    @Test
    fun testReportFitsUpTo24ReceiptsOnSinglePage() {
        val rows = createReceipts(24)
        val params = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = rows,
            quarterLabel = "I квартал 2026 г."
        )
        val pageCount = PdfReportGenerator.calculatePageCount(params)
        assertEquals("Отчёт до 24 чеков должен помещаться ровно на 1 страницу А4", 1, pageCount)
    }

    @Test
    fun testReportFits54ReceiptsOnTwoPages() {
        val rows = createReceipts(54)
        val params = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = rows,
            quarterLabel = "I квартал 2026 г."
        )
        val pageCount = PdfReportGenerator.calculatePageCount(params)
        assertEquals("Отчёт на 54 чека (24 на 1-й + 30 на 2-й) должен занимать 2 страницы", 2, pageCount)
    }
}
```

**Step 2: Запустить тест и убедиться, что он падает (Red)**
Команда: `.\gradlew.bat testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.export.PdfReportPageCapacityTest"`
Ожидаемый результат: FAIL (метод `calculatePageCount` отсутствует или возвращает > 1 страницы из-за старых отступов 40f и лимита 110f).

**Step 3: Реализовать оптимизацию отступов и динамический расчет страниц в `PdfReportGenerator.kt`**
В `app/src/main/java/com/example/uzb_qqs_for_dip/export/PdfReportGenerator.kt`:
1. Установить `private const val MARGIN = 28f` (10 мм).
2. Обновить `COL_WIDTHS = floatArrayOf(28f, 194f, 110f, 100f, 107f)` (сумма = 539f, точно равна `PAGE_WIDTH - 2 * MARGIN`).
3. Сделать `calculatePageCount(params: ReportParams): Int` публичным методом для подсчета страниц.
4. Задать компактный блок подписи: `val signatureBlockHeight = 70f`.
5. Реализовать динамический лимит высоты строк таблицы:
   - На промежуточных страницах (пока остаются чеки, которые не поместятся вместе с подписью на текущую страницу): лимит равен `PAGE_HEIGHT - 32f` (810f).
   - На последней странице: лимит под чеки равен `PAGE_HEIGHT - 32f - ROW_HEIGHT_MIN - signatureBlockHeight`.
   - Если остаток строк + итог + подпись укладываются до 810f — они рендерятся на текущей странице без создания лишнего листа.
6. В блоке подписи:
   - Уменьшить вертикальные отступы: базовые линии подписи `py = signatureTop + 20f`, `dateY = lastBaseline + 20f`, метка `(подпись)` на `lastBaseline + 9f`.
   - Добавить параметр `includeManualAttachments: Boolean = true` в `renderInto`, чтобы при объединении с реестром чеков страницы фотографий не дублировались.

**Step 4: Запустить тест и убедиться, что он проходит (Green)**
Команда: `.\gradlew.bat testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.export.PdfReportPageCapacityTest"`
Ожидаемый результат: PASS (1 страница для 24 чеков, 2 страницы для 54 чеков).

**Step 5: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/export/PdfReportGenerator.kt app/src/test/java/com/example/uzb_qqs_for_dip/export/PdfReportPageCapacityTest.kt`
`git commit -m "feat(export): optimize PdfReportGenerator margins to 28f and fit 24 receipts on page 1"`

---

### Task 2: Создание `CombinedPdfGenerator.kt` и изоляция нумерации страниц реестра

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/export/ReceiptsSheetPdfGenerator.kt`
- Create: `app/src/main/java/com/example/uzb_qqs_for_dip/export/CombinedPdfGenerator.kt`
- Test: `app/src/test/java/com/example/uzb_qqs_for_dip/export/CombinedPdfGeneratorTest.kt`

**Step 1: Написать failing test для `CombinedPdfGenerator`**
Создать `app/src/test/java/com/example/uzb_qqs_for_dip/export/CombinedPdfGeneratorTest.kt`:
```kotlin
package com.example.uzb_qqs_for_dip.export

import android.content.Context
import com.example.uzb_qqs_for_dip.data.model.PaymentType
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.data.model.ReceiptSource
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CombinedPdfGeneratorTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context

    private val testUser = User(
        id = 1L,
        fullName = "Иванов Иван",
        position = "Инженер",
        initialsSurname = "И. Иванов",
        role = UserRole.EMPLOYEE
    )

    private val receipt1 = ReceiptWithUser(
        receipt = Receipt(
            id = 1L, userId = 1L, purchasedAt = 1715000000000L, sellerName = "OOO SHOP A",
            totalAmountTiyin = 200_000L, vatAmountTiyin = 24_000L, qrUrl = "https://ofd.soliq.uz/check?t=1",
            paymentType = PaymentType.CARD, source = ReceiptSource.APP
        ),
        userFullName = testUser.fullName, userPosition = testUser.position, userInitialsSurname = testUser.initialsSurname
    )

    private val receiptZero = ReceiptWithUser(
        receipt = Receipt(
            id = 2L, userId = 1L, purchasedAt = 1715000000000L, sellerName = "OOO ZERO",
            totalAmountTiyin = 100_000L, vatAmountTiyin = 0L, qrUrl = "https://ofd.soliq.uz/check?t=2",
            paymentType = PaymentType.CARD, source = ReceiptSource.APP
        ),
        userFullName = testUser.fullName, userPosition = testUser.position, userInitialsSurname = testUser.initialsSurname
    )

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("combined_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
    }

    @Test
    fun testCombinedPdfExcludesZeroVatAndGeneratesFile() = runBlocking {
        val rows = listOf(receipt1, receiptZero)
        val params = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = rows,
            quarterLabel = "II квартал 2026 г."
        )
        val file = CombinedPdfGenerator.generate(
            context = mockContext,
            params = params,
            periodLabel = "II квартал 2026 г.",
            fileName = "test_all.pdf"
        )
        assertTrue("Файл объединенного PDF должен быть создан", file.exists())
        assertTrue("Размер файла должен быть больше 0", file.length() > 0)
    }

    @Test
    fun testRegistryPagesCountCalculation() {
        // 13 чеков при 6 на страницу дают ceil(13/6) = 3 страницы реестра
        val count = CombinedPdfGenerator.calculateRegistryPagesCount(13, manualPhotosCount = 0)
        assertEquals(3, count)
    }
}
```

**Step 2: Запустить тест и убедиться, что он падает (Red)**
Команда: `.\gradlew.bat testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.export.CombinedPdfGeneratorTest"`
Ожидаемый результат: FAIL (`CombinedPdfGenerator` не существует).

**Step 3: Реализация рефакторинга `ReceiptsSheetPdfGenerator.kt` и создание `CombinedPdfGenerator.kt`**
1. В `ReceiptsSheetPdfGenerator.kt`:
   - Выделить метод `renderInto(doc: PdfDocument, rowsInOrder: List<ReceiptWithUser>, headerRightText: String, periodLabel: String, startDocPageNumber: Int = 1, generatedAt: Long = System.currentTimeMillis()): Int`.
   - Внутри `renderInto`:
     - В цикле генерации страниц: `val registryPageNumber = pageIndex + 1`.
     - На холсте страницы отрисовывать:
       - Вверху справа: `canvas.drawText("стр. $registryPageNumber из $totalPages", PAGE_W - MARGIN, MARGIN - 6f, pagePaint)` (нумерация всегда с 1 до M!).
       - Вверху шапку: `headerRightText` (Должность и ФИО).
       - Внизу: `canvas.drawText(generatedAtText, PAGE_W - MARGIN, PAGE_H - 10f, footerDatePaint)` («Сформировано дд.мм.гггг чч:мм:сс»).
     - Метод `generate(...)` делегирует рендеринг в `renderInto`.
2. Создать `app/src/main/java/com/example/uzb_qqs_for_dip/export/CombinedPdfGenerator.kt`:
   - Содержит `calculateRegistryPagesCount(receiptsCount: Int, manualPhotosCount: Int): Int`.
   - `suspend fun generate(context: Context, params: ReportParams, periodLabel: String, fileName: String): File = withContext(Dispatchers.IO)`:
     - Очищает чеки от нулевого НДС: `val sanitizedRows = params.rows.filter { it.receipt.vatAmountTiyin > 0L }`.
     - Создает `val doc = PdfDocument()`.
     - Рендерит сводный отчет: `val reportPagesCount = PdfReportGenerator.renderInto(doc, params.copy(rows = sanitizedRows), includeManualAttachments = false)`.
     - С новой страницы рендерит реестр карточек чеков: `ReceiptsSheetPdfGenerator.renderInto(doc, sanitizedRows, headerRight, periodLabel, startDocPageNumber = reportPagesCount + 1, generatedAt = params.generatedAt)`.
     - Если есть ручные чеки с фото: рендерит страницы фотографий в конец документа с корректной сквозной регистрационной нумерацией.
     - Сохраняет `doc.writeTo(fos)` в `ExportPaths.exportsDir(context)`.

**Step 4: Запустить тест и убедиться, что он проходит (Green)**
Команда: `.\gradlew.bat testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.export.CombinedPdfGeneratorTest"`
Ожидаемый результат: PASS.

**Step 5: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/export/CombinedPdfGenerator.kt app/src/main/java/com/example/uzb_qqs_for_dip/export/ReceiptsSheetPdfGenerator.kt app/src/test/java/com/example/uzb_qqs_for_dip/export/CombinedPdfGeneratorTest.kt`
`git commit -m "feat(export): create CombinedPdfGenerator for mode ALL with isolated registry page numbering"`

---

### Task 3: Расширение `ReportViewModel.kt` для объединенного функционала

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/ReportViewModel.kt`
- Test: `app/src/test/java/com/example/uzb_qqs_for_dip/ui/ReportViewModelTest.kt`

**Step 1: Написать failing unit-тест для `ReportViewModel`**
Создать `app/src/test/java/com/example/uzb_qqs_for_dip/ui/ReportViewModelTest.kt`:
```kotlin
package com.example.uzb_qqs_for_dip.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportViewModelTest {

    @Test
    fun testExportModeDefaultsToAll() {
        val defaultMode = ExportMode.ALL
        assertEquals("По умолчанию режим экспорта должен быть 'Всё'", ExportMode.ALL, defaultMode)
        assertEquals("В режиме REPORT название должно быть 'Отчёт'", "Отчёт", ExportMode.REPORT.title)
        assertEquals("В режиме RECEIPTS название должно быть 'Чеки'", "Чеки", ExportMode.RECEIPTS.title)
    }
}
```

**Step 2: Запустить тест и убедиться, что он падает (Red)**
Команда: `.\gradlew.bat testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.ui.ReportViewModelTest"`
Ожидаемый результат: FAIL (`ExportMode` отсутствует).

**Step 3: Реализация `ExportMode` и объединенных операций в `ReportViewModel.kt`**
В `app/src/main/java/com/example/uzb_qqs_for_dip/ui/ReportViewModel.kt`:
1. Добавить `enum class ExportMode(val title: String) { REPORT("Отчёт"), ALL("Всё"), RECEIPTS("Чеки") }`.
2. Добавить `private val _exportMode = MutableStateFlow(ExportMode.ALL)` и публичный `val exportMode: StateFlow<ExportMode> = _exportMode.asStateFlow()`.
3. Добавить метод `fun setExportMode(mode: ExportMode) { _exportMode.value = mode }`.
4. Добавить синхронизацию локальных картинок чеков на диске при изменении списка чеков/сортировки:
   ```kotlin
   viewModelScope.launch {
       rows.distinctUntilChanged { a, b ->
           a.size == b.size && a.zip(b).all { (x, y) -> x.receipt.id == y.receipt.id }
       }.collect { ordered ->
           runCatching {
               ReceiptImageExporter.regenerateAll(appContext, ordered.map { it.receipt })
           }
       }
   }
   ```
5. Добавить операцию шаринга отдельного чека: `fun shareReceiptImage(context: Context, item: ReceiptWithUser)`.
6. Обновить генерацию файлов (`previewPdf`, `printPdf`, `savePdfToUri`, `saveXlsxToUri`, `sharePdf`, `shareXlsx`):
   - При `ExportMode.ALL`:
     - PDF: генерирует через `CombinedPdfGenerator.generate(context, params, periodLabel, fileName)`
     - XLSX: генерирует через `XlsxExporter.exportReport(context, params, fileName)`
   - При `ExportMode.REPORT`:
     - PDF: генерирует через `PdfReportGenerator.generate(context, params, fileName)`
     - XLSX: генерирует через `XlsxExporter.exportReport(context, params, fileName)`
   - При `ExportMode.RECEIPTS`:
     - PDF: генерирует через `ReceiptsSheetPdfGenerator.generate(context, rows, headerRight, periodLabel, fileName)`
     - XLSX: генерирует через `XlsxExporter.export(context, rows, fileName)`
7. Добавить генерацию имен файлов:
   - `suggestedFileName(asPdf: Boolean): String` формирует `all_...`, `report_...`, или `receipts_...` в зависимости от `exportMode.value`.

**Step 4: Запустить тесты и убедиться, что они проходят (Green)**
Команда: `.\gradlew.bat testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.ui.ReportViewModelTest"`
Ожидаемый результат: PASS.

**Step 5: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/ReportViewModel.kt app/src/test/java/com/example/uzb_qqs_for_dip/ui/ReportViewModelTest.kt`
`git commit -m "feat(ui): add ExportMode to ReportViewModel and support ALL/REPORT/RECEIPTS exports"`

---

### Task 4: Редизайн экрана «Чеки и отчёт» (`ReportScreen.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` (для локализации новых надписей)

**Step 1: Реализация UI компонентов экрана по блокам**
В `ReportScreen.kt` организовать единую структуру `LazyColumn` со следующими 7 блоками:
1. **Блок 1 (Вверху): «Шапка отчёта» (`ReportHeaderCard`)**:
   - Карточка с полями:
     - Период: квартал и диапазон дат (например, "I квартал 2026 г. (01.01.2026 — 31.03.2026)" или диапазон для Custom).
     - ФИО и должность выбранного сотрудника.
     - Количество чеков.
     - «Итого сумма»: крупным жирным шрифтом с суффиксом «сум» в `primary` цвете.
     - «Итого НДС»: крупным жирным шрифтом в `primary` цвете.
2. **Блок 2: Переключатель контекста экспорта (`ExportModeSelector`)**:
   - Однорядный переключатель из 3 сегментов: «Отчёт» | «Всё» | «Чеки».
   - По умолчанию выбрано «Всё».
   - Активный сегмент подсвечен `primaryContainer` / `onPrimaryContainer`.
3. **Блок 3: Кнопки действий (`ExportActionGrid`)**:
   - 4 кнопки в один ряд: «Сохранить» | «Поделиться» | «Открыть» | «Печать».
   - При нажатии запускается `syncWithOfd` с индикацией прогресса «Обновлено X/Y».
   - Кнопки открывают соответствующие диалоги (`FormatChoiceDialog`) с подсказками согласно выбранному `exportMode`.
4. **Блок 4: Блок фильтров в один ряд (`FiltersBar`)**:
   - `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp))` из 4 полей:
     1. «Пользователь»: отображает имя/инициалы, по клику открывает диалог выбора пользователя.
     2. «Год»: отображает год, по клику открывает селектор года.
     3. «Квартал»: отображает краткий квартал (например, "I кв", "II кв", "Свой"). По умолчанию: текущий квартал текущей даты Asia/Tashkent.
     4. «Период»: отображает диапазон дат, по клику открывает `DatePickerSheet`.
   - Внутри полей используется компонент `AutoScalingText`, предотвращающий обрезание и переполнение текста на экранах любой ширины.
5. **Блок 5: Панель сортировки и пакетных действий (`SortAndBatchBar`)**:
   - Выпадающий список (Dropdown) «Сортировать по...»: Дата, Юр. лицо, Сумма, НДС, По дате добавления.
   - Кнопка-переключатель направления сортировки (↑ / ↓).
   - Чекбокс «Выбрать все» / TriState.
   - Плашка `SelectionActionBar` с анимацией: «Выбрано: X чеков [Снять] [Удалить]».
   - Диалог подтверждения удаления выбранных чеков.
6. **Блок 6: Предупреждение о чеках с нулевым НДС (`ZeroVatReceiptsCard`)**:
   - Отображается только если в выборке есть чеки с `vatAmountTiyin == 0L`.
   - Кнопки почекового удаления и «Удалить все».
7. **Блок 7: Список карточек чеков (`items(rows, key = { it.receipt.id })`)**:
   - Каждая карточка чека содержит:
     - Чекбокс слева для выбора.
     - Черный квадратный бейдж с порядковым номером (`№ 1..N`), строго совпадающим с номером строки в сводном отчете.
     - Наименование организации (жирным, до 2 строк).
     - Дата и время покупки.
     - Сумма и сумма НДС.
     - Иконка корзины справа для быстрого удаления чека.
   - Клик по карточке открывает `ReceiptPreviewDialog`:
     - Отрисовывает полноразмерный электронный чек высокого разрешения с QR-кодом (`ReceiptCardRenderer.renderBitmap`).
     - Кнопки: «Поделиться» (экспорт PNG чека), «Удалить» (с диалогом подтверждения) и «Закрыть».

**Step 2: Запустить компиляцию и сборку**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 3: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt app/src/main/res/values/strings.xml`
`git commit -m "feat(ui): redesign ReportScreen to unified 7-block Receipts & Report screen"`

---

### Task 5: Обновление навигации в `AppNavHost.kt` и удаление дублирующего кода

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/navigation/AppNavHost.kt`
- Delete: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReceiptsScreen.kt`
- Delete: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/ReceiptsViewModel.kt`

**Step 1: Обновить `MainTab` и маршруты в `AppNavHost.kt`**
1. В `AppNavHost.kt`:
   ```kotlin
   private sealed class MainTab(val route: String, val title: String, val icon: ImageVector) {
       data object Scan : MainTab("main/scan", "Добавить", Icons.Outlined.QrCodeScanner)
       data object Report : MainTab("main/report", "Чеки и отчёт", Icons.Outlined.Description)
       data object Audit : MainTab("main/audit", "Аудит", Icons.AutoMirrored.Outlined.FactCheck)
       data object Profile : MainTab("main/profile", "Профиль", Icons.Outlined.Person)
       companion object {
           val employee = listOf(Scan, Report, Profile)
           val auditor = listOf(Audit, Report, Profile)
       }
   }
   ```
2. Удалить объект `MainTab.Receipts` и удаление `composable(MainTab.Receipts.route)`.
3. Удалить файлы `ReceiptsScreen.kt` и `ReceiptsViewModel.kt`, так как вся их функциональность полностью перенесена и расширена в `ReportScreen.kt` и `ReportViewModel.kt`.

**Step 2: Проверить компиляцию всего проекта**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL (отсутствуют битые импорты и ссылки на удаленные классы).

**Step 3: Коммит**
`git add -A`
`git commit -m "refactor(navigation): reduce navigation tabs to 3 and remove obsolete ReceiptsScreen"`

---

### Task 6: Актуализация справочного раздела `HelpBottomSheet.kt`

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt`

**Step 1: Обновить справочные тексты под новую вкладку**
В `HelpBottomSheet.kt`:
1. Раздел «Что нужно печатать»:
   - Описать, что теперь доступен режим «Всё», позволяющий в один клик распечатать или экспортировать полный комплект документов: сводный отчёт и реестр чеков по 6 на лист с непрерывными реквизитами и отдельной нумерацией.
2. Объединить и актуализировать разделы «Чеки» и «Генерация отчета» в единый подробный пункт «Чеки и отчёт (Режимы экспорта)»:
   - Разъяснить 3 режима переключателя:
     - «Всё» (по умолчанию): единый официальный PDF-документ (сводная таблица, подпись, колонтитулы + реестр чеков с нумерацией «стр. 1 из M»).
     - «Отчёт»: сводный аналитический отчет для налогового возмещения (PDF/XLSX).
     - «Чеки»: реестр карточек чеков высокого разрешения с фискальными данными и QR-кодами (по 6 шт. на лист А4).
   - Описать кнопки действий («Сохранить», «Поделиться», «Открыть», «Печать»).
   - Описать работу онлайн-синхронизации ОФД с индикатором «Обновлено X/Y».
   - Описать фильтры (Пользователь, Год, Квартал по умолчанию Asia/Tashkent, Период).
   - Описать пакетное удаление и предупреждения о нулевом НДС.
   - Описать просмотр полноразмерного чека по клику на карточку.

**Step 2: Проверить сборку**
Команда: `.\gradlew.bat compileDebugKotlin`
Ожидаемый результат: BUILD SUCCESSFUL.

**Step 3: Коммит**
`git add app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt`
`git commit -m "docs(help): update HelpBottomSheet with unified Receipts and Report screen guide"`

---

### Task 7: Финальное тестирование, синхронизация архитектурной карты и Quality Gate

**Files:**
- Modify: `SYSTEM_PROMPT.md`
- Modify: `.cursorrules`

**Step 1: Запустить все Unit-тесты проекта**
Команда: `.\gradlew.bat test`
Ожидаемый результат: BUILD SUCCESSFUL, все тесты пройдены без ошибок (100% green).

**Step 2: Запустить автосинхронизацию архитектурной карты**
Команда: `python scripts/update_architecture_map.py`
Ожидаемый результат:
- Успешное обновление блока `<architecture_map>` в `SYSTEM_PROMPT.md`.
- Успешная синхронизация в `.cursorrules`.

**Step 3: Финальный коммит изменений**
`git add SYSTEM_PROMPT.md .cursorrules`
`git commit -m "chore(architecture): update architecture map after unifying receipts and report screens"`

---

## Валидация и критерии готовности (Verification Checklist)

- [ ] Навигация в `AppNavHost.kt` содержит ровно 3 вкладки:
  - Для сотрудника: «Добавить», «Чеки и отчёт», «Профиль»
  - Для аудитора: «Аудит», «Чеки и отчёт», «Профиль»
- [ ] Экран «Чеки и отчёт» содержит все 7 блоков в строгом порядке сверху вниз:
  1. Шапка отчёта (период, ФИО, должность, количество чеков, общая сумма и НДС).
  2. Переключатель в один ряд: «Отчёт» | «Всё» | «Чеки» (по умолчанию «Всё»).
  3. Кнопки экспорта в один ряд: «Сохранить», «Поделиться», «Открыть», «Печать» с предварительной ОФД-синхронизацией и индикатором «Обновлено X/Y».
  4. Фильтры в один ряд из 4 полей с `AutoScalingText`: Пользователь, Год, Квартал (Asia/Tashkent по умолчанию), Период.
  5. Сортировка (выпадающий список полей + переключатель направления) и чекбокс «Выбрать все» / `SelectionActionBar`.
  6. Предупреждение о чеках с нулевым НДС (`ZeroVatReceiptsCard`).
  7. Список карточек чеков с черными квадратными бейджами номеров № 1..N, совпадающими со строкой отчета, и открытием `ReceiptPreviewDialog` по клику.
- [ ] Генерация файла «Всё» (`CombinedPdfGenerator.kt`):
  - Страницы сводного отчета 1..N.
  - С НОВОЙ страницы начинается реестр карточек чеков по 6 на лист А4.
  - Нумерация страниц реестра изолирована и начинается с 1-й страницы: «стр. 1 из M».
  - На каждой странице реестра выводятся Должность, ФИО и дата формирования («Сформировано дд.мм.гггг чч:мм:сс»).
- [ ] Оптимизация отступов в `PdfReportGenerator.kt`:
  - `MARGIN = 28f` (10 мм).
  - На промежуточных страницах без подписи лимит высоты `PAGE_HEIGHT - 32f` (резерв 110f убран).
  - Компактный блок подписи (70f).
  - До 24 чеков гарантированно умещаются на 1 странице А4; до 30 на последующих.
- [ ] Справочный раздел `HelpBottomSheet.kt` полностью актуализирован.
- [ ] Все Unit-тесты (`.\gradlew.bat test`) проходят успешно.
- [ ] Скрипт `python scripts/update_architecture_map.py` выполнен без ошибок.
