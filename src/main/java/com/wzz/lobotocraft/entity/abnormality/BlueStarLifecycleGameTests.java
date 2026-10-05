package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.work.AbnormalityWorkRules;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.GenerationBiggerData;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.structure.ProtectionHelper;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import com.wzz.lobotocraft.world.structure.Structures;
import com.wzz.lobotocraft.util.TimerEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import com.mojang.authlib.GameProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(BlueStarLifecycleGameTests.TEST_NAMESPACE)
public final class BlueStarLifecycleGameTests {
    static final String TEST_NAMESPACE = ModMain.MODID + "_blue_star_lifecycle";

    private BlueStarLifecycleGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "blue_star_escape_lifecycle")
    public static void escapeLandsInsideHomeDepartmentNearItsReactor(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension unavailable");
            return;
        }
        DepartmentProgressData progress = DepartmentProgressData.get(facility.getServer());
        CompoundTag previousProgress = progress.save(new CompoundTag());
        GenerationBiggerData previousGeneration = GenerationBiggerData.get(facility);
        facility.getServer().overworld().getDataStorage().set(GenerationBiggerData.ID,
                GenerationBiggerData.load(previousGeneration.save(new CompoundTag())));
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
        Set<BlockPos> protectedBlocks = new java.util.HashSet<>();
        Map<String, java.util.List<BlockPos>> reactors = new LinkedHashMap<>();
        try {
            // Match StructureUpdater's persisted legacy bounds; measured geometry is authoritative.
            Structures.LOBOTO.setGenerated(facility, 100, 100, 100);
            for (EmployeeDepartment department : EmployeeDepartment.values()) {
                if (department != EmployeeDepartment.UNASSIGNED) progress.setLevel(department, 5);
            }
            var halls = FacilityWorldLayout.fixedAreas(facility.dimension().location()).stream()
                    .filter(area -> area.type() == FacilityAreaType.REST_ROOM
                            && (area.id().endsWith("/main") || area.id().endsWith("-main")
                            || area.id().endsWith("/main-reactor") || area.id().endsWith("-main-reactor"))).toList();
            // Use the authored structure, not synthetic reactors at guessed room centers.
            placeAuthoredMainRooms(facility, halls, reactors, blocks, blockEntities, protectedBlocks);
            int checked = 0;
            for (var binding : FacilityWorldLayout.containmentBindings(facility.dimension().location())) {
                boolean lower = binding.groupId().startsWith("dept/central/lower/");
                var eligibleHalls = halls.stream()
                        .filter(area -> area.departmentId().equals(binding.departmentId()))
                        .filter(area -> !binding.departmentId().equals("central")
                                || area.groupId().startsWith("dept/central/lower/") == lower)
                        .filter(area -> !reactors.getOrDefault(area.id(), java.util.List.of()).isEmpty())
                        .toList();
                helper.assertFalse(eligibleHalls.isEmpty(),
                        "authored main room has no reactor for " + binding.groupId());
                EntityBlueStar blueStar = ModEntities.blue_star.get().create(facility);
                if (blueStar == null) throw new IllegalStateException("could not create blue star");
                try {
                    blueStar.bindHomeRoom(facility.dimension().location(), binding.markerPos());
                    blueStar.moveTo(binding.markerPos().getX() + 0.5D, binding.markerPos().getY(),
                            binding.markerPos().getZ() + 0.5D, 0, 0);
                    facility.addFreshEntity(blueStar);
                    blueStar.triggerEscape();
                    helper.assertTrue(blueStar.hasEscape(), "did not escape from " + binding.markerPos()
                            + "; main-room reactors=" + reactors);
                    var hall = eligibleHalls.stream().filter(area -> area.contains(blueStar.blockPosition()))
                            .findFirst().orElse(null);
                    helper.assertTrue(hall != null,
                            "wrong department/section for " + binding.markerPos() + ": " + blueStar.blockPosition());
                    if (hall == null) return;
                    var box = blueStar.getBoundingBox();
                    helper.assertTrue(box.minX >= hall.minPos().getX() && box.maxX <= hall.maxPos().getX() + 1
                                    && box.minY >= hall.minPos().getY() && box.maxY <= hall.maxPos().getY() + 1
                                    && box.minZ >= hall.minPos().getZ() && box.maxZ <= hall.maxPos().getZ() + 1,
                            "body extends outside the main room: " + hall.id());
                    helper.assertTrue(facility.noCollision(blueStar, box), "landing overlaps blocks");
                    helper.assertTrue(facility.getBlockState(blueStar.blockPosition().below()).isFaceSturdy(
                                    facility, blueStar.blockPosition().below(), net.minecraft.core.Direction.UP),
                            "landing has no floor");
                    helper.assertTrue(reactors.get(hall.id()).stream()
                                    .anyMatch(reactor -> blueStar.blockPosition().distSqr(reactor) <= 100),
                            "landing too far from main-room reactor");
                    checked++;
                } finally {
                    EscapeTracker.getInstance().onEscapeStop(blueStar);
                    blueStar.discard();
                }
            }
            helper.assertTrue(checked == 48, "source-room matrix was unexpectedly incomplete: " + checked);
            helper.succeed();
        } finally {
            blocks.forEach(facility::setBlockAndUpdate);
            blockEntities.forEach((pos, tag) -> {
                if (facility.getBlockEntity(pos) != null) facility.getBlockEntity(pos).load(tag);
            });
            ProtectionHelper.markProtectedBatch(facility, protectedBlocks);
            facility.getServer().overworld().getDataStorage().set("lobotocraft_department_progress",
                    DepartmentProgressData.load(previousProgress));
            facility.getServer().overworld().getDataStorage().set(GenerationBiggerData.ID, previousGeneration);
        }
    }

    private static void placeAuthoredMainRooms(ServerLevel level,
            java.util.List<FacilityWorldLayout.AreaDefinition> halls,
            Map<String, java.util.List<BlockPos>> reactors, Map<BlockPos, BlockState> blocks,
            Map<BlockPos, CompoundTag> blockEntities, Set<BlockPos> protectedBlocks) {
        String base = Structures.LOBOTO.name();
        CompoundTag metadata = StructureLoader.loadMetadata(base + "_main");
        int size = metadata.getInt("chunkSize");
        Map<BlockPos, BlockState> authored = new java.util.HashMap<>();
        for (int x = 0; x < metadata.getInt("totalXChunks"); x++) {
            for (int y = 0; y < metadata.getInt("totalYChunks"); y++) {
                for (int z = 0; z < metadata.getInt("totalZChunks"); z++) {
                    CompoundTag tag = StructureLoader.loadMetadata(String.format(java.util.Locale.ROOT,
                            "%s_part%02d%02d%02d", base, x, y, z));
                    if (tag == null || tag.isEmpty()) continue;
                    StructureTemplate template = new StructureTemplate();
                    template.load(level.holderLookup(Registries.BLOCK), tag);
                    if (template.palettes.isEmpty()) continue;
                    BlockPos offset = new BlockPos(5 + x * size, 2 + y * size, z * size);
                    for (var info : template.palettes.get(0).blocks()) {
                        BlockPos pos = offset.offset(info.pos());
                        if (halls.stream().noneMatch(hall -> hall.contains(pos) || hall.contains(pos.above()))) continue;
                        authored.put(pos.immutable(), info.state());
                        if (info.state().is(ModBlocks.REGENERATION_REACTOR.get())) {
                            halls.stream().filter(hall -> hall.contains(pos)).forEach(hall ->
                                    reactors.computeIfAbsent(hall.id(), ignored -> new java.util.ArrayList<>())
                                            .add(pos.immutable()));
                        }
                    }
                }
            }
        }
        for (var hall : halls) {
            for (BlockPos position : BlockPos.betweenClosed(hall.minPos().below(), hall.maxPos())) {
                BlockPos pos = position.immutable();
                BlockState state = authored.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                if (!level.getBlockState(pos).equals(state)) {
                    changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, pos, state);
                }
            }
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "blue_star_escape_lifecycle")
    public static void blockedOrLockedMainRoomDoesNotFallBackAndRetries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) throw new IllegalStateException("facility dimension unavailable");
        DepartmentProgressData progress = DepartmentProgressData.get(level.getServer());
        CompoundTag previousProgress = progress.save(new CompoundTag());
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
        Set<BlockPos> protectedBlocks = new java.util.HashSet<>();
        var hall = FacilityWorldLayout.fixedAreas(level.dimension().location()).stream()
                .filter(area -> area.id().equals("training/main")).findFirst().orElseThrow();
        var binding = FacilityWorldLayout.containmentBindings(level.dimension().location()).stream()
                .filter(room -> room.departmentId().equals("training")).findFirst().orElseThrow();
        BlockPos reactor = new BlockPos((hall.minPos().getX() + hall.maxPos().getX()) / 2,
                hall.minPos().getY() + 2, (hall.minPos().getZ() + hall.maxPos().getZ()) / 2);
        EntityBlueStar blueStar = ModEntities.blue_star.get().create(level);
        if (blueStar == null) throw new IllegalStateException("could not create blue star");
        try {
            progress.setLevel(EmployeeDepartment.TRAINING, 5);
            blueStar.bindHomeRoom(level.dimension().location(), binding.markerPos());
            blueStar.moveTo(binding.markerPos().getX() + 0.5D, binding.markerPos().getY(),
                    binding.markerPos().getZ() + 0.5D, 0, 0);
            level.addFreshEntity(blueStar);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    changeFixtureBlock(level, blocks, blockEntities, protectedBlocks,
                            binding.markerPos().offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                }
            }
            blueStar.recoverForNewFacilityDay();
            var origin = blueStar.position();
            // Seal the entire destination, including any existing reactors.
            for (BlockPos pos : BlockPos.betweenClosed(hall.minPos(), hall.maxPos())) {
                changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, pos.immutable(),
                        Blocks.BARRIER.defaultBlockState());
            }
            changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, reactor,
                    ModBlocks.REGENERATION_REACTOR.get().defaultBlockState());
            blueStar.triggerEscape();
            helper.assertFalse(blueStar.hasEscape(), "blocked main room entered escape with a fallback");
            helper.assertTrue(blueStar.position().equals(origin), "blocked destination moved the entity");

            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    for (int y = hall.minPos().getY() - 1; y <= hall.maxPos().getY(); y++) {
                        changeFixtureBlock(level, blocks, blockEntities, protectedBlocks,
                                new BlockPos(reactor.getX() + dx, y, reactor.getZ() + dz),
                                y < hall.minPos().getY() ? Blocks.STONE.defaultBlockState()
                                        : Blocks.AIR.defaultBlockState());
                    }
                }
            }
            changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, reactor,
                    ModBlocks.REGENERATION_REACTOR.get().defaultBlockState());
            progress.setLevel(EmployeeDepartment.TRAINING, 0);
            blueStar.triggerEscape();
            helper.assertFalse(blueStar.hasEscape(), "locked department allowed escape elsewhere");
            progress.setLevel(EmployeeDepartment.TRAINING, 5);
            // Exercise the production retry, not just the destination helper.
            for (int tick = 0; tick < 40 && !blueStar.hasEscape(); tick++) {
                blueStar.tickCount++;
                blueStar.tick();
            }
            helper.assertTrue(blueStar.hasEscape() && hall.contains(blueStar.blockPosition()),
                    "unblocked destination did not complete pending escape: pos=" + blueStar.blockPosition()
                            + ", ticks=" + blueStar.tickCount + ", home=" + blueStar.getHomeRoomMarker()
                            + ", removed=" + blueStar.isRemoved());
            helper.assertTrue(binding.markerPos().equals(blueStar.getEscapePosition()),
                    "escape origin was lost during a destination retry");
            helper.succeed();
        } finally {
            EscapeTracker.getInstance().onEscapeStop(blueStar);
            blueStar.discard();
            blocks.forEach(level::setBlockAndUpdate);
            blockEntities.forEach((pos, tag) -> {
                if (level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(tag);
            });
            ProtectionHelper.markProtectedBatch(level, protectedBlocks);
            level.getServer().overworld().getDataStorage().set("lobotocraft_department_progress",
                    DepartmentProgressData.load(previousProgress));
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "blue_star_escape_lifecycle")
    public static void triggerEscapePublishesCommonLifecycle(GameTestHelper helper) {
        EntityBlueStar blueStar = ModEntities.blue_star.get().create(helper.getLevel());
        if (blueStar == null) {
            helper.fail("could not create blue star");
            return;
        }

        BlockPos spawn = helper.absolutePos(new BlockPos(4, 1, 4));
        blueStar.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(blueStar);

        EscapeTracker tracker = EscapeTracker.getInstance();
        tracker.resetForDay(helper.getLevel().getServer());
        EscapeEventRecorder recorder = new EscapeEventRecorder(blueStar);
        MinecraftForge.EVENT_BUS.register(recorder);
        try {
            helper.assertTrue(tracker.getCurrentAlertLevel(helper.getLevel().getServer()) == -1,
                    "escape alert baseline was not normal");

            blueStar.triggerEscape();

            helper.assertTrue(blueStar.hasEscape(), "blue star did not enter escaped state");
            helper.assertTrue(recorder.matchingEvents == 1,
                    "blue star escape did not publish exactly one abnormality escape event");
            helper.assertTrue(tracker.getCurrentAlertLevel(helper.getLevel().getServer()) >= 0,
                    "blue star escape was not registered by the facility escape tracker");
            helper.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(recorder);
            blueStar.discard();
            tracker.resetForDay(helper.getLevel().getServer());
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 100, required = true, batch = "blue_star_escape_lifecycle")
    public static void containmentCancelsAllTimersOwnedByOneBlueStar(GameTestHelper helper) {
        EntityBlueStar first = ModEntities.blue_star.get().create(helper.getLevel());
        EntityBlueStar second = ModEntities.blue_star.get().create(helper.getLevel());
        if (first == null || second == null) {
            helper.fail("could not create blue stars");
            return;
        }
        BlockPos firstPos = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos secondPos = helper.absolutePos(new BlockPos(12, 1, 4));
        first.moveTo(firstPos.getX() + 0.5D, firstPos.getY(), firstPos.getZ() + 0.5D, 0.0F, 0.0F);
        second.moveTo(secondPos.getX() + 0.5D, secondPos.getY(), secondPos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(first);
        helper.getLevel().addFreshEntity(second);
        Player target = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "blue_star_timer_target"));
        Player otherTarget = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "blue_star_other_timer_target"));

        try {
            first.triggerEscape();
            second.triggerEscape();
            Method killMob = EntityBlueStar.class.getDeclaredMethod(
                    "killMob", LivingEntity.class, boolean.class);
            killMob.setAccessible(true);
            killMob.invoke(first, target, false);

            Method getEntityTimerEntry = EntityBlueStar.class.getDeclaredMethod("getEntityTimerEntry");
            getEntityTimerEntry.setAccessible(true);
            @SuppressWarnings("unchecked")
            TimerEntry<LivingEntity> firstSecondTimer =
                    (TimerEntry<LivingEntity>) getEntityTimerEntry.invoke(first);
            helper.assertTrue(firstSecondTimer.addSkillTimer(target, 0, 1800, 1),
                    "second timer for the same target could not be started");
            Method trackEscapeTimer = EntityBlueStar.class.getDeclaredMethod(
                    "trackEscapeTimer", TimerEntry.class, LivingEntity.class);
            trackEscapeTimer.setAccessible(true);
            trackEscapeTimer.invoke(first, firstSecondTimer, target);

            @SuppressWarnings("unchecked")
            TimerEntry<LivingEntity> secondTimer =
                    (TimerEntry<LivingEntity>) getEntityTimerEntry.invoke(second);
            helper.assertTrue(secondTimer.addSkillTimer(otherTarget, 0, 1800, 1),
                    "other blue star could not start its timer");
            trackEscapeTimer.invoke(second, secondTimer, otherTarget);

            Field activeTimers = EntityBlueStar.class.getDeclaredField("activeEscapeTimers");
            activeTimers.setAccessible(true);
            helper.assertTrue(((Set<?>) activeTimers.get(first)).size() == 2,
                    "first blue star did not retain both timer handles");
            helper.assertTrue(((Set<?>) activeTimers.get(second)).size() == 1,
                    "second blue star timer was not tracked");

            first.stopEscape();
            helper.assertTrue(((Set<?>) activeTimers.get(first)).isEmpty(),
                    "containment left a timer owned by the first blue star");
            helper.assertTrue(((Set<?>) activeTimers.get(second)).size() == 1,
                    "stopping one blue star cancelled another star's timer");
            second.stopEscape();
            first.discard();
            second.discard();
            target.discard();
            otherTarget.discard();
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            first.discard();
            second.discard();
            target.discard();
            otherTarget.discard();
            helper.fail("could not construct blue star timer fixture: " + exception);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 120, required = true, batch = "blue_star_escape_lifecycle")
    public static void deferredContainmentDoesNotRestartEscapeTimers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) throw new IllegalStateException("facility dimension unavailable");
        EntityBlueStar blueStar = ModEntities.blue_star.get().create(level);
        LivingEntity target = EntityType.ZOMBIE.create(level);
        if (blueStar == null || target == null) {
            helper.fail("could not create deferred containment fixture");
            return;
        }

        BlockPos markerPos = new BlockPos(160, 270, 30);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
        Set<BlockPos> protectedBlocks = new java.util.HashSet<>();
        CompanyRoomData previousRooms = CompanyRoomData.get(level);
        level.getServer().overworld().getDataStorage().set("lobotocraft_company_rooms",
                CompanyRoomData.load(previousRooms.save(new CompoundTag())));
        try {
            // A registered facility escape now requires the department's real destination.
            BlockPos reactor = new BlockPos(195, 272, 42);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int y = 269; y <= 285; y++) {
                        changeFixtureBlock(level, blocks, blockEntities, protectedBlocks,
                                new BlockPos(reactor.getX() + dx, y, reactor.getZ() + dz),
                                y == 269 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
            changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, reactor,
                    ModBlocks.REGENERATION_REACTOR.get().defaultBlockState());
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, markerPos.offset(dx, -1, dz),
                            Blocks.STONE.defaultBlockState());
                    for (int dy = 0; dy <= 5; dy++) {
                        changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, markerPos.offset(dx, dy, dz),
                                Blocks.AIR.defaultBlockState());
                    }
                }
            }
            changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, markerPos,
                    ModBlocks.ROOM_MARKER.get().defaultBlockState());
            blueStar.moveTo(markerPos.getX() + 2.5D, markerPos.getY(),
                    markerPos.getZ() + 0.5D, 0.0F, 0.0F);
            target.moveTo(markerPos.getX() + 42.5D, markerPos.getY(),
                    markerPos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(blueStar);
            level.addFreshEntity(target);
            RoomMarkerBlockEntity marker = (RoomMarkerBlockEntity) level.getBlockEntity(markerPos);
            helper.assertTrue(marker != null && marker.bindByUuid(level, blueStar.getUUID())
                            && AbnormalityWorkRules.isRegistered(blueStar),
                    "deferred fixture requires a real registered containment room");
            blueStar.triggerEscape();
            helper.assertTrue(blueStar.hasEscape(), "fixture did not escape to its main-room reactor");
            blueStar.moveTo(markerPos.getX() + 40.5D, markerPos.getY(),
                    markerPos.getZ() + 0.5D, 0.0F, 0.0F);

            // Keep the authoritative marker, but leave no collision-free return position.
            int radius = marker.getRadiusXZ();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 0; dy <= 5; dy++) {
                        BlockPos pos = markerPos.offset(dx, dy, dz);
                        if (!pos.equals(markerPos)) {
                            changeFixtureBlock(level, blocks, blockEntities, protectedBlocks, pos,
                                    Blocks.BARRIER.defaultBlockState());
                        }
                    }
                }
            }

            Field activeTimers = EntityBlueStar.class.getDeclaredField("activeEscapeTimers");
            activeTimers.setAccessible(true);
            Field pendingReturn = blueStar.getClass().getSuperclass()
                    .getDeclaredField("pendingContainmentReturn");
            pendingReturn.setAccessible(true);
            Method killMob = EntityBlueStar.class.getDeclaredMethod(
                    "killMob", LivingEntity.class, boolean.class);
            killMob.setAccessible(true);
            killMob.invoke(blueStar, target, false);
            helper.assertFalse(((Set<?>) activeTimers.get(blueStar)).isEmpty(),
                    "fixture did not create a timer before containment");
            blueStar.stopEscape();
            helper.assertTrue((boolean) pendingReturn.get(blueStar),
                    "fixture did not enter deferred containment return state");
            helper.assertFalse(blueStar.isRemoved(),
                    "fixture transferred instead of remaining in deferred containment");
            helper.assertTrue(((Set<?>) activeTimers.get(blueStar)).isEmpty(),
                    "stopping escape did not cancel existing timers");

            killMob.invoke(blueStar, target, false);
            helper.assertTrue(((Set<?>) activeTimers.get(blueStar)).isEmpty(),
                    "deferred containment accepted a new direct execution timer");

            for (int tick = 0; tick < 90; tick++) {
                blueStar.tickCount++;
                blueStar.tick();
                helper.assertTrue((boolean) pendingReturn.get(blueStar),
                        "sealed containment unexpectedly completed its pending return");
                helper.assertTrue(((Set<?>) activeTimers.get(blueStar)).isEmpty(),
                        "deferred containment restarted blue star escape timers");
            }
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            helper.fail("could not construct deferred containment fixture: " + exception);
        } finally {
            blueStar.stopEscape();
            EscapeTracker.getInstance().onEscapeStop(blueStar);
            TimerEntry.shutdown(blueStar);
            blueStar.discard();
            target.discard();
            blocks.forEach(level::setBlockAndUpdate);
            blockEntities.forEach((pos, tag) -> {
                if (level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(tag);
            });
            ProtectionHelper.markProtectedBatch(level, protectedBlocks);
            level.getServer().overworld().getDataStorage().set("lobotocraft_company_rooms", previousRooms);
        }
    }

    private static void changeFixtureBlock(ServerLevel level, Map<BlockPos, BlockState> blocks,
                                           Map<BlockPos, CompoundTag> blockEntities,
                                           Set<BlockPos> protectedBlocks, BlockPos pos, BlockState state) {
        if (!blocks.containsKey(pos)) {
            blocks.put(pos.immutable(), level.getBlockState(pos));
            if (ProtectionHelper.isProtected(level, pos)) {
                protectedBlocks.add(pos.immutable());
                ProtectionHelper.removeProtection(level, pos);
            }
            if (level.getBlockEntity(pos) != null) {
                blockEntities.put(pos.immutable(), level.getBlockEntity(pos).saveWithFullMetadata());
            }
        }
        level.setBlockAndUpdate(pos, state);
    }

    private static final class EscapeEventRecorder {
        private final EntityBlueStar expected;
        private int matchingEvents;

        private EscapeEventRecorder(EntityBlueStar expected) {
            this.expected = expected;
        }

        @SubscribeEvent
        public void onEscape(AbnormalityEscapeEvent event) {
            if (event.getAbnormality() == expected) matchingEvents++;
        }
    }
}
