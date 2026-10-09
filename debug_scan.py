file_path = 'app/src/main/java/com/example/uzb_qqs_for_dip/ui/screens/ScanScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

def uncorrupt_dbg(text):
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
            elif c == '”': bytes_arr.append(0x94)
            elif c == '•': bytes_arr.append(0x95)
            elif c == '–': bytes_arr.append(0x96)
            elif c == '—': bytes_arr.append(0x97)
            elif c == '™': bytes_arr.append(0x99)
            elif c == 'š': bytes_arr.append(0x9A)
            elif c == '›': bytes_arr.append(0x9B)
            elif c == 'œ': bytes_arr.append(0x9C)
            elif c == 'ž': bytes_arr.append(0x9E)
            elif c == 'џ': bytes_arr.append(0x9F)
            elif c == 'В': bytes_arr.append(0xC2)
            else:
                bytes_arr.extend(c.encode('utf-8'))
    
    try:
        bytes_arr.decode('utf-8')
    except UnicodeDecodeError as e:
        idx = e.start
        print('Fails around byte', idx, 'context:', bytes_arr[max(0, idx-20):idx+20])

uncorrupt_dbg(text)
