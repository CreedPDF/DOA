"""
Realistic space for DOA's cinematic moves, painted procedurally from 3D noise sampled on the sphere and LIT here:
every planet is baked with the scene's fixed sun (relief from its own height field, soft terminators, atmospheric
reddening at dusk, ocean glint, cloud shadows, city lights on the night side), so the game can draw it unlit and it
looks like a photograph. The sun direction and each planet's orientation must match SpaceScene.java.

UV mapping (Blender UV sphere, see planet.json): u = atan2(x, z)/2pi + 0.25, v = 0.5 - lat/pi   (Minecraft axes)

Output: src/main/resources/assets/doa/textures/space/
  earth.png (4096) earth_clouds.png  moon.png  red.png  red_cracks.png  giant.png  ring.png
  milkyway.png (4096, inside of the sky)  galaxy.png (face-on spiral)  deepfield.png (galaxies + cosmic web)
  glare.png  corona.png  white.png
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/doa/textures/space"

SUN = np.array([-0.62, 0.18, -0.76], np.float32)
SUN /= np.linalg.norm(SUN)


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], np.float32)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], np.float32)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], np.float32)


# planet orientations (JOML: new Quaternionf().rotateZ(a).rotateY(b) == Rz @ Ry)
EARTH_ROT = rot_x(-1.2) @ rot_z(0.41) @ rot_y(1.9)
MOON_ROT = rot_y(2.2)
GIANT_ROT = rot_z(0.38) @ rot_x(0.22)
RED_ROT = rot_z(-0.2) @ rot_y(0.7)


# ------------------------------------------------------------------------------------------ noise

def hash3(ix, iy, iz, seed):
    h = (ix.astype(np.uint32) * np.uint32(374761393) + iy.astype(np.uint32) * np.uint32(668265263)
         + iz.astype(np.uint32) * np.uint32(2147483647) + np.uint32(seed * 1013904223 & 0xFFFFFFFF))
    h = (h ^ (h >> np.uint32(13))) * np.uint32(1274126177)
    h = h ^ (h >> np.uint32(16))
    return (h & np.uint32(0xFFFFFF)).astype(np.float32) / np.float32(0xFFFFFF)


def vnoise(p, seed):
    f = np.floor(p)
    t = p - f
    t = t * t * (3 - 2 * t)
    i = f.astype(np.int64)
    out = 0
    for dx in (0, 1):
        wx = t[..., 0] if dx else 1 - t[..., 0]
        for dy in (0, 1):
            wy = t[..., 1] if dy else 1 - t[..., 1]
            for dz in (0, 1):
                wz = t[..., 2] if dz else 1 - t[..., 2]
                out = out + wx * wy * wz * hash3(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
    return out


def fbm(p, seed, octaves=6, lac=2.03, gain=0.5):
    amp, tot, out = 1.0, 0.0, 0
    for o in range(octaves):
        out = out + vnoise(p * (lac ** o) + o * 17.3, seed + o * 31) * amp
        tot += amp
        amp *= gain
    return out / tot


def ridged(p, seed, octaves=6):
    amp, tot, out, w = 1.0, 0.0, 0, 1.0
    for o in range(octaves):
        n = 1 - np.abs(vnoise(p * (2.05 ** o) + o * 7.1, seed + o * 17) * 2 - 1)
        n = n * n * w
        w = np.clip(n * 1.6, 0, 1)
        out = out + n * amp
        tot += amp
        amp *= 0.5
    return out / tot


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim:
        t = t[..., None]
    return np.asarray(a, np.float32) * (1 - t) + np.asarray(b, np.float32) * t


def save(arr, name, alpha=None):
    OUT.mkdir(parents=True, exist_ok=True)
    a = np.clip(arr, 0, 1)
    if alpha is not None:
        a = np.concatenate([a, np.clip(alpha, 0, 1)[..., None]], -1)
    elif a.shape[-1] == 3:
        a = np.concatenate([a, np.ones(a.shape[:2] + (1,), np.float32)], -1)
    Image.fromarray((a * 255 + 0.5).astype(np.uint8), "RGBA").save(OUT / name, optimize=True)
    print(" ", name, a.shape[1], "x", a.shape[0], flush=True)


# ------------------------------------------------------------------------------------------ sphere geometry

def grid(w, h, rows=None):
    """Local unit positions for a texel block (Minecraft axes, Blender UV-sphere mapping)."""
    r0, r1 = rows or (0, h)
    u = (np.arange(w, dtype=np.float32) + 0.5) / w
    v = (np.arange(r0, r1, dtype=np.float32) + 0.5) / h
    uu, vv = np.meshgrid(u, v)
    lat = (0.5 - vv) * math.pi
    a = (uu - 0.25) * 2 * math.pi
    cl = np.cos(lat)
    p = np.stack([cl * np.sin(a), np.sin(lat), cl * np.cos(a)], -1).astype(np.float32)
    east = np.stack([np.cos(a), np.zeros_like(a), -np.sin(a)], -1).astype(np.float32)
    north = np.stack([-np.sin(lat) * np.sin(a), cl, -np.sin(lat) * np.cos(a)], -1).astype(np.float32)
    return p, lat.astype(np.float32), east, north


def bump_normals(height, p, east, north, w, h, strength):
    """Perturb the sphere normal by the height field's gradient (height in planet radii)."""
    dlon = 2 * math.pi / w
    dlat = math.pi / h
    gx = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) / (2 * dlon)
    gy = (np.roll(height, 1, 0) - np.roll(height, -1, 0)) / (2 * dlat)
    gy[0] = gy[1]
    gy[-1] = gy[-2]
    cl = np.maximum(np.sqrt(p[..., 0] ** 2 + p[..., 2] ** 2), 0.05)
    n = p - strength * (gx / cl)[..., None] * east - strength * gy[..., None] * north
    return n / np.linalg.norm(n, axis=-1, keepdims=True)


