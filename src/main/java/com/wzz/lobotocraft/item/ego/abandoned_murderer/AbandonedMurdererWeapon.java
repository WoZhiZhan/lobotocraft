package com.wzz.lobotocraft.item.ego.abandoned_murderer;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AbandonedMurdererWeapon extends BaseEgoWeapon {
    public AbandonedMurdererWeapon() {
        super(
                new ModTier.WeaponTier(),
                2,
                -3.2f,
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "abandoned_murderer";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (entity instanceof LivingEntity living) {
            living.hurt(DamageHelper.getDamage(player, "red"), 15f);
            player.playSound(ModSounds.ABANDONED_MURDERER_ATTACK.get());
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        for (int index = 1; index <= 6; index++) {
            EgoTooltipUtil.add(components, "tooltip.lobotocraft.regret_weapon.story." + index,
                    ChatFormatting.GRAY);
        }
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.regret_weapon.damage",
                ChatFormatting.RED);
    }
}
