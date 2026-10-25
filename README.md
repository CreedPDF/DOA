# DOA — Delux's Orbital Armory

Fabric 1.21.1 mod. Hold use with the **Staff of the Orbital Armory** to charge it, and the armory answers:

1. A temple bell tolls from the heavens and the cutscene begins.
2. Seven pillars appear one after another as flickering stars high in the sky, then plunge to earth in under half a second, landing in a wide circle (13 blocks out). Each one has its own shot, a hand-inked impact frame in its colour, and its name written above it in DOA's own lettering:
   死 Death · 贖 Redemption · 怒 Wrath · 魂 Soul · 運 Fate · 静 Silence · 虚 Void
3. A ring of light binds them, a black core orb forms, ink spreads over the ground and turns it to black ink stone, and an opaque dark sphere (22 blocks across the radius) rises and closes. Its wall is solid; nothing gets in.
4. Each pillar releases a relic. Look at one and use the staff to awaken its move (sneak + use breaks the seal):
   - 死 **Eclipse**: the moon swallows the sun; at totality the black sun's crimson light executes every enemy in turn.
   - 贖 **Dawn**: up through the clouds to a sunrise sea of cloud; a colossal halo gate pours heaven's light down into the sphere. Full heal, cleanse, Regeneration, Absorption, Resistance; the undead burn.
   - 怒 **Planetbreaker**: up into orbit; the pillars forge a spear of light that breaks a red planet apart; its burning pieces fall as meteors on your enemies.
   - 魂 **Heart of the World**: down a shaft through every layer of the earth to the Heart Engine at the core, which drains your enemies' souls and gives you their strength.
   - 運 **Beyond**: out past the planet, the solar system and the Milky Way to the cosmic web, where the seven stars align, then a rush back down as the light of the universe strikes.
   - 静 **Moonrise**: night falls, a colossal moon rises and everything around you freezes for 30 seconds.
   - 虚 **Rift**: the sky tears open into space and your enemies are pulled up through the rifts.

Recipe: gold ingot, eye of ender, gold ingot on top, blaze rods down the middle.

## Build
Everything is built in GitHub Actions:
- `tools/blender/doa_meshes.py` models and bakes (in headless Blender) the pillar, staff, Heart Engine, halo gate and shattered-planet fragments, and renders the deep-space panorama; `tools/blender/doa_models.py` builds the relic and the planet meshes. Previews go to `docs/renders`.
- `tools/textures/doa_textures.py` makes the VFX, GUI and item textures.
- `tools/textures/glyphs.py` makes the brushed kanji masks.
- `tools/textures/impact_frames.py` paints the 28 hand-inked impact frames (four per pillar).
- `tools/textures/space_textures.py` paints and bakes the lighting of the planets (Earth, moon, red planet, ringed giant), plus the Milky Way, a spiral galaxy, the cosmic web, the sun's glare and the eclipse corona.
- `tools/textures/doa_scenes.py` paints the pillar panels, clouds, the layers of the earth and other scene textures; `tools/textures/doa_font.py` builds DOA's lettering.
- `tools/sfx/doa_sounds.py` synthesizes every sound from scratch: bonsho temple bell, taiko, hyoshigi, shakuhachi, koto in the hirajoshi scale, furin chimes, and a monk drone, all in a stone-hall reverb.
- Gradle builds the jar, and each push to `main` publishes a release.
