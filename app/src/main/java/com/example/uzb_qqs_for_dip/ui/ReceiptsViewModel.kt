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
 * ViewModel РІРєР»Р°РґРєРё В«Р§РµРєРёВ». РЎР°РјР° РІРєР»Р°РґРєР° Р±РѕР»СЊС€Рµ РЅРµ СѓРїСЂР°РІР»СЏРµС‚ РЅРё С„РёР»СЊС‚СЂРѕРј, РЅРё
 * СЃРѕСЂС‚РёСЂРѕРІРєРѕР№: Рё С‚РѕС‚, Рё РґСЂСѓРіРѕР№ Р±РµСЂС‘С‚ РёР· РѕР±С‰РµРіРѕ [com.example.uzb_qqs_for_dip.data.settings.ReportSettingsHolder].
 * РўР°Рє РЅСѓРјРµСЂР°С†РёСЏ С‡РµРєРѕРІ Рё РЅР°Р±РѕСЂ Р·Р°РїРёСЃРµР№ РІ С‚РѕС‡РЅРѕСЃС‚Рё СЃРѕРІРїР°РґР°СЋС‚ СЃ С‚РµРј, С‡С‚Рѕ РїРѕРєР°Р·Р°РЅРѕ
 * РІ С‚Р°Р±Р»РёС†Рµ РЅР° РІРєР»Р°РґРєРµ В«РћС‚С‡С‘С‚В» Рё РїРѕРїР°РґР°РµС‚ РІ PDF.
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
     * Р’РёРґРёРјС‹Рµ РЅР° РІРєР»Р°РґРєРµ С‡РµРєРё вЂ” РѕС‚С„РёР»СЊС‚СЂРѕРІР°РЅРЅС‹Рµ Рё РѕС‚СЃРѕСЂС‚РёСЂРѕРІР°РЅРЅС‹Рµ С‚РѕС‡РЅРѕ С‚Р°Рє Р¶Рµ,
     * РєР°Рє РЅР° РІРєР»Р°РґРєРµ В«РћС‚С‡С‘С‚В». РџРѕСЂСЏРґРєРѕРІС‹Р№ в„– = РёРЅРґРµРєСЃ + 1.
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
        // Р›СЋР±РѕРµ РёР·РјРµРЅРµРЅРёРµ РІРёРґРёРјРѕРіРѕ РїРѕСЂСЏРґРєР° вЂ” РїРµСЂРµРіРµРЅРµСЂРёСЂСѓРµРј PNG С‡РµРєРѕРІ РЅР° РґРёСЃРєРµ,
        // С‡С‚РѕР±С‹ в„– РІ С‡С‘СЂРЅРѕРј РєРІР°РґСЂР°С‚Рµ РЅР° РєР°Р¶РґРѕР№ РєР°СЂС‚РёРЅРєРµ СЃРѕРІРїР°РґР°Р» СЃ РЅРѕРјРµСЂРѕРј РІ С‚Р°Р±Р»РёС†Рµ
        // (Рё РґР°Р»РµРµ РІ PDF/РїРµС‡Р°С‚Рё).
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
            SortField.CREATED -> compareBy { it.receipt.createdAt }
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

    /** Р”РµР»РёС‚СЃСЏ PNG РѕРґРЅРѕРіРѕ С‡РµРєР° (СЃ Р°РєС‚СѓР°Р»СЊРЅС‹Рј в„– РІ С‡С‘СЂРЅРѕРј РєРІР°РґСЂР°С‚Рµ). */
    fun shareReceiptImage(context: Context, item: ReceiptWithUser) {
        viewModelScope.launch {
            container.receiptRepository.refresh()
            _isSaving.value = true
            try {
                val ordinal = receipts.value.indexOfFirst { it.receipt.id == item.receipt.id } + 1
                if (ordinal <= 0) {
                    _exportEvents.value = ExportEvent.Error("Р§РµРє Р±РѕР»СЊС€Рµ РЅРµ РІРёРґРµРЅ РІ С‚Р°Р±Р»РёС†Рµ")
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
                    Intent.createChooser(intent, "РџРѕРґРµР»РёС‚СЊСЃСЏ С‡РµРєРѕРј в„–$ordinal")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("РќРµ СѓРґР°Р»РѕСЃСЊ СЃРѕС…СЂР°РЅРёС‚СЊ РєР°СЂС‚РёРЅРєСѓ: ${e.message}")
            } finally {
                  _isSaving.value = false
                  _saveProgress.value = 0f
              }
        }
    }

    /** РџРµС‡Р°С‚СЊ РІСЃРµС… С‡РµРєРѕРІ РІ С‚РµРєСѓС‰РµР№ СЃРѕСЂС‚РёСЂРѕРІРєРµ (6 РЅР° Р»РёСЃС‚, РІ РїРѕСЂСЏРґРєРµ в„– С‚Р°Р±Р»РёС†С‹). */
    fun printAllAsSheets(context: Context) {
        viewModelScope.launch {
            container.receiptRepository.refresh()
            _isSaving.value = true
            try {
                val file = generateReceiptsSheetPdf(context)
                _exportEvents.value = ExportEvent.Print(file, "QQS С‡РµРєРё (${receipts.value.size} С€С‚.)")
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("РќРµ СѓРґР°Р»РѕСЃСЊ СЃС„РѕСЂРјРёСЂРѕРІР°С‚СЊ PDF: ${e.message}")
            } finally {
                  _isSaving.value = false
                  _saveProgress.value = 0f
              }
        }
    }

    /** РџСЂРµРґРїСЂРѕСЃРјРѕС‚СЂ PDF СЃ С‡РµРєР°РјРё РґР»СЏ РїРµС‡Р°С‚Рё (6 РЅР° Р»РёСЃС‚). */
    fun previewReceiptsPdf(context: Context) {
        viewModelScope.launch {
            container.receiptRepository.refresh()
            _isSaving.value = true
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
                    Intent.createChooser(intent, "РџСЂРѕСЃРјРѕС‚СЂ С‡РµРєРѕРІ")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("РќРµ СѓРґР°Р»РѕСЃСЊ РѕС‚РєСЂС‹С‚СЊ РїСЂРµРґРїСЂРѕСЃРјРѕС‚СЂ: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
            }
        }
    }

    private suspend fun generateReceiptsSheetPdf(context: Context): File {
        val rows = receipts.value
        if (rows.isEmpty()) error("РќРµС‚ С‡РµРєРѕРІ РґР»СЏ РїРµС‡Р°С‚Рё")
        val s = settings.value
        val periodLabel = if (s.quarter == Quarter.Custom) {
            "${DateFormat.formatDate(s.from)} вЂ” ${DateFormat.formatDate(s.to)}"
        } else {
            "${s.quarter.label} ${s.year} Рі."
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

    /** Р—Р°РїСѓСЃРєР°РµС‚ СЃРёСЃС‚РµРјРЅС‹Р№ РґРёР°Р»РѕРі РїРµС‡Р°С‚Рё РїРѕ СѓР¶Рµ СЃС„РѕСЂРјРёСЂРѕРІР°РЅРЅРѕРјСѓ PDF. */
    fun launchPrint(context: Context, file: File, jobName: String) {
        PdfPrint.print(context, file, jobName)
    }

    fun suggestedReceiptsPdfName(): String {
        return "receipts_${System.currentTimeMillis()}.pdf"
    }

    fun suggestedReceiptsXlsxName(): String {
        return "receipts_${System.currentTimeMillis()}.xlsx"
    }

    fun saveReceiptsPdfToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            saveReceiptsFile(context, uri, asPdf = true)
        }
    }

    fun saveReceiptsXlsxToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            saveReceiptsFile(context, uri, asPdf = false)
        }
    }

    fun shareReceiptsPdf(context: Context) {
        viewModelScope.launch {
            container.receiptRepository.refresh()
            _isSaving.value = true
            try {
                val file = generateReceiptsSheetPdf(context)
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _exportEvents.value = ExportEvent.Share(
                    Intent.createChooser(intent, "РџРѕРґРµР»РёС‚СЊСЃСЏ PDF СЃ С‡РµРєР°РјРё")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("РћС€РёР±РєР° СЌРєСЃРїРѕСЂС‚Р° PDF: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
            }
        }
    }

    private suspend fun saveReceiptsFile(context: Context, uri: Uri, asPdf: Boolean) {
        container.receiptRepository.refresh()
        val rows = receipts.value
        if (rows.isEmpty()) {
            _exportEvents.value = ExportEvent.Error("РќРµС‚ С‡РµРєРѕРІ РґР»СЏ СЃРѕС…СЂР°РЅРµРЅРёСЏ")
            return
        }
        _isSaving.value = true
        _saveProgress.value = 0.05f
        try {
            _saveProgress.value = 0.15f
            val file = if (asPdf) {
                generateReceiptsSheetPdf(context)
            } else {
                XlsxExporter.export(context, rows, suggestedReceiptsXlsxName())
            }
            _saveProgress.value = 0.75f
            UriFileWriter.copyFileToUri(context, file, uri)
            _saveProgress.value = 1f
            _exportEvents.value = ExportEvent.Saved(
                if (asPdf) "PDF СЃ С‡РµРєР°РјРё СѓСЃРїРµС€РЅРѕ СЃРѕС…СЂР°РЅС‘РЅ" else "Excel СЃ С‡РµРєР°РјРё СѓСЃРїРµС€РЅРѕ СЃРѕС…СЂР°РЅС‘РЅ"
            )
        } catch (e: Throwable) {
            _exportEvents.value = ExportEvent.Error(
                "РћС€РёР±РєР° СЃРѕС…СЂР°РЅРµРЅРёСЏ ${if (asPdf) "PDF" else "Excel"}: ${e.message}"
            )
        } finally {
            _isSaving.value = false
            _saveProgress.value = 0f
        }
    }

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    /** РџСЂРѕРіСЂРµСЃСЃ РѕР±РЅРѕРІР»РµРЅРёСЏ РґР°РЅРЅС‹С… СЃ СЃР°Р№С‚Р° РІ РґРёР°РїР°Р·РѕРЅРµ 0f..1f (РґР»СЏ Р·Р°РїРѕР»РЅРµРЅРёСЏ РєРЅРѕРїРєРё). */
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
            // Р¤Р°Р№Р» РєР°СЂС‚РёРЅРєРё СЃР°РјРѕСѓРґР°Р»РёС‚СЃСЏ РїСЂРё Р±Р»РёР¶Р°Р№С€РµР№ РїРµСЂРµРіРµРЅРµСЂР°С†РёРё, РёРЅРёС†РёРёСЂРѕРІР°РЅРЅРѕР№ flow.
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
            container.receiptRepository.refresh()
            _isSaving.value = true
            try {
                val rows = receipts.value
                if (rows.isEmpty()) {
                    _exportEvents.value = ExportEvent.Error("РќРµС‚ РґР°РЅРЅС‹С… РґР»СЏ СЌРєСЃРїРѕСЂС‚Р°")
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
                    Intent.createChooser(intent, "РџРѕРґРµР»РёС‚СЊСЃСЏ С„Р°Р№Р»РѕРј")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("РћС€РёР±РєР° СЌРєСЃРїРѕСЂС‚Р°: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
            }
        }
    }
}

