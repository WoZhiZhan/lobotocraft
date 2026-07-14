package com.wzz.lobotocraft.item.ego.redhat_mercenary;

import com.wzz.lobotocraft.color.ExtendedColor;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
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
            p_41423_.add(Component.literal("※持有者攻击时会在一定时间持续造成红色伤害。如果持有者的生命值小于或等于50%，武器的伤害会额外增加。但是，持有者在该状态下攻击时会对其他玩家造成无差别伤害。")
                    .withStyle(ExtendedColor.ORANGE.toStyle()));
            p_41423_.add(Component.literal("※持有者攻击到目标时会为其附加一个每秒受到3点物理红色伤害的dot，持续5秒，不可叠加，每次造成伤害刷新持续时间。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            p_41423_.add(Component.literal("※该武器右键可以发起一次特殊攻击，造成3x3范围的30点红色伤害。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            p_41423_.add(Component.literal("※在持有者的生命值低于50%时，玩家周围会有深蓝色渲染特效环绕，武器造成的红色伤害提高100%，普通攻击命中会造成4x4的范围伤害，此时这把武器造成伤害将不分敌我。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            return;
        }
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7这件武器看起来就像那只邪恶巨狼的爪子。"));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7这些爪子曾经撕裂，割破了无数生物的内脏。"));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7爪子能够削开目标的血肉，让血液喷涌而出。"));
    }
}