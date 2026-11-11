"""Analyze the developer-supplied icon for Android launcher-icon generation.

Probes: corners, edge band, average colors, luminance spread, and whether the
artwork has meaningful content near the borders (decides adaptive-foreground
inset). Stdlib + Pillow only; writes no files.
"""
from PIL import Image
from collections import Counter

SRC = "icon/icon.png"

im = Image.open(SRC)
print(f"size={im.size} mode={im.mode} format={im.format}")

rgb = im.convert("RGB")
w, h = im.size

print("\n-- corner pixels (R,G,B) --")
for name, (x, y) in {
    "top-left": (2, 2),
    "top-right": (w - 3, 2),
    "bottom-left": (2, h - 3),
    "bottom-right": (w - 3, h - 3),
    "center": (w // 2, h // 2),
}.items():
    print(f"{name:14s} {rgb.getpixel((x, y))}")

print("\n-- edge bands (outer 3% frame) --")
bw, bh = max(3, int(w * 0.03)), max(3, int(h * 0.03))
edge_pixels = []
for y in range(0, h, max(1, h // 200)):
    for x in range(0, w, max(1, w // 200)):
        if x < bw or x >= w - bw or y < bh or y >= h - bh:
            edge_pixels.append(rgb.getpixel((x, y)))
edge_counter = Counter(edge_pixels)
print(f"sampled {len(edge_pixels)} edge pixels; top colors:")
for color, count in edge_counter.most_common(5):
    print(f"  {color}  {100.0 * count / len(edge_pixels):.1f}%")

print("\n-- whole-image average --")
small = rgb.resize((64, 64))
px = list(small.getdata())
avg = tuple(sum(c[i] for c in px) // len(px) for i in range(3))
print(f"average color {avg}")

lum = [0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] for c in px]
print(f"luminance min={min(lum):.0f} max={max(lum):.0f} avg={sum(lum)/len(lum):.0f}")

print("\n-- content bounding box (pixels differing > 24/step from the dominant edge color) --")
dom = edge_counter.most_common(1)[0][0]
mask = rgb.resize((256, 256))
mw, mh = mask.size
bbox = [mw, mh, 0, 0]
hit = 0
for y in range(mh):
    for x in range(mw):
        c = mask.getpixel((x, y))
        if abs(c[0] - dom[0]) + abs(c[1] - dom[1]) + abs(c[2] - dom[2]) > 72:
            hit += 1
            bbox[0] = min(bbox[0], x)
            bbox[1] = min(bbox[1], y)
            bbox[2] = max(bbox[2], x)
            bbox[3] = max(bbox[3], y)
if hit:
    print(f"dominant edge color {dom}; differing-content bbox (256-grid): {bbox}")
    print(f"differing pixels: {100.0 * hit / (mw * mh):.1f}%")
else:
    print("image is uniform")
