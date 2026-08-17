package com.wzz.lobotocraft.client.core;

import com.mojang.math.Axis;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahVisualEffectType;
import com.wzz.lobotocraft.entity.EntityGeburah;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

public final class GeburahEffectCompositor {
    private static final int MAX_DURATION_TICKS = 60;
    static final int POSTURE_BREAK_CRACK_COLOR = 0xB8FFE2A3;
    static final int POSTURE_BREAK_SHARD_RGB = 0xFFD36A;
    private static final EnumMap<GeburahVisualEffectType, ActiveEffect> ACTIVE =
            new EnumMap<>(GeburahVisualEffectType.class);
    private static long triggerSequence;
    private static boolean shaderEnabled;
    private static Mode mode = Mode.REDUCED;

    private GeburahEffectCompositor() {
    }

    public static void trigger(GeburahVisualEffectType type, Vec3 origin, Vec3 direction,
                               float intensity, int durationTicks, int seed) {
        trigger(type, origin, direction, intensity, 0.0F, durationTicks, seed);
    }

    public static void trigger(GeburahVisualEffectType type, Vec3 origin, Vec3 direction,
                               float intensity, float hitSeverity,
                               int durationTicks, int seed) {
        trigger(type, origin, direction, intensity, hitSeverity, false, durationTicks, seed);
    }

    public static void trigger(GeburahVisualEffectType type, Vec3 origin, Vec3 direction,
                               float intensity, float hitSeverity, boolean ego,
                               int durationTicks, int seed) {
        if (type == null || origin == null || direction == null
                || !Float.isFinite(intensity) || intensity <= 0.0F) return;
        int duration = Math.max(1, Math.min(MAX_DURATION_TICKS, durationTicks));
        float safeIntensity = Mth.clamp(intensity, 0.05F, 2.0F);
        float safeSeverity = Float.isFinite(hitSeverity)
                ? Mth.clamp(hitSeverity, 0.0F, 1.25F) : 0.0F;
        long sequence = ++triggerSequence;
        shaderEnabled |= CoreSuppressionClientState.usesFullVisualEffects();
        ActiveEffect existing = ACTIVE.get(type);
        if (existing != null) {
            if (isAttackEffect(type)) {
                existing.remainingTicks = duration;
                existing.totalTicks = duration;
                existing.intensity = safeIntensity;
                existing.hitSeverity = safeSeverity;
                existing.ego = ego;
            } else {
                existing.remainingTicks = Math.max(existing.remainingTicks, duration);
                existing.totalTicks = Math.max(existing.totalTicks, duration);
                existing.intensity = Math.max(existing.intensity, safeIntensity);
                existing.hitSeverity = Math.max(existing.hitSeverity, safeSeverity);
            }
            existing.origin = origin;
            existing.direction = horizontal(direction);
            existing.seed = seed;
            existing.sequence = sequence;
            return;
        }
        ACTIVE.put(type, new ActiveEffect(type, origin, horizontal(direction),
                safeIntensity, safeSeverity, ego, duration, duration, seed, sequence));
    }

