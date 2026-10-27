# План реализации: Рефакторинг навигации, главное меню профиля в TopAppBar, шторки резервного копирования и «О приложении», единое управление пользователями и обновление справки

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Удалить вкладку «Профиль» из нижней панели навигации, интегрировать круглый кликабельный аватар текущего пользователя в TopAppBar с открытием `MainMenuBottomSheet`, разработать шторки `BackupBottomSheet` и `AboutAppBottomSheet`, сделать кликабельным приветствие сотрудника в «Добавить», внедрить единую шторку `UserManagementBottomSheet` с инлайн-редактированием (✏️), безопасным удалением (🗑️) и сквозной синхронизацией сессии во всех экранах, а также полностью актуализировать раздел справки `HelpBottomSheet.kt`.

**Architecture:**
1. **Навигационный слой (`ui/navigation/AppNavHost.kt`):**
   - Удаление `MainTab.Profile`. В нижней навигационной панели (`NavigationBar`) остаются строго по 2 вкладки:
     - Режим сотрудника (`EMPLOYEE`): «Добавить» (`Scan`) и «Чеки и отчёт» (`Report`).
     - Режим аудитора (`AUDITOR`): «Аудит» (`Audit`) и «Чеки и отчёт» (`Report`).
   - В правом верхнем углу `TopAppBar` на всех экранах вместо статичной иконки «?» размещается интерактивный круглый аватар активного пользователя (диаметр 38dp, контейнер `primaryContainer`, первая буква имени, сенсорная зона 48dp). Клик по аватару открывает `MainMenuBottomSheet`.
   - Удаление устаревшего экрана `ProfileScreen.kt`.

2. **Компонентный слой (`ui/components/`):**
   - `ProfileDialogs.kt`: вынос переиспользуемых диалогов профиля (`EditProfileDialog`, `CreateProfileDialog`, `EditableProfileField`, `EditSingleFieldDialog`) из `ProfileScreen.kt` в отдельный независимый файл компонентов.
   - `MainMenuBottomSheet.kt`: карточка текущего пользователя (аватар, ФИО, должность, переход в управление пользователями), переключатель «Режим работы» (`switchCurrentUserRole()`), вызовы шторок «Резервная копия», «Справка» и «О приложении».
   - `BackupBottomSheet.kt`: шторка резервного копирования с кнопками «Создать бэкап», «Загрузить бэкап» (с диалогом выбора: «Добавить к текущим» или «Полностью заменить» с окном предупреждения) и «Поделиться данными».
   - `AboutAppBottomSheet.kt`: карточка информации о приложении (версия, описание), кнопка «Поделиться приложением» (ссылка на RuStore), диалог `PrivacyPolicyDialog` и блок «Обратная связь» с кликабельными контактами (Telegram, Email).
   - `UserManagementBottomSheet.kt`: единый компонент управления пользователями со списком сотрудников, индикатором «Активен», кнопкой редактирования ✏️ (`EditProfileDialog`), кнопкой безопасного двухэтапного удаления 🗑️, кнопкой «+ Добавить новый профиль» (`CreateProfileDialog`) и мгновенным переключением активной сессии через `appViewModel.login(userId)`.

3. **Слой экранов (`ui/screens/`):**
   - `ScanScreen.kt`: заголовок «Здравствуйте, [ФИО] ▾» становится кликабельным с иконкой выпадающего списка `ArrowDropDown`. Клик открывает единый `UserManagementBottomSheet`.
   - `ReportScreen.kt`: строка «Сотрудник: [ФИО] ▾» в шапке отчёта открывает единый `UserManagementBottomSheet` вместо старого `SelectUserDialog`. Устаревший диалог `SelectUserDialog` удаляется.
   - `HelpBottomSheet.kt`: полное очищение от устаревших упоминаний вкладки «Профиль» и детальное описание новой архитектуры навигации, главного меню, управления профилями, 5 способов сканирования с иконками, sticky header в отчётах, бэкапа и аудита.

