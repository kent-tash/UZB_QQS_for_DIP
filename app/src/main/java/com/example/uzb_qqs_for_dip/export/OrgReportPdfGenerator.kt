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
 * Второй отчёт аудитора: "Возврат НДС по личным расходам".
 *
 * Шапка: "Возврат НДС по личным расходам за [N квартал год]"
 * Таблица сгруппирована по организации сотрудника (поле User.organization).
 * После каждой группы — подытог "ИТОГО [ОРГАНИЗАЦИЯ]".
 * В конце — общий "ИТОГО".
 * Подписи: Руководитель и Главный бухгалтер.
 */
object OrgReportPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    private val COL_NUM_W  = CONTENT_WIDTH * 0.08f
    private val COL_NAME_W = CONTENT_WIDTH * 0.60f
    private val COL_VAT_W  = CONTENT_WIDTH * 0.32f

    private const val CELL_PAD_H = 5f
    private const val ROW_H = 22f
    private const val SUBTOTAL_ROW_H = 22f
    private const val HEADER_ROW_H = 26f
    private const val LINE_SPACING = 14f
    private const val SIGNATURE_HEIGHT = 60f

    suspend fun generate(
        context: Context,
        rows: List<EmployeeSummary>,
        quarter: Quarter,
        year: Int,
        auditorSettings: AuditorSettings,
        fileName: String = "audit_org_report_${quarter.name}_$year.pdf"
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
            // LEFT required for StaticLayout; centering via Layout.Alignment.ALIGN_CENTER
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
        val subtotalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 9.5f; color = 0xFF111827.toInt()
            textAlign = Paint.Align.RIGHT
        }
        val subtotalLabelPaint = TextPaint(subtotalPaint).apply {
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
        val subtotalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFDDE8F5.toInt(); style = Paint.Style.FILL
        }
        val grandTotalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFBDD7EE.toInt(); style = Paint.Style.FILL
        }
        val signaturePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = 9.5f; color = 0xFF111827.toInt()
        }

        val quarterLabel = "${SummaryPdfGenerator.quarterLabel(quarter)} $year г."

        fun drawTableHeader(canvas: Canvas, startY: Float): Float {
            val x = MARGIN
            canvas.drawRect(x, startY, x + CONTENT_WIDTH, startY + HEADER_ROW_H, headerFillPaint)
            canvas.drawRect(x, startY, x + CONTENT_WIDTH, startY + HEADER_ROW_H, borderPaint)
            val midY = startY + HEADER_ROW_H / 2f + headerPaint.textSize / 3f
            canvas.drawText("№", x + COL_NUM_W / 2f, midY, headerPaint)
            canvas.drawLine(x + COL_NUM_W, startY, x + COL_NUM_W, startY + HEADER_ROW_H, borderPaint)
            canvas.drawText("Фамилия И.О.",
                x + COL_NUM_W + COL_NAME_W / 2f, midY, headerPaint)
            canvas.drawLine(x + COL_NUM_W + COL_NAME_W, startY,
                x + COL_NUM_W + COL_NAME_W, startY + HEADER_ROW_H, borderPaint)
            canvas.drawText(quarterLabel,
                x + COL_NUM_W + COL_NAME_W + COL_VAT_W / 2f, midY, headerPaint)
            return startY + HEADER_ROW_H
        }

        val BLANK_ORG_KEY = "\uFFFE"

        // Group by org, then sort groups: largest first, blank-org group last
        // Within each group: sort by displayed "Фамилия И.О." alphabetically
        val grouped: Map<String, List<EmployeeSummary>> = rows
            .groupBy { it.organization.ifBlank { BLANK_ORG_KEY } }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<EmployeeSummary>>> {
                    if (it.key == BLANK_ORG_KEY) Int.MIN_VALUE else it.value.size
                }
            )
            .associate { (key, list) ->
                key to list.sortedBy {
                    SummaryPdfGenerator.toSurnameInitials(
                        it.initialsSurname.ifBlank { it.fullName }
                    ).lowercase()
                }
            }

        var pageNum = 0
        var currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNum).create())
        var canvas = currentPage.canvas
        var y = MARGIN

        // ── Title block ────────────────────────────────────────────────────────
        val titleText = "Возврат НДС по личным расходам за $quarterLabel"
        val titleLayout = StaticLayout.Builder
            .obtain(titleText, 0, titleText.length, titlePaint, CONTENT_WIDTH.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(2f, 1f)
            .build()
        canvas.save()
        canvas.translate(MARGIN, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 10f

        y = drawTableHeader(canvas, y)

        var globalIndex = 0

        for ((orgName, orgRows) in grouped) {
            var orgVatTotal = 0L

            for ((localIdx, summary) in orgRows.withIndex()) {
                globalIndex++
                if (y + ROW_H > PAGE_HEIGHT - MARGIN - SUBTOTAL_ROW_H - ROW_H) {
                    doc.finishPage(currentPage)
                    currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNum).create())
                    canvas = currentPage.canvas
                    y = MARGIN
                    y = drawTableHeader(canvas, y)
                }

                val x = MARGIN
                if (localIdx % 2 == 1) canvas.drawRect(x, y, x + CONTENT_WIDTH, y + ROW_H, zebraFillPaint)
                canvas.drawRect(x, y, x + CONTENT_WIDTH, y + ROW_H, borderPaint)
                val textY = y + ROW_H / 2f + cellPaint.textSize / 3f

                canvas.drawText("$globalIndex.", x + COL_NUM_W - CELL_PAD_H, textY, cellNumRightPaint)
                canvas.drawLine(x + COL_NUM_W, y, x + COL_NUM_W, y + ROW_H, borderPaint)

                val displayName = SummaryPdfGenerator.toSurnameInitials(
                    summary.initialsSurname.ifBlank { summary.fullName }
                )
                drawClippedText(canvas, displayName, x + COL_NUM_W + CELL_PAD_H, textY,
                    COL_NAME_W - CELL_PAD_H * 2, cellPaint)
                canvas.drawLine(x + COL_NUM_W + COL_NAME_W, y,
                    x + COL_NUM_W + COL_NAME_W, y + ROW_H, borderPaint)

                canvas.drawText(MoneyFormat.fromTiyin(summary.vatTiyin),
                    x + COL_NUM_W + COL_NAME_W + COL_VAT_W - CELL_PAD_H, textY, cellNumRightPaint)

                orgVatTotal += summary.vatTiyin
                y += ROW_H
            }

            // Subtotal row for this org
            if (y + SUBTOTAL_ROW_H > PAGE_HEIGHT - MARGIN) {
                doc.finishPage(currentPage)
                currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNum).create())
                canvas = currentPage.canvas
                y = MARGIN
                y = drawTableHeader(canvas, y)
            }
            val x = MARGIN
            canvas.drawRect(x, y, x + CONTENT_WIDTH, y + SUBTOTAL_ROW_H, subtotalFillPaint)
            canvas.drawRect(x, y, x + CONTENT_WIDTH, y + SUBTOTAL_ROW_H, borderPaint)
            val subY = y + SUBTOTAL_ROW_H / 2f + subtotalPaint.textSize / 3f
            val subtotalLabel = if (orgName == BLANK_ORG_KEY) "ИТОГО:" else "ИТОГО $orgName:"
            drawClippedText(canvas, subtotalLabel, x + CELL_PAD_H, subY,
                COL_NUM_W + COL_NAME_W - CELL_PAD_H, subtotalLabelPaint)
            canvas.drawLine(x + COL_NUM_W + COL_NAME_W, y,
                x + COL_NUM_W + COL_NAME_W, y + SUBTOTAL_ROW_H, borderPaint)
            canvas.drawText(MoneyFormat.fromTiyin(orgVatTotal),
                x + COL_NUM_W + COL_NAME_W + COL_VAT_W - CELL_PAD_H, subY, subtotalPaint)
            y += SUBTOTAL_ROW_H
        }

        // ── Grand total row ───────────────────────────────────────────────────
        if (y + ROW_H + SIGNATURE_HEIGHT > PAGE_HEIGHT - MARGIN) {
            doc.finishPage(currentPage)
            currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNum).create())
            canvas = currentPage.canvas
            y = MARGIN
        }
        val x = MARGIN
        canvas.drawRect(x, y, x + CONTENT_WIDTH, y + ROW_H, grandTotalFillPaint)
        canvas.drawRect(x, y, x + CONTENT_WIDTH, y + ROW_H, borderPaint)
        val grandY = y + ROW_H / 2f + subtotalPaint.textSize / 3f
        canvas.drawText("ИТОГО:", x + CELL_PAD_H, grandY, subtotalLabelPaint)
        canvas.drawLine(x + COL_NUM_W + COL_NAME_W, y, x + COL_NUM_W + COL_NAME_W, y + ROW_H, borderPaint)
        val grandTotal = rows.sumOf { it.vatTiyin }
        canvas.drawText(MoneyFormat.fromTiyin(grandTotal),
            x + COL_NUM_W + COL_NAME_W + COL_VAT_W - CELL_PAD_H, grandY, subtotalPaint)
        y += ROW_H

        // ── Signature block ───────────────────────────────────────────────────
        y += 28f
        SummaryPdfGenerator.drawSignatureBlock(canvas, y, s, signaturePaint)

        doc.finishPage(currentPage)
    }

    private fun drawClippedText(
        canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: TextPaint
    ) {
        val ellipsized = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(ellipsized, x, y, paint)
    }
}
