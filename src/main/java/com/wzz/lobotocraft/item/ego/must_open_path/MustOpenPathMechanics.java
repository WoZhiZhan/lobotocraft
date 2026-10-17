package com.wzz.lobotocraft.item.ego.must_open_path;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.MustOpenPathMeterSyncPacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MustOpenPathMechanics {
    private static final String DEPLOYED = "LobotocraftMustOpenPathDeployed";
    private static final String DEPLOYED_UNTIL = "LobotocraftMustOpenPathDeployedUntil";
    private static final String PENDING_UNTIL = "LobotocraftMustOpenPathPendingUntil";
    private static final String COOLDOWN_UNTIL = "LobotocraftMustOpenPathCooldownUntil";
    private static final String ORIGIN_DIMENSION = "LobotocraftMustOpenPathOriginDimension";
    private static final String ORIGIN_X = "LobotocraftMustOpenPathOriginX";
    private static final String ORIGIN_Y = "LobotocraftMustOpenPathOriginY";
    private static final String ORIGIN_Z = "LobotocraftMustOpenPathOriginZ";
    private static final String STUN_UNTIL = "LobotocraftMustOpenPathStunUntil";
    private static final String METER = "LobotocraftMustOpenPathMeter";
    private static final String LAST_SYNCED_METER = "LobotocraftMustOpenPathLastSyncedMeter";
    private static final String FULL_CHARGE_ATTACK_TICK = "LobotocraftMustOpenPathFullChargeAttackTick";
    private static final String BOUNDARY_SOUND_TICK = "LobotocraftMustOpenPathBoundarySoundTick";

    private static final UUID MOVEMENT_UUID = UUID.fromString("79b22718-1495-4485-bb8c-f9e00f438f28");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("f97b02e1-14dc-4ca3-b347-6f0f75b8061f");
    private static final AttributeModifier MOVEMENT_PENALTY = new AttributeModifier(
            MOVEMENT_UUID, "must_open_path_anchor_movement", -0.80D,
            AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier ATTACK_SPEED_PENALTY = new AttributeModifier(
            ATTACK_SPEED_UUID, "must_open_path_anchor_attack_speed", -0.20D,
            AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final int DEPLOYMENT_TICKS = 45 * 20;
    public static final int COOLDOWN_TICKS = 40 * 20;
    public static final int STUN_TICKS = 6 * 20;
    public static final int BOUNDARY_TICKS = 30 * 20;
    public static final int BOUNDARY_INVULNERABLE_TICKS = 15;

    private MustOpenPathMechanics() {
    }

    public static boolean isDeployed(Player player) {
        return player != null && player.getPersistentData().getBoolean(DEPLOYED);
    }

    public static boolean hasPendingAnchor(Player player) {
        return player != null
                && player.getPersistentData().getLong(PENDING_UNTIL) > player.level().getGameTime();
    }

    public static void markPendingAnchor(ServerPlayer player) {
        player.getPersistentData().putLong(PENDING_UNTIL, player.level().getGameTime() + 100L);
    }

    public static long remainingCooldown(Player player) {
        if (player == null) return 0L;
        return Math.max(0L, player.getPersistentData().getLong(COOLDOWN_UNTIL)
                - player.level().getGameTime());
    }

    public static void activateAt(ServerPlayer player, ResourceKey<Level> originDimension,
                                  Vec3 origin, Vec3 landing) {
        activateAt(player, originDimension, origin, landing, true);
    }

    public static void activateAt(ServerPlayer player, ResourceKey<Level> originDimension,
                                  Vec3 origin, Vec3 landing, boolean fullyCharged) {
        if (player == null || originDimension == null || origin == null || landing == null
                || isDeployed(player)) return;
        CompoundTag data = player.getPersistentData();
        data.remove(PENDING_UNTIL);
        data.putBoolean(DEPLOYED, true);
        data.putLong(DEPLOYED_UNTIL, player.level().getGameTime() + DEPLOYMENT_TICKS);
        data.putString(ORIGIN_DIMENSION, originDimension.location().toString());
        data.putDouble(ORIGIN_X, origin.x);
        data.putDouble(ORIGIN_Y, origin.y);
        data.putDouble(ORIGIN_Z, origin.z);

        Vec3 safeLanding = findSafePosition(player.serverLevel(), player, landing);
        EntityUtil.teleportPlayer(player, player.serverLevel().dimension(), safeLanding);
        applyDeploymentModifiers(player);
        player.addEffect(new MobEffectInstance(ModMobEffects.MUST_OPEN_PATH_ANCHORED.get(),
                DEPLOYMENT_TICKS, 0, false, false, false));

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SPLASH, safeLanding.x, safeLanding.y + 0.5D,
                safeLanding.z, 520, 3.2D, 1.8D, 3.2D, 0.62D);
        level.sendParticles(ParticleTypes.BUBBLE, safeLanding.x, safeLanding.y + 0.5D,
                safeLanding.z, 420, 3.0D, 1.6D, 3.0D, 0.48D);
        if (fullyCharged) {
            level.playSound(null, BlockPos.containing(safeLanding), ModSounds.MUST_OPEN_PATH_HIT.get(),
                    SoundSource.PLAYERS, 1.8F, 1.0F);
        }

        float damage = MustOpenPathWeapon.rollDamage(player) * 2.0F;
        AABB area = specialAttackArea(player);
        long stunUntil = level.getGameTime() + STUN_TICKS;
        boolean hitTarget = false;
        if (fullyCharged) markFullChargeAttack(player);
        try {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                    target -> target != player && target.isAlive())) {
                EntityUtil.clearHurtTime(target);
                boolean hit = target.hurt(DamageHelper.getDamage(player, "red"), damage);
                hitTarget |= hit;
                if (!target.isAlive()) continue;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        STUN_TICKS, 255, false, false, true));
                target.getPersistentData().putLong(STUN_UNTIL, stunUntil);
            }
        } finally {
            clearFullChargeAttack(player);
        }
        if (fullyCharged && hitTarget
                && (!player.getPersistentData().contains(BOUNDARY_SOUND_TICK)
                || player.getPersistentData().getLong(BOUNDARY_SOUND_TICK) != level.getGameTime())) {
            MustOpenPathWeapon.playRandomAttackSound(level, player.blockPosition(), player.getRandom());
        }
    }

    public static boolean returnToOrigin(ServerPlayer player) {
        if (player == null || !isDeployed(player)) return false;
        CompoundTag data = player.getPersistentData();
        ResourceLocation dimensionId = ResourceLocation.tryParse(data.getString(ORIGIN_DIMENSION));
        ResourceKey<Level> dimension = dimensionId == null
                ? player.serverLevel().dimension()
                : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId);
        ServerLevel targetLevel = player.getServer() == null ? null : player.getServer().getLevel(dimension);
        if (targetLevel == null) return false;
        Vec3 origin = new Vec3(data.getDouble(ORIGIN_X), data.getDouble(ORIGIN_Y), data.getDouble(ORIGIN_Z));
        Vec3 safeOrigin = findSafePosition(targetLevel, player, origin);
        if (!EntityUtil.teleportPlayer(player, dimension, safeOrigin)) return false;

        clearDeployment(player);
        data.putLong(COOLDOWN_UNTIL, player.level().getGameTime() + COOLDOWN_TICKS);
        player.getCooldowns().addCooldown(ModItems.MUST_OPEN_PATH_WEAPON.get(), COOLDOWN_TICKS);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.MUST_OPEN_PATH_RETURN.get(),
                SoundSource.PLAYERS, 1.5F, 1.0F);
        return true;
    }

    public static void clearDeployment(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(DEPLOYED, false);
        data.remove(DEPLOYED_UNTIL);
        data.remove(PENDING_UNTIL);
        data.remove(ORIGIN_DIMENSION);
        data.remove(ORIGIN_X);
        data.remove(ORIGIN_Y);
        data.remove(ORIGIN_Z);
        player.removeEffect(ModMobEffects.MUST_OPEN_PATH_ANCHORED.get());
        removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_UUID);
        removeModifier(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_UUID);
    }

    private static Vec3 findSafePosition(ServerLevel level, LivingEntity entity, Vec3 wanted) {
        BlockPos origin = BlockPos.containing(wanted);
        for (int radius = 0; radius <= 2; radius++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (!level.hasChunkAt(candidate)) continue;
                        Vec3 position = Vec3.atBottomCenterOf(candidate);
                        AABB box = entity.getDimensions(entity.getPose())
                                .makeBoundingBox(position.x, position.y, position.z);
                        if (level.noCollision(entity, box)) return position;
                    }
                }
            }
        }
        return wanted;
    }

    static AABB specialAttackArea(Player player) {
        Vec3 playerCenter = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
        return AABB.ofSize(playerCenter, 7.0D, 3.0D, 7.0D);
    }

    private static void applyDeploymentModifiers(Player player) {
        addModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_PENALTY);
        addModifier(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_PENALTY);
    }

    private static void addModifier(AttributeInstance attribute, AttributeModifier modifier) {
        if (attribute != null && attribute.getModifier(modifier.getId()) == null) {
            attribute.addTransientModifier(modifier);
        }
    }

    private static void removeModifier(AttributeInstance attribute, UUID id) {
        if (attribute != null) attribute.removeModifier(id);
    }

    public static void updateSetHealth(Player player, boolean complete) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        AttributeModifier current = health.getModifier(MustOpenPathCurio.SET_HEALTH_UUID);
        if (complete && current == null) {
            health.addTransientModifier(new AttributeModifier(MustOpenPathCurio.SET_HEALTH_UUID,
                    "must_open_path_complete_set_health", 325.0D,
                    AttributeModifier.Operation.ADDITION));
        } else if (!complete && current != null) {
            health.removeModifier(MustOpenPathCurio.SET_HEALTH_UUID);
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        }
    }

    public static int meter(Player player) {
        return Math.max(0, Math.min(100, player.getPersistentData().getInt(METER)));
    }

    public static boolean isBoundaryActive(Player player) {
        return player != null && player.hasEffect(ModMobEffects.MUST_OPEN_PATH_BOUNDARY.get());
    }

    static void markFullChargeAttack(ServerPlayer player) {
        player.getPersistentData().putLong(FULL_CHARGE_ATTACK_TICK, player.level().getGameTime());
    }

    static void clearFullChargeAttack(ServerPlayer player) {
        player.getPersistentData().remove(FULL_CHARGE_ATTACK_TICK);
    }

    static boolean shouldCountOutgoingAttack(ServerPlayer player) {
        return player != null && (MustOpenPathWeapon.isFullyChargedAttack(player)
                || (player.getPersistentData().contains(FULL_CHARGE_ATTACK_TICK)
                && player.getPersistentData().getLong(FULL_CHARGE_ATTACK_TICK)
                == player.level().getGameTime()));
    }

    private static void addMeter(ServerPlayer player) {
        if (!EgoArmorHelper.isFullEGOWithWeaponAnywhere(player, MustOpenPathWeapon.SET_ID)) return;
        MobEffectInstance active = player.getEffect(ModMobEffects.MUST_OPEN_PATH_BOUNDARY.get());
        if (active != null) {
            player.addEffect(new MobEffectInstance(ModMobEffects.MUST_OPEN_PATH_BOUNDARY.get(),
                    BOUNDARY_TICKS, active.getAmplifier(), false, true, true));
            return;
        }
        int next = Math.min(100, meter(player) + 2);
        player.getPersistentData().putInt(METER, next);
        if (next >= 100) {
            player.getPersistentData().putInt(METER, 0);
            player.addEffect(new MobEffectInstance(ModMobEffects.MUST_OPEN_PATH_BOUNDARY.get(),
                    BOUNDARY_TICKS, 0, false, true, true));
            player.getPersistentData().putLong(BOUNDARY_SOUND_TICK, player.level().getGameTime());
            MustOpenPathWeapon.playRandomAttackSound(
                    player.level(), player.blockPosition(), player.getRandom());
        }
        syncMeter(player, true);
    }

    private static void syncMeter(ServerPlayer player, boolean force) {
        int value = meter(player);
        CompoundTag data = player.getPersistentData();
        if (!force && data.getInt(LAST_SYNCED_METER) == value) return;
        data.putInt(LAST_SYNCED_METER, value);
        MessageLoader.getLoader().sendToPlayer(player, new MustOpenPathMeterSyncPacket(value));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (isDeployed(player)) {
            applyDeploymentModifiers(player);
            if (!player.hasEffect(ModMobEffects.MUST_OPEN_PATH_ANCHORED.get())) {
                long remaining = Math.max(1L, player.getPersistentData().getLong(DEPLOYED_UNTIL)
                        - player.level().getGameTime());
                player.addEffect(new MobEffectInstance(ModMobEffects.MUST_OPEN_PATH_ANCHORED.get(),
                        (int) Math.min(Integer.MAX_VALUE, remaining), 0, false, false, false));
            }
            if (player.level().getGameTime() >= player.getPersistentData().getLong(DEPLOYED_UNTIL)) {
                returnToOrigin(player);
            }
        } else {
            removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_UUID);
            removeModifier(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_UUID);
        }

        boolean complete = EgoArmorHelper.isFullEGOWithWeaponAnywhere(
                player, MustOpenPathWeapon.SET_ID);
        updateSetHealth(player, complete);
        if (!complete && meter(player) != 0) {
            player.getPersistentData().putInt(METER, 0);
            syncMeter(player, true);
        } else if (player.tickCount % 40 == 0) {
            syncMeter(player, false);
        }
        if (player.hasEffect(ModMobEffects.MUST_OPEN_PATH_BOUNDARY.get())
                && player.invulnerableTime > BOUNDARY_INVULNERABLE_TICKS) {
            player.invulnerableTime = BOUNDARY_INVULNERABLE_TICKS;
        }
    }

    /** Invoked in the explicit DamageSettlement order; not an independent listener. */
    public static void onArmorHurt(LivingHurtEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || event.getAmount() <= 0.0F
                || !EgoArmorHelper.isWearingFullSet(player, MustOpenPathWeapon.SET_ID)
                || MentalValueUtil.isPanic(player)
                || event.getAmount() >= player.getHealth()) return;
        float preHitHealth = player.getHealth();
        player.heal(preHitHealth < player.getMaxHealth() * 0.5F ? 12.0F : 7.0F);
    }

    /** Called after final HP protection by DamageSettlement. */
    public static void onDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) return;
        Set<ServerPlayer> participants = new LinkedHashSet<>();
        if (event.getEntity() instanceof ServerPlayer victim) participants.add(victim);
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && shouldCountOutgoingAttack(attacker)) participants.add(attacker);
        participants.forEach(MustOpenPathMechanics::addMeter);
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasEffect(ModMobEffects.MUST_OPEN_PATH_BOUNDARY.get())
                && player.invulnerableTime > BOUNDARY_INVULNERABLE_TICKS) {
            player.invulnerableTime = BOUNDARY_INVULNERABLE_TICKS;
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModMobEffects.MUST_OPEN_PATH_BOUNDARY.get())) {
            event.setAmount(event.getAmount() * 1.20F);
        }
    }

    @SubscribeEvent
    public static void onTargetChanged(LivingChangeTargetEvent event) {
        if (!isStunned(event.getEntity()) || !(event.getNewTarget() instanceof Player player)) return;
        if (EgoArmorHelper.isFullEGOWithWeaponAnywhere(player, MustOpenPathWeapon.SET_ID)) {
            event.setNewTarget(null);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (!isStunned(living)) return;
        if (living instanceof Mob mob && mob.getTarget() instanceof Player target
                && EgoArmorHelper.isFullEGOWithWeaponAnywhere(target, MustOpenPathWeapon.SET_ID)) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            if (mob instanceof NeutralMob neutral) {
                neutral.setPersistentAngerTarget(null);
                neutral.setRemainingPersistentAngerTime(0);
            }
        }
        if (living.level() instanceof ServerLevel level && living.tickCount % 4 == 0) {
            double angle = living.tickCount * 0.45D;
            level.sendParticles(ParticleTypes.CRIT,
                    living.getX() + Math.cos(angle) * 0.65D,
                    living.getY() + living.getBbHeight() + 0.25D,
                    living.getZ() + Math.sin(angle) * 0.65D,
                    2, 0.06D, 0.06D, 0.06D, 0.01D);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (EgoArmorHelper.isWearingFullSet(player, MustOpenPathWeapon.SET_ID)) {
            player.serverLevel().playSound(null, player.blockPosition(),
                    ModSounds.MUST_OPEN_PATH_DEATH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (isDeployed(player)) clearDeployment(player);
    }

    private static boolean isStunned(LivingEntity living) {
        return living != null && living.getPersistentData().getLong(STUN_UNTIL)
                > living.level().getGameTime();
    }
}
