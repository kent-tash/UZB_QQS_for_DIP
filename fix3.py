import os
import re

def encode_ps_cp1251(s):
    res = bytearray()
    for c in s:
        try:
            res.extend(c.encode('cp1251'))
        except UnicodeEncodeError:
            if ord(c) < 256:
                res.append(ord(c))
            else:
                raise
    return bytes(res)

def fix_mojibake_in_file(filepath):
    try:
        with open(filepath, 'rb') as f:
            b = f.read()
    except Exception as e:
        return False
        
    try:
        text = b.decode('utf-8')
    except:
        return False
        
    original_text = text
    
    def repl(m):
        s = m.group(0)
        try:
            b = encode_ps_cp1251(s)
            fixed = b.decode('utf-8')
            if re.search(r'[\u0400-\u04FF]', fixed):
                return fixed
        except:
            pass
        return s

    # Regex matches any contiguous chunk containing characters above U+007F
    # including \u0098 which is matched by [^\x00-\x7F]
    text = re.sub(r'[^\x00-\x7F]+', repl, text)
    
    if text != original_text:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(text)
        return True
    return False

changed_files = []
for r, d, files in os.walk('app/src/main/java'):
    for f in files:
        if f.endswith('.kt'):
            if fix_mojibake_in_file(os.path.join(r, f)):
                changed_files.append(os.path.join(r, f))

print("Fixed files:", changed_files)
