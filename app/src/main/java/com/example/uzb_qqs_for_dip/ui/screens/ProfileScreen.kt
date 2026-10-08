package com.example.uzb_qqs_for_dip.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uzb_qqs_for_dip.data.backup.AppBackup
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    appViewModel: AppViewModel,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val user by appViewModel.currentUser.collectAsStateWithLifecycle()
    val editError by appViewModel.editError.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var showReplaceWarning by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    LaunchedEffect(appViewModel.scrollToTopEvent) {
        appViewModel.scrollToTopEvent.collect { route ->
            if (route == "main/profile") {
                scrollState.animateScrollTo(0)
            }
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AppBackup.MIME_TYPE)
    ) { uri ->
        if (uri != null) appViewModel.exportBackupToUri(context, uri)
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedBackupUri = uri
            showReplaceWarning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user?.fullName?.take(1)?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(
                            user?.fullName ?: "РќРµ Р°РІС‚РѕСЂРёР·РѕРІР°РЅ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        user?.position?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                ProfileRow("РРјСЏ", user?.fullName ?: "вЂ”")
                ProfileRow("Р”РѕР»Р¶РЅРѕСЃС‚СЊ", user?.position ?: "вЂ”")
                ProfileRow("Р.Рћ. Р¤Р°РјРёР»РёСЏ РґР»СЏ РїРѕРґРїРёСЃРё", user?.initialsSurname ?: "вЂ”")
                ProfileRow("РћСЂРіР°РЅРёР·Р°С†РёСЏ", user?.organization?.ifBlank { "вЂ”" } ?: "вЂ”")
                ProfileRow(
                    "Р РѕР»СЊ",
                    if (user?.role == UserRole.AUDITOR) "РђСѓРґРёС‚РѕСЂ" else "РЎРѕС‚СЂСѓРґРЅРёРє"
                )
            }
        }

        // Role switch card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Р РµР¶РёРј СЂР°Р±РѕС‚С‹",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (user?.role == UserRole.AUDITOR)
                        "Р’С‹ СЂР°Р±РѕС‚Р°РµС‚Рµ РІ СЂРµР¶РёРјРµ В«РђСѓРґРёС‚РѕСЂВ». Р”РѕСЃС‚СѓРїРЅР° РІРєР»Р°РґРєР° В«РђСѓРґРёС‚В» РґР»СЏ РєРІР°СЂС‚Р°Р»СЊРЅРѕР№ СЃРІРµСЂРєРё."
                    else
                        "Р’С‹ СЂР°Р±РѕС‚Р°РµС‚Рµ РІ СЂРµР¶РёРјРµ В«РЎРѕС‚СЂСѓРґРЅРёРєВ». Р’РєР»СЋС‡РёС‚Рµ СЂРµР¶РёРј Р°СѓРґРёС‚РѕСЂР°, С‡С‚РѕР±С‹ РїРѕР»СѓС‡РёС‚СЊ РґРѕСЃС‚СѓРї Рє РІРєР»Р°РґРєРµ В«РђСѓРґРёС‚В».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { appViewModel.switchCurrentUserRole() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text(
                        if (user?.role == UserRole.AUDITOR)
                            "РџРµСЂРµРєР»СЋС‡РёС‚СЊ РЅР° В«РЎРѕС‚СЂСѓРґРЅРёРєВ»"
                        else
                            "РџРµСЂРµРєР»СЋС‡РёС‚СЊ РЅР° В«РђСѓРґРёС‚РѕСЂВ»"
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Р РµР·РµСЂРІРЅР°СЏ РєРѕРїРёСЏ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "РћРґРёРЅ С„Р°Р№Р» JSON: РІСЃРµ РїСЂРѕС„РёР»Рё, СЃРѕС…СЂР°РЅС‘РЅРЅС‹Рµ С‡РµРєРё Рё РЅР°СЃС‚СЂРѕР№РєРё РѕС‚С‡С‘С‚Р°.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        createBackupLauncher.launch("${AppBackup.FILE_BASE_NAME}_$stamp.json")
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("РЎРѕР·РґР°С‚СЊ Р±СЌРєР°Рї")
                }
                Button(
                    onClick = {
                        openBackupLauncher.launch(arrayOf(AppBackup.MIME_TYPE, "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Р—Р°РіСЂСѓР·РёС‚СЊ Р±СЌРєР°Рї")
                }
                Button(
                    onClick = { appViewModel.shareBackup(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("РџРѕРґРµР»РёС‚СЊСЃСЏ РґР°РЅРЅС‹РјРё")
                }
            }
        }

        if (user != null) {
            Button(
                onClick = {
                    appViewModel.clearEditError()
                    editing = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Р РµРґР°РєС‚РёСЂРѕРІР°С‚СЊ")
            }

            Button(
                onClick = {
                    appViewModel.logout()
                    onLoggedOut()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Р’С‹Р№С‚Рё РёР· РїСЂРѕС„РёР»СЏ")
            }

            Button(
                onClick = { pendingDelete = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("РЈРґР°Р»РёС‚СЊ")
            }
        }
    }

    selectedBackupUri?.let { uri ->
        if (!showReplaceWarning) {
            AlertDialog(
                onDismissRequest = { selectedBackupUri = null },
                title = { Text("РљР°Рє РёСЃРїРѕР»СЊР·РѕРІР°С‚СЊ С„Р°Р№Р»?") },
                text = { Text("Р’С‹ РјРѕР¶РµС‚Рµ РґРѕР±Р°РІРёС‚СЊ РґР°РЅРЅС‹Рµ Рє С‚РµРєСѓС‰РёРј РёР»Рё РїРѕР»РЅРѕСЃС‚СЊСЋ Р·Р°РјРµРЅРёС‚СЊ Р±Р°Р·Сѓ.") },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showReplaceWarning = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("РџРѕР»РЅРѕСЃС‚СЊСЋ Р·Р°РјРµРЅРёС‚СЊ") }
                        
                        Button(
                            onClick = {
                                selectedBackupUri = null
                                android.widget.Toast.makeText(context, "РќР°С‡Р°С‚Рѕ СЃР»РёСЏРЅРёРµ Р±Р°Р·...", android.widget.Toast.LENGTH_SHORT).show()
                                appViewModel.mergeBackupFromUri(context, uri)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Р”РѕР±Р°РІРёС‚СЊ Рє С‚РµРєСѓС‰РёРј") }
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { selectedBackupUri = null }) { Text("РћС‚РјРµРЅР°") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showReplaceWarning = false },
                title = { Text("Р’РЅРёРјР°РЅРёРµ! Р’С‹ СѓРІРµСЂРµРЅС‹ РІ РїРѕР»РЅРѕР№ Р·Р°РјРµРЅРµ Р±Р°Р·С‹?") },
                text = { Text("РўРµРєСѓС‰РёРµ РґР°РЅРЅС‹Рµ Р±СѓРґСѓС‚ Р±РµР·РІРѕР·РІСЂР°С‚РЅРѕ РїРѕС‚РµСЂСЏРЅС‹.") },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedBackupUri = null
                            showReplaceWarning = false
                            appViewModel.importBackupFromUri(context, uri, onLoggedOut)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Р”Р°, Р·Р°РјРµРЅРёС‚СЊ") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showReplaceWarning = false }) { Text("РћС‚РјРµРЅР°") }
                }
            )
        }
    }

    if (editing) {
        val u = user
        if (u == null) {
            editing = false
        } else {
            EditProfileDialog(
                initialFullName = u.fullName,
                initialPosition = u.position,
                initialOrganization = u.organization,
                error = editError,
                onClearError = { appViewModel.clearEditError() },
                onDismiss = {
                    editing = false
                    appViewModel.clearEditError()
                },
                onConfirm = { fullName, position, organization ->
                    appViewModel.updateProfile(
                        userId = u.id,
                        fullName = fullName,
                        position = position,
                        organization = organization,
                        onDone = { editing = false }
                    )
                }
            )
        }
    }

    if (pendingDelete) {
        val u = user
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("РЈРґР°Р»РёС‚СЊ РїСЂРѕС„РёР»СЊ?") },
            text = {
                Text(
                    "РџСЂРѕС„РёР»СЊ В«${u?.fullName ?: ""}В» Рё РІСЃРµ СЃРІСЏР·Р°РЅРЅС‹Рµ СЃ РЅРёРј СЃРѕС…СЂР°РЅС‘РЅРЅС‹Рµ С‡РµРєРё " +
                        "Р±СѓРґСѓС‚ СѓРґР°Р»РµРЅС‹ Р±РµР·РІРѕР·РІСЂР°С‚РЅРѕ."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = false
                        u?.id?.let { id ->
                            appViewModel.deleteProfile(id) { onLoggedOut() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("РЈРґР°Р»РёС‚СЊ") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = false }) { Text("РћС‚РјРµРЅР°") }
            }
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
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
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.55f)
        )
    }
}

