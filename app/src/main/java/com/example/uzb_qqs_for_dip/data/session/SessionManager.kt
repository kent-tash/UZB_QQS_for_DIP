package com.example.uzb_qqs_for_dip.data.session

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранит идентификатор активного пользователя в SharedPreferences и публикует его как Flow.
 */
class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentUserId = MutableStateFlow(loadId())
    val currentUserId: StateFlow<Long?> = _currentUserId.asStateFlow()

    private fun loadId(): Long? {
        if (!prefs.contains(KEY_USER_ID)) return null
        val raw = prefs.getLong(KEY_USER_ID, -1L)
        return if (raw <= 0) null else raw
    }

    fun login(userId: Long) {
        prefs.edit { putLong(KEY_USER_ID, userId) }
        _currentUserId.value = userId
    }

    fun logout() {
        prefs.edit { remove(KEY_USER_ID) }
        _currentUserId.value = null
    }

    private companion object {
        const val PREFS_NAME = "qqs_session"
        const val KEY_USER_ID = "current_user_id"
    }
}
