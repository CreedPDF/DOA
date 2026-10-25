"""
DOA mesh models, built and textured in headless Blender (run by tools/blender/build.sh).

Every model is modelled from primitives and modifiers, unwrapped, and its textures are BAKED in Cycles from
procedural materials (an albedo atlas and a glow mask), then exported as "doa_mesh_2": named parts (so the game can
animate them) with per-corner position / uv / normal / flag, in Minecraft axes (y up).

  pillar        the new pillar: stepped octagonal plinth, tapered obelisk with inlaid glowing channels,
                gold bands and pyramidion; floating halo rings with orbiting shards; a crystal above
  pillar_panel  the four inscription panels (uv 0..1, textured per pillar)
  staff         the Staff of Summoning: lacquered shaft with gold spiral, lotus head, halo ring, seven beads,
                floating gem and two paper talismans
  engine        the Heart Engine at the core of the world: glowing core, three armillary rings, cavern frame
  gate          the halo gate of Dawn: a colossal ring of light with turning inner wheel
  fragments     a shattered planet: 28 crust plates with a glowing inner face (flag = 1)
Also renders previews into --out and the space panorama used by the Rift.
"""
import argparse
import json
import math
import os
import random
import sys

import bpy
import bmesh
from mathutils import Vector, Matrix

ARGS = None


# ------------------------------------------------------------------------------------------------ scene utils

def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = 8
    sc.cycles.use_denoising = False
    sc.view_settings.view_transform = "Standard"
    sc.render.bake.margin = 6


def active(obj):
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj


def last():
    return bpy.context.active_object


def apply_mods(obj):
    active(obj)
    for m in list(obj.modifiers):
        bpy.ops.object.modifier_apply(modifier=m.name)


def tag(obj, part, mat, smooth=False, flag=0):
    """Remember which animated part and material a primitive belongs to."""
    obj["doa_part"] = part
    obj["doa_flag"] = flag
    if mat is not None:
        obj.data.materials.clear()
        obj.data.materials.append(mat)
    active(obj)
    if smooth:
        bpy.ops.object.shade_smooth()
    else:
        bpy.ops.object.shade_flat()
    return obj


def stamp_attributes(objs, parts):
    """Write the part index and flag as face attributes so they survive joining."""
    for o in objs:
        me = o.data
        me.attributes.new("doa_part", "INT", "FACE")
        me.attributes.new("doa_flag", "INT", "FACE")
        pa, fl = me.attributes["doa_part"], me.attributes["doa_flag"]
        pi = parts.index(o["doa_part"])
        for k in range(len(me.polygons)):
            pa.data[k].value = pi
            fl.data[k].value = int(o["doa_flag"])


def join(objs, name):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    o = bpy.context.active_object
    o.name = name
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return o


def unwrap(obj, margin=0.004):
    active(obj)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(60), island_margin=margin)
    bpy.ops.object.mode_set(mode="OBJECT")


# ------------------------------------------------------------------------------------------------ materials

class Mat:
    """A bake material: an albedo colour branch and a glow-mask branch, each feeding its own emission node."""

    def __init__(self, name):
        self.m = bpy.data.materials.new(name)
        self.m.use_nodes = True
        self.nt = self.m.node_tree
        self.nt.nodes.clear()
        self.out = self.nt.nodes.new("ShaderNodeOutputMaterial")
        self.e_col = self.nt.nodes.new("ShaderNodeEmission")
        self.e_glow = self.nt.nodes.new("ShaderNodeEmission")
        self.coord = self.nt.nodes.new("ShaderNodeTexCoord")
        self.geo = self.nt.nodes.new("ShaderNodeNewGeometry")
        self.e_glow.inputs["Color"].default_value = (0, 0, 0, 1)

    def n(self, kind, **inputs):
        node = self.nt.nodes.new(kind)
        for k, v in inputs.items():
            if k.startswith("_"):
                setattr(node, k[1:], v)
            else:
                node.inputs[k].default_value = v
        return node

    def link(self, a, b):
        self.nt.links.new(a, b)

    def color(self, sock):
        self.link(sock, self.e_col.inputs["Color"])

    def glow(self, sock):
        self.link(sock, self.e_glow.inputs["Color"])

    def mode(self, which):
        for l in list(self.out.inputs["Surface"].links):
            self.nt.links.remove(l)
        self.link((self.e_col if which == "color" else self.e_glow).outputs[0], self.out.inputs["Surface"])

    def obj_coord(self, scale=1.0):
        if scale == 1.0:
            return self.coord.outputs["Object"]
        m = self.n("ShaderNodeVectorMath", _operation="SCALE")
        self.link(self.coord.outputs["Object"], m.inputs[0])
        m.inputs["Scale"].default_value = scale
        return m.outputs[0]

    def noise(self, scale, detail=6, rough=0.6, vec=None, dist=0.0):
        nz = self.n("ShaderNodeTexNoise", Scale=scale, Detail=detail, Roughness=rough, Distortion=dist)
        self.link(vec or self.coord.outputs["Object"], nz.inputs["Vector"])
        return nz

    def ramp(self, fac, stops):
        r = self.n("ShaderNodeValToRGB")
        cr = r.color_ramp
        while len(cr.elements) > 2:
            cr.elements.remove(cr.elements[-1])
        cr.elements[0].position, cr.elements[0].color = stops[0][0], (*stops[0][1], 1)
        cr.elements[1].position, cr.elements[1].color = stops[1][0], (*stops[1][1], 1)
        for p, c in stops[2:]:
            e = cr.elements.new(p)
            e.color = (*c, 1)
        self.link(fac, r.inputs["Fac"])
        return r.outputs["Color"]

    def mix(self, a, b, fac, blend="MIX"):
        m = self.n("ShaderNodeMix", _data_type="RGBA", _blend_type=blend)
        if isinstance(fac, (int, float)):
            m.inputs[0].default_value = fac
        else:
            self.link(fac, m.inputs[0])
        for idx, v in ((6, a), (7, b)):
            if isinstance(v, tuple):
                m.inputs[idx].default_value = (*v, 1)
            else:
                self.link(v, m.inputs[idx])
        return m.outputs[2]

    def math(self, op, a, b=None, clamp=False):
        m = self.n("ShaderNodeMath", _operation=op, _use_clamp=clamp)
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                m.inputs[i].default_value = v
            else:
                self.link(v, m.inputs[i])
        return m.outputs[0]

    def ao(self, distance=0.4):
        a = self.n("ShaderNodeAmbientOcclusion", Distance=distance)
        a.samples = 16
        return a.outputs["AO"]