4. **Тестирование и проверка:**
   - Сохранение и адаптация существующих Unit-тестов (`ProfileValidationTest.kt`).
   - Прогон полного набора unit-тестов `./gradlew testDebugUnitTest`.
   - Автоматическая синхронизация карты архитектуры проекта через `python scripts/update_architecture_map.py`.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Material 3, AndroidX Navigation, StateFlow / Coroutines, JUnit 4.

---

## Область изменений (Scope)

- **In Scope:**
  - Создание `ui/components/ProfileDialogs.kt` с переносом `EditProfileDialog`, `CreateProfileDialog`, `EditableProfileField`, `EditSingleFieldDialog`.
  - Обновление `ui/components/UserManagementBottomSheet.kt` с поддержкой редактирования (✏️), создания, двухэтапного удаления (🗑️) и авто-синхронизации через `appViewModel.login(userId)`.
  - Создание `ui/components/BackupBottomSheet.kt` для создания, загрузки (merge/replace) и отправки бэкапа.
  - Создание `ui/components/AboutAppBottomSheet.kt` с версией приложения, ссылкой на RuStore, политикой конфиденциальности и контактами разработчика.
  - Создание `ui/components/MainMenuBottomSheet.kt` для TopAppBar.
  - Модификация `ui/navigation/AppNavHost.kt`: удаление `MainTab.Profile`, интеграция аватара в `TopAppBar`, поддержка 2 вкладок для режимов сотрудника и аудитора.
  - Удаление `ui/screens/ProfileScreen.kt`.
  - Модификация `ui/screens/ScanScreen.kt`: кликабельное приветствие с иконкой выпадающего списка и вызовом `UserManagementBottomSheet`.
  - Модификация `ui/screens/ReportScreen.kt`: замена `SelectUserDialog` на `UserManagementBottomSheet`.
  - Полная переработка контента `ui/components/HelpBottomSheet.kt`.
  - Адаптация тестов `ProfileValidationTest.kt` и прогон тестов `./gradlew testDebugUnitTest`.
  - Запуск скрипта обновления карты архитектуры `scripts/update_architecture_map.py`.

- **Out of Scope:**
  - Изменение схемы базы данных SQLite (`DbHelper.kt`) и миграции (база данных не меняется).
  - Модификация логики парсинга фискальных чеков и сетевых запросов (`ReceiptParser.kt`, `OfdPaymentApi.kt`).
  - Изменение логики алгоритмов генерации PDF и Excel экспорта.

---

## План пошаговой реализации (Action Items)

### Task 1: Выделение диалогов профилей в `ProfileDialogs.kt` и адаптация импортов

**Files:**
- Create: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/ProfileDialogs.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/AuthScreen.kt`
- Modify: `app/src/test/java/com/example/uzb_qqs_for_dip/ui/ProfileValidationTest.kt`

**Step 1: Создание `ProfileDialogs.kt`**
Перенести компоненты `EditableProfileField`, `EditSingleFieldDialog`, `CreateProfileDialog` и `EditProfileDialog` из `ProfileScreen.kt` в отдельный файл `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/ProfileDialogs.kt`.
Диалоги должны использовать Material 3, валидировать обязательные поля (имя и должность) и иметь эргономичные кнопки.

```kotlin
package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class EditableProfileField(val title: String, val label: String) {
    FULL_NAME("Изменить имя", "Имя пользователя (полностью)"),
    POSITION("Изменить должность", "Должность"),
    ORGANIZATION("Изменить организацию", "Организация (необязательно)")
}

