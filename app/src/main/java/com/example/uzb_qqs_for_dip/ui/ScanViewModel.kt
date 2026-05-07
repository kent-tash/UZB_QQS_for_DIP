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

sealed interface ScanState {
    data object Idle : ScanState
    data object Loading : ScanState
    data class Parsed(val parsed: ParsedReceipt, val alreadyExists: Boolean) : ScanState
    data class Error(val message: String) : ScanState
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as QqsApp).container

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun reset() {
        _state.value = ScanState.Idle
    }

    /**
     * Распознаёт QR на фотографии чека, выбранной пользователем (галерея/файлы),
     * и далее запускает тот же flow, что и обычное сканирование камерой.
     */
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
            val existing = container.receiptRepository.findByQrUrl(raw)
            container.receiptParser.fetchAndParse(raw)
                .onSuccess { parsed ->
                    _state.value = ScanState.Parsed(parsed, alreadyExists = existing != null)
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
            val receipt = Receipt(
                userId = userId,
                purchasedAt = parsed.purchasedAt!!,
                sellerName = parsed.sellerName!!,
                totalAmountTiyin = parsed.totalAmountTiyin!!,
                vatAmountTiyin = parsed.vatAmountTiyin!!,
                qrUrl = parsed.qrUrl,
                rawText = parsed.rawSnippet
            )
            container.receiptRepository.insert(receipt)
                .onSuccess {
                    _state.value = ScanState.Idle
                    onSaved()
                }
                .onFailure { e ->
                    val msg = if (e.message?.contains("UNIQUE", true) == true)
                        "Этот чек уже сохранён ранее"
                    else "Не удалось сохранить чек: ${e.message}"
                    _state.value = ScanState.Error(msg)
                }
        }
    }
}
