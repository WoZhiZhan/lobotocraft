package com.wzz.lobotocraft.item.ego.smiling_corpse_mountain;

import com.wzz.lobotocraft.color.ExtendedColor;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SmilingCorpseMountainBaseArmor extends BaseEgoArmor {
    private static final String CLEARED_TOMBSTONES_TAG = "ClearedTombstones";
    private static final String MAX_TAG = "MaxClearedTombstones";
    public SmilingCorpseMountainBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    // 获取清理数量
    public static int getClearedCount(ItemStack armorStack) {
        if (armorStack.isEmpty() || !(armorStack.getItem() instanceof SmilingCorpseMountainBaseArmor)) {
            return 0;
        }
        CompoundTag tag = armorStack.getOrCreateTag();
        return tag.getInt(CLEARED_TOMBSTONES_TAG);
    }

    // 增加清理数量
    public static void incrementClearedCount(ItemStack armorStack) {
        if (armorStack.isEmpty() || !(armorStack.getItem() instanceof SmilingCorpseMountainBaseArmor)) {
            return;
        }
        CompoundTag tag = armorStack.getOrCreateTag();
        int current = tag.getInt(CLEARED_TOMBSTONES_TAG);
        tag.putInt(CLEARED_TOMBSTONES_TAG, current + 1);
    }

    // 重置计数
    public static void resetClearedCount(ItemStack armorStack) {
        CompoundTag tag = armorStack.getOrCreateTag();
        tag.putInt(CLEARED_TOMBSTONES_TAG, 0);
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 5);
        map.put("TemperanceLevel", 5);
        return map;
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.ALEPH;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "smiling_corpse_mountain";
    }

    @Override
    public String getSetId() {
        return armorName();
    }

    @Override
    public float getRedResistance() {
        return 0.5f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.5f;
    }

    @Override
    public float getBlackResistance() {
        return 0.2f;
    }

    @Override
    public float getPaleResistance() {
        return 1.0f;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(stack, p_41422_, p_41423_, p_41424_);
        int cleared = getClearedCount(stack);
        p_41423_.add(Component.literal("已清理石碑数量：").withStyle(ExtendedColor.PINK.toStyle())
                .append(Component.literal(String.valueOf(cleared))).withStyle(ExtendedColor.GREEN.toStyle()));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§6※穿戴者可以通过shift右键清理石碑来获得加成。"));
        p_41423_.add(Component.literal("§7护甲上嵌入了一张熟悉的面孔...穿戴者会在瞬间感受到死亡之重。"));
        p_41423_.add(Component.literal("§7死尸们能化解外界的伤害。"));
        p_41423_.add(Component.literal("§7“我经常听到那些怪异的呻吟，但我只能选择忽视，因为我无能为力。”"));
    }
}