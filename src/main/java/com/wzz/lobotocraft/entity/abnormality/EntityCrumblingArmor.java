package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.CuriosUtil;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ItemUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;

public class EntityCrumblingArmor extends AbstractAbnormality {
    private static final int EXECUTE_ANIMATION_TICKS = 110;
    private static final int EXECUTE_HIT_TICK = 64;
    private static final String INNER_COURAGE_TAG = "lobotocraft_inner_courage";
    private static final String FOOLHARDY_COURAGE_TAG = "lobotocraft_foolhardy_courage";
    private static final String REPRESSION_COUNT_TAG = "lobotocraft_crumbling_armor_repression";

    private int executeTimer = 0;
    private LivingEntity executeTarget = null;

    public EntityCrumblingArmor(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-05-61";
        this.abnormalityName = "破裂盔甲";
        this.riskLevel = RiskLevel.TETH;
        this.damageType = "RED";
        this.maxPEOutput = 12;

        float[] basePreferences = {0.50f, 0.40f, 0.0f, 0.60f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.50f, 0.50f, 0.55f, 0.55f, 0.60f};
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.40f, 0.40f, 0.40f, 0.40f, 0.40f};
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.60f, 0.60f, 0.65f, 0.65f, 0.70f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 5),
                new ObservationLevelBonus(0.05f, 0),
                new ObservationLevelBonus(0.0f, 5),
                new ObservationLevelBonus(0.05f, 0, false, true, true)
        };
    }

    @Override
    public int getBasicInfoCost() { return 12; }

    @Override
    public int getSensitiveInfoCost() { return 12; }

    @Override
    public int getManualCost(int manualIndex) { return 3; }

    @Override
    public int getWorkPreferencesCost() { return 4; }

    @Override
    public String name() {
        return "crumbling_armor";
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.8D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                target -> target instanceof Player player && !player.isCreative() && !player.isSpectator()));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!hasEscape() || this.executeTimer > 0 || !(target instanceof LivingEntity living)) {
            return true;
        }
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        this.executeTimer = EXECUTE_ANIMATION_TICKS;
        this.executeTarget = living;
        this.setAnimation("execute");
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (!hasEscape()) {
            setNoAi(true);
            setTarget(null);
            getNavigation().stop();
            executeTimer = 0;
            executeTarget = null;
            setAnimation("idle");
            return;
        }

        setNoAi(false);
        if (this.executeTimer <= 0) {
            return;
        }

        this.executeTimer--;
        int elapsed = EXECUTE_ANIMATION_TICKS - this.executeTimer;
        if (elapsed == EXECUTE_HIT_TICK && canHitExecuteTarget()) {
            EntityUtil.clearHurtTime(this.executeTarget, () ->
                    this.executeTarget.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), 10.0F));
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.8F);
        }
        if (this.executeTimer == 0) {
            this.executeTarget = null;
            this.setAnimation("idle");
        }
    }

    private boolean canHitExecuteTarget() {
        return this.executeTarget != null
                && this.executeTarget.isAlive()
                && this.distanceToSqr(this.executeTarget) <= 9.0D;
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            if (stats.getFortitudeLevel() <= 1) {
                executeWorker(player, "§4勇气不足者被破裂盔甲斩首。");
            }
        });

        if (workType == WorkType.REPRESSION && player.isAlive()) {
            grantInnerCourage(player);
        }
    }

    public static boolean hasCourageCurio(Player player) {
        return hasInnerCourage(player) || hasFoolhardyCourage(player);
    }

    public static boolean hasInnerCourage(Player player) {
        return CuriosUtil.hasCurios(player, ModItems.INNER_COURAGE_CURIO.get())
                || ItemUtil.hasItem(player, ModItems.INNER_COURAGE_CURIO.get())
                || player.getPersistentData().getBoolean(INNER_COURAGE_TAG);
    }

    public static boolean hasFoolhardyCourage(Player player) {
        return CuriosUtil.hasCurios(player, ModItems.FOOLHARDY_COURAGE_CURIO.get())
                || ItemUtil.hasItem(player, ModItems.FOOLHARDY_COURAGE_CURIO.get())
                || player.getPersistentData().getBoolean(FOOLHARDY_COURAGE_TAG);
    }

    public static void clearCourage(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        CuriosUtil.removeCurios(player, ModItems.INNER_COURAGE_CURIO.get());
        CuriosUtil.removeCurios(player, ModItems.FOOLHARDY_COURAGE_CURIO.get());
        ItemUtil.removeAllItem(player, ModItems.INNER_COURAGE_CURIO.get());
        ItemUtil.removeAllItem(player, ModItems.FOOLHARDY_COURAGE_CURIO.get());
        data.remove(INNER_COURAGE_TAG);
        data.remove(FOOLHARDY_COURAGE_TAG);
        data.remove(REPRESSION_COUNT_TAG);
    }

    public static void grantInnerCourage(ServerPlayer player) {
        if (hasCourageCurio(player)) {
            return;
        }
        player.getPersistentData().putInt(REPRESSION_COUNT_TAG, 0);
        giveItemOrDrop(player, new ItemStack(ModItems.INNER_COURAGE_CURIO.get()));
        player.displayClientMessage(Component.literal("§7你获得了「内在的勇气」。"), true);
    }

    public static void recordRepressionWork(ServerPlayer player) {
        if (!hasCourageCurio(player)) {
            return;
        }
        if (hasFoolhardyCourage(player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        int count = data.getInt(REPRESSION_COUNT_TAG) + 1;
        data.putInt(REPRESSION_COUNT_TAG, count);
        if (count >= 3 && hasInnerCourage(player)) {
            upgradeCourage(player);
        }
    }

    public static void upgradeCourage(ServerPlayer player) {
        CuriosUtil.removeCurios(player, ModItems.INNER_COURAGE_CURIO.get());
        ItemUtil.removeAllItem(player, ModItems.INNER_COURAGE_CURIO.get());
        player.getPersistentData().remove(INNER_COURAGE_TAG);
        player.getPersistentData().remove(REPRESSION_COUNT_TAG);
        if (!hasFoolhardyCourage(player)) {
            giveItemOrDrop(player, new ItemStack(ModItems.FOOLHARDY_COURAGE_CURIO.get()));
        }
        player.displayClientMessage(Component.literal("§4「内在的勇气」转化为「匹夫之勇」。"), true);
    }

    private static void giveItemOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    public static void executeWorker(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), false);
        EntityUtil.clearHurtTime(player, () ->
                player.hurt(DamageHelper.getDamage(player, "lobotocraft:black"),
                        Math.max(1000.0F, player.getMaxHealth() * 100.0F)));
        if (player.isAlive()) {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), 2.0F + this.random.nextInt(2));
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("胆小的员工在靠近“破裂盔甲”时，会莫名其妙地感到阴森森的感觉。");
        logs.add("与盔甲独处时，<员工名称>清晰地感到盔甲中投来一道视线。当然，盔甲里什么都没有。");
        logs.add("有传闻称，深更半夜时盔甲周围会传出“全军突击！！！”的声音，有时也能听到某人热血激昂的喊杀声。");
        logs.add("尽管有着些许破损，但整件盔甲的保存状态极佳。实在令人难以相信这是几百年前的文物。");
        logs.add("曾有许多将军因拥有这件盔甲而死。没人知道他们究竟是战死沙场，还是被这件盔甲夺走了性命。");
        logs.add("盔甲右侧的臂板断裂了。调查后我们确认，断裂的原因来源于它的内部，而非外部。");
        logs.add("“破裂盔甲”在数百年前就已被锻造出来。虽然我们尚不知晓这件盔甲出自哪位匠人之手，不过至少有一点是可以肯定的——这位匠人肯定很讨厌懦夫。");
        logs.add("这件盔甲依旧在等待那些匹夫，等待那些放弃了自己生命的人们。");
        return logs;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityCrumblingArmor> event) {
        if ("execute".equals(getAnimation())) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.crumbling_armor.execute"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.crumbling_armor.idle"));
    }

    @Override
    public void die(DamageSource source) {
        setAnimation("idle");
        super.die(source);
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityCrumblingArmor((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.18D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.5D);
    }
}
