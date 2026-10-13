# Архитектурный план: Обработка чеков с нулевым НДС (Zero VAT Architecture)

## Контекст и цели
В налоговом законодательстве Республики Узбекистан к возмещению принимаются только чеки с ненулевым налогом на добавленную стоимость (НДС / QQS). Чеки без НДС (ставка 0% или освобождённые от НДС) не должны попадать в официальные реестры и расчёты сумм возмещения, но должны быть зафиксированы, отделены от чеков с положительным НДС и контролироваться пользователем и аудитором.

Ключевые цели плана:
1. **Сетевой слой:** Научить парсеры (`OfdPaymentApi`, `ReceiptParser`) различать **явный нулевой НДС (0 тийинов)** от **отсутствия данных (null / сбой разметки / чек ещё не фискализирован в ОФД)**.
2. **Синхронизация с ОФД (`syncWithOfd`):** Отслеживать чеки, у которых в результате синхронизации НДС изменился с `> 0` на `0`, и формировать список обнулившихся чеков.
3. **Уведомление пользователя:** По завершении синхронизации показывать диалог с перечнем обнулившихся чеков и действиями: «Удалить из базы» / «Оставить для просмотра».
4. **Исключение из отчётов и реестров:**
   - Во все выгружаемые файлы (PDF, XLSX, CSV, ZIP) включать **ТОЛЬКО чеки с `vatAmountTiyin > 0`**.
   - Сквозную нумерацию 1..N и карточки для печати (6 на лист) генерировать **ТОЛЬКО для чеков с `vatAmountTiyin > 0`**.
   - В итоговых строках («Итого», «Всего») учитывать только чеки с `vatAmountTiyin > 0`.
5. **Отображение в UI (`ReportScreen`, `ReceiptsScreen`):**
   - Основной список/таблица отображает только чеки с `vatAmountTiyin > 0`.
   - Чеки с `vatAmountTiyin <= 0` за выбранный период отображаются в отдельном сворачиваемом блоке «Чеки с нулевым НДС (N шт.) — исключены из отчёта» с предупреждением и возможностью удаления.
6. **Режим «Аудитор» (`AuditorRepository`, `AuditorViewModel`, `AuditorVerifyViewModel`):**
   - Расчёт сумм и статистики только по чекам с `vatAmountTiyin > 0`.
   - В `EmployeeSummary` и `UserReceiptStats` добавить счётчик чеков с нулевым НДС (`zeroVatCount`).
   - В UI карточек сотрудников и на экране проверки показывать бейдж-предупреждение о наличии таких чеков.

---

## 1. Анализ текущего состояния и архитектурные решения

### 1.1 Сетевой слой: `OfdPaymentApi` и `ReceiptParser`
- **Текущее состояние:**
  - В `OfdPaymentApi`: `data.optDouble("vatTotal", 0.0)` конвертируется через `soumToTiyin(vatSoum)`. При `soum <= 0.0` функция `soumToTiyin` возвращает `null`! В результате и 0.0, и отсутствие ключа дают `null`.
  - В `ReceiptParser`: `firstMoneyMatch` проверяет `if (v > 0) return v`, а если число `0`, возвращает `null`.
- **Новое решение:**
  - `OfdPaymentApi`: 
    - Проверять наличие ключа `"vatTotal"` в `JSONObject` (`data.has("vatTotal")` и `!data.isNull("vatTotal")`).
    - Если ключ есть и значение числовое:
      - При `vatTotal == 0.0` возвращать `0L`.
      - При `vatTotal > 0.0` возвращать `(vatTotal * 100.0).roundToLong()`.
    - Если ключа нет или парсинг не удался — возвращать `null`.
    - Аналогично для поля `totalAmountTiyin`: если `totalSoum == 0.0`, но ключ присутствовал, корректно конвертировать.
  - `ReceiptParser`:
    - Добавить явный поиск нулевого НДС по регулярным выражениям:
      - Метки `НДС: 0`, `НДС (QQS): 0,00`, `QQS summasi: 0`, `Umumiy QQS qiymati: 0.00`, `0.00 so'm`, `0%` и т.д.
    - В `firstMoneyMatchOrZero(text, labels)`: если найдена метка и число равно `0`, возвращать `0L`.
    - Если найдена метка и число `> 0`, возвращать `v`.
    - Если метка не найдена вообще — возвращать `null` (чтобы сохранить старое значение из базы).
  - Свойство `ParsedReceipt.isValid`:
    - Сейчас: `purchasedAt != null && !sellerName.isNullOrBlank() && totalAmountTiyin != null && vatAmountTiyin != null`.
    - При `vatAmountTiyin == 0L` чек валиден (`vatAmountTiyin != null` выполняется!). Поэтому валидность сохраняется.

