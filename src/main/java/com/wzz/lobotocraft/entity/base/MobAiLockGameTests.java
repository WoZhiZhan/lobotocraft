package com.wzz.lobotocraft.entity.base;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.util.MobAiLockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(MobAiLockGameTests.TEST_NAMESPACE)
public final class MobAiLockGameTests {
    static final String TEST_NAMESPACE = ModMain.MODID + "_mob_ai_lock";

    private MobAiLockGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "stacked_ai_locks")
    public static void stackedSourcesRestoreOnlyAfterFinalRelease(GameTestHelper helper) {
        Zombie activeMob = spawnZombie(helper, new BlockPos(3, 1, 3), false);
        Zombie originallyLockedMob = spawnZombie(helper, new BlockPos(7, 1, 3), true);
        if (activeMob == null || originallyLockedMob == null) {
            if (activeMob != null) activeMob.discard();
            if (originallyLockedMob != null) originallyLockedMob.discard();
            helper.fail("could not create AI lock fixtures");
            return;
        }

        try {
            MobAiLockUtil.acquire(activeMob, "gametest:panic");
            MobAiLockUtil.acquire(activeMob, "gametest:freeze");
            helper.assertTrue(activeMob.isNoAi(), "two active sources did not disable AI");

            MobAiLockUtil.release(activeMob, "gametest:panic");
            helper.assertTrue(activeMob.isNoAi(), "releasing one source restored AI too early");

            MobAiLockUtil.release(activeMob, "gametest:freeze");
            helper.assertFalse(activeMob.isNoAi(), "final release did not restore active AI");

            MobAiLockUtil.acquire(originallyLockedMob, "gametest:freeze");
            MobAiLockUtil.acquire(originallyLockedMob, "gametest:charm");
            MobAiLockUtil.release(originallyLockedMob, "gametest:freeze");
            MobAiLockUtil.release(originallyLockedMob, "gametest:charm");
            helper.assertTrue(originallyLockedMob.isNoAi(),
                    "final release cleared a pre-existing NoAI state");
            helper.succeed();
        } finally {
            activeMob.discard();
            originallyLockedMob.discard();
        }
    }

    private static Zombie spawnZombie(GameTestHelper helper, BlockPos relativePos, boolean noAi) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) return null;
        BlockPos position = helper.absolutePos(relativePos);
        zombie.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        zombie.setNoAi(noAi);
        helper.getLevel().addFreshEntity(zombie);
        return zombie;
    }
}
