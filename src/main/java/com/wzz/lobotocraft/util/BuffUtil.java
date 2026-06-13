package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.init.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 友谊之证 / 亲吻 两个 Buff 的辅助工具,
 * 用于替代原"鹅卵石"和"冰片"物品的持有/添加/移除/计数操作。
 */
public class BuffUtil {

    // 无限持续时间(与原物品长期存在的语义一致)
    private static final int INFINITE_DURATION = -1;

    // ===================== 友谊之证(原鹅卵石) =====================

    /**
     * 是否拥有"友谊之证"。
     */
    public static boolean hasFriendshipProof(Player player) {
        return player.hasEffect(ModEffects.FRIENDSHIP_PROOF.get());
    }

    /**
     * 获取"友谊之证"承载的 QliphothCounter 数值(amplifier + 1)。
     * 未拥有时返回 -1,与原 CobblestoneItem.getQliphothCounter 缺省语义一致。
     */
    public static int getFriendshipProofCounter(Player player) {
        MobEffectInstance instance = player.getEffect(ModEffects.FRIENDSHIP_PROOF.get());
        if (instance == null) return -1;
        return instance.getAmplifier() + 1;
    }

    /**
     * 添加/刷新"友谊之证",counter 决定其强度(counter>=1)。
     */
    public static void giveFriendshipProof(Player player, int counter) {
        int amplifier = Math.max(0, counter - 1);
        player.addEffect(new MobEffectInstance(
                ModEffects.FRIENDSHIP_PROOF.get(),
                INFINITE_DURATION,
                amplifier,
                false,
                false,
                true
        ));
    }

    /**
     * 移除"友谊之证"。
     */
    public static void removeFriendshipProof(Player player) {
        player.removeEffect(ModEffects.FRIENDSHIP_PROOF.get());
    }

    // ===================== 亲吻(原冰片) =====================

    /**
     * 是否拥有"亲吻"。
     */
    public static boolean hasKiss(Player player) {
        return player.hasEffect(ModEffects.KISS.get());
    }

    /**
     * 添加"亲吻"标记。
     */
    public static void giveKiss(Player player) {
        player.addEffect(new MobEffectInstance(
                ModEffects.KISS.get(),
                INFINITE_DURATION,
                0,
                false,
                false,
                true
        ));
    }

    /**
     * 移除"亲吻"标记。
     */
    public static void removeKiss(Player player) {
        player.removeEffect(ModEffects.KISS.get());
    }
}
