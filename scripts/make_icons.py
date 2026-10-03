#!/usr/bin/env python3
"""Generate the Szókert icons: a seedling "ő" (an o with two leaves for its accents) on a dark grey tile.

Writes public/icons/icon-{192,512}.png, icon-512-maskable.png and icon.svg, and prints the glyph's
SVG markup used by src/components/Logo.svelte. Standard library only.
"""

import math
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / 'public' / 'icons'
BG = (0x12, 0x12, 0x12)      # --bg (dark)
FG = (0x8F, 0xE3, 0x88)      # --accent (fresh green)

# Glyph geometry in unit coordinates (0..1, y down), before scaling into the safe zone.
RING = (0.5, 0.635, 0.215, 0.13)              # cx, cy, outer r, inner r
STEM = ((0.5, 0.43), (0.5, 0.36), 0.026)      # bottom, top, half-width
# Each leaf: base, tip, and the bulge of its quadratic-curve sides (max half-width = BULGE / 2).
LEAVES = [((0.495, 0.37), (0.285, 0.165)), ((0.505, 0.37), (0.715, 0.165))]
BULGE = 0.15


def in_leaf(x, y, base, tip):
    """Inside the leaf bounded by two quadratic curves base→tip (half-width 2·BULGE·t·(1−t))."""
    (bx, by), (tx, ty) = base, tip
    length = math.hypot(tx - bx, ty - by)
    ux, uy = (tx - bx) / length, (ty - by) / length
    t = ((x - bx) * ux + (y - by) * uy) / length
    d = abs(-(x - bx) * uy + (y - by) * ux)
    return 0 <= t <= 1 and d <= 2 * BULGE * t * (1 - t)


def in_stem(x, y):
    (x0, y0), (x1, y1), hw = STEM
    return abs(x - x0) <= hw and min(y0, y1) <= y <= max(y0, y1)


def glyph(x, y):
    cx, cy, ro, ri = RING
    if ri <= math.hypot(x - cx, y - cy) <= ro:
        return True
    return in_stem(x, y) or any(in_leaf(x, y, b, t) for b, t in LEAVES)


def rounded(x, y, r):
    qx, qy = abs(x - 0.5) - (0.5 - r), abs(y - 0.5) - (0.5 - r)
    return math.hypot(max(qx, 0), max(qy, 0)) <= r


def render(size, maskable):
    scale = 0.72 if maskable else 0.9
    ss = 4
    rows = []
    for j in range(size):
        row = bytearray([0])
        for i in range(size):
            bg = fg = 0
            for sj in range(ss):
                for si in range(ss):
                    x = (i + (si + 0.5) / ss) / size
                    y = (j + (sj + 0.5) / ss) / size
                    if not (maskable or rounded(x, y, 0.22)):
                        continue
                    bg += 1
                    if glyph(0.5 + (x - 0.5) / scale, 0.5 + (y - 0.5) / scale):
                        fg += 1
            a = bg / (ss * ss)
            f = fg / bg if bg else 0
            rgb = [round(BG[k] * (1 - f) + FG[k] * f) for k in range(3)]
            row += bytes(rgb + [round(a * 255)])
        rows.append(bytes(row))

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data))

    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b''))


def glyph_svg(scale=1.0):
    """The glyph as SVG elements in a 0..100 box, filled/stroked with currentColor."""
    t = lambda v: (0.5 + (v - 0.5) * scale) * 100
    cx, cy, ro, ri = RING
    parts = [f'<circle cx="{t(cx):.2f}" cy="{t(cy):.2f}" r="{(ro + ri) / 2 * scale * 100:.2f}" fill="none" '
             f'stroke="currentColor" stroke-width="{(ro - ri) * scale * 100:.2f}"/>']
    (sx0, sy0), (sx1, sy1), hw = STEM
    parts.append(f'<rect x="{t(sx0 - hw):.2f}" y="{t(min(sy0, sy1)):.2f}" width="{2 * hw * scale * 100:.2f}" '
                 f'height="{abs(sy1 - sy0) * scale * 100:.2f}" fill="currentColor"/>')
    for (bx, by), (tx, ty) in LEAVES:
        length = math.hypot(tx - bx, ty - by)
        nx, ny = -(ty - by) / length, (tx - bx) / length
        mx, my = (bx + tx) / 2, (by + ty) / 2
        c1 = (mx + nx * BULGE, my + ny * BULGE)
        c2 = (mx - nx * BULGE, my - ny * BULGE)
        parts.append(f'<path d="M{t(bx):.2f} {t(by):.2f}Q{t(c1[0]):.2f} {t(c1[1]):.2f} {t(tx):.2f} {t(ty):.2f}'
                     f'Q{t(c2[0]):.2f} {t(c2[1]):.2f} {t(bx):.2f} {t(by):.2f}Z" fill="currentColor"/>')
    return ''.join(parts)


def icon_svg():
    hexc = lambda c: '#' + ''.join(f'{v:02x}' for v in c)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">'
            f'<rect width="100" height="100" rx="22" fill="{hexc(BG)}"/>'
            f'<g color="{hexc(FG)}">{glyph_svg(0.9)}</g></svg>\n')


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'icon-192.png').write_bytes(render(192, False))
    (OUT / 'icon-512.png').write_bytes(render(512, False))
    (OUT / 'icon-512-maskable.png').write_bytes(render(512, True))
    (OUT / 'icon.svg').write_text(icon_svg())
    print('icons written to', OUT)
    print('Logo.svelte glyph (viewBox 0 0 100 100):')
    print(glyph_svg(1.0))
