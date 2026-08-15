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
    private static final String UNIVERSAL_TAG = "Universal";

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
        return create(abnormalityCode, abnormalityName, "");
    }

    public static ItemStack create(String abnormalityCode, String abnormalityName,
                                   String abnormalityNameKey) {
        ItemStack stack = new ItemStack(ModItems.PE_BOX.get());
        CompoundTag tag = stack.getOrCreateTag();

        tag.putString("AbnormalityCode", abnormalityCode);
        tag.putString("AbnormalityName", abnormalityName);
        if (abnormalityNameKey != null && !abnormalityNameKey.isBlank()) {
            tag.putString("AbnormalityNameKey", abnormalityNameKey);
        }

        return stack;
    }

    /** Creative/debug variant that can pay for any abnormality's information. */
    public static ItemStack createUniversal() {
        ItemStack stack = new ItemStack(ModItems.PE_BOX.get());
        stack.getOrCreateTag().putBoolean(UNIVERSAL_TAG, true);
        return stack;
    }

    public static boolean isUniversal(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(UNIVERSAL_TAG);
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

    public static String getAbnormalityNameKey(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getString("AbnormalityNameKey") : "";
    }

    /**
     * 检查PE-BOX是否属于指定异想体（通过编号）
     */
    public static boolean isFromAbnormality(ItemStack stack, String abnormalityCode) {
        return isUniversal(stack) || getAbnormalityCode(stack).equals(abnormalityCode);
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

        // Consume specific PE-BOXes first; universal boxes are fallback currency.
        int remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                boolean eligible = stack.getItem() instanceof PEBoxItem
                        && isFromAbnormality(stack, abnormalityCode)
                        && (pass == 1 || !isUniversal(stack));
                if (eligible) {
                    int toRemove = Math.min(remaining, stack.getCount());
                    stack.shrink(toRemove);
                    remaining -= toRemove;
                }
            }
        }

        return remaining == 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (isUniversal(stack)) {
            tooltip.add(Component.translatable("tooltip.lobotocraft.pe_box.universal")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.lobotocraft.pe_box.universal_description")
                    .withStyle(ChatFormatting.GRAY));
            super.appendHoverText(stack, level, tooltip, flag);
            return;
        }
        if (tag != null) {
            Component displayName = Component.empty();
            boolean hasAbnormalityName = false;

            String nameKey = getAbnormalityNameKey(stack);
            if (!nameKey.isBlank()) {
                displayName = PEBoxNameResolver.resolve(nameKey, "");
                hasAbnormalityName = true;
            } else if (tag.contains("AbnormalityName") && !tag.getString("AbnormalityName").isEmpty()) {
                String name = tag.getString("AbnormalityName");
                if (!name.matches("[A-Z]-\\d{2}-\\d{2}")) {
                    displayName = PEBoxNameResolver.resolve("", name);
                    hasAbnormalityName = true;
                }
            }

            if (displayName.getString().isEmpty() && tag.contains("AbnormalityCode")) {
                displayName = Component.literal(tag.getString("AbnormalityCode"));
            }

            if (!displayName.getString().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.lobotocraft.pe_box.source", displayName)
                        .withStyle(ChatFormatting.GOLD));
            }

            if (tag.contains("AbnormalityCode") && hasAbnormalityName) {
                tooltip.add(Component.translatable("tooltip.lobotocraft.pe_box.code",
                                tag.getString("AbnormalityCode"))
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
        if (isUniversal(stack)) {
            return Component.translatable("item.lobotocraft.pe_box.universal")
                    .withStyle(ChatFormatting.YELLOW);
        }
        String nameKey = getAbnormalityNameKey(stack);
        if (!nameKey.isBlank()) {
            return Component.translatable("item.lobotocraft.pe_box.named",
                    PEBoxNameResolver.resolve(nameKey, getAbnormalityName(stack)))
                    .withStyle(ChatFormatting.YELLOW);
        }
        String abnormalityName = getAbnormalityName(stack);
        if (!abnormalityName.isEmpty()) {
            return Component.translatable("item.lobotocraft.pe_box.named",
                    PEBoxNameResolver.resolve("", abnormalityName)).withStyle(ChatFormatting.YELLOW);
        }
        return super.getName(stack).copy().withStyle(ChatFormatting.YELLOW);
    }
}