def hillshade(n, p, east, north, strength=1.0):
    """Map-style relief shading: light from the upper left of every patch of ground, relative to a flat surface."""
    l = -0.55 * east + 0.55 * north + 0.63 * p
    l /= np.linalg.norm(l, axis=-1, keepdims=True)
    return np.clip(1 + ((n * l).sum(-1) - (p * l).sum(-1)) * 6 * strength, 0.55, 1.35)


def light(n_local, rot, wrap=0.0):
    sun_local = rot.T @ SUN
    d = n_local @ sun_local
    return d, np.clip((d + wrap) / (1 + wrap), 0, 1)


def chunks(h, step=256):
    for r in range(0, h, step):
        yield r, min(h, r + step)


# ------------------------------------------------------------------------------------------ earth

def earth_fields(p):
    warp = np.stack([fbm(p * 1.3 + 7, 11, 5), fbm(p * 1.3 + 13, 12, 5), fbm(p * 1.3 + 19, 13, 5)], -1) - 0.5
    q = p + warp * 0.55
    cont = fbm(q * 1.5, 21, 8) * 0.72 + ridged(q * 2.6, 22, 6) * 0.28
    return q, cont


def earth():
    W, H = 4096, 2048
    sea = 0.5
    # pass 1: elevation (needed whole for gradients)
    elev = np.zeros((H, W), np.float32)
    mount = np.zeros((H, W), np.float32)
    for r0, r1 in chunks(H):
        p, lat, _, _ = grid(W, H, (r0, r1))
        q, cont = earth_fields(p)
        m = ridged(p * 6.5 + 3, 31, 7)
        land = np.clip((cont - sea) / (1 - sea), 0, 1)
        elev[r0:r1] = np.where(cont > sea, land * 0.6 + m * land ** 0.7 * 0.9, (cont - sea) * 1.2)
        mount[r0:r1] = m * smooth(0.02, 0.3, land)
    cloud_a = np.zeros((H, W), np.float32)
    for r0, r1 in chunks(H):
        p, lat, _, _ = grid(W, H, (r0, r1))
        cw = np.stack([fbm(p * 2.2 + 5, 81, 5), fbm(p * 2.2 + 9, 82, 5), fbm(p * 2.2 + 15, 83, 5)], -1) - 0.5
        cq = p + cw * 0.8
        c = fbm(cq * 3.5 + np.stack([np.zeros_like(lat), lat * 2.4, np.zeros_like(lat)], -1), 91, 8)
        band = 0.62 + 0.22 * np.cos(lat * 6.2) - 0.25 * np.exp(-(lat / 0.35) ** 2) * 0.4
        storms = smooth(0.6, 0.8, fbm(p * 1.1 + 40, 92, 3)) * 0.25
        cloud_a[r0:r1] = smooth(0.5, 0.76, c * band + 0.26 + storms)
    col_out = np.zeros((H, W, 3), np.float32)
    cl_out = np.zeros((H, W, 3), np.float32)
    night_out = np.zeros((H, W, 3), np.float32)
    # the sun as seen from the earth's frame, projected for cloud shadows
    sun_local = EARTH_ROT.T @ SUN
    for r0, r1 in chunks(H):
        p, lat, east, north = grid(W, H, (r0, r1))
        e = elev[r0:r1]
        land = e > 0
        moist = smooth(0.25, 0.75, fbm(p * 2.2 + 3, 41, 6))
        alat = np.abs(lat) / (math.pi / 2)
        temp = 1 - alat * 1.15 - np.maximum(e, 0) * 0.8 + (fbm(p * 3, 51, 4) - 0.5) * 0.22
        desert_belt = np.exp(-((alat - 0.28) / 0.12) ** 2)
        dry = np.clip(1 - moist - desert_belt * 0.45, 0, 1)
        # land albedo: realistic, desaturated hues seen from orbit
        sand = np.array([0.62, 0.50, 0.33])
        rock = np.array([0.36, 0.31, 0.26])
        steppe = np.array([0.40, 0.37, 0.24])
        grass = np.array([0.17, 0.23, 0.10])
        forest = np.array([0.07, 0.13, 0.055])
        jungle = np.array([0.045, 0.11, 0.04])
        tundra = np.array([0.33, 0.31, 0.26])
        snow = np.array([0.88, 0.90, 0.93])
        wet = smooth(0.35, 0.7, 1 - dry)
        lowl = mix(mix(sand, steppe, smooth(0.25, 0.55, 1 - dry)), mix(grass, forest, smooth(0.55, 0.85, 1 - dry)), wet)
        lowl = mix(lowl, jungle, smooth(0.7, 0.9, temp) * smooth(0.6, 0.85, 1 - dry))
        lowl = mix(lowl, tundra, smooth(0.35, 0.18, temp))
        lowl = mix(lowl, rock, smooth(0.25, 0.65, mount[r0:r1]))
        detail = fbm(p * 60, 61, 4)
        lowl = lowl * (0.82 + 0.32 * detail[..., None])
        rivers = smooth(0.012, 0.0, np.abs(fbm(p * 9 + 2, 63, 5) - 0.5)) * wet * (e < 0.25)
        lowl = mix(lowl, [0.04, 0.07, 0.09], rivers * 0.6)
        depth = np.clip(-e / 0.6, 0, 1)
        ocean = mix([0.06, 0.17, 0.22], [0.012, 0.045, 0.11], smooth(0.0, 0.5, depth))
        ocean = mix(ocean, [0.10, 0.28, 0.30], smooth(0.035, 0.0, depth) * 0.7)
        albedo = np.where(land[..., None], lowl, ocean)
        ice = smooth(0.1, -0.02, temp + (fbm(p * 7, 71, 5) - 0.5) * 0.14)
        ice = np.maximum(ice, smooth(0.5, 0.85, mount[r0:r1]) * land * smooth(0.6, 0.3, temp))
        albedo = mix(albedo, snow, ice)
        # relief shading that reads at any sun angle (the game lights the sphere itself)
        n = bump_normals(np.where(land, e, 0) * 0.012, p, east, north, W, H, 1.0)
        hs = hillshade(n, p, east, north, 1.6)
        lit = albedo * hs[..., None]
        col_out[r0:r1] = np.clip(lit, 0, 1) ** (1 / 1.05)
        # city lights (drawn on the night side by the game)
        cities = fbm(p * 46, 101, 3) * fbm(p * 10, 102, 3)
        lights = smooth(0.30, 0.48, cities) * land * (1 - ice) * smooth(0.1, 0.45, temp) * smooth(0.0, 0.1, e) * smooth(0.45, 0.1, e)
        night_out[r0:r1] = np.stack([lights, lights * 0.68, lights * 0.32], -1)
        # clouds: bright tops, greyer thin edges
        cl = np.array([0.97, 0.97, 0.98]) * (0.78 + 0.25 * fbm(p * 40, 93, 3)[..., None]) * (0.85 + 0.15 * cloud_a[r0:r1, :, None])
        cl_out[r0:r1] = np.clip(cl, 0, 1)
    save(col_out, "earth.png")
    save(cl_out, "earth_clouds.png", cloud_a * 0.95)
    save(night_out, "earth_night.png")


