package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.EntityBlackForestDoor;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Covers accepted entities whose chunk has not yet published its spatial index. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class BlackForestDoorAdmission {
    private static final Map<Level, Map<UUID, WeakReference<EntityBlackForestDoor>>> DOORS =
            new WeakHashMap<>();

    private BlackForestDoorAdmission() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof EntityBlackForestDoor door)) return;
        Map<UUID, WeakReference<EntityBlackForestDoor>> doors =
                DOORS.computeIfAbsent(event.getLevel(), ignored -> new HashMap<>());
        doors.values().removeIf(reference -> {
            EntityBlackForestDoor known = reference.get();
            return known == null || known.isRemoved() || !known.isAlive();
        });
        for (WeakReference<EntityBlackForestDoor> reference : doors.values()) {
            EntityBlackForestDoor other = reference.get();
            if (other != null && other != door && !other.getUUID().equals(door.getUUID())
                    && door.getBoundingBox().inflate(400.0D).intersects(other.getBoundingBox())) {
                event.setCanceled(true);
                return;
            }
        }
        doors.put(door.getUUID(), new WeakReference<>(door));
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof EntityBlackForestDoor door)) return;
        Map<UUID, WeakReference<EntityBlackForestDoor>> doors = DOORS.get(event.getLevel());
        if (doors == null) return;
        WeakReference<EntityBlackForestDoor> reference = doors.get(door.getUUID());
        if (reference != null && reference.get() == door) doors.remove(door.getUUID());
        if (doors.isEmpty()) DOORS.remove(event.getLevel());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DOORS.clear();
    }
}
