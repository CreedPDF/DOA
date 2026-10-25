"""
DOA sound design: ancient Japanese / Chinese temple instruments, synthesized from scratch.

No samples, nothing electronic or sci-fi: every sound is built from physical-ish models of
  * bonsho   - a great temple bell (inharmonic, beating partial pairs, a long hum)
  * taiko    - o-daiko drums (membrane pitch drop, stick click, room boom)
  * hyoshigi - wooden clappers
  * shakuhachi - breathy bamboo flute with meri bends, chiff and vibrato
  * koto     - plucked silk strings (Karplus-Strong) in the hirajoshi scale, with oshide bends
  * furin    - wind chimes
  * chant    - a low monk drone through vowel formants
  * wind / earth - shaped noise
then placed in a stone-hall reverb. Output: src/main/resources/assets/doa/sounds/*.ogg + sounds.json.
"""
import json
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from scipy import signal

SR = 44100
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/doa/sounds"
RNG = np.random.default_rng(1234)


def t_of(d):
    return np.arange(int(d * SR)) / SR


def canvas(d):
    return np.zeros(int(d * SR))


def put(buf, x, at, g=1.0):
    i = int(at * SR)
    n = min(len(x), len(buf) - i)
    if n > 0:
        buf[i:i + n] += x[:n] * g


def lp(x, f, o=2):
    b, a = signal.butter(o, min(f / (SR / 2), 0.99), "low")
    return signal.lfilter(b, a, x)


def hp(x, f, o=2):
    b, a = signal.butter(o, min(f / (SR / 2), 0.99), "high")
    return signal.lfilter(b, a, x)


def bp(x, f0, f1, o=2):
    b, a = signal.butter(o, [f0 / (SR / 2), min(f1 / (SR / 2), 0.99)], "band")
    return signal.lfilter(b, a, x)


def noise(d):
    return RNG.standard_normal(int(d * SR))


def env_ar(d, a, r_tau):
    t = t_of(d)
    return np.clip(t / max(a, 1e-4), 0, 1) * np.exp(-np.maximum(t - a, 0) / r_tau)


def swell(d, power=2.0):
    t = t_of(d)
    return (t / d) ** power


# ------------------------------------------------------------------------------------------ instruments

def bonsho(f0, d, strength=1.0):
    """Temple bell: inharmonic partials in slightly detuned pairs (the slow beating 'wow')."""
    t = t_of(d)
    ratios = [0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166, 5.43]
    decays = [9.0, 7.0, 5.5, 4.0, 3.2, 2.2, 2.0, 1.4, 0.9, 0.5]
    amps = [0.55, 1.0, 0.6, 0.45, 0.35, 0.25, 0.2, 0.15, 0.09, 0.05]
    x = np.zeros_like(t)
    for r, dc, a in zip(ratios, decays, amps):
        f = f0 * r
        beat = 0.35 + 0.08 * RNG.random()
        x += a * (np.sin(2 * np.pi * f * t) + 0.8 * np.sin(2 * np.pi * (f + beat) * t + 1.0)) * np.exp(-t / dc)
    strike = lp(noise(0.25), 900) * env_ar(0.25, 0.002, 0.05)
    out = canvas(d)
    put(out, x * 0.25, 0)
    put(out, strike * 0.6 * strength, 0)
    return out


def taiko(f0=62, d=2.5, hard=1.0):
    t = t_of(d)
    f = f0 * (1 + 0.5 * np.exp(-t / 0.05))
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-t / 0.55) + 0.4 * np.sin(1.59 * ph) * np.exp(-t / 0.25)
    skin = bp(noise(d), 120, 900) * np.exp(-t / 0.12) * 0.5
    stick = hp(noise(0.03), 1800) * env_ar(0.03, 0.0005, 0.008) * 0.5 * hard
    out = canvas(d)
    put(out, (body + skin) * hard, 0)
    put(out, stick, 0)
    return out


def hyoshigi(d=0.6):
    t = t_of(d)
    x = sum(np.sin(2 * np.pi * f * t) * np.exp(-t / tau) for f, tau in ((1180, 0.06), (2360, 0.03), (3420, 0.02)))
    return x * 0.5 + hp(noise(d), 2000) * np.exp(-t / 0.01) * 0.4


