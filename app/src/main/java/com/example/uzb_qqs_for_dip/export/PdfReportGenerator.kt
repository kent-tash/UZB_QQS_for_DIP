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
 * РџР°СЂР°РјРµС‚СЂС‹ С„РѕСЂРјРёСЂРѕРІР°РЅРёСЏ PDF-РѕС‚С‡С‘С‚Р° РїРѕ С‡РµРєР°Рј РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.
 *
 * @property user РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ, С‡СЊРё С‡РµРєРё РІС‹РіСЂСѓР¶Р°СЋС‚СЃСЏ (РµРіРѕ РґР°РЅРЅС‹Рµ РїРѕРґСЃС‚Р°РІСЏС‚СЃСЏ РІ С€Р°РїРєСѓ Рё РїРѕРґРїРёСЃСЊ).
 * @property periodStart РќР°С‡Р°Р»Рѕ РїРµСЂРёРѕРґР° (РјРёР»Р»РёСЃРµРєСѓРЅРґС‹).
 * @property periodEnd РљРѕРЅРµС† РїРµСЂРёРѕРґР° (РјРёР»Р»РёСЃРµРєСѓРЅРґС‹).
 * @property quarterLabel Р•СЃР»Рё РІС‹Р±СЂР°РЅ РєРІР°СЂС‚Р°Р» вЂ” РµРіРѕ РЅР°Р·РІР°РЅРёРµ (РЅР°РїСЂРёРјРµСЂ, В«II РєРІР°СЂС‚Р°Р» (Р°РїСЂРµР»СЊвЂ“РёСЋРЅСЊ) 2026 Рі.В»),
 * РёРЅР°С‡Рµ null Рё РІ С€Р°РїРєРµ РІС‹РІРѕРґСЏС‚СЃСЏ РґР°С‚С‹ РїРµСЂРёРѕРґР°.
 * @property rows Р§РµРєРё РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ Р·Р° СѓРєР°Р·Р°РЅРЅС‹Р№ РїРµСЂРёРѕРґ (РѕС‚СЃРѕСЂС‚РёСЂРѕРІР°РЅРЅС‹Рµ РїРѕ РґР°С‚Рµ).
 * @property generatedAt Р”Р°С‚Р° С„РѕСЂРјРёСЂРѕРІР°РЅРёСЏ РѕС‚С‡С‘С‚Р°.
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
 * Р¤РѕСЂРјРёСЂСѓРµС‚ PDF-РѕС‚С‡С‘С‚ РїРѕ С‡РµРєР°Рј, РіРѕС‚РѕРІС‹Р№ Рє РїРµС‡Р°С‚Рё/СЃРѕС…СЂР°РЅРµРЅРёСЋ. РљРёСЂРёР»Р»РёС†Р° РѕС‚СЂРёСЃРѕРІС‹РІР°РµС‚СЃСЏ С‡РµСЂРµР·
 * СЃРёСЃС‚РµРјРЅС‹Р№ Typeface.SANS_SERIF, РІ РєРѕС‚РѕСЂРѕРј Сѓ Android РµСЃС‚СЊ РїРѕР»РЅРѕС†РµРЅРЅС‹Р№ РЅР°Р±РѕСЂ РіР»РёС„РѕРІ.
 *
 * РЎС‚СЂСѓРєС‚СѓСЂР° СЃС‚СЂР°РЅРёС†С‹:
 *   1. РЁР°РїРєР° (РїРѕ С†РµРЅС‚СЂСѓ) вЂ” Р·Р°РіРѕР»РѕРІРѕРє СЂРµРµСЃС‚СЂР°; СЃ РЅРѕРІРѕР№ СЃС‚СЂРѕРєРё РїРµСЂРёРѕРґ; СЃ РЅРѕРІРѕР№ СЃС‚СЂРѕРєРё РґРѕР»Р¶РЅРѕСЃС‚СЊ Рё Р¤РРћ.
 *   2. РўР°Р±Р»РёС†Р° вЂ” в„–, РќР°РёРјРµРЅРѕРІР°РЅРёРµ РѕСЂРіР°РЅРёР·Р°С†РёРё, РЎСѓРјРјР°, РќР”РЎ, Р”Р°С‚Р°, СЃРѕ СЃС‚СЂРѕРєРѕР№ В«РС‚РѕРіРѕВ».
 *   3. РџРѕРґРїРёСЃСЊ вЂ” РґРѕР»Р¶РЅРѕСЃС‚СЊ, Р»РёРЅРёСЏ РґР»СЏ Р¶РёРІРѕР№ РїРѕРґРїРёСЃРё, Р.Рћ. Р¤Р°РјРёР»РёСЏ, РїРѕР»Рµ В«Р”Р°С‚Р°: _______В».
 */