def stone_mat(name, base=(0.035, 0.033, 0.038), vein=(0.11, 0.1, 0.12), scale=2.2):
    m = Mat(name)
    n1 = m.noise(scale, 8, 0.62)
    n2 = m.noise(scale * 6, 4, 0.5)
    vor = m.n("ShaderNodeTexVoronoi", Scale=scale * 1.4, _feature="DISTANCE_TO_EDGE")
    m.link(m.coord.outputs["Object"], vor.inputs["Vector"])
    cracks = m.math("LESS_THAN", vor.outputs["Distance"], 0.012)
    col = m.ramp(n1.outputs["Fac"], [(0.3, base), (0.55, tuple(c * 1.6 for c in base)), (0.78, vein)])
    col = m.mix(col, m.ramp(n2.outputs["Fac"], [(0.4, (0.0, 0.0, 0.0)), (0.7, (0.07, 0.07, 0.075))]), 0.35, "ADD")
    col = m.mix(col, (0.006, 0.005, 0.006), cracks)
    # worn edges catch the light; crevices hold dust
    edge = m.math("POWER", m.math("SUBTRACT", 1.0, m.ao(0.15)), 2.0)
    col = m.mix(col, (0.2, 0.19, 0.2), m.math("MULTIPLY", edge, 0.25, True))
    col = m.mix(col, (0.0, 0.0, 0.0), m.math("MULTIPLY", m.math("SUBTRACT", 1.0, m.ao(0.6)), 0.6, True))
    m.color(col)
    return m


def gold_mat(name, tint=(0.72, 0.52, 0.2)):
    m = Mat(name)
    n1 = m.noise(9, 6, 0.55)
    col = m.ramp(n1.outputs["Fac"], [(0.3, tuple(c * 0.55 for c in tint)), (0.6, tint), (0.85, tuple(min(1, c * 1.35) for c in tint))])
    pat = m.noise(40, 2, 0.4)
    col = m.mix(col, (0.12, 0.2, 0.15), m.math("MULTIPLY", m.math("GREATER_THAN", pat.outputs["Fac"], 0.68), 0.35))
    col = m.mix(col, (0.0, 0.0, 0.0), m.math("MULTIPLY", m.math("SUBTRACT", 1.0, m.ao(0.3)), 0.7, True))
    m.color(col)
    return m


def lacquer_mat(name):
    m = Mat(name)
    n1 = m.noise(30, 4, 0.5)
    col = m.ramp(n1.outputs["Fac"], [(0.4, (0.03, 0.012, 0.012)), (0.7, (0.09, 0.025, 0.02))])
    # a gold spiral inlay running up the shaft (object z with angle)
    ang = m.n("ShaderNodeSeparateXYZ")
    m.link(m.coord.outputs["Object"], ang.inputs[0])
    at = m.math("ARCTAN2", ang.outputs["Y"], ang.outputs["X"])
    spiral = m.math("FRACT", m.math("ADD", m.math("MULTIPLY", ang.outputs["Z"], 3.5), m.math("DIVIDE", at, 6.2832)))
    line = m.math("LESS_THAN", m.math("ABSOLUTE", m.math("SUBTRACT", spiral, 0.5)), 0.035)
    col = m.mix(col, (0.78, 0.56, 0.22), line)
    m.color(col)
    m.glow(m.ramp(line, [(0.0, (0, 0, 0)), (1.0, (0.35, 0.35, 0.35))]))
    return m


def glow_mat(name, color=(0.95, 0.95, 0.95), glow=1.0, glow_color=None):
    m = Mat(name)
    n1 = m.noise(6, 4, 0.5)
    m.color(m.ramp(n1.outputs["Fac"], [(0.2, tuple(c * 0.75 for c in color)), (0.8, color)]))
    gc = glow_color or (1.0, 1.0, 1.0)
    m.glow(m.ramp(n1.outputs["Fac"], [(0.2, tuple(c * glow * 0.7 for c in gc)), (0.8, tuple(c * glow for c in gc))]))
    return m


