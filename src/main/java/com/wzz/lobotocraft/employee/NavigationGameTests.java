package com.wzz.lobotocraft.employee;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.employee.navigation.NavigationSnapshot;
import com.wzz.lobotocraft.init.ModDimensions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/** Server-side navigation scenarios using real player physics and block collision. */
@GameTestHolder(ModMain.MODID)
public final class NavigationGameTests {
    private NavigationGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 20, required = true, batch = "employee_food")
    public static void aiEmployeeAlwaysKeepsFullFood(GameTestHelper helper) {
        EmployeeServerPlayer employee = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "employee_food_test");
        employee.getFoodData().setFoodLevel(1);
        employee.getFoodData().setSaturation(0.0F);
        employee.getFoodData().setExhaustion(4.0F);
        employee.causeFoodExhaustion(10.0F);

        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(employee.getFoodData().getFoodLevel() == 20,
                        "AI employee food level was not restored");
                helper.assertTrue(employee.getFoodData().getSaturationLevel() == 20.0F,
                        "AI employee saturation was not restored");
                helper.assertTrue(employee.getFoodData().getExhaustionLevel() == 0.0F,
                        "AI employee exhaustion was not cleared");
                helper.succeed();
            } finally {
                employee.kill();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 500,
            required = true, batch = "navigation")
    public static void longSameFloorRouteDoesNotTakeStairDetour(GameTestHelper helper) {
        buildScenario(helper, false);
        EmployeeServerPlayer employee = spawnEmployee(helper, new BlockPos(2, 1, 2));
        EmployeeNavigator navigator = new EmployeeNavigator(employee);
        navigator.moveTo(helper.absolutePos(new BlockPos(42, 1, 8)), 1.6D);
        tickUntilComplete(helper, employee, navigator, "long same-floor route", false);
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 500,
            required = true, batch = "navigation")
    public static void closedIronDoorRequiresPressurePlate(GameTestHelper helper) {
        buildScenario(helper, true);
        EmployeeServerPlayer employee = spawnEmployee(helper, new BlockPos(2, 1, 2));
        EmployeeNavigator navigator = new EmployeeNavigator(employee);
        // Stop on the pressure plate itself; the harness then verifies that the
        // adjacent iron door is powered/open before any crossing is attempted.
        navigator.moveTo(helper.absolutePos(new BlockPos(29, 1, 8)), 1.6D);
        tickUntilComplete(helper, employee, navigator, "pressure plate iron door", false);
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "navigation_doors")
    public static void plannerRejectsIronDoorUntilPressurePlateExists(GameTestHelper helper) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 4; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        }
        placeIronDoor(helper, 3, 2);
        BlockPos minimum = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos maximum = helper.absolutePos(new BlockPos(6, 4, 4));
        BlockPos lowerDoor = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos upperDoor = lowerDoor.above();
        NavigationSnapshot withoutPlate = NavigationSnapshot.capture(
                helper.getLevel(), minimum, maximum, 1_000);
        helper.assertTrue(withoutPlate.cell(lowerDoor)
                        .map(cell -> !cell.traversableClosedDoor()).orElse(false)
                        && withoutPlate.cell(upperDoor)
                        .map(cell -> !cell.traversableClosedDoor()).orElse(false),
                "closed iron door without a pressure plate was classified as openable");

        helper.setBlock(2, 1, 2, Blocks.STONE_PRESSURE_PLATE);
        NavigationSnapshot withPlate = NavigationSnapshot.capture(
                helper.getLevel(), minimum, maximum, 1_000);
        helper.assertTrue(withPlate.cell(lowerDoor)
                        .map(cell -> cell.traversableClosedDoor()).orElse(false)
                        && withPlate.cell(upperDoor)
                        .map(cell -> cell.traversableClosedDoor()).orElse(false),
                "closed iron door beside a pressure plate was not classified as openable");
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 500, required = true, batch = "navigation_combat")
    public static void meleeCombatRoutesAroundWallToVisibleAttackPosition(GameTestHelper helper) {
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 8; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        }
        for (int z = 1; z <= 7; z++) {
            if (z != 2) helper.setBlock(5, 1, z, Blocks.STONE);
        }
        EmployeeServerPlayer employee = spawnEmployee(helper, new BlockPos(2, 1, 4),
                "combat_wall_test");
        Pig target = EntityType.PIG.create(helper.getLevel());
        if (target == null) {
            employee.kill();
            helper.fail("combat target was not created");
            return;
        }
        BlockPos targetPosition = helper.absolutePos(new BlockPos(8, 1, 4));
        target.absMoveTo(targetPosition.getX() + 0.5D, targetPosition.getY(),
                targetPosition.getZ() + 0.5D, 0.0F, 0.0F);
        target.setInvulnerable(true);
        helper.getLevel().addFreshEntity(target);
        EmployeeNavigator navigator = new EmployeeNavigator(employee);

        helper.onEachTick(() -> {
            navigator.retargetCombat(target, 2.65D);
            navigator.tick();
            Vec3 movementTarget = navigator.testMovementTarget();
            if (movementTarget != null) {
                employee.absMoveTo(movementTarget.x, movementTarget.y, movementTarget.z,
                        employee.getYRot(), employee.getXRot());
            }
            if (navigator.isUnreachable()) {
                employee.kill();
                target.discard();
                helper.fail("combat route around the wall became unreachable");
                return;
            }
            if (!navigator.isComplete()) return;
            double distance = employee.distanceTo(target);
            if (distance > 2.65D || !employee.hasLineOfSight(target)) {
                employee.kill();
                target.discard();
                helper.fail("combat route stopped without a visible in-range target");
                return;
            }
            employee.kill();
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 1200,
            required = true, batch = "navigation_stress")
    public static void fiftyConcurrentLongRoutesUseTheSharedPlanningBudget(GameTestHelper helper) {
        buildScenario(helper, false);
        List<EmployeeServerPlayer> employees = new ArrayList<>();
        List<EmployeeNavigator> navigators = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            EmployeeServerPlayer employee = spawnEmployee(helper,
                    new BlockPos(2, 1, 2), "navigation_stress_" + index);
            EmployeeNavigator navigator = new EmployeeNavigator(employee);
            navigator.moveTo(helper.absolutePos(new BlockPos(42, 1, 8)), 1.6D);
            employees.add(employee);
            navigators.add(navigator);
        }

        helper.onEachTick(() -> {
            long tickStarted = System.nanoTime();
            int complete = 0;
            for (int index = 0; index < navigators.size(); index++) {
                EmployeeNavigator navigator = navigators.get(index);
                EmployeeServerPlayer employee = employees.get(index);
                navigator.tick();
                Vec3 movementTarget = navigator.testMovementTarget();
                if (movementTarget != null) {
                    employee.absMoveTo(movementTarget.x, movementTarget.y, movementTarget.z,
                            employee.getYRot(), employee.getXRot());
                }
                if (navigator.isUnreachable()) {
                    employees.forEach(EmployeeServerPlayer::kill);
                    helper.fail("concurrent route became unreachable for employee " + index);
                    return;
                }
                if (navigator.isComplete()) complete++;
            }
            long elapsedMillis = (System.nanoTime() - tickStarted) / 1_000_000L;
            if (elapsedMillis > 500L) {
                employees.forEach(EmployeeServerPlayer::kill);
                helper.fail("50 concurrent navigator ticks blocked the server for "
                        + elapsedMillis + " ms");
                return;
            }
            if (complete == navigators.size()) {
                employees.forEach(EmployeeServerPlayer::kill);
                helper.succeed();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 1_200, required = true, batch = "navigation_retarget")
    public static void rapidRetargetingCancelsStalePathsAndCompletesLatestOrder(
            GameTestHelper helper) {
        for (int x = 0; x <= 46; x++) {
            for (int z = 0; z <= 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        }
        // Two tall offset walls force opposite targets to use meaningfully different routes.
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 9; z++) {
                if (z != 2) helper.setBlock(18, y, z, Blocks.STONE);
            }
            for (int z = 3; z <= 11; z++) {
                if (z != 10) helper.setBlock(30, y, z, Blocks.STONE);
            }
        }

        EmployeeServerPlayer employee = spawnEmployee(helper, new BlockPos(4, 1, 6),
                "navigation_retarget_test");
        EmployeeNavigator navigator = new EmployeeNavigator(employee);
        BlockPos targetA = helper.absolutePos(new BlockPos(43, 1, 2));
        BlockPos targetB = helper.absolutePos(new BlockPos(43, 1, 10));
        navigator.moveTo(targetA, 1.6D);
        int[] ticks = {0};
        boolean[] finalOrderIssued = {false};

        helper.onEachTick(() -> {
            ticks[0]++;
            employee.doTick();
            if (ticks[0] <= 40 && ticks[0] % 2 == 0) {
                navigator.moveTo((ticks[0] / 2) % 2 == 0 ? targetA : targetB, 1.6D);
            } else if (!finalOrderIssued[0] && ticks[0] > 40) {
                finalOrderIssued[0] = true;
                navigator.moveTo(targetB, 1.6D);
            }
            navigator.tick();

            if (navigator.isUnreachable()) {
                employee.kill();
                helper.fail("latest target became unreachable after rapid retargeting at "
                        + employee.blockPosition());
                return;
            }
            if (!finalOrderIssued[0] || !navigator.isComplete()) return;
            if (employee.position().distanceTo(Vec3.atBottomCenterOf(targetB)) > 2.0D) {
                employee.kill();
                helper.fail("stale path completed at " + employee.blockPosition()
                        + " instead of the final target " + targetB);
                return;
            }
            employee.kill();
            helper.succeed();
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 800, required = true, batch = "navigation_carpet")
    public static void carpetedCorridorDoesNotBlockRealEmployeeMovement(GameTestHelper helper) {
        for (int x = 0; x <= 22; x++) {
            for (int z = 0; z <= 4; z++) helper.setBlock(x, 0, z, Blocks.STONE);
            helper.setBlock(x, 1, 1, Blocks.STONE);
            helper.setBlock(x, 2, 1, Blocks.STONE);
            helper.setBlock(x, 1, 3, Blocks.STONE);
            helper.setBlock(x, 2, 3, Blocks.STONE);
        }
        for (int x = 1; x < 22; x++) helper.setBlock(x, 1, 2, Blocks.RED_CARPET);

        EmployeeServerPlayer employee = spawnEmployee(helper, new BlockPos(2, 2, 2),
                "navigation_carpet_test");
        EmployeeNavigator navigator = new EmployeeNavigator(employee);
        BlockPos target = helper.absolutePos(new BlockPos(20, 1, 2));
        navigator.moveTo(target, 1.6D);

        helper.onEachTick(() -> {
            employee.doTick();
            navigator.tick();
            if (navigator.isUnreachable()) {
                employee.kill();
                helper.fail("carpeted corridor became unreachable at "
                        + employee.blockPosition());
                return;
            }
            if (!navigator.isComplete()) return;
            employee.kill();
            helper.succeed();
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 600, required = true, batch = "navigation_cache")
    public static void fixedFacilityCacheLoadsSparseStructureData(GameTestHelper helper) {
        var facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        FacilityNavigationCache.prepare(facility);
        helper.onEachTick(() -> {
            int cells = FacilityNavigationCache.staticCellCount(facility);
            if (cells < 0) return;
            if (cells < 1_000) {
                helper.fail("facility cache contained only " + cells + " non-air cells");
                return;
            }
            NavigationSnapshot snapshot = FacilityNavigationCache.snapshot(facility,
                    FacilityNavigationCache.COMPANY_MIN, FacilityNavigationCache.COMPANY_MAX);
            if (snapshot == null
                    || snapshot.cell(FacilityNavigationCache.COMPANY_MIN.below()).isPresent()) {
                helper.fail("facility cache did not enforce its fixed company bounds");
                return;
            }
            BlockPos spawnFloor = new BlockPos(195, 269, 29);
            if (snapshot.cell(spawnFloor).filter(cell -> cell.hasCollision()).isEmpty()) {
                helper.fail("facility cache was not aligned with the fixed company coordinates");
                return;
            }
            helper.succeed();
        });
    }

    private static void buildScenario(GameTestHelper helper, boolean ironDoor) {
        for (int x = 0; x <= 46; x++) {
            for (int z = 0; z <= 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        }
        // The intended route is: forward, right, forward, right. The side stairs
        // are deliberately shorter in Euclidean distance but not part of the route.
        for (int x = 0; x <= 46; x++) {
            helper.setBlock(x, 1, 0, Blocks.STONE);
            helper.setBlock(x, 1, 12, Blocks.STONE);
        }
        for (int z = 0; z <= 12; z++) {
            helper.setBlock(0, 1, z, Blocks.STONE);
            helper.setBlock(46, 1, z, Blocks.STONE);
        }
        // Put the first turn at the initial horizontal search boundary. This
        // keeps the route long while allowing the bounded planner to finish
        // its first segment before turning into the connector.
        for (int x = 1; x < 20; x++) {
            helper.setBlock(x, 1, 1, Blocks.STONE);
            helper.setBlock(x, 1, 3, Blocks.STONE);
        }
        // Leave the far-side wall open at z=8 so the vertical connector can
        // turn into the second corridor. Keeping this cell solid accidentally
        // sealed the only route and made both tests report an unreachable
        // target before exercising navigation behavior.
        // Keep z=2 open as the entrance from the first corridor; the side walls
        // begin at the next row so the employee can turn into the connector.
        for (int z = 4; z <= 7; z++) {
            helper.setBlock(20, 1, z, Blocks.STONE);
            helper.setBlock(22, 1, z, Blocks.STONE);
        }
        for (int x = 21; x <= 45; x++) {
            if (x != 21) helper.setBlock(x, 1, 7, Blocks.STONE);
            helper.setBlock(x, 1, 9, Blocks.STONE);
        }
        if (ironDoor) {
            placeIronDoor(helper, 30, 8);
            helper.setBlock(29, 1, 8, Blocks.STONE_PRESSURE_PLATE);
            helper.setBlock(31, 1, 8, Blocks.STONE_PRESSURE_PLATE);
        }

        // A tempting vertical shortcut. Same-floor navigation must not use it as
        // a segment boundary merely because it is closer in the bounded search.
        // Keep the vertical obstacle beside (rather than inside) the first
        // corridor. Placing it at z=2 blocked the only legal route and tested
        // an impossible map instead of stair-detour selection.
        for (int y = 1; y <= 5; y++) helper.setBlock(6, y, 5, Blocks.STONE);
    }

    private static void placeIronDoor(GameTestHelper helper, int x, int z) {
        BlockPos lower = new BlockPos(x, 1, z);
        BlockPos upper = lower.above();
        BlockState lowerState = Blocks.IRON_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperState = lowerState.setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                DoubleBlockHalf.UPPER);
        helper.setBlock(x, 1, z, lowerState);
        helper.setBlock(x, 2, z, upperState);
    }

    private static EmployeeServerPlayer spawnEmployee(GameTestHelper helper, BlockPos relative) {
        return spawnEmployee(helper, relative, "navigation_test");
    }

    private static EmployeeServerPlayer spawnEmployee(GameTestHelper helper, BlockPos relative,
                                                       String name) {
        var level = helper.getLevel();
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name,
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(level,
                new GameProfile(record.id(), record.name()), record);
        employee.setInvulnerable(true);
        BlockPos absolute = helper.absolutePos(relative);
        employee.absMoveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D,
                0.0F, 0.0F);
        level.addNewPlayer(employee);
        return employee;
    }

    private static void tickUntilComplete(GameTestHelper helper, EmployeeServerPlayer employee,
                                          EmployeeNavigator navigator, String name,
                                          boolean requireOpenIronDoor) {
        helper.onEachTick(() -> {
            navigator.tick();
            // Forge's synthetic ServerPlayer has no client input stream, so the
            // harness advances only to the navigator's already validated point.
            // Collision, door and pressure-plate checks still run in navigator.tick().
            Vec3 movementTarget = navigator.testMovementTarget();
            if (movementTarget != null) {
                employee.absMoveTo(movementTarget.x, movementTarget.y, movementTarget.z,
                        employee.getYRot(), employee.getXRot());
                BlockPos stepped = BlockPos.containing(movementTarget);
                BlockState steppedState = helper.getLevel().getBlockState(stepped);
                if (steppedState.getBlock() instanceof PressurePlateBlock) {
                    steppedState.entityInside(helper.getLevel(), stepped, employee);
                    for (BlockPos adjacent : new BlockPos[]{
                            stepped.north(), stepped.south(), stepped.west(), stepped.east()}) {
                        BlockState doorState = helper.getLevel().getBlockState(adjacent);
                        if (doorState.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door
                                && doorState.is(Blocks.IRON_DOOR)
                                && doorState.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)
                                && !doorState.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                            door.setOpen(employee, helper.getLevel(), doorState, adjacent, true);
                        }
                    }
                }
                // Keep the synthetic harness's redstone state in sync while the
                // employee is standing on either side of the test door.
                for (BlockPos nearby : new BlockPos[]{stepped, stepped.north(), stepped.south(),
                        stepped.west(), stepped.east()}) {
                    BlockState doorState = helper.getLevel().getBlockState(nearby);
                    if (doorState.getBlock() == Blocks.IRON_DOOR
                            && doorState.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)
                            && !doorState.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                        ((net.minecraft.world.level.block.DoorBlock) doorState.getBlock())
                                .setOpen(employee, helper.getLevel(), doorState, nearby, true);
                    }
                }
            }
            if (navigator.isComplete()) {
                if (requireOpenIronDoor) {
                    BlockPos door = helper.absolutePos(new BlockPos(30, 1, 8));
                    if (!helper.getLevel().getBlockState(door).getValue(
                            net.minecraft.world.level.block.DoorBlock.OPEN)) {
                        employee.kill();
                        helper.fail("employee arrived but iron door remained closed");
                        return;
                    }
                }
                employee.kill();
                helper.succeed();
            } else if (navigator.isUnreachable()) {
                employee.kill();
                helper.fail(name + " became unreachable at " + employee.blockPosition());
            }
        });
    }
}
