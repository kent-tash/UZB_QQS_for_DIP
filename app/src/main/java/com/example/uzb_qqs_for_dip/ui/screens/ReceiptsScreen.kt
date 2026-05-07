package com.example.uzb_qqs_for_dip.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.ExportEvent
import com.example.uzb_qqs_for_dip.ui.ReceiptsViewModel
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat

@Suppress("UNUSED_PARAMETER")
@Composable
fun ReceiptsScreen(
    appViewModel: AppViewModel,
    receiptsViewModel: ReceiptsViewModel = viewModel()
) {
    val context = LocalContext.current
    val rows by receiptsViewModel.receipts.collectAsStateWithLifecycle()
    val settings by receiptsViewModel.settings.collectAsStateWithLifecycle()
    val users by receiptsViewModel.users.collectAsStateWithLifecycle()
    val currentUser by receiptsViewModel.currentUser.collectAsStateWithLifecycle()
    val exportEvent by receiptsViewModel.exportEvents.collectAsStateWithLifecycle()

    LaunchedEffect(exportEvent) {
        when (val e = exportEvent) {
            is ExportEvent.Share -> {
                runCatching { context.startActivity(e.intent) }
                receiptsViewModel.consumeExportEvent()
            }
            is ExportEvent.Print -> {
                receiptsViewModel.launchPrint(context, e.file, e.jobName)
                receiptsViewModel.consumeExportEvent()
            }
            is ExportEvent.Error -> {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                receiptsViewModel.consumeExportEvent()
            }
            null -> Unit
        }
    }

    var pendingDelete by remember { mutableStateOf<ReceiptWithUser?>(null) }
    var previewItem by remember { mutableStateOf<ReceiptWithUser?>(null) }

    val effectiveUserName = users.firstOrNull { it.id == settings.userId }?.fullName
        ?: currentUser?.fullName
        ?: "—"
    val periodLabel = if (settings.quarter == Quarter.Custom) {
        "Свой период"
    } else {
        "${settings.quarter.label}, ${settings.year} год"
    }
    val sortLabel = "${settings.sortField.label} • ${settings.sortOrder.label}"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                "Чеки",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Список и нумерация повторяют таблицу со вкладки «Отчёт». Чтобы изменить " +
                    "пользователя, период или сортировку — переключитесь на «Отчёт».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    SettingsRow("Пользователь", effectiveUserName)
                    SettingsRow("Период", periodLabel)
                    SettingsRow(
                        "Даты",
                        "${DateFormat.formatDate(settings.from)} — ${DateFormat.formatDate(settings.to)}"
                    )
                    SettingsRow("Сортировка", sortLabel)
                    SettingsRow("Чеков", rows.size.toString())
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { receiptsViewModel.exportCsv(context) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.TableView, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("CSV")
                }
                OutlinedButton(
                    onClick = { receiptsViewModel.exportXlsx(context) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.GridOn, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("XLSX")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { receiptsViewModel.printAllAsSheets(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Печать всех чеков (по 6 на лист)")
            }

            Spacer(Modifier.height(12.dp))
        }

        if (rows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        "Нет чеков по выбранным настройкам отчёта",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = rows,
                key = { it.receipt.id },
                contentType = { _ -> "receipt" }
            ) { item ->
                val ordinal = rows.indexOfFirst { it.receipt.id == item.receipt.id } + 1
                ReceiptListRow(
                    modifier = Modifier.padding(bottom = 10.dp),
                    ordinal = ordinal,
                    item = item,
                    onPreview = { previewItem = item },
                    onDelete = { pendingDelete = item }
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить чек?") },
            text = {
                Text(
                    "${item.receipt.sellerName} от " +
                        DateFormat.formatDateTime(item.receipt.purchasedAt)
                )
            },
            confirmButton = {
                Button(onClick = {
                    receiptsViewModel.deleteReceipt(item.receipt.id)
                    pendingDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            }
        )
    }

    previewItem?.let { item ->
        val ordinal = rows.indexOfFirst { it.receipt.id == item.receipt.id } + 1
        ReceiptPreviewDialog(
            item = item,
            ordinal = ordinal,
            onDismiss = { previewItem = null },
            onShare = {
                receiptsViewModel.shareReceiptImage(context, item)
                previewItem = null
            }
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun ReceiptListRow(
    modifier: Modifier = Modifier,
    ordinal: Int,
    item: ReceiptWithUser,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onPreview() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            androidx.compose.ui.graphics.Color.Black,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        ordinal.toString(),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.receipt.sellerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Text(
                        DateFormat.formatDateTime(item.receipt.purchasedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Сумма: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "НДС: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            MoneyFormat.fromTiyin(item.receipt.vatAmountTiyin),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ReceiptPreviewDialog(
    item: ReceiptWithUser,
    ordinal: Int,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Поделиться")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Закрыть") }
        },
        title = { Text("Чек №$ordinal") },
        text = {
            ReceiptCardPreview(item = item, ordinal = ordinal)
        }
    )
}

@Composable
private fun ReceiptCardPreview(item: ReceiptWithUser, ordinal: Int) {
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
            .padding(top = 8.dp)
    )
}