    public static void tick() {
        Iterator<Map.Entry<GeburahVisualEffectType, ActiveEffect>> iterator =
                ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveEffect effect = iterator.next().getValue();
            effect.remainingTicks--;
            if (effect.remainingTicks <= 0) iterator.remove();
        }
        if (ACTIVE.isEmpty()) shaderEnabled = false;
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        if (mode == Mode.OFF) return;
        ActiveEffect attack = dominant(false);
        ActiveEffect stage = dominant(true);
        if (attack != null) renderEffect(graphics, attack, partialTick);
        if (stage != null) renderEffect(graphics, stage, partialTick);
    }

    public static void renderPersistent(GuiGraphics graphics) {
        if (mode == Mode.OFF) return;
        float strength = mode == Mode.FULL ? 1.0F : 0.58F;
        if (CoreSuppressionClientState.getVisualStage() == 1) {
            renderEgoDangerFrame(graphics, strength);
        }
    }

    private static void renderEffect(GuiGraphics graphics, ActiveEffect effect,
                                     float partialTick) {
        float intensity = effect.intensity
                * (mode == Mode.FULL ? 1.0F : 0.58F);
        float progress = progress(effect, partialTick);
        float[] origin = projectedOrigin(effect.origin);
        int centerX = Math.round(origin[0] * graphics.guiWidth());
        int centerY = Math.round(origin[1] * graphics.guiHeight());
        boolean renderAttackFallback = shouldRenderAttackFallback(
                mode, CoreSuppressionPostProcessor.isReady());

        switch (effect.type) {
            case EGO_RELEASE -> renderEgoRelease(graphics, centerX, centerY, progress, intensity);
            case EGO_EXHAUSTION -> renderExhaustion(
                    graphics, centerX, centerY, progress, intensity, effect.seed);
            case POSTURE_BREAK -> renderPostureBreak(graphics, centerX, centerY,
                    progress, intensity, effect.seed);
            case DEFEAT -> renderDefeat(
                    graphics, centerX, centerY, progress, intensity, effect.seed);
            case COMBO_HIT, SLASH_HIT, STAB_HIT, VERTICAL_SLASH_HIT -> {
                if (renderAttackFallback) renderComboHit(
                        graphics, effect, centerX, centerY, progress, intensity);
            }
            case WILL_OF_BATTLE_HIT -> {
                if (renderAttackFallback) renderWillOfBattleHit(
                        graphics, effect, centerX, centerY, progress, intensity);
            }
            case BLOOD_MIST_HIT -> {
                if (renderAttackFallback) renderBloodMistHit(
                        graphics, effect, centerX, centerY, progress, intensity);
            }
            case BODIES_EVERYWHERE_HIT -> {
                if (renderAttackFallback) renderBodiesEverywhereHit(
                        graphics, effect, centerX, centerY, progress, intensity);
            }
        }
        if (renderAttackFallback && isAttackEffect(effect.type)) {
            renderHitReaction(graphics, effect, progress, intensity);
        }
    }

    public static void clear() {
        ACTIVE.clear();
        shaderEnabled = false;
        triggerSequence = 0L;
    }

    public static boolean isActive() {
        return !ACTIVE.isEmpty();
    }

    public static boolean usesShader() {
        return mode == Mode.FULL && shaderEnabled && !ACTIVE.isEmpty();
    }

    public static void setMode(Mode selectedMode) {
        mode = selectedMode == null ? Mode.REDUCED : selectedMode;
        if (mode != Mode.FULL) shaderEnabled = false;
    }

    public static Mode mode() {
        return mode;
    }

    public enum Mode {
        FULL,
        REDUCED,
        OFF
    }

    public static GeburahVisualEffectType dominantType() {
        ActiveEffect effect = dominant();
        return effect == null ? null : effect.type;
    }

    public static float shaderProgress(float partialTick) {
        ActiveEffect effect = dominant();
        return effect == null ? 0.0F : progress(effect, partialTick);
    }

    public static float shaderIntensity() {
        ActiveEffect effect = dominant();
        return effect == null ? 0.0F : effect.intensity;
    }

    public static float shaderHitSeverity() {
        ActiveEffect effect = dominant();
        return effect == null ? 0.0F : effect.hitSeverity;
    }

    public static float shaderEgoAttack() {
        ActiveEffect effect = dominant();
        return effect != null && effect.ego ? 1.0F : 0.0F;
    }

    public static float[] shaderOrigin() {
        ActiveEffect effect = dominant();
        if (effect == null) return new float[]{0.5F, 0.5F};
        if (isWeaponHit(effect.type)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                Vec3 bladePosition = GeburahBladeTrailState.recentPosition(
                        minecraft.level.getGameTime(), 2.0D);
                if (bladePosition != null) return projectedOrigin(bladePosition);
            }
        }
        return projectedOrigin(effect.origin);
    }

    public static float[] shaderDirection() {
        ActiveEffect effect = dominant();
        if (effect == null) return new float[]{0.0F, 1.0F};
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot());
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 visualDirection = effect.direction;
        if (effect.type != GeburahVisualEffectType.WILL_OF_BATTLE_HIT
                && effect.type.priority()
                <= GeburahVisualEffectType.BODIES_EVERYWHERE_HIT.priority()
                && minecraft.level != null) {
            Vec3 bladeDirection = GeburahBladeTrailState.recentDirection(
                    minecraft.level.getGameTime(), 2.0D);
            if (bladeDirection != null) visualDirection = bladeDirection;
        }
        float screenX = (float) visualDirection.dot(right);
        float screenY = (float) (-visualDirection.y
                - visualDirection.dot(forward) * 0.24D);
        if (screenX * screenX + screenY * screenY < 1.0E-4F) {
            return new float[]{0.0F, 1.0F};
        }
        float length = Mth.sqrt(screenX * screenX + screenY * screenY);
        return new float[]{screenX / length, screenY / length};
    }

    public static float shaderProximity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return 0.0F;
        double nearest = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof EntityGeburah) {
                nearest = Math.min(nearest, minecraft.player.distanceTo(entity));
            }
        }
        if (!Double.isFinite(nearest) || nearest == Double.MAX_VALUE) return 0.0F;
        return Mth.clamp(1.0F - (float) nearest / 96.0F, 0.0F, 1.0F);
    }

    private static ActiveEffect dominant() {
        ActiveEffect stage = dominant(true);
        return stage != null ? stage : dominant(false);
    }

    private static ActiveEffect dominant(boolean stageEffect) {
        ActiveEffect result = null;
        for (ActiveEffect effect : ACTIVE.values()) {
            boolean isStage = effect.type.priority()
                    > GeburahVisualEffectType.BODIES_EVERYWHERE_HIT.priority();
            if (isStage != stageEffect) continue;
            if (!stageEffect && (result == null || effect.sequence > result.sequence)) {
                result = effect;
            } else if (stageEffect && (result == null
                    || effect.type.priority() > result.type.priority()
                    || effect.type.priority() == result.type.priority()
                    && effect.intensity > result.intensity)) {
                result = effect;
            }
        }
        return result;
    }

    private static boolean isWeaponHit(GeburahVisualEffectType type) {
        return type == GeburahVisualEffectType.SLASH_HIT
                || type == GeburahVisualEffectType.STAB_HIT
                || type == GeburahVisualEffectType.VERTICAL_SLASH_HIT;
    }

    private static boolean isAttackEffect(GeburahVisualEffectType type) {
        return type == GeburahVisualEffectType.COMBO_HIT
                || type == GeburahVisualEffectType.WILL_OF_BATTLE_HIT
                || type == GeburahVisualEffectType.BLOOD_MIST_HIT
                || type == GeburahVisualEffectType.BODIES_EVERYWHERE_HIT
                || isWeaponHit(type);
    }

    static VisualProfile visualProfile(GeburahVisualEffectType type) {
        return switch (type) {
            case COMBO_HIT, SLASH_HIT -> VisualProfile.BLADE_TRAIL;
            case STAB_HIT -> VisualProfile.THRUST_BLUR;
            case VERTICAL_SLASH_HIT -> VisualProfile.GROUND_CRUSH;
            case WILL_OF_BATTLE_HIT -> VisualProfile.DEPTH_COMPRESSION;
            case BLOOD_MIST_HIT -> VisualProfile.BLOOD_PRESSURE;
            case BODIES_EVERYWHERE_HIT -> VisualProfile.HORIZONTAL_SLICE;
            default -> VisualProfile.PHASE_EVENT;
        };
    }

    static int bloodMistTier(float intensity) {
        if (intensity < 0.3F) return 0;
        return intensity < 0.75F ? 1 : 2;
    }

    static boolean shouldRenderAttackFallback(Mode selectedMode,
                                              boolean postProcessorReady) {
        return selectedMode != Mode.FULL || !postProcessorReady;
    }

    enum VisualProfile {
        BLADE_TRAIL,
        THRUST_BLUR,
        GROUND_CRUSH,
        DEPTH_COMPRESSION,
        BLOOD_PRESSURE,
        HORIZONTAL_SLICE,
        PHASE_EVENT
    }

    private static float progress(ActiveEffect effect, float partialTick) {
        float elapsed = effect.totalTicks - effect.remainingTicks
                + Mth.clamp(partialTick, 0.0F, 1.0F);
        return Mth.clamp(elapsed / Math.max(1.0F, effect.totalTicks), 0.0F, 1.0F);
    }

    private static float[] projectedOrigin(Vec3 origin) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 delta = origin.subtract(camera.getPosition());
        Vec3 horizontal = delta.multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-6D) return new float[]{0.5F, 0.5F};
        horizontal = horizontal.normalize();
        double yaw = Math.toRadians(camera.getYRot());
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        double depth = horizontal.dot(forward);
        float x = Mth.clamp(0.5F + (float) horizontal.dot(right) * 0.44F,
                0.06F, 0.94F);
        float y = Mth.clamp(0.5F - (float) (delta.y / Math.max(4.0D, delta.length())) * 0.34F,
                0.12F, 0.88F);
        if (depth < -0.15D) {
            x = horizontal.dot(right) < 0.0D ? 0.06F : 0.94F;
        }
        return new float[]{x, y};
    }

    private static void renderEgoRelease(GuiGraphics graphics, int x, int y,
                                         float progress, float intensity) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float collapse = progress < 0.42F ? 1.0F - progress / 0.42F
                : (progress - 0.42F) / 0.58F;
        int radiusX = Math.max(10, Math.round((width * 0.08F + width * 0.45F * collapse)));
        int radiusY = Math.max(7, Math.round((height * 0.06F + height * 0.42F * collapse)));
        int alpha = Math.round((1.0F - Math.abs(progress * 2.0F - 1.0F))
                * 150.0F * intensity);
        for (int layer = 0; layer < 5; layer++) {
            int inset = layer * 4;
            outline(graphics, x - radiusX + inset, y - radiusY + inset,
                    x + radiusX - inset, y + radiusY - inset,
                    1 + layer % 2, (Mth.clamp(alpha - layer * 17, 0, 255) << 24) | 0xA70D18);
        }
        float burst = Mth.clamp((progress - 0.38F) / 0.62F, 0.0F, 1.0F);
        for (int ring = 0; ring < 3; ring++) {
            int ringX = Math.round(width * (0.08F + burst * (0.18F + ring * 0.09F)));
            int ringY = Math.round(height * (0.06F + burst * (0.16F + ring * 0.08F)));
            int ringAlpha = Mth.clamp(Math.round((1.0F - burst) * 135.0F
                    * intensity) - ring * 24, 0, 255);
            outline(graphics, x - ringX, y - ringY, x + ringX, y + ringY,
                    ring == 0 ? 2 : 1, (ringAlpha << 24) | 0xD51A24);
        }
        int edgeAlpha = Math.round(30.0F * intensity * (1.0F - progress));
        int slice = Math.max(2, height / 90);
        graphics.fill(0, 0, width, slice, (edgeAlpha << 24) | 0x8A0710);
        graphics.fill(0, height - slice, width, height,
                (edgeAlpha << 24) | 0x8A0710);
        renderFrameCuts(graphics, progress, intensity, 0xC51A24);
    }

    private static void renderExhaustion(GuiGraphics graphics, int x, int y,
                                         float progress, float intensity, int seed) {
        renderCracks(graphics, x, y, progress, intensity, seed, 0x88E9B949);
        int alpha = Math.round((1.0F - progress) * 92.0F * intensity);
        int border = Math.max(2, Math.round(11.0F * (1.0F - progress)));
        graphics.fill(0, 0, graphics.guiWidth(), border, (alpha << 24) | 0x8B1218);
        graphics.fill(0, graphics.guiHeight() - border, graphics.guiWidth(),
                graphics.guiHeight(), (alpha << 24) | 0x8B1218);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        for (int band = 0; band < 7; band++) {
            int h = hash(seed ^ 0x52A19B, band);
            int bandY = Math.floorMod(h, Math.max(1, height));
            int bandWidth = Math.round(width * (0.12F + (h & 255) / 255.0F * 0.25F));
            int drift = Math.round(progress * width * 0.2F) * ((h & 1) == 0 ? 1 : -1);
            int bandX = Mth.clamp(x + drift - bandWidth / 2, 0, Math.max(0, width - bandWidth));
            int bandAlpha = Mth.clamp(Math.round((1.0F - progress) * 86.0F
                    * intensity) - band * 5, 0, 255);
            graphics.fill(bandX, bandY, bandX + bandWidth, bandY + 2,
                    (bandAlpha << 24) | ((band & 1) == 0 ? 0xE9B949 : 0x9A1019));
        }
    }

    private static void renderDefeat(GuiGraphics graphics, int x, int y,
                                     float progress, float intensity, int seed) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int rays = 14;
        for (int i = 0; i < rays; i++) {
            int hash = hash(seed, i);
            int edgeX = Math.floorMod(hash, Math.max(1, width));
            int edgeY = (hash & 1) == 0 ? 0 : height;
            float reverse = 1.0F - progress;
            int px = Math.round(Mth.lerp(reverse, x, edgeX));
            int py = Math.round(Mth.lerp(reverse, y, edgeY));
            int alpha = Math.round(145.0F * (1.0F - progress) * intensity);
            graphics.fill(px - 1, py - 5, px + 2, py + 5, (alpha << 24) | 0xB20B17);
        }
        if (progress > 0.52F) {
            renderCracks(graphics, x, y, (progress - 0.52F) / 0.48F,
                    intensity, seed ^ 0x6D2B79F5, 0x99D41B2B);
        }
        int frameAlpha = Mth.clamp(Math.round((1.0F - progress) * 118.0F * intensity),
                0, 255);
        for (int echo = 0; echo < 4; echo++) {
            int spreadX = Math.round((1.0F - progress) * (18 + echo * 16));
            int spreadY = Math.round((1.0F - progress) * (10 + echo * 9));
            outline(graphics, x - spreadX, y - spreadY, x + spreadX, y + spreadY,
                    1, (Mth.clamp(frameAlpha - echo * 22, 0, 255) << 24) | 0xA90B18);
        }
    }

    private static void renderPostureBreak(GuiGraphics graphics, int x, int y,
                                            float progress, float intensity, int seed) {
        int impactAlpha = Mth.clamp(Math.round((1.0F - progress) * (1.0F - progress)
                * 86.0F * intensity), 0, 255);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                (impactAlpha << 24) | 0x160607);
        renderCracks(graphics, x, y, progress, intensity, seed,
                POSTURE_BREAK_CRACK_COLOR);
        float burst = Mth.clamp(progress * 1.8F, 0.0F, 1.0F);
        int alpha = Mth.clamp(Math.round((1.0F - progress) * 132.0F * intensity), 0, 255);
        for (int shard = 0; shard < 12; shard++) {
            int h = hash(seed ^ 0x19C43A, shard);
            double angle = shard * Math.PI * 2.0D / 12.0D + (h & 127) / 420.0D;
            int distance = Math.round((18 + (h >>> 8 & 31)) * burst);
            int shardX = x + (int) Math.round(Math.cos(angle) * distance);
            int shardY = y + (int) Math.round(Math.sin(angle) * distance);
            float degrees = (float) Math.toDegrees(angle) + 24.0F;
            drawRotatedBar(graphics, shardX, shardY, degrees,
                    7 + (h & 7), 2, (alpha << 24)
                            | (shard % 3 == 0 ? 0xFFF1C2 : POSTURE_BREAK_SHARD_RGB));
        }
        int ringX = Math.round(graphics.guiWidth() * 0.08F * burst);
        int ringY = Math.round(graphics.guiHeight() * 0.07F * burst);
        outline(graphics, x - ringX, y - ringY, x + ringX, y + ringY,
                2, (alpha << 24) | 0xFFE6A1);
        int aftershockX = Math.round(graphics.guiWidth() * 0.12F * burst);
        int aftershockY = Math.round(graphics.guiHeight() * 0.10F * burst);
        int aftershockAlpha = Math.round(alpha * 0.62F);
        outline(graphics, x - aftershockX, y - aftershockY,
                x + aftershockX, y + aftershockY,
                1, (aftershockAlpha << 24) | 0x8F151A);
    }

    private static void renderCracks(GuiGraphics graphics, int x, int y,
                                     float progress, float intensity,
                                     int seed, int baseColor) {
        int rays = 11;
        int maxLength = Math.round(Math.min(graphics.guiWidth(), graphics.guiHeight())
                * 0.38F * Mth.clamp(progress * 2.2F, 0.0F, 1.0F));
        int alpha = Math.round(((baseColor >>> 24) & 0xFF)
                * (1.0F - progress * 0.58F) * intensity);
        int color = (Mth.clamp(alpha, 0, 255) << 24) | (baseColor & 0xFFFFFF);
        for (int ray = 0; ray < rays; ray++) {
            double angle = ray * Math.PI * 2.0D / rays
                    + (hash(seed, ray) & 255) / 255.0D * 0.35D;
            int lastX = x;
            int lastY = y;
            for (int segment = 1; segment <= 5; segment++) {
                double distance = maxLength * segment / 5.0D;
                int jitter = (hash(seed ^ ray, segment) & 7) - 3;
                int nextX = x + (int) Math.round(Math.cos(angle) * distance) + jitter;
                int nextY = y + (int) Math.round(Math.sin(angle) * distance) - jitter;
                drawSteppedLine(graphics, lastX, lastY, nextX, nextY, color);
                lastX = nextX;
                lastY = nextY;
            }
        }
    }

    private static void renderComboHit(GuiGraphics graphics, ActiveEffect effect,
                                       int x, int y, float progress, float intensity) {
        float[] direction = shaderDirection();
        float angle = (float) Math.toDegrees(Math.atan2(direction[1], direction[0]));
        int baseLength = Math.round(Math.min(graphics.guiWidth(), graphics.guiHeight())
                * (effect.intensity >= 0.45F ? 0.68F : 0.42F));
        float envelope = (float) Math.sin(progress * Math.PI);
        int alpha = Mth.clamp(Math.round(envelope * 185.0F * intensity), 0, 255);
        for (int trail = 0; trail < 5; trail++) {
            int offset = (trail - 2) * 3;
            int lag = Math.round((1.0F - progress) * (trail + 1) * 5.0F);
            int layerAlpha = Mth.clamp(alpha - trail * 27, 0, 255);
            drawRotatedBar(graphics, x - lag, y + offset, angle,
                    Math.max(12, baseLength - trail * 12), trail == 0 ? 3 : 2,
                    (layerAlpha << 24) | (trail == 0 ? 0xFF3B48 : 0x720812));
        }
        if (effect.intensity >= 0.45F) {
            int crossAlpha = Mth.clamp(alpha - 18, 0, 255);
            drawRotatedBar(graphics, x, y, angle + 74.0F,
                    Math.round(baseLength * 0.72F), 2,
                    (crossAlpha << 24) | 0xD30E20);
            int radiusX = Math.round(baseLength * progress * 0.22F);
            int radiusY = Math.max(4, Math.round(radiusX * 0.42F));
            outline(graphics, x - radiusX, y - radiusY, x + radiusX, y + radiusY,
                    1, (Mth.clamp(alpha / 2, 0, 255) << 24) | 0xFF5360);
        }
    }

    private static void renderWillOfBattleHit(GuiGraphics graphics, ActiveEffect effect,
                                               int x, int y, float progress,
                                               float intensity) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float[] direction = shaderDirection();
        float angle = (float) Math.toDegrees(Math.atan2(direction[1], direction[0]));
        float envelope = (float) Math.sin(progress * Math.PI);
        int alpha = Mth.clamp(Math.round(envelope * 190.0F * intensity), 0, 255);
        for (int frame = 0; frame < 5; frame++) {
            float convergence = Mth.clamp(progress * 1.35F - frame * 0.06F, 0.0F, 1.0F);
            int radiusX = Math.round(Mth.lerp(convergence, width * 0.42F,
                    width * (0.055F + frame * 0.012F)));
            int radiusY = Math.round(Mth.lerp(convergence, height * 0.38F,
                    height * (0.045F + frame * 0.009F)));
            outline(graphics, x - radiusX, y - radiusY, x + radiusX, y + radiusY,
                    frame == 0 ? 2 : 1,
                    (Mth.clamp(alpha - frame * 25, 0, 255) << 24) | 0x9C0B17);
        }
        int lineLength = Math.round(Math.min(width, height) * 0.86F);
        drawRotatedBar(graphics, x, y, angle, lineLength, 3,
                (alpha << 24) | 0xFF3947);
        for (int streak = 0; streak < 8; streak++) {
            int h = hash(effect.seed, streak);
            int offset = (h & 63) - 31;
            int streakLength = Math.round(lineLength * (0.18F + (h >>> 8 & 63) / 180.0F));
            drawRotatedBar(graphics, x - Math.round(direction[0] * lineLength * 0.25F),
                    y + offset, angle, streakLength, 1,
                    (Mth.clamp(alpha - 55 - streak * 7, 0, 255) << 24) | 0xBC1624);
        }
    }

    private static void renderBloodMistHit(GuiGraphics graphics, ActiveEffect effect,
                                           int x, int y, float progress,
                                           float intensity) {
        int tier = bloodMistTier(effect.intensity);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float[] direction = shaderDirection();
        float angle = (float) Math.toDegrees(Math.atan2(direction[1], direction[0]));
        float envelope = (float) Math.sin(progress * Math.PI);
        int alpha = Mth.clamp(Math.round(envelope * (105 + tier * 46) * intensity), 0, 255);
        int marks = 5 + tier * 4;
        for (int mark = 0; mark < marks; mark++) {
            int h = hash(effect.seed ^ 0x6D45A1, mark);
            boolean left = (h & 1) == 0;
            int markX = left ? Math.floorMod(h >>> 2, Math.max(1, width / 5))
                    : width - Math.floorMod(h >>> 2, Math.max(1, width / 5));
            int markY = Math.floorMod(h >>> 10, Math.max(1, height));
            drawRotatedBar(graphics, markX, markY,
                    angle + ((h >>> 18 & 31) - 15), 12 + (h >>> 23 & 31),
                    tier == 2 && mark % 3 == 0 ? 3 : 2,
                    (Mth.clamp(alpha - mark * 6, 0, 255) << 24) | 0x890713);
        }
        int tearLength = Math.round(Math.min(width, height) * (0.38F + tier * 0.18F));
        drawRotatedBar(graphics, x, y, angle, tearLength, 2 + tier,
                (alpha << 24) | 0xEF2636);
        if (tier == 2) {
            drawRotatedBar(graphics, x, y, angle + 7.0F,
                    Math.round(tearLength * 0.92F), 2,
                    (Mth.clamp(alpha - 34, 0, 255) << 24) | 0x470008);
            for (int ring = 0; ring < 2; ring++) {
                float ringProgress = Mth.clamp(progress * 1.45F - ring * 0.22F, 0.0F, 1.0F);
                int ringX = Math.round(width * ringProgress * (0.10F + ring * 0.07F));
                int ringY = Math.round(height * ringProgress * (0.09F + ring * 0.06F));
                int ringAlpha = Mth.clamp(alpha - ring * 46, 0, 255);
                outline(graphics, x - ringX, y - ringY, x + ringX, y + ringY,
                        ring == 0 ? 2 : 1, (ringAlpha << 24) | 0xD61B29);
            }
        }
    }

    private static void renderBodiesEverywhereHit(GuiGraphics graphics, ActiveEffect effect,
                                                   int x, int y, float progress,
                                                   float intensity) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float[] direction = shaderDirection();
        float angle = (float) Math.toDegrees(Math.atan2(direction[1], direction[0])) * 0.18F;
        float firstWave = (float) Math.sin(Mth.clamp(progress * 1.35F, 0.0F, 1.0F)
                * Math.PI);
        float secondWave = progress < 0.38F ? 0.0F
                : (float) Math.sin((progress - 0.38F) / 0.62F * Math.PI);
        int alpha = Mth.clamp(Math.round((firstWave + secondWave * 0.62F)
                * 185.0F * intensity), 0, 255);
        int length = Math.round(Math.max(width, height) * 1.18F);
        for (int slice = 0; slice < 7; slice++) {
            int h = hash(effect.seed ^ 0x38F21C, slice);
            int offsetY = (slice - 3) * Math.max(5, height / 24)
                    + ((h >>> 8 & 15) - 7);
            int displacement = Math.round((1.0F - progress) * ((h & 1) == 0 ? 18 : -18));
            drawRotatedBar(graphics, x + displacement, y + offsetY, angle,
                    length - slice * 13, slice % 3 == 0 ? 5 : 3,
                    (Mth.clamp(alpha - slice * 17, 0, 255) << 24)
                            | (slice % 3 == 0 ? 0x260006 : 0xB10B19));
            drawRotatedBar(graphics, x - displacement / 2, y + offsetY + 4, angle,
                    Math.round(length * 0.68F), 1,
                    (Mth.clamp(alpha - 48 - slice * 9, 0, 255) << 24) | 0xF22A38);
        }
    }

    private static void renderHitReaction(GuiGraphics graphics, ActiveEffect effect,
                                          float progress, float intensity) {
        float severity = Mth.clamp(Math.max(effect.hitSeverity, intensity * 0.18F),
                0.0F, 1.25F);
        if (severity <= 0.01F) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float envelope = (float) Math.sin(progress * Math.PI);
        int alpha = Mth.clamp(Math.round(envelope * severity * 92.0F), 0, 150);
        int thickness = Math.max(2, Math.round(Math.min(width, height)
                * (0.008F + severity * 0.018F)));
        graphics.fill(0, 0, width, thickness, (alpha << 24) | 0x8D0710);
        graphics.fill(0, height - thickness, width, height, (alpha << 24) | 0x8D0710);
        graphics.fill(0, thickness, thickness, height - thickness,
                (alpha << 24) | 0x64040B);
        graphics.fill(width - thickness, thickness, width, height - thickness,
                (alpha << 24) | 0x64040B);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getMaxHealth() > 0.0F
                && minecraft.player.getHealth() / minecraft.player.getMaxHealth() <= 0.25F) {
            renderCracks(graphics, width / 2, height / 2, progress,
                    severity * 0.42F, effect.seed ^ 0x4B1D0A, 0x799B0A14);
        }
    }

    private static void renderEgoDangerFrame(GuiGraphics graphics, float strength) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float time = CoreSuppressionClientState.getEffectTime(0.0F);
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 1.45F);
        int alpha = Mth.clamp(Math.round((34.0F + pulse * 28.0F) * strength), 0, 92);
        int inset = Math.max(8, Math.min(width, height) / 38);
        int cornerX = Math.max(36, width / 7);
        int cornerY = Math.max(24, height / 7);
        int color = (alpha << 24) | 0xD10D1B;
        graphics.fill(inset, inset, inset + cornerX, inset + 2, color);
        graphics.fill(inset, inset, inset + 2, inset + cornerY, color);
        graphics.fill(width - inset - cornerX, inset, width - inset, inset + 2, color);
        graphics.fill(width - inset - 2, inset, width - inset, inset + cornerY, color);
        graphics.fill(inset, height - inset - 2, inset + cornerX, height - inset, color);
        graphics.fill(inset, height - inset - cornerY, inset + 2, height - inset, color);
        graphics.fill(width - inset - cornerX, height - inset - 2,
                width - inset, height - inset, color);
        graphics.fill(width - inset - 2, height - inset - cornerY,
                width - inset, height - inset, color);
        int scanX = inset + Math.floorMod((int) (time * 42.0F),
                Math.max(1, width - inset * 2 - 30));
        graphics.fill(scanX, inset + 5, scanX + 26, inset + 7,
                (Mth.clamp(alpha + 26, 0, 128) << 24) | 0xF22B39);
    }

    private static void renderFrameCuts(GuiGraphics graphics, float progress,
                                        float intensity, int rgb) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int alpha = Mth.clamp(Math.round((float) Math.sin(progress * Math.PI)
                * 86.0F * intensity), 0, 128);
        for (int cut = 0; cut < 6; cut++) {
            int y = (cut + 1) * height / 7;
            int length = Math.round(width * (0.08F + cut * 0.018F));
            int x = (cut & 1) == 0 ? 0 : width - length;
            graphics.fill(x, y, x + length, y + 2, (alpha << 24) | rgb);
        }
    }

    private static void drawRotatedBar(GuiGraphics graphics, int x, int y,
                                       float angle, int length, int thickness, int color) {
        if (length <= 0 || thickness <= 0 || (color >>> 24) == 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.fill(-length / 2, -thickness / 2,
                length / 2, Math.max(1, thickness - thickness / 2), color);
        graphics.pose().popPose();
    }

    private static void outline(GuiGraphics graphics, int left, int top,
                                int right, int bottom, int thickness, int color) {
        if (right <= left || bottom <= top) return;
        graphics.fill(left, top, right, top + thickness, color);
        graphics.fill(left, bottom - thickness, right, bottom, color);
        graphics.fill(left, top, left + thickness, bottom, color);
        graphics.fill(right - thickness, top, right, bottom, color);
    }

    private static void drawSteppedLine(GuiGraphics graphics, int x0, int y0,
                                        int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) return;
        int stride = Math.max(1, steps / 18);
        for (int i = 0; i <= steps; i += stride) {
            float progress = i / (float) steps;
            int x = Math.round(Mth.lerp(progress, x0, x1));
            int y = Math.round(Mth.lerp(progress, y0, y1));
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static int hash(int seed, int value) {
        int hash = seed ^ value * 0x9E3779B9;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        return hash;
    }

    private static Vec3 horizontal(Vec3 direction) {
        Vec3 horizontal = direction.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : horizontal.normalize();
    }

    private static final class ActiveEffect {
        private final GeburahVisualEffectType type;
        private Vec3 origin;
        private Vec3 direction;
        private float intensity;
        private float hitSeverity;
        private boolean ego;
        private int totalTicks;
        private int remainingTicks;
        private int seed;
        private long sequence;

        private ActiveEffect(GeburahVisualEffectType type, Vec3 origin, Vec3 direction,
                             float intensity, float hitSeverity,
                             boolean ego,
                             int totalTicks, int remainingTicks, int seed, long sequence) {
            this.type = type;
            this.origin = origin;
            this.direction = direction;
            this.intensity = intensity;
            this.hitSeverity = hitSeverity;
            this.ego = ego;
            this.totalTicks = totalTicks;
            this.remainingTicks = remainingTicks;
            this.seed = seed;
            this.sequence = sequence;
        }
    }
}