### 1.2 База данных SQLite (`DbHelper`)
- Поле `vat_amount_tiyin` в таблице `receipts` уже имеет тип `INTEGER NOT NULL`.
- Значение `0L` сохраняется без изменения схемы БД! Миграция схемы не требуется, целостность БД полностью сохраняется.

### 1.3 Синхронизация с ОФД (`syncWithOfd`) и модель результата
- Создать модель данных для отслеживания обнулившегося чека:
  ```kotlin
  data class ZeroVatReceiptInfo(
      val receiptId: Long,
      val sellerName: String,
      val purchasedAt: Long,
      val previousVatTiyin: Long,
      val currentVatTiyin: Long = 0L
  )
  ```
- Метод `syncWithOfd`:
  - Должен возвращать `List<ZeroVatReceiptInfo>` — список чеков, у которых до синхронизации `vatAmountTiyin > 0L`, а после получения ответа от сервера ОФД `parsed.vatAmountTiyin == 0L`.
  - При обновлении чека в БД:
    ```kotlin
    val oldVat = r.vatAmountTiyin
    val newVat = parsed.vatAmountTiyin ?: r.vatAmountTiyin
    if (oldVat > 0L && newVat == 0L) {
        zeroedReceipts.add(ZeroVatReceiptInfo(r.id, r.sellerName, r.purchasedAt, oldVat, 0L))
    }
    ```
- Диалог в UI (`ReceiptsScreen`, `ReportScreen`):
  - При наличии обнулившихся чеков показывать `AlertDialog`:
    - Заголовок: «Обнаружены чеки с нулевым НДС»
    - Список чеков: продавец, дата, прежняя сумма НДС -> 0 сум.
    - Кнопки: «Удалить из базы» (вызывает `deleteAll(ids)`) и «Оставить для просмотра» (просто закрывает диалог).

### 1.4 Исключение из отчётов и реестров
- **`ReportViewModel.rows`**:
  - Разделить чеки:
    - `validRows` (основной список `rows`): `receipt.vatAmountTiyin > 0L`.
    - `zeroVatRows`: `receipt.vatAmountTiyin <= 0L`.
  - Экспортные функции (`savePdfToUri`, `saveXlsxToUri`, `sharePdf`, `shareXlsx`, `generate`) передают в генераторы только чеки с `vatAmountTiyin > 0L`.
- **`ReceiptsViewModel.receipts`**:
  - `receipts` (основной список для генерации листов чеков): фильтровать `receipt.vatAmountTiyin > 0L`.
  - `zeroVatReceipts`: список чеков за выбранный период с `receipt.vatAmountTiyin <= 0L`.
  - `generateReceiptsSheetPdf` берёт только `receipts` (где `vatAmountTiyin > 0L`). Сквозная нумерация 1..N карточек идёт только по ним!
- **Генераторы отчётов (`PdfReportGenerator`, `XlsxExporter`, `CsvExporter`, `ReceiptsSheetPdfGenerator`, `ZipExporter`):**
  - Гарантировать защитную фильтрацию на входе каждого генератора:
    `val validRows = params.rows.filter { it.receipt.vatAmountTiyin > 0 }`
  - Нумерация 1..N, карточки (6 на лист), строки «Итого:» рассчитываются исключительно по чекам с `vatAmountTiyin > 0`.
  - В `ZipExporter` (вызываемом из `AuditorViewModel`): для каждого сотрудника брать чеки с `vatAmountTiyin > 0`.

