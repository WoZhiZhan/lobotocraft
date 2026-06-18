package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.datafixers.util.Pair;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.BlackForestEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FullScreenRenderMessage;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.util.TimerEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;

import java.util.*;

public class EntityBlackForestDoor extends BaseGeoEntity {

    private static final long ENTER_TIMEOUT = 1200; // 1分钟 = 1200 ticks
    private static final int TELEPORT_RANGE = 16;
    private static final double ENTER_DISTANCE = 1.2;
    private static final int ROOM_WALL_CHECK_RADIUS = 20;
    private static final int ROOM_MIN_WALLS = 3;

    private long spawnTime;
    private int birdsEntered = 0;
    private boolean endBirdSpawned = false;
    private final Map<String, Pair<String, BlockPos>> birdInfo = new HashMap<>();

    public EntityBlackForestDoor(EntityType<? extends TamableAnimal> p_21368_, Level p_21369_) {
        super(p_21368_, p_21369_);
        this.spawnTime = p_21369_.getGameTime();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount % 20 == 0) {
            BlackForestEvent.BlackForestSavedData savedData =
                    BlackForestEvent.BlackForestSavedData.get((ServerLevel) level());
            Iterator<String> iterator = savedData.getEscapedBirdUUIDs().iterator();
            boolean removed = false;
            while (iterator.hasNext()) {
                String stringUUID = iterator.next();
                try {
                    UUID uuid = UUID.fromString(stringUUID);
                    Entity entity = ((ServerLevel) level()).getEntity(uuid);
                    if (entity == null || !entity.isAlive() ||
                            (entity instanceof AbstractAbnormality ab && !ab.hasEscape())) {
                        iterator.remove();
                        removed = true;
                    }
                } catch (IllegalArgumentException e) {
                    iterator.remove();
                    removed = true;
                }
            }
            if (removed) {
                savedData.setDirty();
            }
            if (savedData.isDoorSpawned() && savedData.getEscapedCount() <= 0) {
                savedData.setDoorSpawned(false);
                this.discard();
                return;
            }
        }

