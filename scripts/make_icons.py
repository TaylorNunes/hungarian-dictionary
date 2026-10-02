#!/usr/bin/env python3
"""Generate the app icons (a stylised "ő" on a green tile) as PNG and SVG. Standard library only."""

import math
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / 'public' / 'icons'
BG = (0x2F, 0x6B, 0x4F)
FG = (0xF7, 0xF5, 0xEF)

# Glyph geometry in unit coordinates (0..1), before scaling into the safe zone.
RING = (0.5, 0.62, 0.25, 0.145)                     # cx, cy, outer r, inner r
ACCENTS = [((0.385, 0.305), (0.455, 0.135)), ((0.565, 0.305), (0.635, 0.135))]
STROKE = 0.036                                      # accent half-width


def seg_dist(px, py, a, b):
    (ax, ay), (bx, by) = a, b
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def glyph(x, y):
    cx, cy, ro, ri = RING
    d = math.hypot(x - cx, y - cy)
    if ri <= d <= ro:
        return True
    return any(seg_dist(x, y, a, b) <= STROKE for a, b in ACCENTS)


def rounded(x, y, r):
    qx, qy = abs(x - 0.5) - (0.5 - r), abs(y - 0.5) - (0.5 - r)
    return math.hypot(max(qx, 0), max(qy, 0)) <= r


def render(size, maskable):
    scale = 0.72 if maskable else 0.92
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
                    inside = True if maskable else rounded(x, y, 0.22)
                    if not inside:
                        continue
                    bg += 1
                    gx, gy = 0.5 + (x - 0.5) / scale, 0.5 + (y - 0.5) / scale
                    if glyph(gx, gy):
                        fg += 1
            n = ss * ss
            a = bg / n
            f = fg / bg if bg else 0
            rgb = [round(BG[k] * (1 - f) + FG[k] * f) for k in range(3)]
            row += bytes(rgb + [round(a * 255)])
        rows.append(bytes(row))
    raw = zlib.compress(b''.join(rows), 9)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data))

    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', raw) + chunk(b'IEND', b''))


def svg():
    cx, cy, ro, ri = RING
    s = 0.92
    t = lambda v: 0.5 + (v - 0.5) * s
    mid = (ro + ri) / 2
    accents = ''.join(
        f'<line x1="{t(a[0]) * 100:.2f}" y1="{t(a[1]) * 100:.2f}" x2="{t(b[0]) * 100:.2f}" y2="{t(b[1]) * 100:.2f}"/>'
        for a, b in ACCENTS)
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">'
        f'<rect width="100" height="100" rx="22" fill="#{BG[0]:02x}{BG[1]:02x}{BG[2]:02x}"/>'
        f'<g fill="none" stroke="#{FG[0]:02x}{FG[1]:02x}{FG[2]:02x}">'
        f'<circle cx="{t(cx) * 100:.2f}" cy="{t(cy) * 100:.2f}" r="{mid * s * 100:.2f}" stroke-width="{(ro - ri) * s * 100:.2f}"/>'
        f'<g stroke-width="{STROKE * 2 * s * 100:.2f}" stroke-linecap="round">{accents}</g></g></svg>\n')


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'icon-192.png').write_bytes(render(192, False))
    (OUT / 'icon-512.png').write_bytes(render(512, False))
    (OUT / 'icon-512-maskable.png').write_bytes(render(512, True))
    (OUT / 'icon.svg').write_text(svg())
    print('icons written to', OUT)