def paper_mat(name):
    m = Mat(name)
    n1 = m.noise(60, 3, 0.5)
    col = m.ramp(n1.outputs["Fac"], [(0.3, (0.78, 0.72, 0.58)), (0.7, (0.92, 0.88, 0.76))])
    stripe = m.n("ShaderNodeSeparateXYZ")
    m.link(m.coord.outputs["Object"], stripe.inputs[0])
    ink = m.math("LESS_THAN", m.math("ABSOLUTE", m.math("SINE", m.math("MULTIPLY", stripe.outputs["Z"], 90.0))), 0.25)
    col = m.mix(col, (0.06, 0.04, 0.04), m.math("MULTIPLY", ink, 0.85))
    m.color(col)
    return m


def channel_glow(m, coord_axis_pairs):
    """Inlaid glowing channels: thin lines at fixed object-space coordinates."""
    sep = m.n("ShaderNodeSeparateXYZ")
    m.link(m.coord.outputs["Object"], sep.inputs[0])
    acc = None
    for axis, period, width in coord_axis_pairs:
        v = sep.outputs[axis]
        f = m.math("FRACT", m.math("MULTIPLY", v, 1.0 / period))
        line = m.math("LESS_THAN", m.math("ABSOLUTE", m.math("SUBTRACT", f, 0.5)), width / period)
        acc = line if acc is None else m.math("MAXIMUM", acc, line)
    return acc


# ------------------------------------------------------------------------------------------------ bake + export

def bake(obj, mats, size, path_color, path_glow=None):
    imgs = {}
    for which, path in (("color", path_color), ("glow", path_glow)):
        if path is None:
            continue
        img = bpy.data.images.new(os.path.basename(path), size, size, alpha=False)
        img.colorspace_settings.name = "Non-Color"
        for mt in mats:
            mt.mode(which)
            tex = mt.nt.nodes.get("doa_bake") or mt.nt.nodes.new("ShaderNodeTexImage")
            tex.name = "doa_bake"
            tex.image = img
            mt.nt.nodes.active = tex
        active(obj)
        bpy.ops.object.bake(type="EMIT", margin=6)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.filepath_raw = path
        img.file_format = "PNG"
        img.save()
        imgs[which] = img
        print(f"[bake] {os.path.basename(path)} {size}px")
    for mt in mats:
        mt.mode("color")
    return imgs


