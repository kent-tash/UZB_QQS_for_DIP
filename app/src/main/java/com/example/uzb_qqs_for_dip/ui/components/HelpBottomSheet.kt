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

            // 1. Навигация и Главное меню
            AccordionItem(title = "Навигация и Главное меню") {
                Text(
                    text = "Архитектура навигации оптимизирована для быстрого доступа ко всем ключевым сценариям:\n\n" +
                        "1. Нижняя панель (строго 2 вкладки):\n" +
                        "• В режиме «Сотрудник»: «Добавить» (сканирование и ввод) и «Чеки и отчёт» (учёт, аналитика и экспорт документов).\n" +
                        "• В режиме «Аудитор»: «Аудит» (квартальная сверка, контроль расхождений) и «Чеки и отчёт».\n\n" +
                        "2. Аватар в правом верхнем углу (TopAppBar):\n" +
                        "• На всех основных экранах вверху справа размещён круглый аватар активного пользователя с первой буквой имени.\n" +
                        "• Тап по аватару открывает Главное меню приложения из любой точки без лишних переходов.\n\n" +
                        "3. Состав Главного меню:\n" +
                        "• Карточка текущего профиля: ФИО, должность и организация с переходом к полному управлению пользователями.\n" +
                        "• «Режим работы»: моментальное переключение между ролями «Сотрудник» и «Аудитор».\n" +
                        "• «Резервная копия»: создание бэкапа базы данных, восстановление и отправка файла.\n" +
                        "• «Справка и руководство»: встроенные инструкции по всем функциям.\n" +
                        "• «О приложении»: версия, политика конфиденциальности и прямые контакты поддержки.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            // 2. Управление профилями
            AccordionItem(title = "Управление профилями") {
                Text(
                    text = "Единая система управления пользователями со сквозной синхронизацией сессии:\n\n" +
                        "1. Три удобные точки входа:\n" +
                        "• Главное меню (клик по аватару в верхнем баре -> карточка профиля).\n" +
                        "• Приветствие «Здравствуйте, [ФИО] ▾» вверху экрана «Добавить».\n" +
                        "• Строка сотрудника «Сотрудник: [ФИО] ▾» в сводной шапке экрана «Чеки и отчёт».\n\n" +
                        "2. Мгновенное переключение пользователей:\n" +
                        "• Тап по любой карточке сотрудника в списке делает его активным и мгновенно переключает рабочую сессию на всех экранах приложения (добавляемые чеки привязываются к нему, а отчёт сразу фильтрует его данные).\n" +
                        "• Активный профиль наглядно подсвечен и отмечен бейджем «Активен».\n\n" +
                        "3. Инлайн-редактирование (кнопка ✏️):\n" +
                        "• Кнопка с карандашом в строке любого пользователя позволяет изменить ФИО, должность и организацию в удобном окне.\n\n" +
                        "4. Безопасное двухэтапное удаление (кнопка 🗑️):\n" +
                        "• Удаление профиля надёжно защищено двойным подтверждением: сначала отображается предупреждение об удалении связанных чеков, затем окно с подтверждением «Удалить окончательно».\n\n" +
                        "5. Создание нового профиля:\n" +
                        "• Кнопка «+ Добавить новый профиль» внизу шторки позволяет зарегистрировать нового сотрудника за пару секунд.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            // 3. Добавление чеков
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
                        description = "Одиночное сканирование камерой устройства в реальном времени с автоматической загрузкой фискальных данных из ОФД Республики Узбекистан.",
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

            // 4. Чеки и отчёт
            AccordionItem(title = "Чеки и отчёт (Единый рабочий центр)") {
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
                        "4. Закреплённая панель (Sticky Header):\n" +
                        "• Панель сортировки и пакетных действий закрепляется вверху экрана при прокрутке списка чеков, позволяя менять порядок сортировки и удалять выбранные чеки в любой момент без возврата наверх.\n" +
                        "• Компактная кнопка сортировки шириной 170dp с встроенным быстрым переключением направления (↑ / ↓) и выбором поля по клику на шеврон.\n" +
                        "• Справа расположена плашка «Все (N)» с чекбоксом [✓] для быстрого выбора всех чеков в одно касание.\n\n" +
                        "5. Карточки чеков и модальное превью:\n" +
                        "• Номер каждого чека отображается в компактном бейдже 26×26dp.\n" +
                        "• Центральный блок оптимизирован для длинных названий продавцов и сумм.\n" +
                        "• Чекбокс выбора справа с комфортной зоной касания 48dp.\n" +
                        "• Клик по чеку открывает полноразмерное модальное превью с 3 кнопками («Поделиться», красная «Удалить», «Закрыть»).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            // 5. Резервная копия
            AccordionItem(title = "Резервная копия базы данных") {
                Text(
                    text = "Полный контроль над сохранностью информации без привязки к внешним серверам:\n\n" +
                        "1. Единый файл JSON:\n" +
                        "• Резервная копия содержит полную информацию: профили сотрудников, все сохранённые фискальные чеки, фотографии оригиналов ручного ввода и настройки отчётов.\n\n" +
                        "2. Создание копии («Создать бэкап»):\n" +
                        "• Сохранение защищённого JSON-файла в память устройства или на карту памяти через системный файловый менеджер.\n\n" +
                        "3. Восстановление («Загрузить бэкап»):\n" +
                        "• «Добавить к текущим» — умное слияние данных. Новые чеки и профили добавляются в базу без дублирования уже существующих записей.\n" +
                        "• «Полностью заменить» — перезапись базы данных данными из файла (действие защищено окном предупреждения от случайного стирания).\n\n" +
                        "4. Быстрая отправка («Поделиться данными»):\n" +
                        "• Отправка файла базы данных коллегам или на свой компьютер через Telegram, WhatsApp, Email или Google Диск.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            // 6. Аудит
            AccordionItem(title = "Аудит (Режим аудитора)") {
                Text(
                    text = "Инструменты контроля и финансового аудита для проверки фискальных данных:\n\n" +
                        "1. Квартальная сверка:\n" +
                        "• Сверка данных по всем сотрудникам организации за выбранный отчётный квартал.\n" +
                        "• Наглядный статус закрытия периода и прогресс проверенных сотрудников.\n\n" +
                        "2. Контроль расхождений и конфликтов:\n" +
                        "• Автоматическое выявление расхождений между суммами чеков и показателями налоговых деклараций.\n" +
                        "• Мониторинг конфликтов дубликатов (одинаковые чеки, прикреплённые разными сотрудниками или повторно внесённые).\n\n" +
                        "3. Почековая верификация:\n" +
                        "• Окно «Проверка чеков» для детальной верификации документов сотрудника с подтверждением через сканер QR-кода или вручную.\n\n" +
                        "4. Экспорт аналитических отчётов:\n" +
                        "• Выгрузка «Сводной таблицы» аудита в PDF и Excel.\n" +
                        "• Генерация специализированного отчёта «Возврат НДС» для налогового возмещения.\n" +
                        "• Экспорт полного ZIP-архива чеков с оригинальными данными и фотографиями.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            // 7. Что нужно печатать
            AccordionItem(title = "Что нужно печатать в бухгалтерию") {
                Text(
                    text = "Для предоставления в бухгалтерию и налоговые органы приложение позволяет сформировать полный комплект или отдельные документы:\n\n" +
                        "1. Полный пакет («Всё») — рекомендуемый официальный вариант. Объединяет в один PDF-документ официальный сводный отчёт (Часть 1) и реестр чеков по 6 штук на страницу А4 с независимой нумерацией страниц (Часть 2).\n\n" +
                        "2. Сводный отчёт («Отчёт») — итоговая ведомость с расчётом общей суммы расходов, НДС, ставками и блоком подписи ответственного лица.\n\n" +
                        "3. Реестр чеков («Чеки») — визуальные карточки чеков с оригинальными QR-кодами, фискальными признаками и фото оригиналов для чеков ручного ввода (по 6 карточек на лист А4).\n\n" +
                        "Все документы поддерживают мгновенный предпросмотр, прямую печать, отправку в мессенджеры и сохранение в PDF или Excel (XLSX).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            HorizontalDivider()

            // 8. Контакты
            AccordionItem(title = "Контакты разработчика") {
                val uriHandler = LocalUriHandler.current
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Связь в Telegram: t.me/dis_night",
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
                }
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
                contentDescription = if (expanded) "Свернуть" else "Раскрыть",
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
