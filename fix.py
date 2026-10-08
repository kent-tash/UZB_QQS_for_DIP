import re
file_path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/AuditorVerifyViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(
    r'errorMessage = ".*?\$\{e\.message \?\: e\:\:class\.simpleName\}"',
    'errorMessage = "Не удалось проверить чек: ${e.toReadableMessage()}"',
    content
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
