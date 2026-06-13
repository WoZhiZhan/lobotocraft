package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 计数器归零后,在最近的再生反应堆处生成"伊莎玛拉"的钩子。
 */
public class SpawnIsharmlaHook {

    public static void trySpawnIsharmlaAtNearestReactor(ServerLevel level, BlockPos origin) {
        BlockPos reactorPos = findNearestReactor(level, origin);
        if (reactorPos == null) {
            reactorPos = origin;
        }
        EntityIsharmla isharmla = ModEntities.isharmla.get().create(level);
        if (isharmla == null) return;
        isharmla.moveTo(reactorPos.getX() + 0.5, reactorPos.getY(), reactorPos.getZ() + 0.5, 0f, 0f);
        isharmla.finalizeSpawn(level, level.getCurrentDifficultyAt(reactorPos),
                MobSpawnType.EVENT, null, null);
        isharmla.setPersistenceRequired();
        level.addFreshEntity(isharmla);
        // 初始化形态并瞬移到反应堆、生成之泪
        isharmla.onSpawnFromSkadi(level);
    }

    /** 在原点附近查找最近的再生反应堆方块实体位置 */
    private static BlockPos findNearestReactor(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockEntity be : EntityUtil.findBlockEntities(level, origin, 64)) {
            if (be instanceof RegenerationReactorBlockEntity) {
                double d = be.getBlockPos().distSqr(origin);
                if (d < bestDist) {
                    bestDist = d;
                    best = be.getBlockPos();
                }
            }
        }
        return best;
    }

    /** 供伊莎玛拉自身定位反应堆使用 */
    public static BlockPos findNearestReactorPublic(ServerLevel level, BlockPos origin) {
        return findNearestReactor(level, origin);
    }
}
