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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Unit-тесты для фильтрации чеков с нулевым НДС в экспортерах:
 * - PdfReportGenerator
 * - ReceiptsSheetPdfGenerator
 * - XlsxExporter
 * - CsvExporter
 *
 * Проверяется, что строки с vatAmountTiyin == 0L отфильтровываются и не попадают
 * в экспортируемый результат и подсчёт итогов.
 */
class ZeroVatExporterFilterTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context

    private val testUser = User(
        id = 1L,
        fullName = "Иванов Иван Иванович",
        position = "Бухгалтер",
        initialsSurname = "И.И. Иванов",
        role = UserRole.EMPLOYEE
    )

    private val receiptWithVat1 = ReceiptWithUser(
        receipt = Receipt(
            id = 101L,
            userId = 1L,
            purchasedAt = 1715000000000L,
            sellerName = "OOO SUPERMARKET ONE",
            totalAmountTiyin = 1_000_000L, // 10 000.00
            vatAmountTiyin = 120_000L,     // 1 200.00
            qrUrl = "https://ofd.soliq.uz/check?t=1&r=1&s=1",
            paymentType = PaymentType.CARD,
            source = ReceiptSource.APP
        ),
        userFullName = testUser.fullName,
        userPosition = testUser.position,
        userInitialsSurname = testUser.initialsSurname
    )

    private val receiptWithZeroVat = ReceiptWithUser(
        receipt = Receipt(
            id = 102L,
            userId = 1L,
            purchasedAt = 1715100000000L,
            sellerName = "OOO ZERO PHARMACY",
            totalAmountTiyin = 500_000L,   // 5 000.00
            vatAmountTiyin = 0L,           // 0.00 (Zero VAT!)
            qrUrl = "https://ofd.soliq.uz/check?t=2&r=2&s=2",
            paymentType = PaymentType.CASH,
            source = ReceiptSource.APP
        ),
        userFullName = testUser.fullName,
        userPosition = testUser.position,
        userInitialsSurname = testUser.initialsSurname
    )

    private val receiptWithVat2 = ReceiptWithUser(
        receipt = Receipt(
            id = 103L,
            userId = 1L,
            purchasedAt = 1715200000000L,
            sellerName = "OOO TECH STORE TWO",
            totalAmountTiyin = 3_000_000L, // 30 000.00
            vatAmountTiyin = 360_000L,     // 3 600.00
            qrUrl = "https://ofd.soliq.uz/check?t=3&r=3&s=3",
            paymentType = PaymentType.CARD,
            source = ReceiptSource.APP
        ),
        userFullName = testUser.fullName,
        userPosition = testUser.position,
        userInitialsSurname = testUser.initialsSurname
    )

    private val allRows = listOf(receiptWithVat1, receiptWithZeroVat, receiptWithVat2)

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("exporter_filter_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
    }

    // =========================================================================
    // PdfReportGenerator
    // =========================================================================

    @Test
    fun pdfReportGenerator_sanitizeParams_filtersOutZeroVatReceipts() {
        val originalParams = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = allRows
        )

        val sanitized = PdfReportGenerator.sanitizeParams(originalParams)

        assertEquals("Sanitized rows must only contain receipts with VAT > 0", 2, sanitized.rows.size)
        assertFalse(
            "Zero VAT receipt must NOT be present in sanitized rows",
            sanitized.rows.any { it.receipt.id == receiptWithZeroVat.receipt.id }
        )
        assertTrue(
            "receiptWithVat1 must be present",
            sanitized.rows.any { it.receipt.id == receiptWithVat1.receipt.id }
        )
        assertTrue(
            "receiptWithVat2 must be present",
            sanitized.rows.any { it.receipt.id == receiptWithVat2.receipt.id }
        )

        // Totals calculated over sanitized.rows must exclude receiptWithZeroVat
        val totalSum = sanitized.rows.sumOf { it.receipt.totalAmountTiyin }
        val totalVat = sanitized.rows.sumOf { it.receipt.vatAmountTiyin }
        assertEquals(4_000_000L, totalSum) // 10 000 + 30 000 (5 000 from zero vat is excluded)
        assertEquals(480_000L, totalVat)   // 1 200 + 3 600
    }

    // =========================================================================
    // ReceiptsSheetPdfGenerator
    // =========================================================================

    @Test
    fun receiptsSheetPdfGenerator_sanitizeRows_filtersOutZeroVatReceipts() {
        val sanitized = ReceiptsSheetPdfGenerator.sanitizeRows(allRows)

        assertEquals(2, sanitized.size)
        assertFalse(
            "Zero VAT receipt must be excluded from sheet cards",
            sanitized.any { it.receipt.id == receiptWithZeroVat.receipt.id }
        )
        assertTrue(sanitized.any { it.receipt.id == receiptWithVat1.receipt.id })
        assertTrue(sanitized.any { it.receipt.id == receiptWithVat2.receipt.id })
    }

    // =========================================================================
    // CsvExporter
    // =========================================================================

    @Test
    fun csvExporter_sanitizeRows_filtersOutZeroVatReceipts() {
        val sanitized = CsvExporter.sanitizeRows(allRows)
        assertEquals(2, sanitized.size)
        assertFalse(sanitized.any { it.receipt.id == receiptWithZeroVat.receipt.id })
    }

    @Test
    fun csvExporter_export_producesCsvWithoutZeroVatReceipts() = runBlocking {
        val file = CsvExporter.export(mockContext, allRows, "test_checks.csv")
        assertTrue("Exported file must exist", file.exists())

        val content = file.readText(Charsets.UTF_8)

        // Must contain header and non-zero receipts
        assertTrue("CSV must contain seller 1", content.contains("OOO SUPERMARKET ONE"))
        assertTrue("CSV must contain seller 2", content.contains("OOO TECH STORE TWO"))

        // Must NOT contain zero-vat receipt
        assertFalse(
            "CSV must NOT contain seller with zero VAT",
            content.contains("OOO ZERO PHARMACY")
        )
    }

    // =========================================================================
    // XlsxExporter
    // =========================================================================

    @Test
    fun xlsxExporter_sanitizeRows_filtersOutZeroVatReceipts() {
        val sanitized = XlsxExporter.sanitizeRows(allRows)
        assertEquals(2, sanitized.size)
        assertFalse(sanitized.any { it.receipt.id == receiptWithZeroVat.receipt.id })
    }

    @Test
    fun xlsxExporter_export_producesXlsxWithoutZeroVatReceiptsAndWithCorrectTotals() = runBlocking {
        val file = XlsxExporter.export(mockContext, allRows, "test_checks.xlsx")
        assertTrue("XLSX file must exist", file.exists())

        // Inspect zip contents
        ZipFile(file).use { zip ->
            val stringsEntry = zip.getEntry("xl/sharedStrings.xml")
            val stringsXml = zip.getInputStream(stringsEntry).bufferedReader(Charsets.UTF_8).readText()

            assertTrue(stringsXml.contains("OOO SUPERMARKET ONE"))
            assertTrue(stringsXml.contains("OOO TECH STORE TWO"))
            assertFalse(
                "XLSX shared strings must NOT contain seller with zero VAT",
                stringsXml.contains("OOO ZERO PHARMACY")
            )

            val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
            val sheetXml = zip.getInputStream(sheetEntry).bufferedReader(Charsets.UTF_8).readText()

            // Total sum: 40000.00 (10 000 + 30 000), total VAT: 4800.00 (1200 + 3600)
            // 45000.00 would indicate zero-vat total (5000) was improperly added
            assertTrue("Sheet must contain correct sum 40000.00", sheetXml.contains("<v>40000.00</v>"))
            assertTrue("Sheet must contain correct VAT sum 4800.00", sheetXml.contains("<v>4800.00</v>"))
            assertFalse("Sheet must NOT contain total including zero-vat receipt (45000.00)", sheetXml.contains("<v>45000.00</v>"))
        }
    }

    @Test
    fun xlsxExporter_exportReport_producesReportXlsxWithoutZeroVatReceiptsAndWithCorrectTotals() = runBlocking {
        val params = ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = allRows
        )

        val file = XlsxExporter.exportReport(mockContext, params, "test_report.xlsx")
        assertTrue("Report XLSX file must exist", file.exists())

        ZipFile(file).use { zip ->
            val stringsEntry = zip.getEntry("xl/sharedStrings.xml")
            val stringsXml = zip.getInputStream(stringsEntry).bufferedReader(Charsets.UTF_8).readText()

            assertTrue(stringsXml.contains("OOO SUPERMARKET ONE"))
            assertTrue(stringsXml.contains("OOO TECH STORE TWO"))
            assertFalse(
                "Report XLSX must NOT contain zero-vat receipt seller",
                stringsXml.contains("OOO ZERO PHARMACY")
            )

            val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
            val sheetXml = zip.getInputStream(sheetEntry).bufferedReader(Charsets.UTF_8).readText()

            assertTrue("Report sheet must contain total sum 40000.00", sheetXml.contains("<v>40000.00</v>"))
            assertTrue("Report sheet must contain total VAT 4800.00", sheetXml.contains("<v>4800.00</v>"))
            assertFalse("Report sheet must NOT contain 45000.00", sheetXml.contains("<v>45000.00</v>"))
        }
    }
}
