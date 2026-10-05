package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.FacilityMoveTargetSampler;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.entity.ai.FacilityMobNavigationTarget;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.concurrent.atomic.AtomicBoolean;

/** Real generated walls/elevators; no synthetic bridges, teleport-arrival hooks or direct damage. */
@GameTestHolder(ArmyFacilityNavigationGameTests.NAMESPACE)
public final class ArmyFacilityNavigationGameTests {
    static final String NAMESPACE = "lobotocraft_army_navigation";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 16000,
            batch = "army_generated_routes")
    public static void armyWalksFromSafetyUpperAndControlLowerToTheirMainReactors(GameTestHelper helper) {
        new Routes(helper).start();
    }

    private static final class Routes {
        private final GameTestHelper helper;
        private final AtomicBoolean generated = new AtomicBoolean();
        private boolean generating, prepared, finished;
        private EntityArmyInBlack army;
        private CompoundTag previousProgress;
        private ServerLevel facility;
        private int route, routeTicks;
        private final String[] sources = {
                "dept/safety/passage/n-upper-way-rightdoor", "dept/control/passage/left-down-way1"
        };
        private final String[] departments = {"safety", "control"};
        private final int[] preferredY = {236, 237};
        private BlockPos birth;

        private Routes(GameTestHelper helper) { this.helper = helper; }
        private void start() {
            helper.onEachTick(() -> {
                if (finished) return;
                try { tick(); }
                catch (Exception failure) {
                    cleanup();
                    helper.fail(failure.getClass().getSimpleName() + ": " + failure.getMessage());
                }
            });
        }
        private void tick() throws Exception {
            facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            if (facility == null) throw new IllegalStateException("facility missing");
            if (!prepared) {
                if (!generating) {
                    generating = true;
                    if (!StructureLoader.beginLoading(facility, null, Structures.LOBOTO.name(),
                            new BlockPos(5, 2, 0), () -> generated.set(true)))
                        throw new IllegalStateException("facility generation rejected");
                }
                if (!generated.get()) return;
                var progress = DepartmentProgressData.get(facility.getServer());
                if (previousProgress == null) {
                    previousProgress = progress.save(new CompoundTag());
                    for (var department : EmployeeDepartment.values())
                        if (department != EmployeeDepartment.UNASSIGNED) progress.setLevel(department, 5);
                    for (int cx = FacilityNavigationCache.COMPANY_MIN.getX() >> 4;
                         cx <= FacilityNavigationCache.COMPANY_MAX.getX() >> 4; cx++)
                        for (int cz = FacilityNavigationCache.COMPANY_MIN.getZ() >> 4;
                             cz <= FacilityNavigationCache.COMPANY_MAX.getZ() >> 4; cz++)
                            facility.getChunk(cx, cz);
                    FacilityNavigationCache.prepare(facility);
                }
                if (FacilityNavigationCache.staticCellCount(facility) < 0) return;
                prepared = true;
            }
            if (army == null) {
                army = ModEntities.army_in_black.get().create(facility);
                if (army == null) throw new IllegalStateException("army missing");
                var candidates = FacilityMoveTargetSampler.sampleCandidates(
                        FacilityWorldLayout.targetAreas(facility, sources[route]),
                        RandomSource.create(106L + route), 512, 256,
                        pos -> Math.abs(pos.getY() - preferredY[route]) <= 1
                                && FacilityMobNavigationTarget.isNavigable(facility, army, pos));
                if (candidates.isEmpty()) throw new IllegalStateException("no navigable source " + sources[route]);
                birth = candidates.get(0);
                army.moveTo(birth.getX() + 0.5D, birth.getY(), birth.getZ() + 0.5D, 0, 0);
                army.setEscape(true);
                army.setNoAi(false);
                facility.addFreshEntity(army);
                routeTicks = 0;
                return;
            }
            routeTicks++;
            if (routeTicks == 100) {
                // Interrupt a real pending/walking route with its real retaliation wind-up.
                ArmyRootRegressionGameTests.invoke(army, "beginEscapeAttack", new Class<?>[0]);
                helper.assertTrue(ArmyRootRegressionGameTests.get(army, "reactorWalkPos") == null,
                        "attack wind-up retained its walking route");
            }
            int detonation = (int) ArmyRootRegressionGameTests.get(army, "selfDetonationTicks");
            if (detonation > 0) {
                var hall = FacilityWorldLayout.areaAt(facility.dimension().location(), army.blockPosition())
                        .orElse(null);
                if (hall == null || !hall.departmentId().equals(departments[route])
                        || !hall.id().endsWith("/main"))
                    throw new IllegalStateException("detonating outside target main room: " + army.position()
                            + ", hall=" + hall);
                helper.assertTrue(Math.abs(army.getY() - birth.getY()) > 10,
                        "route never crossed the source/main-room height gap");
                army.discard();
                army = null;
                if (++route == sources.length) { cleanup(); helper.succeed(); }
                return;
            }
            if (routeTicks >= 4500) {
                var target = ArmyRootRegressionGameTests.get(army, "targetReactorPos");
                Object nav = ArmyRootRegressionGameTests.get(army, "reactorNavigator");
                throw new IllegalStateException("route stalled " + sources[route] + ", birth=" + birth
                        + ", now=" + army.position() + ", reactor=" + target
                        + ", elevator=" + ArmyRootRegressionGameTests.get(nav, "elevator")
                        + ", state=" + ArmyRootRegressionGameTests.get(nav, "state"));
            }
        }
        private void cleanup() {
            finished = true;
            if (army != null) army.discard();
            if (facility != null && previousProgress != null)
                facility.getServer().overworld().getDataStorage().set("lobotocraft_department_progress",
                        DepartmentProgressData.load(previousProgress));
        }
    }
}
