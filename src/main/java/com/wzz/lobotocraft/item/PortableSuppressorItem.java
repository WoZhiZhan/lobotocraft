package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.item.api.ProhibitDiscardingItem;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class PortableSuppressorItem extends ProhibitDiscardingItem {
    public PortableSuppressorItem() {
        super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC).durability(120));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int p_41407_, boolean p_41408_) {
        super.inventoryTick(stack, level, entity, p_41407_, p_41408_);
        if (entity instanceof Player player && level.dimension != ModDimensions.LOBOTO_KEY) {
            if (player.tickCount % 160 == 0 && !player.isDeadOrDying() && !player.isCreative()) {
                if (stack.getDamageValue() >= 120) {
                    EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage(player, "red"), 3f + player.random.nextInt()));
                    EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage(player, "white"), 3f + player.random.nextInt()));
                    EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage(player, "black"), 3f + player.random.nextInt()));
                    EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage(player, "blue"), 3f + player.random.nextInt()));
                    stack.setDamageValue(120);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 255));
                    return;
                }
                stack.setDamageValue(stack.getDamageValue() + 1);
            }
        }
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        ItemStack result = new ItemStack(this);
        result.setDamageValue(120);
        return result;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @org.jetbrains.annotations.Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7玩家外出时携带，可以保护玩家提供在公司外不被负面能量伤害"));
    }

    @Override
    public int getDamage(ItemStack stack) {
        int damage = super.getDamage(stack);
        if (damage > 120)
            damage = 120;
        return damage;
    }
}