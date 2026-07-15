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
            p_41423_.add(Component.literal("※※这把武器持有者shift右键使用火铳开火时射出黑色的粒子特效，飞行15格，距离内的所有目标造成一次11-13点物理伤害。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            p_41423_.add(Component.literal("※当且仅当持有者的生命值低于50%时进入暴怒状态，手持武器切肉刀的玩家伤害会额外增加50%。切肉刀造成伤害时同步身前2x3范围内的所有生物。但是持有者在该状态下攻击时会对其他玩家造成无差别伤害。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            p_41423_.add(Component.literal("※当玩家使用火铳攻击目标时，会使目标陷入“猎物”效果，猎物会获得发光效果。玩家对猎物造成的红色伤害提高2-3点。猎物最多存在一只，效果持续10秒，每次火铳攻击会刷新持续时间。")
                    .withStyle(ExtendedColor.PINK.toStyle()));
            return;
        }
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("※如果持有者的生命值低于50%，武器的伤害会额外增加50%。但是，持有者在该状态下攻击时会对其他员工造成无差别伤害。火铳攻击会标记猎物，对猎物造成的伤害提高。")
                .withStyle(ExtendedColor.ORANGE.toStyle()));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7如果我左手紧握着钢铁制成的刀刃，右手紧握着填满火药的短铳，那么这里又有什么好令我畏惧的？"));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7因为狂怒而盲目，失去理智，对杀戮果断而坚决，那也总比因为懦弱而恐惧要好太多！"));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7让我们期望这篇幼稚的童话早日结束吧。"));
    }
}