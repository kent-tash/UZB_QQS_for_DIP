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
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.network.ParsedReceipt
import com.example.uzb_qqs_for_dip.render.QrFromImageDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _verifiedCount = MutableStateFlow(0)
    val verifiedCount: StateFlow<Int> = _verifiedCount.asStateFlow()

    private val _verifiedTotal = MutableStateFlow(0L)
    val verifiedTotal: StateFlow<Long> = _verifiedTotal.asStateFlow()

    private val _verifiedVat = MutableStateFlow(0L)
    val verifiedVat: StateFlow<Long> = _verifiedVat.asStateFlow()

    private val _addEmployeeError = MutableStateFlow<String?>(null)
    val addEmployeeError: StateFlow<String?> = _addEmployeeError.asStateFlow()

    private val _quarter = MutableStateFlow(ReportSettings.currentQuarter())
    val quarter: StateFlow<Quarter> = _quarter.asStateFlow()

    private val _year = MutableStateFlow(ReportSettings.currentYear())
    val year: StateFlow<Int> = _year.asStateFlow()

    fun selectEmployee(user: User) {
        _selectedEmployee.value = user
        _verifyResult.value = VerifyResult.Idle
        resetCounters()
    }

    private val _autoVerifyMessage = MutableStateFlow<String?>(null)
    val autoVerifyMessage: StateFlow<String?> = _autoVerifyMessage.asStateFlow()

    fun clearAutoVerifyMessage() { _autoVerifyMessage.value = null }

    fun clearVerifyResult() { _verifyResult.value = VerifyResult.Idle }

    fun clearAddEmployeeError() { _addEmployeeError.value = null }

    /**
     * Автоматически помечает все непроверенные чеки выбранного сотрудника
     * за текущий квартал как проверенные аудитором.
     */
    fun autoVerifyAll() {
        val employee = _selectedEmployee.value ?: run {
            _autoVerifyMessage.value = "Сначала выберите сотрудника"
            return
        }
        val auditorId = container.sessionManager.currentUserId.value ?: return
        val q = _quarter.value; val y = _year.value
        val from = ReportSettings.quarterStart(y, q)
        val to = ReportSettings.quarterEnd(y, q)
        viewModelScope.launch {
            val result = container.receiptRepository.markAllVerifiedForUser(
                userId = employee.id,
                auditorUserId = auditorId,
                fromMs = from,
                toMs = to
            )
            result.onSuccess { count ->
                _autoVerifyMessage.value =
                    if (count > 0) "Отмечено проверенными: $count чеков"
                    else "Все чеки сотрудника уже проверены"
                if (count > 0) {
                    _verifiedCount.value += count
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
            val q = _quarter.value; val y = _year.value
            val from = ReportSettings.quarterStart(y, q)
            val to = ReportSettings.quarterEnd(y, q)

            container.receiptParser.fetchAndParse(raw)
                .onSuccess { parsed ->
                    val existingOwner = container.receiptRepository.findOwnerByQrUrl(raw)
                    val alreadyForThisEmployee = existingOwner?.userId == employee.id

                    val outOfPeriod = parsed.purchasedAt?.let { it < from || it > to } ?: false

                    when {
                        existingOwner != null && existingOwner.userId != employee.id -> {
                            // Чек у другого сотрудника
                            _verifyResult.value = VerifyResult.Success(
                                parsed = parsed,
                                owner = existingOwner,
                                alreadyForThisEmployee = false,
                                outOfPeriod = outOfPeriod,
                                markedVerified = false
                            )
                        }
                        alreadyForThisEmployee -> {
                            // Чек уже у этого сотрудника — пометить как проверенный
                            if (auditorId != null) {
                                container.receiptRepository.markVerified(existingOwner!!.receiptId, auditorId)
                            }
                            _verifyResult.value = VerifyResult.Success(
                                parsed = parsed,
                                owner = existingOwner,
                                alreadyForThisEmployee = true,
                                outOfPeriod = outOfPeriod,
                                markedVerified = true
                            )
                            accumulateCounts(parsed)
                        }
                        else -> {
                            // Новый чек — сохранить с user_id сотрудника и пометить проверенным
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
                                if (insertResult.isSuccess && auditorId != null) {
                                    insertResult.getOrNull()?.let { id ->
                                        container.receiptRepository.markVerified(id, auditorId)
                                    }
                                }
                                _verifyResult.value = VerifyResult.Success(
                                    parsed = parsed,
                                    owner = null,
                                    alreadyForThisEmployee = false,
                                    outOfPeriod = outOfPeriod,
                                    markedVerified = insertResult.isSuccess
                                )
                                if (insertResult.isSuccess) accumulateCounts(parsed)
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

    private fun accumulateCounts(parsed: ParsedReceipt) {
        _verifiedCount.value += 1
        _verifiedTotal.value += parsed.totalAmountTiyin ?: 0L
        _verifiedVat.value += parsed.vatAmountTiyin ?: 0L
    }

    private fun resetCounters() {
        _verifiedCount.value = 0
        _verifiedTotal.value = 0L
        _verifiedVat.value = 0L
    }
}
