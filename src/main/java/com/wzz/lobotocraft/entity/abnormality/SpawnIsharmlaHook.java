package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.item.SkadiBanishData;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/** Creates Isharmla after Dark Skadi's counter reaches zero. */
public final class SpawnIsharmlaHook {
    private SpawnIsharmlaHook() {
    }

    static boolean trySpawnTransaction(ServerLevel level, BlockPos reactorPos, UUID transactionId) {
        String transaction = transactionId.toString();
        for (EntityIsharmla existing : level.getEntitiesOfClass(EntityIsharmla.class,
                new net.minecraft.world.phys.AABB(reactorPos).inflate(32.0D), EntityIsharmla::isAlive)) {
            if (transaction.equals(existing.getPersistentData().getString("SkadiTransitionId"))) return true;
        }
        EntityIsharmla isharmla = ModEntities.isharmla.get().create(level);
        if (isharmla == null) return false;
        BlockPos spawnPos = findCollisionSafeSpawn(level, reactorPos, isharmla);
        if (spawnPos == null) return false;
        isharmla.getPersistentData().putString("SkadiTransitionId", transaction);
        SkadiBanishData origin = SkadiBanishData.get(level);
        if (origin.getHomeDimension() != null && origin.getHomeMarker() != null) {
            isharmla.bindHomeRoom(origin.getHomeDimension(), origin.getHomeMarker());
        }
        isharmla.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        isharmla.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.EVENT, null, null);
        isharmla.setPersistenceRequired();
        if (!level.addFreshEntity(isharmla)) return false;
        isharmla.onSpawnFromSkadi(level, reactorPos);
        return true;
    }

    private static BlockPos findCollisionSafeSpawn(ServerLevel level, BlockPos reactorPos,
                                                    EntityIsharmla isharmla) {
        for (int range : new int[]{0, 4, 8}) {
            BlockPos candidate = EntityUtil.findReactorSpawnPositionInCompany(level, reactorPos, range);
            if (candidate == null || !FacilityAccessService.isPositionOpen(level.getServer(),
                    level.dimension().location(), candidate)) continue;
            double x = candidate.getX() + 0.5D;
            double y = candidate.getY();
            double z = candidate.getZ() + 0.5D;
            if (level.noCollision(isharmla,
                    isharmla.getDimensions(isharmla.getPose()).makeBoundingBox(x, y, z))) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockPos findNearestReactor(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level, origin, 64)) {
            if (blockEntity instanceof RegenerationReactorBlockEntity
                    && FacilityAccessService.isPositionOpen(level.getServer(),
                    level.dimension().location(), blockEntity.getBlockPos())) {
                double distance = blockEntity.getBlockPos().distSqr(origin);
                if (distance < bestDist) {
                    bestDist = distance;
                    best = blockEntity.getBlockPos();
                }
            }
        }
        return best;
    }

    public static BlockPos findNearestReactorPublic(ServerLevel level, BlockPos origin) {
        return findNearestReactor(level, origin);
    }
}
