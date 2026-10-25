"""
Brush-calligraphy glyph masks for the seven pillars (and the summoning talisman).

Renders each kanji with Noto Serif CJK (OFL), then roughens it into a brushed, hand-cut look:
ragged stroke edges, dry-brush streaks running along the strokes and a few ink flecks. Output:
tools/glyphs/<name>.png, 512x512 greyscale masks (white = ink). These masks are the source for the
carved, glowing inscriptions on the pillars (baked by Blender), the floating name labels, the
summoning seal circles and the talisman item.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

OUT = Path(__file__).resolve().parents[1] / "glyphs"
FONT = "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc"

GLYPHS = {
    "death": "死", "redemption": "贖", "wrath": "怒", "soul": "魂", "fate": "運", "silence": "静", "void": "虚",
    "summon": "召",
}


def noise(h, w, scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((h // scale + 2, w // scale + 2)).astype(np.float32)
    return ndimage.zoom(g, scale, order=3)[:h, :w]


def brush(ch, seed, size=512):
    img = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, int(size * 0.80), index=0)
    box = d.textbbox((0, 0), ch, font=font)
    w, h = box[2] - box[0], box[3] - box[1]
    d.text(((size - w) / 2 - box[0], (size - h) / 2 - box[1]), ch, fill=255, font=font)
    a = np.asarray(img).astype(np.float32) / 255
    # ragged edges: blur, then threshold against noise
    soft = ndimage.gaussian_filter(a, 3.2)
    n = noise(size, size, 6, seed) * 0.6 + noise(size, size, 24, seed + 1) * 0.4
    rough = np.clip((soft - 0.32 - (n - 0.5) * 0.35) * 7, 0, 1)
    # dry-brush streaks along the dominant stroke direction (gradient-perpendicular)
    gy, gx = np.gradient(ndimage.gaussian_filter(a, 6))
    streak = noise(size, size, 2, seed + 2)
    streak = ndimage.uniform_filter1d(streak, 21, axis=1) * (np.abs(gy) > np.abs(gx)) + \
        ndimage.uniform_filter1d(streak, 21, axis=0) * (np.abs(gy) <= np.abs(gx))
    dry = np.clip((streak - 0.38) * 3.5, 0, 1)
    ink = rough * (0.78 + 0.22 * dry)
    ink = np.where(rough > 0.5, np.maximum(ink, 0.8), ink)
    # flecks of ink thrown off the brush
    r = np.random.default_rng(seed + 3)
    fl = np.zeros_like(a)
    for _ in range(10):
        cy, cx = r.integers(40, size - 40, 2)
        if soft[cy, cx] > 0.05 or r.random() < 0.6:
            rr = r.uniform(1.5, 5)
            yy, xx = np.ogrid[:size, :size]
            fl = np.maximum(fl, np.clip(rr - np.hypot(yy - cy, xx - cx), 0, 1))
    ink = np.clip(np.maximum(ink, fl * 0.85), 0, 1)
    return Image.fromarray((ink * 255).astype(np.uint8), "L")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for i, (name, ch) in enumerate(GLYPHS.items()):
        brush(ch, 100 + i * 17).save(OUT / f"{name}.png")
        print(name, ch)


if __name__ == "__main__":
    main()
