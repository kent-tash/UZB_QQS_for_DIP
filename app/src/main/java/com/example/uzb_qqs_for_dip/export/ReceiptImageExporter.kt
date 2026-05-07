package com.example.uzb_qqs_for_dip.export

import android.content.Context
import android.graphics.Bitmap
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.render.ReceiptCardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ReceiptImageExporter {

    /** Возвращает каталог, куда складываются PNG карточек чеков. */
    fun imagesDir(context: Context): File {
        val dir = File(ExportPaths.exportsDir(context), "receipts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Имя файла на диске — стабильно по id чека, чтобы перезаписывать. */
    fun fileForReceipt(context: Context, receiptId: Long): File =
        File(imagesDir(context), "receipt_${receiptId}.png")

    /**
     * Сохраняет PNG карточки чека с указанным порядковым номером (тем же, что в таблице сейчас).
     * Перезаписывает существующий файл, чтобы № в файле всегда совпадал с текущей сортировкой.
     */
    suspend fun saveSingle(
        context: Context,
        receipt: Receipt,
        ordinal: Int,
        widthPx: Int = 720
    ): File = withContext(Dispatchers.IO) {
        val bmp = ReceiptCardRenderer.renderBitmap(receipt = receipt, ordinal = ordinal, width = widthPx)
        try {
            val file = fileForReceipt(context, receipt.id)
            FileOutputStream(file).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            file
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Перегенерирует PNG для всех чеков в порядке списка: ordinal = индекс + 1.
     * Также удаляет «осиротевшие» PNG-файлы для удалённых чеков.
     */
    suspend fun regenerateAll(
        context: Context,
        receiptsInOrder: List<Receipt>,
        widthPx: Int = 720
    ) = withContext(Dispatchers.IO) {
        val dir = imagesDir(context)
        val keepNames = receiptsInOrder.map { "receipt_${it.id}.png" }.toSet()
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("receipt_") && f.name.endsWith(".png") && f.name !in keepNames) {
                f.delete()
            }
        }
        receiptsInOrder.forEachIndexed { idx, r ->
            saveSingle(context, r, ordinal = idx + 1, widthPx = widthPx)
        }
    }
}
