"""
Procedural VFX / GUI / item textures for DOA (run locally; PNGs are committed).

Everything is hand-made here from noise and the brushed kanji masks in tools/glyphs:
  particle/   mote (light mote), petal_0..3, ink_0..3 (ink smoke), shard_0..2 (talisman scraps), spark
  effect/     brush_trail (falling light stroke), seal_<id> (summoning circle per pillar),
              ink_ring (brushed shockwave ring), light_column (descending heavenly light),
              orb_skin (the corruption orb's surface), orb_edge (its burning rim)
  gui/        label/<id> (floating name kanji); impact frames live in impact_frames.py
  item/       summon_talisman (the ofuda that calls the armory)
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/doa/textures"
G = ROOT / "tools/glyphs"
IDS = ["death", "redemption", "wrath", "soul", "fate", "silence", "void"]


def noise(h, w, cell, seed, octaves=4, wrap=False):
    r = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp = tot = 0.0
    amp = 1.0
    for o in range(octaves):
        c = max(1, cell >> o)
        gh, gw = max(2, h // c), max(2, w // c)
        g = r.random((gh, gw)).astype(np.float32)
        up = ndimage.zoom(g, (h / gh, w / gw), order=3, mode="grid-wrap" if wrap else "reflect", grid_mode=True)[:h, :w]
        out += up * amp
        tot += amp
        amp *= 0.5
    out /= tot
    lo, hi = np.percentile(out, 1), np.percentile(out, 99)
    return np.clip((out - lo) / (hi - lo + 1e-6), 0, 1)


def save(arr, path, rgb=(1, 1, 1)):
    path = A / path
    path.parent.mkdir(parents=True, exist_ok=True)
    a = np.clip(arr, 0, 1)
    if a.ndim == 2:
        img = np.dstack([np.full_like(a, rgb[0]), np.full_like(a, rgb[1]), np.full_like(a, rgb[2]), a])
    else:
        img = a
    Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(path, optimize=True)
    print(" ", path.relative_to(A))


def grid(s):
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32)
    c = (s - 1) / 2
    return (xx - c) / c, (yy - c) / c


def glyph(name, size):
    return np.asarray(Image.open(G / f"{name}.png").convert("L").resize((size, size), Image.LANCZOS)).astype(np.float32) / 255


# ------------------------------------------------------------------------------------------ particles

def particles():
    s = 16
    x, y = grid(s)
    r = np.hypot(x, y)
    mote = np.clip(1 - r, 0, 1) ** 2.2 + np.clip(1 - np.abs(x) * 9, 0, 1) * np.clip(1 - np.abs(y), 0, 1) * 0.5 \
        + np.clip(1 - np.abs(y) * 9, 0, 1) * np.clip(1 - np.abs(x), 0, 1) * 0.5
    save(mote, "particle/mote.png")
    spark = np.clip(1 - r * 1.6, 0, 1) ** 1.5
    save(spark, "particle/spark.png")
    # petals: a notched teardrop, rotated
    for k in range(4):
        a = k * 0.7 + 0.3
        xr = x * math.cos(a) - y * math.sin(a)
        yr = x * math.sin(a) + y * math.cos(a)
        body = ((xr / 0.55) ** 2 + ((yr + 0.1) / 0.85) ** 2) < 1
        notch = (np.abs(xr) < 0.12) & (yr < -0.6)
        p = (body & ~notch).astype(np.float32)
        shade = 0.75 + 0.25 * (1 - np.clip(np.abs(xr) / 0.55, 0, 1))
        rgba = np.dstack([shade, shade * 0.9, shade * 0.92, p])
        save(rgba, f"particle/petal_{k}.png")
    # ink smoke puffs (dark, soft, ragged)
    s2 = 32
    x2, y2 = grid(s2)
    r2 = np.hypot(x2, y2)
    for k in range(4):
        n = noise(s2, s2, 8, 40 + k, 3)
        a = np.clip((1 - r2 * (1.1 + 0.6 * (n - 0.5))) * 1.8, 0, 1) ** 1.3 * (0.6 + 0.4 * n)
        rgba = np.dstack([np.full_like(a, 0.06), np.full_like(a, 0.05), np.full_like(a, 0.06), a])
        save(rgba, f"particle/ink_{k}.png")
    # talisman scraps: torn paper with a stroke of ink or red seal
    for k in range(3):
        rr = np.random.default_rng(70 + k)
        poly = np.zeros((s, s), np.float32)
        x0, x1 = rr.integers(2, 5), rr.integers(11, 14)
        poly[2:14, x0:x1] = 1
        edge = noise(s, s, 2, 80 + k, 2)
        poly *= (edge > 0.25)
        col = np.dstack([np.full((s, s), 0.88), np.full((s, s), 0.82), np.full((s, s), 0.68)])
        ry = rr.integers(4, 10)
        if k == 2:
            col[ry:ry + 3, 6:10] = [0.7, 0.1, 0.08]
        else:
            col[ry:ry + 1, x0 + 1:x1 - 1] = [0.05, 0.05, 0.05]
            col[ry + 3:ry + 4, x0 + 2:x1 - 2] = [0.05, 0.05, 0.05]
        save(np.dstack([col, poly]), f"particle/shard_{k}.png")


# ------------------------------------------------------------------------------------------ effects

def effects():
    # falling stroke of light: bright head at the bottom, a dry-brush tail fading upward
    W, H = 64, 512
    xx = np.linspace(-1, 1, W)[None, :]
    yy = np.linspace(0, 1, H)[:, None]             # 0 top (tail) -> 1 bottom (head)
    n = noise(H, W, 8, 5, 3)
    width = 0.25 + 0.55 * yy ** 1.5
    core = np.exp(-(xx / (width * 0.45)) ** 2)
    hair = np.clip((noise(H, W * 4, 2, 6, 2)[:, ::4] - 0.35) * 2.5, 0, 1)
    trail = core * (yy ** 1.3) * (0.55 + 0.45 * hair) * (0.75 + 0.25 * n)
    trail += np.exp(-(xx / 0.12) ** 2) * np.exp(-((yy - 0.96) / 0.05) ** 2) * 1.2
    save(trail, "effect/brush_trail.png")

    # brushed ink shockwave ring
    S = 512
    x, y = grid(S)
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    n = noise(S, S, 16, 11, 4)
    band = np.exp(-((r - 0.82 - 0.05 * (n - 0.5)) / (0.07 + 0.04 * np.sin(ang * 3 + 1))) ** 2)
    dry = np.clip((noise(S, S, 3, 12, 2) - 0.3) * 2.2, 0, 1)
    ring = band * (0.4 + 0.6 * dry)
    splat = np.clip((n - 0.78) * 5, 0, 1) * (r > 0.85) * (r < 1.0)
    save(np.clip(ring + splat, 0, 1), "effect/ink_ring.png")

    # descending heavenly light column (vertical gradient, soft edges)
    W, H = 64, 256
    xx = np.linspace(-1, 1, W)[None, :]
    yy = np.linspace(0, 1, H)[:, None]
    col = np.exp(-(xx / 0.45) ** 2) * (0.3 + 0.7 * yy) + np.exp(-(xx / 0.12) ** 2) * 0.6
    col *= np.clip(yy * 6, 0, 1)
    save(col, "effect/light_column.png")

    # summoning seal per pillar: concentric brushed rings, a band of marks, the kanji at the heart
    S = 512
    x, y = grid(S)
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    for i, pid in enumerate(IDS):
        n = noise(S, S, 8, 200 + i, 3)
        rings = np.zeros((S, S), np.float32)
        for rad, wdt in ((0.96, 0.012), (0.88, 0.008), (0.60, 0.010), (0.52, 0.006)):
            rings = np.maximum(rings, np.exp(-((r - rad) / wdt) ** 2) * (0.7 + 0.3 * n))
        # band of radial marks between 0.62 and 0.86 (like an ancient script ring)
        k = (ang / (2 * math.pi) * 56) % 1.0
        rr = np.random.default_rng(300 + i)
        heights = rr.uniform(0.3, 1.0, 56)
        idx = ((ang / (2 * math.pi) * 56) % 56).astype(int)
        mark = (np.abs(k - 0.5) < 0.12) & (r > 0.66) & (r < 0.66 + 0.18 * heights[idx])
        rings = np.maximum(rings, mark * (0.6 + 0.4 * n))
        # seven-point star of brushed lines
        for j in range(7):
            a0, a1 = j * 2 * math.pi / 7, (j + 3) * 2 * math.pi / 7
            p0 = np.array([math.cos(a0), math.sin(a0)]) * 0.58
            p1 = np.array([math.cos(a1), math.sin(a1)]) * 0.58
            d = p1 - p0
            t = np.clip(((x - p0[0]) * d[0] + (y - p0[1]) * d[1]) / d.dot(d), 0, 1)
            dist = np.hypot(x - p0[0] - t * d[0], y - p0[1] - t * d[1])
            rings = np.maximum(rings, np.exp(-(dist / 0.006) ** 2) * 0.55)
        g = glyph(pid, int(S * 0.42))
        gl = np.zeros((S, S), np.float32)
        o = (S - g.shape[0]) // 2
        gl[o:o + g.shape[0], o:o + g.shape[1]] = g
        seal = np.clip(np.maximum(rings, gl), 0, 1)
        save(seal, f"effect/seal_{pid}.png")

    # the corruption orb's skin: black lacquer with slow ink swirls and faint veins (tileable)
    S = 256
    n = noise(S, S, 32, 500, 5, wrap=True)
    n2 = noise(S, S, 8, 501, 3, wrap=True)
    vein = np.clip(1 - np.abs(n - 0.5) * 18, 0, 1) * np.clip((n2 - 0.4) * 2, 0, 1)
    base = 0.02 + 0.05 * n
    rgb = np.dstack([base * 1.1 + vein * 0.22, base * 0.9 + vein * 0.05, base * 1.15 + vein * 0.12])
    save(np.dstack([rgb, np.ones((S, S))]), "effect/orb_skin.png")
    # the orb's burning rim / creeping edge: a tileable strip of ragged ink flames
    W, H = 256, 64
    n = noise(H, W, 16, 510, 4, wrap=True)
    yy = np.linspace(0, 1, H)[:, None]
    edge = np.clip((n * 0.6 + 0.4 - yy) * 3, 0, 1) * np.clip(yy * 8, 0, 1)
    save(edge, "effect/orb_edge.png")


# ------------------------------------------------------------------------------------------ gui

def labels():
    for pid in IDS:
        g = glyph(pid, 224)
        S = 256
        a = np.zeros((S, S), np.float32)
        o = (S - 224) // 2
        a[o:o + 224, o:o + 224] = g
        halo = ndimage.gaussian_filter(a, 10) * 0.9
        save(np.clip(np.maximum(a, halo * 0.6), 0, 1), f"gui/label/{pid}.png")


def item():
    S = 32
    paper = np.zeros((S, S, 4), np.float32)
    n = noise(S, S, 4, 77, 2)
    x0, x1 = 7, 25
    paper[1:31, x0:x1, :3] = (np.array([0.88, 0.82, 0.66]) * (0.85 + 0.2 * n[1:31, x0:x1, None]))
    paper[1:31, x0:x1, 3] = 1
    paper[1:31, x0:x0 + 1, :3] = [0.62, 0.08, 0.06]
    paper[1:31, x1 - 1:x1, :3] = [0.62, 0.08, 0.06]
    paper[1:2, x0:x1, :3] = [0.62, 0.08, 0.06]
    paper[30:31, x0:x1, :3] = [0.62, 0.08, 0.06]
    g = glyph("summon", 16)
    m = g > 0.3
    sub = paper[3:19, 8:24, :3]
    sub[m] = [0.05, 0.04, 0.04]
    paper[22:28, 13:19, :3] = [0.66, 0.1, 0.07]
    paper[23:27, 14:18, :3] = [0.82, 0.22, 0.12]
    # a torn bottom corner
    paper[29:31, x1 - 4:x1, 3] = 0
    save(paper, "item/summon_talisman.png")


def additive():
    """Premultiplied copies (rgb *= alpha) of the light textures, for the additive 'eyes' render layer."""
    for src in sorted(list((A / "effect").glob("*.png")) + list((A / "gui/label").glob("*.png"))):
        if src.name.startswith("add_") or src.name == "orb_skin.png":
            continue
        img = np.asarray(Image.open(src).convert("RGBA")).astype(np.float32) / 255
        img[..., :3] *= img[..., 3:4]
        img[..., 3] = 1
        out = src.with_name("add_" + src.name)
        Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(out, optimize=True)
        print(" ", out.relative_to(A))


if __name__ == "__main__":
    particles()
    effects()
    labels()
    item()
    additive()
