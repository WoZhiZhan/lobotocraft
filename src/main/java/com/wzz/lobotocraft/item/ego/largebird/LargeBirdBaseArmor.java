package com.wzz.lobotocraft.item.ego.largebird;

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

public abstract class LargeBirdBaseArmor extends BaseEgoArmor {

    public LargeBirdBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 3);
        map.put("PrudenceLevel", 3);
        map.put("EmployeeLevel", 4);
        return map;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.WAW;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "largebird";
    }

    @Override
    public String getSetId() {
        return "largebird";
    }

    @Override
    public float getRedResistance() {
        return 0.8f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.7f;
    }

    @Override
    public float getBlackResistance() {
        return 0.4f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7每当有一只黑森林里的动物被它“拯救”时，它的身上就会多出一颗令人毛骨悚然的眼睛。"));
        p_41423_.add(Component.literal("§7这件护甲上大小不一却又无比密集的眼睛更是能体现出它对自己所作所为的骄傲。"));
        p_41423_.add(Component.literal("§7那些明亮而又炙热的眼睛总是同时注视着一处。"));
    }
}