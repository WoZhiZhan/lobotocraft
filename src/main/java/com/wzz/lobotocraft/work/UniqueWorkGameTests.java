package com.wzz.lobotocraft.work;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.EntityCensored;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("lobotocraft_unique_work")
public final class UniqueWorkGameTests {
    @GameTest(template = "navigation_empty", timeoutTicks = 400, batch = "unique_sacrifice_clerk")
    public static void lotteryExcludesHumanAndClerkWalksThenSettlesOnce(GameTestHelper helper) {
        Fixture f = new Fixture(helper, 0);
        helper.runAfterDelay(5, () -> {
        try {
            f.bind();
            helper.assertFalse(UniqueWorkRequest.isCandidate(f.operator), "human player entered lottery");
            helper.assertTrue(UniqueWorkRequest.isCandidate(f.clerk), "clerk was excluded from lottery");
            helper.assertTrue(UniqueWorkRequest.start(f.operator, f.source) == null, "sacrifice rejected");
            helper.assertTrue(UniqueWorkRequest.isControlled(f.clerk), "clerk was not allocated");
            helper.assertTrue(UniqueWorkRequest.start(f.operator, f.source) != null, "duplicate draw admitted");
            helper.assertFalse(WorkManager.startWork(f.operator, f.source, WorkType.INSTINCT),
                    "ordinary work was allowed during sacrifice");
            helper.runAfterDelay(300, () -> {
                try {
                    helper.assertFalse(f.clerk.isAlive(), "clerk did not reach containment and sacrifice");
                    helper.assertTrue(f.source.getQliphothCounter() == 2, "counter did not increase exactly once");
                    helper.assertFalse(UniqueWorkRequest.isBusy(f.source), "completed source remains locked");
                    helper.assertFalse(UniqueWorkRequest.isControlled(f.clerk), "victim remains controlled");
                    helper.assertTrue(f.operator.isAlive(), "human operator was sacrificed");
                    helper.succeed();
                } finally { f.close(); }
            });
        } catch (Throwable failure) { f.close(); throw failure; }
        });
    }

    @GameTest(template = "navigation_empty", timeoutTicks = 100, batch = "unique_sacrifice_cancel")
    public static void sourceRemovalReleasesVictimWithoutCounterReward(GameTestHelper helper) {
        Fixture f = new Fixture(helper, 0);
        helper.runAfterDelay(5, () -> {
        try {
            f.bind();
            helper.assertTrue(UniqueWorkRequest.start(f.operator, f.source) == null, "sacrifice rejected");
            f.source.discard();
            helper.runAfterDelay(3, () -> {
                try {
                    helper.assertTrue(f.clerk.isAlive(), "cancellation killed the victim");
                    helper.assertFalse(f.clerk.isInvulnerable(), "cancellation leaked invulnerability");
                    helper.assertFalse(UniqueWorkRequest.isControlled(f.clerk), "cancellation leaked control");
                    helper.assertTrue(f.source.getQliphothCounter() == 1, "cancellation granted a counter");
                    helper.succeed();
                } finally { f.close(); }
            });
        } catch (Throwable failure) { f.close(); throw failure; }
        });
    }

    @GameTest(template = "navigation_empty", timeoutTicks = 10000, batch = "unique_sacrifice_ai")
    public static void aiEmployeeUsesNavigationAndCannotBeReassigned(GameTestHelper helper) {
        afterFacilityInitialization(helper, () -> runAiScenario(helper));
    }

