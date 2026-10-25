"""
DOA (Delux's Orbital Armory) model pipeline, run headless:

    blender -b --factory-startup --python-exit-code 1 -P tools/blender/doa_models.py -- \
        --assets src/main/resources/assets/doa --out build/blender --glyphs tools/glyphs

Builds every model of the mod in Blender from boxes, packs a texture atlas, paints it
procedurally (weathered black basalt, aged bronze with verdigris, glazed roof tiles, straw rope,
paper talismans, carved glowing inscriptions from brushed kanji masks), bakes a glowmask per
variant, exports the geometry in the mod's own JSON format, and renders previews with Cycles.

Models:
  * pillar  - a 10-block black obelisk with a tiered pagoda cap, bronze bands, a sacred straw rope
              with paper streamers, hanging talismans, a carved kanji on all four faces and a
              floating jewel finial. Seven texture variants (one per pillar, each with its own
              kanji and glow colour).
  * relic   - the device each pillar releases (placeholder reliquary): a bronze cage around a
              glowing core inside a slowly turning ring. Glow is white; tinted in game.

Coordinates are Minecraft pixels (16 px = 1 block), y up, model front = north (-Z). Face UVs use
the convention documented in face_quad(); the Java renderer (DoaModel) uses the same one.
"""
import argparse
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

# ------------------------------------------------------------------------------------------- data

PILLARS = [
    # id, kanji mask, glow colour (sRGB)
    ("death", (0.86, 0.06, 0.12)),
    ("redemption", (0.95, 0.72, 0.22)),
    ("wrath", (1.00, 0.36, 0.10)),
    ("soul", (0.26, 0.80, 0.55)),
    ("fate", (0.62, 0.38, 1.00)),
    ("silence", (0.93, 0.90, 0.82)),
    ("void", (1.00, 0.32, 0.64)),
]

TPP = 3  # texels per model pixel (1024 px atlas)


def P(bone, kind, origin, size, rot=None, faces=None):
    return {"bone": bone, "kind": kind, "from": list(origin), "to": [origin[i] + size[i] for i in range(3)],
            "rot": rot, "faces": faces or {}}


def pillar_parts():
    parts = []
    S, B = "stone", "bronze"
    # buried plinth and steps
    parts.append(P("pillar", S, [-22, -24, -22], [44, 30, 44]))
    parts.append(P("pillar", S, [-19, 6, -19], [38, 6, 38]))
    parts.append(P("pillar", B, [-16.5, 12, -16.5], [33, 3, 33]))
    # the shaft: a carved inscription on every face
    ins = {f: "inscribed" for f in ("north", "south", "east", "west")}
    parts.append(P("pillar", S, [-14, 15, -14], [28, 84, 28], faces=ins))
    for sx in (-1, 1):
        for sz in (-1, 1):
            parts.append(P("pillar", B, [sx * 14 - 1.6 + (0 if sx < 0 else -0.2), 15, sz * 14 - 1.6 + (0 if sz < 0 else -0.2)],
                           [3.4, 84, 3.4]))
    parts.append(P("pillar", B, [-14.8, 17, -14.8], [29.6, 2.4, 29.6]))
    parts.append(P("pillar", B, [-14.8, 95, -14.8], [29.6, 2.4, 29.6]))
    # sacred straw rope with paper streamers (shide)
    parts.append(P("pillar", "rope", [-15.6, 88, -15.6], [31.2, 4, 31.2]))
    for f, (dx, dz, w, d) in {"north": (0, -15.8, 4, 0.4), "south": (0, 15.4, 4, 0.4),
                              "east": (15.4, 0, 0.4, 4), "west": (-15.8, 0, 0.4, 4)}.items():
        for k in (-9, 9):
            ox = dx + (k if w > 1 else 0) - (w / 2 if w > 1 else 0)
            oz = dz + (k if d > 1 else 0) - (d / 2 if d > 1 else 0)
            parts.append(P("pillar", "paper", [ox, 76, oz], [w, 12, d]))
    # hanging talismans (ofuda) at the corners of the capital
    for sx in (-1, 1):
        parts.append(P("pillar", "ofuda", [sx * 17.2 - (0.4 if sx > 0 else 0), 72, -6], [0.4, 26, 5]))
        parts.append(P("pillar", "ofuda", [-6 + sx * 3, 74, sx * 17.2 - (0.4 if sx > 0 else 0)], [5, 24, 0.4]))
    # capital and pagoda roofs
    parts.append(P("pillar", S, [-17, 99, -17], [34, 6, 34]))
    parts.append(P("pillar", B, [-17.6, 105, -17.6], [35.2, 2, 35.2]))
    parts.append(P("pillar", "roof", [-25, 107, -25], [50, 4, 50]))
    parts.append(P("pillar", "roof", [-21, 111, -21], [42, 3, 42]))
    parts.append(P("pillar", "roof", [-16, 114, -16], [32, 3, 32]))
    for sx in (-1, 1):
        for sz in (-1, 1):
            # up-turned roof corners
            parts.append(P("pillar", B, [sx * 24 - 2, 108, sz * 24 - 2], [4, 9, 4],
                           rot={"axis": "x", "angle": -24 * sz, "pivot": [sx * 24, 108, sz * 24]}))
            parts.append(P("pillar", "jewel", [sx * 24 - 1, 117, sz * 24 - 1], [2, 2, 2]))
    parts.append(P("pillar", S, [-11, 117, -11], [22, 10, 22]))
    parts.append(P("pillar", B, [-11.5, 121, -11.5], [23, 1.6, 23]))
    parts.append(P("pillar", "roof", [-17, 127, -17], [34, 3, 34]))
    parts.append(P("pillar", "roof", [-12, 130, -12], [24, 3, 24]))
    parts.append(P("pillar", B, [-3, 133, -3], [6, 6, 6]))
    # the floating jewel finial (spins in game)
    parts.append(P("finial", "jewel", [-4, 141, -4], [8, 8, 8]))
    parts.append(P("finial", "jewel", [-4, 141, -4], [8, 8, 8], rot={"axis": "y", "angle": 45, "pivot": [0, 145, 0]}))
    parts.append(P("finial", B, [-1, 149, -1], [2, 8, 2]))
    parts.append(P("finial", B, [-1, 133, -1], [2, 8, 2]))
    return parts


