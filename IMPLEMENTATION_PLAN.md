# План реализации: Новая иконка и имя приложения, цветовая индикация роли, единая иерархическая шторка (Hierarchical BottomSheet), переключатель тем, редизайн приветствия и актуализация справки с виджетом

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Внедрить новую официальную иконку и название «Скан чеков Узбекистана», разработать единую иерархическую шторку (`Hierarchical BottomSheet`) с плавной горизонтальной слайд-анимацией (`AnimatedContent`) и системным `BackHandler`, внедрить цветовое разделение ролей (красный «Аудитор» / синий «Сотрудник»), реализовать сохраняемое переключение тем оформления (Светлая / Тёмная / Системная) с сегментированным контролом в меню, обновить экран «Добавить» полноразмерной карточкой приветствия без заголовка «Добавить чек», добавить в «О приложении» официальный логотип и прямую ссылку на RuStore, а также полностью актуализировать раздел справки иконками для всех пунктов и новыми блоками про виджет и авто-открытие ссылок.

**Architecture:**
1. **Ресурсы и брендинг (`res/`):**
   - Сохранение официальной иконки пользователя как `app/src/main/res/drawable/app_logo.png`.
   - Генерация полного комплекта launcher mipmaps (`mdpi` 48x48, `hdpi` 72x72, `xhdpi` 96x96, `xxhdpi` 144x144, `xxxhdpi` 192x192), адаптивного слоя `ic_launcher_foreground.png` (108..432px) и `ic_launcher-playstore.png` (512x512).
   - Обновление названия в `res/values/strings.xml` на `<string name="app_name">Скан чеков Узбекистана</string>`.
2. **Слой настроек и тем оформления (`data/settings/ThemeManager.kt`, `ui/theme/`):**
   - `AppThemeMode` (`SYSTEM`, `LIGHT`, `DARK`) с персистентным хранением в `SharedPreferences` (`app_theme_prefs`).
   - Реактивный `StateFlow<AppThemeMode>` в `ThemeManager`, проброшенный через `AppContainer` в `AppViewModel` и `MainActivity`.
   - `MainActivity.kt` динамически переключает тему `UZB_QQS_for_DIPTheme(darkTheme = isDark)` без перезапуска Activity.
3. **Единая иерархическая шторка (`Hierarchical BottomSheet` в `ui/components/MainMenuBottomSheet.kt`):**
   - ОДНА общая `ModalBottomSheet` с состоянием `MenuScreen` (`MAIN`, `USERS`, `BACKUP`, `HELP`, `ABOUT`).
   - Навигация между экранами внутри шторки через `AnimatedContent` с горизонтальными слайд-анимациями влево/вправо.
   - Каждый подэкран содержит `SubScreenHeader` со стрелкой «Назад» `←` и заголовком.
   - `BackHandler(enabled = currentScreen != MenuScreen.MAIN) { currentScreen = MenuScreen.MAIN }` перехватывает системный жест «Назад».
   - В главном экране меню (`MAIN`):
     - Цветовая индикация роли «Режим работы»: «Аудитор» (красная иконка, светло-красная подложка, красный бейдж) / «Сотрудник» (синяя иконка, светло-синяя подложка, синий бейдж).
     - Сегментированный переключатель `[Системная] [Светлая] [Тёмная]`.
   - Экспорт содержимого подэкранов как переиспользуемых composables: `UserManagementContent`, `BackupContent`, `HelpContent`, `AboutAppContent`.
4. **Сквозная навигация к шторке профилей (`AppNavHost.kt`, `ScanScreen.kt`, `ReportScreen.kt`):**
   - `AppViewModel` предоставляет методы `openMenu(screen: MenuScreen)` и `closeMenu()`.
   - Клик по аватару в `TopAppBar` открывает `MenuScreen.MAIN`.
   - Клик по приветствию в «Добавить» (`ScanScreen`) и строке сотрудника в «Чеки и отчёт» (`ReportScreen`) открывает шторку сразу на `MenuScreen.USERS`. При нажатии «Назад» `←` пользователь попадает в главное меню.
5. **Экран «Добавить» (`ScanScreen.kt`):**
   - Карточка приветствия «Здравствуйте, [ФИО] ▾» занимает всю ширину экрана (16dp скругление, аватар с первой буквой, имя, стрелка).
   - Удаление текста «Добавить чек» и лишнего вертикального отступа над кнопками.
