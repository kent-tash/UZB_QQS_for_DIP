package com.example.uzb_qqs_for_dip.export

import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.PrintAttributes.MediaSize
import android.print.PrintAttributes.Resolution
import androidx.core.content.getSystemService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Передаёт уже сгенерированный PDF-файл в системный диалог печати Android,
 * откуда пользователь может либо распечатать, либо «Сохранить как PDF».
 */
object PdfPrint {

    fun print(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService<PrintManager>() ?: return
        val attrs = PrintAttributes.Builder()
            .setMediaSize(MediaSize.ISO_A4)
            .setResolution(Resolution("default", "default", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, FilePrintAdapter(file, jobName), attrs)
    }

    private class FilePrintAdapter(
        private val source: File,
        private val displayName: String
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(displayName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                FileInputStream(source).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback.onWriteCancelled()
                                return
                            }
                            output.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (t: Throwable) {
                callback.onWriteFailed(t.message ?: "Ошибка записи PDF")
            }
        }
    }
}
