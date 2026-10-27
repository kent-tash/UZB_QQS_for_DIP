package com.example.uzb_qqs_for_dip.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uzb_qqs_for_dip.data.backup.AppBackup
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupContent(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    onNavigateToAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var showReplaceWarning by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AppBackup.MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            appViewModel.exportBackupToUri(context, uri)
        }
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
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Один файл JSON: все профили, сохранённые чеки и настройки отчёта.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                createBackupLauncher.launch("${AppBackup.FILE_BASE_NAME}_$stamp.json")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Создать бэкап", fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = {
                openBackupLauncher.launch(arrayOf(AppBackup.MIME_TYPE, "*/*"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Загрузить бэкап", fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = { appViewModel.shareBackup(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Поделиться данными", fontWeight = FontWeight.SemiBold)
        }
    }

    // --- Диалоги загрузки бэкапа ---
    selectedBackupUri?.let { uri ->
        if (!showReplaceWarning) {
            AlertDialog(
                onDismissRequest = { selectedBackupUri = null },
                title = { Text("Как использовать файл?") },
                text = { Text("Вы можете добавить данные к текущим или полностью заменить базу.") },
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
                        ) { Text("Полностью заменить") }

                        Button(
                            onClick = {
                                selectedBackupUri = null
                                Toast.makeText(context, "Начато слияние баз...", Toast.LENGTH_SHORT).show()
                                appViewModel.mergeBackupFromUri(context, uri)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Добавить к текущим") }
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { selectedBackupUri = null }) { Text("Отмена") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showReplaceWarning = false },
                title = { Text("Внимание! Вы уверены в полной замене базы?") },
                text = { Text("Текущие данные будут безвозвратно потеряны.") },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedBackupUri = null
                            showReplaceWarning = false
                            appViewModel.importBackupFromUri(context, uri, onNavigateToAuth)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Да, заменить") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showReplaceWarning = false }) { Text("Отмена") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupBottomSheet(
    appViewModel: AppViewModel,
    onDismissRequest: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Резервная копия",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            BackupContent(
                appViewModel = appViewModel,
                onNavigateToAuth = onNavigateToAuth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
