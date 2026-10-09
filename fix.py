import os
path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ReportScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

old = """                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField(
                        label = "Год",
                        value = selectedYear,
                        options = years,
                        optionLabel = { it.year.toString() },
                        onSelected = { reportViewModel.setYear(it.year) },
                        modifier = Modifier.weight(1f)
                    )
                    SelectField(
                        label = "Квартал",
                        value = selectedQuarter,
                        options = quarters,
                        optionLabel = { it.quarter.label },
                        onSelected = { reportViewModel.setQuarter(it.quarter) },
                        modifier = Modifier.weight(1.4f)
                    )
                }"""

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

if old in text:
    text = text.replace(old, new)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    print("Success")
else:
    print("Not found")