# ------------------------------------------------------------------------------------------ rocky bodies

def craters(p, seed, count, rmin, rmax, depth=1.0):
    rng = np.random.default_rng(seed)
    h = np.zeros(p.shape[:2], np.float32)
    rays = np.zeros(p.shape[:2], np.float32)
    for k in range(count):
        c = rng.normal(size=3).astype(np.float32)
        c /= np.linalg.norm(c)
        r = rmin + (rmax - rmin) * rng.random() ** 6
        d = np.arccos(np.clip(p @ c, -1, 1)) / r
        m = d < 3.5
        if not m.any():
            continue
        dd = d[m]
        # shallow, flat-floored bowls with soft raised rims (real craters are far shallower than they are wide)
        bowl = np.where(dd < 1, -(1 - smooth(0.55, 1.0, dd)) * 0.5 - (1 - dd * dd) * 0.15, 0)
        rim = np.exp(-((dd - 1.0) / 0.22) ** 2) * 0.22
        ejecta = np.where(dd > 1, np.exp(-(dd - 1) * 2.2) * 0.06, 0)
        peak = np.where(dd < 0.18, (0.18 - dd) * 1.2 * (r > rmin * 8), 0)
        h[m] += (bowl + rim + ejecta + peak) * r * 0.16 * depth
        if r > rmin * 8 and rng.random() < 0.35:
            ang = np.arctan2(p[..., 1][m] - c[1], p[..., 0][m] - c[0] + p[..., 2][m] - c[2])
            streak = np.clip(np.sin(ang * rng.integers(9, 17) + rng.random() * 6) * 2 - 1.2, 0, 1)
            rays[m] += np.where((dd > 1) & (dd < 3.5), streak * np.exp(-(dd - 1) * 0.9), 0) * 0.6
    return h, rays


