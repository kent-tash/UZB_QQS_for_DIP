file_path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/AuditorVerifyScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Add showManualDialog state
state_old = "var showSheetCamera by remember { mutableStateOf(false) }"
state_new = "var showSheetCamera by remember { mutableStateOf(false) }\n    var showManualDialog by remember { mutableStateOf(false) }"
text = text.replace(state_old, state_new)

# 2. Add the ManualEntryBottomSheet import if not there
if "import com.example.uzb_qqs_for_dip.ui.components.ManualEntryBottomSheet" not in text:
    text = text.replace(
        "import com.example.uzb_qqs_for_dip.ui.components.MultiQrCameraScannerDialog",
        "import com.example.uzb_qqs_for_dip.ui.components.MultiQrCameraScannerDialog\nimport com.example.uzb_qqs_for_dip.ui.components.ManualEntryBottomSheet"
    )

# 3. Add the BottomSheet invocation
sheet_old = "if (showLinkDialog) {"
sheet_new = """if (showManualDialog) {
        ManualEntryBottomSheet(
            onDismiss = { showManualDialog = false },
            onSubmit = { storeName, dateMs, totalTiyin, vatTiyin, photoUri ->
                showManualDialog = false
                vm.submitManualEntry(context.applicationContext, storeName, dateMs, totalTiyin, vatTiyin, photoUri)
            }
        )
    }

    if (showLinkDialog) {"""
text = text.replace(sheet_old, sheet_new)

# 4. Replace the old Column of buttons with the new Grid of 5 buttons
import re

# We find the start of the buttons section:
start_marker = 'Text(\n                    "Сканировать QR-код",'

# And the end of the GridView button:
end_marker = 'onClick = { showSheetSourceDialog = true }\n                )'

buttons_new = """Text(
                    "Сканировать QR-код",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val btnEnabled = selectedEmployee != null && verifyResult == VerifyResult.Idle && sheetPreviewItems.isEmpty() && !sheetLoading

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanActionButton(
                        icon = Icons.Outlined.QrCodeScanner,
                        label = "Скан QR",
                        enabled = btnEnabled,
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
                        label = "Галерея",
                        enabled = btnEnabled,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = { galleryLauncher.launch("image/*") }
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanActionButton(
                        icon = Icons.Outlined.Link,
                        label = "Ссылка",
                        enabled = btnEnabled,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { showLinkDialog = true }
                    )
                    ScanActionButton(
                        icon = Icons.Outlined.Create,
                        label = "Вручную",
                        enabled = btnEnabled,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        onClick = { showManualDialog = true }
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanActionButton(
                        icon = Icons.Outlined.GridView,
                        label = "Скан всех чеков",
                        enabled = btnEnabled,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { showSheetSourceDialog = true }
                    )
                    Spacer(Modifier.weight(1f))
                }"""

# Using regex to replace everything between start_marker and end_marker
pattern = re.compile(re.escape(start_marker) + r'.*?' + re.escape(end_marker), re.DOTALL)
text = pattern.sub(buttons_new, text)

# Add Icons.Outlined.Create import if needed
if "import androidx.compose.material.icons.outlined.Create" not in text:
    text = text.replace(
        "import androidx.compose.material.icons.outlined.QrCodeScanner",
        "import androidx.compose.material.icons.outlined.QrCodeScanner\nimport androidx.compose.material.icons.outlined.Create"
    )

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)
print("Updated UI layout in AuditorVerifyScreen!")
