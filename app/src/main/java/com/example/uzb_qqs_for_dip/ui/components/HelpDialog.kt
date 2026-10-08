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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun HelpDialog(
    onDismissRequest: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Помощь и контакты") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Связаться в Telegram: t.me/dis_night",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { uriHandler.openUri("https://t.me/dis_night") }
                        .padding(vertical = 4.dp)
                )
                Text(
                    text = "Email: i@dis-night.ru",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { uriHandler.openUri("mailto:i@dis-night.ru") }
                        .padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Добавить: ")
                        }
                        append("Инструкция по добавлению чеков (сканирование QR, фото, ручной ввод).\n\n")
                        
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Чеки: ")
                        }
                        append("Инструкция по просмотру, фильтрации и управлению добавленными чеками.\n\n")
                        
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Отчёт: ")
                        }
                        append("Инструкция по формированию и выгрузке отчетов.\n\n")
                        
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Аудит: ")
                        }
                        append("Инструкция по проверке пользователей и просмотру статистики аудитора.\n\n")
                        
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Профиль: ")
                        }
                        append("Инструкция по управлению аккаунтом, настройкам и бэкапам.")
                    }
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