/**
 * Р”РёР°Р»РѕРі СЂРµРґР°РєС‚РёСЂРѕРІР°РЅРёСЏ РїРѕР»РµР№ С‚РµРєСѓС‰РµРіРѕ РїСЂРѕС„РёР»СЏ. РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ Рё РёР· РІРєР»Р°РґРєРё В«РџСЂРѕС„РёР»СЊВ»,
 * Рё РёР· СЌРєСЂР°РЅР° Р°РІС‚РѕСЂРёР·Р°С†РёРё (РєР°СЂР°РЅРґР°С€РёРє Сѓ РєР°Р¶РґРѕРіРѕ РїСЂРѕС„РёР»СЏ).
 */
@Composable
fun EditProfileDialog(
    initialFullName: String,
    initialPosition: String,
    initialOrganization: String = "",
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, position: String, organization: String) -> Unit,
    title: String = "Р РµРґР°РєС‚РёСЂРѕРІР°РЅРёРµ РїСЂРѕС„РёР»СЏ"
) {
    var fullName by remember { mutableStateOf(initialFullName) }
    var position by remember { mutableStateOf(initialPosition) }
    var organization by remember { mutableStateOf(initialOrganization) }

    LaunchedEffect(initialFullName, initialPosition, initialOrganization) {
        fullName = initialFullName
        position = initialPosition
        organization = initialOrganization
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; onClearError() },
                    label = { Text("РРјСЏ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ (РїРѕР»РЅРѕСЃС‚СЊСЋ)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it; onClearError() },
                    label = { Text("Р”РѕР»Р¶РЅРѕСЃС‚СЊ") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it; onClearError() },
                    label = { Text("РћСЂРіР°РЅРёР·Р°С†РёСЏ (РЅРµРѕР±СЏР·Р°С‚РµР»СЊРЅРѕ)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(fullName, position, organization) }) {
                Text("РЎРѕС…СЂР°РЅРёС‚СЊ")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("РћС‚РјРµРЅР°") }
        }
    )
}






