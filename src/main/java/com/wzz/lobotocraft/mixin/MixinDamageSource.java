package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.mixinaccess.IDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DamageSource.class)
public class MixinDamageSource implements IDamageSource {
    @Unique
    private String lobotocraft$damageType = null;

    @Override
    public String getDamageType() {
        return this.lobotocraft$damageType;
    }

    @Override
    public void setDamageType(String damageType) {
        this.lobotocraft$damageType = damageType;
    }
}