def relic_parts():
    parts = []
    B = "bronze"
    parts.append(P("relic", "stone", [-7, -1, -7], [14, 2, 14]))
    parts.append(P("relic", "stone", [-7, 17, -7], [14, 2, 14]))
    for sx in (-1, 1):
        for sz in (-1, 1):
            parts.append(P("relic", B, [sx * 6 - 1, 1, sz * 6 - 1], [2, 16, 2]))
    parts.append(P("relic", B, [-1, 19, -1], [2, 4, 2]))
    parts.append(P("relic", "jewel", [-1.5, 23, -1.5], [3, 3, 3]))
    parts.append(P("relic_core", "jewel", [-3.5, 5.5, -3.5], [7, 7, 7]))
    parts.append(P("relic_core", "jewel", [-3.5, 5.5, -3.5], [7, 7, 7], rot={"axis": "y", "angle": 45, "pivot": [0, 9, 0]}))
    for k in range(8):
        a = k * 45
        parts.append(P("relic_ring", B, [-2, 8.5, 9], [4, 1, 1.2], rot={"axis": "y", "angle": a, "pivot": [0, 9, 0]}))
        parts.append(P("relic_ring", "jewel", [-0.5, 8.4, 10.2], [1, 1.2, 0.3], rot={"axis": "y", "angle": a, "pivot": [0, 9, 0]}))
    return parts


BONES = {
    "pillar": [("pillar", None, [0, 0, 0]), ("finial", "pillar", [0, 145, 0])],
    "relic": [("relic", None, [0, 0, 0]), ("relic_core", "relic", [0, 9, 0]), ("relic_ring", "relic", [0, 9, 0])],
}

# ------------------------------------------------------------------------------------------- faces

FACES = ("north", "south", "east", "west", "up", "down")


def face_dims(f, lo, hi):
    sx, sy, sz = (hi[i] - lo[i] for i in range(3))
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[f]


