import os

def is_corrupted(text):
    # A strong indicator of CP1251-as-UTF8 mojibake is the prevalence of
    # Р (U+0420, corresponding to 0xD0) and С (U+0421, corresponding to 0xD1)
    # followed by other specific characters like ” (U+201D) or ѕ (U+0455) etc.
    # We can test if reversing the mojibake produces valid utf-8.
    
    # Let's count how many CP1251 decode artifacts there are.
    # Typical artifacts: Р° (а), Р± (б), РІ (в), Рі (г), Рґ (д), Рµ (е)
    # If we find "Р°" or "Рѕ" or "Рµ" it's almost certainly corrupted.
    artifacts = ['Р°', 'Р±', 'РІ', 'Рі', 'Рґ', 'Рµ', 'Рѕ', 'Рё', 'Рє', 'Р»', 'Рј', 'РЅ', 'С‚', 'СЊ', 'СЏ', 'СЃ', 'Р”', 'Рџ', 'Рћ']
    for a in artifacts:
        if a in text:
            return True
    return False

def uncorrupt(text):
    if text.startswith('\ufeff'):
        text = text[1:]
    bytes_arr = bytearray()
    for c in text:
        try:
            b = c.encode('cp1251')
            bytes_arr.extend(b)
        except Exception:
            if ord(c) < 256:
                bytes_arr.append(ord(c))
            elif c == '”': # U+201D mapped from 0x94
                bytes_arr.append(0x94)
            elif c == '•': # U+2022 mapped from 0x95
                bytes_arr.append(0x95)
            elif c == '–': # U+2013 mapped from 0x96
                bytes_arr.append(0x96)
            elif c == '—': # U+2014 mapped from 0x97
                bytes_arr.append(0x97)
            elif c == '™': # U+2122 mapped from 0x99
                bytes_arr.append(0x99)
            elif c == 'š': # U+0161 mapped from 0x9A
                bytes_arr.append(0x9A)
            elif c == '›': # U+203A mapped from 0x9B
                bytes_arr.append(0x9B)
            elif c == 'œ': # U+0153 mapped from 0x9C
                bytes_arr.append(0x9C)
            elif c == 'ž': # U+017E mapped from 0x9E
                bytes_arr.append(0x9E)
            elif c == 'џ': # U+045F mapped from 0x9F
                bytes_arr.append(0x9F)
            elif c == 'В': # U+0412 might be 0xC2 ?
                bytes_arr.append(0xC2)
            else:
                print("Unhandled char:", repr(c), ord(c))
                bytes_arr.extend(c.encode('utf-8'))
                
    return bytes_arr.decode('utf-8')

def scan_and_fix(d):
    fixed_count = 0
    for root, dirs, files in os.walk(d):
        for f in files:
            if f.endswith('.kt'):
                path = os.path.join(root, f)
                with open(path, 'r', encoding='utf-8') as file:
                    text = file.read()
                
                if is_corrupted(text):
                    print(f"Corrupted: {path}")
                    try:
                        restored = uncorrupt(text)
                        with open(path, 'w', encoding='utf-8') as file:
                            file.write(restored)
                        fixed_count += 1
                        print(f"  -> Fixed!")
                    except Exception as e:
                        print(f"  -> Failed to fix: {e}")
    print(f"Total fixed: {fixed_count}")

scan_and_fix('app/src/main/java/com/example/uzb_qqs_for_dip')
