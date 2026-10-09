file_path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/AuditorVerifyViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

new_func = """
    fun submitManualEntry(context: android.content.Context, storeName: String, dateMs: Long, totalTiyin: Long, vatTiyin: Long, photoUri: android.net.Uri?) {
        val employee = _selectedEmployee.value ?: return
        viewModelScope.launch {
            val (from, to) = periodBounds()
            val finalUri = if (photoUri != null) {
                com.example.uzb_qqs_for_dip.util.UriFileWriter.copyUriToInternal(context, photoUri, "manual_${System.currentTimeMillis()}.jpg")
            } else null
            
            val isOutOfPeriod = dateMs !in from..to
            val reason = if (isOutOfPeriod) "Не входит в отчетный период" else null
            
            val item = com.example.uzb_qqs_for_dip.data.model.VerifyItem(
                id = java.util.UUID.randomUUID().toString(),
                employeeId = employee.id,
                dateMs = dateMs,
                totalAmountTiyin = totalTiyin,
                vatAmountTiyin = vatTiyin,
                rawUrl = "MANUAL_ENTRY",
                storeName = storeName,
                isConflict = isOutOfPeriod,
                conflictReason = reason,
                photoUri = finalUri?.toString()
            )
            val currentList = _verifyItems.value.toMutableList()
            currentList.add(item)
            _verifyItems.value = currentList
            _verifyResult.value = VerifyResult.Success(item)
        }
    }
"""

idx = text.rfind('}')
if idx != -1:
    text = text[:idx] + new_func + text[idx:]

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)
print('Added submitManualEntry')
