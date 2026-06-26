package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.work.WorkCompleteEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.LockInputPacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class EntityLadyFacingTheWall extends AbstractAbnormality {
    private static final int SEARCH_LIMIT = 30_000_000;
    private static final int PUNISHMENT_TICKS = 30 * 20;
    private static final int PUNISHMENT_DAMAGE_INTERVAL = 6 * 20;
    private static final Map<UUID, PunishmentData> PUNISHED_PLAYERS = new HashMap<>();

    public EntityLadyFacingTheWall(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-01-12";
        this.abnormalityName = "老妇人";
        this.riskLevel = RiskLevel.TETH;
        this.damageType = "WHITE";
        this.maxPEOutput = 14;

        initializeWorkPreferences(new float[]{0.40f, 0.50f, 0.60f, 0.30f});
        initializeQliphothCounter(4);
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.45f, 0.45f, 0.40f, 0.40f, 0.40f};
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.45f, 0.45f, 0.50f, 0.50f, 0.50f};
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{0.65f, 0.65f, 0.60f, 0.60f, 0.60f};
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.30f, 0.30f, 0.30f, 0.30f, 0.30f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.04f, 0),
                new ObservationLevelBonus(0.0f, 6, true, true, false),
                new ObservationLevelBonus(0.04f, 0, false, false, true),
                new ObservationLevelBonus(0.0f, 6)
        };
    }

    @Override
    public int getBasicInfoCost() {
        return 12;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 12;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 3;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 4;
    }

    @Override
    public int getGoodWorkResultMin() {
        return 11;
    }

    @Override
    public int getNormalWorkResultMin() {
        return 6;
    }

    @Override
    public String name() {
        return "the_lady_facing_the_wall";
    }

    @Override
    public List<String> getWorkLogs() {
        return List.of(
                "<员工名称>陪伴老妇人坐在沉默之中。",
                "老妇人依旧面对着墙，没有回头。",
                "收容单元里只剩下压抑的寂静。"
        );
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        if (this.qliphothCounter <= 0) {
            startPunishment(player);
            this.qliphothCounter = this.maxQliphothCounter;
            player.sendSystemMessage(Component.literal("§8老妇人没有回头，只留下令人窒息的沉默。"));
            return false;
        }
        return super.onWorkStart(player, workType);
    }

    @Override
    public void onBadWork(ServerPlayer player) {
    }

    @Override
    public void onWorkTick(ServerPlayer player, WorkManager.WorkSession session, WorkType workType) {
        if (PUNISHED_PLAYERS.containsKey(player.getUUID())) {
            WorkManager.forceCompleteWork(player, WorkResult.BAD, "孤独");
            player.closeContainer();
        }
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        increaseQliphothCounter(1);
        if (this.qliphothCounter > this.maxQliphothCounter) {
            this.qliphothCounter = this.maxQliphothCounter;
        }
    }

    @Override
    public void onQliphothMeltdown() {
        if (this.level() instanceof ServerLevel level) {
            spawnBlackSmoke(level, this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                    60, 2.5D, 1.2D, 2.5D);
        }
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        player.hurt(DamageHelper.getDamage(this, "white"), 2.0F + this.random.nextInt(3));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 4, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityLadyFacingTheWall> event) {
        return event.setAndContinue(RawAnimation.begin()
                .thenLoop("animation.the_lady_facing_the_wall.idle"));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityLadyFacingTheWall((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void die(DamageSource source) {
        PUNISHED_PLAYERS.values().removeIf(data -> data.ladyUuid.equals(this.getUUID()));
        super.die(source);
    }

    private void startPunishment(ServerPlayer player) {
        PunishmentData data = new PunishmentData(
                player.level().dimension(),
                player.getX(),
                player.getY(),
                player.getZ(),
                PUNISHMENT_TICKS,
                0,
                this.getUUID()
        );
        PUNISHED_PLAYERS.put(player.getUUID(), data);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, PUNISHMENT_TICKS + 20,
                0, false, true, true));
        if (player.level() instanceof ServerLevel level) {
            spawnBlackSmoke(level, player.getX(), player.getY() + 0.8D, player.getZ(),
                    30, 0.45D, 0.8D, 0.45D);
        }
    }

    private boolean hasEmployeeInContainment() {
        return !this.level().getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(5.0D),
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator()).isEmpty();
    }

    private static void decreaseUnattendedLadies(ServerLevel level) {
        AABB wholeLevel = new AABB(-SEARCH_LIMIT, level.getMinBuildHeight(), -SEARCH_LIMIT,
                SEARCH_LIMIT, level.getMaxBuildHeight(), SEARCH_LIMIT);
        for (EntityLadyFacingTheWall lady : level.getEntitiesOfClass(EntityLadyFacingTheWall.class, wholeLevel,
                lady -> lady.isAlive() && !lady.isRemoved())) {
            if (lady.hasEmployeeInContainment() || lady.getQliphothCounter() <= 0) {
                continue;
            }
            lady.decreaseQliphothCounter(1);
        }
    }

    @SubscribeEvent
    public static void onWorkComplete(WorkCompleteEvent event) {
        if (event.getAbnormality() instanceof EntityLadyFacingTheWall) {
            return;
        }
        if (event.getEntity().level() instanceof ServerLevel level) {
            decreaseUnattendedLadies(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        PunishmentData data = PUNISHED_PLAYERS.get(player.getUUID());
        if (data == null) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !player.level().dimension().equals(data.dimension)
                || !player.isAlive()) {
            PUNISHED_PLAYERS.remove(player.getUUID());
            return;
        }

        data.remainingTicks--;
        player.teleportTo(data.x, data.y, data.z);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        MessageLoader.getLoader().sendToPlayer(player, new LockInputPacket());

        if (player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, true, true));
        }
        if (player.tickCount % 10 == 0) {
            spawnBlackSmoke(level, player.getX(), player.getY() + 0.8D, player.getZ(),
                    6, 0.35D, 0.6D, 0.35D);
        }

        data.damageTimer++;
        if (data.damageTimer >= PUNISHMENT_DAMAGE_INTERVAL) {
            data.damageTimer = 0;
            Entity source = level.getEntity(data.ladyUuid);
            player.hurt(DamageHelper.getDamage(source == null ? player : source, "white"),
                    4.0F + level.getRandom().nextInt(3));
        }

        if (data.remainingTicks <= 0) {
            PUNISHED_PLAYERS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§7沉默终于散去，你重新取回了行动能力。"));
        }
    }

    private static void spawnBlackSmoke(ServerLevel level, double x, double y, double z,
                                        int count, double xOffset, double yOffset, double zOffset) {
        for (int i = 0; i < count; i++) {
            double px = x + (level.random.nextDouble() - 0.5D) * xOffset * 2.0D;
            double py = y + (level.random.nextDouble() - 0.5D) * yOffset * 2.0D;
            double pz = z + (level.random.nextDouble() - 0.5D) * zOffset * 2.0D;
            level.sendParticles(ParticleTypes.ENTITY_EFFECT, px, py, pz, 0,
                    0.01D, 0.01D, 0.01D, 1.0D);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.02D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D);
    }

    private static class PunishmentData {
        private final ResourceKey<Level> dimension;
        private final double x;
        private final double y;
        private final double z;
        private final UUID ladyUuid;
        private int remainingTicks;
        private int damageTimer;

        private PunishmentData(ResourceKey<Level> dimension, double x, double y, double z,
                               int remainingTicks, int damageTimer, UUID ladyUuid) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.remainingTicks = remainingTicks;
            this.damageTimer = damageTimer;
            this.ladyUuid = ladyUuid;
        }
    }
}
