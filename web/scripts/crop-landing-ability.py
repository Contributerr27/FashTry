from PIL import Image
import os

out = r"d:\myCode\ai-clothing-swap\web\public\static\landing"
os.makedirs(out, exist_ok=True)
assets = r"C:\Users\He\.cursor\projects\d-myCode-ai-clothing-swap\assets"


def fit(im, box_w, box_h, fill=(16, 18, 22)):
    im = im.copy()
    im.thumbnail((box_w, box_h), Image.Resampling.LANCZOS)
    bg = Image.new("RGB", (box_w, box_h), fill)
    bg.paste(im, ((box_w - im.width) // 2, (box_h - im.height) // 2))
    return bg


# 套图：卖点长图，去掉底部按钮
suite = Image.open(os.path.join(assets, "image-a10feeda-f069-412e-9e35-fa2e2b1c0503.png")).convert("RGB")
sw, sh = suite.size
suite.crop((0, 0, sw, int(sh * 0.86))).save(os.path.join(out, "ability-suite.jpg"), quality=92, optimize=True)

# 精修：原图 / 白底成品
ret = Image.open(os.path.join(assets, "image-e7e4142d-9c25-485f-89d9-2a06010d8072.png")).convert("RGB")
orig = ret.crop((28, 58, 500, 575))
fin = ret.crop((548, 58, 1000, 548))
W, H = 900, 1200
canvas = Image.new("RGB", (W, H), (16, 18, 22))
pad, gap = 28, 40
box_h = (H - pad * 2 - gap) // 2
box_w = W - pad * 2
canvas.paste(fit(orig, box_w, box_h), (pad, pad))
canvas.paste(fit(fin, box_w, box_h, fill=(245, 245, 247)), (pad, pad + box_h + gap))
canvas.save(os.path.join(out, "ability-retouch.jpg"), quality=92, optimize=True)

# 模特出图：只取上身成片，去掉工作台 UI
pk = Image.open(os.path.join(assets, "image-e1044678-0308-4931-bf81-6e309c5a64c4.png")).convert("RGB")
hero = pk.crop((344, 140, 562, 420))
hero.save(os.path.join(out, "ability-packshot.jpg"), quality=92, optimize=True)
print("done", "suite/retouch/packshot")
