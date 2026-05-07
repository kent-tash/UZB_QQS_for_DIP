package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Корневой ViewModel: знает текущего пользователя и список зарегистрированных пользователей,
 * умеет логинить/выводить из системы. Используется на экране авторизации и навигационным графом.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as QqsApp).container

    val users: StateFlow<List<User>> = container.userRepository.users
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentUser: StateFlow<User?> = combine(
        container.sessionManager.currentUserId,
        users
    ) { id, list -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _registerError = MutableStateFlow<String?>(null)
    val registerError: StateFlow<String?> = _registerError.asStateFlow()

    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError.asStateFlow()

    fun login(userId: Long) = container.sessionManager.login(userId)

    fun logout() = container.sessionManager.logout()

    fun register(fullName: String, position: String, initialsSurname: String, autoLogin: Boolean = true) {
        val name = fullName.trim()
        val pos = position.trim()
        val initials = initialsSurname.trim()
        if (name.isEmpty() || pos.isEmpty() || initials.isEmpty()) {
            _registerError.value = "Заполните все поля профиля"
            return
        }
        viewModelScope.launch {
            val res = container.userRepository.create(
                User(fullName = name, position = pos, initialsSurname = initials)
            )
            res.onSuccess { id ->
                _registerError.value = null
                if (autoLogin) container.sessionManager.login(id)
            }.onFailure { e ->
                _registerError.value = if (e.message?.contains("UNIQUE", true) == true)
                    "Пользователь с таким именем уже существует"
                else "Не удалось сохранить профиль: ${e.message}"
            }
        }
    }

    fun clearRegisterError() {
        _registerError.value = null
    }

    fun clearEditError() {
        _editError.value = null
    }

    /**
     * Обновляет существующий профиль. На UI вызывается из ProfileScreen.
     * Возвращаемый Job можно слушать, но проще ориентироваться на [editError].
     */
    fun updateProfile(
        userId: Long,
        fullName: String,
        position: String,
        initialsSurname: String,
        onDone: () -> Unit = {}
    ) {
        val name = fullName.trim()
        val pos = position.trim()
        val initials = initialsSurname.trim()
        if (name.isEmpty() || pos.isEmpty() || initials.isEmpty()) {
            _editError.value = "Заполните все поля профиля"
            return
        }
        viewModelScope.launch {
            val res = container.userRepository.update(
                User(id = userId, fullName = name, position = pos, initialsSurname = initials)
            )
            res.onSuccess {
                _editError.value = null
                onDone()
            }.onFailure { e ->
                _editError.value = if (e.message?.contains("UNIQUE", true) == true)
                    "Пользователь с таким именем уже существует"
                else "Не удалось обновить профиль: ${e.message}"
            }
        }
    }

    /**
     * Удаляет профиль и все связанные с ним чеки (CASCADE на уровне БД).
     * Если удаляемый пользователь — текущий, выполняем logout, чтобы UI вернулся
     * на экран авторизации.
     */
    fun deleteProfile(userId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrent = container.sessionManager.currentUserId.value == userId
            container.userRepository.delete(userId)
            // Каскад БД удалил чеки — обновим in-memory кэш репозитория.
            container.receiptRepository.refresh()
            if (isCurrent) container.sessionManager.logout()
            onDone()
        }
    }

    /**
     * Сохраняет один JSON-файл: все профили, чеки и настройки отчёта.
     */
    fun exportBackupToUri(context: Context, uri: Uri) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val json = container.appBackup.exportJsonString()
                    appCtx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Не удалось записать файл")
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "Бэкап сохранён", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appCtx,
                        e.message ?: "Ошибка сохранения бэкапа",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Полностью заменяет локальные данные содержимым файла. При необходимости переводит на экран входа.
     */
    fun importBackupFromUri(
        context: Context,
        uri: Uri,
        onNavigateToAuth: () -> Unit
    ) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            runCatching {
                val prevSession = container.sessionManager.currentUserId.value
                val json = withContext(Dispatchers.IO) {
                    val bytes = appCtx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Не удалось прочитать файл")
                    String(bytes, Charsets.UTF_8)
                }

                val outcome = container.appBackup.importJsonString(json, prevSession).getOrThrow()

                withContext(Dispatchers.Main.immediate) {
                    val newId = outcome.newSessionUserId
                    if (newId != null) {
                        container.sessionManager.login(newId)
                        Toast.makeText(appCtx, "Бэкап восстановлен", Toast.LENGTH_SHORT).show()
                    } else {
                        container.sessionManager.logout()
                        Toast.makeText(appCtx, "Бэкап восстановлен. Выберите профиль.", Toast.LENGTH_LONG).show()
                        onNavigateToAuth()
                    }
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appCtx,
                        e.message ?: "Ошибка восстановления из бэкапа",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
