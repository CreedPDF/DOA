# DOA — Delux's Orbital Armory

Fabric 1.21.1 mod. Use the **Talisman of Summoning** (召) and the orbital armory answers:

1. A temple bell tolls from the heavens and the cutscene begins.
2. Seven black, ancient pillars fall one by one into a circle around you (clockwise from north). Each one gets its own low-angle shot and a rugged, hand-inked impact frame in its colour as it lands. Then its name lights up above it:
   死 Death (crimson) · 贖 Redemption (gold) · 怒 Wrath (ember) · 魂 Soul (jade) · 運 Fate (violet) · 静 Silence (ivory) · 虚 Void (sakura)
3. A ring of light binds the seven pillars. A black core orb forms, ink corruption spreads over the ground, and an opaque dark sphere rises with a burning, ragged rim until it closes over you. Its wall is solid: nothing gets in.
4. Each pillar releases its relic (a device from the armory; what each one does comes in a later version).
5. Use the talisman again inside the sphere to release the seal. The sphere sinks and the pillars return to the heavens.

Recipe: gold ingot on top, then paper / eye of ender / paper, then redstone on the bottom.

## Build
Everything is built in GitHub Actions:
- `tools/blender/doa_models.py` builds the pillar and relic models, their atlases and glow maps in headless Blender, and renders preview images into `docs/renders`.
- `tools/textures/doa_textures.py` makes the VFX, GUI and item textures.
- `tools/textures/glyphs.py` makes the brushed kanji masks.
- `tools/sfx/doa_sounds.py` synthesizes every sound from scratch: bonsho temple bell, taiko, hyoshigi, shakuhachi, koto in the hirajoshi scale, furin chimes, and a monk drone, all in a stone-hall reverb.
- Gradle builds the jar, and each push to `main` publishes a release.
