package com.example.uzb_qqs_for_dip.data.model

/**
 * Профиль пользователя приложения.
 *
 * @property fullName Полное имя пользователя (для входа и отображения).
 * @property position Должность (используется в шапке и блоке подписи отчёта).
 * @property initialsSurname Запись вида "И.О. Фамилия" — подставляется в подпись PDF.
 */
data class User(
    val id: Long = 0,
    val fullName: String,
    val position: String,
    val initialsSurname: String,
    val createdAt: Long = System.currentTimeMillis()
)
