"""
Impact frames for DOA: four hand-inked, full-colour frames per pillar (28 in all), played over ~6 ticks the
moment a pillar plants (and at the climax of its ability).

Every frame is painted from scratch with dry-brush strokes, torn paper, ink splatter and cracks - deliberately
rough and uneven, like a manga artist's frame slapped down in ink, not a clean vector drawing.

  0  KANJI   - raw paper, the pillar's kanji in a huge dry-brush stroke, radiating speed strokes, a coloured seal
  1  INVERT  - ink-black night, speed strokes in the pillar's colour, the pillar silhouette slamming down, paper-white kanji
  2  FIELD   - a flood of the pillar's colour, the planted silhouette and the earth splitting in black, an enso circle
  3  SHATTER - the frame cracks apart like a broken ink slab, colour bleeding through the fissures
Output: src/main/resources/assets/doa/textures/gui/impact/<id>_<k>.png  (768 x 432)
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from doa_textures import noise, glyph, IDS

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/doa/textures/gui/impact"
W, H = 768, 432
COLORS = {
    "death": (0.86, 0.06, 0.12), "redemption": (0.95, 0.72, 0.22), "wrath": (1.0, 0.36, 0.10),
    "soul": (0.26, 0.80, 0.55), "fate": (0.62, 0.38, 1.0), "silence": (0.93, 0.90, 0.82), "void": (1.0, 0.32, 0.64),
}
INK = np.array([0.035, 0.03, 0.035])
PAPER = np.array([0.93, 0.89, 0.80])
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)


def paper(seed):
    n = noise(H, W, 24, seed, 4)
    fib = noise(H, W * 6, 2, seed + 1, 2)[:, ::6]
    p = PAPER[None, None] * (0.88 + 0.1 * n[..., None]) - (fib[..., None] > 0.82) * 0.05
    return np.clip(p, 0, 1)


def rough(mask, seed, amount=0.35, cell=6):
    """Break a clean mask's edge up into ragged, bleeding ink."""
    n = noise(H, W, cell, seed, 3)
    soft = ndimage.gaussian_filter(mask.astype(np.float32), 1.6)
    return np.clip((soft - 0.5 + (n - 0.5) * amount) * 6 + 0.5, 0, 1)


def dry(mask, ang_field, seed, density=0.55):
    """Dry-brush striations: hairs of the brush running along the stroke direction."""
    # sample streak noise in a frame rotated along the stroke: use the radial/angular coordinate given
    n = noise(H, W, 2, seed, 2)
    s = ndimage.map_coordinates(noise(64, 2048, 3, seed + 7, 2), [np.clip(ang_field[1] * 63, 0, 63), ang_field[0] * 2047 % 2047], order=1)
    hairs = np.clip((s - (1 - density)) * 5, 0, 1)
    return mask * np.clip(hairs * 0.85 + n * 0.25, 0, 1)


def polar(cx, cy):
    dx, dy = XX - cx, YY - cy
    r = np.hypot(dx / W, dy / H) * 2
    a = (np.arctan2(dy, dx) + math.pi) / (2 * math.pi)
    return a, np.clip(r / 1.5, 0, 1), r


def speed_strokes(cx, cy, seed, count=90, inner=0.18):
    rr = np.random.default_rng(seed)
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        a = rr.uniform(0, 2 * math.pi)
        r0 = rr.uniform(inner, inner + 0.25) * W * 0.6
        r1 = r0 + rr.uniform(0.25, 0.9) * W * 0.7
        w0 = rr.uniform(1.5, 10)
        steps = 10
        for k in range(steps):
            t0, t1 = k / steps, (k + 1) / steps
            ra, rb = r0 + (r1 - r0) * t0, r0 + (r1 - r0) * t1
            wa = max(1, w0 * (0.3 + 0.7 * t1) * (1 - 0.6 * t1 ** 3))
            d.line([(cx + math.cos(a) * ra, cy + math.sin(a) * ra * 0.75), (cx + math.cos(a) * rb, cy + math.sin(a) * rb * 0.75)],
                   fill=255, width=int(wa))
    m = np.asarray(img).astype(np.float32) / 255
    a, rn, _ = polar(cx, cy)
    return dry(m, (a, rn), seed, 0.6)


