package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.uzb_qqs_for_dip.ui.SheetItemStatus
import com.example.uzb_qqs_for_dip.ui.SheetReceiptItem
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat

private val SheetSuccess = Color(0xFF2E7D32)
private val SheetDanger = Color(0xFFC62828)
private val SheetWarning = Color(0xFFF57F17)

/**
 * Диалог превью пакетного скана QR: список чеков с чекбоксами и подтверждением.
 *
 * @param alreadyThisLabel подпись для [SheetItemStatus.ALREADY_THIS]
 *   (аудитор: «Уже у сотрудника», пользователь: «Уже сохранён»).
 * @param titlePrefix префикс заголовка, например «Чеки с листа».
 */
@Composable
fun SheetPreviewDialog(
    items: List<SheetReceiptItem>,
    loading: Boolean,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    alreadyThisLabel: String = "Уже сохранён",
    titlePrefix: String = "Чеки",
    /** Статусы, которые учитываются в счётчике и разблокируют «Подтвердить». */
    confirmableStatuses: Set<SheetItemStatus> = setOf(
        SheetItemStatus.NEW,
        SheetItemStatus.ALREADY_THIS,
        SheetItemStatus.OUT_OF_PERIOD
    )
) {
    val selectedCount = items.count { it.selected && it.status in confirmableStatuses }

    Dialog(
        onDismissRequest = { if (!loading) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "$titlePrefix (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Выбрано к сохранению/подтверждению: $selectedCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        SheetPreviewRow(
                            item = item,
                            alreadyThisLabel = alreadyThisLabel,
                            enabled = !loading &&
                                item.status != SheetItemStatus.OTHER_OWNER &&
                                item.status != SheetItemStatus.ERROR,
                            onToggle = { onToggle(index) }
                        )
                        if (index < items.lastIndex) HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (loading) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !loading,
                        modifier = Modifier.weight(1f)
                    ) { Text("Отмена") }
                    Button(
                        onClick = onConfirm,
                        enabled = !loading && selectedCount > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Подтвердить") }
                }
            }
        }
    }
}

@Composable
private fun SheetPreviewRow(
    item: SheetReceiptItem,
    alreadyThisLabel: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val (label, color) = when (item.status) {
        SheetItemStatus.NEW -> "Новый" to SheetSuccess
        SheetItemStatus.ALREADY_THIS -> alreadyThisLabel to SheetSuccess
        SheetItemStatus.OTHER_OWNER ->
            "Чужой: ${item.ownerName ?: "?"}" to SheetDanger
        SheetItemStatus.ERROR ->
            (item.errorMessage ?: "Ошибка") to SheetDanger
        SheetItemStatus.OUT_OF_PERIOD -> "Вне периода" to SheetWarning
    }
    val parsed = item.parsed
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.selected,
            onCheckedChange = { if (enabled) onToggle() },
            enabled = enabled
        )
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = color,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall
            )
            if (parsed != null) {
                Text(
                    parsed.sellerName ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    listOfNotNull(
                        parsed.purchasedAt?.let { DateFormat.formatDateTime(it) },
                        parsed.totalAmountTiyin?.let { MoneyFormat.fromTiyin(it) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    item.qrUrl.take(60) + if (item.qrUrl.length > 60) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
