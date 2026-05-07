package com.example.uzb_qqs_for_dip

import com.example.uzb_qqs_for_dip.network.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Регрессионный тест для парсера электронного чека ofd.soliq.uz/epi.
 *
 * HTML лежит в test-resources: app/src/test/resources/soliq_epi_multicard_payment.html.
 * Это реальная страница чека пользователя, на котором ранее не распознавались поля
 * «продавец» и «НДС», а время покупки сбрасывалось в 00:00 (см. screenshot из чата).
 */
class ReceiptParserDebugTest {

    private fun loadFixture(name: String): String {
        val cl = javaClass.classLoader ?: error("classloader недоступен")
        val stream = cl.getResourceAsStream(name)
            ?: error("Не найден тестовый ресурс: $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun parses_soliq_epi_multicard_payment_receipt() {
        val html = loadFixture("soliq_epi_multicard_payment.html")
        val parser = ReceiptParser()

        val parsed = parser.parseHtml(
            qrUrl = "http://ofd.soliq.uz/epi?t=EP000000000510&r=104636849&c=20260329185823&s=259200054220",
            html = html
        )

        assertNotNull("date is null", parsed.purchasedAt)
        assertNotNull("seller is null", parsed.sellerName)
        assertNotNull("total is null", parsed.totalAmountTiyin)
        assertNotNull("vat is null", parsed.vatAmountTiyin)

        // 853 374,00 → 85_337_400 тийин
        assertEquals(85_337_400L, parsed.totalAmountTiyin)
        // 89 828,54 → 8_982_854 тийин
        assertEquals(8_982_854L, parsed.vatAmountTiyin)

        // 2026-03-29 18:58:23 в зоне Asia/Tashkent
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tashkent")).apply {
            timeInMillis = parsed.purchasedAt!!
        }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(58, cal.get(Calendar.MINUTE))

        val seller = parsed.sellerName!!
        assertTrue("seller='${seller}'", seller.contains("MULTICARD PAYMENT", ignoreCase = true))
        assertTrue("seller='${seller}'", seller.contains("AJ", ignoreCase = false))
    }

    /**
     * Регрессия: чек ofd.soliq.uz/check (ANGLESEY FOOD) ранее парсился
     * неверно: парсер цеплялся за хвостовое слово «KORXONA» из названия
     * как за лейбл «Korxona» и подставлял в продавца адрес юрлица.
     * После фикса корректное юрлицо берётся из <h3>.
     */
    @Test
    fun parses_soliq_check_anglesey_food_receipt() {
        val html = loadFixture("soliq_check_anglesey_food.html")
        val parser = ReceiptParser()

        val parsed = parser.parseHtml(
            qrUrl = "https://ofd.soliq.uz/check?t=LG420230644664&r=84229&c=20260407105718&s=054387142715",
            html = html
        )

        val seller = parsed.sellerName ?: error("seller is null")
        assertTrue("seller='${seller}'", seller.contains("ANGLESEY FOOD", ignoreCase = true))
        // Адрес и регистрационные номера НЕ должны попасть в название юрлица.
        assertTrue(
            "seller wrongly contains address: '${seller}'",
            !seller.contains("Toshkent shahri", ignoreCase = true)
        )
        assertTrue(
            "seller wrongly contains registration id: '${seller}'",
            !seller.contains("LG420230644664", ignoreCase = true)
        )

        // Дата 07.04.2026 10:57 в зоне Asia/Tashkent
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tashkent")).apply {
            timeInMillis = parsed.purchasedAt!!
        }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, cal.get(Calendar.MONTH))
        assertEquals(7, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(57, cal.get(Calendar.MINUTE))

        // Суммы из чека: «Jami to`lov: 167 010,00» и «Umumiy QQS qiymati: 17 893,92».
        assertEquals(16_701_000L, parsed.totalAmountTiyin)
        assertEquals(1_789_392L, parsed.vatAmountTiyin)
    }
}
