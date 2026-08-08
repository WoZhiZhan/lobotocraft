package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(ModMain.MODID)
public final class GreenNoonCombatGameTests {
    private GreenNoonCombatGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "green_noon_combat")
    public static void rangedAttackStopsAtSolidWall(GameTestHelper helper) {
        EntityGreenNoon greenNoon = ModEntities.green_noon.get().create(helper.getLevel());
        Pig target = EntityType.PIG.create(helper.getLevel());
        if (greenNoon == null || target == null) {
            helper.fail("green noon or target could not be created");
            return;
        }

        BlockPos attackerPos = helper.absolutePos(new BlockPos(2, 1, 4));
        BlockPos targetPos = helper.absolutePos(new BlockPos(6, 1, 4));
        greenNoon.moveTo(attackerPos.getX() + 0.5D, attackerPos.getY(),
                attackerPos.getZ() + 0.5D, 0.0F, 0.0F);
        target.moveTo(targetPos.getX() + 0.5D, targetPos.getY(),
                targetPos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(greenNoon);
        helper.getLevel().addFreshEntity(target);

        for (int y = 0; y <= 7; y++) {
            for (int z = 1; z <= 7; z++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
        float healthBefore = target.getHealth();
        greenNoon.tick();
        if (target.getHealth() != healthBefore
                || greenNoon.hasClearShot(helper.getLevel(), target)) {
            greenNoon.discard();
            target.discard();
            helper.fail("green noon ranged damage crossed a solid wall");
            return;
        }

        for (int y = 0; y <= 7; y++) {
            for (int z = 1; z <= 7; z++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.AIR);
            }
        }
        greenNoon.tick();
        if (target.getHealth() >= healthBefore
                || !greenNoon.hasClearShot(helper.getLevel(), target)) {
            greenNoon.discard();
            target.discard();
            helper.fail("green noon did not resume ranged damage with a clear shot");
            return;
        }

        greenNoon.discard();
        target.discard();
        helper.succeed();
    }
}
