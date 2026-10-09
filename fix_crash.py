file_path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/components/ManualEntryBottomSheet.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)', '"image/*"')
text = text.replace('ActivityResultContracts.PickVisualMedia()', 'ActivityResultContracts.GetContent()')

text = text.replace('photoPicker.launch("image/*")', 'try { photoPicker.launch("image/*") } catch (e: Exception) { e.printStackTrace() }')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)
print('Fixed crash!')