def koto(freq, d=2.5, bend=0.0):
    """Karplus-Strong plucked silk string; optional oshide (pressed) bend up in semitones."""
    n = int(d * SR)
    period = SR / freq
    L = int(period)
    buf = RNG.uniform(-1, 1, L)
    buf = lp(buf, freq * 6, 1)
    out = np.zeros(n)
    idx = 0
    damp = 0.996
    for i in range(n):
        j = idx % L
        k = (idx + 1) % L
        v = damp * 0.5 * (buf[j] + buf[k])
        out[i] = buf[j]
        buf[j] = v
        idx += 1
    if bend:
        t = t_of(d)
        ratio = 2 ** (bend / 12 * np.clip((t - 0.25) / 0.3, 0, 1))
        pos = np.cumsum(ratio)
        pos = pos / pos[-1] * (n - 1)
        out = np.interp(np.clip(pos - pos[0], 0, n - 1), np.arange(n), out)
    pluck = hp(noise(0.02), 3000) * env_ar(0.02, 0.0005, 0.004) * 0.3
    put(out, pluck, 0)
    return out * 0.6


def shakuhachi(f_curve, d, breath=0.35):
    """Bamboo flute: a breathy tone following f_curve(t), chiff at the attack, late vibrato."""
    t = t_of(d)
    f = f_curve(t)
    vib = 1 + 0.012 * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - d * 0.35) / (d * 0.3), 0, 1)
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    tone = np.sin(ph) + 0.18 * np.sin(2 * ph + 0.3) + 0.06 * np.sin(3 * ph)
    amp = np.clip(t / 0.18, 0, 1) * np.clip((d - t) / 0.35, 0, 1)
    br = bp(noise(d), 600, 4500) * breath
    chiff = bp(noise(0.12), 1500, 6000) * env_ar(0.12, 0.004, 0.04) * 0.6
    out = (tone * 0.5 + br * (0.5 + 0.5 * np.abs(np.sin(ph * 0.5)))) * amp
    put(out, chiff, 0)
    return out


def furin(d=3.0, n=6, base=2200):
    out = canvas(d)
    for i in range(n):
        f = base * RNG.uniform(0.9, 1.6)
        t = t_of(d - i * 0.12)
        x = (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * f * 2.76 * t)) * np.exp(-t / RNG.uniform(0.6, 1.4))
        put(out, x * 0.18, i * RNG.uniform(0.08, 0.3))
    return out