def export(obj, parts, pivots, path, flip_v=True):
    """doa_mesh_2: parts with per-corner [x,y,z,u,v,nx,ny,nz,flag] in Minecraft axes ((x,y,z) -> (x,z,-y))."""
    me = obj.data
    mw = obj.matrix_world
    nm = mw.to_3x3()
    uv = me.uv_layers.active.data
    pa = me.attributes["doa_part"].data
    fl = me.attributes["doa_flag"].data
    out = {p: {"verts": [], "quads": []} for p in parts}
    for poly in me.polygons:
        p = parts[pa[poly.index].value]
        f = fl[poly.index].value
        dst = out[p]
        idx = []
        for li in poly.loop_indices:
            vi = me.loops[li].vertex_index
            co = mw @ me.vertices[vi].co
            n = (nm @ (me.vertices[vi].normal if poly.use_smooth else poly.normal)).normalized()
            u, v = uv[li].uv
            dst["verts"] += [round(co.x, 4), round(co.z, 4), round(-co.y, 4), round(u, 5), round(1 - v if flip_v else v, 5),
                             round(n.x, 3), round(n.z, 3), round(-n.y, 3), f]
            idx.append(len(dst["verts"]) // 9 - 1)
        if len(idx) == 3:
            idx.append(idx[2])
        if len(idx) == 4:
            dst["quads"] += idx
        elif len(idx) > 4:  # fan n-gons into quads
            for k in range(1, len(idx) - 1, 2):
                dst["quads"] += [idx[0], idx[k], idx[k + 1], idx[min(k + 2, len(idx) - 1)]]
    js = {"format": "doa_mesh_2", "stride": 9, "parts": []}
    for p in parts:
        pv = pivots.get(p, (0, 0, 0))
        js["parts"].append({"name": p, "pivot": [pv[0], pv[2], -pv[1]], **out[p]})
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(js, fh, separators=(",", ":"))
    print(f"[mesh] {os.path.basename(path)}: " + ", ".join(f"{p}={len(out[p]['quads']) // 4}q" for p in parts))


def preview_material(name, color_img, glow_img, strength=4.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = color_img
    nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = 0.55
    if glow_img is not None:
        g = nt.nodes.new("ShaderNodeTexImage")
        g.image = glow_img
        nt.links.new(g.outputs["Color"], b.inputs["Emission Color"])
        b.inputs["Emission Strength"].default_value = strength
    return m


def preview(obj, mat, path, cam_loc, target, lens=50, res=900, light=3.0):
    sc = bpy.context.scene
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    w = bpy.data.worlds.new("w")
    w.use_nodes = True
    w.node_tree.nodes["Background"].inputs[0].default_value = (0.01, 0.009, 0.012, 1)
    sc.world = w
    if "key" not in bpy.data.objects:
        l = bpy.data.lights.new("key", "SUN")
        l.energy = light
        lo = bpy.data.objects.new("key", l)
        lo.rotation_euler = (math.radians(50), math.radians(10), math.radians(40))
        sc.collection.objects.link(lo)
    cam = bpy.data.cameras.new("cam")
    co = bpy.data.objects.new("cam", cam)
    sc.collection.objects.link(co)
    sc.camera = co
    co.location = Vector(cam_loc)
    co.rotation_euler = (Vector(target) - co.location).to_track_quat("-Z", "Y").to_euler()
    cam.lens = lens
    sc.cycles.samples = 24
    sc.render.resolution_x = res
    sc.render.resolution_y = res
    sc.render.filepath = path
    if not ARGS.no_render:
        bpy.ops.render.render(write_still=True)
    sc.cycles.samples = 8


# ------------------------------------------------------------------------------------------------ pillar

def obelisk(z0, z1, w0, w1, bevel=0.05):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        top = v.co.z > 0
        s = w1 if top else w0
        v.co.x *= 2 * s
        v.co.y *= 2 * s
        v.co.z = z1 if top else z0
    me = bpy.data.meshes.new("obelisk")
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new("obelisk", me)
    bpy.context.scene.collection.objects.link(o)
    if bevel:
        b = o.modifiers.new("b", "BEVEL")
        b.width = bevel
        b.segments = 2
        apply_mods(o)
    return o


def width_at(z, z0=0.9, z1=11.0, w0=0.85, w1=0.6):
    return w0 + (w1 - w0) * (z - z0) / (z1 - z0)


def build_pillar(assets, out):
    reset()
    stone = stone_mat("pillar_stone")
    # glowing channels inlaid along the shaft
    ch = channel_glow(stone, [("Z", 2.3, 0.03)])
    stone.glow(stone.ramp(ch, [(0.0, (0, 0, 0)), (1.0, (1, 1, 1))]))
    gold = gold_mat("pillar_gold")
    gem = glow_mat("pillar_gem", (0.95, 0.95, 0.95), 1.0)
    rim = gold_mat("pillar_ring_gold", (0.8, 0.6, 0.25))
    rim.glow(rim.ramp(channel_glow(rim, [("Z", 0.12, 0.012)]), [(0.0, (0, 0, 0)), (1.0, (0.9, 0.9, 0.9))]))
    objs = []
    # plinth: three stepped octagons, the lowest half-buried
    for r, z0, z1 in ((2.05, -0.7, 0.05), (1.75, 0.05, 0.5), (1.42, 0.5, 0.9)):
        bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=r, depth=z1 - z0, location=(0, 0, (z0 + z1) / 2), rotation=(0, 0, math.pi / 8))
        o = last()
        b = o.modifiers.new("b", "BEVEL")
        b.width = 0.04
        b.segments = 2
        apply_mods(o)
        objs.append(tag(o, "body", stone.m))
    # tapered obelisk shaft
    objs.append(tag(obelisk(0.9, 11.0, 0.85, 0.6), "body", stone.m))
    # gold bands
    for z in (1.25, 5.9, 10.5):
        w = width_at(z) + 0.045
        o = obelisk(z - 0.11, z + 0.11, w, w - 0.004, 0.02)
        objs.append(tag(o, "body", gold.m))
    # pyramidion
    bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.6 * math.sqrt(2) + 0.02, radius2=0.0, depth=1.5, location=(0, 0, 11.75), rotation=(0, 0, math.pi / 4))
    objs.append(tag(last(), "body", gold.m))
    # halo rings with orbiting shards (animated)
    for R, tilt in ((1.75, 0.0), (2.25, 0.22)):
        bpy.ops.mesh.primitive_torus_add(major_radius=R, minor_radius=0.055, major_segments=96, minor_segments=8, location=(0, 0, 8.3), rotation=(tilt, 0, 0))
        objs.append(tag(last(), "rings", rim.m, smooth=True))
    for k in range(8):
        a = k / 8 * math.tau
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.13, radius2=0.0, depth=0.42, location=(math.cos(a) * 2.0, math.sin(a) * 2.0, 8.3 + 0.12),
                                        rotation=(0, 0, a))
        o = last()
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.13, radius2=0.0, depth=0.24, location=(math.cos(a) * 2.0, math.sin(a) * 2.0, 8.3 - 0.21),
                                        rotation=(math.pi, 0, a))
        objs += [tag(o, "rings", gem.m), tag(last(), "rings", gem.m)]
    # the crystal that hovers above
    bpy.ops.mesh.primitive_cone_add(vertices=6, radius1=0.38, radius2=0.0, depth=0.9, location=(0, 0, 13.7))
    c1 = tag(last(), "crystal", gem.m)
    bpy.ops.mesh.primitive_cone_add(vertices=6, radius1=0.38, radius2=0.0, depth=0.55, location=(0, 0, 12.98), rotation=(math.pi, 0, 0))
    objs += [c1, tag(last(), "crystal", gem.m)]

    parts = ["body", "rings", "crystal"]
    stamp_attributes(objs, parts)
    o = join(objs, "pillar")
    unwrap(o, 0.003)
    tex = os.path.join(assets, "textures/entity/pillar")
    imgs = bake(o, [stone, gold, gem, rim], 2048, os.path.join(tex, "body.png"), os.path.join(tex, "body_glow.png"))
    export(o, parts, {"rings": (0, 0, 8.3), "crystal": (0, 0, 13.3)}, os.path.join(assets, "models/entity/pillar2.json"))
    preview(o, preview_material("pv", imgs["color"], imgs["glow"]), os.path.join(out, "pillar2.png"), (6, -15, 7), (0, 0, 6.5), 40)

    # inscription panels: one quad per face, uv 0..1, set into the shaft between the lower bands
    reset()
    pobj = []
    z0, z1 = 1.75, 5.45
    for k in range(4):
        a = k * math.pi / 2
        w0, w1 = width_at(z0) + 0.012, width_at(z1) + 0.012
        h0, h1 = width_at(z0) * 0.78, width_at(z1) * 0.78
        bm = bmesh.new()
        verts = [bm.verts.new(v) for v in ((-h0, -w0, z0), (h0, -w0, z0), (h1, -w1, z1), (-h1, -w1, z1))]
        f = bm.faces.new(verts)
        uvl = bm.loops.layers.uv.new("UVMap")
        for loop, uvv in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
            loop[uvl].uv = uvv
        bm.normal_update()
        me = bpy.data.meshes.new("panel")
        bm.to_mesh(me)
        bm.free()
        po = bpy.data.objects.new("panel", me)
        bpy.context.scene.collection.objects.link(po)
        po.rotation_euler = (0, 0, a)
        pobj.append(tag(po, "panel", None))
    stamp_attributes(pobj, ["panel"])
    p = join(pobj, "panels")
    export(p, ["panel"], {}, os.path.join(assets, "models/entity/pillar_panel.json"))


