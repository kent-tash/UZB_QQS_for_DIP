package com.example.uzb_qqs_for_dip.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormat {
    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("ru-RU")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    private val moneyFormat = DecimalFormat("#,##0.00", symbols).apply {
        isGroupingUsed = true
    }

    /** Форматирует сумму в тийинах: 85337400 -> "853 374,00". */
    fun fromTiyin(tiyin: Long): String {
        val sum = tiyin / 100.0
        return moneyFormat.format(sum)
    }

    /** Парсит строку вида "853 374,00" / "853,374.00" / "853374" в тийины. */
    fun toTiyin(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val cleaned = raw
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace("'", "")
            .filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        if (cleaned.isEmpty()) return 0L

        val negative = cleaned.startsWith('-')
        val unsigned = cleaned.removePrefix("-")

        val lastDot = unsigned.lastIndexOf('.')
        val lastComma = unsigned.lastIndexOf(',')
        val decimalIndex = maxOf(lastDot, lastComma)

        val (intPart, fracPart) = if (decimalIndex < 0) {
            unsigned to ""
        } else {
            val tail = unsigned.substring(decimalIndex + 1)
            // Хвост из 1-2 цифр считаем десятичной частью, иначе это разделитель тысяч.
            if (tail.length in 1..2 && tail.all { it.isDigit() }) {
                unsigned.substring(0, decimalIndex) to tail
            } else {
                unsigned to ""
            }
        }

        val intDigits = intPart.filter { it.isDigit() }
        val fracDigits = (fracPart + "00").take(2)
        val whole = (intDigits.ifEmpty { "0" }).toLong() * 100L + fracDigits.toLong()
        return if (negative) -whole else whole
    }
}
