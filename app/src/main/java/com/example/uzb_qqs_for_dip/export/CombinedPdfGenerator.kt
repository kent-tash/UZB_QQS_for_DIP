package com.example.uzb_qqs_for_dip.export

import android.content.Context
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Генератор единого официального PDF-документа («Всё»):
 * 1. Часть 1: Сводный отчёт (страницы 1..N отчёта).
 * 2. С новой страницы: Реестр чеков (карточки по 6 на лист А4).
 *
 * КРИТИЧЕСКОЕ ТРЕБОВАНИЕ:
 * Нумерация страниц реестра чеков изолирована и начинается с 1-й страницы («стр. 1 из M»).
 * На каждой странице реестра также выводятся Должность, ФИО пользователя и штамп «Сформировано дд.мм.гггг чч:мм:сс».
 */
object CombinedPdfGenerator {

    fun calculateRegistryPagesCount(receiptsCount: Int, manualPhotosCount: Int = 0): Int =
        ReceiptsSheetPdfGenerator.calculatePagesCount(receiptsCount, manualPhotosCount)

    fun calculateReportPagesCount(params: ReportParams): Int =
        PdfReportGenerator.calculatePageCount(sanitizeParams(params), includeManualAttachments = false)

    fun calculateTotalPages(params: ReportParams, manualPhotosCount: Int = 0): Int {
        val sanitized = sanitizeParams(params)
        val reportPages = calculateReportPagesCount(sanitized)
        val registryPages = calculateRegistryPagesCount(sanitized.rows.size, manualPhotosCount)
        return reportPages + registryPages
    }

    /**
     * Возвращает начальный номер страницы в сквозном документе для реестра чеков (Часть 2).
     * Реестр чеков всегда начинается со следующей страницы после сводного отчёта.
     */
    fun calculateRegistryStartDocPage(params: ReportParams): Int {
        return calculateReportPagesCount(params) + 1
    }

    internal fun sanitizeParams(params: ReportParams): ReportParams =
        params.copy(rows = params.rows.filter { it.receipt.vatAmountTiyin > 0L })

    suspend fun generate(
        context: Context,
        params: ReportParams,
        periodLabel: String,
        fileName: String = "all_${params.user.id}_${System.currentTimeMillis()}.pdf"
    ): File = withContext(Dispatchers.IO) {
        val sanitizedParams = sanitizeParams(params)
        val file = File(ExportPaths.exportsDir(context), fileName)
        val doc = PdfDocument()
        try {
            // Часть 1: Сводный отчёт (страницы 1..N)
            // При объединении страницы с фото ручных чеков выводятся в самом конце реестра чеков
            val reportPagesCount = PdfReportGenerator.renderInto(
                doc = doc,
                params = sanitizedParams,
                includeManualAttachments = false
            )

            // Часть 2: Реестр карточек чеков (начиная со страницы reportPagesCount + 1 в doc)
            val headerRightText = "${params.user.position} ${params.user.fullName}".trim()
            ReceiptsSheetPdfGenerator.renderInto(
                doc = doc,
                rowsInOrder = sanitizedParams.rows,
                headerRightText = headerRightText,
                periodLabel = periodLabel,
                startDocPageNumber = reportPagesCount + 1,
                generatedAt = params.generatedAt
            )

            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        file
    }
}
