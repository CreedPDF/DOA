"""
Textures for the pillars' inscription panels and the cinematic moves' worlds (all procedural, nothing from Minecraft):

  entity/pillar/panel_<id>.png (+ _glow)   polished black stone panel, gold frame, the kanji carved and glowing
  scene/cloud_0..3.png                     cumulus sprites (alpha, lit from above) for the sea of clouds at Dawn
  scene/strata_0..7.png (+ _glow)          the layers of the world on the way down to its heart:
                                            0 topsoil and roots  1 clay and sediment  2 granite  3 deep slate
                                            4 crystal caverns    5 basalt and magma   6 mantle   7 outer core
  scene/rift_edge.png                      the burning lip of a tear in the sky (premultiplied)
  scene/beam.png                           a soft beam profile (premultiplied)
  scene/streak.png                         a star-streak (premultiplied) for the falling stars and warp
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

from doa_textures import noise, glyph, IDS

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/doa/textures"


def save(rgb, path, alpha=None):
    path = A / path
    path.parent.mkdir(parents=True, exist_ok=True)
    rgb = np.clip(rgb, 0, 1)
    a = np.ones(rgb.shape[:2]) if alpha is None else np.clip(alpha, 0, 1)
    Image.fromarray((np.dstack([rgb, a]) * 255 + 0.5).astype(np.uint8), "RGBA").save(path, optimize=True)
    print(" ", path.relative_to(A))


def tile_noise(h, w, cell, seed, octaves=4):
    return noise(h, w, cell, seed, octaves, wrap=True)


# ------------------------------------------------------------------------------------------ pillar panels

def panels():
    W, H = 384, 1024
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    for k, pid in enumerate(IDS):
        n = noise(H, W, 40, 900 + k, 5)
        n2 = noise(H, W, 6, 920 + k, 3)
        stone = 0.03 + 0.05 * n[..., None] * np.array([1, 0.95, 1.05]) + (n2[..., None] - 0.5) * 0.015
        # polished sheen: a soft vertical gradient
        stone = stone * (0.85 + 0.35 * np.exp(-((xx - W * 0.3) / (W * 0.25)) ** 2))[..., None]
        edge = np.minimum.reduce([xx, W - 1 - xx, yy, H - 1 - yy])
        frame = ((edge > 10) & (edge < 22)) | ((edge > 30) & (edge < 34))
        corner = np.zeros_like(xx, bool)
        for cx, cy in ((40, 40), (W - 40, 40), (40, H - 40), (W - 40, H - 40)):
            r = np.hypot(xx - cx, yy - cy)
            corner |= (r > 12) & (r < 18)
        gold = (frame | corner).astype(np.float32)
        gn = noise(H, W, 4, 940 + k, 2)
        gold_col = np.array([0.72, 0.52, 0.2]) * (0.7 + 0.5 * gn[..., None])
        img = stone * (1 - gold[..., None]) + gold_col * gold[..., None]
        # the kanji, cut deep: a dark recess with a lit upper-left bevel, glowing ink in the cut
        g = np.zeros((H, W), np.float32)
        gs = glyph(pid, 300)
        y0 = 120
        g[y0:y0 + 300, (W - 300) // 2:(W + 300) // 2] = gs
        # a column of small carved marks below it
        rng = np.random.default_rng(960 + k)
        for row in range(11):
            yb = 470 + row * 46
            for s in range(rng.integers(1, 4)):
                x0 = W // 2 - 26 + rng.integers(-10, 10)
                w_ = rng.integers(14, 52)
                y1 = yb + s * 10
                g[y1:y1 + 6, x0:x0 + w_] = 1.0
        m = ndimage.gaussian_filter(g, 1.0)
        dx = ndimage.sobel(ndimage.gaussian_filter(g, 3), 1)
        dy = ndimage.sobel(ndimage.gaussian_filter(g, 3), 0)
        bevel = np.clip(-(dx + dy) * 0.6, -1, 1)
        img = img * (1 - m[..., None] * 0.85) + np.clip(bevel, 0, 1)[..., None] * 0.25
        glow = np.clip(ndimage.gaussian_filter(g, 1.6) * 1.2, 0, 1) * (0.75 + 0.25 * gn)
        glow = np.maximum(glow, gold * 0.15)
        save(img, f"entity/pillar/panel_{pid}.png")
        save(np.dstack([glow, glow, glow]), f"entity/pillar/panel_{pid}_glow.png")


# ------------------------------------------------------------------------------------------ clouds

def clouds():
    S = 512
    yy, xx = (np.mgrid[0:S, 0:S].astype(np.float32) + 0.5) / S * 2 - 1
    for k in range(4):
        rng = np.random.default_rng(1100 + k)
        dens = np.zeros((S, S), np.float32)
        for b in range(14):
            cx, cy = rng.uniform(-0.55, 0.55), rng.uniform(-0.25, 0.25)
            r = rng.uniform(0.18, 0.42)
            dens = np.maximum(dens, np.clip(1 - ((xx - cx) ** 2 + ((yy - cy) * 1.3) ** 2) / r ** 2, 0, 1))
        dens *= np.clip((0.45 - yy) * 3, 0, 1)  # flat bases
        n = noise(S, S, 16, 1110 + k, 6)
        dens = np.clip(dens * 1.4 - (1 - n) * 0.7, 0, 1)
        dens = ndimage.gaussian_filter(dens, 1.5)
        alpha = np.clip(dens * 1.6, 0, 1) * np.clip((1 - np.hypot(xx, yy * 1.6)) * 3, 0, 1)
        # lit from above: bright crowns, blue-grey shadowed bellies (tinted warm by the sunrise in game)
        shade = np.clip(0.55 + 0.5 * (-yy) + (n - 0.5) * 0.4, 0, 1)
        top = ndimage.gaussian_filter(np.roll(dens, 6, 0) - dens, 4)
        shade = np.clip(shade + np.clip(-top, 0, 1) * 0.5, 0.25, 1)
        rgb = np.dstack([shade, shade * 0.97, shade * 0.95])
        save(rgb, f"scene/cloud_{k}.png", alpha)


# ------------------------------------------------------------------------------------------ strata

def strata():
    S = 512
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    rng = np.random.default_rng(1200)

    def n(cell, seed, o=4):
        return tile_noise(S, S, cell, seed, o)

    layers = []
    # 0 topsoil with roots and pebbles
    soil = np.array([0.20, 0.13, 0.08]) * (0.6 + 0.6 * n(32, 1201)[..., None])
    roots = np.zeros((S, S), np.float32)
    for r in range(40):
        x = rng.uniform(0, S)
        y = 0
        for step in range(80):
            x += rng.normal(0, 3)
            y += rng.uniform(2, 7)
            if y >= S:
                break
            rr = max(1, int(3 - step * 0.04))
            yi, xi = int(y), int(x) % S
            roots[max(0, yi - rr):yi + rr, max(0, xi - rr):xi + rr] = 1
    roots = ndimage.gaussian_filter(roots, 0.8)
    soil = soil * (1 - roots[..., None] * 0.6) + np.array([0.33, 0.24, 0.15]) * roots[..., None] * 0.6
    peb = n(6, 1202) > 0.82
    soil[peb] = soil[peb] * 0.5 + np.array([0.35, 0.33, 0.3]) * 0.5
    layers.append((soil, None))
    # 1 clay & sediment bands
    band = np.sin(yy / S * 2 * math.pi * 7 + n(64, 1203) * 4) * 0.5 + 0.5
    clay = np.array([0.52, 0.36, 0.22]) * (1 - band[..., None] * 0.35) + np.array([0.70, 0.56, 0.38]) * band[..., None] * 0.35
    clay *= (0.8 + 0.3 * n(8, 1204)[..., None])
    layers.append((clay, None))
    # 2 granite
    g = n(4, 1205, 3)
    gran = np.array([0.42, 0.40, 0.40]) * (0.7 + 0.4 * g[..., None])
    gran[g > 0.8] = np.array([0.75, 0.70, 0.65])
    gran[g < 0.15] = np.array([0.08, 0.08, 0.09])
    crack = np.abs(n(48, 1206) - 0.5) < 0.012
    gran[crack] *= 0.3
    layers.append((gran, None))
    # 3 deep slate with veins
    sl = np.sin((yy + xx * 0.3) / S * 2 * math.pi * 18 + n(32, 1207) * 6) * 0.5 + 0.5
    slate = np.array([0.12, 0.12, 0.15]) * (0.7 + 0.4 * sl[..., None]) * (0.85 + 0.3 * n(6, 1208)[..., None])
    vein = (np.abs(n(64, 1209) - 0.5) < 0.008)
    slate[vein] = np.array([0.55, 0.50, 0.65])
    layers.append((slate, np.dstack([vein * 0.3] * 3).astype(np.float32)))
    # 4 crystal caverns: dark rock studded with glowing crystals
    rock = np.array([0.06, 0.05, 0.07]) * (0.7 + 0.5 * n(16, 1210)[..., None])
    cg = np.zeros((S, S), np.float32)
    for c in range(60):
        cx, cy = rng.uniform(0, S), rng.uniform(0, S)
        ang = rng.uniform(0, math.pi)
        L, w = rng.uniform(12, 40), rng.uniform(3, 8)
        dx, dy = (xx - cx + S / 2) % S - S / 2, (yy - cy + S / 2) % S - S / 2
        u = dx * math.cos(ang) + dy * math.sin(ang)
        v = -dx * math.sin(ang) + dy * math.cos(ang)
        cg = np.maximum(cg, np.clip(1 - np.abs(v) / (w * np.clip(1 - np.abs(u) / L, 0, 1) + 1e-3), 0, 1) * (np.abs(u) < L))
    cryst = rock * (1 - cg[..., None]) + np.array([0.55, 0.85, 0.95]) * cg[..., None]
    layers.append((cryst, np.dstack([cg * 0.9, cg * 0.95, cg]).astype(np.float32)))
    # 5 basalt with magma veins
    bas = np.array([0.05, 0.04, 0.04]) * (0.7 + 0.6 * n(8, 1211)[..., None])
    mv = np.clip(1 - np.abs(n(40, 1212, 5) - 0.5) / 0.03, 0, 1) ** 2
    mag = mv[..., None] * np.array([1.0, 0.42, 0.08])
    layers.append((bas * (1 - mv[..., None]) + mag, np.dstack([mv, mv * 0.45, mv * 0.1]).astype(np.float32)))
    # 6 mantle: slow molten rock, convection cells
    m1 = n(96, 1213, 5)
    mant = np.dstack([0.75 + 0.25 * m1, 0.22 + 0.25 * m1 ** 2, 0.04 + 0.05 * m1]) * (0.6 + 0.5 * n(12, 1214)[..., None])
    crust = (n(24, 1215) > 0.72)
    mant[crust] *= 0.25
    glow6 = mant * (1 - crust[..., None] * 0.8)
    layers.append((mant, glow6))
    # 7 outer core: liquid iron, near white-hot
    m2 = n(64, 1216, 5)
    core = np.dstack([1.0 * np.ones_like(m2), 0.75 + 0.2 * m2, 0.35 + 0.35 * m2])
    layers.append((core, core))
    for i, (rgb, glow) in enumerate(layers):
        save(rgb, f"scene/strata_{i}.png")
        if glow is not None:
            save(glow, f"scene/strata_{i}_glow.png")


def misc():
    # rift edge: v = 0 at the lip, fading inward
    W, H = 256, 128
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    nn = noise(H, W, 8, 1300, 4, wrap=True)
    v = yy / H
    edge = np.exp(-((v - 0.08 - nn * 0.12) / 0.04) ** 2) + np.exp(-v * 4) * 0.35 * nn
    save(np.dstack([edge, edge * 0.9, edge * 0.95]), "scene/rift_edge.png")
    # beam profile (u across)
    u = np.linspace(-1, 1, 128)
    prof = np.exp(-(u / 0.12) ** 2) + np.exp(-(u / 0.45) ** 2) * 0.35
    b = np.repeat(prof[None, :], 64, 0)
    save(np.dstack([b, b, b]), "scene/beam.png")
    # star streak (u along, v across)
    u = np.linspace(0, 1, 256)[None, :]
    v = np.linspace(-1, 1, 32)[:, None]
    s_ = np.exp(-(v / 0.18) ** 2) * u ** 2.5 + np.exp(-(v / 0.5) ** 2) * u ** 6 * 0.3
    save(np.dstack([s_, s_, s_]), "scene/streak.png")


if __name__ == "__main__":
    panels()
    clouds()
    strata()
    misc()
