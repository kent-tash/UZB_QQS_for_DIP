#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
update_architecture_map.py
Скрипт автоматического сканирования структуры кодовой базы UZB_QQS_for_DIP
и обновления блока <architecture_map> в SYSTEM_PROMPT.md и .cursorrules.
"""

import os
import re
import shutil
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BASE_PACKAGE = PROJECT_ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "uzb_qqs_for_dip"
SYSTEM_PROMPT_PATH = PROJECT_ROOT / "SYSTEM_PROMPT.md"
CURSORRULES_PATH = PROJECT_ROOT / ".cursorrules"

def scan_files(dir_path: Path):
    if not dir_path.exists():
        return []
    return sorted([f.name for f in dir_path.glob("*.kt") if f.is_file()])

def generate_architecture_map() -> str:
    lines = []
    lines.append("<architecture_map>")
    lines.append("<directory_tree>")
    lines.append(r"C:\Users\kuret\AndroidStudioProjects\UZB_QQS_for_DIP")
    lines.append(r"│   .cursorrules               <-- Копия этого файла (SYSTEM_PROMPT)")
    lines.append(r"│   build.gradle.kts           <-- Корневой Gradle скрипт")
    lines.append(r"│   settings.gradle.kts        <-- Настройки модулей проекта")
    lines.append(r"│   SYSTEM_PROMPT.md           <-- Системный промт (этот файл)")
    lines.append(r"│   README.md")
    lines.append(r"│")
    lines.append(r"├───release/                   <-- Сюда Releaser копирует подписанные APK")
    lines.append(r"└───app/                       <-- Главный модуль приложения Android")
    lines.append(r"    │   build.gradle.kts       <-- Настройки сборки приложения")
    lines.append(r"    │   proguard-rules.pro")
    lines.append(r"    │")
    lines.append(r"    └───src/")
    lines.append(r"        ├───main/")
    lines.append(r"        │   ├───AndroidManifest.xml")
    lines.append(r"        │   ├───res/           <-- Ресурсы (xml, иконки, цвета, строки)")
    lines.append(r"        │   └───java/com/example/uzb_qqs_for_dip/  <-- КОРНЕВОЙ ПАКЕТ КОДА")
    lines.append(r"        │       ├───data/      <-- База данных, репозитории, сессии, DI")
    lines.append(r"        │       ├───export/    <-- Экспорт в PDF, CSV, Excel, ZIP")
    lines.append(r"        │       ├───network/   <-- Сетевое взаимодействие (API)")
    lines.append(r"        │       ├───render/    <-- Генерация и отрисовка QR кодов")
    lines.append(r"        │       ├───ui/        <-- Jetpack Compose экраны, компоненты, навигация, ViewModels")
    lines.append(r"        │       └───util/      <-- Вспомогательные классы и утилиты")
    lines.append(r"        │")
    lines.append(r"        ├───test/              <-- Локальные Unit-тесты (JUnit)")
    lines.append(r"        └───androidTest/       <-- Инструментальные UI-тесты на эмуляторе/устройстве")
    lines.append(r"</directory_tree>")
    lines.append("")
    lines.append(r"### 📦 Внутреннее устройство кода (Class-by-class map):")
    lines.append("")

    # 1. data
    lines.append(r"#### `data` (Слой Данных)")
    data_dir = BASE_PACKAGE / "data"
    data_root_files = scan_files(data_dir)
    if "AppContainer.kt" in data_root_files:
        lines.append(r"- **DI:** `AppContainer.kt`")
    
    db_files = scan_files(data_dir / "db")
    if db_files:
        lines.append(f"- **БД (SQLite):** `db/{', '.join(db_files)}` [КРИТИЧЕСКИЙ КЛАСС - ОСТОРОЖНО С МИГРАЦИЯМИ]")
    
    session_files = scan_files(data_dir / "session")
    if session_files:
        lines.append(f"- **Сессии:** `session/{', '.join(session_files)}` [КРИТИЧЕСКИЙ КЛАСС]")

    backup_files = scan_files(data_dir / "backup")
    if backup_files:
        lines.append(f"- **Бэкап:** `backup/{', '.join(backup_files)}`")

    repo_files = scan_files(data_dir / "repository")
    if repo_files:
        lines.append(f"- **Репозитории:** `repository/{', '.join(repo_files)}`")

    model_files = scan_files(data_dir / "model")
    if model_files:
        lines.append(f"- **Модели:** `model/{', '.join(model_files)}`")

    settings_files = scan_files(data_dir / "settings")
    if settings_files:
        lines.append(f"- **Настройки:** `settings/{', '.join(settings_files)}`")
    lines.append("")

    # 2. export
    lines.append(r"#### `export` (Модуль Экспорта)")
    export_files = scan_files(BASE_PACKAGE / "export")
    tables = [f for f in export_files if "Csv" in f or "Xlsx" in f or "Table" in f]
    pdfs = [f for f in export_files if "Pdf" in f]
    others = [f for f in export_files if f not in tables and f not in pdfs]
    if tables:
        lines.append(f"- **Таблицы:** {', '.join(f'`{f}`' for f in tables)}")
    if pdfs:
        lines.append(f"- **PDF Отчеты:** {', '.join(f'`{f}`' for f in pdfs)}")
    if others:
        lines.append(f"- **Архивы и утилиты:** {', '.join(f'`{f}`' for f in others)}")
    lines.append("")

    # 3. network
    network_files = scan_files(BASE_PACKAGE / "network")
    if network_files:
        lines.append(r"#### `network` (Сетевое Взаимодействие)")
        for nf in network_files:
            lines.append(f"- `{nf}`")
        lines.append("")

    # 4. render
    render_files = scan_files(BASE_PACKAGE / "render")
    if render_files:
        lines.append(r"#### `render` (Рендеринг и QR)")
        for rf in render_files:
            lines.append(f"- `{rf}`")
        lines.append("")

    # 5. ui
    lines.append(r"#### `ui` (Пользовательский Интерфейс - Jetpack Compose)")
    ui_dir = BASE_PACKAGE / "ui"
    nav_files = scan_files(ui_dir / "navigation")
    if nav_files:
        lines.append(f"- **Навигация:** `navigation/{', '.join(nav_files)}`")

    screen_files = scan_files(ui_dir / "screens")
    if screen_files:
        lines.append(f"- **Экраны (`screens/`):** {', '.join(f'`{f}`' for f in screen_files)}")

    ui_root_files = scan_files(ui_dir)
    vm_files = [f for f in ui_root_files if "ViewModel" in f or "Result" in f or "State" in f]
    if vm_files:
        lines.append(f"- **ViewModels & Состояния:** {', '.join(f'`{f}`' for f in vm_files)}")

    comp_files = scan_files(ui_dir / "components")
    if comp_files:
        lines.append(f"- **Компоненты (`components/`):** {', '.join(f'`{f}`' for f in comp_files)}")

    theme_files = scan_files(ui_dir / "theme")
    if theme_files:
        lines.append(f"- **Тема (`theme/`):** `theme/{', '.join(theme_files)}`")
    lines.append("")

    # 6. util
    util_files = scan_files(BASE_PACKAGE / "util")
    if util_files:
        lines.append(r"#### `util` (Утилиты)")
        lines.append(f"- {', '.join(f'`{f}`' for f in util_files)}")
        lines.append("")

    # 7. test
    test_dir = PROJECT_ROOT / "app" / "src" / "test" / "java" / "com" / "example" / "uzb_qqs_for_dip"
    if test_dir.exists():
        lines.append(r"#### `test` (Unit-тесты)")
        test_root_files = scan_files(test_dir)
        if test_root_files:
            lines.append(f"- **Корень тестов:** {', '.join(f'`{f}`' for f in test_root_files)}")
        test_data = scan_files(test_dir / "data")
        if test_data:
            lines.append(f"- **Тесты данных и настроек:** {', '.join(f'`{f}`' for f in test_data)}")
        test_export = scan_files(test_dir / "export")
        if test_export:
            lines.append(f"- **Тесты экспорта:** {', '.join(f'`{f}`' for f in test_export)}")
        test_ui = scan_files(test_dir / "ui")
        if test_ui:
            lines.append(f"- **Тесты UI & ViewModels:** {', '.join(f'`{f}`' for f in test_ui)}")
        test_util = scan_files(test_dir / "util")
        if test_util:
            lines.append(f"- **Тесты утилит:** {', '.join(f'`{f}`' for f in test_util)}")

    lines.append(r"</architecture_map>")
    return "\n".join(lines)

def update_prompt():
    if not SYSTEM_PROMPT_PATH.exists():
        print(f"Error: {SYSTEM_PROMPT_PATH} not found.")
        return

    content = SYSTEM_PROMPT_PATH.read_text(encoding="utf-8")
    new_map = generate_architecture_map()

    pattern = re.compile(r"^<architecture_map>\s*$.*?^</architecture_map>\s*$", re.DOTALL | re.MULTILINE)
    if not pattern.search(content):
        print("Error: <architecture_map> block not found in SYSTEM_PROMPT.md")
        return

    updated_content = pattern.sub(lambda _: new_map, content)
    SYSTEM_PROMPT_PATH.write_text(updated_content, encoding="utf-8")
    print(f"Successfully updated <architecture_map> in {SYSTEM_PROMPT_PATH.name}")

    # Копирование в .cursorrules
    shutil.copyfile(SYSTEM_PROMPT_PATH, CURSORRULES_PATH)
    print(f"Successfully synced to {CURSORRULES_PATH.name}")

if __name__ == "__main__":
    update_prompt()
