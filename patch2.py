import sys
import re

file_path = r'C:\Users\kuret\AndroidStudioProjects\UZB_QQS_for_DIP\app\src\main\java\com\example\uzb_qqs_for_dip\ui\ReceiptsViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

def patch_function(name, is_export=False):
    global content
    # Find the function definition
    pattern = r'(fun ' + name + r'\([^)]*\)\s*\{.*?viewModelScope\.launch\s*\{.*?)try\s*\{'
    if is_export:
         pattern = r'(private fun export\([^)]*\)\s*\{.*?viewModelScope\.launch\s*\{.*?)try\s*\{'
    
    match = re.search(pattern, content, re.DOTALL)
    if not match:
        print(f"Could not find function {name}")
        return
        
    start_idx = match.start(1)
    try_idx = match.end(1) - 5
    
    # Check if _isSaving.value = true is already there
    if '_isSaving.value = true' in content[start_idx:try_idx]:
        print(f"{name} already sets _isSaving")
        return
        
    # We will add _isSaving.value = true before try {
    # and add finally { _isSaving.value = false; _saveProgress.value = 0f } to the catch block
    
    # We need to find the matching catch block for this try
    
    # Let's just use string replacement on the whole function body carefully.
    pass

# Actually, it might be simpler to write regex replacements
# For `export`:
old_export = """            try {
                val rows = receipts.value"""
new_export = """            _isSaving.value = true
            _saveProgress.value = 0.5f
            try {
                val rows = receipts.value"""

if old_export in content:
    content = content.replace(old_export, new_export)
    print("Patched export try")

old_export_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Ошибка экспорта: ${e.message}")
            }
        }
    }"""
new_export_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Ошибка экспорта: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
            }
        }
    }"""
if old_export_catch in content:
    content = content.replace(old_export_catch, new_export_catch)
    print("Patched export catch")


# For `shareReceiptImage`:
old_shareImage = """            try {
                val ordinal = receipts.value.indexOfFirst { it.receipt.id == item.receipt.id } + 1"""
new_shareImage = """            _isSaving.value = true
            try {
                val ordinal = receipts.value.indexOfFirst { it.receipt.id == item.receipt.id } + 1"""

if old_shareImage in content:
    content = content.replace(old_shareImage, new_shareImage)
    print("Patched shareReceiptImage try")

old_shareImage_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось поделиться чеком: ${e.message}")
            }
        }
    }"""
new_shareImage_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось поделиться чеком: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }"""

if old_shareImage_catch in content:
    content = content.replace(old_shareImage_catch, new_shareImage_catch)
    print("Patched shareReceiptImage catch")


# For `printAllAsSheets`:
old_printAll = """            try {
                val file = generateReceiptsSheetPdf(context)"""
new_printAll = """            _isSaving.value = true
            _saveProgress.value = 0.5f
            try {
                val file = generateReceiptsSheetPdf(context)"""
if old_printAll in content:
    content = content.replace(old_printAll, new_printAll)
    print("Patched printAllAsSheets try")

old_printAll_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось сгенерировать PDF: ${e.message}")
            }
        }
    }"""
new_printAll_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось сгенерировать PDF: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
            }
        }
    }"""
if old_printAll_catch in content:
    content = content.replace(old_printAll_catch, new_printAll_catch)
    print("Patched printAllAsSheets catch")


# For `previewReceiptsPdf`:
# The try is identical to printAllAsSheets, so the first replace might have hit it. Wait.
# Let's use more context for try.
