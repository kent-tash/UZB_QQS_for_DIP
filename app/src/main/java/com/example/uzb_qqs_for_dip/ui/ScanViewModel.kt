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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * РћРїРёСЃС‹РІР°РµС‚ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РµРіРѕ РІР»Р°РґРµР»СЊС†Р° С‡РµРєР° РїСЂРё РґСѓР±Р»РёРєР°С‚Рµ QR:
 * null вЂ” С‡РµРєР° РЅРµС‚ РІ Р±Р°Р·Рµ; SameUser вЂ” Сѓ С‚РµРєСѓС‰РµРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ; OtherUser вЂ” Сѓ РґСЂСѓРіРѕРіРѕ.
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

fun Throwable.toReadableMessage(): String {
    return when (this) {
        is java.net.UnknownHostException -> "РќРµС‚ РїРѕРґРєР»СЋС‡РµРЅРёСЏ Рє РёРЅС‚РµСЂРЅРµС‚Сѓ РёР»Рё СЃРµСЂРІРµСЂ РЅРµРґРѕСЃС‚СѓРїРµРЅ"
        is java.net.SocketTimeoutException -> "РЎРµСЂРІРµСЂ РЅР°Р»РѕРіРѕРІРѕР№ РЅРµ РѕС‚РІРµС‡Р°РµС‚ (С‚Р°Р№Рј-Р°СѓС‚)"
        is java.net.ConnectException -> "РќРµ СѓРґР°Р»РѕСЃСЊ РїРѕРґРєР»СЋС‡РёС‚СЊСЃСЏ Рє СЃРµСЂРІРµСЂСѓ"
        is java.net.SocketException -> "РџСЂРµСЂРІР°РЅРѕ СЃРѕРµРґРёРЅРµРЅРёРµ СЃ СЃРµСЂРІРµСЂРѕРј"
        is org.json.JSONException -> "РќРµРІРµСЂРЅС‹Р№ С„РѕСЂРјР°С‚ РѕС‚РІРµС‚Р° РѕС‚ СЃРµСЂРІРµСЂР°"
        else -> this.message ?: this::class.simpleName ?: "РќРµРёР·РІРµСЃС‚РЅР°СЏ РѕС€РёР±РєР°"
    }
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

    val scannedCount: StateFlow<Int> = combine(
        container.sessionManager.currentUserId,
        container.receiptRepository.receipts
    ) { userId, receipts ->
        if (userId == null) 0 else receipts.count { it.receipt.userId == userId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
                        e.message ?: "РќРµ СѓРґР°Р»РѕСЃСЊ СЂР°СЃРїРѕР·РЅР°С‚СЊ QR РЅР° РёР·РѕР±СЂР°Р¶РµРЅРёРё"
                    )
                }
        }
    }

    fun handleScan(qrPayload: String?) {
        val raw = qrPayload?.trim().orEmpty()
        if (raw.isEmpty()) {
            _state.value = ScanState.Error("РџСѓСЃС‚РѕР№ QR-РєРѕРґ")
            return
        }
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            _state.value =
                ScanState.Error("QR РЅРµ СЃРѕРґРµСЂР¶РёС‚ СЃСЃС‹Р»РєСѓ РЅР° С‡РµРє: \"${raw.take(64)}\"")
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
                        "РќРµ СѓРґР°Р»РѕСЃСЊ Р·Р°РіСЂСѓР·РёС‚СЊ С‡РµРє: ${e.toReadableMessage()}"
                    )
                }
        }
    }

    /**
     * Р”РµРєРѕРґРёСЂСѓРµС‚ РІСЃРµ QR СЃ С„РѕС‚Рѕ, РґР»СЏ РєР°Р¶РґРѕРіРѕ URL РїР°СЂСЃРёС‚ С‡РµРє Рё РёС‰РµС‚ РІР»Р°РґРµР»СЊС†Р°
     * Р±РµР· РІСЃС‚Р°РІРєРё РІ Р‘Р” вЂ” СЂРµР·СѓР»СЊС‚Р°С‚ РїРѕРїР°РґР°РµС‚ РІ [sheetPreviewItems].
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
                        message = e.message ?: "РќРµ СѓРґР°Р»РѕСЃСЊ СЂР°СЃРїРѕР·РЅР°С‚СЊ QR РЅР° РёР·РѕР±СЂР°Р¶РµРЅРёРё"
                    )
                    return@launch
                }
            _sheetLoading.value = false
            prepareSheetFromUrls(urls)
        }
    }

    suspend fun processSingleQr(rawUrl: String): Pair<Boolean, String> {
        val userId = container.sessionManager.currentUserId.value
            ?: return false to "РЎРµСЃСЃРёСЏ РёСЃС‚РµРєР»Р°. Р’РѕР№РґРёС‚Рµ СЃРЅРѕРІР°."
        val url = rawUrl.trim()
        if (url.isEmpty()) return false to "РџСѓСЃС‚РѕР№ QR-РєРѕРґ"
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false to "QR-РєРѕРґ РЅРµ СЏРІР»СЏРµС‚СЃСЏ С‡РµРєРѕРј"

        val parsedResult = container.receiptParser.fetchAndParse(url)
        if (parsedResult.isFailure) {
            val e = parsedResult.exceptionOrNull()
            val msg = when (e) {
                is java.net.UnknownHostException -> "РќРµС‚ РїРѕРґРєР»СЋС‡РµРЅРёСЏ Рє СЃРµС‚Рё"
                is java.net.SocketTimeoutException, is java.net.ConnectException -> "РЎР°Р№С‚ РЅР°Р»РѕРіРѕРІРѕР№ РЅРµ РѕС‚РІРµС‡Р°РµС‚"
                else -> "РћС€РёР±РєР° СЃРµС‚Рё РёР»Рё СЃРµСЂРІРµСЂР°: " + (e?.toReadableMessage() ?: "")
            }
            return false to msg
        }
        val parsed = parsedResult.getOrThrow()

        if (!parsed.isValid) return false to "РќРµ РІСЃРµ РїРѕР»СЏ С‡РµРєР° СЂР°СЃРїРѕР·РЅР°РЅС‹"

        val existingOwner = container.receiptRepository.findOwner(
            qrUrl = parsed.qrUrl,
            fiscalSign = parsed.fiscalSign,
            terminalId = parsed.terminalId,
            receiptNumber = parsed.receiptNumber,
        )

        if (existingOwner != null) {
            return if (existingOwner.userId == userId) false to "Р­С‚РѕС‚ С‡РµРє СѓР¶Рµ Р±С‹Р» РґРѕР±Р°РІР»РµРЅ"
            else false to "Р§РµРє РїСЂРёРЅР°РґР»РµР¶РёС‚: ${existingOwner.fullName}"
        }

        val insertRes = insertParsed(parsed, userId)
        return if (insertRes.isSuccess) {
            val vat = parsed.vatAmountTiyin ?: 0L
            if (vat == 0L) true to "Р’ С‡РµРєРµ РЅРµС‚ РќР”РЎ (0 СЃСѓРј)"
            else true to "Р§РµРє РґРѕР±Р°РІР»РµРЅ (РќР”РЎ: ${vat / 100} СЃСѓРј)"
        }
        else false to "РћС€РёР±РєР° СЃРѕС…СЂР°РЅРµРЅРёСЏ РІ Р‘Р”"
    }

    /**
     * Р“РѕС‚РѕРІРёС‚ РїСЂРµРІСЊСЋ РїР°РєРµС‚РЅРѕРіРѕ СЃРєР°РЅР° РїРѕ СѓР¶Рµ СЃРѕР±СЂР°РЅРЅС‹Рј URL (РєР°РјРµСЂР° РёР»Рё РіР°Р»РµСЂРµСЏ).
     */
    fun prepareSheetFromUrls(urls: List<String>) {
        val userId = container.sessionManager.currentUserId.value
        if (userId == null) {
            _sheetSummary.value = SheetSummary(
                scanned = 0, saved = 0, alreadyVerified = 0, conflicts = 0,
                errors = 1, skipped = 0,
                message = "РЎРµСЃСЃРёСЏ РёСЃС‚РµРєР»Р°. Р’РѕР№РґРёС‚Рµ СЃРЅРѕРІР°"
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
                        message = "QR-РєРѕРґС‹ РЅРµ РЅР°Р№РґРµРЅС‹"
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
                errorMessage = "РџСѓСЃС‚РѕР№ QR-РєРѕРґ"
            )
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return SheetReceiptItem(
                qrUrl = url,
                status = SheetItemStatus.ERROR,
                errorMessage = "QR РЅРµ СЃРѕРґРµСЂР¶РёС‚ СЃСЃС‹Р»РєСѓ РЅР° С‡РµРє"
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
                                errorMessage = "РќРµ РІСЃРµ РїРѕР»СЏ С‡РµРєР° СЂР°СЃРїРѕР·РЅР°РЅС‹",
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
                        errorMessage = "РќРµ СѓРґР°Р»РѕСЃСЊ Р·Р°РіСЂСѓР·РёС‚СЊ С‡РµРє: ${e.toReadableMessage()}",
                        selected = false
                    )
                }
            )
    }

    /**
     * РЎРѕС…СЂР°РЅСЏРµС‚ РІС‹Р±СЂР°РЅРЅС‹Рµ NEW РґР»СЏ С‚РµРєСѓС‰РµРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ.
     * ALREADY_THIS СѓС‡РёС‚С‹РІР°РµС‚ РІ summary; OTHER_OWNER / ERROR / РЅРµРІС‹Р±СЂР°РЅРЅС‹Рµ вЂ” Р±РµР· insert.
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
                append("РЎРєР°РЅРёСЂРѕРІР°РЅРѕ: $scanned")
                append(". РЎРѕС…СЂР°РЅРµРЅРѕ: $saved")
                append(". РЈР¶Рµ РІ Р±Р°Р·Рµ: $alreadyInDb")
                append(". РљРѕРЅС„Р»РёРєС‚С‹: $conflicts")
                if (errors > 0) append(". РћС€РёР±РєРё: $errors")
                if (skipped > 0) append(". РџСЂРѕРїСѓС‰РµРЅРѕ: $skipped")
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
            _state.value = ScanState.Error("РќРµ СѓРґР°Р»РѕСЃСЊ СЂР°СЃРїРѕР·РЅР°С‚СЊ РѕР±СЏР·Р°С‚РµР»СЊРЅС‹Рµ РїРѕР»СЏ С‡РµРєР°")
            return
        }
        val userId = container.sessionManager.currentUserId.value
        if (userId == null) {
            _state.value = ScanState.Error("РЎРµСЃСЃРёСЏ РёСЃС‚РµРєР»Р°. Р’РѕР№РґРёС‚Рµ СЃРЅРѕРІР°")
            return
        }
        viewModelScope.launch {
            // РџРѕРІС‚РѕСЂРЅР°СЏ РїСЂРѕРІРµСЂРєР° РїРµСЂРµРґ Р·Р°РїРёСЃСЊСЋ (race condition guard).
            val owner = container.receiptRepository.findOwner(
                qrUrl = parsed.qrUrl,
                fiscalSign = parsed.fiscalSign,
                terminalId = parsed.terminalId,
                receiptNumber = parsed.receiptNumber,
            )
            if (owner != null && owner.userId != userId) {
                _state.value = ScanState.Error(
                    "Р”Р°РЅРЅС‹Р№ С‡РµРє СѓР¶Рµ РµСЃС‚СЊ Сѓ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ ${owner.fullName}"
                )
                return@launch
            }
            if (owner != null && owner.userId == userId) {
                _state.value = ScanState.Error("Р­С‚РѕС‚ С‡РµРє СѓР¶Рµ СЃРѕС…СЂР°РЅС‘РЅ СЂР°РЅРµРµ")
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
                            "Р”Р°РЅРЅС‹Р№ С‡РµРє СѓР¶Рµ РµСЃС‚СЊ Сѓ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ ${existingOwner.fullName}"
                        } else {
                            "Р­С‚РѕС‚ С‡РµРє СѓР¶Рµ СЃРѕС…СЂР°РЅС‘РЅ СЂР°РЅРµРµ"
                        }
                    } else "РќРµ СѓРґР°Р»РѕСЃСЊ СЃРѕС…СЂР°РЅРёС‚СЊ С‡РµРє: ${e.message}"
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

    fun saveManualReceipt(
        context: Context,
        storeName: String,
        dateMs: Long,
        totalAmountTiyin: Long,
        vatAmountTiyin: Long,
        photoUri: Uri?,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = container.sessionManager.currentUserId.value
        if (userId == null) {
            onError("РЎРµСЃСЃРёСЏ РёСЃС‚РµРєР»Р°. Р’РѕР№РґРёС‚Рµ СЃРЅРѕРІР°")
            return
        }
        viewModelScope.launch {
            _state.value = ScanState.Loading
            var localPhotoPath: String? = null
            if (photoUri != null) {
                try {
                    val fileName = "manual_${System.currentTimeMillis()}.jpg"
                    val file = java.io.File(context.filesDir, fileName)
                    context.contentResolver.openInputStream(photoUri)?.use { input ->
                        file.outputStream().use { out -> input.copyTo(out) }
                    }
                    localPhotoPath = file.absolutePath
                } catch (e: Exception) {
                    _state.value = ScanState.Idle
                    onError("РќРµ СѓРґР°Р»РѕСЃСЊ СЃРѕС…СЂР°РЅРёС‚СЊ С„РѕС‚Рѕ: ${e.message}")
                    return@launch
                }
            }
            
            val receipt = Receipt(
                userId = userId,
                purchasedAt = dateMs,
                sellerName = storeName,
                totalAmountTiyin = totalAmountTiyin,
                vatAmountTiyin = vatAmountTiyin,
                qrUrl = "manual_${System.currentTimeMillis()}_${(1000..9999).random()}", // Fake QR URL for uniqueness constraint
                isManual = true,
                manualPhotoUri = localPhotoPath
            )
            
            container.receiptRepository.insert(receipt)
                .onSuccess {
                    _state.value = ScanState.Idle
                    onSaved()
                }
                .onFailure { e ->
                    _state.value = ScanState.Idle
                    onError(e.message ?: "РћС€РёР±РєР° СЃРѕС…СЂР°РЅРµРЅРёСЏ")
                }
        }
    }

    /**
     * РЎРѕС…СЂР°РЅСЏРµС‚ С‡РµРє РґР»СЏ СѓРєР°Р·Р°РЅРЅРѕРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ (РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ Р°СѓРґРёС‚РѕСЂРѕРј РїСЂРё QR-РІРµСЂРёС„РёРєР°С†РёРё).
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ id РЅРѕРІРѕР№ Р·Р°РїРёСЃРё РёР»Рё РѕС€РёР±РєСѓ.
     */
    suspend fun saveForUser(
        parsed: ParsedReceipt,
        userId: Long,
        auditorUserId: Long? = null
    ): Result<Long> {
        if (!parsed.isValid) return Result.failure(IllegalStateException("РќРµРїРѕР»РЅС‹Рµ РґР°РЅРЅС‹Рµ С‡РµРєР°"))
        val owner = container.receiptRepository.findOwner(
            qrUrl = parsed.qrUrl,
            fiscalSign = parsed.fiscalSign,
            terminalId = parsed.terminalId,
            receiptNumber = parsed.receiptNumber,
        )
        if (owner != null && owner.userId != userId) {
            return Result.failure(
                IllegalStateException("Р”Р°РЅРЅС‹Р№ С‡РµРє СѓР¶Рµ РµСЃС‚СЊ Сѓ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ ${owner.fullName}")
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
