"""Build app and web icons (web, Android, iOS) from the PPF symbol."""
import os
import subprocess
from brandlib import (INDIGO, RED, RED_LIGHT, WHITE, DARK_BRAND, symbol_centered, svg)

ROOT = "/home/claude/ppf-brand/out"
SRC = f"{ROOT}/_sources-svg"


def render(svg_text, name, out_png, size, opaque=False):
    os.makedirs(os.path.dirname(out_png), exist_ok=True)
    os.makedirs(SRC, exist_ok=True)
    src = f"{SRC}/{name}.svg"
    with open(src, "w") as f:
        f.write(svg_text)
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), src, "-o", out_png], check=True)
    if opaque:
        subprocess.run(["convert", out_png, "-alpha", "off", out_png], check=True)


def full_square(n, vis, bg=INDIGO, bubble=WHITE, dot=RED_LIGHT):
    """Full-bleed square icon (iOS, Play Store, maskable, apple-touch)."""
    body = symbol_centered(n / 2, n / 2, vis * n, bubble, dot)
    return svg(n, n, body, bg=bg)


def rounded_square(n, vis, radius=0.2, inset=2 / 48):
    i = inset * n
    s = n - 2 * i
    body = (f'<rect x="{i}" y="{i}" width="{s}" height="{s}" rx="{radius * s}" fill="{INDIGO}"/>'
            + symbol_centered(n / 2, n / 2, vis * n, WHITE, RED_LIGHT))
    return svg(n, n, body)


def round_icon(n, vis, inset=2 / 48):
    r = n / 2 - inset * n
    body = (f'<circle cx="{n / 2}" cy="{n / 2}" r="{r}" fill="{INDIGO}"/>'
            + symbol_centered(n / 2, n / 2, vis * n, WHITE, RED_LIGHT))
    return svg(n, n, body)


def bare(n, vis, bubble, dot):
    return svg(n, n, symbol_centered(n / 2, n / 2, vis * n, bubble, dot))


def web():
    d = f"{ROOT}/web"
    # Favicon SVG adapts to the browser's dark mode
    fav = bare(64, 0.94, INDIGO, RED)
    fav = fav.replace(f'fill="{INDIGO}"', f'class="b" fill="{INDIGO}"').replace(
        f'fill="{RED}"', f'class="d" fill="{RED}"')
    fav = fav.replace("</title>", "</title><style>@media (prefers-color-scheme: dark)"
                      "{.b{fill:#9AA6F0}.d{fill:#FF7083}}</style>", 1)
    os.makedirs(d, exist_ok=True)
    with open(f"{d}/favicon.svg", "w") as f:
        f.write(fav)
    for n in (16, 32, 48):
        render(bare(n, 0.96, INDIGO, RED), f"favicon-{n}", f"{d}/favicon-{n}x{n}.png", n)
    subprocess.run(["convert", f"{d}/favicon-16x16.png", f"{d}/favicon-32x32.png",
                    f"{d}/favicon-48x48.png", f"{d}/favicon.ico"], check=True)
    render(full_square(180, 0.62), "apple-touch-icon", f"{d}/apple-touch-icon.png", 180, opaque=True)
    for n in (192, 512):
        render(rounded_square(n, 0.56, inset=0), f"icon-{n}", f"{d}/icon-{n}.png", n)
        # maskable: everything inside the central circle of radius 40 %
        render(full_square(n, 0.52), f"icon-maskable-{n}", f"{d}/icon-maskable-{n}.png", n, opaque=True)


def android():
    d = f"{ROOT}/android"
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for k, f in dens.items():
        n = int(48 * f)
        render(rounded_square(n, 0.56), "ic_launcher", f"{d}/res/mipmap-{k}/ic_launcher.png", n)
        render(round_icon(n, 0.52), "ic_launcher_round", f"{d}/res/mipmap-{k}/ic_launcher_round.png", n)
        m = int(108 * f)
        # adaptive layers: symbol fits inside the 66 dp safe zone (visual width 46 dp of 108)
        render(bare(m, 46 / 108, WHITE, RED_LIGHT), "ic_launcher_foreground",
               f"{d}/res/mipmap-{k}/ic_launcher_foreground.png", m)
        render(bare(m, 46 / 108, WHITE, WHITE), "ic_launcher_monochrome",
               f"{d}/res/mipmap-{k}/ic_launcher_monochrome.png", m)
    os.makedirs(f"{d}/res/mipmap-anydpi-v26", exist_ok=True)
    os.makedirs(f"{d}/res/values", exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@color/ic_launcher_background" />\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
           '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />\n'
           '</adaptive-icon>\n')
    for name in ("ic_launcher", "ic_launcher_round"):
        with open(f"{d}/res/mipmap-anydpi-v26/{name}.xml", "w") as fh:
            fh.write(xml)
    with open(f"{d}/res/values/ic_launcher_background.xml", "w") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                 f'    <color name="ic_launcher_background">{INDIGO}</color>\n</resources>\n')
    # Play Store icon (full square, Google applies the rounded mask)
    render(full_square(512, 0.58), "play-store-icon", f"{d}/play-store/icon-512.png", 512, opaque=True)
    # Android 12+ splash screen icon: 288 dp canvas, content inside a 192 dp circle
    render(bare(1152, 520 / 1152, INDIGO, RED), "splash-icon-clair",
           f"{d}/splash/splash-icon-clair-1152.png", 1152)
    render(bare(1152, 520 / 1152, DARK_BRAND, RED_LIGHT), "splash-icon-sombre",
           f"{d}/splash/splash-icon-sombre-1152.png", 1152)


def ios():
    d = f"{ROOT}/ios"
    render(full_square(1024, 0.6), "AppIcon-1024", f"{d}/AppIcon-1024.png", 1024, opaque=True)
    render(bare(1024, 0.6, DARK_BRAND, RED_LIGHT), "AppIcon-1024-sombre", f"{d}/AppIcon-1024-sombre.png", 1024)
    render(full_square(1024, 0.6, bg="#000000", bubble=WHITE, dot=WHITE), "AppIcon-1024-teinte",
           f"{d}/AppIcon-1024-teinte.png", 1024, opaque=True)


if __name__ == "__main__":
    web()
    android()
    ios()
    print("ok")
