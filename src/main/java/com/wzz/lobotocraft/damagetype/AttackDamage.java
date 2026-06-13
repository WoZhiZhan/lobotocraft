package com.wzz.lobotocraft.damagetype;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

public class AttackDamage extends DamageSource {
    private final Entity attacker;

    public AttackDamage(Holder<DamageType> type, Entity attacker) {
        super(type, attacker);
        this.attacker = attacker;
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