object PdfReportGenerator {

    // A4 РІ pt (1/72 РґСЋР№РјР°): 595 x 842.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    // РљРѕР»РѕРЅРєРё: в„–, РќР°РёРјРµРЅРѕРІР°РЅРёРµ РѕСЂРіР°РЅРёР·Р°С†РёРё, РЎСѓРјРјР°, РќР”РЎ, Р”Р°С‚Р°.
    private val COL_WIDTHS = floatArrayOf(28f, 170f, 110f, 100f, 107f)
    private val COL_HEADERS = arrayOf("в„–", "РќР°РёРјРµРЅРѕРІР°РЅРёРµ РѕСЂРіР°РЅРёР·Р°С†РёРё", "РЎСѓРјРјР°", "РќР”РЎ", "Р”Р°С‚Р°")

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
     * Р РµРЅРґРµСЂРёС‚ РѕС‚С‡С‘С‚ РІ [doc]. Р РµР°Р»СЊРЅРѕ РїСЂРѕС…РѕРґРѕРІ РґРІР°:
     *   1. В«РЎСѓС…РѕР№В» РїСЂРѕРіРѕРЅ РІРѕ РІСЂРµРјРµРЅРЅС‹Р№ РґРѕРєСѓРјРµРЅС‚ вЂ” С‡С‚РѕР±С‹ СѓР·РЅР°С‚СЊ РѕР±С‰РµРµ С‡РёСЃР»Рѕ СЃС‚СЂР°РЅРёС†
     *      (СЌС‚Рѕ РЅСѓР¶РЅРѕ РґР»СЏ РЅСѓРјРµСЂР°С†РёРё РІ РїСЂР°РІРѕРј РІРµСЂС…РЅРµРј СѓРіР»Сѓ: РїРµСЂРІСѓСЋ СЃС‚СЂР°РЅРёС†Сѓ РЅРµ РЅСѓРјРµСЂСѓРµРј,
     *      Рё РЅСѓРјРµСЂР°С†РёСЏ РґРѕР±Р°РІР»СЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РµСЃР»Рё СЃС‚СЂР°РЅРёС† Р±РѕР»СЊС€Рµ РѕРґРЅРѕР№).
     *   2. РќР°СЃС‚РѕСЏС‰РёР№ РїСЂРѕРіРѕРЅ РІ РІС‹С…РѕРґРЅРѕР№ [doc] СЃ СѓР¶Рµ РёР·РІРµСЃС‚РЅС‹Рј [totalPages] вЂ” СЂРёСЃСѓСЋС‚СЃСЏ
     *      РЅРѕРјРµСЂР° СЃС‚СЂР°РЅРёС† Рё РЅРёР¶РЅРёР№ РєРѕР»РѕРЅС‚РёС‚СѓР» (РґРѕР»Р¶РЅРѕСЃС‚СЊ + Р.Рћ. Р¤Р°РјРёР»РёСЏ).
     *
     * РћС‚РєСЂС‹С‚Рѕ, С‡С‚РѕР±С‹ С‚РѕС‚ Р¶Рµ РєРѕРЅС‚РµРЅС‚ РёСЃРїРѕР»СЊР·РѕРІР°С‚СЊ РІ PrintDocumentAdapter.
     */
    fun renderInto(doc: PdfDocument, params: ReportParams) {
        val totalPages = countPages(params)
        renderOnce(doc, params, totalPages)
    }

