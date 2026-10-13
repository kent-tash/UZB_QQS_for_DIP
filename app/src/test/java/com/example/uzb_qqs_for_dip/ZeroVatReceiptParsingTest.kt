package com.example.uzb_qqs_for_dip

import com.example.uzb_qqs_for_dip.network.OfdPaymentApi
import com.example.uzb_qqs_for_dip.network.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты для фичи «Обработка чеков с нулевым НДС»:
 * 1. Проверка OfdPaymentApi.parseResponse с vatTotal == 0.0, vatTotal > 0.0, vatTotal отсутствующим/null.
 * 2. Проверка ReceiptParser с явным 0 НДС в HTML vs отсутствием метки НДС.
 */
class ZeroVatReceiptParsingTest {

    private val testQrUrl = "https://ofd.soliq.uz/check?t=LG123456789012&r=999&c=20260510143000&s=123456789"

    // =========================================================================
    // OfdPaymentApi.parseResponse tests
    // =========================================================================

    @Test
    fun ofdPaymentApi_vatTotalZeroDouble_parsedAsZeroTiyinAndValid() {
        val json = """
            {
                "success": true,
                "data": {
                    "terminalId": "LG123456789012",
                    "paymentNo": "999",
                    "paymentDate": "2026-05-10 14:30:00",
                    "cashTotal": 25000.0,
                    "cardTotal": 0.0,
                    "vatTotal": 0.0,
                    "extraInfo": {
                        "companyName": "OOO ZERO VAT"
                    }
                }
            }
        """.trimIndent()

        val parsed = OfdPaymentApi.parseResponse(testQrUrl, json)
        assertNotNull(parsed)
        assertEquals(0L, parsed!!.vatAmountTiyin)
        assertEquals(2_500_000L, parsed.totalAmountTiyin)
        assertEquals("OOO ZERO VAT", parsed.sellerName)
        assertTrue("Receipt with 0 VAT must be valid if all fields present", parsed.isValid)
    }

    @Test
    fun ofdPaymentApi_vatTotalZeroInteger_parsedAsZeroTiyinAndValid() {
        val json = """
            {
                "success": true,
                "data": {
                    "terminalId": "LG123456789012",
                    "paymentNo": "999",
                    "paymentDate": "2026-05-10 14:30:00",
                    "cashTotal": 10000,
                    "cardTotal": 0,
                    "vatTotal": 0,
                    "extraInfo": {
                        "companyName": "OOO ZERO INT"
                    }
                }
            }
        """.trimIndent()

        val parsed = OfdPaymentApi.parseResponse(testQrUrl, json)
        assertNotNull(parsed)
        assertEquals(0L, parsed!!.vatAmountTiyin)
        assertEquals(1_000_000L, parsed.totalAmountTiyin)
        assertTrue(parsed.isValid)
    }

    @Test
    fun ofdPaymentApi_vatTotalPositive_parsedCorrectlyInTiyin() {
        val json = """
            {
                "success": true,
                "data": {
                    "terminalId": "LG123456789012",
                    "paymentNo": "999",
                    "paymentDate": "2026-05-10 14:30:00",
                    "cashTotal": 0.0,
                    "cardTotal": 112000.0,
                    "vatTotal": 12000.0,
                    "extraInfo": {
                        "companyName": "OOO STANDARD VAT"
                    }
                }
            }
        """.trimIndent()

        val parsed = OfdPaymentApi.parseResponse(testQrUrl, json)
        assertNotNull(parsed)
        assertEquals(1_200_000L, parsed!!.vatAmountTiyin)
        assertEquals(11_200_000L, parsed.totalAmountTiyin)
        assertTrue(parsed.isValid)
    }

