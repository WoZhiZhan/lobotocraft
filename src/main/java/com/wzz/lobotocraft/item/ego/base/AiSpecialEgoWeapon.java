package com.wzz.lobotocraft.item.ego.base;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Explicit contract for an E.G.O. weapon whose right-click ability may be used by AI employees.
 * The weapon remains responsible for validating and applying its own cooldown and effects.
 */
public interface AiSpecialEgoWeapon {
    double aiSpecialAttackRange();

    default boolean isAiSpecialAttackReady(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
                || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        return !(stack.getItem() instanceof BaseEgoWeapon weapon)
                || !weapon.isOnCooldown(player, stack);
    }

    default boolean aiSpecialRequiresCrouch() {
        return false;
    }

    default boolean aiSpecialRequiresLineOfSight() {
        return true;
    }
}
