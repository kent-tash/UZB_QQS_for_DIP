package com.example.uzb_qqs_for_dip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.ui.EditablePaperRow
import com.example.uzb_qqs_for_dip.ui.PaperReportScanViewModel
import com.example.uzb_qqs_for_dip.ui.PaperScanPhase
import com.example.uzb_qqs_for_dip.ui.components.DocumentCameraCaptureDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperReportScanScreen(
    userId: Long,
    quarter: Quarter,
    year: Int,
    startManual: Boolean = false,
    onBack: () -> Unit,
    vm: PaperReportScanViewModel = viewModel()
) {
    LaunchedEffect(userId, quarter, year, startManual) {
        vm.init(userId, quarter, year, startManual)
    }

    val employee by vm.employee.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val capturing by vm.capturing.collectAsStateWithLifecycle()
    val confirm by vm.confirm.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val savedCount by vm.savedCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Распечатка отчёта")
                        employee?.let {
                            Text(
                                it.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        when (phase) {
            PaperScanPhase.CAMERA -> {
                DocumentCameraCaptureDialog(
                    onDismiss = onBack,
                    onCaptured = vm::onBitmapCaptured,
                    capturing = capturing
                )
                // Фоновый контент под диалогом
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Сфотографируйте таблицу реестра. Можно снять несколько страниц подряд.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = vm::skipCameraToManual) {
                        Text("Ввести вручную без камеры")
                    }
                }
            }
            PaperScanPhase.CONFIRM -> {
                val c = confirm
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Результат распознавания", style = MaterialTheme.typography.titleMedium)
                    if (c != null) {
                        ConfirmLine("Страниц снято", c.pagesScanned.toString())
                        ConfirmLine("Строк на странице", c.rowCount.toString())
                        ConfirmLine("Ячеек распознано", c.cellCount.toString())
                        ConfirmLine("Всего строк в сессии", c.totalRowsAccumulated.toString())
                        ConfirmLine("ФИО с листа", c.ocrFullName ?: "—")
                        ConfirmLine("Должность", c.ocrPosition ?: "—")
                        ConfirmLine("Квартал", c.ocrPeriodLabel ?: "—")
                        if (!c.nameMatches) {
                            Text(
                                "ФИО на листе слабо совпадает с выбранным сотрудником. Проверьте перед сохранением.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Отсканировать следующие страницы?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = vm::scanNextPage,
                            modifier = Modifier.weight(1f)
                        ) { Text("Да, ещё страница") }
                        OutlinedButton(
                            onClick = vm::goToEdit,
                            modifier = Modifier.weight(1f)
                        ) { Text("К правке") }
                    }
                }
            }
            PaperScanPhase.EDIT -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                "Проверьте и при необходимости исправьте строки. Пустые/некорректные не сохранятся.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(rows, key = { it.id }) { row ->
                            EditableRowCard(
                                row = row,
                                onChange = { updated -> vm.updateRow(row.id) { updated } },
                                onDelete = { vm.removeRow(row.id) }
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = vm::addEmptyRow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Добавить строку")
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { vm.scanNextPage() },
                            modifier = Modifier.weight(1f),
                            enabled = !saving
                        ) { Text("Ещё страница") }
                        Button(
                            onClick = vm::save,
                            modifier = Modifier.weight(1f),
                            enabled = !saving
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Сохранить")
                            }
                        }
                    }
                }
            }
            PaperScanPhase.DONE -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Сохранено чеков: $savedCount",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Пометка: с распечатки (не из приложения)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Готово") }
                }
            }
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("Ошибка") },
            text = { Text(error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = vm::clearError) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ConfirmLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EditableRowCard(
    row: EditablePaperRow,
    onChange: (EditablePaperRow) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Строка",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                }
            }
            OutlinedTextField(
                value = row.sellerName,
                onValueChange = { onChange(row.copy(sellerName = it)) },
                label = { Text("Организация / магазин") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.totalText,
                    onValueChange = { onChange(row.copy(totalText = it)) },
                    label = { Text("Сумма") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = row.vatText,
                    onValueChange = { onChange(row.copy(vatText = it)) },
                    label = { Text("НДС") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = row.dateText,
                onValueChange = { onChange(row.copy(dateText = it)) },
                label = { Text("Дата (дд.мм.гггг)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
