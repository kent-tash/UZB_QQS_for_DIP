package com.example.uzb_qqs_for_dip.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuditorSettings(
    val organizationName: String = "",
    val directorTitle: String = "Директор",
    val directorName: String = "",
    val accountantTitle: String = "Главный бухгалтер",
    val accountantName: String = "",
)

class AuditorSettingsHolder(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auditor_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AuditorSettings> = _settings.asStateFlow()

    private fun load() = AuditorSettings(
        organizationName = prefs.getString(KEY_ORG_NAME, "") ?: "",
        directorTitle = prefs.getString(KEY_DIRECTOR_TITLE, "Директор") ?: "Директор",
        directorName = prefs.getString(KEY_DIRECTOR_NAME, "") ?: "",
        accountantTitle = prefs.getString(KEY_ACCOUNTANT_TITLE, "Главный бухгалтер") ?: "Главный бухгалтер",
        accountantName = prefs.getString(KEY_ACCOUNTANT_NAME, "") ?: "",
    )

    fun save(s: AuditorSettings) {
        prefs.edit()
            .putString(KEY_ORG_NAME, s.organizationName)
            .putString(KEY_DIRECTOR_TITLE, s.directorTitle)
            .putString(KEY_DIRECTOR_NAME, s.directorName)
            .putString(KEY_ACCOUNTANT_TITLE, s.accountantTitle)
            .putString(KEY_ACCOUNTANT_NAME, s.accountantName)
            .apply()
        _settings.value = s
    }

    companion object {
        private const val KEY_ORG_NAME = "org_name"
        private const val KEY_DIRECTOR_TITLE = "director_title"
        private const val KEY_DIRECTOR_NAME = "director_name"
        private const val KEY_ACCOUNTANT_TITLE = "accountant_title"
        private const val KEY_ACCOUNTANT_NAME = "accountant_name"
    }
}
