"""Builds web/src/main/resources/static/icons.svg: the Lucide icons of the site navigation as one SVG sprite.

The site references them with <svg><use href="/static/icons.svg#name"/></svg>: same icons as the
application (D-025), served by our own server, no inline style (the CSP forbids it).
Run from the repository root: python -I tools/icons/make_web_sprite.py
"""
import re
from pathlib import Path

ICONS = [
    "radio", "newspaper", "map", "megaphone", "hand-fist", "eye", "book-open", "vote", "scroll-text",
    "message-square", "circle-user-round", "menu", "chevron-down", "life-buoy", "phone", "calendar",
    "external-link", "search",
]

root = Path(__file__).resolve().parents[2]
source = root / "tools" / "icons" / "lucide"
symbols = []
for name in ICONS:
    svg = (source / f"{name}.svg").read_text(encoding="utf-8")
    body = re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)
    body = "".join(line.strip() for line in body.strip().splitlines())
    symbols.append(f'<symbol id="{name}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">{body}</symbol>')

license_note = "Lucide icons, ISC License, see tools/icons/lucide/LICENSE"
out = (
    '<svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="2" '
    'stroke-linecap="round" stroke-linejoin="round">'
    f"<!-- {license_note} -->\n" + "\n".join(symbols) + "\n</svg>\n"
)
(root / "web" / "src" / "main" / "resources" / "static" / "icons.svg").write_text(out, encoding="utf-8", newline="\n")
print(f"{len(symbols)} icons")
