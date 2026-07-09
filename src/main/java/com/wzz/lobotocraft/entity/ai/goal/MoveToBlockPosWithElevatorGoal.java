package com.wzz.lobotocraft.entity.ai.goal;

import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class MoveToBlockPosWithElevatorGoal extends Goal {
    private final AbstractAbnormality mob;
    private final double speed;
    private final Supplier<BlockPos> targetSupplier;
    private final BooleanSupplier activeSupplier;
    private final Runnable arrivalAction;
    private BlockPos targetPos;
    private BlockPos targetElevatorPos;
    private int stuckTimer = 0;
    private int recalcTimer = 0;
    private int elevatorWaitTimer = 0;
    private int stateCheckTimer = 0;

    private enum State {
        FINDING_PATH,
        MOVING_TO_ELEVATOR,
        WAITING_ON_ELEVATOR,
        AFTER_ELEVATOR,
        DIRECT_MOVE
    }

    private State state = State.FINDING_PATH;

    public MoveToBlockPosWithElevatorGoal(AbstractAbnormality mob, double speed,
                                          Supplier<BlockPos> targetSupplier,
                                          BooleanSupplier activeSupplier,
                                          Runnable arrivalAction) {
        this.mob = mob;
        this.speed = speed;
        this.targetSupplier = targetSupplier;
        this.activeSupplier = activeSupplier;
        this.arrivalAction = arrivalAction;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!activeSupplier.getAsBoolean() || mob.isDeadOrDying() || mob.level().isClientSide) {
            return false;
        }
        targetPos = targetSupplier.get();
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        targetPos = targetSupplier.get();
        return activeSupplier.getAsBoolean()
                && targetPos != null
                && !mob.isDeadOrDying()
                && mob.distanceToSqr(Vec3.atCenterOf(targetPos)) > 2.25D;
    }

    @Override
    public void start() {
        stuckTimer = 0;
        recalcTimer = 0;
        targetElevatorPos = null;
        elevatorWaitTimer = 0;
        stateCheckTimer = 0;
        evaluateState();
    }

    @Override
    public void tick() {
        targetPos = targetSupplier.get();
        if (targetPos == null) {
            return;
        }

        mob.getLookControl().setLookAt(
                targetPos.getX() + 0.5D,
                targetPos.getY() + 0.5D,
                targetPos.getZ() + 0.5D
        );

        if (mob.distanceToSqr(Vec3.atCenterOf(targetPos)) <= 2.25D) {
            mob.getNavigation().stop();
            arrivalAction.run();
            return;
        }

        stateCheckTimer++;
        if (stateCheckTimer >= 40) {
            stateCheckTimer = 0;
            evaluateState();
        }

        switch (state) {
            case MOVING_TO_ELEVATOR -> tickMovingToElevator();
            case WAITING_ON_ELEVATOR -> tickWaitingOnElevator();
            case AFTER_ELEVATOR -> tickAfterElevator();
            case DIRECT_MOVE -> tickDirectMove();
            default -> evaluateState();
        }
    }

    private void evaluateState() {
        if (targetPos == null) {
            return;
        }

        double yDiff = targetPos.getY() - mob.getY();
        if (Math.abs(yDiff) <= 5.0D) {
            state = State.DIRECT_MOVE;
            navigateDirectlyToTarget();
            return;
        }

        BlockPos nearestElevator = findBestElevator(yDiff);
        if (nearestElevator == null) {
            state = State.DIRECT_MOVE;
            navigateDirectlyToTarget();
            return;
        }

        targetElevatorPos = nearestElevator;
        state = State.MOVING_TO_ELEVATOR;
        elevatorWaitTimer = 0;

        Path path = mob.getNavigation().createPath(
                nearestElevator.getX() + 0.5D,
                nearestElevator.getY() + 1.0D,
                nearestElevator.getZ() + 0.5D,
                1
        );

        if (path != null && path.canReach()) {
            mob.getNavigation().moveTo(path, speed);
        } else {
            mob.getNavigation().moveTo(
                    nearestElevator.getX() + 0.5D,
                    nearestElevator.getY() + 1.0D,
                    nearestElevator.getZ() + 0.5D,
                    speed
            );
        }
    }

    private BlockPos findBestElevator(double yDiffToTarget) {
        if (!(mob.level() instanceof ServerLevel)) {
            return null;
        }

        BlockPos mobPos = mob.blockPosition();
        boolean needGoUp = yDiffToTarget > 0.0D;
        int targetY = targetPos.getY();
        int currentY = mobPos.getY();

        List<BlockEntity> nearbyBlockEntities = EntityUtil.findBlockEntities(mob.level(), mobPos, 30);
        BlockPos bestElevator = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockEntity blockEntity : nearbyBlockEntities) {
            if (!(blockEntity instanceof ElevatorBlockEntity elevator)) {
                continue;
            }

            BlockPos elevatorPos = blockEntity.getBlockPos();
            if (needGoUp != elevator.isTeleportUp()) {
                continue;
            }

            BlockPos destinationElevator = elevator.findDestinationElevator(mob.level());
            if (destinationElevator == null) {
                continue;
            }

            int destY = destinationElevator.getY() + 1;
            int diffAfterRide = Math.abs(targetY - destY);
            int currentDiff = Math.abs(targetY - currentY);
            if (diffAfterRide >= currentDiff) {
                continue;
            }

            double score = Math.sqrt(mobPos.distSqr(elevatorPos)) + diffAfterRide * 2.0D;
            if (score < bestScore) {
                bestScore = score;
                bestElevator = elevatorPos;
            }
        }

        return bestElevator;
    }

    private void tickMovingToElevator() {
        if (targetElevatorPos == null) {
            evaluateState();
            return;
        }

        elevatorWaitTimer++;
        double distToElevator = mob.distanceToSqr(
                targetElevatorPos.getX() + 0.5D,
                targetElevatorPos.getY() + 1.0D,
                targetElevatorPos.getZ() + 0.5D
        );

        if (distToElevator <= 2.5D) {
            state = State.WAITING_ON_ELEVATOR;
            elevatorWaitTimer = 0;
            mob.getNavigation().stop();
            return;
        }

        if (elevatorWaitTimer > 160) {
            evaluateState();
            return;
        }

        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            stuckTimer++;
            if (stuckTimer > 40) {
                stuckTimer = 0;
                Vec3 dir = new Vec3(
                        targetElevatorPos.getX() + 0.5D - mob.getX(),
                        targetElevatorPos.getY() + 1.0D - mob.getY(),
                        targetElevatorPos.getZ() + 0.5D - mob.getZ()
                ).normalize();
                mob.setDeltaMovement(dir.scale(speed * 0.4D));
            }
        } else {
            stuckTimer = 0;
        }
    }

    private void tickWaitingOnElevator() {
        elevatorWaitTimer++;
        double currentYDiff = Math.abs(targetPos.getY() - mob.getY());

        if (currentYDiff <= 5.0D) {
            state = State.AFTER_ELEVATOR;
            elevatorWaitTimer = 0;
            return;
        }

        if (elevatorWaitTimer > 100) {
            if (mob.level().getBlockEntity(targetElevatorPos) instanceof ElevatorBlockEntity elevator) {
                elevator.onStep(mob);
            }
            elevatorWaitTimer = 60;
        }
    }

    private void tickAfterElevator() {
        if (Math.abs(targetPos.getY() - mob.getY()) > 5.0D) {
            state = State.FINDING_PATH;
            evaluateState();
            return;
        }

        state = State.DIRECT_MOVE;
        navigateDirectlyToTarget();
    }

    private void tickDirectMove() {
        recalcTimer++;
        if (recalcTimer >= 60) {
            recalcTimer = 0;
            if (Math.abs(targetPos.getY() - mob.getY()) > 5.0D) {
                evaluateState();
                return;
            }
        }

        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            stuckTimer++;
            if (stuckTimer > 60) {
                stuckTimer = 0;
                Vec3 dir = Vec3.atCenterOf(targetPos).subtract(mob.position()).normalize();
                mob.setDeltaMovement(dir.scale(speed * 0.3D));
                mob.getNavigation().moveTo(
                        targetPos.getX() + 0.5D,
                        targetPos.getY(),
                        targetPos.getZ() + 0.5D,
                        speed
                );
            }
        } else {
            stuckTimer = 0;
        }
    }

    private void navigateDirectlyToTarget() {
        Path path = mob.getNavigation().createPath(targetPos, 1);
        if (path != null && path.canReach()) {
            mob.getNavigation().moveTo(path, speed);
        } else {
            mob.getNavigation().moveTo(
                    targetPos.getX() + 0.5D,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5D,
                    speed
            );
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        targetElevatorPos = null;
        state = State.FINDING_PATH;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
