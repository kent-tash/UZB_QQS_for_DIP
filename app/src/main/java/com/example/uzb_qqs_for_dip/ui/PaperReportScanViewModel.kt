package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.data.model.ReceiptSource
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.scan.PaperReportOcr
import com.example.uzb_qqs_for_dip.scan.PaperReportRow
import com.example.uzb_qqs_for_dip.scan.PaperReportTableParser
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class PaperScanPhase {
    CAMERA,
    CONFIRM,
    EDIT,
    DONE
}

/** Редактируемая строка перед сохранением. */
data class EditablePaperRow(
    val id: String = UUID.randomUUID().toString(),
    val sellerName: String = "",
    val totalText: String = "",
    val vatText: String = "",
    val dateText: String = ""
)

data class PaperPageConfirm(
    val rowCount: Int,
    val cellCount: Int,
    val ocrFullName: String?,
    val ocrPosition: String?,
    val ocrPeriodLabel: String?,
    val nameMatches: Boolean,
    val pagesScanned: Int,
    val totalRowsAccumulated: Int
)

class PaperReportScanViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as QqsApp).container

    private val sessionId = UUID.randomUUID().toString()

    private val _employee = MutableStateFlow<User?>(null)
    val employee: StateFlow<User?> = _employee.asStateFlow()

    private val _quarter = MutableStateFlow(Quarter.Q1)
    val quarter: StateFlow<Quarter> = _quarter.asStateFlow()

    private val _year = MutableStateFlow(2026)
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _phase = MutableStateFlow(PaperScanPhase.CAMERA)
    val phase: StateFlow<PaperScanPhase> = _phase.asStateFlow()

    private val _capturing = MutableStateFlow(false)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    private val _confirm = MutableStateFlow<PaperPageConfirm?>(null)
    val confirm: StateFlow<PaperPageConfirm?> = _confirm.asStateFlow()

    private val _rows = MutableStateFlow<List<EditablePaperRow>>(emptyList())
    val rows: StateFlow<List<EditablePaperRow>> = _rows.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

    private var pagesScanned = 0
    private val accumulated = mutableListOf<PaperReportRow>()

    fun init(userId: Long, quarter: Quarter, year: Int, startManual: Boolean) {
        _quarter.value = quarter
        _year.value = year
        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) {
                container.userRepository.getById(userId)
            }
            _employee.value = user
            if (user == null) {
                _error.value = "Сотрудник не найден"
                return@launch
            }
            if (startManual) {
                _phase.value = PaperScanPhase.EDIT
                if (_rows.value.isEmpty()) {
                    _rows.value = listOf(EditablePaperRow())
                }
            } else {
                _phase.value = PaperScanPhase.CAMERA
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun onBitmapCaptured(bitmap: Bitmap) {
        if (_capturing.value) return
        viewModelScope.launch {
            _capturing.value = true
            _error.value = null
            try {
                val text = withContext(Dispatchers.Default) {
                    PaperReportOcr.recognize(bitmap)
                }
                val page = withContext(Dispatchers.Default) {
                    PaperReportTableParser.parse(text)
                }
                pagesScanned++
                mergeRows(page.rows)
                val emp = _employee.value
                val nameOk = PaperReportTableParser.namesLikelyMatch(page.ocrFullName, emp?.fullName.orEmpty())
                _confirm.value = PaperPageConfirm(
                    rowCount = page.rows.size,
                    cellCount = page.recognizedCellCount,
                    ocrFullName = page.ocrFullName,
                    ocrPosition = page.ocrPosition ?: emp?.position,
                    ocrPeriodLabel = page.ocrPeriodLabel
                        ?: "${_quarter.value.label} ${_year.value} г.",
                    nameMatches = nameOk,
                    pagesScanned = pagesScanned,
                    totalRowsAccumulated = accumulated.size
                )
                _phase.value = PaperScanPhase.CONFIRM
            } catch (e: Throwable) {
                _error.value = "Ошибка распознавания: ${e.message}"
            } finally {
                _capturing.value = false
                runCatching { bitmap.recycle() }
            }
        }
    }

    private fun mergeRows(newRows: List<PaperReportRow>) {
        for (r in newRows) {
            val key = "${r.rowNumber}|${r.purchasedAt}|${r.totalAmountTiyin}|${r.sellerName.lowercase()}"
            val exists = accumulated.any {
                "${it.rowNumber}|${it.purchasedAt}|${it.totalAmountTiyin}|${it.sellerName.lowercase()}" == key
            }
            if (!exists) accumulated.add(r)
        }
    }

    fun scanNextPage() {
        _confirm.value = null
        _phase.value = PaperScanPhase.CAMERA
    }

    fun goToEdit() {
        _rows.value = accumulated.map { it.toEditable() }.ifEmpty { listOf(EditablePaperRow()) }
        _confirm.value = null
        _phase.value = PaperScanPhase.EDIT
    }

    fun skipCameraToManual() {
        _phase.value = PaperScanPhase.EDIT
        if (_rows.value.isEmpty()) {
            _rows.value = accumulated.map { it.toEditable() }.ifEmpty { listOf(EditablePaperRow()) }
        }
    }

    fun updateRow(id: String, transform: (EditablePaperRow) -> EditablePaperRow) {
        _rows.value = _rows.value.map { if (it.id == id) transform(it) else it }
    }

    fun addEmptyRow() {
        _rows.value = _rows.value + EditablePaperRow()
    }

    fun removeRow(id: String) {
        _rows.value = _rows.value.filterNot { it.id == id }
    }

    fun save() {
        val emp = _employee.value ?: run {
            _error.value = "Сотрудник не выбран"
            return
        }
        val auditorId = container.sessionManager.currentUserId.value
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            try {
                val toSave = _rows.value.mapNotNull { it.toReceiptOrNull(emp.id) }
                if (toSave.isEmpty()) {
                    _error.value = "Нет корректных строк для сохранения"
                    return@launch
                }
                var saved = 0
                withContext(Dispatchers.IO) {
                    toSave.forEachIndexed { index, receipt ->
                        val withUrl = receipt.copy(
                            qrUrl = "paper://$sessionId/${emp.id}/$index-${System.nanoTime()}"
                        )
                        val idResult = container.receiptRepository.insert(withUrl)
                        val id = idResult.getOrThrow()
                        if (auditorId != null) {
                            container.receiptRepository.markVerified(id, auditorId)
                        }
                        saved++
                    }
                }
                _savedCount.value = saved
                _phase.value = PaperScanPhase.DONE
            } catch (e: Throwable) {
                _error.value = "Ошибка сохранения: ${e.message}"
            } finally {
                _saving.value = false
            }
        }
    }

    private fun PaperReportRow.toEditable(): EditablePaperRow = EditablePaperRow(
        sellerName = sellerName,
        totalText = MoneyFormat.fromTiyin(totalAmountTiyin),
        vatText = MoneyFormat.fromTiyin(vatAmountTiyin),
        dateText = DateFormat.formatDate(purchasedAt)
    )

    private fun EditablePaperRow.toReceiptOrNull(userId: Long): Receipt? {
        val seller = sellerName.trim()
        if (seller.isEmpty()) return null
        val total = MoneyFormat.toTiyin(totalText)
        val vat = MoneyFormat.toTiyin(vatText)
        if (total <= 0L) return null
        val purchasedAt = parseEditableDate(dateText) ?: return null
        return Receipt(
            userId = userId,
            purchasedAt = purchasedAt,
            sellerName = seller,
            totalAmountTiyin = total,
            vatAmountTiyin = vat.coerceAtLeast(0L),
            qrUrl = "paper://pending",
            source = ReceiptSource.PAPER,
            rawText = "paper-scan"
        )
    }

    private fun parseEditableDate(raw: String): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        // reuse parser line helper via fake line with amounts
        val probe = PaperReportTableParser.parseDataLine("1 X 1,00 1,00 $t")
        return probe?.purchasedAt
    }
}
