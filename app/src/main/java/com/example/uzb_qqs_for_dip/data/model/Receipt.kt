package com.example.uzb_qqs_for_dip.data.model

/**
 * Запись о фискальном чеке Узбекистана, привязанная к пользователю.
 *
 * Все денежные суммы хранятся в тийинах (минимальная единица), чтобы избежать ошибок
 * округления double-арифметики. Преобразование выполняется через
 * [com.example.uzb_qqs_for_dip.util.MoneyFormat].
 */
data class Receipt(
    val id: Long = 0,
    val userId: Long,
    val purchasedAt: Long,
    val sellerName: String,
    val totalAmountTiyin: Long,
    val vatAmountTiyin: Long,
    val qrUrl: String,
    val rawText: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** То же, что [Receipt], но с полями пользователя — для отображения в таблице. */
data class ReceiptWithUser(
    val receipt: Receipt,
    val userFullName: String,
    val userPosition: String,
    val userInitialsSurname: String
)