def moon():
    W, H = 2048, 1024
    p, lat, east, north = grid(W, H)
    maria = smooth(0.54, 0.62, fbm(p * 1.3 + 3, 202, 6))
    h, rays = craters(p, 203, 700, 0.004, 0.16)
    h = h + (fbm(p * 18, 205, 6) - 0.5) * 0.0025 - maria * 0.002
    base = 0.47 + 0.12 * (fbm(p * 5, 201, 6) - 0.5) + fbm(p * 80, 206, 3) * 0.06
    alb = base * (1 - 0.5 * maria) + rays * 0.12
    n = bump_normals(h, p, east, north, W, H, 1.0)
    lit = alb * hillshade(n, p, east, north, 2.5)
    col = np.stack([lit * 1.01, lit, lit * 0.97], -1)
    save(col, "moon.png")


def red():
    W, H = 2048, 1024
    p, lat, east, north = grid(W, H)
    warp = np.stack([fbm(p * 1.6 + 1, 601, 4), fbm(p * 1.6 + 2, 602, 4), fbm(p * 1.6 + 3, 603, 4)], -1) - 0.5
    q = p + warp * 0.4
    hi = fbm(q * 1.4, 604, 7)
    canyon = smooth(0.03, 0.0, np.abs(fbm(q * np.array([2.0, 6.0, 2.0], np.float32), 605, 5) - 0.5)) * smooth(0.4, 0.0, np.abs(lat))
    h, rays = craters(p, 606, 350, 0.005, 0.1, 0.6)
    height = (hi - 0.5) * 0.008 - canyon * 0.006 + h
    dust = fbm(p * 3.5 + 9, 607, 6)
    alb = mix([0.55, 0.27, 0.13], [0.33, 0.15, 0.08], smooth(0.35, 0.7, dust))
    alb = mix(alb, [0.65, 0.42, 0.25], smooth(0.62, 0.8, hi) * 0.6)
    alb = mix(alb, [0.18, 0.09, 0.06], canyon * 0.8)
    alb = alb * (0.85 + 0.25 * fbm(p * 70, 608, 3)[..., None])
    cap = smooth(0.78, 0.9, np.abs(lat) / (math.pi / 2) + (fbm(p * 8, 609, 4) - 0.5) * 0.12)
    alb = mix(alb, [0.92, 0.88, 0.84], cap)
    n = bump_normals(height, p, east, north, W, H, 1.0)
    lit = alb * hillshade(n, p, east, north, 2.0)[..., None]
    save(lit, "red.png")
    # the fissures that open when the planet is broken: a glowing network (white, tinted molten in game)
    vor = np.zeros(lat.shape, np.float32)
    rng = np.random.default_rng(610)
    seeds = []
    while len(seeds) < 34:
        s_ = rng.normal(size=3).astype(np.float32)
        s_ /= np.linalg.norm(s_)
        if all(np.dot(s_, o) < 0.9 for o in seeds):
            seeds.append(s_)
    seeds = np.stack(seeds)
    dots = p.reshape(-1, 3) @ seeds.T
    srt = np.sort(dots, axis=1)
    edge = (srt[:, -1] - srt[:, -2]).reshape(lat.shape)
    jag = fbm(p * 30, 611, 3) * 0.012
    vor = smooth(0.012, 0.0, edge - jag) + smooth(0.03, 0.0, edge - jag) * 0.25
    fine = smooth(0.012, 0.0, np.abs(fbm(p * 12, 612, 5) - 0.5)) * 0.6
    glow = np.clip(vor + fine * smooth(0.06, 0.0, edge), 0, 1)
    rgb = np.stack([glow, glow * 0.55, glow * 0.18], -1)
    save(rgb, "red_cracks.png")


