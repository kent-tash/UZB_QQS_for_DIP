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

    private fun createReceipts(count: Int, withVat: Boolean = true): List<ReceiptWithUser> {
        return (1..count).map { i ->
            ReceiptWithUser(
                receipt = Receipt(
                    id = i.toLong(),
                    userId = 1L,
                    purchasedAt = 1715000000000L + i * 3600000L,
                    sellerName = "OOO SELLER $i",
                    totalAmountTiyin = 100_000L,
                    vatAmountTiyin = if (withVat) 12_000L else 0L,
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

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("combined_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
    }

    @Test
    fun testRegistryPagesCountCalculation() {
        // 13 чеков при 6 на страницу дают ceil(13/6) = 3 страницы реестра
        val count = CombinedPdfGenerator.calculateRegistryPagesCount(13, manualPhotosCount = 0)
        assertEquals(3, count)
        // 0 чеков -> 1 страница заглушки
        assertEquals(1, CombinedPdfGenerator.calculateRegistryPagesCount(0, manualPhotosCount = 0))
        // 6 чеков -> 1 страница
        assertEquals(1, CombinedPdfGenerator.calculateRegistryPagesCount(6, manualPhotosCount = 0))
        // 6 чеков + 2 фото -> 3 страницы
        assertEquals(3, CombinedPdfGenerator.calculateRegistryPagesCount(6, manualPhotosCount = 2))
    }

    @Test
    fun testSanitizeRowsExcludesZeroVat() {
        val rows = listOf(receipt1, receiptZero)
        val params = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = rows,
            quarterLabel = "II квартал 2026 г."
        )
        val sanitized = CombinedPdfGenerator.sanitizeParams(params)
        assertEquals(1, sanitized.rows.size)
        assertEquals(receipt1.receipt.id, sanitized.rows.first().receipt.id)
    }

    @Test
    fun testCombinedDocumentTwoPartStructureSinglePageReport() {
        // 24 чека: Часть 1 (отчет) занимает 1 страницу А4.
        // Часть 2 (реестр): 24 / 6 = 4 страницы.
        // Часть 2 начинается со страницы 2 в сквозном документе doc.
        // Всего в документе: 1 + 4 = 5 страниц.
        val rows = createReceipts(24)
        val params = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = rows,
            quarterLabel = "II квартал 2026 г."
        )

        val reportPages = CombinedPdfGenerator.calculateReportPagesCount(params)
        assertEquals("Часть 1 (отчёт) должна занимать ровно 1 страницу", 1, reportPages)

        val registryStartDocPage = CombinedPdfGenerator.calculateRegistryStartDocPage(params)
        assertEquals("Часть 2 (реестр) должна начинаться со 2-й страницы в документе", 2, registryStartDocPage)

        val registryPages = CombinedPdfGenerator.calculateRegistryPagesCount(rows.size)
        assertEquals("Часть 2 (реестр) должна занимать 4 страницы (по 6 карточек на лист)", 4, registryPages)

        val totalPages = CombinedPdfGenerator.calculateTotalPages(params)
        assertEquals("Общее количество страниц объединенного документа должно быть 5", 5, totalPages)
    }

    @Test
    fun testCombinedDocumentTwoPartStructureMultiPageReport() {
        // 54 чека: Часть 1 (отчет) занимает 2 страницы А4 (24 на 1-й + 30 на 2-й).
        // Часть 2 (реестр): 54 / 6 = 9 страниц.
        // Часть 2 начинается со страницы 3 в сквозном документе.
        // Всего страниц: 2 + 9 = 11 страниц.
        val rows = createReceipts(54)
        val params = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = rows,
            quarterLabel = "II квартал 2026 г."
        )

        val reportPages = CombinedPdfGenerator.calculateReportPagesCount(params)
        assertEquals("Часть 1 (отчет на 54 чека) должна занимать ровно 2 страницы", 2, reportPages)

        val registryStartDocPage = CombinedPdfGenerator.calculateRegistryStartDocPage(params)
        assertEquals("Часть 2 должна начинаться со 3-й страницы документа", 3, registryStartDocPage)

        val registryPages = CombinedPdfGenerator.calculateRegistryPagesCount(rows.size)
        assertEquals("Часть 2 должна занимать 9 страниц (54 / 6)", 9, registryPages)

        val totalPages = CombinedPdfGenerator.calculateTotalPages(params)
        assertEquals("Общее количество страниц должно быть 11 (2 + 9)", 11, totalPages)
    }

    @Test
    fun testRegistryReceiptOrdinalStartsFromOne() {
        // Проверка требования: нумерация карточек в реестре чеков строго начинается с 1
        assertEquals("Первый чек в реестре должен иметь порядковый номер 1", 1, ReceiptsSheetPdfGenerator.getReceiptOrdinal(0))
        assertEquals("Второй чек в реестре должен иметь порядковый номер 2", 2, ReceiptsSheetPdfGenerator.getReceiptOrdinal(1))
        assertEquals("24-й чек в реестре должен иметь порядковый номер 24", 24, ReceiptsSheetPdfGenerator.getReceiptOrdinal(23))
        assertEquals("54-й чек в реестре должен иметь порядковый номер 54", 54, ReceiptsSheetPdfGenerator.getReceiptOrdinal(53))
    }

    @Test
    fun testRegistryPageNumberingIsIsolatedAndStartsFromOne() {
        // Проверка требования: нумерация страниц реестра изолирована и начинается с 1 («стр. 1 из M»)
        val totalRegistryPages = 5

        val page1Header = ReceiptsSheetPdfGenerator.formatRegistryPageNumber(0, totalRegistryPages)
        assertEquals("Первая страница реестра должна иметь номер 'стр. 1 из 5'", "стр. 1 из 5", page1Header)

        val page2Header = ReceiptsSheetPdfGenerator.formatRegistryPageNumber(1, totalRegistryPages)
        assertEquals("Вторая страница реестра должна иметь номер 'стр. 2 из 5'", "стр. 2 из 5", page2Header)

        val page5Header = ReceiptsSheetPdfGenerator.formatRegistryPageNumber(4, totalRegistryPages)
        assertEquals("Последняя страница реестра должна иметь номер 'стр. 5 из 5'", "стр. 5 из 5", page5Header)
    }

    @Test
    fun testCombinedDocumentExcludesZeroVatFromBothReportAndRegistry() {
        val vatRows = createReceipts(12, withVat = true)
        val zeroVatRows = createReceipts(6, withVat = false)
        val combinedRows = vatRows + zeroVatRows

        val params = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = combinedRows,
            quarterLabel = "II квартал 2026 г."
        )

        // Должно учитываться только 12 чеков с НДС > 0
        val reportPages = CombinedPdfGenerator.calculateReportPagesCount(params)
        assertEquals(1, reportPages)

        val sanitized = CombinedPdfGenerator.sanitizeParams(params)
        assertEquals(12, sanitized.rows.size)

        val registryPages = CombinedPdfGenerator.calculateRegistryPagesCount(sanitized.rows.size)
        assertEquals("12 чеков / 6 на страницу = 2 страницы реестра", 2, registryPages)

        val totalPages = CombinedPdfGenerator.calculateTotalPages(params)
        assertEquals("1 страница отчёта + 2 страницы реестра = 3 страницы", 3, totalPages)
    }
}
