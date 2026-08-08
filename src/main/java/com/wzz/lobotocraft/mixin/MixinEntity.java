package com.wzz.lobotocraft.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Deliberately contains no position hooks. Blocking Entity's low-level position
 * APIs also blocked respawn, cross-dimension transfer and server corrections.
 */
@Mixin(Entity.class)
public abstract class MixinEntity {
}
