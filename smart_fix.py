import os
import re

def fix_mojibake_match(match):
    text = match.group(0)
    bytes_arr = bytearray()
    for c in text:
        try:
            bytes_arr.extend(c.encode('cp1251'))
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
                return text # give up, return original
    
    try:
        return bytes_arr.decode('utf-8')
    except UnicodeDecodeError:
        return text

def fix_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()
    
    if text.startswith('\ufeff'):
        text = text[1:]
        
    # Match sequences that start with Р or С and contain other typical mojibake chars.
    # Actually, let's just find sequences of characters that are in the CP1251 range or specific mapped chars
    # that could be UTF-8 bytes.
    # Basically, any word containing Р or С.
    # Or even simpler: apply it to strings like "Р..."
    # But wait, we can just find any contiguous sequence of non-ASCII characters and try to un-mojibake it.
    # If un-mojibake throws an error, we keep the original.
    
    # We find all blocks of non-ascii characters (including spaces/punctuation between them).
    # Wait, spaces are just spaces, they don't corrupt.
    # Let's match sequences of [^\x00-\x7F]+
    
    def repl(m):
        return fix_mojibake_match(m)
        
    new_text = re.sub(r'[^\x00-\x7F]+', repl, text)
    
    if new_text != text:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(new_text)
        print("Fixed:", path)

for root, dirs, files in os.walk('app/src/main/java/com/example/uzb_qqs_for_dip'):
    for f in files:
        if f.endswith('.kt'):
            fix_file(os.path.join(root, f))
