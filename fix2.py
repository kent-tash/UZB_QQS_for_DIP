import re

with open('app/src/main/java/com/example/uzb_qqs_for_dip/ui/ReportViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# I will replace generate with one that uses _isSaving and syncWithOfd
# I see syncWithOfd is in this ViewModel: `private suspend fun syncWithOfd(onProgress: (Int, Int) -> Unit)`

new_generate = """    private fun generate(context: Context, openSystemPrint: Boolean) {
        viewModelScope.launch {
            container.receiptRepository.refresh()
            val s = settings.value
            val user = users.value.firstOrNull { it.id == s.userId }
                ?: currentUser.value
            if (user == null) {
                _event.value = ReportEvent.Error("Пользователь не найден")
                return@launch
            }
            
            _isSaving.value = true
            try {
                _saveProgress.value = 0f
                syncWithOfd { current, total ->
                    _savePhase.value = "Синхронизация... Обработано $current из $total"
                    _saveProgress.value = 0.5f * (current.toFloat() / total.toFloat())
                }
                
                _savePhase.value = "Формирование PDF..."
                val params = ReportParams(
                    user = user,
                    periodStart = s.from,
                    periodEnd = s.to,
                    rows = rows.value,
                    quarterLabel = if (s.quarter == Quarter.Custom) null
                        else "${s.quarter.label} ${s.year} г."
                )
                val safeName = user.fullName.replace(Regex("[^A-Za-zА-Яа-я0-9_-]"), "_").take(40)
                val fileName = "report_${safeName}_${System.currentTimeMillis()}.pdf"
                val file = com.example.uzb_qqs_for_dip.render.PdfReportGenerator.generate(
                    context, params, fileName
                )
                
                _saveProgress.value = 1f
                
                if (openSystemPrint) {
                    com.example.uzb_qqs_for_dip.util.PrintHelper.printPdf(context, file)
                } else {
                    com.example.uzb_qqs_for_dip.util.FileOpener.openPdf(context, file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _event.value = ReportEvent.Error(e.message ?: "Ошибка")
            } finally {
                _isSaving.value = false
            }
        }
    }"""

content = re.sub(r'    private fun generate\(context: Context, openSystemPrint: Boolean\) \{.*?(?=    fun sharePdf)', new_generate + '\n\n', content, flags=re.DOTALL)

new_share = """    private fun shareReport(context: Context, asPdf: Boolean) {
        viewModelScope.launch {
            container.receiptRepository.refresh()
            val s = settings.value
            val user = users.value.firstOrNull { it.id == s.userId } ?: currentUser.value
            if (user == null) {
                _event.value = ReportEvent.Error("Пользователь не найден")
                return@launch
            }
            if (rows.value.isEmpty()) {
                _event.value = ReportEvent.Error("Нет данных для отчета")
                return@launch
            }
            
            _isSaving.value = true
            try {
                _saveProgress.value = 0f
                syncWithOfd { current, total ->
                    _savePhase.value = "Синхронизация... Обработано $current из $total"
                    _saveProgress.value = 0.5f * (current.toFloat() / total.toFloat())
                }
                
                _savePhase.value = "Формирование файла..."
                val safeName = user.fullName.replace(Regex("[^A-Za-zА-Яа-я0-9_-]"), "_").take(40)
                val file = if (asPdf) {
                    val params = ReportParams(
                        user = user,
                        periodStart = s.from,
                        periodEnd = s.to,
                        rows = rows.value,
                        quarterLabel = if (s.quarter == Quarter.Custom) null
                        else "${s.quarter.label} ${s.year} г."
                    )
                    com.example.uzb_qqs_for_dip.render.PdfReportGenerator.generate(
                        context, params, "report_${safeName}_${System.currentTimeMillis()}.pdf"
                    )
                } else {
                    val params = ReportParams(
                        user = user,
                        periodStart = s.from,
                        periodEnd = s.to,
                        rows = rows.value,
                        quarterLabel = if (s.quarter == Quarter.Custom) null
                        else "${s.quarter.label} ${s.year} г."
                    )
                    com.example.uzb_qqs_for_dip.render.ExcelReportGenerator.generate(
                        context, params, "report_${safeName}_${System.currentTimeMillis()}.xlsx"
                    )
                }
                _saveProgress.value = 1f
                com.example.uzb_qqs_for_dip.util.FileOpener.shareFile(context, file)
            } catch (e: Exception) {
                e.printStackTrace()
                _event.value = ReportEvent.Error(e.message ?: "Ошибка")
            } finally {
                _isSaving.value = false
            }
        }
    }"""

content = re.sub(r'    private fun shareReport\(context: Context, asPdf: Boolean\) \{.*?(?=    private suspend fun saveReportFile)', new_share + '\n\n', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/uzb_qqs_for_dip/ui/ReportViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Replaced!")