# ------------------------------------------------------------------------------------------ gas giant

def giant():
    W, H = 2048, 1024
    p, lat, east, north = grid(W, H)
    turb = np.stack([fbm(p * 3 + 1, 301, 6), fbm(p * 3 + 2, 302, 6), fbm(p * 3 + 3, 303, 6)], -1) - 0.5
    q = p + turb * np.array([0.12, 0.03, 0.12], np.float32)
    y = q[..., 1]
    shear = fbm(p * np.array([10, 2, 10], np.float32), 304, 5)
    bands = np.sin(y * 24 + shear * 2.5) * 0.5 + 0.5
    fine = np.sin(y * 85 + fbm(p * np.array([24, 4, 24], np.float32), 305, 5) * 6) * 0.5 + 0.5
    pal = np.array([[0.90, 0.82, 0.66], [0.78, 0.60, 0.40], [0.60, 0.40, 0.25], [0.86, 0.72, 0.52], [0.52, 0.34, 0.22], [0.93, 0.88, 0.76]])
    idx = np.clip(bands * 0.7 + fine * 0.3, 0, 0.999) * (len(pal) - 1)
    i0 = np.floor(idx).astype(int)
    t = (idx - i0)[..., None]
    col = pal[i0] * (1 - t) + pal[np.minimum(i0 + 1, len(pal) - 1)] * t
    # vortices along the belts
    rng = np.random.default_rng(306)
    for k in range(14):
        la = rng.uniform(-0.9, 0.9)
        lo = rng.uniform(0, 2 * math.pi)
        c = np.array([math.cos(la) * math.sin(lo), math.sin(la), math.cos(la) * math.cos(lo)], np.float32)
        size = 0.03 + 0.05 * rng.random() if k else 0.16
        d = np.arccos(np.clip(p @ c, -1, 1)) / size
        m = d < 1.6
        sw = np.exp(-d[m] ** 2 * 1.4)
        tone = np.array([0.74, 0.32, 0.18]) if k == 0 else np.array([0.95, 0.92, 0.85])
        col[m] = col[m] * (1 - sw[:, None] * 0.8) + tone * sw[:, None] * 0.8
    col *= (0.88 + 0.18 * fbm(p * 30, 307, 3))[..., None]
    col = mix(col, [0.52, 0.44, 0.38], smooth(0.78, 1.0, np.abs(lat) / (math.pi / 2)) * 0.6)
    save(col, "giant.png")
    # ring: u = radius (inner -> outer)
    Wr = 2048
    r = np.linspace(0, 1, Wr)
    dens = np.zeros(Wr)
    for k in range(260):
        c, w, a = rng.random(), 0.001 + rng.random() * 0.02, rng.random()
        dens += a * np.exp(-((r - c) / w) ** 2)
    dens = dens / dens.max()
    dens *= smooth(0.0, 0.04, r) * smooth(1.0, 0.94, r)
    dens[(r > 0.58) & (r < 0.625)] *= 0.06
    dens[(r > 0.84) & (r < 0.85)] *= 0.2
    colr = np.stack([0.84 - 0.18 * r, 0.74 - 0.22 * r, 0.60 - 0.22 * r], -1)
    ring = np.repeat(np.concatenate([colr, np.clip(dens * 1.2, 0, 1)[:, None] * 0.9], -1)[None], 16, 0)
    save(ring[..., :3], "ring.png", ring[..., 3])


