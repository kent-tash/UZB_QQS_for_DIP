package com.example.uzb_qqs_for_dip.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat

/**
 * Рендерит «карточку чека», стилизованную под фискальный чек Узбекистана:
 * шапка с названием организации, дата/время, строки оплаты, итог («Jami to`lov»),
 * сумма НДС («Umumiy QQS qiymati»), фискальный признак, QR-код и черный квадрат
 * с порядковым номером в левом нижнем углу.
 *
 * Используется как для PNG-экспорта одного чека, так и для PDF с 6 чеками на лист.
 */
object ReceiptCardRenderer {

    /** Стандартное соотношение сторон карточки (близко к чеку: высокий портретный). */
    const val ASPECT_W = 360f
    const val ASPECT_H = 460f

    /** Создаёт PNG-битмап карточки в заданной ширине, высота вычисляется по соотношению. */
    fun renderBitmap(receipt: Receipt, sellerNameOverride: String? = null, ordinal: Int, width: Int = 720): Bitmap {
        val w = width.coerceAtLeast(360)
        val h = (w * ASPECT_H / ASPECT_W).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        renderInto(
            canvas = canvas,
            receipt = receipt,
            sellerNameOverride = sellerNameOverride,
            ordinal = ordinal,
            left = 0f,
            top = 0f,
            width = w.toFloat(),
            height = h.toFloat()
        )
        return bmp
    }

