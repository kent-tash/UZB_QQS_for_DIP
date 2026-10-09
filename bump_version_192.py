file_path = 'app/build.gradle.kts'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

import re
text = re.sub(r'versionName = "1\.9\.1"', 'versionName = "1.9.2"', text)
text = re.sub(r'versionCode = 21', 'versionCode = 22', text)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)

print("Bumped version to 1.9.2")
