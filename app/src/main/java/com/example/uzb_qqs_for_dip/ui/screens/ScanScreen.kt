package com.example.uzb_qqs_for_dip.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uzb_qqs_for_dip.network.ParsedReceipt
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.ExistingOwner
import com.example.uzb_qqs_for_dip.ui.ScanState
import com.example.uzb_qqs_for_dip.ui.ScanViewModel
import com.example.uzb_qqs_for_dip.ui.SheetItemStatus
import com.example.uzb_qqs_for_dip.ui.components.MultiQrCameraScannerDialog
import com.example.uzb_qqs_for_dip.ui.components.SheetPreviewDialog
import com.example.uzb_qqs_for_dip.ui.theme.Danger
import com.example.uzb_qqs_for_dip.ui.theme.Success
import com.example.uzb_qqs_for_dip.ui.theme.Warning
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import com.example.uzb_qqs_for_dip.util.startQrScanner

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    appViewModel: AppViewModel,
    scanViewModel: ScanViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    val state by scanViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state is ScanState.Error) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(message = (state as ScanState.Error).message, duration = SnackbarDuration.Short)
            scanViewModel.reset()
        }
    }
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()
    val sheetPreviewItems by scanViewModel.sheetPreviewItems.collectAsStateWithLifecycle()
    val sheetLoading by scanViewModel.sheetLoading.collectAsStateWithLifecycle()
    val sheetSummary by scanViewModel.sheetSummary.collectAsStateWithLifecycle()
    val scannedCount by scanViewModel.scannedCount.collectAsStateWithLifecycle()

    // РЎРёСЃС‚РµРјРЅС‹Р№ PhotoPicker вЂ” РЅРµ С‚СЂРµР±СѓРµС‚ РЅРёРєР°РєРёС… runtime-СЂР°Р·СЂРµС€РµРЅРёР№.
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scanViewModel.handleImageFromGallery(context, uri)
        }
    }

    val sheetGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { scanViewModel.prepareSheetFromUri(context, it) } }

    // Р”РёР°Р»РѕРі РІРІРѕРґР° СЃСЃС‹Р»РєРё РЅР° СЌР»РµРєС‚СЂРѕРЅРЅС‹Р№ С‡РµРє (РЅР°РїСЂРёРјРµСЂ, СЃРєРѕРїРёСЂРѕРІР°РЅРЅРѕР№ РёР· SMS/Telegram).
    var showLinkDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showSheetSourceDialog by remember { mutableStateOf(false) }
    var showSheetCamera by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(appViewModel.scrollToTopEvent) {
        appViewModel.scrollToTopEvent.collect { route ->
            if (route == "main/scan") {
                scrollState.animateScrollTo(0)
            }
        }
    }

    val isBusy = state is ScanState.Loading ||
        state is ScanState.Parsed ||
        sheetLoading ||
        sheetPreviewItems.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Box(modifier = Modifier.clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) { Text(text = "Отсканировано: $scannedCount", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) } }
        Header(userName = currentUser?.fullName.orEmpty())

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Р”РѕР±Р°РІРёС‚СЊ С‡РµРє",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "РњРѕР¶РЅРѕ РѕС‚СЃРєР°РЅРёСЂРѕРІР°С‚СЊ QR-РєРѕРґ, РІС‹Р±СЂР°С‚СЊ СЃРЅРёРјРѕРє С‡РµРєР° РёР· РіР°Р»РµСЂРµРё " +
                        "РёР»Рё РІСЃС‚Р°РІРёС‚СЊ СЃСЃС‹Р»РєСѓ РЅР° СЌР»РµРєС‚СЂРѕРЅРЅС‹Р№ С‡РµРє. РџСЂРёР»РѕР¶РµРЅРёРµ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё " +
                        "Р·Р°РіСЂСѓР·РёС‚ РґР°РЅРЅС‹Рµ СЃ РїРѕСЂС‚Р°Р»Р° soliq.uz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (!isBusy) {
                            startQrScanner(
                                context = context,
                                onScanned = { scanViewModel.handleScan(it) },
                                onError = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (state is ScanState.Loading) "Р—Р°РіСЂСѓР·РєР° С‡РµРєР°..." else "РЎРєР°РЅРёСЂРѕРІР°С‚СЊ QR-РєРѕРґ")
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (!isBusy) {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Р—Р°РіСЂСѓР·РёС‚СЊ С„РѕС‚Рѕ С‡РµРєР°")
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { if (!isBusy) showLinkDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Р”РѕР±Р°РІРёС‚СЊ РїРѕ СЃСЃС‹Р»РєРµ")
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { if (!isBusy) showSheetSourceDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Icon(Icons.Outlined.GridView, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("РЎРєР°РЅ РІСЃРµС… С‡РµРєРѕРІ")
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { if (!isBusy) showManualDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Р”РѕР±Р°РІРёС‚СЊ РІСЂСѓС‡РЅСѓСЋ")
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "РњРѕР¶РЅРѕ РІС‹Р±СЂР°С‚СЊ РіРѕС‚РѕРІС‹Р№ СЃРЅРёРјРѕРє С‡РµРєР° РёР· РїР°РјСЏС‚Рё СѓСЃС‚СЂРѕР№СЃС‚РІР° РёР»Рё РІСЃС‚Р°РІРёС‚СЊ " +
                        "СЃСЃС‹Р»РєСѓ СЃ РїРѕСЂС‚Р°Р»РѕРІ soliq.uz / multicard.uz вЂ” РїСЂРёР»РѕР¶РµРЅРёРµ СЃР°РјРѕ " +
                        "Р·Р°РіСЂСѓР·РёС‚ Рё СЂР°СЃРїРѕР·РЅР°РµС‚ РґР°РЅРЅС‹Рµ С‡РµРєР°. В«РЎРєР°РЅ РІСЃРµС… С‡РµРєРѕРІВ» С‡РёС‚Р°РµС‚ " +
                        "СЃСЂР°Р·Сѓ РЅРµСЃРєРѕР»СЊРєРѕ QR РєР°РјРµСЂРѕР№ РёР»Рё СЃ РѕРґРЅРѕРіРѕ С„РѕС‚Рѕ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (val s = state) {
            ScanState.Idle -> { /* nothing */ }
            ScanState.Loading -> LoadingCard()
            is ScanState.Parsed -> { /* РґРёР°Р»РѕРі СЃРј. РЅРёР¶Рµ */ }
            is ScanState.Error -> { /* SnackBar is handling this now */ }
        }
    }

    val parsedState = state as? ScanState.Parsed
    if (parsedState != null) {
        Dialog(
            onDismissRequest = { scanViewModel.reset() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            ParsedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .wrapContentHeight(),
                parsed = parsedState.parsed,
                existingOwner = parsedState.existingOwner,
                onSave = {
                    scanViewModel.saveCurrent {
                        Toast.makeText(context, "Р§РµРє СЃРѕС…СЂР°РЅС‘РЅ", Toast.LENGTH_SHORT).show()
                    }
                },
                onCancel = { scanViewModel.reset() }
            )
        }
    }

    if (showManualDialog) {
        com.example.uzb_qqs_for_dip.ui.components.ManualEntryBottomSheet(
            onDismiss = { showManualDialog = false },
            onSubmit = { storeName, dateMs, totalTiyin, vatTiyin, photoUri ->
                showManualDialog = false
                scanViewModel.saveManualReceipt(
                    context = context,
                    storeName = storeName,
                    dateMs = dateMs,
                    totalAmountTiyin = totalTiyin,
                    vatAmountTiyin = vatTiyin,
                    photoUri = photoUri,
                    onSaved = { Toast.makeText(context, "Р§РµРє РґРѕР±Р°РІР»РµРЅ РІСЂСѓС‡РЅСѓСЋ", Toast.LENGTH_SHORT).show() },
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                )
            }
        )
    }

    if (showLinkDialog) {
        AddLinkDialog(
            onDismiss = { showLinkDialog = false },
            onSubmit = { url ->
                showLinkDialog = false
                scanViewModel.handleScan(url)
            }
        )
    }

    if (showSheetSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSheetSourceDialog = false },
            title = { Text("РЎРєР°РЅ РІСЃРµС… С‡РµРєРѕРІ") },
            text = {
                Text(
                    "РћС‚СЃРєР°РЅРёСЂСѓР№С‚Рµ РІСЃРµ QR РєР°РјРµСЂРѕР№ РёР»Рё РІС‹Р±РµСЂРёС‚Рµ С„РѕС‚Рѕ СЃ РЅРµСЃРєРѕР»СЊРєРёРјРё QR РёР· РіР°Р»РµСЂРµРё."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSheetSourceDialog = false
                        showSheetCamera = true
                    }
                ) { Text("РљР°РјРµСЂР°") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSheetSourceDialog = false
                        sheetGalleryLauncher.launch("image/*")
                    }
                ) { Text("Р“Р°Р»РµСЂРµСЏ") }
            }
        )
    }

    if (showSheetCamera) {
        MultiQrCameraScannerDialog(
            onDismiss = { showSheetCamera = false },
            onQrDetected = { url ->
                scanViewModel.processSingleQr(url)
            }
        )
    }

    sheetSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = scanViewModel::clearSheetSummary,
            title = { Text("РЎРєР°РЅ РІСЃРµС… С‡РµРєРѕРІ") },
            text = { Text(summary.message) },
            confirmButton = {
                TextButton(onClick = scanViewModel::clearSheetSummary) { Text("OK") }
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
                    Text("Р Р°СЃРїРѕР·РЅР°С‘Рј QR...")
                }
            }
        }
    }

    if (sheetPreviewItems.isNotEmpty()) {
        SheetPreviewDialog(
            items = sheetPreviewItems,
            loading = sheetLoading,
            onToggle = scanViewModel::toggleSheetItem,
            onConfirm = scanViewModel::confirmSheetSelection,
            onCancel = scanViewModel::clearSheetPreview,
            alreadyThisLabel = "РЈР¶Рµ СЃРѕС…СЂР°РЅС‘РЅ",
            titlePrefix = "Р§РµРєРё",
            confirmableStatuses = setOf(SheetItemStatus.NEW)
        )
    }
}

