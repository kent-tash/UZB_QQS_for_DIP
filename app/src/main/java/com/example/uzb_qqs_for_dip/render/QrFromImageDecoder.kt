package com.example.uzb_qqs_for_dip.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.BarcodeFormat
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Декодирует QR-код с фотографии, выбранной пользователем (Uri из галереи/файлов).
 *
 * Стратегия: загружаем картинку (учитывая EXIF-ориентацию через ImageDecoder),
 * при необходимости даунсемплим, далее пробуем несколько вариантов:
 *   1) HybridBinarizer на оригинале;
 *   2) GlobalHistogramBinarizer (лучше для фото с засветами);
 *   3) HybridBinarizer на повороте 180° (на случай чека «вверх ногами»).
 *
 * Возвращает текст QR-кода или бросает исключение с понятным сообщением.
 */
object QrFromImageDecoder {

    /** Максимальная сторона рабочего изображения (большего не нужно для QR). */
    private const val MAX_SIDE = 1600

    suspend fun decode(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(context, uri)
            ?: throw IllegalArgumentException("Не удалось открыть изображение")
        try {
            decodeBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** Открыто для тестов: декодирование готового битмапа. */
    fun decodeBitmap(bitmap: Bitmap): String {
        val attempts = listOf(
            { tryDecode(bitmap, hybrid = true, rotate180 = false) },
            { tryDecode(bitmap, hybrid = false, rotate180 = false) },
            { tryDecode(bitmap, hybrid = true, rotate180 = true) }
        )
        for (attempt in attempts) {
            val result = attempt()
            if (!result.isNullOrBlank()) return result
        }
        throw IllegalStateException("На фото не удалось распознать QR-код")
    }

    private fun tryDecode(bitmap: Bitmap, hybrid: Boolean, rotate180: Boolean): String? {
        val src = if (rotate180) rotate(bitmap, 180) else bitmap
        try {
            val w = src.width
            val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            val luminance = RGBLuminanceSource(w, h, pixels)
            val binary = BinaryBitmap(
                if (hybrid) HybridBinarizer(luminance) else GlobalHistogramBinarizer(luminance)
            )
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.CHARACTER_SET to "UTF-8"
                    )
                )
            }
            val result: Result = try {
                reader.decodeWithState(binary)
            } catch (_: NotFoundException) {
                return null
            } catch (_: ChecksumException) {
                return null
            } catch (_: FormatException) {
                return null
            }
            return result.text
        } finally {
            if (rotate180 && src !== bitmap) src.recycle()
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val maxSide = maxOf(info.size.width, info.size.height)
                if (maxSide > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / maxSide
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            // На API < 28 EXIF-ориентация по-хорошему требует дополнительных шагов;
            // для нашей задачи (QR на чеке) обычно достаточно «ровного» снимка.
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            val sample = computeInSampleSize(opts.outWidth, opts.outHeight)
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts2)
            }
        }
    }

    private fun computeInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w, h) > MAX_SIDE) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    private fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bmp
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }
}
