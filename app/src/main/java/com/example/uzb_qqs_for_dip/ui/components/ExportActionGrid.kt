package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

@Composable
fun ExportActionGrid(
    onOpen: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    isSaving: Boolean,
    saveProgress: Float,
    isSaveEnabled: Boolean = true,
    isOpenPrintEnabled: Boolean = true,
    saveProgressLabel: String = "Сохранение %"
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = isSaveEnabled
        ) {
            Icon(Icons.Outlined.SaveAlt, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Сохранить")
        }
        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = isSaveEnabled,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Поделиться")
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = isOpenPrintEnabled,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Outlined.Visibility, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Открыть")
        }
        Button(
            onClick = onPrint,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = isOpenPrintEnabled,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Outlined.Print, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Печать")
        }
    }

    if (isSaving) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(text = saveProgressLabel)
                }
            }
        }
    }
}
