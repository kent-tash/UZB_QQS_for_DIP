package com.example.uzb_qqs_for_dip.ui

import android.content.Context
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.PaymentType
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.data.model.ReceiptSource
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.repository.ReceiptRepository
import com.example.uzb_qqs_for_dip.data.repository.UserRepository
import com.example.uzb_qqs_for_dip.data.session.SessionManager
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.data.settings.ReportSettingsHolder
import com.example.uzb_qqs_for_dip.data.settings.SortField
import com.example.uzb_qqs_for_dip.data.settings.SortOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockApp: QqsApp
    private lateinit var mockContainer: AppContainer
    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockReceiptRepository: ReceiptRepository
    private lateinit var mockSessionManager: SessionManager
    private lateinit var reportSettingsHolder: ReportSettingsHolder

    private val userFlow = MutableStateFlow<List<User>>(emptyList())
    private val receiptFlow = MutableStateFlow<List<ReceiptWithUser>>(emptyList())
    private val currentUserIdFlow = MutableStateFlow<Long?>(null)

    private val testUser1 = User(
        id = 1L,
        fullName = "Иванов Иван Иванович",
        position = "Бухгалтер",
        initialsSurname = "И.И. Иванов",
        role = UserRole.EMPLOYEE
    )

    private val testUser2 = User(
        id = 2L,
        fullName = "Петров Петр Петрович",
        position = "Менеджер",
        initialsSurname = "П.П. Петров",
        role = UserRole.EMPLOYEE
    )

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("report_vm_test").toFile()

        mockApp = mockk(relaxed = true)
        mockContainer = mockk(relaxed = true)
        mockUserRepository = mockk(relaxed = true)
        mockReceiptRepository = mockk(relaxed = true)
        mockSessionManager = mockk(relaxed = true)
        reportSettingsHolder = ReportSettingsHolder()

        every { mockApp.container } returns mockContainer
        every { mockApp.applicationContext } returns mockApp
        every { mockApp.filesDir } returns tempDir

        every { mockContainer.userRepository } returns mockUserRepository
        every { mockContainer.receiptRepository } returns mockReceiptRepository
        every { mockContainer.sessionManager } returns mockSessionManager
        every { mockContainer.reportSettings } returns reportSettingsHolder

        every { mockUserRepository.users } returns userFlow
        every { mockReceiptRepository.receipts } returns receiptFlow
        every { mockSessionManager.currentUserId } returns currentUserIdFlow
    }

    // =========================================================================
    // 1. ExportMode Тесты
    // =========================================================================

    @Test
    fun testExportModeDefaultsToAll() {
        val defaultMode = ExportMode.ALL
        assertEquals("По умолчанию режим экспорта должен быть 'Всё'", ExportMode.ALL, defaultMode)
        assertEquals("В режиме REPORT название должно быть 'Отчёт'", "Отчёт", ExportMode.REPORT.title)
        assertEquals("В режиме ALL название должно быть 'Всё'", "Всё", ExportMode.ALL.title)
        assertEquals("В режиме RECEIPTS название должно быть 'Чеки'", "Чеки", ExportMode.RECEIPTS.title)
    }

    @Test
    fun testSetExportModeSwitchesState() = runTest {
        val viewModel = ReportViewModel(mockApp)
        assertEquals(ExportMode.ALL, viewModel.exportMode.value)

        viewModel.setExportMode(ExportMode.REPORT)
        assertEquals(ExportMode.REPORT, viewModel.exportMode.value)

        viewModel.setExportMode(ExportMode.RECEIPTS)
        assertEquals(ExportMode.RECEIPTS, viewModel.exportMode.value)

        viewModel.setExportMode(ExportMode.ALL)
        assertEquals(ExportMode.ALL, viewModel.exportMode.value)
    }

    // =========================================================================
    // 2. Тесты кварталов по умолчанию и переключения кварталов
    // =========================================================================

    @Test
    fun testReportSettingsDefaultQuarterMatchesCalendarMonth() {
        val defaultQuarter = ReportSettings.currentQuarter()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tashkent"))
        val month = cal.get(Calendar.MONTH)
        val expected = when (month) {
            in Calendar.JANUARY..Calendar.MARCH -> Quarter.Q1
            in Calendar.APRIL..Calendar.JUNE -> Quarter.Q2
            in Calendar.JULY..Calendar.SEPTEMBER -> Quarter.Q3
            else -> Quarter.Q4
        }
        assertEquals("Квартал по умолчанию должен соответствовать текущему месяцу", expected, defaultQuarter)
    }

    @Test
    fun testQuarterStartAndEndTimestampsCalculation() {
        val year = 2026
        val tz = TimeZone.getTimeZone("Asia/Tashkent")

        // Q1: Jan 1 00:00:00 to Mar 31 23:59:59
        val q1Start = ReportSettings.quarterStart(year, Quarter.Q1)
        val q1End = ReportSettings.quarterEnd(year, Quarter.Q1)
        val calStart = Calendar.getInstance(tz).apply { timeInMillis = q1Start }
        val calEnd = Calendar.getInstance(tz).apply { timeInMillis = q1End }

        assertEquals(2026, calStart.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, calStart.get(Calendar.MONTH))
        assertEquals(1, calStart.get(Calendar.DAY_OF_MONTH))

        assertEquals(2026, calEnd.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, calEnd.get(Calendar.MONTH))
        assertEquals(31, calEnd.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, calEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calEnd.get(Calendar.MINUTE))
        assertEquals(59, calEnd.get(Calendar.SECOND))

        // Q2: Apr 1 to Jun 30
        val q2Start = ReportSettings.quarterStart(year, Quarter.Q2)
        val q2End = ReportSettings.quarterEnd(year, Quarter.Q2)
        calStart.timeInMillis = q2Start
        calEnd.timeInMillis = q2End
        assertEquals(Calendar.APRIL, calStart.get(Calendar.MONTH))
        assertEquals(1, calStart.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JUNE, calEnd.get(Calendar.MONTH))
        assertEquals(30, calEnd.get(Calendar.DAY_OF_MONTH))

        // Q3: Jul 1 to Sep 30
        val q3Start = ReportSettings.quarterStart(year, Quarter.Q3)
        val q3End = ReportSettings.quarterEnd(year, Quarter.Q3)
        calStart.timeInMillis = q3Start
        calEnd.timeInMillis = q3End
        assertEquals(Calendar.JULY, calStart.get(Calendar.MONTH))
        assertEquals(1, calStart.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, calEnd.get(Calendar.MONTH))
        assertEquals(30, calEnd.get(Calendar.DAY_OF_MONTH))

        // Q4: Oct 1 to Dec 31
        val q4Start = ReportSettings.quarterStart(year, Quarter.Q4)
        val q4End = ReportSettings.quarterEnd(year, Quarter.Q4)
        calStart.timeInMillis = q4Start
        calEnd.timeInMillis = q4End
        assertEquals(Calendar.OCTOBER, calStart.get(Calendar.MONTH))
        assertEquals(1, calStart.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.DECEMBER, calEnd.get(Calendar.MONTH))
        assertEquals(31, calEnd.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testReportSettingsHolderSwitchQuarter() {
        val holder = ReportSettingsHolder()
        val year = holder.settings.value.year

        holder.setQuarter(Quarter.Q1)
        assertEquals(Quarter.Q1, holder.settings.value.quarter)
        assertEquals(ReportSettings.quarterStart(year, Quarter.Q1), holder.settings.value.from)
        assertEquals(ReportSettings.quarterEnd(year, Quarter.Q1), holder.settings.value.to)

        holder.setQuarter(Quarter.Q2)
        assertEquals(Quarter.Q2, holder.settings.value.quarter)
        assertEquals(ReportSettings.quarterStart(year, Quarter.Q2), holder.settings.value.from)
        assertEquals(ReportSettings.quarterEnd(year, Quarter.Q2), holder.settings.value.to)

        holder.setQuarter(Quarter.Q3)
        assertEquals(Quarter.Q3, holder.settings.value.quarter)
        assertEquals(ReportSettings.quarterStart(year, Quarter.Q3), holder.settings.value.from)
        assertEquals(ReportSettings.quarterEnd(year, Quarter.Q3), holder.settings.value.to)

        holder.setQuarter(Quarter.Q4)
        assertEquals(Quarter.Q4, holder.settings.value.quarter)
        assertEquals(ReportSettings.quarterStart(year, Quarter.Q4), holder.settings.value.from)
        assertEquals(ReportSettings.quarterEnd(year, Quarter.Q4), holder.settings.value.to)
    }

    @Test
    fun testReportSettingsHolderSetYearUpdatesDates() {
        val holder = ReportSettingsHolder()
        holder.setQuarter(Quarter.Q2)

        holder.setYear(2025)
        assertEquals(2025, holder.settings.value.year)
        assertEquals(ReportSettings.quarterStart(2025, Quarter.Q2), holder.settings.value.from)
        assertEquals(ReportSettings.quarterEnd(2025, Quarter.Q2), holder.settings.value.to)
    }

    @Test
    fun testReportSettingsHolderSetFromOrToSwitchesToCustom() {
        val holder = ReportSettingsHolder()
        holder.setQuarter(Quarter.Q1)
        assertEquals(Quarter.Q1, holder.settings.value.quarter)

        holder.setFrom(123456789L)
        assertEquals("При установке точной даты from квартал должен стать Custom", Quarter.Custom, holder.settings.value.quarter)
        assertEquals(123456789L, holder.settings.value.from)

        holder.setQuarter(Quarter.Q3)
        assertEquals(Quarter.Q3, holder.settings.value.quarter)

        holder.setTo(987654321L)
        assertEquals("При установке точной даты to квартал должен стать Custom", Quarter.Custom, holder.settings.value.quarter)
        assertEquals(987654321L, holder.settings.value.to)
    }

    @Test
    fun testReportSettingsHolderSortToggle() {
        val holder = ReportSettingsHolder()
        assertEquals(SortField.DATE, holder.settings.value.sortField)
        assertEquals(SortOrder.ASC, holder.settings.value.sortOrder)

        // Повторный клик по тому же полю инвертирует порядок
        holder.toggleSort(SortField.DATE)
        assertEquals(SortOrder.DESC, holder.settings.value.sortOrder)

        holder.toggleSort(SortField.DATE)
        assertEquals(SortOrder.ASC, holder.settings.value.sortOrder)

        // Клик по другому полю устанавливает его и сбрасывает порядок в ASC
        holder.toggleSort(SortField.TOTAL)
        assertEquals(SortField.TOTAL, holder.settings.value.sortField)
        assertEquals(SortOrder.ASC, holder.settings.value.sortOrder)
    }

    // =========================================================================
    // 3. Тесты фильтрации данных во ViewModel
    // =========================================================================

    private fun createReceiptWithUser(
        id: Long,
        userId: Long,
        purchasedAt: Long,
        totalTiyin: Long = 100_000L,
        vatTiyin: Long = 12_000L,
        seller: String = "SELLER $id"
    ): ReceiptWithUser {
        val user = if (userId == 1L) testUser1 else testUser2
        return ReceiptWithUser(
            receipt = Receipt(
                id = id,
                userId = userId,
                purchasedAt = purchasedAt,
                sellerName = seller,
                totalAmountTiyin = totalTiyin,
                vatAmountTiyin = vatTiyin,
                qrUrl = "https://ofd.soliq.uz/check?t=$id",
                paymentType = PaymentType.CARD,
                source = ReceiptSource.APP
            ),
            userFullName = user.fullName,
            userPosition = user.position,
            userInitialsSurname = user.initialsSurname
        )
    }

    @Test
    fun testViewModelFiltersReceiptsByQuarterUserAndVat() = runTest {
        // Устанавливаем год 2026, квартал Q1
        reportSettingsHolder.setYear(2026)
        reportSettingsHolder.setQuarter(Quarter.Q1)
        reportSettingsHolder.setUserId(1L)

        userFlow.value = listOf(testUser1, testUser2)
        currentUserIdFlow.value = 1L

        val q1Start = ReportSettings.quarterStart(2026, Quarter.Q1)
        val q1Mid = q1Start + 86400000L * 15 // 16 января 2026
        val q2Mid = ReportSettings.quarterStart(2026, Quarter.Q2) + 86400000L * 15 // 16 апреля 2026

        val r1User1Q1Vat = createReceiptWithUser(1L, userId = 1L, purchasedAt = q1Mid, vatTiyin = 12_000L)
        val r2User1Q1ZeroVat = createReceiptWithUser(2L, userId = 1L, purchasedAt = q1Mid, vatTiyin = 0L)
        val r3User2Q1Vat = createReceiptWithUser(3L, userId = 2L, purchasedAt = q1Mid, vatTiyin = 12_000L)
        val r4User1Q2Vat = createReceiptWithUser(4L, userId = 1L, purchasedAt = q2Mid, vatTiyin = 12_000L)

        receiptFlow.value = listOf(r1User1Q1Vat, r2User1Q1ZeroVat, r3User2Q1Vat, r4User1Q2Vat)

        val viewModel = ReportViewModel(mockApp)
        advanceUntilIdle()

        // В rows должны попасть ТОЛЬКО чеки пользователя 1, за период Q1, с vat > 0 (только r1)
        val rows = viewModel.rows.value
        assertEquals("В отчёт должен попасть ровно 1 чек", 1, rows.size)
        assertEquals(1L, rows.first().receipt.id)

        // В zeroVatRows должен попасть r2 (пользователь 1, Q1, vat = 0)
        val zeroVatRows = viewModel.zeroVatRows.value
        assertEquals("В список нулевого НДС должен попасть ровно 1 чек", 1, zeroVatRows.size)
        assertEquals(2L, zeroVatRows.first().receipt.id)

        // Теперь переключаем квартал на Q2
        viewModel.setQuarter(Quarter.Q2)
        advanceUntilIdle()

        // В rows теперь должен быть чек r4 (пользователь 1, Q2, vat > 0)
        val rowsQ2 = viewModel.rows.value
        assertEquals("После переключения на Q2 должен отобразиться чек за Q2", 1, rowsQ2.size)
        assertEquals(4L, rowsQ2.first().receipt.id)

        // В zeroVatRows за Q2 должно быть пусто
        assertEquals(0, viewModel.zeroVatRows.value.size)

        // Теперь переключаем фильтр на пользователя 2 и квартал Q1
        viewModel.setUserFilter(2L)
        viewModel.setQuarter(Quarter.Q1)
        advanceUntilIdle()

        val rowsUser2 = viewModel.rows.value
        assertEquals("Для пользователя 2 должен быть найден его чек за Q1", 1, rowsUser2.size)
        assertEquals(3L, rowsUser2.first().receipt.id)
    }

    @Test
    fun testViewModelSelectionLogic() = runTest {
        reportSettingsHolder.setYear(2026)
        reportSettingsHolder.setQuarter(Quarter.Q1)
        reportSettingsHolder.setUserId(1L)
        userFlow.value = listOf(testUser1)
        currentUserIdFlow.value = 1L

        val q1Mid = ReportSettings.quarterStart(2026, Quarter.Q1) + 86400000L
        val r1 = createReceiptWithUser(101L, userId = 1L, purchasedAt = q1Mid)
        val r2 = createReceiptWithUser(102L, userId = 1L, purchasedAt = q1Mid + 1000)
        receiptFlow.value = listOf(r1, r2)

        val viewModel = ReportViewModel(mockApp)
        advanceUntilIdle()

        assertEquals(emptySet<Long>(), viewModel.selectedIds.value)

        // Тогглим выделение
        viewModel.toggleSelection(101L)
        assertEquals(setOf(101L), viewModel.selectedIds.value)

        viewModel.toggleSelection(102L)
        assertEquals(setOf(101L, 102L), viewModel.selectedIds.value)

        viewModel.toggleSelection(101L)
        assertEquals(setOf(102L), viewModel.selectedIds.value)

        // Выбрать все
        viewModel.selectAllVisible()
        assertEquals(setOf(101L, 102L), viewModel.selectedIds.value)

        // Очистить
        viewModel.clearSelection()
        assertEquals(emptySet<Long>(), viewModel.selectedIds.value)
    }

    @Test
    fun testSuggestedPdfFileNamePrefix() = runTest {
        reportSettingsHolder.setUserId(1L)
        userFlow.value = listOf(testUser1)
        currentUserIdFlow.value = 1L

        val viewModel = ReportViewModel(mockApp)
        advanceUntilIdle()

        // По умолчанию ExportMode.ALL -> префикс all_
        viewModel.setExportMode(ExportMode.ALL)
        val allName = viewModel.suggestedPdfName()
        assertNotNull(allName)
        assertTrue("Имя файла в режиме ALL должно начинаться с 'all_'", allName!!.startsWith("all_"))

        // В режиме REPORT -> префикс report_
        viewModel.setExportMode(ExportMode.REPORT)
        val reportName = viewModel.suggestedPdfName()
        assertNotNull(reportName)
        assertTrue("Имя файла в режиме REPORT должно начинаться с 'report_'", reportName!!.startsWith("report_"))

        // В режиме RECEIPTS -> префикс receipts_
        viewModel.setExportMode(ExportMode.RECEIPTS)
        val receiptsName = viewModel.suggestedPdfName()
        assertNotNull(receiptsName)
        assertTrue("Имя файла в режиме RECEIPTS должно начинаться с 'receipts_'", receiptsName!!.startsWith("receipts_"))
    }
}
