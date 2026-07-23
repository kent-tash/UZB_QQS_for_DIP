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
import com.example.uzb_qqs_for_dip.data.model.AuditDeclaration
import com.example.uzb_qqs_for_dip.data.model.AuditStatus
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.repository.UserReceiptStats
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.network.ParsedReceipt
import com.example.uzb_qqs_for_dip.render.QrFromImageDecoder
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

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            container.receiptRepository.receipts
                .drop(1)
                .debounce(300)
                .collect {
                    refreshEmployeeData()
                }
        }
    }

    fun setPeriod(quarter: Quarter, year: Int) {
        _quarter.value = quarter
        _year.value = year
    }

    fun selectEmployee(user: User) {
        _selectedEmployee.value = user
        _verifyResult.value = VerifyResult.Idle
        viewModelScope.launch { refreshEmployeeData() }
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