# ------------------------------------------------------------------------------------------ sky

def milkyway():
    W, H = 4096, 2048
    out = np.zeros((H, W, 3), np.float32)
    a = math.radians(62)
    up = np.array([0, math.cos(a), math.sin(a)], np.float32)
    core_dir = np.array([0.8, -0.2, 0.0], np.float32)
    core_dir /= np.linalg.norm(core_dir)
    for r0, r1 in chunks(H):
        p, lat, _, _ = grid(W, H, (r0, r1))
        g = p @ up
        band = np.exp(-(g / 0.14) ** 2) + np.exp(-(g / 0.38) ** 2) * 0.22
        core = np.exp(-(np.arccos(np.clip(p @ core_dir, -1, 1)) / 0.5) ** 2)
        cloud = fbm(p * 6, 401, 8)
        glow = band * (0.25 + 0.9 * smooth(0.3, 0.75, cloud)) * (0.5 + core * 1.6)
        dust = smooth(0.42, 0.72, fbm(p * 9 + 2, 402, 7)) * np.exp(-(g / 0.06) ** 2)
        glow = glow * (1 - 0.9 * dust)
        neb = smooth(0.66, 0.88, fbm(p * 4 + 4, 403, 6)) * band
        rgb = np.stack([glow * 0.60 + neb * 0.32, glow * 0.54 + neb * 0.07, glow * 0.48 + neb * 0.18], -1)
        rgb += np.stack([core * band * 0.3, core * band * 0.2, core * band * 0.09], -1)
        # unresolved star dust: a fine grain of faint stars
        grain = hash3((p[..., 0] * 3000).astype(np.int64), (p[..., 1] * 3000).astype(np.int64), (p[..., 2] * 3000).astype(np.int64), 404)
        rgb += (grain > 0.9985)[..., None] * 0.5 * (0.4 + band[..., None])
        out[r0:r1] = np.clip(rgb * 0.6, 0, 1)
    save(out, "milkyway.png")


