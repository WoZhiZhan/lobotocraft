package com.wzz.lobotocraft.entity.control;

import com.wzz.lobotocraft.entity.abnormality.EntityRedShoes;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Facility-facing interface for a controlled combatant.
 *
 * <p>Control-source implementations keep their unique movement, attacks and
 * presentation. Generic facility modules use this interface for hostility,
 * command restrictions and lifecycle recovery without depending on a specific
 * abnormality class.</p>
 */
public final class HostileControlState {
    private HostileControlState() {
    }

    public static boolean isActive(@Nullable LivingEntity entity) {
        return entity != null && (EntityRedShoes.isRedShoesControlled(entity)
                || SingingMachineControl.isActive(entity));
    }

    public static void clearForRecovery(@Nullable LivingEntity entity) {
        if (entity == null) return;
        EntityRedShoes.clearControlForRecovery(entity);
        SingingMachineControl.clearForRecovery(entity);
    }
}
