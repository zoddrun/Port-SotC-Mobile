"""Generate Android launcher icon resources from the developer-supplied artwork.

Source of truth: icon/icon.png (1254x1254 RGB, centered subject on pure black).
This script derives everything under app/src/main/res and never modifies the
source asset.

Adaptive icon geometry: 108dp canvas, 66dp centered safe zone (content there is
visible under every launcher mask). The artwork is scaled so its DIM content
bounding box fits inside the safe zone, then centered on the canvas.

Outputs:
  res/drawable/ic_launcher_background.xml          solid black color drawable
  res/mipmap-anydpi-v26/ic_launcher.xml            adaptive (bg + fg + mono)
  res/mipmap-anydpi-v26/ic_launcher_round.xml      adaptive (round alias)
  res/mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png   108dp canvases
  res/mipmap-{mdpi..xxxhdpi}/ic_launcher_monochrome.png  white silhouettes

Run from the repository root: python tools/generate_launcher_icons.py
"""
from PIL import Image

SRC = "icon/icon.png"
RES = "app/src/main/res"

CANVAS_DP = 108.0
SAFE_ZONE_DP = 66.0
DENSITIES = {  # qualifier -> pixels per dp
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}
BRIGHT_THRESHOLD = 72   # sum-of-channel delta vs background: clearly visible
DIM_THRESHOLD = 24      # faintly visible content (safe-zone math uses this)
BACKGROUND_RGB = (0, 0, 0)


def content_bbox(rgb, threshold):
    """Content bounding box in fractions of the image (x0, y0, x1, y1)."""
    w, h = rgb.size
    small = rgb.resize((256, 256))
    x0, y0, x1, y1 = 1.0, 1.0, 0.0, 0.0
    hit = False
    for y in range(256):
        for x in range(256):
            c = small.getpixel((x, y))
            delta = abs(c[0] - BACKGROUND_RGB[0]) + abs(c[1] - BACKGROUND_RGB[1]) + abs(c[2] - BACKGROUND_RGB[2])
            if delta > threshold:
                hit = True
                x0, y0 = min(x0, x / 256), min(y0, y / 256)
                x1, y1 = max(x1, (x + 1) / 256), max(y1, (y + 1) / 256)
    return (x0, y0, x1, y1) if hit else None


def main():
    im = Image.open(SRC).convert("RGB")
    print(f"source: {im.size} {im.mode}")

    import os
    for qualifier in list(DENSITIES) + ["anydpi-v26"]:
        os.makedirs(f"{RES}/mipmap-{qualifier}", exist_ok=True)
    os.makedirs(f"{RES}/drawable", exist_ok=True)

    for name, threshold in (("bright", BRIGHT_THRESHOLD), ("dim", DIM_THRESHOLD)):
        bbox = content_bbox(im, threshold)
        if bbox:
            print(f"{name} content bbox: x [{bbox[0]:.3f}, {bbox[2]:.3f}] y [{bbox[1]:.3f}, {bbox[3]:.3f}]"
                  f" -> {100 * (bbox[2] - bbox[0]):.1f}% x {100 * (bbox[3] - bbox[1]):.1f}% of image")
        else:
            raise SystemExit(f"no {name} content found; refusing to generate")

    # Scale the full image so the DIM bbox fits inside the safe zone.
    dim = content_bbox(im, DIM_THRESHOLD)
    content_w = (dim[2] - dim[0]) * CANVAS_DP
    content_h = (dim[3] - dim[1]) * CANVAS_DP
    scale = min(SAFE_ZONE_DP / content_w, SAFE_ZONE_DP / content_h)
    image_dp = CANVAS_DP * scale
    print(f"image scaled to {image_dp:.1f}dp on the {CANVAS_DP:.0f}dp canvas (inset {(CANVAS_DP - image_dp) / 2:.1f}dp per side)")

    with open(f"{RES}/drawable/ic_launcher_background.xml", "w", encoding="utf-8", newline="\n") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                "<!-- Solid black background layer for the adaptive launcher icon.\n"
                "     Matches the artwork's own background (icon/icon.png) so the\n"
                "     inset foreground blends seamlessly under every mask shape. -->\n"
                '<color xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:color="#000000" />\n')

    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Adaptive launcher icon derived from the developer-supplied artwork\n"
        "     (icon/icon.png). Background and monochrome layers match the artwork;\n"
        "     the foreground is inset so the content stays inside the 66dp safe\n"
        "     zone under every launcher mask. -->\n"
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )
    with open(f"{RES}/mipmap-anydpi-v26/ic_launcher.xml", "w", encoding="utf-8", newline="\n") as f:
        f.write(adaptive)
    with open(f"{RES}/mipmap-anydpi-v26/ic_launcher_round.xml", "w", encoding="utf-8", newline="\n") as f:
        f.write(adaptive)

    for qualifier, ppdp in DENSITIES.items():
        canvas_px = int(round(CANVAS_DP * ppdp))
        image_px = int(round(image_dp * ppdp))

        foreground = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        scaled = im.resize((image_px, image_px), Image.LANCZOS)
        offset = (canvas_px - image_px) // 2
        foreground.paste(scaled, (offset, offset))
        fg_path = f"{RES}/mipmap-{qualifier}/ic_launcher_foreground.png"
        foreground.save(fg_path, optimize=True)

        # Monochrome silhouette: white where the artwork is bright, alpha from
        # a gamma-lifted luminance so the glyph keeps the subject's shape.
        mono = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        gray = scaled.convert("L")
        lum = gray.load()
        mask = Image.new("L", gray.size)
        mask_px = mask.load()
        for y in range(gray.size[1]):
            for x in range(gray.size[0]):
                v = lum[x, y]
                # 0 stays invisible; 255 fully white; gamma-lifted in between.
                mask_px[x, y] = min(255, int((v / 255.0) ** 0.5 * 255.0 * 1.15))
        white = Image.new("RGBA", gray.size, (255, 255, 255, 255))
        white.putalpha(mask)
        # Plain paste: white already carries the alpha channel itself; passing
        # it as the paste mask too would multiply the alpha in twice.
        mono.paste(white, (offset, offset))
        mono_path = f"{RES}/mipmap-{qualifier}/ic_launcher_monochrome.png"
        mono.save(mono_path, optimize=True)

        print(f"mipmap-{qualifier}: foreground {canvas_px}x{canvas_px} (artwork {image_px}px), monochrome {canvas_px}x{canvas_px}")

    print("done")


if __name__ == "__main__":
    main()
