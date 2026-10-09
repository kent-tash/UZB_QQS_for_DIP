package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryBottomSheet(
    onDismiss: () -> Unit,
    onSubmit: (storeName: String, dateMs: Long, totalTiyin: Long, vatTiyin: Long, photoUri: android.net.Uri?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    
    var storeName by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") } // DD.MM.YYYY
    var totalAmountStr by remember { mutableStateOf("") }
    var vatAmountStr by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) photoUri = uri }

    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success && tempCameraUri != null) photoUri = tempCameraUri }

    var pendingCameraAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCameraAction?.invoke()
        }
        pendingCameraAction = null
    }

    fun runWithCameraPermission(action: () -> Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingCameraAction = action
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    val dateError = dateStr.isNotBlank() && parseDate(dateStr) == null
    val totalError = totalAmountStr.isNotBlank() && totalAmountStr.replace(',', '.').toDoubleOrNull() == null
    val vatError = vatAmountStr.isNotBlank() && vatAmountStr.replace(',', '.').toDoubleOrNull() == null
    
    val isValid = storeName.isNotBlank() && 
                  dateStr.isNotBlank() && !dateError &&
                  totalAmountStr.isNotBlank() && !totalError &&
                  vatAmountStr.isNotBlank() && !vatError &&
                  photoUri != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Добавить чек вручную",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it },
                label = { Text("Название магазина") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dateStr,
                onValueChange = { dateStr = it },
                label = { Text("Дата (ДД.ММ.ГГГГ)") },
                singleLine = true,
                isError = dateError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = totalAmountStr,
                onValueChange = { totalAmountStr = it },
                label = { Text("Итоговая сумма (сум)") },
                singleLine = true,
                isError = totalError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vatAmountStr,
                onValueChange = { vatAmountStr = it },
                label = { Text("Сумма НДС (сум)") },
                singleLine = true,
                isError = vatError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        runWithCameraPermission {
                            try {
                                val imagesDir = File(context.cacheDir, "images")
                                if (!imagesDir.exists()) imagesDir.mkdirs()
                                val file = File(imagesDir, "manual_receipt_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },

                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Камера")
                }
                Button(
                    onClick = {
                        try { photoPicker.launch("image/*") } catch (e: Exception) { e.printStackTrace() }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Галерея")
                }
            }
            if (photoUri != null) {
                Text(
                    text = "Фото прикреплено ✓", 
                    color = MaterialTheme.colorScheme.primary, 
                    style = MaterialTheme.typography.bodyMedium, 
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Отмена")
                }
                Button(
                    onClick = {
                        val dateMs = parseDate(dateStr) ?: return@Button
                        val totalTiyin = ((totalAmountStr.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong()
                        val vatTiyin = ((vatAmountStr.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong()
                        onSubmit(storeName, dateMs, totalTiyin, vatTiyin, photoUri)
                    },
                    enabled = isValid,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { 
                    Text("Сохранить") 
                }
            }
        }
    }
}

private fun parseDate(dateStr: String): Long? {
    return try {
        val format = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        format.parse(dateStr)?.time
    } catch (e: Exception) {
        null
    }
}
