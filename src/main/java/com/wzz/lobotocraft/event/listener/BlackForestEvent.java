package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.abnormality.EntityBlackForestDoor;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.abnormality.EntityLargeBird;
import com.wzz.lobotocraft.entity.abnormality.EntityPunishingBird;
import com.wzz.lobotocraft.entity.abnormality.EntityApprovalBird;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FullScreenRenderMessage;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber
public class BlackForestEvent {

    @SubscribeEvent
    public static void onAbnormalityEscape(AbnormalityEscapeEvent event) {
        AbstractAbnormality abnormality = event.getAbnormality();
        if (!isBird(abnormality) || abnormality.level().isClientSide) return;

        ServerLevel level = (ServerLevel) abnormality.level();
        BlackForestSavedData data = BlackForestSavedData.get(level);
        data.addEscapedBird(abnormality);
        EnumMap<BirdRole, AbstractAbnormality> escapedBirdsByRole = collectEscapedBirdsByRole(level, data);
        int validCount = escapedBirdsByRole.size();
        if (data.isResolvingMissingBird()) return;

        // 检查门是否实际存在
        if (data.isDoorSpawned()) {
            boolean doorExists = false;
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityBlackForestDoor door && door.isAlive()) {
                    doorExists = true;
                    break;
                }
            }
            if (!doorExists && data.isDoorLocationLoaded(level.getServer())) {
                data.setDoorSpawned(false);
                data.clearEscapedBirds();
            }
        }

        if (validCount >= 2 && !data.isDoorSpawned()) {
            boolean hasArmor = false;
            for (ServerPlayer player : EntityUtil.findAllPlayer(abnormality)) {
                if (EgoArmorHelper.isWearingFullSet(player, "end_bird")) {
                    player.displayClientMessage(Component.literal("§e黑森林事件被阻止了..."), false);
                    hasArmor = true;
                    break;
                }
            }
            if (hasArmor) return;

            if (validCount == 2) {
                BirdRole missingRole = null;
                for (BirdRole role : BirdRole.values()) {
                    if (!escapedBirdsByRole.containsKey(role)) {
                        missingRole = role;
                        break;
                    }
                }
                AbstractAbnormality otherBird = findRemainingBird(level, escapedBirdsByRole, missingRole);
                if (otherBird == null) return;
                data.setResolvingMissingBird(true);
                try {
                    otherBird.triggerEscape();
                    data.addEscapedBird(otherBird);
                } finally {
                    data.setResolvingMissingBird(false);
                }
                escapedBirdsByRole = collectEscapedBirdsByRole(level, data);
            }
            if (escapedBirdsByRole.size() != BirdRole.values().length) return;

            EntityBlackForestDoor door = new EntityBlackForestDoor(ModEntities.black_forest_door.get(), level);
            BlockPos spawnPos = null;

            // 获取所有已出逃鸟的位置中心点
            BlockPos averageBirdPos = abnormality.getOnPos();
            List<AbstractAbnormality> escapedBirds = new ArrayList<>();
            for (String uuid : data.getEscapedBirdUUIDs()) {
                Entity entity = level.getEntity(UUID.fromString(uuid));
                if (entity instanceof AbstractAbnormality bird && bird.hasEscape()) {
                    escapedBirds.add(bird);
                }
            }

            if (!escapedBirds.isEmpty()) {
                double avgX = escapedBirds.stream().mapToDouble(Entity::getX).average().orElse(abnormality.getX());
                double avgY = escapedBirds.stream().mapToDouble(Entity::getY).average().orElse(abnormality.getY());
                double avgZ = escapedBirds.stream().mapToDouble(Entity::getZ).average().orElse(abnormality.getZ());
                averageBirdPos = BlockPos.containing(avgX, avgY, avgZ);
            }
            Set<BlockPos> playerPositions = new HashSet<>();
            for (ServerPlayer player : EntityUtil.findAllPlayer(abnormality)) {
                playerPositions.add(player.blockPosition());
            }

            List<BlockEntity> allBlockEntities = EntityUtil.findBlockEntities(level, averageBirdPos, 300);
            List<BlockPos> allReactorPositions = new ArrayList<>();
            List<BlockPos> safeReactorPositions = new ArrayList<>();
            for (BlockEntity be : allBlockEntities) {
                if (be instanceof RegenerationReactorBlockEntity
                        && FacilityAccessService.isPositionOpen(level.getServer(),
                        level.dimension().location(), be.getBlockPos())) {
                    BlockPos reactorPos = be.getBlockPos();
                    allReactorPositions.add(reactorPos);
                    boolean nearPlayer = false;
                    for (BlockPos playerPos : playerPositions) {
                        if (reactorPos.distSqr(playerPos) <= 100.0) { // 10^2 = 100
                            nearPlayer = true;
                            break;
                        }
                    }
                    if (!nearPlayer) {
                        safeReactorPositions.add(reactorPos);
                    }
                }
            }
            if (!safeReactorPositions.isEmpty()) {
                final BlockPos centerPos = averageBirdPos;
                safeReactorPositions.sort(Comparator.comparingDouble(pos -> pos.distSqr(centerPos)));
                BlockPos nearestSafeReactor = safeReactorPositions.get(0);
                spawnPos = EntityUtil.findSafeGroundPosition(level, nearestSafeReactor, 5);
            } else if (!allReactorPositions.isEmpty()) {
                final BlockPos centerPos = averageBirdPos;
                allReactorPositions.sort(Comparator.comparingDouble(pos -> pos.distSqr(centerPos)));

                BlockPos nearestReactor = allReactorPositions.get(0);
                spawnPos = EntityUtil.findSafeGroundPosition(level, nearestReactor, 5);
            }
            if (spawnPos == null) {
                spawnPos = EntityUtil.findSafeGroundPosition(level, averageBirdPos, 5);
            }
            spawnPos = EntityUtil.findSafeEntityPositionInCompany(level, door, spawnPos, 12, 4);
            if (spawnPos == null || !FacilityAccessService.isPositionOpen(level.getServer(),
                    level.dimension().location(), spawnPos)) return;
            door.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            door.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1));
            if (!level.addFreshEntity(door)) return;
            data.setDoorSpawned(level, door.blockPosition());
            for (ServerPlayer player : EntityUtil.findAllPlayer(abnormality)) {
                FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                        .showDuration(4000)
                        .fade(500, 1000)
                        .texture(ResourceUtil.createInstance("textures/gui/end_bird_cg/cg0.png"))
                        .build();
                MessageLoader.getLoader().sendToPlayer(player, msg);
            }
            tellBirdsToMoveToDoor(level, data);
        }
    }

    /**
     * 通知所有已出逃的鸟向门移动
     */
    private static void tellBirdsToMoveToDoor(ServerLevel level, BlackForestSavedData data) {
        for (String uuid : data.getEscapedBirdUUIDs()) {
            if (level.getEntity(UUID.fromString(uuid)) instanceof AbstractAbnormality bird) {
                bird.setTarget(null);
                bird.getNavigation().stop();
                if (bird instanceof EntityPunishingBird punishingBird) {
                    punishingBird.clearStateForDoor();
                }
                bird.startMovingToDoor();
            }
        }
    }

    /**
     * 找到还没出逃的鸟
     */
    private static AbstractAbnormality findRemainingBird(ServerLevel level,
                                                          EnumMap<BirdRole, AbstractAbnormality> escaped,
                                                          BirdRole missingRole) {
        if (missingRole == null || escaped.isEmpty()) return null;
        AbstractAbnormality origin = escaped.values().iterator().next();
        AbstractAbnormality best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof AbstractAbnormality bird) || !bird.isAlive() || bird.hasEscape()
                    || roleOf(bird) != missingRole) continue;
            double distance = origin.distanceToSqr(bird);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = bird;
            }
        }
        return best;
    }

    private static EnumMap<BirdRole, AbstractAbnormality> collectEscapedBirdsByRole(
            ServerLevel level, BlackForestSavedData data) {
        EnumMap<BirdRole, AbstractAbnormality> result = new EnumMap<>(BirdRole.class);
        boolean removed = false;
        Iterator<String> iterator = data.getEscapedBirdUUIDs().iterator();
        while (iterator.hasNext()) {
            String uuidString = iterator.next();
            try {
                Entity entity = data.findTrackedEntity(level.getServer(), uuidString);
                if (entity == null) {
                    if (data.shouldRetainMissing(level.getServer(), uuidString)) continue;
                    iterator.remove();
                    data.removeTrackingOnly(uuidString);
                    removed = true;
                    continue;
                }
                if (!(entity instanceof AbstractAbnormality bird) || !bird.isAlive() || !bird.hasEscape()) {
                    iterator.remove();
                    data.removeTrackingOnly(uuidString);
                    removed = true;
                    continue;
                }
                data.observeEscapedBird(bird);
                BirdRole role = roleOf(bird);
                if (role == null || result.putIfAbsent(role, bird) != null) {
                    iterator.remove();
                    removed = true;
                }
            } catch (IllegalArgumentException exception) {
                iterator.remove();
                removed = true;
            }
        }
        if (removed) data.setDirty();
        return result;
    }

    public static boolean isBird(AbstractAbnormality abnormality) {
        return roleOf(abnormality) != null;
    }

    private static BirdRole roleOf(AbstractAbnormality abnormality) {
        if (abnormality instanceof EntityLargeBird) return BirdRole.LARGE;
        if (abnormality instanceof EntityApprovalBird) return BirdRole.JUDGEMENT;
        if (abnormality instanceof EntityPunishingBird) return BirdRole.PUNISHING;
        return null;
    }

    private enum BirdRole { LARGE, JUDGEMENT, PUNISHING }

    @SubscribeEvent
    public static void onBirdLoaded(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof AbstractAbnormality bird)
                || !isBird(bird) || !bird.hasEscape()) return;
        BlackForestSavedData data = BlackForestSavedData.get(level);
        data.addEscapedBird(bird);
        data.requestReevaluation();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != ModDimensions.LOBOTO_KEY || level.getGameTime() % 20L != 0L) return;
        BlackForestSavedData data = BlackForestSavedData.get(level);
        data.retryPendingCapturedBirds(level);
        AbstractAbnormality loadedEscapedBird = null;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractAbnormality bird && isBird(bird)
                    && bird.isAlive() && bird.hasEscape()) {
                data.addEscapedBird(bird);
                if (loadedEscapedBird == null) loadedEscapedBird = bird;
            }
        }
        if (loadedEscapedBird != null && data.consumeReevaluationRequest()) {
            // Chunk loading does not replay escape events, so periodically converge the role state.
            onAbnormalityEscape(new AbnormalityEscapeEvent(loadedEscapedBird));
        }
        if (loadedEscapedBird != null && data.isDoorSpawned()) tellBirdsToMoveToDoor(level, data);
    }

    public static CompoundTag captureBird(AbstractAbnormality bird, String code, BlockPos returnPos) {
        CompoundTag record = new CompoundTag();
        record.putString("Code", code);
        record.putString("Dimension", bird.level().dimension().location().toString());
        if (bird.getHomeRoomDimension() != null) {
            record.putString("HomeRoomDimension", bird.getHomeRoomDimension().toString());
        }
        if (bird.getHomeRoomMarker() != null) {
            record.putLong("HomeRoomMarker", bird.getHomeRoomMarker().asLong());
        }
        BlockPos target = returnPos == null ? bird.blockPosition() : returnPos;
        record.putInt("X", target.getX());
        record.putInt("Y", target.getY());
        record.putInt("Z", target.getZ());
        CompoundTag snapshot = new CompoundTag();
        bird.saveWithoutId(snapshot);
        sanitizeCapturedBirdSnapshot(snapshot, code);
        record.put("Entity", snapshot);
        return record;
    }

    static void sanitizeCapturedBirdSnapshot(CompoundTag snapshot, String code) {
        snapshot.putBoolean("HasEscaped", false);
        snapshot.putBoolean("PendingContainmentReturn", false);
        snapshot.putBoolean("PendingStopEscapeEvent", false);
        snapshot.putBoolean("ContainmentReturnWasSuppression", false);
        snapshot.putBoolean("Invulnerable", false);
        snapshot.putBoolean("NoAI", false);
        snapshot.putShort("Fire", (short) 0);
        snapshot.putShort("HurtTime", (short) 0);
        snapshot.putShort("DeathTime", (short) 0);
        snapshot.putInt("HurtByTimestamp", 0);
        snapshot.remove("EscapePosX");
        snapshot.remove("EscapePosY");
        snapshot.remove("EscapePosZ");
        snapshot.remove("EscapeDimension");
        ListTag motion = new ListTag();
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0D));
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0D));
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0D));
        snapshot.put("Motion", motion);

        if (snapshot.contains("ForgeData", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag persistent = snapshot.getCompound("ForgeData");
            persistent.remove("EscapeTime");
            persistent.remove("NotFoundTick");
        }
        switch (code) {
            case "punishing_bird", "O-02-56" -> {
                snapshot.putBoolean("Angry", false);
                snapshot.putBoolean("Returning", false);
                snapshot.putString("CurrentAction", "idle");
                snapshot.putInt("NormalAttackCount", 0);
                snapshot.putInt("AngryTimer", 0);
                snapshot.putInt("NormalAttackCooldown", 0);
                snapshot.putInt("ReturningTicks", 0);
                snapshot.putInt("ReturnNoProgressTicks", 0);
                snapshot.remove("CurrentHelpTarget");
                snapshot.remove("AngryTarget");
            }
            case "large_bird", "O-02-40" -> {
                snapshot.putString("CurrentAnimation", "idle");
                snapshot.putBoolean("AttackPending", false);
                snapshot.putInt("AttackAnimationTimer", 0);
                snapshot.putInt("AttackSoundTimer", 0);
            }
            case "approval_bird" -> {
                snapshot.putBoolean("IsCharging", false);
                snapshot.putInt("ChargeTick", 0);
                snapshot.putInt("CooldownTick", 0);
            }
            default -> {
            }
        }
    }

    public static boolean tryRestoreCapturedBird(ServerLevel fallbackLevel, CompoundTag record) {
        CompoundTag storedEntity = record.contains("Entity", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? record.getCompound("Entity") : null;
        String homeDimension = record.getString("HomeRoomDimension");
        if (homeDimension.isBlank() && storedEntity != null) {
            homeDimension = storedEntity.getString("HomeRoomDimension");
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(homeDimension);
        if (dimensionId == null) dimensionId = ResourceLocation.tryParse(record.getString("Dimension"));
        ServerLevel level = dimensionId == null ? fallbackLevel : fallbackLevel.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
        if (level == null || !record.contains("X")) return false;

        BlockPos returnPos = new BlockPos(record.getInt("X"), record.getInt("Y"), record.getInt("Z"));
        BlockPos homeMarker = record.contains("HomeRoomMarker")
                ? BlockPos.of(record.getLong("HomeRoomMarker"))
                : storedEntity != null && storedEntity.contains("HomeRoomMarker")
                ? BlockPos.of(storedEntity.getLong("HomeRoomMarker")) : null;
        BlockPos chunkAnchor = homeMarker == null ? returnPos : homeMarker;
        if (!level.hasChunkAt(chunkAnchor)) level.getChunkAt(chunkAnchor);
        AbstractAbnormality bird = createBirdByCode(record.getString("Code"), level);
        if (bird == null) return false;
        CompoundTag sanitizedSnapshot = null;
        if (storedEntity != null) {
            sanitizedSnapshot = storedEntity.copy();
            sanitizeCapturedBirdSnapshot(sanitizedSnapshot, record.getString("Code"));
            bird.load(sanitizedSnapshot);
        }

        BlockPos safeReturn = findCapturedBirdReturnPosition(level, bird, homeMarker, returnPos);
        if (safeReturn == null || !FacilityAccessService.isPositionOpen(level.getServer(),
                level.dimension().location(), safeReturn)) return false;
        boolean alreadyPresent = false;
        Entity existing = level.getEntity(bird.getUUID());
        if (existing != null && !existing.isRemoved()) {
            if (!(existing instanceof AbstractAbnormality existingBird)
                    || existing.getType() != bird.getType()) return false;
            bird = existingBird;
            if (sanitizedSnapshot != null) bird.load(sanitizedSnapshot.copy());
            alreadyPresent = true;
        }
        bird.moveTo(safeReturn.getX() + 0.5D, safeReturn.getY(), safeReturn.getZ() + 0.5D,
                bird.getYRot(), bird.getXRot());
        prepareCapturedBirdForWorld(bird);
        bird.setPersistenceRequired();
        if (!alreadyPresent && !level.addFreshEntity(bird)) return false;
        return true;
    }

    private static BlockPos findCapturedBirdReturnPosition(ServerLevel level, AbstractAbnormality bird,
                                                            BlockPos homeMarker, BlockPos legacyReturn) {
        if (homeMarker != null && level.getBlockEntity(homeMarker) instanceof RoomMarkerBlockEntity marker) {
            AABB roomBounds = marker.getScanBox();
            BlockPos preferred = roomBounds.contains(Vec3.atCenterOf(legacyReturn)) ? legacyReturn : homeMarker;
            int horizontal = Math.min(32, Math.max(0, marker.getRadiusXZ()));
            for (int radius = 0; radius <= horizontal; radius++) {
                for (int dy = -2; dy <= 3; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                            BlockPos candidate = preferred.offset(dx, dy, dz);
                            if (!level.hasChunkAt(candidate)
                                    || !roomBounds.contains(Vec3.atCenterOf(candidate))) continue;
                            if (!level.getBlockState(candidate.below()).isFaceSturdy(
                                    level, candidate.below(), net.minecraft.core.Direction.UP)) continue;
                            AABB box = bird.getDimensions(bird.getPose()).makeBoundingBox(
                                    candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                            if (!level.noCollision(bird, box)) continue;
                            if (!level.getEntities(bird, box,
                                    entity -> entity.isAlive() && !entity.isRemoved()).isEmpty()) continue;
                            return candidate;
                        }
                    }
                }
            }
            return null;
        }
        return EntityUtil.findSafeEntityPositionInCompany(level, bird, legacyReturn, 24, 8);
    }

    /**
     * Persists every captured bird before attempting to put it back into the world. A failed
     * placement, chunk unload or server stop therefore leaves a durable retry record instead of
     * losing the original abnormality with the Apocalypse Bird entity.
     */
    public static void queueAndRestoreCapturedBirds(ServerLevel fallbackLevel,
                                                     Collection<CompoundTag> records) {
        if (records == null || records.isEmpty()) return;
        BlackForestSavedData data = BlackForestSavedData.get(fallbackLevel);
        data.queueCapturedBirds(records);
        data.retryPendingCapturedBirds(fallbackLevel);
    }

    private static void prepareCapturedBirdForWorld(AbstractAbnormality bird) {
        bird.setEscape(false);
        bird.setQliphothCounter(bird.getMaxQliphothCounter());
        bird.setNoAi(false);
        bird.setInvulnerable(false);
        bird.setTarget(null);
        bird.setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (bird instanceof EntityPunishingBird punishingBird) {
            punishingBird.resetForBlackForestRestore();
        } else if (bird instanceof EntityLargeBird largeBird) {
            largeBird.resetForBlackForestRestore();
        } else if (bird instanceof EntityApprovalBird approvalBird) {
            approvalBird.resetForBlackForestRestore();
        }
        bird.setHealth(bird.getMaxHealth());
    }

    private static AbstractAbnormality createBirdByCode(String code, ServerLevel level) {
        return switch (code) {
            case "large_bird", "O-02-40" -> new EntityLargeBird(ModEntities.large_bird.get(), level);
            case "approval_bird" -> new EntityApprovalBird(ModEntities.approval_bird.get(), level);
            case "punishing_bird", "O-02-56" -> new EntityPunishingBird(ModEntities.punishing_bird.get(), level);
            default -> null;
        };
    }

    public static class BlackForestSavedData extends SavedData {
        private static final String NAME = "black_forest_event";

        private final Set<String> escapedBirdUUIDs = new HashSet<>();
        private final Map<String, TrackedBird> escapedBirdRecords = new HashMap<>();
        private final List<CompoundTag> pendingCapturedBirds = new ArrayList<>();
        private boolean doorSpawned = false;
        private ResourceLocation doorDimension;
        private BlockPos doorPosition;
        private transient boolean resolvingMissingBird;
        private transient boolean reevaluationRequested = true;

        public static BlackForestSavedData get(ServerLevel level) {
            // Keep the established per-dimension storage location so active legacy
            // encounters and queued captured-bird snapshots survive an upgrade.
            return level.getDataStorage()
                    .computeIfAbsent(BlackForestSavedData::load, BlackForestSavedData::new, NAME);
        }

        public void addEscapedBird(String uuid) {
            escapedBirdUUIDs.add(uuid);
            setDirty();
        }

        public void addEscapedBird(AbstractAbnormality bird) {
            escapedBirdUUIDs.add(bird.getStringUUID());
            observeEscapedBird(bird);
        }

        public void observeEscapedBird(AbstractAbnormality bird) {
            escapedBirdRecords.put(bird.getStringUUID(), new TrackedBird(
                    bird.level().dimension().location(), bird.blockPosition().immutable()));
            setDirty();
        }

        public void removeEscapedBird(String uuid) {
            escapedBirdUUIDs.remove(uuid);
            escapedBirdRecords.remove(uuid);
            setDirty();
        }

        public void removeTrackingOnly(String uuid) {
            escapedBirdRecords.remove(uuid);
        }

        public void clearEscapedBirds() {
            escapedBirdUUIDs.clear();
            escapedBirdRecords.clear();
            setDirty();
        }

        public Set<String> getEscapedBirdUUIDs() {
            return escapedBirdUUIDs;
        }

        public int getEscapedCount() {
            return escapedBirdUUIDs.size();
        }

        public boolean isDoorSpawned() { return doorSpawned; }
        public void setDoorSpawned(boolean value) {
            this.doorSpawned = value;
            if (!value) {
                doorDimension = null;
                doorPosition = null;
            }
            setDirty();
        }
        public void setDoorSpawned(ServerLevel level, BlockPos position) {
            doorSpawned = true;
            doorDimension = level.dimension().location();
            doorPosition = position.immutable();
            setDirty();
        }
        public boolean isDoorLocationLoaded(net.minecraft.server.MinecraftServer server) {
            if (doorDimension == null || doorPosition == null) return false;
            ServerLevel level = server.getLevel(ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, doorDimension));
            return level != null && level.hasChunkAt(doorPosition);
        }
        public boolean isResolvingMissingBird() { return resolvingMissingBird; }
        public void setResolvingMissingBird(boolean value) { resolvingMissingBird = value; }
        public void requestReevaluation() { reevaluationRequested = true; }
        public boolean consumeReevaluationRequest() {
            if (!reevaluationRequested) return false;
            reevaluationRequested = false;
            return true;
        }

        public Entity findTrackedEntity(net.minecraft.server.MinecraftServer server, String uuidString) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException exception) {
                return null;
            }
            TrackedBird tracked = escapedBirdRecords.get(uuidString);
            if (tracked != null) {
                ServerLevel level = server.getLevel(ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, tracked.dimension()));
                if (level != null) return level.getEntity(uuid);
            }
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) return entity;
            }
            return null;
        }

        public boolean shouldRetainMissing(net.minecraft.server.MinecraftServer server, String uuidString) {
            TrackedBird tracked = escapedBirdRecords.get(uuidString);
            if (tracked == null) return true;
            ServerLevel level = server.getLevel(ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, tracked.dimension()));
            return level == null || !level.hasChunkAt(tracked.position());
        }

        public void queueCapturedBird(CompoundTag record) {
            queueCapturedBirds(List.of(record));
        }

        public void queueCapturedBirds(Collection<CompoundTag> records) {
            boolean changed = false;
            for (CompoundTag record : records) {
                if (record == null || record.isEmpty() || containsCapturedBird(record)) continue;
                pendingCapturedBirds.add(record.copy());
                changed = true;
            }
            if (changed) setDirty();
        }

        public void retryPendingCapturedBirds(ServerLevel fallbackLevel) {
            if (pendingCapturedBirds.isEmpty()) return;
            boolean changed = pendingCapturedBirds.removeIf(record -> tryRestoreCapturedBird(fallbackLevel, record));
            if (changed) setDirty();
        }

        private boolean containsCapturedBird(CompoundTag candidate) {
            for (CompoundTag pending : pendingCapturedBirds) {
                if (sameCapturedBird(pending, candidate)) return true;
            }
            return false;
        }

        private static boolean sameCapturedBird(CompoundTag first, CompoundTag second) {
            UUID firstUuid = capturedBirdUuid(first);
            UUID secondUuid = capturedBirdUuid(second);
            if (firstUuid != null && secondUuid != null) return firstUuid.equals(secondUuid);
            return first.getString("Code").equals(second.getString("Code"))
                    && first.getString("Dimension").equals(second.getString("Dimension"))
                    && first.getInt("X") == second.getInt("X")
                    && first.getInt("Y") == second.getInt("Y")
                    && first.getInt("Z") == second.getInt("Z");
        }

        private static UUID capturedBirdUuid(CompoundTag record) {
            if (!record.contains("Entity", net.minecraft.nbt.Tag.TAG_COMPOUND)) return null;
            CompoundTag entity = record.getCompound("Entity");
            return entity.hasUUID("UUID") ? entity.getUUID("UUID") : null;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (String uuid : escapedBirdUUIDs) {
                list.add(StringTag.valueOf(uuid));
            }
            tag.put("EscapedBirds", list);
            ListTag records = new ListTag();
            escapedBirdRecords.forEach((uuid, tracked) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("UUID", uuid);
                entry.putString("Dimension", tracked.dimension().toString());
                entry.putInt("X", tracked.position().getX());
                entry.putInt("Y", tracked.position().getY());
                entry.putInt("Z", tracked.position().getZ());
                records.add(entry);
            });
            tag.put("EscapedBirdRecords", records);
            ListTag pending = new ListTag();
            pendingCapturedBirds.forEach(record -> pending.add(record.copy()));
            tag.put("PendingCapturedBirds", pending);
            tag.putBoolean("DoorSpawned", doorSpawned);
            if (doorDimension != null) tag.putString("DoorDimension", doorDimension.toString());
            if (doorPosition != null) {
                tag.putInt("DoorX", doorPosition.getX());
                tag.putInt("DoorY", doorPosition.getY());
                tag.putInt("DoorZ", doorPosition.getZ());
            }
            return tag;
        }

        public static BlackForestSavedData load(CompoundTag tag) {
            BlackForestSavedData data = new BlackForestSavedData();
            ListTag list = tag.getList("EscapedBirds", 8); // 8 = TAG_String
            for (int i = 0; i < list.size(); i++) {
                data.escapedBirdUUIDs.add(list.getString(i));
            }
            ListTag records = tag.getList("EscapedBirdRecords", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < records.size(); i++) {
                CompoundTag entry = records.getCompound(i);
                ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
                if (dimension != null && entry.contains("UUID") && entry.contains("X")) {
                    data.escapedBirdRecords.put(entry.getString("UUID"), new TrackedBird(dimension,
                            new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"))));
                }
            }
            ListTag pending = tag.getList("PendingCapturedBirds", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < pending.size(); i++) data.pendingCapturedBirds.add(pending.getCompound(i).copy());
            data.doorSpawned = tag.getBoolean("DoorSpawned");
            data.doorDimension = ResourceLocation.tryParse(tag.getString("DoorDimension"));
            if (tag.contains("DoorX")) {
                data.doorPosition = new BlockPos(tag.getInt("DoorX"), tag.getInt("DoorY"), tag.getInt("DoorZ"));
            }
            return data;
        }

        private record TrackedBird(ResourceLocation dimension, BlockPos position) {
        }
    }
}
