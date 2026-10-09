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
                        text = "Связь в Telegram: t.me/dis_night",
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
            
            AccordionItem(title = "Что нужно печатать") {
                Text(
                    text = "Для предоставления в бухгалтерию и налоговые органы необходимо распечатать следующие документы:\n\n1. Реестр чеков (содержит сводный перечень всех отсканированных и внесенных вручную чеков).\n2. Отчет (сводные данные и аналитика за выбранный период).\n\nОба документа генерируются в соответствующих вкладках и могут быть экспортированы в формате PDF для последующей печати.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()
            
            AccordionItem(title = "Чеки") {
                Text(
                    text = "Раздел предназначен для добавления, просмотра и управления чеками:\n\n" +
                        "1. Способы добавления:\n" +
                        "• Одиночное сканирование камерой — быстрое распознавание QR-кода на чеке в реальном времени с автоматической загрузкой фискальных данных.\n" +
                        "• Поточное сканирование — режим непрерывного сканирования серии чеков подряд без закрытия камеры.\n" +
                        "• Сканирование из галереи — выбор сохранённого фото или скриншота чека из галереи устройства.\n" +
                        "• Скан листа с несколькими QR — одновременный поиск и распознавание нескольких QR-кодов на одном листе или общем фото.\n" +
                        "• Ручной ввод — если QR-код повреждён или отсутствует: укажите сумму, дату и время покупки, ставку и сумму НДС, а также прикрепите фото чека.\n\n" +
                        "2. Просмотр и проверка:\n" +
                        "• Нажмите на чек в списке для открытия карточки с подробными позициями товаров, фискальными реквизитами и оригинальным QR-кодом для сверки.\n\n" +
                        "3. Удаление чеков:\n" +
                        "• Смахните чек в списке или используйте кнопку удаления в карточке чека, если документ был добавлен ошибочно.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()
            
            AccordionItem(title = "Генерация реестра чеков") {
                Text(
                    text = "Формирование официального реестра чеков во вкладке «Чеки»:\n\n" +
                        "1. Структура печатного документа:\n" +
                        "• Печатный макет автоматически компонует ровно по 6 чеков на каждой странице формата А4.\n" +
                        "• В верхней части каждого листа размещена сквозная нумерация страниц («Страница X из Y»).\n" +
                        "• В нижнем колонтитуле проставляется динамическая дата и точное время генерации документа.\n" +
                        "• Для чеков с ручным вводом автоматически формируются страницы приложений с прикреплёнными фотографиями оригиналов.\n\n" +
                        "2. Экспорт и операции:\n" +
                        "• «Открыть» — мгновенный просмотр сформированного реестра во встроенном просмотрщике.\n" +
                        "• «Печать» — прямая отправка документа на печать через системный сервис Android.\n" +
                        "• «Сохранить» — экспорт файла в память устройства в формате PDF или таблицы XLSX.\n" +
                        "• «Поделиться» — отправка реестра коллегам через мессенджеры или почту.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()
            
            AccordionItem(title = "Генерация отчета") {
                Text(
                    text = "Формирование аналитической и налоговой отчётности во вкладке «Отчёт»:\n\n" +
                        "1. Настройка периода и параметров:\n" +
                        "• Выберите отчётный период: конкретный «Год» и «Квартал» (I–IV) либо произвольный диапазон дат («Период с / по»).\n" +
                        "• Возможность выбора конкретного сотрудника или формирования отчёта по всей организации.\n\n" +
                        "2. Автообновление данных:\n" +
                        "• Функция онлайн-синхронизации опрашивает серверы налоговой службы для проверки актуального статуса чеков.\n" +
                        "• Процесс сопровождается наглядным счётчиком прогресса загрузки и индикацией результата.\n\n" +
                        "3. Выгрузка и печать:\n" +
                        "• Экспорт итоговой таблицы в форматы PDF (официальный отчёт) и Excel / XLSX (для бухгалтерской обработки).\n" +
                        "• Доступны кнопки быстрого локального сохранения и прямой отправки на печать.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()
            
            AccordionItem(title = "Управление профилями") {
                Text(
                    text = "Настройка профиля и управление данными во вкладке «Профиль»:\n\n" +
                        "1. Режимы работы:\n" +
                        "• Переключение между ролями «Сотрудник» (личный учёт и сканирование чеков) и «Аудитор» (контроль, квартальная сверка и проверка чеков коллег).\n\n" +
                        "2. Реквизиты пользователя:\n" +
                        "• Редактирование ФИО, должности и названия организации для автоматического включения в формируемые документы.\n\n" +
                        "3. Резервное копирование и базы данных:\n" +
                        "• Создание резервной копии всей базы чеков в формате JSON для надёжного хранения.\n" +
                        "• Загрузка бэкапа с поддержкой безопасного слияния баз без утери существующих записей.\n\n" +
                        "4. Безопасность:\n" +
                        "• Выход из профиля и полное удаление локальной базы данных при необходимости.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()
            
            AccordionItem(title = "Аудит") {
                Text(
                    text = "Инструменты внутреннего и внешнего аудита для проверки фискальных данных:\n\n" +
                        "1. Квартальная сверка и контроль:\n" +
                        "• Сверка данных по сотрудникам за выбранный отчётный квартал.\n" +
                        "• Наглядное отслеживание проверенных сотрудников и статуса закрытия периода.\n\n" +
                        "2. Выявление расхождений и конфликтов:\n" +
                        "• Автоматическое обнаружение расхождений между суммами чеков и показателями налоговых деклараций.\n" +
                        "• Мониторинг конфликтов дубликатов (одинаковые чеки у разных сотрудников или повторно внесённые).\n\n" +
                        "3. Окно «Проверка чеков»:\n" +
                        "• Почековая верификация документов сотрудника с подтверждением через сканер QR-кода или в ручном режиме с установкой статусов проверки.\n\n" +
                        "4. Экспорт аналитики:\n" +
                        "• Выгрузка «Сводной таблицы» аудита в PDF и Excel.\n" +
                        "• Генерация специализированного отчёта «Возврат НДС» для налогового возмещения.\n" +
                        "• Экспорт полного ZIP-архива чеков с оригинальными данными и фотографиями.",
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
                contentDescription = "Раскрыть",
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
