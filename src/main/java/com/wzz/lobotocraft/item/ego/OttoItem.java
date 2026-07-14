package com.wzz.lobotocraft.item.ego;

import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class OttoItem extends BaseEgoWeapon {

    public OttoItem() {
        super(
                new ModTier.WeaponTier(),
                1,
                -1.5f,
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "otto";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player attacker, Entity target) {
        boolean leftClick = super.onLeftClickEntity(stack, attacker, target);
        if (!leftClick) return false;
        if (!attacker.level.isClientSide) {
            DamageSource src = DamageHelper.getDamage(attacker, "red");
            float damage = 2.0F + attacker.random.nextInt(2) + 1;
            target.hurt(src, damage);
        }
        stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }
}