### 1.5 Отображение в UI
- **`ReportScreen`**:
  - Основная таблица `ReportTablePreview` отображает только чеки с `vatAmountTiyin > 0`.
  - Строка «Итого:» суммирует только чеки с `vatAmountTiyin > 0`.
  - Внизу таблицы (или перед ней) разместить раскрывающийся блок (`Expandable Card`):
    «Чеки с нулевым НДС (N шт.) — исключены из отчёта»
    с оранжево-красным предупреждением, списком продавцов/сумм и кнопкой «Удалить эти чеки».
- **`ReceiptsScreen`**:
  - Основной список `LazyColumn` показывает карточки чеков с `vatAmountTiyin > 0` с нумерацией 1..N.
  - В отдельной сворачиваемой секции выводятся чеки с нулевым НДС с пометкой «НДС 0 сум — исключён из отчёта и печати» и кнопкой быстрого удаления.

### 1.6 Аудиторский режим (`AuditorRepository`, `AuditorViewModel`, `AuditorVerifyViewModel`)
- **`AuditorRepository.getEmployeeSummaries`**:
  - В SQL-запросе сводных данных:
    - `SUM(r.total_amount_tiyin)` и `SUM(r.vat_amount_tiyin)` агрегировать только по чекам `WHERE r.vat_amount_tiyin > 0`!
    - `receiptCount` — количество чеков с `vat_amount_tiyin > 0`.
    - Добавить `zeroVatCount`: `COUNT(CASE WHEN r.vat_amount_tiyin <= 0 THEN 1 END)`.
  - В `EmployeeSummary`:
    - Добавить поле `val zeroVatCount: Int = 0`.
- **`ReceiptRepository.getUserPeriodStats`**:
  - `totalTiyin`, `vatTiyin`, `totalCount`, `verifiedCount` считать по чекам `WHERE vat_amount_tiyin > 0`.
  - В `UserReceiptStats` добавить поле `val zeroVatCount: Int = 0`.
- **`AuditorScreen`**:
  - На карточке сотрудника `EmployeeCard`: если `summary.zeroVatCount > 0`, отображать яркий бейдж/чип:
    `⚠️ Нулевой НДС: X шт.`
  - В `StatsRow`: общие суммы НДС и оплат рассчитываются без чеков с нулевым НДС.
- **`AuditorVerifyScreen`**:
  - В блоке статистики проверки сотрудника показывать предупреждение:
    «Обнаружено X чеков с нулевым НДС (исключены из возмещения)».
  - В списке найденных чеков сотрудника чеки с 0 НДС помечать бейджем «НДС: 0 сум».

---

## 2. Пошаговый план внедрения (Step-by-Step Implementation Plan)

### Фаза 1: Сетевой слой и парсинг чеков
- [ ] **1.1. Модификация `OfdPaymentApi.kt`**:
  - Изменить `parseResponse`:
    - Проверять наличие поля `"vatTotal"` в `data` через `data.has("vatTotal") && !data.isNull("vatTotal")`.
    - Выделять точный `vatSoum: Double?`: если ключ отсутствует — `null`, если присутствует — `data.optDouble("vatTotal")`.
    - Доработать функцию `soumToTiyin(soum: Double)`:
      - Сделать перегрузку или отдельный метод `vatSoumToTiyin(soum: Double?): Long?`, возвращающий `0L`, если `soum == 0.0`, и `(soum * 100.0).roundToLong()`, если `soum > 0.0`. При `null` или отрицательном значении — возвращать `null`.
  - Покрыть unit-тестами явный `vatTotal: 0.0` и отсутствие ключа `vatTotal`.