/**
 * Р”РёР°Р»РѕРі РІРІРѕРґР° СЃСЃС‹Р»РєРё РЅР° СЌР»РµРєС‚СЂРѕРЅРЅС‹Р№ С‡РµРє. РџСЂРёРЅРёРјР°СЋС‚СЃСЏ URL ofd.soliq.uz, ofd.multicard.uz
 * Рё С‚.Рї. вЂ” РґР°Р»РµРµ РёС… РѕР±СЂР°Р±Р°С‚С‹РІР°РµС‚ С‚Р° Р¶Рµ Р»РѕРіРёРєР°, С‡С‚Рѕ Рё QR-РєРѕРґ, РѕС‚СЃРєР°РЅРёСЂРѕРІР°РЅРЅС‹Р№ РєР°РјРµСЂРѕР№.
 */
@Composable
private fun AddLinkDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    val trimmed = url.trim()
    val looksValid = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Р”РѕР±Р°РІРёС‚СЊ С‡РµРє РїРѕ СЃСЃС‹Р»РєРµ") },
        text = {
            Column {
                Text(
                    "Р’СЃС‚Р°РІСЊС‚Рµ СЃСЃС‹Р»РєСѓ РЅР° СЌР»РµРєС‚СЂРѕРЅРЅС‹Р№ С‡РµРє (РЅР°РїСЂРёРјРµСЂ, СЃ soliq.uz РёР»Рё " +
                        "multicard.uz). РџСЂРёР»РѕР¶РµРЅРёРµ СЃР°РјРѕ Р·Р°РіСЂСѓР·РёС‚ СЃС‚СЂР°РЅРёС†Сѓ С‡РµРєР° Рё " +
                        "СЂР°СЃРїРѕР·РЅР°РµС‚ РїРѕР»СЏ.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("РЎСЃС‹Р»РєР° РЅР° С‡РµРє") },
                    placeholder = { Text("https://ofd.soliq.uz/check?...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                if (trimmed.isNotEmpty() && !looksValid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "РЎСЃС‹Р»РєР° РґРѕР»Р¶РЅР° РЅР°С‡РёРЅР°С‚СЊСЃСЏ СЃ http:// РёР»Рё https://",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(trimmed) },
                enabled = looksValid
            ) { Text("Р—Р°РіСЂСѓР·РёС‚СЊ") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("РћС‚РјРµРЅР°") }
        }
    )
}

@Composable
private fun Header(userName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Р—РґСЂР°РІСЃС‚РІСѓР№С‚Рµ,",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                userName.ifEmpty { "вЂ”" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.size(12.dp))
            Text("Р—Р°РіСЂСѓР¶Р°РµРј СЃС‚СЂР°РЅРёС†Сѓ С‡РµРєР° Рё СЂР°СЃРїРѕР·РЅР°С‘Рј РґР°РЅРЅС‹Рµ...")
        }
    }
}

