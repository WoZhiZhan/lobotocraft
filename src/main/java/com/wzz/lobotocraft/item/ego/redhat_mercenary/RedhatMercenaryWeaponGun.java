package com.wzz.lobotocraft.item.ego.redhat_mercenary;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.event.SheepskinSetHandler;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.bigbadwolf.BigBadwolfWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 赤色佣兵 —— 火铳
 * ※ Shift+右键开火，射出黑色粒子，飞行15格
 * ※ 对路径上所有目标造成 11-13 点物理伤害（贯穿）
 * ※ 冷却 3 秒
 * ※ 被击中的目标陷入【猎物】：发光，持续10秒（TimerEntry 实现，不是药水效果）
 * ※ 持有者对猎物造成的红色伤害 +2~3
 * ※ 猎物同时只能有一只，再次开火会刷新/转移
 */
public class RedhatMercenaryWeaponGun extends RedhatMercenaryWeapon {
    /** 射程：15格 */
    private static final double ATTACK_RANGE = 15;
    /** 冷却：3秒 */
    private static final int USE_COOLDOWN_TICKS = 3 * 20;
    /** 粒子拖尾的采样间隔（格） */
    private static final double TRAIL_STEP = 0.35D;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        if (!canUseItem(player)) {
            return InteractionResultHolder.pass(stack);
        }

        int cooldown = stack.getOrCreateTag().getInt("UseTick");
        if (cooldown > 0) {
            player.displayClientMessage(
                    Component.literal("§a冷却中,还剩:" + cooldown + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }
        stack.getOrCreateTag().putInt("UseTick",
                SheepskinSetHandler.getGunCooldown(player, USE_COOLDOWN_TICKS));

        if (!level.isClientSide) {
            SoundUtil.playSound(level, player, ModSounds.REDHAT_MERCENARY_WEAPON_GUN.get());
            triggerAttackAnimation(player, stack);
            fire(level, player);
        }
        return super.use(level, player, hand);
    }

    /** 开火：黑色粒子拖尾 + 贯穿伤害 + 标记猎物 */
    private void fire(Level level, Player player) {
        spawnBlackTrail(level, player);

        float damage = 11f + player.getRandom().nextInt(3); // 11~13
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (LivingEntity target : EntityUtil.findAllLivingEntitiesInLookDirection(player, ATTACK_RANGE)) {
            if (target == player || !target.isAlive()) continue;

            // 贯穿：清掉无敌帧，保证路径上每个目标都吃到伤害
            EntityUtil.clearHurtTime(target,
                    () -> target.hurt(target.damageSources().playerAttack(player), damage));
            if (EgoArmorHelper.isFullEGO(player, "redhat_mercenary")) {
                SlowTimer.applySlowEffect(target);
            }
            double dist = target.distanceToSqr(player);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = target;
            }
        }

        // 猎物只能有一只：标记路径上最近的那个（再次开火会刷新/转移）
        if (nearest != null) {
            Prey.mark(player, nearest);
        }
    }

    private void spawnBlackTrail(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        for (double d = 0.5D; d <= ATTACK_RANGE; d += TRAIL_STEP) {
            Vec3 pos = start.add(dir.scale(d));
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        int cooldown = stack.getOrCreateTag().getInt("UseTick");
        if (cooldown > 0) {
            stack.getOrCreateTag().putInt("UseTick", cooldown - 1);
        }
    }

    @Override
    protected String postFix() {
        return "gun";
    }

    public static class SlowTimer extends TimerEntry<LivingEntity> {
        private static final UUID SLOW_UUID = UUID.fromString("83e8b7ab-7062-4d3a-a693-9670ba802d5d");
        private final double slowAmount;

        public SlowTimer(double slowAmount) {
            this.slowAmount = slowAmount;
        }

        @Override
        public void onStart(@NotNull LivingEntity entity) {
            applySlow(entity, true);
        }

        @Override
        public void onEnd(@NotNull LivingEntity entity) {
            applySlow(entity, false);
        }

        private void applySlow(LivingEntity entity, boolean apply) {
            AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.removeModifier(SLOW_UUID);
                if (apply) {
                    AttributeModifier modifier = new AttributeModifier(
                            SLOW_UUID,
                            "slow_effect",
                            slowAmount,
                            AttributeModifier.Operation.MULTIPLY_TOTAL
                    );
                    attribute.addTransientModifier(modifier);
                }
            }
        }

        public static void applySlowEffect(LivingEntity target) {
            int duration = 10 * 50;
            BigBadwolfWeapon.SlowTimer timer = new BigBadwolfWeapon.SlowTimer(-0.1);
            timer.addSkillTimer(target, 0, duration, 1, true);
        }
    }

