package com.example.uzb_qqs_for_dip.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.data.settings.SortField
import com.example.uzb_qqs_for_dip.data.settings.SortOrder
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.ExportMode
import com.example.uzb_qqs_for_dip.ui.ReportEvent
import com.example.uzb_qqs_for_dip.ui.ReportViewModel
import com.example.uzb_qqs_for_dip.ui.components.AutoScalingText
import com.example.uzb_qqs_for_dip.ui.components.DatePickerSheet
import com.example.uzb_qqs_for_dip.ui.components.ExportActionGrid
import com.example.uzb_qqs_for_dip.ui.components.ExportFileFormat
import com.example.uzb_qqs_for_dip.ui.components.FormatChoiceDialog
import com.example.uzb_qqs_for_dip.ui.components.ZeroVatDetectedDialog
import com.example.uzb_qqs_for_dip.ui.components.ZeroVatReceiptsCard
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat

private data class UserChoice(val id: Long?, val label: String)
private data class YearChoice(val year: Int)
private data class QuarterChoice(val quarter: Quarter, val shortLabel: String)

@Composable
fun ReportScreen(
    appViewModel: AppViewModel,
    reportViewModel: ReportViewModel = viewModel()
) {
    val context = LocalContext.current
    val users by reportViewModel.users.collectAsStateWithLifecycle()
    val rows by reportViewModel.rows.collectAsStateWithLifecycle()
    val zeroVatRows by reportViewModel.zeroVatRows.collectAsStateWithLifecycle()
    val settings by reportViewModel.settings.collectAsStateWithLifecycle()
    val exportMode by reportViewModel.exportMode.collectAsStateWithLifecycle()
    val event by reportViewModel.event.collectAsStateWithLifecycle()
    val selectedIds by reportViewModel.selectedIds.collectAsStateWithLifecycle()
    val isSaving by reportViewModel.isSaving.collectAsStateWithLifecycle()
    val saveProgress by reportViewModel.saveProgress.collectAsStateWithLifecycle()
    val savePhase by reportViewModel.savePhase.collectAsStateWithLifecycle()
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()

    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }
    var showSaveFormatDialog by remember { mutableStateOf(false) }
    var showShareFormatDialog by remember { mutableStateOf(false) }
    var detectedZeroVatList by remember { mutableStateOf<List<com.example.uzb_qqs_for_dip.data.model.ZeroVatReceiptInfo>?>(null) }

    var previewReceipt by remember { mutableStateOf<ReceiptWithUser?>(null) }
    var singleDeleteReceipt by remember { mutableStateOf<ReceiptWithUser?>(null) }
    var showDeleteBatchConfirm by remember { mutableStateOf(false) }

    // Диалоги фильтров
    var showUserPicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }
    var showQuarterPicker by remember { mutableStateOf(false) }
    var showPeriodPicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) reportViewModel.savePdfToUri(context, uri)
    }
    val saveXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) reportViewModel.saveXlsxToUri(context, uri)
    }

    LaunchedEffect(event) {
        when (val e = event) {
            is ReportEvent.Open -> {
                runCatching { context.startActivity(e.intent) }
                    .onFailure { Toast.makeText(context, "Не нашли приложение для просмотра PDF", Toast.LENGTH_LONG).show() }
                reportViewModel.consumeEvent()
            }
            is ReportEvent.Share -> {
                runCatching { context.startActivity(e.intent) }
                    .onFailure { Toast.makeText(context, "Не удалось поделиться", Toast.LENGTH_LONG).show() }
                reportViewModel.consumeEvent()
            }
            is ReportEvent.Print -> {
                reportViewModel.launchPrint(context, e.file, e.jobName)
                reportViewModel.consumeEvent()
            }
            is ReportEvent.Error -> {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                reportViewModel.consumeEvent()
            }
            is ReportEvent.Saved -> {
                saveSuccessMessage = e.message
                reportViewModel.consumeEvent()
            }
            is ReportEvent.Deleted -> {
                Toast.makeText(context, "Удалено чеков: ${e.count}", Toast.LENGTH_SHORT).show()
                reportViewModel.consumeEvent()
            }
            is ReportEvent.ZeroVatDetected -> {
                detectedZeroVatList = e.list
                reportViewModel.consumeEvent()
            }
            null -> Unit
        }
    }

    val userOptions = remember(users) { users.map { UserChoice(it.id, it.fullName) } }
    val selectedUser =
        userOptions.firstOrNull { it.id == settings.userId }
            ?: userOptions.firstOrNull { it.id == currentUser?.id }
            ?: userOptions.firstOrNull()
            ?: UserChoice(null, "—")

    val totalSum = rows.sumOf { it.receipt.totalAmountTiyin }
    val totalVat = rows.sumOf { it.receipt.vatAmountTiyin }

    val periodLabel = if (settings.quarter == Quarter.Custom) {
        "${DateFormat.formatDate(settings.from)} — ${DateFormat.formatDate(settings.to)}"
    } else {
        "${settings.quarter.label} ${settings.year} г."
    }

    val selectedUserObj = users.firstOrNull { it.id == selectedUser.id } ?: currentUser

    val listState = rememberLazyListState()
    LaunchedEffect(appViewModel.scrollToTopEvent) {
        appViewModel.scrollToTopEvent.collect { route ->
            if (route == "main/report" && rows.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // =====================================================================
        // Блок 1: Шапка отчёта (ReportHeaderCard)
        // =====================================================================
        item {
            ReportHeaderCard(
                userFullName = selectedUser.label,
                position = selectedUserObj?.position ?: "",
                periodText = periodLabel,
                count = rows.size,
                totalSum = totalSum,
                totalVat = totalVat
            )
            Spacer(Modifier.height(10.dp))
        }

        // =====================================================================
        // Блок 2: Переключатель режима экспорта (ExportModeSelector)
        // =====================================================================
        item {
            ExportModeSelector(
                currentMode = exportMode,
                onModeSelected = { reportViewModel.setExportMode(it) }
            )
            Spacer(Modifier.height(10.dp))
        }

        // =====================================================================
        // Блок 3: Кнопки экспорта и печати (ExportActionGrid)
        // =====================================================================
        item {
            ExportActionGrid(
                onOpen = { reportViewModel.previewPdf(context) },
                onPrint = { reportViewModel.printPdf(context) },
                onSave = { showSaveFormatDialog = true },
                onShare = { showShareFormatDialog = true },
                isSaving = isSaving,
                saveProgress = saveProgress,
                isSaveEnabled = !isSaving && rows.isNotEmpty() && selectedUser.id != null,
                isOpenPrintEnabled = rows.isNotEmpty() && selectedUser.id != null,
                saveProgressLabel = if (savePhase.isNotEmpty()) savePhase else "Сохранение… ${(saveProgress * 100).toInt()}%"
            )
            Spacer(Modifier.height(10.dp))
        }

        // =====================================================================
        // Блок 4: Панель фильтров в один ряд (CompactFiltersBar)
        // =====================================================================
        item {
            CompactFiltersBar(
                userName = selectedUser.label,
                year = settings.year,
                quarter = settings.quarter,
                from = settings.from,
                to = settings.to,
                onUserClick = { showUserPicker = true },
                onYearClick = { showYearPicker = true },
                onQuarterClick = { showQuarterPicker = true },
                onPeriodClick = { showPeriodPicker = true }
            )
            Spacer(Modifier.height(10.dp))
        }

        // =====================================================================
        // Блок 5: Сортировка и пакетные действия (SortAndBatchBar + SelectionActionBar)
        // =====================================================================
        item {
            SortAndBatchBar(
                totalCount = rows.size,
                selectedCount = selectedIds.size,
                onSelectAll = { reportViewModel.selectAllVisible() },
                onClearAll = { reportViewModel.clearSelection() },
                sortField = settings.sortField,
                sortOrder = settings.sortOrder,
                onSortFieldSelected = { reportViewModel.toggleSort(it) },
                onToggleSortOrder = { reportViewModel.toggleSort(settings.sortField) }
            )
        }

        item {
            AnimatedVisibility(
                visible = selectedIds.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SelectionActionBar(
                        selectedCount = selectedIds.size,
                        onClear = { reportViewModel.clearSelection() },
                        onDelete = { showDeleteBatchConfirm = true }
                    )
                }
            }
        }

        // =====================================================================
        // Блок 6: Карточка чеков с нулевым НДС (ZeroVatReceiptsCard)
        // =====================================================================
        if (zeroVatRows.isNotEmpty()) {
            item {
                ZeroVatReceiptsCard(
                    zeroVatReceipts = zeroVatRows,
                    onDeleteReceipt = { reportViewModel.deleteReceipt(it) },
                    onDeleteAll = { reportViewModel.deleteZeroVatReceipts(it) },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // =====================================================================
        // Блок 7: Список карточек чеков
        // =====================================================================
        if (rows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет чеков за выбранный период",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(
                items = rows,
                key = { _, item -> item.receipt.id },
                contentType = { _, _ -> "receipt_card" }
            ) { index, item ->
                val ordinal = index + 1
                val isSelected = item.receipt.id in selectedIds

                ReceiptListCard(
                    modifier = Modifier.padding(vertical = 4.dp),
                    ordinal = ordinal,
                    item = item,
                    isSelected = isSelected,
                    onToggleSelect = { reportViewModel.toggleSelection(item.receipt.id) },
                    onPreview = { previewReceipt = item },
                    onDelete = { singleDeleteReceipt = item }
                )
            }
        }
    }

    // --- Диалоги просмотра и удаления чеков ---
    previewReceipt?.let { item ->
        val ordinal = rows.indexOfFirst { it.receipt.id == item.receipt.id } + 1
        ReceiptPreviewDialog(
            item = item,
            ordinal = ordinal,
            onDismiss = { previewReceipt = null },
            onShare = {
                reportViewModel.shareReceiptImage(context, item)
                previewReceipt = null
            },
            onDelete = {
                singleDeleteReceipt = item
                previewReceipt = null
            }
        )
    }

    singleDeleteReceipt?.let { item ->
        AlertDialog(
            onDismissRequest = { singleDeleteReceipt = null },
            title = { Text("Удалить чек?") },
            text = {
                Text(
                    "${item.receipt.sellerName} от " +
                        DateFormat.formatDateTime(item.receipt.purchasedAt) +
                        "\nСумма: ${MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin)} сум"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        reportViewModel.deleteReceipt(item.receipt.id)
                        singleDeleteReceipt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { singleDeleteReceipt = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showDeleteBatchConfirm && selectedIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteBatchConfirm = false },
            title = { Text("Удалить выбранные чеки?") },
            text = {
                Text(
                    "Будет удалено ${selectedIds.size} чек(а/ов). " +
                        "Записи будут удалены из базы данных безвозвратно."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteBatchConfirm = false
                        reportViewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteBatchConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // --- Диалоги выбора параметров фильтров ---
    if (showUserPicker && userOptions.isNotEmpty()) {
        SelectUserDialog(
            userOptions = userOptions,
            selectedUserId = selectedUser.id,
            onDismiss = { showUserPicker = false },
            onSelect = {
                reportViewModel.setUserFilter(it)
                showUserPicker = false
            }
        )
    }

    if (showYearPicker) {
        val currentYear = ReportSettings.currentYear()
        val years = (currentYear + 1 downTo currentYear - 5).map { YearChoice(it) }
        SelectOptionDialog(
            title = "Выберите год",
            options = years,
            selectedOption = YearChoice(settings.year),
            optionLabel = { it.year.toString() },
            onDismiss = { showYearPicker = false },
            onSelect = {
                reportViewModel.setYear(it.year)
                showYearPicker = false
            }
        )
    }

    if (showQuarterPicker) {
        val quarters = listOf(
            QuarterChoice(Quarter.Q1, "I квартал (январь–март)"),
            QuarterChoice(Quarter.Q2, "II квартал (апрель–июнь)"),
            QuarterChoice(Quarter.Q3, "III квартал (июль–сентябрь)"),
            QuarterChoice(Quarter.Q4, "IV квартал (октябрь–декабрь)"),
            QuarterChoice(Quarter.Custom, "Произвольный период")
        )
        val selected = quarters.firstOrNull { it.quarter == settings.quarter } ?: quarters.first()
        SelectOptionDialog(
            title = "Выберите квартал",
            options = quarters,
            selectedOption = selected,
            optionLabel = { it.shortLabel },
            onDismiss = { showQuarterPicker = false },
            onSelect = {
                reportViewModel.setQuarter(it.quarter)
                showQuarterPicker = false
            }
        )
    }

    if (showPeriodPicker) {
        AlertDialog(
            onDismissRequest = { showPeriodPicker = false },
            title = { Text("Настройка периода") },
            text = {
                Column {
                    Text(
                        text = "Текущий диапазон:\n${DateFormat.formatDate(settings.from)} — ${DateFormat.formatDate(settings.to)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            showPeriodPicker = false
                            showFromPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Изменить дату «С» (${DateFormat.formatDate(settings.from)})")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showPeriodPicker = false
                            showToPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Изменить дату «По» (${DateFormat.formatDate(settings.to)})")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPeriodPicker = false }) {
                    Text("Готово")
                }
            }
        )
    }

    if (showFromPicker) {
        DatePickerSheet(
            initialMillis = settings.from,
            onDismiss = { showFromPicker = false },
            onPicked = { it?.let(reportViewModel::setFrom) }
        )
    }
    if (showToPicker) {
        DatePickerSheet(
            initialMillis = settings.to,
            onDismiss = { showToPicker = false },
            onPicked = { it?.let(reportViewModel::setTo) }
        )
    }

    // --- Диалоги экспорта и уведомлений ---
    if (showSaveFormatDialog) {
        FormatChoiceDialog(
            title = "Сохранить ${exportMode.title.lowercase()} как",
            onDismiss = { showSaveFormatDialog = false },
            onChoose = { format ->
                showSaveFormatDialog = false
                when (format) {
                    ExportFileFormat.PDF ->
                        reportViewModel.suggestedPdfName()?.let { savePdfLauncher.launch(it) }
                    ExportFileFormat.XLSX ->
                        reportViewModel.suggestedXlsxName()?.let { saveXlsxLauncher.launch(it) }
                }
            }
        )
    }

    if (showShareFormatDialog) {
        FormatChoiceDialog(
            title = "Поделиться ${exportMode.title.lowercase()}",
            onDismiss = { showShareFormatDialog = false },
            onChoose = { format ->
                showShareFormatDialog = false
                when (format) {
                    ExportFileFormat.PDF -> reportViewModel.sharePdf(context)
                    ExportFileFormat.XLSX -> reportViewModel.shareXlsx(context)
                }
            }
        )
    }

    detectedZeroVatList?.let { detected ->
        ZeroVatDetectedDialog(
            detectedReceipts = detected,
            onDelete = { ids ->
                reportViewModel.deleteZeroVatReceipts(ids)
                detectedZeroVatList = null
            },
            onDismiss = { detectedZeroVatList = null }
        )
    }

    saveSuccessMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { saveSuccessMessage = null },
            title = { Text("Сохранено") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { saveSuccessMessage = null }) { Text("OK") }
            }
        )
    }
}

// =============================================================================
// Компоненты экрана
// =============================================================================

/**
 * Блок 1: Карточка «Шапка отчёта».
 */
@Composable
private fun ReportHeaderCard(
    userFullName: String,
    position: String,
    periodText: String,
    count: Int,
    totalSum: Long,
    totalVat: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сводный отчёт",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "$count чеков",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    "Сотрудник:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.35f)
                )
                Text(
                    userFullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(0.65f)
                )
            }
            if (position.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        "Должность:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.35f)
                    )
                    Text(
                        position,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.65f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    "Период:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.35f)
                )
                Text(
                    periodText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.65f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Итого сумма",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        MoneyFormat.fromTiyin(totalSum) + " сум",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        "Итого НДС (12%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        MoneyFormat.fromTiyin(totalVat) + " сум",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

/**
 * Блок 2: Сегментированный переключатель режимов «Отчёт» | «Всё» | «Чеки».
 */
@Composable
private fun ExportModeSelector(
    currentMode: ExportMode,
    onModeSelected: (ExportMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExportMode.entries.forEach { mode ->
                val isSelected = mode == currentMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface
                            else Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = MaterialTheme.colorScheme.primary),
                            role = Role.Tab
                        ) { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Блок 4: Компактная 1-рядная панель фильтров из 4 адаптивных ячеек с AutoScalingText.
 */
@Composable
private fun CompactFiltersBar(
    userName: String,
    year: Int,
    quarter: Quarter,
    from: Long,
    to: Long,
    onUserClick: () -> Unit,
    onYearClick: () -> Unit,
    onQuarterClick: () -> Unit,
    onPeriodClick: () -> Unit
) {
    val quarterShort = when (quarter) {
        Quarter.Q1 -> "I кв"
        Quarter.Q2 -> "II кв"
        Quarter.Q3 -> "III кв"
        Quarter.Q4 -> "IV кв"
        Quarter.Custom -> "Свой"
    }

    val periodShort = "${DateFormat.formatDate(from).take(5)}—${DateFormat.formatDate(to).take(5)}"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactFilterTile(
            label = "Пользователь",
            valueText = userName,
            onClick = onUserClick,
            modifier = Modifier.weight(1.15f)
        )
        CompactFilterTile(
            label = "Год",
            valueText = year.toString(),
            onClick = onYearClick,
            modifier = Modifier.weight(0.85f)
        )
        CompactFilterTile(
            label = "Квартал",
            valueText = quarterShort,
            onClick = onQuarterClick,
            modifier = Modifier.weight(0.95f)
        )
        CompactFilterTile(
            label = "Период",
            valueText = periodShort,
            onClick = onPeriodClick,
            modifier = Modifier.weight(1.05f)
        )
    }
}

@Composable
private fun CompactFilterTile(
    label: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    letterSpacing = 0.3.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AutoScalingText(
                    text = valueText,
                    minFontSize = 7.5.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Блок 5: Панель сортировки.
 */
@Composable
private fun SortAndBatchBar(
    totalCount: Int,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    sortField: SortField,
    sortOrder: SortOrder,
    onSortFieldSelected: (SortField) -> Unit,
    onToggleSortOrder: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val toggleState = when {
        totalCount == 0 || selectedCount == 0 -> ToggleableState.Off
        selectedCount == totalCount -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    if (toggleState == ToggleableState.On) onClearAll() else onSelectAll()
                }
                .padding(end = 6.dp)
        ) {
            TriStateCheckbox(
                state = toggleState,
                onClick = {
                    if (toggleState == ToggleableState.On) onClearAll() else onSelectAll()
                },
                enabled = totalCount > 0
            )
            Text(
                text = "Все ($totalCount)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(
                    onClick = { sortMenuExpanded = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = sortField.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(field.label) },
                            onClick = {
                                sortMenuExpanded = false
                                onSortFieldSelected(field)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            IconButton(
                onClick = onToggleSortOrder,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (sortOrder == SortOrder.ASC) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = sortOrder.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Плашка действий пакетного удаления (SelectionActionBar).
 */
@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Выбрано: $selectedCount",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onClear,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            ) {
                Text("Снять")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDelete,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Удалить")
            }
        }
    }
}

/**
 * Блок 7: Карточка строки чека.
 */
@Composable
private fun ReceiptListCard(
    modifier: Modifier = Modifier,
    ordinal: Int,
    item: ReceiptWithUser,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Чекбокс слева (48dp touch target)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 24.dp),
                        role = Role.Checkbox,
                        onClick = onToggleSelect
                    ),
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }

            Spacer(Modifier.width(4.dp))

            // Черный прямоугольный бейдж с номером
            Box(
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "№ $ordinal",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.width(10.dp))

            // Информационный блок
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onPreview
                    )
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = item.receipt.sellerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateFormat.formatDateTime(item.receipt.purchasedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сумма: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin) + " сум",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "НДС: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyFormat.fromTiyin(item.receipt.vatAmountTiyin) + " сум",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Кнопка удаления справа (48dp touch target)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Удалить чек №$ordinal",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Диалог полноразмерного превью электронного чека с QR-кодом.
 */
@Composable
private fun ReceiptPreviewDialog(
    item: ReceiptWithUser,
    ordinal: Int,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Чек №$ordinal")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val bitmap = remember(item.receipt.id, ordinal) {
                    com.example.uzb_qqs_for_dip.render.ReceiptCardRenderer.renderBitmap(
                        receipt = item.receipt,
                        ordinal = ordinal,
                        width = 540
                    )
                }
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Превью чека №$ordinal",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Поделиться")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Удалить")
                }
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    )
}

/**
 * Диалог выбора пользователя.
 */
@Composable
private fun SelectUserDialog(
    userOptions: List<UserChoice>,
    selectedUserId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите сотрудника") },
        text = {
            Column {
                userOptions.forEach { user ->
                    val isSelected = user.id == selectedUserId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(user.id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(user.id) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = user.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

/**
 * Универсальный диалог выбора опции (год / квартал).
 */
@Composable
private fun <T> SelectOptionDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(option) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
