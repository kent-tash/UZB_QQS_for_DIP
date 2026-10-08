package com.example.uzb_qqs_for_dip.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Кодирует строку в QR-матрицу и рисует её внутри прямоугольника на холсте. */
object QrEncoder {

    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    /**
     * Кодирует [content] и рисует QR в квадрате со стороной [side] с верхним левым углом ([left], [top]).
     * Если кодирование не удалось — рисуется белый квадрат с тонкой рамкой (визуальный фолбэк).
     */
    fun draw(canvas: Canvas, content: String, left: Float, top: Float, side: Float) {
        val writer = QRCodeWriter()
        val matrix: BitMatrix? = try {
            writer.encode(
                content,
                BarcodeFormat.QR_CODE,
                512,
                512,
                mapOf(
                    EncodeHintType.MARGIN to 0,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8"
                )
            )
        } catch (_: WriterException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        // Белый фон под QR.
        canvas.drawRect(left, top, left + side, top + side, whitePaint)

        if (matrix == null) {
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF888888.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f
            }
            canvas.drawRect(left, top, left + side, top + side, border)
            return
        }

        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        var offset = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[offset++] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        val paint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        val destRect = RectF(left, top, left + side, top + side)
        canvas.drawBitmap(bitmap, null, destRect, paint)
    }
}
