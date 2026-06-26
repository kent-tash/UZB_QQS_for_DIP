package com.example.uzb_qqs_for_dip.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import com.example.uzb_qqs_for_dip.data.model.PaymentType
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
            textSize = 9f * unit
            color = 0xFF111827.toInt()
            textAlign = Paint.Align.CENTER
        }
        val companyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 11.5f * unit
            color = 0xFF000000.toInt()
            textAlign = Paint.Align.CENTER
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 9f * unit
            color = 0xFF111827.toInt()
            textAlign = Paint.Align.LEFT
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 9f * unit
            color = 0xFF111827.toInt()
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 9f * unit
            color = 0xFF111827.toInt()
            textAlign = Paint.Align.RIGHT
        }
        val totalLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 12f * unit
            color = 0xFF000000.toInt()
        }
        val totalValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typefaceBold
            textSize = 12f * unit
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
        canvas.drawText("Savdo cheki / Sotuv", cx, y + 9f * unit, titlePaint)
        y += 13f * unit

        val sellerRaw = (sellerNameOverride ?: receipt.sellerName).trim().ifEmpty { "—" }
        val sellerLines = wrapToLines(companyPaint, formatSeller(sellerRaw), width - 2 * padX)
        sellerLines.forEach { line ->
            canvas.drawText(line, cx, y + 11.5f * unit, companyPaint)
            y += 14f * unit
        }
        y += 1f * unit

        // Дополнительные данные заголовка
        // 1. Адрес — по центру после названия
        receipt.address?.let {
            val addrLines = wrapToLines(titlePaint, it, width - 2 * padX)
            addrLines.forEach { line ->
                canvas.drawText(line, cx, y + 9f * unit, titlePaint)
                y += 11f * unit
            }
        }

        // 2. ИНН (STIR) продавца — по центру под адресом
        receipt.tin?.let {
            canvas.drawText(it, cx, y + 9f * unit, titlePaint)
            y += 11f * unit
        }
        y += 1f * unit

        // 3. Остальные строки — по левому краю
        receipt.terminalId?.let {
            canvas.drawText(it, left + padX, y + 9f * unit, datePaint)
            y += 11f * unit
        }

        receipt.receiptNumber?.let {
            canvas.drawText("Chek raqami : $it", left + padX, y + 9f * unit, datePaint)
            y += 11f * unit
        }

        receipt.nkmName?.let {
            // Если это Uzum или маркетплейс, на сайте пишется "Marketpleys nomi : ...", иначе "Onlayn NKM nomi : ..."
            val prefix = if (it.contains("market", ignoreCase = true) || it.contains("uzum", ignoreCase = true)) "Marketpleys nomi" else "Onlayn NKM nomi"
            canvas.drawText("$prefix : $it", left + padX, y + 9f * unit, datePaint)
            y += 11f * unit
        }

        receipt.sn?.let {
            canvas.drawText("SN : $it", left + padX, y + 9f * unit, datePaint)
            y += 11f * unit
        }

        canvas.drawText(DateFormat.formatDateTime(receipt.purchasedAt), left + padX, y + 9f * unit, datePaint)
        y += 13f * unit

        // Разделитель
        canvas.drawLine(left + padX, y, left + width - padX, y, dividerPaint)
        y += 9f * unit

        // 2) Данные оплаты — убираем Naqd/Bank по просьбе пользователя,
        // оставляем только Jami и QQS под чертой.
        val total = receipt.totalAmountTiyin
        val vat = receipt.vatAmountTiyin

        // 3) Jami to`lov
        canvas.drawText("Jami to`lov:", left + padX, y + 11f * unit, totalLabelPaint)
        canvas.drawText(MoneyFormat.fromTiyinDot(total), left + width - padX, y + 11f * unit, totalValuePaint)
        y += 13f * unit

        // 4) Umumiy QQS qiymati (НДС) — межстрочный интервал сжат, чтобы освободить место под QR
        canvas.drawText("Umumiy QQS qiymati", left + padX, y + 9f * unit, labelPaint)
        canvas.drawText(MoneyFormat.fromTiyinDot(vat), left + width - padX, y + 9f * unit, valuePaint)
        y += 11f * unit

        // 5) QR — занимает оставшееся пространство. Шрифты выше уменьшены, поэтому
        // под QR освобождается больше места; делаем его крупнее для удобного скана.
        val badgeSide = 32f * unit
        val bottom = top + height - 8f * unit
        val qrTopMin = y + 4f * unit
        // QR держим над бейджем номера (он рисуется поверх), чтобы не перекрыть угол кода.
        val qrAreaBottom = bottom - badgeSide - 4f * unit
        val qrAvailH = (qrAreaBottom - qrTopMin).coerceAtLeast(40f * unit)
        val qrSide = minOf(qrAvailH, width - 1.2f * padX, 300f * unit)
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
