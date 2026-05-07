package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import android.content.Context
import android.content.Intent
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
import com.example.uzb_qqs_for_dip.export.PdfPrint
import com.example.uzb_qqs_for_dip.export.PdfReportGenerator
import com.example.uzb_qqs_for_dip.export.ReceiptImageExporter
import com.example.uzb_qqs_for_dip.export.ReportParams
import com.example.uzb_qqs_for_dip.util.DateFormat
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

    /** Общий стейт настроек (фильтр + сортировка), общий с вкладкой «Чеки». */
    val settings: StateFlow<ReportSettings> = container.reportSettings.settings

    /**
     * Чеки выбранного пользователя за выбранный период, отсортированные согласно
     * текущему [SortField]/[SortOrder]. На основе этого списка строится PDF-отчёт
     * и подсвечивается № в карточке чека на вкладке «Чеки».
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

    /**
     * Идентификаторы чеков, отмеченных пользователем для пакетного удаления.
     * Множество живёт между перерисовками экрана и сбрасывается, когда меняются
     * настройки фильтра (другой пользователь / период / сортировка), чтобы случайно
     * не удалять записи, которые сейчас не видны.
     */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    init {
        // Подставляем текущего пользователя в фильтр по умолчанию.
        viewModelScope.launch {
            currentUser.collect { user ->
                if (settings.value.userId == null && user != null) {
                    container.reportSettings.setUserId(user.id)
                }
            }
        }
        // Любое изменение фильтра/сортировки/пользователя — снимаем выделение.
        viewModelScope.launch {
            settings.collect { _selectedIds.value = emptySet() }
        }
        // На всякий случай чистим выделение, если выбранный чек больше не виден
        // (например, удалён в другой вкладке или каскадно при удалении профиля).
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
     * Удаляет все отмеченные чеки одним батчем. Картинки-карточки удаляем тоже,
     * чтобы они не оставались на диске «осиротевшими».
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
                _event.value = ReportEvent.Error("Не удалось удалить чеки: ${e.message}")
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
        // Стабильный вторичный ключ — id, чтобы порядок не «прыгал» при равных значениях.
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

    fun savePdf(context: Context) = generate(context, openSystemPrint = false)
    fun printPdf(context: Context) = generate(context, openSystemPrint = true)

    private fun generate(context: Context, openSystemPrint: Boolean) {
        viewModelScope.launch {
            val s = settings.value
            val user = users.value.firstOrNull { it.id == s.userId }
                ?: currentUser.value
            if (user == null) {
                _event.value = ReportEvent.Error("Нет выбранного пользователя")
                return@launch
            }
            try {
                val params = ReportParams(
                    user = user,
                    periodStart = s.from,
                    periodEnd = s.to,
                    rows = rows.value,
                    quarterLabel = if (s.quarter == Quarter.Custom) null
                        else "${s.quarter.label} ${s.year} г."
                )
                val safeName = user.fullName.replace(Regex("[^A-Za-zА-Яа-я0-9_-]"), "_").take(40)
                val fileName = "report_${safeName}_${System.currentTimeMillis()}.pdf"
                val file = PdfReportGenerator.generate(context, params, fileName)
                if (openSystemPrint) {
                    _event.value = ReportEvent.Print(
                        file = file,
                        jobName = "QQS отчёт ${user.fullName}"
                    )
                } else {
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, "Открыть PDF")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    _event.value = ReportEvent.Open(chooser)
                }
            } catch (e: Throwable) {
                _event.value = ReportEvent.Error("Ошибка формирования PDF: ${e.message}")
            }
        }
    }

    /** Удобный метод для UI: вызвать системный диалог печати по уже сформированному файлу. */
    fun launchPrint(context: Context, file: File, jobName: String) {
        PdfPrint.print(context, file, jobName)
    }
}
