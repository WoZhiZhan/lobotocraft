package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.abnormality.EntityBlackForestDoor;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.abnormality.EntityLargeBird;
import com.wzz.lobotocraft.entity.abnormality.EntityPunishingBird;
import com.wzz.lobotocraft.entity.abnormality.EntityApprovalBird;
import com.wzz.lobotocraft.event.abnormality.AbnormalityEscapeEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FullScreenRenderMessage;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
        data.addEscapedBird(abnormality.getStringUUID());
        // 清理无效UUID并获取真实出逃数量
        int validCount = 0;
        Iterator<String> iterator = data.getEscapedBirdUUIDs().iterator();
        while (iterator.hasNext()) {
            String uuidStr = iterator.next();
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Entity entity = level.getEntity(uuid);
                if (entity instanceof AbstractAbnormality bird && bird.hasEscape() && bird.isAlive()) {
                    validCount++;
                } else {
                    iterator.remove();
                }
            } catch (IllegalArgumentException e) {
                iterator.remove();
            }
        }
        if (validCount != data.getEscapedBirdUUIDs().size()) {
            data.setDirty();
        }

        // 检查门是否实际存在
        if (data.isDoorSpawned()) {
            boolean doorExists = !level.getEntitiesOfClass(EntityBlackForestDoor.class,
                    new AABB(abnormality.blockPosition()).inflate(500),
                    LivingEntity::isAlive).isEmpty();
            if (!doorExists) {
                data.setDoorSpawned(false);
                data.getEscapedBirdUUIDs().clear();
                data.setDirty();
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

            AbstractAbnormality otherBird = findRemainingBird(level, data);
            if (otherBird != null) {
                otherBird.triggerEscape();
                data.addEscapedBird(otherBird.getStringUUID());
            } else return;

            data.setDoorSpawned(true);
            for (ServerPlayer player : EntityUtil.findAllPlayer(abnormality)) {
                FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                        .showDuration(4000)
                        .fade(500, 1000)
                        .texture(ResourceUtil.createInstance("textures/gui/end_bird_cg/cg0.png"))
                        .build();
                MessageLoader.getLoader().sendToPlayer(player, msg);
            }
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
                if (be instanceof RegenerationReactorBlockEntity) {
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
            door.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            door.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1));
            level.addFreshEntity(door);
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
    private static AbstractAbnormality findRemainingBird(ServerLevel level, BlackForestSavedData data) {
        Set<String> escapedUUIDs = data.getEscapedBirdUUIDs();
        for (String uuid : escapedUUIDs) {
            if (level.getEntity(UUID.fromString(uuid)) instanceof AbstractAbnormality escapedBird) {
                List<AbstractAbnormality> allBirds = EntityUtil.findEntitiesAround(
                        escapedBird, 256, 256, AbstractAbnormality.class
                );
                for (AbstractAbnormality bird : allBirds) {
                    if (isBird(bird) && !escapedUUIDs.contains(bird.getStringUUID()) && !bird.hasEscape()) {
                        return bird;
                    }
                }
            }
        }
        return null;
    }

    public static boolean isBird(AbstractAbnormality abnormality) {
        return abnormality instanceof EntityLargeBird
                || abnormality instanceof EntityPunishingBird
                || abnormality instanceof EntityApprovalBird;
    }

    public static class BlackForestSavedData extends SavedData {
        private static final String NAME = "black_forest_event";

        private final Set<String> escapedBirdUUIDs = new HashSet<>();
        private boolean doorSpawned = false;

        public static BlackForestSavedData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(BlackForestSavedData::load, BlackForestSavedData::new, NAME);
        }

        public void addEscapedBird(String uuid) {
            escapedBirdUUIDs.add(uuid);
            setDirty();
        }

        public void removeEscapedBird(String uuid) {
            escapedBirdUUIDs.remove(uuid);
            setDirty();
        }

        public Set<String> getEscapedBirdUUIDs() {
            return escapedBirdUUIDs;
        }

        public int getEscapedCount() {
            return escapedBirdUUIDs.size();
        }

        public boolean isDoorSpawned() { return doorSpawned; }
        public void setDoorSpawned(boolean value) { this.doorSpawned = value; setDirty(); }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (String uuid : escapedBirdUUIDs) {
                list.add(StringTag.valueOf(uuid));
            }
            tag.put("EscapedBirds", list);
            tag.putBoolean("DoorSpawned", doorSpawned);
            return tag;
        }

        public static BlackForestSavedData load(CompoundTag tag) {
            BlackForestSavedData data = new BlackForestSavedData();
            ListTag list = tag.getList("EscapedBirds", 8); // 8 = TAG_String
            for (int i = 0; i < list.size(); i++) {
                data.escapedBirdUUIDs.add(list.getString(i));
            }
            data.doorSpawned = tag.getBoolean("DoorSpawned");
            return data;
        }
    }
}