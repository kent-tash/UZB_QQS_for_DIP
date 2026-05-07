package com.example.uzb_qqs_for_dip.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.data.settings.SortField
import com.example.uzb_qqs_for_dip.data.settings.SortOrder
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.ReportEvent
import com.example.uzb_qqs_for_dip.ui.ReportViewModel
import com.example.uzb_qqs_for_dip.ui.components.DateField
import com.example.uzb_qqs_for_dip.ui.components.DatePickerSheet
import com.example.uzb_qqs_for_dip.ui.components.SelectField
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat

private data class UserChoice(val id: Long?, val label: String)
private data class YearChoice(val year: Int)
private data class QuarterChoice(val quarter: Quarter)

@Composable
fun ReportScreen(
    appViewModel: AppViewModel,
    reportViewModel: ReportViewModel = viewModel()
) {
    val context = LocalContext.current
    val users by reportViewModel.users.collectAsStateWithLifecycle()
    val rows by reportViewModel.rows.collectAsStateWithLifecycle()
    val settings by reportViewModel.settings.collectAsStateWithLifecycle()
    val event by reportViewModel.event.collectAsStateWithLifecycle()
    val selectedIds by reportViewModel.selectedIds.collectAsStateWithLifecycle()
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        when (val e = event) {
            is ReportEvent.Open -> {
                runCatching { context.startActivity(e.intent) }
                    .onFailure { Toast.makeText(context, "Не нашли приложение для PDF", Toast.LENGTH_LONG).show() }
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
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                reportViewModel.consumeEvent()
            }
            is ReportEvent.Deleted -> {
                Toast.makeText(
                    context,
                    "Удалено чеков: ${e.count}",
                    Toast.LENGTH_SHORT
                ).show()
                reportViewModel.consumeEvent()
            }
            null -> Unit
        }
    }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val userOptions = remember(users) { users.map { UserChoice(it.id, it.fullName) } }
    val selectedUser =
        userOptions.firstOrNull { it.id == settings.userId }
            ?: userOptions.firstOrNull { it.id == currentUser?.id }
            ?: userOptions.firstOrNull()
            ?: UserChoice(null, "—")

    val totalSum = rows.sumOf { it.receipt.totalAmountTiyin }
    val totalVat = rows.sumOf { it.receipt.vatAmountTiyin }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "PDF-отчёт для печати",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Сформирует PDF: шапка → таблица «№, Юр. лицо, Дата, Сумма, НДС» → итоги → " +
                "блок подписи. Сортировка ниже определяет № в таблице и порядок чеков на " +
                "вкладке «Чеки».",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                if (userOptions.isNotEmpty()) {
                    SelectField(
                        label = "Пользователь",
                        value = selectedUser,
                        options = userOptions,
                        optionLabel = { it.label },
                        onSelected = { reportViewModel.setUserFilter(it.id) }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Квартал/год — основной способ задать период.
                val years = remember(settings.year) {
                    val current = ReportSettings.currentYear()
                    val pivot = settings.year.coerceAtMost(current)
                    val from = (pivot - 5).coerceAtMost(current - 5)
                    val to = current + 1
                    (to downTo from).map { YearChoice(it) }
                }
                val quarters = remember { Quarter.entries.map { QuarterChoice(it) } }
                val selectedYear = years.firstOrNull { it.year == settings.year }
                    ?: YearChoice(settings.year)
                val selectedQuarter = quarters.firstOrNull { it.quarter == settings.quarter }
                    ?: QuarterChoice(settings.quarter)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField(
                        label = "Год",
                        value = selectedYear,
                        options = years,
                        optionLabel = { it.year.toString() },
                        onSelected = { reportViewModel.setYear(it.year) },
                        modifier = Modifier.weight(1f)
                    )
                    SelectField(
                        label = "Квартал",
                        value = selectedQuarter,
                        options = quarters,
                        optionLabel = { it.quarter.label },
                        onSelected = { reportViewModel.setQuarter(it.quarter) },
                        modifier = Modifier.weight(1.4f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField(
                        label = "Период с",
                        valueText = DateFormat.formatDate(settings.from),
                        onClick = { showFromPicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    DateField(
                        label = "Период по",
                        valueText = DateFormat.formatDate(settings.to),
                        onClick = { showToPicker = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (settings.quarter == Quarter.Custom) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Выбран произвольный период. Чтобы вернуться к кварталу — " +
                            "просто выберите его в списке выше.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Краткая шапка: имя/должность/период/количество.
        ReportPreviewCard(
            userFullName = selectedUser.label,
            position = users.firstOrNull { it.id == selectedUser.id }?.position ?: "",
            initialsSurname = users.firstOrNull { it.id == selectedUser.id }?.initialsSurname ?: "",
            from = settings.from,
            to = settings.to,
            count = rows.size,
            totalSum = totalSum,
            totalVat = totalVat
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { reportViewModel.savePdf(context) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = selectedUser.id != null
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Открыть/сохранить")
            }
            Button(
                onClick = { reportViewModel.printPdf(context) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = selectedUser.id != null
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Печать / PDF")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Системный диалог печати позволяет либо отправить отчёт на принтер, либо сохранить PDF в файлы устройства.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // Панель пакетного удаления — появляется, когда выбран хотя бы один чек.
        if (selectedIds.isNotEmpty()) {
            SelectionActionBar(
                selectedCount = selectedIds.size,
                onClear = { reportViewModel.clearSelection() },
                onDelete = { showDeleteConfirm = true }
            )
            Spacer(Modifier.height(8.dp))
        }

        // Превью таблицы — то же содержимое, что попадёт в PDF/печать.
        // Расположено в самом низу, чтобы кнопки экспорта оставались на виду
        // независимо от того, насколько длинной стала таблица.
        ReportTablePreview(
            rows = rows,
            sortField = settings.sortField,
            sortOrder = settings.sortOrder,
            onToggleSort = { reportViewModel.toggleSort(it) },
            selectedIds = selectedIds,
            onToggleSelection = { reportViewModel.toggleSelection(it) },
            onSelectAllVisible = { reportViewModel.selectAllVisible() },
            onClearSelection = { reportViewModel.clearSelection() },
            totalSum = totalSum,
            totalVat = totalVat
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

    if (showDeleteConfirm && selectedIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить выбранные чеки?") },
            text = {
                Text(
                    "Будет удалено ${selectedIds.size} чек(а/ов). " +
                        "Записи и связанные с ними данные исчезнут из отчёта и со вкладки «Чеки» " +
                        "безвозвратно."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        reportViewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Выбрано: $selectedCount",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onClear,
                shape = RoundedCornerShape(10.dp)
            ) { Text("Снять") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDelete,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Удалить")
            }
        }
    }
}

/* --------------------------- Preview table --------------------------- */

private val PT_CHECK = 44.dp
private val PT_NUM = 40.dp
private val PT_SELLER = 200.dp
private val PT_DATE = 130.dp
private val PT_TOTAL = 120.dp
private val PT_VAT = 110.dp

@Composable
private fun ReportTablePreview(
    rows: List<ReceiptWithUser>,
    sortField: SortField,
    sortOrder: SortOrder,
    onToggleSort: (SortField) -> Unit,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    totalSum: Long,
    totalVat: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                "Превью таблицы. Клик по заголовку сортирует, чек-боксы слева — для пакетного удаления.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp)
            )
            val hScroll = rememberScrollState()
            Column(Modifier.horizontalScroll(hScroll)) {
                val visibleCount = rows.size
                val selectedVisible = if (visibleCount == 0) 0
                    else rows.count { it.receipt.id in selectedIds }
                val headerToggleState = when {
                    selectedVisible == 0 -> ToggleableState.Off
                    selectedVisible == visibleCount -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(PT_CHECK),
                        contentAlignment = Alignment.Center
                    ) {
                        TriStateCheckbox(
                            state = headerToggleState,
                            enabled = visibleCount > 0,
                            onClick = {
                                if (headerToggleState == ToggleableState.On) onClearSelection()
                                else onSelectAllVisible()
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedColor = MaterialTheme.colorScheme.onPrimary,
                                checkmarkColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    SortableHeader(
                        text = "№",
                        width = PT_NUM,
                        end = true,
                        field = SortField.DATE,
                        currentField = sortField,
                        currentOrder = sortOrder,
                        onClick = onToggleSort,
                        showIndicator = false
                    )
                    SortableHeader(
                        text = "Юр. лицо",
                        width = PT_SELLER,
                        field = SortField.SELLER,
                        currentField = sortField,
                        currentOrder = sortOrder,
                        onClick = onToggleSort
                    )
                    SortableHeader(
                        text = "Сумма",
                        width = PT_TOTAL,
                        end = true,
                        field = SortField.TOTAL,
                        currentField = sortField,
                        currentOrder = sortOrder,
                        onClick = onToggleSort
                    )
                    SortableHeader(
                        text = "НДС",
                        width = PT_VAT,
                        end = true,
                        field = SortField.VAT,
                        currentField = sortField,
                        currentOrder = sortOrder,
                        onClick = onToggleSort
                    )
                    SortableHeader(
                        text = "Дата",
                        width = PT_DATE,
                        field = SortField.DATE,
                        currentField = sortField,
                        currentOrder = sortOrder,
                        onClick = onToggleSort
                    )
                }

                if (rows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Нет чеков за выбранный период",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    rows.forEachIndexed { idx, item ->
                        val isSelected = item.receipt.id in selectedIds
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(PT_CHECK),
                                contentAlignment = Alignment.Center
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSelection(item.receipt.id) }
                                )
                            }
                            BodyCell((idx + 1).toString(), PT_NUM, end = true, bold = true)
                            BodyCell(item.receipt.sellerName, PT_SELLER)
                            BodyCell(MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin), PT_TOTAL, end = true, bold = true)
                            BodyCell(MoneyFormat.fromTiyin(item.receipt.vatAmountTiyin), PT_VAT, end = true)
                            BodyCell(DateFormat.formatDateTime(item.receipt.purchasedAt), PT_DATE)
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        BodyCell("Итого:", PT_CHECK + PT_NUM + PT_SELLER, end = true, bold = true)
                        BodyCell(MoneyFormat.fromTiyin(totalSum), PT_TOTAL, end = true, bold = true)
                        BodyCell(MoneyFormat.fromTiyin(totalVat), PT_VAT, end = true, bold = true)
                        BodyCell("", PT_DATE)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortableHeader(
    text: String,
    width: Dp,
    field: SortField,
    currentField: SortField,
    currentOrder: SortOrder,
    onClick: (SortField) -> Unit,
    end: Boolean = false,
    showIndicator: Boolean = true
) {
    val active = field == currentField
    Row(
        modifier = Modifier
            .width(width)
            .clickable { onClick(field) }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (end) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = if (end) TextAlign.End else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (showIndicator && active) {
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = if (currentOrder == SortOrder.ASC)
                    Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = currentOrder.label,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun BodyCell(
    text: String,
    width: Dp,
    end: Boolean = false,
    bold: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp),
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 8,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ReportPreviewCard(
    userFullName: String,
    position: String,
    initialsSurname: String,
    from: Long,
    to: Long,
    count: Int,
    totalSum: Long,
    totalVat: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Шапка отчёта", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    InfoRow("Пользователь:", userFullName)
                    InfoRow("Должность:", position)
                    InfoRow("Подпись:", initialsSurname)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    InfoRow(
                        "Период:",
                        "${DateFormat.formatDate(from)} — ${DateFormat.formatDate(to)}",
                        endAlign = true
                    )
                    InfoRow("Чеков:", count.toString(), endAlign = true)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Итого сумма", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(
                        MoneyFormat.fromTiyin(totalSum) + " сум",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Итого НДС", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(
                        MoneyFormat.fromTiyin(totalVat) + " сум",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, endAlign: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (!endAlign) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.5f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.5f)
            )
        } else {
            Text(
                "$label $value",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
