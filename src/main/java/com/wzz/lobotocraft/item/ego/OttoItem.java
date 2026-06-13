package com.wzz.lobotocraft.item.ego;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class OttoItem extends BaseEgoWeapon {

    public OttoItem() {
        super(
            new Tier(),
            1,
            -2.5f,
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

    /**
     * 忏悔武器材质
     */
    private static class Tier implements net.minecraft.world.item.Tier {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 4.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 0.0F;
        }

        @Override
        public int getLevel() {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 14;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }
}