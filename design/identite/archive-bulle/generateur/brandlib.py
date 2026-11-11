"""Shared drawing helpers for the PPF brand assets (symbol, wordmark, motif)."""
import io
import os
import uharfbuzz as hb
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

FONT_DIR = "/home/claude/ppf-ds/project/fonts"
TMP = "/home/claude/ppf-brand/tmp"
os.makedirs(TMP, exist_ok=True)

# Colours (from the design system tokens)
INDIGO = "#2B3A8F"
INDIGO_STRONG = "#1F2A6B"
INDIGO_TINT = "#E9ECF8"
RED = "#C8213A"
RED_LIGHT = "#FF7083"
INK = "#14182B"
INK_MUTED = "#4A5272"
WHITE = "#FFFFFF"
DARK_BG = "#0F1220"
DARK_BRAND = "#9AA6F0"
DARK_INK = "#EEF0F8"

PALETTES = {
    # bubble, dot, text
    "couleur": (INDIGO, RED, INDIGO),
    "inverse": (WHITE, RED_LIGHT, WHITE),
    "sombre": (DARK_BRAND, RED_LIGHT, DARK_INK),
    "noir": (INK, INK, INK),
    "blanc": (WHITE, WHITE, WHITE),
}


class Font:
    def __init__(self, weight):
        src = f"{FONT_DIR}/atkinson-hyperlegible-latin-{weight}-normal.woff2"
        tt = TTFont(src)
        tt.flavor = None
        buf = io.BytesIO()
        tt.save(buf)
        data = buf.getvalue()
        self.tt = TTFont(io.BytesIO(data))
        self.upem = self.tt["head"].unitsPerEm
        self.glyphset = self.tt.getGlyphSet()
        self.order = self.tt.getGlyphOrder()
        os2 = self.tt["OS/2"]
        self.cap = getattr(os2, "sCapHeight", 0) or 0.7 * self.upem
        self.desc = -self.tt["hhea"].descent
        face = hb.Face(data)
        self.hbfont = hb.Font(face)

    def path(self, text, size, x=0.0, y=0.0, tracking=0.0):
        """Return (svg path d, advance width) for text with baseline at y."""
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(self.hbfont, buf, {"kern": True, "liga": True})
        s = size / self.upem
        pen_x = 0.0
        parts = []
        for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
            name = self.order[info.codepoint]
            gx = x + (pen_x + pos.x_offset) * s
            gy = y - pos.y_offset * s
            sp = SVGPathPen(self.glyphset)
            tp = TransformPen(sp, (s, 0, 0, -s, gx, gy))
            self.glyphset[name].draw(tp)
            d = sp.getCommands()
            if d:
                parts.append(d)
            pen_x += pos.x_advance + tracking * self.upem
        width = (pen_x - tracking * self.upem) * s
        return " ".join(parts), width

    def width(self, text, size, tracking=0.0):
        return self.path(text, size, tracking=tracking)[1]

    def cap_height(self, size):
        return self.cap * size / self.upem

    def descender(self, size):
        return self.desc * size / self.upem


BOLD = Font(700)
REGULAR = Font(400)


def rrect(x, y, w, h, r):
    return (f"M{x + r},{y} H{x + w - r} A{r},{r} 0 0 1 {x + w},{y + r} "
            f"V{y + h - r} A{r},{r} 0 0 1 {x + w - r},{y + h} H{x + r} "
            f"A{r},{r} 0 0 1 {x},{y + h - r} V{y + r} A{r},{r} 0 0 1 {x + r},{y} Z")


# Symbol geometry, in a 100 x 100 box.
BUBBLE = ("M26,6 H74 A20,20 0 0 1 94,26 V60 A20,20 0 0 1 74,80 H37 "
          "L15.5,95 Q6,101.5 6,90 V26 A20,20 0 0 1 26,6 Z")
# Three figures of rising height (bodies + heads), knocked out of the bubble.
FIG_BOTTOM = 64
FIGS = [(26, 14), (44, 22), (62, 30)]  # (x, body height); body width 12
FIG_W = 12
HEAD_R = 5.5
HEAD_GAP = 3


def _body(x, y, w, h, rt, rb):
    return (f"M{x + rt},{y} A{rt},{rt} 0 0 1 {x + w},{y + rt} V{y + h - rb} "
            f"A{rb},{rb} 0 0 1 {x + w - rb},{y + h} H{x + rb} A{rb},{rb} 0 0 1 {x},{y + h - rb} "
            f"V{y + rt} A{rt},{rt} 0 0 1 {x + rt},{y} Z")


