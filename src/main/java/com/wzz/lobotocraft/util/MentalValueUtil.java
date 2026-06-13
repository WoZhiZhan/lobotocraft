package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.capability.IMentalValue;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class MentalValueUtil {
    
    /**
     * 增加玩家精神值
     * @param player 玩家
     * @param amount 增加的数量
     */
    public static void addMentalValue(ServerPlayer player, float amount) {
        // 伊莎玛拉机制1:被其攻击的玩家在 debuff 期间,正向精神值恢复减少 50%
        if (amount > 0) {
            long until = player.getPersistentData().getLong("isharmla_mental_recover_debuff_until");
            if (until > 0 && player.level().getGameTime() < until) {
                amount *= 0.5f;
            }
        }
        final float finalAmount = amount;
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.addMentalValue(finalAmount);
            MessageLoader.getLoader().sendToPlayer(player,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue()));
        });
    }

    public static void reduceMentalValue(ServerPlayer player, float amount) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.reduceMentalValue(amount);
            MessageLoader.getLoader().sendToPlayer(player,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue()));
        });
    }

    /**
     * 设置玩家精神值
     * @param player 玩家
     * @param value 要设置的值
     */
    public static void setMentalValue(ServerPlayer player, float value) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.setMentalValue(value);
            MessageLoader.getLoader().sendToPlayer(player,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue()));
        });
    }
    
    /**
     * 获取玩家精神值
     * @param player 玩家
     * @return 精神值
     */
    public static float getMentalValue(Player player) {
        var value = new Object() { float val = 0; };
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            value.val = mental.getMentalValue();
        });
        return value.val;
    }

    /**
     * 获取玩家的有效最大精神值（基础 + 额外）
     */
    public static float getEffectiveMaxMentalValue(Player player) {
        return player.getCapability(MentalValueProvider.MENTAL_VALUE)
                .map(IMentalValue::getEffectiveMaxMentalValue)
                .orElse(20f);
    }

    /**
     * 获取玩家的额外精神值
     */
    public static float getExtraMentalValue(Player player) {
        return player.getCapability(MentalValueProvider.MENTAL_VALUE)
                .map(IMentalValue::getExtraMentalValue)
                .orElse(20f);
    }
}