def galaxy():
    """A face-on spiral galaxy (premultiplied, additive): bulge, two logarithmic arms, dust lanes, star clusters."""
    S = 2048
    y, x = (np.mgrid[0:S, 0:S].astype(np.float32) + 0.5) / S * 2 - 1
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    k = 4.2
    arms = 0
    for arm in range(2):
        phase = th - (np.log(np.maximum(r, 1e-3)) * k) + arm * math.pi
        arms = arms + np.exp(-((np.angle(np.exp(1j * phase))) / 0.75) ** 2) * (0.6 + 0.8 * fbm(np.stack([x * 9, y * 9, np.full_like(x, arm * 3.0)], -1), 505 + arm, 5))
    p3 = np.stack([x * 4, y * 4, np.zeros_like(x)], -1)
    n = fbm(p3, 501, 7)
    disk = np.exp(-r / 0.32)
    bulge = np.exp(-(r / 0.09) ** 2) * 1.6 + np.exp(-(r / 0.22) ** 2) * 0.5
    knots = fbm(p3 * 3.2 + 11, 506, 6)
    arm_light = arms * disk * (0.15 + 1.6 * smooth(0.35, 0.85, n) * (0.4 + knots)) * smooth(1.0, 0.7, r)
    dust = smooth(0.55, 0.8, fbm(p3 * 2.2 + 5, 502, 6)) * arms * smooth(0.06, 0.2, r)
    clusters = smooth(0.74, 0.92, fbm(p3 * 7 + 3, 503, 5)) * arms * disk * 3.0
    light = bulge[..., None] * np.array([1.0, 0.86, 0.62])[None, None] + arm_light[..., None] * np.array([0.75, 0.8, 1.0])[None, None] * 1.3
    light = light * (1 - 0.8 * dust)[..., None]
    light += clusters[..., None] * np.array([1.0, 0.45, 0.7])[None, None]
    light += (disk * 0.25)[..., None] * np.array([0.9, 0.85, 0.8])
    grain = hash3((x * 4000).astype(np.int64), (y * 4000).astype(np.int64), np.zeros_like(x, dtype=np.int64), 504)
    light += ((grain > 0.997) * disk * 3)[..., None]
    light *= smooth(1.0, 0.85, r)[..., None]
    light = light / (1 + light * 0.6)
    save(np.clip(light * 0.9, 0, 1), "galaxy.png")


def deepfield():
    """The universe beyond: faint filaments of the cosmic web and thousands of distant galaxies (equirect)."""
    W, H = 2048, 1024
    p, lat, _, _ = grid(W, H)
    web = 1 - np.abs(fbm(p * 2.5, 701, 6) * 2 - 1)
    web = smooth(0.86, 0.99, web) * 0.12 + smooth(0.65, 0.95, web) * 0.03
    rgb = web[..., None] * np.array([0.55, 0.42, 0.7])[None, None]
    rng = np.random.default_rng(702)
    for k in range(5000):
        c = rng.normal(size=3).astype(np.float32)
        c /= np.linalg.norm(c)
        lo = math.atan2(c[0], c[2])
        u = (lo / (2 * math.pi) + 0.25) % 1.0
        v = 0.5 - math.asin(c[1]) / math.pi
        px, py = int(u * W), int(v * H)
        size = 1 + int(rng.random() ** 8 * 6)
        tone = np.array([1.0, 0.85, 0.65]) if rng.random() < 0.6 else np.array([0.7, 0.8, 1.0])
        b = 0.3 + rng.random() * 0.7
        for dy in range(-size, size + 1):
            for dx in range(-size * 2, size * 2 + 1):
                e = (dx / (2 * size)) ** 2 + (dy / size) ** 2
                if e <= 1:
                    rgb[(py + dy) % H, (px + dx) % W] += tone * b * (1 - e) ** 2
    save(np.clip(rgb, 0, 1), "deepfield.png")


