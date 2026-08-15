package com.wzz.lobotocraft.item.ego.redhat_mercenary;

import com.wzz.lobotocraft.color.ExtendedColor;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class RedhatMercenaryWeapon extends BaseEgoWeapon {
    public RedhatMercenaryWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(11),
                MathUtil.toSpeedModifier(1.2f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 3);
        map.put("JusticeLevel", 3);
        map.put("EmployeeLevel", 3);
        return map;
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public String weaponName() {
        return "redhat_mercenary";
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (ClientInputUtil.isShiftPressed()) {
            for (int index = 1; index <= 3; index++) {
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_hood_weapon.detail." + index,
                        ExtendedColor.PINK.toStyle());
            }
            return;
        }
        EgoTooltipUtil.blank(p_41423_);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_hood_weapon.summary",
                ExtendedColor.ORANGE.toStyle());
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.blank(p_41423_);
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_hood_weapon.story." + index);
        }
    }
}
