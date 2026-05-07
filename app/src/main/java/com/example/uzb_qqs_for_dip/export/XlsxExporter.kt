package com.example.uzb_qqs_for_dip.export

import android.content.Context
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.util.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Минималистичный writer XLSX без внешних библиотек: формирует ZIP-архив с набором XML-файлов
 * по спецификации Office Open XML (SpreadsheetML). Числа пишутся как numeric — Excel/Google Sheets
 * корректно их суммируют. Строки кодируются через sharedStrings, что компактно и поддерживает кириллицу.
 */
object XlsxExporter {

    private data class Cell(val text: String? = null, val number: Double? = null)

    suspend fun export(
        context: Context,
        rows: List<ReceiptWithUser>,
        fileName: String = "checks_${System.currentTimeMillis()}.xlsx"
    ): File = withContext(Dispatchers.IO) {
        val header = listOf(
            "Пользователь",
            "Должность",
            "Дата и время",
            "Юридическое лицо",
            "Итоговая сумма",
            "НДС (QQS)"
        ).map { Cell(text = it) }

        val data = rows.map { r ->
            listOf(
                Cell(text = r.userFullName),
                Cell(text = r.userPosition),
                Cell(text = DateFormat.formatDateTime(r.receipt.purchasedAt)),
                Cell(text = r.receipt.sellerName),
                Cell(number = r.receipt.totalAmountTiyin / 100.0),
                Cell(number = r.receipt.vatAmountTiyin / 100.0)
            )
        }

        val totalSum = rows.sumOf { it.receipt.totalAmountTiyin } / 100.0
        val totalVat = rows.sumOf { it.receipt.vatAmountTiyin } / 100.0
        val totals = listOf(
            Cell(text = "Итого:"),
            Cell(text = ""),
            Cell(text = ""),
            Cell(text = ""),
            Cell(number = totalSum),
            Cell(number = totalVat)
        )

        val sheet: List<List<Cell>> = buildList {
            add(header)
            addAll(data)
            add(totals)
        }

        val sharedStrings = mutableListOf<String>()
        val sharedIndex = mutableMapOf<String, Int>()
        fun stringIndex(s: String): Int = sharedIndex.getOrPut(s) {
            sharedStrings.add(s); sharedStrings.size - 1
        }

        val sheetXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<cols>")
            append("<col min=\"1\" max=\"1\" width=\"24\" customWidth=\"1\"/>")
            append("<col min=\"2\" max=\"2\" width=\"22\" customWidth=\"1\"/>")
            append("<col min=\"3\" max=\"3\" width=\"18\" customWidth=\"1\"/>")
            append("<col min=\"4\" max=\"4\" width=\"36\" customWidth=\"1\"/>")
            append("<col min=\"5\" max=\"5\" width=\"18\" customWidth=\"1\"/>")
            append("<col min=\"6\" max=\"6\" width=\"18\" customWidth=\"1\"/>")
            append("</cols>")
            append("<sheetData>")
            sheet.forEachIndexed { rowIdx, cells ->
                val rowNumber = rowIdx + 1
                val isHeader = rowIdx == 0
                val isTotalsRow = rowIdx == sheet.lastIndex && rows.isNotEmpty()
                append("<row r=\"$rowNumber\">")
                cells.forEachIndexed { colIdx, cell ->
                    val ref = colName(colIdx) + rowNumber
                    val style = when {
                        isHeader -> 1
                        isTotalsRow && cell.number != null -> 3
                        isTotalsRow -> 2
                        cell.number != null -> 4
                        else -> 0
                    }
                    if (cell.number != null) {
                        append("<c r=\"$ref\" t=\"n\" s=\"$style\"><v>${formatNumber(cell.number)}</v></c>")
                    } else {
                        val txt = cell.text.orEmpty()
                        if (txt.isEmpty()) {
                            append("<c r=\"$ref\" s=\"$style\"/>")
                        } else {
                            val idx = stringIndex(txt)
                            append("<c r=\"$ref\" t=\"s\" s=\"$style\"><v>$idx</v></c>")
                        }
                    }
                }
                append("</row>")
            }
            append("</sheetData>")
            append("</worksheet>")
        }

        val sharedStringsXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
            append("count=\"${sharedStrings.size}\" uniqueCount=\"${sharedStrings.size}\">")
            sharedStrings.forEach {
                append("<si><t xml:space=\"preserve\">").append(escapeXml(it)).append("</t></si>")
            }
            append("</sst>")
        }

        val workbookXml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"Чеки\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

        val workbookRels =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>"

        val rootRels =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>"

        val contentTypes =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>"

        val styles = buildStylesXml()

        val file = File(ExportPaths.exportsDir(context), fileName)
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zip ->
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                writeEntry(zip, "[Content_Types].xml", contentTypes)
                writeEntry(zip, "_rels/.rels", rootRels)
                writeEntry(zip, "xl/workbook.xml", workbookXml)
                writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels)
                writeEntry(zip, "xl/sharedStrings.xml", sharedStringsXml)
                writeEntry(zip, "xl/styles.xml", styles)
                writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
            }
        }
        file
    }

    private fun buildStylesXml(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<numFmts count=\"1\">")
        append("<numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/>")
        append("</numFmts>")
        append("<fonts count=\"3\">")
        append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        append("<font><sz val=\"11\"/><name val=\"Calibri\"/><b/><color rgb=\"FFFFFFFF\"/></font>")
        append("<font><sz val=\"11\"/><name val=\"Calibri\"/><b/></font>")
        append("</fonts>")
        append("<fills count=\"4\">")
        append("<fill><patternFill patternType=\"none\"/></fill>")
        append("<fill><patternFill patternType=\"gray125\"/></fill>")
        append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4E79\"/></patternFill></fill>")
        append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9E1F2\"/></patternFill></fill>")
        append("</fills>")
        append("<borders count=\"2\">")
        append("<border/>")
        append("<border>")
        append("<left style=\"thin\"><color rgb=\"FFAAAAAA\"/></left>")
        append("<right style=\"thin\"><color rgb=\"FFAAAAAA\"/></right>")
        append("<top style=\"thin\"><color rgb=\"FFAAAAAA\"/></top>")
        append("<bottom style=\"thin\"><color rgb=\"FFAAAAAA\"/></bottom>")
        append("</border>")
        append("</borders>")
        append("<cellXfs count=\"5\">")
        // 0: default
        append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyBorder=\"1\"/>")
        // 1: header
        append("<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">")
        append("<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>")
        // 2: totals row text
        append("<xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">")
        append("<alignment horizontal=\"right\"/></xf>")
        // 3: totals row number
        append("<xf numFmtId=\"164\" fontId=\"2\" fillId=\"3\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyNumberFormat=\"1\"/>")
        // 4: data number
        append("<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyBorder=\"1\" applyNumberFormat=\"1\"/>")
        append("</cellXfs>")
        append("</styleSheet>")
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun colName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    private fun formatNumber(value: Double): String {
        // XLSX требует точку в качестве десятичного разделителя.
        return "%.2f".format(java.util.Locale.US, value)
    }

    private fun escapeXml(s: String): String = buildString(s.length + 16) {
        s.forEach { c ->
            when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    @Suppress("unused")
    private fun unused(): ByteArrayOutputStream = ByteArrayOutputStream()
}