6. **Шторка «О приложении» (`AboutAppContent`):**
   - Логотип `app_logo.png` (72x72dp, 16dp скругление), имя «Скан чеков Узбекистана», версия.
   - Прямая кнопка «Страница в RuStore» (`https://www.rustore.ru/catalog/app/com.example.uzb_qqs_for_dip`).
   - Кнопка «Поделиться приложением», «Политика конфиденциальности», интерактивные контакты (Telegram `@dis_night`, Email `i@dis-night.ru`).
7. **Справка («?») (`HelpContent`):**
   - Иконки и значки во всех пунктах и подпунктах в едином визуальном стиле карточек функций.
   - Новый раздел: «Виджет быстрого сканирования» (добавление на домашний экран, скан в 1 тап без открытия приложения).
   - Новый раздел: «Автоматическое открытие ссылок на чеки» (настройки Android для `ofd.soliq.uz`, `my.soliq.uz`, `ofd.multicard.uz` и системный Share).
8. **Тестирование и автосинхронизация карты архитектуры:**
   - Unit-тесты для `ThemeManager`, `AppViewModel` и иерархической навигации шторки.
   - Автосинхронизация карты архитектуры через `python scripts/update_architecture_map.py`.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Material 3, AndroidX Navigation, StateFlow / Coroutines, AndroidX Activity Compose (BackHandler), JUnit 4, Python Pillow (для ассетов).

---

## Область изменений (Scope)

- **In Scope:**
  - Генерация ассетов иконок (`app_logo.png`, `ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`, `ic_launcher-playstore.png`) из загруженного изображения `media_1793121883860_72c3d03d.png`.
  - Изменение `<string name="app_name">Скан чеков Узбекистана</string>` в `strings.xml` и актуализация названий в интерфейсе.
  - Создание `data/settings/ThemeManager.kt` с enum `AppThemeMode` (`SYSTEM`, `LIGHT`, `DARK`) и сохранением в SharedPreferences.
  - Подключение `ThemeManager` в `AppContainer`, `AppViewModel` и `MainActivity`.
  - Модификация `MainMenuBottomSheet.kt`: единый `HierarchicalMenuBottomSheet` с `AnimatedContent`, `BackHandler`, индикацией ролей (красный/синий), сегментированным селектором тем и `SubScreenHeader`.
  - Декомпозиция шторок на `UserManagementContent`, `BackupContent`, `HelpContent`, `AboutAppContent`.
  - Модификация `AppNavHost.kt`: управление единой шторкой, поддержка открытия с `MenuScreen.USERS` или `MenuScreen.MAIN`.
  - Модификация `ScanScreen.kt`: карточка приветствия во всю ширину, удаление надписи «Добавить чек» и отступа над кнопками.
  - Модификация `AboutAppBottomSheet.kt`: логотип 72x72dp, имя «Скан чеков Узбекистана», прямая кнопка в RuStore.
  - Модификация `HelpBottomSheet.kt`: цветные иконки для ВСЕХ пунктов справки, новые разделы виджета и авто-открытия ссылок.
  - Создание Unit-тестов для `ThemeManager` и навигации шторки.
  - Запуск скрипта обновления карты архитектуры `scripts/update_architecture_map.py`.

- **Out of Scope:**
  - Изменение схемы БД SQLite (`DbHelper.kt`) и миграции.
  - Изменение сетевого протокола парсинга ОФД (`ReceiptParser.kt`, `OfdPaymentApi.kt`).
  - Изменение логики генерации PDF/Excel отчетов.

---

## План пошаговой реализации (Action Items)

### Task 1: Генерация иконок приложения и обновление названия в `strings.xml`

