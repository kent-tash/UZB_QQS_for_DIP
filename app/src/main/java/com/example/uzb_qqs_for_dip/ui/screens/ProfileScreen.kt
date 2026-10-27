package com.example.uzb_qqs_for_dip.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.ui.components.UserManagementBottomSheet
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uzb_qqs_for_dip.data.backup.AppBackup
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    appViewModel: AppViewModel,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val user by appViewModel.currentUser.collectAsStateWithLifecycle()
    val users by appViewModel.users.collectAsStateWithLifecycle()
    val editError by appViewModel.editError.collectAsStateWithLifecycle()

    var showUserManagementSheet by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<EditableProfileField?>(null) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var profileToDeleteStage1 by remember { mutableStateOf<User?>(null) }
    var profileToDeleteStage2 by remember { mutableStateOf<User?>(null) }
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var showReplaceWarning by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    LaunchedEffect(appViewModel.scrollToTopEvent) {
        appViewModel.scrollToTopEvent.collect { route ->
            if (route == "main/profile") {
                scrollState.animateScrollTo(0)
            }
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AppBackup.MIME_TYPE)
    ) { uri ->
        if (uri != null) appViewModel.exportBackupToUri(context, uri)
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedBackupUri = uri
            showReplaceWarning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showUserManagementSheet = true }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user?.fullName?.take(1)?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                user?.fullName ?: "Не авторизован",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Outlined.SwapHoriz,
                                contentDescription = "Сменить профиль",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        user?.position?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                ProfileRow(
                    label = "Имя",
                    value = user?.fullName ?: "—",
                    onClick = {
                        user?.let { editingField = EditableProfileField.FULL_NAME }
                    }
                )
                ProfileRow(
                    label = "Должность",
                    value = user?.position ?: "—",
                    onClick = {
                        user?.let { editingField = EditableProfileField.POSITION }
                    }
                )
                ProfileRow(
                    label = "Организация",
                    value = user?.organization?.ifBlank { "—" } ?: "—",
                    onClick = {
                        user?.let { editingField = EditableProfileField.ORGANIZATION }
                    }
                )
            }
        }

        // Role switch card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Режим работы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (user?.role == UserRole.AUDITOR)
                        "Вы работаете в режиме «Аудитор». Доступна вкладка «Аудит» для квартальной сверки."
                    else
                        "Вы работаете в режиме «Сотрудник». Включите режим аудитора, чтобы получить доступ к вкладке «Аудит».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { appViewModel.switchCurrentUserRole() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text(
                        if (user?.role == UserRole.AUDITOR)
                            "Переключить на «Сотрудник»"
                        else
                            "Переключить на «Аудитор»"
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Резервная копия",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Один файл JSON: все профили, сохранённые чеки и настройки отчёта.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        createBackupLauncher.launch("${AppBackup.FILE_BASE_NAME}_$stamp.json")
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Создать бэкап")
                }
                Button(
                    onClick = {
                        openBackupLauncher.launch(arrayOf(AppBackup.MIME_TYPE, "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Загрузить бэкап")
                }
                Button(
                    onClick = { appViewModel.shareBackup(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Поделиться данными")
                }
            }
        }
    }

    // --- Диалоги управления бэкапом ---
    selectedBackupUri?.let { uri ->
        if (!showReplaceWarning) {
            AlertDialog(
                onDismissRequest = { selectedBackupUri = null },
                title = { Text("Как использовать файл?") },
                text = { Text("Вы можете добавить данные к текущим или полностью заменить базу.") },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showReplaceWarning = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Полностью заменить") }

                        Button(
                            onClick = {
                                selectedBackupUri = null
                                android.widget.Toast.makeText(context, "Начато слияние баз...", android.widget.Toast.LENGTH_SHORT).show()
                                appViewModel.mergeBackupFromUri(context, uri)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Добавить к текущим") }
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { selectedBackupUri = null }) { Text("Отмена") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showReplaceWarning = false },
                title = { Text("Внимание! Вы уверены в полной замене базы?") },
                text = { Text("Текущие данные будут безвозвратно потеряны.") },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedBackupUri = null
                            showReplaceWarning = false
                            appViewModel.importBackupFromUri(context, uri, onLoggedOut)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Да, заменить") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showReplaceWarning = false }) { Text("Отмена") }
                }
            )
        }
    }

    // --- Шторка управления пользователями ---
    if (showUserManagementSheet) {
        UserManagementBottomSheet(
            users = users,
            currentUserId = user?.id,
            onSelectUser = { selectedUser ->
                appViewModel.login(selectedUser.id)
            },
            onDeleteUser = { targetUser ->
                profileToDeleteStage1 = targetUser
            },
            onCreateProfileClick = {
                showCreateProfileDialog = true
            },
            onDismissRequest = { showUserManagementSheet = false }
        )
    }

    // --- Диалог создания нового профиля ---
    if (showCreateProfileDialog) {
        val registerError by appViewModel.registerError.collectAsStateWithLifecycle()
        CreateProfileDialog(
            error = registerError,
            onClearError = { appViewModel.clearRegisterError() },
            onDismiss = {
                showCreateProfileDialog = false
                appViewModel.clearRegisterError()
            },
            onConfirm = { fullName, position, organization ->
                appViewModel.register(
                    fullName = fullName,
                    position = position,
                    organization = organization,
                    autoLogin = false
                )
                showCreateProfileDialog = false
            }
        )
    }

    // --- Диалог инлайн-редактирования поля профиля ---
    editingField?.let { field ->
        val u = user
        if (u != null) {
            val initial = when (field) {
                EditableProfileField.FULL_NAME -> u.fullName
                EditableProfileField.POSITION -> u.position
                EditableProfileField.ORGANIZATION -> u.organization
            }
            EditSingleFieldDialog(
                field = field,
                initialValue = initial,
                onDismiss = { editingField = null },
                onConfirm = { newValue ->
                    editingField = null
                    val newName = if (field == EditableProfileField.FULL_NAME) newValue else u.fullName
                    val newPos = if (field == EditableProfileField.POSITION) newValue else u.position
                    val newOrg = if (field == EditableProfileField.ORGANIZATION) newValue else u.organization
                    appViewModel.updateProfile(
                        userId = u.id,
                        fullName = newName,
                        position = newPos,
                        organization = newOrg,
                        role = u.role
                    )
                }
            )
        } else {
            editingField = null
        }
    }

    // --- Двухэтапное удаление профиля ---
    // Этап 1: Предупреждение об удалении профиля и связанных чеков
    profileToDeleteStage1?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { profileToDeleteStage1 = null },
            title = { Text("Удалить профиль «${targetUser.fullName}»?") },
            text = {
                Text(
                    "Все привязанные чеки и отчёты этого сотрудника будут безвозвратно удалены из базы данных."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        profileToDeleteStage2 = targetUser
                        profileToDeleteStage1 = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { profileToDeleteStage1 = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Этап 2: Финальное подтверждение
    profileToDeleteStage2?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { profileToDeleteStage2 = null },
            icon = {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Вы абсолютно уверены?") },
            text = {
                Text(
                    "Это действие необратимо! Профиль «${targetUser.fullName}» и все его чеки будут немедленно стёрты."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isCurrent = targetUser.id == user?.id
                        profileToDeleteStage2 = null
                        appViewModel.deleteProfile(targetUser.id) {
                            if (isCurrent) onLoggedOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Удалить окончательно")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { profileToDeleteStage2 = null }) {
                    Text("Назад")
                }
            }
        )
    }
}

@Composable
private fun ProfileRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Outlined.Edit,
            contentDescription = "Редактировать $label",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

enum class EditableProfileField(val title: String, val label: String) {
    FULL_NAME("Изменить имя", "Имя пользователя (полностью)"),
    POSITION("Изменить должность", "Должность"),
    ORGANIZATION("Изменить организацию", "Организация (необязательно)")
}

@Composable
fun EditSingleFieldDialog(
    field: EditableProfileField,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = { Text(field.label) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = value.trim()
                    if (field != EditableProfileField.ORGANIZATION && trimmed.isEmpty()) {
                        error = "Поле не может быть пустым"
                        return@Button
                    }
                    onConfirm(trimmed)
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun CreateProfileDialog(
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, position: String, organization: String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; localError = null; onClearError() },
                    label = { Text("Имя пользователя (полностью)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it; localError = null; onClearError() },
                    label = { Text("Должность") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it; localError = null; onClearError() },
                    label = { Text("Организация (необязательно)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                val displayError = localError ?: error
                displayError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.trim().isEmpty() || position.trim().isEmpty()) {
                        localError = "Заполните имя и должность"
                        return@Button
                    }
                    onConfirm(fullName.trim(), position.trim(), organization.trim())
                }
            ) { Text("Создать") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/**
 * Диалог редактирования полей текущего профиля. Используется и из вкладки «Профиль»,
 * и из экрана авторизации (карандашик у каждого профиля).
 */
@Composable
fun EditProfileDialog(
    initialFullName: String,
    initialPosition: String,
    initialOrganization: String = "",
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, position: String, organization: String) -> Unit,
    title: String = "Редактирование профиля"
) {
    var fullName by remember { mutableStateOf(initialFullName) }
    var position by remember { mutableStateOf(initialPosition) }
    var organization by remember { mutableStateOf(initialOrganization) }

    LaunchedEffect(initialFullName, initialPosition, initialOrganization) {
        fullName = initialFullName
        position = initialPosition
        organization = initialOrganization
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; onClearError() },
                    label = { Text("Имя пользователя (полностью)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it; onClearError() },
                    label = { Text("Должность") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it; onClearError() },
                    label = { Text("Организация (необязательно)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(fullName, position, organization) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}






