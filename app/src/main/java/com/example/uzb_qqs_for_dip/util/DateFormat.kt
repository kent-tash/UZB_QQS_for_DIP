package com.example.uzb_qqs_for_dip.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateFormat {
    private val ruLocale: Locale = Locale.forLanguageTag("ru-RU")

    private val ruDateTime = SimpleDateFormat("dd.MM.yyyy HH:mm", ruLocale).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tashkent")
    }
    private val ruDate = SimpleDateFormat("dd.MM.yyyy", ruLocale).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tashkent")
    }
    private val isoDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tashkent")
    }

    fun formatDateTime(timestampMs: Long): String = ruDateTime.format(Date(timestampMs))
    fun formatDate(timestampMs: Long): String = ruDate.format(Date(timestampMs))
    fun formatIso(timestampMs: Long): String = isoDateTime.format(Date(timestampMs))

    fun startOfDay(timestampMs: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tashkent"))
        cal.timeInMillis = timestampMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfDay(timestampMs: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tashkent"))
        cal.timeInMillis = timestampMs
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    /**
     * Пытается распарсить дату чека в одном из распространённых форматов
     * (dd.MM.yyyy [HH:mm[:ss]], yyyy-MM-dd HH:mm:ss, dd/MM/yyyy и т.п.).
     * Возвращает null, если ни один формат не подошёл.
     */
    fun tryParseReceiptDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
            .replace("\u00A0", " ")
            // На страницах ofd.soliq.uz/epi дата выводится как "29.03.2026, 18:58" —
            // нормализуем: запятая между датой и временем превращается в пробел.
            .replace(Regex(",\\s*"), " ")
            .replace(Regex("\\s+"), " ")
        val patterns = listOf(
            "dd.MM.yyyy HH:mm:ss",
            "dd.MM.yyyy HH:mm",
            "dd.MM.yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("Asia/Tashkent")
                }
                return sdf.parse(text)?.time
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }
}
