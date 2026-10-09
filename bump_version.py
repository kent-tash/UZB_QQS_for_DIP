file_path = 'app/build.gradle.kts'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

import re
old_version = re.search(r'versionName = "(.*?)"', text).group(1)
old_code = int(re.search(r'versionCode = (\d+)', text).group(1))

# Let's say we bump patch version
parts = old_version.split('.')
parts[-1] = str(int(parts[-1]) + 1)
new_version = '.'.join(parts)
new_code = old_code + 1

text = re.sub(r'versionName = ".*?"', f'versionName = "{new_version}"', text)
text = re.sub(r'versionCode = \d+', f'versionCode = {new_code}', text)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)

print(f"Bumped version to {new_version} ({new_code})")
