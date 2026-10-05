package com.wzz.lobotocraft.client.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.config.ClientVisualConfig;
import com.wzz.lobotocraft.core_suppression.geburah.*;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahSpecialVisuals.*;
import com.wzz.lobotocraft.entity.EntityGeburah;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** Event-driven world-space special skills, with a bounded local replay of the old particle style. */
@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GeburahSpecialEffects {
    private static final GeburahSpecialVisualState STATE = new GeburahSpecialVisualState();
    private static final Map<UUID, Integer> LEGACY_TICKS = new HashMap<>();
    private static final Set<GeburahSpecialVisualState.Accent> PLAYED = new HashSet<>();
    private static final Map<UUID, Focus> FOCUS = new HashMap<>();
    private static final Map<UUID, Double> BLADE_SAMPLES = new HashMap<>();
    private static ClientLevel owner;
    private static int particleBudget;
    private static final int PARTICLE_BUDGET = 192;

    private GeburahSpecialEffects() {}

    public static boolean enhanced() { return ClientVisualConfig.GEBURAH_SPECIAL_VISUALS.get(); }

    public static void accept(ResourceLocation dimension, Snapshot snapshot) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(dimension)) return;
        ensureOwner(level);
        var previous = frame(snapshot.caster());
        STATE.accept(snapshot, level.getGameTime());
        if (snapshot.event() == Event.STOP && previous != null
                && previous.snapshot().castId() == snapshot.castId()
                && GeburahBladeTrailState.capturedEntityId() == snapshot.entityId()) {
            var entity = level.getEntity(snapshot.entityId());
            if (entity != null && entity.getUUID().equals(snapshot.caster())) GeburahBladeTrailState.clear();
        }
    }

    private static void ensureOwner(ClientLevel level) {
        if (owner != level) { clear(); owner = level; }
    }
    public static void clear() {
        STATE.clear(); LEGACY_TICKS.clear(); PLAYED.clear(); FOCUS.clear(); BLADE_SAMPLES.clear(); owner = null;
    }
    public static void clearAccents() {
        STATE.clearAccents(); PLAYED.clear(); GeburahBladeTrailState.clear();
        FOCUS.clear(); BLADE_SAMPLES.clear();
    }
    public static GeburahSpecialVisualState.Frame frame(UUID caster) {
        var level = Minecraft.getInstance().level;
        if (level == null || owner != level) return null;
        return STATE.frames(level.getGameTime()).stream()
                .filter(frame -> frame.snapshot().caster().equals(caster)).findFirst().orElse(null);
    }
    public static boolean captureBlade(UUID caster) {
        if (!canCapture(caster)) return false;
        var frame = frame(caster);
        var mc = Minecraft.getInstance();
        return frame != null && GeburahSpecialVisuals.bladeQuality(mc.options.particles().get().getId(),
                mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(frame.snapshot().origin()))
                && GeburahSpecialVisuals.bladeWindow(frame.snapshot().skill(), frame.elapsed());
    }
    public static boolean allowCapturedTrail(int entityId) {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        var entity = level.getEntity(entityId);
        if (!enhanced() || entity == null || frame(entity.getUUID()) == null) return true;
        return captureBlade(entity.getUUID());
    }
    public static boolean canCapture(UUID caster) {
        if (!enhanced() || GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.OFF) return false;
        var frame = frame(caster);
        if (frame == null) return false;
        var entity = Minecraft.getInstance().level.getEntity(frame.snapshot().entityId());
        return entity != null && entity.getUUID().equals(caster) && withinView(entity.position());
    }
    public static boolean sampleBlade(UUID caster, double sampleTime) {
        if (!captureBlade(caster)) return false;
        double minimum = Minecraft.getInstance().options.particles().get().getId() == 1 ? 0.5 : 0;
        if (sampleTime - BLADE_SAMPLES.getOrDefault(caster, Double.NEGATIVE_INFINITY) < minimum) return false;
        BLADE_SAMPLES.put(caster, sampleTime);
        return true;
    }
    public static void captureFocus(UUID caster, Vec3 position, double sampleTime) {
        if (canCapture(caster) && GeburahSpecialVisuals.worldPoint(position))
            FOCUS.put(caster, new Focus(position, sampleTime));
    }
    private static Vec3 focus(UUID caster, double now) {
        var focus = FOCUS.get(caster);
        return focus == null || now - focus.sampleTime > 2 ? null : focus.position;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        ensureOwner(level);
        if (level == null || mc.isPaused()) return;
        particleBudget = PARTICLE_BUDGET;
        long now = level.getGameTime();
        if (GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.OFF) {
            STATE.clearAccents();
            PLAYED.clear();
        }
        for (var frame : STATE.frames(now)) {
            Snapshot s = frame.snapshot();
            var entity = level.getEntity(s.entityId());
            if (entity == null && frame.elapsed() < 10) continue;
            if (!(entity instanceof EntityGeburah boss) || !boss.getUUID().equals(s.caster())
                    || !boss.isAlive() || boss.syncedPhase() == GeburahPhase.DEFEATED) {
                STATE.remove(s.caster()); LEGACY_TICKS.remove(s.caster()); continue;
            }
            if (GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.OFF) continue;
            if (!enhanced() && s.skill().major() != null
                    && withinView(s.origin())) {
                int elapsed = (int) frame.elapsed();
                if (LEGACY_TICKS.getOrDefault(s.caster(), -1) != elapsed) {
                    LEGACY_TICKS.put(s.caster(), elapsed);
                    GeburahMajorAttackEffects.spawnFrame(GeburahSpecialEffects::particles,
                            s.skill().major(), elapsed, s.origin(), s.target(), s.forward());
                }
            }
        }
        var accents = STATE.accents(now);
        PLAYED.retainAll(accents);
        for (var accent : accents) {
            if (!PLAYED.add(accent) || !withinView(accent.snapshot().origin())
                    || GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.OFF) continue;
            Snapshot s = accent.snapshot();
            if (!enhanced()) {
                if (s.event() == Event.DASH) {
                    var entity = level.getEntity(s.entityId());
                    double height = entity == null ? 2.0 : entity.getBbHeight();
                    GeburahMajorAttackEffects.spawnWill(GeburahSpecialEffects::particles, s.skill().ego(),
                            s.target(), s.origin(), height);
                } else if (s.skill().major() != null) {
                    GeburahMajorAttackEffects.spawnImpact(GeburahSpecialEffects::particles, s.skill().major(),
                            s.skill().major().cues().get(s.cueIndex()), s.origin(), s.target(), s.forward());
                }
            } else if (s.event() == Event.CUE && (s.skill() == Skill.BODIES
                    || s.skill() == Skill.EGO_BLOOD && s.cueIndex() == s.skill().major().cues().size() - 1)) {
                // Short dust is secondary to the mesh; no broad per-frame particle broadcasts.
                int puffs = mc.options.particles().get().getId() == 2 ? 2 : 6;
                particles(ParticleTypes.CLOUD, s.origin().x + s.forward().x * 5,
                        s.origin().y + 0.1, s.origin().z + s.forward().z * 5,
                        puffs, 1.2, 0.1, 1.2, 0.035);
            }
        }
        Set<UUID> active = new HashSet<>();
        STATE.frames(now).forEach(frame -> active.add(frame.snapshot().caster()));
        LEGACY_TICKS.keySet().retainAll(active);
        FOCUS.keySet().retainAll(active);
        BLADE_SAMPLES.keySet().retainAll(active);
    }

    private static boolean withinView(Vec3 point) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(point)
                <= GeburahSpecialVisuals.VIEW_DISTANCE * GeburahSpecialVisuals.VIEW_DISTANCE;
    }
    private static void particles(ParticleOptions type, double x, double y, double z, int count,
                                  double dx, double dy, double dz, double speed) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int quality = mc.options.particles().get().getId();
        int allowed = Math.min(particleBudget, quality == 2 ? Math.min(count, 2)
                : quality == 1 ? Math.max(1, count / 2) : count);
        particleBudget -= allowed;
        for (int i = 0; i < allowed; i++) {
            var random = mc.level.random;
            mc.level.addParticle(type, x + random.nextGaussian() * dx, y + random.nextGaussian() * dy,
                    z + random.nextGaussian() * dz, random.nextGaussian() * speed,
                    random.nextGaussian() * speed, random.nextGaussian() * speed);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !enhanced()
                || GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.OFF) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || owner != mc.level) return;
        long tick = mc.level.getGameTime();
        var frames = STATE.frames(tick);
        var accents = STATE.accents(tick);
        if (frames.isEmpty() && accents.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();
        double now = tick + event.getPartialTick();
        float strength = GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.FULL ? 1 : 0.65F;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            var tess = Tesselator.getInstance();
            var buffer = tess.getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (var frame : frames) {
                Snapshot s = frame.snapshot();
                int segments = GeburahSpecialVisuals.qualitySegments(mc.options.particles().get().getId(),
                        camera.distanceToSqr(s.origin()));
                if (segments == 0 || mc.level.getEntity(s.entityId()) == null) continue;
                double elapsed = frame.elapsed() + event.getPartialTick();
                Vec3 side = side(s.forward());
                if (s.skill() == Skill.BODIES && elapsed < s.skill().major().cues().get(0).tick()) {
                    float charge = (float) (elapsed / s.skill().major().cues().get(0).tick());
                    Vec3 base = s.origin().add(0, 0.035, 0);
                    Vec3 a = base.subtract(side.scale(GeburahSpecialVisuals.BODIES_WIDTH / 2));
                    Vec3 b = base.add(side.scale(GeburahSpecialVisuals.BODIES_WIDTH / 2));
                    Vec3 c = b.add(s.forward().scale(GeburahSpecialVisuals.BODIES_LENGTH));
                    Vec3 d = a.add(s.forward().scale(GeburahSpecialVisuals.BODIES_LENGTH));
                    float alpha = (0.10F + charge * 0.25F) * strength;
                    strip(buffer, matrix, camera, a, b, s.forward(), 0.035, 0.45F, 0.01F, 0.02F, alpha);
                    strip(buffer, matrix, camera, b, c, side, 0.035, 0.45F, 0.01F, 0.02F, alpha);
                    strip(buffer, matrix, camera, c, d, s.forward(), 0.035, 0.45F, 0.01F, 0.02F, alpha);
                    strip(buffer, matrix, camera, d, a, side, 0.035, 0.45F, 0.01F, 0.02F, alpha);
                    // A small converging blade-side accent, not a screen-wide flashing overlay.
                    Vec3 bladeFocus = focus(s.caster(), now);
                    for (int i = 0; bladeFocus != null && i < 3; i++) {
                        Vec3 from = bladeFocus.add(side.scale((i - 1) * (1.2 - charge)))
                                .add(0, 0.7 * (1 - charge), 0);
                        strip(buffer, matrix, camera, from, bladeFocus, side, 0.035,
                                0.6F, 0.01F, 0.02F, alpha);
                    }
                } else if (s.skill().major() == null && elapsed < GeburahSpecialController.WINDUP_TICKS) {
                    Vec3 blade = focus(s.caster(), now);
                    if (blade == null) continue;
                    strip(buffer, matrix, camera, blade, blade.add(s.forward().scale(0.5)),
                            side, 0.055, 0.7F, 0.01F, 0.02F, 0.35F * strength);
                }
            }
            for (var accent : accents) {
                Snapshot s = accent.snapshot();
                int segments = GeburahSpecialVisuals.qualitySegments(mc.options.particles().get().getId(),
                        camera.distanceToSqr(s.origin()));
                if (segments == 0) continue;
                float p = (float) ((now - accent.start()) / accent.duration());
                if (p < 0 || p >= 1) continue;
                float alpha = (1 - p) * (1 - p) * strength;
                Vec3 side = side(s.forward());
                if (s.event() == Event.DASH) {
                    Vec3 start = s.target().add(0, 1.0, 0), end = s.origin().add(0, 1.0, 0);
                    Vec3 direction = end.subtract(start);
                    if (direction.lengthSqr() < 0.001) continue;
                    Vec3 vertical = new Vec3(0, 1, 0);
                    strip(buffer, matrix, camera, start, end, vertical, 0.19,
                            0.10F, 0.001F, 0.005F, alpha * 0.6F);
                    strip(buffer, matrix, camera, start, end, vertical, 0.035,
                            0.95F, 0.07F, 0.05F, alpha * 0.8F);
                    strip(buffer, matrix, camera, start.add(0, -0.6, 0), start.add(0, 0.8, 0),
                            side, 0.22, 0.22F, 0.002F, 0.01F, alpha * 0.45F);
                } else if (s.skill() == Skill.BODIES || s.skill() == Skill.EGO_BLOOD
                        && s.cueIndex() == s.skill().major().cues().size() - 1) {
                    double length = s.skill() == Skill.BODIES ? GeburahSpecialVisuals.BODIES_LENGTH : GeburahSpecialVisuals.BLOOD_LENGTH;
                    double width = s.skill() == Skill.BODIES ? GeburahSpecialVisuals.BODIES_WIDTH : GeburahSpecialVisuals.BLOOD_WIDTH;
                    for (int layer = 0; layer < 3; layer++) {
                        float layerAlpha = alpha * (layer == 0 ? 0.9F : 0.28F);
                        double lift = layer * 0.18;
                        for (int i = 0; i < segments; i++) {
                            Vec3 a = crescent(s.origin(), s.forward(), side, i * Math.PI / segments, length, width, lift);
                            Vec3 b = crescent(s.origin(), s.forward(), side, (i + 1) * Math.PI / segments, length, width, lift);
                            strip(buffer, matrix, camera, a, b, new Vec3(0, 1, 0), layer == 0 ? 0.23 : 0.05,
                                    layer == 0 ? 0.25F : 0.65F, 0.003F, 0.012F, layerAlpha);
                            if (layer == 0) strip(buffer, matrix, camera, a.add(0, 0.17, 0), b.add(0, 0.17, 0),
                                    new Vec3(0, 1, 0), 0.025, 1, 0.16F, 0.10F, alpha);
                        }
                    }
                } else {
                    Vec3 center = s.target().add(0, 1.0, 0);
                    double radius = 1.0;
                    if (s.skill().major() != null) {
                        var cue = s.skill().major().cues().get(s.cueIndex());
                        radius = cue.maxDamage() >= 30 ? 2.4 : cue.maxDamage() >= 12 ? 1.7 : 0.8;
                    }
                    Vec3 axis = side.add(0, s.cueIndex() % 2 == 0 ? 0.65 : -0.65, 0).normalize();
                    Vec3 across = new Vec3(-axis.y * side.x, 1, -axis.y * side.z).normalize();
                    strip(buffer, matrix, camera, center.subtract(axis.scale(radius)), center.add(axis.scale(radius)),
                            across, 0.13, 0.25F, 0.002F, 0.01F, alpha);
                    strip(buffer, matrix, camera, center.subtract(axis.scale(radius)), center.add(axis.scale(radius)),
                            across, 0.025, 0.95F, 0.10F, 0.07F, alpha * 0.75F);
                }
                if (s.event() == Event.CUE && s.connected()) {
                    // Bounded representative contact also applies to area cues; strike mesh stays independent.
                    Vec3 contact = s.target().add(0, 1.0, 0);
                    strip(buffer, matrix, camera, contact.add(0, -0.32, 0), contact.add(0, 0.32, 0),
                            side, 0.05, 1, 0.55F, 0.40F, alpha);
                    strip(buffer, matrix, camera, contact.subtract(side.scale(0.2)), contact.add(side.scale(0.2)),
                            new Vec3(0, 1, 0), 0.025, 1, 0.30F, 0.20F, alpha);
                }
            }
            tess.end();
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }
    private static Vec3 side(Vec3 forward) { return new Vec3(-forward.z, 0, forward.x); }
    private record Focus(Vec3 position, double sampleTime) {}
    private static Vec3 crescent(Vec3 origin, Vec3 forward, Vec3 side, double a, double length, double width, double lift) {
        return origin.add(forward.scale(Math.sin(a) * length)).add(side.scale(-Math.cos(a) * width * 0.5))
                .add(0, 0.55 + Math.sin(a * 2) * 0.2 + lift, 0);
    }
    private static void strip(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, Vec3 start, Vec3 end,
                              Vec3 across, double halfWidth, float r, float g, float b, float alpha) {
        Vec3 half = across.scale(halfWidth);
        vertex(buffer, matrix, start.subtract(half).subtract(camera), r, g, b, alpha);
        vertex(buffer, matrix, end.subtract(half).subtract(camera), r, g, b, alpha);
        vertex(buffer, matrix, end.add(half).subtract(camera), r, g, b, alpha);
        vertex(buffer, matrix, start.add(half).subtract(camera), r, g, b, alpha);
    }
    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float r, float g, float b, float alpha) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, alpha).endVertex();
    }
}