@Composable
private fun ParsedCard(
    modifier: Modifier = Modifier,
    parsed: ParsedReceipt,
    existingOwner: ExistingOwner? = null,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val isValid = parsed.isValid
    val canSave = isValid && existingOwner !is ExistingOwner.SameUser && existingOwner !is ExistingOwner.OtherUser
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(
                        icon = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        color = if (isValid) Success else Danger,
                        text = if (isValid) "Р§РµРє СЂР°СЃРїРѕР·РЅР°РЅ" else "РќРµ РІСЃРµ РїРѕР»СЏ СЂР°СЃРїРѕР·РЅР°РЅС‹"
                    )
                    Spacer(Modifier.weight(1f))
                }
                when (existingOwner) {
                    ExistingOwner.SameUser -> {
                        Spacer(Modifier.height(8.dp))
                        StatusBadge(
                            icon = Icons.Filled.Error,
                            color = Warning,
                            text = "Р­С‚РѕС‚ С‡РµРє СѓР¶Рµ СЃРѕС…СЂР°РЅС‘РЅ Сѓ РІР°СЃ"
                        )
                    }
                    is ExistingOwner.OtherUser -> {
                        Spacer(Modifier.height(8.dp))
                        StatusBadge(
                            icon = Icons.Filled.Error,
                            color = Danger,
                            text = "Р”Р°РЅРЅС‹Р№ С‡РµРє СѓР¶Рµ РµСЃС‚СЊ Сѓ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ ${existingOwner.fullName}"
                        )
                    }
                    null -> Unit
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                ReceiptField("Р”Р°С‚Р° РїРѕРєСѓРїРєРё", parsed.purchasedAt?.let { DateFormat.formatDateTime(it) })
                ReceiptField("Р®СЂ. Р»РёС†Рѕ", parsed.sellerName)
                ReceiptField("РђРґСЂРµСЃ", parsed.address)
                ReceiptField("РРќРќ (STIR)", parsed.tin)
                ReceiptField(
                    "РС‚РѕРіРѕРІР°СЏ СЃСѓРјРјР°, СЃСѓРј",
                    parsed.totalAmountTiyin?.let { MoneyFormat.fromTiyin(it) },
                    bold = true
                )
                ReceiptField(
                    "РќР”РЎ (QQS), СЃСѓРј",
                    parsed.vatAmountTiyin?.let { MoneyFormat.fromTiyin(it) },
                    bold = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    parsed.qrUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }

            // Р¤РёРєСЃРёСЂРѕРІР°РЅРЅС‹Рµ РєРЅРѕРїРєРё РІРЅРёР·Сѓ РєР°СЂС‚РѕС‡РєРё
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("РћС‚РјРµРЅР°") }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("РЎРѕС…СЂР°РЅРёС‚СЊ")
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(20.dp)) {
            StatusBadge(
                icon = Icons.Filled.Error,
                color = Danger,
                text = "РћС€РёР±РєР°"
            )
            Spacer(Modifier.height(8.dp))
            Text(message)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
            ) { Text("Р—Р°РєСЂС‹С‚СЊ") }
        }
    }
}

@Composable
private fun StatusBadge(icon: ImageVector, color: androidx.compose.ui.graphics.Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReceiptField(label: String, value: String?, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            value?.takeIf { it.isNotBlank() } ?: "вЂ”",
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(0.55f)
        )
    }
}











