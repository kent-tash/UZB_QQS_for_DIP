package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
