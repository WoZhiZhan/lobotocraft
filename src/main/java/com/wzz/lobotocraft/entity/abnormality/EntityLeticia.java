package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEffects;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ParticleUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EntityLeticia extends AbstractAbnormality {
    private static final int GIFT_DURATION = 20 * 60 * 20;
    private static final int GIFT_ANIMATION_TICKS = 45;
    private static final int IDLE_SOUND_INTERVAL_TICKS = 20 * 53;

    private int actionAnimationTimer = 0;

    public EntityLeticia(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-01-67";
        this.abnormalityName = "蕾蒂希娅";
        this.riskLevel = RiskLevel.HE;
        this.damageType = "BLACK";
        this.maxPEOutput = 16;

        this.workPreferences = new float[]{0.50f, 0.40f, 0.65f, 0.0f};
        this.fullWorkPreferences = new float[4][5];
        this.fullWorkPreferences[WorkType.INSTINCT.ordinal()] = new float[]{0.40f, 0.45f, 0.50f, 0.50f, 0.50f};
        this.fullWorkPreferences[WorkType.INSIGHT.ordinal()] = new float[]{0.40f, 0.40f, 0.40f, 0.40f, 0.40f};
        this.fullWorkPreferences[WorkType.ATTACHMENT.ordinal()] = new float[]{0.60f, 0.60f, 0.60f, 0.65f, 0.65f};
        this.fullWorkPreferences[WorkType.REPRESSION.ordinal()] = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        initializeQliphothCounter(0, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        setNoAi(true);
        if (actionAnimationTimer > 0) {
            actionAnimationTimer--;
            if (actionAnimationTimer == 0) {
                setAnimation("idle");
            }
        }
    }

    @Override
    public void onGoodWork(ServerPlayer player) {
    }

    @Override
    public void onNormalWork(ServerPlayer player) {
        giveGift(player);
    }

    @Override
    public void onBadWork(ServerPlayer player) {
    }

    public void giveGift(ServerPlayer player) {
        clearOtherGiftHolders(player);
        player.removeEffect(ModEffects.LETICIA_BROKEN_GIFT.get());
        player.addEffect(new MobEffectInstance(ModEffects.LETICIA_GIFT.get(),
                GIFT_DURATION, 0, false, true, true));
        ParticleUtil.spawnParticles(player, ParticleUtil.getDustParticle(0.85f, 0.55f, 0.9f, 1.1f), 12, 0.02D);
        setActionAnimation("gift", GIFT_ANIMATION_TICKS);
    }

    private void setActionAnimation(String animation, int ticks) {
        setAnimation(animation);
        actionAnimationTimer = ticks;
    }

    public static void spawnFriendFor(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        EntityLeticiaFriend friend = ModEntities.leticia_friend.get().create(serverLevel);
        if (friend == null) {
            return;
        }

        BlockPos spawnPos = findFriendSpawnPosition(serverLevel, player.blockPosition());
        boolean escapeBlockSpawn = isEscapeBlockSpawn(serverLevel, spawnPos);
        double angle = escapeBlockSpawn ? 0.0D : serverLevel.getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = escapeBlockSpawn ? 0.0D : 1.0D + serverLevel.getRandom().nextDouble() * 2.0D;
        friend.moveTo(
                spawnPos.getX() + 0.5D + Math.cos(angle) * distance,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D + Math.sin(angle) * distance,
                serverLevel.getRandom().nextFloat() * 360.0F,
                0.0F
        );
        friend.finalizeSpawn(
                serverLevel,
                serverLevel.getCurrentDifficultyAt(friend.blockPosition()),
                MobSpawnType.MOB_SUMMONED,
                null,
                null
        );
        serverLevel.addFreshEntity(friend);
        serverLevel.playSound(null, friend.blockPosition(), ModSounds.LETICIA_FRIEND_SPAWN.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
        ParticleUtil.spawnParticles(friend, ParticleUtil.getDustParticle(0.82f, 0.35f, 0.88f, 1.4f), 24, 0.03D);
    }

    private static BlockPos findFriendSpawnPosition(ServerLevel level, BlockPos origin) {
        List<BlockPos> escapeBlocks = new ArrayList<>(EscapeBlockEntity.getEscapeBlocks(level.dimension()));
        return escapeBlocks.stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof EscapeBlockEntity)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
                .map(BlockPos::above)
                .orElseGet(() -> EntityUtil.findSafeGroundPositionInCompany(level, origin, 4));
    }

    private static boolean isEscapeBlockSpawn(ServerLevel level, BlockPos spawnPos) {
        return level.getBlockEntity(spawnPos.below()) instanceof EscapeBlockEntity;
    }

    private void clearOtherGiftHolders(ServerPlayer giftHolder) {
        if (!(giftHolder.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player == giftHolder) {
                continue;
            }
            player.removeEffect(ModEffects.LETICIA_GIFT.get());
            player.removeEffect(ModEffects.LETICIA_BROKEN_GIFT.get());
        }
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
    public int getSensitiveInfoCost() {
        return 16;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 5;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 6;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), 2.0F + this.random.nextInt(3));
    }

    @Override
    public Float modifyWorkSuccessRate(ServerPlayer player, WorkType workType, float baseRate) {
        if (workType == WorkType.ATTACHMENT && player.hasEffect(ModEffects.LETICIA_GIFT.get())) {
            return Math.min(0.95f, baseRate + 0.20f);
        }
        return null;
    }

    @Override
    public int getGoodWorkResultMin() {
        return 11;
    }

    @Override
    public int getNormalWorkResultMin() {
        return 7;
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (result != WorkResult.BAD && player.hasEffect(ModEffects.LETICIA_BROKEN_GIFT.get())) {
            giveGift(player);
        }
    }

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.LETICIA_IDLE.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return IDLE_SOUND_INTERVAL_TICKS;
    }

    @Override
    public double getAbnormalityAmbientSoundRange() {
        return 8.0D;
    }

    @Override
    public SoundSource getAbnormalityAmbientSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 4),
                new ObservationLevelBonus(0.04f, 0, true, false, false),
                new ObservationLevelBonus(0.0f, 4),
                new ObservationLevelBonus(0.04f, 0, false, true, true)
        };
    }

    @Override
    public List<String> getWorkLogs() {
        return List.of(
                "蕾蒂希娅认真挑选着要送给<员工名称>的礼物。",
                "收容单元里传来轻柔的铃声。",
                "蕾蒂希娅希望这份礼物能让朋友露出笑容。"
        );
    }

    @Override
    public String name() {
        return "leticia";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 4, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityLeticia> event) {
        if ("gift".equals(getAnimation())) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.laetitia.gift"));
        }
        if ("bell".equals(getAnimation())) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.laetitia.bell"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.laetitia.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ActionAnimationTimer", actionAnimationTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        actionAnimationTimer = tag.getInt("ActionAnimationTimer");
    }
}
