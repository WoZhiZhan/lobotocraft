package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.IEgoArmor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * E.G.O装备工具类
 * 用于检查E.G.O套装完整性和应用效果
 */
public class EgoArmorHelper {

    /**
     * 检查玩家是否穿戴完整的E.G.O套装
     *
     * @param player 玩家
     * @return 如果穿戴完整套装且装备已锁定，返回套装ID，否则返回null
     */
    public static String getFullSetId(Player player) {
        // 检查装备是否锁定
        if (!ArmorEffectChecker.isArmorLocked(player)) {
            return null;  // 装备未锁定，套装不生效
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);

        // 检查所有装备槽是否都有装备
        if (chest.isEmpty() || legs.isEmpty() || feet.isEmpty()) {
            return null;  // 装备不完整
        }

        // 检查所有装备是否都是E.G.O装备
        if (!(chest.getItem() instanceof IEgoArmor) ||
                !(legs.getItem() instanceof IEgoArmor) ||
                !(feet.getItem() instanceof IEgoArmor)) {
            return null;  // 不是完整的E.G.O装备
        }

        String chestSetId = ((IEgoArmor) chest.getItem()).getSetId();
        String legsSetId = ((IEgoArmor) legs.getItem()).getSetId();
        String feetSetId = ((IEgoArmor) feet.getItem()).getSetId();

        // 检查套装ID是否一致
        if (chestSetId.equals(legsSetId) &&
                legsSetId.equals(feetSetId)) {
            return chestSetId;  // 返回套装ID
        }

        return null;  // 套装不完整
    }

    public static RiskLevel getArmorRiskLevel(Player player) {
        if (!ArmorEffectChecker.isArmorLocked(player)) {
            return null;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof IEgoArmor egoArmor) {
            return egoArmor.riskLevel();
        }
        return null;
    }

    /**
     * 检查玩家是否穿戴完整的特定E.G.O套装
     *
     * @param player 玩家
     * @param setId 套装ID
     * @return true如果穿戴完整的指定套装
     */
    public static boolean isWearingFullSet(Player player, String setId) {
        String fullSetId = getFullSetId(player);
        return fullSetId != null && fullSetId.equals(setId);
    }

