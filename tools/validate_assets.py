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


for name in ("relic",):
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
    for p in (A / f"textures/entity/pillar/panel_{pid}.png", A / f"textures/entity/pillar/panel_{pid}_glow.png"):
        if not p.exists():
            errors.append(f"{p.name} missing")
for rel in ("models/entity/pillar2.json", "models/entity/pillar_panel.json", "models/item/staff_mesh.json", "models/entity/engine.json",
            "models/entity/gate.json", "models/space/fragments.json"):
    p = A / rel
    if not p.exists():
        errors.append(f"{rel} missing")
        continue
    m = json.loads(p.read_text())
    for part in m["parts"]:
        nv = len(part["verts"]) // m["stride"]
        if part["quads"] and max(part["quads"]) >= nv:
            errors.append(f"{rel}: part {part['name']} malformed")
    print(rel, ", ".join(f"{q['name']}:{len(q['quads']) // 4}" for q in m["parts"]))
for rel in ("textures/entity/pillar/body.png", "textures/entity/pillar/body_glow.png", "textures/item/staff.png", "textures/item/staff_glow.png",
            "textures/entity/engine.png", "textures/entity/engine_glow.png", "textures/entity/gate.png", "textures/entity/gate_glow.png"):
    if not (A / rel).exists():
        errors.append(f"{rel} missing")
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
for t in ("earth", "earth_clouds", "moon", "red", "red_cracks", "giant", "ring", "milkyway", "galaxy", "deepfield", "glare", "corona", "white"):
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
