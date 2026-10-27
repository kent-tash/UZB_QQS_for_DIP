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
}