def glare():
    S = 512
    y, x = (np.mgrid[0:S, 0:S] + 0.5) / S * 2 - 1
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    core = np.exp(-(r / 0.05) ** 2) * 1.0 + np.exp(-(r / 0.16) ** 2) * 0.55 + np.exp(-(r / 0.45) ** 2) * 0.16
    rng = np.random.default_rng(501)
    rays = np.zeros_like(r)
    for k in range(40):
        a0 = rng.uniform(0, 2 * math.pi)
        w = rng.uniform(0.003, 0.015)
        L = rng.uniform(0.3, 1.0)
        da = np.angle(np.exp(1j * (ang - a0)))
        rays += np.exp(-(da / w) ** 2) * np.clip(1 - r / L, 0, 1) ** 2 * rng.uniform(0.15, 0.5)
    for a0 in (0, math.pi / 2):
        da = np.angle(np.exp(1j * (2 * (ang - a0)))) / 2
        rays += np.exp(-(da / 0.005) ** 2) * np.clip(1 - r, 0, 1) ** 1.4 * 0.9
    ringf = np.exp(-((r - 0.62) / 0.012) ** 2) * 0.06
    v = np.clip(core + rays * 0.7 + ringf, 0, 1) * np.clip((1 - r) * 4, 0, 1)
    save(np.stack([v, v * 0.94, v * 0.83], -1), "glare.png")


def corona():
    """Total-eclipse corona: pearly streamers and a thin red chromosphere around a black disc (premultiplied)."""
    S = 1024
    y, x = (np.mgrid[0:S, 0:S] + 0.5) / S * 2 - 1
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    disc = 0.25
    out_r = np.maximum(r - disc, 0)
    p3 = np.stack([np.cos(ang) * 3, np.sin(ang) * 3, out_r * 2], -1).astype(np.float32)
    streams = fbm(p3, 801, 6)
    stre = smooth(0.4, 0.85, streams) * np.exp(-out_r / 0.22) * 1.2 + np.exp(-out_r / 0.06) * 0.9
    # equatorial elongation, polar plumes
    stre *= 0.6 + 0.6 * np.abs(np.cos(ang)) ** 1.5
    plumes = np.exp(-((np.angle(np.exp(1j * (ang * 9))) / 0.25) ** 2)) * np.exp(-out_r / 0.12) * np.abs(np.sin(ang)) ** 4 * 0.5
    c = (stre + plumes) * (r > disc)
    chrom = np.exp(-((r - disc - 0.006) / 0.006) ** 2) * (1 + 0.6 * fbm(p3 * 4, 802, 3))
    rgb = np.stack([c * 0.95 + chrom * 1.0, c * 0.95 + chrom * 0.2, c * 1.0 + chrom * 0.25], -1)
    rgb *= np.clip((1 - r) * 3, 0, 1)[..., None]
    save(np.clip(rgb, 0, 1), "corona.png")


def sun():
    """The sun's photosphere: boiling granulation, faculae and a few dark spots (limb darkening is done in game)."""
    W, H = 1024, 512
    p, lat, _, _ = grid(W, H)
    gran = fbm(p * 60, 901, 3)
    cells = 1 - np.abs(fbm(p * 22, 902, 4) * 2 - 1)
    hot = 0.75 + 0.18 * gran + 0.12 * cells
    rng = np.random.default_rng(903)
    spots = np.zeros(lat.shape, np.float32)
    for k in range(9):
        la = rng.uniform(-0.5, 0.5)
        lo = rng.uniform(0, 2 * math.pi)
        c = np.array([math.cos(la) * math.sin(lo), math.sin(la), math.cos(la) * math.cos(lo)], np.float32)
        d = np.arccos(np.clip(p @ c, -1, 1))
        r = rng.uniform(0.01, 0.035)
        spots = np.maximum(spots, smooth(r * 2.2, r * 0.6, d) * 0.55 + smooth(r * 0.8, r * 0.3, d) * 0.4)
    v = hot * (1 - spots)
    rgb = np.stack([np.clip(v * 1.05, 0, 1), np.clip(v * 0.82, 0, 1), np.clip(v * 0.5, 0, 1)], -1)
    save(rgb, "sun.png")


def white():
    save(np.ones((4, 4, 3), np.float32), "white.png")


if __name__ == "__main__":
    import sys
    want = sys.argv[1:]
    for name, fn in [("earth", earth), ("moon", moon), ("red", red), ("giant", giant), ("milkyway", milkyway), ("galaxy", galaxy),
                     ("deepfield", deepfield), ("glare", glare), ("corona", corona), ("sun", sun), ("white", white)]:
        if want and name not in want:
            continue
        print(name, flush=True)
        fn()
