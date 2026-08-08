package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.employee.ElevatorRegistry;
import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class ElevatorBlockEntity extends BaseGeoBlockEntity {
    private static final RawAnimation ACTIVE_ANIMATION = RawAnimation.begin().thenLoop("dt-3");
    public static final double INTERACTION_RADIUS = 3.0D;
    private static final double INTERACTION_RADIUS_SQR = INTERACTION_RADIUS * INTERACTION_RADIUS;
    private static final int DESTINATION_HORIZONTAL_RADIUS = 3;
    private static final int MAX_DESTINATION_PROBES = 64;
    private static final long TELEPORT_COOLDOWN_TICKS = 20L;
    private static final String COOLDOWN_TAG = "LobotocraftElevatorCooldown";
    private static final int[][] LANDING_OFFSETS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    private int teleportDistance = 10;
    private boolean teleportUp = true;
    private int animationDuration = 40;

    private boolean isActive;
    private long activatedTick = -1L;

    public ElevatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ELEVATOR.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) ElevatorRegistry.register(level, worldPosition);
        if (level instanceof ServerLevel serverLevel) syncArea(serverLevel);
    }

    @Override
    public void setRemoved() {
        if (level != null) ElevatorRegistry.unregister(level, worldPosition);
        if (level instanceof ServerLevel serverLevel) {
            BlockPos markerPos = worldPosition.immutable();
            BlockState markerState = getBlockState();
            var dimension = serverLevel.dimension().location();
            serverLevel.getServer().execute(() -> {
                if (serverLevel.hasChunkAt(markerPos)
                        && !serverLevel.getBlockState(markerPos).is(markerState.getBlock())) {
                    CompanyRoomData.get(serverLevel).removeArea(
                            FacilityAreaType.ELEVATOR, dimension, markerPos);
                }
            });
        }
        super.setRemoved();
    }

    private void syncArea(ServerLevel level) {
        CompanyRoomData data = CompanyRoomData.get(level);
        FacilityAreaEntry area = data.getOrCreateArea(
                level.dimension().location(), worldPosition, FacilityAreaType.ELEVATOR);
        area.markerPos = worldPosition.immutable();
        area.setBounds(worldPosition.offset(-3, 0, -3), worldPosition.offset(3, 2, 3));
        area.departmentId = "";
        area.type = FacilityAreaType.ELEVATOR;
        area.groupId = "shaft:" + worldPosition.getX() + ":" + worldPosition.getZ();
        data.putArea(area);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElevatorBlockEntity be) {
        if (level.isClientSide()) return;
        if (be.isActive && level.getGameTime() - be.activatedTick >= be.animationDuration) {
            be.isActive = false;
            be.activatedTick = -1L;
            be.syncToClient();
        }
    }

    /** Compatibility entry point for old callers that still use the saved default direction. */
    @Deprecated
    public void onStep(LivingEntity entity) {
        tryTravel(entity, teleportUp);
    }

    public TravelResult tryTravel(LivingEntity entity, boolean up) {
        Level entityLevel = entity.level();
        if (!(entityLevel instanceof ServerLevel serverLevel)
                || entityLevel != level || !entity.isAlive()) {
            return TravelResult.INVALID_ENTITY;
        }
        if (!isWithinInteractionRange(entity)) return TravelResult.TOO_FAR;

        long currentTick = entityLevel.getGameTime();
        CompoundTag persistentData = entity.getPersistentData();
        long cooldownUntil = persistentData.getLong(COOLDOWN_TAG);
        if (cooldownUntil > currentTick) {
            if (isTeleportCooldownActive(currentTick, cooldownUntil)) {
                return TravelResult.COOLDOWN;
            }
            // Absolute ticks can become stale after a restored backup or a clock reset.
            persistentData.remove(COOLDOWN_TAG);
        }

        BlockPos destination = findDestinationElevatorServer(serverLevel, up);
        if (destination == null) return TravelResult.NO_DESTINATION;

        Vec3 landing = findSafeLanding(serverLevel, entity, destination);
        if (landing == null) return TravelResult.BLOCKED_DESTINATION;

        isActive = true;
        activatedTick = currentTick;
        syncToClient();
        persistentData.putLong(COOLDOWN_TAG, currentTick + TELEPORT_COOLDOWN_TICKS);
        // A passenger's position is owned by its vehicle. Teleporting without
        // dismounting lets the next vehicle tick pull the rider back to the old floor.
        if (entity.isPassenger()) entity.stopRiding();
        entity.teleportTo(landing.x, landing.y, landing.z);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        if (entityLevel.getBlockEntity(destination) instanceof ElevatorBlockEntity target) {
            target.isActive = true;
            target.activatedTick = currentTick;
            target.syncToClient();
        }
        return TravelResult.SUCCESS;
    }

    public BlockPos findDestinationElevator(Level level) {
        return findDestinationElevator(level, teleportUp);
    }

    public BlockPos findDestinationElevator(Level level, boolean up) {
        if (level instanceof ServerLevel serverLevel) {
            return findDestinationElevatorServer(serverLevel, up);
        }
        return rankedPositions(getBlockPos(), scanClientElevators(level, getBlockPos(), up), up)
                .stream()
                .filter(candidate -> level.getBlockEntity(candidate) instanceof ElevatorBlockEntity)
                .findFirst()
                .orElse(null);
    }

    private BlockPos findDestinationElevatorServer(ServerLevel level, boolean up) {
        java.util.Set<BlockPos> candidates = ElevatorRegistry.knownPositions(level);

        int probes = 0;
        for (BlockPos candidate : rankedPositions(getBlockPos(), candidates, up)) {
            if (level.isOutsideBuildHeight(candidate)) continue;
            // Route discovery is read-only. Loading arbitrary registered chunks here
            // lets repeated navigation requests turn into unbounded chunk generation.
            if (!level.hasChunkAt(candidate)) continue;
            if (++probes > MAX_DESTINATION_PROBES) break;
            if (level.getBlockEntity(candidate) instanceof ElevatorBlockEntity) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private static java.util.List<BlockPos> rankedPositions(
            BlockPos origin, Iterable<BlockPos> candidates, boolean up) {
        java.util.Map<ElevatorRoutePlanner.Stop, BlockPos> positions = new java.util.HashMap<>();
        for (BlockPos candidate : candidates) {
            if (candidate == null) continue;
            positions.put(new ElevatorRoutePlanner.Stop(
                    candidate.getX(), candidate.getY(), candidate.getZ()), candidate.immutable());
        }
        ElevatorRoutePlanner.Stop originStop = new ElevatorRoutePlanner.Stop(
                origin.getX(), origin.getY(), origin.getZ());
        return ElevatorRoutePlanner.rank(originStop, positions.keySet(), up,
                        DESTINATION_HORIZONTAL_RADIUS).stream()
                .map(positions::get)
                .toList();
    }

    private Iterable<BlockPos> scanClientElevators(Level level, BlockPos origin, boolean up) {
        java.util.List<BlockPos> result = new java.util.ArrayList<>();
        int startY = up ? origin.getY() + 1 : level.getMinBuildHeight();
        int endY = up ? level.getMaxBuildHeight() - 1 : origin.getY() - 1;
        for (int y = startY; y <= endY; y++) {
            for (int dx = -DESTINATION_HORIZONTAL_RADIUS; dx <= DESTINATION_HORIZONTAL_RADIUS; dx++) {
                for (int dz = -DESTINATION_HORIZONTAL_RADIUS; dz <= DESTINATION_HORIZONTAL_RADIUS; dz++) {
                    BlockPos candidate = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    if (level.getBlockEntity(candidate) instanceof ElevatorBlockEntity) result.add(candidate);
                }
            }
        }
        return result;
    }

    public boolean isWithinInteractionRange(LivingEntity entity) {
        Vec3 elevatorCenter = Vec3.atCenterOf(getBlockPos().above());
        return entity.position().distanceToSqr(elevatorCenter) <= INTERACTION_RADIUS_SQR;
    }

    public static Vec3 findSafeLanding(ServerLevel level, LivingEntity entity,
                                       BlockPos destination) {
        if (level == null || entity == null || destination == null) return null;
        for (BlockPos feet : landingCandidates(destination)) {
            if (level.isOutsideBuildHeight(feet) || !level.hasChunkAt(feet)
                    || !hasLandingSupport(level, feet, destination)) continue;
            Vec3 landing = Vec3.atBottomCenterOf(feet);
            AABB landingBox = entity.getDimensions(Pose.STANDING).makeBoundingBox(landing);
            if (level.getWorldBorder().isWithinBounds(landingBox)
                    && level.noCollision(entity, landingBox)
                    && level.getEntitiesOfClass(LivingEntity.class, landingBox,
                            occupant -> occupant != entity && occupant.isAlive()).isEmpty()) {
                return landing;
            }
        }
        return null;
    }

    static java.util.List<BlockPos> landingCandidates(BlockPos destination) {
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>(LANDING_OFFSETS.length);
        for (int[] offset : LANDING_OFFSETS) {
            candidates.add(destination.offset(offset[0], 1, offset[1]));
        }
        return java.util.List.copyOf(candidates);
    }

    private static boolean hasLandingSupport(ServerLevel level, BlockPos feet,
                                             BlockPos destination) {
        BlockPos support = feet.below();
        return support.equals(destination)
                || !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
    }

    static boolean isTeleportCooldownActive(long currentTick, long cooldownUntil) {
        long remaining = cooldownUntil - currentTick;
        return remaining > 0L && remaining <= TELEPORT_COOLDOWN_TICKS;
    }

    public enum TravelResult {
        SUCCESS,
        TOO_FAR,
        COOLDOWN,
        NO_DESTINATION,
        LOCKED_AREA,
        BLOCKED_DESTINATION,
        INVALID_ENTITY
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isActive", isActive);
        tag.putInt("teleportDistance", teleportDistance);
        tag.putBoolean("teleportUp", teleportUp);
        tag.putInt("animationDuration", animationDuration);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            isActive = tag.getBoolean("isActive");
            teleportDistance = tag.getInt("teleportDistance");
            teleportUp = tag.getBoolean("teleportUp");
            animationDuration = tag.getInt("animationDuration");
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
        tag.putInt("teleportDistance", teleportDistance);
        tag.putBoolean("teleportUp", teleportUp);
        tag.putInt("animationDuration", animationDuration);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        teleportDistance = tag.getInt("teleportDistance");
        teleportUp = tag.getBoolean("teleportUp");
        animationDuration = tag.contains("animationDuration") ? tag.getInt("animationDuration") : 40;
    }

    public int getTeleportDistance() {
        return teleportDistance;
    }

    public void setTeleportDistance(int distance) {
        teleportDistance = distance;
    }

    public boolean isTeleportUp() {
        return teleportUp;
    }

    public void setTeleportUp(boolean up) {
        teleportUp = up;
    }

    public int getAnimationDuration() {
        return animationDuration;
    }

    public void setAnimationDuration(int duration) {
        animationDuration = duration;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<ElevatorBlockEntity> state) {
        if (isActive) {
            state.getController().setAnimation(ACTIVE_ANIMATION);
            return PlayState.CONTINUE;
        }
        state.getController().stop();
        return PlayState.STOP;
    }
}
