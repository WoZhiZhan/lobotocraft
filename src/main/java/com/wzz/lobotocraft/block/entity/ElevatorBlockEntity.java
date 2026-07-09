package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ElevatorBlockEntity extends BaseGeoBlockEntity {
    private static final RawAnimation ACTIVE_ANIMATION = RawAnimation.begin().thenLoop("dt-3");
    private static final double DETECTION_HORIZONTAL_RADIUS = 3.0D;
    private static final int DESTINATION_HORIZONTAL_RADIUS = 3;

    private int teleportDistance = 10;
    private boolean teleportUp = true;
    private int animationDuration = 40; // 动画持续时间(ticks)，40tick = 2秒

    private boolean isActive = false;
    private long activatedTick = -1;
    private static final long DELAY_TICKS = 20;
    private static final long TELEPORT_COOLDOWN_TICKS = 20 * 6L;
    private static final Map<UUID, Long> TELEPORT_COOLDOWNS = new HashMap<>();
    private final Map<UUID, Long> entitiesInRangeSince = new HashMap<>();

    public ElevatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ELEVATOR.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElevatorBlockEntity be) {
        if (level.isClientSide()) return;
        be.tickDwellDetection(level, pos);
        if (!be.isActive) return;

        be.executeReadyTeleports(level, pos, level.getGameTime());
    }

    public void onStep(LivingEntity entity) {
        Level level = entity.level();
        if (level.isClientSide()) return;
        if (!getDetectionBox(getBlockPos()).intersects(entity.getBoundingBox())) return;

        long currentTick = level.getGameTime();
        cleanupExpiredCooldowns(currentTick);
        if (isOnTeleportCooldown(entity, currentTick)) {
            this.entitiesInRangeSince.remove(entity.getUUID());
            return;
        }

        this.entitiesInRangeSince.putIfAbsent(entity.getUUID(), currentTick);
        beginActivation(currentTick);
    }

    private void tickDwellDetection(Level level, BlockPos pos) {
        long currentTick = level.getGameTime();
        cleanupExpiredCooldowns(currentTick);

        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                getDetectionBox(pos),
                entity -> entity.isAlive() && !isOnTeleportCooldown(entity, currentTick)
        );
        if (entities.isEmpty()) {
            resetActivation();
            return;
        }

        Set<UUID> currentEntities = entities.stream()
                .map(LivingEntity::getUUID)
                .collect(Collectors.toSet());
        entitiesInRangeSince.keySet().removeIf(uuid -> !currentEntities.contains(uuid));

        for (LivingEntity entity : entities) {
            entitiesInRangeSince.putIfAbsent(entity.getUUID(), currentTick);
        }

        beginActivation(currentTick);
    }

    private void beginActivation(long currentTick) {
        if (this.isActive) return;

        this.isActive = true;
        this.activatedTick = currentTick;
        syncToClient();
    }

    private boolean hasEntityStayedLongEnough(LivingEntity entity, long currentTick) {
        Long startTick = entitiesInRangeSince.get(entity.getUUID());
        return startTick != null && currentTick - startTick >= DELAY_TICKS;
    }

    private static boolean isOnTeleportCooldown(LivingEntity entity, long currentTick) {
        Long cooldownUntil = TELEPORT_COOLDOWNS.get(entity.getUUID());
        return cooldownUntil != null && cooldownUntil > currentTick;
    }

    private static void startTeleportCooldown(LivingEntity entity, long currentTick) {
        TELEPORT_COOLDOWNS.put(entity.getUUID(), currentTick + TELEPORT_COOLDOWN_TICKS);
    }

    private static void cleanupExpiredCooldowns(long currentTick) {
        TELEPORT_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
    }

    private void resetActivation() {
        boolean wasActive = this.isActive;
        this.isActive = false;
        this.activatedTick = -1;
        this.entitiesInRangeSince.clear();
        if (wasActive) {
            syncToClient();
        }
    }

    private void executeReadyTeleports(Level level, BlockPos pos, long currentTick) {
        AABB detectionBox = getDetectionBox(pos);
        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                detectionBox,
                entity -> entity.isAlive() && hasEntityStayedLongEnough(entity, currentTick)
        );
        BlockPos targetElevatorPos = findDestinationElevator(level);
        for (LivingEntity entity : entities) {
            if (targetElevatorPos == null) {
                entitiesInRangeSince.remove(entity.getUUID());
                continue;
            }
            entity.teleportTo(
                    targetElevatorPos.getX() + 0.5D,
                    targetElevatorPos.getY() + 1.0D,
                    targetElevatorPos.getZ() + 0.5D
            );
            startTeleportCooldown(entity, currentTick);
            entitiesInRangeSince.remove(entity.getUUID());
        }

        if (entitiesInRangeSince.isEmpty()) {
            resetActivation();
        }
    }

    public BlockPos findDestinationElevator(Level level) {
        return findNearestElevatorInDirection(level, getBlockPos());
    }

    private BlockPos findNearestElevatorInDirection(Level level, BlockPos pos) {
        int step = this.teleportUp ? 1 : -1;
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int y = pos.getY() + step; y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight(); y += step) {
            for (int dx = -DESTINATION_HORIZONTAL_RADIUS; dx <= DESTINATION_HORIZONTAL_RADIUS; dx++) {
                for (int dz = -DESTINATION_HORIZONTAL_RADIUS; dz <= DESTINATION_HORIZONTAL_RADIUS; dz++) {
                    BlockPos candidate = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    if (level.getBlockEntity(candidate) instanceof ElevatorBlockEntity) {
                        double distance = candidate.distSqr(pos);
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = candidate;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private AABB getDetectionBox(BlockPos pos) {
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        return new AABB(
                centerX - DETECTION_HORIZONTAL_RADIUS, pos.getY(), centerZ - DETECTION_HORIZONTAL_RADIUS,
                centerX + DETECTION_HORIZONTAL_RADIUS, pos.getY() + 2.5D, centerZ + DETECTION_HORIZONTAL_RADIUS
        );
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isActive", this.isActive);
        tag.putInt("teleportDistance", this.teleportDistance);
        tag.putBoolean("teleportUp", this.teleportUp);
        tag.putInt("animationDuration", this.animationDuration);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.isActive = tag.getBoolean("isActive");
            this.teleportDistance = tag.getInt("teleportDistance");
            this.teleportUp = tag.getBoolean("teleportUp");
            this.animationDuration = tag.getInt("animationDuration");
        }
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            BlockState state = level.getBlockState(getBlockPos());
            level.sendBlockUpdated(getBlockPos(), state, state, 2);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("teleportDistance", this.teleportDistance);
        tag.putBoolean("teleportUp", this.teleportUp);
        tag.putInt("animationDuration", this.animationDuration);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.teleportDistance = tag.getInt("teleportDistance");
        this.teleportUp = tag.getBoolean("teleportUp");
        // 使用默认值40，如果NBT中没有则保持默认
        if (tag.contains("animationDuration")) {
            this.animationDuration = tag.getInt("animationDuration");
        } else {
            this.animationDuration = 40; // 确保有默认值
        }
    }

    public int getTeleportDistance() { return this.teleportDistance; }
    public void setTeleportDistance(int distance) { this.teleportDistance = distance; }
    public boolean isTeleportUp() { return this.teleportUp; }
    public void setTeleportUp(boolean up) { this.teleportUp = up; }
    public int getAnimationDuration() { return this.animationDuration; }
    public void setAnimationDuration(int duration) { this.animationDuration = duration; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<ElevatorBlockEntity> state) {
        if (this.isActive) {
            state.getController().setAnimation(ACTIVE_ANIMATION);
            return PlayState.CONTINUE;
        } else {
            state.getController().stop();
            return PlayState.STOP;
        }
    }
}
