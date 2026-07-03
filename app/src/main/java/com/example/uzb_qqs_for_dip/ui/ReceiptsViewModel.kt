package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.data.settings.SortField
import com.example.uzb_qqs_for_dip.data.settings.SortOrder
import com.example.uzb_qqs_for_dip.export.CsvExporter
import com.example.uzb_qqs_for_dip.export.PdfPrint
import com.example.uzb_qqs_for_dip.export.ReceiptImageExporter
import com.example.uzb_qqs_for_dip.export.ReceiptsSheetPdfGenerator
import com.example.uzb_qqs_for_dip.export.XlsxExporter
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.UriFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ExportEvent {
    data class Open(val intent: Intent) : ExportEvent
    data class Share(val intent: Intent) : ExportEvent
    data class Print(val file: File, val jobName: String) : ExportEvent
    data class Error(val message: String) : ExportEvent
    data class Saved(val message: String) : ExportEvent
}

/**
 * ViewModel вкладки «Чеки». Сама вкладка больше не управляет ни фильтром, ни
 * сортировкой: и тот, и другой берёт из общего [com.example.uzb_qqs_for_dip.data.settings.ReportSettingsHolder].
 * Так нумерация чеков и набор записей в точности совпадают с тем, что показано
 * в таблице на вкладке «Отчёт» и попадает в PDF.
 */
class ReceiptsViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as QqsApp).container
    private val appContext: Context = app.applicationContext

    val settings: StateFlow<ReportSettings> = container.reportSettings.settings

    val users: StateFlow<List<User>> = container.userRepository.users
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentUser: StateFlow<User?> = combine(
        container.sessionManager.currentUserId,
        users
    ) { id, list -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Видимые на вкладке чеки — отфильтрованные и отсортированные точно так же,
     * как на вкладке «Отчёт». Порядковый № = индекс + 1.
     */
    val receipts: StateFlow<List<ReceiptWithUser>> = combine(
        container.receiptRepository.receipts,
        settings,
        currentUser
    ) { all, s, currUser ->
        val userId = s.userId ?: currUser?.id
        val start = DateFormat.startOfDay(s.from)
        val end = DateFormat.endOfDay(s.to)
        val filtered = all.filter {
            it.receipt.userId == userId && it.receipt.purchasedAt in start..end
        }
        sortRows(filtered, s.sortField, s.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _exportEvents = MutableStateFlow<ExportEvent?>(null)
    val exportEvents: StateFlow<ExportEvent?> = _exportEvents.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveProgress = MutableStateFlow(0f)
    val saveProgress: StateFlow<Float> = _saveProgress.asStateFlow()

    init {
        // Любое изменение видимого порядка — перегенерируем PNG чеков на диске,
        // чтобы № в чёрном квадрате на каждой картинке совпадал с номером в таблице
        // (и далее в PDF/печати).
        viewModelScope.launch {
            receipts
                .distinctUntilChanged { a, b ->
                    a.size == b.size && a.zip(b).all { (x, y) -> x.receipt.id == y.receipt.id }
                }
                .collect { ordered ->
                    runCatching {
                        ReceiptImageExporter.regenerateAll(appContext, ordered.map { it.receipt })
                    }
                }
        }
    }

    private fun sortRows(
        rows: List<ReceiptWithUser>,
        field: SortField,
        order: SortOrder
    ): List<ReceiptWithUser> {
        val cmp: Comparator<ReceiptWithUser> = when (field) {
            SortField.DATE -> compareBy { it.receipt.purchasedAt }
            SortField.SELLER -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.receipt.sellerName }
            SortField.TOTAL -> compareBy { it.receipt.totalAmountTiyin }
            SortField.VAT -> compareBy { it.receipt.vatAmountTiyin }
        }
        val tie: Comparator<ReceiptWithUser> = compareBy { it.receipt.id }
        val sorted = rows.sortedWith(cmp.then(tie))
        return if (order == SortOrder.DESC) sorted.reversed() else sorted
    }

    fun consumeExportEvent() { _exportEvents.value = null }

    fun exportCsv(context: Context) {
        export(context) { rows -> CsvExporter.export(context, rows) to "text/csv" }
    }

    fun exportXlsx(context: Context) {
        export(context) { rows ->
            XlsxExporter.export(context, rows) to
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
    }

    /** Делится PNG одного чека (с актуальным № в чёрном квадрате). */
    fun shareReceiptImage(context: Context, item: ReceiptWithUser) {
        viewModelScope.launch {
            try {
                val ordinal = receipts.value.indexOfFirst { it.receipt.id == item.receipt.id } + 1
                if (ordinal <= 0) {
                    _exportEvents.value = ExportEvent.Error("Чек больше не виден в таблице")
                    return@launch
                }
                val file = ReceiptImageExporter.saveSingle(context, item.receipt, ordinal)
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _exportEvents.value = ExportEvent.Share(
                    Intent.createChooser(intent, "Поделиться чеком №$ordinal")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось сохранить картинку: ${e.message}")
            }
        }
    }

    /** Печать всех чеков в текущей сортировке (6 на лист, в порядке № таблицы). */
    fun printAllAsSheets(context: Context) {
        viewModelScope.launch {
            try {
                val file = generateReceiptsSheetPdf(context)
                _exportEvents.value = ExportEvent.Print(file, "QQS чеки (${receipts.value.size} шт.)")
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось сформировать PDF: ${e.message}")
            }
        }
    }

    /** Предпросмотр PDF с чеками для печати (6 на лист). */
    fun previewReceiptsPdf(context: Context) {
        viewModelScope.launch {
            try {
                val file = generateReceiptsSheetPdf(context)
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _exportEvents.value = ExportEvent.Open(
                    Intent.createChooser(intent, "Просмотр чеков")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось открыть предпросмотр: ${e.message}")
            }
        }
    }

    private suspend fun generateReceiptsSheetPdf(context: Context): File {
        val rows = receipts.value
        if (rows.isEmpty()) error("Нет чеков для печати")
        val s = settings.value
        val periodLabel = if (s.quarter == Quarter.Custom) {
            "${DateFormat.formatDate(s.from)} — ${DateFormat.formatDate(s.to)}"
        } else {
            "${s.quarter.label} ${s.year} г."
        }
        val first = rows.first()
        val headerRight = "${first.userPosition} ${first.userFullName}".trim()
        return ReceiptsSheetPdfGenerator.generate(
            context = context,
            rowsInOrder = rows,
            headerRightText = headerRight,
            periodLabel = periodLabel
        )
    }

    /** Запускает системный диалог печати по уже сформированному PDF. */
    fun launchPrint(context: Context, file: File, jobName: String) {
        PdfPrint.print(context, file, jobName)
    }

    fun suggestedReceiptsPdfName(): String {
        return "receipts_${System.currentTimeMillis()}.pdf"
    }

    fun saveReceiptsPdfToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val rows = receipts.value
            if (rows.isEmpty()) {
                _exportEvents.value = ExportEvent.Error("Нет чеков для сохранения")
                return@launch
            }
            _isSaving.value = true
            _saveProgress.value = 0.05f
            try {
                _saveProgress.value = 0.15f
                val file = generateReceiptsSheetPdf(context)
                _saveProgress.value = 0.75f
                UriFileWriter.copyFileToUri(context, file, uri)
                _saveProgress.value = 1f
                _exportEvents.value = ExportEvent.Saved("PDF с чеками успешно сохранён")
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Ошибка сохранения PDF: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
            }
        }
    }

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    /** Прогресс обновления данных с сайта в диапазоне 0f..1f (для заполнения кнопки). */
    private val _updateProgress = MutableStateFlow(0f)
    val updateProgress: StateFlow<Float> = _updateProgress.asStateFlow()

    fun updateVisibleReceiptsFromSite() {
        val list = receipts.value
        if (list.isEmpty()) return
        
        viewModelScope.launch {
            _isUpdating.value = true
            _updateProgress.value = 0f
            val total = list.size
            try {
                list.forEachIndexed { index, item ->
                    val r = item.receipt
                    val result = container.receiptParser.fetchAndParse(r.qrUrl)
                    result.onSuccess { parsed ->
                        val updated = r.copy(
                            purchasedAt = parsed.purchasedAt ?: r.purchasedAt,
                            sellerName = parsed.sellerName ?: r.sellerName,
                            totalAmountTiyin = parsed.totalAmountTiyin ?: r.totalAmountTiyin,
                            vatAmountTiyin = parsed.vatAmountTiyin ?: r.vatAmountTiyin,
                            paymentType = parsed.paymentType,
                            fiscalSign = parsed.fiscalSign ?: r.fiscalSign,
                            address = parsed.address ?: r.address,
                            tin = parsed.tin ?: r.tin,
                            terminalId = parsed.terminalId ?: r.terminalId,
                            receiptNumber = parsed.receiptNumber ?: r.receiptNumber,
                            nkmName = parsed.nkmName ?: r.nkmName,
                            sn = parsed.sn ?: r.sn,
                            rawText = parsed.rawSnippet ?: r.rawText
                        )
                        container.receiptRepository.update(updated)
                    }
                    _updateProgress.value = (index + 1).toFloat() / total
                }
            } finally {
                _isUpdating.value = false
                _updateProgress.value = 0f
                container.receiptRepository.refresh()
            }
        }
    }

    fun deleteReceipt(id: Long) {
        viewModelScope.launch {
            container.receiptRepository.delete(id)
            // Файл картинки самоудалится при ближайшей перегенерации, инициированной flow.
            withContext(Dispatchers.IO) {
                ReceiptImageExporter.fileForReceipt(appContext, id).delete()
            }
        }
    }

    private fun export(
        context: Context,
        block: suspend (List<ReceiptWithUser>) -> Pair<File, String>
    ) {
        viewModelScope.launch {
            try {
                val rows = receipts.value
                if (rows.isEmpty()) {
                    _exportEvents.value = ExportEvent.Error("Нет данных для экспорта")
                    return@launch
                }
                val (file, mime) = block(rows)
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _exportEvents.value = ExportEvent.Share(
                    Intent.createChooser(intent, "Поделиться файлом")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Ошибка экспорта: ${e.message}")
            }
        }
    }
}
