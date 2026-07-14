package com.wzz.lobotocraft.item.ego.wingbeat;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WingBeatWeapon extends BaseEgoWeapon {
    public WingBeatWeapon() {
        super(new ModTier.WeaponTier(),
                5,
                -3.0f,
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "wingbeat";
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            stack.getOrCreateTag().putInt("UseTick", stack.getOrCreateTag().getInt("UseTick") - 1);
        }
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        player.playSound(ModSounds.WINGBEAT_WEAPON.get());
        entity.hurt(DamageHelper.getDamage(player, "red"), 5 + player.random.nextInt(2) + 1);
        return true;
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        components.add(Component.literal("§7这把武器闪耀着和精灵们身上相同的，苍白的光。"));
        components.add(Component.literal("§7这把武器并不像精灵的羽翼那样轻薄，而要沉重许多。"));
    }
}