    /** РЎСѓС…РѕР№ РїСЂРѕРіРѕРЅ СЂР°РґРё РїРѕРґСЃС‡С‘С‚Р° СЃС‚СЂР°РЅРёС†. Р’СЂРµРјРµРЅРЅС‹Р№ РґРѕРєСѓРјРµРЅС‚ Р·Р°РєСЂС‹РІР°РµС‚СЃСЏ. */
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
     * РћРґРёРЅ РїСЂРѕС…РѕРґ СЂРµРЅРґРµСЂР°. РџСЂРё [totalPages] > 1 СЂРёСЃСѓРµС‚ РЅРѕРјРµСЂР° СЃС‚СЂР°РЅРёС† (СЃРѕ 2-Р№ РІРєР»СЋС‡РёС‚РµР»СЊРЅРѕ),
     * РЅР° РєР°Р¶РґРѕР№ СЃС‚СЂР°РЅРёС†Рµ РґРѕР±Р°РІР»СЏРµС‚ РІ РїСЂР°РІС‹Р№ РЅРёР¶РЅРёР№ СѓРіРѕР» РґРѕР»Р¶РЅРѕСЃС‚СЊ Рё РїРѕРґРїРёСЃСЊ РёР· РїСЂРѕС„РёР»СЏ.
     */
    private fun renderOnce(doc: PdfDocument, params: ReportParams, totalPages: Int) {
        val typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val typefaceBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)

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

