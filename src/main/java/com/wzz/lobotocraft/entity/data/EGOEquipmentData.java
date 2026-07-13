package com.wzz.lobotocraft.entity.data;

import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * E.G.O装备完整数据
 * 包含图标、属性、描述等所有显示信息
 * <p>
 * 注意：record 本身不做任何改动（改了所有异想体的构造调用都得跟着改）。
 * 「一个异想体给多个武器/饰品」通过 IAbnormality 上的
 * getEGOWeaponStacks() / getEGOGiftStacks() 覆写实现，见下方工具方法。
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
     * @param itemId              物品注册ID。多武器异想体这里填「主武器」的ID（图鉴显示用），
     *                            实际发放的物品由 IAbnormality#getEGOWeaponStacks() 决定
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

    /* =========================================================
     *  工具方法：由注册ID构造 ItemStack
     * ========================================================= */

    /**
     * 由物品注册ID构造一个 ItemStack。
     * 物品不存在时返回 ItemStack.EMPTY（不会抛异常）。
     *
     * @param itemId 物品注册ID，如 "butterfly_funeral_weapon"（默认使用本 mod 的命名空间）
     */
    public static ItemStack stack(String itemId) {
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(ResourceUtil.createInstance(itemId));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * 由多个物品注册ID构造 ItemStack 列表，自动跳过不存在的物品。
     */
    public static List<ItemStack> stacks(String... itemIds) {
        List<ItemStack> list = new ArrayList<>();
        if (itemIds == null) return list;
        for (String id : itemIds) {
            ItemStack stack = stack(id);
            if (!stack.isEmpty()) list.add(stack);
        }
        return list;
    }
}