import os
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

TARGET_WIDTH = 1080
TARGET_HEIGHT = 1920

WORKSPACE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW_DIR = os.path.join(WORKSPACE_DIR, "screenshots", "raw")
OUT_PROMO_DIR = os.path.join(WORKSPACE_DIR, "screenshots", "rustore_promo_1080x1920")
OUT_CLEAN_DIR = os.path.join(WORKSPACE_DIR, "screenshots", "rustore_clean_1080x1920")

os.makedirs(OUT_PROMO_DIR, exist_ok=True)
os.makedirs(OUT_CLEAN_DIR, exist_ok=True)

WIN_DIR = os.environ.get("WINDIR", "C:\\Windows")
FONT_TITLE_PATH = os.path.join(WIN_DIR, "Fonts", "segoeuib.ttf")
FONT_SUB_PATH = os.path.join(WIN_DIR, "Fonts", "segoeui.ttf")

FONT_TITLE = ImageFont.truetype(FONT_TITLE_PATH, 48)
FONT_SUB = ImageFont.truetype(FONT_SUB_PATH, 25)
FONT_BADGE = ImageFont.truetype(FONT_TITLE_PATH, 20)

SCREENS_METADATA = [
    {
        "id": "01",
        "raw_file": "01_scanner_hub.png",
        "out_name": "01_scanner_qr_soliq.jpg",
        "badge": "МГНОВЕННЫЙ СКАНЕР",
        "title": "Быстрый ввод и сканер",
        "subtitle": "Распознавание QR чеков Soliq и фото из галереи",
        "accent": (59, 130, 246) # Blue
    },
    {
        "id": "02",
        "raw_file": "02_summary_report.png",
        "out_name": "02_summary_report_vat.jpg",
        "badge": "НАЛОГОВАЯ СВОДКА",
        "title": "Сводный отчёт и расчёт НДС",
        "subtitle": "Автоматический расчёт налога и экспорт в PDF / Excel",
        "accent": (16, 185, 129) # Emerald Green
    },
    {
        "id": "03",
        "raw_file": "03_receipts_list.png",
        "out_name": "03_sticky_receipts_list.jpg",
        "badge": "НОВОЕ В ВЕРСИИ 1.13.0",
        "title": "Удобный реестр чеков",
        "subtitle": "Залипающая сортировка и массовые операции",
        "accent": (14, 165, 233) # Sky Blue
    },
    {
        "id": "04",
        "raw_file": "04_period_picker.png",
        "out_name": "04_period_selection.jpg",
        "badge": "ОТЧЁТНЫЕ ПЕРИОДЫ",
        "title": "Выбор периода в 1 клик",
        "subtitle": "Квартальная отчетность и фильтрация по датам",
        "accent": (139, 92, 246) # Purple
    },
    {
        "id": "05",
        "raw_file": "05_receipt_detail.png",
        "out_name": "05_receipt_preview.jpg",
        "badge": "ФИСКАЛЬНЫЙ КОНТРОЛЬ",
        "title": "Детализация и проверка чека",
        "subtitle": "Полные реквизиты Soliq, QR-код и сумма НДС",
        "accent": (245, 158, 11) # Amber
    },
    {
        "id": "06",
        "raw_file": "06_auditor_mode.png",
        "out_name": "06_auditor_verification.jpg",
        "badge": "РЕЖИМ АУДИТОРА",
        "title": "Контроль и сверка чеков",
        "subtitle": "Сверка сотрудников, отчёты и поиск расхождений",
        "accent": (236, 72, 153) # Pink / Magenta
    },
    {
        "id": "07",
        "raw_file": "07_profile_backup.png",
        "out_name": "07_profile_and_backup.jpg",
        "badge": "БЕЗОПАСНОСТЬ И БЭКАП",
        "title": "Профиль и резервная копия",
        "subtitle": "Безопасное локальное хранилище и JSON-бэкап",
        "accent": (99, 102, 241) # Indigo
    }
]