    /**
     * Рисует карточку чека внутри прямоугольника ([left], [top], width × height) на готовом холсте.
     * Не очищает фон сам — нужный цвет фона должен быть нанесён вызывающим, иначе будет прозрачно.
     */
    fun renderInto(
        canvas: Canvas,
        receipt: Receipt,
        sellerNameOverride: String? = null,
        ordinal: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        // Базовая единица: 1 «slot» = 1/360 от ширины — упрощает шкалирование шрифтов.
        val unit = width / ASPECT_W
        val padX = 14f * unit
        var y = top + 14f * unit

        val typeface = Typeface.SANS_SERIF
        val typefaceBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 11f * unit
            color = 0xFF111827.toInt()
            textAlign = Paint.Align.CENTER
        }
        val companyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 14f * unit
            color = 0xFF000000.toInt()
            textAlign = Paint.Align.CENTER
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 11f * unit
            color = 0xFF111827.toInt()
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 11f * unit
            color = 0xFF111827.toInt()
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 11f * unit
            color = 0xFF111827.toInt()
            textAlign = Paint.Align.RIGHT
        }
        val totalLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 14.5f * unit
            color = 0xFF000000.toInt()
        }
        val totalValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 14.5f * unit
            color = 0xFF000000.toInt()
            textAlign = Paint.Align.RIGHT
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111827.toInt()
            strokeWidth = 1f * unit
            style = Paint.Style.STROKE
        }

        // Внешняя рамка карточки — необязательно, но визуально полезно.
        val outerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1F2937.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * unit
        }
        canvas.drawRect(
            left + 0.5f * unit,
            top + 0.5f * unit,
            left + width - 0.5f * unit,
            top + height - 0.5f * unit,
            outerBorder
        )

        val cx = left + width / 2f

        // 1) Шапка
        canvas.drawText("Savdo cheki / Sotuv", cx, y + 11f * unit, titlePaint)
        y += 16f * unit

        val sellerRaw = (sellerNameOverride ?: receipt.sellerName).trim().ifEmpty { "—" }
        val sellerLines = wrapToLines(companyPaint, formatSeller(sellerRaw), width - 2 * padX)
        sellerLines.forEach { line ->
            canvas.drawText(line, cx, y + 14f * unit, companyPaint)
            y += 17f * unit
        }
        y += 2f * unit
        canvas.drawText(DateFormat.formatDateTime(receipt.purchasedAt), cx, y + 11f * unit, datePaint)
        y += 16f * unit

        // Разделитель
        canvas.drawLine(left + padX, y, left + width - padX, y, dividerPaint)
        y += 10f * unit

        // 2) Строки оплаты — у нас есть только итог, поэтому отображаем
        //    «Naqd pul ... 0.00» и «Bank kartalari ... <total>».
        val total = receipt.totalAmountTiyin
        val vat = receipt.vatAmountTiyin

        drawKeyValue(canvas, "Naqd pul", "0.00", left + padX, left + width - padX, y, labelPaint, valuePaint)
        y += 16f * unit
        drawKeyValue(
            canvas, "Bank kartalari", MoneyFormat.fromTiyin(total),
            left + padX, left + width - padX, y, labelPaint, valuePaint
        )
        y += 18f * unit

        // 3) Jami to`lov
        canvas.drawText("Jami to`lov:", left + padX, y + 14f * unit, totalLabelPaint)
        canvas.drawText(MoneyFormat.fromTiyin(total), left + width - padX, y + 14f * unit, totalValuePaint)
        y += 22f * unit

        // 4) Umumiy QQS qiymati (НДС)
        drawKeyValue(
            canvas, "Umumiy QQS qiymati", MoneyFormat.fromTiyin(vat),
            left + padX, left + width - padX, y, labelPaint, valuePaint
        )
        y += 16f * unit

        // 5) Fiskal belgi — пробуем достать из URL, иначе считаем стабильный псевдо-номер.
        val fiscal = extractFiscalSign(receipt.qrUrl) ?: deterministicFiscal(receipt.qrUrl)
        drawKeyValue(
            canvas, "Fiskal belgi", fiscal,
            left + padX, left + width - padX, y, labelPaint, valuePaint
        )
        y += 14f * unit

        // 6) QR — занимает оставшееся пространство, не залезая на нижний бейдж номера.
        val badgeSide = 36f * unit
        val bottom = top + height - 10f * unit
        val qrTopMin = y + 6f * unit
        val qrAreaBottom = bottom - badgeSide - 6f * unit
        val qrAvailH = (qrAreaBottom - qrTopMin).coerceAtLeast(40f * unit)
        // Ранее было 130*unit — под QR обычно больше высоты; крупнее QR проще отсканировать с бумаги/PDF.
        val qrSide = minOf(qrAvailH, width - 2 * padX, 200f * unit)
        val qrLeft = left + (width - qrSide) / 2f
        val qrTop = qrTopMin
        QrEncoder.draw(canvas, receipt.qrUrl, qrLeft, qrTop, qrSide)

        // 7) Бейдж с порядковым номером — белая цифра на чёрном квадрате (нижний-левый угол)
        drawOrdinalBadge(
            canvas = canvas,
            ordinal = ordinal,
            left = left + 8f * unit,
            top = bottom - badgeSide,
            side = badgeSide
        )
    }

    private fun drawKeyValue(
        canvas: Canvas,
        label: String,
        value: String,
        leftEdge: Float,
        rightEdge: Float,
        y: Float,
        labelPaint: TextPaint,
        valuePaint: TextPaint
    ) {
        canvas.drawText(label, leftEdge, y + 11f, labelPaint)
        canvas.drawText(value, rightEdge, y + 11f, valuePaint)
    }

    private fun drawOrdinalBadge(canvas: Canvas, ordinal: Int, left: Float, top: Float, side: Float) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); style = Paint.Style.FILL }
        canvas.drawRect(left, top, left + side, top + side, bg)
        val text = ordinal.toString()
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            // подбираем размер так, чтобы число влезало в квадрат
            textSize = side * when (text.length) { 1 -> 0.7f; 2 -> 0.55f; 3 -> 0.42f; else -> 0.34f }
        }
        val bounds = Rect()
        tp.getTextBounds(text, 0, text.length, bounds)
        val cx = left + side / 2f
        val cy = top + side / 2f - bounds.exactCenterY()
        canvas.drawText(text, cx, cy, tp)
    }

    /** Строит набор строк, перенося по словам, чтобы каждая помещалась в [maxWidth]. */
    private fun wrapToLines(paint: TextPaint, text: String, maxWidth: Float): List<String> {
        val words = text.split(' ')
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (word in words) {
            val candidate = if (cur.isEmpty()) word else "$cur $word"
            if (paint.measureText(candidate) <= maxWidth) {
                cur.clear(); cur.append(candidate)
            } else {
                if (cur.isNotEmpty()) out.add(cur.toString())
                if (paint.measureText(word) > maxWidth) {
                    // обрезаем длинное слово многоточием
                    var s = word
                    while (s.isNotEmpty() && paint.measureText("$s…") > maxWidth) {
                        s = s.dropLast(1)
                    }
                    out.add(if (s.isEmpty()) "" else "$s…")
                    cur.clear()
                } else {
                    cur.clear(); cur.append(word)
                }
            }
            if (out.size >= 2) break
        }
        if (cur.isNotEmpty() && out.size < 2) out.add(cur.toString())
        if (out.isEmpty()) out.add("")
        return out
    }

    private fun formatSeller(name: String): String {
        // Делаем «Savdo cheki / Sotuv» стилистически — название в кавычках, как на чеках.
        val trimmed = name.trim()
        if (trimmed.startsWith("\"") || trimmed.contains("«")) return trimmed
        // Если в названии уже есть форма организации, не оборачиваем.
        val orgSuffixes = listOf("AJ", "MCHJ", "OOO", "ZAO", "ИП", "OAJ", "AO")
        return if (orgSuffixes.any { trimmed.endsWith(" $it", ignoreCase = true) }) {
            val parts = trimmed.split(' ')
            val org = parts.last()
            val core = parts.dropLast(1).joinToString(" ")
            "\"$core\" $org"
        } else {
            "\"$trimmed\""
        }
    }

    private fun extractFiscalSign(url: String): String? {
        // Параметр `f`/`s` в URL чека soliq.uz часто содержит фискальный признак/сумму.
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return null
        val params = q.split('&').mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx).lowercase() to it.substring(idx + 1)
        }.toMap()
        val candidates = listOf("f", "fs", "fp", "fb", "fd", "code")
        for (k in candidates) {
            params[k]?.takeIf { it.length in 6..30 }?.let { return it.uppercase() }
        }
        return null
    }

    private fun deterministicFiscal(seed: String): String {
        // Стабильный 12-символьный индекс на основе хэша URL (чтобы поле не было пустым,
        // если на странице не нашли реальный fiscal sign). Это не подмена реального признака,
        // а лишь визуальный плейсхолдер.
        val hash = seed.hashCode().toLong() and 0xFFFFFFFFL
        return hash.toString().padStart(12, '0').take(12)
    }
}