    @Test
    fun ofdPaymentApi_vatTotalPositiveWithFraction_roundedProperly() {
        val json = """
            {
                "success": true,
                "data": {
                    "terminalId": "LG123456789012",
                    "paymentNo": "999",
                    "paymentDate": "2026-05-10 14:30:00",
                    "cashTotal": 55555.55,
                    "cardTotal": 0.0,
                    "vatTotal": 5952.38,
                    "extraInfo": {
                        "companyName": "OOO FRACTION VAT"
                    }
                }
            }
        """.trimIndent()

        val parsed = OfdPaymentApi.parseResponse(testQrUrl, json)
        assertNotNull(parsed)
        assertEquals(595_238L, parsed!!.vatAmountTiyin)
        assertEquals(5_555_555L, parsed.totalAmountTiyin)
        assertTrue(parsed.isValid)
    }

    @Test
    fun ofdPaymentApi_missingVatTotalKey_parsedAsNullAndNotValid() {
        val json = """
            {
                "success": true,
                "data": {
                    "terminalId": "LG123456789012",
                    "paymentNo": "999",
                    "paymentDate": "2026-05-10 14:30:00",
                    "cashTotal": 10000.0,
                    "cardTotal": 0.0,
                    "extraInfo": {
                        "companyName": "OOO NO VAT KEY"
                    }
                }
            }
        """.trimIndent()

        val parsed = OfdPaymentApi.parseResponse(testQrUrl, json)
        assertNotNull(parsed)
        assertNull("Missing vatTotal key should result in null vatAmountTiyin", parsed!!.vatAmountTiyin)
        assertFalse("Receipt with missing VAT is not valid", parsed.isValid)
    }

    @Test
    fun ofdPaymentApi_explicitNullVatTotal_parsedAsNullAndNotValid() {
        val json = """
            {
                "success": true,
                "data": {
                    "terminalId": "LG123456789012",
                    "paymentNo": "999",
                    "paymentDate": "2026-05-10 14:30:00",
                    "cashTotal": 10000.0,
                    "cardTotal": 0.0,
                    "vatTotal": null,
                    "extraInfo": {
                        "companyName": "OOO NULL VAT"
                    }
                }
            }
        """.trimIndent()

        val parsed = OfdPaymentApi.parseResponse(testQrUrl, json)
        assertNotNull(parsed)
        assertNull("Explicit null vatTotal should result in null vatAmountTiyin", parsed!!.vatAmountTiyin)
        assertFalse("Receipt with null VAT is not valid", parsed.isValid)
    }

    @Test
    fun ofdPaymentApi_negativeVatTotal_parsedAsNullAndNotValid() {
        val json = """
            {
                "success": true,
                "data": {
                    "terminalId": "LG123456789012",
                    "paymentNo": "999",
                    "paymentDate": "2026-05-10 14:30:00",
                    "cashTotal": 10000.0,
                    "cardTotal": 0.0,
                    "vatTotal": -100.0,
                    "extraInfo": {
                        "companyName": "OOO NEGATIVE VAT"
                    }
                }
            }
        """.trimIndent()

        val parsed = OfdPaymentApi.parseResponse(testQrUrl, json)
        assertNotNull(parsed)
        assertNull("Negative vatTotal should result in null vatAmountTiyin", parsed!!.vatAmountTiyin)
        assertFalse(parsed.isValid)
    }

    // =========================================================================
    // ReceiptParser HTML tests
    // =========================================================================

    @Test
    fun receiptParser_htmlExplicitZeroVat_uzbekUmumiyQqsQiymati() {
        val html = """
            <html>
            <body>
                <h1>Savdo cheki</h1>
                <h2>OOO ZERO SERVICE</h2>
                <div>Sana: 15.05.2026 12:00:00</div>
                <div>Jami to'lov: 75 000.00</div>
                <div>Umumiy QQS qiymati: 0.00</div>
            </body>
            </html>
        """.trimIndent()

        val parser = ReceiptParser()
        val parsed = parser.parseHtml(testQrUrl, html)
        assertEquals(7_500_000L, parsed.totalAmountTiyin)
        assertEquals(0L, parsed.vatAmountTiyin)
        assertTrue(parsed.isValid)
    }

