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

    private fun createReceipts(
        count: Int,
        vatAmount: Long = 12_000L,
        sellerPrefix: String = "OOO SELLER",
        isManual: Boolean = false,
        photoUri: String? = null
    ): List<ReceiptWithUser> {
        return (1..count).map { i ->
            ReceiptWithUser(
                receipt = Receipt(
                    id = i.toLong(),
                    userId = 1L,
                    purchasedAt = 1715000000000L + i * 3600000L,
                    sellerName = "$sellerPrefix $i",
                    totalAmountTiyin = 100_000L,
                    vatAmountTiyin = vatAmount,
                    qrUrl = "https://ofd.soliq.uz/check?t=$i",
                    paymentType = PaymentType.CARD,
                    source = ReceiptSource.APP,
                    isManual = isManual,
                    manualPhotoUri = photoUri
                ),
                userFullName = testUser.fullName,
                userPosition = testUser.position,
                userInitialsSurname = testUser.initialsSurname
            )
        }
    }

    private fun createParams(rows: List<ReceiptWithUser>): ReportParams {
        return ReportParams(
            user = testUser,
            periodStart = 1714000000000L,
            periodEnd = 1716000000000L,
            rows = rows,
            quarterLabel = "I квартал 2026 г."
        )
    }

    @Test
    fun testReportZeroReceiptsFitsOnSinglePage() {
        val rows = emptyList<ReceiptWithUser>()
        val params = createParams(rows)
        val pageCount = PdfReportGenerator.calculatePageCount(params)
        assertEquals("Пустой отчёт (0 чеков) должен занимать ровно 1 страницу", 1, pageCount)
    }

    @Test
    fun testReportSingleReceiptFitsOnSinglePage() {
        val rows = createReceipts(1)
        val params = createParams(rows)
        val pageCount = PdfReportGenerator.calculatePageCount(params)
        assertEquals("Отчёт с 1 чеком должен занимать 1 страницу", 1, pageCount)
    }

    @Test
    fun testReportFitsUpTo24ReceiptsOnSinglePage() {
        // Требование: до 24 чеков гарантированно помещаются на 1 страницу А4
        val rows = createReceipts(24)
        val params = createParams(rows)
        val pageCount = PdfReportGenerator.calculatePageCount(params)
        assertEquals("Отчёт до 24 чеков должен помещаться ровно на 1 страницу А4", 1, pageCount)
    }

    @Test
    fun testReportFitsUpTo27ReceiptsOnSinglePage() {
        // При стандартной высоте строки 22pt на 1-ю страницу вместе с шапкой,
        // строкой 'Итого' и блоком подписи помещается максимум 27 чеков
        val rows27 = createReceipts(27)
        assertEquals("27 чеков должны помещаться на 1 страницу", 1, PdfReportGenerator.calculatePageCount(createParams(rows27)))

        // 28 чеков превышают доступную высоту первой страницы и переходят на 2 страницы
        val rows28 = createReceipts(28)
        assertEquals("28 чеков переходят на 2 страницы", 2, PdfReportGenerator.calculatePageCount(createParams(rows28)))
    }

    @Test
    fun testReportFits54ReceiptsOnTwoPages() {
        // Требование: отчёт на 54 чека должен занимать 2 страницы
        val rows = createReceipts(54)
        val params = createParams(rows)
        val pageCount = PdfReportGenerator.calculatePageCount(params)
        assertEquals("Отчёт на 54 чека должен занимать 2 страницы", 2, pageCount)
    }

    @Test
    fun testReportTwoPageCapacityAndTransitionToThirdPage() {
        // На 1-й странице при многостраничном отчете помещается 31 чек (без подписи),
        // на 2-й странице помещается до 30 чеков (с итогом и подписью), итого до 61 чека на 2 страницы
        val rows61 = createReceipts(61)
        assertEquals("61 чек должен занимать 2 страницы", 2, PdfReportGenerator.calculatePageCount(createParams(rows61)))

        // 62 чека переходят на 3 страницы
        val rows62 = createReceipts(62)
        assertEquals("62 чека должны занимать 3 страницы", 3, PdfReportGenerator.calculatePageCount(createParams(rows62)))
    }

    @Test
    fun testReportCapacityCalculationFiltersZeroVat() {
        val vatRows = createReceipts(24, vatAmount = 12_000L)
        val zeroVatRows = createReceipts(20, vatAmount = 0L, sellerPrefix = "ZERO VAT")
        val combined = vatRows + zeroVatRows

        val params = createParams(combined)
        val pageCount = PdfReportGenerator.calculatePageCount(params)
        assertEquals(
            "Чеки с нулевым НДС не должны влиять на вместимость отчёта (24 валидных = 1 страница)",
            1,
            pageCount
        )
    }

    @Test
    fun testReportManualAttachmentsImpactOnPageCount() {
        // 24 чека, из них 3 ручных с прикрепленным фото
        val standardRows = createReceipts(21, vatAmount = 12_000L)
        val manualRows = createReceipts(
            3,
            vatAmount = 12_000L,
            sellerPrefix = "MANUAL RECEIPT",
            isManual = true,
            photoUri = "content://media/photos/1.jpg"
        )
        val allRows = standardRows + manualRows
        val params = createParams(allRows)

        // При includeManualAttachments = true к 1 странице таблицы добавляются 3 страницы фото
        val countWithAttachments = PdfReportGenerator.calculatePageCount(params, includeManualAttachments = true)
        assertEquals("1 страница таблицы + 3 страницы фото = 4 страницы", 4, countWithAttachments)

        // При includeManualAttachments = false (как в CombinedPdfGenerator) фото не включаются в часть отчёта
        val countWithoutAttachments = PdfReportGenerator.calculatePageCount(params, includeManualAttachments = false)
        assertEquals("Без приложений отчёт должен занимать ровно 1 страницу", 1, countWithoutAttachments)
    }

    @Test
    fun testReportMultiLineSellerRowHeightImpactOnCapacity() {
        // Если наименования продавцов очень длинные и переносятся на 3-4 строки,
        // высота строки становится больше 22pt, и количество чеков на страницу уменьшается
        val longName = "OOO O'ZBEKISTON RESPUBLIKASI DAVLAT SOLIQ QO'MITASI HUZURIDAGI YANGI TEXNOLOGIYALAR ILMIY-AXBOROT MARKAZI VA SERVIS XIZMATI"
        
        // 12 чеков с длинными именами занимают 2 страницы вместо 1
        val rows12 = createReceipts(12, sellerPrefix = longName)
        assertEquals("12 чеков с многострочными именами должны занять 2 страницы", 2, PdfReportGenerator.calculatePageCount(createParams(rows12)))

        // 20 чеков с длинными именами занимают 3 страницы
        val rows20 = createReceipts(20, sellerPrefix = longName)
        assertEquals("20 чеков с многострочными именами должны занять 3 страницы", 3, PdfReportGenerator.calculatePageCount(createParams(rows20)))
    }
}
