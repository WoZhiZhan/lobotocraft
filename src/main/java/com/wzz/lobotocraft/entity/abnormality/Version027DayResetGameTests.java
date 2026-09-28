package com.wzz.lobotocraft.entity.abnormality;

import com.lobotocraft.facility.FacilityManager;
import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.work.AbnormalityWorkRules;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.OrdealData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.gametest.GameTestHolder;

/** Run only lobotocraft_v027_day: beginDay intentionally visits every server dimension. */
@GameTestHolder(Version027DayResetGameTests.TEST_NAMESPACE)
public final class Version027DayResetGameTests {
    static final String TEST_NAMESPACE = "lobotocraft_v027_day";
    private static final String BATCH = "v027_day_reset_isolated";

    private Version027DayResetGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void loadedContainedPpodaeResetsBeforeBeginDayReturns(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityPpodae ppodae = fixture.spawn(ModEntities.ppodae.get());
            ppodae.decreaseQliphothCounter(1);
            helper.assertTrue(ppodae.getQliphothCounter() == 1 && !ppodae.hasEscape(),
                    "fixture must be depleted but still contained");
            FacilityManager.beginDay(fixture.level, 2);
            assertCounter(helper, ppodae, ppodae.getMaxQliphothCounter(), "loaded contained ppodae");
            helper.assertFalse(ppodae.hasEscape(), "day reset made contained ppodae escape");
            ppodae.decreaseQliphothCounter(1);
            tick(ppodae);
            assertCounter(helper, ppodae, 1, "first tick after beginDay must not reset twice");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void loadedContainedCensoredResetsBeforeBeginDayReturns(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityCensored censored = fixture.spawn(ModEntities.censored.get());
            censored.decreaseQliphothCounter(1);
            helper.assertTrue(censored.getQliphothCounter() == 1 && !censored.hasEscape(),
                    "fixture must be depleted but still contained");
            FacilityManager.beginDay(fixture.level, 2);
            assertCounter(helper, censored, censored.getMaxQliphothCounter(), "loaded contained CENSORED");
            helper.assertFalse(censored.hasEscape(), "day reset made contained CENSORED escape");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void nonEscapableOldWomanResetsFromZero(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityLadyFacingTheWall oldWoman = fixture.spawn(ModEntities.the_lady_facing_the_wall.get());
            oldWoman.decreaseQliphothCounter(oldWoman.getMaxQliphothCounter());
            helper.assertFalse(oldWoman.canEscape() || oldWoman.hasEscape(),
                    "old woman must remain non-escapable at zero");
            assertCounter(helper, oldWoman, 0, "old woman fixture");
            FacilityManager.beginDay(fixture.level, 2);
            assertCounter(helper, oldWoman, 4, "non-escapable old woman at day start");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void loadedEscapeReturnsAndCommitsDayOnlyOnce(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityPpodae ppodae = fixture.spawn(ModEntities.ppodae.get());
            ppodae.decreaseQliphothCounter(ppodae.getMaxQliphothCounter());
            helper.assertTrue(ppodae.hasEscape(), "zero did not trigger a real ppodae escape");
            FacilityManager.beginDay(fixture.level, 2);
            fixture.assertReturned(ppodae);
            assertCounter(helper, ppodae, 2, "loaded escaped ppodae after return");
            helper.assertTrue(save(ppodae).getInt("LastFacilityDaySeen") == 2,
                    "successful day recovery did not commit the day before beginDay returned");
            ppodae.decreaseQliphothCounter(1);
            tick(ppodae);
            assertCounter(helper, ppodae, 1, "first post-return tick must not refill again");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void unloadedContainedEntityCatchesUpThenSameDayReloadKeepsDepletion(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityPpodae original = fixture.spawn(ModEntities.ppodae.get());
            original.decreaseQliphothCounter(1);
            CompoundTag previousDay = fixture.unload(original);
            FacilityManager.beginDay(fixture.level, 2);
            EntityPpodae reloaded = fixture.reload(ModEntities.ppodae.get(), previousDay);
            assertCounter(helper, reloaded, 1, "NBT must preserve depletion until catch-up tick");
            tick(reloaded);
            assertCounter(helper, reloaded, 2, "unloaded contained entity missed day reset");
            helper.assertFalse(reloaded.hasEscape(), "contained catch-up triggered escape");
            reloaded.decreaseQliphothCounter(1);
            CompoundTag sameDay = fixture.unload(reloaded);
            helper.assertTrue(sameDay.getInt("LastFacilityDaySeen") == 2,
                    "catch-up completion was not persisted");
            EntityPpodae secondReload = fixture.reload(ModEntities.ppodae.get(), sameDay);
            tick(secondReload);
            assertCounter(helper, secondReload, 1, "same-day second reload refilled the counter");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void sameDayReloadAndRepeatedBeginDayDoNotRefill(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityPpodae original = fixture.spawn(ModEntities.ppodae.get());
            original.decreaseQliphothCounter(1);
            EntityPpodae reloaded = fixture.reload(ModEntities.ppodae.get(), fixture.unload(original));
            tick(reloaded);
            FacilityManager.beginDay(fixture.level, 1);
            tick(reloaded);
            assertCounter(helper, reloaded, 1, "same-day load or beginDay refilled the counter");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void unloadedEscapeReturnsOnReloadTick(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityPpodae original = fixture.spawn(ModEntities.ppodae.get());
            original.decreaseQliphothCounter(original.getMaxQliphothCounter());
            helper.assertTrue(original.hasEscape(), "fixture did not escape");
            CompoundTag previousDay = fixture.unload(original);
            FacilityManager.beginDay(fixture.level, 2);
            EntityPpodae reloaded = fixture.reload(ModEntities.ppodae.get(), previousDay);
            helper.assertTrue(reloaded.hasEscape(), "NBT reload lost the escaped state");
            tick(reloaded);
            fixture.assertReturned(reloaded);
            assertCounter(helper, reloaded, 2, "unloaded escaped entity did not recover its counter");
            helper.assertTrue(save(reloaded).getInt("LastFacilityDaySeen") == 2,
                    "reload recovery did not persist its completed day");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void galaxyBoyResetsHighCounterToOneNotMaximum(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityChildrenGalaxy galaxy = fixture.spawn(ModEntities.children_galaxy.get());
            galaxy.increaseQliphothCounter(4);
            assertCounter(helper, galaxy, 5, "galaxy fixture must distinguish one from max");
            FacilityManager.beginDay(fixture.level, 2);
            assertCounter(helper, galaxy, 1, "GalaxyBoy OnStageStart override");
            helper.assertTrue(galaxy.getMaxQliphothCounter() == 5, "day reset changed galaxy maximum");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void galaxyBoyResetsZeroCounterToOne(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityChildrenGalaxy galaxy = fixture.spawn(ModEntities.children_galaxy.get());
            galaxy.decreaseQliphothCounter(1);
            assertCounter(helper, galaxy, 0, "galaxy zero fixture");
            FacilityManager.beginDay(fixture.level, 2);
            assertCounter(helper, galaxy, 1, "depleted GalaxyBoy OnStageStart override");
            helper.assertFalse(galaxy.hasEscape(), "galaxy should not escape during reset");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void unloadedGalaxyBoyUsesOverrideWithoutResettingSameDayReload(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityChildrenGalaxy original = fixture.spawn(ModEntities.children_galaxy.get());
            original.increaseQliphothCounter(4);
            CompoundTag previousDay = fixture.unload(original);
            FacilityManager.beginDay(fixture.level, 2);
            EntityChildrenGalaxy galaxy = fixture.reload(ModEntities.children_galaxy.get(), previousDay);
            tick(galaxy);
            assertCounter(helper, galaxy, 1, "unloaded galaxy day catch-up must use subtype override");
            galaxy.increaseQliphothCounter(2);
            EntityChildrenGalaxy sameDay = fixture.reload(ModEntities.children_galaxy.get(), fixture.unload(galaxy));
            tick(sameDay);
            assertCounter(helper, sameDay, 3, "same-day galaxy reload must preserve earned counter");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void containedLumberjackDayStartClearsArmedSacrifice(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityLumberjack lumberjack = fixture.spawn(ModEntities.lumberjack.get());
            lumberjack.decreaseQliphothCounter(1);
            helper.assertTrue(save(lumberjack).getBoolean("LumberjackSacrificeArmed")
                            && !lumberjack.hasEscape(),
                    "real zero-counter transition must arm sacrifice without escaping");
            FacilityManager.beginDay(fixture.level, 2);
            helper.assertFalse(save(lumberjack).getBoolean("LumberjackSacrificeArmed"),
                    "day start left a contained woodsman armed to sacrifice the next employee");
            assertCounter(helper, lumberjack, 1, "contained woodsman day start");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void unloadedLumberjackCatchUpClearsPersistedArmedSacrifice(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityLumberjack original = fixture.spawn(ModEntities.lumberjack.get());
            original.decreaseQliphothCounter(1);
            CompoundTag previousDay = fixture.unload(original);
            helper.assertTrue(previousDay.getBoolean("LumberjackSacrificeArmed"),
                    "fixture did not persist an armed sacrifice");
            FacilityManager.beginDay(fixture.level, 2);
            EntityLumberjack lumberjack = fixture.reload(ModEntities.lumberjack.get(), previousDay);
            tick(lumberjack);
            helper.assertFalse(save(lumberjack).getBoolean("LumberjackSacrificeArmed"),
                    "reload catch-up restored the counter but left the persisted sacrifice armed");
            assertCounter(helper, lumberjack, 1, "unloaded woodsman day start");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void deferredReturnPersistsPendingAndRetriesBeforeCommittingDay(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityPpodae original = fixture.spawn(ModEntities.ppodae.get());
            original.decreaseQliphothCounter(original.getMaxQliphothCounter());
            original.moveTo(fixture.markerPos.getX() + 24.5D, fixture.markerPos.getY(),
                    fixture.markerPos.getZ() + 0.5D, 0.0F, 0.0F);
            helper.assertTrue(original.hasEscape() && save(original).getBoolean("EscapedFromRegisteredContainment"),
                    "deferred fixture must enter the registered containment-return path");
            fixture.blockReturn(true);
            helper.assertTrue(AbnormalityWorkRules.isRegistered(original),
                    "blocking the room must not destroy its authoritative binding");
            FacilityManager.beginDay(fixture.level, 2);
            tick(original);
            CompoundTag pending = fixture.unload(original);
            helper.assertTrue(pending.getBoolean("PendingContainmentReturn") && pending.getBoolean("HasEscaped"),
                    "blocked real containment return did not persist pending escape recovery");
            helper.assertTrue(pending.getInt("QliphothCounter") == 0,
                    "day reset refilled the counter before containment return succeeded");
            EntityPpodae reloaded = fixture.reload(ModEntities.ppodae.get(), pending);
            for (int i = 0; i < 21; i++) tick(reloaded);
            helper.assertTrue(save(reloaded).getBoolean("PendingContainmentReturn"),
                    "blocked reload lost its pending retry transaction");
            fixture.blockReturn(false);
            for (int i = 0; i < 21; i++) tick(reloaded);
            fixture.assertReturned(reloaded);
            assertCounter(helper, reloaded, 2, "deferred recovery after reopening room");
            helper.assertTrue(pending.getInt("LastFacilityDaySeen") == 1,
                    "deferred day reset was marked complete before return succeeded; reload can lose reset");
            helper.assertTrue(save(reloaded).getInt("LastFacilityDaySeen") == 2,
                    "successful deferred recovery did not commit the day");
            reloaded.decreaseQliphothCounter(1);
            tick(reloaded);
            assertCounter(helper, reloaded, 1, "deferred recovery ran day reset more than once");
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = BATCH)
    public static void crossDimensionReturnCommitsDayOnReplacementNotRemovedSource(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            EntityPpodae original = fixture.spawn(ModEntities.ppodae.get());
            original.decreaseQliphothCounter(original.getMaxQliphothCounter());
            BlockPos outside = helper.absolutePos(new BlockPos(4, 1, 4));
            Entity moved = original.changeDimension(helper.getLevel(), new ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel source, ServerLevel destination,
                                          float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                    Entity replacement = repositionEntity.apply(false);
                    if (replacement != null) {
                        replacement.moveTo(outside.getX() + 0.5D, outside.getY(), outside.getZ() + 0.5D,
                                0.0F, 0.0F);
                    }
                    return replacement;
                }
            });
            helper.assertTrue(moved instanceof EntityPpodae && moved.level() == helper.getLevel(),
                    "fixture did not genuinely transfer the escaped entity out of its home dimension");
            fixture.entities.add(moved);
            UUID id = moved.getUUID();
            FacilityManager.beginDay(helper.getLevel(), 2);
            helper.assertTrue(moved.isRemoved(), "day recovery did not remove the cross-dimension source");
            Entity returned = fixture.level.getEntity(id);
            helper.assertTrue(returned instanceof EntityPpodae && returned != moved,
                    "day recovery did not produce the authoritative replacement in the home dimension");
            EntityPpodae ppodae = (EntityPpodae) returned;
            fixture.entities.add(ppodae);
            fixture.assertReturned(ppodae);
            assertCounter(helper, ppodae, 2, "cross-dimension recovered counter");
            helper.assertTrue(save(ppodae).getInt("LastFacilityDaySeen") == 2,
                    "replacement retained the old day after cross-dimension recovery");
            ppodae.decreaseQliphothCounter(1);
            tick(ppodae);
            assertCounter(helper, ppodae, 1, "replacement first tick must not apply day reset twice");
        }
        helper.succeed();
    }

    private static void assertCounter(GameTestHelper helper, AbstractAbnormality entity, int expected, String context) {
        helper.assertTrue(entity.getQliphothCounter() == expected,
                context + ": expected=" + expected + ", actual=" + entity.getQliphothCounter());
    }

    private static CompoundTag save(AbstractAbnormality entity) {
        CompoundTag tag = new CompoundTag();
        if (!entity.save(tag)) throw new IllegalStateException("could not save " + entity.getType());
        return tag;
    }

    private static void tick(AbstractAbnormality entity) {
        // ServerLevel normally advances age before tick; preserve the 20-tick deferred retry cadence.
        entity.tickCount++;
        entity.tick();
    }

    /** No scheduled callbacks: each test restores global data before another test can execute. */
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos markerPos = new BlockPos(160, 270, 30);
        private final Map<String, SavedData> originalData = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        private final Map<BlockPos, CompoundTag> originalBlockEntities = new LinkedHashMap<>();
        private final List<Entity> entities = new ArrayList<>();
        private boolean roomPrepared;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            if (level == null) throw new IllegalStateException("facility dimension unavailable");
            // Park the actual objects, not just Day. beginDay also mutates schedules, ordeals and expansion.
            originalData.put("lobotocraft_facility_day", FacilityDayData.get(level));
            originalData.put("lobotocraft_department_progress", DepartmentProgressData.get(level.getServer()));
            originalData.put("lobotocraft_ordeal_data", OrdealData.get(level));
            originalData.put("lobotocraft_company_rooms", CompanyRoomData.get(level));
            var storage = level.getServer().overworld().getDataStorage();
            storage.set("lobotocraft_facility_day", new FacilityDayData());
            storage.set("lobotocraft_department_progress", new DepartmentProgressData());
            storage.set("lobotocraft_ordeal_data", new OrdealData());
            storage.set("lobotocraft_company_rooms", new CompanyRoomData());
            EscapeTracker.getInstance().resetForDay(level.getServer());
        }

        private <T extends AbstractAbnormality> T spawn(EntityType<T> type) {
            if (!roomPrepared) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        setBlock(markerPos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                        for (int dy = 0; dy <= 4; dy++) {
                            setBlock(markerPos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                setBlock(markerPos, ModBlocks.ROOM_MARKER.get().defaultBlockState());
                roomPrepared = true;
            }
            T entity = type.create(level);
            if (entity == null) throw new IllegalStateException("could not create " + type);
            entities.add(entity);
            entity.moveTo(markerPos.getX() + 2.5D, markerPos.getY(), markerPos.getZ() + 0.5D, 0.0F, 0.0F);
            entity.setNoAi(true);
            helper.assertTrue(level.addFreshEntity(entity), "fixture entity was not added to the level");
            RoomMarkerBlockEntity marker = (RoomMarkerBlockEntity) level.getBlockEntity(markerPos);
            helper.assertTrue(marker != null && marker.bindByUuid(level, entity.getUUID()),
                    "fixture could not register a real containment binding");
            helper.assertTrue(AbnormalityWorkRules.isRegistered(entity), "fixture is not registered");
            tick(entity);
            return entity;
        }

        private CompoundTag unload(AbstractAbnormality entity) {
            // Simulates the entity persistence boundary, NOT actual chunk-ticket eviction/unloading.
            CompoundTag tag = save(entity);
            entity.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(entity.isRemoved() && level.getEntity(entity.getUUID()) == null,
                    "saved entity remained visible to beginDay's loaded-entity scan");
            return tag;
        }

        private <T extends AbstractAbnormality> T reload(EntityType<T> type, CompoundTag tag) {
            T entity = type.create(level);
            if (entity == null) throw new IllegalStateException("could not recreate " + type);
            entities.add(entity);
            entity.load(tag);
            helper.assertTrue(level.addFreshEntity(entity), "NBT reload was rejected by the entity manager");
            helper.assertTrue(entity.getUUID().equals(tag.getUUID("UUID")), "reload changed persistent identity");
            return entity;
        }

        private void blockReturn(boolean blocked) {
            RoomMarkerBlockEntity marker = (RoomMarkerBlockEntity) level.getBlockEntity(markerPos);
            // The marker has no collision: a contained ppodae (0.9 blocks tall) fits in its voxel.
            // Keep the binding but remove that lone landing spot's support while the room is sealed.
            setBlock(markerPos.below(), (blocked ? Blocks.AIR : Blocks.STONE).defaultBlockState());
            int radius = marker.getRadiusXZ();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 0; dy <= 5; dy++) {
                        BlockPos pos = markerPos.offset(dx, dy, dz);
                        if (!pos.equals(markerPos)) {
                            setBlock(pos, (blocked ? Blocks.BARRIER : Blocks.AIR).defaultBlockState());
                        }
                    }
                }
            }
        }

        private void assertReturned(AbstractAbnormality entity) {
            helper.assertTrue(entity.isAlive() && !entity.isRemoved() && !entity.hasEscape(),
                    "entity did not finish containment return alive");
            helper.assertFalse(save(entity).getBoolean("PendingContainmentReturn"),
                    "successful return left a pending transaction");
            RoomMarkerBlockEntity marker = (RoomMarkerBlockEntity) level.getBlockEntity(markerPos);
            helper.assertTrue(entity.level() == level && marker.getScanBox().contains(entity.position())
                            && entity.getUUID().equals(marker.getBoundAbnormality()),
                    "returned entity lost its authoritative home-room placement or binding");
        }

        private void setBlock(BlockPos pos, BlockState state) {
            if (!originalBlocks.containsKey(pos)) {
                originalBlocks.put(pos.immutable(), level.getBlockState(pos));
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) originalBlockEntities.put(pos.immutable(), blockEntity.saveWithFullMetadata());
            }
            level.setBlockAndUpdate(pos, state);
        }

        @Override
        public void close() {
            try {
                // A return may replace its source before an assertion fails; clean every tracked UUID in all levels.
                for (Entity entity : entities) {
                    for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
                        Entity live = serverLevel.getEntity(entity.getUUID());
                        if (live != null) live.discard();
                    }
                    if (!entity.isRemoved()) entity.discard();
                }
                originalBlocks.forEach(level::setBlockAndUpdate);
                originalBlockEntities.forEach((pos, tag) -> {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        blockEntity.load(tag);
                        blockEntity.setChanged();
                    }
                });
                EscapeTracker.getInstance().resetForDay(level.getServer());
            } finally {
                originalData.forEach(level.getServer().overworld().getDataStorage()::set);
            }
        }
    }
}
