package com.example.uzb_qqs_for_dip.export

import android.content.Context
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.util.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Минималистичный writer XLSX без внешних библиотек.
 *
 * [exportReport] повторяет структуру PDF-отчёта ([PdfReportGenerator]):
 * шапка реестра, таблица № | Организация | Сумма | НДС | Дата, Итого, блок подписи.
 *
 * [export] — плоская таблица чеков (вкладка «Чеки»).
 */
object XlsxExporter {

    private data class Cell(
        val text: String? = null,
        val number: Double? = null,
        /** 0 default, 1 header, 2 totals text, 3 totals num, 4 data num, 5 title, 6 signature */
        val style: Int = 0
    )

    /** Отчёт в форме PDF: шапка + таблица + подпись. */
    suspend fun exportReport(
        context: Context,
        params: ReportParams,
        fileName: String = "report_${params.user.id}_${System.currentTimeMillis()}.xlsx"
    ): File = withContext(Dispatchers.IO) {
        val periodText = params.quarterLabel
            ?: "${DateFormat.formatDate(params.periodStart)} — ${DateFormat.formatDate(params.periodEnd)}"
        val userLine = "${params.user.position} ${params.user.fullName}".trim()

        val sheet = buildList {
            add(listOf(Cell(text = "Реестр предъявляемых к возмещению платежных документов", style = 5)))
            add(listOf(Cell(text = periodText, style = 5)))
            add(listOf(Cell(text = userLine, style = 5)))
            add(listOf(Cell(text = "")))

            add(
                listOf(
                    Cell(text = "№", style = 1),
                    Cell(text = "Наименование организации", style = 1),
                    Cell(text = "Сумма", style = 1),
                    Cell(text = "НДС", style = 1),
                    Cell(text = "Дата", style = 1)
                )
            )

            params.rows.forEachIndexed { idx, item ->
                add(
                    listOf(
                        Cell(number = (idx + 1).toDouble()),
                        Cell(text = item.receipt.sellerName.ifBlank { "—" }),
                        Cell(number = item.receipt.totalAmountTiyin / 100.0, style = 4),
                        Cell(number = item.receipt.vatAmountTiyin / 100.0, style = 4),
                        Cell(text = DateFormat.formatDateTime(item.receipt.purchasedAt))
                    )
                )
            }

            val totalSum = params.rows.sumOf { it.receipt.totalAmountTiyin } / 100.0
            val totalVat = params.rows.sumOf { it.receipt.vatAmountTiyin } / 100.0
            add(
                listOf(
                    Cell(text = "Итого:", style = 2),
                    Cell(text = "", style = 2),
                    Cell(number = totalSum, style = 3),
                    Cell(number = totalVat, style = 3),
                    Cell(text = "", style = 2)
                )
            )

            add(listOf(Cell(text = "")))
            add(
                listOf(
                    Cell(text = params.user.position, style = 6),
                    Cell(text = "____________________", style = 6),
                    Cell(text = "(подпись)", style = 6),
                    Cell(text = params.user.initialsSurname, style = 6),
                    Cell(text = "")
                )
            )
            add(
                listOf(
                    Cell(text = "Дата: ____________________", style = 6),
                    Cell(text = ""),
                    Cell(text = ""),
                    Cell(text = ""),
                    Cell(text = "")
                )
            )
        }

        writeWorkbook(
            context = context,
            fileName = fileName,
            sheetName = "Отчёт",
            sheet = sheet,
            colWidths = listOf(8, 36, 14, 14, 18),
            mergeTitleRows = true
        )
    }

    /** Плоская таблица для вкладки «Чеки». */
    suspend fun export(
        context: Context,
        rows: List<ReceiptWithUser>,
        fileName: String = "checks_${System.currentTimeMillis()}.xlsx"
    ): File = withContext(Dispatchers.IO) {
        val sheet = buildList {
            add(
                listOf(
                    Cell(text = "Пользователь", style = 1),
                    Cell(text = "Должность", style = 1),
                    Cell(text = "Дата и время", style = 1),
                    Cell(text = "Юридическое лицо", style = 1),
                    Cell(text = "Итоговая сумма", style = 1),
                    Cell(text = "НДС (QQS)", style = 1)
                )
            )
            rows.forEach { r ->
                add(
                    listOf(
                        Cell(text = r.userFullName),
                        Cell(text = r.userPosition),
                        Cell(text = DateFormat.formatDateTime(r.receipt.purchasedAt)),
                        Cell(text = r.receipt.sellerName),
                        Cell(number = r.receipt.totalAmountTiyin / 100.0, style = 4),
                        Cell(number = r.receipt.vatAmountTiyin / 100.0, style = 4)
                    )
                )
            }
            val totalSum = rows.sumOf { it.receipt.totalAmountTiyin } / 100.0
            val totalVat = rows.sumOf { it.receipt.vatAmountTiyin } / 100.0
            add(
                listOf(
                    Cell(text = "Итого:", style = 2),
                    Cell(text = "", style = 2),
                    Cell(text = "", style = 2),
                    Cell(text = "", style = 2),
                    Cell(number = totalSum, style = 3),
                    Cell(number = totalVat, style = 3)
                )
            )
        }
        writeWorkbook(
            context = context,
            fileName = fileName,
            sheetName = "Чеки",
            sheet = sheet,
            colWidths = listOf(24, 22, 18, 36, 18, 18),
            mergeTitleRows = false
        )
    }

