package com.wzz.lobotocraft.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 便携式抑制计数(HUD 机制,替代原便携式抑制器物品)。
 * 初始/上限 120,存于玩家的持久数据(跨死亡保留)。
 * 公司维度外每 8 秒 -1;受额外伤害按伤害值扣减;归零后触发与原道具相同的惩罚。
 * 与不同等级异想体工作可回复:ZAYIN+2 / TETH+4 / HE+8 / WAW+16 / ALEPH+24。
 */
public class SuppressorCounterUtil {

    public static final int MAX_COUNT = 120;
    private static final String KEY = "lobotocraft_suppressor_count";
    private static final String INIT_KEY = "lobotocraft_suppressor_init";

    /** 读取持久数据子标签(跨死亡保留) */
    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persist = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, persist);
        }
        return persist;
    }

    /** 获取当前抑制计数(首次访问初始化为上限) */
    public static int get(Player player) {
        CompoundTag p = persisted(player);
        if (!p.getBoolean(INIT_KEY)) {
            p.putBoolean(INIT_KEY, true);
            p.putInt(KEY, MAX_COUNT);
            return MAX_COUNT;
        }
        return p.getInt(KEY);
    }

    public static void set(Player player, int value) {
        if (value < 0) value = 0;
        if (value > MAX_COUNT) value = MAX_COUNT;
        CompoundTag p = persisted(player);
        p.putBoolean(INIT_KEY, true);
        p.putInt(KEY, value);
    }

    /** 扣减计数,返回扣减后的值 */
    public static int reduce(Player player, int amount) {
        int v = get(player) - amount;
        set(player, v);
        return v < 0 ? 0 : v;
    }

    /** 回复计数 */
    public static void restore(Player player, int amount) {
        set(player, get(player) + amount);
    }

    public static boolean isEmpty(Player player) {
        return get(player) <= 0;
    }

    /** 根据异想体危险等级名返回工作回复量 */
    public static int restoreAmountForRiskLevel(String riskLevel) {
        if (riskLevel == null) return 0;
        switch (riskLevel.toUpperCase()) {
            case "ZAYIN": return 2;
            case "TETH": return 4;
            case "HE": return 8;
            case "WAW": return 16;
            case "ALEPH": return 24;
            default: return 0;
        }
    }
}
