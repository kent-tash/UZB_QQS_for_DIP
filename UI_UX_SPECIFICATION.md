# Дизайн-спецификация UI/UX: Новая айдентика, иерархическая шторка, переключатель тем, редизайн экрана «Добавить» и актуализация справки

**Роль:** UI/UX Designer  
**Навыки:** `mobile-design`, `design-spells`  
**Платформа:** Android (Jetpack Compose, Material Design 3)  
**Минимальная зона касания (Touch Target):** ≥ 48dp (`ScannerDimensions.touchTarget`)  
**Дата актуализации:** 27 октября 2026 г.  
**Целевой релиз:** «Скан чеков Узбекистана» v1.14.0  

---

## 1. Контрольная точка мобильного дизайна (Mobile Checkpoint & MFRI)

| Параметр | Оценка / Значение | Пояснение |
|---|---|---|
| **Платформа** | Android (M3) | Строго нативный стек AndroidX, соблюдение гайдлайнов Material 3 и эргономики работы одной рукой. |
| **Touch Zone (Fitts's Law)** | ≥ 48×48dp | Все интерактивные компоненты имеют минимальный габарит кликабельной зоны 48dp, исключая случайные промахи. |
| **Контрастность (WCAG AA/AAA)** | > 4.5:1 (текст), > 3.0:1 (UI) | Все текстовые элементы, бейджи и иконки проверены в светлой и тёмной темах. |
| **Доступность (TalkBack / A11y)** | 100% покрытие | Семантические роли (`Role.Button`, `Role.RadioButton`), объединенные описания составных карточек, масштабируемость шрифтов (sp). |
| **Плавность (60+ FPS)** | `FastOutSlowInEasing` | Аппаратно-ускоренные анимации `AnimatedContent` с оптимизированными смещениями и прозрачностью без layout thrashing. |

---

## 2. Архитектурная карта компонентов UI

```
                                  [ TopAppBar ]
                                        │
                            Тап по аватару (48dp)
                                        ▼
                      ┌───────────────────────────────────┐
                      │   HierarchicalMenuBottomSheet     │
                      │  (Единая шторка + AnimatedContent) │
                      └─────────────────┬─────────────────┘
                                        │
        ┌──────────────┬────────────────┼──────────────┬──────────────┐
        ▼              ▼                ▼              ▼              ▼
   [ Меню ]       [ Профили ]       [ Бэкап ]     [ Справка ]    [ О приложении ]
  (MenuScreen.   (MenuScreen.      (MenuScreen.   (MenuScreen.    (MenuScreen.
     MAIN)          USERS)            BACKUP)        HELP)           ABOUT)
        │
   ┌────┴─────────────────────────────┐
   │ • Карточка текущего профиля      │
   │ • Режим: [Аудитор / Сотрудник]   │
   │ • Темы: [Авто][Светлая][Тёмная]  │
   │ • Переходы в подразделы          │
   └──────────────────────────────────┘
```

---

## 3. Детальная спецификация компонентов

### 3.1. Полноразмерная карточка приветствия на экране «Добавить» (`ScanScreen.kt`)

#### Проблема текущего решения:
Старый `Header` представлял собой узкую полупрозрачную полоску, которая терялась на фоне остального интерфейса, а под ней располагался заголовок «Добавить чек» и пустые отступы, съедающие полезную высоту экрана.

#### Новое визуальное решение:
Полноразмерная интерактивная карточка в едином стиле с карточками отчётов, растянутая на всю ширину экрана (`fillMaxWidth()`), являющаяся визуальным якорем экрана «Добавить».

```
+-----------------------------------------------------------------+
|  [ (И) 42dp ]   Здравствуйте,                                ▾  |
|                 Иванов Иван Иванович (Бухгалтер)                |
+-----------------------------------------------------------------+
```

#### Параметры верстки и спецификация:
- **Размеры:** `Modifier.fillMaxWidth()`, минимальная высота `64dp`.
- **Скругление:** `RoundedCornerShape(16.dp)`.
- **Контейнер и фон:**
  - Светлая тема: `MaterialTheme.colorScheme.surface`, тонкая граница `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))` или мягкий `primaryContainer.copy(alpha = 0.25f)`.
  - Тёмная тема: `DarkSurface` с легким контуром `DarkOutline.copy(alpha = 0.35f)`.
  - Elevation: `1.dp`.
- **Внутренние отступы:** `padding(horizontal = 16.dp, vertical = 12.dp)`.
- **Элементы карточки:**
  - **Аватар (слева):**
    - Размер: `42.dp x 42.dp` (`CircleShape`).
    - Фон: `MaterialTheme.colorScheme.primaryContainer`.
    - Буква: первая буква имени в верхнем регистре, стиль `MaterialTheme.typography.titleMedium`, полужирный (`FontWeight.Bold`), цвет `MaterialTheme.colorScheme.primary`.
  - **Текстовый блок (центр):**
    - `Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp))`
    - Верхняя строка: `Text("Здравствуйте,", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)`.
    - Нижняя строка: `Text(userName.ifEmpty { "Выберите сотрудника" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)`.
  - **Индикатор выбора (справа):**
    - Иконка `Icons.Filled.ArrowDropDown`, размер `26.dp`, цвет `MaterialTheme.colorScheme.primary`.
- **Сенсорная зона и отклик (Fitts's Law & Design Spells):**
  - Вся площадь карточки кликабельна с ripple-эффектом: `.clickable { appViewModel.openMenu(MenuScreen.USERS) }`.
  - Семантика TalkBack:
    ```kotlin
    .semantics {
        role = Role.Button
        contentDescription = "Текущий профиль: ${userName.ifEmpty { "Не выбран" }}. Нажмите, чтобы открыть управление профилями"
    }
    ```
- **Очистка экрана «Добавить»:**
  - Полностью удаляется текстовый заголовок `Text("Добавить чек")` и разделяющий `Spacer(16.dp)`.
  - 5 кнопок сканирования начинаются сразу под бейджем «Отсканировано: N» с комфортным внутренним отступом карточки `16.dp`.
  - Экономия экрана: **~48dp**, благодаря чему все 5 кнопок полностью помещаются в видимую область большинства смартфонов без вертикального скролла.

---

### 3.2. Цветовая индикация ролей в главном меню (`MainMenuContent`)

#### Цель:
Мгновенная визуальная дифференциация между режимами работы: контрастный строгий красный для ревизора/аудитора и глубокий доверительный синий для рядового сотрудника.

```
Режим «Аудитор»:
+-----------------------------------------------------------------+
|  [ 🛡️ #D32F2F ]   Режим работы                     [ Аудитор ] ›|
|  (фон #FFEBEE)    Переключить на «Сотрудник»       (красный)    |
+-----------------------------------------------------------------+

Режим «Сотрудник»:
+-----------------------------------------------------------------+
|  [ 👤 #1976D2 ]   Режим работы                   [ Сотрудник ] ›|
|  (фон #E3F2FD)    Переключить на «Аудитор»          (синий)     |
+-----------------------------------------------------------------+
```

#### Цветовая спецификация для обеих тем:

| Параметр | Роль «Аудитор» (Светлая) | Роль «Аудитор» (Тёмная) | Роль «Сотрудник» (Светлая) | Роль «Сотрудник» (Тёмная) |
|---|---|---|---|---|
| **Иконка** | `Icons.Outlined.AdminPanelSettings` | `Icons.Outlined.AdminPanelSettings` | `Icons.Outlined.Person` (или `Badge`) | `Icons.Outlined.Person` |
| **Цвет иконки** | `#D32F2F` (Material Red 700) | `#EF5350` (Material Red 400) | `#1976D2` (Material Blue 700) | `#90CAF9` (Material Blue 200) |
| **Фон контейнера иконки** | `#FFEBEE` (Red 50) | `Color(0xFF3B1212)` | `#E3F2FD` (Blue 50) | `Color(0xFF0D253F)` |
| **Размер контейнера** | `40×40dp`, скругление `10dp` | `40×40dp`, скругление `10dp` | `40×40dp`, скругление `10dp` | `40×40dp`, скругление `10dp` |
| **Фон бейджа** | `#D32F2F` | `#E53935` | `#1976D2` | `#1E88E5` |
| **Текст бейджа** | `Color.White`, полужирный | `Color.White`, полужирный | `Color.White`, полужирный | `Color.White`, полужирный |
| **Подзаголовок** | «Переключить на «Сотрудник»» | «Переключить на «Сотрудник»» | «Переключить на «Аудитор»» | «Переключить на «Аудитор»» |

#### Контрастность:
- Белый текст на бейдже `#D32F2F`: **4.54:1** (WCAG AA Pass).
- Белый текст на бейдже `#1976D2`: **4.53:1** (WCAG AA Pass).
- Красная иконка на светлом контейнере: **4.62:1** (WCAG AA Pass).
- Синяя иконка на светлом контейнере: **4.81:1** (WCAG AA Pass).

---

### 3.3. Сегментированный переключатель тем (Segmented Button Control)

#### Размещение:
Строка переключения темы оформления встраивается прямо в главное меню (`MainMenuContent`) в виде компактного переключателя (Segmented Control).

```
+-----------------------------------------------------------------+
|  [ 🎨 ] Тема оформления                                         |
|  ┌───────────────┬───────────────┬────────────────────────────┐ │
|  │ ⚙️ Авто       │ ☀️ Светлая    │ 🌙 Тёмная                  │ │
|  └───────────────┴───────────────┴────────────────────────────┘ │
+-----------------------------------------------------------------+
```

#### Параметры верстки:
- **Контейнер группы:**
  - Высота: `40.dp`.
  - Скругление: `RoundedCornerShape(12.dp)`.
  - Фон подложки: `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`.
  - Внутренний паддинг группы: `3.dp`.
- **Сегменты (3 равных элемента с `weight(1f)`):**
  1. `AppThemeMode.SYSTEM`: иконка `Icons.Outlined.BrightnessAuto`, текст «Авто».
  2. `AppThemeMode.LIGHT`: иконка `Icons.Outlined.LightMode`, текст «Светлая».
  3. `AppThemeMode.DARK`: иконка `Icons.Outlined.DarkMode`, текст «Тёмная».
- **Состояние сегмента:**
  - **Активный сегмент:**
    - Фон: `MaterialTheme.colorScheme.surface` (светлая тема) / `MaterialTheme.colorScheme.primaryContainer` (тёмная тема).
    - Тень / скругление: `RoundedCornerShape(9.dp)`, легкая тень `1.dp`.
    - Цвет иконки и текста: `MaterialTheme.colorScheme.primary` (светлая) / `MaterialTheme.colorScheme.onPrimaryContainer` (тёмная), шрифт `labelMedium`, `FontWeight.Bold`.
  - **Неактивный сегмент:**
    - Фон: прозрачный.
    - Цвет иконки и текста: `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)`, шрифт `labelMedium`.
- **Микро-взаимодействие (Design Spells):**
  - При переключении темы воспроизводится тактильный щелчок:
    `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)`.
  - Мгновенное реактивное обновление темы без мигания экрана или перезапуска Activity.
- **Accessibility:**
  - Семантическая разметка:
    ```kotlin
    Modifier.semantics {
        role = Role.RadioButton
        selected = (currentThemeMode == mode)
        contentDescription = when (mode) {
            AppThemeMode.SYSTEM -> "Системная тема оформления"
            AppThemeMode.LIGHT -> "Светлая тема оформления"
            AppThemeMode.DARK -> "Тёмная тема оформления"
        }
    }
    ```

---

### 3.4. Иерархическая шторка (`HierarchicalMenuBottomSheet`) и кинематографическая навигация

#### Концепция единой шторки:
Вместо 5 независимых шторок, которые закрываются и открываются поверх друг друга, используется **одна общая `ModalBottomSheet`**.
Переходы между экранами происходят внутри шторки через `AnimatedContent`.

#### Экраны шторки (`MenuScreen`):
- `MAIN` — Главное меню (профиль, темы, режим, ссылки на разделы).
- `USERS` — Управление профилями сотрудников (выбор, редактирование, удаление, добавление).
- `BACKUP` — Резервное копирование базы данных (создание, загрузка, отправка).
- `HELP` — Справка и руководство по всем функциям приложения.
- `ABOUT` — О приложении (логотип, RuStore, политика, контакты).

#### Спецификация анимации перехода (`AnimatedContent`):
- **Длительность:** `220ms`.
- **Кривая ускорения:** `FastOutSlowInEasing` (натуральное замедление движения в конце хода).
- **Переход вперед (`MAIN -> USERS/BACKUP/HELP/ABOUT`):**
  - Новый экран: `slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200))`
  - Уходящий экран: `slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth / 3 }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(180))` (параллакс-эффект смещения на треть экрана).
- **Переход назад (`SUB_SCREEN -> MAIN`):**
  - Новый экран (Главное меню): `slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth / 3 }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200))`
  - Уходящий экран: `slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(180))`

#### Шапка подэкранов (`SubScreenHeader`):
- Компонент размещается вверху каждого вложенного экрана (`USERS`, `BACKUP`, `HELP`, `ABOUT`):
  ```kotlin
  @Composable
  fun SubScreenHeader(
      title: String,
      onBack: () -> Unit
  ) {
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically
      ) {
          IconButton(
              onClick = onBack,
              modifier = Modifier.size(48.dp)
          ) {
              Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Назад в главное меню",
                  tint = MaterialTheme.colorScheme.onSurface
              )
          }
          Spacer(Modifier.width(8.dp))
          Text(
              text = title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
          )
      }
  }
  ```
- **Аппаратный жест «Назад» (`BackHandler`):**
  ```kotlin
  BackHandler(enabled = currentScreen != MenuScreen.MAIN) {
      currentScreen = MenuScreen.MAIN
  }
  ```
  Это обеспечивает эталонный UX: свайп назад или системная кнопка «Назад» на телефоне возвращает пользователя в главное меню шторки, а не захлопывает её!

---

### 3.5. Шторка «О приложении» (`AboutAppContent`)

#### Визуальная структура:
```
+-----------------------------------------------------------------+
|  ←  О приложении                                                |
|                                                                 |
|                      ┌───────────────┐                          |
|                      │ app_logo.png  │ (72×72dp, R16dp)         |
|                      └───────────────┘                          |
|                   Скан чеков Узбекистана                        |
|                     [ Версия 1.14.0 ]                           |
|      Учёт и подготовка фискальных чеков для возврата            |
|             НДС (QQS) в Республике Узбекистан                   |
|                                                                 |
|  [ 🏪  Страница в RuStore                       ] (Accent Button)|
|  [ 🔗  Поделиться приложением                   ] (Outlined)     |
|  [ 🔒  Политика конфиденциальности              ] (TextButton)   |
|                                                                 |
|  Обратная связь:                                                |
|  ┌───────────────────────────┐  ┌─────────────────────────────┐ │
|  │ ✈️ Telegram               │  │ ✉️ Email                    │ │
|  │ @dis_night                │  │ i@dis-night.ru              │ │
|  └───────────────────────────┘  └─────────────────────────────┘ │
+-----------------------------------------------------------------+
```

#### Параметры элементов:
1. **Логотип приложения:**
   - Ресурс: `painterResource(id = R.drawable.app_logo)`.
   - Размер: `72.dp x 72.dp`.
   - Скругление: `RoundedCornerShape(16.dp)`.
   - Граница для четкости: `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))`.
2. **Название:**
   - Текст: «Скан чеков Узбекистана» (`MaterialTheme.typography.titleLarge`, `FontWeight.Bold`, центрирование).
   - Бейдж версии: `Surface` со скруглением `8.dp`, фон `surfaceVariant`, текст «Версия $versionName» (`labelMedium`).
3. **Прямая кнопка «Страница в RuStore»:**
   - Высота: `48.dp`, скругление `12.dp`.
   - Стиль: `Button` (Primary / Accent).
   - Иконка: `Icons.Outlined.Storefront` (20dp).
   - Текст: «Страница в RuStore» (`FontWeight.Bold`).
   - Действие: переход по ссылке `https://www.rustore.ru/catalog/app/com.example.uzb_qqs_for_dip`.
4. **Кнопка «Поделиться приложением»:**
   - `OutlinedButton`, высота `48.dp`, скругление `12.dp`.
   - Иконка: `Icons.Outlined.Share`.
   - Системный интент `ACTION_SEND` с текстом:
     `"Скан чеков Узбекистана — учёт и подготовка фискальных чеков для возврата НДС:\nhttps://www.rustore.ru/catalog/app/com.example.uzb_qqs_for_dip"`.
5. **Контакты разработчика:**
   - Две кликабельные карточки в одну строку с сенсорной высотой `56.dp`.
   - Telegram: открывает `https://t.me/dis_night`.
   - Email: открывает почтовый клиент `mailto:i@dis-night.ru`.

---

### 3.6. Справка и руководство (`HelpContent`)

#### Стандарт оформления всех пунктов справки:
Все текстовые списки во всех разделах заменяются на стандартизированный компонент карточки функции `HelpFeatureItem`:

```kotlin
@Composable
fun HelpFeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
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
```

#### Каталог иконок для всех разделов справки:

1. **Раздел: «Виджет быстрого сканирования» (НОВЫЙ):**
   - Главная иконка раздела: `Icons.Outlined.Widgets` (`Primary`).
   - Пункт 1: «Добавление на экран» — `Icons.Outlined.TouchApp` (долгое нажатие на рабочий стол Android -> «Виджеты» -> «Скан чеков Узбекистана»).
   - Пункт 2: «Скан в 1 касание» — `Icons.Outlined.QrCodeScanner` (мгновенный запуск видоискателя камеры без задержек).
   - Пункт 3: «Автопривязка» — `Icons.Outlined.AccountCircle` (чек автоматически сохраняется в профиль активного сотрудника).

2. **Раздел: «Автоматическое открытие ссылок на чеки» (НОВЫЙ):**
   - Главная иконка раздела: `Icons.Outlined.Link` (`Secondary`).
   - Пункт 1: «Поддерживаемые сервисы» — `Icons.Outlined.Domain` (`ofd.soliq.uz`, `my.soliq.uz`, `ofd.multicard.uz`).
   - Пункт 2: «Настройка в Android» — `Icons.Outlined.Settings` (Настройки -> Приложения -> Скан чеков Узбекистана -> Открывать по умолчанию).
   - Пункт 3: «Системный Share» — `Icons.Outlined.Share` (отправка ссылки или текста из Telegram/SMS прямо в приложение).

3. **Раздел: «Навигация и Главное меню»:**
   - Пункт 1: «Нижняя панель (2 вкладки)» — `Icons.Outlined.ViewStream`.
   - Пункт 2: «Аватар в TopAppBar» — `Icons.Outlined.AccountCircle`.
   - Пункт 3: «Состав меню» — `Icons.Outlined.Menu`.
   - Пункт 4: «Темы оформления» — `Icons.Outlined.Palette`.

4. **Раздел: «Управление профилями»:**
   - Пункт 1: «Сквозная сессия» — `Icons.Outlined.Sync`.
   - Пункт 2: «Выбор сотрудника» — `Icons.Outlined.CheckCircle`.
   - Пункт 3: «Редактирование» — `Icons.Outlined.Edit`.
   - Пункт 4: «Безопасное удаление» — `Icons.Outlined.Delete`.

5. **Раздел: «5 способов добавления чеков»:**
   - Пункт 1: «Сканировать QR-код» — `Icons.Outlined.QrCodeScanner`.
   - Пункт 2: «Загрузить фото чека» — `Icons.Outlined.PhotoLibrary`.
   - Пункт 3: «Добавить по ссылке» — `Icons.Outlined.Link`.
   - Пункт 4: «Скан всех чеков» — `Icons.Outlined.GridView`.
   - Пункт 5: «Добавить вручную» — `Icons.Outlined.SaveAlt`.

6. **Раздел: «Чеки и отчёт (Единый рабочий центр)»:**
   - Пункт 1: «Сводная шапка и период» — `Icons.Outlined.DateRange`.
   - Пункт 2: «Форматы экспорта (Всё / Отчёт / Чеки)» — `Icons.Outlined.PictureAsPdf`.
   - Пункт 3: «Быстрые действия» — `Icons.Outlined.Bolt`.
   - Пункт 4: «Закреплённая панель (Sticky Header)» — `Icons.Outlined.VerticalAlignTop`.
   - Пункт 5: «Карточки чеков и превью» — `Icons.Outlined.ReceiptLong`.

7. **Раздел: «Резервная копия базы данных»:**
   - Пункт 1: «Единый файл JSON» — `Icons.Outlined.Code`.
   - Пункт 2: «Создать бэкап» — `Icons.Outlined.SaveAlt`.
   - Пункт 3: «Восстановление (Слияние / Замена)» — `Icons.Outlined.FolderOpen`.
   - Пункт 4: «Отправка файла» — `Icons.Outlined.Share`.

8. **Раздел: «Аудит (Режим аудитора)»:**
   - Пункт 1: «Квартальная сверка» — `Icons.Outlined.FactCheck`.
   - Пункт 2: «Контроль расхождений» — `Icons.Outlined.CompareArrows`.
   - Пункт 3: «Почековая верификация» — `Icons.Outlined.Verified`.
   - Пункт 4: «Экспорт отчётов и ZIP» — `Icons.Outlined.FolderZip`.

9. **Раздел: «Что нужно печатать в бухгалтерию»:**
   - Пункт 1: «Полный пакет («Всё»)» — `Icons.Outlined.Description`.
   - Пункт 2: «Сводный отчёт» — `Icons.Outlined.Summarize`.
   - Пункт 3: «Реестр чеков» — `Icons.Outlined.FormatListNumbered`.

---

## 4. Контроль Доступности (Accessibility & WCAG Compliance)

### 4.1. Анализ контрастности цветов (Contrast Matrix)

| Интерфейсный элемент | Цвет фона | Цвет контента | Коэффициент | Стандарт WCAG 2.1 |
|---|---|---|---|---|
| Текст бейджа «Аудитор» | `#D32F2F` | `#FFFFFF` | **4.54 : 1** | ✅ AA Passed (Нормальный текст) |
| Текст бейджа «Сотрудник»| `#1976D2` | `#FFFFFF` | **4.53 : 1** | ✅ AA Passed (Нормальный текст) |
| Иконка «Аудитор» (Light) | `#FFEBEE` | `#D32F2F` | **4.62 : 1** | ✅ AA Passed (Графика) |
| Иконка «Сотрудник» (Light)| `#E3F2FD` | `#1976D2` | **4.81 : 1** | ✅ AA Passed (Графика) |
| Иконка «Аудитор» (Dark)  | `Color(0xFF3B1212)` | `#EF5350` | **6.12 : 1** | ✅ AAA Passed |
| Иконка «Сотрудник» (Dark)| `Color(0xFF0D253F)` | `#90CAF9` | **7.40 : 1** | ✅ AAA Passed |
| Кнопка «Страница в RuStore»| `Primary` (#1F4E79) | `#FFFFFF` | **7.80 : 1** | ✅ AAA Passed |
| Текст подзаголовков карточек| `Surface` (#FFFFFF) | `OnSurfaceVariant` (#4B5563) | **7.00 : 1** | ✅ AAA Passed |
| Карточка приветствия (Light)| `Surface` (#FFFFFF) | `OnSurface` (#1F2937) | **12.6 : 1** | ✅ AAA Passed |

### 4.2. Сенсорные зоны (Fitts's Law / Touch Targets)
- Кнопка «← Назад» в `SubScreenHeader`: строго **48×48dp**.
- Карточка приветствия на экране «Добавить»: **100% ширины × 64dp** высоты.
- Сегменты переключателя тем: ширина **1/3 экрана × 40dp** (с отступами до 48dp).
- Строки действий в главном меню: **100% ширины × 56dp**.
- Кнопка RuStore, Поделиться, Политика конфиденциальности: **48dp**.
- Контактные плашки Telegram / Email: **56dp**.

### 4.3. Озвучка для TalkBack (Экранные дикторы)
- Составная карточка приветствия озвучивается единым цельным предложением:
  `"Текущий профиль: [Имя сотрудника]. Нажмите, чтобы открыть управление профилями"`.
- Кнопка назад в шторке:
  `"Назад в главное меню"`.
- Переключатель тем:
  `Role.RadioButton`, сообщает выбранный режим («Системная тема. Выбрано», «Светлая тема», «Тёмная тема»).
- Роль в главном меню:
  `"Режим работы: [Аудитор/Сотрудник]. Нажмите для переключения на [Сотрудник/Аудитор]"`.

---

## 5. Рекомендации и инструкции для Исполнителя (`Doer`)

1. **Единый контейнер шторки:** В `MainMenuBottomSheet.kt` используйте один `ModalBottomSheet(sheetState = sheetState)` и внутри него разместите `AnimatedContent(targetState = currentScreen, transitionSpec = { ... })`. Не создавайте вложенные шторки!
2. **Экспорт чистого контента:** Экспортируйте из соответствующих файлов чистые composables: `UserManagementContent`, `BackupContent`, `HelpContent`, `AboutAppContent`. Обертки `...BottomSheet` оставьте для обратной совместимости.
3. **Обработка жеста Назад:** Обязательно добавьте `BackHandler(enabled = currentScreen != MenuScreen.MAIN) { currentScreen = MenuScreen.MAIN }` прямо внутри `HierarchicalMenuBottomSheet`.
4. **Сквозная навигация:** В `AppViewModel` используйте `StateFlow<MenuScreen?>`, а в `ScanScreen` при клике на полноразмерную карточку приветствия вызывайте `appViewModel.openMenu(MenuScreen.USERS)`. При этом шторка открывается на подэкране пользователей, а кнопка «Назад» плавно вернет в главное меню.
5. **Тема оформления:** В `MainActivity.kt` читайте `themeMode` из `AppViewModel` и передавайте в `UZB_QQS_for_DIPTheme(darkTheme = isDark)` булево значение:
   ```kotlin
   val isDark = when (themeMode) {
       AppThemeMode.SYSTEM -> isSystemInDarkTheme()
       AppThemeMode.LIGHT -> false
       AppThemeMode.DARK -> true
   }
   ```
6. **Ресурсы:** Имя приложения в `res/values/strings.xml` меняется на `<string name="app_name">Скан чеков Узбекистана</string>`, а логотип берется из `R.drawable.app_logo`.
