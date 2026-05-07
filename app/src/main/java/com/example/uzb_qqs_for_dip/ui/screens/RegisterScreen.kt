package com.example.uzb_qqs_for_dip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uzb_qqs_for_dip.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val users by appViewModel.users.collectAsStateWithLifecycle()
    val error by appViewModel.registerError.collectAsStateWithLifecycle()
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()

    var fullName by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var initialsSurname by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val initialUserCount = remember { users.size }

    // Если профиль успешно создан и установлен в сессии — переходим дальше.
    LaunchedEffect(submitted, currentUser, users.size) {
        if (submitted && error == null && (currentUser != null || users.size > initialUserCount)) {
            onCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Создание профиля") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Профиль будет привязываться ко всем сохранённым чекам и подставляться в шапку отчёта.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; appViewModel.clearRegisterError() },
                label = { Text("Имя пользователя (полностью)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Например: Иванов Иван Иванович") }
            )

            OutlinedTextField(
                value = position,
                onValueChange = { position = it; appViewModel.clearRegisterError() },
                label = { Text("Должность") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Например: Главный бухгалтер") }
            )

            OutlinedTextField(
                value = initialsSurname,
                onValueChange = { initialsSurname = it; appViewModel.clearRegisterError() },
                label = { Text("И.О. Фамилия (для подписи)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Например: И.И. Иванов") }
            )

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    submitted = true
                    appViewModel.register(fullName, position, initialsSurname)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Создать и войти")
            }
        }
    }
}
