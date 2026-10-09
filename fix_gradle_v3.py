file_path = 'app/build.gradle.kts'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

import re
# Remove previous bad blocks
text = re.sub(r'tasks\.withType<JavaCompile>.*?\}', '', text, flags=re.DOTALL)
text = re.sub(r'tasks\.withType<org\.jetbrains\.kotlin\.gradle\.tasks\.KotlinCompile>.*?\}', '', text, flags=re.DOTALL)
text = re.sub(r'kotlin\s*\{\s*compilerOptions.*?\}', '', text, flags=re.DOTALL)

# Add correct block
correct_block = """
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xencoding=UTF-8")
    }
}
"""

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text.strip() + "\n\n" + correct_block.strip() + "\n")
print("Fixed gradle again")
