package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.capability.IMentalValue;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicBoolean;

public class MentalValueUtil {
    
    /**
     * 增加玩家精神值
     * @param player 玩家
     * @param amount 增加的数量
     */
    public static void addMentalValue(Player player, float amount) {
        addMentalValue(player, amount, MentalValueEvent.ChangeType.ADD);
    }

    public static void addMentalValue(Player player, float amount, MentalValueEvent.ChangeType changeType) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        final float finalAmount = amount;
        serverPlayer.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.addMentalValue(finalAmount, changeType);
            MessageLoader.getLoader().sendToPlayer(serverPlayer,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue(), changeType));
        });
    }

    // 检测恐慌状态
    public static boolean isPanic(ServerPlayer player) {
        AtomicBoolean panic = new AtomicBoolean(false);
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> panic.set(mental.isMentalValueEmpty()));
        return panic.get();
    }

    public static void reduceMentalValue(ServerPlayer player, float amount, MentalValueEvent.ChangeType changeType) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.reduceMentalValue(amount, changeType);
            MessageLoader.getLoader().sendToPlayer(player,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue(), changeType));
        });
    }

    public static void reduceMentalValue(ServerPlayer player, float amount) {
        reduceMentalValue(player, amount, MentalValueEvent.ChangeType.REDUCE);
    }

    /**
     * 设置玩家精神值
     * @param player 玩家
     * @param value 要设置的值
     */
    public static void setMentalValue(ServerPlayer player, float value) {
        setMentalValue(player, value, MentalValueEvent.ChangeType.SET);
    }

    public static void setMentalValue(ServerPlayer player, float value, MentalValueEvent.ChangeType changeType) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.setMentalValue(value, changeType);
            MessageLoader.getLoader().sendToPlayer(player,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue(), changeType));
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