- [ ] **1.2. Модификация `ReceiptParser.kt`**:
  - В `extractVat(doc, text)`:
    - Добавить поиск нулевого значения НДС.
    - Реализовать метод `extractVatValue(doc, text): Long?`, который отличает явный ноль (0.00 / 0 / 0% / "0 so'm") от отсутствия метки НДС на странице.
    - Если в HTML обнаружена метка НДС и за ней следует 0 — возвращать `0L`.
    - Если метка не найдена вовсе — возвращать `null`.
  - Обновить `ReceiptParserDebugTest.kt` и добавить тесты на распознавание нулевого НДС.

### Фаза 2: Репозиторий и модуль синхронизации ОФД
- [ ] **2.1. Создание модели `ZeroVatReceiptInfo`**:
  - Создать `data class ZeroVatReceiptInfo` в пакете `com.example.uzb_qqs_for_dip.data.model` или `data.repository`.
- [ ] **2.2. Модификация `ReceiptRepository.kt`**:
  - Обновить метод `getUserPeriodStats(userId, fromMs, toMs)`:
    - Считать чеки с `vat_amount_tiyin > 0` для официальных сумм и количества.
    - Добавить подсчёт `COUNT(CASE WHEN vat_amount_tiyin <= 0 THEN 1 END) AS zero_vat_count`.
    - Добавить `zeroVatCount: Int` в модель `UserReceiptStats`.
  - Добавить методы выборки чеков с нулевым НДС за период:
    - `getZeroVatReceipts(userId: Long?, fromMs: Long, toMs: Long): List<ReceiptWithUser>`.
- [ ] **2.3. Модификация `AuditorRepository.kt`**:
  - Добавить поле `zeroVatCount: Int = 0` в `data class EmployeeSummary`.
  - Обновить SQL-запрос в `getEmployeeSummaries`:
    - `SUM(CASE WHEN r.vat_amount_tiyin > 0 THEN r.total_amount_tiyin ELSE 0 END)`
    - `SUM(CASE WHEN r.vat_amount_tiyin > 0 THEN r.vat_amount_tiyin ELSE 0 END)`
    - `COUNT(CASE WHEN r.vat_amount_tiyin > 0 THEN 1 END)` (для `receiptCount`)
    - `COUNT(CASE WHEN r.vat_amount_tiyin > 0 AND r.verified_at IS NOT NULL THEN 1 END)` (для `verifiedCount`)
    - `COUNT(CASE WHEN r.vat_amount_tiyin <= 0 THEN 1 END)` (для `zeroVatCount`)
  - Обновить вызовы создания `EmployeeSummary`.

### Фаза 3: Логика ViewModel и отслеживание обнуления
- [ ] **3.1. Доработка `ReceiptsViewModel.kt`**:
  - Разделить поток чеков:
    - `receipts`: только чеки с `vatAmountTiyin > 0`.
    - `zeroVatReceipts`: чеки за выбранный период с `vatAmountTiyin <= 0`.
  - Доработать `syncWithOfd`:
    - Отслеживать переход `oldVat > 0L && newVat == 0L`.
    - Сохранять список `zeroedReceipts: List<ZeroVatReceiptInfo>`.
    - Добавить `StateFlow<List<ZeroVatReceiptInfo>?>` или событие для отображения `AlertDialog` в UI.
    - Реализовать метод `deleteZeroVatReceipts(ids: List<Long>)`.
- [ ] **3.2. Доработка `ReportViewModel.kt`**:
  - Разделить поток чеков:
    - `rows`: только чеки с `vatAmountTiyin > 0`.
    - `zeroVatRows`: чеки за выбранный период с `vatAmountTiyin <= 0`.
  - Доработать `syncWithOfd`:
    - Отслеживать обнулившиеся чеки `List<ZeroVatReceiptInfo>`.
    - Добавить событие `ReportEvent.ZeroVatDetected(list: List<ZeroVatReceiptInfo>)`.
    - Добавить метод удаления группы чеков с нулевым НДС.

### Фаза 4: Экспорт и генераторы отчётов
- [ ] **4.1. `PdfReportGenerator.kt`**:
  - Проверить и гарантировать фильтрацию `params.rows.filter { it.receipt.vatAmountTiyin > 0 }`.
  - Строка «Итого:» и нумерация строк 1..N формируются только по чекам с положительным НДС.
