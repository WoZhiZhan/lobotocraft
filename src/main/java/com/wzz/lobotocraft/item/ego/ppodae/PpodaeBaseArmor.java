package com.wzz.lobotocraft.item.ego.ppodae;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PpodaeBaseArmor extends BaseEgoArmor {

    public PpodaeBaseArmor(Type type) {
        super(ModArmorMaterial.REPENTANCE, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.TETH;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "ppodae";
    }

    @Override
    public String getSetId() {
        return "ppodae";
    }

    @Override
    public float getRedResistance() {
        return 0.8f;
    }

    @Override
    public float getWhiteResistance() {
        return 1.5f;
    }

    @Override
    public float getBlackResistance() {
        return 0.8f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§6※这件护甲可以额外减少玩家受到的物理伤害"));
        p_41423_.add(Component.literal("§7我想，从有着可爱外表的异想体中提取出来的武器应该不会很厉害吧？"));
        p_41423_.add(Component.literal("§7好吧，我错了，这只爪子不仅结实耐用，还很可爱！！！"));
        p_41423_.add(Component.literal("§7咳咳，如果你过度依赖这把武器，你会意识到寄托其中的残暴野性正在你的体内慢慢苏醒。"));
        p_41423_.add(Component.literal("§7但是但是...这些果冻状的爪爪垫摸起来真是让人欲罢不能啊，简直爽到~"));
    }
}