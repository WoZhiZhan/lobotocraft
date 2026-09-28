package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.FacilityMoveTargetSampler;
import com.wzz.lobotocraft.employee.ElevatorRegistry;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.ai.FacilityMobNavigationTarget;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import com.wzz.lobotocraft.world.structure.Structures;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(AbnormalityFacilityRoamingGameTests.TEST_NAMESPACE)
public final class AbnormalityFacilityRoamingGameTests {
    public static final String TEST_NAMESPACE = ModMain.MODID + "_abnormality_roaming";
    private static final BlockPos SOURCE_ELEVATOR = new BlockPos(4, 1, 4);
    private static final BlockPos DESTINATION_ELEVATOR = new BlockPos(4, 9, 4);
    private static final BlockPos FACILITY_ANCHOR = new BlockPos(195, 269, 29);
    private static final BlockPos STRUCTURE_ORIGIN = new BlockPos(5, 2, 0);
    private static final String CONTROL_MAIN_GROUP =
            "dept/control/passage/sefira-malkuth-1";

    private AbnormalityFacilityRoamingGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true,
            batch = "abnormality_facility_roaming_murderer")
    public static void escapedMurdererUsesElevatorWithoutDroppingHate(GameTestHelper helper) {
        helper.setBlock(SOURCE_ELEVATOR, ModBlocks.ELEVATOR.get());
        helper.setBlock(DESTINATION_ELEVATOR, ModBlocks.ELEVATOR.get());
        BlockPos source = helper.absolutePos(SOURCE_ELEVATOR);
        BlockPos destination = helper.absolutePos(DESTINATION_ELEVATOR);
        ElevatorRegistry.register(helper.getLevel(), source);
        ElevatorRegistry.register(helper.getLevel(), destination);

        EntityAbandonedMurderer murderer = ModEntities.abandoned_murderer.get()
                .create(helper.getLevel());
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(),
                "cross_floor_hate_target", EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        if (murderer == null) {
            helper.fail("could not create the escaped murderer");
            return;
        }

        murderer.absMoveTo(source.getX() + 0.5D, source.getY() + 1.0D,
                source.getZ() + 0.5D, 0.0F, 0.0F);
        murderer.setEscape(true);
        murderer.setNoAi(false);
        BlockPos targetPos = destination.offset(4, 4, 0);
        employee.absMoveTo(targetPos.getX() + 0.5D, targetPos.getY(),
                targetPos.getZ() + 0.5D, 0.0F, 0.0F);
        employee.setInvulnerable(true);
        helper.getLevel().addNewPlayer(employee);
        helper.getLevel().addFreshEntity(murderer);
        murderer.setTarget(employee);

        helper.runAfterDelay(2, () -> {
            Vec3 expectedFloor = Vec3.atBottomCenterOf(destination.above());
            boolean usedElevator = Math.abs(murderer.getY() - expectedFloor.y) <= 0.75D;
            boolean keptHate = murderer.getTarget() == employee;
            murderer.discard();
            employee.kill();
            if (!usedElevator) {
                helper.fail("escaped murderer did not bridge cross-floor hate with the elevator");
            } else if (!keptHate) {
                helper.fail("escaped murderer dropped hate while using the elevator");
            } else {
                helper.succeed();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true,
            batch = "abnormality_facility_roaming_censored")
    public static void escapedCensoredUsesElevatorWithoutDroppingHate(GameTestHelper helper) {
        helper.setBlock(SOURCE_ELEVATOR, ModBlocks.ELEVATOR.get());
        helper.setBlock(DESTINATION_ELEVATOR, ModBlocks.ELEVATOR.get());
        BlockPos source = helper.absolutePos(SOURCE_ELEVATOR);
        BlockPos destination = helper.absolutePos(DESTINATION_ELEVATOR);
        ElevatorRegistry.register(helper.getLevel(), source);
        ElevatorRegistry.register(helper.getLevel(), destination);

        EntityCensored censored = ModEntities.censored.get().create(helper.getLevel());
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(),
                "censored_cross_floor_target", EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        if (censored == null) {
            helper.fail("could not create CENSORED");
            return;
        }

        censored.absMoveTo(source.getX() + 0.5D, source.getY() + 1.0D,
                source.getZ() + 0.5D, 0.0F, 0.0F);
        censored.setEscape(true);
        censored.setNoAi(false);
        BlockPos targetPos = destination.offset(4, 4, 0);
        employee.absMoveTo(targetPos.getX() + 0.5D, targetPos.getY(),
                targetPos.getZ() + 0.5D, 0.0F, 0.0F);
        employee.setInvulnerable(true);
        helper.getLevel().addNewPlayer(employee);
        helper.getLevel().addFreshEntity(censored);
        censored.setTarget(employee);

        helper.runAfterDelay(2, () -> {
            boolean usedElevator = Math.abs(censored.getY() - (destination.getY() + 1.0D)) <= 0.75D;
            boolean keptHate = censored.getTarget() == employee;
            censored.discard();
            employee.kill();
            if (!usedElevator) {
                helper.fail("CENSORED did not bridge cross-floor hate with the elevator");
            } else if (!keptHate) {
                helper.fail("CENSORED dropped hate while using the elevator");
            } else {
                helper.succeed();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 12_000, required = true,
            batch = "abnormality_facility_roaming_generated")
    public static void escapedMurdererRoamsFightsAndResumesRoaming(GameTestHelper helper) {
        new GeneratedRoamingScenario(helper).start();
    }

    private static final class GeneratedRoamingScenario {
        private enum Phase { PREPARING, ROAMING, CHASING, RESUMING }

        private final GameTestHelper helper;
        private final AtomicBoolean generationFinished = new AtomicBoolean();
        private Phase phase = Phase.PREPARING;
        private boolean generationStarted;
        private boolean chunksLoaded;
        private int phaseTicks;
        private EntityAbandonedMurderer murderer;
        private EntityClerk clerk;
        private Vec3 phaseStart;
        private double chaseStartDistance;

        private GeneratedRoamingScenario(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            helper.onEachTick(() -> {
                try {
                    tick();
                } catch (RuntimeException exception) {
                    cleanup();
                    helper.fail(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                }
            });
        }

        private void tick() {
            ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            if (facility == null) {
                helper.fail("facility dimension was not loaded");
                return;
            }
            if (phase == Phase.PREPARING) {
                tickPreparing(facility);
                return;
            }
            phaseTicks++;
            switch (phase) {
                case ROAMING -> tickRoaming(facility);
                case CHASING -> tickChasing(facility);
                case RESUMING -> tickResuming(facility);
                default -> { }
            }
        }

        private void tickPreparing(ServerLevel facility) {
            if (!facility.getBlockState(FACILITY_ANCHOR)
                    .getCollisionShape(facility, FACILITY_ANCHOR).isEmpty()) {
                generationFinished.set(true);
            } else if (!generationStarted) {
                generationStarted = true;
                if (!StructureLoader.beginLoading(facility, null, Structures.LOBOTO.name(),
                        STRUCTURE_ORIGIN, () -> generationFinished.set(true))) {
                    helper.fail("facility structure loader rejected the roaming fixture");
                    return;
                }
            }
            if (!generationFinished.get()) return;
            if (!chunksLoaded) {
                loadFacilityChunks(facility);
                FacilityNavigationCache.prepare(facility);
                chunksLoaded = true;
                return;
            }
            if (FacilityNavigationCache.staticCellCount(facility) < 0) return;

            discardTestCombatants(facility);
            murderer = ModEntities.abandoned_murderer.get().create(facility);
            if (murderer == null) {
                helper.fail("could not create the generated-facility murderer");
                return;
            }
            BlockPos start = sampleTarget(facility, murderer, CONTROL_MAIN_GROUP, 0xABAD54L,
                    ignored -> true);
            if (start == null) {
                murderer.discard();
                helper.fail("could not sample a murderer-sized control-room start");
                return;
            }
            murderer.absMoveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D,
                    0.0F, 0.0F);
            murderer.getRandom().setSeed(0xFACADE54L);
            murderer.setEscape(true);
            murderer.setNoAi(false);
            facility.addFreshEntity(murderer);
            phaseStart = murderer.position();
            phase = Phase.ROAMING;
            phaseTicks = 0;
        }

        private void tickRoaming(ServerLevel facility) {
            boolean travelled = murderer.position().distanceToSqr(phaseStart) >= 8.0D * 8.0D;
            if (travelled) {
                spawnClerkForChase(facility);
                return;
            }
            if (phaseTicks >= 4_000) fail("facility A* patrol did not leave its start position");
        }

        private void spawnClerkForChase(ServerLevel facility) {
            clerk = ModEntities.clerk.get().create(facility);
            if (clerk == null) {
                fail("could not create the roaming chase clerk");
                return;
            }
            FacilityAreaEntry area = FacilityWorldLayout.facilityAreaAt(
                    facility, murderer.blockPosition()).orElse(null);
            if (area == null || area.groupId.isBlank()) {
                fail("roaming murderer was outside measured facility geometry");
                return;
            }
            BlockPos target = sampleTarget(facility, clerk, area.groupId, 0xC1E4C5L,
                    candidate -> {
                        double distance = murderer.distanceToSqr(
                                candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                        return distance >= 36.0D && distance <= 144.0D;
                    });
            if (target == null) {
                fail("could not place a same-area clerk for native pursuit");
                return;
            }
            clerk.absMoveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                    0.0F, 0.0F);
            clerk.setNoAi(true);
            EntityClerk.markNoTombstone(clerk);
            facility.addFreshEntity(clerk);
            chaseStartDistance = murderer.distanceToSqr(clerk);
            phase = Phase.CHASING;
            phaseTicks = 0;
        }

        private void tickChasing(ServerLevel facility) {
            if (murderer.getTarget() == clerk
                    && murderer.distanceToSqr(clerk) + 4.0D < chaseStartDistance) {
                clerk.discard();
                phaseStart = murderer.position();
                phase = Phase.RESUMING;
                phaseTicks = 0;
                return;
            }
            if (phaseTicks >= 400) fail("native pursuit did not take over from facility A*");
        }

        private void tickResuming(ServerLevel facility) {
            boolean targetReleased = murderer.getTarget() == null
                    || murderer.getTarget().isRemoved() || !murderer.getTarget().isAlive();
            if (targetReleased && phaseTicks >= 100) {
                cleanup();
                helper.succeed();
                return;
            }
            if (phaseTicks >= 400) fail("facility A* did not resume after hate was lost");
        }

        private void fail(String reason) {
            String diagnostics = reason + "; phase=" + phase + ", ticks=" + phaseTicks
                    + ", murderer=" + (murderer == null ? "null" : murderer.position())
                    + ", target=" + (murderer == null ? "null" : murderer.getTarget())
                    + ", group=" + (murderer == null ? "" : areaGroup(
                    (ServerLevel) murderer.level(), murderer.blockPosition()));
            cleanup();
            helper.fail(diagnostics);
        }

        private void cleanup() {
            if (clerk != null && !clerk.isRemoved()) clerk.discard();
            if (murderer != null && !murderer.isRemoved()) murderer.discard();
        }
    }

    private static BlockPos sampleTarget(ServerLevel facility,
                                         net.minecraft.world.entity.PathfinderMob mob,
                                         String groupId, long seed,
                                         java.util.function.Predicate<BlockPos> extra) {
        List<FacilityWorldLayout.AreaDefinition> areas =
                FacilityWorldLayout.targetAreas(facility, groupId);
        return FacilityMoveTargetSampler.sampleCandidates(areas, RandomSource.create(seed),
                        256, 64, candidate -> extra.test(candidate)
                                && FacilityMobNavigationTarget.isNavigable(
                                facility, mob, candidate))
                .stream().findFirst().orElse(null);
    }

    private static String areaGroup(ServerLevel facility, BlockPos position) {
        return FacilityWorldLayout.facilityAreaAt(facility, position)
                .map(area -> area.groupId).orElse("");
    }

    private static void loadFacilityChunks(ServerLevel facility) {
        int minChunkX = FacilityNavigationCache.COMPANY_MIN.getX() >> 4;
        int maxChunkX = FacilityNavigationCache.COMPANY_MAX.getX() >> 4;
        int minChunkZ = FacilityNavigationCache.COMPANY_MIN.getZ() >> 4;
        int maxChunkZ = FacilityNavigationCache.COMPANY_MAX.getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                facility.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static void discardTestCombatants(ServerLevel facility) {
        List<net.minecraft.world.entity.Entity> snapshot = new ArrayList<>();
        facility.getAllEntities().forEach(snapshot::add);
        for (var entity : snapshot) {
            if (entity instanceof AbstractAbnormality || entity instanceof EntityClerk) {
                entity.discard();
            }
        }
    }
}
