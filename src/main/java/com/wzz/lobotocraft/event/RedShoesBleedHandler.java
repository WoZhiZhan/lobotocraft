package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.util.DotHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 血之渴望(red_shoes)套装 —— 流血
 *
 * 每次命中叠一层，最多5层，持续10秒（命中刷新），每秒每层 3点红色伤害。
 * 真正的计时/结算全在 {@link DotHelper} 的通用 DOT 容器里，这里只是个语义包装。
 */
public class RedShoesBleedHandler {

    /** 最大层数 */
    public static final int MAX_STACKS = 5;
    /** 持续时间：10秒 */
    public static final int DURATION_TICKS = 10 * 20;
    /** 跳伤间隔：1秒 */
    public static final int INTERVAL_TICKS = 20;
    /** 每层每秒伤害 */
    public static final float DAMAGE_PER_STACK = 3.0f;

    public static void applyBleed(Player attacker, LivingEntity target) {
        DotHelper.applyDot(DotHelper.RED_SHOES_BLEED, attacker, target,
                DAMAGE_PER_STACK, "red", INTERVAL_TICKS, DURATION_TICKS, MAX_STACKS);
    }

    public static int getStacks(LivingEntity target) {
        return DotHelper.getStacks(target, DotHelper.RED_SHOES_BLEED);
    }

    public static void clearBleed(LivingEntity target) {
        DotHelper.clearDot(target, DotHelper.RED_SHOES_BLEED);
    }
}