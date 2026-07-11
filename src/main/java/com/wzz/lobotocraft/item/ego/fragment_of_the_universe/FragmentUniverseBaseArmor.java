package com.wzz.lobotocraft.item.ego.fragment_of_the_universe;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class FragmentUniverseBaseArmor extends BaseEgoArmor {

    public FragmentUniverseBaseArmor(Type type) {
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
        return "fragment_of_the_universe";
    }

    @Override
    public String getSetId() {
        return "fragment_of_the_universe";
    }

    @Override
    public float getRedResistance() {
        return 1.0f;
    }

    @Override
    public float getWhiteResistance() {
        return 1.2f;
    }

    @Override
    public float getBlackResistance() {
        return 0.6f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7不要想着去理解它，只是这样穿着它就可以了。"));
        p_41423_.add(Component.literal("§7据穿戴者们反馈，这件E.G.O护甲会使他们看到一些被他们遗忘的事物。"));
        p_41423_.add(Component.literal("§7但是没人能确定穿戴者会看到些什么，因为每一位员工都看到了不同的东西。"));
        p_41423_.add(Component.literal("§7该异想体的核心形式在每次提取过程中都发生了变化。"));
        p_41423_.add(Component.literal("§7经过无数次尝试，才稳定成现在的模样。"));
    }
}