- [ ] **4.2. `ReceiptsSheetPdfGenerator.kt`**:
  - Фильтровать `rowsInOrder.filter { it.receipt.vatAmountTiyin > 0 }`.
  - Отрисовывать сетку 6 чеков на лист и сквозную нумерацию 1..N только для чеков с положительным НДС.
- [ ] **4.3. `XlsxExporter.kt`**:
  - В `exportReport` и `export`: фильтровать записи с `vatAmountTiyin > 0`. Итоги и порядковые номера 1..N только по валидным чекам.
- [ ] **4.4. `CsvExporter.kt`**:
  - Фильтровать чеки с `vatAmountTiyin > 0`.
- [ ] **4.5. `SummaryPdfGenerator.kt`, `OrgReportPdfGenerator.kt`, `SummaryTableExporter.kt`**:
  - Убедиться, что передаваемые `EmployeeSummary` содержат очищенные суммы и счётчики.
- [ ] **4.6. `ZipExporter` в `AuditorViewModel.kt`**:
  - При формировании чеков по каждому сотруднику брать только чеки с `vatAmountTiyin > 0`.

### Фаза 5: Пользовательский интерфейс (UI / UX)
- [ ] **5.1. Диалог обнулившихся чеков**:
  - В `ReceiptsScreen` и `ReportScreen` при получении списка обнулившихся чеков показывать `AlertDialog`:
    - Заголовок: «Обнаружены чеки с нулевым НДС»
    - Сообщение с перечнем чеков (Продавец, дата, прежняя сумма НДС -> 0 сум).
    - Кнопки: «Удалить из базы» / «Оставить для просмотра».
- [ ] **5.2. Сворачиваемый блок чеков с нулевым НДС на `ReportScreen`**:
  - Добавить карточку «Чеки с нулевым НДС (N шт.) — исключены из отчёта» с возможностью раскрытия, списком позиций и кнопкой «Удалить».
- [ ] **5.3. Сворачиваемый блок на `ReceiptsScreen`**:
  - Добавить секцию отображения исключённых чеков с нулевым НДС.
- [ ] **5.4. Режим «Аудитор» (`AuditorScreen`, `AuditorVerifyScreen`)**:
  - В `EmployeeCard` добавить предупреждающий бейдж `⚠️ Нулевой НДС: X` при `summary.zeroVatCount > 0`.
  - В `AuditorVerifyScreen` отображать информацию о чеках с нулевым НДС в блоке `VerificationProgress`.
- [ ] **5.5. Локализация**:
  - Добавить все новые тексты и сообщения в `res/values/strings.xml`.

### Фаза 6: Верификация и тесты
- [ ] **6.1. Unit-тесты**:
  - Тестирование `OfdPaymentApi` на `vatTotal == 0.0` vs отсутствие поля.
  - Тестирование `ReceiptParser` на распознавание 0 НДС.
  - Тестирование `AuditorRepository` и `ReceiptRepository` на раздельный подсчёт.
- [ ] **6.2. Регрессионная сборка**:
  - Запуск `./gradlew test` и `./gradlew assembleDebug`.

---

## 3. Анализ рисков и правила безопасности
1. **Сохранность БД:**
   - Таблица `receipts` уже имеет поле `vat_amount_tiyin INTEGER NOT NULL`. Мы не меняем типы колонок и не удаляем таблицы. Значение `0L` полностью валидно для базы данных.
2. **Кодировка UTF-8:**
   - Все файлы модифицируются строго через нативные инструменты редактирования в кодировке UTF-8. Никаких выводов в терминал PowerShell через `cat` или `>`.
3. **Согласованность нумерации:**
   - Номер чека в таблице отчёта, номер на карточке в PDF-листе (6 на лист) и номер в XLSX/CSV будут строго согласованы, так как чеки с нулевым НДС исключаются из основного списка синхронно во всех экранах и экспортерах.
