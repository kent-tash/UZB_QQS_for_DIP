package com.example.uzb_qqs_for_dip.data.model

/**
 * Способ оплаты чека.
 */
enum class PaymentType(val label: String) {
    CASH("Наличные"),
    CARD("Карта");

    companion object {
        /** Читает значение из БД; старые записи могли не иметь payment_type. */
        fun fromDb(value: String?): PaymentType =
            if (value.isNullOrBlank()) CARD
            else runCatching { valueOf(value) }.getOrDefault(CARD)
    }
}
