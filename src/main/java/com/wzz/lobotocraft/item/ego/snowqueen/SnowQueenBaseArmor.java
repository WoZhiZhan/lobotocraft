package com.wzz.lobotocraft.item.ego.snowqueen;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SnowQueenBaseArmor extends BaseEgoArmor {

    public SnowQueenBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 2);
        return map;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.HE;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "snowqueen";
    }

    @Override
    public String getSetId() {
        return "snowqueen";
    }

    @Override
    public float getRedResistance() {
        return 1.3f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.6f;
    }

    @Override
    public float getBlackResistance() {
        return 0.8f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§6※玩家在细雪中可以正常行走，不会冻伤"));
        p_41423_.add(Component.literal("§7为了使人待在冰雪皇宫中，一件能够保持温暖的斗篷是很有必要的。"));
        p_41423_.add(Component.literal("§7由冰雪所编成的斗篷终有融化之日。"));
        p_41423_.add(Component.literal("§7而当冰雪消融的那天，她冰封的内心也将一并融化。"));
    }
}