"""Build banners (link previews, stores, social headers) and social avatars."""
import os
import subprocess
from brandlib import (INDIGO, INDIGO_TINT, RED, INK, INK_MUTED, WHITE, RED_LIGHT, BOLD, REGULAR,
                      text, svg, dot_grid, symbol_centered)
from build_logos import layout_horizontal

ROOT = "/home/claude/ppf-brand/out"
SRC = f"{ROOT}/_sources-svg"
NAME = "Politisation Populaire Française"
TAGLINE = "Voir, Comprendre, Agir, Décider."
LEGAL = "Plateforme citoyenne indépendante, sans lien avec l'État ni le gouvernement."
SUBTLE = "#5F6685"


def motif(right, h, uw):
    """Cover motif: tint block with dots, indigo block, red block bleeding top, small ink square."""
    p = []
    tx = right - 1.05 * uw
    p.append(f'<rect x="{tx}" y="0" width="{1.05 * uw}" height="{h}" fill="{INDIGO_TINT}"/>')
    pitch = max(uw * 0.05, 12)
    p.append(dot_grid(tx, 0, 1.05 * uw, h, pitch, pitch * 0.15, INDIGO, None))
    p.append(f'<rect x="{right - 0.58 * uw}" y="{0.34 * h}" width="{0.58 * uw}" height="{0.66 * h}" fill="{INDIGO}"/>')
    p.append(f'<rect x="{right - 0.80 * uw}" y="0" width="{0.22 * uw}" height="{0.46 * h}" fill="{RED}"/>')
    sq = 0.08 * uw
    p.append(f'<rect x="{right - 1.05 * uw - 1.6 * sq}" y="{0.70 * h}" width="{sq}" height="{sq}" fill="{INK}"/>')
    return "".join(p), 1.05 * uw + 1.6 * sq


def logo_h(x, y, height):
    """Horizontal logo (symbol + three lines) with top-left at (x, y) and given height."""
    body, w, h = layout_horizontal("couleur")
    s = height / h
    return f'<g transform="translate({x},{y}) scale({s:.5f})">{body}</g>', w * s


def render(name, out, w, h, body):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    os.makedirs(SRC, exist_ok=True)
    src = f"{SRC}/{name}.svg"
    with open(src, "w") as f:
        f.write(svg(w, h, body, bg=WHITE))
    subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h), src, "-o", out], check=True)
    subprocess.run(["convert", out, "-alpha", "off", out], check=True)


def landscape(w, h, logo_height, margin, uw, legal=True):
    """Link preview / store banner: logo + tagline on the left, motif on the right."""
    m, mw = motif(w, h, uw)
    lg, lw = logo_h(margin, 0, logo_height)
    tag_f = logo_height * 0.15
    block = logo_height + tag_f * 2.2
    top = (h - block) / 2 - (h * 0.03 if legal else 0)
    lg, lw = logo_h(margin, top, logo_height)
    tg, _ = text(TAGLINE, tag_f, margin, top + logo_height + tag_f * 2.0, INK_MUTED, font=REGULAR)
    body = m + lg + tg
    if legal:
        lf = h * 0.03
        lp, _ = text(LEGAL, lf, margin, h - margin * 0.6, SUBTLE, font=REGULAR)
        body += lp
    return body


def profile_header(w, h, x_text, uw, name_f, tag_f):
    """Social profile header: name + tagline, motif right. The platform shows our avatar bottom-left."""
    m, _ = motif(w, h, uw)
    cap = BOLD.cap_height(name_f)
    block = cap + tag_f * 1.9
    top = h * 0.44 - block / 2
    n, _ = text(NAME, name_f, x_text, top + cap, INDIGO)
    t, _ = text(TAGLINE, tag_f, x_text, top + cap + tag_f * 1.9, INK_MUTED, font=REGULAR)
    return m + n + t


def youtube():
    w, h = 2560, 1440
    # safe area (all devices): 1546 x 423, centred
    sx0, sy0 = (w - 1546) / 2, (h - 423) / 2
    m, _ = motif(w, h, 420)
    lh = 300
    lg, lw = logo_h(sx0 + 40, sy0 + (423 - lh) / 2, lh)
    f = 64
    tx = sx0 + 40 + lw + 90
    t1, _ = text("Voir, Comprendre,", f, tx, h / 2 - 12, INK_MUTED, font=REGULAR)
    t2, _ = text("Agir, Décider.", f, tx, h / 2 + 72, INK_MUTED, font=REGULAR)
    return m + lg + t1 + t2


def avatar(n, white_bg=False):
    if white_bg:
        return svg(n, n, symbol_centered(n / 2, n / 2, 0.62 * n, INDIGO, RED), bg=WHITE)
    return svg(n, n, symbol_centered(n / 2, n / 2, 0.56 * n, WHITE, RED_LIGHT), bg=INDIGO)


def write_avatar(name, out, n, white_bg):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    src = f"{SRC}/{name}.svg"
    with open(src, "w") as f:
        f.write(avatar(n, white_bg))
    subprocess.run(["rsvg-convert", "-w", str(n), "-h", str(n), src, "-o", out], check=True)
    subprocess.run(["convert", out, "-alpha", "off", out], check=True)


if __name__ == "__main__":
    B = f"{ROOT}/bannieres"
    render("og-image", f"{ROOT}/web/og-image-1200x630.png", 1200, 630,
           landscape(1200, 630, 230, 80, 400))
    render("feature-graphic", f"{ROOT}/android/play-store/feature-graphic-1024x500.png", 1024, 500,
           landscape(1024, 500, 190, 64, 330, legal=False))
    render("x-header", f"{B}/x-1500x500.png", 1500, 500, profile_header(1500, 500, 300, 330, 50, 30))
    render("mastodon-header", f"{B}/mastodon-1500x500.png", 1500, 500, profile_header(1500, 500, 300, 330, 50, 30))
    render("bluesky-banner", f"{B}/bluesky-3000x1000.png", 3000, 1000, profile_header(3000, 1000, 600, 660, 100, 60))
    render("linkedin-page", f"{B}/linkedin-page-1512x256.png", 1512, 256, profile_header(1512, 256, 330, 200, 40, 24))
    render("linkedin-profil", f"{B}/linkedin-profil-1584x396.png", 1584, 396, profile_header(1584, 396, 480, 270, 44, 26))
    render("youtube-banner", f"{B}/youtube-2560x1440.png", 2560, 1440, youtube())
    for n in (400, 800):
        write_avatar(f"avatar-{n}", f"{B}/avatar-{n}.png", n, False)
        write_avatar(f"avatar-blanc-{n}", f"{B}/avatar-fond-blanc-{n}.png", n, True)
    print("ok")