**Files:**
- Source image: `C:/Users/kuret/.gemini/antigravity/brain/96a0e738-0b39-4be1-a6e5-3befd9f9a707/.user_uploaded/media_1793121883860_72c3d03d.png`
- Create/Overwrite: `app/src/main/res/drawable/app_logo.png`
- Create/Overwrite: `app/src/main/ic_launcher-playstore.png` (512x512)
- Create/Overwrite: `app/src/main/res/mipmap-mdpi/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`
- Create/Overwrite: `app/src/main/res/mipmap-hdpi/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`
- Create/Overwrite: `app/src/main/res/mipmap-xhdpi/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`
- Create/Overwrite: `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`
- Create/Overwrite: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`
- Remove old webp icons in `mipmap-*` to prevent duplicate resource conflicts.
- Modify: `app/src/main/res/values/strings.xml:2`

**Step 1: Написание Python-скрипта генерации иконок**
Создать временный скрипт `scripts/generate_app_icons.py` (с кодировкой `utf-8`), который:
1. Загружает исходное изображение `media_1793121883860_72c3d03d.png`.
2. Сохраняет полноразмерный `app/src/main/res/drawable/app_logo.png`.
3. Генерирует `app/src/main/ic_launcher-playstore.png` размером 512x512 (высококачественный ресемплинг `LANCZOS`).
4. Для каждого каталога mipmap генерирует:
   - `mdpi`: 48x48 (`ic_launcher.png`, `ic_launcher_round.png`), 108x108 (`ic_launcher_foreground.png`)
   - `hdpi`: 72x72 (`ic_launcher.png`, `ic_launcher_round.png`), 162x162 (`ic_launcher_foreground.png`)
   - `xhdpi`: 96x96 (`ic_launcher.png`, `ic_launcher_round.png`), 216x216 (`ic_launcher_foreground.png`)
   - `xxhdpi`: 144x144 (`ic_launcher.png`, `ic_launcher_round.png`), 324x324 (`ic_launcher_foreground.png`)
   - `xxxhdpi`: 192x192 (`ic_launcher.png`, `ic_launcher_round.png`), 432x432 (`ic_launcher_foreground.png`)
5. Удаляет старые `.webp` файлы (`ic_launcher.webp`, `ic_launcher_round.webp`, `ic_launcher_foreground.webp`) во всех папках `mipmap-*`.

**Step 2: Запуск скрипта генерации**
Запустить: `python scripts/generate_app_icons.py`.
Убедиться, что файлы созданы и старые webp удалены.

**Step 3: Обновление названия в `strings.xml`**
В `app/src/main/res/values/strings.xml` заменить:
```xml
    <string name="app_name">QQS Сканер</string>
```
на:
```xml
    <string name="app_name">Скан чеков Узбекистана</string>
```

**Step 4: Проверка компиляции ресурсов**
Запустить: `./gradlew processDebugResources`.
Ожидаемый результат: `BUILD SUCCESSFUL`.

---

### Task 2: Реализация менеджера тем `ThemeManager.kt`, реактивного `themeMode` в `AppViewModel` и `MainActivity`

**Files:**
- Create: `app/src/main/java/com/example/uzb_qqs_for_dip/data/settings/ThemeManager.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/data/AppContainer.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/MainActivity.kt`

**Step 1: Создание `ThemeManager.kt`**
Реализовать класс управления темами оформления:
```kotlin
package com.example.uzb_qqs_for_dip.data.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private fun loadThemeMode(): AppThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(raw ?: AppThemeMode.SYSTEM.name) }
            .getOrDefault(AppThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    private companion object {
        const val PREFS_NAME = "app_theme_prefs"
        const val KEY_THEME_MODE = "app_theme_mode"
    }
}
```

**Step 2: Регистрация `ThemeManager` в `AppContainer.kt`**
Добавить в `AppContainer`:
```kotlin
val themeManager: ThemeManager by lazy { ThemeManager(appContext) }
```

**Step 3: Проброс `themeMode` в `AppViewModel.kt`**
Добавить в `AppViewModel`:
```kotlin
val themeMode: StateFlow<AppThemeMode> = container.themeManager.themeMode

fun setThemeMode(mode: AppThemeMode) {
    container.themeManager.setThemeMode(mode)
}
```

**Step 4: Подключение динамической темы в `MainActivity.kt`**
В `MainActivity.kt`:
```kotlin
val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
val isDark = when (themeMode) {
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
}
UZB_QQS_for_DIPTheme(darkTheme = isDark) {
    AppNavHost(appViewModel = appViewModel)
}
```

**Step 5: Проверка тестов**
Запустить: `./gradlew testDebugUnitTest`.

---

### Task 3: Декомпозиция содержимого шторок на переиспользуемые composables (`UserManagementContent`, `BackupContent`, `AboutAppContent`, `HelpContent`)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/UserManagementBottomSheet.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/BackupBottomSheet.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/AboutAppBottomSheet.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt`

