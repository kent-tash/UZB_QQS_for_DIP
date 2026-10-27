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
