import os
import shutil
import subprocess

apk_path = 'app/build/outputs/apk/release/app-release.apk'
release_dir = 'release'
release_apk = os.path.join(release_dir, 'UZB_QQS_v1.9.1.apk')

if not os.path.exists(apk_path):
    print("APK not found! Build failed or not finished.")
    exit(1)

os.makedirs(release_dir, exist_ok=True)
shutil.copy2(apk_path, release_apk)
print(f"Copied APK to {release_apk}")

# Update README.md
with open('README.md', 'r', encoding='utf-8') as f:
    readme = f.read()

import datetime
today = datetime.datetime.now().strftime("%d.%m.%Y")

new_release_entry = f"""## Release list
* **v1.9.1 ({today})** - Исправлен вылет приложения при ручном добавлении чека через Галерею. Исправлен размер, цвета и расположение кнопок во вкладке Аудит (Окно проверки).
"""
readme = readme.replace('## Release list\n', new_release_entry)

with open('README.md', 'w', encoding='utf-8') as f:
    f.write(readme)

# Git commit and tag
subprocess.run(['git', 'add', 'README.md', 'app/build.gradle.kts', 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/ManualEntryBottomSheet.kt', 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/AuditorVerifyScreen.kt', 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/AuditorVerifyViewModel.kt'], check=True)
subprocess.run(['git', 'commit', '-m', 'fix: resolve manual entry crash, fix auditor verify buttons layout and colors'], check=True)
subprocess.run(['git', 'tag', 'v1.9.1'], check=True)
subprocess.run(['git', 'push', 'origin', 'main', '--tags'], check=True)

# GitHub Release
notes = "Исправлен вылет приложения при ручном добавлении чека (через Галерею) и улучшен дизайн и расположение кнопок в окне Проверки чеков."
subprocess.run(['gh', 'release', 'create', 'v1.9.1', release_apk, '--title', 'v1.9.1', '--notes', notes], check=True)

print("Release v1.9.1 successfully created and published!")
