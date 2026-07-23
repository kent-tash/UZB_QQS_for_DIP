package com.example.uzb_qqs_for_dip.scan

import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import com.google.mlkit.vision.text.Text
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Одна строка таблицы распечатанного реестра. */
data class PaperReportRow(
    val rowNumber: Int?,
    val sellerName: String,
    val totalAmountTiyin: Long,
    val vatAmountTiyin: Long,
    /** Дата покупки (ms), время 00:00 если не распознано. */
    val purchasedAt: Long,
    val rawLine: String = ""
)

/** Результат разбора одной страницы распечатки. */
data class PaperReportPageParse(
    val rows: List<PaperReportRow>,
    val recognizedCellCount: Int,
    val ocrFullName: String?,
    val ocrPosition: String?,
    val ocrQuarter: Quarter?,
    val ocrYear: Int?,
    val ocrPeriodLabel: String?
)

/**
 * Разбирает OCR-текст распечатанного реестра (как PDF-отчёт приложения):
 * № | продавец | сумма | НДС | дата.
 */
object PaperReportTableParser {

    private val dateRegex = Regex("""\b(\d{1,2})[./](\d{1,2})[./](\d{2,4})(?:\s+(\d{1,2}):(\d{2}))?\b""")
    private val moneyTokenRegex = Regex(
        """(?<![A-Za-zА-Яа-я0-9])(\d{1,3}(?:[ \u00A0]\d{3})*(?:[.,]\d{1,2})?|\d+[.,]\d{1,2}|\d{4,})(?![A-Za-zА-Яа-я0-9])"""
    )
    private val rowStartRegex = Regex("""^\s*(\d{1,3})[.)]?\s+""")
    private val totalsRegex = Regex("""итог""", RegexOption.IGNORE_CASE)

    private val quarterPatterns = listOf(
        Regex("""\b([1IІ]|I{1,3}|IV)\s*квартал""", RegexOption.IGNORE_CASE),
        Regex("""\b(Q[1-4])\b""", RegexOption.IGNORE_CASE)
    )
    private val yearRegex = Regex("""\b(20\d{2})\s*г?\.?\b""", RegexOption.IGNORE_CASE)

    fun parse(text: Text): PaperReportPageParse {
        val lines = extractLines(text)
        val fullText = text.text.orEmpty()
        val header = parseHeader(fullText, lines.map { it.text })

        val dataRows = mutableListOf<PaperReportRow>()
        for (line in lines) {
            val row = parseDataLine(line.text) ?: continue
            if (totalsRegex.containsMatchIn(line.text)) continue
            dataRows.add(row)
        }

        // Если построчно мало строк — пробуем склейку соседних OCR-линий
        if (dataRows.size < 3) {
            dataRows.clear()
            val merged = mergeBrokenLines(lines.map { it.text })
            for (line in merged) {
                val row = parseDataLine(line) ?: continue
                if (totalsRegex.containsMatchIn(line)) continue
                dataRows.add(row)
            }
        }

        val cells = dataRows.sumOf { row ->
            var n = 0
            if (row.rowNumber != null) n++
            if (row.sellerName.isNotBlank()) n++
            if (row.totalAmountTiyin > 0) n++
            if (row.vatAmountTiyin > 0) n++
            if (row.purchasedAt > 0) n++
            n
        }

        return PaperReportPageParse(
            rows = dataRows.distinctBy {
                "${it.rowNumber}|${it.purchasedAt}|${it.totalAmountTiyin}|${it.sellerName.lowercase()}"
            },
            recognizedCellCount = cells,
            ocrFullName = header.fullName,
            ocrPosition = header.position,
            ocrQuarter = header.quarter,
            ocrYear = header.year,
            ocrPeriodLabel = header.periodLabel
        )
    }

    private data class OcrLine(val text: String, val centerY: Float, val left: Float)

