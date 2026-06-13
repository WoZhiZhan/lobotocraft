package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * PE-BOX 物品
 * NBT标签：
 * - AbnormalityCode: 异想体编号（如 "O-03-03"）
 * - AbnormalityName: 异想体名称（如 "一罪与百善"）
 */
public class PEBoxItem extends Item {

    public PEBoxItem() {
        super(new Properties().stacksTo(64));
    }

    /**
     * 创建带有异想体信息的PE-BOX
     *
     * @param abnormalityCode 异想体编号（如 "O-03-03"）
     * @param abnormalityName 异想体名称（如 "一罪与百善"）
     */
    public static ItemStack create(String abnormalityCode, String abnormalityName) {
        ItemStack stack = new ItemStack(ModItems.PE_BOX.get());
        CompoundTag tag = stack.getOrCreateTag();

        tag.putString("AbnormalityCode", abnormalityCode);
        tag.putString("AbnormalityName", abnormalityName);

        return stack;
    }

    /**
     * 获取异想体编号
     */
    public static String getAbnormalityCode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("AbnormalityCode")) {
            return tag.getString("AbnormalityCode");
        }
        return "";
    }

    /**
     * 获取异想体名称
     */
    public static String getAbnormalityName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("AbnormalityName")) {
            return tag.getString("AbnormalityName");
        }
        return "";
    }

    /**
     * 检查PE-BOX是否属于指定异想体（通过编号）
     */
    public static boolean isFromAbnormality(ItemStack stack, String abnormalityCode) {
        return getAbnormalityCode(stack).equals(abnormalityCode);
    }

    /**
     * 统计背包中指定异想体的PE-BOX数量
     */
    public static int countPEBoxes(net.minecraft.world.entity.player.Inventory inventory,
                                   String abnormalityCode) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof PEBoxItem &&
                    isFromAbnormality(stack, abnormalityCode)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 消耗指定数量的PE-BOX
     *
     * @return 是否成功消耗
     */
    public static boolean consumePEBoxes(net.minecraft.world.entity.player.Inventory inventory,
                                         String abnormalityCode, int amount) {
        // 先检查是否有足够数量
        if (countPEBoxes(inventory, abnormalityCode) < amount) {
            return false;
        }

        // 消耗物品
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof PEBoxItem &&
                    isFromAbnormality(stack, abnormalityCode)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }

        return remaining == 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            String displayName = "";

            // 优先显示真实名字
            if (tag.contains("AbnormalityName") && !tag.getString("AbnormalityName").isEmpty()) {
                String name = tag.getString("AbnormalityName");
                // 检查是否是编号格式（如T-01-54、O-03-03等）
                // 格式: 字母-数字数字-数字数字
                if (!name.matches("[A-Z]-\\d{2}-\\d{2}")) {
                    displayName = name;
                }
            }

            // 如果没有真实名字（或名字是编号格式），尝试使用编号
            if (displayName.isEmpty() && tag.contains("AbnormalityCode")) {
                displayName = tag.getString("AbnormalityCode");
            }

            // 显示"来自"信息
            if (!displayName.isEmpty()) {
                tooltip.add(Component.literal("来自: " + displayName)
                        .withStyle(ChatFormatting.GOLD));
            }

            // 如果显示的是真实名字，则同时显示编号作为副标题
            if (tag.contains("AbnormalityCode") &&
                    tag.contains("AbnormalityName") &&
                    !displayName.equals(tag.getString("AbnormalityCode"))) {
                tooltip.add(Component.literal("编号: " + tag.getString("AbnormalityCode"))
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            // 没有NBT数据，显示默认提示
            tooltip.add(Component.translatable("lobotocraft.item.pe_box.1"));
            tooltip.add(Component.translatable("lobotocraft.item.pe_box.2"));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public Component getName(ItemStack stack) {
        String abnormalityName = getAbnormalityName(stack);
        if (!abnormalityName.isEmpty()) {
            return Component.literal(abnormalityName + " PE-BOX")
                    .withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal("PE-BOX").withStyle(ChatFormatting.YELLOW);
    }
}