package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("lobotocraft_skadi_archive")
@PrefixGameTestTemplate(false)
public final class SkadiArchiveRegressionGameTests {
    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_skadi_archive", timeoutTicks = 20)
    public static void giftInformationUnlocksAtLevelFourLikeBlueStar(GameTestHelper helper) {
        var skadi = ModEntities.skadi_corrupted.get().create(helper.getLevel());
        var star = ModEntities.blue_star.get().create(helper.getLevel());
        helper.assertTrue(skadi != null && star != null, "missing archive test entities");
        for (int level = 1; level <= 4; level++) {
            helper.assertTrue(skadi.getObservationBonus(level).unlocksGift()
                    == star.getObservationBonus(level).unlocksGift(),
                    "Skadi gift information disagrees with ALEPH level-four unlock at level " + level);
        }
        helper.assertTrue(skadi.getEGOGiftData() != null, "suppression trophy information missing");
        helper.succeed();
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_skadi_archive", timeoutTicks = 20)
    public static void workGiftRateIsZeroNotUnknown(GameTestHelper helper) {
        var skadi = ModEntities.skadi_corrupted.get().create(helper.getLevel());
        helper.assertTrue(skadi != null && skadi.getGiftProbability() == 0.0F,
                "suppression-only trophy must display a zero work acquisition rate");
        helper.succeed();
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_skadi_archive", timeoutTicks = 20)
    public static void secondPhaseTargetingDoesNotRequireHumanPhaseAggro(GameTestHelper helper) throws Exception {
        var boss = ModEntities.isharmla.get().create(helper.getLevel());
        var near = ModEntities.clerk.get().create(helper.getLevel());
        var far = ModEntities.clerk.get().create(helper.getLevel());
        helper.assertTrue(boss != null && near != null && far != null, "missing targeting fixtures");
        var origin = helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 6));
        boss.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
        near.moveTo(boss.getX() + 2, boss.getY(), boss.getZ(), 0, 0);
        far.moveTo(boss.getX() + 7, boss.getY(), boss.getZ(), 0, 0);
        helper.getLevel().addFreshEntity(boss);
        helper.getLevel().addFreshEntity(near);
        helper.getLevel().addFreshEntity(far);
        try {
            var form = EntityIsharmla.class.getDeclaredMethod("setForm", EntityIsharmla.Form.class);
            form.setAccessible(true);
            form.invoke(boss, EntityIsharmla.Form.MONSTER);
            var targets = EntityIsharmla.class.getDeclaredMethod("findNearestTarget",
                    net.minecraft.server.level.ServerLevel.class, double.class);
            targets.setAccessible(true);
            helper.assertTrue(targets.invoke(boss, helper.getLevel(), 24D) == near,
                    "unattacked personnel excluded from second-phase targeting");
            boss.setLastHurtByMob(far);
            helper.assertTrue(targets.invoke(boss, helper.getLevel(), 24D) == near,
                    "old hurt attribution overrides current independent targeting");
            helper.succeed();
        } finally { boss.discard(); near.discard(); far.discard(); }
    }
}
