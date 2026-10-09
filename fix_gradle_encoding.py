file_path = 'app/build.gradle.kts'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

# Append compilation encoding rules at the end of the file if not already present
encoding_block = """
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Dfile.encoding=UTF-8"
    }
}
"""

if "options.encoding = \"UTF-8\"" not in text:
    text += encoding_block
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(text)
    print("Added UTF-8 encoding rules to build.gradle.kts")
else:
    print("Already exists")
