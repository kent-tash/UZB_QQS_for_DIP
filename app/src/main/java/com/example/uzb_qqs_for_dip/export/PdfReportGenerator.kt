package com.example.uzb_qqs_for_dip.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Параметры формирования PDF-отчёта по чекам пользователя.
 *
 * @property user Пользователь, чьи чеки выгружаются (его данные подставятся в шапку и подпись).
 * @property periodStart Начало периода (миллисекунды).
 * @property periodEnd Конец периода (миллисекунды).
 * @property quarterLabel Если выбран квартал — его название (например, «II квартал (апрель–июнь) 2026 г.»),
 * иначе null и в шапке выводятся даты периода.
 * @property rows Чеки пользователя за указанный период (отсортированные по дате).
 * @property generatedAt Дата формирования отчёта.
 */
data class ReportParams(
    val user: User,
    val periodStart: Long,
    val periodEnd: Long,
    val rows: List<ReceiptWithUser>,
    val quarterLabel: String? = null,
    val generatedAt: Long = System.currentTimeMillis()
)

/**
 * Формирует PDF-отчёт по чекам, готовый к печати/сохранению. Кириллица отрисовывается через
 * системный Typeface.SANS_SERIF, в котором у Android есть полноценный набор глифов.
 *
 * Структура страницы:
 *   1. Шапка (по центру) — заголовок реестра; с новой строки период; с новой строки должность и ФИО.
 *   2. Таблица — №, Наименование организации, Сумма, НДС, Дата, со строкой «Итого».
 *   3. Подпись — должность, линия для живой подписи, И.О. Фамилия, поле «Дата: _______».
 */
object PdfReportGenerator {

    // A4 в pt (1/72 дюйма): 595 x 842.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    // Колонки: №, Наименование организации, Сумма, НДС, Дата.
    private val COL_WIDTHS = floatArrayOf(28f, 170f, 110f, 100f, 107f)
    private val COL_HEADERS = arrayOf("№", "Наименование организации", "Сумма", "НДС", "Дата")

    private const val COL_NUM = 0
    private const val COL_SELLER = 1
    private const val COL_TOTAL = 2
    private const val COL_VAT = 3
    private const val COL_DATE = 4

    private const val CELL_PADDING_H = 5f
    private const val CELL_PADDING_V = 4f
    private const val LINE_HEIGHT = 12f
    private const val ROW_HEIGHT_MIN = 22f
    private const val HEADER_ROW_HEIGHT = 28f
    private const val MAX_SELLER_LINES = 8

