package com.creedpdf.doa.client.fx;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/** One tinted DOA particle: light motes, sparks, petals, ink wisps and talisman shards. */
public class DoaParticle extends SpriteBillboardParticle {
    private final float baseSize;
    private final float baseAlpha;
    private final boolean glow;
    private final float spin;
    private final float fadeIn;
    private final float sway;
    private final float growth;
    private final float swayPhase;

    public DoaParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites,
                       float r, float g, float b, float a, float size, int life, float gravity, float drag, boolean glow,
                       float spin, float growth, float sway) {
        super(world, x, y, z);
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.setSprite(sprites.getSprite(world.random));
        this.red = r;
        this.green = g;
        this.blue = b;
        this.baseAlpha = a;
        this.alpha = a;
        this.baseSize = size;
        this.scale = size;
        this.maxAge = Math.max(2, life);
        this.gravityStrength = gravity;
        this.velocityMultiplier = drag;
        this.glow = glow;
        this.spin = spin;
        this.growth = growth;
        this.sway = sway;
        this.fadeIn = Math.min(6, life * 0.15f);
        this.swayPhase = world.random.nextFloat() * 6.28f;
        this.collidesWithWorld = false;
        this.angle = world.random.nextFloat() * 6.28f;
        this.prevAngle = this.angle;
    }

    @Override
    public void tick() {
        this.prevAngle = this.angle;
        this.angle += spin;
        if (sway != 0) {
            float s = MathHelper.sin(age * 0.18f + swayPhase) * sway;
            this.velocityX += s * 0.01;
            this.velocityZ += MathHelper.cos(age * 0.15f + swayPhase) * sway * 0.01;
        }
        super.tick();
        float k = (float) age / maxAge;
        float in = fadeIn <= 0 ? 1 : Math.min(1, age / fadeIn);
        float out = 1 - k * k;
        this.alpha = baseAlpha * in * out;
        this.scale = baseSize * (1 + growth * k);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tint) {
        return glow ? 0xF000F0 : super.getBrightness(tint);
    }
}
