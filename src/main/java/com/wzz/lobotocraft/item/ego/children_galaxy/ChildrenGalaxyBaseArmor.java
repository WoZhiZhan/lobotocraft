package com.wzz.lobotocraft.item.ego.children_galaxy;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.util.ClientInputUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ChildrenGalaxyBaseArmor extends BaseEgoArmor {
    public ChildrenGalaxyBaseArmor(Type type) {
        super(ModArmorMaterial.REPENTANCE, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String armorName() {
        return "children_galaxy";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String getSetId() {
        return "children_galaxy";
    }

    @Override
    public float getRedResistance() {
        return 0.8f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.8f;
    }

    @Override
    public float getBlackResistance() {
        return 1.2f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.HE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (ClientInputUtil.isShiftPressed()) {
            tooltip.add(Component.literal("§7无数泪滴仿佛从云端坠落的繁星。"));
            tooltip.add(Component.literal("§6※持有友谊之证时，穿戴全套护甲会使回复效果翻倍。"));
            tooltip.add(Component.literal("§6※玩家手持武器小小银河时回复效果提高50%。"));
            tooltip.add(Component.literal("§6※触发饰品恢复时，10x10范围内其他玩家恢复一半数值。"));
            return;
        }
        tooltip.add(Component.literal("§6※持有友谊之证时，穿戴全套护甲会使回复效果翻倍。"));
        tooltip.add(Component.literal("§6※玩家手持武器小小银河时回复效果提高50%。"));
        tooltip.add(Component.literal("§7按住<Shift>查看详情"));
    }
}
