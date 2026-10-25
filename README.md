# DOA — Delux's Orbital Armory

Fabric 1.21.1 mod. Use the **Talisman of Summoning** (召) and the orbital armory answers:

1. A temple bell tolls from the heavens and the cutscene begins.
2. Seven black, ancient pillars fall one by one into a circle around you (clockwise from north). Each one gets its own low-angle shot and a rugged, hand-inked impact frame in its colour as it lands. Then its name lights up above it:
   死 Death (crimson) · 贖 Redemption (gold) · 怒 Wrath (ember) · 魂 Soul (jade) · 運 Fate (violet) · 静 Silence (ivory) · 虚 Void (sakura)
3. A ring of light binds the seven pillars. A black core orb forms, ink corruption spreads over the ground, and an opaque dark sphere rises with a burning, ragged rim until it closes over you. Its wall is solid: nothing gets in.
4. The ground inside turns to black ink stone (put back exactly as it was when the seal breaks), and each pillar releases its relic.
5. Look at a relic and use the talisman to awaken it. Each has its own cutscene and impact frames:
   - 死 **Verdict of Death**: every hostile within 64 blocks is branded with a crimson seal, then executed by falling ink blades.
   - 贖 **Absolution**: the dome's crown opens and a shaft of golden light heals you fully, cleanses debuffs and grants Regeneration II, Absorption IV, Resistance II and Fire Resistance.
   - 怒 **Heaven's Wrath**: a burning gate opens in the sky and 30 spears of fire rain on enemies around the armory (explosions that don't break blocks).
   - 魂 **Soul Harvest**: jade soul threads drain every nearby hostile and pour into you: healing, Strength II, Speed II.
   - 運 **Alignment of Fate**: you rise into space. A living planet with clouds, city lights and atmosphere, its moon, a ringed gas giant, the sun and the Milky Way. The seven stars align and fire, and a pillar of starlight strikes the strongest enemy (50 damage, clears the weather, grants Luck, Strength, Haste, Hero of the Village).
   - 静 **Bell of Silence**: a great bell tolls and every mob within 64 blocks freezes in place for 10 seconds.
   - 虚 **The Void Maw**: a singularity opens outside the sphere, drags enemies in and collapses.
   Each relic rests after use (45 s to 2 min).
6. Sneak + use the talisman to break the seal. The sphere sinks and the pillars return to the heavens.

Recipe: gold ingot on top, then paper / eye of ender / paper, then redstone on the bottom.

## Build
Everything is built in GitHub Actions:
- `tools/blender/doa_models.py` builds the pillar and relic models, their atlases and glow maps in headless Blender, and renders preview images into `docs/renders`.
- `tools/textures/doa_textures.py` makes the VFX, GUI and item textures.
- `tools/textures/glyphs.py` makes the brushed kanji masks.
- `tools/textures/impact_frames.py` paints the 28 hand-inked impact frames (four per pillar).
- `tools/textures/space_textures.py` paints the planets, clouds, city lights, rings, Milky Way and sun glare. Blender builds the planet and ring meshes and renders `docs/renders/space.png`.
- `tools/sfx/doa_sounds.py` synthesizes every sound from scratch: bonsho temple bell, taiko, hyoshigi, shakuhachi, koto in the hirajoshi scale, furin chimes, and a monk drone, all in a stone-hall reverb.
- Gradle builds the jar, and each push to `main` publishes a release.