def create_gradient(width, height, top_color, bottom_color):
    top_r, top_g, top_b = top_color[:3]
    bot_r, bot_g, bot_b = bottom_color[:3]
    
    gradient_img = Image.new("RGBA", (1, height))
    for y in range(height):
        factor = y / float(height - 1)
        r = int(top_r + (bot_r - top_r) * factor)
        g = int(top_g + (bot_g - top_g) * factor)
        b = int(bot_b + (bot_b - top_b) * factor)
        gradient_img.putpixel((0, y), (r, g, b, 255))
        
    return gradient_img.resize((width, height))

def build_promo_screenshot(meta):
    raw_path = os.path.join(RAW_DIR, meta["raw_file"])
    if not os.path.exists(raw_path):
        print(f"Error: {raw_path} not found!")
        return
        
    raw_img = Image.open(raw_path).convert("RGBA")
    
    # Crop off the Android OS gesture bar at the very bottom (2230..2340) so the navigation bar is the true bottom
    app_crop = raw_img.crop((0, 0, 1080, 2230))
    
    # 1. Base canvas: Deep sleek dark slate gradient
    bg = create_gradient(TARGET_WIDTH, TARGET_HEIGHT, (10, 15, 27, 255), (17, 24, 39, 255))
    
    # Ambient colored glow at top matching accent color
    glow = Image.new("RGBA", (TARGET_WIDTH, TARGET_HEIGHT), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    ar, ag, ab = meta["accent"]
    glow_draw.ellipse([TARGET_WIDTH//2 - 380, -120, TARGET_WIDTH//2 + 380, 420], fill=(ar, ag, ab, 38))
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    bg = Image.alpha_composite(bg, glow)
    
    draw = ImageDraw.Draw(bg)
    
    # 2. Pill Badge
    badge_text = meta["badge"]
    b_bbox = draw.textbbox((0, 0), badge_text, font=FONT_BADGE)
    bw = b_bbox[2] - b_bbox[0]
    bh = b_bbox[3] - b_bbox[1]
    
    pad_x = 22
    pad_y = 6
    badge_x = (TARGET_WIDTH - bw) // 2
    badge_y = 52
    
    pill_box = [badge_x - pad_x, badge_y - pad_y, badge_x + bw + pad_x, badge_y + bh + pad_y + 4]
    draw.rounded_rectangle(pill_box, radius=18, fill=(ar, ag, ab, 40), outline=(ar, ag, ab, 140), width=2)
    draw.text((badge_x, badge_y), badge_text, font=FONT_BADGE, fill=(245, 248, 255, 255))
    
    # 3. Main Title
    title = meta["title"]
    t_bbox = draw.textbbox((0, 0), title, font=FONT_TITLE)
    tw = t_bbox[2] - t_bbox[0]
    title_x = (TARGET_WIDTH - tw) // 2
    title_y = 110
    draw.text((title_x, title_y), title, font=FONT_TITLE, fill=(255, 255, 255, 255))
    
    # 4. Subtitle
    sub = meta["subtitle"]
    s_bbox = draw.textbbox((0, 0), sub, font=FONT_SUB)
    sw = s_bbox[2] - s_bbox[0]
    sub_x = (TARGET_WIDTH - sw) // 2
    sub_y = 178
    draw.text((sub_x, sub_y), sub, font=FONT_SUB, fill=(156, 172, 196, 255))
    
    # 5. Device Mockup Frame
    # phone_w = 760 px, scaled height = 760 * 2230 / 1080 = 1569 px
    phone_w = 760
    phone_h = int(phone_w * (app_crop.height / app_crop.width)) # 1569 px
    phone_x = (TARGET_WIDTH - phone_w) // 2 # 160 px
    phone_y = 250 # ends at 250 + 1569 = 1819 px (leaving 101 px margin at bottom!)
    
    scaled_screen = app_crop.resize((phone_w, phone_h), Image.Resampling.LANCZOS)
    
    corner_radius = 42
    mask = Image.new("L", (phone_w, phone_h), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([0, 0, phone_w, phone_h], radius=corner_radius, fill=255)
    
    # Shadow layer
    shadow = Image.new("RGBA", (TARGET_WIDTH, TARGET_HEIGHT), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow)
    shadow_box = [phone_x - 14, phone_y - 8, phone_x + phone_w + 14, phone_y + phone_h + 16]
    s_draw.rounded_rectangle(shadow_box, radius=corner_radius + 10, fill=(0, 0, 0, 180))
    shadow = shadow.filter(ImageFilter.GaussianBlur(36))
    bg = Image.alpha_composite(bg, shadow)
    
    # Device Outer Frame / Body (phone chassis)
    chassis = Image.new("RGBA", (TARGET_WIDTH, TARGET_HEIGHT), (0, 0, 0, 0))
    ch_draw = ImageDraw.Draw(chassis)
    chassis_box = [phone_x - 4, phone_y - 4, phone_x + phone_w + 4, phone_y + phone_h + 4]
    ch_draw.rounded_rectangle(chassis_box, radius=corner_radius + 4, fill=(30, 41, 59, 255), outline=(51, 65, 85, 255), width=2)
    bg = Image.alpha_composite(bg, chassis)
    
    # Paste masked screen
    bg.paste(scaled_screen, (phone_x, phone_y), mask)
    
    # Screen inner border
    bezel_draw = ImageDraw.Draw(bg)
    bezel_box = [phone_x, phone_y, phone_x + phone_w, phone_y + phone_h]
    bezel_draw.rounded_rectangle(bezel_box, radius=corner_radius, outline=(71, 85, 105, 180), width=2)
    
    # Top speaker / camera hole on phone mockup
    cam_x = TARGET_WIDTH // 2
    cam_y = phone_y + 14
    bezel_draw.ellipse([cam_x - 7, cam_y - 7, cam_x + 7, cam_y + 7], fill=(15, 23, 42, 230), outline=(51, 65, 85, 200), width=1)
    
    # Convert RGBA to RGB for JPEG
    final_rgb = Image.new("RGB", (TARGET_WIDTH, TARGET_HEIGHT), (15, 23, 42))
    final_rgb.paste(bg, mask=bg.split()[3])
    
    out_file = os.path.join(OUT_PROMO_DIR, meta["out_name"])
    final_rgb.save(out_file, "JPEG", quality=95, optimize=True)
    
    sz_kb = os.path.getsize(out_file) / 1024.0
    print(f"Generated Promo: {meta['out_name']} -> {final_rgb.size[0]}x{final_rgb.size[1]} px, {sz_kb:.1f} KB")

def build_clean_screenshot(meta):
    raw_path = os.path.join(RAW_DIR, meta["raw_file"])
    if not os.path.exists(raw_path):
        return
        
    raw_img = Image.open(raw_path).convert("RGBA")
    app_crop = raw_img.crop((0, 0, 1080, 2230))
    
    # Direct fitted screen: 1080 x 1920
    # Clean background in dark theme color
    bg = Image.new("RGBA", (TARGET_WIDTH, TARGET_HEIGHT), (15, 20, 32, 255))
    
    fit_h = TARGET_HEIGHT
    fit_w = int(app_crop.width * (fit_h / app_crop.height)) # 930 px
    
    scaled_screen = app_crop.resize((fit_w, fit_h), Image.Resampling.LANCZOS)
    
    pos_x = (TARGET_WIDTH - fit_w) // 2
    bg.paste(scaled_screen, (pos_x, 0))
    
    final_rgb = bg.convert("RGB")
    out_file = os.path.join(OUT_CLEAN_DIR, meta["out_name"])
    final_rgb.save(out_file, "JPEG", quality=95, optimize=True)
    
    sz_kb = os.path.getsize(out_file) / 1024.0
    print(f"Generated Clean: {meta['out_name']} -> {final_rgb.size[0]}x{final_rgb.size[1]} px, {sz_kb:.1f} KB")

if __name__ == "__main__":
    print("Generating RuStore screenshots (1080x1920 JPG, ratio 9:16)...")
    for m in SCREENS_METADATA:
        build_promo_screenshot(m)
        build_clean_screenshot(m)
    print("\nAll RuStore screenshots successfully generated!")