def chant(d, f0=72):
    """Low monks' drone on an 'o' vowel with a slowly moving overtone."""
    t = t_of(d)
    f = f0 * (1 + 0.004 * np.sin(2 * np.pi * 0.3 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    saw = sum(np.sin(k * ph) / k for k in range(1, 40))
    vowel = bp(saw, 380, 620, 2) * 1.2 + bp(saw, 700, 1000, 2) * 0.6 + bp(saw, 2300, 2700, 2) * 0.12
    over = bp(saw, 900 + 400 * np.sin(2 * np.pi * 0.07 * t).mean(), 1300, 2) * 0.3
    return (vowel + over) * 0.4


def wind(d, f0=300, f1=1800):
    t = t_of(d)
    x = noise(d)
    # sweep a band through the noise in blocks
    out = np.zeros_like(x)
    blocks = 60
    L = len(x) // blocks
    for i in range(blocks):
        fc = f0 + (f1 - f0) * (i / blocks)
        seg = bp(x[i * L:(i + 2) * L], fc * 0.7, fc * 1.4)
        out[i * L:i * L + len(seg[:L])] += seg[:L]
    return out


def earth(d):
    t = t_of(d)
    rum = lp(noise(d), 120, 3) * np.exp(-t / (d * 0.35)) * 2.5
    crack = np.zeros_like(t)
    for _ in range(40):
        at = RNG.uniform(0, d * 0.7)
        put(crack, hp(noise(0.05), 800) * env_ar(0.05, 0.001, 0.01), at, RNG.uniform(0.1, 0.4))
    return rum + crack * np.exp(-t / (d * 0.4))


def hall(x, size=1.0, wet=0.35):
    """Stone temple hall: Schroeder combs + allpasses."""
    out = np.zeros(len(x) + int(SR * 2.5 * size))
    dry = np.zeros_like(out)
    dry[:len(x)] = x
    acc = np.zeros_like(out)
    for dl, g in ((0.0297, 0.84), (0.0371, 0.83), (0.0411, 0.82), (0.0437, 0.81)):
        D = int(dl * SR * size)
        y = np.zeros_like(out)
        for i in range(0, len(out), D):
            seg = dry[i:i + D].copy()
            if i >= D:
                seg += g * y[i - D:i - D + len(seg)]
            y[i:i + len(seg)] = seg
        acc += y
    for dl, g in ((0.005, 0.7), (0.0017, 0.7)):
        D = int(dl * SR)
        b = np.zeros(D + 1); b[0] = -g; b[D] = 1
        a = np.zeros(D + 1); a[0] = 1; a[D] = -g
        acc = signal.lfilter(b, a, acc)
    acc = lp(acc, 5000)
    return dry * (1 - wet) + acc * wet * 0.25


def finish(x, peak=0.92):
    x = np.tanh(x * 1.2) / np.tanh(1.2)
    m = np.max(np.abs(x)) + 1e-9
    x = x / m * peak
    n = int(0.03 * SR)
    x[-n:] *= np.linspace(1, 0, n)
    # trim trailing silence
    thr = 10 ** (-58 / 20)
    nz = np.where(np.abs(x) > thr)[0]
    return x[: (nz[-1] + 1 if len(nz) else len(x))]


# ------------------------------------------------------------------------------------------ hirajoshi
D4 = 293.66
HIRA = [0, 1, 5, 7, 8, 12, 13, 17, 19, 20, 24]   # D, Eb, G, A, Bb ...


def note(st):
    return D4 * 2 ** (st / 12)


# ------------------------------------------------------------------------------------------ sounds

def s_call():
    out = canvas(7.0)
    put(out, bonsho(98, 7.0), 0.0, 1.0)
    c = chant(5.5) * swell(5.5, 1.2)
    put(out, c, 0.6, 0.8)
    put(out, taiko(55, 2.5), 0.0, 0.7)
    return hall(out, 1.3, 0.45)


def hichiriki(f_curve, d):
    """Gagaku double reed: a buzzing, nasal, piercing tone that slides (enbai) between pitches."""
    t = t_of(d)
    f = f_curve(t) * (1 + 0.009 * np.sin(2 * np.pi * 6.1 * t) * np.clip(t / 0.4, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    reed = sum(np.sin(k * ph) * (1.0 / k ** 0.8) for k in range(1, 24))
    reed = np.tanh(reed * 1.6)
    nasal = bp(reed, 900, 1700, 2) * 1.4 + bp(reed, 2400, 3400, 2) * 0.6 + lp(reed, 700) * 0.5
    breath = bp(noise(d), 1500, 6000) * 0.08
    return (nasal + breath) * 0.5


def tamtam(d, f0=62):
    """Great gong: dense inharmonic partials whose highs bloom slowly after the strike."""
    t = t_of(d)
    x = np.zeros_like(t)
    for _ in range(60):
        f = f0 * np.exp(RNG.uniform(0, 4.2))
        bloom = np.clip(t / RNG.uniform(0.05, 0.9), 0, 1) ** 2
        x += np.sin(2 * np.pi * f * t + RNG.uniform(0, 6.28)) * bloom * np.exp(-t / RNG.uniform(1.5, 5)) / (1 + f / 900)
    return x * 0.12


def s_descend():
    # 1.7 s from the armory to the earth: a far star's shimmer, then a dive that swells into the moment of impact
    d = 2.15
    hit = 1.7
    out = canvas(d)
    # the reed's cry, sliding down from the heavens
    cry = hichiriki(lambda t: note(17) * 2 ** (-(np.clip(t / hit, 0, 1) ** 1.6) * 1.0), hit + 0.08)
    cry *= np.clip(t_of(hit + 0.08) / 0.12, 0, 1) * (0.4 + 0.6 * (t_of(hit + 0.08) / hit))
    put(out, cry, 0, 0.75)
    # a struck great gong played backwards: it blooms toward the impact
    g = tamtam(2.2)[::-1]
    put(out, g[-int(hit * SR):], 0, 1.0)
    # the air tearing open, a low roar thickening
    roar = lp(noise(hit), 380, 3) * swell(hit, 2.2) * 3.0 + bp(noise(hit), 700, 3000) * swell(hit, 3.0) * 0.7
    put(out, roar, 0, 0.8)
    # an accelerating taiko roll driving into the strike
    k = 0
    tt = 0.2
    while tt < hit - 0.03:
        put(out, taiko(70 + k * 1.5, 0.6, 0.5 + 0.6 * tt / hit), tt, 0.25 + 0.55 * (tt / hit) ** 2)
        tt += 0.22 * (1 - tt / hit) + 0.035
        k += 1
    # the star's shimmer: high glassy bells trembling before the dive
    for k in range(9):
        put(out, singing_bowl(note(36 + (k % 3) * 5), 1.0) * 0.6, k * 0.12, 0.25)
    # wooden clapper crack right at the moment it plants
    put(out, hyoshigi(0.45), hit - 0.02, 0.9)
    return hall(out, 0.9, 0.3)


def s_impact():
    out = canvas(5.0)
    put(out, taiko(48, 3.0, 1.4), 0.0, 1.4)
    put(out, taiko(60, 2.5, 1.0), 0.14, 0.9)
    put(out, earth(3.5), 0.0, 1.0)
    put(out, bonsho(146, 4.5, 0.4), 0.02, 0.6)
    put(out, hyoshigi(), 0.0, 0.5)
    return hall(out, 1.4, 0.4)


def s_reveal():
    out = canvas(3.4)
    for i, st in enumerate((0, 5, 7, 12, 13)):
        put(out, koto(note(st), 2.6, bend=1 if i == 4 else 0), i * 0.11, 0.8 - i * 0.08)
    return hall(out, 0.9, 0.35)


def s_seal():
    out = canvas(3.5)
    put(out, furin(3.2, 7, 2400), 0, 1.0)
    put(out, bonsho(392, 3.2, 0.2), 0.05, 0.25)
    return hall(out, 0.9, 0.35)


def s_orb_rise():
    d = 7.0
    out = canvas(d)
    put(out, chant(d, 55) * swell(d, 0.8), 0, 1.0)
    bell = bonsho(110, 3.5)[::-1]
    put(out, bell, d - 3.6, 0.7)
    put(out, lp(wind(d, 80, 500), 900) * swell(d, 1.5), 0, 0.8)
    return hall(out, 1.5, 0.5)


def s_orb_close():
    out = canvas(7.5)
    put(out, bonsho(73, 7.5, 1.4), 0.25, 1.4)
    for i in range(6):
        put(out, taiko(52 + i * 2, 1.8, 0.8 + i * 0.06), i * 0.045, 0.5 + i * 0.1)
    put(out, taiko(44, 3.0, 1.5), 0.27, 1.3)
    return hall(out, 1.6, 0.5)


def s_orb_hum():
    d = 6.0
    t = t_of(d)
    out = chant(d, 49) * 0.7 + lp(noise(d), 160) * 0.4
    out *= np.clip(t / 0.8, 0, 1) * np.clip((d - t) / 0.8, 0, 1)
    return hall(out, 1.2, 0.5)


def s_dissolve():
    d = 4.5
    out = canvas(d)
    put(out, bonsho(98, 3.0)[::-1], 0, 0.9)
    put(out, shakuhachi(lambda t: note(12) * 2 ** (-(t / d) * 1.2), d, 0.45), 0.2, 0.6)
    return hall(out, 1.2, 0.45)


def s_ascend():
    d = 3.2
    out = canvas(d)
    put(out, wind(d, 1800, 300)[::-1], 0, 0.8)
    put(out, shakuhachi(lambda t: note(0) * 2 ** ((t / d) * 1.0), d, 0.5), 0, 0.5)
    put(out, furin(2.5, 4, 2600), 0.6, 0.6)
    return hall(out, 1.0, 0.35)


def s_relic():
    out = canvas(2.6)
    put(out, furin(2.4, 5, 3000), 0, 0.8)
    put(out, koto(note(24), 2.0), 0.05, 0.5)
    put(out, koto(note(31), 2.0), 0.12, 0.35)
    return hall(out, 0.8, 0.3)


# ------------------------------------------------------------------------------------------ ability sounds

def slash(d=0.5):
    """A blade of ink cutting the air: a fast band sweep through noise."""
    t = t_of(d)
    x = noise(d)
    out = np.zeros_like(x)
    L = len(x) // 20
    for i in range(20):
        fc = 6000 * (1 - i / 20) + 600
        seg = bp(x[i * L:(i + 2) * L], fc * 0.6, min(fc * 1.6, 20000))
        out[i * L:i * L + len(seg[:L])] += seg[:L]
    return out * np.exp(-t / 0.12) * np.clip(t / 0.01, 0, 1)


def singing_bowl(f0, d):
    t = t_of(d)
    x = sum(a * np.sin(2 * np.pi * f0 * r * t + 0.3 * np.sin(2 * np.pi * 0.8 * t)) * np.exp(-t / dc)
            for r, a, dc in ((1, 1, 6), (2.71, 0.5, 4), (5.13, 0.25, 2.5), (8.0, 0.1, 1.5)))
    return x * np.clip(t / 0.02, 0, 1) * 0.3


def s_verdict():
    out = canvas(6.0)
    put(out, bonsho(65, 5.5, 1.2), 0, 1.0)
    put(out, chant(4.5, 58) * swell(4.5, 0.7), 0.3, 0.7)
    for k in range(5):
        put(out, slash(0.5), 2.45 + k * 0.05, 0.6)
    put(out, taiko(44, 3.0, 1.5), 2.5, 1.4)
    put(out, earth(2.5), 2.5, 0.6)
    return hall(out, 1.4, 0.45)


def s_absolution():
    out = canvas(6.0)
    for k, st in enumerate((24, 29, 31, 36, 37, 41, 43, 48)):
        put(out, singing_bowl(note(st), 4.0), k * 0.16, 0.5)
    for k, st in enumerate((0, 7, 12)):
        put(out, chant(4.5, note(st) / 4) * swell(4.5, 0.6), 0.2, 0.35)
    put(out, furin(4.0, 10, 2600), 1.4, 0.8)
    put(out, bonsho(196, 4.0, 0.3), 1.45, 0.6)
    return hall(out, 1.5, 0.55)


def s_war_drums():
    d = 7.5
    out = canvas(d)
    beat = 0.32
    tt, k = 0.0, 0
    while tt < d - 1.5:
        accent = 1.0 if k % 4 == 0 else 0.55
        put(out, taiko(50 if k % 4 == 0 else 66, 1.6, accent), tt, 0.4 + 0.5 * accent)
        if k % 2 == 1:
            put(out, hyoshigi(0.3), tt + beat / 2, 0.25)
        tt += beat * (1 - 0.25 * tt / d)
        k += 1
    roar = lp(noise(d), 500, 2) * swell(d, 0.7) * 1.8
    put(out, roar, 0, 0.6)
    put(out, hichiriki(lambda t: note(12) * (1 + 0.02 * np.sin(t * 3)), 4.0) * np.clip(t_of(4.0) / 0.3, 0, 1), 0.8, 0.45)
    return hall(out, 1.3, 0.4)


def s_spear():
    out = canvas(2.2)
    put(out, wind(0.6, 2500, 600) * swell(0.6, 2), 0, 0.5)
    put(out, taiko(58, 1.6, 1.1), 0.6, 1.0)
    put(out, earth(1.4), 0.6, 0.6)
    put(out, lp(noise(1.2), 2500) * env_ar(1.2, 0.01, 0.25), 0.6, 0.5)
    return hall(out, 0.9, 0.3)


def s_soul():
    d = 6.5
    out = canvas(d)
    put(out, shakuhachi(lambda t: note(7) * 2 ** (np.sin(t * 0.9) * 0.15 + (t / d) * 0.4), d - 0.5, 0.7), 0.2, 0.7)
    put(out, furin(3.0, 6, 1900)[::-1], 0.5, 0.5)
    put(out, chant(d, 52) * swell(d, 1.0), 0, 0.5)
    put(out, bonsho(130, 2.5, 0.5), 4.3, 0.7)
    return hall(out, 1.6, 0.6)


def s_fate_ascend():
    d = 2.2
    out = canvas(d)
    put(out, wind(d, 300, 3600) * swell(d, 1.6), 0, 1.0)
    for k, st in enumerate((0, 5, 7, 12, 17, 19, 24)):
        put(out, koto(note(st), 1.6), k * 0.2, 0.5)
    put(out, chant(d, 65) * swell(d, 1.0), 0, 0.4)
    return hall(out, 1.2, 0.5)


def s_fate_space():
    d = 8.0
    t = t_of(d)
    out = canvas(d)
    drone = sum(np.sin(2 * np.pi * f * t + np.sin(2 * np.pi * 0.05 * t * (k + 1))) * a for k, (f, a) in enumerate(((36.7, 0.5), (55, 0.35), (73.4, 0.25))))
    out += drone * np.clip(t / 1.5, 0, 1) * np.clip((d - t) / 1.0, 0, 1) * 0.6
    for k, st in enumerate((12, 19, 24, 17, 29, 31, 36)):
        put(out, singing_bowl(note(st) / 2, 5.0), 0.6 + k * 0.9, 0.35)
    put(out, lp(noise(d), 200) * 0.25 * np.clip(t / 2, 0, 1), 0, 1)
    return hall(out, 2.0, 0.65)


def s_fate_strike():
    out = canvas(7.0)
    g = tamtam(3.0)[::-1]
    put(out, g[-int(0.8 * SR):], 0, 1.0)
    put(out, bonsho(55, 6.0, 1.6), 0.8, 1.4)
    put(out, taiko(40, 3.5, 1.6), 0.8, 1.5)
    put(out, earth(4.0), 0.8, 1.2)
    put(out, tamtam(5.0), 0.8, 0.7)
    return hall(out, 1.8, 0.5)


def s_silence():
    d = 9.0
    out = canvas(d)
    put(out, bonsho(82, 9.0, 1.0), 0, 1.3)
    put(out, singing_bowl(note(24), 8.0), 0.05, 0.4)
    return hall(out, 2.2, 0.6)


def s_void_open():
    d = 6.5
    t = t_of(d)
    out = canvas(d)
    put(out, tamtam(4.0)[::-1], 0, 0.8)
    sub = np.sin(2 * np.pi * (30 + 8 * t / d) * t) * swell(d, 1.2) * 0.8
    out += sub
    put(out, chant(d, 41) * swell(d, 0.8), 0, 0.6)
    put(out, lp(wind(d, 100, 900), 1200) * swell(d, 1.0), 0, 0.7)
    return hall(out, 1.6, 0.5)


def s_void_collapse():
    out = canvas(6.0)
    rev = (bonsho(70, 1.4)[::-1])
    put(out, rev, 0, 0.9)
    put(out, taiko(38, 3.5, 1.8), 1.4, 1.6)
    put(out, earth(3.5), 1.4, 1.2)
    put(out, bonsho(49, 4.5, 1.0), 1.4, 0.9)
    return hall(out, 1.7, 0.5)



SOUNDS = {
    "call": (s_call, 96), "descend": (s_descend, 160), "impact": (s_impact, 160), "reveal": (s_reveal, 48),
    "seal": (s_seal, 48), "orb_rise": (s_orb_rise, 96), "orb_close": (s_orb_close, 128), "orb_hum": (s_orb_hum, 32),
    "dissolve": (s_dissolve, 96), "ascend": (s_ascend, 128), "relic": (s_relic, 32),
    "verdict": (s_verdict, 160), "absolution": (s_absolution, 64), "war_drums": (s_war_drums, 160), "spear": (s_spear, 96),
    "soul": (s_soul, 96), "fate_ascend": (s_fate_ascend, 48), "fate_space": (s_fate_space, 32), "fate_strike": (s_fate_strike, 192),
    "silence": (s_silence, 192), "void_open": (s_void_open, 128), "void_collapse": (s_void_collapse, 160),
}
SUBTITLES = {
    "call": "A temple bell tolls from the heavens", "descend": "A pillar falls from the sky", "impact": "A pillar strikes the earth",
    "reveal": "A name is spoken", "seal": "A seal is drawn", "orb_rise": "Darkness rises", "orb_close": "The dark sphere seals",
    "orb_hum": "The dark sphere hums", "dissolve": "The dark sphere fades", "ascend": "The pillars return to the heavens",
    "relic": "A relic awakens",
    "verdict": "Death passes judgement", "absolution": "Heaven's light descends", "war_drums": "War drums of heaven",
    "spear": "A spear of fire strikes", "soul": "Souls are drawn away", "fate_ascend": "You rise to the heavens",
    "fate_space": "The stars sing", "fate_strike": "The stars strike", "silence": "A great bell tolls",
    "void_open": "The void opens", "void_collapse": "The void collapses",
}


def write_ogg(x, path):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "6", str(path)], check=True)
    Path(tmp.name).unlink()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    js = {}
    for name, (fn, dist) in SOUNDS.items():
        x = finish(fn())
        write_ogg(x, OUT / f"{name}.ogg")
        js[name] = {"subtitle": f"subtitles.doa.{name}", "sounds": [{"name": f"doa:{name}", "attenuation_distance": dist}]}
        print(f"{name:10s} {len(x) / SR:5.2f}s")
    (OUT.parent / "sounds.json").write_text(json.dumps(js, indent=2))


if __name__ == "__main__":
    main()
