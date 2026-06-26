package com.example.uzb_qqs_for_dip.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.uzb_qqs_for_dip.data.repository.EmployeeSummary
import com.example.uzb_qqs_for_dip.data.settings.AuditorSettings
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Первый отчёт аудитора: сводная таблица за квартал.
 *
 * Шапка: "Список сотрудников [ОРГАНИЗАЦИЯ] в Узбекистане, предъявляющих к возмещению уплаченный НДС"
 * Столбцы: № | Фамилия Инициалы | Сумма НДС (квартал год)
 * Подписи: Руководитель и Главный бухгалтер.
 */
object SummaryPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    // Ширина трёх колонок: № | Фамилия Инициалы | Сумма НДС
    private val COL_NUM_W  = CONTENT_WIDTH * 0.08f
    private val COL_NAME_W = CONTENT_WIDTH * 0.60f
    private val COL_VAT_W  = CONTENT_WIDTH * 0.32f

    private const val CELL_PAD_H = 5f
    private const val CELL_PAD_V = 6f
    private const val ROW_H = 22f
    private const val HEADER_ROW_H = 26f
    private const val LINE_SPACING = 14f

    suspend fun generate(
        context: Context,
        rows: List<EmployeeSummary>,
        quarter: Quarter,
        year: Int,
        auditorSettings: AuditorSettings,
        fileName: String = "audit_summary_${quarter.name}_$year.pdf"
    ): File = withContext(Dispatchers.IO) {
        val file = File(ExportPaths.exportsDir(context), fileName)
        val doc = PdfDocument()
        try {
            render(doc, rows, quarter, year, auditorSettings)
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        file
    }

    private fun render(
        doc: PdfDocument,
        rows: List<EmployeeSummary>,
        quarter: Quarter,
        year: Int,
        s: AuditorSettings,
    ) {
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val regular = Typeface.SANS_SERIF

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 11f; color = 0xFF111827.toInt()
            // LEFT is required for StaticLayout; centering is handled by Layout.Alignment.ALIGN_CENTER
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = 9.5f; color = 0xFF374151.toInt()
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 9.5f; color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
        }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = 9.5f; color = 0xFF111827.toInt()
        }
        val cellNumRightPaint = TextPaint(cellPaint).apply { textAlign = Paint.Align.RIGHT }
        val totalsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 9.5f; color = 0xFF111827.toInt()
            textAlign = Paint.Align.RIGHT
        }
        val totalsLabelPaint = TextPaint(totalsPaint).apply {
            textAlign = Paint.Align.LEFT
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB0B7C3.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.7f
        }
        val headerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1F4E79.toInt(); style = Paint.Style.FILL
        }
        val zebraFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF7F8FA.toInt(); style = Paint.Style.FILL
        }
        val totalsFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFDDE8F5.toInt(); style = Paint.Style.FILL
        }
        val signaturePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = 9.5f; color = 0xFF111827.toInt()
        }

        val quarterLabel = "${quarterLabel(quarter)} $year г."

        fun drawTableHeader(canvas: Canvas, startY: Float): Float {
            val x = MARGIN
            canvas.drawRect(x, startY, x + CONTENT_WIDTH, startY + HEADER_ROW_H, headerFillPaint)
            canvas.drawRect(x, startY, x + CONTENT_WIDTH, startY + HEADER_ROW_H, borderPaint)

            val midY = startY + HEADER_ROW_H / 2f + headerPaint.textSize / 3f

            // №
            canvas.drawText("№", x + COL_NUM_W / 2f, midY, headerPaint)
            canvas.drawLine(x + COL_NUM_W, startY, x + COL_NUM_W, startY + HEADER_ROW_H, borderPaint)

            // Фамилия И.О.
            canvas.drawText("Фамилия И.О.",
                x + COL_NUM_W + COL_NAME_W / 2f, midY, headerPaint)
            canvas.drawLine(x + COL_NUM_W + COL_NAME_W, startY,
                x + COL_NUM_W + COL_NAME_W, startY + HEADER_ROW_H, borderPaint)

            // Квартал Год / Сумма НДС
            canvas.drawText(quarterLabel,
                x + COL_NUM_W + COL_NAME_W + COL_VAT_W / 2f, midY, headerPaint)

            return startY + HEADER_ROW_H
        }

        var pageNum = 0
        var currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNum).create())
        var canvas = currentPage.canvas
        var y = MARGIN

        // Sort rows by displayed "Фамилия И.О." alphabetically
        val rows = rows.sortedBy { toSurnameInitials(it.initialsSurname.ifBlank { it.fullName }).lowercase() }

        // ── Title block ────────────────────────────────────────────────────────
        val orgName = s.organizationName.ifBlank { "_______________" }
        val titleText = "Список сотрудников $orgName в Узбекистане,\n" +
                "предъявляющих к возмещению уплаченный НДС"

        val titleLayout = StaticLayout.Builder
            .obtain(titleText, 0, titleText.length, titlePaint, CONTENT_WIDTH.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(2f, 1f)
            .build()

        canvas.save()
        canvas.translate(MARGIN, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 6f

        canvas.drawText(quarterLabel, PAGE_WIDTH / 2f, y + 10f, subtitlePaint)
        y += 20f

        y = drawTableHeader(canvas, y)

        // ── Data rows ─────────────────────────────────────────────────────────
        rows.forEachIndexed { idx, summary ->
            if (y + ROW_H > PAGE_HEIGHT - MARGIN - ROW_H * 3) {
                doc.finishPage(currentPage)
                currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNum).create())
                canvas = currentPage.canvas
                y = MARGIN
                y = drawTableHeader(canvas, y)
            }

            val x = MARGIN
            if (idx % 2 == 1) canvas.drawRect(x, y, x + CONTENT_WIDTH, y + ROW_H, zebraFillPaint)
            canvas.drawRect(x, y, x + CONTENT_WIDTH, y + ROW_H, borderPaint)

            val textY = y + ROW_H / 2f + cellPaint.textSize / 3f

            // №
            canvas.drawText("${idx + 1}.", x + COL_NUM_W - CELL_PAD_H, textY, cellNumRightPaint)
            canvas.drawLine(x + COL_NUM_W, y, x + COL_NUM_W, y + ROW_H, borderPaint)

            // Фамилия И.О.
            val nameX = x + COL_NUM_W + CELL_PAD_H
            val displayName = toSurnameInitials(summary.initialsSurname.ifBlank { summary.fullName })
            drawClippedText(canvas, displayName, nameX, textY,
                COL_NAME_W - CELL_PAD_H * 2, cellPaint)
            canvas.drawLine(x + COL_NUM_W + COL_NAME_W, y,
                x + COL_NUM_W + COL_NAME_W, y + ROW_H, borderPaint)

            // Сумма НДС
            val vatX = x + COL_NUM_W + COL_NAME_W + COL_VAT_W - CELL_PAD_H
            canvas.drawText(MoneyFormat.fromTiyin(summary.vatTiyin), vatX, textY, cellNumRightPaint)

            y += ROW_H
        }

        // ── ИТОГО row ─────────────────────────────────────────────────────────
        if (y + ROW_H > PAGE_HEIGHT - MARGIN) {
            doc.finishPage(currentPage)
            currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNum).create())
            canvas = currentPage.canvas
            y = MARGIN
        }
        val x = MARGIN
        canvas.drawRect(x, y, x + CONTENT_WIDTH, y + ROW_H, totalsFillPaint)
        canvas.drawRect(x, y, x + CONTENT_WIDTH, y + ROW_H, borderPaint)
        val totY = y + ROW_H / 2f + totalsPaint.textSize / 3f

        // "ИТОГО:" spans columns 1+2
        canvas.drawText("ИТОГО:", x + CELL_PAD_H, totY, totalsLabelPaint)
        canvas.drawLine(x + COL_NUM_W + COL_NAME_W, y, x + COL_NUM_W + COL_NAME_W, y + ROW_H, borderPaint)

        // Total VAT
        val totalVat = rows.sumOf { it.vatTiyin }
        val vatTotX = x + COL_NUM_W + COL_NAME_W + COL_VAT_W - CELL_PAD_H
        canvas.drawText(MoneyFormat.fromTiyin(totalVat), vatTotX, totY, totalsPaint)
        y += ROW_H

        // ── Signature block ───────────────────────────────────────────────────
        y += 28f
        drawSignatureBlock(canvas, y, s, signaturePaint)

        doc.finishPage(currentPage)
    }

    internal fun drawSignatureBlock(
        canvas: Canvas,
        startY: Float,
        s: AuditorSettings,
        paint: TextPaint
    ) {
        val lineText = "____________________"
        val x = MARGIN
        val lineStartX = x + CONTENT_WIDTH * 0.50f
        // Names right-aligned to the right page margin
        val namePaint = TextPaint(paint).apply { textAlign = Paint.Align.RIGHT }
        val rightEdge = x + CONTENT_WIDTH

        val dirTitle = s.directorTitle.ifBlank { "Руководитель организации" }
        val dirName  = s.directorName.ifBlank { "_______________" }
        canvas.drawText(dirTitle, x, startY, paint)
        canvas.drawText(lineText, lineStartX, startY, paint)
        canvas.drawText(dirName, rightEdge, startY, namePaint)

        val accTitle = s.accountantTitle.ifBlank { "Главный бухгалтер организации" }
        val accName  = s.accountantName.ifBlank { "_______________" }
        val y2 = startY + LINE_SPACING * 2.4f
        canvas.drawText(accTitle, x, y2, paint)
        canvas.drawText(lineText, lineStartX, y2, paint)
        canvas.drawText(accName, rightEdge, y2, namePaint)
    }

    private fun drawClippedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: TextPaint
    ) {
        val ellipsized = TextUtils.ellipsize(
            text, paint, maxWidth, TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(ellipsized, x, y, paint)
    }

    /**
     * Переставляет "И.О. Фамилия" → "Фамилия И.О.".
     * Если значение уже в нужном формате (первое слово не содержит точку) — возвращает как есть.
     */
    internal fun toSurnameInitials(value: String): String {
        val parts = value.trim().split(" ")
        if (parts.size < 2) return value.trim()
        return if (parts.first().contains('.')) {
            val surname = parts.last()
            val initials = parts.dropLast(1).joinToString(" ")
            "$surname $initials"
        } else {
            value.trim()
        }
    }

    internal fun quarterLabel(q: Quarter): String = when (q) {
        Quarter.Q1 -> "I квартал"
        Quarter.Q2 -> "II квартал"
        Quarter.Q3 -> "III квартал"
        Quarter.Q4 -> "IV квартал"
        Quarter.Custom -> "Квартал"
    }
}
