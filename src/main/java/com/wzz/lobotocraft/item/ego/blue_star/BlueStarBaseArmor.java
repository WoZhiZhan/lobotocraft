package com.wzz.lobotocraft.item.ego.blue_star;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.util.ClientInputUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 新星之声 护甲基类(ALEPH)。
 * 三件(胸甲/护腿/靴子)使用独立模型，共用一张贴图。
 * 抗性:红/白/黑 0.4(极高减伤)、蓝 1.0(一般)。
 * 装备要求:谨慎V 自律V 等级V。
 * 特殊效果:每5秒为同一房间内所有员工(含自己)恢复5点精神值(对恐慌玩家无效)——由 BlueStarSetEvent 处理。
 */
public abstract class BlueStarBaseArmor extends BaseEgoArmor {

    public BlueStarBaseArmor(Type type) {
        super(ModArmorMaterial.BLUE_STAR, type, new Properties().stacksTo(1).fireResistant());
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
    public boolean useSeparateModel() {
        return true;
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
    }

    @Override
    public String armorName() {
        return "blue_star";
    }

    @Override
    public String getSetId() {
        return "blue_star";
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("PrudenceLevel", 5);   // 谨慎 V
        map.put("TemperanceLevel", 5); // 自律 V
        map.put("EmployeeLevel", 5);   // 等级 V
        return map;
    }

    @Override
    public float getRedResistance() {
        return 0.4f;  // 物理抗性极高
    }

    @Override
    public float getWhiteResistance() {
        return 0.4f;  // 精神抗性极高
    }

    @Override
    public float getBlackResistance() {
        return 0.4f;  // 侵蚀抗性极高
    }

    @Override
    public float getPaleResistance() {
        return 1.0f;  // 灵魂(蓝)抗性一般
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (ClientInputUtil.isShiftPressed()) {
            tooltip.add(Component.literal("§7在这件护甲的某处，有一颗心脏正闪烁着令人着迷的光芒。"));
            tooltip.add(Component.literal("§7那光辉时而隐约暗淡，时而绚烂无比。"));
            tooltip.add(Component.literal("§7如果仔细地凝视它，那你必会醒悟——我们终有一天会回到那个地方。"));
        } else {
            tooltip.add(Component.literal("§6每5秒为同一房间内的所有员工（包括自己）恢复5点精神值。（对陷入恐慌玩家无效）"));
            tooltip.add(Component.literal("§7<按Shift查看详细信息>"));
            tooltip.add(Component.literal("§c红色伤害：0.4"));
            tooltip.add(Component.literal("§f白色伤害：0.4"));
            tooltip.add(Component.literal("§5黑色伤害：0.4"));
            tooltip.add(Component.literal("§b蓝色伤害：1.0"));
        }
    }
}
