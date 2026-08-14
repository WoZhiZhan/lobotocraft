package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class EntityCENSOREDContained extends AbstractAbnormality {
    private static final int SACRIFICE_ANIMATION_TICKS = 91;

    private int sacrificeTimer = 0;

    public EntityCENSOREDContained(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-03-89";
        this.abnormalityName = "§4CENSORED";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "BLACK";
        this.maxPEOutput = 28;

        float[] basePreferences = {0.35f, 0.40f, 0.25f, 0.10f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    public String name() {
        return "o_03_89";
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        setNoAi(true);
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);

        if (sacrificeTimer > 0) {
            sacrificeTimer--;
            if (sacrificeTimer == 0) {
                setAnimation("idle");
            }
        }
    }

    public void playSacrifice() {
        if (this.level().isClientSide) return;
        this.sacrificeTimer = SACRIFICE_ANIMATION_TICKS;
        this.setAnimation("sacrifice");
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.CENSORED_SACRIFICE.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), 23 + this.random.nextInt(4));
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityCENSOREDContained> event) {
        if ("sacrifice".equals(getAnimation())) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("sacrifice"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1500.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(com.wzz.lobotocraft.init.ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(com.wzz.lobotocraft.init.ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.4D)
                .add(com.wzz.lobotocraft.init.ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(com.wzz.lobotocraft.init.ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 0.8D);
    }
}
