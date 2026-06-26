package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.AuditDeclaration
import com.example.uzb_qqs_for_dip.data.model.AuditStatus
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.repository.EmployeeSummary
import com.example.uzb_qqs_for_dip.data.repository.ReceiptConflict
import com.example.uzb_qqs_for_dip.data.settings.AuditorSettings
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.export.OrgReportPdfGenerator
import com.example.uzb_qqs_for_dip.export.SummaryPdfGenerator
import com.example.uzb_qqs_for_dip.export.SummaryTableExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuditorFilter { ALL, DISCREPANCY, UNVERIFIED, CONFLICT, INCOMPLETE }

class AuditorViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as QqsApp).container

    private val _quarter = MutableStateFlow(ReportSettings.currentQuarter())
    val quarter: StateFlow<Quarter> = _quarter.asStateFlow()

    private val _year = MutableStateFlow(ReportSettings.currentYear())
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _filter = MutableStateFlow(AuditorFilter.ALL)
    val filter: StateFlow<AuditorFilter> = _filter.asStateFlow()

    private val _summaries = MutableStateFlow<List<EmployeeSummary>>(emptyList())
    val summaries: StateFlow<List<EmployeeSummary>> = _summaries.asStateFlow()

    private val _conflicts = MutableStateFlow<List<ReceiptConflict>>(emptyList())
    val conflicts: StateFlow<List<ReceiptConflict>> = _conflicts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _batchImportResult = MutableStateFlow<String?>(null)
    val batchImportResult: StateFlow<String?> = _batchImportResult.asStateFlow()

    private val _addEmployeeError = MutableStateFlow<String?>(null)
    val addEmployeeError: StateFlow<String?> = _addEmployeeError.asStateFlow()

    val auditorSettings: StateFlow<AuditorSettings> = container.auditorSettings.settings

    /** Реактивно отфильтрованный список сотрудников по поиску и выбранному фильтру. */
    val filteredSummaries: StateFlow<List<EmployeeSummary>> = combine(
        _summaries, _search, _filter, _conflicts
    ) { sums, query, f, conflicts ->
        val q = query.trim().lowercase()
        sums.filter { s ->
            val matchesSearch = q.isEmpty() || s.fullName.lowercase().contains(q)
            val matchesFilter = when (f) {
                AuditorFilter.ALL -> true
                AuditorFilter.DISCREPANCY -> {
                    val decl = s.declaration
                    decl != null && (
                        decl.declaredTotalTiyin != 0L && decl.declaredTotalTiyin != s.totalTiyin ||
                            decl.declaredVatTiyin != 0L && decl.declaredVatTiyin != s.vatTiyin
                        )
                }
                AuditorFilter.UNVERIFIED -> s.declaration == null || s.declaration.status == AuditStatus.PENDING
                AuditorFilter.CONFLICT -> conflicts.any { c -> c.user1Id == s.userId || c.user2Id == s.userId }
                AuditorFilter.INCOMPLETE -> s.verifiedCount < s.receiptCount
            }
            matchesSearch && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        refresh()
        // Автоматически обновляем сводку, когда чеки в БД изменяются
        // (например, аудитор добавил/проверил чек в AuditorVerifyScreen).
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            container.receiptRepository.receipts
                .drop(1)           // пропускаем первую эмиссию, т.к. refresh() уже вызван выше
                .debounce(500)     // группируем быстрые изменения
                .collect { refresh() }
        }
    }

    fun setQuarter(q: Quarter) {
        _quarter.value = q
        refresh()
    }

    fun setYear(y: Int) {
        _year.value = y
        refresh()
    }

    fun setSearch(s: String) { _search.value = s }

    fun setFilter(f: AuditorFilter) { _filter.value = f }

    fun clearError() { _errorMessage.value = null }

    fun clearBatchImportResult() { _batchImportResult.value = null }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val q = _quarter.value
            val y = _year.value
            val from = ReportSettings.quarterStart(y, q)
            val to = ReportSettings.quarterEnd(y, q)
            try {
                val sums = container.auditorRepository.getEmployeeSummaries(from, to, y, q.name)
                _summaries.value = sums
                val conf = container.auditorRepository.findConflicts(from, to)
                _conflicts.value = conf
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки данных: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearAddEmployeeError() { _addEmployeeError.value = null }

    fun saveAuditorSettings(s: AuditorSettings) {
        container.auditorSettings.save(s)
    }

    /** Создаёт нового сотрудника (без входа в аккаунт). */
    fun addEmployee(
        fullName: String,
        position: String,
        initialsSurname: String,
        organization: String = "",
        onSuccess: () -> Unit
    ) {
        val name = fullName.trim()
        val pos = position.trim()
        val initials = initialsSurname.trim()
        if (name.isEmpty() || pos.isEmpty() || initials.isEmpty()) {
            _addEmployeeError.value = "Заполните все поля"
            return
        }
        viewModelScope.launch {
            val res = container.userRepository.create(
                User(
                    fullName = name,
                    position = pos,
                    initialsSurname = initials,
                    organization = organization.trim(),
                    role = UserRole.EMPLOYEE
                )
            )
            res.onSuccess {
                _addEmployeeError.value = null
                refresh()
                withContext(Dispatchers.Main) { onSuccess() }
            }.onFailure { e ->
                _addEmployeeError.value = if (e.message?.contains("UNIQUE", true) == true)
                    "Сотрудник с таким именем уже существует"
                else "Ошибка: ${e.message}"
            }
        }
    }

    fun upsertDeclaration(decl: AuditDeclaration) {
        viewModelScope.launch {
            container.auditorRepository.upsertDeclaration(decl)
            refresh()
        }
    }

    fun exportPdf(context: Context) {
        viewModelScope.launch {
            val appCtx = context.applicationContext
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    SummaryPdfGenerator.generate(
                        appCtx, _summaries.value,
                        _quarter.value, _year.value,
                        container.auditorSettings.settings.value
                    )
                }
                sharePdf(appCtx, file, "Экспорт сводной таблицы PDF")
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "Ошибка экспорта PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportOrgReportPdf(context: Context) {
        viewModelScope.launch {
            val appCtx = context.applicationContext
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    OrgReportPdfGenerator.generate(
                        appCtx, _summaries.value,
                        _quarter.value, _year.value,
                        container.auditorSettings.settings.value
                    )
                }
                sharePdf(appCtx, file, "Возврат НДС по организациям PDF")
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "Ошибка экспорта PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun sharePdf(appCtx: Context, file: java.io.File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            appCtx.startActivity(
                Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun exportXlsx(context: Context) {
        viewModelScope.launch {
            val appCtx = context.applicationContext
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    SummaryTableExporter.exportXlsx(
                        appCtx, _summaries.value,
                        _quarter.value.name, _year.value
                    )
                }
                val uri = FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    appCtx.startActivity(
                        Intent.createChooser(intent, "Экспорт сводной таблицы")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            val appCtx = context.applicationContext
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    SummaryTableExporter.exportCsv(
                        appCtx, _summaries.value,
                        _quarter.value.name, _year.value
                    )
                }
                val uri = FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    appCtx.startActivity(
                        Intent.createChooser(intent, "Экспорт CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun mergeBatchFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val appCtx = context.applicationContext
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                val jsons = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        val bytes = appCtx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Не удалось прочитать файл: $uri")
                        String(bytes, Charsets.UTF_8)
                    }
                }
                container.appBackup.mergeMany(jsons).getOrThrow()
            }.onSuccess { outcome ->
                val conflictMsg = if (outcome.qrConflicts.isNotEmpty()) {
                    "\n⚠️ Конфликты QR (${outcome.qrConflicts.size}): " +
                        outcome.qrConflicts.entries.take(3).joinToString { (_, owner) -> owner }
                } else ""
                _batchImportResult.value =
                    "Добавлено профилей: ${outcome.addedUsers}\n" +
                    "Добавлено чеков: ${outcome.addedReceipts}\n" +
                    "Пропущено дублей: ${outcome.skippedReceipts}" +
                    conflictMsg
                refresh()
            }.onFailure { e ->
                _errorMessage.value = "Ошибка пакетного импорта: ${e.message}"
            }
            _isLoading.value = false
        }
    }
}