**Требования к декомпозиции:**
Чтобы избежать создания вложенных `ModalBottomSheet` и эффекта мерцания при переходах, каждый файл должен предоставлять чистый composable-контент (например, `UserManagementContent(...)`), который может размещаться внутри единой шторки с горизонтальной навигацией `AnimatedContent`.

**Step 1: Выделение `UserManagementContent` в `UserManagementBottomSheet.kt`**
Вынести внутренний Column со списком пользователей, кнопками ✏️ и 🗑️, а также диалогами в:
```kotlin
@Composable
fun UserManagementContent(
    appViewModel: AppViewModel,
    onUserSelected: (User) -> Unit,
    onLoggedOut: () -> Unit = {},
    modifier: Modifier = Modifier
)
```
Оставить `UserManagementBottomSheet` как легкую composable-обертку вокруг `UserManagementContent` для обратной совместимости.

**Step 2: Выделение `BackupContent` в `BackupBottomSheet.kt`**
Вынести карточки создания, загрузки (с диалогом выбора merge/replace) и отправки бэкапа в:
```kotlin
@Composable
fun BackupContent(
    appViewModel: AppViewModel,
    onNavigateToAuth: () -> Unit = {},
    modifier: Modifier = Modifier
)
```
Оставить `BackupBottomSheet` как composable-обертку вокруг `BackupContent`.

**Step 3: Выделение `AboutAppContent` в `AboutAppBottomSheet.kt`**
Вынести карточку с логотипом, описанием, кнопками RuStore, Поделиться, диалогом политики и контактами в:
```kotlin
@Composable
fun AboutAppContent(
    onDismissRequest: () -> Unit = {},
    modifier: Modifier = Modifier
)
```
Оставить `AboutAppBottomSheet` как обертку.

**Step 4: Выделение `HelpContent` в `HelpBottomSheet.kt`**
Вынести список раскрывающихся разделов справки в:
```kotlin
@Composable
fun HelpContent(
    modifier: Modifier = Modifier
)
```
Оставить `HelpBottomSheet` как обертку.

**Step 5: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`.

---

### Task 4: Реализация единой иерархической шторки `HierarchicalMenuBottomSheet` с анимациями и индикацией роли

**Files:**
- Modify/Rewrite: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/MainMenuBottomSheet.kt`

**Требования к UI и поведению:**
1. **Перечисление экранов шторки:**
   ```kotlin
   enum class MenuScreen(val title: String) {
       MAIN("Меню"),
       USERS("Управление профилями"),
       BACKUP("Резервная копия"),
       HELP("Справка и руководство"),
       ABOUT("О приложении")
   }
   ```
2. **Шапка подэкранов `SubScreenHeader`:**
   - Стрелка «Назад» `IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }`.
   - Заголовок подэкрана крупным шрифтом (`titleLarge`, `FontWeight.Bold`).
   - Минимальный сенсорный размер зоны нажатия — 48dp.
3. **Плавная горизонтальная навигация через `AnimatedContent`:**
   - Переход вперед (`MAIN -> USERS/BACKUP/HELP/ABOUT`): `slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()`.
   - Переход назад (`... -> MAIN`): `slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()`.
4. **Аппаратный перехват жеста/кнопки «Назад»:**
   - `BackHandler(enabled = currentScreen != MenuScreen.MAIN) { currentScreen = MenuScreen.MAIN }`.
5. **Цветовая индикация роли в `MainMenuContent`:**
   - Для роли «Аудитор»:
     - Иконка `Icons.Outlined.AdminPanelSettings`: цвет `Danger` (`Color(0xFFDC2626)`).
     - Подложка иконки: светло-красная (`Color(0xFFFEE2E2)` в светлой теме / `Danger.copy(alpha = 0.2f)` в тёмной).
     - Подзаголовок: «Переключить на «Сотрудник»».
     - Бейдж: сплошной красный фон (`Danger`), белый текст (`Color.White`), надпись «Аудитор».
   - Для роли «Сотрудник»:
     - Иконка `Icons.Outlined.AdminPanelSettings`: цвет `Primary` (`Color(0xFF2563EB)`).
     - Подложка иконки: светло-синяя (`Color(0xFFDBEAFE)` в светлой теме / `Primary.copy(alpha = 0.2f)` в тёмной).
     - Подзаголовок: «Переключить на «Аудитор»».
     - Бейдж: сплошной синий фон (`Primary`), белый текст (`Color.White`), надпись «Сотрудник».
