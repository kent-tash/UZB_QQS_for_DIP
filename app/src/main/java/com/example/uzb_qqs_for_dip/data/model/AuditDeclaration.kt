package com.example.uzb_qqs_for_dip.data.model

enum class AuditStatus {
    PENDING,      // ещё не проверено
    APPROVED,     // принято
    REVISION,     // на доработку
    CONFLICT;     // есть конфликты QR
}

/**
 * Запись об итогах сотрудника, заявленных в бумажном PDF и введённых аудитором вручную.
 *
 * @property declaredTotalTiyin  Общая сумма из строки «Итого» в принесённом PDF.
 * @property declaredVatTiyin    Сумма НДС из строки «Итого» в принесённом PDF.
 * @property declaredCount       Количество чеков в принесённом PDF (для контроля полноты QR-проверки).
 */
data class AuditDeclaration(
    val id: Long = 0,
    val userId: Long,
    val year: Int,
    val quarter: String,
    val declaredTotalTiyin: Long = 0L,
    val declaredVatTiyin: Long = 0L,
    val declaredCount: Int = 0,
    val status: AuditStatus = AuditStatus.PENDING,
    val note: String? = null,
    val checkedAt: Long? = null
)
