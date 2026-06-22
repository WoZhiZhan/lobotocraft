package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.effect.QueenBeeSporeEffect;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EntityQueenBee extends AbstractAbnormality {
    private static final int SPORE_DURATION = 20 * 20;
    private static final int SPORE_RELEASE_TICKS = 28;

    private int sporeReleaseTimer = 0;
    private boolean sporesApplied = false;

    public EntityQueenBee(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "T-04-50";
        this.abnormalityName = "蜂后";
        this.riskLevel = RiskLevel.WAW;
        this.damageType = "RED";
        this.maxPEOutput = 22;

        this.workPreferences = new float[]{0.45f, 0.55f, 0.40f, 0.0f};
        this.fullWorkPreferences = new float[][]{
                {0.0f, 0.0f, 0.45f, 0.45f, 0.50f},
                {0.0f, 0.0f, 0.55f, 0.55f, 0.60f},
                {0.0f, 0.0f, 0.40f, 0.40f, 0.40f},
                {0.0f, 0.0f, 0.00f, 0.00f, 0.00f}
        };
        initializeQliphothCounter(1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        setNoAi(true);

        if (sporeReleaseTimer > 0) {
            sporeReleaseTimer--;
            if (!sporesApplied && sporeReleaseTimer <= 16) {
                sporesApplied = true;
                releaseSpores();
            }
            if (sporeReleaseTimer <= 0) {
                setAnimation("idle");
                setQliphothCounter(getMaxQliphothCounter());
                sporesApplied = false;
            }
        }
    }

    @Override
    public void onNormalWork(ServerPlayer player) {
        if (this.random.nextFloat() < 0.50f) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        if (this.random.nextFloat() < 0.70f) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (getQliphothCounter() <= 0 && sporeReleaseTimer <= 0) {
            startSporeRelease();
        }
    }

    @Override
    public void onQliphothMeltdown() {
        startSporeRelease();
    }

    private void startSporeRelease() {
        if (this.level().isClientSide || sporeReleaseTimer > 0) {
            return;
        }
        setAnimation("skill");
        sporeReleaseTimer = SPORE_RELEASE_TICKS;
        sporesApplied = false;
        this.level().playSound(null, this.blockPosition(), ModSounds.QUEEN_BEE_SPORE.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void releaseSpores() {
        List<LivingEntity> targets = findSporeTargets();
        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(ModEffects.QUEEN_BEE_SPORE.get(),
                    SPORE_DURATION, 0, false, true, true));
            ParticleUtil.spawnParticles(target, ParticleUtil.getDustParticle(1.0f, 0.78f, 0.08f, 1.4f), 18, 0.03D);
            if (target instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("§e蜂后的孢子附着在了你的身上。"));
            }
        }
    }

    private List<LivingEntity> findSporeTargets() {
        AABB range = this.getBoundingBox().inflate(30.0D);
        List<LivingEntity> targets = new ArrayList<>();
        targets.addAll(this.level().getEntitiesOfClass(EntityClerk.class, range, QueenBeeSporeEffect::canInfect));
        targets.addAll(this.level().getEntitiesOfClass(Villager.class, range, QueenBeeSporeEffect::canInfect));
        targets.addAll(this.level().getEntitiesOfClass(ServerPlayer.class, range, QueenBeeSporeEffect::canInfect));
        targets.sort(Comparator.comparingDouble(this::distanceToSqr));
        if (targets.size() > 6) {
            return targets.subList(0, 6);
        }
        return targets;
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    @Override
    public int getBasicInfoCost() {
        return 20;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 20;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 7;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 6;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 4.0F + this.random.nextInt(3);
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
    }

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.QUEEN_BEE_IDLE.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 120;
    }

    @Override
    public double getAbnormalityAmbientSoundRange() {
        return 12.0D;
    }

    @Override
    public SoundSource getAbnormalityAmbientSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.03f, 0),
                new ObservationLevelBonus(0.0f, 5),
                new ObservationLevelBonus(0.03f, 0, true, true, false),
                new ObservationLevelBonus(0.0f, 5, false, false, true)
        };
    }

    @Override
    public List<String> getWorkLogs() {
        return List.of(
                "工蜂们绝不能容忍损坏的蜂巢。",
                "工蜂们穷尽一生为它们的女皇投递食料。",
                "工蜂们的首要任务是确保蜂巢的安全。",
                "如果有人胆敢伤害“蜂后”，那就会导致不可逆转的灾难。",
                "如果<员工名称>感到胃部剧痛或是颈部发痒，那么唯一能做的事就是最后一次仰望蓝天。",
                "<员工名称>尽力避免吸入收容单元中的空气。",
                "“蜂后”想要更多、更多、更多的工蜂来繁荣它的王国。",
                "随着工蜂们的辛勤付出，“蜂后”的王国将会一点一点地构筑起来。"
        );
    }

    @Override
    public String name() {
        return "queen_bee";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityQueenBee> event) {
        if ("skill".equals(getAnimation())) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("skill"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SporeReleaseTimer", sporeReleaseTimer);
        tag.putBoolean("SporesApplied", sporesApplied);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sporeReleaseTimer = tag.getInt("SporeReleaseTimer");
        sporesApplied = tag.getBoolean("SporesApplied");
    }
}
