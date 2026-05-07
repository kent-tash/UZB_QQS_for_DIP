package com.example.uzb_qqs_for_dip.export

import android.content.Context
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object CsvExporter {

    suspend fun export(
        context: Context,
        rows: List<ReceiptWithUser>,
        fileName: String = "checks_${System.currentTimeMillis()}.csv"
    ): File = withContext(Dispatchers.IO) {
        val file = File(ExportPaths.exportsDir(context), fileName)
        FileOutputStream(file).use { fos ->
            // BOM нужен Excel-у, чтобы корректно отобразить кириллицу из UTF-8.
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { w ->
                w.append("Пользователь;Должность;Дата и время;Юридическое лицо;Итоговая сумма;НДС (QQS)\r\n")
                rows.forEach { r ->
                    w.append(escape(r.userFullName)).append(';')
                    w.append(escape(r.userPosition)).append(';')
                    w.append(escape(DateFormat.formatDateTime(r.receipt.purchasedAt))).append(';')
                    w.append(escape(r.receipt.sellerName)).append(';')
                    w.append(escape(MoneyFormat.fromTiyin(r.receipt.totalAmountTiyin))).append(';')
                    w.append(escape(MoneyFormat.fromTiyin(r.receipt.vatAmountTiyin))).append("\r\n")
                }
            }
        }
        file
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        val safe = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$safe\"" else safe
    }
}
