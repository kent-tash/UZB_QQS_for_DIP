import os, re

files = [
    'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReceiptsScreen.kt',
    'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ScanScreen.kt',
    'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ProfileScreen.kt',
    'app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/ExportActionGrid.kt',
    'app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/HelpDialog.kt',
    'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt',
    'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/AuditorScreen.kt',
]

for f in files:
    if os.path.exists(f):
        with open(f, encoding='utf-8') as fp:
            content = fp.read()
        has_help = 'HelpDialog' in content or 'showHelp' in content
        has_refresh = 'Обновить данные' in content or 'isRefreshing' in content
        print(f"OK  {os.path.basename(f)} | HelpDialog={has_help} | RefreshBtn={has_refresh}")
    else:
        print(f"MISSING: {f}")
