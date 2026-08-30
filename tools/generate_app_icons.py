#!/usr/bin/env python3
"""
Generate Android App Icons from the newly generated aesthetic icon image.
"""

import os
from PIL import Image, ImageDraw

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(BASE_DIR, "app", "src", "main", "res")
SRC_ICON = r"C:\Users\qmxz7\.gemini\antigravity\brain\432eb68a-e849-443e-a971-6651b94b0b96\dict_app_icon_1788093043953.jpg"

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def make_round_icon(im):
    size = im.size
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    
    round_im = Image.new('RGBA', size, (0, 0, 0, 0))
    round_im.paste(im, (0, 0), mask=mask)
    return round_im

def main():
    print(f"[*] Loading source icon from: {SRC_ICON}")
    orig_img = Image.open(SRC_ICON).convert("RGBA")
    
    for folder, size in SIZES.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        
        # Standard square/rounded icon
        resized = orig_img.resize((size, size), Image.Resampling.LANCZOS)
        out_path = os.path.join(out_dir, "ic_launcher.png")
        resized.save(out_path, "PNG")
        print(f"[+] Saved {out_path} ({size}x{size})")
        
        # Round icon
        round_icon = make_round_icon(resized)
        round_out_path = os.path.join(out_dir, "ic_launcher_round.png")
        round_icon.save(round_out_path, "PNG")
        print(f"[+] Saved {round_out_path}")

    # Also save a 512x512 playstore/hi-res icon
    playstore_path = os.path.join(BASE_DIR, "ic_launcher-playstore.png")
    orig_img.resize((512, 512), Image.Resampling.LANCZOS).save(playstore_path, "PNG")
    print(f"[+] Saved 512x512 icon to {playstore_path}")

if __name__ == "__main__":
    main()
