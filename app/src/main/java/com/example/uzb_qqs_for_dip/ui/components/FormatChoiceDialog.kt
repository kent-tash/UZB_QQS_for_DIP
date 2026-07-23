package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ExportFileFormat { PDF, XLSX }

@Composable
fun FormatChoiceDialog(
    title: String = "Формат файла",
    onDismiss: () -> Unit,
    onChoose: (ExportFileFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                FormatRow(
                    label = "PDF",
                    subtitle = "Документ для печати и просмотра"
                ) { onChoose(ExportFileFormat.PDF) }
                HorizontalDivider()
                FormatRow(
                    label = "Excel (xlsx)",
                    subtitle = "Таблица для Excel / Google Sheets"
                ) { onChoose(ExportFileFormat.XLSX) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun FormatRow(
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
