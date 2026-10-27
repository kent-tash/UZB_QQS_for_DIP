package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
                    text = "Для предоставления в бухгалтерию и налоговые органы приложение позволяет сформировать полный комплект или отдельные документы:\n\n" +
                        "1. Полный пакет («Всё») — рекомендуемый вариант. Объединяет в один PDF-документ официальный сводный отчёт (Часть 1) и реестр чеков по 6 штук на страницу А4 с независимой нумерацией страниц (Часть 2).\n\n" +
                        "2. Сводный отчёт («Отчёт») — итоговая ведомость с расчётом общей суммы расходов, НДС, ставками и блоком подписи ответственного лица.\n\n" +
                        "3. Реестр чеков («Чеки») — визуальные карточки чеков с оригинальными QR-кодами, фискальными признаками и фото оригиналов для чеков ручного ввода (по 6 карточек на лист А4).\n\n" +
                        "Документы доступны для мгновенного предпросмотра, печати, отправки через мессенджеры и сохранения в PDF или Excel (XLSX).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            AccordionItem(title = "Добавление чеков («Добавить»)") {
                Text(
                    text = "Способы внесения чеков в систему:\n\n" +
                        "• Одиночное сканирование камерой — моментальное распознавание QR-кода на чеке с автоматической загрузкой фискальных данных через ОФД.\n" +
                        "• Поточное сканирование — режим непрерывного сканирования пачки чеков подряд без закрытия камеры.\n" +
                        "• Сканирование из галереи — распознавание чеков по сохранённым фотографиям или скриншотам.\n" +
                        "• Скан листа с несколькими QR — одновременный поиск и распознавание нескольких QR-кодов на одном листе или общем фото.\n" +
                        "• Ручной ввод — если QR-код повреждён или чек старого образца: укажите сумму, дату и время покупки, ставку и сумму НДС, а также прикрепите фото чека.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            AccordionItem(title = "Чеки и отчёт (Единый экран)") {
                Text(
                    text = "Единый рабочий центр для анализа, фильтрации, проверки и экспорта документов:\n\n" +
                        "1. Сводная шапка:\n" +
                        "• Наглядное отображение общего количества чеков за выбранный период, суммарной стоимости покупок и начисленного НДС.\n\n" +
                        "2. Режим экспорта (кнопки «Всё», «Отчёт», «Чеки»):\n" +
                        "• «Всё» — формирует объединённый двухчастный документ (сводная таблица + реестр чеков по 6 на лист А4 с отдельной нумерацией).\n" +
                        "• «Отчёт» — формирует только сводный отчёт за период с подписью.\n" +
                        "• «Чеки» — формирует только графический реестр чеков (6 карточек на лист А4).\n\n" +
                        "3. Быстрые действия экспорта:\n" +
                        "• «Открыть» — мгновенный просмотр документа во встроенном просмотрщике.\n" +
                        "• «Печать» — прямая отправка на физический или виртуальный принтер Android.\n" +
                        "• «Сохранить» — экспорт файла в память устройства (PDF или таблица Excel XLSX).\n" +
                        "• «Поделиться» — отправка готового файла через мессенджеры, почту или облачные диски.\n\n" +
                        "4. Компактные фильтры:\n" +
                        "• Быстрый выбор отчётного года, квартала (Q1–Q4), статуса синхронизации ОФД (Все, Синхронизирован, Ошибка, Не проверен) и сотрудника (для аудиторов).\n\n" +
                        "5. Сортировка и групповые операции:\n" +
                        "• Упорядочивание списка чеков по дате, сумме или названию торговой точки.\n" +
                        "• Режим выбора нескольких чеков для группового удаления или массовой проверки.\n\n" +
                        "6. Проверка чеков и детальный просмотр:\n" +
                        "• Нажмите на любую карточку чека для открытия интерактивного диалога с фискальными реквизитами, товарными позициями и оригинальным QR-кодом.\n" +
                        "• В диалоге доступна кнопка отправки фото/изображения отдельного чека.\n" +
                        "• Смахивание влево удаляет ошибочно добавленный чек из базы.\n\n" +
                        "7. Онлайн-синхронизация ОФД:\n" +
                        "• Автоматическая проверка статусов чеков на серверах налоговой службы с отображением прогресса сверки.",
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