# ------------------------------------------------------------------------------------------------ staff

STAFF_HEAD = 1.98


def build_staff(assets, out):
    reset()
    lac = lacquer_mat("staff_lacquer")
    gold = gold_mat("staff_gold", (0.8, 0.6, 0.24))
    gold.glow(gold.ramp(channel_glow(gold, [("Z", 0.05, 0.004)]), [(0.0, (0, 0, 0)), (1.0, (0.5, 0.5, 0.5))]))
    gem = glow_mat("staff_gem", (0.96, 0.94, 0.98), 1.0)
    paper = paper_mat("staff_paper")
    cloth = Mat("staff_cloth")
    cn = cloth.noise(80, 3, 0.5)
    cloth.color(cloth.ramp(cn.outputs["Fac"], [(0.3, (0.32, 0.03, 0.04)), (0.7, (0.5, 0.06, 0.06))]))
    beads = []
    pillar_cols = [(0.86, 0.06, 0.12), (0.95, 0.72, 0.22), (1.0, 0.36, 0.10), (0.26, 0.80, 0.55), (0.62, 0.38, 1.0), (0.93, 0.90, 0.82),
                   (1.0, 0.32, 0.64)]
    for i, c in enumerate(pillar_cols):
        beads.append(glow_mat(f"bead{i}", c, 1.0, c))
    objs = []
    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=0.032, depth=1.8, location=(0, 0, 0.9))
    objs.append(tag(last(), "shaft", lac.m, smooth=True))
    for z, r, d in ((0.03, 0.04, 0.06), (0.6, 0.038, 0.03), (1.2, 0.038, 0.03), (1.76, 0.042, 0.05)):
        bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=r, depth=d, location=(0, 0, z))
        objs.append(tag(last(), "shaft", gold.m, smooth=True))
    bpy.ops.mesh.primitive_cone_add(vertices=16, radius1=0.04, radius2=0.0, depth=0.08, location=(0, 0, -0.04), rotation=(math.pi, 0, 0))
    objs.append(tag(last(), "shaft", gold.m, smooth=True))
    # cloth grip
    for k in range(7):
        bpy.ops.mesh.primitive_torus_add(major_radius=0.034, minor_radius=0.008, major_segments=16, minor_segments=6, location=(0, 0, 0.75 + k * 0.03))
        objs.append(tag(last(), "shaft", cloth.m, smooth=True))
    # lotus cup: petals
    for k in range(8):
        a = k / 8 * math.tau
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.035, radius2=0.0, depth=0.14,
                                        location=(math.cos(a) * 0.045, math.sin(a) * 0.045, 1.83), rotation=(0, math.radians(35), a))
        o = last()
        o.rotation_euler = (math.cos(a + math.pi / 2) * 0.6, math.sin(a + math.pi / 2) * 0.6, a)
        objs.append(tag(o, "shaft", gold.m))
    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=0.05, depth=0.03, location=(0, 0, 1.8))
    objs.append(tag(last(), "shaft", gold.m, smooth=True))
    # the halo: a vertical ring facing forward, with seven spikes
    bpy.ops.mesh.primitive_torus_add(major_radius=0.2, minor_radius=0.011, major_segments=64, minor_segments=8, location=(0, 0, STAFF_HEAD),
                                     rotation=(math.pi / 2, 0, 0))
    objs.append(tag(last(), "halo", gold.m, smooth=True))
    bpy.ops.mesh.primitive_torus_add(major_radius=0.235, minor_radius=0.004, major_segments=64, minor_segments=6, location=(0, 0, STAFF_HEAD),
                                     rotation=(math.pi / 2, 0, 0))
    objs.append(tag(last(), "halo", gold.m, smooth=True))
    for k in range(14):
        a = k / 14 * math.tau
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.01, radius2=0.0, depth=0.06 if k % 2 == 0 else 0.035,
                                        location=(math.cos(a) * 0.235, 0, STAFF_HEAD + math.sin(a) * 0.235), rotation=(0, math.pi / 2 - a, 0))
        objs.append(tag(last(), "halo", gold.m))
    # seven beads that orbit inside the halo
    for i in range(7):
        a = i / 7 * math.tau
        bpy.ops.mesh.primitive_uv_sphere_add(segments=12, ring_count=8, radius=0.02, location=(math.cos(a) * 0.15, 0, STAFF_HEAD + math.sin(a) * 0.15))
        objs.append(tag(last(), "beads", beads[i].m, smooth=True))
    # the gem
    bpy.ops.mesh.primitive_cone_add(vertices=6, radius1=0.045, radius2=0.0, depth=0.12, location=(0, 0, STAFF_HEAD + 0.045))
    g1 = tag(last(), "gem", gem.m)
    bpy.ops.mesh.primitive_cone_add(vertices=6, radius1=0.045, radius2=0.0, depth=0.09, location=(0, 0, STAFF_HEAD - 0.06), rotation=(math.pi, 0, 0))
    objs += [g1, tag(last(), "gem", gem.m)]
    # two paper talismans on cords
    for s in (-1, 1):
        bpy.ops.mesh.primitive_cube_add(size=1, location=(s * 0.06, 0, 1.62))
        o = last()
        o.scale = (0.035, 0.002, 0.12)
        o.rotation_euler = (0, s * 0.12, 0)
        objs.append(tag(o, "tassel", paper.m))
        bpy.ops.mesh.primitive_cylinder_add(vertices=6, radius=0.003, depth=0.12, location=(s * 0.05, 0, 1.76), rotation=(0, s * 0.3, 0))
        objs.append(tag(last(), "tassel", cloth.m))
    parts = ["shaft", "halo", "beads", "gem", "tassel"]
    stamp_attributes(objs, parts)
    o = join(objs, "staff")
    unwrap(o, 0.006)
    tex = os.path.join(assets, "textures/item")
    imgs = bake(o, [lac, gold, gem, paper, cloth] + beads, 1024, os.path.join(tex, "staff.png"), os.path.join(tex, "staff_glow.png"))
    piv = {"halo": (0, 0, STAFF_HEAD), "beads": (0, 0, STAFF_HEAD), "gem": (0, 0, STAFF_HEAD), "tassel": (0, 0, 1.79)}
    export(o, parts, piv, os.path.join(assets, "models/item/staff_mesh.json"))
    preview(o, preview_material("pv", imgs["color"], imgs["glow"], 5), os.path.join(out, "staff.png"), (0.9, -2.2, 1.7), (0, 0, 1.5), 50)