def splatter(seed, count=26, cx=W / 2, cy=H / 2, spread=1.0, big=(8, 34)):
    rr = np.random.default_rng(seed)
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        a = rr.uniform(0, 2 * math.pi)
        dist = rr.uniform(0.25, 1.0) * spread
        x, y = cx + math.cos(a) * dist * W / 2, cy + math.sin(a) * dist * H / 2
        r = rr.uniform(*big)
        d.ellipse([x - r, y - r * rr.uniform(0.6, 1), x + r, y + r], fill=255)
        for _ in range(rr.integers(3, 12)):
            b = a + rr.normal(0, 0.25)
            q = rr.uniform(1.0, 3.2) * r
            s = rr.uniform(1, 4)
            px, py = x + math.cos(b) * q, y + math.sin(b) * q
            d.ellipse([px - s, py - s, px + s, py + s], fill=255)
            d.line([(x, y), (px, py)], fill=255, width=max(1, int(s * 0.8)))
    return rough(np.asarray(img) > 0, seed + 3, 0.5, 4)


def kanji(pid, size, cx, cy, seed):
    g = glyph(pid, size)
    m = np.zeros((H, W), np.float32)
    x0, y0 = int(cx - size / 2), int(cy - size / 2)
    xs, ys = max(0, x0), max(0, y0)
    xe, ye = min(W, x0 + size), min(H, y0 + size)
    m[ys:ye, xs:xe] = g[ys - y0:ye - y0, xs - x0:xe - x0]
    m = rough(m > 0.35, seed, 0.45, 5)
    # dry-brush hairs running roughly vertically (the dominant stroke direction of a brushed glyph)
    hairs = noise(H, W * 8, 2, seed + 5, 2)[:, ::8]
    hairs = ndimage.zoom(noise(H // 6 + 1, W, 2, seed + 9, 2), (6, 1), order=1)[:H, :W]
    return m * np.clip(0.55 + hairs * 0.6, 0, 1)


def silhouette(cx, base_y, height, seed, sink=0.0):
    """The new pillar seen from below: stepped plinth, tapered obelisk, gold bands, pyramidion, halo ring, crystal."""
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    u = height / 14.0  # one block
    top = base_y - 11.0 * u
    for r, z0, z1 in ((2.05, -0.7, 0.05), (1.75, 0.05, 0.5), (1.42, 0.5, 0.9)):
        d.rectangle([cx - r * u, base_y - z1 * u, cx + r * u, base_y - z0 * u + 30], fill=255)
    d.polygon([(cx - 0.85 * u, base_y - 0.9 * u), (cx + 0.85 * u, base_y - 0.9 * u), (cx + 0.6 * u, top), (cx - 0.6 * u, top)], fill=255)
    for z in (1.25, 5.9, 10.5):
        w = 0.85 + (0.6 - 0.85) * (z - 0.9) / 10.1 + 0.07
        d.rectangle([cx - w * u, base_y - (z + 0.13) * u, cx + w * u, base_y - (z - 0.13) * u], fill=255)
    d.polygon([(cx - 0.65 * u, top), (cx + 0.65 * u, top), (cx, top - 1.5 * u)], fill=255)
    ry = base_y - 8.3 * u
    d.ellipse([cx - 2.25 * u, ry - 0.42 * u, cx + 2.25 * u, ry + 0.42 * u], outline=255, width=max(2, int(0.14 * u)))
    d.ellipse([cx - 1.75 * u, ry - 0.3 * u, cx + 1.75 * u, ry + 0.3 * u], outline=255, width=max(2, int(0.1 * u)))
    cy = base_y - 13.3 * u
    d.polygon([(cx, cy - 0.9 * u), (cx + 0.38 * u, cy), (cx, cy + 0.55 * u), (cx - 0.38 * u, cy)], fill=255)
    return rough(np.asarray(img) > 0, seed, 0.3, 4)


def cracks(cx, cy, seed, count=14, length=260, width=5):
    rr = np.random.default_rng(seed)
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)

    def walk(x, y, a, L, w, depth):
        pts = [(x, y)]
        while L > 0:
            step = rr.uniform(6, 16)
            a += rr.normal(0, 0.35)
            x, y = x + math.cos(a) * step, y + math.sin(a) * step * 0.7
            pts.append((x, y))
            L -= step
            if depth < 3 and rr.random() < 0.08:
                walk(x, y, a + rr.choice([-1, 1]) * rr.uniform(0.4, 1.1), L * 0.6, max(1, w * 0.6), depth + 1)
        for i in range(len(pts) - 1):
            k = i / max(1, len(pts) - 1)
            d.line([pts[i], pts[i + 1]], fill=255, width=max(1, int(w * (1 - k * 0.8))))

    for _ in range(count):
        a = rr.uniform(0, 2 * math.pi)
        walk(cx, cy, a, rr.uniform(0.5, 1.0) * length, width, 0)
    return rough(np.asarray(img) > 0, seed + 1, 0.25, 3)


def enso(cx, cy, r, seed, width=22):
    """A single dry zen brush circle, thick where the brush lands, splitting into hairs where it lifts."""
    rr = np.random.default_rng(seed)
    a0 = rr.uniform(0, 2 * math.pi)
    dx, dy = XX - cx, YY - cy
    ang = (np.arctan2(dy, dx) - a0) % (2 * math.pi) / (2 * math.pi)
    rad = np.hypot(dx, dy)
    sweep = 0.9
    t = np.clip(ang / sweep, 0, 1)
    w = width * (1.2 - 0.9 * t ** 1.5) * (ang < sweep)
    wob = r * (1 + 0.03 * np.sin(ang * 14 + seed))
    band = np.clip(1 - np.abs(rad - wob) / np.maximum(w, 0.1), 0, 1)
    hairs = ndimage.map_coordinates(noise(64, 2048, 2, seed + 4, 2), [np.clip((rad - wob) / (2 * width + 1) * 32 + 32, 0, 63), ang * 2047], order=1)
    return np.clip(band * 3, 0, 1) * np.clip((hairs - t * 0.55) * 3, 0, 1)


def seal(cx, cy, s, seed):
    """A red-ish hanko stamp: square frame, uneven inking."""
    m = ((np.abs(XX - cx) < s) & (np.abs(YY - cy) < s)) & ~((np.abs(XX - cx) < s * 0.78) & (np.abs(YY - cy) < s * 0.78))
    inner = ((np.abs(XX - cx) < s * 0.62) & (np.abs(YY - cy) < s * 0.62))
    n = noise(H, W, 3, seed, 2)
    return rough(m | (inner & (n > 0.55)), seed + 2, 0.5, 3) * (n * 0.4 + 0.7)


def torn_border(seed, thick=18):
    n = noise(H, W, 10, seed, 3)
    edge = np.minimum.reduce([XX, W - 1 - XX, YY, H - 1 - YY])
    return np.clip((thick * (0.4 + n) - edge) / 4, 0, 1)


def comp(base, layer, color, mask):
    return base * (1 - mask[..., None]) + np.asarray(color)[None, None] * mask[..., None]


def frames(pid, idx):
    col = np.array(COLORS[pid])
    dark = col * 0.25
    s = 3000 + idx * 97
    rr = np.random.default_rng(s)
    out = []

    # 0 KANJI
    cx, cy = W / 2 + rr.uniform(-60, 60), H / 2 + rr.uniform(-20, 20)
    img = paper(s)
    img = comp(img, None, INK, speed_strokes(cx, cy, s + 1, 110, 0.32) * 0.92)
    img = comp(img, None, INK * 0.6 + col * 0.4, splatter(s + 2, 18, cx, cy, 1.1, (4, 16)) * 0.8)
    img = comp(img, None, INK, kanji(pid, 400, cx, cy, s + 3))
    img = comp(img, None, col * 0.85, seal(W - 70 + rr.uniform(-15, 5), H - 64, 30, s + 4))
    img = comp(img, None, INK, torn_border(s + 5, 22))
    out.append(img)

    # 1 INVERT
    cx2 = W / 2 + rr.uniform(-140, 140)
    img = np.ones((H, W, 3)) * INK[None, None]
    img = img + noise(H, W, 30, s + 10, 3)[..., None] * dark[None, None] * 0.8
    img = comp(img, None, col, speed_strokes(cx2, H * 0.98, s + 11, 140, 0.05) * 0.95)
    sil = silhouette(cx2, H * 0.98, H * 1.15, s + 12)
    glow = np.clip(ndimage.gaussian_filter(sil, 10) * 2.2 - sil, 0, 1)
    img = comp(img, None, col * 0.7 + 0.3, glow * 0.85)
    img = comp(img, None, INK, sil)
    kx = W * (0.2 if cx2 > W / 2 else 0.8)
    img = comp(img, None, PAPER, kanji(pid, 300, kx, H * 0.45, s + 13))
    img = comp(img, None, col, splatter(s + 14, 30, cx2, H * 0.95, 1.6, (2, 12)) * 0.9)
    out.append(img)

    # 2 FIELD
    cx3, gy = W / 2 + rr.uniform(-80, 80), H * 0.78
    img = np.ones((H, W, 3)) * col[None, None] * (0.85 + 0.2 * noise(H, W, 20, s + 20, 3)[..., None])
    img = comp(img, None, PAPER, enso(cx3, gy - H * 0.32, H * 0.42, s + 21, 26) * 0.9)
    ground = rough(YY > gy + 6 * np.sin(XX / 37.0) + 10 * noise(H, W, 40, s + 22, 2), s + 23, 0.3, 5)
    img = comp(img, None, INK * 0.5 + dark * 0.5, ground)
    img = comp(img, None, INK, cracks(cx3, gy, s + 24, 16, 380, 7) * ground)
    img = comp(img, None, INK, silhouette(cx3, gy + 4, H * 0.8, s + 25))
    rocks = splatter(s + 26, 40, cx3, gy - 30, 0.9, (3, 14))
    img = comp(img, None, INK, rocks * (YY < gy + 20))
    img = comp(img, None, PAPER, speed_strokes(cx3, gy, s + 27, 40, 0.5) * 0.5 * (YY < gy))
    out.append(img)

    # 3 BLAST - the shockwave tearing outward: a thick brushed ring of the pillar's colour bursting across the
    # frame, the pillar standing in negative space at its heart, ink thrown off the ring's edge
    cx4, cy4 = W / 2 + rr.uniform(-60, 60), H * 0.62
    img = np.ones((H, W, 3)) * INK[None, None] + noise(H, W, 18, s + 30, 3)[..., None] * dark[None, None] * 0.6
    ring_outer = enso(cx4, cy4, H * 0.62, s + 31, 70)
    ring_inner = enso(cx4, cy4, H * 0.38, s + 35, 34)
    img = comp(img, None, col, np.clip(ring_outer * 1.1, 0, 1))
    img = comp(img, None, col * 0.5 + 0.5, ring_inner * 0.9)
    a_, rn, _ = polar(cx4, cy4)
    img = comp(img, None, col * 0.8, speed_strokes(cx4, cy4, s + 32, 70, 0.55) * 0.7)
    img = comp(img, None, INK, splatter(s + 33, 34, cx4, cy4, 1.5, (3, 16)) * (ring_outer > 0.2))
    sil = silhouette(cx4, cy4 + 60, H * 0.72, s + 34)
    halo = np.clip(ndimage.gaussian_filter(sil, 8) * 2.5 - sil, 0, 1)
    img = comp(img, None, PAPER, halo * 0.9)
    img = comp(img, None, INK * 0.4 + col * 0.12, sil)
    img = comp(img, None, INK, torn_border(s + 36, 16))
    out.append(img)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for idx, pid in enumerate(IDS):
        for k, img in enumerate(frames(pid, idx)):
            rgb = (np.clip(img, 0, 1) * 255).astype(np.uint8)
            Image.fromarray(rgb, "RGB").quantize(colors=128, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG) \
                .save(OUT / f"{pid}_{k}.png", optimize=True)
        print(pid)


if __name__ == "__main__":
    main()
