import sys
import re

file_path = r'C:\Users\kuret\AndroidStudioProjects\UZB_QQS_for_DIP\app\src\main\java\com\example\uzb_qqs_for_dip\ui\ReceiptsViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace shareReceiptImage
def patch_method(name, err_msg_part, is_export=False):
    global content
    
    # find try block
    if is_export:
        search = r'(private fun export\([^)]*\)\s*\{\s*viewModelScope\.launch\s*\{\s*container\.receiptRepository\.refresh\(\)\s*)try\s*\{'
    else:
        search = r'(fun ' + name + r'\([^)]*\)\s*\{\s*viewModelScope\.launch\s*\{\s*container\.receiptRepository\.refresh\(\)\s*)try\s*\{'
        
    repl = r'\1_isSaving.value = true\n            try {'
    if '_isSaving.value = true' not in re.search(search, content, re.DOTALL).group(0) if re.search(search, content, re.DOTALL) else True:
        content = re.sub(search, repl, content, count=1, flags=re.DOTALL)

    # find catch block
    catch_search = r'(\} catch \(e: Throwable\) \{\s*_exportEvents\.value = ExportEvent\.Error\("[^"]*' + err_msg_part + r'[^"]*"\)\s*\})'
    
    if is_export:
        catch_repl = r'\1 finally {\n                _isSaving.value = false\n                _saveProgress.value = 0f\n            }'
    else:
        catch_repl = r'\1 finally {\n                  _isSaving.value = false\n              }'
        
    # check if finally already exists
    match = re.search(catch_search + r'\s*finally', content, re.DOTALL)
    if not match:
        content = re.sub(catch_search, catch_repl, content, count=1, flags=re.DOTALL)
        print(f"Patched {name}")
    else:
        print(f"{name} already has finally")

patch_method("shareReceiptImage", "e.message")
patch_method("printAllAsSheets", "PDF")
patch_method("previewReceiptsPdf", "e.message")
patch_method("shareReceiptsPdf", "PDF")
patch_method("export", "e.message", is_export=True)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
