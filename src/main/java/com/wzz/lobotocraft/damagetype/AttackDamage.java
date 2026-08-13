package com.wzz.lobotocraft.damagetype;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class AttackDamage extends DamageSource {
    private final Entity directAttacker;
    private final Entity causingEntity;

    public AttackDamage(Holder<DamageType> type, Entity attacker) {
        this(type, attacker, attacker);
    }

    public AttackDamage(Holder<DamageType> type, Entity directAttacker, Entity causingEntity) {
        super(type, directAttacker, causingEntity);
        this.directAttacker = directAttacker;
        this.causingEntity = causingEntity;
    }

    @Override
    public @NotNull Component getLocalizedDeathMessage(LivingEntity victim) {
        if (directAttacker != null) {
            return Component.translatable("death.attack.mob", victim.getDisplayName(),
                    directAttacker.getDisplayName());
        }
        return Component.translatable("death.attack.generic", victim.getDisplayName());
    }

    @Override
    public Entity getDirectEntity() {
        return directAttacker;
    }

    @Override
    public Entity getEntity() {
        return causingEntity;
    }
}
