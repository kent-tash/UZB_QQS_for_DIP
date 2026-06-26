package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.backup.AppBackup
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    fun register(
        fullName: String,
        position: String,
        initialsSurname: String,
        organization: String = "",
        autoLogin: Boolean = true,
        role: UserRole = UserRole.EMPLOYEE
    ) {
        val name = fullName.trim()
        val pos = position.trim()
        val initials = initialsSurname.trim()
        if (name.isEmpty() || pos.isEmpty() || initials.isEmpty()) {
            _registerError.value = "Заполните все поля профиля"
            return
        }
        viewModelScope.launch {
            val res = container.userRepository.create(
                User(fullName = name, position = pos, initialsSurname = initials,
                    organization = organization.trim(), role = role)
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
        organization: String = "",
        role: UserRole = UserRole.EMPLOYEE,
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
                User(id = userId, fullName = name, position = pos, initialsSurname = initials,
                    organization = organization.trim(), role = role)
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

    /** Переключает роль текущего пользователя между EMPLOYEE и AUDITOR. */
    fun switchCurrentUserRole() {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val newRole = if (user.role == UserRole.AUDITOR) UserRole.EMPLOYEE else UserRole.AUDITOR
            container.userRepository.update(user.copy(role = newRole))
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
     * Формирует JSON-бэкап во временный файл и открывает системный диалог «Поделиться».
     */
    fun shareBackup(context: Context) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val json = container.appBackup.exportJsonString()
                    val dir = File(appCtx.cacheDir, "exports").apply { mkdirs() }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    File(dir, "${AppBackup.FILE_BASE_NAME}_$stamp.json").apply {
                        writeText(json, Charsets.UTF_8)
                    }
                }
                val uri = FileProvider.getUriForFile(
                    appCtx, "${appCtx.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = AppBackup.MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    appCtx.startActivity(
                        Intent.createChooser(intent, "Поделиться данными")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appCtx,
                        e.message ?: "Не удалось поделиться данными",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Добавляет данные из файла бэкапа к текущей базе БЕЗ удаления имеющихся данных.
     * Профили сопоставляются по имени, дубликаты чеков (по ссылке) пропускаются.
     */
    fun mergeBackupFromUri(context: Context, uri: Uri) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    val bytes = appCtx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Не удалось прочитать файл")
                    String(bytes, Charsets.UTF_8)
                }
                container.appBackup.mergeJsonString(json).getOrThrow()
            }.onSuccess { outcome ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appCtx,
                        "Добавлено: профилей ${outcome.addedUsers}, чеков ${outcome.addedReceipts}" +
                            if (outcome.skippedReceipts > 0) " (пропущено дублей: ${outcome.skippedReceipts})" else "",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appCtx,
                        e.message ?: "Ошибка добавления из бэкапа",
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
