package com.example.uzb_qqs_for_dip.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptUrlExtractorTest {

    @Test
    fun extractUrlFromText_directValidUrl() {
        val url = "https://ofd.soliq.uz/check?id=12345"
        val extracted = ReceiptUrlExtractor.extractUrlFromText(url)
        assertEquals(url, extracted)
    }

    @Test
    fun extractUrlFromText_supportedHosts() {
        assertEquals(
            "https://ofd.soliq.uz/check?id=1",
            ReceiptUrlExtractor.extractUrlFromText("https://ofd.soliq.uz/check?id=1")
        )
        assertEquals(
            "https://my.soliq.uz/receipt?id=2",
            ReceiptUrlExtractor.extractUrlFromText("https://my.soliq.uz/receipt?id=2")
        )
        assertEquals(
            "https://ofd.multicard.uz/check?id=3",
            ReceiptUrlExtractor.extractUrlFromText("https://ofd.multicard.uz/check?id=3")
        )
    }

    @Test
    fun extractUrlFromText_unsupportedHostReturnsNull() {
        assertNull(ReceiptUrlExtractor.extractUrlFromText("https://google.com/check?id=1"))
        assertNull(ReceiptUrlExtractor.extractUrlFromText("https://yandex.uz/check?id=1"))
    }

    @Test
    fun extractUrlFromText_removesTrailingPunctuation() {
        assertEquals(
            "https://ofd.soliq.uz/check?id=123",
            ReceiptUrlExtractor.extractUrlFromText("Вот чек: https://ofd.soliq.uz/check?id=123.")
        )
        assertEquals(
            "https://ofd.soliq.uz/check?id=123",
            ReceiptUrlExtractor.extractUrlFromText("Чек (https://ofd.soliq.uz/check?id=123)")
        )
        assertEquals(
            "https://ofd.soliq.uz/check?id=123",
            ReceiptUrlExtractor.extractUrlFromText("Ссылка: https://ofd.soliq.uz/check?id=123!")
        )
        assertEquals(
            "https://ofd.soliq.uz/check?id=123",
            ReceiptUrlExtractor.extractUrlFromText("https://ofd.soliq.uz/check?id=123, сохраните")
        )
    }

    @Test
    fun extractUrlFromText_urlWithoutScheme() {
        assertEquals(
            "https://ofd.soliq.uz/check?id=12345",
            ReceiptUrlExtractor.extractUrlFromText("ofd.soliq.uz/check?id=12345")
        )
    }

    @Test
    fun extractUrlFromText_multipleUrlsFindsSupported() {
        val text = "Сайт компании https://example.com, чек: https://ofd.soliq.uz/check?id=999"
        assertEquals(
            "https://ofd.soliq.uz/check?id=999",
            ReceiptUrlExtractor.extractUrlFromText(text)
        )
    }

    @Test
    fun isValidReceiptUrl_validation() {
        assertTrue(ReceiptUrlExtractor.isValidReceiptUrl("https://ofd.soliq.uz/check?id=1"))
        assertTrue(ReceiptUrlExtractor.isValidReceiptUrl("http://my.soliq.uz/check"))
        assertTrue(ReceiptUrlExtractor.isValidReceiptUrl("https://ofd.multicard.uz/receipt"))
        assertFalse(ReceiptUrlExtractor.isValidReceiptUrl("https://other.domain.com/receipt"))
        assertFalse(ReceiptUrlExtractor.isValidReceiptUrl("ftp://ofd.soliq.uz/check"))
        assertFalse(ReceiptUrlExtractor.isValidReceiptUrl(""))
    }
}
