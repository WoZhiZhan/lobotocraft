package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.work.AbnormalityWorkRules;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.structure.ProtectionHelper;
import com.wzz.lobotocraft.util.TimerEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
