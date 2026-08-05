package com.wzz.lobotocraft.item.ego.base;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Explicit contract for an E.G.O. weapon whose normal AI attack is performed through use().
 * Special-only right-click abilities must not implement this interface.
 */
public interface AiRangedEgoWeapon {
    double aiAttackRange();

    double aiMinSpacing();

    double aiMaxSpacing();

    double aiPreferredSpacing();

    default boolean aiRequiresCrouch() {
        return false;
    }

    /** Gives target-aware weapons the AI's actual hostile target instead of only its view angle. */
    default InteractionResult useForAi(ServerPlayer player, ItemStack stack,
                                       InteractionHand hand, LivingEntity target) {
        return stack.getItem().use(player.level(), player, hand).getResult();
    }
}
