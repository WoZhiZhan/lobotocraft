package com.wzz.lobotocraft.item.ego.wingbeat;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class WingBeatBaseArmor extends BaseEgoArmor {

    public WingBeatBaseArmor(Type type) {
        super(ModArmorMaterial.REPENTANCE, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.ZAYIN;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "wingbeat";
    }

    @Override
    public String getSetId() {
        return "wingbeat";
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
        return 1.0f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7这件护甲闪耀着和精灵们身上相同的，苍白的光。"));
        p_41423_.add(Component.literal("§7这件护甲并不像精灵的羽翼那样轻薄，而要沉重许多。"));
    }
}