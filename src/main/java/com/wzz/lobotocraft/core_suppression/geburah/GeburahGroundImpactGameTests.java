package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder("lobotocraft_ego_ground")
@PrefixGameTestTemplate(false)
public final class GeburahGroundImpactGameTests {
    @GameTest(template = "workcloseregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_ego_ground", timeoutTicks = 40,
            batch = "ego_ground_cosmetic")
    public static void repeatedProductionImpactsDoNotDestroyOrSpawnEntities(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(5, 1, 5));
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos pos = floor.offset(x, 0, z);
            BlockState state = (x == 0 ? Blocks.IRON_BLOCK : Blocks.STONE).defaultBlockState();
            level.setBlock(pos, state, 3);
            states.put(pos, state);
        }
        Vec3 origin = Vec3.atBottomCenterOf(floor.above()).add(0, 0, -4.176D);
        Vec3 forward = new Vec3(0, 0, 1);
        long before = entityCount(h);
        for (int i = 0; i < 12; i++) {
            GeburahAttackEffects.spawn(level, GeburahMove.VERTICAL_SLASH,
                    true, true, origin, forward);
            GeburahAttackEffects.spawn(level, GeburahMove.VERTICAL_SLASH,
                    false, false, origin, forward);
            GeburahAttackEffects.spawn(level, GeburahMove.VERTICAL_SLASH,
                    true, false, origin, forward);
        }
        states.forEach((pos, state) -> h.assertTrue(level.getBlockState(pos).equals(state),
                "cosmetic impact modified facility floor at " + pos));
        h.assertTrue(entityCount(h) == before, "impact spawned falling blocks or item entities");
        h.succeed();
    }

    private static long entityCount(GameTestHelper h) {
        long count = 0;
        for (var ignored : h.getLevel().getAllEntities()) count++;
        return count;
    }
}
