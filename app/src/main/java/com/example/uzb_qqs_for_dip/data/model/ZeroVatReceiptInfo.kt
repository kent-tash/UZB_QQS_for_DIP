package com.example.uzb_qqs_for_dip.data.model

/**
 * Информация о чеке, у которого в результате синхронизации с ОФД
 * сумма НДС обнулилась (была > 0, стала 0).
 */
data class ZeroVatReceiptInfo(
    val receiptId: Long,
    val sellerName: String,
    val purchasedAt: Long,
    val previousVatTiyin: Long,
    val currentVatTiyin: Long = 0L
)
