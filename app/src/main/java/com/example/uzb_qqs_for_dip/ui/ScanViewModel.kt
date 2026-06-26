package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.data.model.ReceiptOwner
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

    fun reset() {
        _state.value = ScanState.Idle
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
            val owner: ReceiptOwner? = container.receiptRepository.findOwnerByQrUrl(raw)
            val existingOwner: ExistingOwner? = when {
                owner == null -> null
                owner.userId == currentUserId -> ExistingOwner.SameUser
                else -> ExistingOwner.OtherUser(owner.fullName)
            }
            container.receiptParser.fetchAndParse(raw)
                .onSuccess { parsed ->
                    _state.value = ScanState.Parsed(parsed, existingOwner)
                }
                .onFailure { e ->
                    _state.value = ScanState.Error(
                        "Не удалось загрузить чек: ${e.message ?: e::class.simpleName}"
                    )
                }
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
            val owner = container.receiptRepository.findOwnerByQrUrl(parsed.qrUrl)
            if (owner != null && owner.userId != userId) {
                _state.value = ScanState.Error(
                    "Данный чек уже есть у пользователя ${owner.fullName}"
                )
                return@launch
            }
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
            container.receiptRepository.insert(receipt)
                .onSuccess {
                    _state.value = ScanState.Idle
                    onSaved()
                }
                .onFailure { e ->
                    // Fallback: если UNIQUE сработал — подтянуть владельца для точного сообщения.
                    val msg = if (e.message?.contains("UNIQUE", true) == true) {
                        val existingOwner = container.receiptRepository.findOwnerByQrUrl(parsed.qrUrl)
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
        val owner = container.receiptRepository.findOwnerByQrUrl(parsed.qrUrl)
        if (owner != null && owner.userId != userId) {
            return Result.failure(
                IllegalStateException("Данный чек уже есть у пользователя ${owner.fullName}")
            )
        }
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
        val result = container.receiptRepository.insert(receipt)
        if (result.isSuccess && auditorUserId != null) {
            result.getOrNull()?.let { id ->
                container.receiptRepository.markVerified(id, auditorUserId)
            }
        }
        return result
    }
}
