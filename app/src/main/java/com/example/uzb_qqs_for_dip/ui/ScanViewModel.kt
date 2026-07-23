package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.network.ParsedReceipt
import com.example.uzb_qqs_for_dip.render.QrFromImageDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Описывает существующего владельца чека при дубликате QR:
 * null — чека нет в базе; SameUser — у текущего пользователя; OtherUser — у другого.
 */
sealed interface ExistingOwner {
    data object SameUser : ExistingOwner
    data class OtherUser(val fullName: String) : ExistingOwner
}

sealed interface ScanState {
    data object Idle : ScanState
    data object Loading : ScanState
    data class Parsed(val parsed: ParsedReceipt, val existingOwner: ExistingOwner? = null) : ScanState
    data class Error(val message: String) : ScanState
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as QqsApp).container

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _sheetPreviewItems = MutableStateFlow<List<SheetReceiptItem>>(emptyList())
    val sheetPreviewItems: StateFlow<List<SheetReceiptItem>> = _sheetPreviewItems.asStateFlow()

    private val _sheetSummary = MutableStateFlow<SheetSummary?>(null)
    val sheetSummary: StateFlow<SheetSummary?> = _sheetSummary.asStateFlow()

    private val _sheetLoading = MutableStateFlow(false)
    val sheetLoading: StateFlow<Boolean> = _sheetLoading.asStateFlow()

    fun reset() {
        _state.value = ScanState.Idle
    }

    fun clearSheetPreview() {
        _sheetPreviewItems.value = emptyList()
        _sheetLoading.value = false
    }

    fun clearSheetSummary() {
        _sheetSummary.value = null
    }

    fun toggleSheetItem(index: Int) {
        val list = _sheetPreviewItems.value.toMutableList()
        if (index !in list.indices) return
        val item = list[index]
        if (item.status == SheetItemStatus.OTHER_OWNER ||
            item.status == SheetItemStatus.ERROR
        ) {
            return
        }
        list[index] = item.copy(selected = !item.selected)
        _sheetPreviewItems.value = list
    }

    fun handleImageFromGallery(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = ScanState.Loading
            val decoded = runCatching { QrFromImageDecoder.decode(context, uri) }
            decoded.onSuccess { payload -> handleScan(payload) }
                .onFailure { e ->
                    _state.value = ScanState.Error(
                        e.message ?: "Не удалось распознать QR на изображении"
                    )
                }
        }
    }

    fun handleScan(qrPayload: String?) {
        val raw = qrPayload?.trim().orEmpty()
        if (raw.isEmpty()) {
            _state.value = ScanState.Error("Пустой QR-код")
            return
        }
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            _state.value =
                ScanState.Error("QR не содержит ссылку на чек: \"${raw.take(64)}\"")
            return
        }
        viewModelScope.launch {
            _state.value = ScanState.Loading
            val currentUserId = container.sessionManager.currentUserId.value
            container.receiptParser.fetchAndParse(raw)
                .onSuccess { parsed ->
                    val owner = container.receiptRepository.findOwner(
                        qrUrl = parsed.qrUrl,
                        fiscalSign = parsed.fiscalSign,
                        terminalId = parsed.terminalId,
                        receiptNumber = parsed.receiptNumber,
                    )
                    val existingOwner: ExistingOwner? = when {
                        owner == null -> null
                        owner.userId == currentUserId -> ExistingOwner.SameUser
                        else -> ExistingOwner.OtherUser(owner.fullName)
                    }
                    _state.value = ScanState.Parsed(parsed, existingOwner)
                }
                .onFailure { e ->
                    _state.value = ScanState.Error(
                        "Не удалось загрузить чек: ${e.message ?: e::class.simpleName}"
                    )
                }
        }
    }

    /**
     * Декодирует все QR с фото, для каждого URL парсит чек и ищет владельца
     * без вставки в БД — результат попадает в [sheetPreviewItems].
     */
    fun prepareSheetFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _sheetLoading.value = true
            _sheetSummary.value = null
            _sheetPreviewItems.value = emptyList()
            val urls = runCatching { QrFromImageDecoder.decodeAll(context, uri) }
                .getOrElse { e ->
                    _sheetLoading.value = false
                    _sheetSummary.value = SheetSummary(
                        scanned = 0, saved = 0, alreadyVerified = 0, conflicts = 0,
                        errors = 1, skipped = 0,
                        message = e.message ?: "Не удалось распознать QR на изображении"
                    )
                    return@launch
                }
            _sheetLoading.value = false
            prepareSheetFromUrls(urls)
        }
    }

    /**
     * Готовит превью пакетного скана по уже собранным URL (камера или галерея).
     */
    fun prepareSheetFromUrls(urls: List<String>) {
        val userId = container.sessionManager.currentUserId.value
        if (userId == null) {
            _sheetSummary.value = SheetSummary(
                scanned = 0, saved = 0, alreadyVerified = 0, conflicts = 0,
                errors = 1, skipped = 0,
                message = "Сессия истекла. Войдите снова"
            )
            return
        }
        viewModelScope.launch {
            _sheetLoading.value = true
            _sheetSummary.value = null
            _sheetPreviewItems.value = emptyList()
            try {
                val distinct = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                if (distinct.isEmpty()) {
                    _sheetSummary.value = SheetSummary(
                        scanned = 0, saved = 0, alreadyVerified = 0, conflicts = 0,
                        errors = 1, skipped = 0,
                        message = "QR-коды не найдены"
                    )
                    return@launch
                }
                val items = distinct.map { raw -> buildSheetItem(raw, userId) }
                _sheetPreviewItems.value = items
            } finally {
                _sheetLoading.value = false
            }
        }
    }

    private suspend fun buildSheetItem(raw: String, userId: Long): SheetReceiptItem {
        val url = raw.trim()
        if (url.isEmpty()) {
            return SheetReceiptItem(
                qrUrl = raw,
                status = SheetItemStatus.ERROR,
                errorMessage = "Пустой QR-код"
            )
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return SheetReceiptItem(
                qrUrl = url,
                status = SheetItemStatus.ERROR,
                errorMessage = "QR не содержит ссылку на чек"
            )
        }
        return container.receiptParser.fetchAndParse(url)
            .fold(
                onSuccess = { parsed ->
                    val existingOwner = container.receiptRepository.findOwner(
                        qrUrl = parsed.qrUrl,
                        fiscalSign = parsed.fiscalSign,
                        terminalId = parsed.terminalId,
                        receiptNumber = parsed.receiptNumber,
                    )
                    when {
                        existingOwner != null && existingOwner.userId != userId ->
                            SheetReceiptItem(
                                qrUrl = parsed.qrUrl,
                                parsed = parsed,
                                status = SheetItemStatus.OTHER_OWNER,
                                ownerName = existingOwner.fullName,
                                selected = false
                            )
                        !parsed.isValid ->
                            SheetReceiptItem(
                                qrUrl = parsed.qrUrl,
                                parsed = parsed,
                                status = SheetItemStatus.ERROR,
                                errorMessage = "Не все поля чека распознаны",
                                selected = false
                            )
                        existingOwner?.userId == userId ->
                            SheetReceiptItem(
                                qrUrl = parsed.qrUrl,
                                parsed = parsed,
                                status = SheetItemStatus.ALREADY_THIS,
                                ownerName = existingOwner.fullName,
                                selected = false
                            )
                        else ->
                            SheetReceiptItem(
                                qrUrl = parsed.qrUrl,
                                parsed = parsed,
                                status = SheetItemStatus.NEW,
                                selected = true
                            )
                    }
                },
                onFailure = { e ->
                    SheetReceiptItem(
                        qrUrl = url,
                        status = SheetItemStatus.ERROR,
                        errorMessage = "Не удалось загрузить чек: ${e.message ?: e::class.simpleName}",
                        selected = false
                    )
                }
            )
    }

    /**
     * Сохраняет выбранные NEW для текущего пользователя.
     * ALREADY_THIS учитывает в summary; OTHER_OWNER / ERROR / невыбранные — без insert.
     */
    fun confirmSheetSelection() {
        val userId = container.sessionManager.currentUserId.value ?: return
        val items = _sheetPreviewItems.value
        if (items.isEmpty()) return

        viewModelScope.launch {
            _sheetLoading.value = true
            var saved = 0
            var alreadyInDb = 0
            var conflicts = 0
            var errors = 0
            var skipped = 0

            for (item in items) {
                when {
                    item.status == SheetItemStatus.OTHER_OWNER -> conflicts++
                    item.status == SheetItemStatus.ERROR -> errors++
                    !item.selected -> {
                        if (item.status == SheetItemStatus.ALREADY_THIS) alreadyInDb++
                        else skipped++
                    }
                    item.status == SheetItemStatus.ALREADY_THIS -> alreadyInDb++
                    item.status == SheetItemStatus.NEW -> {
                        val parsed = item.parsed
                        if (parsed == null || !parsed.isValid) {
                            errors++
                            continue
                        }
                        val insertResult = insertParsed(parsed, userId)
                        if (insertResult.isFailure) {
                            val ownerAfterFail = container.receiptRepository.findOwner(
                                qrUrl = parsed.qrUrl,
                                fiscalSign = parsed.fiscalSign,
                                terminalId = parsed.terminalId,
                                receiptNumber = parsed.receiptNumber,
                            )
                            when {
                                ownerAfterFail != null && ownerAfterFail.userId != userId ->
                                    conflicts++
                                ownerAfterFail != null && ownerAfterFail.userId == userId ->
                                    alreadyInDb++
                                else -> errors++
                            }
                        } else {
                            saved++
                        }
                    }
                    else -> skipped++
                }
            }

            val scanned = items.size
            val message = buildString {
                append("Сканировано: $scanned")
                append(". Сохранено: $saved")
                append(". Уже в базе: $alreadyInDb")
                append(". Конфликты: $conflicts")
                if (errors > 0) append(". Ошибки: $errors")
                if (skipped > 0) append(". Пропущено: $skipped")
            }
            _sheetSummary.value = SheetSummary(
                scanned = scanned,
                saved = saved,
                alreadyVerified = alreadyInDb,
                conflicts = conflicts,
                errors = errors,
                skipped = skipped,
                message = message
            )
            _sheetPreviewItems.value = emptyList()
            _sheetLoading.value = false
        }
    }

    fun saveCurrent(onSaved: () -> Unit = {}) {
        val current = _state.value
        if (current !is ScanState.Parsed) return
        val parsed = current.parsed
        if (!parsed.isValid) {
            _state.value = ScanState.Error("Не удалось распознать обязательные поля чека")
            return
        }
        val userId = container.sessionManager.currentUserId.value
        if (userId == null) {
            _state.value = ScanState.Error("Сессия истекла. Войдите снова")
            return
        }
        viewModelScope.launch {
            // Повторная проверка перед записью (race condition guard).
            val owner = container.receiptRepository.findOwner(
                qrUrl = parsed.qrUrl,
                fiscalSign = parsed.fiscalSign,
                terminalId = parsed.terminalId,
                receiptNumber = parsed.receiptNumber,
            )
            if (owner != null && owner.userId != userId) {
                _state.value = ScanState.Error(
                    "Данный чек уже есть у пользователя ${owner.fullName}"
                )
                return@launch
            }
            if (owner != null && owner.userId == userId) {
                _state.value = ScanState.Error("Этот чек уже сохранён ранее")
                return@launch
            }
            insertParsed(parsed, userId)
                .onSuccess {
                    _state.value = ScanState.Idle
                    onSaved()
                }
                .onFailure { e ->
                    val msg = if (e.message?.contains("UNIQUE", true) == true) {
                        val existingOwner = container.receiptRepository.findOwner(
                            qrUrl = parsed.qrUrl,
                            fiscalSign = parsed.fiscalSign,
                            terminalId = parsed.terminalId,
                            receiptNumber = parsed.receiptNumber,
                        )
                        if (existingOwner != null && existingOwner.userId != userId) {
                            "Данный чек уже есть у пользователя ${existingOwner.fullName}"
                        } else {
                            "Этот чек уже сохранён ранее"
                        }
                    } else "Не удалось сохранить чек: ${e.message}"
                    _state.value = ScanState.Error(msg)
                }
        }
    }

    private suspend fun insertParsed(parsed: ParsedReceipt, userId: Long): Result<Long> {
        val receipt = Receipt(
            userId = userId,
            purchasedAt = parsed.purchasedAt!!,
            sellerName = parsed.sellerName!!,
            totalAmountTiyin = parsed.totalAmountTiyin!!,
            vatAmountTiyin = parsed.vatAmountTiyin!!,
            qrUrl = parsed.qrUrl,
            paymentType = parsed.paymentType,
            fiscalSign = parsed.fiscalSign,
            address = parsed.address,
            tin = parsed.tin,
            terminalId = parsed.terminalId,
            receiptNumber = parsed.receiptNumber,
            nkmName = parsed.nkmName,
            sn = parsed.sn,
            rawText = parsed.rawSnippet
        )
        return container.receiptRepository.insert(receipt)
    }

    /**
     * Сохраняет чек для указанного пользователя (используется аудитором при QR-верификации).
     * Возвращает id новой записи или ошибку.
     */
    suspend fun saveForUser(
        parsed: ParsedReceipt,
        userId: Long,
        auditorUserId: Long? = null
    ): Result<Long> {
        if (!parsed.isValid) return Result.failure(IllegalStateException("Неполные данные чека"))
        val owner = container.receiptRepository.findOwner(
            qrUrl = parsed.qrUrl,
            fiscalSign = parsed.fiscalSign,
            terminalId = parsed.terminalId,
            receiptNumber = parsed.receiptNumber,
        )
        if (owner != null && owner.userId != userId) {
            return Result.failure(
                IllegalStateException("Данный чек уже есть у пользователя ${owner.fullName}")
            )
        }
        if (owner != null && owner.userId == userId) {
            return Result.success(owner.receiptId)
        }
        val result = insertParsed(parsed, userId)
        if (result.isSuccess && auditorUserId != null) {
            result.getOrNull()?.let { id ->
                container.receiptRepository.markVerified(id, auditorUserId)
            }
        }
        return result
    }
}