    private static void afterFacilityInitialization(GameTestHelper helper, Runnable scenario) {
        var level = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        com.wzz.lobotocraft.world.structure.StructureUpdater.onPlayerEnter(level, null);
        var started = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            if (com.wzz.lobotocraft.world.structure.StructureUpdater.isRunning()
                    || !started.compareAndSet(false, true)) return;
            scenario.run();
        });
    }

    private static void runAiScenario(GameTestHelper helper) {
        Fixture f = new Fixture(helper, 0);
        f.clerk.discard();
        helper.runAfterDelay(5, () -> {
            f.bind();
            f.operator.moveTo(f.source.getX() + 6, f.source.getY(), f.source.getZ(), 0, 0);
            var record = com.wzz.lobotocraft.employee.EmployeeRuntimeManager.createAndSpawn(
                    f.operator, "SacrificeAI", com.wzz.lobotocraft.employee.EmployeeDepartment.UNASSIGNED,
                    100, 100, 100, 100);
            try {
                helper.assertTrue(record != null, "AI employee could not be deployed");
                var employee = com.wzz.lobotocraft.employee.EmployeeRuntimeManager.activePlayer(record.id());
                f.operator.moveTo(f.source.getX(), f.source.getY(), f.source.getZ() + 3.5, 0, 0);
                helper.assertTrue(f.level.noCollision(employee), "AI fixture starts inside an obstruction: "
                        + employee.getBoundingBox() + ", feet=" + f.level.getBlockState(employee.blockPosition())
                        + ", head=" + f.level.getBlockState(employee.blockPosition().above())
                        + ", floor=" + f.level.getBlockState(employee.blockPosition().below())
                        + ", entities=" + f.level.getEntities(employee, employee.getBoundingBox()).stream()
                        .map(entity -> entity.getType() + "@" + entity.position()).toList());
                helper.assertTrue(UniqueWorkRequest.isCandidate(employee), "AI employee was excluded");
                helper.assertTrue(UniqueWorkRequest.start(f.operator, f.source) == null, "AI draw failed");
                helper.assertFalse(com.wzz.lobotocraft.employee.EmployeeRuntimeManager.assignMove(
                        record, f.markerPos), "allocated employee accepted another order");
                helper.runAfterDelay(380, () -> {
                    try {
                        helper.assertFalse(employee.isAlive(), "AI employee failed to arrive and sacrifice: "
                                + employee.position() + ", source=" + f.source.position()
                                + ", busy=" + UniqueWorkRequest.isBusy(f.source)
                                + ", animation=" + f.source.getAnimation()
                                + ", " + navigationDiagnostics(record));
                        helper.assertTrue(f.source.getQliphothCounter() == 2, "AI sacrifice did not settle");
                        helper.assertFalse(UniqueWorkRequest.isControlled(employee), "AI control leaked");
                        helper.succeed();
                    } finally {
                        com.wzz.lobotocraft.employee.EmployeeRuntimeManager.remove(f.level.getServer(), record);
                        f.close();
                    }
                });
            } catch (Throwable failure) {
                if (record != null) com.wzz.lobotocraft.employee.EmployeeRuntimeManager.remove(f.level.getServer(), record);
                f.close();
                throw failure;
            }
        });
    }

    private static String navigationDiagnostics(com.wzz.lobotocraft.employee.EmployeeRecord record) {
        try {
            var method = com.wzz.lobotocraft.employee.EmployeeRuntimeManager.class
                    .getDeclaredMethod("testNavigationDiagnostics",
                            com.wzz.lobotocraft.employee.EmployeeRecord.class);
            method.setAccessible(true);
            return String.valueOf(method.invoke(null, record));
        } catch (ReflectiveOperationException failure) {
            return failure.toString();
        }
    }

    @GameTest(template = "navigation_empty", timeoutTicks = 100, batch = "unique_malkuth_dispatch")
    public static void malkuthDoesNotRemapSacrificeAndDayEndReleasesPendingVictim(GameTestHelper helper) {
        Fixture f = new Fixture(helper, 0);
        helper.runAfterDelay(5, () -> {
            var core = com.wzz.lobotocraft.core_suppression.CoreSuppressionData.get(f.level);
            try {
                f.bind();
                helper.assertFalse(core.isActive(), "another suppression is active in the fixture");
                core.start(com.wzz.lobotocraft.core_suppression.CoreSuppressionType.MALKUTH,
                        f.operator.getUUID(), "SacrificeViewer", 1, 0, 0, Map.of());
                core.setMalkuthWorkState(new int[]{3, 2, 1, 0}, 0);
                helper.assertTrue(AbnormalityCommandPolicy.dispatch(f.operator, f.source, 3),
                        "fourth slot did not dispatch the unique action");
                helper.assertTrue(UniqueWorkRequest.isBusy(f.source), "Malkuth redirected sacrifice");
                helper.assertFalse(WorkManager.isPlayerWorking(f.operator), "sacrifice became ordinary work");
                UniqueWorkRequest.endDay(f.level.getServer());
                helper.assertTrue(f.clerk.isAlive(), "day end killed a victim still in the draw");
                helper.assertFalse(f.clerk.isInvulnerable(), "day end leaked invulnerability");
                helper.assertFalse(UniqueWorkRequest.isBusy(f.source), "day end leaked the source reservation");
                helper.succeed();
            } finally {
                core.finish(false);
                f.close();
            }
        });
    }

    @GameTest(template = "navigation_empty", timeoutTicks = 160, batch = "unique_day_settlement")
    public static void dayEndSettlesAlreadyStartedDeathScene(GameTestHelper helper) {
        Fixture f = new Fixture(helper, 0);
        helper.runAfterDelay(5, () -> {
            try {
                f.bind();
                f.clerk.teleportTo(f.source.getX() + 2, f.source.getY(), f.source.getZ());
                helper.assertTrue(UniqueWorkRequest.start(f.operator, f.source) == null, "draw failed");
                helper.runAfterDelay(90, () -> {
                    try {
                        helper.assertTrue(f.clerk.isAlive() && "sacrifice".equals(f.source.getAnimation()),
                                "fixture did not enter an unsettled death scene");
                        UniqueWorkRequest.endDay(f.level.getServer());
                        helper.assertFalse(f.clerk.isAlive(), "day end did not finish the active death scene");
                        helper.assertTrue(f.source.getQliphothCounter() == 2, "day-end settlement lost its reward");
                        helper.assertFalse(f.clerk.isInvulnerable(), "settlement leaked invulnerability");
                        helper.succeed();
                    } finally { f.close(); }
                });
            } catch (Throwable failure) { f.close(); throw failure; }
        });
    }

    @GameTest(template = "navigation_empty", timeoutTicks = 100, batch = "unique_restart_cleanup")
    public static void savedVictimRecoversWhenRuntimeRequestNoLongerExists(GameTestHelper helper) {
        Fixture f = new Fixture(helper, 0);
        helper.runAfterDelay(5, () -> {
            EntityClerk restored = null;
            try {
                f.bind();
                helper.assertTrue(UniqueWorkRequest.start(f.operator, f.source) == null, "draw failed");
                var saved = f.clerk.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                UniqueWorkRequest.endDay(f.level.getServer());
                restored = ModEntities.clerk.get().create(f.level);
                helper.assertTrue(restored != null, "could not recreate saved victim");
                restored.load(saved);
                helper.assertTrue(restored.isInvulnerable(), "fixture did not retain saved control state");
                UniqueWorkRequest.recoverTransientState(restored);
                helper.assertFalse(restored.isInvulnerable(), "restart left the victim invulnerable");
                helper.assertFalse(UniqueWorkRequest.isControlled(restored), "restart retained a dead request");
                helper.succeed();
            } finally {
                if (restored != null) restored.discard();
                f.close();
            }
        });
    }

    @GameTest(template = "navigation_empty", timeoutTicks = 10000, batch = "unique_protection_ai")
    public static void protectionCommandSurvivesMalkuthAndTracksSyntheticEmployee(GameTestHelper helper) {
        afterFacilityInitialization(helper, () -> {
            Fixture f = new Fixture(helper, 0);
            f.source.discard();
            f.clerk.discard();
            var army = ModEntities.army_in_black.get().create(f.level);
            if (army == null) { f.close(); throw new IllegalStateException("Missing army"); }
            army.moveTo(f.source.getX(), f.source.getY(), f.source.getZ(), 0, 0);
            f.level.addFreshEntity(army);
            helper.runAfterDelay(5, () -> {
                var core = com.wzz.lobotocraft.core_suppression.CoreSuppressionData.get(f.level);
                com.wzz.lobotocraft.employee.EmployeeRecord record = null;
                try {
                    helper.assertTrue(((RoomMarkerBlockEntity) f.level.getBlockEntity(f.markerPos))
                            .bindByUuid(f.level, army.getUUID()), "army was not registered");
                    f.refreshNavigation();
                    f.operator.moveTo(army.getX() + 6, army.getY(), army.getZ(), 0, 0);
                    record = com.wzz.lobotocraft.employee.EmployeeRuntimeManager.createAndSpawn(
                            f.operator, "ProtectedAI", com.wzz.lobotocraft.employee.EmployeeDepartment.UNASSIGNED,
                            100, 100, 100, 100);
                    helper.assertTrue(record != null, "could not create protected AI");
                    var employee = com.wzz.lobotocraft.employee.EmployeeRuntimeManager.activePlayer(record.id());
                    f.operator.moveTo(army.getX(), army.getY(), army.getZ() + 3.5, 0, 0);
                    core.start(com.wzz.lobotocraft.core_suppression.CoreSuppressionType.MALKUTH,
                            f.operator.getUUID(), "ProtectionViewer", 1, 0, 0, Map.of());
                    core.setMalkuthWorkState(new int[]{3, 2, 1, 0}, 0);
                    helper.assertTrue(UniqueWorkRequest.startProtection(f.operator, army, employee) == null,
                            "Malkuth blocked the protection command");
                    helper.assertFalse(WorkManager.isPlayerWorking(employee), "protection became ordinary work");
                    core.finish(false);
                    var createdRecord = record;
                    helper.runAfterDelay(260, () -> {
                        try {
                            helper.assertTrue(employee.isAlive(), "non-lethal protection command killed employee");
                            helper.assertTrue(army.isProtecting(employee.getUUID()), "AI protection did not persist");
                            helper.assertTrue(com.wzz.lobotocraft.entity.abnormality.EntityArmyInBlack
                                    .hasActiveProtection(employee), "AI lost the protection effect");
                            helper.assertFalse(UniqueWorkRequest.isControlled(employee), "protection left employee controlled");
                            helper.assertTrue(army.getQliphothCounter() == 2, "protection settled more than once");
                            army.discard();
                            helper.assertFalse(com.wzz.lobotocraft.entity.abnormality.EntityArmyInBlack
                                    .hasActiveProtection(employee), "protector removal leaked the effect");
                            helper.succeed();
                        } finally {
                            army.discard();
                            com.wzz.lobotocraft.employee.EmployeeRuntimeManager.remove(f.level.getServer(), createdRecord);
                            f.close();
                        }
                    });
                } catch (Throwable failure) {
                    core.finish(false);
                    army.discard();
                    if (record != null) com.wzz.lobotocraft.employee.EmployeeRuntimeManager.remove(f.level.getServer(), record);
                    f.close();
                    throw failure;
                }
            });
        });
    }

    @GameTest(template = "navigation_empty", timeoutTicks = 10000, batch = "unique_commission")
    public static void commissionKeepsNativeTargetRulesAndConsumesOneMarkerUnderMalkuth(GameTestHelper helper) {
        afterFacilityInitialization(helper, () -> {
            Fixture f = new Fixture(helper, 0);
            f.source.discard();
            f.clerk.discard();
            var hood = ModEntities.redhat_mercenary.get().create(f.level);
            var target = ModEntities.helper.get().create(f.level);
            if (hood == null || target == null) { f.close(); throw new IllegalStateException("Missing commission entities"); }
            hood.moveTo(f.source.getX(), f.source.getY(), f.source.getZ(), 0, 0);
            target.moveTo(f.source.getX() + 8, f.source.getY(), f.source.getZ(), 0, 0);
            f.level.addFreshEntity(hood);
            f.level.addFreshEntity(target);
            helper.runAfterDelay(5, () -> {
                var core = com.wzz.lobotocraft.core_suppression.CoreSuppressionData.get(f.level);
                try {
                    helper.assertTrue(((RoomMarkerBlockEntity) f.level.getBlockEntity(f.markerPos))
                            .bindByUuid(f.level, hood.getUUID()), "red hood was not registered");
                    target.triggerEscape();
                    helper.assertTrue(target.hasEscape(), "commission target did not escape");
                    f.operator.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    var marker = new net.minecraft.world.item.ItemStack(
                            com.wzz.lobotocraft.init.ModItems.TARGET_MARKER.get());
                    f.operator.getInventory().add(marker);
                    core.start(com.wzz.lobotocraft.core_suppression.CoreSuppressionType.MALKUTH,
                            f.operator.getUUID(), "CommissionViewer", 1, 0, 0, Map.of());
                    core.setMalkuthWorkState(new int[]{3, 2, 1, 0}, 0);
                    helper.assertTrue(UniqueWorkRequest.commitCommission(f.operator, hood, f.operator) != null,
                            "commission accepted a human target");
                    helper.assertTrue(f.operator.getInventory().countItem(
                            com.wzz.lobotocraft.init.ModItems.TARGET_MARKER.get()) == 1,
                            "invalid target consumed the marker");
                    helper.assertTrue(UniqueWorkRequest.commitCommission(f.operator, hood, target) == null,
                            "Malkuth redirected or blocked commission");
                    helper.assertTrue(hood.isCommissionedAgainst(target.getUUID()), "wrong commission target");
                    helper.assertFalse(WorkManager.isPlayerWorking(f.operator), "commission became ordinary work");
                    helper.assertTrue(f.operator.getInventory().countItem(
                            com.wzz.lobotocraft.init.ModItems.TARGET_MARKER.get()) == 0, "marker was not consumed");
                    helper.assertTrue(UniqueWorkRequest.commitCommission(f.operator, hood, target) != null,
                            "duplicate commission was accepted");
                    helper.succeed();
                } finally {
                    core.finish(false);
                    hood.discard();
                    target.discard();
                    f.close();
                }
            });
        });
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final ServerPlayer operator;
        final EntityCensored source;
        final EntityClerk clerk;
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Map<BlockPos, net.minecraft.nbt.CompoundTag> blockEntities = new HashMap<>();
        final java.util.Set<BlockPos> protectedBlocks = new java.util.HashSet<>();
        final java.util.Set<net.minecraft.world.entity.LivingEntity> excluded =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final BlockPos markerPos;

        Fixture(GameTestHelper helper, int offset) {
            level = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            if (level == null) throw new IllegalStateException("Missing facility dimension");
            for (var entity : level.getAllEntities()) {
                if (entity instanceof net.minecraft.world.entity.LivingEntity living
                        && UniqueWorkRequest.isCandidate(living)) {
                    excluded.add(living);
                    living.setInvulnerable(true);
                }
            }
            for (var employee : com.wzz.lobotocraft.employee.EmployeeRuntimeManager.activePlayers()) {
                if (employee.level() == level && UniqueWorkRequest.isCandidate(employee)) {
                    excluded.add(employee);
                    employee.setInvulnerable(true);
                }
            }
            markerPos = new BlockPos(160 + offset, 270, 30);
            for (int x = -3; x <= 10; x++) for (int z = -4; z <= 4; z++) {
                BlockPos floor = markerPos.offset(x, -1, z);
                saveAndUnprotect(floor);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 7; y++) {
                    BlockPos space = markerPos.offset(x, y, z);
                    saveAndUnprotect(space);
                    level.setBlockAndUpdate(space, Blocks.AIR.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(markerPos, ModBlocks.ROOM_MARKER.get().defaultBlockState());
            source = ModEntities.censored.get().create(level);
            clerk = ModEntities.clerk.get().create(level);
            if (source == null || clerk == null) throw new IllegalStateException("Fixture entities missing");
            source.moveTo(markerPos.getX() + 2.5, markerPos.getY(), markerPos.getZ() + .5, 0, 0);
            clerk.moveTo(markerPos.getX() + 8.5, markerPos.getY(), markerPos.getZ() + .5, 0, 0);
            level.addFreshEntity(source);
            level.addFreshEntity(clerk);
            operator = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SacrificeViewer"));
            operator.moveTo(source.getX(), source.getY(), source.getZ() + 2.0, 0, 0);
        }

        private void saveAndUnprotect(BlockPos pos) {
            blocks.put(pos, level.getBlockState(pos));
            if (level.getBlockEntity(pos) != null) {
                blockEntities.put(pos, level.getBlockEntity(pos).saveWithFullMetadata());
            }
            if (com.wzz.lobotocraft.world.structure.ProtectionHelper.isProtected(level, pos)) {
                protectedBlocks.add(pos);
                com.wzz.lobotocraft.world.structure.ProtectionHelper.removeProtection(level, pos);
            }
        }

        void bind() {
            if (!(level.getBlockEntity(markerPos) instanceof RoomMarkerBlockEntity marker)
                    || !marker.bindByUuid(level, source.getUUID())) {
                throw new IllegalStateException("Could not register source");
            }
            source.setQliphothCounter(1);
            refreshNavigation();
        }

        @Override public void close() {
            source.discard();
            UniqueWorkRequest.endDay(level.getServer());
            clerk.discard();
            operator.discard();
            excluded.forEach(entity -> entity.setInvulnerable(false));
            com.wzz.lobotocraft.world.data.CompanyRoomData.get(level)
                    .removeRoom(level.dimension().location(), markerPos);
            blocks.forEach((pos, state) -> {
                com.wzz.lobotocraft.world.structure.ProtectionHelper.removeProtection(level, pos);
                level.setBlockAndUpdate(pos, state);
            });
            blockEntities.forEach((pos, tag) -> {
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    blockEntity.load(tag);
                    blockEntity.setChanged();
                }
            });
            protectedBlocks.forEach(pos -> com.wzz.lobotocraft.world.structure.ProtectionHelper.markProtected(level, pos));
            refreshNavigation();
        }

        private void refreshNavigation() {
            // Test block placement is not a player BlockEvent; publish the fixture
            // to the shared navigation cache as well as the live level.
            com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache.refreshVolume(level,
                    new net.minecraft.world.phys.AABB(markerPos.offset(-3, -1, -4),
                            markerPos.offset(11, 8, 5)));
        }
    }
}
