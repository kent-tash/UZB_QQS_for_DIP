import os
path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReceiptsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

old = """    AlertDialog(
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
        title = { Text(f"Чек №$ordinal") },""" # I am using string replacement, but $ordinal is kotlin string interpolation! Wait, `text` is just a string, so `$ordinal` is literal.

old = """    AlertDialog(
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
    )"""

new = """    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("Закрыть")
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Поделиться")
                }
            }
        },
        title = { Text("Чек №$ordinal") },
        text = {
            ReceiptCardPreview(item = item, ordinal = ordinal)
        }
    )"""

if old in text:
    text = text.replace(old, new)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    print("Success")
else:
    print("Not found")