    public static class Prey extends TimerEntry<LivingEntity> {
        /** 单例：class 锁是按 (计时器类, 实体) 的，new 新实例会被旧实例的锁挡住 */
        private static final Prey INSTANCE = new Prey();

        private static final int DURATION_MS = 10_000;
        private static final String TAG_OWNER = "RedhatPreyOwner";
        private static final String TAG_EXPIRE = "RedhatPreyExpire";

        /** 持有者 -> 当前猎物，保证"最多一只" */
        private static final Map<UUID, UUID> PREY_BY_OWNER = new ConcurrentHashMap<>();

        private Prey() {
            this.setRequireMainThread(true);
        }

        /** 标记 / 刷新 / 转移猎物 */
        public static void mark(Player player, LivingEntity target) {
            if (player == null || target == null) return;
            if (target.level().isClientSide || !target.isAlive()) return;

            // 换目标：先把上一只猎物的计时器停掉并解除标记
            UUID previousId = PREY_BY_OWNER.get(player.getUUID());
            if (previousId != null && !previousId.equals(target.getUUID())
                    && player.level() instanceof ServerLevel serverLevel
                    && serverLevel.getEntity(previousId) instanceof LivingEntity previous) {
                INSTANCE.removeTimer(previousId);
                release(previous);
            }

            target.getPersistentData().putUUID(TAG_OWNER, player.getUUID());
            target.getPersistentData().putLong(TAG_EXPIRE, System.currentTimeMillis() + DURATION_MS);
            target.setGlowingTag(true);
            PREY_BY_OWNER.put(player.getUUID(), target.getUUID());

            // refresh=true：同一实例会先 removeTimer 再重开，持续时间就刷新了
            INSTANCE.addSkillTimer(target, 0, DURATION_MS, 1, true);
        }

        public static boolean isPreyOf(Player player, LivingEntity target) {
            if (player == null || target == null) return false;
            if (!target.getPersistentData().hasUUID(TAG_OWNER)) return false;
            return player.getUUID().equals(target.getPersistentData().getUUID(TAG_OWNER));
        }

        public static void release(LivingEntity target) {
            if (target == null) return;
            if (target.getPersistentData().hasUUID(TAG_OWNER)) {
                PREY_BY_OWNER.remove(target.getPersistentData().getUUID(TAG_OWNER));
            }
            target.getPersistentData().remove(TAG_OWNER);
            target.getPersistentData().remove(TAG_EXPIRE);
            target.setGlowingTag(false);
        }

        @Override
        public void onStart(@NotNull LivingEntity entity) {
            entity.setGlowingTag(true);
        }

        @Override
        public void onRunning(@NotNull LivingEntity entity) {
            // 发光可能被别的逻辑清掉，这里兜底
            if (isPreyActive(entity) && !entity.hasGlowingTag()) {
                entity.setGlowingTag(true);
            }
        }

        @Override
        public void onEnd(@NotNull LivingEntity entity) {
            // refresh 后旧的 stop 任务仍会到点触发（TimerEntry 取消不了它），
            // 靠到期时间戳判断：还没到期说明自己是被顶掉的旧任务，别乱清
            if (isPreyActive(entity)) return;
            release(entity);
        }

        private static boolean isPreyActive(LivingEntity entity) {
            long expire = entity.getPersistentData().getLong(TAG_EXPIRE);
            return expire > 0 && System.currentTimeMillis() < expire - 50L;
        }
    }

    /* ================================================================
     * 猎物相关的事件
     * ================================================================ */
    @Mod.EventBusSubscriber(modid = ModMain.MODID)
    public static class PreyEvents {

        /** 持有者对猎物造成的红色伤害 +2~3 */
        @SubscribeEvent
        public static void onLivingHurt(LivingHurtEvent event) {
            LivingEntity target = event.getEntity();
            if (target.level().isClientSide) return;
            if (!DamageHelper.isRedDamage(event.getSource())) return;
            if (!(event.getSource().getEntity() instanceof Player player)) return;
            if (!Prey.isPreyOf(player, target)) return;

            // 想让DOT跳伤不吃这个加成，就加一句：if (DotHelper.isDotDamage(target)) return;
            event.setAmount(event.getAmount() + 2 + player.getRandom().nextInt(2)); // +2 或 +3
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            Prey.release(event.getEntity());
        }
    }
}