package com.example.uzb_qqs_for_dip.ui.screens

import android.widget.Toast
import com.example.uzb_qqs_for_dip.ui.components.MultiQrCameraScannerDialog
import com.example.uzb_qqs_for_dip.util.startQrScanner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uzb_qqs_for_dip.data.model.AuditStatus
import com.example.uzb_qqs_for_dip.data.model.ReceiptSource
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.repository.UserReceiptStats
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.network.ParsedReceipt
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.AuditorVerifyViewModel
import com.example.uzb_qqs_for_dip.ui.SheetItemStatus
import com.example.uzb_qqs_for_dip.ui.SheetReceiptItem
import com.example.uzb_qqs_for_dip.ui.VerifyResult
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat

private val VerifySuccess = Color(0xFF2E7D32)
private val VerifyDanger = Color(0xFFC62828)
private val VerifyWarning = Color(0xFFF57F17)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditorVerifyScreen(
    appViewModel: AppViewModel,
    preselectedUserId: Long? = null,
    initialQuarter: Quarter? = null,
    initialYear: Int? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: AuditorVerifyViewModel = viewModel()

    val employees by vm.employees.collectAsStateWithLifecycle()
    val selectedEmployee by vm.selectedEmployee.collectAsStateWithLifecycle()
    val verifyResult by vm.verifyResult.collectAsStateWithLifecycle()
    val employeeStats by vm.employeeStats.collectAsStateWithLifecycle()
    val receiptSearchQuery by vm.receiptSearchQuery.collectAsStateWithLifecycle()
    val receiptSearchResults by vm.receiptSearchResults.collectAsStateWithLifecycle()
    val declaration by vm.declaration.collectAsStateWithLifecycle()
    val addEmployeeError by vm.addEmployeeError.collectAsStateWithLifecycle()
    val autoVerifyMessage by vm.autoVerifyMessage.collectAsStateWithLifecycle()
    val manualVerifyMessage by vm.manualVerifyMessage.collectAsStateWithLifecycle()
    val sheetPreviewItems by vm.sheetPreviewItems.collectAsStateWithLifecycle()
    val sheetSummary by vm.sheetSummary.collectAsStateWithLifecycle()
    val sheetLoading by vm.sheetLoading.collectAsStateWithLifecycle()

    LaunchedEffect(preselectedUserId, initialQuarter, initialYear, employees) {
        if (initialQuarter != null && initialYear != null) {
            vm.setPeriod(initialQuarter, initialYear)
        }
        preselectedUserId?.let { id ->
            employees.firstOrNull { it.id == id }?.let { vm.selectEmployee(it) }
        }
    }

    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showManualVerifyConfirm by remember { mutableStateOf(false) }
    var showSheetSourceDialog by remember { mutableStateOf(false) }
    var showSheetCamera by remember { mutableStateOf(false) }

    autoVerifyMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearAutoVerifyMessage,
            title = { Text("Автоматическая проверка") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = vm::clearAutoVerifyMessage) { Text("OK") }
            }
        )
    }

    manualVerifyMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearManualVerifyMessage,
            title = { Text("Ручная проверка") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = vm::clearManualVerifyMessage) { Text("OK") }
            }
        )
    }

    if (showManualVerifyConfirm) {
        AlertDialog(
            onDismissRequest = { showManualVerifyConfirm = false },
            title = { Text("Проверено вручную") },
            text = {
                Text(
                    "Подтвердить, что чеки сотрудника ${selectedEmployee?.fullName ?: ""} " +
                        "проверены вручную по бумажным документам (без занесения в приложение)?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showManualVerifyConfirm = false
                    vm.markManuallyVerified()
                }) { Text("Подтвердить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showManualVerifyConfirm = false }) { Text("Отмена") }
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.handleImageFromGallery(context, it) } }

    val sheetGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.prepareSheetFromUri(context, it) } }

    if (showSheetSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSheetSourceDialog = false },
            title = { Text("Скан листа") },
            text = {
                Text("Отсканируйте все QR на листе камерой или выберите фото листа из галереи.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSheetSourceDialog = false
                        showSheetCamera = true
                    }
                ) { Text("Камера") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSheetSourceDialog = false
                        sheetGalleryLauncher.launch("image/*")
                    }
                ) { Text("Галерея") }
            }
        )
    }

    if (showSheetCamera) {
        MultiQrCameraScannerDialog(
            onDismiss = { showSheetCamera = false },
            onFinished = { urls ->
                showSheetCamera = false
                vm.prepareSheetFromUrls(urls)
            }
        )
    }

    sheetSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = vm::clearSheetSummary,
            title = { Text("Скан листа") },
            text = { Text(summary.message) },
            confirmButton = {
                TextButton(onClick = vm::clearSheetSummary) { Text("OK") }
            }
        )
    }

    if (sheetLoading && sheetPreviewItems.isEmpty()) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Распознаём QR на листе...")
                }
            }
        }
    }

    if (sheetPreviewItems.isNotEmpty()) {
        SheetPreviewDialog(
            items = sheetPreviewItems,
            loading = sheetLoading,
            onToggle = vm::toggleSheetItem,
            onConfirm = vm::confirmSheetSelection,
            onCancel = vm::clearSheetPreview
        )
    }

    if (showAddEmployeeDialog) {
        AddEmployeeDialog(
            error = addEmployeeError,
            onDismiss = {
                showAddEmployeeDialog = false
                vm.clearAddEmployeeError()
            },
            onAdd = { name, pos, ini -> vm.addEmployee(name, pos, ini) }
        )
        // Auto-close when employee was added (error cleared + selectedEmployee set)
        if (addEmployeeError == null && selectedEmployee != null) {
            showAddEmployeeDialog = false
        }
    }

    if (showLinkDialog) {
        AddLinkDialogVerify(
            onDismiss = { showLinkDialog = false },
            onSubmit = { url ->
                showLinkDialog = false
                vm.handleScan(url)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Проверка чеков")
                        selectedEmployee?.let {
                            Text(
                                "Проверяется: ${it.fullName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Employee selector
            EmployeeSelectorRow(
                employees = employees,
                selected = selectedEmployee,
                onSelect = vm::selectEmployee,
                onAddEmployee = { showAddEmployeeDialog = true }
            )

            // Progress counter + auto-verify (if employee selected)
            selectedEmployee?.let {
                val manuallyApproved = declaration?.status == AuditStatus.APPROVED
                VerificationProgress(stats = employeeStats, manuallyApproved = manuallyApproved)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = vm::autoVerifyAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = VerifySuccess
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Проверить автоматически (все чеки в базе)")
                }
                OutlinedButton(
                    onClick = { showManualVerifyConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !manuallyApproved
                ) {
                    Icon(
                        Icons.Outlined.Done,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (manuallyApproved) VerifySuccess else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (manuallyApproved) "Отмечено: проверено вручную"
                        else "Проверено вручную"
                    )
                }

                OutlinedTextField(
                    value = receiptSearchQuery,
                    onValueChange = vm::setReceiptSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Поиск чеков сотрудника") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }
                )
                if (receiptSearchQuery.isNotBlank()) {
                    Text(
                        if (receiptSearchResults.isEmpty()) "Ничего не найдено"
                        else "Найдено: ${receiptSearchResults.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    receiptSearchResults.forEach { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(item.receipt.sellerName, fontWeight = FontWeight.Medium)
                                if (item.receipt.source == ReceiptSource.PAPER) {
                                    Text(
                                        "С распечатки",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Text(
                                    DateFormat.formatDateTime(item.receipt.purchasedAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin)} · НДС ${MoneyFormat.fromTiyin(item.receipt.vatAmountTiyin)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(
                                    onClick = { vm.markReceiptVerifiedFromSearch(item.receipt.id) },
                                    enabled = true
                                ) {
                                    Text("Отметить проверенным")
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Scan section: buttons below, result card overlaid on top when active
            Box(Modifier.fillMaxWidth()) {
                // Always-visible scan buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Способ захвата QR-кода",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanActionButton(
                            icon = Icons.Outlined.QrCodeScanner,
                            label = "Камера",
                            enabled = selectedEmployee != null &&
                                verifyResult == VerifyResult.Idle &&
                                sheetPreviewItems.isEmpty() &&
                                !sheetLoading,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                startQrScanner(
                                    context = context,
                                    onScanned = { url -> vm.handleScan(url) },
                                    onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                )
                            }
                        )
                        ScanActionButton(
                            icon = Icons.Outlined.Image,
                            label = "Галерея",
                            enabled = selectedEmployee != null &&
                                verifyResult == VerifyResult.Idle &&
                                sheetPreviewItems.isEmpty() &&
                                !sheetLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { galleryLauncher.launch("image/*") }
                        )
                        ScanActionButton(
                            icon = Icons.Outlined.Link,
                            label = "Ссылка",
                            enabled = selectedEmployee != null &&
                                verifyResult == VerifyResult.Idle &&
                                sheetPreviewItems.isEmpty() &&
                                !sheetLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { showLinkDialog = true }
                        )
                    }
                    ScanActionButton(
                        icon = Icons.Outlined.GridView,
                        label = "Скан листа",
                        enabled = selectedEmployee != null &&
                            verifyResult == VerifyResult.Idle &&
                            sheetPreviewItems.isEmpty() &&
                            !sheetLoading,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showSheetSourceDialog = true }
                    )
                }

                // Result overlay — floats above buttons until dismissed
                when (val r = verifyResult) {
                    VerifyResult.Idle -> Unit
                    VerifyResult.Loading -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Загружаем страницу чека...")
                            }
                        }
                    }
                    is VerifyResult.Error -> {
                        VerifyErrorCard(message = r.message, onDismiss = vm::clearVerifyResult)
                    }
                    is VerifyResult.Success -> {
                        VerifySuccessCard(result = r, onDismiss = vm::clearVerifyResult)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeSelectorRow(
    employees: List<User>,
    selected: User?,
    onSelect: (User) -> Unit,
    onAddEmployee: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selected?.fullName ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Сотрудник") },
                placeholder = { Text("Выберите сотрудника") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (employees.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Нет сотрудников — добавьте через «+»") },
                        onClick = {}
                    )
                }
                employees.forEach { emp ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(emp.fullName)
                                if (emp.position.isNotBlank()) {
                                    Text(
                                        emp.position,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { onSelect(emp); expanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onAddEmployee) {
            Icon(Icons.Outlined.AddCircleOutline, "Добавить сотрудника",
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun VerificationProgress(stats: UserReceiptStats, manuallyApproved: Boolean) {
    val allVerifiedInDb = stats.totalCount > 0 && stats.verifiedCount >= stats.totalCount
    val allVerified = allVerifiedInDb || manuallyApproved
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allVerified) {
                VerifySuccess.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allVerified) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = VerifySuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    when {
                        manuallyApproved && stats.totalCount == 0 ->
                            "Проверено вручную (чеки не в приложении)"
                        manuallyApproved ->
                            "Проверено вручную · в базе: ${stats.verifiedCount}/${stats.totalCount}"
                        stats.totalCount == 0 ->
                            "Нет чеков за выбранный период"
                        else ->
                            "Проверено: ${stats.verifiedCount} из ${stats.totalCount} чеков"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allVerified) VerifySuccess else MaterialTheme.colorScheme.onSurface
                )
            }
            if (stats.verifiedCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Сумма: ${MoneyFormat.fromTiyin(stats.verifiedTotalTiyin)} | " +
                        "НДС: ${MoneyFormat.fromTiyin(stats.verifiedVatTiyin)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (stats.totalCount > 0 && !allVerifiedInDb && !manuallyApproved) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { stats.verifiedCount.toFloat() / stats.totalCount },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScanActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VerifySuccessCard(result: VerifyResult.Success, onDismiss: () -> Unit) {
    val parsed = result.parsed
    val statusText: String
    val statusColor: Color
    val statusIcon = when {
        result.owner != null && !result.alreadyForThisEmployee -> {
            statusText = "Чек уже принадлежит: ${result.owner.fullName}"
            statusColor = VerifyDanger
            Icons.Filled.Error
        }
        result.markedVerified && result.alreadyForThisEmployee -> {
            statusText = "Чек подтверждён (уже в базе)"
            statusColor = VerifySuccess
            Icons.Filled.CheckCircle
        }
        result.markedVerified -> {
            statusText = "Чек сохранён и подтверждён"
            statusColor = VerifySuccess
            Icons.Filled.CheckCircle
        }
        else -> {
            statusText = "Чек не сохранён"
            statusColor = VerifyWarning
            Icons.Filled.Warning
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(statusText, color = statusColor, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (result.outOfPeriod) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = VerifyWarning, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Чек вне выбранного квартала", color = VerifyWarning,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            ReceiptDetailRow("Дата", parsed.purchasedAt?.let { DateFormat.formatDateTime(it) })
            ReceiptDetailRow("Продавец", parsed.sellerName)
            ReceiptDetailRow("Итого", parsed.totalAmountTiyin?.let { MoneyFormat.fromTiyin(it) + " сум" }, bold = true)
            ReceiptDetailRow("НДС", parsed.vatAmountTiyin?.let { MoneyFormat.fromTiyin(it) + " сум" }, bold = true)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Следующий чек") }
        }
    }
}

@Composable
private fun VerifyErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Error, null, tint = VerifyDanger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ошибка", color = VerifyDanger, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(message)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Закрыть") }
        }
    }
}

@Composable
private fun ReceiptDetailRow(label: String, value: String?, bold: Boolean = false) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun AddEmployeeDialog(
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var initialsSurname by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить сотрудника") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Фамилия Имя Отчество") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Должность") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = initialsSurname,
                    onValueChange = { initialsSurname = it },
                    label = { Text("И.О. Фамилия (для подписи)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error, color = VerifyDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(fullName, position, initialsSurname) }) { Text("Добавить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AddLinkDialogVerify(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val trimmed = url.trim()
    val looksValid = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить чек по ссылке") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Ссылка на чек") },
                placeholder = { Text("https://ofd.soliq.uz/check?...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSubmit(trimmed) }, enabled = looksValid) { Text("Загрузить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun SheetPreviewDialog(
    items: List<SheetReceiptItem>,
    loading: Boolean,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val selectedCount = items.count {
        it.selected && (
            it.status == SheetItemStatus.NEW ||
                it.status == SheetItemStatus.ALREADY_THIS ||
                it.status == SheetItemStatus.OUT_OF_PERIOD
            )
    }

    Dialog(
        onDismissRequest = { if (!loading) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Чеки с листа (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Выбрано к сохранению/подтверждению: $selectedCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        SheetPreviewRow(
                            item = item,
                            enabled = !loading && item.status != SheetItemStatus.OTHER_OWNER &&
                                item.status != SheetItemStatus.ERROR,
                            onToggle = { onToggle(index) }
                        )
                        if (index < items.lastIndex) HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (loading) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !loading,
                        modifier = Modifier.weight(1f)
                    ) { Text("Отмена") }
                    Button(
                        onClick = onConfirm,
                        enabled = !loading && selectedCount > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Подтвердить") }
                }
            }
        }
    }
}

@Composable
private fun SheetPreviewRow(
    item: SheetReceiptItem,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val (label, color) = when (item.status) {
        SheetItemStatus.NEW -> "Новый" to VerifySuccess
        SheetItemStatus.ALREADY_THIS -> "Уже у сотрудника" to VerifySuccess
        SheetItemStatus.OTHER_OWNER ->
            "Чужой: ${item.ownerName ?: "?"}" to VerifyDanger
        SheetItemStatus.ERROR ->
            (item.errorMessage ?: "Ошибка") to VerifyDanger
        SheetItemStatus.OUT_OF_PERIOD -> "Вне периода" to VerifyWarning
    }
    val parsed = item.parsed
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.selected,
            onCheckedChange = { if (enabled) onToggle() },
            enabled = enabled
        )
        Column(Modifier.weight(1f)) {
            Text(label, color = color, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall)
            if (parsed != null) {
                Text(
                    parsed.sellerName ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    listOfNotNull(
                        parsed.purchasedAt?.let { DateFormat.formatDateTime(it) },
                        parsed.totalAmountTiyin?.let { MoneyFormat.fromTiyin(it) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    item.qrUrl.take(60) + if (item.qrUrl.length > 60) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
