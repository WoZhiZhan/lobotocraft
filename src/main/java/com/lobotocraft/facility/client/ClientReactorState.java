package com.lobotocraft.facility.client;

import com.lobotocraft.facility.FacilityPatch;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FacilityPatch.MOD_ID, value = Dist.CLIENT)
public final class ClientReactorState {
    // The reactor normally reports every 10 ticks. Keep enough headroom for an
    // integrated server or modpack tick spike without fading the whole panel.
    private static final int ACTIVE_TIMEOUT_TICKS = 60;
    private static final int REMOVE_AFTER_TICKS = 100;
    private static final float FADE_IN_STEP = 0.16F;
    private static final float FADE_OUT_STEP = 0.12F;
    private static final Map<BlockPos, Entry> REACTORS = new HashMap<>();

    private static Entry active;
    private static float visibility;

    private ClientReactorState() {
    }

    public static void update(BlockPos reactorPos, float progress, boolean failed) {
        Entry entry = REACTORS.computeIfAbsent(reactorPos.immutable(), Entry::new);
        entry.progressAtUpdate = Mth.clamp(progress, 0.0F, 1.0F);
        entry.failed = failed;
        entry.updateMillis = System.currentTimeMillis();
        entry.staleTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) return;

        Iterator<Entry> iterator = REACTORS.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            entry.staleTicks++;
            if (entry.staleTicks > REMOVE_AFTER_TICKS) iterator.remove();
        }
        active = nearestActive(minecraft);
        float target = active == null ? 0.0F : 1.0F;
        visibility = Mth.clamp(visibility + (target > visibility ? FADE_IN_STEP : -FADE_OUT_STEP),
            0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        REACTORS.clear();
        active = null;
        visibility = 0.0F;
    }

    public static float visibility() {
        return visibility;
    }

    public static boolean failed() {
        return active != null && active.failed;
    }

    public static float progress() {
        if (active == null || active.failed) return 0.0F;
        float elapsed = Math.max(0L, System.currentTimeMillis() - active.updateMillis) / 10_000.0F;
        return Mth.clamp(active.progressAtUpdate + elapsed, 0.0F, 1.0F);
    }

    private static Entry nearestActive(Minecraft minecraft) {
        if (minecraft.player == null) return null;
        BlockPos playerPos = minecraft.player.blockPosition();
        Entry nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entry entry : REACTORS.values()) {
            if (entry.staleTicks > ACTIVE_TIMEOUT_TICKS) continue;
            if (!isPlayerInEffectRange(minecraft.player.getBoundingBox(), minecraft.level, entry.pos)) {
                continue;
            }
            double distance = entry.pos.distSqr(playerPos);
            if (distance < nearestDistance) {
                nearest = entry;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean isPlayerInEffectRange(AABB playerBounds, Level level, BlockPos pos) {
        boolean aboveBlocked = isBlocked(level, pos.above());
        boolean belowBlocked = isBlocked(level, pos.below());
        double minX = pos.getX() + 0.5D - 16.0D;
        double maxX = pos.getX() + 0.5D + 16.0D;
        double minZ = pos.getZ() + 0.5D - 16.0D;
        double maxZ = pos.getZ() + 0.5D + 16.0D;
        AABB effectRange;
        if (aboveBlocked && !belowBlocked) {
            effectRange = new AABB(minX, pos.getY() - 16.0D, minZ,
                maxX, pos.getY() + 1.0D, maxZ);
        } else if (belowBlocked && !aboveBlocked) {
            effectRange = new AABB(minX, pos.getY(), minZ,
                maxX, pos.getY() + 16.0D, maxZ);
        } else {
            effectRange = new AABB(pos).inflate(16.0D);
        }
        return effectRange.intersects(playerBounds);
    }

    private static boolean isBlocked(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape((BlockGetter) level, pos).isEmpty();
    }

    private static final class Entry {
        private final BlockPos pos;
        private float progressAtUpdate;
        private long updateMillis;
        private int staleTicks;
        private boolean failed;

        private Entry(BlockPos pos) {
            this.pos = pos;
        }
    }
}
