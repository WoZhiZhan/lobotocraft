package com.wzz.lobotocraft.entity.ai.goal;

import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.employee.FacilityMoveTargetSampler;
import com.wzz.lobotocraft.entity.ai.FacilityMobNavigationTarget;
import com.wzz.lobotocraft.entity.ai.FacilityMobNavigator;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/** Runs facility A* patrol only while an escaped abnormality has no hate target. */
public final class FacilityRoamGoal extends Goal {
    private static final int SAMPLE_COLUMNS = 96;
    private static final int SAMPLE_RESULTS = 12;
    private static final double MIN_DESTINATION_DISTANCE_SQUARED = 16.0D * 16.0D;
    private static final int ARRIVAL_DELAY_MIN = 80;
    private static final int ARRIVAL_DELAY_RANDOM = 121;
    private static final int FAILURE_DELAY = 100;

    private final AbstractAbnormality abnormality;
    private final FacilityMobNavigator navigator;
    private BlockPos target;
    private int retryAt;

    public FacilityRoamGoal(AbstractAbnormality abnormality, double speed) {
        this.abnormality = abnormality;
        this.navigator = new FacilityMobNavigator(abnormality, speed);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!isActive() || abnormality.tickCount < retryAt) return false;
        if (!selectTarget()) {
            retryAt = abnormality.tickCount + FAILURE_DELAY;
            return false;
        }
        navigator.moveTo(target);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return isActive() && target != null
                && !navigator.isComplete() && !navigator.isUnreachable();
    }

    @Override
    public void tick() {
        navigator.tick();
        if (navigator.isComplete()) {
            target = null;
            retryAt = abnormality.tickCount + ARRIVAL_DELAY_MIN
                    + abnormality.getRandom().nextInt(ARRIVAL_DELAY_RANDOM);
        } else if (navigator.isUnreachable()) {
            target = null;
            retryAt = abnormality.tickCount + FAILURE_DELAY;
        }
    }

    @Override
    public void stop() {
        navigator.stop();
        if (!isActive()) target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean isActive() {
        return abnormality.canUseFacilityRoaming() && abnormality.getTarget() == null;
    }

    private boolean selectTarget() {
        if (!(abnormality.level() instanceof ServerLevel level)
                || level.dimension() != ModDimensions.LOBOTO_KEY) return false;
        List<FacilityWorldLayout.AreaDefinition> openAreas =
                FacilityWorldLayout.roamingAreas(level.dimension().location()).stream()
                        .filter(area -> FacilityAccessService.isGroupOpen(level.getServer(),
                                level.dimension().location(), area.groupId()))
                        .toList();
        List<FacilityWorldLayout.AreaDefinition> distantAreas = openAreas.stream()
                .filter(area -> distanceToAreaSquared(area, abnormality.blockPosition())
                        >= MIN_DESTINATION_DISTANCE_SQUARED)
                .toList();
        String currentGroup = FacilityWorldLayout.facilityAreaAt(level, abnormality.blockPosition())
                .map(area -> area.groupId).orElse("");
        List<FacilityWorldLayout.AreaDefinition> otherGroups = distantAreas.stream()
                .filter(area -> !area.groupId().equals(currentGroup))
                .toList();
        List<FacilityWorldLayout.AreaDefinition> source = !otherGroups.isEmpty() ? otherGroups
                : !distantAreas.isEmpty() ? distantAreas : openAreas;
        if (source.isEmpty()) return false;
        List<BlockPos> candidates = FacilityMoveTargetSampler.sampleCandidates(
                source, abnormality.getRandom(), SAMPLE_COLUMNS, SAMPLE_RESULTS,
                candidate -> FacilityAccessService.isPositionOpen(level.getServer(),
                        level.dimension().location(), candidate)
                        && abnormality.distanceToSqr(candidate.getX() + 0.5D,
                        candidate.getY(), candidate.getZ()) >= MIN_DESTINATION_DISTANCE_SQUARED
                        && FacilityMobNavigationTarget.isNavigable(level, abnormality, candidate));
        if (candidates.isEmpty()) return false;
        target = candidates.get(abnormality.getRandom().nextInt(candidates.size())).immutable();
        return true;
    }

    private static double distanceToAreaSquared(FacilityWorldLayout.AreaDefinition area,
                                                BlockPos position) {
        int x = Math.max(area.minPos().getX(), Math.min(position.getX(), area.maxPos().getX()));
        int y = Math.max(area.minPos().getY(), Math.min(position.getY(), area.maxPos().getY()));
        int z = Math.max(area.minPos().getZ(), Math.min(position.getZ(), area.maxPos().getZ()));
        return position.distSqr(new BlockPos(x, y, z));
    }
}
