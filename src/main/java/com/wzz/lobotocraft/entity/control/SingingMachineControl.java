package com.wzz.lobotocraft.entity.control;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.EntitySingingMachine;
import com.wzz.lobotocraft.entity.abnormality.SingingMachinePolicy;
import com.wzz.lobotocraft.event.listener.PlayerControlLock;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = com.wzz.lobotocraft.ModMain.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SingingMachineControl {
    private static final String ACTIVE = "lobotocraft_singing_machine_controlled";
    private static final String SOURCE = "lobotocraft_singing_machine_source";
    private static final String TARGET = "lobotocraft_singing_machine_target";
    private static final String ATTACK_COOLDOWN = "lobotocraft_singing_machine_attack_cooldown";
    private static final String ATTACK_WINDUP = "lobotocraft_singing_machine_attack_windup";
    private static final String RETURNING = "lobotocraft_singing_machine_returning";
    private static final String LAST_TICK = "lobotocraft_singing_machine_last_tick";

    private static final UUID MAX_HEALTH_MODIFIER =
            UUID.fromString("8590d42c-89a0-41a8-9367-e901cb3e04f0");
    private static final int ATTACK_WINDUP_TICKS = SingingMachinePolicy.ATTACK_WINDUP_TICKS;
    private static final int ATTACK_COOLDOWN_TICKS = SingingMachinePolicy.ATTACK_COOLDOWN_TICKS;
    private static final double ATTACK_RANGE_SQR = 9.0D;
    private static final double SOURCE_RANGE_SQR = 9.0D;
    private static final double TARGET_SEARCH_RANGE = 64.0D;
    private static final int CLERK_GLOW_DURATION = 40;

    private SingingMachineControl() {
    }

    public static boolean isActive(LivingEntity entity) {
        return entity != null && entity.getPersistentData().getBoolean(ACTIVE);
    }

    public static boolean start(LivingEntity target, EntitySingingMachine source) {
        if (target == null || source == null || !target.isAlive()
                || HostileControlState.isActive(target)) return false;
        CompoundTag data = target.getPersistentData();
        data.putBoolean(ACTIVE, true);
        data.putUUID(SOURCE, source.getUUID());
        data.remove(TARGET);
        data.remove(RETURNING);
        data.putInt(ATTACK_COOLDOWN, 20);
        data.putInt(ATTACK_WINDUP, 0);
        data.remove(LAST_TICK);

        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(MAX_HEALTH_MODIFIER) == null) {
            maxHealth.addPermanentModifier(new AttributeModifier(MAX_HEALTH_MODIFIER,
                    "Singing Machine controlled health", target.getMaxHealth(),
                    AttributeModifier.Operation.ADDITION));
        }
        target.setHealth(target.getMaxHealth());
        if (target instanceof ServerPlayer player) {
            WorkManager.cancelWork(player, "被歌唱机的旋律控制");
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.lobotocraft.abnormality.singing_machine.controlled"));
        }
        if (target instanceof Mob mob) {
            mob.setNoAi(false);
            mob.setTarget(null);
        }
        revealControlledClerk(target);
        return true;
    }

    private static void revealControlledClerk(LivingEntity entity) {
        if (!(entity instanceof EntityClerk)) return;
        MobEffectInstance glow = entity.getEffect(MobEffects.GLOWING);
        if (glow == null || !glow.isInfiniteDuration() && glow.getDuration() < 20) {
            // Short refreshes expire naturally after recovery. Never remove a
            // longer/infinite glow that may belong to another mechanic.
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                    CLERK_GLOW_DURATION, 0, false, false));
        }
    }

    public static void clearForRecovery(LivingEntity entity) {
        if (entity == null) return;
        CompoundTag data = entity.getPersistentData();
        data.remove(ACTIVE);
        data.remove(SOURCE);
        data.remove(TARGET);
        data.remove(ATTACK_COOLDOWN);
        data.remove(ATTACK_WINDUP);
        data.remove(RETURNING);
        data.remove(LAST_TICK);
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.removeModifier(MAX_HEALTH_MODIFIER);
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        if (entity instanceof Player player) PlayerControlLock.unlock(player);
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        tickControlled(event.getEntity());
    }

    /**
     * Runs one controlled-combatant update. EmployeeServerPlayer is ticked by
     * EmployeeRuntimeManager rather than Forge's normal entity loop, so both
     * paths invoke this method. The persistent game-time guard keeps it to one
     * update even where a player receives both callbacks.
     */
    public static void tickControlled(LivingEntity attacker) {
        if (attacker == null || attacker.level().isClientSide
                || !isActive(attacker) || !attacker.isAlive()) return;
        CompoundTag data = attacker.getPersistentData();
        long gameTime = attacker.level().getGameTime();
        if (data.contains(LAST_TICK) && data.getLong(LAST_TICK) == gameTime) return;
        data.putLong(LAST_TICK, gameTime);
        EntitySingingMachine source = source(attacker);
        if (source == null) {
            clearForRecovery(attacker);
            return;
        }
        revealControlledClerk(attacker);

        decrement(data, ATTACK_COOLDOWN);
        if (data.getBoolean(RETURNING)) {
            returnToMachine(attacker, source);
            return;
        }

        LivingEntity target = storedTarget(attacker);
        if (!isValidTarget(attacker, target)) {
            target = findTarget(attacker);
            if (target == null) {
                data.remove(TARGET);
                stop(attacker);
                return;
            }
            data.putUUID(TARGET, target.getUUID());
        }

        chase(attacker, target);
        if (attacker.distanceToSqr(target) > ATTACK_RANGE_SQR) {
            data.putInt(ATTACK_WINDUP, 0);
            return;
        }
        if (data.getInt(ATTACK_COOLDOWN) > 0) return;

        int windup = data.getInt(ATTACK_WINDUP);
        if (windup <= 0) {
            data.putInt(ATTACK_WINDUP, ATTACK_WINDUP_TICKS);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    100, 5, false, true, true));
            return;
        }
        windup--;
        data.putInt(ATTACK_WINDUP, windup);
        if (windup > 0 || attacker.distanceToSqr(target) > 25.0D) return;

        float damage = SingingMachinePolicy.controlledAttackDamage(fortitudeLevel(attacker));
        boolean bypassPvp = attacker instanceof Player && target instanceof ServerPlayer
                && target.getServer() != null && !target.getServer().isPvpAllowed();
        net.minecraft.world.damagesource.DamageSource damageSource = bypassPvp
                ? DamageHelper.getDamage(attacker, source, "lobotocraft:red")
                : DamageHelper.getDamage(attacker, "lobotocraft:red");
        EntityUtil.clearHurtTime(target);
        target.hurt(damageSource, damage);
        data.putInt(ATTACK_COOLDOWN, ATTACK_COOLDOWN_TICKS);
        if (!target.isAlive() || target.isDeadOrDying()) {
            if (target instanceof EntityClerk clerk) EntityClerk.markNoTombstone(clerk);
            data.remove(TARGET);
            data.putBoolean(RETURNING, true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (isActive(event.getEntity())) clearForRecovery(event.getEntity());
    }

    private static EntitySingingMachine source(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.hasUUID(SOURCE) || !(entity.level() instanceof ServerLevel level)) return null;
        Entity source = level.getEntity(data.getUUID(SOURCE));
        return source instanceof EntitySingingMachine machine && machine.isAlive()
                ? machine : null;
    }

    private static LivingEntity storedTarget(LivingEntity attacker) {
        CompoundTag data = attacker.getPersistentData();
        if (!data.hasUUID(TARGET) || !(attacker.level() instanceof ServerLevel level)) return null;
        Entity target = level.getEntity(data.getUUID(TARGET));
        return target instanceof LivingEntity living ? living : null;
    }

    private static LivingEntity findTarget(LivingEntity attacker) {
        AABB area = attacker.getBoundingBox().inflate(TARGET_SEARCH_RANGE);
        return attacker.level().getEntitiesOfClass(LivingEntity.class, area,
                        candidate -> isValidTarget(attacker, candidate))
                .stream().min(Comparator.comparingDouble(attacker::distanceToSqr)).orElse(null);
    }

    public static boolean isEligibleWorker(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer) && !(entity instanceof EntityClerk)) return false;
        if (!entity.isAlive() || entity.isRemoved() || entity.isInvulnerable()
                || HostileControlState.isActive(entity)) return false;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return !(entity instanceof ServerPlayer player)
                || !com.wzz.lobotocraft.util.MentalValueUtil.isPanic(player)
                && !WorkManager.isPlayerWorking(player);
    }

    private static boolean isValidTarget(LivingEntity attacker, LivingEntity target) {
        return target != null && target != attacker && isEligibleWorker(target);
    }

    private static void chase(LivingEntity attacker, LivingEntity target) {
        if (attacker instanceof Player player) {
            PlayerControlLock.lock(player, target, 0.16D, 2.4D);
        } else if (attacker instanceof Mob mob) {
            mob.setTarget(target);
            mob.getLookControl().setLookAt(target);
            mob.getNavigation().moveTo(target, 1.15D);
        }
        moveDirectly(attacker, target.position(), 0.10D);
    }

    private static void returnToMachine(LivingEntity attacker, EntitySingingMachine source) {
        if (attacker.distanceToSqr(source) <= SOURCE_RANGE_SQR) {
            attacker.getPersistentData().remove(RETURNING);
            source.acceptCorpseDelivery();
            return;
        }
        if (attacker instanceof Player player) {
            PlayerControlLock.lock(player, source, 0.16D, 2.0D);
        } else if (attacker instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().moveTo(source, 1.15D);
        }
        moveDirectly(attacker, source.position(), 0.10D);
    }

    private static void moveDirectly(LivingEntity entity, Vec3 destination, double speed) {
        Vec3 delta = new Vec3(destination.x - entity.getX(), 0.0D,
                destination.z - entity.getZ());
        if (delta.lengthSqr() < 0.01D) return;
        Vec3 motion = delta.normalize().scale(speed);
        entity.setDeltaMovement(motion.x, entity.getDeltaMovement().y, motion.z);
        entity.hasImpulse = true;
    }

    private static void stop(LivingEntity entity) {
        if (entity instanceof Player player) PlayerControlLock.unlock(player);
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        entity.setDeltaMovement(0.0D, entity.getDeltaMovement().y, 0.0D);
    }

    private static int fortitudeLevel(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return 1;
        return player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .map(stats -> stats.getFortitudeLevel()).orElse(1);
    }

    private static void decrement(CompoundTag data, String key) {
        int value = data.getInt(key);
        if (value > 0) data.putInt(key, value - 1);
    }
}
