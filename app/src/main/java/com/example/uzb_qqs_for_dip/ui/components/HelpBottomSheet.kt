package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SaveAlt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "В приложении доступно 5 удобных способов добавления фискальных чеков:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HelpButtonFeatureItem(
                        icon = Icons.Outlined.QrCodeScanner,
                        title = "«Сканировать QR-код»",
                        description = "Одиночное сканирование камерой устройства в реальном времени с автоматической загрузкой фискальных данных из ОФД.",
                        iconTint = MaterialTheme.colorScheme.primary
                    )

                    HelpButtonFeatureItem(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "«Загрузить фото чека»",
                        description = "Выбор сохранённого фото чека или скриншота из галереи устройства с локальным распознаванием через Google ML Kit / ZXing.",
                        iconTint = MaterialTheme.colorScheme.secondary
                    )

                    HelpButtonFeatureItem(
                        icon = Icons.Outlined.Link,
                        title = "«Добавить по ссылке»",
                        description = "Вставка ссылки на электронный чек (из SMS, Telegram или порталов soliq.uz / multicard.uz) для прямой загрузки данных.",
                        iconTint = MaterialTheme.colorScheme.secondary
                    )

                    HelpButtonFeatureItem(
                        icon = Icons.Outlined.GridView,
                        title = "«Скан всех чеков»",
                        description = "Режим непрерывного поточного сканирования серии чеков подряд без закрытия видоискателя, либо распознавание множества QR-кодов с одного листа или фото.",
                        iconTint = MaterialTheme.colorScheme.primary
                    )

                    HelpButtonFeatureItem(
                        icon = Icons.Outlined.SaveAlt,
                        title = "«Добавить вручную»",
                        description = "Ручной ввод реквизитов и прикрепление фото чека, когда QR-код повреждён или не читается.",
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            HorizontalDivider()

            AccordionItem(title = "Чеки и отчёт (Единый экран)") {
                Text(
                    text = "Единый рабочий центр для анализа, фильтрации, проверки и экспорта документов:\n\n" +
                        "1. Сводная шапка и выбор периода:\n" +
                        "• Выбор сотрудника и отчётного периода встроен прямо в интерактивные строки шапки «Сводный отчёт».\n" +
                        "• Клик по строке «Период» открывает шторку с селектором года (< 2026 год >), моментальным выбором квартала в сетке 2×2 (I–IV кв.) и кнопкой для перехода к точным датам в календаре.\n" +
                        "• Наглядное отображение количества чеков, общей суммы расходов и начисленного НДС (12%).\n\n" +
                        "2. Режим экспорта (кнопки «Всё», «Отчёт», «Чеки»):\n" +
                        "• «Всё» — объединённый двухчастный документ (сводная ведомость + реестр чеков по 6 на страницу А4 с отдельной нумерацией).\n" +
                        "• «Отчёт» — только сводный официальный отчёт за период с подписью.\n" +
                        "• «Чеки» — только графический реестр чеков с QR-кодами.\n\n" +
                        "3. Быстрые действия экспорта:\n" +
                        "• «Открыть» — мгновенный просмотр PDF во встроенном просмотрщике.\n" +
                        "• «Печать» — прямая отправка на физический или виртуальный принтер Android.\n" +
                        "• «Сохранить» — экспорт файла в память устройства (PDF или Excel XLSX).\n" +
                        "• «Поделиться» — отправка документа в мессенджеры, почту или облако.\n\n" +
                        "4. Сортировка и групповые операции:\n" +
                        "• Компактная кнопка сортировки шириной 170dp с встроенным быстрым переключением направления (↑ / ↓) и выбором поля по клику на шеврон.\n" +
                        "• Справа расположена плашка «Все (N)» с чекбоксом [✓] для быстрого выбора всех чеков в одно касание.\n\n" +
                        "5. Карточки чеков в списке:\n" +
                        "• Номер каждого чека отображается в компактном бейдже 26×26dp (без префикса «№»).\n" +
                        "• Центральный блок расширен на 50dp для лучшей читаемости длинных названий продавцов и сумм.\n" +
                        "• Чекбокс выбора расположен справа с комфортной зоной касания 48dp.\n" +
                        "• Красная корзина удалена из карточек для предотвращения случайных нажатий.\n\n" +
                        "6. Просмотр и безопасное удаление:\n" +
                        "• Клик по информации о чеке открывает полноразмерное модальное превью с 3 кнопками в один ряд равного веса («Поделиться», красная «Удалить», «Закрыть»).\n" +
                        "• Удаление единичного чека производится через кнопку «Удалить» в превью с подтверждением, а массовое удаление — через всплывающую нижнюю панель выбранных чеков.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            AccordionItem(title = "Управление профилями") {
                Text(
                    text = "Настройка профилей и управление данными во вкладке «Профиль»:\n\n" +
                        "1. Минималистичный экран:\n" +
                        "• Интерфейс очищен от перегруженных нижних кнопок. Все ключевые действия доступны прямо из карточки профиля.\n\n" +
                        "2. Шторка «Управление профилями»:\n" +
                        "• Клик по шапке профиля (аватару, ФИО или иконке смены) открывает удобную шторку со списком всех сотрудников, отметкой активного профиля и возможностью переключения в один клик.\n" +
                        "• Внизу шторки расположена кнопка «+ Добавить новый профиль» для быстрого создания новой учётной записи.\n\n" +
                        "3. Инлайн-редактирование реквизитов:\n" +
                        "• Кликните по строке «Имя», «Должность» или «Организация» с иконкой карандаша ✏ для быстрого редактирования конкретного поля без заполнения всей формы заново.\n\n" +
                        "4. Двухэтапное подтверждение удаления (Safety First):\n" +
                        "• Удаление любого профиля защищено двойным подтверждением: первый диалог предупреждает об удалении связанных чеков, второй (с яркой индикацией предупреждения) требует окончательного осознанного подтверждения.\n\n" +
                        "5. Режимы работы и резервное копирование:\n" +
                        "• Переключение между ролями «Сотрудник» и «Аудитор» для доступа к сверке.\n" +
                        "• Создание резервной копии базы данных (JSON), безопасное слияние или замена при импорте, а также экспорт бэкапа через системное меню «Поделиться».",
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

@Composable
private fun HelpButtonFeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 38.dp)
        )
    }
}
