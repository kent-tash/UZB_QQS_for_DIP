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
import com.example.uzb_qqs_for_dip.export.ExportPaths
import com.example.uzb_qqs_for_dip.export.PdfPrint
import com.example.uzb_qqs_for_dip.export.PdfReportGenerator
import com.example.uzb_qqs_for_dip.export.ReceiptImageExporter
import com.example.uzb_qqs_for_dip.export.ReportParams
import com.example.uzb_qqs_for_dip.export.XlsxExporter
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.UriFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ReportEvent {
    data class Open(val intent: Intent) : ReportEvent
    data class Share(val intent: Intent) : ReportEvent
    data class Print(val file: File, val jobName: String) : ReportEvent
    data class Error(val message: String) : ReportEvent
    data class Saved(val message: String) : ReportEvent
    data class Deleted(val count: Int) : ReportEvent
}

class ReportViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as QqsApp).container
    private val appContext: Context = app.applicationContext

    val users: StateFlow<List<User>> = container.userRepository.users
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentUser: StateFlow<User?> = combine(
        container.sessionManager.currentUserId,
        users
    ) { id, list -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** РћР±С‰РёР№ СЃС‚РµР№С‚ РЅР°СЃС‚СЂРѕРµРє (С„РёР»СЊС‚СЂ + СЃРѕСЂС‚РёСЂРѕРІРєР°), РѕР±С‰РёР№ СЃ РІРєР»Р°РґРєРѕР№ В«Р§РµРєРёВ». */
    val settings: StateFlow<ReportSettings> = container.reportSettings.settings

    /**
     * Р§РµРєРё РІС‹Р±СЂР°РЅРЅРѕРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ Р·Р° РІС‹Р±СЂР°РЅРЅС‹Р№ РїРµСЂРёРѕРґ, РѕС‚СЃРѕСЂС‚РёСЂРѕРІР°РЅРЅС‹Рµ СЃРѕРіР»Р°СЃРЅРѕ
     * С‚РµРєСѓС‰РµРјСѓ [SortField]/[SortOrder]. РќР° РѕСЃРЅРѕРІРµ СЌС‚РѕРіРѕ СЃРїРёСЃРєР° СЃС‚СЂРѕРёС‚СЃСЏ PDF-РѕС‚С‡С‘С‚
     * Рё РїРѕРґСЃРІРµС‡РёРІР°РµС‚СЃСЏ в„– РІ РєР°СЂС‚РѕС‡РєРµ С‡РµРєР° РЅР° РІРєР»Р°РґРєРµ В«Р§РµРєРёВ».
     */
    val rows: StateFlow<List<ReceiptWithUser>> = combine(
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

    private val _event = MutableStateFlow<ReportEvent?>(null)
    val event: StateFlow<ReportEvent?> = _event.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveProgress = MutableStateFlow(0f)
    val saveProgress: StateFlow<Float> = _saveProgress.asStateFlow()

    private val _savePhase = MutableStateFlow("")
    val savePhase: StateFlow<String> = _savePhase.asStateFlow()

    /**
     * РРґРµРЅС‚РёС„РёРєР°С‚РѕСЂС‹ С‡РµРєРѕРІ, РѕС‚РјРµС‡РµРЅРЅС‹С… РїРѕР»СЊР·РѕРІР°С‚РµР»РµРј РґР»СЏ РїР°РєРµС‚РЅРѕРіРѕ СѓРґР°Р»РµРЅРёСЏ.
     * РњРЅРѕР¶РµСЃС‚РІРѕ Р¶РёРІС‘С‚ РјРµР¶РґСѓ РїРµСЂРµСЂРёСЃРѕРІРєР°РјРё СЌРєСЂР°РЅР° Рё СЃР±СЂР°СЃС‹РІР°РµС‚СЃСЏ, РєРѕРіРґР° РјРµРЅСЏСЋС‚СЃСЏ
     * РЅР°СЃС‚СЂРѕР№РєРё С„РёР»СЊС‚СЂР° (РґСЂСѓРіРѕР№ РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ / РїРµСЂРёРѕРґ / СЃРѕСЂС‚РёСЂРѕРІРєР°), С‡С‚РѕР±С‹ СЃР»СѓС‡Р°Р№РЅРѕ
     * РЅРµ СѓРґР°Р»СЏС‚СЊ Р·Р°РїРёСЃРё, РєРѕС‚РѕСЂС‹Рµ СЃРµР№С‡Р°СЃ РЅРµ РІРёРґРЅС‹.
     */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    init {
        // РџРѕРґСЃС‚Р°РІР»СЏРµРј С‚РµРєСѓС‰РµРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ РІ С„РёР»СЊС‚СЂ РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ.
        viewModelScope.launch {
            currentUser.collect { user ->
                if (settings.value.userId == null && user != null) {
                    container.reportSettings.setUserId(user.id)
                }
            }
        }
        // Р›СЋР±РѕРµ РёР·РјРµРЅРµРЅРёРµ С„РёР»СЊС‚СЂР°/СЃРѕСЂС‚РёСЂРѕРІРєРё/РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ вЂ” СЃРЅРёРјР°РµРј РІС‹РґРµР»РµРЅРёРµ.
        viewModelScope.launch {
            settings.collect { _selectedIds.value = emptySet() }
        }
        // РќР° РІСЃСЏРєРёР№ СЃР»СѓС‡Р°Р№ С‡РёСЃС‚РёРј РІС‹РґРµР»РµРЅРёРµ, РµСЃР»Рё РІС‹Р±СЂР°РЅРЅС‹Р№ С‡РµРє Р±РѕР»СЊС€Рµ РЅРµ РІРёРґРµРЅ
        // (РЅР°РїСЂРёРјРµСЂ, СѓРґР°Р»С‘РЅ РІ РґСЂСѓРіРѕР№ РІРєР»Р°РґРєРµ РёР»Рё РєР°СЃРєР°РґРЅРѕ РїСЂРё СѓРґР°Р»РµРЅРёРё РїСЂРѕС„РёР»СЏ).
        viewModelScope.launch {
            rows.collect { visible ->
                val visibleIds = visible.mapTo(HashSet()) { it.receipt.id }
                val current = _selectedIds.value
                val pruned = current.filterTo(HashSet()) { it in visibleIds }
                if (pruned.size != current.size) _selectedIds.value = pruned
            }
        }
    }

    fun toggleSelection(id: Long) {
        val cur = _selectedIds.value
        _selectedIds.value = if (id in cur) cur - id else cur + id
    }

    fun selectAllVisible() {
        _selectedIds.value = rows.value.mapTo(HashSet()) { it.receipt.id }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * РЈРґР°Р»СЏРµС‚ РІСЃРµ РѕС‚РјРµС‡РµРЅРЅС‹Рµ С‡РµРєРё РѕРґРЅРёРј Р±Р°С‚С‡РµРј. РљР°СЂС‚РёРЅРєРё-РєР°СЂС‚РѕС‡РєРё СѓРґР°Р»СЏРµРј С‚РѕР¶Рµ,
     * С‡С‚РѕР±С‹ РѕРЅРё РЅРµ РѕСЃС‚Р°РІР°Р»РёСЃСЊ РЅР° РґРёСЃРєРµ В«РѕСЃРёСЂРѕС‚РµРІС€РёРјРёВ».
     */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val removed = container.receiptRepository.deleteAll(ids)
                withContext(Dispatchers.IO) {
                    ids.forEach { id ->
                        ReceiptImageExporter.fileForReceipt(appContext, id).delete()
                    }
                }
                _selectedIds.value = emptySet()
                _event.value = ReportEvent.Deleted(removed)
            } catch (e: Throwable) {
                _event.value = ReportEvent.Error("РќРµ СѓРґР°Р»РѕСЃСЊ СѓРґР°Р»РёС‚СЊ С‡РµРєРё: ${e.message}")
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
        // РЎС‚Р°Р±РёР»СЊРЅС‹Р№ РІС‚РѕСЂРёС‡РЅС‹Р№ РєР»СЋС‡ вЂ” id, С‡С‚РѕР±С‹ РїРѕСЂСЏРґРѕРє РЅРµ В«РїСЂС‹РіР°Р»В» РїСЂРё СЂР°РІРЅС‹С… Р·РЅР°С‡РµРЅРёСЏС….
        val tie: Comparator<ReceiptWithUser> = compareBy { it.receipt.id }
        val sorted = rows.sortedWith(cmp.then(tie))
        return if (order == SortOrder.DESC) sorted.reversed() else sorted
    }

    fun setUserFilter(userId: Long?) = container.reportSettings.setUserId(userId)
    fun setFrom(ts: Long) = container.reportSettings.setFrom(ts)
    fun setTo(ts: Long) = container.reportSettings.setTo(ts)
    fun setQuarter(q: Quarter) = container.reportSettings.setQuarter(q)
    fun setYear(year: Int) = container.reportSettings.setYear(year)
    fun toggleSort(field: SortField) = container.reportSettings.toggleSort(field)

    fun consumeEvent() {
        _event.value = null
    }

    fun previewPdf(context: Context) = generate(context, openSystemPrint = false)
    fun printPdf(context: Context) = generate(context, openSystemPrint = true)

    fun suggestedReportPdfName(): String? = suggestedReportBaseName()?.let { "$it.pdf" }

    fun suggestedReportXlsxName(): String? = suggestedReportBaseName()?.let { "$it.xlsx" }

    private fun suggestedReportBaseName(): String? {
        val s = settings.value
        val user = users.value.firstOrNull { it.id == s.userId } ?: currentUser.value ?: return null
        val safeName = user.fullName.replace(Regex("[^A-Za-zРђ-РЇР°-СЏ0-9_-]"), "_").take(40)
        return "report_${safeName}_${System.currentTimeMillis()}"
    }

    fun savePdfToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            saveReportFile(context, uri, asPdf = true)
        }
    }

    fun saveXlsxToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            saveReportFile(context, uri, asPdf = false)
        }
    }

    fun sharePdf(context: Context) = shareReport(context, asPdf = true)

    fun shareXlsx(context: Context) = shareReport(context, asPdf = false)

    private fun shareReport(context: Context, asPdf: Boolean) {
        viewModelScope.launch {
            container.receiptRepository.refresh()
            val s = settings.value
            val user = users.value.firstOrNull { it.id == s.userId } ?: currentUser.value
            if (user == null) {
                _event.value = ReportEvent.Error("РќРµС‚ РІС‹Р±СЂР°РЅРЅРѕРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ")
                return@launch
            }
            if (rows.value.isEmpty()) {
                _event.value = ReportEvent.Error("РќРµС‚ РґР°РЅРЅС‹С… РґР»СЏ СЌРєСЃРїРѕСЂС‚Р°")
                return@launch
            }
            _isSaving.value = true
            _saveProgress.value = 0f
            try {
                syncWithOfd { current, total ->
                    _savePhase.value = "РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ... РћР±СЂР°Р±РѕС‚Р°РЅРѕ $current РёР· $total"
                    _saveProgress.value = 0.5f * (current.toFloat() / total.toFloat())
                }
                _savePhase.value = "Р¤РѕСЂРјРёСЂРѕРІР°РЅРёРµ С„Р°Р№Р»Р°..."

                val safeName = user.fullName.replace(Regex("[^A-Za-zРђ-РЇР°-СЏ0-9_-]"), "_").take(40)
                val file = if (asPdf) {
                    val params = ReportParams(
                        user = user,
                        periodStart = s.from,
                        periodEnd = s.to,
                        rows = rows.value,
                        quarterLabel = if (s.quarter == Quarter.Custom) null
                        else "${s.quarter.label} ${s.year} Рі."
                    )
                    PdfReportGenerator.generate(
                        context, params, "report_${safeName}_${System.currentTimeMillis()}.pdf"
                    )
                } else {
                    val params = ReportParams(
                        user = user,
                        periodStart = s.from,
                        periodEnd = s.to,
                        rows = rows.value,
                        quarterLabel = if (s.quarter == Quarter.Custom) null
                        else "${s.quarter.label} ${s.year} Рі."
                    )
                    XlsxExporter.exportReport(
                        context, params, "report_${safeName}_${System.currentTimeMillis()}.xlsx"
                    )
                }
                val mime = if (asPdf) "application/pdf"
                else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                val uri = ExportPaths.shareUriFor(context, file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _event.value = ReportEvent.Share(
                    Intent.createChooser(intent, if (asPdf) "РџРѕРґРµР»РёС‚СЊСЃСЏ PDF" else "РџРѕРґРµР»РёС‚СЊСЃСЏ Excel")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Throwable) {
                _event.value = ReportEvent.Error("РћС€РёР±РєР° СЌРєСЃРїРѕСЂС‚Р°: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
                _savePhase.value = ""
            }
        }
    }

    private suspend fun syncWithOfd(onProgress: (Int, Int) -> Unit) {
        val total = 10
        for (i in 1..total) {
            kotlinx.coroutines.delay(100) // stub delay
            onProgress(i, total)
        }
    }

    private suspend fun saveReportFile(context: Context, uri: Uri, asPdf: Boolean) {
        container.receiptRepository.refresh()
        val s = settings.value
        val user = users.value.firstOrNull { it.id == s.userId } ?: currentUser.value
        if (user == null) {
            _event.value = ReportEvent.Error("РќРµС‚ РІС‹Р±СЂР°РЅРЅРѕРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ")
            return
        }
        _isSaving.value = true
        try {
            // Р¤Р°Р·Р° 1: РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ СЃ Р±Р°Р·РѕР№ (РћР¤Р”)
            _saveProgress.value = 0f
            syncWithOfd { current, total ->
                _savePhase.value = "РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ... РћР±РЅРѕРІР»РµРЅРѕ $current РёР· $total"
                _saveProgress.value = 0.5f * (current.toFloat() / total.toFloat())
            }

            // Р¤Р°Р·Р° 2: Р¤РѕСЂРјРёСЂРѕРІР°РЅРёРµ РѕС‚С‡С‘С‚Р°
            _savePhase.value = "Р¤РѕСЂРјРёСЂРѕРІР°РЅРёРµ ${if (asPdf) "PDF" else "Excel"}..."
            val safeName = user.fullName.replace(Regex("[^A-Za-zРђ-РЇР°-СЏ0-9_-]"), "_").take(40)
            
            val file = if (asPdf) {
                val params = ReportParams(
                    user = user,
                    periodStart = s.from,
                    periodEnd = s.to,
                    rows = rows.value,
                    quarterLabel = if (s.quarter == Quarter.Custom) null
                    else "${s.quarter.label} ${s.year} Рі."
                )
                PdfReportGenerator.generate(
                    context, params, "report_${safeName}_${System.currentTimeMillis()}.pdf"
                )
            } else {
                val params = ReportParams(
                    user = user,
                    periodStart = s.from,
                    periodEnd = s.to,
                    rows = rows.value,
                    quarterLabel = if (s.quarter == Quarter.Custom) null
                    else "${s.quarter.label} ${s.year} Рі."
                )
                XlsxExporter.exportReport(
                    context, params, "report_${safeName}_${System.currentTimeMillis()}.xlsx"
                )
            }
            _saveProgress.value = 0.75f
            _savePhase.value = "РЎРѕС…СЂР°РЅРµРЅРёРµ С„Р°Р№Р»Р°..."
            UriFileWriter.copyFileToUri(context, file, uri)
            _saveProgress.value = 1f
            _savePhase.value = "Р“РѕС‚РѕРІРѕ!"
            _event.value = ReportEvent.Saved(
                if (asPdf) "PDF-РѕС‚С‡С‘С‚ СѓСЃРїРµС€РЅРѕ СЃРѕС…СЂР°РЅС‘РЅ" else "Excel-РѕС‚С‡С‘С‚ СѓСЃРїРµС€РЅРѕ СЃРѕС…СЂР°РЅС‘РЅ"
            )
        } catch (e: Throwable) {
            _event.value = ReportEvent.Error(
                "РћС€РёР±РєР° СЃРѕС…СЂР°РЅРµРЅРёСЏ ${if (asPdf) "PDF" else "Excel"}: ${e.message}"
            )
        } finally {
            _isSaving.value = false
            _saveProgress.value = 0f
            _savePhase.value = ""
        }
    }

    private fun generate(context: Context, openSystemPrint: Boolean) {
        viewModelScope.launch {
            container.receiptRepository.refresh()
            val s = settings.value
            val user = users.value.firstOrNull { it.id == s.userId }
                ?: currentUser.value
            if (user == null) {
                _event.value = ReportEvent.Error("РќРµС‚ РІС‹Р±СЂР°РЅРЅРѕРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ")
                return@launch
            }
            _isSaving.value = true
            _saveProgress.value = 0f
            try {
                syncWithOfd { current, total ->
                    _savePhase.value = "РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ... РћР±СЂР°Р±РѕС‚Р°РЅРѕ $current РёР· $total"
                    _saveProgress.value = 0.5f * (current.toFloat() / total.toFloat())
                }
                _savePhase.value = "Р¤РѕСЂРјРёСЂРѕРІР°РЅРёРµ PDF..."

                val params = ReportParams(
                    user = user,
                    periodStart = s.from,
                    periodEnd = s.to,
                    rows = rows.value,
                    quarterLabel = if (s.quarter == Quarter.Custom) null
                        else "${s.quarter.label} ${s.year} Рі."
                )
                val safeName = user.fullName.replace(Regex("[^A-Za-zРђ-РЇР°-СЏ0-9_-]"), "_").take(40)
                val fileName = "report_${safeName}_${System.currentTimeMillis()}.pdf"
                val file = PdfReportGenerator.generate(context, params, fileName)
                if (openSystemPrint) {
                    _event.value = ReportEvent.Print(
                        file = file,
                        jobName = "QQS РѕС‚С‡С‘С‚ ${user.fullName}"
                    )
                } else {
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, "РџСЂРѕСЃРјРѕС‚СЂ РѕС‚С‡С‘С‚Р°")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    _event.value = ReportEvent.Open(chooser)
                }
                _saveProgress.value = 1f
            } catch (e: Throwable) {
                _event.value = ReportEvent.Error("РћС€РёР±РєР° С„РѕСЂРјРёСЂРѕРІР°РЅРёСЏ PDF: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
                _savePhase.value = ""
            }
        }
    }

    /** РЈРґРѕР±РЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ UI: РІС‹Р·РІР°С‚СЊ СЃРёСЃС‚РµРјРЅС‹Р№ РґРёР°Р»РѕРі РїРµС‡Р°С‚Рё РїРѕ СѓР¶Рµ СЃС„РѕСЂРјРёСЂРѕРІР°РЅРЅРѕРјСѓ С„Р°Р№Р»Сѓ. */
    fun launchPrint(context: Context, file: File, jobName: String) {
        PdfPrint.print(context, file, jobName)
    }
}

