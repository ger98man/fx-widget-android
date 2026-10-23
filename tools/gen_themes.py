#!/usr/bin/env python3
"""
Generates widget themes: per-theme drawables (card background, rows, keys, chips,
swap capsule, decorative art as a vector), Themes.kt, and an SVG preview of each theme.

Run from the repo root:  python3 tools/gen_themes.py [preview_dir]
Art is drawn on a 360x420 canvas; the widget shows it with centerCrop.
"""
import math
import os
import random
import sys

RES = "app/src/main/res/drawable"
KT = "app/src/main/java/com/fxwidget/Themes.kt"
W, H = 360, 420


def circle_d(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0Z"


def ellipse_d(cx, cy, rx, ry, rot):
    a = math.radians(rot)
    dx, dy = rx * math.cos(a), rx * math.sin(a)
    return (f"M{cx - dx:.2f},{cy - dy:.2f}"
            f"A{rx:.2f},{ry:.2f} {rot:.1f} 1,0 {cx + dx:.2f},{cy + dy:.2f}"
            f"A{rx:.2f},{ry:.2f} {rot:.1f} 1,0 {cx - dx:.2f},{cy - dy:.2f}Z")


# ---- art primitives: ("fill", d, color, alpha) | ("stroke", d, color, width, alpha)
def C(cx, cy, r, color, a):
    return ("fill", circle_d(cx, cy, r), color, a)


def E(cx, cy, rx, ry, rot, color, a):
    return ("fill", ellipse_d(cx, cy, rx, ry, rot), color, a)


def L(points, color, width, a):
    d = "M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in points)
    return ("stroke", d, color, width, a)


def P(d, color, a):
    return ("fill", d, color, a)


def S(d, color, width, a):
    return ("stroke", d, color, width, a)


def crescent_d(cx, cy, r, dx, dy, r2):
    """Moon crescent: outer circle minus an offset circle, as one closed path of two arcs."""
    ix, iy = cx + dx, cy + dy
    d = math.hypot(dx, dy)
    a = (r * r - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(r * r - a * a)
    mx, my = cx + a * dx / d, cy + a * dy / d
    p1 = (mx + h * dy / d, my - h * dx / d)
    p2 = (mx - h * dy / d, my + h * dx / d)
    return (f"M{p1[0]:.2f},{p1[1]:.2f}A{r},{r} 0 1,0 {p2[0]:.2f},{p2[1]:.2f}"
            f"A{r2},{r2} 0 0,1 {p1[0]:.2f},{p1[1]:.2f}Z")


# anime-style motifs ------------------------------------------------------
def focus_lines(cx, cy, n, r0, r1, color, a, seed=1, width=(0.6, 2.2)):
    """Manga 'concentration lines': thin tapered wedges converging on a point."""
    rnd = random.Random(seed)
    out = []
    for k in range(n):
        ang = 2 * math.pi * k / n + rnd.uniform(-0.03, 0.03)
        inner = r0 * rnd.uniform(0.9, 1.4)
        w = rnd.uniform(*width) / 100
        x1, y1 = cx + inner * math.cos(ang), cy + inner * math.sin(ang)
        xa, ya = cx + r1 * math.cos(ang - w), cy + r1 * math.sin(ang - w)
        xb, yb = cx + r1 * math.cos(ang + w), cy + r1 * math.sin(ang + w)
        out.append(P(f"M{x1:.1f},{y1:.1f}L{xa:.1f},{ya:.1f}L{xb:.1f},{yb:.1f}Z", color, a * rnd.uniform(0.5, 1)))
    return out


def halftone(x0, y0, cols, rows, step, size, color, a):
    """Screen-tone dots; size(col, row) -> radius (0 skips)."""
    out = []
    for r in range(rows):
        for c in range(cols):
            rad = size(c, r)
            if rad > 0.25:
                out.append(C(x0 + c * step + (step / 2 if r % 2 else 0), y0 + r * step, rad, color, a))
    return out


def sparkle(cx, cy, r, color, a):
    """Four-point anime twinkle ✦."""
    k = r * 0.22
    return P(f"M{cx},{cy - r}Q{cx + k},{cy - k} {cx + r},{cy}Q{cx + k},{cy + k} {cx},{cy + r}"
             f"Q{cx - k},{cy + k} {cx - r},{cy}Q{cx - k},{cy - k} {cx},{cy - r}Z", color, a)


# ---------------------------------------------------------------- theme art
def art_space():
    rnd = random.Random(7)
    out = [E(250, 95, 150, 70, -20, "#8B5CF6", 0.13), E(80, 300, 170, 80, 15, "#3B82F6", 0.10),
           E(300, 380, 120, 50, -10, "#EC4899", 0.07)]
    for _ in range(90):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H)
        out.append(C(x, y, rnd.uniform(0.5, 1.5), "#FFFFFF", rnd.uniform(0.35, 0.9)))
    # two generic constellations
    for pts in ([(28, 40), (58, 30), (88, 44), (112, 38), (128, 66), (160, 60), (150, 34), (112, 38)],
                [(200, 205), (226, 186), (250, 210), (276, 190), (300, 214)]):
        out.append(L(pts, "#C7D2FE", 0.9, 0.5))
        for x, y in pts:
            out.append(C(x, y, 4.5, "#C7D2FE", 0.18))
            out.append(C(x, y, 1.9, "#FFFFFF", 0.95))
    for x, y, r in [(190, 28, 7), (60, 150, 5), (330, 140, 6), (140, 250, 4), (300, 300, 5)]:
        out.append(sparkle(x, y, r, "#FFFFFF", 0.85))
    # ringed planet
    out += [C(312, 52, 24, "#A78BFA", 0.55), C(304, 44, 24, "#C4B5FD", 0.25),
            S(ellipse_d(312, 52, 42, 10, -18), "#E9D5FF", 1.6, 0.6)]
    return out


def art_fire():
    rnd = random.Random(11)
    out = [E(180, 450, 280, 130, 0, "#F97316", 0.20), E(40, -20, 170, 95, 0, "#DC2626", 0.18),
           E(330, 200, 90, 160, 0, "#7F1D1D", 0.25)]
    for _ in range(55):
        y = H - rnd.random() ** 1.6 * H
        out.append(C(rnd.uniform(0, W), y, rnd.uniform(0.8, 2.4),
                     rnd.choice(["#F97316", "#FBBF24", "#EF4444"]), rnd.uniform(0.3, 0.85)))
    out += focus_lines(420, 60, 46, 150, 520, "#FFB4A0", 0.10, seed=4)
    out += halftone(0, 250, 14, 12, 14, lambda c, r: max(0.0, 3.2 - c * 0.28 - (11 - r) * 0.12), "#F97316", 0.22)
    # three claw scratches, top right
    for i in range(3):
        x1, x2 = 236 + i * 18, 300 + i * 18
        out.append(P(f"M{x1},16L{x1 + 5},16L{x2 + 1},132L{x2 - 1},132Z", "#EF4444", 0.42))
    # jagged iron edge along the top
    d = "M0,0" + "".join(f"L{x},{14 if (x // 24) % 2 else 4}" for x in range(0, W + 24, 24)) + f"L{W},0Z"
    out.append(P(d, "#3F3F46", 0.55))
    return out


def art_sea():
    rnd = random.Random(5)
    out = [E(70, 40, 150, 70, 10, "#38BDF8", 0.10)]
    for i, (base, amp, a) in enumerate([(300, 10, 0.10), (340, 12, 0.13), (380, 9, 0.18)]):
        pts = " ".join(f"{x},{base + amp * math.sin(x / 34 + i * 1.7):.1f}" for x in range(0, W + 10, 10))
        out.append(P(f"M0,{H}L{pts.replace(' ', 'L')}L{W},{H}Z", "#7DD3FC", a))
    for x, y, r in [(40, 60, 26), (74, 48, 32), (112, 60, 24), (90, 72, 26), (56, 76, 20)]:
        out.append(C(x, y, r, "#FFFFFF", 0.10))
    for x, y, r in [(70, 290, 6), (210, 320, 5), (300, 285, 7), (150, 360, 4), (330, 370, 5)]:
        out.append(sparkle(x, y, r, "#FFFFFF", 0.75))
    # compass rose
    cx, cy = 326, 38
    out.append(S(circle_d(cx, cy, 26), "#FCD34D", 1.4, 0.45))
    out.append(S(circle_d(cx, cy, 20), "#FCD34D", 0.8, 0.30))
    star = []
    for k in range(16):
        r = 32 if k % 4 == 0 else (13 if k % 2 == 0 else 6)
        ang = math.radians(k * 22.5 - 90)
        star.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    out.append(P("M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in star) + "Z", "#FCD34D", 0.40))
    out.append(C(cx, cy, 3, "#FDE68A", 0.9))
    # gulls
    for gx, gy, s in [(60, 120, 1.0), (96, 98, 0.8), (130, 132, 0.6)]:
        out.append(S(f"M{gx},{gy}q{6 * s:.1f},{-6 * s:.1f} {12 * s:.1f},0q{6 * s:.1f},{-6 * s:.1f} {12 * s:.1f},0",
                     "#F0F9FF", 1.6, 0.6))
    for _ in range(25):
        out.append(C(rnd.uniform(0, W), rnd.uniform(270, H), rnd.uniform(0.6, 1.4), "#FFFFFF", rnd.uniform(0.3, 0.7)))
    return out


def art_night():
    rnd = random.Random(3)
    out = [C(322, 40, 58, "#E0E7FF", 0.05), C(322, 40, 38, "#E0E7FF", 0.06),
           P(crescent_d(322, 40, 26, 12, -9, 23), "#F5F5F4", 0.9)]
    for _ in range(30):
        out.append(C(rnd.uniform(0, W), rnd.uniform(0, 180), rnd.uniform(0.5, 1.2), "#FFFFFF", rnd.uniform(0.3, 0.7)))
    out += halftone(0, 330, 26, 7, 14, lambda c, r: max(0.0, 0.4 + r * 0.32 - abs(c - 13) * 0.05), "#FFFFFF", 0.10)
    for x, y, r in [(40, 40, 5), (180, 70, 4), (240, 24, 6)]:
        out.append(sparkle(x, y, r, "#E0E7FF", 0.8))
    # ink brush slash
    out.append(P("M8,300Q170,205 352,166L355,173Q178,218 14,310Z", "#FFFFFF", 0.13))
    out.append(P("M30,326Q200,250 350,232L351,236Q204,258 34,331Z", "#FFFFFF", 0.07))
    # falling petals
    for _ in range(22):
        out.append(E(rnd.uniform(0, W), rnd.uniform(0, H), rnd.uniform(2.8, 4.2), rnd.uniform(1.6, 2.4),
                     rnd.uniform(0, 180), "#FBCFE8", rnd.uniform(0.45, 0.85)))
    return out


def art_wall():
    rnd = random.Random(9)
    out = []
    # big halftone sun sinking behind the wall
    out += halftone(190, 120, 16, 10, 12, lambda c, r: max(0.0, 4.6 - math.hypot(c - 7.5, r - 9) * 0.55), "#FDBA74", 0.30)
    out.append(C(280, 228, 52, "#F59E0B", 0.22))
    out += focus_lines(280, 228, 40, 70, 420, "#FED7AA", 0.07, seed=8)
    # flock of birds
    for _ in range(9):
        x, y, k = rnd.uniform(30, 220), rnd.uniform(40, 150), rnd.uniform(0.5, 1.0)
        out.append(S(f"M{x:.1f},{y:.1f}q{5 * k:.1f},{-5 * k:.1f} {10 * k:.1f},0q{5 * k:.1f},{-5 * k:.1f} {10 * k:.1f},0",
                     "#1C1917", 1.4, 0.7))
    # the wall: towering stone band with a slightly ragged top
    top = 230
    edge = "".join(f"L{x},{top + rnd.uniform(-3, 3):.1f}" for x in range(0, W + 20, 20))
    out.append(P(f"M0,{H}L0,{top}{edge}L{W},{H}Z", "#57534E", 0.75))
    out.append(P(f"M0,{top - 2}L{W},{top - 2}L{W},{top + 8}L0,{top + 8}Z", "#A8A29E", 0.35))
    # brick courses
    for i, y in enumerate(range(top + 22, H, 22)):
        out.append(L([(0, y), (W, y)], "#1C1917", 1.0, 0.45))
        off = 0 if i % 2 else 30
        for x in range(off, W, 60):
            out.append(L([(x, y - 22), (x, y)], "#1C1917", 1.0, 0.35))
    # warm rim light along the wall top
    out.append(P(f"M0,{top}L{W},{top}L{W},{top + 2}L0,{top + 2}Z", "#FDBA74", 0.5))
    return out


# --------------------------------------------------------------- palettes
THEMES = [
    dict(id="base", name="Базовая", sub="Тёмная с фуксией",
         bg=("#15181F", "#15181F"), row="#232733", key="#1F232C", chip="#3A4050",
         pill=("#FF7AD6", "#C2187F"), text="#FFFFFF", secondary="#AEB5C6", code="#C4CAD8",
         accent="#FF4FC3", result="#FF4FC3", swap_icon="#FFFFFF", art=[]),
    dict(id="space", name="Космос", sub="Звёзды и созвездия",
         bg=("#0B1026", "#1E1548"), row="#B31A2147", key="#B3151B3A", chip="#4B5A9A",
         pill=("#A5B4FC", "#6366F1"), text="#F1F5FF", secondary="#A8B6E6", code="#C7D2FE",
         accent="#7DD3FC", result="#7DD3FC", swap_icon="#FFFFFF", art=art_space()),
    dict(id="fire", name="Пламя и сталь", sub="Багровый и раскалённый металл",
         bg=("#1C1110", "#2E0C0A"), row="#B33A1513", key="#B32A1210", chip="#7A3226",
         pill=("#FF7A45", "#B91C1C"), text="#FFF4EC", secondary="#DDB2A3", code="#F5C9B8",
         accent="#FF5A36", result="#FF7A45", swap_icon="#FFFFFF", art=art_fire()),
    dict(id="sea", name="Открытое море", sub="Волны, компас и золото",
         bg=("#062A3F", "#0B4A5E"), row="#B30E3A52", key="#B30A3046", chip="#2E7A92",
         pill=("#FDE68A", "#D97706"), text="#F0FBFF", secondary="#A3D3E0", code="#CDEEF6",
         accent="#FBBF24", result="#FCD34D", swap_icon="#3B2300", art=art_sea()),
    dict(id="night", name="Ночной клинок", sub="Луна, тушь и лепестки",
         bg=("#0A0A0C", "#17171E"), row="#B31C1C24", key="#B317171D", chip="#45455A",
         pill=("#DBEAFE", "#60A5FA"), text="#FAFAFA", secondary="#A1A1AA", code="#D4D4D8",
         accent="#93C5FD", result="#BFDBFE", swap_icon="#0B1B33", art=art_night()),
    dict(id="wall", name="За стеной", sub="Каменная стена и закатное небо",
         bg=("#4A2418", "#1A1512"), row="#B32A1E19", key="#B3231B17", chip="#7C6A55",
         pill=("#BBF7D0", "#15803D"), text="#FFF7ED", secondary="#D6C4AE", code="#F1E2CC",
         accent="#86EFAC", result="#86EFAC", swap_icon="#052E16", art=art_wall()),
]


def argb(c):
    """#RRGGBB or #AARRGGBB -> 0xAARRGGBB literal for Kotlin."""
    c = c.lstrip("#")
    if len(c) == 6:
        c = "FF" + c
    return f"0x{c.upper()}.toInt()"


def write(name, body):
    with open(os.path.join(RES, name), "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n' + body)


def shape(color, radius, stroke=None):
    s = f'    <solid android:color="{color}" />\n' if color else ""
    if stroke:
        s += f'    <stroke android:width="1dp" android:color="{stroke}" />\n'
    return ('<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'{s}    <corners android:radius="{radius}dp" />\n</shape>\n')


def vector(items):
    paths = []
    for it in items:
        if it[0] == "fill":
            _, d, color, a = it
            paths.append(f'    <path android:fillColor="{color}" android:fillAlpha="{a:.2f}" android:pathData="{d}" />')
        else:
            _, d, color, w, a = it
            paths.append(f'    <path android:strokeColor="{color}" android:strokeAlpha="{a:.2f}" '
                         f'android:strokeWidth="{w}" android:strokeLineCap="round" android:strokeLineJoin="round" '
                         f'android:pathData="{d}" />')
    if not paths:  # base theme: transparent 1-path vector
        paths.append('    <path android:fillColor="#00000000" android:pathData="M0,0h1v1h-1z" />')
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{W}dp" android:height="{H}dp" android:viewportWidth="{W}" android:viewportHeight="{H}">\n'
            + "\n".join(paths) + "\n</vector>\n")


def svg_items(items):
    o = ""
    for it in items:
        if it[0] == "fill":
            _, d, color, a = it
            o += f'<path d="{d}" fill="{color}" fill-opacity="{a:.2f}"/>'
        else:
            _, d, color, w, a = it
            o += (f'<path d="{d}" fill="none" stroke="{color}" stroke-opacity="{a:.2f}" stroke-width="{w}" '
                  f'stroke-linecap="round" stroke-linejoin="round"/>')
    return o


def svg_color(c):
    c = c.lstrip("#")
    if len(c) == 8:
        return f"#{c[2:]}", int(c[:2], 16) / 255
    return f"#{c}", 1.0


def preview(t, x0, y0, w=240, h=280):
    """Simplified widget mock using the theme's real palette and art."""
    fid = f"g_{t['id']}"
    o = (f'<defs><linearGradient id="{fid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{t["bg"][0]}"/>'
         f'<stop offset="1" stop-color="{t["bg"][1]}"/></linearGradient>'
         f'<linearGradient id="p_{t["id"]}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{t["pill"][0]}"/>'
         f'<stop offset="1" stop-color="{t["pill"][1]}"/></linearGradient>'
         f'<clipPath id="c_{t["id"]}"><rect x="{x0}" y="{y0}" width="{w}" height="{h}" rx="22"/></clipPath></defs>')
    o += f'<g clip-path="url(#c_{t["id"]})"><rect x="{x0}" y="{y0}" width="{w}" height="{h}" fill="url(#{fid})"/>'
    # art with centerCrop
    sc = max(w / W, h / H)
    o += (f'<g transform="translate({x0 + (w - W * sc) / 2:.1f},{y0 + (h - H * sc) / 2:.1f}) scale({sc:.4f})">'
          + svg_items(t["art"]) + "</g>")
    p = 8
    rh = 52
    rc, ra = svg_color(t["row"])
    o += f'<rect x="{x0 + p}" y="{y0 + p}" width="{w - 2 * p}" height="{rh}" rx="14" fill="{rc}" fill-opacity="{ra:.2f}"/>'
    F = 'font-family="Roboto, sans-serif" font-weight="700"'
    o += f'<text x="{x0 + p + 12}" y="{y0 + p + 32}" font-size="13" fill="{t["code"]}" {F}>THB</text>'
    o += f'<text x="{x0 + w - p - 10}" y="{y0 + p + 38}" font-size="30" fill="{t["text"]}" text-anchor="end" {F}>1,250 ฿</text>'
    o += f'<text x="{x0 + p + 12}" y="{y0 + p + rh + 36}" font-size="13" fill="{t["code"]}" {F}>EUR</text>'
    o += f'<text x="{x0 + w - p - 10}" y="{y0 + p + rh + 42}" font-size="30" fill="{t["result"]}" text-anchor="end" {F}>33.13 €</text>'
    o += (f'<rect x="{x0 + p + 46}" y="{y0 + p + rh - 22}" width="26" height="44" rx="13" fill="url(#p_{t["id"]})" '
          f'stroke="{t["bg"][0]}" stroke-width="3"/>')
    o += (f'<g stroke="{t["swap_icon"]}" stroke-width="2" fill="none" stroke-linecap="round">'
          f'<path d="M{x0 + p + 54},{y0 + p + rh + 12}V{y0 + p + rh - 12}M{x0 + p + 50},{y0 + p + rh - 8}l4,-4 4,4"/>'
          f'<path d="M{x0 + p + 64},{y0 + p + rh - 12}V{y0 + p + rh + 12}M{x0 + p + 60},{y0 + p + rh + 8}l4,4 4,-4"/></g>')
    o += (f'<text x="{x0 + p + 4}" y="{y0 + 138}" font-size="10" fill="{t["secondary"]}" '
          f'font-family="Roboto, sans-serif">1 € = 37.73 ฿ · 0 минут назад</text>')
    kc, ka = svg_color(t["key"])
    kw = (w - 2 * p - 3 * 4) / 4
    for r, row in enumerate([["7", "8", "9", "⌫"], ["4", "5", "6", "0"], ["1", "2", "3", "."]]):
        for c, ch in enumerate(row):
            kx, ky = x0 + p + c * (kw + 4), y0 + 146 + r * 32
            o += f'<rect x="{kx:.1f}" y="{ky}" width="{kw:.1f}" height="28" rx="9" fill="{kc}" fill-opacity="{ka:.2f}"/>'
            col = t["secondary"] if ch in "⌫." else t["text"]
            o += (f'<text x="{kx + kw / 2:.1f}" y="{ky + 19}" font-size="15" fill="{col}" text-anchor="middle" '
                  f'font-family="Roboto, sans-serif">{ch}</text>')
    cw = (w - 2 * p - 6 * 3) / 7
    for i, ch in enumerate(["+10", "+50", "+100", "+500", "+1k", "+5k", "C"]):
        cx = x0 + p + i * (cw + 3)
        o += (f'<rect x="{cx:.1f}" y="{y0 + 248}" width="{cw:.1f}" height="24" rx="12" fill="none" '
              f'stroke="{t["chip"]}"/>')
        o += (f'<text x="{cx + cw / 2:.1f}" y="{y0 + 264}" font-size="9" fill="{t["accent"] if ch == "C" else t["text"]}" '
              f'text-anchor="middle" {F}>{ch}</text>')
    o += "</g>"
    o += (f'<text x="{x0 + w / 2}" y="{y0 + h + 22}" font-size="15" fill="#FFFFFF" text-anchor="middle" {F}>'
          f'{t["name"]}</text>')
    return o


def main():
    os.makedirs(RES, exist_ok=True)
    for t in THEMES:
        i = t["id"]
        write(f"theme_{i}_bg.xml",
              '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
              f'    <gradient android:angle="270" android:startColor="{t["bg"][0]}" android:endColor="{t["bg"][1]}" />\n'
              '    <corners android:radius="28dp" />\n</shape>\n')
        write(f"theme_{i}_row.xml", shape(t["row"], 18))
        write(f"theme_{i}_key.xml", shape(t["key"], 12))
        write(f"theme_{i}_chip.xml", shape("#00000000", 16, stroke=t["chip"]))
        write(f"theme_{i}_card.xml", shape(None, 16, stroke=t["chip"]))
        write(f"theme_{i}_pill.xml",
              '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
              f'    <item><shape><solid android:color="#2E{t["pill"][1].lstrip("#")}" /><corners android:radius="26dp" /></shape></item>\n'
              '    <item android:bottom="3dp" android:left="3dp" android:right="3dp" android:top="3dp">\n'
              f'        <shape><solid android:color="{t["bg"][0]}" /><corners android:radius="23dp" /></shape></item>\n'
              '    <item android:bottom="5dp" android:left="5dp" android:right="5dp" android:top="5dp">\n'
              f'        <shape><gradient android:angle="270" android:startColor="{t["pill"][0]}" android:endColor="{t["pill"][1]}" />\n'
              '            <stroke android:width="1dp" android:color="#59FFFFFF" /><corners android:radius="21dp" /></shape></item>\n'
              '    <item android:bottom="40dp" android:left="8dp" android:right="8dp" android:top="8dp">\n'
              '        <shape><gradient android:angle="270" android:startColor="#59FFFFFF" android:endColor="#00FFFFFF" />\n'
              '            <corners android:radius="18dp" /></shape></item>\n</layer-list>\n')
        write(f"theme_{i}_art.xml", vector(t["art"]))

    entries = []
    for t in THEMES:
        i = t["id"]
        entries.append(
            f'        WidgetTheme(\n'
            f'            id = "{i}", name = "{t["name"]}", subtitle = "{t["sub"]}",\n'
            f'            bg = R.drawable.theme_{i}_bg, row = R.drawable.theme_{i}_row, key = R.drawable.theme_{i}_key,\n'
            f'            chip = R.drawable.theme_{i}_chip, pill = R.drawable.theme_{i}_pill, art = R.drawable.theme_{i}_art,\n'
            f'            text = {argb(t["text"])}, secondary = {argb(t["secondary"])}, code = {argb(t["code"])},\n'
            f'            accent = {argb(t["accent"])}, result = {argb(t["result"])}, swapIcon = {argb(t["swap_icon"])},\n'
            f'        ),')
    with open(KT, "w") as f:
        f.write(
            "// GENERATED by tools/gen_themes.py — edit the script, not this file.\n"
            "package com.fxwidget\n\n"
            "/** Widget colour theme: drawable resources + text colours (ARGB). */\n"
            "data class WidgetTheme(\n"
            "    val id: String, val name: String, val subtitle: String,\n"
            "    val bg: Int, val row: Int, val key: Int, val chip: Int, val pill: Int, val art: Int,\n"
            "    val text: Int, val secondary: Int, val code: Int, val accent: Int, val result: Int, val swapIcon: Int,\n"
            ")\n\n"
            "object Themes {\n"
            "    val ALL = listOf(\n" + "\n".join(entries) + "\n    )\n\n"
            "    fun byId(id: String?): WidgetTheme = ALL.firstOrNull { it.id == id } ?: ALL[0]\n"
            "}\n")

    if len(sys.argv) > 1:
        out = sys.argv[1]
        cols, cw, chh, gap = 3, 240, 280, 20  # 6 themes -> 3x2 grid
        rows = math.ceil(len(THEMES) / cols)
        tw, th = cols * cw + (cols + 1) * gap, rows * (chh + 40) + gap * 2
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{tw}" height="{th}">'
               f'<rect width="{tw}" height="{th}" fill="#4B5A78"/>')
        for k, t in enumerate(THEMES):
            r, c = divmod(k, cols)
            svg += preview(t, gap + c * (cw + gap), gap + r * (chh + 40 + gap / 2))
        svg += "</svg>"
        with open(out, "w") as f:
            f.write(svg)


if __name__ == "__main__":
    main()