@Composable
fun EditSingleFieldDialog(
    field: EditableProfileField,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = { Text(field.label) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = value.trim()
                    if (field != EditableProfileField.ORGANIZATION && trimmed.isEmpty()) {
                        error = "Поле не может быть пустым"
                        return@Button
                    }
                    onConfirm(trimmed)
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun CreateProfileDialog(
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, position: String, organization: String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; localError = null; onClearError() },
                    label = { Text("Имя пользователя (полностью)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it; localError = null; onClearError() },
                    label = { Text("Должность") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it; localError = null; onClearError() },
                    label = { Text("Организация (необязательно)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                val displayError = localError ?: error
                displayError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.trim().isEmpty() || position.trim().isEmpty()) {
                        localError = "Заполните имя и должность"
                        return@Button
                    }
                    onConfirm(fullName.trim(), position.trim(), organization.trim())
                }
            ) { Text("Создать") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun EditProfileDialog(
    initialFullName: String,
    initialPosition: String,
    initialOrganization: String = "",
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, position: String, organization: String) -> Unit,
    title: String = "Редактирование профиля"
) {
    var fullName by remember { mutableStateOf(initialFullName) }
    var position by remember { mutableStateOf(initialPosition) }
    var organization by remember { mutableStateOf(initialOrganization) }

    LaunchedEffect(initialFullName, initialPosition, initialOrganization) {
        fullName = initialFullName
        position = initialPosition
        organization = initialOrganization
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; onClearError() },
                    label = { Text("Имя пользователя (полностью)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it; onClearError() },
                    label = { Text("Должность") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it; onClearError() },
                    label = { Text("Организация (необязательно)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(fullName, position, organization) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
```

**Step 2: Обновление импортов в `AuthScreen.kt` и `ProfileValidationTest.kt`**
- В `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/AuthScreen.kt`:
  Заменить импорт `EditProfileDialog` из `ProfileScreen` на `com.example.uzb_qqs_for_dip.ui.components.EditProfileDialog`.
- В `app/src/test/java/com/example/uzb_qqs_for_dip/ui/ProfileValidationTest.kt`:
  Заменить импорт `EditableProfileField` на `com.example.uzb_qqs_for_dip.ui.components.EditableProfileField`.

**Step 3: Проверка тестов валидации**
Запустить: `./gradlew testDebugUnitTest --tests "com.example.uzb_qqs_for_dip.ui.ProfileValidationTest"`
Ожидаемый результат: тесты проходят успешно (BUILD SUCCESSFUL).

---

### Task 2: Единая шторка управления пользователями `UserManagementBottomSheet.kt`

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/UserManagementBottomSheet.kt`

**Требования к UI и поведению:**
1. Отображение списка всех зарегистрированных пользователей (`users` из `AppViewModel`).
2. В строке каждого пользователя:
   - Круглый аватар с первой буквой имени.
   - ФИО (жирным шрифтом, если активен) и должность.
   - Индикатор «Активен» (чип `primaryContainer` / `primary`) для текущего авторизованного пользователя.
   - Кнопка-карандаш ✏️ `Icons.Outlined.Edit` с доступным Touch Target (не менее 48dp).
   - Кнопка-корзина 🗑️ `Icons.Outlined.Delete` с оттенком `error` и Touch Target не менее 48dp.
3. Поведение при клике на строку пользователя:
   - Вызывает `appViewModel.login(user.id)`.
   - Мгновенно синхронизирует текущую сессию во всех экранах приложения.
   - Закрывает шторку (`onDismissRequest()`).
4. Поведение при клике на ✏️:
   - Открывает модальный диалог `EditProfileDialog`.
   - Позволяет изменить «Имя», «Должность», «Организацию».
   - При подтверждении вызывает `appViewModel.updateProfile(user.id, name, pos, org, user.role)`.
5. Поведение при клике на 🗑️:
   - Запускает двухэтапное безопасное подтверждение:
     - Этап 1: Диалог с предупреждением, что все чеки этого пользователя будут удалены.
     - Этап 2: Финальный диалог с иконкой предупреждения `Icons.Filled.WarningAmber` и кнопкой «Удалить окончательно».
     - При подтверждении вызывает `appViewModel.deleteProfile(user.id) { if (isCurrent) onLoggedOut() }`.
6. Кнопка «+ Добавить новый профиль»:
   - Расположена внизу списка пользователей.
   - Открывает `CreateProfileDialog`.
   - При создании вызывает `appViewModel.register(fullName, position, organization, autoLogin = false)`.

**Сигнатура компонента:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementBottomSheet(
    appViewModel: AppViewModel,
    onDismissRequest: () -> Unit,
    onLoggedOut: () -> Unit = {}
)
```

**Step 1: Реализация компонента в `UserManagementBottomSheet.kt`**
Написать код компонента с внутренней обработкой состояний диалогов редактирования, создания и удаления, исключая дублирование кода в других экранах.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`
Убедиться в отсутствии ошибок компиляции.

---

### Task 3: Шторка резервного копирования `BackupBottomSheet.kt`

**Files:**
- Create: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/BackupBottomSheet.kt`

**Требования к UI и поведению:**
1. Заголовок «Резервная копия» и пояснение: «Один файл JSON: все профили, сохранённые чеки и настройки отчёта.»
2. Карточка / кнопки действий:
   - **«Создать бэкап»** (`Icons.Outlined.SaveAlt`):
     - Открывает системный ActivityResultLauncher `CreateDocument(AppBackup.MIME_TYPE)`.
     - При выборе файла вызывает `appViewModel.exportBackupToUri(context, uri)`.
   - **«Загрузить бэкап»** (`Icons.Outlined.FolderOpen`):
     - Открывает системный ActivityResultLauncher `OpenDocument()`.
     - При выборе файла открывает модальный диалог с двумя опциями:
       - «Добавить к текущим» -> вызывает `appViewModel.mergeBackupFromUri(context, uri)`.
       - «Полностью заменить» -> открывает второе окно предупреждения «Внимание! Вы уверены в полной замене базы? Текущие данные будут безвозвратно потеряны» -> вызывает `appViewModel.importBackupFromUri(context, uri, onNavigateToAuth)`.
   - **«Поделиться данными»** (`Icons.Outlined.Share`):
     - Вызывает `appViewModel.shareBackup(context)`, создавая временный файл во внешнем хранилище кэша и вызывая системное меню «Поделиться».
3. Соблюдение Material 3, скругление карточек 16dp, высота кнопок 48dp, иконки перед текстом кнопок.

**Сигнатура компонента:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupBottomSheet(
    appViewModel: AppViewModel,
    onDismissRequest: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
)
```

**Step 1: Создание `BackupBottomSheet.kt`**
Реализовать компонент с интеграцией ActivityResultContract и вызовом методов бэкапа из `AppViewModel`.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`

---

### Task 4: Шторка «О приложении» `AboutAppBottomSheet.kt` и диалог политики

**Files:**
- Create: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/AboutAppBottomSheet.kt`

**Требования к UI и поведению:**
1. Информационный блок:
   - Название приложения: **UZB QQS for DIP**.
   - Версия приложения: динамическое получение из `PackageManager` (с фоллбэком на `1.13.0`).
   - Иконка приложения или тематический символ (`Icons.Outlined.QrCodeScanner`).
   - Краткое описание: «Учёт и подготовка фискальных чеков для возврата НДС (QQS) в Республике Узбекистан».
2. Интерактивные строки/кнопки:
   - **«Поделиться приложением»** (`Icons.Outlined.Share`):
     - Отправка системного Intent `ACTION_SEND` с текстом ссылки на RuStore: `https://www.rustore.ru/catalog/app/com.example.uzb_qqs_for_dip`.
   - **«Политика конфиденциальности»** (`Icons.Outlined.Security`):
     - Открывает диалог `PrivacyPolicyDialog`.
   - **«Обратная связь»**:
     - Блок контактов с кликабельными ссылками (используя `LocalUriHandler`):
     - Telegram: `t.me/dis_night` (открывает `https://t.me/dis_night`).
     - Email: `i@dis-night.ru` (открывает `mailto:i@dis-night.ru`).
3. Диалог `PrivacyPolicyDialog`:
   - Разъясняет локальное хранение данных на устройстве пользователя в SQLite.
   - Подтверждает отсутствие сбора аналитики, трекеров и передачи данных третьим лицам.
   - Поясняет, что сетевые запросы совершаются только к официальным шлюзам ОФД / Soliq для получения фискальных реквизитов чека.

**Сигнатура компонента:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppBottomSheet(
    onDismissRequest: () -> Unit
)
```

**Step 1: Создание `AboutAppBottomSheet.kt`**
Написать компонент в соответствии со спецификацией.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`

---

### Task 5: Главная шторка `MainMenuBottomSheet.kt`

**Files:**
- Create: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/MainMenuBottomSheet.kt`

**Требования к UI и поведению:**
1. Карточка текущего профиля:
   - Аватар пользователя (46dp, CircleShape, `primaryContainer`, первая буква имени).
   - ФИО и должность.
   - Иконка перехода / смены профиля (`Icons.Outlined.SwapHoriz` или шеврон).
   - Клик по карточке закрывает текущую шторку и открывает `UserManagementBottomSheet`.
2. Тонкий разделитель `HorizontalDivider()`.
3. Меню пунктов (вертикальный список с иконкой, заголовком и подзаголовком / бейджем):
   - **«Режим работы»** (`Icons.Outlined.AdminPanelSettings`):
     - Отображает текущий режим: «Сотрудник» или «Аудитор» с бейджем.
     - Клик вызывает `onSwitchRole()`.
   - **«Резервная копия»** (`Icons.Outlined.Backup`):
     - Подзаголовок: «Сохранение и восстановление базы данных».
     - Клик закрывает меню и открывает `BackupBottomSheet`.
   - **«Справка»** (`Icons.AutoMirrored.Outlined.HelpOutline`):
     - Подзаголовок: «Инструкции и руководство по работе».
     - Клик закрывает меню и открывает `HelpBottomSheet`.
   - **«О приложении»** (`Icons.Outlined.Info`):
     - Подзаголовок: «Версия, контакты и политика конфиденциальности».
     - Клик закрывает меню и открывает `AboutAppBottomSheet`.

**Сигнатура компонента:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuBottomSheet(
    user: User?,
    onOpenUserManagement: () -> Unit,
    onSwitchRole: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAboutApp: () -> Unit,
    onDismissRequest: () -> Unit
)
```

**Step 1: Создание `MainMenuBottomSheet.kt`**
Реализовать шторку согласно архитектурному шаблону.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`

---

### Task 6: Рефакторинг навигации в `AppNavHost.kt`, TopAppBar и удаление `ProfileScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/navigation/AppNavHost.kt`
- Delete: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ProfileScreen.kt`

**Требования к UI и навигации:**
1. Удалить `MainTab.Profile`:
   - Вкладки в `MainTab.employee`: `listOf(Scan, Report)` (ровно 2 вкладки: «Добавить» и «Чеки и отчёт»).
   - Вкладки в `MainTab.auditor`: `listOf(Audit, Report)` (ровно 2 вкладки: «Аудит» и «Чеки и отчёт»).
2. В `TopAppBar` (в `MainScaffold`):
   - В слоте `actions` заменить старую кнопку со знаком «?» на круглый аватар активного пользователя:
     - Размер аватара: 38dp.
     - Фон: `MaterialTheme.colorScheme.primaryContainer`.
     - Текст: первая буква имени `currentUser?.fullName?.take(1)?.uppercase() ?: "?"`.
     - Стиль текста: `MaterialTheme.typography.titleMedium`, цвет `MaterialTheme.colorScheme.primary`, `FontWeight.Bold`.
     - Контейнер кликабельный с Touch Target не менее 48dp (`IconButton` или `Box(Modifier.size(48.dp), contentAlignment = Alignment.Center)`).
     - Клик по аватару устанавливает `showMainMenu = true`.
3. Управление шторками в `MainScaffold`:
   - `var showMainMenu by remember { mutableStateOf(false) }`
   - `var showUserManagement by remember { mutableStateOf(false) }`
   - `var showBackup by remember { mutableStateOf(false) }`
   - `var showHelp by remember { mutableStateOf(false) }`
   - `var showAboutApp by remember { mutableStateOf(false) }`
   - Подключение `MainMenuBottomSheet`, `UserManagementBottomSheet`, `BackupBottomSheet`, `AboutAppBottomSheet`, `HelpBottomSheet`.
4. Удалить `composable(MainTab.Profile.route)` из `NavHost`.
5. Удалить неиспользуемый файл `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ProfileScreen.kt`.

**Step 1: Обновление `AppNavHost.kt`**
Применить изменения в `AppNavHost.kt`.

**Step 2: Удаление файла `ProfileScreen.kt`**
Удалить файл `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ProfileScreen.kt`.

**Step 3: Проверка сборки и тестов**
Запустить: `./gradlew testDebugUnitTest`
Убедиться, что проект компилируется и старые тесты не ломаются.

---

### Task 7: Кликабельное приветствие в «Добавить» (`ScanScreen.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ScanScreen.kt`

**Требования к UI и поведению:**
1. Блок приветствия вверху экрана:
   - Отображает «Здравствуйте,», а ниже ФИО текущего пользователя со стрелочкой выпадающего списка `Icon(Icons.Filled.ArrowDropDown, contentDescription = "Сменить профиль", tint = MaterialTheme.colorScheme.primary)`.
   - Обернут в кликабельный контейнер с `clip(RoundedCornerShape(12.dp))` и эффектом нажатия (ripple).
   - Клик по блоку открывает `UserManagementBottomSheet`.
2. Подключение `UserManagementBottomSheet(appViewModel = appViewModel, onDismissRequest = { showUserManagement = false })` в `ScanScreen`.
3. Так как `currentUser` собирается через `collectAsStateWithLifecycle()`, смена пользователя в шторке мгновенно обновляет имя на экране и привязку добавляемых чеков.

**Step 1: Редактирование `ScanScreen.kt`**
Обновить composable `Header` и добавить состояние `showUserManagementSheet`.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`

---

### Task 8: Интеграция `UserManagementBottomSheet` в «Чеки и отчёт» (`ReportScreen.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`

**Требования к UI и поведению:**
1. В шапке отчёта (`ReportHeaderCard`) клик по строке сотрудника вызывает открытие `UserManagementBottomSheet`.
2. Заменить старый радио-диалог `SelectUserDialog` на `UserManagementBottomSheet(appViewModel = appViewModel, onDismissRequest = { showUserPicker = false })`.
3. Удалить composable `SelectUserDialog` и вспомогательный класс `UserChoice` (если он больше не используется).
4. При смене пользователя в `UserManagementBottomSheet` происходит вызов `appViewModel.login(user.id)`, а в отчёте сбрасывается или синхронизируется фильтр, чтобы отчёт сразу отображал чеки выбранного сотрудника.

**Step 1: Модификация `ReportScreen.kt`**
Заменить `SelectUserDialog` на `UserManagementBottomSheet`, вычистить устаревший код диалога.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`

---

### Task 9: Полная актуализация справки в `HelpBottomSheet.kt`

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt`

**Требования к содержанию справки:**
1. **Полная зачистка:** Удалить любые упоминания старой вкладки «Профиль» в нижней панели.
2. **Раздел «Навигация и Главное меню» (Новый):**
   - Описание обновлённой нижней панели: строго 2 вкладки («Добавить» и «Чеки и отчёт» для сотрудников, «Аудит» и «Чеки и отчёт» для аудитора).
   - Круглый аватар пользователя в TopAppBar: быстрый доступ к главному меню из любой точки приложения.
   - Состав Главного меню: карточка профиля, переключение режима работы («Сотрудник» / «Аудитор»), «Резервная копия», «Справка» и «О приложении».
3. **Раздел «Управление профилями» (Актуализированный):**
   - 3 удобных точки входа: клик по аватару в TopAppBar -> карточка профиля, клик по приветствию «Здравствуйте, [ФИО] ▾» в «Добавить», клик по строке сотрудника в шапке «Чеки и отчёт».
   - Единая шторка: список всех сотрудников с отметкой «Активен», кнопка редактирования ✏️ (ФИО, должность, организация), кнопка безопасного двухэтапного удаления 🗑️, кнопка «+ Добавить новый профиль».
   - Мгновенная сквозная синхронизация активной сессии во всем приложении.
4. **Раздел «Резервная копия» (Новый/Актуализированный):**
   - Создание бэкапа в единый JSON-файл (все профили, чеки и настройки).
   - Загрузка бэкапа: «Добавить к текущим» (умное слияние без дублей) или «Полностью заменить» (с защитой от случайного удаления).
   - Функция «Поделиться данными» для быстрой отправки базы через мессенджеры или почту.
5. **Раздел «О приложении» (Новый):**
   - Версия приложения, ссылка на RuStore, политика конфиденциальности и прямые контакты разработчика (Telegram, Email).
6. **Разделы «Добавление чеков» (5 способов с иконками), «Чеки и отчёт» (со sticky header) и «Аудит»:**
   - Сохранить и привести к идеальному гармоничному виду с аккордеонами `AccordionItem`.

**Step 1: Обновление `HelpBottomSheet.kt`**
Переписать содержимое с актуализацией всех блоков.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`

---

### Task 10: Финальное тестирование, сборка и автосинхронизация карты архитектуры

**Files:**
- Test verification: Unit-тесты
- Architecture Map sync: `SYSTEM_PROMPT.md`, `.cursorrules`

**Step 1: Запуск unit-тестов проекта**
Выполнить команду в PowerShell:
`./gradlew testDebugUnitTest`
Ожидаемый результат: Все тесты (включая тесты экспорта, UI-логики, валидации профиля и парсинга) завершаются успешно: `BUILD SUCCESSFUL`.

**Step 2: Автосинхронизация карты архитектуры**
Выполнить команду:
`python scripts/update_architecture_map.py`
Скрипт просканирует файловую структуру, зафиксирует удаление `ProfileScreen.kt`, появление `MainMenuBottomSheet.kt`, `BackupBottomSheet.kt`, `AboutAppBottomSheet.kt`, `ProfileDialogs.kt` и обновит блок `<architecture_map>` в `SYSTEM_PROMPT.md` и `.cursorrules`.

**Step 3: Проверка статуса Git**
`git status` — убедиться, что изменены только целевые файлы и нет лишнего мусора или повреждения кодировок UTF-8.

---

## Варианты выполнения (Execution Handoff)

План составлен, детализирован и сохранен в `IMPLEMENTATION_PLAN.md`. Доступны следующие варианты исполнения:

1. **Subagent-Driven (в текущей сессии):**
   - Оркестратор запускает субагента-Дизайнера (`Designer`) для верификации эргономики UI/UX и Touch Targets (>= 48dp).
   - Затем субагент-Исполнитель (`Doer`) последовательно реализует задачи 1–9.
   - Тестировщик (`TestEngineer`) и Ревьюер (`Reviewer`) проверяют тесты и синхронизируют карту архитектуры.
2. **Ручное выполнение (Step-by-step):**
   - Пошаговое подтверждение каждого этапа пользователем.
