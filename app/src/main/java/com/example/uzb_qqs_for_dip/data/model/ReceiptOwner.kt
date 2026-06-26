package com.example.uzb_qqs_for_dip.data.model

/** Информация о владельце чека — используется при обнаружении дубликата QR. */
data class ReceiptOwner(
    val receiptId: Long,
    val userId: Long,
    val fullName: String
)
