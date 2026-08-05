package com.wzz.lobotocraft.damagetype;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class AttackDamage extends DamageSource {
    private final Entity attacker;

    public AttackDamage(Holder<DamageType> type, Entity attacker) {
        super(type, attacker);
        this.attacker = attacker;
    }

    @Override
    public @NotNull Component getLocalizedDeathMessage(LivingEntity victim) {
        if (attacker != null) {
            return Component.translatable("death.attack.mob", victim.getDisplayName(), attacker.getDisplayName());
        }
        return Component.translatable("death.attack.generic", victim.getDisplayName());
    }

    @Override
    public Entity getDirectEntity() {
        return attacker;
    }

    @Override
    public Entity getEntity() {
        return attacker;
    }
}
