package com.example.uzb_qqs_for_dip.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * OCR распечатанного реестра через ML Kit Text Recognition (латиница + цифры;
 * кириллические названия часто тоже читаются; при ошибках — ручная правка).
 */
object PaperReportOcr {

    suspend fun recognize(bitmap: Bitmap): Text = suspendCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                recognizer.close()
                cont.resume(text)
            }
            .addOnFailureListener { e ->
                recognizer.close()
                cont.resumeWithException(e)
            }
    }
}
