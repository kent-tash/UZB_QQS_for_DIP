import os
import re

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
    
    # Mojibake string consists of pairs of characters where each pair was a single UTF-8 Cyrillic byte.
    # Since Cyrillic bytes are \xD0 or \xD1 followed by \x80-\xBF.
    # When interpreted as CP1251, \xD0 and \xD1 become 'Р' and 'С' (U+0420, U+0421).
    # So mojibake is typically a sequence that has lots of 'Р' and 'С' interspersed with other chars.
    
    # We can just try to find all maximal substrings that CAN be encoded to CP1251 and when decoded to UTF-8 produce valid Cyrillic.
    # We will iterate through the string and find regions.
    
    # A simple regex for mojibake: any sequence of 2+ characters in the range [\u0400-\u04FF] combined with [\x80-\xFF] or other chars
    # Actually, let's just find sequences that start with U+0420 ('Р') or U+0421 ('С') and try to decode them.
    
    # Let's match chunks of text that do NOT contain ASCII characters (so we don't accidentally match English text).
    # Mojibake only contains characters above U+007F (and maybe some punctuation).
    def repl(m):
        s = m.group(0)
        try:
            fixed = s.encode('cp1251').decode('utf-8')
            # Check if the fixed string actually contains Cyrillic (so we didn't just convert random bytes to random bytes)
            if re.search(r'[\u0400-\u04FF]', fixed):
                return fixed
        except:
            pass
        return s

    # Regex: sequence of non-ASCII characters (and maybe spaces/punctuation inside them)
    # Actually, Mojibake can contain spaces? No, UTF-8 spaces are 1 byte (\x20), they remain \x20 in CP1251.
    # So spaces are ASCII spaces.
    # Let's match sequences of NON-ASCII characters!
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