    @Test
    fun receiptParser_htmlExplicitZeroVat_uzbekQqsSummasiZero() {
        val html = """
            <html>
            <body>
                <h1>Savdo cheki</h1>
                <h2>OOO ZERO SERVICE</h2>
                <div>Sana: 15.05.2026 12:00:00</div>
                <div>Jami to'lov: 75 000.00</div>
                <div>QQS summasi: 0</div>
            </body>
            </html>
        """.trimIndent()

        val parser = ReceiptParser()
        val parsed = parser.parseHtml(testQrUrl, html)
        assertEquals(7_500_000L, parsed.totalAmountTiyin)
        assertEquals(0L, parsed.vatAmountTiyin)
        assertTrue(parsed.isValid)
    }

    @Test
    fun receiptParser_htmlExplicitZeroVat_russianItogovyNdsComma() {
        val html = """
            <html>
            <body>
                <h1>Электронный чек</h1>
                <h2>OOO ZERO SERVICE</h2>
                <div>Дата: 15.05.2026 12:00:00</div>
                <div>Итого: 40 000,00</div>
                <div>Итоговый НДС: 0,00</div>
            </body>
            </html>
        """.trimIndent()

        val parser = ReceiptParser()
        val parsed = parser.parseHtml(testQrUrl, html)
        assertEquals(4_000_000L, parsed.totalAmountTiyin)
        assertEquals(0L, parsed.vatAmountTiyin)
        assertTrue(parsed.isValid)
    }

    @Test
    fun receiptParser_htmlExplicitZeroVat_domTableLabelValue() {
        val html = """
            <html>
            <body>
                <h1>Savdo cheki</h1>
                <h2>OOO ZERO TABLE</h2>
                <div>Sana: 15.05.2026 12:00:00</div>
                <table>
                    <tr><th>Jami to'lov</th><td>100 000.00</td></tr>
                    <tr><th>Umumiy QQS qiymati</th><td>0.00</td></tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val parser = ReceiptParser()
        val parsed = parser.parseHtml(testQrUrl, html)
        assertEquals(10_000_000L, parsed.totalAmountTiyin)
        assertEquals(0L, parsed.vatAmountTiyin)
        assertTrue(parsed.isValid)
    }

    @Test
    fun receiptParser_htmlMissingVatTag_parsedAsNullAndNotValid() {
        val html = """
            <html>
            <body>
                <h1>Savdo cheki</h1>
                <h2>OOO NO VAT TAG</h2>
                <div>Sana: 15.05.2026 12:00:00</div>
                <div>Jami to'lov: 50 000.00</div>
            </body>
            </html>
        """.trimIndent()

        val parser = ReceiptParser()
        val parsed = parser.parseHtml(testQrUrl, html)
        assertEquals(5_000_000L, parsed.totalAmountTiyin)
        assertNull("When VAT tag is missing entirely, vatAmountTiyin must be null", parsed.vatAmountTiyin)
        assertFalse("When vatAmountTiyin is null, isValid must be false", parsed.isValid)
    }

    @Test
    fun receiptParser_htmlPositiveVat_parsedCorrectly() {
        val html = """
            <html>
            <body>
                <h1>Savdo cheki</h1>
                <h2>OOO POSITIVE VAT</h2>
                <div>Sana: 15.05.2026 12:00:00</div>
                <div>Jami to'lov: 50 000.00</div>
                <div>Umumiy QQS qiymati: 5 357.14</div>
            </body>
            </html>
        """.trimIndent()

        val parser = ReceiptParser()
        val parsed = parser.parseHtml(testQrUrl, html)
        assertEquals(5_000_000L, parsed.totalAmountTiyin)
        assertEquals(535_714L, parsed.vatAmountTiyin)
        assertTrue(parsed.isValid)
    }
}
