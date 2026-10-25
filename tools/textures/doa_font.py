"""
DOA's own lettering: an engraved, slightly brushed serif for Latin (wide-tracked capitals read like carved stone)
and the brushed kanji the pillars use. Everything DOA writes on screen or in the world is drawn from this atlas,
never Minecraft's font.

Output: assets/doa/textures/font/doa.png       white glyphs, alpha = ink        (HUD, alpha-blended)
        assets/doa/textures/font/doa_glow.png  soft halo of the same glyphs     (HUD shadow / glow)
        assets/doa/textures/font/doa_add.png   premultiplied glyphs + halo      (world, additive)
        assets/doa/fontdata/doa.json               per-glyph cell + advance
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/doa"
FONT = "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc"
CELL = 96
COLS = 16
LATIN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,:;!?'\"-()/+%&·—…<>[]=*#"
KANJI = "死贖怒魂運静虚召封天月日星地心核界蝕暁砕裂淵時止門光闇"


def noise(h, w, seed, cell=4):
    r = np.random.default_rng(seed)
    g = r.random((h // cell + 2, w // cell + 2)).astype(np.float32)
    return ndimage.zoom(g, cell, order=3)[:h, :w]


def main():
    chars = list(LATIN) + list(KANJI)
    rows = (len(chars) + COLS - 1) // COLS
    W, H = COLS * CELL, rows * CELL
    H2 = 1 << (H - 1).bit_length()
    ink = np.zeros((H2, W), np.float32)
    meta = {"cell": CELL, "cols": COLS, "size": [W, H2], "glyphs": {}}
    latin = ImageFont.truetype(FONT, int(CELL * 0.72), index=0)
    kanji = ImageFont.truetype(FONT, int(CELL * 0.74), index=0)
    for i, ch in enumerate(chars):
        cx, cy = (i % COLS) * CELL, (i // COLS) * CELL
        big = Image.new("L", (CELL * 2, CELL * 2), 0)
        d = ImageDraw.Draw(big)
        f = kanji if ch in KANJI else latin
        fb = ImageFont.truetype(FONT, f.size * 2, index=0)
        bbox = d.textbbox((0, 0), ch, font=fb)
        w = bbox[2] - bbox[0]
        # baseline-aligned: place so the cap height sits in the cell
        ox = (CELL * 2 - w) // 2 - bbox[0]
        oy = int(CELL * 2 * (0.02 if ch in KANJI else 0.06))
        d.text((ox, oy), ch, font=fb, fill=255)
        g = np.asarray(big).astype(np.float32) / 255
        # carve: ragged edges and dry hairs, like ink cut into stone
        n = noise(CELL * 2, CELL * 2, 100 + i, 3)
        soft = ndimage.gaussian_filter(g, 1.2)
        g = np.clip((soft - 0.5 + (n - 0.5) * 0.22) * 5 + 0.5, 0, 1)
        hairs = ndimage.zoom(np.random.default_rng(500 + i).random((CELL * 2 // 4 + 1, CELL * 2)), (4, 1), order=1)[:CELL * 2, :CELL * 2]
        g *= np.clip(0.78 + hairs * 0.35, 0, 1)
        small = np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize((CELL, CELL), Image.LANCZOS)).astype(np.float32) / 255
        ink[cy:cy + CELL, cx:cx + CELL] = small
        adv = (w / 2 + (CELL * 0.14 if ch not in KANJI else CELL * 0.1)) / CELL if ch != " " else 0.35
        x0 = max(0, ((CELL * 2 - w) // 2) / 2 - 2) / CELL
        meta["glyphs"][ch] = {"i": i, "x0": round(x0, 4), "w": round(min(1, w / 2 / CELL + 4 / CELL), 4), "adv": round(adv, 4)}
    meta["glyphs"][" "] = {"i": -1, "x0": 0, "w": 0, "adv": 0.32}
    glow = np.clip(ndimage.gaussian_filter(ink, 5) * 2.2, 0, 1)
    out = A / "textures/font"
    out.mkdir(parents=True, exist_ok=True)
    white = np.ones_like(ink)
    Image.fromarray((np.dstack([white, white, white, ink]) * 255).astype(np.uint8), "RGBA").save(out / "doa.png", optimize=True)
    Image.fromarray((np.dstack([white, white, white, glow]) * 255).astype(np.uint8), "RGBA").save(out / "doa_glow.png", optimize=True)
    add = np.clip(ink + glow * 0.45, 0, 1)
    Image.fromarray((np.dstack([add, add, add, np.ones_like(add)]) * 255).astype(np.uint8), "RGBA").save(out / "doa_add.png", optimize=True)
    (A / "fontdata").mkdir(parents=True, exist_ok=True)
    (A / "fontdata/doa.json").write_text(json.dumps(meta, ensure_ascii=False))
    print("font atlas", W, H2, len(chars), "glyphs")


if __name__ == "__main__":
    main()
