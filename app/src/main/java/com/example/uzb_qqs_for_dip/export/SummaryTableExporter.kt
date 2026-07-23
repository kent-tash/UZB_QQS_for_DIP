package com.example.uzb_qqs_for_dip.export

import android.content.Context
import com.example.uzb_qqs_for_dip.data.repository.EmployeeSummary
import com.example.uzb_qqs_for_dip.data.settings.AuditorSettings
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Экспорт сводных отчётов аудитора в XLSX/CSV.
 * XLSX повторяет форму PDF ([SummaryPdfGenerator] / [OrgReportPdfGenerator]):
 * шапка, колонки № | Фамилия И.О. | НДС, ИТОГО, блок подписей.
 */
object SummaryTableExporter {

    private data class Cell(
        val text: String? = null,
        val number: Double? = null,
        /** 0 default, 1 header, 2 totals text, 3 totals num, 4 data num, 5 title, 6 signature */
        val style: Int = 0
    )

    /** Сводная таблица — как [SummaryPdfGenerator]. */
    suspend fun exportXlsx(
        context: Context,
        rows: List<EmployeeSummary>,
        quarter: Quarter,
        year: Int,
        auditorSettings: AuditorSettings,
        fileName: String = "audit_${quarter.name}_${year}_summary.xlsx"
    ): File = withContext(Dispatchers.IO) {
        val qLabel = "${SummaryPdfGenerator.quarterLabel(quarter)} $year г."
        val orgName = auditorSettings.organizationName.ifBlank { "_______________" }
        val sorted = rows.sortedBy {
            SummaryPdfGenerator.toSurnameInitials(
                it.initialsSurname.ifBlank { it.fullName }
            ).lowercase()
        }

        val sheet = buildList {
            add(
                listOf(
                    Cell(
                        text = "Список сотрудников $orgName в Узбекистане, предъявляющих к возмещению уплаченный НДС",
                        style = 5
                    )
                )
            )
            add(listOf(Cell(text = qLabel, style = 5)))
            add(listOf(Cell(text = "")))

            add(
                listOf(
                    Cell(text = "№", style = 1),
                    Cell(text = "Фамилия И.О.", style = 1),
                    Cell(text = qLabel, style = 1)
                )
            )

            sorted.forEachIndexed { idx, s ->
                add(
                    listOf(
                        Cell(number = (idx + 1).toDouble()),
                        Cell(
                            text = SummaryPdfGenerator.toSurnameInitials(
                                s.initialsSurname.ifBlank { s.fullName }
                            )
                        ),
                        Cell(number = s.vatTiyin / 100.0, style = 4)
                    )
                )
            }

            add(
                listOf(
                    Cell(text = "ИТОГО:", style = 2),
                    Cell(text = "", style = 2),
                    Cell(number = rows.sumOf { it.vatTiyin } / 100.0, style = 3)
                )
            )

            addAll(signatureRows(auditorSettings))
        }

        writeWorkbook(
            context = context,
            fileName = fileName,
            sheetName = "Аудит ${quarter.name} $year",
            sheet = sheet,
            colWidths = listOf(8, 36, 22),
            mergeTitleRows = 2
        )
    }

    /**
     * Отчёт «Возврат НДС по личным расходам» — как [OrgReportPdfGenerator].
     */
    suspend fun exportOrgXlsx(
        context: Context,
        rows: List<EmployeeSummary>,
        quarter: Quarter,
        year: Int,
        auditorSettings: AuditorSettings,
        fileName: String = "audit_org_${quarter.name}_$year.xlsx"
    ): File = withContext(Dispatchers.IO) {
        val qLabel = "${SummaryPdfGenerator.quarterLabel(quarter)} $year г."
        val blankKey = "\uFFFE"
        val grouped = rows
            .groupBy { it.organization.ifBlank { blankKey } }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<EmployeeSummary>>> {
                    if (it.key == blankKey) Int.MIN_VALUE else it.value.size
                }
            )
            .associate { (key, list) ->
                key to list.sortedBy {
                    SummaryPdfGenerator.toSurnameInitials(
                        it.initialsSurname.ifBlank { it.fullName }
                    ).lowercase()
                }
            }

