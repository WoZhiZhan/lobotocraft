package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("lobotocraft_door_clearance")
@PrefixGameTestTemplate(false)
public final class DoorClearanceRegressionGameTests {
    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_door_clearance", timeoutTicks = 500)
    public static void originalWidthGroundNavigationTraversesOpenSingleDoor(GameTestHelper helper) {
        exercise(helper, Direction.NORTH, DoorHingeSide.LEFT, .8F, true);
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_door_clearance", timeoutTicks = 500)
    public static void rightHingeDoorAlsoUsesActualPanel(GameTestHelper helper) {
        exercise(helper, Direction.SOUTH, DoorHingeSide.RIGHT, .8F, true);
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_door_clearance", timeoutTicks = 500)
    public static void eastFacingDoorCorrectsZNotX(GameTestHelper helper) {
        exercise(helper, Direction.EAST, DoorHingeSide.LEFT, .8F, true);
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_door_clearance", timeoutTicks = 500)
    public static void westFacingRightHingeDoorHasNoSpeciesException(GameTestHelper helper) {
        exercise(helper, Direction.WEST, DoorHingeSide.RIGHT, .8F, true);
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_door_clearance", timeoutTicks = 500)
    public static void centeredThinBodyStillTraverses(GameTestHelper helper) {
        exercise(helper, Direction.NORTH, DoorHingeSide.LEFT, .6F, true);
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_door_clearance", timeoutTicks = 500)
    public static void closedIronDoorIsNotForcedOpen(GameTestHelper helper) {
        exercise(helper, Direction.NORTH, DoorHingeSide.LEFT, .8F, false);
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_door_clearance", timeoutTicks = 500)
    public static void bodyTooWideDoesNotPhaseThroughOpenDoor(GameTestHelper helper) {
        exercise(helper, Direction.NORTH, DoorHingeSide.LEFT, .9F, true);
    }

    @GameTest(template = "workarchiveregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_door_clearance", timeoutTicks = 500)
    public static void reversePassageUsesSamePhysicalClearance(GameTestHelper helper) {
        exercise(helper, Direction.NORTH, DoorHingeSide.LEFT, .8F, true, true);
    }

    private static void exercise(GameTestHelper helper, Direction facing, DoorHingeSide hinge,
                                 float width, boolean open) {
        exercise(helper, facing, hinge, width, open, false);
    }

    private static void exercise(GameTestHelper helper, Direction facing, DoorHingeSide hinge,
                                 float width, boolean open, boolean reverse) {
        boolean alongZ = facing.getAxis() == Direction.Axis.Z;
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 12; z++)
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        for (int axis = 1; axis <= 12; axis++) for (int y = 1; y <= 3; y++) {
            helper.setBlock(alongZ ? new BlockPos(5, y, axis) : new BlockPos(axis, y, 5), Blocks.STONE);
            helper.setBlock(alongZ ? new BlockPos(7, y, axis) : new BlockPos(axis, y, 7), Blocks.STONE);
        }
        for (int across = 5; across <= 7; across++) for (int y = 1; y <= 3; y++) {
            helper.setBlock(alongZ ? new BlockPos(across, y, 0) : new BlockPos(0, y, across), Blocks.STONE);
            helper.setBlock(alongZ ? new BlockPos(across, y, 13) : new BlockPos(13, y, across), Blocks.STONE);
        }
        var lower = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, open)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(new BlockPos(6, 1, 6), lower);
        helper.setBlock(new BlockPos(6, 2, 6), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        var girl = new BodyProbe(helper.getLevel(), width);
        int startAxis = reverse ? 2 : 10, targetAxis = reverse ? 10 : 2;
        var origin = helper.absolutePos(alongZ ? new BlockPos(6, 1, startAxis) : new BlockPos(startAxis, 1, 6));
        var target = helper.absolutePos(alongZ ? new BlockPos(6, 1, targetAxis) : new BlockPos(targetAxis, 1, 6));
        boolean shouldPass = open && width <= .8125F;
        girl.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
        helper.getLevel().addFreshEntity(girl);
        girl.triggerEscape();
        girl.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
        girl.goalSelector.removeAllGoals(goal -> true);
        girl.targetSelector.removeAllGoals(goal -> true);
        helper.onEachTick(() -> {
            if (girl.isRemoved()) return;
            helper.assertTrue(helper.getLevel().noCollision(girl), "body intersected wall or door during passage");
            if (helper.getTick() % 20 == 0)
                girl.getNavigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, 1.2);
            BlockPos door = helper.absolutePos(new BlockPos(6, 1, 6));
            boolean crossed = reverse
                    ? (alongZ ? girl.getBoundingBox().minZ : girl.getBoundingBox().minX)
                        > (alongZ ? door.getZ() : door.getX()) + 1
                    : (alongZ ? girl.getBoundingBox().maxZ : girl.getBoundingBox().maxX)
                        < (alongZ ? door.getZ() : door.getX());
            if (crossed) {
                helper.assertTrue(shouldPass, "closed door or over-wide body passed illegally");
                helper.assertTrue(helper.getLevel().noCollision(girl), "traversal bypasses physical collision");
                helper.assertTrue(Math.abs(girl.getBbWidth() - width) < .0001, "body was shrunk");
                girl.discard();
                helper.succeed();
            } else if (helper.getTick() > 460) {
                String position = girl.position().toString();
                girl.discard();
                if (shouldPass) helper.fail("centered path cannot traverse door with original body: " + position
                        + " door=" + helper.getBlockState(new BlockPos(6, 1, 6)));
                else helper.succeed();
            }
        });
    }

    private static final class BodyProbe extends EntityFourthMatchFlame {
        private float width;
        BodyProbe(net.minecraft.world.level.Level level, float width) {
            super(ModEntities.fourth_match_flame.get(), level);
            this.width = width;
            refreshDimensions();
        }
        @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
            var original = super.getDimensions(pose);
            return width <= 0 ? original : original.scale(width / original.width, 1);
        }
    }
}
