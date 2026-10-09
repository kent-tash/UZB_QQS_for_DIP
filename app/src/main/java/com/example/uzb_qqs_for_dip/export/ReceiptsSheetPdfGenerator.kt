package com.example.uzb_qqs_for_dip.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.render.ReceiptCardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque

/**
 * Формирует PDF, в котором отрисованы все чеки в указанном порядке: 6 карточек на A4 (2×3),
 * порядковые номера в чёрном квадрате на каждой карточке совпадают с номером в таблице.
 */
object ReceiptsSheetPdfGenerator {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 24f
    private const val GAP = 10f
    /** Расстояние между базовыми линиями соседних строк одного блока. */
    private const val TITLE_LINE_EXTRA = 5f
    private const val SUB_LINE_EXTRA = 4f
    /** Отступ между блоком заголовка и блоком «должность / ФИО». */
    private const val HEADER_BLOCKS_GAP = 10f
    /** Запас снизу шапки до сетки карточек. */
    private const val HEADER_BOTTOM_PAD = 6f

    private const val COLS = 2
    private const val ROWS = 3
    const val PER_PAGE: Int = COLS * ROWS

    /**
     * @param headerRightText правая часть верхнего колонтитула: должность и ФИО пользователя
     * (как в профиле).
     * @param periodLabel выбранный на вкладке «Отчёт» период: квартал и год или диапазон дат.
     */
    suspend fun generate(
        context: Context,
        rowsInOrder: List<ReceiptWithUser>,
        headerRightText: String,
        periodLabel: String,
        fileName: String = "receipts_sheet_${System.currentTimeMillis()}.pdf"
    ): File = withContext(Dispatchers.IO) {
        val file = File(ExportPaths.exportsDir(context), fileName)
        val doc = PdfDocument()
        val printableRows = rowsInOrder.filter { !it.receipt.isManual }
        try {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textSize = 13f
                color = 0xFF111827.toInt()
                textAlign = Paint.Align.LEFT
            }
            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                textSize = 10f
                color = 0xFF6B7280.toInt()
                textAlign = Paint.Align.RIGHT
            }
            val pagePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                textSize = 8f
                color = 0xFF6B7280.toInt()
                textAlign = Paint.Align.RIGHT
            }

            val manualReceiptsCount = rowsInOrder.count { it.receipt.isManual && it.receipt.manualPhotoUri != null }
            val gridPages = if (rowsInOrder.isEmpty()) 1 else (rowsInOrder.size + PER_PAGE - 1) / PER_PAGE
            val totalPages = gridPages + manualReceiptsCount

            val sheetTitle =
                "Сохранённые чеки (${rowsInOrder.size}) за $periodLabel"
            val contentW = PAGE_W - 2f * MARGIN
            val titleLines = wrapLines(sheetTitle, titlePaint, contentW, maxLines = 8)
            val rightLines = if (headerRightText.isBlank()) emptyList()
            else wrapLines(headerRightText.trim(), subtitlePaint, contentW, maxLines = 6)

            val gridTop = measureHeaderBottomY(titleLines, rightLines, titlePaint, subtitlePaint)

            // Размер ячейки
            val gridLeft = MARGIN
            val gridW = PAGE_W - MARGIN * 2
            val gridH = PAGE_H - gridTop - 5.67f
            val cellW = (gridW - GAP * (COLS - 1)) / COLS
            val cellH = (gridH - GAP * (ROWS - 1)) / ROWS

            for (pageIndex in 0 until gridPages) {
                val pageNumber = pageIndex + 1
                val page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
                )
                val canvas = page.canvas

                // Шапка: заголовок на всю ширину (перенос), ниже — должность и ФИО справа (перенос),
                // без горизонтального пересечения.
                var baseline = MARGIN + titlePaint.textSize
                for (i in titleLines.indices) {
                    canvas.drawText(titleLines[i], MARGIN, baseline, titlePaint)
                    if (i == 0) {
                        canvas.drawText("Стр. $pageNumber из $totalPages", PAGE_W - MARGIN, baseline, pagePaint)
                    }
                    if (i < titleLines.lastIndex) {
                        baseline += titlePaint.textSize + TITLE_LINE_EXTRA
                    }
                }
                if (rightLines.isNotEmpty()) {
                    baseline = if (titleLines.isNotEmpty()) {
                        baseline + titlePaint.fontMetrics.descent + HEADER_BLOCKS_GAP -
                            subtitlePaint.fontMetrics.ascent
                    } else {
                        MARGIN + subtitlePaint.textSize
                    }
                    for (i in rightLines.indices) {
                        canvas.drawText(rightLines[i], PAGE_W - MARGIN, baseline, subtitlePaint)
                        if (i < rightLines.lastIndex) {
                            baseline += subtitlePaint.textSize + SUB_LINE_EXTRA
                        }
                    }
                }

                val from = pageIndex * PER_PAGE
                val to = minOf(from + PER_PAGE, rowsInOrder.size)
                for (i in from until to) {
                    val cellIndex = i - from
                    val row = cellIndex / COLS
                    val col = cellIndex % COLS
                    val left = gridLeft + col * (cellW + GAP)
                    val top = gridTop + row * (cellH + GAP)

                    val item = rowsInOrder[i].receipt
                    ReceiptCardRenderer.renderInto(
                        canvas = canvas,
                        receipt = item,
                        ordinal = i + 1,
                        left = left,
                        top = top,
                        width = cellW,
                        height = cellH
                    )
                }



                doc.finishPage(page)
            }

            // --- Добавление страниц с фото для чеков, добавленных вручную ---
            val manualReceipts = rowsInOrder.mapIndexedNotNull { index, row ->
                if (row.receipt.isManual && row.receipt.manualPhotoUri != null) {
                    index + 1 to row.receipt
                } else null
            }

            var manualSeq = 1
            for ((tableIdx, receipt) in manualReceipts) {
                val photoFile = java.io.File(receipt.manualPhotoUri!!)
                if (photoFile.exists()) {
                    val pageNumber = gridPages + manualSeq
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
                    val canvas = page.canvas

                    val attachTitle = "Фото ручного чека № $tableIdx (в реестре)"
                    canvas.drawText(attachTitle, PAGE_W / 2f, MARGIN + titlePaint.textSize, titlePaint.apply { textAlign = Paint.Align.CENTER })
                    titlePaint.textAlign = Paint.Align.LEFT // Возвращаем как было

                    val bitmap = android.graphics.BitmapFactory.decodeFile(receipt.manualPhotoUri)
                    if (bitmap != null) {
                        val imgMaxWidth = PAGE_W - 2f * MARGIN
                        val imgMaxHeight = PAGE_H - 2f * MARGIN - titlePaint.textSize - 20f
                        val scale = minOf(imgMaxWidth / bitmap.width, imgMaxHeight / bitmap.height, 1f)
                        val scaledWidth = bitmap.width * scale
                        val scaledHeight = bitmap.height * scale
                        
                        val imgLeft = (PAGE_W - scaledWidth) / 2f
                        val imgTop = MARGIN + titlePaint.textSize + 20f
                        
                        val destRect = android.graphics.RectF(imgLeft, imgTop, imgLeft + scaledWidth, imgTop + scaledHeight)
                        canvas.drawBitmap(bitmap, null, destRect, null)
                        bitmap.recycle()
                    }

                    canvas.drawText("Стр. $pageNumber из $totalPages", PAGE_W - MARGIN, MARGIN + titlePaint.textSize, pagePaint)
                    doc.finishPage(page)
                    manualSeq++
                }
            }

            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        file
    }

    /** Y верхней границы сетки карточек под шапкой. */
    private fun measureHeaderBottomY(
        titleLines: List<String>,
        rightLines: List<String>,
        titlePaint: TextPaint,
        subtitlePaint: TextPaint
    ): Float {
        var baseline = MARGIN + titlePaint.textSize
        for (i in titleLines.indices) {
            if (i < titleLines.lastIndex) baseline += titlePaint.textSize + TITLE_LINE_EXTRA
        }
        if (rightLines.isEmpty()) {
            val bottom = if (titleLines.isEmpty()) MARGIN else baseline + titlePaint.fontMetrics.descent
            return bottom + HEADER_BOTTOM_PAD
        }
        var rBaseline = if (titleLines.isNotEmpty()) {
            baseline + titlePaint.fontMetrics.descent + HEADER_BLOCKS_GAP -
                subtitlePaint.fontMetrics.ascent
        } else {
            MARGIN + subtitlePaint.textSize
        }
        for (i in rightLines.indices) {
            if (i < rightLines.lastIndex) rBaseline += subtitlePaint.textSize + SUB_LINE_EXTRA
        }
        return rBaseline + subtitlePaint.fontMetrics.descent + HEADER_BOTTOM_PAD
    }

    /** Перенос по словам; слишком длинные слова режутся. */
    private fun wrapLines(text: String, paint: TextPaint, maxWidth: Float, maxLines: Int): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        if (paint.measureText(t) <= maxWidth) return listOf(t)
        val queue = ArrayDeque<String>().apply {
            t.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { addLast(it) }
        }
        val lines = mutableListOf<String>()
        var current = ""
        while (queue.isNotEmpty() && lines.size < maxLines) {
            val w = queue.removeFirst()
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else if (current.isNotEmpty()) {
                lines.add(current)
                current = ""
                queue.addFirst(w)
            } else {
                val cut = forceFitWordPrefix(w, paint, maxWidth)
                lines.add(cut)
                val rest = w.substring(cut.length)
                if (rest.isNotEmpty()) queue.addFirst(rest)
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current)
        return if (lines.isEmpty()) listOf(ellipsizeToWidth(t, paint, maxWidth)) else lines
    }

    private fun forceFitWordPrefix(word: String, paint: TextPaint, maxWidth: Float): String {
        if (word.isEmpty()) return ""
        if (paint.measureText(word) <= maxWidth) return word
        var lo = 1
        var hi = word.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (paint.measureText(word.substring(0, mid)) <= maxWidth) lo = mid else hi = mid - 1
        }
        return word.substring(0, lo)
    }

    private fun ellipsizeToWidth(s: String, paint: TextPaint, maxWidth: Float): String {
        var u = s.trim()
        while (u.isNotEmpty() && paint.measureText("$u...") > maxWidth) u = u.dropLast(1)
        return if (u.isEmpty()) "..." else "$u..."
    }
}
