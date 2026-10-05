package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.employee.*;
import com.wzz.lobotocraft.entity.data.AbnormalityEncyclopediaData;
import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.*;

@GameTestHolder(WolfHoodTeddyRegressionGameTests.NAMESPACE)
public final class WolfHoodTeddyRegressionGameTests {
    static final String NAMESPACE = "lobotocraft_wolf_hood_teddy";
    private static final BlockPos HOME = new BlockPos(160, 270, 30);
    private static final BlockPos PASSAGE = new BlockPos(160, 270, 52);

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void entranceDetectsFullHealthBleedingPersonFarFromWolfOnlyOnce(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.boundWolf();
            var person = f.facilityMob(ModEntities.clerk.get(), PASSAGE);
            f.bleeding(person);
            f.poll(wolf);
            helper.assertTrue(wolf.getQliphothCounter() == 1,
                    "entrance passage ignored because person is >16 blocks away or healed to full");
            f.poll(wolf);
            helper.assertTrue(wolf.getQliphothCounter() == 1, "same bleeding person counted twice");
            helper.assertTrue(person.getPersistentData().getLong("lobotocraft_redhat_wounded") > f.facility.getGameTime(),
                    "wolf consumes globally owned bleed marker");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void containmentAndDifferentFloorAreNotEntrancePassage(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.boundWolf();
            var inRoom = f.facilityMob(ModEntities.clerk.get(), HOME.offset(2, 0, 2));
            inRoom.setHealth(inRoom.getMaxHealth() - 1);
            f.bleeding(inRoom);
            var otherFloor = f.facilityMob(ModEntities.clerk.get(), PASSAGE.above(4));
            f.bleeding(otherFloor);
            f.poll(wolf);
            helper.assertTrue(wolf.getQliphothCounter() == 2 && !wolf.hasEscape(),
                    "radius/vertical cuboid leaks containment or another floor into passage");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void workDefersPassageDebtUntilSessionEnds(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.boundWolf();
            var person = f.facilityMob(ModEntities.clerk.get(), PASSAGE);
            f.bleeding(person);
            var employee = f.player(4);
            var works = activeWorks();
            works.put(employee.getUUID(), new WorkManager.WorkSession(employee, wolf, WorkType.INSIGHT, false));
            try {
                f.poll(wolf);
                helper.assertTrue(wolf.getQliphothCounter() == 2, "working wolf deducts passage debt immediately");
            } finally { works.remove(employee.getUUID()); }
            f.poll(wolf);
            helper.assertTrue(wolf.getQliphothCounter() == 1, "deferred passage debt lost after work ends");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void redHoodEntrySetsTwoDebtNotInstantEscapeDuringWork(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.boundWolf();
            var hood = f.facilityMob(ModEntities.redhat_mercenary.get(), PASSAGE);
            hood.setEscape(true);
            var employee = f.player(4);
            var works = activeWorks();
            works.put(employee.getUUID(), new WorkManager.WorkSession(employee, wolf, WorkType.INSIGHT, false));
            try {
                f.poll(wolf);
                f.poll(wolf);
                helper.assertTrue(!wolf.hasEscape() && wolf.getQliphothCounter() == 2,
                        "hood entry forces escape through an active work session");
            } finally { works.remove(employee.getUUID()); }
            f.poll(wolf);
            helper.assertTrue(wolf.hasEscape() && wolf.getQliphothCounter() == 0,
                    "hood entry debt does not trigger normal zero-counter escape");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void bleedConsumptionIsPerWolfAndSurvivesSaveUntilReset(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var first = f.boundWolf();
            var second = f.boundWolf();
            var person = f.facilityMob(ModEntities.clerk.get(), PASSAGE);
            person.setHealth(person.getMaxHealth() - 1);
            f.bleeding(person);
            f.poll(first); f.poll(second);
            helper.assertTrue(first.getQliphothCounter() == 1 && second.getQliphothCounter() == 1,
                    "first wolf steals the second wolf's bleed observation");
            var saved = new CompoundTag();
            first.addAdditionalSaveData(saved);
            first.readAdditionalSaveData(saved);
            f.poll(first);
            helper.assertTrue(first.getQliphothCounter() == 1, "reload forgets checked person");
            var reset = com.wzz.lobotocraft.entity.base.AbstractAbnormality.class.getDeclaredMethod("resetForNewDay");
            reset.setAccessible(true); reset.invoke(first);
            f.poll(first);
            helper.assertTrue(first.getQliphothCounter() == 1, "new cycle fails to clear checked-person memory");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void pendingWorkDebtPersistsEvenWhenBleedingPersonLeavesBeforeSettlement(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.boundWolf();
            var person = f.facilityMob(ModEntities.clerk.get(), PASSAGE);
            f.bleeding(person);
            var worker = f.player(4);
            var works = activeWorks();
            works.put(worker.getUUID(), new WorkManager.WorkSession(worker, wolf, WorkType.INSIGHT, false));
            try {
                f.poll(wolf);
                var saved = new CompoundTag();
                wolf.addAdditionalSaveData(saved);
                wolf.readAdditionalSaveData(saved);
                person.discard();
            } finally { works.remove(worker.getUUID()); }
            f.poll(wolf);
            helper.assertTrue(wolf.getQliphothCounter() == 1,
                    "reload/departure erased already observed work-deferred debt");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void wolfLegacyInjectedPaleTwoMigratesWithoutErasingStampedCustomTuning(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.mob(ModEntities.bigbadwolf.get(), 8);
            var pale = wolf.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
            pale.setBaseValue(2.0);
            var modifier = new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUID.randomUUID(),
                    "legacy-pale-probe",0.1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
            pale.addPermanentModifier(modifier);
            var legacy = new CompoundTag();
            wolf.addAdditionalSaveData(legacy);
            legacy.remove("WolfPaleResistanceVersion");
            wolf.readAdditionalSaveData(legacy);
            helper.assertTrue(pale.getBaseValue() == 1.0 && pale.getModifier(modifier.getId()) != null,
                    "legacy injected PALE default or modifier did not migrate correctly");
            pale.setBaseValue(2.0);
            var custom = new CompoundTag();
            wolf.addAdditionalSaveData(custom);
            wolf.readAdditionalSaveData(custom);
            helper.assertTrue(pale.getBaseValue() == 2.0, "stamped custom PALE tuning overwritten");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void finalRedHoodHitNeverTriggersHerOwnRampage(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.mob(ModEntities.bigbadwolf.get(), 8);
            var hood = f.mob(ModEntities.redhat_mercenary.get(), 4);
            wolf.setEscape(true); hood.setEscape(true);
            wolf.setHealth(10);
            helper.assertTrue(wolf.hurt(DamageHelper.getDamage(hood, "red"), 20), "real lethal hood hit rejected");
            helper.assertFalse(raging(hood), "actual suppressor is notified as an outsider");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void finalProjectileOwnerIsRedHoodNotAnOutsideKiller(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.mob(ModEntities.bigbadwolf.get(), 8);
            var hood = f.mob(ModEntities.redhat_mercenary.get(), 4);
            wolf.setEscape(true); hood.setEscape(true);
            var projectile = new Arrow(helper.getLevel(), hood);
            wolf.setHealth(10);
            helper.assertTrue(wolf.hurt(wolf.damageSources().arrow(projectile, hood), 20),
                    "owner-attributed projectile hit rejected");
            helper.assertFalse(raging(hood), "projectile suppressor ownership was lost");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void outsideKillerAlwaysHealsAndRampagesWithoutTargetMarker(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.mob(ModEntities.bigbadwolf.get(), 8);
            var hood = f.mob(ModEntities.redhat_mercenary.get(), 4);
            var outsider = f.mob(ModEntities.nothing_there.get(), 12);
            wolf.setEscape(true); hood.setEscape(true);
            hood.setHealth(hood.getMaxHealth() * 0.4F);
            wolf.setHealth(10);
            helper.assertTrue(wolf.hurt(DamageHelper.getDamage(outsider, "red"), 20), "outsider lethal hit rejected");
            helper.assertTrue(raging(hood) && hood.getHealth() == hood.getMaxHealth(),
                    "outside suppression incorrectly depends on player/target marker to restore HP");
            float before = hood.getHealth();
            helper.assertTrue(hood.hurt(DamageHelper.getDamage(outsider, "red"), 10), "rampage RED positive control rejected");
            helper.assertTrue(Math.abs(before - hood.getHealth() - 12.15F) < 0.01F,
                    "candidate original 1.5×0.9 rampage factor or RED 0.9 changed");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void redHoodFinishingHerWolfCommissionReturnsWithoutRampage(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.mob(ModEntities.bigbadwolf.get(), 8);
            var hood = f.mob(ModEntities.redhat_mercenary.get(), 4);
            wolf.setEscape(true);
            hood.forceWolfTarget(wolf);
            wolf.setHealth(10);
            wolf.hurt(DamageHelper.getDamage(hood, "red"), 20);
            for (int i = 0; i < 2 && hood.hasEscape(); i++) hood.tick();
            helper.assertFalse(raging(hood), "self-suppression leaves commission in rampage");
            helper.assertFalse(hood.hasEscape(), "completed own-wolf commission never returns");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void giftsUseMaskAndHeadWithoutTeddysExtraBoxingSlot(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var hood = f.mob(ModEntities.redhat_mercenary.get(), 4);
            var teddy = f.mob(ModEntities.happy_teddy.get(), 8);
            helper.assertTrue("口罩".equals(hood.getEGOGiftData().part()), "hood gift not mask");
            helper.assertTrue("头部".equals(teddy.getEGOGiftData().part()), "teddy gift not head");
            var stack = ModItems.HAPPY_TEDDY_CURIO.get().getDefaultInstance();
            helper.assertTrue(stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:head"))),
                    "teddy lacks real head slot");
            helper.assertFalse(stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:boxing"))),
                    "native Curios description retains bogus boxing membership");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void wolfPaleBodyActualDamageAndDescriptionStayOneArmorStaysTwo(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.mob(ModEntities.bigbadwolf.get(), 8);
            var attacker = f.mob(ModEntities.nothing_there.get(), 4);
            wolf.setEscape(true);
            float before = wolf.getHealth();
            helper.assertTrue(wolf.hurt(DamageHelper.getDamage(attacker, "blue"), 10), "actual PALE entry rejected");
            helper.assertTrue(Math.abs(before - wolf.getHealth() - 10) < 0.001,
                    "actual PALE body multiplier not candidate original 1.0");
            helper.assertTrue(AbnormalityEncyclopediaData.getData("F-02-58").sensitiveInfo().contains("PALE(1.0")
                    && wolf.getEGOArmorData().paleResistance() == 2.0F, "body display and armor PALE confused");
            helper.succeed();
        }
    }

    private static boolean raging(EntityRedHoodMercenary hood) {
        try {
            var field = EntityRedHoodMercenary.class.getDeclaredField("rageMode");
            field.setAccessible(true); return field.getBoolean(hood);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked")
    private static Map<UUID, WorkManager.WorkSession> activeWorks() throws Exception {
        var field = WorkManager.class.getDeclaredField("activeWorks");
        field.setAccessible(true); return (Map<UUID, WorkManager.WorkSession>) field.get(null);
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel facility;
        final List<Entity> entities = new ArrayList<>();
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            if (facility == null) throw new IllegalStateException("missing facility");
            for (int x=1;x<=16;x++) for (int z=1;z<=11;z++) helper.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        }
        <T extends Mob> T mob(EntityType<T> type, int x) {
            return spawn(type, helper.getLevel(), helper.absolutePos(new BlockPos(x,1,5)));
        }
        <T extends Mob> T facilityMob(EntityType<T> type, BlockPos pos) { return spawn(type, facility, pos); }
        <T extends Mob> T spawn(EntityType<T> type, ServerLevel level, BlockPos pos) {
            level.getChunkAt(pos);
            T mob = type.create(level);
            if (mob == null) throw new IllegalStateException("missing entity");
            mob.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            mob.setNoAi(true);
            if (!level.addFreshEntity(mob) || level.getEntity(mob.getUUID()) != mob)
                throw new IllegalStateException("fixture entity was not registered");
            entities.add(mob);
            return mob;
        }
        EntityBigBadWolf boundWolf() {
            var wolf = facilityMob(ModEntities.bigbadwolf.get(), HOME);
            wolf.bindHomeRoom(facility.dimension().location(),HOME);
            wolf.setQliphothCounter(2);
            return wolf;
        }
        void bleeding(LivingEntity person) {
            person.getPersistentData().putLong("lobotocraft_redhat_wounded",facility.getGameTime()+1200);
        }
        void poll(EntityBigBadWolf wolf) { wolf.tickCount=20; wolf.tick(); }
        EmployeeServerPlayer player(int x) {
            var record = new EmployeeRecord(UUID.randomUUID(),"hood_work",EmployeeDepartment.CONTROL);
            var player = new EmployeeServerPlayer(helper.getLevel(),new GameProfile(record.id(),record.name()),record);
            var pos=helper.absolutePos(new BlockPos(x,1,5));
            player.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            helper.getLevel().addNewPlayer(player);
            entities.add(player); return player;
        }
        @Override public void close() {
            for (var entity:entities) if (entity instanceof EmployeeServerPlayer player) {
                ControlStateRecoveryService.clearTransientControls(player);
                player.kill();
            } else entity.discard();
        }
    }
}
