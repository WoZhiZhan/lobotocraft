package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * E.G.O装备接口
 * 所有E.G.O装备（胸甲、护腿、靴子）都应该实现这个接口
 * E.G.O装备特性：
 * - 必须穿戴全套才能生效
 * - 必须装备锁定才能生效
 * - 提供自定义抗性（RED/WHITE/BLACK/PALE）
 * - 可以有特殊效果
 */
public interface IEgoArmor {

    /**
     * 获取E.G.O套装ID
     * 同一套装的所有装备（胸甲、护腿、靴子）应该返回相同的ID
     * 
     * @return 套装ID（例如："repentance", "twilight"等）
     */
    String getSetId();

    /**
     * 获取RED伤害抗性
     * 
     * @return 抗性值（例如：0.9表示受到90%伤害，减伤10%；2.0表示受到200%伤害）
     */
    float getRedResistance();

    RiskLevel riskLevel();

    /**
     * 获取WHITE伤害抗性
     * 
     * @return 抗性值
     */
    float getWhiteResistance();

    /**
     * 获取BLACK伤害抗性
     * 
     * @return 抗性值
     */
    float getBlackResistance();

    /**
     * 获取PALE伤害抗性
     * 
     * @return 抗性值
     */
    float getPaleResistance();

    /**
     * 当玩家穿戴完整套装时每tick调用
     * 可以用于实现被动效果
     * 
     * @param player 穿戴者
     * @param armorPiece 当前装备部件（头/胸/腿/脚）
     */
    default void onWearingFullSet(Player player, ItemStack armorPiece) {
        // 默认什么都不做
    }

    /**
     * 当玩家穿戴完整套装受到伤害时调用
     * 可以用于实现触发型效果
     * 
     * @param player 穿戴者
     * @param damageType 伤害类型（"red", "white", "black", "pale"）
     * @param damage 原始伤害值
     * @return 是否已处理伤害（返回true表示已处理，不会再计算抗性）
     */
    default boolean onDamaged(Player player, String damageType, float damage) {
        return false;  // 默认不处理
    }

    /**
     * 当玩家穿戴完整套装造成伤害时调用
     * 可以用于实现攻击触发效果
     * 
     * @param player 穿戴者
     * @param target 目标实体
     * @param damage 造成的伤害
     */
    default void onAttack(Player player, net.minecraft.world.entity.Entity target, float damage) {
        // 默认什么都不做
    }
}