package com.example.uzb_qqs_for_dip.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun HelpDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Помощь и контакты") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Инструкция по использованию приложения...")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Связаться в Telegram: t.me/dis_night",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/dis_night"))
                        context.startActivity(intent)
                    }.padding(vertical = 4.dp)
                )
                Text(
                    text = "Email: i@dis-night.ru",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:i@dis-night.ru")
                        }
                        context.startActivity(intent)
                    }.padding(vertical = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("ОК")
            }
        }
    )
}
