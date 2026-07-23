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
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.model.AuditDeclaration
import com.example.uzb_qqs_for_dip.data.model.AuditStatus
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.repository.UserReceiptStats
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.network.ParsedReceipt
import com.example.uzb_qqs_for_dip.render.QrFromImageDecoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Результат скана в контексте аудиторской проверки. */
sealed interface VerifyResult {
    data object Idle : VerifyResult
    data object Loading : VerifyResult
    data class Success(
        val parsed: ParsedReceipt,
        val owner: ReceiptOwner?,
        /** Чек уже принадлежит именно проверяемому сотруднику. */
        val alreadyForThisEmployee: Boolean,
        /** Чек за пределами выбранного квартала. */
        val outOfPeriod: Boolean,
        /** Чек был только что сохранён или уже существовал — и помечен как проверенный. */
        val markedVerified: Boolean
    ) : VerifyResult
    data class Error(val message: String) : VerifyResult
}

/** Статус одного QR с листа при пакетной проверке. */
enum class SheetItemStatus {
    NEW,
    ALREADY_THIS,
    OTHER_OWNER,
    ERROR,
    OUT_OF_PERIOD
}

data class SheetReceiptItem(
    val qrUrl: String,
    val parsed: ParsedReceipt? = null,
    val status: SheetItemStatus,
    val ownerName: String? = null,
    val selected: Boolean = false,
    val errorMessage: String? = null
)

data class SheetSummary(
    val scanned: Int,
    val saved: Int,
    val alreadyVerified: Int,
    val conflicts: Int,
    val errors: Int,
    val skipped: Int,
    val message: String
)

class AuditorVerifyViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as QqsApp).container

    // Показываем всех пользователей (включая тех, кто перешёл в роль AUDITOR),
    // т.к. у них тоже могут быть чеки, которые нужно проверить.
    val employees: StateFlow<List<User>> = container.userRepository.users.let { flow ->
        MutableStateFlow<List<User>>(emptyList()).also { mutable ->
            viewModelScope.launch {
                flow.collect { all -> mutable.value = all }
            }
        }
    }

    private val _selectedEmployee = MutableStateFlow<User?>(null)
    val selectedEmployee: StateFlow<User?> = _selectedEmployee.asStateFlow()

    private val _verifyResult = MutableStateFlow<VerifyResult>(VerifyResult.Idle)
    val verifyResult: StateFlow<VerifyResult> = _verifyResult.asStateFlow()

    /** Актуальная статистика проверки из БД за выбранный период. */
    private val _employeeStats = MutableStateFlow(UserReceiptStats(0, 0, 0L, 0L, 0L, 0L))
    val employeeStats: StateFlow<UserReceiptStats> = _employeeStats.asStateFlow()

    /** Запись о ручной проверке / итогах из PDF за квартал. */
    private val _declaration = MutableStateFlow<AuditDeclaration?>(null)
    val declaration: StateFlow<AuditDeclaration?> = _declaration.asStateFlow()

    private val _addEmployeeError = MutableStateFlow<String?>(null)
    val addEmployeeError: StateFlow<String?> = _addEmployeeError.asStateFlow()

    private val _quarter = MutableStateFlow(ReportSettings.currentQuarter())
    val quarter: StateFlow<Quarter> = _quarter.asStateFlow()

    private val _year = MutableStateFlow(ReportSettings.currentYear())
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _autoVerifyMessage = MutableStateFlow<String?>(null)
    val autoVerifyMessage: StateFlow<String?> = _autoVerifyMessage.asStateFlow()

    private val _manualVerifyMessage = MutableStateFlow<String?>(null)
    val manualVerifyMessage: StateFlow<String?> = _manualVerifyMessage.asStateFlow()

    private val _receiptSearchQuery = MutableStateFlow("")
    val receiptSearchQuery: StateFlow<String> = _receiptSearchQuery.asStateFlow()

    private val _receiptSearchResults = MutableStateFlow<List<ReceiptWithUser>>(emptyList())
    val receiptSearchResults: StateFlow<List<ReceiptWithUser>> = _receiptSearchResults.asStateFlow()

    private val _sheetPreviewItems = MutableStateFlow<List<SheetReceiptItem>>(emptyList())
    val sheetPreviewItems: StateFlow<List<SheetReceiptItem>> = _sheetPreviewItems.asStateFlow()

    private val _sheetSummary = MutableStateFlow<SheetSummary?>(null)
    val sheetSummary: StateFlow<SheetSummary?> = _sheetSummary.asStateFlow()

    private val _sheetLoading = MutableStateFlow(false)
    val sheetLoading: StateFlow<Boolean> = _sheetLoading.asStateFlow()

    private var receiptSearchJob: Job? = null

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            container.receiptRepository.receipts
                .drop(1)
                .debounce(300)
                .collect {
                    refreshEmployeeData()
                    if (_receiptSearchQuery.value.isNotBlank()) {
                        runReceiptSearch(_receiptSearchQuery.value)
                    }
                }
        }
    }

    fun setPeriod(quarter: Quarter, year: Int) {
        _quarter.value = quarter
        _year.value = year
        if (_receiptSearchQuery.value.isNotBlank()) {
            setReceiptSearchQuery(_receiptSearchQuery.value)
        }
    }

    fun selectEmployee(user: User) {
        _selectedEmployee.value = user
        _verifyResult.value = VerifyResult.Idle
        _receiptSearchQuery.value = ""
        _receiptSearchResults.value = emptyList()
        _sheetPreviewItems.value = emptyList()
        _sheetSummary.value = null
        viewModelScope.launch { refreshEmployeeData() }
    }

    fun setReceiptSearchQuery(value: String) {
        _receiptSearchQuery.value = value
        receiptSearchJob?.cancel()
        receiptSearchJob = viewModelScope.launch {
            delay(350)
            runReceiptSearch(value)
        }
    }

    private suspend fun runReceiptSearch(raw: String) {
        val employee = _selectedEmployee.value
        val q = raw.trim()
        if (employee == null || q.isEmpty()) {
            _receiptSearchResults.value = emptyList()
            return
        }
        val (from, to) = periodBounds()
        _receiptSearchResults.value = container.receiptRepository.search(
            query = q,
            userId = employee.id,
            fromMs = from,
            toMs = to
        )
    }

    fun markReceiptVerifiedFromSearch(receiptId: Long) {
        val auditorId = container.sessionManager.currentUserId.value ?: return
        viewModelScope.launch {
            container.receiptRepository.markVerified(receiptId, auditorId)
            refreshEmployeeData()
            if (_receiptSearchQuery.value.isNotBlank()) {
                runReceiptSearch(_receiptSearchQuery.value)
            }
        }
    }

    fun clearAutoVerifyMessage() { _autoVerifyMessage.value = null }

    fun clearManualVerifyMessage() { _manualVerifyMessage.value = null }

    fun clearVerifyResult() { _verifyResult.value = VerifyResult.Idle }

    fun clearAddEmployeeError() { _addEmployeeError.value = null }

    private fun periodBounds(): Pair<Long, Long> {
        val y = _year.value
        val q = _quarter.value
        return ReportSettings.quarterStart(y, q) to ReportSettings.quarterEnd(y, q)
    }

    fun refreshEmployeeStats() {
        viewModelScope.launch { refreshEmployeeData() }
    }

    private suspend fun refreshEmployeeData() {
        _employeeStats.value = loadEmployeeStats()
        _declaration.value = loadDeclaration()
    }

    private suspend fun loadEmployeeStats(): UserReceiptStats {
        val employee = _selectedEmployee.value
            ?: return UserReceiptStats(0, 0, 0L, 0L, 0L, 0L)
        val (from, to) = periodBounds()
        return container.receiptRepository.getUserPeriodStats(employee.id, from, to)
    }

    private suspend fun loadDeclaration(): AuditDeclaration? {
        val employee = _selectedEmployee.value ?: return null
        return container.auditorRepository.getDeclaration(
            employee.id, _year.value, _quarter.value.name
        )
    }

    /**
     * Отмечает сотрудника как проверенного вручную (чеки на бумаге, не в приложении).
     */
    fun markManuallyVerified() {
        val employee = _selectedEmployee.value ?: run {
            _manualVerifyMessage.value = "Сначала выберите сотрудника"
            return
        }
        viewModelScope.launch {
            val existing = loadDeclaration()
            val decl = AuditDeclaration(
                id = existing?.id ?: 0,
                userId = employee.id,
                year = _year.value,
                quarter = _quarter.value.name,
                declaredTotalTiyin = existing?.declaredTotalTiyin ?: 0L,
                declaredVatTiyin = existing?.declaredVatTiyin ?: 0L,
                declaredCount = existing?.declaredCount ?: 0,
                status = AuditStatus.APPROVED,
                note = existing?.note?.takeIf { it.isNotBlank() } ?: "Проверено вручную",
                checkedAt = System.currentTimeMillis()
            )
            container.auditorRepository.upsertDeclaration(decl)
                .onSuccess {
                    _declaration.value = decl
                    _manualVerifyMessage.value =
                        "Сотрудник ${employee.fullName} отмечен как проверенный вручную"
                }
                .onFailure { e ->
                    _manualVerifyMessage.value = "Ошибка: ${e.message}"
                }
        }
    }

    /**
     * Автоматически помечает все непроверенные чеки выбранного сотрудника
     * за текущий квартал как проверенные аудитором.
     */
    fun autoVerifyAll() {
        val employee = _selectedEmployee.value ?: run {
            _autoVerifyMessage.value = "Сначала выберите сотрудника"
            return
        }
        val auditorId = container.sessionManager.currentUserId.value ?: run {
            _autoVerifyMessage.value = "Не удалось определить аудитора. Войдите в профиль аудитора."
            return
        }
        val (from, to) = periodBounds()
        viewModelScope.launch {
            val result = container.receiptRepository.markAllVerifiedForUser(
                userId = employee.id,
                auditorUserId = auditorId,
                fromMs = from,
                toMs = to
            )
            result.onSuccess { count ->
                val stats = loadEmployeeStats()
                _employeeStats.value = stats
                _autoVerifyMessage.value = when {
                    count > 0 -> "Отмечено проверенными: $count чеков"
                    stats.totalCount == 0 -> "У сотрудника нет чеков за выбранный период"
                    stats.verifiedCount >= stats.totalCount ->
                        "Все чеки сотрудника уже проверены (${stats.verifiedCount}/${stats.totalCount})"
                    else -> "Нет непроверенных чеков за выбранный период"
                }
            }.onFailure { e ->
                _autoVerifyMessage.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun addEmployee(fullName: String, position: String, initialsSurname: String) {
        val name = fullName.trim(); val pos = position.trim(); val ini = initialsSurname.trim()
        if (name.isEmpty() || pos.isEmpty() || ini.isEmpty()) {
            _addEmployeeError.value = "Заполните все поля"
            return
        }
        viewModelScope.launch {
            val result = container.userRepository.create(
                User(fullName = name, position = pos, initialsSurname = ini, role = UserRole.EMPLOYEE)
            )
            result.onSuccess { id ->
                _addEmployeeError.value = null
                val newUser = container.userRepository.getById(id)
                if (newUser != null) selectEmployee(newUser)
            }.onFailure { e ->
                _addEmployeeError.value = if (e.message?.contains("UNIQUE", true) == true)
                    "Сотрудник с таким именем уже существует"
                else "Не удалось создать профиль: ${e.message}"
            }
        }
    }

    fun handleImageFromGallery(context: Context, uri: Uri) {
        viewModelScope.launch {
            _verifyResult.value = VerifyResult.Loading
            runCatching { QrFromImageDecoder.decode(context, uri) }
                .onSuccess { payload -> handleScan(payload) }
                .onFailure { e ->
                    _verifyResult.value = VerifyResult.Error(
                        e.message ?: "Не удалось распознать QR на изображении"
                    )
                }
        }
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
        if (item.status == SheetItemStatus.OTHER_OWNER) return
        list[index] = item.copy(selected = !item.selected)
        _sheetPreviewItems.value = list
    }

    /**
     * Декодирует все QR с фото листа, для каждого URL парсит чек и ищет владельца
     * без вставки в БД — результат попадает в [sheetPreviewItems].
     */
    fun prepareSheetFromUri(context: Context, uri: Uri) {
        val employee = _selectedEmployee.value ?: run {
            _sheetSummary.value = SheetSummary(
                scanned = 0, saved = 0, alreadyVerified = 0, conflicts = 0, errors = 1, skipped = 0,
                message = "Сначала выберите сотрудника"
            )
            return
        }
        viewModelScope.launch {
            _sheetLoading.value = true
            _sheetSummary.value = null
            _sheetPreviewItems.value = emptyList()
            try {
                val urls = runCatching { QrFromImageDecoder.decodeAll(context, uri) }
                    .getOrElse { e ->
                        _sheetSummary.value = SheetSummary(
                            scanned = 0, saved = 0, alreadyVerified = 0, conflicts = 0,
                            errors = 1, skipped = 0,
                            message = e.message ?: "Не удалось распознать QR на листе"
                        )
                        return@launch
                    }
                val (from, to) = periodBounds()
                val items = urls.map { raw ->
                    buildSheetItem(raw, employee, from, to)
                }
                _sheetPreviewItems.value = items
            } finally {
                _sheetLoading.value = false
            }
        }
    }

    private suspend fun buildSheetItem(
        raw: String,
        employee: User,
        from: Long,
        to: Long
    ): SheetReceiptItem {
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
                    val outOfPeriod = parsed.purchasedAt?.let { it < from || it > to } ?: false

                    when {
                        existingOwner != null && existingOwner.userId != employee.id ->
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
                        outOfPeriod ->
                            SheetReceiptItem(
                                qrUrl = parsed.qrUrl,
                                parsed = parsed,
                                status = SheetItemStatus.OUT_OF_PERIOD,
                                ownerName = existingOwner?.fullName,
                                selected = false
                            )
                        existingOwner?.userId == employee.id ->
                            SheetReceiptItem(
                                qrUrl = parsed.qrUrl,
                                parsed = parsed,
                                status = SheetItemStatus.ALREADY_THIS,
                                ownerName = existingOwner.fullName,
                                selected = true
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
     * Сохраняет выбранные NEW (insert + markVerified), помечает ALREADY_THIS,
     * OTHER_OWNER пропускает. Затем очищает превью и выставляет [sheetSummary].
     */
    fun confirmSheetSelection() {
        val employee = _selectedEmployee.value ?: return
        val auditorId = container.sessionManager.currentUserId.value
        val items = _sheetPreviewItems.value
        if (items.isEmpty()) return

        viewModelScope.launch {
            _sheetLoading.value = true
            var saved = 0
            var alreadyVerified = 0
            var conflicts = 0
            var errors = 0
            var skipped = 0

            for (item in items) {
                when {
                    item.status == SheetItemStatus.OTHER_OWNER -> {
                        conflicts++
                    }
                    !item.selected -> {
                        skipped++
                    }
                    item.status == SheetItemStatus.ALREADY_THIS -> {
                        val parsed = item.parsed
                        if (parsed == null || auditorId == null) {
                            errors++
                            continue
                        }
                        val owner = container.receiptRepository.findOwner(
                            qrUrl = parsed.qrUrl,
                            fiscalSign = parsed.fiscalSign,
                            terminalId = parsed.terminalId,
                            receiptNumber = parsed.receiptNumber,
                        )
                        if (owner != null && owner.userId == employee.id) {
                            container.receiptRepository.markVerified(owner.receiptId, auditorId)
                            alreadyVerified++
                        } else {
                            errors++
                        }
                    }
                    item.status == SheetItemStatus.NEW ||
                        item.status == SheetItemStatus.OUT_OF_PERIOD -> {
                        val parsed = item.parsed
                        if (parsed == null || !parsed.isValid) {
                            errors++
                            continue
                        }
                        val receipt = Receipt(
                            userId = employee.id,
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
                        val insertResult = container.receiptRepository.insert(receipt)
                        if (insertResult.isFailure) {
                            val ownerAfterFail = container.receiptRepository.findOwner(
                                qrUrl = parsed.qrUrl,
                                fiscalSign = parsed.fiscalSign,
                                terminalId = parsed.terminalId,
                                receiptNumber = parsed.receiptNumber,
                            )
                            when {
                                ownerAfterFail != null && ownerAfterFail.userId != employee.id ->
                                    conflicts++
                                ownerAfterFail != null && ownerAfterFail.userId == employee.id &&
                                    auditorId != null -> {
                                    container.receiptRepository.markVerified(
                                        ownerAfterFail.receiptId, auditorId
                                    )
                                    alreadyVerified++
                                }
                                else -> errors++
                            }
                        } else {
                            if (auditorId != null) {
                                insertResult.getOrNull()?.let { id ->
                                    container.receiptRepository.markVerified(id, auditorId)
                                }
                            }
                            saved++
                        }
                    }
                    else -> skipped++
                }
            }

            refreshEmployeeData()
            val scanned = items.size
            val message = buildString {
                append("Сканировано: $scanned")
                append(". Сохранено: $saved")
                append(". Уже в базе: $alreadyVerified")
                append(". Конфликты: $conflicts")
                if (errors > 0) append(". Ошибки: $errors")
                if (skipped > 0) append(". Пропущено: $skipped")
            }
            _sheetSummary.value = SheetSummary(
                scanned = scanned,
                saved = saved,
                alreadyVerified = alreadyVerified,
                conflicts = conflicts,
                errors = errors,
                skipped = skipped,
                message = message
            )
            _sheetPreviewItems.value = emptyList()
            _sheetLoading.value = false
        }
    }

    fun handleScan(qrPayload: String?) {
        val raw = qrPayload?.trim().orEmpty()
        if (raw.isEmpty()) { _verifyResult.value = VerifyResult.Error("Пустой QR-код"); return }
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            _verifyResult.value = VerifyResult.Error("QR не содержит ссылку на чек")
            return
        }
        val employee = _selectedEmployee.value ?: run {
            _verifyResult.value = VerifyResult.Error("Сначала выберите сотрудника")
            return
        }
        viewModelScope.launch {
            _verifyResult.value = VerifyResult.Loading
            val auditorId = container.sessionManager.currentUserId.value
            val (from, to) = periodBounds()

            container.receiptParser.fetchAndParse(raw)
                .onSuccess { parsed ->
                    val existingOwner = container.receiptRepository.findOwner(
                        qrUrl = parsed.qrUrl,
                        fiscalSign = parsed.fiscalSign,
                        terminalId = parsed.terminalId,
                        receiptNumber = parsed.receiptNumber,
                    )
                    val alreadyForThisEmployee = existingOwner?.userId == employee.id

                    val outOfPeriod = parsed.purchasedAt?.let { it < from || it > to } ?: false

                    when {
                        existingOwner != null && existingOwner.userId != employee.id -> {
                            _verifyResult.value = VerifyResult.Success(
                                parsed = parsed,
                                owner = existingOwner,
                                alreadyForThisEmployee = false,
                                outOfPeriod = outOfPeriod,
                                markedVerified = false
                            )
                        }
                        alreadyForThisEmployee -> {
                            if (auditorId != null) {
                                container.receiptRepository.markVerified(existingOwner!!.receiptId, auditorId)
                            }
                            _verifyResult.value = VerifyResult.Success(
                                parsed = parsed,
                                owner = existingOwner,
                                alreadyForThisEmployee = true,
                                outOfPeriod = outOfPeriod,
                                markedVerified = auditorId != null
                            )
                            refreshEmployeeData()
                        }
                        else -> {
                            if (parsed.isValid) {
                                val receipt = Receipt(
                                    userId = employee.id,
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
                                val insertResult = container.receiptRepository.insert(receipt)
                                if (insertResult.isFailure) {
                                    val err = insertResult.exceptionOrNull()
                                    val ownerAfterFail = container.receiptRepository.findOwner(
                                        qrUrl = parsed.qrUrl,
                                        fiscalSign = parsed.fiscalSign,
                                        terminalId = parsed.terminalId,
                                        receiptNumber = parsed.receiptNumber,
                                    )
                                    _verifyResult.value = when {
                                        ownerAfterFail != null && ownerAfterFail.userId != employee.id ->
                                            VerifyResult.Success(
                                                parsed = parsed,
                                                owner = ownerAfterFail,
                                                alreadyForThisEmployee = false,
                                                outOfPeriod = outOfPeriod,
                                                markedVerified = false
                                            )
                                        err?.message?.contains("UNIQUE", true) == true ->
                                            VerifyResult.Error("Этот чек уже есть в базе")
                                        else ->
                                            VerifyResult.Error(
                                                "Не удалось сохранить чек: ${err?.message ?: "ошибка"}"
                                            )
                                    }
                                    return@onSuccess
                                }
                                if (auditorId != null) {
                                    insertResult.getOrNull()?.let { id ->
                                        container.receiptRepository.markVerified(id, auditorId)
                                    }
                                }
                                _verifyResult.value = VerifyResult.Success(
                                    parsed = parsed,
                                    owner = null,
                                    alreadyForThisEmployee = false,
                                    outOfPeriod = outOfPeriod,
                                    markedVerified = auditorId != null
                                )
                                refreshEmployeeData()
                            } else {
                                _verifyResult.value = VerifyResult.Error("Не все поля чека распознаны")
                            }
                        }
                    }
                }
                .onFailure { e ->
                    _verifyResult.value = VerifyResult.Error(
                        "Не удалось загрузить чек: ${e.message ?: e::class.simpleName}"
                    )
                }
        }
    }
}
