import os
import re

def fix_mojibake_in_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            text = f.read()
    except Exception as e:
        return False
        
    original_text = text
    
    # We will look for any chunk of text that looks like mojibake.
    # Actually, we can just split the file by non-ascii characters and try to fix the non-ascii chunks.
    # But it's easier to just find all string literals, OR just find any sequence of characters in the range U+0400 to U+04FF (Cyrillic) 
    # which is what CP1251 maps UTF-8 bytes to.
    # A mojibaked string is a sequence of Cyrillic characters (and maybe some punctuation like \x98 which maps to undefined in cp1251? Wait.
    
    # Let's try to just use a sliding window, or regex to find strings.
    # String literals in Kotlin: "..." or """..."""
    
    def repl(m):
        s = m.group(0)
        try:
            # Only attempt if there's at least one Cyrillic char (mojibake)
            if re.search(r'[\u0400-\u04FF]', s):
                fixed = s.encode('cp1251').decode('utf-8')
                return fixed
        except:
            pass
        return s

    # Match double-quoted strings (single line)
    text = re.sub(r'"([^"\\]|\\.)*"', repl, text)
    
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
