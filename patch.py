import sys

file_path = r'C:\Users\kuret\AndroidStudioProjects\UZB_QQS_for_DIP\app\src\main\java\com\example\uzb_qqs_for_dip\ui\ReportViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace shareReport catch block
old_share_catch = """            } catch (e: Throwable) {
                _event.value = ReportEvent.Error("Ошибка экспорта: ${e.message}")
            }
        }
    }"""

new_share_catch = """            } catch (e: Throwable) {
                _event.value = ReportEvent.Error("Ошибка экспорта: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
                _savePhase.value = ""
            }
        }
    }"""

if old_share_catch in content:
    content = content.replace(old_share_catch, new_share_catch)
    print("Replaced shareReport catch block")
else:
    print("Could not find old_share_catch")

# Replace generate catch block
old_generate_catch = """            } catch (e: Throwable) {
                _event.value = ReportEvent.Error("Ошибка формирования PDF: ${e.message}")
            }
        }
    }"""

new_generate_catch = """            } catch (e: Throwable) {
                _event.value = ReportEvent.Error("Ошибка формирования PDF: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = 0f
                _savePhase.value = ""
            }
        }
    }"""

if old_generate_catch in content:
    content = content.replace(old_generate_catch, new_generate_catch)
    print("Replaced generate catch block")
else:
    print("Could not find old_generate_catch")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
