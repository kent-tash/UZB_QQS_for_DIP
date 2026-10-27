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
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material.icons.outlined.Widgets
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpFeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
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
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 38.dp)
        )
    }
}

@Composable
fun HelpContent(
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. Виджет быстрого сканирования (НОВЫЙ)
        AccordionItem(
            title = "Виджет быстрого сканирования",
            icon = Icons.Outlined.Widgets,
            iconTint = MaterialTheme.colorScheme.primary
        ) {
            HelpFeatureItem(
                icon = Icons.Outlined.TouchApp,
                title = "Добавление на экран",
                description = "Зажмите палец на свободном месте рабочего стола Android -> выберите «Виджеты» -> найдите «Скан чеков Узбекистана» и перетащите виджет на экран."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.QrCodeScanner,
                title = "Скан в 1 касание",
                description = "Нажатие на кнопку виджета моментально запускает камеру со сканером чеков без лишних экранов и задержек."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.AccountCircle,
                title = "Автоматическая привязка",
                description = "Отсканированный через виджет чек сразу сохраняется в базу данных и привязывается к текущему активному профилю сотрудника."
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 2. Автоматическое открытие ссылок на чеки (НОВЫЙ)
        AccordionItem(
            title = "Автоматическое открытие ссылок на чеки",
            icon = Icons.Outlined.Link,
            iconTint = MaterialTheme.colorScheme.secondary
        ) {
            HelpFeatureItem(
                icon = Icons.Outlined.Domain,
                title = "Поддерживаемые сервисы",
                description = "Приложение распознает официальные фискальные ссылки ОФД Республики Узбекистан: ofd.soliq.uz, my.soliq.uz и ofd.multicard.uz."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Settings,
                title = "Настройка в Android",
                description = "Откройте Настройки телефона -> Приложения -> Скан чеков Узбекистана -> Открывать по умолчанию -> Добавить ссылки и отметьте указанные домены."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Share,
                title = "Системное меню «Поделиться»",
                description = "Вы можете отправить текст или ссылку из SMS, Telegram или браузера в приложение через стандартную кнопку «Поделиться» (Share)."
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 3. Навигация и Главное меню
        AccordionItem(
            title = "Навигация и Главное меню",
            icon = Icons.Outlined.Menu,
            iconTint = MaterialTheme.colorScheme.primary
        ) {
            HelpFeatureItem(
                icon = Icons.Outlined.ViewStream,
                title = "Нижняя панель (2 вкладки)",
                description = "«Добавить» и «Чеки и отчёт» в режиме сотрудника; «Аудит» и «Чеки и отчёт» в режиме аудитора."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.AccountCircle,
                title = "Аватар в TopAppBar",
                description = "На всех главных экранах вверху справа расположен аватар текущего профиля для быстрого вызова главного меню."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.AdminPanelSettings,
                title = "Режимы «Сотрудник» / «Аудитор»",
                description = "Переключение рабочей роли с контрастной цветовой индикацией (красный Аудитор / синий Сотрудник)."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Palette,
                title = "Темы оформления",
                description = "Встроенный переключатель [Авто] [Светлая] [Тёмная] с сохранением настроек без перезапуска приложения."
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 4. Управление профилями
        AccordionItem(
            title = "Управление профилями",
            icon = Icons.Outlined.AccountCircle,
            iconTint = MaterialTheme.colorScheme.tertiary
        ) {
            HelpFeatureItem(
                icon = Icons.Outlined.Sync,
                title = "Сквозная сессия",
                description = "Все сохраняемые чеки автоматически закрепляются за выбранным активным сотрудником."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.CheckCircle,
                title = "Быстрый выбор сотрудника",
                description = "Тап по карточке профиля в меню или по шапке отчёта открывает список для моментального переключения."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Edit,
                title = "Редактирование профилей",
                description = "Иконка карандаша позволяет скорректировать ФИО, должность и название организации."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Delete,
                title = "Безопасное удаление",
                description = "Двухэтапное подтверждение защищает от случайного удаления профиля и его чеков."
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 5. Способы добавления чеков
        AccordionItem(
            title = "5 способов добавления чеков",
            icon = Icons.Outlined.QrCodeScanner,
            iconTint = MaterialTheme.colorScheme.primary
        ) {
            HelpFeatureItem(
                icon = Icons.Outlined.QrCodeScanner,
                title = "Сканировать QR-код",
                description = "Потоковый сканер камеры с поддержкой вспышки и моментальным фискальным запросом в ОФД."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Загрузить фото чека",
                description = "Распознавание QR-кода с уже сделанной фотографии или электронного скриншота из галереи."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Link,
                title = "Добавить по ссылке",
                description = "Ручная вставка URL-адреса чека из буфера обмена."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.GridView,
                title = "Скан всех чеков",
                description = "Пакетное сканирование папки с фотографиями чеков на вашем смартфоне."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.SaveAlt,
                title = "Добавить вручную",
                description = "Ввод фискального признака (ФП/ФД), даты и суммы, если QR-код чека повреждён."
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 6. Чеки и отчёт
        AccordionItem(
            title = "Чеки и отчёт (Рабочий центр)",
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            iconTint = MaterialTheme.colorScheme.secondary
        ) {
            HelpFeatureItem(
                icon = Icons.Outlined.DateRange,
                title = "Сводная шапка и выбор периода",
                description = "Фильтрация по кварталу и году с автоматическим расчётом общей суммы и НДС к возврату."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Форматы экспорта",
                description = "Формирование пакета документов («Всё»), Сводного отчёта или Реестра чеков в форматах PDF и Excel."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Bolt,
                title = "Быстрые действия",
                description = "Моментальная отправка готовых документов в Telegram, на почту или прямая печать на принтер."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.VerticalAlignTop,
                title = "Закреплённая панель (Sticky Header)",
                description = "Строка поиска по торговым точкам, фильтрация по статусам и сортировка не пропадают при скролле."
            )
            HelpFeatureItem(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = "Детали чеков и превью",
                description = "Просмотр товарных позиций, ставок НДС, статуса синхронизации и сохранённого фото оригинала."
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 7. Резервная копия базы данных
        AccordionItem(
            title = "Резервная копия базы данных",
            icon = Icons.Outlined.FolderOpen,
            iconTint = MaterialTheme.colorScheme.primary
        ) {
            HelpFeatureItem(
                icon = Icons.Outlined.Code,
                title = "Единый файл JSON",
                description = "Бэкап содержит полную базу: профили, чеки, товарные позиции и настройки приложения."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.SaveAlt,
                title = "Создать бэкап",
                description = "Сохранение файла резервной копии в любую выбранную папку на телефоне."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.FolderOpen,
                title = "Восстановление данных",
                description = "Выбор режима: «Добавить к текущим» (слияние без потерь) или «Полностью заменить базу»."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Share,
                title = "Поделиться файлом",
                description = "Быстрая пересылка файла бэкапа в рабочий чат или на запасное устройство."
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 8. Аудит (Режим аудитора)
        AccordionItem(
            title = "Аудит (Режим аудитора)",
            icon = Icons.AutoMirrored.Outlined.FactCheck,
            iconTint = Color(0xFFD32F2F)
        ) {
            HelpFeatureItem(
                icon = Icons.AutoMirrored.Outlined.FactCheck,
                title = "Квартальная сверка",
                description = "Консолидированная проверка чеков всех сотрудников организации за отчётный период.",
                iconTint = Color(0xFFD32F2F)
            )
            HelpFeatureItem(
                icon = Icons.AutoMirrored.Outlined.CompareArrows,
                title = "Контроль расхождений",
                description = "Автоматический поиск дубликатов чеков, чеков с нулевым НДС и подозрительных операций.",
                iconTint = Color(0xFFD32F2F)
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Verified,
                title = "Почековая верификация",
                description = "Аудиторская отметка о проверке подлинности фискального документа.",
                iconTint = Color(0xFFD32F2F)
            )
            HelpFeatureItem(
                icon = Icons.Outlined.FolderZip,
                title = "Экспорт отчётов и ZIP",
                description = "Выгрузка сводных реестров и архива фотографий оригиналов чеков для налоговой проверки.",
                iconTint = Color(0xFFD32F2F)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 9. Что нужно печатать в бухгалтерию
        AccordionItem(
            title = "Что нужно печатать в бухгалтерию",
            icon = Icons.Outlined.Description,
            iconTint = MaterialTheme.colorScheme.tertiary
        ) {
            HelpFeatureItem(
                icon = Icons.Outlined.Description,
                title = "Полный пакет («Всё»)",
                description = "Рекомендуемый вариант: сводная справка-расчёт и постраничный реестр всех чеков."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.Summarize,
                title = "Сводный отчёт",
                description = "Итоговый документ с реквизитами организации, периодом и подписями ответственных лиц."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.FormatListNumbered,
                title = "Реестр чеков",
                description = "Детальная таблица со списком номеров чеков, ФП, торговых точек, сумм и НДС."
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 10. Контакты разработчика
        AccordionItem(
            title = "Контакты разработчика",
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            iconTint = MaterialTheme.colorScheme.primary
        ) {
            HelpFeatureItem(
                icon = Icons.AutoMirrored.Outlined.Send,
                title = "Telegram",
                description = "@dis_night — оперативная поддержка и предложения."
            )
            HelpFeatureItem(
                icon = Icons.Outlined.AlternateEmail,
                title = "Email",
                description = "i@dis-night.ru — официальная связь по вопросам приложения."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBottomSheet(
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp, top = 8.dp)
        ) {
            Text(
                text = "Справка и руководство",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            HelpContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun AccordionItem(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "accordion")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                content()
            }
        }
    }
}
