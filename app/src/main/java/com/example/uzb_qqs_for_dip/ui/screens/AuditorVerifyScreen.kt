package com.example.uzb_qqs_for_dip.ui.screens

import android.widget.Toast
import com.example.uzb_qqs_for_dip.ui.components.MultiQrCameraScannerDialog
import com.example.uzb_qqs_for_dip.ui.components.SheetPreviewDialog
import com.example.uzb_qqs_for_dip.util.startQrScanner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.AuditorVerifyViewModel
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
    var showManualDialog by remember { mutableStateOf(false) }
    var showSheetCamera by remember { mutableStateOf(false) }

    autoVerifyMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearAutoVerifyMessage,
            title = { Text("РђРІС‚РѕРјР°С‚РёС‡РµСЃРєР°СЏ РїСЂРѕРІРµСЂРєР°") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = vm::clearAutoVerifyMessage) { Text("OK") }
            }
        )
    }

    manualVerifyMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearManualVerifyMessage,
            title = { Text("Р СѓС‡РЅР°СЏ РїСЂРѕРІРµСЂРєР°") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = vm::clearManualVerifyMessage) { Text("OK") }
            }
        )
    }

    if (showManualVerifyConfirm) {
        AlertDialog(
            onDismissRequest = { showManualVerifyConfirm = false },
            title = { Text("РџСЂРѕРІРµСЂРµРЅРѕ РІСЂСѓС‡РЅСѓСЋ") },
            text = {
                Text(
                    "РџРѕРґС‚РІРµСЂРґРёС‚СЊ, С‡С‚Рѕ С‡РµРєРё СЃРѕС‚СЂСѓРґРЅРёРєР° ${selectedEmployee?.fullName ?: ""} " +
                        "РїСЂРѕРІРµСЂРµРЅС‹ РІСЂСѓС‡РЅСѓСЋ РїРѕ Р±СѓРјР°Р¶РЅС‹Рј РґРѕРєСѓРјРµРЅС‚Р°Рј (Р±РµР· Р·Р°РЅРµСЃРµРЅРёСЏ РІ РїСЂРёР»РѕР¶РµРЅРёРµ)?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showManualVerifyConfirm = false
                    vm.markManuallyVerified()
                }) { Text("РџРѕРґС‚РІРµСЂРґРёС‚СЊ") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showManualVerifyConfirm = false }) { Text("РћС‚РјРµРЅР°") }
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.handleImageFromGallery(context.applicationContext, it) } }

    val sheetGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.prepareSheetFromUri(context.applicationContext, it) } }

    if (showManualDialog && selectedEmployee != null) {
        com.example.uzb_qqs_for_dip.ui.components.ManualEntryBottomSheet(
            onDismiss = { showManualDialog = false },
            onSubmit = { storeName, dateMs, totalTiyin, vatTiyin, photoUri ->
                showManualDialog = false
                vm.handleManualReceipt(storeName, dateMs, totalTiyin, vatTiyin, photoUri)
            }
        )
    }

    if (showSheetSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSheetSourceDialog = false },
            title = { Text("РЎРєР°РЅ Р»РёСЃС‚Р°") },
            text = {
                Text("РћС‚СЃРєР°РЅРёСЂСѓР№С‚Рµ РІСЃРµ QR РЅР° Р»РёСЃС‚Рµ РєР°РјРµСЂРѕР№ РёР»Рё РІС‹Р±РµСЂРёС‚Рµ С„РѕС‚Рѕ Р»РёСЃС‚Р° РёР· РіР°Р»РµСЂРµРё.")
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
                vm.processSingleQr(url)
            }
        )
    }

    sheetSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = vm::clearSheetSummary,
            title = { Text("РЎРєР°РЅ Р»РёСЃС‚Р°") },
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
                    Text("Р Р°СЃРїРѕР·РЅР°С‘Рј QR РЅР° Р»РёСЃС‚Рµ...")
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
            onCancel = vm::clearSheetPreview,
            alreadyThisLabel = "РЈР¶Рµ Сѓ СЃРѕС‚СЂСѓРґРЅРёРєР°",
            titlePrefix = "Р§РµРєРё СЃ Р»РёСЃС‚Р°"
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

    Column(
        Modifier
            .fillMaxSize()
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
                    Text("РџСЂРѕРІРµСЂРёС‚СЊ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё (РІСЃРµ С‡РµРєРё РІ Р±Р°Р·Рµ)")
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
                        if (manuallyApproved) "РћС‚РјРµС‡РµРЅРѕ: РїСЂРѕРІРµСЂРµРЅРѕ РІСЂСѓС‡РЅСѓСЋ"
                        else "РџСЂРѕРІРµСЂРµРЅРѕ РІСЂСѓС‡РЅСѓСЋ"
                    )
                }

                OutlinedTextField(
                    value = receiptSearchQuery,
                    onValueChange = vm::setReceiptSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("РџРѕРёСЃРє С‡РµРєРѕРІ СЃРѕС‚СЂСѓРґРЅРёРєР°") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }
                )
                if (receiptSearchQuery.isNotBlank()) {
                    Text(
                        if (receiptSearchResults.isEmpty()) "РќРёС‡РµРіРѕ РЅРµ РЅР°Р№РґРµРЅРѕ"
                        else "РќР°Р№РґРµРЅРѕ: ${receiptSearchResults.size}",
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
                                        "РЎ СЂР°СЃРїРµС‡Р°С‚РєРё",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Text(
                                    DateFormat.formatDateTime(item.receipt.purchasedAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${MoneyFormat.fromTiyin(item.receipt.totalAmountTiyin)} В· РќР”РЎ ${MoneyFormat.fromTiyin(item.receipt.vatAmountTiyin)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(
                                    onClick = { vm.markReceiptVerifiedFromSearch(item.receipt.id) },
                                    enabled = true
                                ) {
                                    Text("РћС‚РјРµС‚РёС‚СЊ РїСЂРѕРІРµСЂРµРЅРЅС‹Рј")
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
                        "РЎРїРѕСЃРѕР± Р·Р°С…РІР°С‚Р° QR-РєРѕРґР°",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanActionButton(
                            icon = Icons.Outlined.QrCodeScanner,
                            label = "РљР°РјРµСЂР°",
                            enabled = selectedEmployee != null && verifyResult == VerifyResult.Idle && sheetPreviewItems.isEmpty() && !sheetLoading,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
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
                            label = "Р“Р°Р»РµСЂРµСЏ",
                            enabled = selectedEmployee != null && verifyResult == VerifyResult.Idle && sheetPreviewItems.isEmpty() && !sheetLoading,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { galleryLauncher.launch("image/*") }
                        )
                        ScanActionButton(
                            icon = Icons.Outlined.Link,
                            label = "РЎСЃС‹Р»РєР°",
                            enabled = selectedEmployee != null && verifyResult == VerifyResult.Idle && sheetPreviewItems.isEmpty() && !sheetLoading,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { showLinkDialog = true }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanActionButton(
                            icon = Icons.Outlined.GridView,
                            label = "РЎРєР°РЅ Р»РёСЃС‚Р°",
                            enabled = selectedEmployee != null && verifyResult == VerifyResult.Idle && sheetPreviewItems.isEmpty() && !sheetLoading,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { showSheetSourceDialog = true }
                        )
                        ScanActionButton(
                            icon = Icons.Outlined.Edit,
                            label = "Р’СЂСѓС‡РЅСѓСЋ",
                            enabled = selectedEmployee != null && verifyResult == VerifyResult.Idle && sheetPreviewItems.isEmpty() && !sheetLoading,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            onClick = { showManualDialog = true }
                        )
                    }
                }

                // Result overlay вЂ” floats above buttons until dismissed
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
                                Text("Р—Р°РіСЂСѓР¶Р°РµРј СЃС‚СЂР°РЅРёС†Сѓ С‡РµРєР°...")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeSelectorRow(
    employees: List<User>,
    selected: User?,
    onSelect: (User) -> Unit,
    onAddEmployee: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
            OutlinedTextField(
                value = selected?.fullName ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("РЎРѕС‚СЂСѓРґРЅРёРє") },
                placeholder = { Text("Р’С‹Р±РµСЂРёС‚Рµ СЃРѕС‚СЂСѓРґРЅРёРєР°") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (employees.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("РќРµС‚ СЃРѕС‚СЂСѓРґРЅРёРєРѕРІ вЂ” РґРѕР±Р°РІСЊС‚Рµ С‡РµСЂРµР· В«+В»") },
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
                            "РџСЂРѕРІРµСЂРµРЅРѕ РІСЂСѓС‡РЅСѓСЋ (С‡РµРєРё РЅРµ РІ РїСЂРёР»РѕР¶РµРЅРёРё)"
                        manuallyApproved ->
                            "РџСЂРѕРІРµСЂРµРЅРѕ РІСЂСѓС‡РЅСѓСЋ В· РІ Р±Р°Р·Рµ: ${stats.verifiedCount}/${stats.totalCount}"
                        stats.totalCount == 0 ->
                            "РќРµС‚ С‡РµРєРѕРІ Р·Р° РІС‹Р±СЂР°РЅРЅС‹Р№ РїРµСЂРёРѕРґ"
                        else ->
                            "РџСЂРѕРІРµСЂРµРЅРѕ: ${stats.verifiedCount} РёР· ${stats.totalCount} С‡РµРєРѕРІ"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allVerified) VerifySuccess else MaterialTheme.colorScheme.onSurface
                )
            }
            if (stats.verifiedCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "РЎСѓРјРјР°: ${MoneyFormat.fromTiyin(stats.verifiedTotalTiyin)} | " +
                        "РќР”РЎ: ${MoneyFormat.fromTiyin(stats.verifiedVatTiyin)}",
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
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    contentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (containerColor == androidx.compose.ui.graphics.Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else containerColor,
            contentColor = if (contentColor == androidx.compose.ui.graphics.Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
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
            statusText = "Р§РµРє СѓР¶Рµ РїСЂРёРЅР°РґР»РµР¶РёС‚: ${result.owner.fullName}"
            statusColor = VerifyDanger
            Icons.Filled.Error
        }
        result.markedVerified && result.alreadyForThisEmployee -> {
            statusText = "Р§РµРє РїРѕРґС‚РІРµСЂР¶РґС‘РЅ (СѓР¶Рµ РІ Р±Р°Р·Рµ)"
            statusColor = VerifySuccess
            Icons.Filled.CheckCircle
        }
        result.markedVerified -> {
            statusText = "Р§РµРє СЃРѕС…СЂР°РЅС‘РЅ Рё РїРѕРґС‚РІРµСЂР¶РґС‘РЅ"
            statusColor = VerifySuccess
            Icons.Filled.CheckCircle
        }
        else -> {
            statusText = "Р§РµРє РЅРµ СЃРѕС…СЂР°РЅС‘РЅ"
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
                    Text("Р§РµРє РІРЅРµ РІС‹Р±СЂР°РЅРЅРѕРіРѕ РєРІР°СЂС‚Р°Р»Р°", color = VerifyWarning,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            ReceiptDetailRow("Р”Р°С‚Р°", parsed.purchasedAt?.let { DateFormat.formatDateTime(it) })
            ReceiptDetailRow("РџСЂРѕРґР°РІРµС†", parsed.sellerName)
            ReceiptDetailRow("РС‚РѕРіРѕ", parsed.totalAmountTiyin?.let { MoneyFormat.fromTiyin(it) + " СЃСѓРј" }, bold = true)
            ReceiptDetailRow("РќР”РЎ", parsed.vatAmountTiyin?.let { MoneyFormat.fromTiyin(it) + " СЃСѓРј" }, bold = true)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("РЎР»РµРґСѓСЋС‰РёР№ С‡РµРє") }
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
                Text("РћС€РёР±РєР°", color = VerifyDanger, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(message)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Р—Р°РєСЂС‹С‚СЊ") }
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
        title = { Text("Р”РѕР±Р°РІРёС‚СЊ СЃРѕС‚СЂСѓРґРЅРёРєР°") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Р¤Р°РјРёР»РёСЏ РРјСЏ РћС‚С‡РµСЃС‚РІРѕ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Р”РѕР»Р¶РЅРѕСЃС‚СЊ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = initialsSurname,
                    onValueChange = { initialsSurname = it },
                    label = { Text("Р.Рћ. Р¤Р°РјРёР»РёСЏ (РґР»СЏ РїРѕРґРїРёСЃРё)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error, color = VerifyDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(fullName, position, initialsSurname) }) { Text("Р”РѕР±Р°РІРёС‚СЊ") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("РћС‚РјРµРЅР°") } }
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
        title = { Text("Р”РѕР±Р°РІРёС‚СЊ С‡РµРє РїРѕ СЃСЃС‹Р»РєРµ") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("РЎСЃС‹Р»РєР° РЅР° С‡РµРє") },
                placeholder = { Text("https://ofd.soliq.uz/check?...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSubmit(trimmed) }, enabled = looksValid) { Text("Р—Р°РіСЂСѓР·РёС‚СЊ") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("РћС‚РјРµРЅР°") } }
    )
}