    public static boolean isFullEGO(Player player, String name) {
        if (player == null) return false;
        if (!isWearingFullSet(player, name))
            return false;
        boolean hasCurio = false;
        for (ItemStack stack : CuriosUtil.getCuriosItems(player)) {
            if (stack.getItem() instanceof BaseEgoCurio baseEgoCurio && baseEgoCurio.curioName().equals(name)) {
                hasCurio = true;
                break;
            }
        }
        if (!hasCurio)
            return false;
        for (ItemStack stack : player.inventory.items) {
            if (stack.getItem() instanceof BaseEgoWeapon baseEgoWeapon && baseEgoWeapon.weaponName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 应用装备抗性到玩家属性
     * 当装备锁定且穿戴完整套装时，将玩家的抗性属性设置为装备的抗性值
     *
     * @param player 玩家
     */
    public static void applyArmorResistances(Player player) {
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            // 检查装备是否锁定
            if (!data.isArmorLocked()) {
                // 装备未锁定，恢复原始抗性
                if (data.isResistancesApplied()) {
                    restoreOriginalResistances(player, data);
                }
                return;
            }

            // 检查是否穿戴完整套装
            String setId = getFullSetId(player);
            if (setId == null) {
                // 没有完整套装，恢复原始抗性
                if (data.isResistancesApplied()) {
                    restoreOriginalResistances(player, data);
                }
                return;
            }

            // 获取装备的抗性值
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!(chest.getItem() instanceof IEgoArmor egoArmor)) {
                return;
            }

            float redRes = egoArmor.getRedResistance();
            float whiteRes = egoArmor.getWhiteResistance();
            float blackRes = egoArmor.getBlackResistance();
            float blueRes = egoArmor.getPaleResistance();  // Pale对应Blue伤害

            // 获取玩家的抗性属性
            AttributeInstance redAttr = player.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
            AttributeInstance whiteAttr = player.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
            AttributeInstance blackAttr = player.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
            AttributeInstance blueAttr = player.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());

            if (redAttr == null || whiteAttr == null || blackAttr == null || blueAttr == null) {
                return;
            }

            // 如果还没有保存原始抗性，先保存
            if (!data.isResistancesApplied()) {
                data.saveOriginalResistances(
                        redAttr.getBaseValue(),
                        whiteAttr.getBaseValue(),
                        blackAttr.getBaseValue(),
                        blueAttr.getBaseValue()
                );
            }

            // 应用装备抗性
            redAttr.setBaseValue(redRes);
            whiteAttr.setBaseValue(whiteRes);
            blackAttr.setBaseValue(blackRes);
            blueAttr.setBaseValue(blueRes);

            // 标记抗性已应用
            data.setResistancesApplied(true);
        });
    }

    /**
     * 恢复玩家的原始抗性值
     *
     * @param player 玩家
     * @param data 公司日常数据
     */
    private static void restoreOriginalResistances(Player player, com.wzz.lobotocraft.capability.CompanyDailyData data) {
        AttributeInstance redAttr = player.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        AttributeInstance whiteAttr = player.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        AttributeInstance blackAttr = player.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        AttributeInstance blueAttr = player.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());

        if (redAttr == null || whiteAttr == null || blackAttr == null || blueAttr == null) {
            return;
        }

        // 恢复原始抗性
        redAttr.setBaseValue(data.getOriginalRedResistance());
        whiteAttr.setBaseValue(data.getOriginalWhiteResistance());
        blackAttr.setBaseValue(data.getOriginalBlackResistance());
        blueAttr.setBaseValue(data.getOriginalBlueResistance());

        // 清除抗性已应用标记
        data.setResistancesApplied(false);
    }

    /**
     * 触发套装的穿戴效果
     * 应该在tick中调用
     *
     * @param player 玩家
     */
    public static void tickSetEffects(Player player) {
        String setId = getFullSetId(player);
        if (setId == null) {
            return;  // 没有完整套装
        }

        // 对每件装备调用onWearingFullSet
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        }) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.getItem() instanceof IEgoArmor egoArmor) {
                egoArmor.onWearingFullSet(player, armor);
            }
        }
    }

    /**
     * 触发套装的受伤效果
     * 应该在受伤事件中调用
     *
     * @param player 玩家
     * @param damageType 伤害类型
     * @param damage 伤害值
     * @return 是否已被套装效果处理
     */
    public static boolean triggerDamageEffect(Player player, String damageType, float damage) {
        String setId = getFullSetId(player);
        if (setId == null) {
            return false;  // 没有完整套装
        }

        // 尝试触发装备的onDamaged效果
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof IEgoArmor egoArmor) {
            return egoArmor.onDamaged(player, damageType, damage);
        }

        return false;
    }

    /**
     * 触发套装的攻击效果
     * 应该在攻击事件中调用
     *
     * @param player 玩家
     * @param target 目标
     * @param damage 伤害
     */
    public static void triggerAttackEffect(Player player, net.minecraft.world.entity.Entity target, float damage) {
        String setId = getFullSetId(player);
        if (setId == null) {
            return;  // 没有完整套装
        }

        // 触发装备的onAttack效果
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof IEgoArmor egoArmor) {
            egoArmor.onAttack(player, target, damage);
        }
    }

    /**
     * 计算等级压制后的伤害
     *
     * @param originalDamage 原始伤害
     * @param egoLevel       装备等级
     * @param enemyLevel     异想体等级
     * @return 压制后伤害
     */
    public static float applyRiskLevelSuppression(
            float originalDamage,
            RiskLevel egoLevel,
            RiskLevel enemyLevel
    ) {
        // ZAYIN 装备不受等级压制影响
        if (egoLevel == null || enemyLevel == null || egoLevel == RiskLevel.ZAYIN) {
            return originalDamage;
        }

        int ego = egoLevel.ordinal();
        int enemy = enemyLevel.ordinal();

        // 敌人等级 >= 装备等级，不压制
        if (enemy >= ego) {
            return originalDamage;
        }

        int diff = ego - enemy;

        float multiplier;
        switch (diff) {
            case 1 -> multiplier = 0.8f;
            case 2 -> multiplier = 0.7f;
            case 3 -> multiplier = 0.6f;
            case 4 -> multiplier = 0.4f;
            default -> multiplier = 1.0f;
        }

        return originalDamage * multiplier;
    }

    /**
     * 检查玩家的装备组合（通用方法）
     * @param player 玩家
     * @param setId 套装/武器/饰品ID
     * @param needFullSet 是否需要完整套装
     * @param needWeapon 是否需要武器
     * @param needCurio 是否需要饰品
     * @return true如果满足所有条件
     */
    public static boolean hasEquipmentCombination(Player player, String setId,
                                                  boolean needFullSet,
                                                  boolean needWeapon,
                                                  boolean needCurio) {
        if (needFullSet && !isWearingFullSet(player, setId)) {
            return false;
        }

        if (needWeapon) {
            boolean hasWeapon = false;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof BaseEgoWeapon baseEgoWeapon &&
                        baseEgoWeapon.weaponName().equals(setId)) {
                    hasWeapon = true;
                    break;
                }
            }
            if (!hasWeapon) return false;
        }

        if (needCurio) {
            boolean hasCurio = false;
            for (ItemStack stack : CuriosUtil.getCuriosItems(player)) {
                if (stack.getItem() instanceof BaseEgoCurio baseEgoCurio &&
                        baseEgoCurio.curioName().equals(setId)) {
                    hasCurio = true;
                    break;
                }
            }
            return hasCurio;
        }

        return true;
    }

    /**
     * 玩家主手是否手持指定套装的 E.G.O 武器。
     * 用于:套装的攻击/伤害特效(伤害翻倍、吸血、增伤、攻速移速等)只应在
     * 实际手持对应套装武器时触发,而非仅靠背包里存在该武器。
     */
    public static boolean isHoldingWeapon(Player player, String name) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        return main.getItem() instanceof BaseEgoWeapon weapon && weapon.weaponName().equals(name);
    }

    /**
     * 玩家是否:穿戴指定套装的完整护甲 + 对应饰品 + 已使用"装备锁定"。
     * 用于正义裁决者/悔恨等套装效果的触发判定(要求装备锁定后才生效)。
     */
    public static boolean isFullSetWithCurioLocked(Player player, String name) {
        if (player == null) return false;
        if (!com.wzz.lobotocraft.util.ArmorEffectChecker.isArmorLocked(player)) return false;
        if (!isWearingFullSet(player, name)) return false;
        for (ItemStack stack : CuriosUtil.getCuriosItems(player)) {
            if (stack.getItem() instanceof BaseEgoCurio curio && curio.curioName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}