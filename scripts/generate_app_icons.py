import os
import shutil
from PIL import Image, ImageDraw

def generate_icons():
    src_path = r"C:/Users/kuret/.gemini/antigravity/brain/96a0e738-0b39-4be1-a6e5-3befd9f9a707/.user_uploaded/media_1793121883860_72c3d03d.png"
    workspace = r"c:/Users/kuret/AndroidStudioProjects/UZB_QQS_for_DIP"
    
    src_img = Image.open(src_path).convert("RGBA")
    
    # 1. Save app_logo.png
    drawable_dir = os.path.join(workspace, "app", "src", "main", "res", "drawable")
    os.makedirs(drawable_dir, exist_ok=True)
    src_img.save(os.path.join(drawable_dir, "app_logo.png"), "PNG")
    
    # 2. Save ic_launcher-playstore.png (512x512)
    playstore_icon = src_img.resize((512, 512), Image.Resampling.LANCZOS)
    playstore_icon.save(os.path.join(workspace, "app", "src", "main", "ic_launcher-playstore.png"), "PNG")
    
    # Mipmap configurations: (folder_name, launcher_size, foreground_size)
    configs = [
        ("mipmap-mdpi", 48, 108),
        ("mipmap-hdpi", 72, 162),
        ("mipmap-xhdpi", 96, 216),
        ("mipmap-xxhdpi", 144, 324),
        ("mipmap-xxxhdpi", 192, 432),
    ]
    
    res_dir = os.path.join(workspace, "app", "src", "main", "res")
    
    for folder, l_size, fg_size in configs:
        target_dir = os.path.join(res_dir, folder)
        os.makedirs(target_dir, exist_ok=True)
        
        # Remove old webp files
        for f in os.listdir(target_dir):
            if f.endswith(".webp"):
                os.remove(os.path.join(target_dir, f))
                
        # ic_launcher.png
        launcher_img = src_img.resize((l_size, l_size), Image.Resampling.LANCZOS)
        launcher_img.save(os.path.join(target_dir, "ic_launcher.png"), "PNG")
        
        # ic_launcher_round.png with circular alpha mask
        round_mask = Image.new('L', (l_size, l_size), 0)
        draw = ImageDraw.Draw(round_mask)
        draw.ellipse((0, 0, l_size, l_size), fill=255)
        round_img = launcher_img.copy()
        round_img.putalpha(round_mask)
        round_img.save(os.path.join(target_dir, "ic_launcher_round.png"), "PNG")
        
        # ic_launcher_foreground.png (108dp scaled, centered icon within safe area ~72%)
        fg_canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
        icon_scale_size = int(fg_size * 0.72)
        scaled_icon = src_img.resize((icon_scale_size, icon_scale_size), Image.Resampling.LANCZOS)
        offset = (fg_size - icon_scale_size) // 2
        fg_canvas.paste(scaled_icon, (offset, offset), scaled_icon)
        fg_canvas.save(os.path.join(target_dir, "ic_launcher_foreground.png"), "PNG")
        
    print("SUCCESS: Icons generated successfully.")

if __name__ == "__main__":
    generate_icons()
