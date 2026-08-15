package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(ModMain.MODID)
public final class CensoredLifecycleGameTests {
    private CensoredLifecycleGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_lifecycle")
    public static void escapeAndSuppressionUseSharedContainmentLifecycle(GameTestHelper helper) {
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 3));
        censored.tick();
        helper.assertTrue(!censored.hasEscape(), "CENSORED spawned in the escaped state");
        helper.assertTrue(censored.isNoAi(), "contained CENSORED did not disable AI");

        censored.triggerEscape();
        helper.assertTrue(censored.hasEscape(), "CENSORED did not enter the escaped state");
        helper.assertTrue(!censored.isNoAi(), "escaped CENSORED kept NoAI enabled");

        censored.stopEscape();
        helper.assertTrue(!censored.hasEscape(), "suppressed CENSORED stayed escaped");
        helper.assertTrue(censored.isNoAi(), "returned CENSORED did not restore containment AI state");
        censored.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 100, required = true, batch = "censored_lifecycle")
    public static void tombstoneSacrificeSpawnsMinionAfterAnimation(GameTestHelper helper) {
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 3));
        censored.triggerEscape();
        BlockPos tombstone = censored.blockPosition().east();
        helper.getLevel().setBlockAndUpdate(tombstone, ModBlocks.TOMBSTONE.get().defaultBlockState());

        helper.runAfterDelay(70, () -> {
            int minionCount = helper.getLevel().getEntitiesOfClass(
                    EntityCensoredMinion.class, censored.getBoundingBox().inflate(8.0D)).size();
            helper.assertTrue(minionCount == 1,
                    "CENSORED sacrifice produced " + minionCount + " minions instead of one");
            helper.assertTrue(!helper.getLevel().getBlockState(tombstone).is(ModBlocks.TOMBSTONE.get()),
                    "CENSORED did not consume the tombstone");
            censored.discard();
            helper.getLevel().getEntitiesOfClass(EntityCensoredMinion.class,
                    censored.getBoundingBox().inflate(8.0D)).forEach(EntityCensoredMinion::discard);
            helper.succeed();
        });
    }

    private static EntityCensored spawnCensored(GameTestHelper helper, BlockPos relativePos) {
        EntityCensored censored = ModEntities.censored.get().create(helper.getLevel());
        if (censored == null) {
            helper.fail("CENSORED could not be created");
            throw new IllegalStateException("CENSORED could not be created");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        censored.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(censored);
        return censored;
    }
}