# ------------------------------------------------------------------------------------------------ heart engine

def build_engine(assets, out):
    reset()
    bronze = gold_mat("eng_bronze", (0.5, 0.33, 0.16))
    bronze.glow(bronze.ramp(channel_glow(bronze, [("Z", 0.6, 0.03), ("X", 0.6, 0.03)]), [(0.0, (0, 0, 0)), (1.0, (1, 1, 1))]))
    rock = stone_mat("eng_rock", (0.05, 0.03, 0.025), (0.16, 0.08, 0.05), 0.6)
    core = glow_mat("eng_core", (0.9, 1.0, 0.95), 1.0)
    objs = []
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=2.4, location=(0, 0, 0))
    objs.append(tag(last(), "core", core.m, smooth=True))
    for k, (R, w) in enumerate(((4.4, 0.32), (6.2, 0.4), (8.2, 0.5))):
        bpy.ops.mesh.primitive_torus_add(major_radius=R, minor_radius=w, major_segments=128, minor_segments=4, location=(0, 0, 0))
        o = last()
        o.scale = (1, 1, 1.8)
        objs.append(tag(o, f"ring{k + 1}", bronze.m))
        n = 36 + k * 12
        for t in range(n):
            a = t / n * math.tau
            bpy.ops.mesh.primitive_cube_add(size=1, location=(math.cos(a) * (R + w + 0.18), math.sin(a) * (R + w + 0.18), 0), rotation=(0, 0, a))
            tooth = last()
            tooth.scale = (0.36, 0.18, w * 1.6)
            objs.append(tag(tooth, f"ring{k + 1}", bronze.m))
        for t in range(4):
            a = t / 4 * math.tau + k
            bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=0.16, depth=R * 2 - 2.6, location=(0, 0, 0), rotation=(math.pi / 2, 0, a))
            objs.append(tag(last(), f"ring{k + 1}", bronze.m))
    # the cavern frame: a ring floor and eight great buttresses
    bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=13, depth=1.2, location=(0, 0, -10.5))
    objs.append(tag(last(), "frame", rock.m))
    for t in range(8):
        a = t / 8 * math.tau
        bpy.ops.mesh.primitive_cube_add(size=1, location=(math.cos(a) * 12, math.sin(a) * 12, -2), rotation=(0, 0, a))
        o = last()
        o.scale = (1.4, 1.0, 17)
        bv = o.modifiers.new("b", "BEVEL")
        bv.width = 0.15
        apply_mods(o)
        objs.append(tag(o, "frame", rock.m))
        bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=0.35, depth=10.5, location=(math.cos(a) * 6.2, math.sin(a) * 6.2, -5.2), rotation=(0, 0, 0))
        objs.append(tag(last(), "frame", bronze.m))
    parts = ["core", "ring1", "ring2", "ring3", "frame"]
    stamp_attributes(objs, parts)
    o = join(objs, "engine")
    unwrap(o, 0.002)
    tex = os.path.join(assets, "textures/entity")
    imgs = bake(o, [bronze, rock, core], 2048, os.path.join(tex, "engine.png"), os.path.join(tex, "engine_glow.png"))
    export(o, parts, {}, os.path.join(assets, "models/entity/engine.json"))
    preview(o, preview_material("pv", imgs["color"], imgs["glow"], 3), os.path.join(out, "engine.png"), (14, -24, 8), (0, 0, -1), 30)