6. **Сегментированный переключатель тем в `MainMenuContent`:**
   - Строка с выбором: `[Системная] [Светлая] [Тёмная]`.
   - Наглядная подсветка активного сегмента (`primaryContainer` / `primary`, `FontWeight.Bold`).
   - Иконки перед текстом каждого сегмента (`AutoMode`, `LightMode`, `DarkMode`).
   - Мгновенное обновление `onSetThemeMode(mode)`.
7. **Сигнатура компонента:**
   ```kotlin
   @OptIn(ExperimentalMaterial3Api::class)
   @Composable
   fun HierarchicalMenuBottomSheet(
       appViewModel: AppViewModel,
       initialScreen: MenuScreen = MenuScreen.MAIN,
       onDismissRequest: () -> Unit,
       onLoggedOut: () -> Unit = {}
   )
   ```

**Step 1: Реализация `HierarchicalMenuBottomSheet` в `MainMenuBottomSheet.kt`**
Написать полный код шторки с переключателем тем, цветовой индикацией ролей, анимацией смены экранов и `BackHandler`.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`.

---

### Task 5: Интеграция единой шторки в `AppNavHost.kt` и сквозное открытие с экрана `USERS`

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/navigation/AppNavHost.kt`
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt`

**Step 1: Добавление событий управления меню в `AppViewModel.kt`**
Добавить в `AppViewModel`:
```kotlin
private val _menuInitialScreen = MutableStateFlow<MenuScreen?>(null)
val menuInitialScreen: StateFlow<MenuScreen?> = _menuInitialScreen.asStateFlow()

fun openMenu(screen: MenuScreen = MenuScreen.MAIN) {
    _menuInitialScreen.value = screen
}

fun closeMenu() {
    _menuInitialScreen.value = null
}
```

**Step 2: Обновление `AppNavHost.kt`**
1. В `MainScaffold`:
   - Заменить 5 флагов (`showMainMenu`, `showUserManagement`, `showBackup`, `showHelp`, `showAboutApp`) на единый сбор `val menuInitialScreen by appViewModel.menuInitialScreen.collectAsStateWithLifecycle()`.
   - При `menuInitialScreen != null`: отображать `HierarchicalMenuBottomSheet(appViewModel = appViewModel, initialScreen = it, onDismissRequest = { appViewModel.closeMenu() }, onLoggedOut = onLoggedOut)`.
   - В TopAppBar: клик по аватару вызывает `appViewModel.openMenu(MenuScreen.MAIN)`.
   - Актуализировать дефолтный заголовок приложения: «Скан чеков Узбекистана».

**Step 3: Обновление `ReportScreen.kt`**
В шапке отчёта при клике на строку «Сотрудник: [ФИО] ▾» вызывать `appViewModel.openMenu(MenuScreen.USERS)`.
Удалить вызов локального `showUserPicker` и шторки `UserManagementBottomSheet`, так как теперь используется централизованная иерархическая шторка.

**Step 4: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`.

---

### Task 6: Редизайн экрана «Добавить» (`ScanScreen.kt`): Карточка приветствия во всю ширину и очистка

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ScanScreen.kt`

**Требования к UI экрана «Добавить»:**
1. **Полноразмерная карточка приветствия:**
   - Компонент `Header`:
     - Растянут на всю ширину: `Modifier.fillMaxWidth()`.
     - Скругление углов: 16dp (`RoundedCornerShape(16.dp)`).
     - Контейнер карточки с фоном `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`.
     - Слева: круглый аватар 44dp с первой буквой имени пользователя (`primaryContainer`, жирный текст `primary`).
     - В центре: колонка «Здравствуйте,» мелким шрифтом `onSurfaceVariant` и имя пользователя крупным полужирным шрифтом `titleMedium`.
     - Справа: стрелочка вниз `Icons.Filled.ArrowDropDown` (26dp, цвет `primary`).
     - Клик по всей площади карточки вызывает `appViewModel.openMenu(MenuScreen.USERS)`.
2. **Очистка карточки действий:**
   - Удалить заголовок `Text("Добавить чек", style = MaterialTheme.typography.titleLarge, ...)` и `Spacer(Modifier.height(16.dp))`.
   - 5 кнопок сканирования и добавления начинаются сразу вверху карточки с гармоничным внутренним отступом `padding(16.dp)`.
3. **Удаление локальной шторки пользователей:**
   - Убрать `var showUserManagementSheet` и локальный вызов `UserManagementBottomSheet`, так как клик теперь делегируется в `appViewModel.openMenu(MenuScreen.USERS)`.

**Step 1: Редактирование `ScanScreen.kt`**
Применить изменения в `ScanScreen.kt`.

**Step 2: Проверка компиляции и тестов**
Запустить: `./gradlew testDebugUnitTest`.

---

### Task 7: Обогащение шторки «О приложении» (`AboutAppContent`)

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/AboutAppBottomSheet.kt`