def face_quad(f, lo, hi):
    """Corners TL, TR, BR, BL as seen from outside the face (texture u right, v down)."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[f]


def pack(parts, tpp, pad=2):
    """Shelf-pack every face into a square power-of-two atlas. Adds part['uv'][face] = [u, v, w, h] (texels)."""
    rects = []
    for i, p in enumerate(parts):
        p["uv"] = {}
        for f in FACES:
            w, h = face_dims(f, p["from"], p["to"])
            rects.append((max(1, math.ceil(w * tpp)), max(1, math.ceil(h * tpp)), i, f))
    rects.sort(key=lambda r: -r[1])
    size = 256
    while True:
        x = y = shelf = 0
        ok = True
        placed = []
        for w, h, i, f in rects:
            if x + w + pad * 2 > size:
                x, y = 0, y + shelf
                shelf = 0
            if y + h + pad * 2 > size:
                ok = False
                break
            placed.append((x + pad, y + pad, w, h, i, f))
            x += w + pad * 2
            shelf = max(shelf, h + pad * 2)
        if ok:
            break
        size *= 2
    for u, v, w, h, i, f in placed:
        parts[i]["uv"][f] = [u, v, w, h]
    return size


# ------------------------------------------------------------------------------------------- paint

def value_noise(h, w, cell, seed):
    r = np.random.default_rng(seed)
    gh, gw = h // cell + 3, w // cell + 3
    g = r.random((gh, gw)).astype(np.float32)
    yy = np.arange(h) / cell
    xx = np.arange(w) / cell
    y0 = yy.astype(int)
    x0 = xx.astype(int)
    fy = (yy - y0)[:, None]
    fx = (xx - x0)[None, :]
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(h, w, cell, seed, octaves=4):
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        c = max(1, cell >> o)
        out += value_noise(h, w, c, seed + o * 31) * amp
        tot += amp
        amp *= 0.5
    return out / tot


def ridge(n, sharp=10.0):
    return np.clip(1 - np.abs(n - 0.5) * sharp, 0, 1)


def load_mask(path, w, h):
    """Greyscale glyph mask resampled to w x h (nearest-bilinear via numpy)."""
    img = bpy.data.images.load(path, check_existing=True)
    iw, ih = img.size
    a = np.array(img.pixels[:], dtype=np.float32).reshape(ih, iw, 4)[::-1, :, 0]
    ys = (np.linspace(0, ih - 1, h)).astype(int)
    xs = (np.linspace(0, iw - 1, w)).astype(int)
    return a[ys][:, xs]


def blur3(a):
    p = np.pad(a, 1, mode="edge")
    return (p[:-2, :-2] + p[:-2, 1:-1] + p[:-2, 2:] + p[1:-1, :-2] + p[1:-1, 1:-1] + p[1:-1, 2:] + p[2:, :-2] + p[2:, 1:-1] + p[2:, 2:]) / 9


def paint(parts, size, tpp, glyph=None, glow=(1, 1, 1), seed=1):
    """Returns (rgb atlas, glow atlas rgba) as float arrays [size, size, *]."""
    rgb = np.zeros((size, size, 3), np.float32)
    gl = np.zeros((size, size, 4), np.float32)
    glow = np.array(glow, np.float32)
    for pi, p in enumerate(parts):
        for f in FACES:
            u, v, w, h = p["uv"][f]
            kind = p["faces"].get(f, p["kind"])
            s = seed * 1000 + pi * 13 + FACES.index(f)
            tile, gtile = paint_face(kind, w, h, tpp, s, glyph, glow, f)
            rgb[v:v + h, u:u + w] = tile
            gl[v:v + h, u:u + w] = gtile
    # bleed into the padding so filtering never picks up black seams
    for _ in range(2):
        mask = rgb.sum(axis=2) > 0
        shifted = [np.roll(rgb, s, axis=a) for a in (0, 1) for s in (-1, 1)]
        for sh in shifted:
            empty = ~mask
            rgb[empty] = np.maximum(rgb[empty], sh[empty])
    return np.clip(rgb, 0, 1), np.clip(gl, 0, 1)


def edge_wear(w, h, width):
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy)).astype(np.float32)
    return np.clip(1 - d / max(1, width), 0, 1)


def paint_face(kind, w, h, tpp, seed, glyph, glow, face):
    g = np.zeros((h, w, 4), np.float32)
    n = fbm(h, w, max(2, 6 * tpp), seed, 4)
    fine = fbm(h, w, max(1, tpp), seed + 7, 2)
    if kind in ("stone", "inscribed"):
        base = 0.055 + 0.06 * n + 0.025 * fine
        col = np.stack([base * 1.02, base * 0.98, base * 0.97], -1)
        # weathering: pale mineral streaks running down, worn bright edges, lichen
        streak = fbm(h, w * 4, max(2, 10 * tpp), seed + 3, 3)[:, ::4]
        col += (np.clip(streak - 0.62, 0, 1) * 0.25)[..., None] * np.array([0.9, 0.88, 0.82])
        col += (edge_wear(w, h, 1.2 * tpp) * 0.09 * (0.5 + n))[..., None]
        lichen = np.clip((fbm(h, w, max(2, 5 * tpp), seed + 11, 3) - 0.72) * 4, 0, 1)
        col = col * (1 - lichen[..., None] * 0.6) + np.array([0.16, 0.19, 0.12]) * lichen[..., None] * 0.6
        # hairline cracks that glow faintly from within
        cr = ridge(fbm(h, w, max(2, 9 * tpp), seed + 5, 4), 22)
        cr *= np.clip((fbm(h, w, max(2, 14 * tpp), seed + 6, 2) - 0.45) * 4, 0, 1)   # only some veins
        col *= (1 - 0.6 * cr)[..., None]
        g[..., :3] = glow * (cr * 0.22)[..., None]
        g[..., 3] = cr * 0.22
        if kind == "inscribed" and glyph is not None and face in ("north", "south", "east", "west"):
            gw = int(w * 0.84)
            gh = gw
            top = int(h * 0.10)
            left = (w - gw) // 2
            m = np.zeros((h, w), np.float32)
            m[top:top + gh, left:left + gw] = load_mask(glyph, gw, gh)
            # carved: dark recess, chiselled bevel lit from above, glowing ink in the cut
            sh = np.roll(m, tpp // 2 + 1, axis=0)
            bevel = np.clip(sh - m, 0, 1)
            col = col * (1 - 0.85 * m[..., None]) + np.array([0.02, 0.015, 0.015]) * m[..., None]
            col += (bevel * 0.22)[..., None]
            core = np.clip((m - 0.35) * 2, 0, 1)
            g[..., :3] = np.maximum(g[..., :3], glow * core[..., None])
            g[..., 3] = np.maximum(g[..., 3], core)
            # a column of small carved marks beneath the kanji
            r = np.random.default_rng(seed)
            y = top + gh + 2 * tpp
            while y < h - 3 * tpp:
                L = int(r.uniform(0.15, 0.45) * w)
                x0 = (w - L) // 2
                th = max(1, tpp // 2)
                col[y:y + th, x0:x0 + L] *= 0.25
                g[y:y + th, x0:x0 + L, :3] = glow * 0.55
                g[y:y + th, x0:x0 + L, 3] = 0.55
                y += int(r.uniform(2.0, 3.5) * tpp)
    elif kind == "bronze":
        base = np.array([0.30, 0.20, 0.10]) * (0.7 + 0.5 * n)[..., None]
        verd = np.clip((fbm(h, w, max(2, 4 * tpp), seed + 9, 3) - 0.55) * 3, 0, 1)
        col = base * (1 - verd[..., None]) + np.array([0.20, 0.34, 0.28]) * (0.7 + 0.4 * fine)[..., None] * verd[..., None]
        col += (edge_wear(w, h, 0.8 * tpp) * 0.16)[..., None] * np.array([0.9, 0.7, 0.4])
    elif kind == "roof":
        yy = np.arange(h)[:, None]
        rows = (np.sin(yy / (1.6 * tpp) * math.pi) * 0.5 + 0.5)
        base = 0.04 + 0.05 * rows + 0.03 * n
        col = np.stack([base, base * 0.98, base * 1.04], -1) * np.ones((h, w, 1))
        col += (edge_wear(w, h, 0.8 * tpp) * 0.06)[..., None]
    elif kind == "rope":
        yy, xx = np.mgrid[0:h, 0:w]
        tw = np.sin((xx + yy * 1.4) / (1.1 * tpp) * math.pi) * 0.5 + 0.5
        col = np.array([0.62, 0.52, 0.32]) * (0.55 + 0.45 * tw)[..., None] * (0.8 + 0.3 * fine)[..., None]
    elif kind == "paper":
        col = np.array([0.86, 0.82, 0.70]) * (0.85 + 0.2 * n)[..., None]
        col *= (1 - edge_wear(w, h, 0.6 * tpp) * 0.25)[..., None]
    elif kind == "ofuda":
        col = np.array([0.84, 0.78, 0.62]) * (0.85 + 0.2 * n)[..., None]
        r = np.random.default_rng(seed)
        # brushed ink strokes down the strip and a red seal stamp
        x0 = w // 2
        for y in range(2 * tpp, h - 6 * tpp, max(2, int(2.2 * tpp))):
            L = int(r.uniform(0.3, 0.7) * w)
            col[y:y + max(1, tpp // 2), max(0, x0 - L // 2):x0 + L // 2] = [0.05, 0.04, 0.04]
        sy = h - 5 * tpp
        col[sy:sy + 3 * tpp, max(0, x0 - tpp):x0 + tpp] = [0.62, 0.08, 0.06]
        g[sy:sy + 3 * tpp, max(0, x0 - tpp):x0 + tpp, :3] = glow * 0.5
        g[sy:sy + 3 * tpp, max(0, x0 - tpp):x0 + tpp, 3] = 0.5
    elif kind == "jewel":
        yy, xx = np.mgrid[0:h, 0:w]
        rr = np.hypot((yy - h / 2) / max(1, h / 2), (xx - w / 2) / max(1, w / 2))
        core = np.clip(1.1 - rr, 0, 1)
        col = glow * (0.35 + 0.65 * core)[..., None]
        g[..., :3] = glow * (0.7 + 0.3 * core)[..., None] + (core ** 3)[..., None] * 0.3
        g[..., 3] = 1.0
    else:
        col = np.full((h, w, 3), 0.5, np.float32)
    return np.clip(col, 0, 1), np.clip(g, 0, 1)


# ------------------------------------------------------------------------------------------- export

def export_model(parts, size, path, name):
    bones = [{"name": b, "parent": par, "pivot": piv} for b, par, piv in BONES[name]]
    cubes = []
    for p in parts:
        c = {"bone": p["bone"], "from": [round(v, 3) for v in p["from"]], "to": [round(v, 3) for v in p["to"]],
             "uv": p["uv"]}
        if p["rot"]:
            c["rot"] = p["rot"]
        cubes.append(c)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump({"format": "doa_model_1", "texture": [size, size], "bones": bones, "cubes": cubes}, f, separators=(",", ":"))


def save_rgba(arr, path):
    h, w = arr.shape[:2]
    if arr.shape[2] == 3:
        arr = np.concatenate([arr, np.ones((h, w, 1), np.float32)], -1)
    img = bpy.data.images.new(os.path.basename(path), w, h, alpha=True)
    img.pixels[:] = arr[::-1].reshape(-1)
    img.filepath_raw = path
    img.file_format = "PNG"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save()
    return img


# ------------------------------------------------------------------------------------------- blender scene

def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def to_b(p):
    return Vector((p[0] / 16, -p[2] / 16, p[1] / 16))


def rotate(pt, rot):
    if not rot:
        return pt
    a = math.radians(rot["angle"])
    c, s = math.cos(a), math.sin(a)
    x, y, z = (pt[i] - rot["pivot"][i] for i in range(3))
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + rot["pivot"][0], y + rot["pivot"][1], z + rot["pivot"][2])


def build_mesh(name, parts, size, atlas_img, glow_img, offset):
    verts, faces, uvs = [], [], []
    for p in parts:
        for f in FACES:
            u, v, w, h = p["uv"][f]
            q = [rotate(c, p["rot"]) for c in face_quad(f, p["from"], p["to"])]
            uv = [(u, v), (u + w, v), (u + w, v + h), (u, v + h)]
            base = len(verts)
            for c in q:
                verts.append(to_b(c) + offset)
            # BL, BR, TR, TL: counter-clockwise seen from outside -> outward normal
            faces.append((base + 3, base + 2, base + 1, base + 0))
            for k in (3, 2, 1, 0):
                uvs.append((uv[k][0] / size, 1 - uv[k][1] / size))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(v) for v in verts], [], faces)
    mesh.update()
    layer = mesh.uv_layers.new(name="UV")
    for i, loop in enumerate(layer.data):
        loop.uv = uvs[i]
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)

    mat = bpy.data.materials.new(name + "_mat")
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = atlas_img
    tex.interpolation = "Closest"
    gtex = nt.nodes.new("ShaderNodeTexImage")
    gtex.image = glow_img
    gtex.interpolation = "Closest"
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(gtex.outputs["Color"], bsdf.inputs["Emission Color"])
    bsdf.inputs["Emission Strength"].default_value = 6.0
    bsdf.inputs["Roughness"].default_value = 0.72
    mesh.materials.append(mat)
    return obj


def setup_render():
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 40
    scene.cycles.use_denoising = False
    scene.render.film_transparent = False
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.012, 0.010, 0.012, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
    scene.world = world
    bpy.ops.mesh.primitive_plane_add(size=80, location=(0, 0, 0))
    ground = bpy.context.active_object
    gm = bpy.data.materials.new("ground")
    gm.use_nodes = True
    gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.05, 0.045, 0.04, 1)
    gm.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.9
    ground.data.materials.append(gm)
    sun = bpy.data.lights.new("moon", "SUN")
    sun.energy = 1.6
    sun.color = (0.85, 0.85, 1.0)
    so = bpy.data.objects.new("moon", sun)
    so.rotation_euler = (math.radians(55), 0, math.radians(35))
    scene.collection.objects.link(so)
    cam = bpy.data.cameras.new("cam")
    co = bpy.data.objects.new("cam", cam)
    scene.collection.objects.link(co)
    scene.camera = co
    return scene, co


def shoot(scene, cam, path, loc, target, res, lens=40):
    cam.location = Vector(loc)
    d = Vector(target) - cam.location
    cam.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    cam.data.lens = lens
    scene.render.resolution_x = res
    scene.render.resolution_y = res
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


# ------------------------------------------------------------------------------------------- main

def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--glyphs", required=True)
    ap.add_argument("--no-render", action="store_true")
    a = ap.parse_args(argv)
    assets, out, glyphs = (os.path.abspath(x) for x in (a.assets, a.out, a.glyphs))
    os.makedirs(out, exist_ok=True)
    reset()

    # ---- pillar: one geometry, seven painted variants
    parts = pillar_parts()
    size = pack(parts, TPP)
    export_model(parts, size, os.path.join(assets, "models/entity/pillar.json"), "pillar")
    imgs = []
    for i, (pid, col) in enumerate(PILLARS):
        rgb, gl = paint(parts, size, TPP, glyph=os.path.join(glyphs, pid + ".png"), glow=col, seed=7 + i)
        ai = save_rgba(rgb, os.path.join(assets, f"textures/entity/pillar/{pid}.png"))
        gi = save_rgba(gl, os.path.join(assets, f"textures/entity/pillar/{pid}_glow.png"))
        imgs.append((pid, ai, gi))
        print(f"[pillar] {pid}: atlas {size}px")

    # ---- relic
    rparts = relic_parts()
    rsize = pack(rparts, 8)
    export_model(rparts, rsize, os.path.join(assets, "models/entity/relic.json"), "relic")
    rrgb, rgl = paint(rparts, rsize, 8, glow=(1, 1, 1), seed=99)
    rai = save_rgba(rrgb, os.path.join(assets, "textures/entity/relic.png"))
    rgi = save_rgba(rgl, os.path.join(assets, "textures/entity/relic_glow.png"))
    print(f"[relic] atlas {rsize}px")

    if a.no_render:
        return
    scene, cam = setup_render()
    # the ring of seven, as called down
    for i, (pid, ai, gi) in enumerate(imgs):
        ang = i / 7 * math.tau
        off = Vector((math.sin(ang) * 6.0, math.cos(ang) * 6.0, 0))
        build_mesh(f"pillar_{pid}", parts, size, ai, gi, off - Vector((0, 0, 0)))
    shoot(scene, cam, os.path.join(out, "ring.png"), (0, -22, 12), (0, 0, 4), 1200, lens=28)
    shoot(scene, cam, os.path.join(out, "ring_low.png"), (3, -12, 1.5), (0, 0, 6), 1200, lens=22)
    # each pillar alone, front on
    for o in list(bpy.data.objects):
        if o.name.startswith("pillar_"):
            o.hide_render = True
    for i, (pid, ai, gi) in enumerate(imgs):
        o = bpy.data.objects[f"pillar_{pid}"]
        o.hide_render = False
        c = o.matrix_world @ Vector((0, 0, 0))
        mesh_center = sum((o.matrix_world @ v.co for v in o.data.vertices), Vector()) / len(o.data.vertices)
        shoot(scene, cam, os.path.join(out, f"pillar_{pid}.png"), (mesh_center.x * 1.0, mesh_center.y - 12, 4.5),
              (mesh_center.x, mesh_center.y, 4.2), 800, lens=35)
        o.hide_render = True
    # icon
    o = bpy.data.objects["pillar_death"]
    o.hide_render = False
    mc = sum((o.matrix_world @ v.co for v in o.data.vertices), Vector()) / len(o.data.vertices)
    shoot(scene, cam, os.path.join(assets, "icon.png"), (mc.x + 6, mc.y - 9, 6), (mc.x, mc.y, 4.4), 128, lens=40)
    o.hide_render = True
    # relic
    build_mesh("relic", rparts, rsize, rai, rgi, Vector((40, 0, 1)))
    shoot(scene, cam, os.path.join(out, "relic.png"), (41.6, -2.2, 2.4), (40, 0, 1.6), 700, lens=50)


if __name__ == "__main__":
    main()
