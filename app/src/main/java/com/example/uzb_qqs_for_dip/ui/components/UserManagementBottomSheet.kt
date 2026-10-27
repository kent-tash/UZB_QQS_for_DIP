package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementBottomSheet(
    appViewModel: AppViewModel,
    onDismissRequest: () -> Unit,
    onLoggedOut: () -> Unit = {},
    onSelectUser: ((User) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val users by appViewModel.users.collectAsStateWithLifecycle()
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()
    val editError by appViewModel.editError.collectAsStateWithLifecycle()
    val registerError by appViewModel.registerError.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }
    var profileToDeleteStage1 by remember { mutableStateOf<User?>(null) }
    var profileToDeleteStage2 by remember { mutableStateOf<User?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp, top = 4.dp)
        ) {
            Text(
                text = "Управление профилями",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    val isActive = user.id == currentUser?.id
                    UserItemRow(
                        user = user,
                        isActive = isActive,
                        onSelect = {
                            appViewModel.login(user.id)
                            onSelectUser?.invoke(user)
                            onDismissRequest()
                        },
                        onEdit = {
                            appViewModel.clearEditError()
                            editingUser = user
                        },
                        onDelete = {
                            profileToDeleteStage1 = user
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    appViewModel.clearRegisterError()
                    showCreateDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Outlined.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Добавить новый профиль",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // --- Диалог создания нового профиля ---
    if (showCreateDialog) {
        CreateProfileDialog(
            error = registerError,
            onClearError = { appViewModel.clearRegisterError() },
            onDismiss = {
                showCreateDialog = false
                appViewModel.clearRegisterError()
            },
            onConfirm = { fullName, position, organization ->
                appViewModel.register(
                    fullName = fullName,
                    position = position,
                    organization = organization,
                    autoLogin = false
                )
                showCreateDialog = false
            }
        )
    }

    // --- Диалог редактирования профиля ---
    editingUser?.let { targetUser ->
        EditProfileDialog(
            initialFullName = targetUser.fullName,
            initialPosition = targetUser.position,
            initialOrganization = targetUser.organization,
            error = editError,
            onClearError = { appViewModel.clearEditError() },
            onDismiss = {
                editingUser = null
                appViewModel.clearEditError()
            },
            onConfirm = { newName, newPos, newOrg ->
                appViewModel.updateProfile(
                    userId = targetUser.id,
                    fullName = newName,
                    position = newPos,
                    organization = newOrg,
                    role = targetUser.role
                )
                editingUser = null
            }
        )
    }

    // --- Двухэтапное удаление профиля ---
    // Этап 1: Предупреждение об удалении данных пользователя
    profileToDeleteStage1?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { profileToDeleteStage1 = null },
            title = { Text("Удалить профиль «${targetUser.fullName}»?") },
            text = {
                Text("Все привязанные чеки и отчёты этого сотрудника будут безвозвратно удалены из базы данных.")
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
                Text("Это действие необратимо! Профиль «${targetUser.fullName}» и все его чеки будут немедленно стёрты.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isCurrent = targetUser.id == currentUser?.id
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
private fun UserItemRow(
    user: User,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая кликабельная область выбора пользователя
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onSelect)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.fullName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isActive) {
                            Spacer(Modifier.size(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "Активен",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = user.position,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Правая зона действий: кнопка ✏️ и 🗑️
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Редактировать профиль «${user.fullName}»",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Удалить профиль «${user.fullName}»",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