        // РљРѕР»РѕРЅС‚РёС‚СѓР»: РІ РїСЂР°РІРѕРј РЅРёР¶РЅРµРј СѓРіР»Сѓ РєР°Р¶РґРѕР№ СЃС‚СЂР°РЅРёС†С‹ вЂ” РґРѕР»Р¶РЅРѕСЃС‚СЊ Рё Р.Рћ. Р¤Р°РјРёР»РёСЏ
        // РёР· РїСЂРѕС„РёР»СЏ (9 РїС‚, РїРѕ С‚СЂРµР±РѕРІР°РЅРёСЋ Р·Р°РґР°РЅРёСЏ).
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 9f
            color = 0xFF374151.toInt()
            textAlign = Paint.Align.RIGHT
        }
        // РќРѕРјРµСЂ СЃС‚СЂР°РЅРёС†С‹ вЂ” РІ РїСЂР°РІРѕРј РІРµСЂС…РЅРµРј СѓРіР»Сѓ, РЅР°С‡РёРЅР°СЏ СЃРѕ 2-Р№; РїРµСЂРІСѓСЋ РЅРµ РЅСѓРјРµСЂСѓРµРј
        // (Рё РЅРµ РЅСѓРјРµСЂСѓРµРј РІРѕРІСЃРµ, РµСЃР»Рё СЃС‚СЂР°РЅРёС† РІСЃРµРіРѕ РѕРґРЅР°).
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
         * Р”РѕСЂРёСЃРѕРІС‹РІР°РµС‚ РЅР° С‚РµРєСѓС‰РµР№ СЃС‚СЂР°РЅРёС†Рµ РЅРѕРјРµСЂ (РµСЃР»Рё РЅСѓР¶РµРЅ) Рё РєРѕР»РѕРЅС‚РёС‚СѓР»,
         * Р·Р°С‚РµРј Р·Р°РєСЂС‹РІР°РµС‚ РµС‘ С‡РµСЂРµР· [PdfDocument.finishPage].
         */
        fun finalizeAndFinishPage() {
            // РљРѕР»РѕРЅС‚РёС‚СѓР» РїСЂРёСЃСѓС‚СЃС‚РІСѓРµС‚ РЅР° РєР°Р¶РґРѕР№ СЃС‚СЂР°РЅРёС†Рµ.
            val rightX = PAGE_WIDTH - MARGIN
            val positionLines = wrapText(params.user.position, footerPaint, PAGE_WIDTH / 2f, 3)
            var fy = PAGE_HEIGHT - 10f - (positionLines.size - 1) * 12f
            for (i in 0 until positionLines.size - 1) {
                canvas.drawText(positionLines[i], rightX, fy, footerPaint)
                fy += 12f
            }
            val lastLine = positionLines.lastOrNull() ?: ""
            canvas.drawText("$lastLine   ${params.user.initialsSurname}", rightX, fy, footerPaint)
            // РќРѕРјРµСЂ СЃС‚СЂР°РЅРёС†С‹ РґРѕР±Р°РІР»СЏРµРј СЃРѕ РІС‚РѕСЂРѕР№ СЃС‚СЂР°РЅРёС†С‹ Рё С‚РѕР»СЊРєРѕ РµСЃР»Рё СЃС‚СЂР°РЅРёС† > 1.
            if (totalPages > 1 && pageNumber > 1) {
                canvas.drawText(pageNumber.toString(), rightX, MARGIN - 4f, pageNumberPaint)
            }
            doc.finishPage(page)
        }

        // ----- РЁР°РїРєР°: С‚СЂРё Р±Р»РѕРєР° РїРѕ С†РµРЅС‚СЂСѓ ---------------------------------
        // 1) Р·Р°РіРѕР»РѕРІРѕРє; 2) РІС‹Р±СЂР°РЅРЅС‹Р№ РїРµСЂРёРѕРґ; 3) РґРѕР»Р¶РЅРѕСЃС‚СЊ Рё Р¤РРћ РїРѕР»РЅРѕСЃС‚СЊСЋ.
        val periodText = params.quarterLabel
            ?: "${DateFormat.formatDate(params.periodStart)} вЂ” ${DateFormat.formatDate(params.periodEnd)}"
        val titleMaxWidth = PAGE_WIDTH - 2f * MARGIN
        val titleLineHeight = titlePaint.textSize + 4f
        val centerX = PAGE_WIDTH / 2f

        fun drawCenteredTitleBlock(text: String, maxLines: Int = 8) {
            for (line in wrapText(text, titlePaint, titleMaxWidth, maxLines)) {
                canvas.drawText(line, centerX, y + titlePaint.textSize, titlePaint)
                y += titleLineHeight
            }
        }

        drawCenteredTitleBlock("Р РµРµСЃС‚СЂ РїСЂРµРґСЉСЏРІР»СЏРµРјС‹С… Рє РІРѕР·РјРµС‰РµРЅРёСЋ РїР»Р°С‚РµР¶РЅС‹С… РґРѕРєСѓРјРµРЅС‚РѕРІ")
        drawCenteredTitleBlock(periodText)
        drawCenteredTitleBlock("${params.user.position} ${params.user.fullName}".trim())
        y += 10f

        // ----- РўР°Р±Р»РёС†Р° -----
        val tableLeft = MARGIN
        val tableWidth = COL_WIDTHS.sum()

        // Р›РѕРєР°Р»СЊРЅР°СЏ С„СѓРЅРєС†РёСЏ: РѕС‚СЂРёСЃРѕРІР°С‚СЊ С€Р°РїРєСѓ С‚Р°Р±Р»РёС†С‹ СЃ РїРµСЂРµРЅРѕСЃРѕРј РґР»РёРЅРЅС‹С… Р·Р°РіРѕР»РѕРІРєРѕРІ.
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

        // РњРёРЅРёРјСѓРј РјРµСЃС‚Р° РґР»СЏ СЃС‚СЂРѕРєРё СЃ РёС‚РѕРіР°РјРё + РїРѕРґРїРёСЃРё + СѓРІРµР»РёС‡РµРЅРЅС‹Р№ РѕС‚СЃС‚СѓРї
        val signatureBlockHeight = 110f
        val bottomLimit = PAGE_HEIGHT - 5.67f - signatureBlockHeight

        // РЎС‚СЂРѕРєРё РґР°РЅРЅС‹С…
        params.rows.forEachIndexed { idx, item ->
            // Р’С‹СЃРѕС‚Р° СЃС‚СЂРѕРєРё Р·Р°РІРёСЃРёС‚ РѕС‚ РєРѕР»РёС‡РµСЃС‚РІР° СЃС‚СЂРѕРє РІ РЅР°Р·РІР°РЅРёРё РѕСЂРіР°РЅРёР·Р°С†РёРё.
            val sellerMaxWidth = COL_WIDTHS[COL_SELLER] - CELL_PADDING_H * 2f
            val sellerLines = wrapText(
                item.receipt.sellerName.ifBlank { "вЂ”" },
                cellPaint,
                sellerMaxWidth,
                MAX_SELLER_LINES
            )
            val rowHeight = maxOf(
                ROW_HEIGHT_MIN,
                CELL_PADDING_V * 2f + sellerLines.size * LINE_HEIGHT
            )

            // РџСЂРѕРІРµСЂСЏРµРј, РІР»РµР·РµС‚ Р»Рё СЌС‚Р° СЃС‚СЂРѕРєР° РїРѕР»РЅРѕСЃС‚СЊСЋ, РЅРµ РїРµСЂРµРєСЂС‹РІР°СЏ bottomLimit. Р•СЃР»Рё РЅРµС‚ вЂ” РЅРѕРІР°СЏ СЃС‚СЂР°РЅРёС†Р°.
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

            // в„–
            canvas.drawText((idx + 1).toString(), x + COL_WIDTHS[COL_NUM] / 2f, centerY, cellCenterPaint)
            canvas.drawRect(x, y, x + COL_WIDTHS[COL_NUM], y + rowHeight, borderPaint)
            x += COL_WIDTHS[COL_NUM]

            // РќР°РёРјРµРЅРѕРІР°РЅРёРµ РѕСЂРіР°РЅРёР·Р°С†РёРё (РјРЅРѕРіРѕСЃС‚СЂРѕС‡РЅРѕРµ, РІС‹СЂРѕРІРЅРµРЅРѕ РїРѕ Р»РµРІРѕРјСѓ РєСЂР°СЋ)
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

            // РЎСѓРјРјР°
            canvas.drawText(
                MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin),
                x + COL_WIDTHS[COL_TOTAL] - CELL_PADDING_H, centerY, cellNumberPaint
            )
            canvas.drawRect(x, y, x + COL_WIDTHS[COL_TOTAL], y + rowHeight, borderPaint)
            x += COL_WIDTHS[COL_TOTAL]

            // РќР”РЎ
            canvas.drawText(
                MoneyFormat.fromTiyin(item.receipt.vatAmountTiyin),
                x + COL_WIDTHS[COL_VAT] - CELL_PADDING_H, centerY, cellNumberPaint
            )
            canvas.drawRect(x, y, x + COL_WIDTHS[COL_VAT], y + rowHeight, borderPaint)
            x += COL_WIDTHS[COL_VAT]

            // Р”Р°С‚Р°
            canvas.drawText(
                DateFormat.formatDateTime(item.receipt.purchasedAt),
                x + CELL_PADDING_H, centerY, cellPaint
            )
            canvas.drawRect(x, y, x + COL_WIDTHS[COL_DATE], y + rowHeight, borderPaint)

            y += rowHeight
        }

        // Р•СЃР»Рё СЃС‚СЂРѕРє РЅРµС‚ вЂ” РїСѓСЃС‚Р°СЏ СЃС‚СЂРѕРєР°-Р·Р°РіР»СѓС€РєР°
        if (params.rows.isEmpty()) {
            val baseline = y + ROW_HEIGHT_MIN / 2f + cellPaint.textSize / 3f
            canvas.drawRect(tableLeft, y, tableLeft + tableWidth, y + ROW_HEIGHT_MIN, zebraFill)
            canvas.drawText(
                "РќРµС‚ С‡РµРєРѕРІ Р·Р° РІС‹Р±СЂР°РЅРЅС‹Р№ РїРµСЂРёРѕРґ",
                tableLeft + tableWidth / 2f, baseline, cellCenterPaint
            )
            canvas.drawRect(tableLeft, y, tableLeft + tableWidth, y + ROW_HEIGHT_MIN, borderPaint)
            y += ROW_HEIGHT_MIN
        }

        // РЎС‚СЂРѕРєР° В«РС‚РѕРіРѕ:В» вЂ” РѕР±СЉРµРґРёРЅСЏРµС‚ РґРІРµ РїРµСЂРІС‹Рµ СЏС‡РµР№РєРё (в„– Рё РЅР°РёРјРµРЅРѕРІР°РЅРёРµ)
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
            "РС‚РѕРіРѕ:",
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

        // ----- Р‘Р»РѕРє РїРѕРґРїРёСЃРё (РІС‹СЂРѕРІРЅРµРЅ Рє РЅРёР¶РЅРµР№ С‡Р°СЃС‚Рё СЃС‚СЂР°РЅРёС†С‹) -----
        val signatureTop = (PAGE_HEIGHT - 5.67f - signatureBlockHeight).coerceAtLeast(y + 30f)

        // РћРґРЅРѕР№ СЃС‚СЂРѕРєРѕР№:  [Р”РѕР»Р¶РЅРѕСЃС‚СЊ]   ____РїРѕРґРїРёСЃСЊ____   [Р.Рћ. Р¤Р°РјРёР»РёСЏ]
        // Р”РѕР»Р¶РЅРѕСЃС‚СЊ Рё РїРѕРґРїРёСЃСЊ (Р.Рћ. Р¤Р°РјРёР»РёСЏ) РІС‹СЂРѕРІРЅРµРЅС‹ РїРѕ РѕРґРЅРѕР№ Р±Р°Р·РѕРІРѕР№ Р»РёРЅРёРё,
        // Р»РёРЅРёСЏ РґР»СЏ Р¶РёРІРѕР№ РїРѕРґРїРёСЃРё РїСЂРѕС…РѕРґРёС‚ РїРѕ С‚РѕРјСѓ Р¶Рµ СѓСЂРѕРІРЅСЋ РјРµР¶РґСѓ РЅРёРјРё. РџРѕРґРїРёСЃСЊ
        // В«(РїРѕРґРїРёСЃСЊ)В» вЂ” РјРµР»РєРёРј С€СЂРёС„С‚РѕРј СЃС‚СЂРѕРіРѕ РїРѕРґ Р»РёРЅРёРµР№.
        val initials = params.user.initialsSurname
        val initialsWidth = signatureTextPaint.measureText(initials)
        
        val availableWidth = PAGE_WIDTH - 2f * MARGIN
        val maxPositionWidth = availableWidth - initialsWidth - 80f - 32f
        val maxPosWidth = maxPositionWidth.coerceAtLeast(100f)
        
        val positionLines = wrapText(params.user.position, signatureTextPaint, maxPosWidth, 4)
        val actualPosWidth = positionLines.maxOfOrNull { signatureTextPaint.measureText(it) } ?: 0f
        
        val lineStart = MARGIN + actualPosWidth + 16f
        val lineEnd = PAGE_WIDTH - MARGIN - initialsWidth - 16f
        
        var py = signatureTop + 26f
        for (i in 0 until positionLines.size - 1) {
            canvas.drawText(positionLines[i], MARGIN, py, signatureTextPaint)
            py += signatureTextPaint.textSize + 4f
        }
        val lastPosLine = positionLines.lastOrNull() ?: ""
        canvas.drawText(lastPosLine, MARGIN, py, signatureTextPaint)
        
        val lastBaseline = py
        
        canvas.drawLine(lineStart, lastBaseline, lineEnd, lastBaseline, signatureLinePaint)
        canvas.drawText(initials, lineEnd + 16f, lastBaseline, signatureTextPaint)

        canvas.drawText(
            "(РїРѕРґРїРёСЃСЊ)",
            (lineStart + lineEnd) / 2f,
            lastBaseline + 11f,
            signatureSmallPaint
        )

        // РџРѕР»Рµ В«Р”Р°С‚Р°: ____В» вЂ” РЅРёР¶Рµ РїРѕРґРїРёСЃРё.
        val dateY = lastBaseline + 36f
        canvas.drawText("Р”Р°С‚Р°:", MARGIN, dateY, signatureTextPaint)
        val dateLineStart = MARGIN + signatureTextPaint.measureText("Р”Р°С‚Р°: ") + 4f
        canvas.drawLine(dateLineStart, dateY + 1f, dateLineStart + 180f, dateY + 1f, signatureLinePaint)

        finalizeAndFinishPage()

        // --- Р”РѕР±Р°РІР»РµРЅРёРµ СЃС‚СЂР°РЅРёС† СЃ С„РѕС‚Рѕ РґР»СЏ С‡РµРєРѕРІ, РґРѕР±Р°РІР»РµРЅРЅС‹С… РІСЂСѓС‡РЅСѓСЋ ---
        val manualReceipts = params.rows.mapIndexedNotNull { index, row ->
            if (row.receipt.isManual && row.receipt.manualPhotoUri != null) {
                index + 1 to row.receipt
            } else null
        }

        var manualSeq = 1
        for ((tableIdx, receipt) in manualReceipts) {
            val photoFile = java.io.File(receipt.manualPhotoUri!!)
            if (photoFile.exists()) {
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas

                val attachTitle = "РџСЂРёР»РѕР¶РµРЅРёРµ в„– $manualSeq Рє Р РµРµСЃС‚СЂСѓ (С‡РµРє в„– $tableIdx)"
                canvas.drawText(attachTitle, PAGE_WIDTH / 2f, MARGIN + titlePaint.textSize, titlePaint)

                if (totalPages > 0) { // РќРµ РґРµРєРѕРґРёСЂРѕРІР°С‚СЊ РєР°СЂС‚РёРЅРєСѓ РІРѕ РІСЂРµРјСЏ СЃСѓС…РѕРіРѕ РїСЂРѕРіРѕРЅР°
                    val bitmap = android.graphics.BitmapFactory.decodeFile(receipt.manualPhotoUri)
                    if (bitmap != null) {
                        val imgMaxWidth = PAGE_WIDTH - 2f * MARGIN
                        val imgMaxHeight = PAGE_HEIGHT - 2f * MARGIN - titlePaint.textSize - 20f
                        val scale = minOf(imgMaxWidth / bitmap.width, imgMaxHeight / bitmap.height, 1f)
                        val scaledWidth = bitmap.width * scale
                        val scaledHeight = bitmap.height * scale
                        
                        val imgLeft = (PAGE_WIDTH - scaledWidth) / 2f
                        val imgTop = MARGIN + titlePaint.textSize + 20f
                        
                        val destRect = android.graphics.RectF(imgLeft, imgTop, imgLeft + scaledWidth, imgTop + scaledHeight)
                        canvas.drawBitmap(bitmap, null, destRect, null)
                        bitmap.recycle()
                    }
                }

                if (totalPages > 1 || manualReceipts.isNotEmpty()) {
                    canvas.drawText(pageNumber.toString(), PAGE_WIDTH - MARGIN, MARGIN - 4f, pageNumberPaint)
                }

                doc.finishPage(page)
                manualSeq++
            }
        }
    }

    /**
     * РџРµСЂРµРЅРѕСЃРёС‚ С‚РµРєСЃС‚ РїРѕ СЃР»РѕРІР°Рј РІ РїСЂРµРґРµР»Р°С… maxWidth, РЅРµ Р±РѕР»РµРµ maxLines СЃС‚СЂРѕРє.
     * Р•СЃР»Рё РѕСЂРёРіРёРЅР°Р» РЅРµ РїРѕРјРµС‰Р°РµС‚СЃСЏ РїРѕР»РЅРѕСЃС‚СЊСЋ вЂ” РїРѕСЃР»РµРґРЅСЏСЏ СЃС‚СЂРѕРєР° СѓСЃРµРєР°РµС‚СЃСЏ СЃ В«вЂ¦В».
     * РЎР»РѕРІР° РґР»РёРЅРЅРµРµ maxWidth СЂР°Р·СЂС‹РІР°СЋС‚СЃСЏ РїРѕ СЃРёРјРІРѕР»Р°Рј.
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

        // РћС‡РµСЂРµРґСЊ С‚РѕРєРµРЅРѕРІ: РґР»РёРЅРЅС‹Рµ СЃР»РѕРІР° РІ РїСЂРѕС†РµСЃСЃРµ РѕР±СЂР°Р±РѕС‚РєРё РјРѕРіСѓС‚ Р±С‹С‚СЊ СЂР°Р·СЂРµР·Р°РЅС‹
        // Рё В«РґРѕС…РІРѕСЃС‚В» РІРѕР·РІСЂР°С‰С‘РЅ РІ РЅР°С‡Р°Р»Рѕ РѕС‡РµСЂРµРґРё.
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
                queue.addFirst(w) // РїРѕРїС‹С‚Р°РµРјСЃСЏ РІРјРµСЃС‚РёС‚СЊ СЃ РЅРѕРІРѕР№ СЃС‚СЂРѕРєРё
            } else {
                // РЎР»РѕРІРѕ РґР»РёРЅРЅРµРµ, С‡РµРј С€РёСЂРёРЅР° СЏС‡РµР№РєРё вЂ” СЂРµР¶РµРј РїРѕ СЃРёРјРІРѕР»Р°Рј.
                val cut = forceFit(w, paint, maxWidth)
                lines += cut
                val rest = w.substring(cut.length)
                if (rest.isNotEmpty()) queue.addFirst(rest)
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines += current

        // РќРµ РІСЃС‘ СѓР»РѕР¶РёР»РѕСЃСЊ РІ maxLines вЂ” РґРѕР±Р°РІР»СЏРµРј РјРЅРѕРіРѕС‚РѕС‡РёРµ РІ РїРѕСЃР»РµРґРЅСЋСЋ СЃС‚СЂРѕРєСѓ.
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

    /** РџСЂРёРЅСѓРґРёС‚РµР»СЊРЅРѕ РїРѕРґРіРѕРЅСЏРµС‚ РѕРґРЅРѕ СЃР»РѕРІРѕ, С‡С‚РѕР±С‹ РїСЂРµС„РёРєСЃ РїРѕРјРµСЃС‚РёР»СЃСЏ РІ maxWidth. */
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

    /** РћР±СЂРµР·Р°РµС‚ СЃС‚СЂРѕРєСѓ СЃ РєРѕРЅС†Р°. РЎ addEllipsis=true РіР°СЂР°РЅС‚РёСЂСѓРµС‚ В«вЂ¦В» РІ РєРѕРЅС†Рµ СЂРµР·СѓР»СЊС‚Р°С‚Р°. */
    private fun ellipsize(s: String, paint: Paint, maxWidth: Float, addEllipsis: Boolean = true): String {
        val target = if (addEllipsis) s + "..." else s
        if (paint.measureText(target) <= maxWidth) return target
        var t = s.trimEnd()
        while (t.isNotEmpty() && paint.measureText(t + "...") > maxWidth) {
            t = t.dropLast(1)
        }
        return if (t.isEmpty()) "..." else t + "..."
    }
}
