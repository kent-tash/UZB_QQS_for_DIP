package com.example.uzb_qqs_for_dip.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Edit
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
    val editError by appViewModel.editError.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMergeUri by remember { mutableStateOf<Uri?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AppBackup.MIME_TYPE)
    ) { uri ->
        if (uri != null) appViewModel.exportBackupToUri(context, uri)
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    val mergeBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingMergeUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Профиль",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Column {
                        Text(
                            user?.fullName ?: "Не авторизован",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        user?.position?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                ProfileRow("Имя", user?.fullName ?: "—")
                ProfileRow("Должность", user?.position ?: "—")
                ProfileRow("И.О. Фамилия для подписи", user?.initialsSurname ?: "—")
                ProfileRow("Организация", user?.organization?.ifBlank { "—" } ?: "—")
                ProfileRow(
                    "Роль",
                    if (user?.role == UserRole.AUDITOR) "Аудитор" else "Сотрудник"
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
                OutlinedButton(
                    onClick = { appViewModel.switchCurrentUserRole() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
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
                    "Один файл JSON: все профили, сохранённые чеки и настройки отчёта " +
                        "(период, сортировка, выбранный пользователь в фильтре).\n\n" +
                        "• «Загрузить бэкап» — полностью заменяет текущие данные.\n" +
                        "• «Добавить из бэкапа» — добавляет данные из файла к текущим без удаления " +
                        "(например, чеки другого пользователя). Дубликаты чеков пропускаются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        createBackupLauncher.launch("${AppBackup.FILE_BASE_NAME}_$stamp.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Создать бэкап")
                }
                OutlinedButton(
                    onClick = { appViewModel.shareBackup(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Поделиться данными")
                }
                OutlinedButton(
                    onClick = {
                        openBackupLauncher.launch(arrayOf(AppBackup.MIME_TYPE, "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Загрузить бэкап")
                }
                OutlinedButton(
                    onClick = {
                        mergeBackupLauncher.launch(arrayOf(AppBackup.MIME_TYPE, "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.LibraryAdd, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Добавить из бэкапа")
                }
            }
        }

        if (user != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        appViewModel.clearEditError()
                        editing = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Редактировать")
                }
                OutlinedButton(
                    onClick = { pendingDelete = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Удалить")
                }
            }
        }

        OutlinedButton(
            onClick = {
                appViewModel.logout()
                onLoggedOut()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Выйти из профиля")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Все ранее сохранённые чеки остаются в базе и привязаны к этому профилю. " +
                "При удалении профиля все его чеки также будут удалены.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Загрузить бэкап?") },
            text = {
                Text(
                    "Все текущие профили, чеки и настройки отчёта будут безвозвратно заменены " +
                        "данными из выбранного файла."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreUri = null
                        appViewModel.importBackupFromUri(context, uri, onLoggedOut)
                    }
                ) { Text("Восстановить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestoreUri = null }) { Text("Отмена") }
            }
        )
    }

    pendingMergeUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingMergeUri = null },
            title = { Text("Добавить из бэкапа?") },
            text = {
                Text(
                    "Данные из выбранного файла будут добавлены к текущим без удаления. " +
                        "Совпадающие профили объединяются по имени, а уже имеющиеся чеки " +
                        "(по той же ссылке) повторно не добавляются."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingMergeUri = null
                        appViewModel.mergeBackupFromUri(context, uri)
                    }
                ) { Text("Добавить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingMergeUri = null }) { Text("Отмена") }
            }
        )
    }

    if (editing) {
        val u = user
        if (u == null) {
            editing = false
        } else {
            EditProfileDialog(
                initialFullName = u.fullName,
                initialPosition = u.position,
                initialInitialsSurname = u.initialsSurname,
                initialOrganization = u.organization,
                error = editError,
                onClearError = { appViewModel.clearEditError() },
                onDismiss = {
                    editing = false
                    appViewModel.clearEditError()
                },
                onConfirm = { fullName, position, initialsSurname, organization ->
                    appViewModel.updateProfile(
                        userId = u.id,
                        fullName = fullName,
                        position = position,
                        initialsSurname = initialsSurname,
                        organization = organization,
                        onDone = { editing = false }
                    )
                }
            )
        }
    }

    if (pendingDelete) {
        val u = user
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Удалить профиль?") },
            text = {
                Text(
                    "Профиль «${u?.fullName ?: ""}» и все связанные с ним сохранённые чеки " +
                        "будут удалены безвозвратно."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = false
                        u?.id?.let { id ->
                            appViewModel.deleteProfile(id) { onLoggedOut() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.55f)
        )
    }
}

/**
 * Диалог редактирования полей текущего профиля. Используется и из вкладки «Профиль»,
 * и из экрана авторизации (карандашик у каждого профиля).
 */
@Composable
fun EditProfileDialog(
    initialFullName: String,
    initialPosition: String,
    initialInitialsSurname: String,
    initialOrganization: String = "",
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, position: String, initials: String, organization: String) -> Unit,
    title: String = "Редактирование профиля"
) {
    var fullName by remember { mutableStateOf(initialFullName) }
    var position by remember { mutableStateOf(initialPosition) }
    var initials by remember { mutableStateOf(initialInitialsSurname) }
    var organization by remember { mutableStateOf(initialOrganization) }

    LaunchedEffect(initialFullName, initialPosition, initialInitialsSurname, initialOrganization) {
        fullName = initialFullName
        position = initialPosition
        initials = initialInitialsSurname
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
                    value = initials,
                    onValueChange = { initials = it; onClearError() },
                    label = { Text("И.О. Фамилия (для подписи)") },
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
            Button(onClick = { onConfirm(fullName, position, initials, organization) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