        val sheet = buildList {
            add(listOf(Cell(text = "Возврат НДС по личным расходам за $qLabel", style = 5)))
            add(listOf(Cell(text = "")))

            add(
                listOf(
                    Cell(text = "№", style = 1),
                    Cell(text = "Фамилия И.О.", style = 1),
                    Cell(text = qLabel, style = 1)
                )
            )

            var num = 1
            grouped.forEach { (orgKey, list) ->
                list.forEach { s ->
                    add(
                        listOf(
                            Cell(number = num.toDouble()),
                            Cell(
                                text = SummaryPdfGenerator.toSurnameInitials(
                                    s.initialsSurname.ifBlank { s.fullName }
                                )
                            ),
                            Cell(number = s.vatTiyin / 100.0, style = 4)
                        )
                    )
                    num++
                }
                val orgLabel = if (orgKey == blankKey) "ИТОГО:" else "ИТОГО $orgKey:"
                add(
                    listOf(
                        Cell(text = orgLabel, style = 2),
                        Cell(text = "", style = 2),
                        Cell(number = list.sumOf { it.vatTiyin } / 100.0, style = 3)
                    )
                )
            }

            add(
                listOf(
                    Cell(text = "ИТОГО:", style = 2),
                    Cell(text = "", style = 2),
                    Cell(number = rows.sumOf { it.vatTiyin } / 100.0, style = 3)
                )
            )

            addAll(signatureRows(auditorSettings))
        }

        writeWorkbook(
            context = context,
            fileName = fileName,
            sheetName = "Отчёт",
            sheet = sheet,
            colWidths = listOf(8, 40, 22),
            mergeTitleRows = 1
        )
    }

    private fun signatureRows(s: AuditorSettings): List<List<Cell>> {
        val dirTitle = s.directorTitle.ifBlank { "Руководитель организации" }
        val dirName = s.directorName.ifBlank { "_______________" }
        val accTitle = s.accountantTitle.ifBlank { "Главный бухгалтер организации" }
        val accName = s.accountantName.ifBlank { "_______________" }
        return listOf(
            listOf(Cell(text = "")),
            listOf(
                Cell(text = dirTitle, style = 6),
                Cell(text = "____________________", style = 6),
                Cell(text = dirName, style = 6)
            ),
            listOf(
                Cell(text = accTitle, style = 6),
                Cell(text = "____________________", style = 6),
                Cell(text = accName, style = 6)
            )
        )
    }

    private fun writeWorkbook(
        context: Context,
        fileName: String,
        sheetName: String,
        sheet: List<List<Cell>>,
        colWidths: List<Int>,
        mergeTitleRows: Int
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
            append("</cols><sheetData>")
            sheet.forEachIndexed { rowIdx, cells ->
                val rowNumber = rowIdx + 1
                append("<row r=\"$rowNumber\">")
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
            if (mergeTitleRows > 0) {
                append("<mergeCells count=\"$mergeTitleRows\">")
                repeat(mergeTitleRows) { i ->
                    val r = i + 1
                    append("<mergeCell ref=\"A$r:${colName(colCount - 1)}$r\"/>")
                }
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

    suspend fun exportCsv(
        context: Context,
        rows: List<EmployeeSummary>,
        quarter: String,
        year: Int,
        fileName: String = "audit_${quarter}_${year}_summary.csv"
    ): File = withContext(Dispatchers.IO) {
        val file = File(ExportPaths.exportsDir(context), fileName)
        FileOutputStream(file).use { fos ->
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { w ->
                w.append("ФИО;Должность;Итого чеков;Общая сумма;Сумма НДС;Статус\r\n")
                rows.forEach { s ->
                    w.append(escape(s.fullName)).append(';')
                    w.append(escape(s.position)).append(';')
                    w.append(s.receiptCount.toString()).append(';')
                    w.append(escape(MoneyFormat.fromTiyin(s.totalTiyin))).append(';')
                    w.append(escape(MoneyFormat.fromTiyin(s.vatTiyin))).append(';')
                    w.append(escape(s.declaration?.status?.name ?: "—")).append("\r\n")
                }
                val totalRow = "Итого;;" +
                    rows.sumOf { it.receiptCount }.toString() + ";" +
                    escape(MoneyFormat.fromTiyin(rows.sumOf { it.totalTiyin })) + ";" +
                    escape(MoneyFormat.fromTiyin(rows.sumOf { it.vatTiyin })) + ";\r\n"
                w.append(totalRow)
            }
        }
        file
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

    private fun formatNumber(value: Double) = "%.2f".format(java.util.Locale.US, value)

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

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        val safe = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$safe\"" else safe
    }
}