    private fun extractLines(text: Text): List<OcrLine> {
        val out = mutableListOf<OcrLine>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val t = line.text.trim()
                if (t.isEmpty()) continue
                out.add(
                    OcrLine(
                        text = t,
                        centerY = (box.top + box.bottom) / 2f,
                        left = box.left.toFloat()
                    )
                )
            }
        }
        if (out.isEmpty()) {
            text.text.orEmpty().lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEachIndexed { i, t -> out.add(OcrLine(t, i * 20f, 0f)) }
        }
        // Кластеризация по Y: соседние линии с близким Y объединяем слева направо
        out.sortWith(compareBy({ it.centerY }, { it.left }))
        if (out.size <= 1) return out

        val clustered = mutableListOf<OcrLine>()
        var bucket = mutableListOf(out.first())
        val threshold = estimateRowThreshold(out)
        for (i in 1 until out.size) {
            val prev = bucket.last()
            val cur = out[i]
            if (abs(cur.centerY - prev.centerY) <= threshold) {
                bucket.add(cur)
            } else {
                clustered.add(mergeBucket(bucket))
                bucket = mutableListOf(cur)
            }
        }
        clustered.add(mergeBucket(bucket))
        return clustered
    }

    private fun estimateRowThreshold(lines: List<OcrLine>): Float = 22f

    private fun mergeBucket(bucket: List<OcrLine>): OcrLine {
        val sorted = bucket.sortedBy { it.left }
        return OcrLine(
            text = sorted.joinToString(" ") { it.text },
            centerY = sorted.map { it.centerY }.average().toFloat(),
            left = sorted.minOf { it.left }
        )
    }

    private fun mergeBrokenLines(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            var cur = lines[i]
            while (i + 1 < lines.size && !looksComplete(cur) && !rowStartRegex.containsMatchIn(lines[i + 1])) {
                i++
                cur = "$cur ${lines[i]}"
            }
            result.add(cur)
            i++
        }
        return result
    }

    private fun looksComplete(line: String): Boolean =
        dateRegex.containsMatchIn(line) && moneyTokenRegex.findAll(line).count() >= 2

    private data class HeaderInfo(
        val fullName: String?,
        val position: String?,
        val quarter: Quarter?,
        val year: Int?,
        val periodLabel: String?
    )

    private fun parseHeader(fullText: String, lines: List<String>): HeaderInfo {
        val joined = (listOf(fullText) + lines).joinToString("\n")
        val year = yearRegex.find(joined)?.groupValues?.get(1)?.toIntOrNull()
        var quarter: Quarter? = null
        for (p in quarterPatterns) {
            val m = p.find(joined) ?: continue
            quarter = mapQuarterToken(m.groupValues[1])
            if (quarter != null) break
        }
        val periodLabel = when {
            quarter != null && year != null -> "${quarterLabel(quarter)} $year г."
            else -> null
        }

        // Ищем строку с ФИО: часто после «сотрудника» или отдельная строка должность+ФИО
        val nameCandidate = lines.firstOrNull { line ->
            val lower = line.lowercase(Locale.getDefault())
            lower.contains("сотрудник") ||
                (line.split(Regex("\\s+")).size >= 2 &&
                    line.any { it.isLetter() } &&
                    !lower.contains("реестр") &&
                    !lower.contains("наимен") &&
                    !lower.contains("магазин") &&
                    !dateRegex.containsMatchIn(line) &&
                    moneyTokenRegex.findAll(line).count() < 2)
        }

        var position: String? = null
        var fullName: String? = null
        if (nameCandidate != null) {
            val cleaned = nameCandidate
                .replace(Regex("""(?i)сотрудника?\s+"""), "")
                .replace(Regex("""(?i)реестр.*документов\s*"""), "")
                .trim()
            val parts = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
            // Эвристика: последние 2–3 слова — ФИО (Фамилия Имя Отчество)
            when {
                parts.size >= 4 -> {
                    position = parts.dropLast(3).joinToString(" ")
                    fullName = parts.takeLast(3).joinToString(" ")
                }
                parts.size == 3 -> {
                    // Может быть «Должность Фамилия Имя» или полное ФИО
                    if (parts[0].any { it.isLowerCase() } || parts[0].length <= 4) {
                        position = parts[0]
                        fullName = parts.drop(1).joinToString(" ")
                    } else {
                        fullName = parts.joinToString(" ")
                    }
                }
                parts.size == 2 -> fullName = parts.joinToString(" ")
                else -> fullName = cleaned.ifBlank { null }
            }
        }

        // Родительный падеж «Цыганкова Андрея…» → приближённо к именительному по первым буквам
        fullName = fullName?.let { normalizeGenitiveName(it) }

        return HeaderInfo(fullName, position, quarter, year, periodLabel)
    }

    private fun mapQuarterToken(token: String): Quarter? {
        val t = token.trim().uppercase(Locale.ROOT)
            .replace('І', 'I')
            .replace('1', 'I')
        return when (t) {
            "I", "1", "Q1" -> Quarter.Q1
            "II", "2", "Q2" -> Quarter.Q2
            "III", "3", "Q3" -> Quarter.Q3
            "IV", "4", "Q4" -> Quarter.Q4
            else -> null
        }
    }

    private fun quarterLabel(q: Quarter): String = when (q) {
        Quarter.Q1 -> "I квартал"
        Quarter.Q2 -> "II квартал"
        Quarter.Q3 -> "III квартал"
        Quarter.Q4 -> "IV квартал"
        Quarter.Custom -> "Квартал"
    }

    /** Грубая нормализация родительного падежа ФИО к поисковой форме. */
    private fun normalizeGenitiveName(raw: String): String {
        val parts = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return raw
        fun stem(word: String): String {
            val w = word.trim()
            if (w.length < 4) return w
            return when {
                w.endsWith("ова", ignoreCase = true) || w.endsWith("ева", ignoreCase = true) ->
                    w.dropLast(1) // Цыганкова → Цыганков
                w.endsWith("ого", ignoreCase = true) || w.endsWith("его", ignoreCase = true) ->
                    w.dropLast(3) + "ый"
                w.endsWith("а", ignoreCase = true) && w.length > 4 -> w.dropLast(1) // Андрея → Андрей (approx)
                else -> w
            }
        }
        return parts.joinToString(" ") { stem(it) }
    }

    fun parseDataLine(line: String): PaperReportRow? {
        val trimmed = line.trim()
        if (trimmed.length < 8) return null
        if (totalsRegex.containsMatchIn(trimmed)) return null
        if (trimmed.contains("наименование", ignoreCase = true)) return null
        if (trimmed.contains("магазин", ignoreCase = true) && !moneyTokenRegex.containsMatchIn(trimmed)) return null

        val dateMatch = dateRegex.findAll(trimmed).lastOrNull() ?: return null
        val purchasedAt = parseDate(dateMatch) ?: return null

        val beforeDate = trimmed.substring(0, dateMatch.range.first).trim()
        val moneyMatches = moneyTokenRegex.findAll(beforeDate).toList()
        if (moneyMatches.size < 2) return null

        val vatMatch = moneyMatches.last()
        val totalMatch = moneyMatches[moneyMatches.size - 2]
        val total = MoneyFormat.toTiyin(totalMatch.value)
        val vat = MoneyFormat.toTiyin(vatMatch.value)
        if (total <= 0L) return null

        var sellerPart = beforeDate.substring(0, totalMatch.range.first).trim()
        var rowNumber: Int? = null
        val numMatch = rowStartRegex.find(sellerPart)
        if (numMatch != null) {
            rowNumber = numMatch.groupValues[1].toIntOrNull()
            sellerPart = sellerPart.substring(numMatch.range.last + 1).trim()
        } else {
            // № мог быть отдельным токеном в начале
            val firstToken = sellerPart.substringBefore(' ')
            if (firstToken.all { it.isDigit() } && firstToken.length <= 3) {
                rowNumber = firstToken.toIntOrNull()
                sellerPart = sellerPart.substringAfter(' ').trim()
            }
        }
        sellerPart = sellerPart
            .replace(Regex("""^[|•·\-–—]+\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (sellerPart.length < 2) return null

        return PaperReportRow(
            rowNumber = rowNumber,
            sellerName = sellerPart,
            totalAmountTiyin = total,
            vatAmountTiyin = vat,
            purchasedAt = purchasedAt,
            rawLine = trimmed
        )
    }

    private fun parseDate(match: MatchResult): Long? {
        val d = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        var y = match.groupValues[3].toIntOrNull() ?: return null
        if (y < 100) y += 2000
        val hour = match.groupValues.getOrNull(4)?.toIntOrNull() ?: 0
        val minute = match.groupValues.getOrNull(5)?.toIntOrNull() ?: 0
        if (d !in 1..31 || m !in 1..12 || y !in 2000..2100) return null
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Сравнение ФИО с листа и профиля: совпадение по фамилии (первое слово). */
    fun namesLikelyMatch(ocrName: String?, profileName: String): Boolean {
        if (ocrName.isNullOrBlank()) return true
        fun tokens(s: String) = s.lowercase(Locale.getDefault())
            .replace(Regex("[^a-zа-яё0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
        val a = tokens(ocrName)
        val b = tokens(profileName)
        if (a.isEmpty() || b.isEmpty()) return true
        return a.any { at -> b.any { bt -> at.take(4) == bt.take(4) || at.startsWith(bt.take(4)) || bt.startsWith(at.take(4)) } }
    }
}
