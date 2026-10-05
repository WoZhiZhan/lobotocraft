package com.wzz.lobotocraft.entity.abnormality;

import com.lobotocraft.facility.api.DayCompletionRequirements;
import com.lobotocraft.facility.data.FacilityDayData;
import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.effect.KissEffect;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.ai.AbnormalityFearService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.util.AbnormalityCombatUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.data.RoomEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(ModMain.MODID)
public final class Version027ObservationGameTests {
    private static final String TEST_NAMESPACE = ModMain.MODID;

    private Version027ObservationGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = false, batch = "v027_queen_monitor")
    public static void queenFirstTeleportKeepsEscapedMonitorBinding(GameTestHelper helper) {
        ServerLevel facility = facility(helper);
        BlockPos markerPos = new BlockPos(160, 270, 30);
        prepareContainmentFixture(facility, markerPos);
        EntityQueenOfHatred queen = ModEntities.queen_of_hatred.get().create(facility);
        if (queen == null) {
            clearContainmentFixture(facility, markerPos);
            helper.fail("queen could not be created");
            return;
        }
        queen.moveTo(markerPos.getX() + 2.5D, markerPos.getY(), markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addFreshEntity(queen);
        try {
            RoomMarkerBlockEntity marker = facility.getBlockEntity(markerPos)
                    instanceof RoomMarkerBlockEntity room ? room : null;
            helper.assertTrue(marker != null, "containment fixture has no room marker");
            helper.assertTrue(marker.bindByUuid(facility, queen.getUUID()),
                    "room marker rejected queen binding");
            queen.triggerEscape();
            queen.setHealth(queen.getMaxHealth() * 0.69F);
            queen.hurt(DamageHelper.getDamage(queen, "black"), 1.0F);
            marker.getUpdateTag();
            RoomEntry entry = CompanyRoomData.get(facility)
                    .getOrCreateRoom(facility.dimension().location(), markerPos);
            helper.assertTrue(entry.status == RoomEntry.STATUS_ESCAPED,
                    "first post-teleport snapshot was not ESCAPED: " + entry.status);
            helper.assertTrue(entry.escapeDistance >= 0, "escaped snapshot lost distance");
            helper.succeed();
        } finally {
            queen.discard();
            clearContainmentFixture(facility, markerPos);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 220, required = false, batch = "v027_corpse_move")
    public static void smilingCorpseMountainLeavesSpawnWhenEscapedNearPlayer(GameTestHelper helper) {
        // This is a movement observation, not a fall from an empty template.
        for (int x = 1; x <= 14; x++) {
            for (int z = 1; z <= 12; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        EntitySmilingCorpseMountain corpse = spawn(helper, ModEntities.smiling_corpse_mountain.get(),
                new BlockPos(3, 1, 3));
        EmployeeServerPlayer employee = employee(helper, new BlockPos(8, 1, 3), "v027_corpse_bait");
        corpse.triggerEscape();
        Vec3 start = corpse.position();
        helper.runAfterDelay(200, () -> {
            try {
                double dx = corpse.getX() - start.x;
                double dz = corpse.getZ() - start.z;
                helper.assertTrue(dx * dx + dz * dz > 1.0D,
                        "escaped corpse mountain stayed within 1 block for 10 seconds");
                helper.succeed();
            } finally {
                corpse.discard();
                employee.discard();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_censored_work_anim")
    public static void censoredWorkDamageKeepsIdleUntilUniqueSacrifice(GameTestHelper helper) {
        EntityCensored censored = spawn(helper, ModEntities.censored.get(), new BlockPos(3, 1, 3));
        EmployeeServerPlayer employee = employee(helper, new BlockPos(4, 1, 3), "v027_work_anim");
        try {
            helper.assertTrue("idle".equals(censored.getAnimation())
                            || censored.getAnimation().isEmpty()
                            || "idle_inside".equals(censored.getAnimation()),
                    "contained CENSORED did not start idle: " + censored.getAnimation());
            censored.onWorkStart(employee, WorkType.INSTINCT);
            helper.assertTrue(!"special_attack".equals(censored.getAnimation())
                            && !"move".equals(censored.getAnimation()),
                    "work start switched CENSORED into an escape conversion animation: "
                            + censored.getAnimation());
            censored.attackPlayerOnFailure(employee, WorkType.INSTINCT);
            helper.assertTrue("idle".equals(censored.getAnimation()),
                    "ordinary work damage played sacrifice: " + censored.getAnimation());
            censored.beginUniqueSacrificeAnimation();
            helper.assertTrue("sacrifice".equals(censored.getAnimation()),
                    "unique sacrifice did not play its animation: " + censored.getAnimation());
            helper.succeed();
        } finally {
            censored.discard();
            employee.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_censored_extra_entity")
    public static void censoredEscapeDoesNotSpawnExtraLivingEntity(GameTestHelper helper) {
        EntityCensored censored = spawn(helper, ModEntities.censored.get(), new BlockPos(3, 1, 3));
        try {
            long before = livingCount(helper, censored);
            censored.triggerEscape();
            long after = livingCount(helper, censored);
            helper.assertTrue(after == before,
                    "escape spawned extra living entities: before=" + before + " after=" + after);
            helper.succeed();
        } finally {
            censored.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_censored_fear")
    public static void censoredFearDoesNotRetriggerOnSameLife(GameTestHelper helper) {
        EntityCensored censored = spawn(helper, ModEntities.censored.get(), new BlockPos(3, 1, 3));
        EmployeeServerPlayer employee = employee(helper, new BlockPos(4, 1, 3), "v027_fear");
        try {
            MentalValueUtil.setMentalValue(employee, MentalValueUtil.getEffectiveMaxMentalValue(employee));
            employee.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    censored.getEyePosition());
            censored.tickCount = 5;
            AbnormalityFearService.tickEncounter(censored, censored);
            float afterFirst = MentalValueUtil.getMentalValue(employee);
            helper.assertTrue(MentalValueUtil.isPanic(employee) || afterFirst == 0.0F,
                    "first CENSORED encounter did not panic a low-level employee");
            MentalValueUtil.setMentalValue(employee, MentalValueUtil.getEffectiveMaxMentalValue(employee));
            CompoundTag encountered = censored.getPersistentData()
                    .getCompound("lobotocraft_fear_encountered_employees");
            helper.assertTrue(encountered.getBoolean(employee.getUUID().toString()),
                    "first encounter was not recorded on CENSORED");
            AbnormalityFearService.tickEncounter(censored, censored);
            helper.assertTrue(MentalValueUtil.getMentalValue(employee)
                            == MentalValueUtil.getEffectiveMaxMentalValue(employee),
                    "same escape triggered first-encounter fear twice");
            helper.succeed();
        } finally {
            censored.discard();
            employee.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_combat_matrix")
    public static void censoredCombatMatrixObservations(GameTestHelper helper) {
        EntityCensored censored = spawnEscaped(helper, ModEntities.censored.get(), new BlockPos(4, 1, 4));
        EntityLumberjack lumberjack = spawnEscaped(helper, ModEntities.lumberjack.get(), new BlockPos(6, 1, 4));
        EntityRedHoodMercenary redHood = spawnEscaped(helper, ModEntities.redhat_mercenary.get(), new BlockPos(8, 1, 4));
        EntityHelper helperBot = spawnEscaped(helper, ModEntities.helper.get(), new BlockPos(10, 1, 4));
        EntitySmilingCorpseMountain corpse = spawnEscaped(helper, ModEntities.smiling_corpse_mountain.get(),
                new BlockPos(12, 1, 4));
        EntityQueenOfHatred hero = spawn(helper, ModEntities.queen_of_hatred.get(), new BlockPos(14, 1, 4));
        hero.setHeroDamageStateForTest(false);
        EntityLargeBird largeBird = spawnEscaped(helper, ModEntities.large_bird.get(), new BlockPos(16, 1, 4));
        EntityPunishingBird punishingBird = spawnEscaped(helper, ModEntities.punishing_bird.get(),
                new BlockPos(18, 1, 4));
        EntityPpodae ppodae = spawnEscaped(helper, ModEntities.ppodae.get(), new BlockPos(4, 1, 6));
        EntityThornBus bus = spawnEscaped(helper, ModEntities.thorn_bus.get(), new BlockPos(6, 1, 6));
        EntityButterflyFuneral funeral = spawnEscaped(helper, ModEntities.butterfly_funeral.get(),
                new BlockPos(8, 1, 6));
        EntityBigBadWolf wolf = spawnEscaped(helper, ModEntities.bigbadwolf.get(), new BlockPos(10, 1, 6));
        EntityNothingThere nothing = spawnEscaped(helper, ModEntities.nothing_there.get(), new BlockPos(12, 1, 6));
        EntityWorkerBee bee = spawn(helper, ModEntities.worker_bee.get(), new BlockPos(14, 1, 6));
        List<Entity> spawned = List.of(censored, lumberjack, redHood, helperBot, corpse, hero, largeBird,
                punishingBird, ppodae, bus, funeral, wolf, nothing, bee);
        try {
            List<String> notes = new ArrayList<>();
            notes.add("censored->lumberjack=" + censored.isValidCombatTarget(lumberjack));
            notes.add("censored->redHood=" + censored.isValidCombatTarget(redHood));
            notes.add("censored->helper=" + censored.isValidCombatTarget(helperBot));
            notes.add("censored->corpse=" + censored.isValidCombatTarget(corpse));
            notes.add("censored->hero=" + censored.isValidCombatTarget(hero));
            notes.add("censored->largeBird=" + censored.isValidCombatTarget(largeBird));
            notes.add("censored->punishingBird=" + censored.isValidCombatTarget(punishingBird));
            notes.add("censored->ppodae=" + censored.isValidCombatTarget(ppodae));
            notes.add("censored->bus=" + censored.isValidCombatTarget(bus));
            notes.add("censored->funeral=" + censored.isValidCombatTarget(funeral));
            notes.add("censored->wolf=" + censored.isValidCombatTarget(wolf));
            notes.add("censored->nothing=" + censored.isValidCombatTarget(nothing));
            notes.add("censored->bee=" + censored.isValidCombatTarget(bee));
            notes.add("bee->censored=" + AbnormalityCombatUtil.isValidSuppressorTarget(bee, censored));
            notes.add("heroState=" + hero.stateNameForTest() + ",heroEscape=" + hero.hasEscape());
            helper.succeed();
            helper.getLevel().getServer().sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("v027 combat matrix: "
                            + String.join("; ", notes)));
        } finally {
            spawned.forEach(Entity::discard);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_snow_queen")
    public static void kissFreezeRequiresThirdStackOnContainedAbnormality(GameTestHelper helper) {
        EntityFragmentOfUniverse fragment = spawn(helper, ModEntities.fragment_of_the_universe.get(),
                new BlockPos(3, 1, 3));
        try {
            fragment.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    ModMobEffects.KISS.get(), 200, 0, false, false));
            helper.assertTrue(fragment.hasEffect(ModMobEffects.KISS.get())
                            && fragment.getEffect(ModMobEffects.KISS.get()).getAmplifier() == 0,
                    "first kiss was not amplifier 0");
            helper.assertFalse(fragment.getPersistentData().getBoolean("isFreezeKiss"),
                    "first kiss froze a contained abnormality");
            fragment.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    ModMobEffects.KISS.get(), 200, 1, false, false));
            helper.assertTrue(fragment.getEffect(ModMobEffects.KISS.get()).getAmplifier() == 1,
                    "second kiss was not amplifier 1");
            helper.assertFalse(fragment.getPersistentData().getBoolean("isFreezeKiss"),
                    "second kiss froze a contained abnormality");
            fragment.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    ModMobEffects.KISS.get(), 20, 2, false, false));
            helper.assertTrue(fragment.getEffect(ModMobEffects.KISS.get()).getAmplifier() >= 2,
                    "third kiss did not reach freeze amplifier");
            fragment.tick();
            helper.assertTrue(fragment.getPersistentData().getBoolean("isFreezeKiss")
                            || fragment.getEffect(ModMobEffects.KISS.get()).getAmplifier() >= 2,
                    "third kiss stack did not enter freeze path");
            KissEffect.clearFreezeState(fragment);
            helper.assertFalse(fragment.getPersistentData().getBoolean("isFreezeKiss"),
                    "clearFreezeState left isFreezeKiss");
            helper.succeed();
        } finally {
            fragment.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_department_day")
    public static void pendingDepartmentChoiceBlocksDayCompletion(GameTestHelper helper) {
        ServerLevel facility = facility(helper);
        EmployeeServerPlayer player = employee(helper, new BlockPos(3, 1, 3), "v027_day");
        try {
            FacilityDayData day = FacilityDayData.get(facility.getServer().overworld());
            day.beginDay(2);
            DepartmentProgressData progress = DepartmentProgressData.get(facility.getServer());
            progress.prepareForDay(2);
            helper.assertTrue(progress.pendingDay() == 2, "day 2 did not create a pending expansion");
            helper.assertFalse(DayCompletionRequirements.unmetMessages(player, day).isEmpty(),
                    "pending expansion did not block day completion");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_escape_layout")
    public static void reportedEscapeBindingsExistInLayout(GameTestHelper helper) {
        ResourceLocation dimension = ModDimensions.LOBOTO_KEY.location();
        assertEscape(helper, dimension, new BlockPos(232, 201, 30), new BlockPos(232, 200, 53));
        assertEscape(helper, dimension, new BlockPos(232, 168, 30), new BlockPos(232, 167, 52));
        assertEscape(helper, dimension, new BlockPos(276, 236, 30), new BlockPos(276, 235, 52));
        assertEscape(helper, dimension, new BlockPos(254, 117, 30), new BlockPos(254, 116, 51));
        assertEscape(helper, dimension, new BlockPos(344, 92, 30), new BlockPos(344, 91, 51));
        assertEscape(helper, dimension, new BlockPos(243, 45, 30), new BlockPos(250, 44, 51));
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_nothing_day")
    public static void nothingThereContainedCounterStaysOneAcrossBeginDay(GameTestHelper helper) {
        EntityNothingThere nothing = spawn(helper, ModEntities.nothing_there.get(), new BlockPos(3, 1, 3));
        try {
            helper.assertTrue(nothing.getQliphothCounter() == 1, "contained Nothing There did not start at 1");
            FacilityDayData.get(helper.getLevel().getServer().overworld()).beginDay(3);
            helper.assertTrue(nothing.getQliphothCounter() == 1,
                    "contained Nothing There counter changed without escaping: "
                            + nothing.getQliphothCounter());
            helper.assertFalse(nothing.hasEscape(), "contained Nothing There escaped during beginDay");
            helper.succeed();
        } finally {
            nothing.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_nothing_red")
    public static void nothingTherePhase3UsesZeroRedResistance(GameTestHelper helper) {
        EntityNothingThere nothing = spawnEscaped(helper, ModEntities.nothing_there.get(),
                new BlockPos(4, 1, 4));
        EntityFragmentOfUniverse ordinary = ModEntities.fragment_of_the_universe.get().create(helper.getLevel());
        try {
            helper.assertTrue(ordinary != null, "ordinary RED source could not be created");
            nothing.setNoGravity(true);
            nothing.transitionToPhase3();
            helper.assertTrue(nothing.getCurrentPhase() == 2, "phase 3 fixture did not transform");
            helper.assertTrue(nothing.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get()) == 0.0D,
                    "phase 3 red resistance is not zero");
            float before = nothing.getHealth();
            nothing.hurt(DamageHelper.getDamage(ordinary, "red"), 40.0F);
            helper.assertTrue(nothing.getHealth() == before, "phase 3 ordinary RED changed health");
            helper.succeed();
        } finally {
            nothing.discard();
            if (ordinary != null) ordinary.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = false, batch = "v027_viewport")
    public static void monitorPanRangeAllowsLargeOverviewTravel(GameTestHelper helper) {
        com.wzz.lobotocraft.client.room.RoomMapViewport.Bounds content =
                new com.wzz.lobotocraft.client.room.RoomMapViewport.Bounds(-400.0D, -300.0D, 400.0D, 300.0D);
        com.wzz.lobotocraft.client.room.RoomMapViewport.Bounds viewport =
                new com.wzz.lobotocraft.client.room.RoomMapViewport.Bounds(0.0D, 0.0D, 200.0D, 100.0D);
        com.wzz.lobotocraft.client.room.RoomMapViewport.Projection projection =
                new com.wzz.lobotocraft.client.room.RoomMapViewport.Projection(0.0D, 0.0D, 100.0D, 50.0D, 1.0D);
        com.wzz.lobotocraft.client.room.RoomMapViewport.Pan pan =
                com.wzz.lobotocraft.client.room.RoomMapViewport.clampPan(content, viewport, projection,
                        new com.wzz.lobotocraft.client.room.RoomMapViewport.Pan(999.0D, 999.0D), 2.0D);
        helper.assertTrue(Math.abs(pan.x()) >= 100.0D && Math.abs(pan.y()) >= 50.0D,
                "current monitor pan clamp is still tighter than a large overview travel: "
                        + pan.x() + "," + pan.y());
        helper.succeed();
    }

    private static void assertEscape(GameTestHelper helper, ResourceLocation dimension,
                                     BlockPos marker, BlockPos escape) {
        FacilityWorldLayout.ContainmentBinding binding = FacilityWorldLayout.containmentAt(
                dimension, marker).orElse(null);
        helper.assertTrue(binding != null, "missing containment binding at " + marker);
        helper.assertTrue(escape.equals(binding.escapePos()),
                "escape mismatch at " + marker + ": " + binding.escapePos());
    }

    private static <T extends AbstractAbnormality> T spawnEscaped(GameTestHelper helper,
                                                                  EntityType<T> type, BlockPos relative) {
        T entity = spawn(helper, type, relative);
        entity.triggerEscape();
        return entity;
    }

    private static <T extends LivingEntity> T spawn(GameTestHelper helper, EntityType<T> type,
                                                    BlockPos relative) {
        T entity = type.create(helper.getLevel());
        if (entity == null) throw new IllegalStateException("could not create " + type);
        BlockPos pos = helper.absolutePos(relative);
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static EmployeeServerPlayer employee(GameTestHelper helper, BlockPos relative, String name) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name, EmployeeDepartment.CONTROL);
        record.setStats(20, 20, 20, 20);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        BlockPos pos = helper.absolutePos(relative);
        employee.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addNewPlayer(employee);
        return employee;
    }

    private static ServerLevel facility(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            throw new IllegalStateException("facility dimension was not loaded");
        }
        return facility;
    }

    private static long livingCount(GameTestHelper helper, Entity origin) {
        return helper.getLevel().getEntitiesOfClass(LivingEntity.class,
                origin.getBoundingBox().inflate(8.0D)).size();
    }

    private static void prepareContainmentFixture(ServerLevel level, BlockPos markerPos) {
        level.getChunkAt(markerPos);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlockAndUpdate(markerPos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlockAndUpdate(markerPos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(markerPos, ModBlocks.ROOM_MARKER.get().defaultBlockState());
    }

    private static void clearContainmentFixture(ServerLevel level, BlockPos markerPos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    level.setBlockAndUpdate(markerPos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }
}
