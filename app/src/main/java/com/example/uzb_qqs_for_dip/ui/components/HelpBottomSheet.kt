package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBottomSheet(
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp, top = 8.dp)
        ) {
            Text(
                text = "Справка и контакты",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            AccordionItem(title = "Контакты разработчика") {
                val uriHandler = LocalUriHandler.current
                Column {
                    Text(
                        text = "Написать в Telegram: t.me/dis_night",
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable { uriHandler.openUri("https://t.me/dis_night") }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = "Email: i@dis-night.ru",
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable { uriHandler.openUri("mailto:i@dis-night.ru") }
                            .padding(vertical = 8.dp)
                    )
                }
            }
            HorizontalDivider()
            
            AccordionItem(title = "Как сканировать чеки (Сканер)") {
                Text(
                    text = "1. На вкладке «Сканер» нажмите на большую круглую кнопку с иконкой QR-кода.\n" +
                           "2. Разрешите доступ к камере, если потребуется.\n" +
                           "3. Наведите камеру на QR-код чека. Приложение автоматически распознает его и покажет детали.\n" +
                           "4. Если у вас есть фото чека в галерее, используйте кнопку «Из галереи/памяти».\n" +
                           "5. Для непрерывного сканирования используйте режим «Скан всех чеков».",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()
            
            AccordionItem(title = "Как добавить чек вручную") {
                Text(
                    text = "1. На вкладке «Сканер» нажмите «Вставить ссылку или добавить вручную».\n" +
                           "2. Введите название магазина, дату, итоговую сумму и сумму НДС (QQS).\n" +
                           "3. Прикрепите фото чека (через камеру или галерею).\n" +
                           "4. Нажмите «Сохранить». В отчётах ручные чеки будут выводиться в самом конце списка.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            AccordionItem(title = "Генерация отчёта (PDF/Excel)") {
                Text(
                    text = "1. Перейдите на вкладку «Отчёт».\n" +
                           "2. Разверните панель «Фильтры» и выберите пользователя, год, квартал или произвольный период.\n" +
                           "3. Нажмите иконку PDF (для сохранения/печати реестра) или Excel (для выгрузки в формате xlsx).\n" +
                           "4. Сгенерированный файл можно сразу открыть или отправить (Поделиться).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()
            
            AccordionItem(title = "Управление профилями (Аудит)") {
                Text(
                    text = "1. Перейдите на вкладку «Профиль».\n" +
                           "2. Нажмите «Переключить» для добавления нового пользователя или выбора существующего.\n" +
                           "3. Администратор (или аудитор) может просматривать чеки любых пользователей, если переключится на их профиль.\n" +
                           "4. Здесь же можно создавать и восстанавливать резервные копии базы данных (.db).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun AccordionItem(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Развернуть",
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                content()
            }
        }
    }
}
