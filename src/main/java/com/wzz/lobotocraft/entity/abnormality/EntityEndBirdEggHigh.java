package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FullScreenRenderMessage;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.EndBirdEggTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class EntityEndBirdEggHigh extends AbstractAbnormality {
    public EntityEndBirdEggHigh(EntityType<? extends TamableAnimal> p_21803_, Level p_21804_) {
        super(p_21803_, p_21804_);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-02-63-1";
        this.abnormalityName = "长臂";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "WHITE";
        this.maxPEOutput = 10;
        float[] basePreferences = {0.6f, 0.3f, 0.8f, 0.4f};
        // 本能60%，洞察30%，沟通80%，压迫40%
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            EndBirdEggTracker tracker = EndBirdEggTracker.get(serverLevel);
            String eggType = EndBirdEggTracker.EGG_HIGH;
            tracker.registerEgg(this.getStringUUID(), eggType);
        }
    }

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource damageSource, float f) {
        Entity entity = damageSource.getEntity();
        if (entity instanceof LivingEntity living && !living.getMainHandItem().getItem().getClass().getName().startsWith("com.wzz.lobotocraft")
                && !(living instanceof AbstractAbnormality))
            return false;
        if (DamageHelper.isBlueDamage(damageSource))
            return false;
        return baseHurt(damageSource, f);
    }

    @Override
    public boolean onOpenManualScreen(ServerPlayer player) {
        return false;
    }

    @Override
    public boolean onOpenWorkScreen(ServerPlayer player) {
        return false;
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BaseGeoEntity.DATA_ANIMATION, "idle");
    }

    @Override
    public String getAbnormalityJadeName() {
        return abnormalityName;
    }

    @Override
    public SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.END_BIRD_EGG_HIGH.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 2400;
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) return;
        if (tickCount % 20 == 0 && !this.isDeadOrDying()) {
            if (getHealth() <= getMaxHealth() / 2) {
                setAnimation("idle2");
            } else {
                setAnimation("idle");
            }
        }
    }

    @Override
    public String name() {
        return "end_bird_egg_high";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "dj", 0, this::movementPredicate));
    }

    @Override
    public void die(DamageSource damageSource) {
        setAnimation("death");
        for (ServerPlayer player : EntityUtil.findAllPlayer(this)) {
            FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                    .showDuration(4000)
                    .fade(500, 1000)
                    .texture(ResourceUtil.createInstance("textures/gui/end_bird_cg/cg7.png"))
                    .build();
            MessageLoader.getLoader().sendToPlayer(player, msg);
        }
        EntityEndBird.endBirdEggDie(level);
        super.die(damageSource);
    }

    private PlayState movementPredicate(AnimationState<EntityEndBirdEggHigh> event) {
        if (getAnimation().equals("idle2")) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("idle2"));
        }
        if (getAnimation().equals("death")) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("death"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 9900.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FLYING_SPEED, 0.0D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), -1D);
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {

    }
}