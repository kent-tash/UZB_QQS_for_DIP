file_path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/AuditorVerifyViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

import re
# Find the faulty submitManualEntry
start = text.find('fun submitManualEntry(')
if start != -1:
    end = text.find('}', start)
    # the function has nested braces, so we should just replace it with a regex
    # But it's easier to find the exact block and replace
    pattern = re.compile(r'fun submitManualEntry.*?_verifyResult\.value = VerifyResult\.Success\(item\)\n        \}\n    \}', re.DOTALL)
    
    correct_func = """fun submitManualEntry(context: android.content.Context, storeName: String, dateMs: Long, totalTiyin: Long, vatTiyin: Long, photoUri: android.net.Uri?) {
        val employee = _selectedEmployee.value ?: return
        val auditorId = container.sessionManager.currentUserId.value
        viewModelScope.launch {
            _verifyResult.value = VerifyResult.Loading
            val (from, to) = periodBounds()
            var localPhotoPath: String? = null
            if (photoUri != null) {
                try {
                    val fileName = "manual_${System.currentTimeMillis()}.jpg"
                    val file = java.io.File(context.filesDir, fileName)
                    context.contentResolver.openInputStream(photoUri)?.use { input ->
                        file.outputStream().use { out -> input.copyTo(out) }
                    }
                    localPhotoPath = file.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val outOfPeriod = dateMs !in from..to
            val qrUrl = "manual_${System.currentTimeMillis()}_${(1000..9999).random()}"
            val parsed = com.example.uzb_qqs_for_dip.network.ParsedReceipt(
                qrUrl = qrUrl,
                purchasedAt = dateMs,
                sellerName = storeName,
                totalAmountTiyin = totalTiyin,
                vatAmountTiyin = vatTiyin,
                paymentType = com.example.uzb_qqs_for_dip.data.model.PaymentType.CASH,
                fiscalSign = null,
                rawSnippet = null
            )
            
            val receipt = com.example.uzb_qqs_for_dip.data.model.Receipt(
                userId = employee.id,
                purchasedAt = dateMs,
                sellerName = storeName,
                totalAmountTiyin = totalTiyin,
                vatAmountTiyin = vatTiyin,
                qrUrl = qrUrl,
                paymentType = com.example.uzb_qqs_for_dip.data.model.PaymentType.CASH,
                fiscalSign = null,
                address = null,
                tin = null,
                terminalId = null,
                receiptNumber = null,
                nkmName = null,
                sn = null,
                rawText = null,
                isManual = true,
                manualPhotoUri = localPhotoPath
            )
            
            val insertResult = container.receiptRepository.insert(receipt)
            if (insertResult.isFailure) {
                val err = insertResult.exceptionOrNull()
                _verifyResult.value = VerifyResult.Error(err?.message ?: "Ошибка сохранения")
                return@launch
            }
            if (auditorId != null) {
                insertResult.getOrNull()?.let { id ->
                    container.receiptRepository.markVerified(id, auditorId)
                }
            }
            _verifyResult.value = VerifyResult.Success(
                parsed = parsed,
                owner = null,
                alreadyForThisEmployee = false,
                outOfPeriod = outOfPeriod,
                markedVerified = auditorId != null
            )
            refreshEmployeeData()
        }
    }"""
    
    text = pattern.sub(correct_func, text)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)
print('Patched submitManualEntry')
