import subprocess
import time
import os
import sys

def run_adb(cmd):
    full_cmd = f"adb {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def tap(x, y):
    run_adb(f"shell input tap {x} {y}")
    time.sleep(1.2)

def key_back():
    run_adb("shell input keyevent 4")
    time.sleep(1.0)

def swipe(x1, y1, x2, y2, duration=400):
    run_adb(f"shell input swipe {x1} {y1} {x2} {y2} {duration}")
    time.sleep(1.2)

def capture_screen(dest_path):
    device_tmp = "/sdcard/rs_tmp.png"
    run_adb(f"shell screencap -p {device_tmp}")
    run_adb(f"pull {device_tmp} \"{dest_path}\"")
    sz = os.path.getsize(dest_path)
    print(f"Captured: {dest_path} ({sz} bytes)")
    return sz

def setup_demo_mode():
    run_adb("shell settings put global sysui_demo_mode 1")
    run_adb("shell am broadcast -a com.android.systemui.demo -e command enter")
    run_adb("shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200")
    run_adb("shell am broadcast -a com.android.systemui.demo -e command battery -e plugged false -e level 100")
    run_adb("shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e datatype lte -e level 4")
    run_adb("shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
    time.sleep(0.8)

def ensure_employee_mode():
    # Go to profile
    tap(908, 2124)
    # Check if we need to switch
    run_adb("shell uiautomator dump /sdcard/mode_check.xml")
    run_adb("pull /sdcard/mode_check.xml temp_mode.xml")
    if os.path.exists("temp_mode.xml"):
        with open("temp_mode.xml", "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()
        if "Переключить на «Сотрудник»" in content:
            print("Switching back to Employee mode...")
            tap(540, 1522)
            time.sleep(1.0)
        if os.path.exists("temp_mode.xml"):
            os.remove("temp_mode.xml")

def run():
    raw_dir = os.path.abspath("screenshots/raw")
    os.makedirs(raw_dir, exist_ok=True)
    
    print("1. Setting up Demo Mode...")
    setup_demo_mode()
    
    print("2. Ensuring Employee mode...")
    ensure_employee_mode()
    
    # Screen 1: Scanner Hub
    print("3. Capturing Screen 1: Scanner Hub...")
    tap(171, 2124)
    time.sleep(1.0)
    capture_screen(os.path.join(raw_dir, "01_scanner_hub.png"))
    
    # Screen 2: Summary Report
    print("4. Capturing Screen 2: Summary Report...")
    tap(540, 2124)
    time.sleep(0.5)
    swipe(540, 600, 540, 1700, 300) # Ensure scrolled to top
    time.sleep(1.0)
    capture_screen(os.path.join(raw_dir, "02_summary_report.png"))
    
    # Screen 3: Scrolled Receipts List with Sticky Header
    print("5. Capturing Screen 3: Receipts List with Sticky Header...")
    swipe(540, 1650, 540, 700, 450)
    time.sleep(1.0)
    capture_screen(os.path.join(raw_dir, "03_receipts_list.png"))
    
    # Reset scroll to top
    print("6. Resetting scroll to top...")
    swipe(540, 700, 540, 1750, 450)
    time.sleep(1.0)
    
    # Screen 4: Period Picker
    print("7. Capturing Screen 4: Period Picker...")
    tap(951, 658) # Period button
    time.sleep(1.2)
    capture_screen(os.path.join(raw_dir, "04_period_picker.png"))
    key_back() # Close sheet
    time.sleep(1.0)
    
    # Screen 5: Receipt Detail Preview
    print("8. Capturing Screen 5: Receipt Details...")
    tap(400, 1750) # Tap first receipt
    time.sleep(1.2)
    capture_screen(os.path.join(raw_dir, "05_receipt_detail.png"))
    key_back() # Close sheet
    time.sleep(1.0)
    
    # Screen 6: Auditor Mode
    print("9. Capturing Screen 6: Auditor Mode...")
    tap(908, 2124) # Profile tab
    time.sleep(1.0)
    tap(540, 1522) # Switch to Auditor
    time.sleep(1.2)
    tap(171, 2124) # Tap Audit tab
    time.sleep(1.2)
    capture_screen(os.path.join(raw_dir, "06_auditor_mode.png"))
    
    # Screen 7: Profile and Backup
    print("10. Capturing Screen 7: Profile and Backup...")
    tap(908, 2124) # Profile tab
    time.sleep(1.0)
    tap(540, 1522) # Switch back to Employee
    time.sleep(1.2)
    capture_screen(os.path.join(raw_dir, "07_profile_backup.png"))
    
    print("\nAll 7 screens captured successfully to screenshots/raw/!")

if __name__ == "__main__":
    run()
