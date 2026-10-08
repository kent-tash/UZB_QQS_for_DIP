import sys

file_path = r'C:\Users\kuret\AndroidStudioProjects\UZB_QQS_for_DIP\app\src\main\java\com\example\uzb_qqs_for_dip\ui\ReceiptsViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix previewReceiptsPdf catch
old_preview_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось открыть предпросмотр: ${e.message}")
            }
        }
    }"""

new_preview_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Не удалось открыть предпросмотр: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }"""

if old_preview_catch in content:
    content = content.replace(old_preview_catch, new_preview_catch)
    print("Fixed previewReceiptsPdf catch")
else:
    print("Could not find previewReceiptsPdf catch")

# Fix shareReceiptsPdf catch
old_sharePdf_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Ошибка экспорта PDF: ${e.message}")
            }
        }
    }"""

new_sharePdf_catch = """            } catch (e: Throwable) {
                _exportEvents.value = ExportEvent.Error("Ошибка экспорта PDF: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }"""

if old_sharePdf_catch in content:
    content = content.replace(old_sharePdf_catch, new_sharePdf_catch)
    print("Fixed shareReceiptsPdf catch")
else:
    print("Could not find shareReceiptsPdf catch")

# Fix export catch
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
    print("Fixed export catch")
else:
    print("Could not find export catch")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