**Требования к UI и функционалу:**
1. **Официальный логотип:**
   - Заменить иконку `Icons.Outlined.QrCodeScanner` на реальный логотип приложения:
     ```kotlin
     Image(
         painter = painterResource(id = R.drawable.app_logo),
         contentDescription = "Логотип приложения",
         modifier = Modifier
             .size(72.dp)
             .clip(RoundedCornerShape(16.dp))
     )
     ```
2. **Название и версия:**
   - Заголовок: «Скан чеков Узбекистана».
   - Бейдж: «Версия $versionName».
   - Описание: «Учёт и подготовка фискальных чеков для возврата НДС (QQS) в Республике Узбекистан».
3. **Прямая кнопка «Страница в RuStore»:**
   - Кнопка с иконкой `Icons.Outlined.Shop` или `Icons.Outlined.OpenInBrowser`, открывающая прямую ссылку: `https://www.rustore.ru/catalog/app/com.example.uzb_qqs_for_dip` через `uriHandler.openUri(RUSTORE_APP_URL)`.
4. **Кнопка «Поделиться приложением»:**
   - Отправка `ACTION_SEND` с текстом:
     `Скан чеков Узбекистана — учёт и подготовка фискальных чеков для возврата НДС:\nhttps://www.rustore.ru/catalog/app/com.example.uzb_qqs_for_dip`.
5. **Кнопка «Политика конфиденциальности»:**
   - Открытие `PrivacyPolicyDialog`.
6. **Блок «Обратная связь»:**
   - Контактные карточки Telegram (`@dis_night`) и Email (`i@dis-night.ru`).

