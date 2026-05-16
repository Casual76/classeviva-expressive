import os
from PIL import Image

def resize_icon(source_path, res_dir):
    if not os.path.exists(source_path):
        print(f"Source image not found: {source_path}")
        return

    img = Image.open(source_path)
    # Ensure it's square
    w, h = img.size
    if w != h:
        s = min(w, h)
        img = img.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2))

    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    for folder, size in sizes.items():
        folder_path = os.path.join(res_dir, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save as ic_launcher.png
        resized_img.save(os.path.join(folder_path, "ic_launcher.png"))
        # Save as ic_launcher_round.png
        resized_img.save(os.path.join(folder_path, "ic_launcher_round.png"))
        print(f"Saved {size}x{size} icons to {folder}")

if __name__ == "__main__":
    source = r"C:\VibeCoded Projects\classeviva-expressive\ChatGPT Image 15 mag 2026, 18_33_43.png"
    res_directory = r"android\app\src\main\res"
    resize_icon(source, res_directory)
    print("Icon generation complete.")
