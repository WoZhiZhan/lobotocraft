package com.wzz.lobotocraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.wzz.lobotocraft.event.listener.ScreenSnakeEvent;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ExplosionRenderer {
    
    public static class UltimateExplosion {
        public Vec3 position;
        public float time;
        public float maxTime;
        public float radius;
        public List<ShockwaveRing> shockwaves;
        public List<FireBall> fireBalls;
        public List<DebrisParticle> debris;
        public boolean hasScreenShake;
        
        public UltimateExplosion(Vec3 pos, float duration, float radius) {
            this.position = pos;
            this.time = 0;
            this.maxTime = duration;
            this.radius = radius;
            this.hasScreenShake = true;
            this.shockwaves = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                shockwaves.add(new ShockwaveRing(pos, radius, i * 0.2f));
            }
            this.fireBalls = new ArrayList<>();
            int fireballCount = (int)(radius * 1.5f) + 5;
            for (int i = 0; i < fireballCount; i++) {
                fireBalls.add(new FireBall(pos, radius));
            }
            this.debris = new ArrayList<>();
            int debrisCount = (int)(radius * 3) + 10;
            for (int i = 0; i < debrisCount; i++) {
                debris.add(new DebrisParticle(pos, radius));
            }
        }
        
        public float getProgress() {
            return Mth.clamp(time / maxTime, 0.0f, 1.0f);
        }
        
        public boolean isActive() {
            return time < maxTime;
        }
        
        public void update(float deltaTime) {
            time += deltaTime;
            if (hasScreenShake && time < 1.0f) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    double distance = mc.player.position().distanceTo(position);
                    if (distance < radius * 5) {
                        applyScreenShake();
                    }
                }
            }
            for (ShockwaveRing ring : shockwaves) {
                ring.update(deltaTime);
            }
            for (FireBall ball : fireBalls) {
                ball.update(deltaTime);
            }
            for (DebrisParticle particle : debris) {
                particle.update(deltaTime);
            }
        }
        
        private void applyScreenShake() {
            ScreenSnakeEvent.triggerShake((int) time);
        }
    }

    public static class ShockwaveRing {
        public Vec3 center;
        public float currentRadius;
        public float maxRadius;
        public float startTime;
        public float life;
        public float maxLife;
        
        public ShockwaveRing(Vec3 center, float maxRadius, float delay) {
            this.center = center;
            this.currentRadius = 0;
            this.maxRadius = maxRadius * 8; // 冲击波比爆炸大
            this.startTime = delay;
            this.life = 0;
            this.maxLife = 2.0f;
        }
        
        public void update(float deltaTime) {
            life += deltaTime;
            
            if (life > startTime) {
                float progress = (life - startTime) / (maxLife - startTime);
                currentRadius = maxRadius * progress;
            }
        }
        
        public boolean isVisible() {
            return life > startTime && life < maxLife;
        }
        
        public float getAlpha() {
            if (!isVisible()) return 0;
            
            float progress = (life - startTime) / (maxLife - startTime);
            return (1.0f - progress) * 0.6f;
        }
    }

    public static class FireBall {
        public Vec3 startPos;
        public Vec3 currentPos;
        public Vec3 velocity;
        public float size;
        public float life;
        public float maxLife;
        public float rotation;
        public float rotationSpeed;
        public int colorPhase; // 0=白热, 1=黄, 2=橙, 3=红
        
        public FireBall(Vec3 startPos, float explosionRadius) {
            this.startPos = startPos;
            this.currentPos = startPos;
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            float speed = (float)(Math.random() * 2 + 0.5) * explosionRadius * 0.3f;
            
            this.velocity = new Vec3(
                Math.sin(phi) * Math.cos(theta) * speed,
                Math.cos(phi) * speed + Math.random() * 0.5, // 向上偏移
                Math.sin(phi) * Math.sin(theta) * speed
            );
            
            this.size = (float)(Math.random() * 0.8 + 0.4) * explosionRadius * 0.2f;
            this.life = 0;
            this.maxLife = (float)(Math.random() * 1.5 + 1.0);
            this.rotation = (float)(Math.random() * 360);
            this.rotationSpeed = (float)(Math.random() * 360 - 180);
            this.colorPhase = 0;
        }
        
        public void update(float deltaTime) {
            life += deltaTime;
            currentPos = currentPos.add(velocity.scale(deltaTime));
            velocity = velocity.add(0, -9.8f * deltaTime * 0.2f, 0);
            velocity = velocity.scale(0.95f);
            rotation += rotationSpeed * deltaTime;
            float progress = life / maxLife;
            if (progress < 0.2f) colorPhase = 0;
            else if (progress < 0.4f) colorPhase = 1;
            else if (progress < 0.7f) colorPhase = 2;
            else colorPhase = 3;
        }
        
        public boolean isAlive() {
            return life < maxLife;
        }
        
        public float getAlpha() {
            float progress = life / maxLife;
            if (progress < 0.1f) return progress / 0.1f;
            else return 1.0f - (progress - 0.1f) / 0.9f;
        }
        
        public int getColor() {
            float alpha = getAlpha();
            int red, green, blue;
            
            switch (colorPhase) {
                case 0: // 白热
                    red = 255; green = 255; blue = 255;
                    break;
                case 1: // 黄
                    red = 255; green = 255; blue = 100;
                    break;
                case 2: // 橙
                    red = 255; green = 150; blue = 0;
                    break;
                default: // 红
                    red = 255; green = 50; blue = 0;
            }
            
            return ((int)(alpha * 255) << 24) | (red << 16) | (green << 8) | blue;
        }
    }

    public static class DebrisParticle {
        public Vec3 currentPos;
        public Vec3 velocity;
        public float size;
        public float life;
        public float maxLife;
        public Vec3 rotationAxis;
        public float rotationAngle;
        public float rotationSpeed;
        
        public DebrisParticle(Vec3 startPos, float explosionRadius) {
            this.currentPos = startPos;
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            float speed = (float)(Math.random() * 3 + 1) * explosionRadius * 0.4f;
            this.velocity = new Vec3(
                Math.sin(phi) * Math.cos(theta) * speed,
                Math.cos(phi) * speed,
                Math.sin(phi) * Math.sin(theta) * speed
            );
            this.size = (float)(Math.random() * 0.3 + 0.1);
            this.life = 0;
            this.maxLife = (float)(Math.random() * 3 + 2);
            this.rotationAxis = new Vec3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize();
            this.rotationAngle = 0;
            this.rotationSpeed = (float)(Math.random() * 720 - 360);
        }
        
        public void update(float deltaTime) {
            life += deltaTime;
            currentPos = currentPos.add(velocity.scale(deltaTime));
            velocity = velocity.add(0, -9.8f * deltaTime, 0);
            velocity = velocity.scale(0.98f);
            rotationAngle += rotationSpeed * deltaTime;
        }
        
        public boolean isAlive() {
            return life < maxLife;
        }
        
        public float getAlpha() {
            float progress = life / maxLife;
            return Math.max(0, 1.0f - progress * progress);
        }
    }
    
    private static final List<UltimateExplosion> explosions = new ArrayList<>();

    private static final RenderType SHOCKWAVE_TYPE = RenderType.create(
        "shockwave",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLE_STRIP,
        256,
        false, false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
            .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false)
    );

    public static final RenderType FIREBALL_TYPE = RenderType.create(
        "fireball",
        DefaultVertexFormat.POSITION_COLOR_TEX,
        VertexFormat.Mode.QUADS,
        256,
        false, false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexShader))
            .setTextureState(new RenderStateShard.TextureStateShard(
                ResourceUtil.createInstance("textures/misc/fire.png"), false, false))
            .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false)
    );
    
    public static void createExplosion(Vec3 position, float duration, float radius) {
        explosions.add(new UltimateExplosion(position, duration, radius));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(
                position.x, position.y, position.z,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS,
                4.0f,
                0.5f + (float)Math.random() * 0.2f,
                false
            );
        }
    }
    
    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (explosions.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float deltaTime = event.getPartialTick() * 0.016f;
        
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        
        for (UltimateExplosion explosion : explosions) {
            if (explosion.isActive()) {
                // 渲染冲击波
                renderShockwaves(poseStack, bufferSource, explosion, cameraPos);
                
                // 渲染火球
                renderFireballs(poseStack, bufferSource, explosion, cameraPos);
                
                // 渲染碎片
                renderDebris(poseStack, bufferSource, explosion, cameraPos);
            }
        }
        
        bufferSource.endBatch();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        
        // 更新爆炸
        explosions.removeIf(explosion -> {
            explosion.update(deltaTime);
            return !explosion.isActive();
        });
    }
    
    private static void renderShockwaves(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                       UltimateExplosion explosion, Vec3 cameraPos) {
        VertexConsumer buffer = bufferSource.getBuffer(SHOCKWAVE_TYPE);
        
        for (ShockwaveRing ring : explosion.shockwaves) {
            if (ring.isVisible()) {
                renderShockwaveRing(poseStack, buffer, ring, cameraPos);
            }
        }
    }
    
    private static void renderShockwaveRing(PoseStack poseStack, VertexConsumer buffer,
                                          ShockwaveRing ring, Vec3 cameraPos) {
        poseStack.pushPose();
        
        Vec3 relativePos = ring.center.subtract(cameraPos);
        poseStack.translate(relativePos.x, relativePos.y, relativePos.z);
        
        Matrix4f matrix = poseStack.last().pose();
        
        int segments = 32;
        float alpha = ring.getAlpha();
        int color = ((int) (alpha * 255) << 24) | (255 << 16) | (100 << 8);
        for (int i = 0; i <= segments; i++) {
            float angle = (float)(i * 2 * Math.PI / segments);
            float x = Mth.cos(angle) * ring.currentRadius;
            float z = Mth.sin(angle) * ring.currentRadius;
            
            buffer.vertex(matrix, x, -0.1f, z).color(color).endVertex();
            buffer.vertex(matrix, x, 0.1f, z).color(color).endVertex();
        }
        
        poseStack.popPose();
    }
    
    private static void renderFireballs(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                      UltimateExplosion explosion, Vec3 cameraPos) {
        VertexConsumer buffer = bufferSource.getBuffer(FIREBALL_TYPE);
        
        for (FireBall fireball : explosion.fireBalls) {
            if (fireball.isAlive()) {
                renderFireball(poseStack, buffer, fireball, cameraPos);
            }
        }
    }
    
    private static void renderFireball(PoseStack poseStack, VertexConsumer buffer,
                                     FireBall fireball, Vec3 cameraPos) {
        poseStack.pushPose();
        
        Vec3 relativePos = fireball.currentPos.subtract(cameraPos);
        poseStack.translate(relativePos.x, relativePos.y, relativePos.z);
        
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(fireball.rotation));
        
        float scale = fireball.size;
        poseStack.scale(scale, scale, scale);
        
        Matrix4f matrix = poseStack.last().pose();
        int color = fireball.getColor();
        buffer.vertex(matrix, -1, -1, 0).color(color).uv(0, 0).endVertex();
        buffer.vertex(matrix, 1, -1, 0).color(color).uv(1, 0).endVertex();
        buffer.vertex(matrix, 1, 1, 0).color(color).uv(1, 1).endVertex();
        buffer.vertex(matrix, -1, 1, 0).color(color).uv(0, 1).endVertex();
        
        poseStack.popPose();
    }
    
    private static void renderDebris(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                   UltimateExplosion explosion, Vec3 cameraPos) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        
        for (DebrisParticle debris : explosion.debris) {
            if (debris.isAlive()) {
                renderDebrisParticle(poseStack, buffer, debris, cameraPos);
            }
        }
    }
    
    private static void renderDebrisParticle(PoseStack poseStack, VertexConsumer buffer,
                                           DebrisParticle debris, Vec3 cameraPos) {
        poseStack.pushPose();
        
        Vec3 relativePos = debris.currentPos.subtract(cameraPos);
        poseStack.translate(relativePos.x, relativePos.y, relativePos.z);
        
        Matrix4f matrix = poseStack.last().pose();
        
        float alpha = debris.getAlpha();
        int color = ((int) (alpha * 255) << 24) | (100 << 16) | (50 << 8);
        
        float size = debris.size;
        buffer.vertex(matrix, -size, 0, 0).color(color).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, size, 0, 0).color(color).normal(0, 1, 0).endVertex();
        
        buffer.vertex(matrix, 0, -size, 0).color(color).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, 0, size, 0).color(color).normal(0, 1, 0).endVertex();
        
        buffer.vertex(matrix, 0, 0, -size).color(color).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, 0, 0, size).color(color).normal(0, 1, 0).endVertex();
        
        poseStack.popPose();
    }
}