"""
Realistic space for the Fate cutscene, painted procedurally from 3D noise sampled on the sphere (no seams, no poles
pinching). Output under src/main/resources/assets/doa/textures/space/:

  earth.png        living world: oceans with shelves, continents with biomes by latitude/moisture, mountains, ice caps
  earth_clouds.png cloud cover (white, alpha), swirled with domain warping
  earth_night.png  city lights on the land (premultiplied, for the additive layer on the night side)
  moon.png         grey regolith, dark maria and a few hundred craters with rims and ejecta
  giant.png        a banded amber gas giant with turbulent belts and a great storm
  ring.png         its ring system (radial density with gaps; u = radius, v unused)
  milkyway.png     the galactic band: glow, dust lanes and faint nebulae (premultiplied, additive)
  glare.png        the sun's corona and diffraction rays (premultiplied, additive)
  white.png        a white texel for rims and fresnel glows
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/doa/textures/space"


def hash3(ix, iy, iz, seed):
    h = (ix.astype(np.uint32) * np.uint32(374761393) + iy.astype(np.uint32) * np.uint32(668265263)
         + iz.astype(np.uint32) * np.uint32(2147483647) + np.uint32(seed * 1013904223 & 0xFFFFFFFF))
    h = (h ^ (h >> np.uint32(13))) * np.uint32(1274126177)
    h = h ^ (h >> np.uint32(16))
    return (h & np.uint32(0xFFFFFF)).astype(np.float32) / np.float32(0xFFFFFF)


def vnoise(p, seed):
    """3D value noise, p: (..., 3) float."""
    f = np.floor(p)
    t = p - f
    t = t * t * (3 - 2 * t)
    i = f.astype(np.int64)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (t[..., 0] if dx else 1 - t[..., 0]) * (t[..., 1] if dy else 1 - t[..., 1]) * (t[..., 2] if dz else 1 - t[..., 2])
                out = out + w * hash3(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
    return out


def fbm(p, seed, octaves=6, lac=2.0, gain=0.5):
    amp, tot, out = 1.0, 0.0, 0
    for o in range(octaves):
        out = out + vnoise(p * (lac ** o), seed + o * 31) * amp
        tot += amp
        amp *= gain
    return out / tot


def ridged(p, seed, octaves=5):
    amp, tot, out = 1.0, 0.0, 0
    for o in range(octaves):
        n = 1 - np.abs(vnoise(p * (2 ** o), seed + o * 17) * 2 - 1)
        out = out + n * n * amp
        tot += amp
        amp *= 0.5
    return out / tot


def sphere_grid(w, h):
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    lon = u * 2 * math.pi
    lat = (0.5 - v) * math.pi
    lon, lat = np.meshgrid(lon, lat)
    p = np.stack([np.cos(lat) * np.cos(lon), np.sin(lat), np.cos(lat) * np.sin(lon)], -1).astype(np.float32)
    return p, lat


def norm(x):
    lo, hi = np.percentile(x, 1), np.percentile(x, 99)
    return np.clip((x - lo) / (hi - lo + 1e-6), 0, 1)


def mix(a, b, t):
    t = np.asarray(t)[..., None] if np.ndim(t) else t
    return np.asarray(a) * (1 - t) + np.asarray(b) * t


def save(arr, name, mode="RGBA"):
    OUT.mkdir(parents=True, exist_ok=True)
    a = np.clip(arr, 0, 1)
    if a.shape[-1] == 3 and mode == "RGBA":
        a = np.concatenate([a, np.ones(a.shape[:2] + (1,))], -1)
    Image.fromarray((a * 255).astype(np.uint8), mode).save(OUT / name, optimize=True)
    print(" ", name, a.shape[1], "x", a.shape[0])


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------------------------------ earth

def earth():
    W, H = 2048, 1024
    p, lat = sphere_grid(W, H)
    warp = np.stack([fbm(p * 1.5 + 7, 11, 4), fbm(p * 1.5 + 13, 12, 4), fbm(p * 1.5 + 19, 13, 4)], -1) - 0.5
    q = p + warp * 0.6
    cont = norm(fbm(q * 1.6, 21, 7) * 0.75 + ridged(q * 3.0, 22, 5) * 0.25)
    sea = 0.56
    land = cont > sea
    elev = np.clip((cont - sea) / (1 - sea), 0, 1)
    depth = np.clip((sea - cont) / sea, 0, 1)
    mount = ridged(p * 7.0, 31, 6) * elev
    moist = norm(fbm(p * 2.5 + 3, 41, 5))
    alat = np.abs(lat) / (math.pi / 2)
    temp = 1 - alat * 1.1 - elev * 0.5 + (fbm(p * 3, 51, 3) - 0.5) * 0.25

    ocean = mix([0.10, 0.30, 0.36], [0.015, 0.05, 0.13], smooth(0.0, 0.35, depth))
    ocean = mix(ocean, [0.12, 0.38, 0.40], smooth(0.04, 0.0, depth) * 0.6)  # bright shelves
    desert = np.array([0.74, 0.62, 0.42])
    steppe = np.array([0.52, 0.48, 0.30])
    grass = np.array([0.26, 0.38, 0.15])
    forest = np.array([0.09, 0.22, 0.08])
    jungle = np.array([0.06, 0.20, 0.07])
    tundra = np.array([0.42, 0.40, 0.33])
    rock = np.array([0.36, 0.31, 0.27])
    snow = np.array([0.93, 0.95, 0.97])
    wet = moist[..., None]
    hot = smooth(0.35, 0.8, temp)[..., None]
    lowl = mix(mix(desert, steppe, smooth(0.25, 0.45, moist)), mix(grass, forest, smooth(0.5, 0.75, moist)), smooth(0.35, 0.55, moist))
    lowl = lowl * (1 - hot * smooth(0.7, 0.9, moist)[..., None]) + jungle * hot * smooth(0.7, 0.9, moist)[..., None]
    cold = smooth(0.35, 0.15, temp)[..., None]
    landc = lowl * (1 - cold) + tundra * cold
    landc = mix(landc, rock, smooth(0.25, 0.6, mount))
    landc = landc * (0.85 + 0.3 * fbm(p * 24, 61, 3)[..., None])
    col = np.where(land[..., None], landc, ocean)
    ice = smooth(0.12, 0.0, temp + (fbm(p * 6, 71, 4) - 0.5) * 0.12)
    ice = np.maximum(ice, smooth(0.55, 0.8, mount) * land)
    col = mix(col, snow, ice)
    save(col, "earth.png", "RGBA")

    # clouds: swirled bands, thicker at storms
    cw = np.stack([fbm(p * 2 + 5, 81, 4), fbm(p * 2 + 9, 82, 4), fbm(p * 2 + 15, 83, 4)], -1) - 0.5
    cq = p + cw * 0.9
    c = fbm(cq * 4 + np.stack([lat * 0.0, lat * 2.0, lat * 0.0], -1), 91, 7)
    c = norm(c)
    band = 0.55 + 0.25 * np.cos(lat * 6)
    alpha = smooth(0.5, 0.85, c * band + 0.18)
    alpha *= 0.92
    clouds = np.concatenate([np.ones(alpha.shape + (3,)) * 0.98, alpha[..., None]], -1)
    save(clouds, "earth_clouds.png", "RGBA")

    # city lights: clustered along coasts and lowlands
    cities = fbm(p * 40, 101, 3) * fbm(p * 9, 102, 3)
    coast = smooth(0.0, 0.08, elev) * smooth(0.5, 0.05, elev)
    lights = smooth(0.32, 0.5, cities) * land * (0.3 + 0.7 * coast) * (1 - ice) * smooth(0.1, 0.4, temp)
    lights = np.clip(lights * 1.6, 0, 1)
    night = np.stack([lights * 1.0, lights * 0.72, lights * 0.38], -1)
    save(night, "earth_night.png", "RGBA")


# ------------------------------------------------------------------------------------------ moon

def moon():
    W, H = 1024, 512
    p, lat = sphere_grid(W, H)
    base = 0.42 + 0.18 * (fbm(p * 4, 201, 6) - 0.5)
    maria = smooth(0.55, 0.62, fbm(p * 1.4 + 3, 202, 5))
    tone = base * (1 - 0.42 * maria)
    rng = np.random.default_rng(203)
    shade = np.zeros(lat.shape, np.float32)
    for k in range(320):
        c = rng.normal(size=3)
        c /= np.linalg.norm(c)
        r = 0.012 + 0.14 * rng.random() ** 5
        d = np.arccos(np.clip(p @ c.astype(np.float32), -1, 1)) / r
        bowl = np.where(d < 1, -(1 - d * d) * 0.6, 0)
        rim = np.exp(-((d - 1.0) / 0.12) ** 2) * 0.5
        ejecta = np.where(d > 1, np.exp(-(d - 1) * 1.6) * 0.15 * (fbm(p * 30 + k, 204, 2) > 0.5), 0)
        shade += (bowl + rim + ejecta) * (0.5 + 0.5 * min(1, r * 20))
    tone = tone * (1 + shade * 0.5) + fbm(p * 40, 205, 2) * 0.05
    col = np.stack([tone * 1.02, tone, tone * 0.97], -1)
    save(col, "moon.png", "RGBA")


# ------------------------------------------------------------------------------------------ gas giant + ring

def giant():
    W, H = 2048, 1024
    p, lat = sphere_grid(W, H)
    turb = np.stack([fbm(p * 3 + 1, 301, 5), fbm(p * 3 + 2, 302, 5), fbm(p * 3 + 3, 303, 5)], -1) - 0.5
    q = p + turb * 0.12
    y = q[..., 1]
    bands = np.sin(y * 22 + fbm(p * np.array([6, 1, 6], np.float32), 304, 4) * 3) * 0.5 + 0.5
    fine = np.sin(y * 70 + fbm(p * 8, 305, 4) * 5) * 0.5 + 0.5
    palette = [np.array(c) for c in ([0.93, 0.85, 0.68], [0.80, 0.58, 0.36], [0.62, 0.38, 0.22], [0.88, 0.74, 0.52], [0.50, 0.30, 0.20])]
    idx = np.clip(bands * 0.7 + fine * 0.3, 0, 0.999) * (len(palette) - 1)
    i0 = np.floor(idx).astype(int)
    t = (idx - i0)[..., None]
    P = np.stack(palette)
    col = P[i0] * (1 - t) + P[np.minimum(i0 + 1, len(palette) - 1)] * t
    # the great storm
    sc = np.array([math.cos(-0.38) * math.cos(1.2), math.sin(-0.38), math.cos(-0.38) * math.sin(1.2)], np.float32)
    d = np.arccos(np.clip(p @ sc, -1, 1))
    ang = np.arctan2(p[..., 1] - sc[1], (p[..., 0] - sc[0]) + (p[..., 2] - sc[2]))
    storm = np.exp(-(d / 0.13) ** 2)
    swirl = 0.5 + 0.5 * np.sin(ang * 3 + d * 60)
    col = mix(col, np.array([0.72, 0.30, 0.16]) * (0.8 + 0.3 * swirl[..., None]), storm * 0.85)
    col *= 0.9 + 0.15 * fbm(p * 20, 306, 3)[..., None]
    pole = smooth(0.75, 1.0, np.abs(lat) / (math.pi / 2))
    col = mix(col, np.array([0.55, 0.45, 0.38]), pole * 0.6)
    save(col, "giant.png", "RGBA")

    # ring: u runs from the inner to the outer edge
    Wr = 1024
    r = np.linspace(0, 1, Wr)
    rng = np.random.default_rng(307)
    dens = np.zeros(Wr)
    for k in range(160):
        c, w, a = rng.random(), 0.002 + rng.random() * 0.03, rng.random()
        dens += a * np.exp(-((r - c) / w) ** 2)
    dens = dens / dens.max()
    dens *= smooth(0.0, 0.05, r) * smooth(1.0, 0.92, r)
    dens[(r > 0.58) & (r < 0.63)] *= 0.08  # the great division
    dens[(r > 0.84) & (r < 0.855)] *= 0.25
    colr = np.stack([0.86 - 0.2 * r, 0.76 - 0.25 * r, 0.62 - 0.25 * r], -1)
    ring = np.concatenate([colr, (np.clip(dens * 1.15, 0, 1) * 0.92)[:, None]], -1)
    ring = np.repeat(ring[None], 16, 0)
    save(ring, "ring.png", "RGBA")


# ------------------------------------------------------------------------------------------ sky

def milkyway():
    W, H = 2048, 1024
    p, lat = sphere_grid(W, H)
    # tilt the galactic plane
    a = math.radians(62)
    up = np.array([0, math.cos(a), math.sin(a)], np.float32)
    g = p @ up
    band = np.exp(-(g / 0.16) ** 2) + np.exp(-(g / 0.4) ** 2) * 0.25
    core_dir = np.array([0.8, -0.2, 0.0], np.float32)
    core_dir /= np.linalg.norm(core_dir)
    core = np.exp(-(np.arccos(np.clip(p @ core_dir, -1, 1)) / 0.55) ** 2)
    glow = band * (0.35 + 0.65 * norm(fbm(p * 5, 401, 6))) * (0.55 + core * 1.2)
    dust = smooth(0.45, 0.75, fbm(p * 7 + 2, 402, 6)) * np.exp(-(g / 0.07) ** 2)
    glow = glow * (1 - 0.85 * dust)
    neb = smooth(0.62, 0.9, fbm(p * 3 + 4, 403, 5)) * band
    rgb = np.stack([glow * 0.62 + neb * 0.30, glow * 0.56 + neb * 0.08, glow * 0.50 + neb * 0.22], -1)
    rgb += np.stack([core * band * 0.25, core * band * 0.17, core * band * 0.08], -1)
    rgb = np.clip(rgb * 0.55, 0, 1)
    save(rgb, "milkyway.png", "RGBA")


def glare():
    S = 512
    y, x = (np.mgrid[0:S, 0:S] + 0.5) / S * 2 - 1
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    core = np.exp(-(r / 0.06) ** 2) * 1.0 + np.exp(-(r / 0.18) ** 2) * 0.55 + np.exp(-(r / 0.5) ** 2) * 0.18
    rng = np.random.default_rng(501)
    rays = np.zeros_like(r)
    for k in range(24):
        a0 = rng.uniform(0, 2 * math.pi)
        w = rng.uniform(0.004, 0.02)
        L = rng.uniform(0.4, 1.0)
        da = np.angle(np.exp(1j * (ang - a0)))
        rays += np.exp(-(da / w) ** 2) * np.clip(1 - r / L, 0, 1) ** 2 * rng.uniform(0.2, 0.6)
    for a0 in (0, math.pi / 2):  # the long diffraction cross
        da = np.angle(np.exp(1j * (2 * (ang - a0)))) / 2
        rays += np.exp(-(da / 0.006) ** 2) * np.clip(1 - r, 0, 1) ** 1.5 * 0.9
    v = np.clip(core + rays * 0.7, 0, 1) * np.clip((1 - r) * 4, 0, 1)
    rgb = np.stack([v, v * 0.93, v * 0.80], -1)
    save(rgb, "glare.png", "RGBA")


def white():
    save(np.ones((4, 4, 3)), "white.png", "RGBA")


if __name__ == "__main__":
    earth()
    moon()
    giant()
    milkyway()
    glare()
    white()
