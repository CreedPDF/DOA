#!/usr/bin/env python3
"""Fails the build if a generated model or texture is missing or broken."""
import json
import struct
import sys
import zlib
from pathlib import Path

A = Path(__file__).resolve().parent.parent / "src/main/resources/assets/doa"
IDS = ["death", "redemption", "wrath", "soul", "fate", "silence", "void"]
errors = []


def png_size(p):
    d = p.read_bytes()
    if d[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    return struct.unpack(">II", d[16:24])


for name in ("pillar", "relic"):
    p = A / f"models/entity/{name}.json"
    if not p.exists():
        errors.append(f"{p.name} missing")
        continue
    m = json.loads(p.read_text())
    bones = {b["name"] for b in m["bones"]}
    for b in m["bones"]:
        if b["parent"] and b["parent"] not in bones:
            errors.append(f"{p.name}: bone {b['name']} parent {b['parent']} undefined")
    for c in m["cubes"]:
        if c["bone"] not in bones:
            errors.append(f"{p.name}: cube on undefined bone {c['bone']}")
        if set(c["uv"]) != {"north", "south", "east", "west", "up", "down"}:
            errors.append(f"{p.name}: cube without all six faces")
    print(f"{p.name}: {len(m['cubes'])} cubes, {len(bones)} bones, atlas {m['texture']}")

for pid in IDS:
    for suffix in ("", "_glow"):
        p = A / f"textures/entity/pillar/{pid}{suffix}.png"
        if not p.exists():
            errors.append(f"{p.name} missing")
        else:
            print(p.name, png_size(p))
    for p in (A / f"effect/seal_{pid}.png", A / f"gui/label/{pid}.png"):
        q = A / "textures" / p.relative_to(A)
        if not q.exists():
            errors.append(f"{q} missing")

for name in ("planet", "ring"):
    p = A / f"models/space/{name}.json"
    if not p.exists():
        errors.append(f"space mesh {name}.json missing")
        continue
    m = json.loads(p.read_text())
    nv = len(m["verts"]) // 8
    if len(m["verts"]) % 8 or len(m["quads"]) % 4 or max(m["quads"]) >= nv:
        errors.append(f"space mesh {name}.json malformed")
    print(f"{name}.json: {nv} corners, {len(m['quads']) // 4} quads")
for t in ("earth", "earth_clouds", "earth_night", "moon", "giant", "ring", "milkyway", "glare", "white"):
    if not (A / f"textures/space/{t}.png").exists():
        errors.append(f"space texture {t}.png missing")
for pid in IDS:
    for k in range(4):
        if not (A / f"textures/gui/impact/{pid}_{k}.png").exists():
            errors.append(f"impact frame {pid}_{k}.png missing")

if errors:
    for e in errors:
        print(f"::error title=Asset validation::{e}")
    sys.exit(1)
print("assets ok")
