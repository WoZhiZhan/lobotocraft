package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.DotHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 血之渴望(red_shoes)套装 —— 流血 DOT
 *
 * 规则：
 * - 穿戴血之渴望全套的玩家每次对目标造成伤害，为目标叠加一层【流血】
 * - 每层流血每秒造成 3 点红色伤害（5层 = 15点/秒），DOT 为真实伤害
 * - 最多叠加 5 层，持续 10 秒；再次命中会刷新持续时间（层数继续累加，上限5）
 *
 * 伤害结算统一走 {@link DotHelper#dealDotDamage}，所以【威胁】的 DOT 增伤会自动生效。
 */
@Mod.EventBusSubscriber
public class RedShoesBleedHandler {

    /** 当前层数 */
    private static final String TAG_STACKS = "RedShoesBleedStacks";
    /** 剩余持续时间(tick) */
    private static final String TAG_DURATION = "RedShoesBleedDuration";
    /** 距离下一次跳伤的剩余tick */
    private static final String TAG_INTERVAL = "RedShoesBleedInterval";
    /** 施加者UUID（用于结算红色伤害的伤害来源） */
    private static final String TAG_SOURCE = "RedShoesBleedSource";

    /** 最大层数 */
    public static final int MAX_STACKS = 5;
    /** 持续时间：10秒 */
    public static final int DURATION_TICKS = 10 * 20;
    /** 跳伤间隔：1秒 */
    public static final int INTERVAL_TICKS = 20;
    /** 每层每秒伤害 */
    public static final float DAMAGE_PER_STACK = 3.0f;

    /**
     * 为目标叠加/刷新一层流血
     */
    public static void applyBleed(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) return;
        if (target == attacker) return;
        if (target.level().isClientSide || !target.isAlive()) return;

        CompoundTag data = target.getPersistentData();
        int stacks = Math.min(MAX_STACKS, data.getInt(TAG_STACKS) + 1);
        data.putInt(TAG_STACKS, stacks);
        // 每次命中刷新持续时间
        data.putInt(TAG_DURATION, DURATION_TICKS);
        if (data.getInt(TAG_INTERVAL) <= 0) {
            data.putInt(TAG_INTERVAL, INTERVAL_TICKS);
        }
        data.putUUID(TAG_SOURCE, attacker.getUUID());
    }

    public static int getStacks(LivingEntity target) {
        return target == null ? 0 : target.getPersistentData().getInt(TAG_STACKS);
    }

    public static void clearBleed(LivingEntity target) {
        if (target == null) return;
        CompoundTag data = target.getPersistentData();
        data.remove(TAG_STACKS);
        data.remove(TAG_DURATION);
        data.remove(TAG_INTERVAL);
        data.remove(TAG_SOURCE);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        int stacks = data.getInt(TAG_STACKS);
        if (stacks <= 0) return;

        if (!entity.isAlive()) {
            clearBleed(entity);
            return;
        }

        int duration = data.getInt(TAG_DURATION);
        if (duration <= 0) {
            clearBleed(entity);
            return;
        }
        data.putInt(TAG_DURATION, duration - 1);

        int interval = data.getInt(TAG_INTERVAL) - 1;
        if (interval > 0) {
            data.putInt(TAG_INTERVAL, interval);
            return;
        }
        data.putInt(TAG_INTERVAL, INTERVAL_TICKS);

        Player source = resolveSource(entity);
        if (source == null) {
            // 施加者已下线/死亡/不在当前维度，本次跳伤跳过（层数与持续时间照常结算）
            return;
        }

        // 统一的 DOT 入口：真实伤害 + 自动吃到【威胁】的 DOT 增伤
        DotHelper.dealDotDamage(entity,
                DamageHelper.getDamage(source, "red"),
                DAMAGE_PER_STACK * stacks);

        ParticleUtil.spawnParticlesAroundEntity(entity, ModParticleTypes.RED.get(), 3, 0.1d);
    }

    private static Player resolveSource(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        if (!data.hasUUID(TAG_SOURCE)) return null;
        UUID uuid = data.getUUID(TAG_SOURCE);
        Player player = target.level().getPlayerByUUID(uuid);
        return (player != null && player.isAlive()) ? player : null;
    }
}