        if (birdsEntered >= 3 && !endBirdSpawned) {
            endBirdSpawned = true; // 立即置标志，防止重入

            TimerEntry timerEntry = new TimerEntry() {
                @Override
                public void onEnd(@NotNull LivingEntity living) {
                    List<ServerPlayer> allPlayers = java.util.Arrays.asList(EntityUtil.findAllPlayer(EntityBlackForestDoor.this));
                    if (!allPlayers.isEmpty()) {
                        FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                                .showDuration(4000)
                                .fade(500, 1000)
                                .texture(ResourceUtil.createInstance("textures/gui/end_bird_cg/cg4.png"))
                                .build();
                        SoundEvent sound = ModSounds.THREE_BIRDS_APPEAR.get();
                        for (ServerPlayer player : allPlayers) {
                            MessageLoader.getLoader().sendToPlayer(player, msg);
                            player.playNotifySound(
                                    sound,
                                    SoundSource.MASTER,
                                    1.0f,
                                    1.0f
                            );
                        }
                        living.discard();
                    }
                }
            };
            timerEntry.addSkillTimer(this, 0, 5000, 1);
            List<BlockPos> reactorPositions = new ArrayList<>();
            for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level(), getOnPos(), 300)) {
                if (blockEntity instanceof RegenerationReactorBlockEntity) {
                    reactorPositions.add(blockEntity.getBlockPos());
                }
            }
            if (!reactorPositions.isEmpty()) {
                BlockPos doorPos = this.blockPosition();
                reactorPositions.sort(Comparator.comparingDouble(pos -> pos.distSqr(doorPos)));
                List<BlockPos> selectedPositions = reactorPositions.size() <= 3
                        ? reactorPositions
                        : reactorPositions.subList(0, 3);
                while (selectedPositions.size() < 3) {
                    selectedPositions.add(selectedPositions.get(0));
                }
                EntityEndBirdEggSmall eggSmall = new EntityEndBirdEggSmall(ModEntities.end_bird_egg_small.get(), level());
                EntityEndBirdEggHigh eggHigh = new EntityEndBirdEggHigh(ModEntities.end_bird_egg_high.get(), level());
                EntityEndBirdEggEye eggEye = new EntityEndBirdEggEye(ModEntities.end_bird_egg_eye.get(), level());
                spawnEggAt(eggSmall, selectedPositions.get(0));
                spawnEggAt(eggHigh, selectedPositions.get(1));
                spawnEggAt(eggEye, selectedPositions.get(2));
            }
            BlackForestEvent.BlackForestSavedData savedData =
                    BlackForestEvent.BlackForestSavedData.get((ServerLevel) level());
            savedData.setDoorSpawned(false);
            savedData.getEscapedBirdUUIDs().clear();
            EntityEndBird endBird = AbnormalitySpawnHelper.spawnPersistent(
                    (ServerLevel) level(), ModEntities.end_bird.get(), this.blockPosition());
            if (endBird != null) {
                endBird.setBirdReturnInfo(birdInfo);
                endBird.triggerEscape();
            }
            return;
        }
        checkBirdEntry();
        long elapsed = level().getGameTime() - spawnTime;
        if (elapsed >= ENTER_TIMEOUT) {
            teleportToNearestBird();
            spawnTime = level().getGameTime();
        }
    }

    /**
     * 在指定反应堆附近生成蛋
     */
    private void spawnEggAt(LivingEntity egg, BlockPos reactorPos) {
        egg.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1));
        // 仅在公司范围内寻找安全地面,避免鸟蛋刷到公司之外
        BlockPos spawnPos = EntityUtil.findReactorSpawnPositionInCompany(level(), reactorPos, 5);
        if (spawnPos == null) {
            spawnPos = reactorPos;
        }
        egg.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        level().addFreshEntity(egg);
    }

    /**
     * 检测鸟是否进入门。
     */
    private void checkBirdEntry() {
        AABB detectionBox = this.getBoundingBox().inflate(ENTER_DISTANCE);
        List<AbstractAbnormality> birds = level().getEntitiesOfClass(
                AbstractAbnormality.class,
                detectionBox,
                bird -> BlackForestEvent.isBird(bird) && bird.hasEscape() && bird.isAlive()
        );
        for (AbstractAbnormality bird : birds) {
            if (bird instanceof EntityPunishingBird) {
                for (ServerPlayer player : EntityUtil.findAllPlayer(this)) {
                    FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                            .showDuration(4000).fade(500, 1000)
                            .texture(ResourceUtil.createInstance("textures/gui/end_bird_cg/cg1.png"))
                            .build();
                    MessageLoader.getLoader().sendToPlayer(player, msg);
                }
            }
            if (bird instanceof EntityApprovalBird) {
                for (ServerPlayer player : EntityUtil.findAllPlayer(this)) {
                    FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                            .showDuration(4000).fade(500, 1000)
                            .texture(ResourceUtil.createInstance("textures/gui/end_bird_cg/cg2.png"))
                            .build();
                    MessageLoader.getLoader().sendToPlayer(player, msg);
                }
            }
            if (bird instanceof EntityLargeBird) {
                for (ServerPlayer player : EntityUtil.findAllPlayer(this)) {
                    FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                            .showDuration(4000).fade(500, 1000)
                            .texture(ResourceUtil.createInstance("textures/gui/end_bird_cg/cg3.png"))
                            .build();
                    MessageLoader.getLoader().sendToPlayer(player, msg);
                }
            }

            String birdType;
            if (bird instanceof EntityLargeBird)    birdType = "large_bird";
            else if (bird instanceof EntityApprovalBird)  birdType = "approval_bird";
            else if (bird instanceof EntityPunishingBird) birdType = "punishing_bird";
            else                                          birdType = bird.getAbnormalityCode(); // 兜底
            BlockPos returnPos = bird.getEscapePosition();
            if (returnPos == null) returnPos = bird.blockPosition();
            birdInfo.put(birdType, Pair.of(bird.getStringUUID(), returnPos));

            bird.discard();
            birdsEntered++;
        }
    }

    @Override
    public void die(DamageSource p_21809_) {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            BlackForestEvent.BlackForestSavedData data =
                    BlackForestEvent.BlackForestSavedData.get(serverLevel);

            for (Map.Entry<String, Pair<String, BlockPos>> entry : birdInfo.entrySet()) {
                BlockPos returnPos = entry.getValue().getSecond();
                if (returnPos == null) continue;
                AbstractAbnormality bird = createBirdByCode(entry.getKey(), serverLevel);
                if (bird != null) {
                    bird.setPos(returnPos.getX() + 0.5, returnPos.getY(), returnPos.getZ() + 0.5);
                    bird.setEscape(false);
                    bird.setQliphothCounter(bird.getMaxQliphothCounter());
                    serverLevel.addFreshEntity(bird);
                }
            }

            for (String uuid : data.getEscapedBirdUUIDs()) {
                try {
                    if (serverLevel.getEntity(UUID.fromString(uuid)) instanceof AbstractAbnormality bird
                            && bird.hasEscape()) {
                        bird.stopEscape();
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            data.setDoorSpawned(false);
            data.getEscapedBirdUUIDs().clear();
            data.setDirty();
        }
        super.die(p_21809_);
    }

    @Nullable
    private AbstractAbnormality createBirdByCode(String code, ServerLevel level) {
        return switch (code) {
            case "large_bird"     -> new EntityLargeBird(ModEntities.large_bird.get(), level);
            case "approval_bird"  -> new EntityApprovalBird(ModEntities.approval_bird.get(), level);
            case "punishing_bird" -> new EntityPunishingBird(ModEntities.punishing_bird.get(), level);
            // 兼容旧存档里以 abnormalityCode 为 key 的情况
            case "O-02-40"        -> new EntityLargeBird(ModEntities.large_bird.get(), level);
            case "O-02-56"        -> new EntityPunishingBird(ModEntities.punishing_bird.get(), level);
            default               -> null;
        };
    }

    /**
     * Bug fix 1: 瞬移前先检测目标位置是否在"室内"（四周有墙）。
     * 只有通过检测的位置才会被使用，否则尝试在鸟周围寻找多个候选点。
     */
    private void teleportToNearestBird() {
        List<AbstractAbnormality> birds = level().getEntitiesOfClass(
                AbstractAbnormality.class,
                new AABB(this.blockPosition()).inflate(256),
                bird -> BlackForestEvent.isBird(bird) && bird.hasEscape() && bird.isAlive()
        );
        if (birds.isEmpty()) return;
        AbstractAbnormality nearest = birds.stream()
                .min(Comparator.comparingDouble(a -> a.distanceToSqr(this)))
                .orElse(null);

        if (nearest == null) return;

        // 尝试在鸟周围找一个室内的安全落点
        BlockPos targetPos = findIndoorPositionNear(nearest.blockPosition(), TELEPORT_RANGE);
        if (targetPos != null) {
            this.setPos(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        }
        // 如果找不到室内位置，则不瞬移，等下次再检测（避免跑到室外）
    }

    /**
     * 在 center 附近（半径 radius）找一个既安全又在室内的落点。
     * 先直接检测 center 本身，若不符合则从近到远逐格螺旋搜索。
     */
    private BlockPos findIndoorPositionNear(BlockPos center, int radius) {
        // 先检查 center 自身（鸟当前位置）
        BlockPos safe = EntityUtil.findSafeGroundPosition(level(), center, 3);
        if (safe != null && isInsideRoom(level(), safe)) {
            return safe;
        }

        // 螺旋扩展搜索
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // 只遍历外圈
                    BlockPos candidate = center.offset(dx, 0, dz);
                    BlockPos candidateSafe = EntityUtil.findSafeGroundPosition(level(), candidate, 2);
                    if (candidateSafe != null && isInsideRoom(level(), candidateSafe)) {
                        return candidateSafe;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 判断一个位置是否在封闭房间内。
     * 向东西南北4个方向各扫 ROOM_WALL_CHECK_RADIUS 格，
     * 若在 ROOM_MIN_WALLS 个以上方向都能找到固体方块（墙），则视为室内。
     */
    private boolean isInsideRoom(Level level, BlockPos pos) {
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        int wallsFound = 0;

        for (int dir = 0; dir < 4; dir++) {
            for (int dist = 1; dist <= ROOM_WALL_CHECK_RADIUS; dist++) {
                BlockPos checkPos = pos.offset(dx[dir] * dist, 0, dz[dir] * dist);
                // 检测该方向的方块本身及上方（兼容不同高度的墙）
                if (level.getBlockState(checkPos).isSolid()
                        || level.getBlockState(checkPos.above()).isSolid()) {
                    wallsFound++;
                    break;
                }
            }
        }

        return wallsFound >= ROOM_MIN_WALLS;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("SpawnTime", spawnTime);
        tag.putInt("BirdsEntered", birdsEntered);
        tag.putBoolean("EndBirdSpawned", endBirdSpawned);
        CompoundTag birdInfoTag = new CompoundTag();
        for (Map.Entry<String, Pair<String, BlockPos>> entry : birdInfo.entrySet()) {
            CompoundTag birdTag = new CompoundTag();
            birdTag.putString("UUID", entry.getValue().getFirst());
            BlockPos pos = entry.getValue().getSecond();
            if (pos != null) {
                birdTag.putInt("X", pos.getX());
                birdTag.putInt("Y", pos.getY());
                birdTag.putInt("Z", pos.getZ());
            }
            birdInfoTag.put(entry.getKey(), birdTag);
        }
        tag.put("BirdInfo", birdInfoTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spawnTime = tag.getLong("SpawnTime");
        birdsEntered = tag.getInt("BirdsEntered");
        endBirdSpawned = tag.getBoolean("EndBirdSpawned");
        birdInfo.clear();
        if (tag.contains("BirdInfo")) {
            CompoundTag birdInfoTag = tag.getCompound("BirdInfo");
            for (String key : birdInfoTag.getAllKeys()) {
                CompoundTag birdTag = birdInfoTag.getCompound(key);
                String uuid = birdTag.getString("UUID");
                BlockPos pos = null;
                if (birdTag.contains("X")) {
                    pos = new BlockPos(birdTag.getInt("X"), birdTag.getInt("Y"), birdTag.getInt("Z"));
                }
                birdInfo.put(key, Pair.of(uuid, pos));
            }
        }
    }

    @Override public boolean isPushable()     { return false; }
    @Override public void push(double x, double y, double z) {}
    @Override public void pushEntities()      {}
    @Override public void doPush(Entity e)    {}
    @Override public void push(Entity e)      {}
    @Override public void knockback(double s, double x, double z) {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {}

    @Override public String name() { return "black_forest_door"; }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mob) {
        return null;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 13000.0D)
                .add(Attributes.FLYING_SPEED, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.3D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.3D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.3D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1024D);
    }
}
