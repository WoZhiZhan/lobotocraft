package com.wzz.lobotocraft.entity.ai.goal;

import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.entity.abnormality.EntityBlackForestDoor;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class MoveToBlackForestDoorGoal extends Goal {

    private final AbstractAbnormality bird;
    private final double speed;
    private EntityBlackForestDoor door;
    private int stuckTimer = 0;
    private int recalcTimer = 0;
    private BlockPos targetElevatorPos = null;
    private int elevatorWaitTimer = 0;
    private int stateCheckTimer = 0;

    // 电梯使用状态
    private enum State {
        FINDING_PATH,       // 寻找路径
        MOVING_TO_ELEVATOR, // 移向电梯
        WAITING_ON_ELEVATOR,// 在电梯上等待
        AFTER_ELEVATOR,     // 刚用完电梯，重新导航到门
        DIRECT_MOVE         // 同楼层直接移动
    }
    private State state = State.FINDING_PATH;

    public MoveToBlackForestDoorGoal(AbstractAbnormality bird, double speed) {
        this.bird = bird;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!bird.hasEscape() || bird.isDeadOrDying() || bird.level().isClientSide) return false;

        door = bird.level().getEntitiesOfClass(
                        EntityBlackForestDoor.class,
                        bird.getBoundingBox().inflate(512),
                        LivingEntity::isAlive
                ).stream()
                .min(Comparator.comparingDouble(a -> a.distanceToSqr(bird)))
                .orElse(null);

        return door != null;
    }

    @Override
    public boolean canContinueToUse() {
        return bird.hasEscape()
                && door != null
                && door.isAlive()
                && !bird.isDeadOrDying()
                && bird.distanceToSqr(door) > 4.0;
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
        if (door == null || !door.isAlive()) return;

        // 始终看向门
        bird.getLookControl().setLookAt(door.getX(), door.getY() + door.getBbHeight() / 2, door.getZ());

        double distanceSq = bird.distanceToSqr(door);

        // 已经足够近
        if (distanceSq <= 4.0) {
            bird.getNavigation().stop();
            return;
        }

        // 定期检查状态
        stateCheckTimer++;
        if (stateCheckTimer >= 40) {
            stateCheckTimer = 0;
            evaluateState();
        }

        // 根据状态执行不同逻辑
        switch (state) {
            case MOVING_TO_ELEVATOR -> tickMovingToElevator();
            case WAITING_ON_ELEVATOR -> tickWaitingOnElevator();
            case AFTER_ELEVATOR -> tickAfterElevator();
            case DIRECT_MOVE -> tickDirectMove();
            default -> evaluateState();
        }
    }

    /**
     * 评估当前应该使用什么状态
     */
    private void evaluateState() {
        if (door == null) return;

        double yDiff = door.getY() - bird.getY();
        double absYDiff = Math.abs(yDiff);

        // 同楼层（Y轴差距在5格以内）
        if (absYDiff <= 5) {
            state = State.DIRECT_MOVE;
            navigateDirectlyToDoor();
            return;
        }

        // 不同楼层，需要找电梯
        BlockPos nearestElevator = findBestElevator(yDiff);

        if (nearestElevator != null) {
            targetElevatorPos = nearestElevator;
            state = State.MOVING_TO_ELEVATOR;
            elevatorWaitTimer = 0;

            // 导航到电梯位置
            Path path = bird.getNavigation().createPath(
                    nearestElevator.getX() + 0.5,
                    nearestElevator.getY() + 1,
                    nearestElevator.getZ() + 0.5,
                    1
            );

            if (path != null && path.canReach()) {
                bird.getNavigation().moveTo(path, speed);
            } else {
                bird.getNavigation().moveTo(
                        nearestElevator.getX() + 0.5,
                        nearestElevator.getY() + 1,
                        nearestElevator.getZ() + 0.5,
                        speed
                );
            }
        } else {
            // 没找到电梯，尝试直接移动
            state = State.DIRECT_MOVE;
            navigateDirectlyToDoor();
        }
    }

    /**
     * 查找最佳电梯
     */
    private BlockPos findBestElevator(double yDiffToDoor) {
        if (!(bird.level() instanceof ServerLevel)) return null;

        BlockPos birdPos = bird.blockPosition();
        boolean needGoUp = yDiffToDoor > 0;  // 门在上方
        int targetY = door.blockPosition().getY();
        int currentY = birdPos.getY();

        List<BlockEntity> nearbyBlockEntities = EntityUtil.findBlockEntities(
                bird.level(),
                birdPos,
                30  // Y轴搜索范围
        );

        BlockPos bestElevator = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockEntity blockEntity : nearbyBlockEntities) {
            if (blockEntity instanceof ElevatorBlockEntity elevator) {
                BlockPos elevatorPos = blockEntity.getBlockPos();
                int elevatorDistance = elevator.getTeleportDistance();

                // 计算乘坐电梯后能到达的高度
                int destY;
                if (needGoUp && elevator.isTeleportUp()) {
                    destY = currentY + elevatorDistance;
                } else if (!needGoUp && !elevator.isTeleportUp()) {
                    destY = currentY - elevatorDistance;
                } else {
                    // 方向不匹配，跳过
                    continue;
                }

                // 计算乘坐后距离目标楼层的差距
                int diffAfterRide = Math.abs(targetY - destY);
                int currentDiff = Math.abs(targetY - currentY);

                // 必须比现在更近
                if (diffAfterRide >= currentDiff) continue;

                // 计算得分：距离电梯的距离 + 乘坐后与目标的差距
                double distToElevator = Math.sqrt(birdPos.distSqr(elevatorPos));
                double score = distToElevator + diffAfterRide * 2;  // 楼层差距权重更高

                if (score < bestScore) {
                    bestScore = score;
                    bestElevator = elevatorPos;
                }
            }
        }

        return bestElevator;
    }

    /**
     * 移向电梯中
     */
    private void tickMovingToElevator() {
        if (door == null || targetElevatorPos == null) {
            evaluateState();
            return;
        }

        elevatorWaitTimer++;

        // 检查是否到达电梯
        double distToElevator = bird.distanceToSqr(
                targetElevatorPos.getX() + 0.5,
                targetElevatorPos.getY() + 1,
                targetElevatorPos.getZ() + 0.5
        );

        if (distToElevator <= 2.5) {
            // 到达电梯，站上去
            state = State.WAITING_ON_ELEVATOR;
            elevatorWaitTimer = 0;
            bird.getNavigation().stop();
            return;
        }

        // 超时或卡住
        if (elevatorWaitTimer > 160) {  // 8秒
            evaluateState();  // 重新找
            return;
        }

        // 检查是否卡住
        Path path = bird.getNavigation().getPath();
        if (path == null || path.isDone()) {
            stuckTimer++;
            if (stuckTimer > 40) {
                stuckTimer = 0;
                // 直接飞向电梯
                Vec3 dir = new Vec3(
                        targetElevatorPos.getX() + 0.5 - bird.getX(),
                        targetElevatorPos.getY() + 1 - bird.getY(),
                        targetElevatorPos.getZ() + 0.5 - bird.getZ()
                ).normalize();
                bird.setDeltaMovement(dir.scale(speed * 0.4));
            }
        } else {
            stuckTimer = 0;
        }
    }

    /**
     * 在电梯上等待传送
     */
    private void tickWaitingOnElevator() {
        elevatorWaitTimer++;

        // 等待电梯生效（电梯有30tick延迟）
        // 检查是否已经传送到新楼层
        double currentYDiff = Math.abs(door.getY() - bird.getY());

        if (currentYDiff <= 5) {
            // 已经到达目标楼层附近
            state = State.AFTER_ELEVATOR;
            elevatorWaitTimer = 0;
            return;
        }

        // 如果等待超过5秒还没传送，可能是电梯没触发
        if (elevatorWaitTimer > 100) {
            // 尝试重新触发电梯
            if (bird.level().getBlockEntity(targetElevatorPos) instanceof ElevatorBlockEntity elevator) {
                elevator.onStep(bird);
            }
            elevatorWaitTimer = 60;  // 再等3秒
        }
    }

    /**
     * 刚用完电梯，重新导航到门
     */
    private void tickAfterElevator() {
        double yDiff = Math.abs(door.getY() - bird.getY());

        if (yDiff > 5) {
            // 电梯没传送到位，重新找
            state = State.FINDING_PATH;
            evaluateState();
            return;
        }

        // 同楼层，直接导航到门
        state = State.DIRECT_MOVE;
        navigateDirectlyToDoor();
    }

    /**
     * 同楼层直接移动
     */
    private void tickDirectMove() {
        if (door == null) return;

        // 定期检查是否需要重新评估
        recalcTimer++;
        if (recalcTimer >= 60) {
            recalcTimer = 0;
            double yDiff = Math.abs(door.getY() - bird.getY());
            if (yDiff > 5) {
                evaluateState();
                return;
            }
        }

        // 检查是否卡住
        Path path = bird.getNavigation().getPath();
        if (path == null || path.isDone()) {
            stuckTimer++;
            if (stuckTimer > 60) {
                stuckTimer = 0;
                // 直接向门飞行
                Vec3 dir = door.position().subtract(bird.position()).normalize();
                bird.setDeltaMovement(dir.scale(speed * 0.3));
                bird.getNavigation().moveTo(door, speed);
            }
        } else {
            stuckTimer = 0;
        }
    }

    private void navigateDirectlyToDoor() {
        if (door == null) return;

        Path path = bird.getNavigation().createPath(door.blockPosition(), 1);
        if (path != null && path.canReach()) {
            bird.getNavigation().moveTo(path, speed);
        } else {
            bird.getNavigation().moveTo(door, speed);
        }
    }

    @Override
    public void stop() {
        bird.getNavigation().stop();
        targetElevatorPos = null;
        state = State.FINDING_PATH;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}