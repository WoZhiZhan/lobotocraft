package com.wzz.lobotocraft.item.ego.army_in_black;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wzz.lobotocraft.util.MentalValueUtil.getMentalValue;

public abstract class ArmyInBlackBaseArmor extends BaseEgoArmor {

    public ArmyInBlackBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 5);
        map.put("FortitudeLevel", 5);
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
        return "army_in_black";
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
        return 0.3f;
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
        p_41423_.add(Component.literal("§6※玩家当前精神值越高，受到的伤害越低。"));
        p_41423_.add(Component.literal("§7这是一件粉红色的军装制服。"));
        p_41423_.add(Component.literal("§7它的口袋能够充分容纳穿戴着所携带的各类弹药。"));
        p_41423_.add(Component.literal("§7这件衣服的粉红色可以抚慰你的心灵，带来一种安全感。"));
        p_41423_.add(Component.literal("§7粉红为许多人带来了心灵上的慰藉。"));
    }
}