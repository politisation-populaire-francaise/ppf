"""Build the logo files: symbol, horizontal, vertical and one-line versions in every palette."""
import os
import subprocess
from brandlib import (PALETTES, BOLD, SYMBOL_BOX, symbol, text, svg)

OUT = "/home/claude/ppf-brand/out/logo"
os.makedirs(OUT, exist_ok=True)
NAME = "Politisation Populaire Française"


def symbol_visual(size):
    x0, y0, x1, y1 = SYMBOL_BOX
    s = size / 100
    return x0 * s, y0 * s, x1 * s, y1 * s


def layout_symbol(pal):
    bubble, dot, _ = PALETTES[pal]
    S = 500
    vx0, vy0, vx1, vy1 = symbol_visual(S)
    body = symbol(-vx0, -vy0, S, bubble, dot)
    return body, vx1 - vx0, vy1 - vy0


def layout_horizontal(pal):
    """Symbol + name on three lines."""
    bubble, dot, ink = PALETTES[pal]
    S = 240
    vx0, vy0, vx1, vy1 = symbol_visual(S)
    F, L = 56, 62
    cap = BOLD.cap_height(F)
    lines = ["Politisation", "Populaire", "Française"]
    # centre cap-top..last-baseline on the bubble body (y 6..80 of 100)
    body_c = 43 * S / 100
    block = cap + 2 * L
    top = body_c - block / 2
    tx = vx1 + 0.12 * S
    parts = [symbol(0, 0, S, bubble, dot)]
    wmax = 0
    for i, ln in enumerate(lines):
        p, w = text(ln, F, tx, top + cap + i * L, ink)
        parts.append(p)
        wmax = max(wmax, w)
    x0, y0 = vx0, vy0
    x1 = tx + wmax
    y1 = max(vy1, top + block + BOLD.descender(F))
    body = f'<g transform="translate({-x0:.2f},{-y0:.2f})">{"".join(parts)}</g>'
    return body, x1 - x0, y1 - y0


def layout_line(pal):
    """Symbol + name on one line."""
    bubble, dot, ink = PALETTES[pal]
    S = 120
    vx0, vy0, vx1, vy1 = symbol_visual(S)
    F = 52
    cap = BOLD.cap_height(F)
    body_c = 43 * S / 100
    tx = vx1 + 0.16 * S
    p, w = text(NAME, F, tx, body_c + cap / 2, ink)
    parts = [symbol(0, 0, S, bubble, dot), p]
    body = f'<g transform="translate({-vx0:.2f},{-vy0:.2f})">{"".join(parts)}</g>'
    return body, tx + w - vx0, vy1 - vy0


def layout_vertical(pal):
    """Symbol above, name on two centred lines."""
    bubble, dot, ink = PALETTES[pal]
    S = 260
    vx0, vy0, vx1, vy1 = symbol_visual(S)
    F, L = 54, 62
    cap = BOLD.cap_height(F)
    lines = ["Politisation Populaire", "Française"]
    widths = [BOLD.width(ln, F) for ln in lines]
    W = max(widths + [vx1 - vx0])
    cx = W / 2
    sym_x = cx - (vx0 + vx1) / 2
    parts = [symbol(sym_x, -vy0, S, bubble, dot)]
    y = (vy1 - vy0) + 0.16 * S + cap
    for i, ln in enumerate(lines):
        p, _ = text(ln, F, cx, y + i * L, ink, anchor="middle")
        parts.append(p)
    H = y + L + BOLD.descender(F)
    return "".join(parts), W, H


LAYOUTS = {
    "symbole": layout_symbol,
    "logo-horizontal": layout_horizontal,
    "logo-ligne": layout_line,
    "logo-vertical": layout_vertical,
}


def write(name, body, w, h, png_width):
    pad = 4
    body = f'<g transform="translate({pad},{pad})">{body}</g>'
    w, h = round(w + 2 * pad, 1), round(h + 2 * pad, 1)
    path = f"{OUT}/{name}.svg"
    with open(path, "w") as f:
        f.write(svg(w, h, body))
    subprocess.run(["rsvg-convert", "-w", str(png_width), path, "-o", f"{OUT}/png/{name}.png"], check=True)


if __name__ == "__main__":
    os.makedirs(f"{OUT}/png", exist_ok=True)
    for lname, fn in LAYOUTS.items():
        for pal in PALETTES:
            body, w, h = fn(pal)
            pw = 1024 if lname == "symbole" else 2000
            write(f"{lname}-{pal}", body, w, h, pw)
    print("ok")
