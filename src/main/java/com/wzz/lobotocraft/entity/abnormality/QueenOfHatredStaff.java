package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Original WorkerModel semantics: employees and clerks are both facility staff. */
final class QueenOfHatredStaff {
    private QueenOfHatredStaff() {
    }

    static boolean isStaff(Entity entity) {
        return entity instanceof ServerPlayer || entity instanceof EntityClerk;
    }

    static boolean isAttackableStaff(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return player.isAlive() && !player.isRemoved() && !player.isCreative() && !player.isSpectator();
        }
        return entity instanceof EntityClerk clerk && clerk.isAlive() && !clerk.isRemoved();
    }

    static List<LivingEntity> liveStaff(ServerLevel level) {
        List<LivingEntity> staff = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living && isStaff(living)
                    && living.isAlive() && !living.isRemoved()
                    && (!(living instanceof ServerPlayer player) || !player.isSpectator())) {
                staff.add(living);
            }
        }
        staff.sort(Comparator.comparing(Entity::getUUID));
        return staff;
    }

    static int liveStaffCount(ServerLevel level) {
        return liveStaff(level).size();
    }

    static ServerPlayer findPlayer(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) return player;
        player = EmployeeRuntimeManager.activePlayer(uuid);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof ServerPlayer levelPlayer) return levelPlayer;
        }
        return null;
    }

    static void heal(LivingEntity target, float health, float mental) {
        if (target == null || !target.isAlive()) return;
        if (health > 0.0F) target.heal(health);
        if (mental > 0.0F && target instanceof ServerPlayer player) {
            MentalValueUtil.addMentalValue(player, mental);
        }
    }

    static void healFully(LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        target.setHealth(target.getMaxHealth());
        if (target instanceof ServerPlayer player) {
            MentalValueUtil.setMentalValue(player, MentalValueUtil.getEffectiveMaxMentalValue(player));
        }
    }

    static void healAllFully(ServerLevel level) {
        liveStaff(level).forEach(QueenOfHatredStaff::healFully);
    }
}
