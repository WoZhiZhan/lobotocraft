package com.wzz.lobotocraft.damagetype;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class AttackDamage extends DamageSource {
    /** Permission and delivery belong to this attack, not the actor's later state. */
    public enum Targeting { NORMAL, INDISCRIMINATE }
    public enum Delivery { DIRECT, RANGED }
    private final Entity directAttacker;
    private final Entity causingEntity;
    private final Targeting targeting;
    private final Delivery delivery;

    public AttackDamage(Holder<DamageType> type, Entity attacker) {
        this(type, attacker, attacker);
    }

    public AttackDamage(Holder<DamageType> type, Entity directAttacker, Entity causingEntity) {
        this(type, directAttacker, causingEntity, Targeting.NORMAL, Delivery.DIRECT);
    }

    public AttackDamage(Holder<DamageType> type, Entity directAttacker, Entity causingEntity,
                        Targeting targeting, Delivery delivery) {
        super(type, directAttacker, causingEntity);
        this.directAttacker = directAttacker;
        this.causingEntity = causingEntity;
        this.targeting = targeting;
        this.delivery = delivery;
    }

    public Targeting targeting() { return targeting; }
    public Delivery delivery() { return delivery; }

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
