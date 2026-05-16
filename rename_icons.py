import os
import glob

res_dir = r"C:\VibeCoded Projects\classeviva-expressive\android\app\src\main\res"

for mipmap_dir in glob.glob(os.path.join(res_dir, "mipmap-*")):
    old_icon = os.path.join(mipmap_dir, "ic_launcher.png")
    new_icon = os.path.join(mipmap_dir, "ic_app_icon.png")
    if os.path.exists(old_icon):
        if os.path.exists(new_icon):
            os.remove(new_icon)
        os.rename(old_icon, new_icon)
        
    old_round = os.path.join(mipmap_dir, "ic_launcher_round.png")
    new_round = os.path.join(mipmap_dir, "ic_app_icon_round.png")
    if os.path.exists(old_round):
        if os.path.exists(new_round):
            os.remove(new_round)
        os.rename(old_round, new_round)

print("Icons renamed successfully.")
