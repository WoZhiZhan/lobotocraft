package com.wzz.lobotocraft.entity.projectile;

import net.minecraft.world.entity.LivingEntity;

/** Custom projectile entities preserve their own steering/targeting when reflected. */
public interface DeflectableAttack {
    void deflect(LivingEntity defender);
}
