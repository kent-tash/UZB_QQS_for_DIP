package com.example.uzb_qqs_for_dip

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uzb_qqs_for_dip.network.ReceiptParser
import com.example.uzb_qqs_for_dip.render.QrFromImageDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone

/**
 * Живой тест на эмуляторе/устройстве: загрузка чека из вложения пользователя
 * (ofd.soliq.uz, UZ210317220155, 29.06.2026 12:28, ANGLESEY FOOD).
 */
@RunWith(AndroidJUnit4::class)
class ReceiptParserLiveInstrumentedTest {

    private val receiptUrl =
        "https://ofd.soliq.uz/check?t=UZ210317220155&r=192888&c=20260629122805&s=700159535136"

    @Test
    fun fetchAndParse_user_attached_receipt() = runBlocking {
        val parsed = fetchReceipt(receiptUrl)
        assertParsedUserReceipt(parsed)
    }

    @Test
    fun decode_qr_from_user_receipt_photo_and_fetch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val stream = context.assets.open("user_receipt_photo.png")
        val bitmap = stream.use { BitmapFactory.decodeStream(it) }
            ?: error("Не удалось загрузить фото чека из assets")
        try {
            val qrUrl = QrFromImageDecoder.decodeBitmap(bitmap)
            assertTrue(qrUrl.contains("ofd.soliq.uz/check"))
            assertTrue(qrUrl.contains("UZ210317220155"))
            val parsed = fetchReceipt(qrUrl)
            assertParsedUserReceipt(parsed)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun fetchReceipt(url: String) =
        ReceiptParser().fetchAndParse(url)
            .getOrElse { error("fetchAndParse failed: ${it.message}") }

    private fun assertParsedUserReceipt(parsed: com.example.uzb_qqs_for_dip.network.ParsedReceipt) {
        assertTrue("parsed receipt invalid: seller=${parsed.sellerName}", parsed.isValid)
        assertTrue(
            "seller='${parsed.sellerName}'",
            parsed.sellerName!!.contains("ANGLESEY FOOD", ignoreCase = true)
        )
        assertEquals(33_800_000L, parsed.totalAmountTiyin)
        assertEquals(3_621_429L, parsed.vatAmountTiyin)
        assertNotNull(parsed.address)
        assertTrue(parsed.address!!.contains("Qo'sh-ko'prik", ignoreCase = true))

        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tashkent")).apply {
            timeInMillis = parsed.purchasedAt!!
        }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH))
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(28, cal.get(Calendar.MINUTE))
    }
}