# ------------------------------------------------------------------------------------------------ halo gate

def build_gate(assets, out):
    reset()
    gold = gold_mat("gate_gold", (0.95, 0.78, 0.4))
    gold.glow(gold.ramp(channel_glow(gold, [("X", 1.6, 0.08), ("Y", 1.6, 0.08)]), [(0.0, (0.15, 0.15, 0.15)), (1.0, (1, 1, 1))]))
    light = glow_mat("gate_light", (1.0, 0.95, 0.8), 1.0)
    objs = []
    bpy.ops.mesh.primitive_torus_add(major_radius=30, minor_radius=1.2, major_segments=192, minor_segments=8, location=(0, 0, 0))
    objs.append(tag(last(), "outer", gold.m, smooth=True))
    bpy.ops.mesh.primitive_torus_add(major_radius=33, minor_radius=0.35, major_segments=192, minor_segments=6, location=(0, 0, 0))
    objs.append(tag(last(), "outer", light.m, smooth=True))
    for t in range(24):
        a = t / 24 * math.tau
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.9, radius2=0.0, depth=5 if t % 2 == 0 else 3,
                                        location=(math.cos(a) * 33.5, math.sin(a) * 33.5, 0), rotation=(0, math.pi / 2, a))
        objs.append(tag(last(), "outer", gold.m))
    bpy.ops.mesh.primitive_torus_add(major_radius=21, minor_radius=0.7, major_segments=160, minor_segments=6, location=(0, 0, 0))
    objs.append(tag(last(), "inner", gold.m, smooth=True))
    for t in range(12):
        a = t / 12 * math.tau
        bpy.ops.mesh.primitive_cube_add(size=1, location=(math.cos(a) * 25.5, math.sin(a) * 25.5, 0), rotation=(0, 0, a))
        o = last()
        o.scale = (9, 0.5, 0.5)
        objs.append(tag(o, "inner", gold.m))
    parts = ["outer", "inner"]
    stamp_attributes(objs, parts)
    o = join(objs, "gate")
    unwrap(o, 0.002)
    tex = os.path.join(assets, "textures/entity")
    imgs = bake(o, [gold, light], 1024, os.path.join(tex, "gate.png"), os.path.join(tex, "gate_glow.png"))
    export(o, parts, {}, os.path.join(assets, "models/entity/gate.json"))
    preview(o, preview_material("pv", imgs["color"], imgs["glow"], 4), os.path.join(out, "gate.png"), (0, -90, 30), (0, 0, 0), 35)


# ------------------------------------------------------------------------------------------------ planet fragments

def build_fragments(assets, out, n=28):
    reset()
    random.seed(11)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=1.0, calc_uvs=True)
    sph = last()
    bpy.ops.object.shade_smooth()
    seeds = []
    while len(seeds) < n:
        v = Vector((random.gauss(0, 1), random.gauss(0, 1), random.gauss(0, 1)))
        if v.length > 1e-3:
            seeds.append(v.normalized())
    me = sph.data
    cell = []
    for poly in me.polygons:
        c = poly.center.normalized()
        # jitter the borders so the plates break raggedly
        c = (c + Vector((random.uniform(-1, 1), random.uniform(-1, 1), random.uniform(-1, 1))) * 0.04).normalized()
        cell.append(max(range(n), key=lambda k: c.dot(seeds[k])))
    pieces = []
    for k in range(n):
        bm = bmesh.new()
        bm.from_mesh(me)
        bm.faces.ensure_lookup_table()
        kill = [f for f in bm.faces if cell[f.index] != k]
        bmesh.ops.delete(bm, geom=kill, context="FACES")
        m2 = bpy.data.meshes.new(f"frag{k}")
        bm.to_mesh(m2)
        bm.free()
        o = bpy.data.objects.new(f"frag{k}", m2)
        bpy.context.scene.collection.objects.link(o)
        sol = o.modifiers.new("s", "SOLIDIFY")
        sol.thickness = 0.16
        sol.offset = -1
        sol.use_rim = True
        sol.material_offset = 1
        sol.material_offset_rim = 1
        o.data.materials.append(bpy.data.materials.new("outer"))
        o.data.materials.append(bpy.data.materials.new("inner"))
        apply_mods(o)
        o["doa_part"] = f"f{k}"
        active(o)
        bpy.ops.object.shade_smooth()
        pieces.append(o)
    bpy.data.objects.remove(sph)
    parts = [f"f{k}" for k in range(n)]
    pivots = {}
    for o in pieces:
        me = o.data
        me.attributes.new("doa_part", "INT", "FACE")
        me.attributes.new("doa_flag", "INT", "FACE")
        pa, fl = me.attributes["doa_part"], me.attributes["doa_flag"]
        pi = parts.index(o["doa_part"])
        for poly in me.polygons:
            pa.data[poly.index].value = pi
            fl.data[poly.index].value = 1 if poly.material_index == 1 else 0
        cen = sum((v.co for v in me.vertices), Vector()) / max(1, len(me.vertices))
        pivots[o["doa_part"]] = (cen.x, cen.y, cen.z)
    o = join(pieces, "fragments")
    export(o, parts, pivots, os.path.join(assets, "models/space/fragments.json"))


