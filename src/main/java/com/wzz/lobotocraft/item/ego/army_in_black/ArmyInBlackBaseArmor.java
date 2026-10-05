package com.wzz.lobotocraft.item.ego.army_in_black;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
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
    public static boolean hasFullSanityCap(Player player) {
        return EgoArmorHelper.isFullEGO(player, "army_in_black")
                && getMentalValue(player) >= com.wzz.lobotocraft.util.MentalValueUtil
                .getEffectiveMaxMentalValue(player);
    }

    public static float capFullSanityHit(Player player, float damage) {
        return hasFullSanityCap(player) ? Math.min(damage, player.getMaxHealth() * .3F) : damage;
    }

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
        return 1.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.army_in_black_armor.detail");
        for (int index = 1; index <= 4; index++) {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.army_in_black_armor.story." + index);
        }
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.army_in_black_armor.resistance.red", getRedResistance());
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.army_in_black_armor.resistance.white", getWhiteResistance());
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.army_in_black_armor.resistance.black", getBlackResistance());
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.army_in_black_armor.resistance.pale", getPaleResistance());
    }
}