    private fun writeWorkbook(
        context: Context,
        fileName: String,
        sheetName: String,
        sheet: List<List<Cell>>,
        colWidths: List<Int>,
        mergeTitleRows: Boolean
    ): File {
        val sharedStrings = mutableListOf<String>()
        val sharedIndex = mutableMapOf<String, Int>()
        fun stringIndex(s: String): Int = sharedIndex.getOrPut(s) {
            sharedStrings.add(s); sharedStrings.size - 1
        }

        val colCount = colWidths.size
        val sheetXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
            append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
            append("<cols>")
            colWidths.forEachIndexed { i, w ->
                append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
            }
            append("</cols>")
            append("<sheetData>")
            sheet.forEachIndexed { rowIdx, cells ->
                val rowNumber = rowIdx + 1
                append("<row r=\"$rowNumber\">")
                // Pad to colCount for merged title rows
                val padded = if (cells.size < colCount && cells.firstOrNull()?.style == 5) {
                    cells + List(colCount - cells.size) { Cell(text = "", style = 5) }
                } else {
                    cells
                }
                padded.forEachIndexed { colIdx, cell ->
                    if (colIdx >= colCount) return@forEachIndexed
                    val ref = colName(colIdx) + rowNumber
                    val style = cell.style
                    if (cell.number != null) {
                        append("<c r=\"$ref\" t=\"n\" s=\"$style\"><v>${formatNumber(cell.number)}</v></c>")
                    } else {
                        val txt = cell.text.orEmpty()
                        if (txt.isEmpty()) {
                            append("<c r=\"$ref\" s=\"$style\"/>")
                        } else {
                            append("<c r=\"$ref\" t=\"s\" s=\"$style\"><v>${stringIndex(txt)}</v></c>")
                        }
                    }
                }
                append("</row>")
            }
            append("</sheetData>")
            if (mergeTitleRows) {
                // mergeCells идёт после sheetData (ECMA-376)
                append("<mergeCells count=\"3\">")
                append("<mergeCell ref=\"A1:${colName(colCount - 1)}1\"/>")
                append("<mergeCell ref=\"A2:${colName(colCount - 1)}2\"/>")
                append("<mergeCell ref=\"A3:${colName(colCount - 1)}3\"/>")
                append("</mergeCells>")
            }
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
                "<sheets><sheet name=\"${escapeXml(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
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

        val file = File(ExportPaths.exportsDir(context), fileName)
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zip ->
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                writeEntry(zip, "[Content_Types].xml", contentTypes)
                writeEntry(zip, "_rels/.rels", rootRels)
                writeEntry(zip, "xl/workbook.xml", workbookXml)
                writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels)
                writeEntry(zip, "xl/sharedStrings.xml", sharedStringsXml)
                writeEntry(zip, "xl/styles.xml", buildStylesXml())
                writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
            }
        }
        return file
    }

    private fun buildStylesXml(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/></numFmts>")
        append("<fonts count=\"4\">")
        append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        append("<font><sz val=\"11\"/><name val=\"Calibri\"/><b/><color rgb=\"FFFFFFFF\"/></font>")
        append("<font><sz val=\"11\"/><name val=\"Calibri\"/><b/></font>")
        append("<font><sz val=\"12\"/><name val=\"Calibri\"/><b/></font>")
        append("</fonts>")
        append("<fills count=\"4\">")
        append("<fill><patternFill patternType=\"none\"/></fill>")
        append("<fill><patternFill patternType=\"gray125\"/></fill>")
        append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4E79\"/></patternFill></fill>")
        append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9E1F2\"/></patternFill></fill>")
        append("</fills>")
        append("<borders count=\"2\"><border/>")
        append("<border><left style=\"thin\"><color rgb=\"FFAAAAAA\"/></left>")
        append("<right style=\"thin\"><color rgb=\"FFAAAAAA\"/></right>")
        append("<top style=\"thin\"><color rgb=\"FFAAAAAA\"/></top>")
        append("<bottom style=\"thin\"><color rgb=\"FFAAAAAA\"/></bottom></border></borders>")
        append("<cellXfs count=\"7\">")
        append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyBorder=\"1\"/>")
        append("<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">")
        append("<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>")
        append("<xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>")
        append("<xf numFmtId=\"164\" fontId=\"2\" fillId=\"3\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyNumberFormat=\"1\"/>")
        append("<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyBorder=\"1\" applyNumberFormat=\"1\"/>")
        append("<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" applyFont=\"1\" applyAlignment=\"1\">")
        append("<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>")
        append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" applyAlignment=\"1\">")
        append("<alignment horizontal=\"left\" vertical=\"center\"/></xf>")
        append("</cellXfs></styleSheet>")
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
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

    private fun formatNumber(value: Double): String =
        "%.2f".format(java.util.Locale.US, value)

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
}
