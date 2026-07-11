package com.wzz.lobotocraft.effect;

import com.wzz.lobotocraft.init.ModEffects;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.DotHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 孢子（蜂后套装）
 * - 无法恢复生命值（在 QueenBeeSetHandler#onLivingHeal 里拦截）
 * - 移动速度 -10%（下面的属性修饰符）
 * - 每2秒造成 8点红色 DOT 伤害（走 DotHelper，所以是真实伤害，并吃【威胁】增伤）
 * - 持续10秒（由施加方决定，见 {@link #applySpore}）
 */
public class SporeEffect extends MobEffect {

    private static final UUID SLOW_UUID = UUID.fromString("d58ab29f-968c-4eeb-82fb-beede4d88302");

    /** 施加者UUID（DOT 的伤害来源） */
    private static final String TAG_SOURCE = "QueenBeeSporeSource";
    /** 距离下一次跳伤的剩余tick */
    private static final String TAG_INTERVAL = "QueenBeeSporeInterval";

    /** 持续时间：10秒 */
    public static final int DURATION_TICKS = 10 * 20;
    /** 跳伤间隔：2秒 */
    public static final int INTERVAL_TICKS = 2 * 20;
    /** 每次跳伤 */
    public static final float DAMAGE = 8.0f;

    public SporeEffect() {
        super(MobEffectCategory.HARMFUL, 0xAEE6FF);
        // 移动速度 -10%
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, SLOW_UUID.toString(),
                -0.10D, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /**
     * 给目标挂上/刷新孢子。命中会刷新持续时间，但不会重置跳伤计时，
     * 所以高攻速刷新也不会把 DOT 变成“每次命中都跳一次”。
     */
    public static void applySpore(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) return;
        if (target == attacker) return;
        if (target.level().isClientSide || !target.isAlive()) return;

        CompoundTag data = target.getPersistentData();
        data.putUUID(TAG_SOURCE, attacker.getUUID());
        if (data.getInt(TAG_INTERVAL) <= 0) {
            data.putInt(TAG_INTERVAL, INTERVAL_TICKS);
        }
        target.addEffect(new MobEffectInstance(ModEffects.SPORE.get(),
                DURATION_TICKS, 0, false, true, true));
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        Level level = living.level();
        if (level.isClientSide()) return;

        CompoundTag data = living.getPersistentData();
        int interval = data.getInt(TAG_INTERVAL) - 1;
        if (interval > 0) {
            data.putInt(TAG_INTERVAL, interval);
            return;
        }
        data.putInt(TAG_INTERVAL, INTERVAL_TICKS);

        Player source = resolveSource(living);
        if (source == null) {
            // 施加者已下线/死亡/不在当前维度：本次跳伤跳过
            return;
        }

        // 统一 DOT 入口：真实伤害 + 自动吃到【威胁】增伤 / 蜂后的无视抗性
        DotHelper.dealDotDamage(living, DamageHelper.getDamage(source, "red"), DAMAGE);
        ParticleUtil.spawnParticlesAroundEntity(living, ModParticleTypes.RED.get(), 5, 0.1d);
    }

    private static Player resolveSource(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        if (!data.hasUUID(TAG_SOURCE)) return null;
        UUID uuid = data.getUUID(TAG_SOURCE);
        Player player = target.level().getPlayerByUUID(uuid);
        return (player != null && player.isAlive()) ? player : null;
    }
}