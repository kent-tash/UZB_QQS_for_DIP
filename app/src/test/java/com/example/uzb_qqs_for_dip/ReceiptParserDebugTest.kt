package com.example.uzb_qqs_for_dip

import com.example.uzb_qqs_for_dip.network.OfdPaymentApi
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

    /**
     * Регрессия: чек ofd.soliq.uz/check ("ZOO LITTLE" MCHJ) ранее не отдавал
     * адрес (сокращённые узбекские формы «sh», «tum», «MFY», «kochasi», «xonadon»
     * не распознавались) и не имел поля ИНН/STIR вовсе.
     */
    @Test
    fun parses_soliq_check_zoo_little_address_and_tin() {
        val html = loadFixture("soliq_check_zoo_little.html")
        val parser = ReceiptParser()

        val parsed = parser.parseHtml(
            qrUrl = "https://ofd.soliq.uz/check?t=VG343420021451&r=16312&c=20260405154318&s=242642240512",
            html = html
        )

        val seller = parsed.sellerName ?: error("seller is null")
        assertTrue("seller='${seller}'", seller.contains("ZOO LITTLE", ignoreCase = true))

        // Адрес должен распознаться целиком.
        val address = parsed.address ?: error("address is null")
        assertTrue("address='${address}'", address.contains("Nukus kochasi", ignoreCase = true))
        assertTrue("address='${address}'", address.contains("Mirobod", ignoreCase = true))
        // ИНН не должен попасть в адрес (он отдельным полем).
        assertTrue("address wrongly contains tin: '${address}'", !address.contains("311067194"))

        // ИНН/STIR продавца.
        assertEquals("311067194", parsed.tin)

        // SN на этом чеке пустой — он НЕ должен подменяться датой покупки.
        assertTrue("sn should be null/blank but was '${parsed.sn}'", parsed.sn.isNullOrBlank())

        // Суммы из чека: «Jami to`lov: 375 460,00» и «Umumiy QQS qiymati: 40 227,86».
        assertEquals(37_546_000L, parsed.totalAmountTiyin)
        assertEquals(4_022_786L, parsed.vatAmountTiyin)
    }

    /**
     * Регрессия: чек ofd.soliq.uz/check ("CHEESE DAY" MCHJ) — адрес на узбекской
     * кириллице («Миробод тумани, …») ранее не распознавался, а SN извлекался корректно.
     */
    @Test
    fun parses_soliq_check_cheese_day_cyrillic_address() {
        val html = loadFixture("soliq_check_cheese_day.html")
        val parser = ReceiptParser()

        val parsed = parser.parseHtml(
            qrUrl = "https://ofd.soliq.uz/check?t=VG343420012599&r=84775&c=20260429114017&s=194167710346",
            html = html
        )

        val seller = parsed.sellerName ?: error("seller is null")
        assertTrue("seller='${seller}'", seller.contains("CHEESE DAY", ignoreCase = true))

        // Кириллический адрес должен распознаться.
        val address = parsed.address ?: error("address is null")
        assertTrue("address='${address}'", address.contains("тумани", ignoreCase = true))
        assertTrue("address wrongly contains tin: '${address}'", !address.contains("309896398"))

        // ИНН/STIR — 9 цифр после адреса.
        assertEquals("309896398", parsed.tin)

        // SN заполнен на сайте и не должен быть датой.
        assertEquals("101680", parsed.sn)

        // Суммы: «Jami to`lov: 112 900,00» и «Umumiy QQS qiymati: 12 096,43».
        assertEquals(11_290_000L, parsed.totalAmountTiyin)
        assertEquals(1_209_643L, parsed.vatAmountTiyin)
    }

    /**
     * Регрессия для разных префиксов терминала (YZ/LG помимо VG/EP), кириллических
     * адресов и пустого SN (он не должен подменяться датой).
     */
    @Test
    fun parses_various_terminal_prefixes_addresses_and_sn() {
        data class Case(
            val fixture: String,
            val qr: String,
            val terminalId: String,
            val addressMustContain: String,
            val sn: String?
        )

        val cases = listOf(
            Case(
                "soliq_check_yz_madina.html",
                "https://ofd.soliq.uz/check?t=YZ231006034585&r=239245&c=20260407103812&s=029441353159",
                "YZ231006034585", "Нукус", "iiko320"
            ),
            Case(
                "soliq_check_lg_anglesey.html",
                "https://ofd.soliq.uz/check?t=LG420230644664&r=84229&c=20260407105718&s=054387142715",
                "LG420230644664", "ko'chasi", "AFK-20250725-000664"
            ),
            Case(
                "soliq_check_vg_kredo.html",
                "https://ofd.soliq.uz/check?t=VG343420026083&r=47555&c=20260410205306&s=331638885541",
                "VG343420026083", "tumani", null
            ),
            Case(
                "soliq_check_yz_makfood.html",
                "https://ofd.soliq.uz/check?t=YZ231006034767&r=346750&c=20260504194541&s=614443346717",
                "YZ231006034767", "тумани", "313219"
            ),
            Case(
                "soliq_check_yz_grandpharm.html",
                "https://ofd.soliq.uz/check?t=YZ231006033214&r=95798&c=20260510121146&s=252545824140",
                "YZ231006033214", "тумани", "TS10012020049"
            ),
            Case(
                "soliq_check_lg_madina.html",
                "https://ofd.soliq.uz/check?t=LG420230604913&r=18476&c=20260515194943&s=276922840530",
                "LG420230604913", "Нукус", "iiko217"
            ),
            Case(
                "soliq_check_lg_chilanzar.html",
                "https://ofd.soliq.uz/check?t=LG420230638307&r=4006&c=20260617203253&s=351400591405",
                "LG420230638307", "р-он", "STS-20200618-000442"
            )
        )

        val parser = ReceiptParser()
        for (c in cases) {
            val parsed = parser.parseHtml(c.qr, loadFixture(c.fixture))
            assertEquals("terminalId for ${c.fixture}", c.terminalId, parsed.terminalId)
            val address = parsed.address
            assertNotNull("address is null for ${c.fixture}", address)
            assertTrue(
                "address '${address}' must contain '${c.addressMustContain}' (${c.fixture})",
                address!!.contains(c.addressMustContain, ignoreCase = true)
            )
            if (c.sn == null) {
                assertTrue("sn for ${c.fixture} must be blank but was '${parsed.sn}'", parsed.sn.isNullOrBlank())
            } else {
                assertEquals("sn for ${c.fixture}", c.sn, parsed.sn)
            }
        }
    }

    /**
     * Регрессия: с июня 2026 г. ofd.soliq.uz отдаёт SPA без HTML-данных чека.
     * Данные приходят из JSON API new-ofd.soliq.uz/api/payment.
     */
    @Test
    fun parses_ofd_payment_api_anglesey_receipt() {
        val json = loadFixture("ofd_payment_api_anglesey.json")
        val qrUrl = "https://ofd.soliq.uz/check?t=LG420230644664&r=84229&c=20260407105718&s=054387142715"
        val parsed = OfdPaymentApi.parseResponse(qrUrl, json) ?: error("parseResponse returned null")

        assertTrue(parsed.isValid)
        assertTrue(parsed.sellerName!!.contains("ANGLESEY FOOD", ignoreCase = true))
        assertEquals(16_701_000L, parsed.totalAmountTiyin)
        assertEquals(1_789_392L, parsed.vatAmountTiyin)
        assertTrue(parsed.address!!.contains("Nukus", ignoreCase = true))
        assertEquals("LG420230644664", parsed.terminalId)
        assertEquals("84229", parsed.receiptNumber)
        assertEquals("AFK-20250725-000664", parsed.sn)
    }

    @Test
    fun parses_ofd_payment_api_user_receipt_from_screenshot() {
        val json = loadFixture("ofd_payment_api_user_receipt.json")
        val qrUrl = "https://ofd.soliq.uz/check?t=UZ210317220155&r=192888&c=20260629122805&s=700159535136"
        val parsed = OfdPaymentApi.parseResponse(qrUrl, json) ?: error("parseResponse returned null")

        assertTrue(parsed.isValid)
        assertTrue(parsed.sellerName!!.contains("ANGLESEY FOOD", ignoreCase = true))
        assertEquals(33_800_000L, parsed.totalAmountTiyin)
        assertEquals(3_621_429L, parsed.vatAmountTiyin)
        assertTrue(parsed.address!!.contains("Qo'sh-ko'prik", ignoreCase = true))
        assertEquals("UZ210317220155", parsed.terminalId)
        assertEquals("192888", parsed.receiptNumber)

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
