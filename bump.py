import re

with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(r'versionCode\s*=\s*(\d+)', lambda m: f'versionCode = {int(m.group(1))+1}', content)
content = re.sub(r'versionName\s*=\s*"1\.6\.1"', 'versionName = "1.7.0"', content)

with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(content)

print('Done')