# ------------------------------------------------------------------------------------------------ space panorama (Rift)

def build_panorama(assets, out):
    """An equirectangular 360 render of deep space: the view through the Rift's tears."""
    reset()
    tex = os.path.join(assets, "textures/space")
    sc = bpy.context.scene
    sc.cycles.samples = 64
    w = bpy.data.worlds.new("space")
    w.use_nodes = True
    nt = w.node_tree
    env = nt.nodes.new("ShaderNodeTexEnvironment")
    env.image = bpy.data.images.load(os.path.join(tex, "milkyway.png"))
    nt.links.new(env.outputs["Color"], nt.nodes["Background"].inputs["Color"])
    nt.nodes["Background"].inputs[1].default_value = 2.2
    sc.world = w

    def planet(img, r, loc, rot=(0, 0, 0), emit=None):
        bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=r, location=loc, calc_uvs=True)
        o = last()
        o.rotation_euler = rot
        bpy.ops.object.shade_smooth()
        m = bpy.data.materials.new("p")
        m.use_nodes = True
        b = m.node_tree.nodes["Principled BSDF"]
        t = m.node_tree.nodes.new("ShaderNodeTexImage")
        t.image = bpy.data.images.load(os.path.join(tex, img))
        m.node_tree.nodes.new("ShaderNodeTexCoord")
        m.node_tree.links.new(t.outputs["Color"], b.inputs["Emission Color" if emit else "Base Color"])
        if emit:
            b.inputs["Emission Strength"].default_value = emit
            b.inputs["Base Color"].default_value = (0, 0, 0, 1)
        b.inputs["Roughness"].default_value = 0.9
        o.data.materials.append(m)
        return o

    planet("giant.png", 9, (40, 60, 12), (0.3, 0.2, 0))
    planet("red.png", 4, (-30, 45, -10), (0, 0, 1))
    planet("moon.png", 1.6, (-18, 30, 8))
    planet("earth.png", 6, (20, -40, -14), (1.1, 0, 0.5))
    bpy.ops.mesh.primitive_plane_add(size=120, location=(-80, -60, 40))
    gal = last()
    gal.rotation_euler = (0.9, 0.3, 0.4)
    gm = bpy.data.materials.new("g")
    gm.use_nodes = True
    gnt = gm.node_tree
    gb = gnt.nodes["Principled BSDF"]
    gt = gnt.nodes.new("ShaderNodeTexImage")
    gt.image = bpy.data.images.load(os.path.join(tex, "galaxy.png"))
    gnt.links.new(gt.outputs["Color"], gb.inputs["Emission Color"])
    gb.inputs["Emission Strength"].default_value = 3.0
    gb.inputs["Base Color"].default_value = (0, 0, 0, 1)
    gb.inputs["Alpha"].default_value = 1.0
    gal.data.materials.append(gm)
    sun = bpy.data.lights.new("sun", "SUN")
    sun.energy = 3.5
    so = bpy.data.objects.new("sun", sun)
    so.rotation_euler = (math.radians(60), 0, math.radians(-50))
    sc.collection.objects.link(so)
    cam = bpy.data.cameras.new("pano")
    cam.type = "PANO"
    try:
        cam.panorama_type = "EQUIRECTANGULAR"
    except Exception:
        cam.cycles.panorama_type = "EQUIRECTANGULAR"
    co = bpy.data.objects.new("pano", cam)
    co.rotation_euler = (math.pi / 2, 0, 0)
    sc.collection.objects.link(co)
    sc.camera = co
    sc.render.resolution_x = 2048
    sc.render.resolution_y = 1024
    sc.render.filepath = os.path.join(tex, "panorama.png")
    if not ARGS.no_render:
        bpy.ops.render.render(write_still=True)
        print("[pano] panorama.png")


def main():
    global ARGS
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--no-render", action="store_true")
    ap.add_argument("--only", default="")
    ARGS = ap.parse_args(argv)
    assets, out = os.path.abspath(ARGS.assets), os.path.abspath(ARGS.out)
    os.makedirs(out, exist_ok=True)
    steps = [("pillar", build_pillar), ("staff", build_staff), ("engine", build_engine), ("gate", build_gate),
             ("fragments", build_fragments), ("panorama", build_panorama)]
    for name, fn in steps:
        if ARGS.only and name not in ARGS.only.split(","):
            continue
        print(f"===== {name}")
        fn(assets, out)


if __name__ == "__main__":
    main()
