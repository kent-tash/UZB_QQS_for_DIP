package com.example.uzb_qqs_for_dip.ui.screens

import android.content.Context
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
import com.example.uzb_qqs_for_dip.ui.theme.Danger
import com.example.uzb_qqs_for_dip.ui.theme.Success
import com.example.uzb_qqs_for_dip.ui.theme.Warning
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import com.example.uzb_qqs_for_dip.util.startQrScanner

@Composable
fun ScanScreen(
    appViewModel: AppViewModel,
    scanViewModel: ScanViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by scanViewModel.state.collectAsStateWithLifecycle()
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()

    // Системный PhotoPicker — не требует никаких runtime-разрешений.
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scanViewModel.handleImageFromGallery(context, uri)
        }
    }

    // Диалог ввода ссылки на электронный чек (например, скопированной из SMS/Telegram).
    var showLinkDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    "Добавить чек",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Можно отсканировать QR-код, выбрать снимок чека из галереи " +
                        "или вставить ссылку на электронный чек. Приложение автоматически " +
                        "загрузит данные с портала soliq.uz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                val isBusy = state is ScanState.Loading || state is ScanState.Parsed
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (isBusy) "Загрузка чека..." else "Сканировать QR-код")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Загрузить фото чека")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { if (!isBusy) showLinkDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Добавить по ссылке")
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Можно выбрать готовый снимок чека из памяти устройства или вставить " +
                        "ссылку с порталов soliq.uz / multicard.uz — приложение само " +
                        "загрузит и распознает данные чека.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (val s = state) {
            ScanState.Idle -> { /* nothing */ }
            ScanState.Loading -> LoadingCard()
            is ScanState.Parsed -> { /* диалог см. ниже */ }
            is ScanState.Error -> ErrorCard(s.message) { scanViewModel.reset() }
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
                        Toast.makeText(context, "Чек сохранён", Toast.LENGTH_SHORT).show()
                    }
                },
                onCancel = { scanViewModel.reset() }
            )
        }
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
}

/**
 * Диалог ввода ссылки на электронный чек. Принимаются URL ofd.soliq.uz, ofd.multicard.uz
 * и т.п. — далее их обрабатывает та же логика, что и QR-код, отсканированный камерой.
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
        title = { Text("Добавить чек по ссылке") },
        text = {
            Column {
                Text(
                    "Вставьте ссылку на электронный чек (например, с soliq.uz или " +
                        "multicard.uz). Приложение само загрузит страницу чека и " +
                        "распознает поля.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Ссылка на чек") },
                    placeholder = { Text("https://ofd.soliq.uz/check?...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                if (trimmed.isNotEmpty() && !looksValid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ссылка должна начинаться с http:// или https://",
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
            ) { Text("Загрузить") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun Header(userName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Здравствуйте,",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                userName.ifEmpty { "—" },
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
            Text("Загружаем страницу чека и распознаём данные...")
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
                        text = if (isValid) "Чек распознан" else "Не все поля распознаны"
                    )
                    Spacer(Modifier.weight(1f))
                }
                when (existingOwner) {
                    ExistingOwner.SameUser -> {
                        Spacer(Modifier.height(8.dp))
                        StatusBadge(
                            icon = Icons.Filled.Error,
                            color = Warning,
                            text = "Этот чек уже сохранён у вас"
                        )
                    }
                    is ExistingOwner.OtherUser -> {
                        Spacer(Modifier.height(8.dp))
                        StatusBadge(
                            icon = Icons.Filled.Error,
                            color = Danger,
                            text = "Данный чек уже есть у пользователя ${existingOwner.fullName}"
                        )
                    }
                    null -> Unit
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                ReceiptField("Дата покупки", parsed.purchasedAt?.let { DateFormat.formatDateTime(it) })
                ReceiptField("Юр. лицо", parsed.sellerName)
                ReceiptField("Адрес", parsed.address)
                ReceiptField("ИНН (STIR)", parsed.tin)
                ReceiptField(
                    "Итоговая сумма, сум",
                    parsed.totalAmountTiyin?.let { MoneyFormat.fromTiyin(it) },
                    bold = true
                )
                ReceiptField(
                    "НДС (QQS), сум",
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

            // Фиксированные кнопки внизу карточки
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Отмена") }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Сохранить")
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
                text = "Ошибка"
            )
            Spacer(Modifier.height(8.dp))
            Text(message)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Закрыть") }
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
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(0.55f)
        )
    }
}

