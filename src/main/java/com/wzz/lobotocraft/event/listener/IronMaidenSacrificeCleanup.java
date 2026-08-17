package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.EntityIronMaiden;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Session and target-side recovery for interrupted We Can Change Anything use. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class IronMaidenSacrificeCleanup {
    private static final long RECOVERY_GRACE_TICKS = 100L;

    private IronMaidenSacrificeCleanup() {
    }

    @SubscribeEvent
    public static void onOperatorDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) return;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityIronMaiden ironMaiden
                    && ironMaiden.endUsageForOperatorDeath(player)) {
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || target.tickCount % 20 != 0
                || !(target.level() instanceof ServerLevel level)) return;

        CompoundTag data = target.getPersistentData();
        if (!data.getBoolean(EntityIronMaiden.SACRIFICE_LOCKED)) return;
        long lockedAt = data.getLong(EntityIronMaiden.SACRIFICE_LOCK_TIME);
        if (level.getGameTime() - lockedAt < RECOVERY_GRACE_TICKS) return;

        if (!data.hasUUID(EntityIronMaiden.SACRIFICE_MACHINE)) {
            EntityIronMaiden.restoreSacrificeState(target);
            return;
        }

        UUID machineUuid = data.getUUID(EntityIronMaiden.SACRIFICE_MACHINE);
        Entity machine = level.getEntity(machineUuid);
        if (!(machine instanceof EntityIronMaiden ironMaiden)
                || !ironMaiden.isAlive()
                || !ironMaiden.holdsSacrifice(target.getUUID())) {
            EntityIronMaiden.restoreSacrificeState(target);
        }
    }
}
