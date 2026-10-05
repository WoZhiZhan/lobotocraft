package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("lobotocraft_placement_root")
@PrefixGameTestTemplate(false)
public final class PlacementRootRegressionGameTests {
    private static final String TEMPLATE = "workarchiveregressiongametests.navigation_empty";
    private static final String NS = "lobotocraft_placement_root";

    @GameTest(template = TEMPLATE, templateNamespace = NS, timeoutTicks = 40)
    public static void naturalBirdNestReturnsAboveFloorNotIntoSupportedUndergroundPocket(GameTestHelper helper) {
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 12; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        }
        BlockPos origin = helper.absolutePos(new BlockPos(6, 3, 6));
        var bird = ModEntities.punishing_bird.get().create(helper.getLevel());
        helper.assertTrue(bird != null, "missing bird");
        bird.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
        bird.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(origin),
                MobSpawnType.EVENT, null, null);
        helper.getLevel().addFreshEntity(bird);
        try {
            bird.triggerEscape();
            bird.stopEscape();
            helper.assertTrue(!bird.hasEscape() && bird.getY() >= origin.getY(),
                    "bird returned to supported pocket below floor: " + bird.position());
            helper.assertTrue(helper.getLevel().noCollision(bird), "return still intersects the nest/floor");
            helper.succeed();
        } finally { bird.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = NS, timeoutTicks = 40)
    public static void genuinelyAirborneUnregisteredOriginStillUsesSupportedLowerFallback(GameTestHelper helper) {
        CensoredLifecycleGameTests.unregisteredSuppressionUsesSupportedFallbackForAirborneOrigin(helper);
    }

    @GameTest(template = TEMPLATE, templateNamespace = NS, timeoutTicks = 40)
    public static void fiveTearsAtWallFitAndRemainInSameVisibleAirPocket(GameTestHelper helper) throws Exception {
        floor(helper);
        for (int z = 1; z <= 12; z++) for (int y = 1; y <= 3; y++)
            helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        var boss = boss(helper);
        var summon = EntityIsharmla.class.getDeclaredMethod("spawnTears");
        summon.setAccessible(true);
        try {
            for (int seed = 0; seed < 8; seed++) {
                boss.getRandom().setSeed(7531L + seed);
                summon.invoke(boss);
                var tears = tears(helper);
                helper.assertTrue(tears.size() == 5, "adequate room lost summon count: " + tears.size());
                for (int i = 0; i < tears.size(); i++) {
                    var tear = tears.get(i);
                    helper.assertTrue(helper.getLevel().noCollision(tear), "tear embedded in wall: " + tear.position());
                    helper.assertTrue(tear.getBoundingBox().maxX <= helper.absolutePos(new BlockPos(7, 1, 1)).getX(),
                            "safe-position search placed tear through solid wall");
                    helper.assertTrue(Math.abs(tear.getY() - boss.getY()) < .001, "tear changed floors");
                    for (int j = 0; j < i; j++)
                        helper.assertFalse(tear.getBoundingBox().intersects(tears.get(j).getBoundingBox()),
                                "summoned tears overlap each other");
                }
                tears.forEach(EntityIsharmlaTear::discard);
            }
            helper.succeed();
        } finally {
            tears(helper).forEach(EntityIsharmlaTear::discard);
            boss.discard();
        }
    }

    @GameTest(template = TEMPLATE, templateNamespace = NS, timeoutTicks = 40)
    public static void insufficientSpaceDoesNotForceTearsIntoSolidWalls(GameTestHelper helper) throws Exception {
        floor(helper);
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 12; z++) for (int y = 1; y <= 3; y++)
            helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(6, y, 6), Blocks.AIR);
        var boss = boss(helper);
        var summon = EntityIsharmla.class.getDeclaredMethod("spawnTears");
        summon.setAccessible(true);
        try {
            summon.invoke(boss);
            helper.assertTrue(tears(helper).isEmpty(), "summon forced tears into walls despite no free space");
            helper.succeed();
        } finally {
            tears(helper).forEach(EntityIsharmlaTear::discard);
            boss.discard();
        }
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 12; z++)
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static EntityIsharmla boss(GameTestHelper helper) {
        var boss = ModEntities.isharmla.get().create(helper.getLevel());
        if (boss == null) throw new IllegalStateException("missing boss");
        var pos = helper.absolutePos(new BlockPos(6, 1, 6));
        boss.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        boss.setNoAi(true);
        helper.getLevel().addFreshEntity(boss);
        return boss;
    }

    private static java.util.List<EntityIsharmlaTear> tears(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(EntityIsharmlaTear.class,
                new AABB(helper.absolutePos(new BlockPos(0, 0, 0)),
                        helper.absolutePos(new BlockPos(14, 5, 14))));
    }
}
