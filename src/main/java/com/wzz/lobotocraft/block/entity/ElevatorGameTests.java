package com.wzz.lobotocraft.block.entity;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.ElevatorRegistry;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.abnormality.EntityPunishingBird;
import com.wzz.lobotocraft.entity.ai.goal.MoveToBlockPosWithElevatorGoal;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.UUID;

/** Server-side elevator behavior involving the vanilla passenger system. */
@GameTestHolder(ModMain.MODID)
public final class ElevatorGameTests {
    private static final BlockPos SOURCE = new BlockPos(4, 1, 4);
    private static final BlockPos DESTINATION = new BlockPos(4, 5, 4);

    private ElevatorGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "elevator_passengers")
    public static void mountedPlayerDismountsBeforeSuccessfulTravel(GameTestHelper helper) {
        TestActors actors = prepare(helper, false);
        ElevatorBlockEntity.TravelResult result = actors.source.tryTravel(actors.player, true);
        Vec3 expected = Vec3.atBottomCenterOf(helper.absolutePos(DESTINATION).above());
        if (result != ElevatorBlockEntity.TravelResult.SUCCESS) {
            actors.cleanup();
            helper.fail("mounted elevator travel returned " + result);
            return;
        }
        if (actors.player.isPassenger()
                || actors.player.position().distanceToSqr(expected) > 1.0E-6D) {
            actors.cleanup();
            helper.fail("mounted player was not dismounted at the destination");
            return;
        }
        actors.cleanup();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "elevator_passengers")
    public static void blockedTravelDoesNotDismountPlayer(GameTestHelper helper) {
        TestActors actors = prepare(helper, true);
        ElevatorBlockEntity.TravelResult result = actors.source.tryTravel(actors.player, true);
        if (result != ElevatorBlockEntity.TravelResult.BLOCKED_DESTINATION
                || !actors.player.isPassenger()) {
            actors.cleanup();
            helper.fail("blocked elevator travel changed the riding relationship: " + result);
            return;
        }
        actors.cleanup();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "elevator_passengers")
    public static void occupiedCenterUsesAdjacentLanding(GameTestHelper helper) {
        helper.setBlock(DESTINATION.offset(1, 0, 0), Blocks.STONE);
        TestActors actors = prepare(helper, false);
        Pig blocker = EntityType.PIG.create(helper.getLevel());
        if (blocker == null) {
            actors.cleanup();
            helper.fail("destination blocker was not created");
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(helper.absolutePos(DESTINATION).above());
        blocker.absMoveTo(center.x, center.y, center.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(blocker);

        ElevatorBlockEntity.TravelResult result = actors.source.tryTravel(actors.player, true);
        if (result != ElevatorBlockEntity.TravelResult.SUCCESS
                || actors.player.position().distanceToSqr(center) < 0.25D) {
            blocker.discard();
            actors.cleanup();
            helper.fail("occupied destination did not use a free adjacent landing: " + result);
            return;
        }
        blocker.discard();
        actors.cleanup();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "elevator_abnormalities")
    public static void abnormalityRouteUsesElevatorForCrossFloorTarget(GameTestHelper helper) {
        helper.setBlock(SOURCE, ModBlocks.ELEVATOR.get());
        helper.setBlock(DESTINATION, ModBlocks.ELEVATOR.get());
        BlockPos absoluteSource = helper.absolutePos(SOURCE);
        BlockPos absoluteDestination = helper.absolutePos(DESTINATION);
        ElevatorRegistry.register(helper.getLevel(), absoluteSource);
        ElevatorRegistry.register(helper.getLevel(), absoluteDestination);

        EntityPunishingBird bird = ModEntities.punishing_bird.get().create(helper.getLevel());
        ElevatorBlockEntity source = (ElevatorBlockEntity) helper.getLevel()
                .getBlockEntity(absoluteSource);
        if (bird == null || source == null) {
            helper.fail("test abnormality or elevator was not created");
            return;
        }
        bird.absMoveTo(absoluteSource.getX() + 0.5D, absoluteSource.getY() + 1.0D,
                absoluteSource.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(bird);
        // The route policy intentionally treats <=5 blocks as the same floor.
        BlockPos target = absoluteDestination.offset(4, 4, 0);
        MoveToBlockPosWithElevatorGoal goal = new MoveToBlockPosWithElevatorGoal(
                bird, 1.2D, () -> target, () -> true, () -> { });
        if (!goal.canUse()) {
            bird.discard();
            helper.fail("cross-floor abnormality route refused to start");
            return;
        }
        goal.start();
        goal.tick();

        Vec3 expected = Vec3.atBottomCenterOf(absoluteDestination.above());
        if (bird.position().distanceToSqr(expected) > 1.0E-6D) {
            bird.discard();
            helper.fail("abnormality route did not use the elevator");
            return;
        }
        bird.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "elevator_abnormalities")
    public static void abnormalityRejectsCloserElevatorOnAnotherFloor(GameTestHelper helper) {
        BlockPos birdStart = new BlockPos(1, 2, 4);
        BlockPos correctSource = new BlockPos(7, 1, 4);
        BlockPos correctDestination = new BlockPos(7, 9, 4);
        BlockPos wrongFloorSource = new BlockPos(1, 6, 4);
        BlockPos wrongFloorDestination = new BlockPos(1, 9, 4);
        for (BlockPos elevator : new BlockPos[]{correctSource, correctDestination,
                wrongFloorSource, wrongFloorDestination}) {
            helper.setBlock(elevator, ModBlocks.ELEVATOR.get());
            ElevatorRegistry.register(helper.getLevel(), helper.absolutePos(elevator));
        }

        EntityPunishingBird bird = ModEntities.punishing_bird.get().create(helper.getLevel());
        if (bird == null) {
            helper.fail("test abnormality was not created");
            return;
        }
        BlockPos absoluteStart = helper.absolutePos(birdStart);
        BlockPos absoluteCorrectSource = helper.absolutePos(correctSource);
        BlockPos absoluteCorrectDestination = helper.absolutePos(correctDestination);
        bird.absMoveTo(absoluteStart.getX() + 0.5D, absoluteStart.getY(),
                absoluteStart.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(bird);

        BlockPos target = absoluteCorrectDestination.above();
        MoveToBlockPosWithElevatorGoal goal = new MoveToBlockPosWithElevatorGoal(
                bird, 1.2D, () -> target, () -> true, () -> { });
        if (!goal.canUse()) {
            bird.discard();
            helper.fail("cross-floor abnormality route refused to start");
            return;
        }
        goal.start();
        Path path = bird.getNavigation().getPath();
        if (path == null || !path.getTarget().equals(absoluteCorrectSource.above())) {
            bird.discard();
            helper.fail("abnormality selected an elevator outside its current floor");
            return;
        }

        bird.teleportTo(absoluteCorrectSource.getX() + 0.5D,
                absoluteCorrectSource.getY() + 1.0D,
                absoluteCorrectSource.getZ() + 0.5D);
        goal.tick();
        Vec3 expected = Vec3.atBottomCenterOf(absoluteCorrectDestination.above());
        if (bird.position().distanceToSqr(expected) > 1.0E-6D) {
            bird.discard();
            helper.fail("abnormality did not travel from the selected same-floor elevator");
            return;
        }
        bird.discard();
        helper.succeed();
    }

    private static TestActors prepare(GameTestHelper helper, boolean blockDestination) {
        helper.setBlock(SOURCE, ModBlocks.ELEVATOR.get());
        helper.setBlock(DESTINATION, ModBlocks.ELEVATOR.get());
        if (blockDestination) {
            for (BlockPos landing : ElevatorBlockEntity.landingCandidates(DESTINATION)) {
                helper.setBlock(landing, Blocks.STONE);
            }
        }

        BlockPos absoluteSource = helper.absolutePos(SOURCE);
        BlockPos absoluteDestination = helper.absolutePos(DESTINATION);
        ElevatorRegistry.register(helper.getLevel(), absoluteSource);
        ElevatorRegistry.register(helper.getLevel(), absoluteDestination);
        ElevatorBlockEntity source = (ElevatorBlockEntity) helper.getLevel()
                .getBlockEntity(absoluteSource);

        UUID id = UUID.randomUUID();
        EmployeeRecord record = new EmployeeRecord(id, "elevator_rider",
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer player = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(id, record.name()), record);
        player.setInvulnerable(true);
        player.absMoveTo(absoluteSource.getX() + 0.5D, absoluteSource.getY() + 1.0D,
                absoluteSource.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addNewPlayer(player);

        Pig vehicle = EntityType.PIG.create(helper.getLevel());
        if (vehicle == null || source == null) {
            throw new IllegalStateException("test elevator or vehicle was not created");
        }
        vehicle.absMoveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(vehicle);
        if (!player.startRiding(vehicle, true)) {
            throw new IllegalStateException("test player could not mount vehicle");
        }
        return new TestActors(player, vehicle, source);
    }

    private record TestActors(EmployeeServerPlayer player, Pig vehicle,
                              ElevatorBlockEntity source) {
        private void cleanup() {
            player.stopRiding();
            player.kill();
            vehicle.discard();
        }
    }
}