def _circle(cx, cy, r):
    return f"M{cx - r},{cy} A{r},{r} 0 1 0 {cx + r},{cy} A{r},{r} 0 1 0 {cx - r},{cy} Z"


def figures():
    parts = []
    for x, h in FIGS:
        top = FIG_BOTTOM - h
        parts.append(_body(x, top, FIG_W, h, FIG_W / 2, 3))
        parts.append(_circle(x + FIG_W / 2, top - HEAD_GAP - HEAD_R, HEAD_R))
    return " ".join(parts)
DOT = (87, 13, 9)   # cx, cy, r
RING = 14           # gap radius around the dot

# Visual extent of the symbol inside its 100 box (for centring)
SYMBOL_BOX = (6, 4, 96, 98)  # x0, y0, x1, y1

_uid = [0]


def symbol(x, y, size, bubble, dot, with_dot=True):
    """SVG group drawing the symbol in a size x size box at (x, y)."""
    _uid[0] += 1
    mid = f"ppfm{_uid[0]}"
    s = size / 100.0
    bars = figures()
    cx, cy, r = DOT
    out = [f'<g transform="translate({x:.3f},{y:.3f}) scale({s:.5f})">']
    if with_dot:
        out.append(
            f'<defs><mask id="{mid}" maskUnits="userSpaceOnUse" x="-10" y="-10" width="120" height="120">'
            f'<rect x="-10" y="-10" width="120" height="120" fill="#fff"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{RING}" fill="#000"/></mask></defs>')
        out.append(f'<path fill="{bubble}" fill-rule="evenodd" mask="url(#{mid})" d="{BUBBLE} {bars}"/>')
        out.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{dot}"/>')
    else:
        out.append(f'<path fill="{bubble}" fill-rule="evenodd" d="{BUBBLE} {bars}"/>')
    out.append("</g>")
    return "".join(out)


def symbol_centered(cx, cy, visual_w, bubble, dot):
    """Place the symbol so its visual box is centred on (cx, cy) with given visual width."""
    x0, y0, x1, y1 = SYMBOL_BOX
    size = visual_w / ((x1 - x0) / 100.0)
    s = size / 100.0
    vx = (x0 + x1) / 2 * s
    vy = (y0 + y1) / 2 * s
    return symbol(cx - vx, cy - vy, size, bubble, dot)


def text(t, size, x, y, fill, font=BOLD, tracking=0.0, anchor="start"):
    w = font.width(t, size, tracking)
    if anchor == "middle":
        x -= w / 2
    elif anchor == "end":
        x -= w
    d, w = font.path(t, size, x, y, tracking)
    return f'<path fill="{fill}" d="{d}"/>', w


def svg(w, h, body, bg=None, title="Politisation Populaire Française"):
    bgr = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" '
            f'role="img" aria-label="{title}"><title>{title}</title>{bgr}{body}</svg>')


def dot_grid(x, y, w, h, pitch, r, fill, clip_id):
    _uid[0] += 1
    pid = f"ppfdots{_uid[0]}"
    return (f'<defs><pattern id="{pid}" x="{x}" y="{y}" width="{pitch}" height="{pitch}" patternUnits="userSpaceOnUse">'
            f'<circle cx="{pitch / 2}" cy="{pitch / 2}" r="{r}" fill="{fill}"/></pattern></defs>'
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#{pid})"/>')


def motif(right, top, h, dark=False):
    """Block composition anchored at the right edge, height h (from the design system cover)."""
    tint = "#232A55" if dark else INDIGO_TINT
    brand = DARK_BRAND if dark else INDIGO
    accent = RED_LIGHT if dark else RED
    ink = DARK_INK if dark else INK
    dots = brand
    u = h
    parts = []
    tx = right - 1.05 * u
    parts.append(f'<rect x="{tx}" y="{top}" width="{1.05 * u}" height="{u}" fill="{tint}"/>')
    parts.append(dot_grid(tx, top, 1.05 * u, u, u * 0.05, u * 0.0075, dots, None))
    parts.append(f'<rect x="{right - 0.58 * u}" y="{top + 0.34 * u}" width="{0.58 * u}" height="{0.66 * u}" fill="{brand}"/>')
    parts.append(f'<rect x="{right - 0.80 * u}" y="{top}" width="{0.22 * u}" height="{0.46 * u}" fill="{accent}"/>')
    parts.append(f'<rect x="{right - 1.17 * u}" y="{top + 0.70 * u}" width="{0.08 * u}" height="{0.08 * u}" fill="{ink}"/>')
    return "".join(parts)
