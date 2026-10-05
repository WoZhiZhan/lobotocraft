package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.entity.abnormality.EntityFourthMatchFlame;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.world.structure.StructureUpdater;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("lobotocraft_scorched_door")
@PrefixGameTestTemplate(false)
public final class ScorchedDoorRegressionGameTests {
    @GameTest(template = "generatedfacilitynavigationgametests.navigation_empty",
            templateNamespace = "lobotocraft_scorched_door", timeoutTicks = 4000, batch = "scorched_door")
    public static void originalWidthGirlCrossesRealDoorTowardsRoomEmployee(GameTestHelper helper) {
        helper.onEachTick(new Runnable() {
            boolean requested, done;
            ServerLevel facility;
            EmployeeRecord record;
            EntityFourthMatchFlame girl;
            int ticks;

            @Override public void run() {
                if (done) return;
                try {
                    facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
                    if (facility == null) throw new IllegalStateException("facility unavailable");
                    if (!requested) {
                        requested = true;
                        StructureUpdater.onPlayerEnter(facility, null);
                    }
                    if (StructureUpdater.isRunning()) return;
                    if (!Structures.LOBOTO.isGenerated(facility))
                        throw new IllegalStateException("facility not generated");
                    if (record == null) {
                        record = FacilityPersonnelData.get(facility.getServer()).create(
                                "scorched_door_regression", EmployeeDepartment.CONTROL);
                        record.setStats(100, 100, 100, 100);
                        record.setPosition(facility.dimension().location(), 160.5, 270, 33.5);
                        if (EmployeeRuntimeManager.spawn(facility.getServer(), record) == null)
                            throw new IllegalStateException("employee spawn refused");
                        var employee = EmployeeRuntimeManager.activePlayer(record.id());
                        if (employee == null) throw new IllegalStateException("employee runtime unavailable");
                        employee.setInvulnerable(true);
                        girl = ModEntities.fourth_match_flame.get().create(facility);
                        if (girl == null) throw new IllegalStateException("girl missing");
                        facility.getChunkAt(new BlockPos(160, 270, 52));
                        girl.moveTo(160.5, 270, 52.5, 0, 0);
                        facility.addFreshEntity(girl);
                        girl.triggerEscape();
                        girl.moveTo(160.5, 270, 52.5, 0, 0);
                    }
                    var employee = EmployeeRuntimeManager.activePlayer(record.id());
                    if (employee == null) throw new IllegalStateException("employee vanished");
                    employee.teleportTo(160.5, 270, 33.5);
                    ticks++;
                    helper.assertTrue(Math.abs(girl.getBbWidth() - .8) < .0001,
                            "regression hides door bug by shrinking production girl");
                    if (Math.abs(girl.getY() - 270) < 1 && Math.abs(girl.getX() - 160.5) < 5
                            && girl.getZ() < 45 && girl.getZ() > 20) {
                        helper.assertTrue(girl.getTargetPlayer() == employee, "girl selected different target");
                        helper.assertTrue(facility.noCollision(girl), "girl crosses door by clipping through it");
                        cleanup();
                        done = true;
                        helper.succeed();
                        return;
                    }
                    if (ticks >= 1000)
                        throw new IllegalStateException("original-width girl cannot cross open doorway: " + girl.position());
                } catch (Exception failure) {
                    cleanup();
                    done = true;
                    helper.fail(failure.toString());
                }
            }

            private void cleanup() {
                if (girl != null) girl.discard();
                if (record != null && facility != null) {
                    EmployeeRuntimeManager.remove(facility.getServer(), record);
                    FacilityPersonnelData.get(facility.getServer()).remove(record.id());
                }
            }
        });
    }
}
