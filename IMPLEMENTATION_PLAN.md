# Implementation Plan (v1.9.0)

## 1. Scanner & Error Notifications (Floating Overlay)
- **Component**: `MultiQrCameraScanner`, `ScanScreen`
- **Changes**: 
  - Remove standard Snackbar for scan errors/duplicates in camera mode.
  - Implement a floating text panel (badge) layered directly over the camera preview center/top.
  - Handle the following states with clear messages:
    - **Duplicate**: "Чек уже отсканирован" (Yellow/Orange)
    - **Zero VAT**: "Чек с нулевым QQS" (Yellow/Orange)
    - **Not loaded yet**: "Информация о чеке не загружена, попробуйте позже" (Red)
    - **Network/Down**: "Нет соединения с интернет или сайт недоступен" (Red)
  - Ensure the floating panel disappears automatically after 2-3 seconds without blocking continuous scanning.
  - Apply similar localized error messages to Manual Link insertion and standard Camera modes.

## 2. Global Receipt Counter
- **Component**: `ScanScreen` (Header section)
- **Changes**:
  - Remove the temporary session counter from the `MultiQrCameraScanner` overlay.
  - Under the User's greeting (`Здравствуйте, И.О. Фамилия`), add a persistent counter: `Отсканировано: <Total DB count for user>`.
  - Observe `receipts.size` from the database so it updates instantly when a new receipt is added.

## 3. Manual Receipt Improvements
- **Component**: `ReceiptsScreen`, `ManualEntryBottomSheet`, PDF Generators
- **Sorting Logic**: 
  - Update `ReceiptsViewModel` sorting logic: Separate `receipt.isManual`. 
  - Normal receipts sorted by date. 
  - Manual receipts (`isManual == true`) are appended at the very end of the list, sorted by insertion order (newest at the very bottom).
- **Camera Integration**:
  - In `ManualEntryBottomSheet`, clicking the photo attachment button should open a BottomSheet/Dialog asking: "Сделать фото" или "Выбрать из галереи".
  - Implement `rememberLauncherForActivityResult(ActivityResultContracts.TakePicture())` alongside the existing `GetContent()`.
  - Save the full-quality captured image via `FileProvider`.
- **PDF Generation**:
  - `ReceiptsSheetPdfGenerator.kt`: Exclude `isManual == true` receipts from the 4-per-page electronic sheet rendering.
  - `PdfReportGenerator.kt`: Ensure the appended photo pages for manual receipts correctly display the sequential number (as it appears in the `SummaryPdfGenerator` table).

## 4. Report Filters Layout (Expandable 4-Row)
- **Component**: `ReportScreen`
- **Changes**:
  - Refactor the filter section into a vertical layout:
    - Row 1: User Dropdown
    - Row 2: Year Dropdown
    - Row 3: Quarter Dropdown
    - Row 4: Period From | Period To (side-by-side or stacked)
  - Wrap these 4 rows in an Expandable Card/Accordion titled "Фильтры". By default, it should be expanded, but the user can collapse it to save screen space.

## 5. Comprehensive Help / Manual
- **Component**: `HelpBottomSheet`
- **Changes**:
  - Rewrite the content to provide a detailed, step-by-step guide for each tab ("Добавить", "Чеки", "Отчёт", "Профиль").
  - Describe all buttons and interactions between tabs.
  - Format with proper typography, bullet points, and clear spacing.
