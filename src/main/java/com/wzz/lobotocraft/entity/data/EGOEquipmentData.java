package com.wzz.lobotocraft.entity.data;

import net.minecraft.resources.ResourceLocation;

/**
 * E.G.O装备完整数据
 * 包含图标、属性、描述等所有显示信息
 */
public class EGOEquipmentData {

    /**
     * @param iconTexture 图标路径
     * @param name        名称（如"忏悔"）
     * @param part        部位（如"头部"）
     * @param effects     效果描述（多行）
     * @param itemId      物品注册ID
     */
    public record GiftData(ResourceLocation iconTexture, String name, String part, String itemId, String... effects) {
    }

    /**
     * @param iconTexture         图标路径
     * @param name                名称（如"忏悔"）
     * @param riskLevel           风险等级
     * @param damageType          伤害类型（WHITE/RED/BLACK/PALE）
     * @param attackPower         攻击力（如"5-7"）
     * @param attackSpeed         攻击速度（如"普通"）
     * @param attackRange         攻击距离（如"近"）
     * @param developmentMaxCount 最大研发数量
     * @param itemId              物品注册ID
     */

    public record WeaponData(ResourceLocation iconTexture, String name, RiskLevel riskLevel, String damageType,
                             String attackPower, String attackSpeed, String attackRange, int developmentMaxCount,
                             String itemId) {
    }

    /**
     * @param iconTexture         图标路径
     * @param name                名称（如"忏悔"）
     * @param riskLevel           风险等级
     * @param redResistance       RED抗性
     * @param whiteResistance     WHITE抗性
     * @param blackResistance     BLACK抗性
     * @param paleResistance      PALE抗性
     * @param developmentMaxCount 最大研发数量
     * @param armorId             套装注册ID
     */
    public record ArmorData(ResourceLocation iconTexture, String name, RiskLevel riskLevel, float redResistance,
                            float whiteResistance, float blackResistance, float paleResistance, int developmentMaxCount,
                            String armorId) {
    }
}