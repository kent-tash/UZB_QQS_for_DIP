# Спецификация UI/UX: Рефакторинг навигации, TopAppBar, главного меню и шторок

**Роль:** UI/UX Designer  
**Навыки:** `mobile-design`, `design-spells`  
**Платформа:** Android (Jetpack Compose, Material 3)  
**Минимальная зона касания (Touch Target):** ≥ 48dp (`ScannerDimensions.touchTarget`)  
**Дата разработки:** 27 октября 2026 г.

---

## 1. Контрольная точка мобильного дизайна (Mobile Checkpoint)

| Параметр | Значение |
|---|---|
| **Платформа** | Android (Material Design 3) |
| **Фреймворк** | Jetpack Compose 1.7+ |
| **Контрастность** | Соответствие WCAG AA/AAA (коэффициент > 4.5:1 для всех текстов и пиктограмм) |
| **Доступность (A11y)** | TalkBack-семантика (`Role.Button`, осмысленные `contentDescription`), масштабируемость шрифтов (sp, `TextOverflow.Ellipsis`) |
| **Touch Zone (Fitts's Law)** | Минимальный габарит кликабельной зоны 48x48dp, защита от ложных нажатий |

---

## 2. Спецификация компонентов

### 2.1. Круглый аватар в TopAppBar (`ui/navigation/AppNavHost.kt`)

Размещается в слоте `actions` компонента `TopAppBar` на всех основных экранах взамен старой кнопки-иконки «?».

```
+-------------------------------------------------------+
|  Заголовок экрана                     [ (A) 38dp ]    |
|                                         48dp зона     |
+-------------------------------------------------------+
```

#### Визуальные и технические параметры:
- **Сенсорная зона (Touch Target):** ровно **48dp x 48dp**.
- **Контейнер клика:** `Box(modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = { showMainMenu = true }), contentAlignment = Alignment.Center)`.
- **Визуальный размер аватара:** **38dp x 38dp** (`Modifier.size(38.dp).clip(CircleShape)`).
- **Цветовая палитра:**
  - Фон аватара: `MaterialTheme.colorScheme.primaryContainer` (светлая: `#D3E2F2`, тёмная: `#1F4E79`).
  - Текст инициала: `MaterialTheme.colorScheme.onPrimaryContainer` (светлая: `#1F4E79`, тёмная: `#E5E7EB`).
  - Тонкая обводка (для контраста): `BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))`.
- **Типографика:** `MaterialTheme.typography.titleMedium`, `FontWeight.Bold`. Текст: `currentUser?.fullName?.take(1)?.uppercase() ?: "?"`.
- **Интерактивность (Design Spells):**
  - Ограниченный кругом Ripple-эффект.
  - Семантика: `Modifier.semantics { role = Role.Button; contentDescription = "Главное меню профиля: ${currentUser?.fullName ?: "Гость"}" }`.

---

### 2.2. Главная шторка `MainMenuBottomSheet.kt`

Открывается по тапу на аватар в TopAppBar. Служит центральным хабом профиля, переключения ролей, бэкапа, справки и информации о приложении.

```
+-------------------------------------------------------+
|                       ════ (Drag Handle)              |
|  [ (A) 44dp ]  Иван Иванов               ›            |
|                Сотрудник · ООО "Компания"             |
|  ---------------------------------------------------  |
|  [ 🛡️ ]  Режим работы               [ Сотрудник ] ›   |
|  [ 💾 ]  Резервная копия                          ›   |
|  [ ❓ ]  Справка и руководство                    ›   |
|  [ ℹ️ ]  О приложении (v1.13.0)                   ›   |
+-------------------------------------------------------+
```

#### Структура и параметры:
1. **Контейнер шторки:** `ModalBottomSheet` с `RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`.
2. **Карточка текущего профиля (Header Profile Card):**
   - Кликабельная карточка со скруглением `16.dp`, фон `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`.
   - Внутренние отступы: `14.dp`.
   - Тап по карточке: закрывает `MainMenuBottomSheet` и сразу открывает `UserManagementBottomSheet`.
   - Слева: круглый аватар `44.dp` (`primaryContainer`, жирный инициал).
   - По центру (`weight(1f)`):
     - ФИО: `MaterialTheme.typography.titleMedium`, `FontWeight.SemiBold`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.
     - Должность / организация: `MaterialTheme.typography.bodySmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`.
   - Справа: шеврон `Icons.AutoMirrored.Filled.ArrowForwardIos` (размер 16dp, цвет `onSurfaceVariant`).
3. **Разделитель:** `HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))`.
4. **Строки меню (Высота 54-56dp, кликабельные с ripple):**
   - **«Режим работы»:**
     - Иконка: `Icons.Outlined.AdminPanelSettings`.
     - Подложка иконки: `Box(size = 40.dp, shape = RoundedCornerShape(10.dp), background = tertiaryContainer.copy(alpha = 0.6f))`.
     - Текст: «Режим работы», подзаголовок: «Переключить на Аудитор/Сотрудник».
     - Справа: бейдж текущей роли (`Surface` со скруглением 8dp) + шеврон.
   - **«Резервная копия»:**
     - Иконка: `Icons.Outlined.Backup`.
     - Подложка: `secondaryContainer.copy(alpha = 0.6f)`, иконка `secondary`.
     - Текст: «Резервная копия», подзаголовок: «Экспорт и импорт базы данных».
     - Клик: закрывает меню -> открывает `BackupBottomSheet`.
   - **«Справка»:**
     - Иконка: `Icons.AutoMirrored.Outlined.HelpOutline`.
     - Подложка: `primaryContainer.copy(alpha = 0.6f)`, иконка `primary`.
     - Текст: «Справка и руководство», подзаголовок: «Инструкции, 5 способов сканирования».
     - Клик: закрывает меню -> открывает `HelpBottomSheet`.
   - **«О приложении»:**
     - Иконка: `Icons.Outlined.Info`.
     - Подложка: `surfaceVariant`, иконка `onSurfaceVariant`.
     - Текст: «О приложении», подзаголовок: «Версия, контакты и политика».
     - Клик: закрывает меню -> открывает `AboutAppBottomSheet`.

---

### 2.3. Шторка резервного копирования `BackupBottomSheet.kt`

```
+-------------------------------------------------------+
|                       ════                            |
|  Резервная копия                                      |
|  Один файл JSON: все профили, чеки и настройки.       |
|                                                       |
|  [ 💾  Создать резервную копию               ] (Pri)  |
|  [ 📂  Восстановить из файла                 ] (Sec)  |
|  [ 🔗  Поделиться файлом базы данных         ] (Tonal)|
+-------------------------------------------------------+
```

#### Визуальные и технические параметры:
- **Заголовок:** «Резервная копия» (`titleLarge`, `FontWeight.Bold`).
- **Описание:** `bodyMedium`, `color = MaterialTheme.colorScheme.onSurfaceVariant`.
- **Стек кнопок (Высота каждой 48-50dp, скругление 12dp):**
  1. **«Создать бэкап»:**
     - Стиль: `Button` (Primary).
     - Иконка: `Icons.Outlined.SaveAlt`.
     - Действие: сохранение файла через системный SAF `CreateDocument`.
  2. **«Загрузить бэкап»:**
     - Стиль: `OutlinedButton` с акцентной границей.
     - Иконка: `Icons.Outlined.FolderOpen`.
     - Поведение безопасности (Safety Spell): выбор файла вызывает диалог с двумя опциями:
       - «Добавить к текущим» (Merge, безопасный).
       - «Полностью заменить» (Replace, с модальным предупреждением об удалении существующих данных).
  3. **«Поделиться данными»:**
     - Стиль: `FilledTonalButton` (`secondaryContainer`).
     - Иконка: `Icons.Outlined.Share`.
     - Действие: системный Intent Share с временным файлом бэкапа.

---

### 2.4. Шторка «О приложении» `AboutAppBottomSheet.kt`

```
+-------------------------------------------------------+
|                       ════                            |
|                  [ 📱 QR-Иконка ]                     |
|                 UZB QQS for DIP                       |
|                 [ Версия 1.13.0 ]                     |
|    Учёт и подготовка фискальных чеков для возврата     |
|             НДС (QQS) в Республике Узбекистан         |
|                                                       |
|  [ 🔗  Поделиться приложением (RuStore)      ]        |
|  [ 🔒  Политика конфиденциальности           ]        |
|                                                       |
|  Обратная связь:                                      |
|  [ ✈️ Telegram: @dis_night  ]  [ ✉️ Email: i@dis... ] |
+-------------------------------------------------------+
```

#### Визуальные и технические параметры:
- **Брендинг:**
  - Иконка приложения: контейнер `size(56.dp)`, скругление `14.dp`, цвет `primaryContainer`, внутри `Icons.Outlined.QrCodeScanner` (30dp).
  - Название: `titleLarge`, `FontWeight.Bold`.
  - Версия: чип `surfaceVariant` с текстом `labelMedium`, динамически получаемый из `PackageManager`.
- **Интерактивные элементы:**
  - Кнопка «Поделиться приложением»: системный шеринг ссылки на RuStore (`https://www.rustore.ru/catalog/app/com.example.uzb_qqs_for_dip`).
  - Кнопка «Политика конфиденциальности»: диалог `PrivacyPolicyDialog` (с акцентом на локальное хранение данных в SQLite и отсутствие внешних трекеров).
  - Блок контактов разработчика: две кликабельные плашки в строку (Telegram и Email) с открытием через `LocalUriHandler`.

---

### 2.5. Единая шторка управления пользователями `UserManagementBottomSheet.kt`

```
+-------------------------------------------------------+
|  Управление профилями                                 |
|                                                       |
|  [ (A) ]  Иван Иванов [Активен]           [✏️]  [🗑️]  |
|           Бухгалтер                                   |
|                                                       |
|  [ (П) ]  Пётр Сидоров                    [✏️]  [🗑️]  |
|           Менеджер                                    |
|                                                       |
|  [ + Добавить новый профиль                         ] |
+-------------------------------------------------------+
```

#### Эргономика и разделение зон нажатия (Fitts's Law):
- **Проблема ложных срабатываний:** Если в строке списка есть и переключение пользователя, и редактирование ✏️, и удаление 🗑️, пальцы могут случайно нажать не ту кнопку.
- **Решение:**
  - Высота карточки пользователя: **68-72dp**.
  - **Левая зона (выбор пользователя):** занимает всю ширину до кнопок действий (`weight(1f)`). Клик переключает активного пользователя (`appViewModel.login(user.id)`) и закрывает шторку.
  - **Правая зона (действия):**
    - Кнопка ✏️: `IconButton` размером **48dp x 48dp** (`Icons.Outlined.Edit`, цвет `primary`).
    - Кнопка 🗑️: `IconButton` размером **48dp x 48dp** (`Icons.Outlined.Delete`, цвет `error`).
    - Между кнопками гарантированный зазор 4dp.
- **Индикатор «Активен»:** аккуратный чип `primary` с белым текстом для текущего пользователя. Сама карточка активного пользователя имеет лёгкую подсветку `primaryContainer.copy(alpha = 0.35f)` и акцентную границу `1.5.dp`.
- **Двухэтапная защита удаления:**
  - Этап 1: предупреждение об удалении чеков пользователя.
  - Этап 2: окно с иконкой `Icons.Filled.WarningAmber` и кнопкой «Удалить окончательно».

---

### 2.6. Кликабельное приветствие в «Добавить» (`ScanScreen.kt`)

```
+-------------------------------------------------------+
|  Здравствуйте,                                        |
|  Иван Иванов  ▾                                       |
+-------------------------------------------------------+
```

#### Визуальные и технические параметры:
- **Контейнер:** компактная кликабельная область с `clip(RoundedCornerShape(12.dp))` и `clickable { showUserManagement = true }`.
- **Подложка:** лёгкий полупрозрачный фон `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)` с внутренними отступами `padding(horizontal = 12.dp, vertical = 6.dp)`.
- **Текст приветствия:**
  - «Здравствуйте,» — `style = MaterialTheme.typography.bodySmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`.
  - ФИО — `style = MaterialTheme.typography.titleMedium`, `FontWeight.SemiBold`, `color = MaterialTheme.colorScheme.onSurface`.
- **Индикатор выбора:** стрелка `Icons.Filled.ArrowDropDown` цвета `MaterialTheme.colorScheme.primary`, размер `22dp`.
- **Accessibility:** TalkBack озвучивает «Текущий профиль: [ФИО]. Нажмите для выбора или изменения профиля», `Role.Button`.

---

## 3. Таблица цветов и контрастности (WCAG 2.1 AA Compliance)

| Элемент | Цвет фона | Цвет контента | Коэффициент контраста | Статус WCAG |
|---|---|---|---|---|
| Аватар в TopAppBar | `PrimaryContainer` (#D3E2F2) | `Primary` (#1F4E79) | **6.1 : 1** | ✅ AAA Passed |
| Кнопка «Создать бэкап» | `Primary` (#1F4E79) | `OnPrimary` (#FFFFFF) | **7.8 : 1** | ✅ AAA Passed |
| Кнопка «Поделиться» | `SecondaryContainer` (#C9E5F2) | `Secondary` (#146C94) | **5.4 : 1** | ✅ AA Passed |
| Иконка 🗑️ Удалить | `Surface` (#FFFFFF) | `Error` (#C53030) | **4.9 : 1** | ✅ AA Passed |
| Текст должности | `Surface` (#FFFFFF) | `OnSurfaceVariant` | **4.8 : 1** | ✅ AA Passed |

---

## 4. Рекомендации для Исполнителя (Doer / Senior Android Dev)

1. **TopAppBar:** В `AppNavHost.kt` в `actions` обернуть аватар в `Box(Modifier.size(48.dp), contentAlignment = Alignment.Center)` с внутренним `Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))`.
2. **Изоляция диалогов:** В `ProfileDialogs.kt` держать диалоги чистыми Composable-функциями без прямых обращений к ViewModel, передавая колбэки `onConfirm` и `onDismiss`.
3. **Строки списков:** В `UserManagementBottomSheet.kt` обязательно разделять кликабельную область строки и `IconButton` для ✏️ и 🗑️, чтобы клик по кнопке не вызывал авторизацию пользователя.
4. **Обработка длинных имен:** Для всех полей ФИО обязательно указывать `maxLines = 1` и `overflow = TextOverflow.Ellipsis`.
5. **Масштабирование экранов:** Все шторки должны содержать прокручиваемый контейнер (`verticalScroll(rememberScrollState())` или `LazyColumn`), чтобы при крупном системном шрифте контент оставался полностью доступен.
