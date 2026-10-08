package com.example.uzb_qqs_for_dip.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.example.uzb_qqs_for_dip.ui.components.ExportActionGrid
import com.example.uzb_qqs_for_dip.ui.components.ExportFileFormat
import com.example.uzb_qqs_for_dip.ui.components.FormatChoiceDialog
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
    val isSaving by receiptsViewModel.isSaving.collectAsStateWithLifecycle()
    val saveProgress by receiptsViewModel.saveProgress.collectAsStateWithLifecycle()

    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }
    var showSaveFormatDialog by remember { mutableStateOf(false) }
    var showShareFormatDialog by remember { mutableStateOf(false) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) receiptsViewModel.saveReceiptsPdfToUri(context, uri)
    }
    val saveXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) receiptsViewModel.saveReceiptsXlsxToUri(context, uri)
    }

    LaunchedEffect(exportEvent) {
        when (val e = exportEvent) {
            is ExportEvent.Open -> {
                runCatching { context.startActivity(e.intent) }
                    .onFailure {
                        Toast.makeText(context, "РќРµ РЅР°С€Р»Рё РїСЂРёР»РѕР¶РµРЅРёРµ РґР»СЏ РїСЂРѕСЃРјРѕС‚СЂР° PDF", Toast.LENGTH_LONG).show()
                    }
                receiptsViewModel.consumeExportEvent()
            }
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
            is ExportEvent.Saved -> {
                saveSuccessMessage = e.message
                receiptsViewModel.consumeExportEvent()
            }
            null -> Unit
        }
    }

    var pendingDelete by remember { mutableStateOf<ReceiptWithUser?>(null) }
    var previewItem by remember { mutableStateOf<ReceiptWithUser?>(null) }

    val effectiveUserName = users.firstOrNull { it.id == settings.userId }?.fullName
        ?: currentUser?.fullName
        ?: "вЂ”"
    val periodLabel = if (settings.quarter == Quarter.Custom) {
        "РЎРІРѕР№ РїРµСЂРёРѕРґ"
    } else {
        "${settings.quarter.label}, ${settings.year} РіРѕРґ"
    }
    val sortLabel = "${settings.sortField.label} вЂў ${settings.sortOrder.label}"

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(appViewModel.scrollToTopEvent) {
        appViewModel.scrollToTopEvent.collect { route ->
            if (route == "main/receipts" && rows.isNotEmpty()) {
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
        item {

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    SettingsRow("РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ", effectiveUserName)
                    SettingsRow("РџРµСЂРёРѕРґ", periodLabel)
                    SettingsRow(
                        "Р”Р°С‚С‹",
                        "${DateFormat.formatDate(settings.from)} вЂ” ${DateFormat.formatDate(settings.to)}"
                    )
                    SettingsRow("РЎРѕСЂС‚РёСЂРѕРІРєР°", sortLabel)
                    SettingsRow("Р§РµРєРѕРІ", rows.size.toString())
                }
            }
            Spacer(Modifier.height(8.dp))

            ExportActionGrid(
                onOpen = { receiptsViewModel.previewReceiptsPdf(context) },
                onPrint = { receiptsViewModel.printAllAsSheets(context) },
                onSave = { showSaveFormatDialog = true },
                onShare = { showShareFormatDialog = true },
                isSaving = isSaving,
                saveProgress = saveProgress,
                isSaveEnabled = !isSaving && rows.isNotEmpty(),
                isOpenPrintEnabled = rows.isNotEmpty()
            )

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
                        "РќРµС‚ С‡РµРєРѕРІ РїРѕ РІС‹Р±СЂР°РЅРЅС‹Рј РЅР°СЃС‚СЂРѕР№РєР°Рј РѕС‚С‡С‘С‚Р°",
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
            title = { Text("РЈРґР°Р»РёС‚СЊ С‡РµРє?") },
            text = {
                Text(
                    "${item.receipt.sellerName} РѕС‚ " +
                        DateFormat.formatDateTime(item.receipt.purchasedAt)
                )
            },
            confirmButton = {
                Button(onClick = {
                    receiptsViewModel.deleteReceipt(item.receipt.id)
                    pendingDelete = null
                }) { Text("РЈРґР°Р»РёС‚СЊ") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text("РћС‚РјРµРЅР°") }
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

    if (showSaveFormatDialog) {
        FormatChoiceDialog(
            title = "РЎРѕС…СЂР°РЅРёС‚СЊ С‡РµРєРё РєР°Рє",
            onDismiss = { showSaveFormatDialog = false },
            onChoose = { format ->
                showSaveFormatDialog = false
                when (format) {
                    ExportFileFormat.PDF ->
                        savePdfLauncher.launch(receiptsViewModel.suggestedReceiptsPdfName())
                    ExportFileFormat.XLSX ->
                        saveXlsxLauncher.launch(receiptsViewModel.suggestedReceiptsXlsxName())
                }
            }
        )
    }
    if (showShareFormatDialog) {
        FormatChoiceDialog(
            title = "РџРѕРґРµР»РёС‚СЊСЃСЏ С‡РµРєР°РјРё",
            onDismiss = { showShareFormatDialog = false },
            onChoose = { format ->
                showShareFormatDialog = false
                when (format) {
                    ExportFileFormat.PDF -> receiptsViewModel.shareReceiptsPdf(context)
                    ExportFileFormat.XLSX -> receiptsViewModel.exportXlsx(context)
                }
            }
        )
    }

    saveSuccessMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { saveSuccessMessage = null },
            title = { Text("РЎРѕС…СЂР°РЅРµРЅРѕ") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { saveSuccessMessage = null }) { Text("OK") }
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
        shape = RoundedCornerShape(20.dp),
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
                            "РЎСѓРјРјР°: ",
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
                            "РќР”РЎ: ",
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
                    contentDescription = "РЈРґР°Р»РёС‚СЊ",
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
                Text("РџРѕРґРµР»РёС‚СЊСЃСЏ")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Р—Р°РєСЂС‹С‚СЊ") }
        },
        title = { Text("Р§РµРє в„–$ordinal") },
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
        contentDescription = "РџСЂРµРІСЊСЋ С‡РµРєР° в„–$ordinal",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

