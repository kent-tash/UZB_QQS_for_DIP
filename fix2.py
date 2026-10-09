import os, re
path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Replace "Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {" up to "modifier = Modifier.weight(1.4f)\n                    )\n                }"
# Let's just use re.sub with dotall

pattern = re.compile(
    r'Row\(\s*horizontalArrangement = Arrangement\.spacedBy\(10\.dp\)\s*\)\s*\{\s*'
    r'SelectField\([^)]*label = "Год".*?modifier = Modifier\.weight\(1f\)\s*\)\s*'
    r'SelectField\([^)]*label = "Квартал".*?modifier = Modifier\.weight\(1\.4f\)\s*\)\s*\}',
    re.DOTALL
)

new = """                SelectField(
                    label = "Год",
                    value = selectedYear,
                    options = years,
                    optionLabel = { it.year.toString() },
                    onSelected = { reportViewModel.setYear(it.year) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                SelectField(
                    label = "Квартал",
                    value = selectedQuarter,
                    options = quarters,
                    optionLabel = { it.quarter.label },
                    onSelected = { reportViewModel.setQuarter(it.quarter) },
                    modifier = Modifier.fillMaxWidth()
                )"""

if pattern.search(text):
    text = pattern.sub(new, text)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    print("Success")
else:
    print("Not found regex")
