package com.example.uzb_qqs_for_dip.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uzb_qqs_for_dip.data.model.AuditDeclaration
import com.example.uzb_qqs_for_dip.data.model.AuditStatus
import com.example.uzb_qqs_for_dip.data.repository.EmployeeSummary
import com.example.uzb_qqs_for_dip.data.settings.AuditorSettings
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.AuditorExportKind
import com.example.uzb_qqs_for_dip.ui.AuditorFilter
import com.example.uzb_qqs_for_dip.ui.AuditorViewModel
import com.example.uzb_qqs_for_dip.util.MoneyFormat

private val Success = Color(0xFF2E7D32)
private val Danger = Color(0xFFC62828)
private val Warning = Color(0xFFF57F17)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditorScreen(
    appViewModel: AppViewModel,
    onVerifyEmployee: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val vm: AuditorViewModel = viewModel()

    // Обновляем сводку при возврате с экрана проверки чеков.
    LaunchedEffect(Unit) { vm.refresh() }

    val quarter by vm.quarter.collectAsStateWithLifecycle()
    val year by vm.year.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val conflicts by vm.conflicts.collectAsStateWithLifecycle()
    val batchImportResult by vm.batchImportResult.collectAsStateWithLifecycle()
    val filtered by vm.filteredSummaries.collectAsStateWithLifecycle()
    val addEmployeeError by vm.addEmployeeError.collectAsStateWithLifecycle()
    val auditorSettings by vm.auditorSettings.collectAsStateWithLifecycle()

    val totalSum = filtered.sumOf { it.totalTiyin }
    val totalVat = filtered.sumOf { it.vatTiyin }
    // Считаем сотрудников, у которых ВСЕ чеки подтверждены аудитором.
    val verifiedEmployees = filtered.count { s ->
        s.declaration?.status == AuditStatus.APPROVED ||
            (s.receiptCount > 0 && s.verifiedCount >= s.receiptCount)
    }

    var showExportMenu by remember { mutableStateOf(false) }
    var showSaveMenu by remember { mutableStateOf(false) }
    var pendingSaveKind by remember { mutableStateOf<AuditorExportKind?>(null) }
    var showDeclarationDialog by remember { mutableStateOf<EmployeeSummary?>(null) }
    var showYearPicker by remember { mutableStateOf(false) }
    var showConflicts by remember { mutableStateOf(false) }
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var showAuditorSettingsDialog by remember { mutableStateOf(false) }

    val batchImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.mergeBatchFromUris(context, uris)
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        pendingSaveKind?.let { kind ->
            if (uri != null) vm.saveExportToUri(context, uri, kind)
        }
        pendingSaveKind = null
    }

    val saveXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        pendingSaveKind?.let { kind ->
            if (uri != null) vm.saveExportToUri(context, uri, kind)
        }
        pendingSaveKind = null
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        pendingSaveKind?.let { kind ->
            if (uri != null) vm.saveExportToUri(context, uri, kind)
        }
        pendingSaveKind = null
    }

    fun launchSave(kind: AuditorExportKind) {
        pendingSaveKind = kind
        val fileName = vm.suggestedFilename(kind)
        when (kind) {
            AuditorExportKind.SUMMARY_PDF, AuditorExportKind.ORG_PDF ->
                savePdfLauncher.launch(fileName)
            AuditorExportKind.XLSX -> saveXlsxLauncher.launch(fileName)
            AuditorExportKind.CSV -> saveCsvLauncher.launch(fileName)
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("Ошибка") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }
        )
    }

    batchImportResult?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearBatchImportResult,
            title = { Text("Результат импорта") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearBatchImportResult) { Text("OK") } }
        )
    }

    if (showAddEmployeeDialog) {
        AddEmployeeDialog(
            error = addEmployeeError,
            onDismiss = { showAddEmployeeDialog = false; vm.clearAddEmployeeError() },
            onSave = { name, pos, initials, org ->
                vm.addEmployee(name, pos, initials, org) { showAddEmployeeDialog = false }
            }
        )
    }

    if (showAuditorSettingsDialog) {
        AuditorSettingsDialog(
            current = auditorSettings,
            onDismiss = { showAuditorSettingsDialog = false },
            onSave = { s -> vm.saveAuditorSettings(s); showAuditorSettingsDialog = false }
        )
    }

    showDeclarationDialog?.let { summary ->
        DeclarationDialog(
            summary = summary,
            quarter = quarter.name,
            year = year,
            onDismiss = { showDeclarationDialog = null },
            onSave = { decl ->
                vm.upsertDeclaration(decl)
                showDeclarationDialog = null
            }
        )
    }

    if (showConflicts && conflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showConflicts = false },
            title = { Text("Пересечения чеков (${conflicts.size})") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(conflicts) { c ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text("${c.user1FullName} ↔ ${c.user2FullName}",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(c.sellerName, style = MaterialTheme.typography.bodySmall)
                            Text(MoneyFormat.fromTiyin(c.totalAmountTiyin) + " сум",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showConflicts = false }) { Text("Закрыть") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аудит") },
                actions = {
                    IconButton(onClick = { showAuditorSettingsDialog = true }) {
                        Icon(Icons.Outlined.Settings, "Настройки аудитора")
                    }
                    IconButton(onClick = { showAddEmployeeDialog = true }) {
                        Icon(Icons.Outlined.PersonAdd, "Добавить сотрудника")
                    }
                    IconButton(onClick = { batchImportLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Outlined.UploadFile, "Импорт бэкапов")
                    }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Outlined.Share, "Экспорт / поделиться")
                        }
                        AuditorExportMenu(
                            expanded = showExportMenu,
                            onDismiss = { showExportMenu = false },
                            onSummaryPdf = { vm.exportPdf(context) },
                            onOrgPdf = { vm.exportOrgReportPdf(context) },
                            onXlsx = { vm.exportXlsx(context) },
                            onCsv = { vm.exportCsv(context) }
                        )
                    }
                    Box {
                        IconButton(onClick = { showSaveMenu = true }) {
                            Icon(Icons.Outlined.Download, "Сохранить")
                        }
                        AuditorExportMenu(
                            expanded = showSaveMenu,
                            onDismiss = { showSaveMenu = false },
                            onSummaryPdf = { launchSave(AuditorExportKind.SUMMARY_PDF) },
                            onOrgPdf = { launchSave(AuditorExportKind.ORG_PDF) },
                            onXlsx = { launchSave(AuditorExportKind.XLSX) },
                            onCsv = { launchSave(AuditorExportKind.CSV) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quarter/Year selector
            QuarterYearRow(
                quarter = quarter,
                year = year,
                onQuarterChange = vm::setQuarter,
                onYearChange = vm::setYear
            )

            // Stats header
            if (!isLoading) {
                StatsRow(
                    employeeCount = filtered.size,
                    verifiedCount = verifiedEmployees,
                    conflictCount = conflicts.size,
                    totalSum = totalSum,
                    totalVat = totalVat,
                    onConflictsClick = { if (conflicts.isNotEmpty()) showConflicts = true }
                )
            }

            // Search bar + filter chips
            OutlinedTextField(
                value = search,
                onValueChange = vm::setSearch,
                placeholder = { Text("Поиск по ФИО") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            )

            FilterChipsRow(currentFilter = filter, onFilterChange = vm::setFilter)

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (filtered.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Нет данных за выбранный период",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.userId }) { summary ->
                    EmployeeCard(
                        summary = summary,
                        hasConflict = conflicts.any { c -> c.user1Id == summary.userId || c.user2Id == summary.userId },
                        onVerify = { onVerifyEmployee(summary.userId) },
                        onEditDeclaration = { showDeclarationDialog = summary }
                    )
                }
                item {
                    TotalsCard(
                        total = filtered.sumOf { it.totalTiyin },
                        vat = filtered.sumOf { it.vatTiyin },
                        count = filtered.size
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditorExportMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSummaryPdf: () -> Unit,
    onOrgPdf: () -> Unit,
    onXlsx: () -> Unit,
    onCsv: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Сводная таблица (PDF)") },
            onClick = { onDismiss(); onSummaryPdf() }
        )
        DropdownMenuItem(
            text = { Text("Возврат НДС по организациям (PDF)") },
            onClick = { onDismiss(); onOrgPdf() }
        )
        DropdownMenuItem(
            text = { Text("Экспорт XLSX") },
            onClick = { onDismiss(); onXlsx() }
        )
        DropdownMenuItem(
            text = { Text("Экспорт CSV") },
            onClick = { onDismiss(); onCsv() }
        )
    }
}

@Composable
private fun QuarterYearRow(
    quarter: Quarter,
    year: Int,
    onQuarterChange: (Quarter) -> Unit,
    onYearChange: (Int) -> Unit
) {
    var showQ by remember { mutableStateOf(false) }
    var showY by remember { mutableStateOf(false) }
    val quarters = Quarter.entries.filter { it != Quarter.Custom }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { showQ = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text(quarter.label, maxLines = 1) }
            DropdownMenu(expanded = showQ, onDismissRequest = { showQ = false }) {
                quarters.forEach { q ->
                    DropdownMenuItem(
                        text = { Text(q.label) },
                        onClick = { onQuarterChange(q); showQ = false }
                    )
                }
            }
        }
        Box {
            OutlinedButton(
                onClick = { showY = true },
                shape = RoundedCornerShape(10.dp)
            ) { Text(year.toString()) }
            DropdownMenu(expanded = showY, onDismissRequest = { showY = false }) {
                val curYear = com.example.uzb_qqs_for_dip.data.settings.ReportSettings.currentYear()
                (curYear - 3..curYear).reversed().forEach { y ->
                    DropdownMenuItem(text = { Text(y.toString()) }, onClick = { onYearChange(y); showY = false })
                }
            }
        }
    }
}

@Composable
private fun StatsRow(
    employeeCount: Int,
    verifiedCount: Int,
    conflictCount: Int,
    totalSum: Long,
    totalVat: Long,
    onConflictsClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatChip(
            label = "Проверено",
            value = "$verifiedCount/$employeeCount",
            color = if (verifiedCount == employeeCount && employeeCount > 0) Success else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = "Конфликты",
            value = conflictCount.toString(),
            color = if (conflictCount > 0) Danger else Success,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = conflictCount > 0, onClick = onConflictsClick)
        )
        StatChip(
            label = "Сумма",
            value = MoneyFormat.fromTiyin(totalSum),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterChipsRow(currentFilter: AuditorFilter, onFilterChange: (AuditorFilter) -> Unit) {
    val chips = listOf(
        AuditorFilter.ALL to "Все",
        AuditorFilter.UNVERIFIED to "Не проверено",
        AuditorFilter.DISCREPANCY to "Расхождение",
        AuditorFilter.INCOMPLETE to "Неполная проверка",
        AuditorFilter.CONFLICT to "Пересечения"
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        items(chips) { (f, label) ->
            FilterChip(
                selected = currentFilter == f,
                onClick = { onFilterChange(f) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun EmployeeCard(
    summary: EmployeeSummary,
    hasConflict: Boolean,
    onVerify: () -> Unit,
    onEditDeclaration: () -> Unit
) {
    val decl = summary.declaration
    val hasDeltaTotal = decl != null && decl.declaredTotalTiyin != 0L && decl.declaredTotalTiyin != summary.totalTiyin
    val hasDeltaVat = decl != null && decl.declaredVatTiyin != 0L && decl.declaredVatTiyin != summary.vatTiyin
    val allReceiptsVerified = summary.receiptCount > 0 && summary.verifiedCount >= summary.receiptCount
    val statusColor = when {
        hasConflict -> Danger
        decl?.status == AuditStatus.APPROVED || allReceiptsVerified -> Success
        decl?.status == AuditStatus.REVISION -> Warning
        hasDeltaTotal || hasDeltaVat -> Warning
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        summary.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (summary.position.isNotBlank()) {
                        Text(
                            summary.position,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (summary.organization.isNotBlank()) {
                        Text(
                            summary.organization,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (decl?.status == AuditStatus.APPROVED || allReceiptsVerified) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(18.dp))
                } else if (hasConflict) {
                    Icon(Icons.Filled.Error, null, tint = Danger, modifier = Modifier.size(18.dp))
                } else if (hasDeltaTotal || hasDeltaVat) {
                    Icon(Icons.Filled.Warning, null, tint = Warning, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                AmountColumn("Итого", summary.totalTiyin, Modifier.weight(1f))
                AmountColumn("НДС", summary.vatTiyin, Modifier.weight(1f))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Чеков", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${summary.verifiedCount}/${summary.receiptCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (summary.verifiedCount == summary.receiptCount && summary.receiptCount > 0) Success
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Declaration delta
            if (decl != null && (hasDeltaTotal || hasDeltaVat)) {
                Spacer(Modifier.height(6.dp))
                val deltaTotal = summary.totalTiyin - decl.declaredTotalTiyin
                val deltaVat = summary.vatTiyin - decl.declaredVatTiyin
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Δ Итого: ${if (deltaTotal >= 0) "+" else ""}${MoneyFormat.fromTiyin(deltaTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Warning
                    )
                    Text(
                        "Δ НДС: ${if (deltaVat >= 0) "+" else ""}${MoneyFormat.fromTiyin(deltaVat)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Warning
                    )
                }
            }
            if (decl?.note?.isNotBlank() == true) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Заметка: ${decl.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onVerify,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Проверить чеки", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onEditDeclaration,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Text(
                        if (decl == null) "Внести итоги" else "Итоги PDF",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountColumn(label: String, tiyin: Long, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(MoneyFormat.fromTiyin(tiyin), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TotalsCard(total: Long, vat: Long, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Итого по организации ($count чел.)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyFormat.fromTiyin(total), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold)
                Text("НДС: ${MoneyFormat.fromTiyin(vat)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun DeclarationDialog(
    summary: EmployeeSummary,
    quarter: String,
    year: Int,
    onDismiss: () -> Unit,
    onSave: (AuditDeclaration) -> Unit
) {
    val existing = summary.declaration
    var totalStr by rememberSaveable { mutableStateOf(
        if ((existing?.declaredTotalTiyin ?: 0L) != 0L)
            MoneyFormat.fromTiyin(existing!!.declaredTotalTiyin).replace(" ", "") else ""
    ) }
    var vatStr by rememberSaveable { mutableStateOf(
        if ((existing?.declaredVatTiyin ?: 0L) != 0L)
            MoneyFormat.fromTiyin(existing!!.declaredVatTiyin).replace(" ", "") else ""
    ) }
    var countStr by rememberSaveable { mutableStateOf(
        if ((existing?.declaredCount ?: 0) != 0) existing!!.declaredCount.toString() else ""
    ) }
    var noteStr by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var statusExpanded by remember { mutableStateOf(false) }
    var selectedStatus by rememberSaveable { mutableStateOf(existing?.status ?: AuditStatus.PENDING) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Итоги из PDF: ${summary.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Введите значения из строки «Итого» принесённого PDF-отчёта.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = totalStr,
                    onValueChange = { totalStr = it },
                    label = { Text("Заявленная сумма, сум") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vatStr,
                    onValueChange = { vatStr = it },
                    label = { Text("Заявленный НДС, сум") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = countStr,
                    onValueChange = { countStr = it },
                    label = { Text("Кол-во чеков в PDF") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteStr,
                    onValueChange = { noteStr = it },
                    label = { Text("Заметка (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(
                        onClick = { statusExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Статус: ${selectedStatus.label()}")
                    }
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        AuditStatus.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label()) },
                                onClick = { selectedStatus = s; statusExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val totalTiyin = com.example.uzb_qqs_for_dip.util.MoneyFormat.toTiyin(totalStr)
                val vatTiyin = com.example.uzb_qqs_for_dip.util.MoneyFormat.toTiyin(vatStr)
                val count = countStr.trim().toIntOrNull() ?: 0
                onSave(
                    AuditDeclaration(
                        id = existing?.id ?: 0,
                        userId = summary.userId,
                        year = year,
                        quarter = quarter,
                        declaredTotalTiyin = totalTiyin,
                        declaredVatTiyin = vatTiyin,
                        declaredCount = count,
                        status = selectedStatus,
                        note = noteStr.trim().takeIf { it.isNotEmpty() },
                        checkedAt = System.currentTimeMillis()
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun AuditStatus.label(): String = when (this) {
    AuditStatus.PENDING -> "На проверке"
    AuditStatus.APPROVED -> "Принято"
    AuditStatus.REVISION -> "На доработку"
    AuditStatus.CONFLICT -> "Конфликт"
}

@Composable
private fun AddEmployeeDialog(
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, position: String, initials: String, organization: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var initials by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить сотрудника") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Фамилия Имя Отчество") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position, onValueChange = { position = it },
                    label = { Text("Должность") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = initials, onValueChange = { initials = it },
                    label = { Text("И.О. Фамилия (для подписи)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = organization, onValueChange = { organization = it },
                    label = { Text("Организация (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, position, initials, organization) }) { Text("Добавить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AuditorSettingsDialog(
    current: AuditorSettings,
    onDismiss: () -> Unit,
    onSave: (AuditorSettings) -> Unit
) {
    var orgName by remember { mutableStateOf(current.organizationName) }
    var dirTitle by remember { mutableStateOf(current.directorTitle) }
    var dirName by remember { mutableStateOf(current.directorName) }
    var accTitle by remember { mutableStateOf(current.accountantTitle) }
    var accName by remember { mutableStateOf(current.accountantName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки аудитора") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Эти данные используются в заголовке и подписях PDF-отчётов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = orgName, onValueChange = { orgName = it },
                    label = { Text("Название организации") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = dirTitle, onValueChange = { dirTitle = it },
                    label = { Text("Должность руководителя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dirName, onValueChange = { dirName = it },
                    label = { Text("И.О. Фамилия руководителя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = accTitle, onValueChange = { accTitle = it },
                    label = { Text("Должность бухгалтера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accName, onValueChange = { accName = it },
                    label = { Text("И.О. Фамилия бухгалтера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(AuditorSettings(
                    organizationName = orgName.trim(),
                    directorTitle = dirTitle.trim().ifBlank { "Руководитель организации" },
                    directorName = dirName.trim(),
                    accountantTitle = accTitle.trim().ifBlank { "Главный бухгалтер организации" },
                    accountantName = accName.trim()
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
