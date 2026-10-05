package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.world.structure.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(CorpseMountainNavigationGameTests.NAMESPACE)
public final class CorpseMountainNavigationGameTests {
    static final String NAMESPACE = "lobotocraft_corpse_navigation";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 420, batch = "corpse_wall_pursuit_phase_one")
    public static void phaseOneWalksAroundWallInsteadOfAttackingThroughIt(GameTestHelper helper) {
        wallPursuit(helper, 1);
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 420, batch = "corpse_wall_pursuit_phase_two")
    public static void phaseTwoWalksAroundWallInsteadOfLockingInHowls(GameTestHelper helper) {
        wallPursuit(helper, 2);
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 420, batch = "corpse_wall_pursuit_phase_three")
    public static void phaseThreeWalksAroundWallInsteadOfLockingInAttacks(GameTestHelper helper) {
        wallPursuit(helper, 3);
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 420, batch = "corpse_unreachable_victim")
    public static void inaccessibleVictimDoesNotBlockPursuitOfAnAccessibleVictim(GameTestHelper helper) {
        prepareDryFloor(helper);
        for (int x = 12; x <= 16; x++) for (int z = 2; z <= 6; z++)
            if (x == 12 || x == 16 || z == 2 || z == 6)
                for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        EntitySmilingCorpseMountain corpse = ModEntities.smiling_corpse_mountain.get().create(helper.getLevel());
        if (corpse == null) throw new IllegalStateException("corpse mountain missing");
        RecordingClerk sealed = new RecordingClerk(helper.getLevel(), corpse);
        RecordingClerk accessible = new RecordingClerk(helper.getLevel(), corpse);
        place(helper, corpse, new BlockPos(7, 1, 4));
        place(helper, sealed, new BlockPos(14, 1, 4));
        place(helper, accessible, new BlockPos(4, 1, 9));
        sealed.setInvulnerable(true);
        accessible.setInvulnerable(true);
        sealed.setNoAi(true);
        accessible.setNoAi(true);
        corpse.recoverForNewFacilityDay();
        corpse.triggerEscape();
        corpse.setTarget(sealed);
        helper.runAfterDelay(360, () -> {
            try {
                helper.assertTrue(corpse.distanceToSqr(accessible) < 16 && corpse.hasLineOfSight(accessible),
                        "an unreachable victim monopolized pursuit: " + corpse.position()
                                + ", target=" + corpse.getTarget());
                helper.succeed();
            } finally { corpse.discard(); sealed.discard(); accessible.discard(); }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 2200, batch = "corpse_facility_wall_pursuit")
    public static void facilityNavigatorWalksAroundARealDepartmentWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) throw new IllegalStateException("facility dimension missing");
        FacilityNavigationCache.prepare(level);
        class State {
            EntitySmilingCorpseMountain corpse;
            RecordingClerk victim;
            Runnable cleanup;
            int elapsed;
            double furthestZ;
            boolean finished;
        }
        State state = new State();
        helper.onEachTick(() -> {
            if (state.finished) return;
            if (state.corpse != null) {
                state.furthestZ = Math.max(state.furthestZ, state.corpse.getZ());
                if (++state.elapsed < 800) return;
                state.finished = true;
                try {
                    helper.assertTrue(state.victim.hiddenHits == 0, "facility pursuit attacked through wall");
                    helper.assertTrue(state.furthestZ > 42 && state.corpse.hasLineOfSight(state.victim)
                                    && state.corpse.distanceToSqr(state.victim) < 16,
                            "facility A* did not walk around the wall: " + state.corpse.position()
                                    + ", furthestZ=" + state.furthestZ);
                    helper.succeed();
                } finally { state.cleanup.run(); }
                return;
            }
            if (FacilityNavigationCache.staticCellCount(level) < 0) return;
            java.util.Map<BlockPos, BlockState> blocks = new java.util.LinkedHashMap<>();
            java.util.Map<BlockPos, CompoundTag> blockEntities = new java.util.LinkedHashMap<>();
            java.util.Set<BlockPos> protectedBlocks = new java.util.HashSet<>();
            AABB region = new AABB(186, 272, 30, 207, 280, 58);
            EntitySmilingCorpseMountain corpse = ModEntities.smiling_corpse_mountain.get().create(level);
            if (corpse == null) throw new IllegalStateException("corpse mountain missing");
            RecordingClerk victim = new RecordingClerk(level, corpse);
            Runnable cleanup = () -> {
                corpse.discard();
                victim.discard();
                blocks.forEach(level::setBlockAndUpdate);
                blockEntities.forEach((pos, tag) -> {
                    if (level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(tag);
                });
                ProtectionHelper.markProtectedBatch(level, protectedBlocks);
                FacilityNavigationCache.refreshVolume(level, region);
            };
            try {
                for (BlockPos cursor : BlockPos.betweenClosed(
                        new BlockPos(186, 272, 30), new BlockPos(206, 279, 57))) {
                    BlockPos pos = cursor.immutable();
                    blocks.put(pos, level.getBlockState(pos));
                    if (level.getBlockEntity(pos) != null)
                        blockEntities.put(pos, level.getBlockEntity(pos).saveWithFullMetadata());
                    if (ProtectionHelper.isProtected(level, pos)) {
                        protectedBlocks.add(pos);
                        ProtectionHelper.removeProtection(level, pos);
                    }
                    level.setBlockAndUpdate(pos, (pos.getY() == 272
                            || pos.getX() == 194 && pos.getZ() <= 42 && pos.getY() <= 277)
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                FacilityNavigationCache.refreshVolume(level, region);
                corpse.moveTo(191.5, 273, 35.5, 0, 0);
                victim.moveTo(195.5, 273, 35.5, 0, 0);
                victim.setNoAi(true);
                victim.setInvulnerable(true);
                level.addFreshEntity(corpse);
                level.addFreshEntity(victim);
                corpse.recoverForNewFacilityDay();
                corpse.triggerEscape();
                // This fixture tests pursuit after escape, not common exit selection.
                // Other batches may have registered real exits in the same dimension.
                corpse.moveTo(191.5, 273, 35.5, 0, 0);
                corpse.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                corpse.getNavigation().stop();
                corpse.setTarget(victim);
                helper.assertTrue(corpse.hasEscape() && !corpse.hasLineOfSight(victim),
                        "facility wall pursuit starts outside its intended escaped fixture");
                // Do not enqueue more GameTest callbacks while its scheduling map is iterated.
                state.corpse = corpse;
                state.victim = victim;
                state.cleanup = cleanup;
                state.furthestZ = corpse.getZ();
            } catch (Throwable failure) { cleanup.run(); throw failure; }
        });
    }

    private static void place(GameTestHelper helper, net.minecraft.world.entity.Entity entity, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(entity);
    }

    private static void prepareDryFloor(GameTestHelper helper) {
        for (int x = 1; x <= 24; x++) for (int z = 1; z <= 12; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 6; y++) helper.setBlock(new BlockPos(x, y, z),
                    x == 1 || x == 24 || z == 1 || z == 12 || y == 6 ? Blocks.STONE : Blocks.AIR);
        }
    }

    private static void wallPursuit(GameTestHelper helper, int phase) {
        prepareDryFloor(helper);
        // A wide, traversable gap exists at the far end; the near target is hidden by the wall.
        for (int z = 1; z <= 7; z++) for (int y = 1; y <= 5; y++)
            helper.setBlock(new BlockPos(9, y, z), Blocks.STONE);
        EntitySmilingCorpseMountain corpse = ModEntities.smiling_corpse_mountain.get().create(helper.getLevel());
        if (corpse == null) throw new IllegalStateException("corpse mountain missing");
        RecordingClerk victim = new RecordingClerk(helper.getLevel(), corpse);
        BlockPos source = helper.absolutePos(new BlockPos(7, 1, 4));
        BlockPos target = helper.absolutePos(new BlockPos(10, 1, 4));
        corpse.moveTo(source.getX() + 0.5, source.getY(), source.getZ() + 0.5, 0, 0);
        victim.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0, 0);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        victim.setHealth(1000);
        victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        victim.setNoAi(true);
        victim.setInvulnerable(true);
        helper.getLevel().addFreshEntity(corpse);
        helper.getLevel().addFreshEntity(victim);
        corpse.recoverForNewFacilityDay();
        corpse.triggerEscape();
        corpse.setPhase(phase);
        corpse.setTarget(victim);
        double[] furthestZ = {corpse.getZ()};
        helper.onEachTick(() -> furthestZ[0] = Math.max(furthestZ[0], corpse.getZ()));
        helper.assertFalse(corpse.hasLineOfSight(victim), "fixture wall does not obstruct visibility");
        helper.runAfterDelay(360, () -> {
            try {
                helper.assertTrue(victim.hiddenHits == 0,
                        "corpse mountain damaged its target through the wall " + victim.hiddenHits + " times");
                helper.assertTrue(furthestZ[0] > target.getZ() + 3
                                && corpse.hasLineOfSight(victim) && corpse.distanceToSqr(victim) < 16,
                        "corpse mountain failed to walk around wall: pos=" + corpse.position()
                                + ", animation=" + corpse.getAnimation()
                                + ", furthestZ=" + furthestZ[0]
                                + ", phase=" + corpse.getPhase() + ", escaped=" + corpse.hasEscape()
                                + ", pathDone=" + corpse.getNavigation().isDone());
                helper.succeed();
            } finally {
                corpse.discard();
                victim.discard();
            }
        });
    }

    private static final class RecordingClerk extends EntityClerk {
        private final EntitySmilingCorpseMountain corpse;
        private int hiddenHits;

        private RecordingClerk(ServerLevel level, EntitySmilingCorpseMountain corpse) {
            super(ModEntities.clerk.get(), level);
            this.corpse = corpse;
        }

        @Override
        public boolean hurt(DamageSource damage, float amount) {
            if (damage.getEntity() == corpse && !corpse.hasLineOfSight(this)) hiddenHits++;
            return super.hurt(damage, amount);
        }
    }
}
