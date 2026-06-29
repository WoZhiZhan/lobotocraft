package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EntityChildrenGalaxy extends AbstractAbnormality {
    private final Random random = new Random();

    public EntityChildrenGalaxy(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        // 基础信息
        this.abnormalityCode = "O-01-55";
        this.abnormalityName = "银河之子";
        this.riskLevel = RiskLevel.HE;
        this.damageType = "BLACK";
        this.maxPEOutput = 16;

        // 工作偏好
        float[] basePreferences = {0.65f, 0.65f, 0.65f, 0.65f};
        // 本能，洞察，沟通，压迫
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(5, 1);
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.0f, 4, false, true, false),
                new ObservationLevelBonus(0.04f, 0, true, false, false),
                new ObservationLevelBonus(0.0f, 4),
                new ObservationLevelBonus(0.04f, 0, false, false, true)
        };
    }

    @Override
    public float getGiftProbability() {
        return 0.04f;
    }

    @Override public int getWeaponDevelopmentCost() { return 45; }
    @Override public int getWeaponDevelopmentMaxCount() { return 1; }
    @Override public int getArmorDevelopmentCost() { return 30; }
    @Override public int getArmorDevelopmentMaxCount() { return 3; }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/children_galaxy_curio.png"),
                "小小银河", "颈部", "children_galaxy_curio",
                "成功率+3,工作速度+3",
                "每3秒恢复3点生命值与精神值");
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/children_galaxy_weapon.png"),
                "小小银河", RiskLevel.HE, "BLACK", "15", "2", "3x3",
                getWeaponDevelopmentMaxCount(), "children_galaxy_weapon");
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/children_galaxy_armor.png"),
                "小小银河", RiskLevel.HE,
                0.8f, 0.8f, 1.2f, 1.5f,
                getArmorDevelopmentMaxCount(), "children_galaxy");
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[2] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[3] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        return levelModifiers;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        if (qliphothCounter > 0) {
            logs.add("我很喜欢你！这颗卵石是我最珍视的东西，所以我要把它送给你！你要一直带在身边哦！");
            logs.add("要珍惜我们的友谊哦！");
            logs.add("我喜欢你，真想把你紧紧握在手中。");
            logs.add("我知道你在哪。");
        } else {
            logs.add("我是那么的喜欢你，可是你为什么不来见我？");
            logs.add("你为什么不来看看我？");
            logs.add("你为什么不来找我啊？");
            logs.add("那东西难道比我重要吗？");
            logs.add("你为什么在那儿？你应该在我这儿！");
        }
        return logs;
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    @Override
    public int getBasicInfoCost() {
        return 16;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 5;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 16;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 3;
    }

    @Override
    public String name() {
        return "children_galaxy";
    }

    @Override
    public void onWorkComplete(ServerPlayer player,
                               WorkType workType,
                               WorkResult result) {
        if (BuffUtil.hasFriendshipProof(player)) {
            increaseQliphothCounter(1);
            increaseQliphothCounter(1);
            player.sendSystemMessage(Component.literal("§a「友谊之证」让银河之子的计数器增加了。"));
            return;
        }
        increaseQliphothCounter(1);
        if (qliphothCounter <= 0) {
            qliphothCounter = 1;
            com.wzz.lobotocraft.util.BuffUtil.giveFriendshipProof(player, 1);
            player.sendSystemMessage(Component.literal("你得到了「友谊之证」！"));
        } else if (!com.wzz.lobotocraft.util.BuffUtil.hasFriendshipProof(player)) {
            com.wzz.lobotocraft.util.BuffUtil.giveFriendshipProof(player, qliphothCounter);
            player.sendSystemMessage(Component.literal("你得到了「友谊之证」！"));
        }
    }

    @Override
    public void onBadWork(ServerPlayer player) {
    }

    @Override
    public void onQliphothMeltdown() {
        for (LivingEntity living : EntityUtil.findAllEntities(this, 400)) {
            if (living instanceof Player player && com.wzz.lobotocraft.util.BuffUtil.hasFriendshipProof(player)) {
                // 标记为"异想体机制处决",使死亡后不清空员工属性(避免变回原始人)
                player.getPersistentData().putBoolean("lobotocraft_abnormality_executed", true);
                com.wzz.lobotocraft.util.BuffUtil.removeFriendshipProof(player);
                // 用带伤害源的致命伤致死,确保走完整死亡流程(触发 onPlayerDeath 备份属性),
                // 而不是 setHealth(0) 可能绕过死亡事件导致备份缺失、重生后属性被清空
                player.invulnerableTime = 0;
                player.hurt(com.wzz.lobotocraft.util.DamageHelper.getDamage(this, "black"), Float.MAX_VALUE);
                setAnimation("attack");
                SoundUtil.playSound(living.level, living, ModSounds.CHILDREN_GALAXY_ATTACK.get());
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        setNoAi(!hasEscape());
        if (this.level.isClientSide) return;
        if (qliphothCounter <= 0) {
            if (this.tickCount % 280 == 0) {
                SoundUtil.playSound(this.level, this, ModSounds.CHILDREN_GALAXY_ATTACK.get());
                setAnimation("attack");
            }
            if (this.tickCount % 20 == 0) {
                for (Player player : EntityUtil.findPlayersAround(this, 3, 3)) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20));
                }
            }
        } else setAnimation("idle");
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 2.0f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:" + this.damageType.toLowerCase()), damage);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityChildrenGalaxy((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityChildrenGalaxy> event) {
        String anim = getAnimation();
        if ("give".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("give"));
        } else if ("attack".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("skill1"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }
}
