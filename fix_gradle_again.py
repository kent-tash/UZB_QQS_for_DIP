file_path = 'app/build.gradle.kts'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

import re
# Remove my bad addition
text = re.sub(r'tasks\.withType<JavaCompile>.*?kotlinOptions \{.*?\}', '', text, flags=re.DOTALL)
text = text.replace('tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {\n\n}\n', '')

correct_block = """
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xencoding=UTF-8"
    }
}
"""

correct_block2 = """
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Dfile.encoding=UTF-8")
    }
}
"""

# wait, another easier way: setting the system property globally or inside `android {}` block?
# Let's just use the `compileOptions` we already have!
# In `build.gradle.kts` there's `compileOptions { ... }` but that's for Java sourceCompatibility.

# Let's just write the modern compilerOptions block:
modern_block = """
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xencoding=UTF-8")
    }
}
"""

text += modern_block

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)
print("Updated build.gradle.kts")
