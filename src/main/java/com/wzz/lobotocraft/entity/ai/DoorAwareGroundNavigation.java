package com.wzz.lobotocraft.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla plans door cells at their centers. An open side panel can leave a
 * passable off-center gap that a wider body's centered waypoint still clips.
 * Correct only the local passage, retaining vanilla paths, doors and physics.
 */
public final class DoorAwareGroundNavigation extends GroundPathNavigation {
    private Passage passage;
    private boolean aligned;

    public DoorAwareGroundNavigation(Mob mob, Level level) { super(mob, level); }

    @Override public void stop() {
        passage = null;
        aligned = false;
        super.stop();
    }

    @Override public void tick() {
        super.tick();
        if (path == null || path.isDone()) {
            passage = null;
            return;
        }
        if (passage != null && (!isOpenDoor(passage.door)
                || mob.position().distanceToSqr(passage.exit) > 25)) {
            passage = null;
            aligned = false;
        }
        if (passage == null) findPassage();
        if (passage == null) return;
        Vec3 crossing = new Vec3(
                Math.abs(passage.exit.x - passage.entry.x) < .001
                        ? mob.getX() : (passage.entry.x + passage.exit.x) / 2,
                mob.getY(),
                Math.abs(passage.exit.x - passage.entry.x) < .001
                        ? (passage.entry.z + passage.exit.z) / 2 : mob.getZ());
        // Momentum/turning can drift out of a narrow gap after initial alignment.
        // Re-check the actual body coordinate, not a latched "aligned once" flag.
        aligned = level.noCollision(mob, mob.getDimensions(mob.getPose())
                .makeBoundingBox(crossing.x, crossing.y, crossing.z));
        if (aligned && mob.position().distanceToSqr(passage.exit) < .04) {
            passage = null;
            aligned = false;
            return;
        }
        // Align laterally first. A diagonal approach can hit the panel before
        // reaching its safe lateral coordinate, even when both endpoints fit.
        Vec3 wanted = aligned ? passage.exit
                : Math.abs(passage.exit.x - passage.entry.x) < .001
                    ? new Vec3(passage.entry.x, mob.getY(), mob.getZ())
                    : new Vec3(mob.getX(), mob.getY(), passage.entry.z);
        mob.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, speedModifier);
    }

    private void findPassage() {
        int last = Math.min(path.getNodeCount(), path.getNextNodeIndex() + 4);
        for (int i = path.getNextNodeIndex(); i < last; i++) {
            BlockPos pos = path.getNode(i).asBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || !state.getValue(DoorBlock.OPEN)) continue;
            boolean alongZ = state.getValue(DoorBlock.FACING).getAxis() == Direction.Axis.Z;
            double centerAxis = alongZ ? pos.getZ() + .5 : pos.getX() + .5;
            double bodyAxis = alongZ ? mob.getZ() : mob.getX();
            int sign = bodyAxis < centerAxis ? 1 : -1;
            if (Math.abs(bodyAxis - centerAxis) > 2.5) continue;
            // Derive clearance from panel geometry, not an abnormality-specific offset.
            java.util.List<Double> offsets = new java.util.ArrayList<>();
            offsets.add(.5);
            double half = mob.getBbWidth() / 2D;
            for (var box : state.getCollisionShape(level, pos).toAabbs()) {
                offsets.add((alongZ ? box.maxX : box.maxZ) + half + .01);
                offsets.add((alongZ ? box.minX : box.minZ) - half - .01);
            }
            for (double offset : offsets) {
                if (offset < half || offset > 1 - half) continue;
                double x = pos.getX() + (alongZ ? offset : .5);
                double z = pos.getZ() + (alongZ ? .5 : offset);
                Vec3 center = new Vec3(x, mob.getY(), z);
                // Do not hijack a centered passage which already fits this body.
                if (offset == .5 && level.noCollision(mob,
                        mob.getDimensions(mob.getPose()).makeBoundingBox(x, mob.getY(), z))) return;
                Vec3 axis = alongZ ? new Vec3(0, 0, sign) : new Vec3(sign, 0, 0);
                double clearance = half + .75;
                Vec3 entry = center.subtract(axis.scale(clearance));
                Vec3 exit = center.add(axis.scale(clearance));
                if (!clearSweep(entry, exit) || !clearSweep(mob.position(), entry)) continue;
                passage = new Passage(pos.immutable(), entry, exit);
                aligned = false;
                return;
            }
        }
    }

    private boolean isOpenDoor(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN);
    }

    private boolean clearSweep(Vec3 from, Vec3 to) {
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .1));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.lerp(to, (double) i / steps);
            var box = mob.getDimensions(mob.getPose()).makeBoundingBox(point.x, point.y, point.z);
            if (!level.noCollision(mob, box)) return false;
            if (level.noCollision(mob, box.move(0, -.0625, 0))) return false;
        }
        return true;
    }

    private record Passage(BlockPos door, Vec3 entry, Vec3 exit) {}
}