**Step 1: Редактирование `AboutAppBottomSheet.kt`**
Обновить `AboutAppContent` в соответствии со спецификацией.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`.

---

### Task 8: Полная актуализация справки (`HelpContent`): иконки для всех пунктов, виджет и авто-открытие ссылок

**Files:**
- Modify: `app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpBottomSheet.kt`

**Требования к UI и содержанию справки:**
1. **Единый компонент элемента справки с иконкой:**
   Создать переиспользуемый composable:
   ```kotlin
   @Composable
   fun HelpFeatureItem(
       icon: ImageVector,
       title: String,
       description: String,
       iconTint: Color = MaterialTheme.colorScheme.primary,
       modifier: Modifier = Modifier
   )
   ```
   Каждый пункт отображает иконку в скругленном квадрате 36x36dp (`iconTint.copy(alpha = 0.12f)`), заголовок полужирным шрифтом и детальное описание.
2. **Иконки для ВСЕХ пунктов справки:**
   - Все текстовые списки во всех аккордеонах перевести на `HelpFeatureItem`.
3. **Новый раздел: «Виджет быстрого сканирования»:**
   - Иконка `Icons.Outlined.Widgets`.
   - Добавление виджета на домашний экран (долгое нажатие на рабочий стол Android -> Меню «Виджеты» -> «Скан чеков Узбекистана»).
   - Сканирование чека в одно касание: запуск камеры мгновенно с рабочего стола без открытия основного окна приложения.
   - Автоматическая привязка чека к текущему активному сотруднику.
4. **Новый раздел: «Автоматическое открытие ссылок на чеки»:**
   - Иконка `Icons.Outlined.Link` или `Icons.Outlined.OpenInBrowser`.
   - Инструкция по настройке в Android: «Настройки» -> «Приложения» -> «Скан чеков Узбекистана» -> «Открывать по умолчанию» -> «Добавить ссылки» -> отметить `ofd.soliq.uz`, `my.soliq.uz`, `ofd.multicard.uz`.
   - Мгновенный импорт: клик по ссылке на чек в SMS, Telegram или браузере сразу открывает приложение и загружает фискальные данные чека.
   - Системное меню «Поделиться» (Share): отправка текста или ссылки из любого приложения в «Скан чеков Узбекистана».
5. **Актуализация разделов:**
   - «Навигация и Главное меню» (иконки для вкладок, аватара, меню тем, ролей).
   - «Управление профилями» (иконки для входов, переключения, ✏️, 🗑️, + создания).
   - «Добавление чеков» (5 способов добавления).
   - «Чеки и отчёт» (Сводная шапка, Выбор квартала и года, Режимы экспорта, Sticky Header, Превью чеков).
   - «Резервная копия базы данных» (JSON, Создание, Слияние, Полная замена, Отправка).
   - «Аудит (Режим аудитора)» (Квартальная сверка, Контроль расхождений, Почековая верификация, Экспорт отчётов и ZIP).
   - «Что нужно печатать в бухгалтерию» (Комплект «Всё», Сводный отчёт, Реестр чеков).
   - «Контакты разработчика» (Telegram, Email).

**Step 1: Редактирование `HelpBottomSheet.kt`**
Применить обновленную структуру и новые разделы справки.

**Step 2: Проверка сборки**
Запустить: `./gradlew testDebugUnitTest`.

---

### Task 9: Unit-тесты для менеджера тем и иерархической навигации меню

**Files:**
- Create: `app/src/test/java/com/example/uzb_qqs_for_dip/data/ThemeManagerTest.kt`
- Create: `app/src/test/java/com/example/uzb_qqs_for_dip/ui/MenuNavigationTest.kt`

**Step 1: Написание теста `ThemeManagerTest.kt`**
Проверить:
1. Загрузка темы по умолчанию (`SYSTEM`).
2. Переключение на `LIGHT` и `DARK` с сохранением и обновлением `themeMode.value`.
3. Корректное чтение сохраненного значения из SharedPreferences.

**Step 2: Написание теста `MenuNavigationTest.kt`**
Проверить:
1. По умолчанию `menuInitialScreen` равен `null`.
2. Вызов `openMenu(MenuScreen.USERS)` выставляет `menuInitialScreen.value == MenuScreen.USERS`.
3. Вызов `closeMenu()` сбрасывает `menuInitialScreen` в `null`.
4. Переключение тем через `appViewModel.setThemeMode(...)`.

**Step 3: Запуск всех unit-тестов**
Запустить: `./gradlew testDebugUnitTest`.
Ожидаемый результат: `BUILD SUCCESSFUL`, все тесты пройдены.

---

### Task 10: Финальная проверка, сборка и автосинхронизация архитектурной карты

**Files:**
- Modify: `SYSTEM_PROMPT.md`, `.cursorrules` (через скрипт)

**Step 1: Полный прогон тестов**
Выполнить команду:
```powershell
./gradlew testDebugUnitTest
```
Ожидаемый результат: все тесты проходят без ошибок (`BUILD SUCCESSFUL`).

**Step 2: Автосинхронизация карты архитектуры**
Выполнить команду:
```powershell
python scripts/update_architecture_map.py
```
Скрипт просканирует добавленные и модифицированные файлы (`ThemeManager.kt`, `ThemeManagerTest.kt`, `MenuNavigationTest.kt`) и обновит секцию `<architecture_map>` в `SYSTEM_PROMPT.md` и `.cursorrules`.

**Step 3: Проверка статуса Git и кодировок**
Выполнить `git status` и убедиться, что:
- Нет нежелательных временных файлов.
- Все кириллические строки сохранены в кодировке `UTF-8` без повреждений (mojibake).

---

## Варианты выполнения (Execution Handoff)

План составлен, полностью детализирован и сохранен в `IMPLEMENTATION_PLAN.md`. Доступны следующие варианты исполнения:

1. **Subagent-Driven (в текущей сессии):**
   - Оркестратор запускает субагента-Дизайнера (`Designer`) для подтверждения визуальных решений (цвета ролей, сегментированный переключатель тем, верстка приветствия).
   - Затем субагент-Исполнитель (`Doer`) последовательно реализует задачи 1–10.
   - Тестировщик (`TestEngineer`) и Ревьюер (`Reviewer`) верифицируют результат и запускают автосинхронизацию карты архитектуры.
2. **Пошаговое подтверждение пользователем:**
   - Утверждение каждого блока изменений перед запуском реализации.
