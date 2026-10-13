package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.uzb_qqs_for_dip.R
import com.example.uzb_qqs_for_dip.data.model.ZeroVatReceiptInfo
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat

/**
 * Диалог предупреждения при обнаружении чеков, у которых НДС стал 0 после синхронизации с ОФД.
 * Предоставляет выбор: «Удалить из базы» или «Оставить для просмотра».
 */
@Composable
fun ZeroVatDetectedDialog(
    detectedReceipts: List<ZeroVatReceiptInfo>,
    onDelete: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    if (detectedReceipts.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.zero_vat_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.zero_vat_dialog_message, detectedReceipts.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((detectedReceipts.size * 54).coerceAtMost(220).dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(detectedReceipts) { item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = item.sellerName.ifBlank { "—" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = DateFormat.formatDateTime(item.purchasedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${MoneyFormat.fromTiyin(item.previousVatTiyin)} сум",
                                        style = MaterialTheme.typography.bodySmall,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "→ 0 сум",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDelete(detectedReceipts.map { it.receiptId }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.zero_vat_dialog_delete))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.zero_vat_dialog_keep))
            }
        }
    )
}