    suspend fun generate(
        context: Context,
        params: ReportParams,
        fileName: String = "report_${params.user.id}_${System.currentTimeMillis()}.pdf"
    ): File = withContext(Dispatchers.IO) {
        val file = File(ExportPaths.exportsDir(context), fileName)
        val doc = PdfDocument()
        try {
            renderInto(doc, params)
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        file
    }

    /**
     * Рендерит отчёт в [doc]. Реально проходов два:
     *   1. «Сухой» прогон во временный документ — чтобы узнать общее число страниц
     *      (это нужно для нумерации в правом верхнем углу: первую страницу не нумеруем,
     *      и нумерация добавляется только если страниц больше одной).
     *   2. Настоящий прогон в выходной [doc] с уже известным [totalPages] — рисуются
     *      номера страниц и нижний колонтитул (должность + И.О. Фамилия).
     *
     * Открыто, чтобы тот же контент использовать в PrintDocumentAdapter.
     */
    fun renderInto(doc: PdfDocument, params: ReportParams) {
        val totalPages = countPages(params)
        renderOnce(doc, params, totalPages)
    }

    /** Сухой прогон ради подсчёта страниц. Временный документ закрывается. */
    private fun countPages(params: ReportParams): Int {
        val tmp = PdfDocument()
        return try {
            renderOnce(tmp, params, totalPages = 0)
            tmp.pages.size
        } finally {
            tmp.close()
        }
    }

    /**
     * Один проход рендера. При [totalPages] > 1 рисует номера страниц (со 2-й включительно),
     * на каждой странице добавляет в правый нижний угол должность и подпись из профиля.
     */
    private fun renderOnce(doc: PdfDocument, params: ReportParams, totalPages: Int) {
        val typeface = Typeface.SANS_SERIF
        val typefaceBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 13f
            color = 0xFF111827.toInt()
            textAlign = Paint.Align.CENTER
        }
        val tableHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 10.5f
            color = 0xFFFFFFFF.toInt()
        }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 10f
            color = 0xFF111827.toInt()
        }
        val cellNumberPaint = TextPaint(cellPaint).apply { textAlign = Paint.Align.RIGHT }
        val cellCenterPaint = TextPaint(cellPaint).apply { textAlign = Paint.Align.CENTER }
        val totalsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 11f
            color = 0xFF111827.toInt()
            textAlign = Paint.Align.RIGHT
        }
        val totalsLabelPaint = TextPaint(totalsPaint).apply { textAlign = Paint.Align.RIGHT }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB0B7C3.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val tableHeaderFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1F4E79.toInt()
            style = Paint.Style.FILL
        }
        val totalsFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE7EEF7.toInt()
            style = Paint.Style.FILL
        }
        val zebraFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF7F8FA.toInt()
            style = Paint.Style.FILL
        }
        val signatureLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111827.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val signatureTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 11f
            color = 0xFF111827.toInt()
        }
        val signatureSmallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 8.5f
            color = 0xFF6B7280.toInt()
            textAlign = Paint.Align.CENTER
        }

        // Колонтитул: в правом нижнем углу каждой страницы — должность и И.О. Фамилия
        // из профиля (9 пт, по требованию задания).
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 9f
            color = 0xFF374151.toInt()
            textAlign = Paint.Align.RIGHT
        }
        // Номер страницы — в правом верхнем углу, начиная со 2-й; первую не нумеруем
        // (и не нумеруем вовсе, если страниц всего одна).
        val pageNumberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 9.5f
            color = 0xFF6B7280.toInt()
            textAlign = Paint.Align.RIGHT
        }

        val totalSum = params.rows.sumOf { it.receipt.totalAmountTiyin }
        val totalVat = params.rows.sumOf { it.receipt.vatAmountTiyin }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        /**
         * Дорисовывает на текущей странице номер (если нужен) и колонтитул,
         * затем закрывает её через [PdfDocument.finishPage].
         */
        fun finalizeAndFinishPage() {
            // Колонтитул присутствует на каждой странице.
            val footerLine1Y = PAGE_HEIGHT - 22f
            val footerLine2Y = PAGE_HEIGHT - 10f
            val rightX = PAGE_WIDTH - MARGIN
            canvas.drawText(params.user.position, rightX, footerLine1Y, footerPaint)
            canvas.drawText(params.user.initialsSurname, rightX, footerLine2Y, footerPaint)
            // Номер страницы добавляем со второй страницы и только если страниц > 1.
            if (totalPages > 1 && pageNumber > 1) {
                canvas.drawText(pageNumber.toString(), rightX, MARGIN - 4f, pageNumberPaint)
            }
            doc.finishPage(page)
        }

        // ----- Шапка: три блока по центру ---------------------------------
        // 1) заголовок; 2) выбранный период; 3) должность и ФИО полностью.
        val periodText = params.quarterLabel
            ?: "${DateFormat.formatDate(params.periodStart)} — ${DateFormat.formatDate(params.periodEnd)}"
        val titleMaxWidth = PAGE_WIDTH - 2f * MARGIN
        val titleLineHeight = titlePaint.textSize + 4f
        val centerX = PAGE_WIDTH / 2f

        fun drawCenteredTitleBlock(text: String, maxLines: Int = 8) {
            for (line in wrapText(text, titlePaint, titleMaxWidth, maxLines)) {
                canvas.drawText(line, centerX, y + titlePaint.textSize, titlePaint)
                y += titleLineHeight
            }
        }

        drawCenteredTitleBlock("Реестр предъявляемых к возмещению платежных документов")
        drawCenteredTitleBlock(periodText)
        drawCenteredTitleBlock("${params.user.position} ${params.user.fullName}".trim())
        y += 10f

        // ----- Таблица -----
        val tableLeft = MARGIN
        val tableWidth = COL_WIDTHS.sum()

        // Локальная функция: отрисовать шапку таблицы с переносом длинных заголовков.
        fun drawTableHeader(yTop: Float): Float {
            canvas.drawRect(tableLeft, yTop, tableLeft + tableWidth, yTop + HEADER_ROW_HEIGHT, tableHeaderFill)
            var hx = tableLeft
            tableHeaderPaint.textAlign = Paint.Align.CENTER
            for (i in COL_HEADERS.indices) {
                val cellWidth = COL_WIDTHS[i]
                val maxTextWidth = cellWidth - CELL_PADDING_H * 2f
                val lines = wrapText(COL_HEADERS[i], tableHeaderPaint, maxTextWidth, MAX_SELLER_LINES)
                val totalH = lines.size * LINE_HEIGHT
                var cy = yTop + (HEADER_ROW_HEIGHT - totalH) / 2f + tableHeaderPaint.textSize
                for (line in lines) {
                    canvas.drawText(line, hx + cellWidth / 2f, cy, tableHeaderPaint)
                    cy += LINE_HEIGHT
                }
                canvas.drawRect(hx, yTop, hx + cellWidth, yTop + HEADER_ROW_HEIGHT, borderPaint)
                hx += cellWidth
            }
            return yTop + HEADER_ROW_HEIGHT
        }

        y = drawTableHeader(y)

        // Минимум места для строки с итогами + подписи
        val signatureBlockHeight = 110f
        val bottomLimit = PAGE_HEIGHT - MARGIN - signatureBlockHeight

        // Строки данных
        params.rows.forEachIndexed { idx, item ->
            // Высота строки зависит от количества строк в названии организации.
            val sellerMaxWidth = COL_WIDTHS[COL_SELLER] - CELL_PADDING_H * 2f
            val sellerLines = wrapText(
                item.receipt.sellerName.ifBlank { "—" },
                cellPaint,
                sellerMaxWidth,
                MAX_SELLER_LINES
            )
            val rowHeight = maxOf(
                ROW_HEIGHT_MIN,
                CELL_PADDING_V * 2f + sellerLines.size * LINE_HEIGHT
            )

            if (y + rowHeight > bottomLimit) {
                finalizeAndFinishPage()
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
                y = drawTableHeader(y)
            }

            if (idx % 2 == 1) {
                canvas.drawRect(tableLeft, y, tableLeft + tableWidth, y + rowHeight, zebraFill)
            }

            val centerY = y + rowHeight / 2f + cellPaint.textSize / 3f
            var x = tableLeft

            // №
            canvas.drawText((idx + 1).toString(), x + COL_WIDTHS[COL_NUM] / 2f, centerY, cellCenterPaint)
            canvas.drawRect(x, y, x + COL_WIDTHS[COL_NUM], y + rowHeight, borderPaint)
            x += COL_WIDTHS[COL_NUM]

            // Наименование организации (многострочное, выровнено по левому краю)
            run {
                val totalH = sellerLines.size * LINE_HEIGHT
                var ly = y + (rowHeight - totalH) / 2f + cellPaint.textSize
                for (line in sellerLines) {
                    canvas.drawText(line, x + CELL_PADDING_H, ly, cellPaint)
                    ly += LINE_HEIGHT
                }
                canvas.drawRect(x, y, x + COL_WIDTHS[COL_SELLER], y + rowHeight, borderPaint)
            }
            x += COL_WIDTHS[COL_SELLER]

            // Сумма
            canvas.drawText(
                MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin),
                x + COL_WIDTHS[COL_TOTAL] - CELL_PADDING_H, centerY, cellNumberPaint
            )
            canvas.drawRect(x, y, x + COL_WIDTHS[COL_TOTAL], y + rowHeight, borderPaint)
            x += COL_WIDTHS[COL_TOTAL]

            // НДС
            canvas.drawText(
                MoneyFormat.fromTiyin(item.receipt.vatAmountTiyin),
                x + COL_WIDTHS[COL_VAT] - CELL_PADDING_H, centerY, cellNumberPaint
            )
            canvas.drawRect(x, y, x + COL_WIDTHS[COL_VAT], y + rowHeight, borderPaint)
            x += COL_WIDTHS[COL_VAT]

            // Дата
            canvas.drawText(
                DateFormat.formatDateTime(item.receipt.purchasedAt),
                x + CELL_PADDING_H, centerY, cellPaint
            )
            canvas.drawRect(x, y, x + COL_WIDTHS[COL_DATE], y + rowHeight, borderPaint)

            y += rowHeight
        }

        // Если строк нет — пустая строка-заглушка
        if (params.rows.isEmpty()) {
            val baseline = y + ROW_HEIGHT_MIN / 2f + cellPaint.textSize / 3f
            canvas.drawRect(tableLeft, y, tableLeft + tableWidth, y + ROW_HEIGHT_MIN, zebraFill)
            canvas.drawText(
                "Нет чеков за выбранный период",
                tableLeft + tableWidth / 2f, baseline, cellCenterPaint
            )
            canvas.drawRect(tableLeft, y, tableLeft + tableWidth, y + ROW_HEIGHT_MIN, borderPaint)
            y += ROW_HEIGHT_MIN
        }

        // Строка «Итого:» — объединяет две первые ячейки (№ и наименование)
        if (y + ROW_HEIGHT_MIN > bottomLimit) {
            finalizeAndFinishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }
        canvas.drawRect(tableLeft, y, tableLeft + tableWidth, y + ROW_HEIGHT_MIN, totalsFill)
        val tBaseline = y + ROW_HEIGHT_MIN / 2f + totalsPaint.textSize / 3f
        var tx = tableLeft
        val labelSpan = COL_WIDTHS[COL_NUM] + COL_WIDTHS[COL_SELLER]
        canvas.drawText(
            "Итого:",
            tx + labelSpan - CELL_PADDING_H,
            tBaseline,
            totalsLabelPaint
        )
        canvas.drawRect(tx, y, tx + labelSpan, y + ROW_HEIGHT_MIN, borderPaint)
        tx += labelSpan
        canvas.drawText(
            MoneyFormat.fromTiyin(totalSum),
            tx + COL_WIDTHS[COL_TOTAL] - CELL_PADDING_H, tBaseline, totalsPaint
        )
        canvas.drawRect(tx, y, tx + COL_WIDTHS[COL_TOTAL], y + ROW_HEIGHT_MIN, borderPaint)
        tx += COL_WIDTHS[COL_TOTAL]
        canvas.drawText(
            MoneyFormat.fromTiyin(totalVat),
            tx + COL_WIDTHS[COL_VAT] - CELL_PADDING_H, tBaseline, totalsPaint
        )
        canvas.drawRect(tx, y, tx + COL_WIDTHS[COL_VAT], y + ROW_HEIGHT_MIN, borderPaint)
        tx += COL_WIDTHS[COL_VAT]
        canvas.drawRect(tx, y, tx + COL_WIDTHS[COL_DATE], y + ROW_HEIGHT_MIN, borderPaint)
        y += ROW_HEIGHT_MIN

        // ----- Блок подписи (выровнен к нижней части страницы) -----
        val signatureTop = (PAGE_HEIGHT - MARGIN - signatureBlockHeight).coerceAtLeast(y + 30f)

        // Одной строкой:  [Должность]   ____подпись____   [И.О. Фамилия]
        // Должность и подпись (И.О. Фамилия) выровнены по одной базовой линии,
        // линия для живой подписи проходит по тому же уровню между ними. Подпись
        // «(подпись)» — мелким шрифтом строго под линией.
        val rowY = signatureTop + 26f
        val positionWidth = signatureTextPaint.measureText(params.user.position)
        val initialsWidth = signatureTextPaint.measureText(params.user.initialsSurname)
        // Зазоры между текстом и линией — по 16pt с каждой стороны.
        val lineStart = MARGIN + positionWidth + 16f
        val lineEnd = (PAGE_WIDTH - MARGIN - initialsWidth - 16f).coerceAtLeast(lineStart + 80f)

        canvas.drawText(params.user.position, MARGIN, rowY, signatureTextPaint)
        canvas.drawLine(lineStart, rowY, lineEnd, rowY, signatureLinePaint)
        canvas.drawText(
            params.user.initialsSurname,
            lineEnd + 16f, rowY, signatureTextPaint
        )
        canvas.drawText(
            "(подпись)",
            (lineStart + lineEnd) / 2f,
            rowY + 11f,
            signatureSmallPaint
        )

        // Поле «Дата: ____» — ниже подписи.
        val dateY = rowY + 36f
        canvas.drawText("Дата:", MARGIN, dateY, signatureTextPaint)
        val dateLineStart = MARGIN + signatureTextPaint.measureText("Дата: ") + 4f
        canvas.drawLine(dateLineStart, dateY + 1f, dateLineStart + 180f, dateY + 1f, signatureLinePaint)

        finalizeAndFinishPage()
    }

    /**
     * Переносит текст по словам в пределах maxWidth, не более maxLines строк.
     * Если оригинал не помещается полностью — последняя строка усекается с «…».
     * Слова длиннее maxWidth разрываются по символам.
     */
    private fun wrapText(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxLines: Int
    ): List<String> {
        if (text.isEmpty()) return listOf("")
        if (maxLines <= 0) return emptyList()
        if (paint.measureText(text) <= maxWidth) return listOf(text)

        // Очередь токенов: длинные слова в процессе обработки могут быть разрезаны
        // и «дохвост» возвращён в начало очереди.
        val queue = ArrayDeque<String>().apply {
            text.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { addLast(it) }
        }
        val lines = mutableListOf<String>()
        var current = ""

        while (queue.isNotEmpty() && lines.size < maxLines) {
            val w = queue.removeFirst()
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else if (current.isNotEmpty()) {
                lines += current
                current = ""
                queue.addFirst(w) // попытаемся вместить с новой строки
            } else {
                // Слово длиннее, чем ширина ячейки — режем по символам.
                val cut = forceFit(w, paint, maxWidth)
                lines += cut
                val rest = w.substring(cut.length)
                if (rest.isNotEmpty()) queue.addFirst(rest)
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines += current

        // Не всё уложилось в maxLines — добавляем многоточие в последнюю строку.
        if (queue.isNotEmpty()) {
            val last = lines.lastOrNull().orEmpty()
            val lastIdx = if (lines.isEmpty()) {
                lines += ""
                0
            } else lines.lastIndex
            lines[lastIdx] = ellipsize(last, paint, maxWidth, addEllipsis = true)
        }
        return lines.ifEmpty { listOf(ellipsize(text, paint, maxWidth, addEllipsis = true)) }
    }

    /** Принудительно подгоняет одно слово, чтобы префикс поместился в maxWidth. */
    private fun forceFit(word: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(word) <= maxWidth) return word
        var lo = 1
        var hi = word.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (paint.measureText(word.substring(0, mid)) <= maxWidth) lo = mid else hi = mid - 1
        }
        return word.substring(0, lo)
    }

    /** Обрезает строку с конца. С addEllipsis=true гарантирует «…» в конце результата. */
    private fun ellipsize(s: String, paint: Paint, maxWidth: Float, addEllipsis: Boolean = true): String {
        val target = if (addEllipsis) "$s…" else s
        if (paint.measureText(target) <= maxWidth) return target
        var t = s.trimEnd()
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) {
            t = t.dropLast(1)
        }
        return if (t.isEmpty()) "…" else "$t…"
    }
}
