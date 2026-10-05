package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/** Real entity entrances for personnel intent, transformation recovery and skill reach. */
@GameTestHolder(PersonnelRangeRegressionGameTests.NAMESPACE)
public final class PersonnelRangeRegressionGameTests {
    static final String NAMESPACE = "lobotocraft_personnel_range";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void ppodaeRecoversWhenAnUnregisteredEscapePositionIsOccupied(GameTestHelper helper) {
        var dog = spawn(helper, ModEntities.ppodae.get(), 5, 1);
        var occupant = spawn(helper, ModEntities.lumberjack.get(), 5, 1);
        try {
            helper.assertTrue(com.wzz.lobotocraft.util.SafeTransformationUtil.findPositionForDimensions(
                    helper.getLevel(), dog, net.minecraft.world.entity.EntityDimensions.scalable(1.8F, 2.7F),
                    10, 3) == null, "default shared transformation policy leaked outside registered areas");
            dog.triggerEscape();
            helper.assertTrue(dog.hasEscape(), "fixture did not trigger escape");
            helper.assertTrue(dog.getBbWidth() > 1.0F && !dog.isNoAi(),
                    "occupied escape position permanently leaves dog small and AI disabled");
            helper.assertFalse(dog.getBoundingBox().intersects(occupant.getBoundingBox()),
                    "transformed dog still overlaps occupant");
            helper.assertTrue(helper.getLevel().noCollision(dog, dog.getBoundingBox()),
                    "safe transformation overlaps blocks");
            helper.succeed();
        } finally { dog.discard(); occupant.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void ppodaeRejectsAbnormalitiesAtTargetAndDelayedHitEntrances(GameTestHelper helper)
            throws Exception {
        var dog = spawn(helper, ModEntities.ppodae.get(), 5, 1);
        var victim = spawn(helper, ModEntities.lumberjack.get(), 7, 1);
        try {
            dog.setEscape(true);
            victim.setEscape(true);
            dog.setTarget(victim);
            helper.assertTrue(dog.getTarget() == null, "common target assignment bypasses personnel intent");
            helper.assertFalse(dog.doHurtTarget(victim), "melee entry accepts abnormality");
            float before = victim.getHealth();
            invoke(dog, "executeDamage", new Class<?>[]{net.minecraft.world.entity.LivingEntity.class}, victim);
            helper.assertTrue(victim.getHealth() == before, "delayed hit bypasses personnel intent");
            helper.succeed();
        } finally { dog.discard(); victim.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void ppodaeWaitsForRealSpaceAndResumesPersonnelAttacks(GameTestHelper helper) throws Exception {
        var dog = spawn(helper, ModEntities.ppodae.get(), 5, 1);
        var clerk = ModEntities.clerk.get().create(helper.getLevel());
        if (clerk == null) throw new IllegalStateException("missing clerk");
        var pos = helper.absolutePos(new BlockPos(5, 1, 5));
        clerk.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
        clerk.setNoAi(true);
        helper.getLevel().addFreshEntity(clerk);
        var areas = com.wzz.lobotocraft.world.data.CompanyRoomData.get(helper.getLevel());
        String areaKey = "ppodae-regression-" + dog.getUUID();
        var area = new com.wzz.lobotocraft.world.data.FacilityAreaEntry(
                helper.getLevel().dimension().location(), areaKey, pos,
                helper.absolutePos(new BlockPos(1, 1, 1)),
                helper.absolutePos(new BlockPos(16, 6, 11)), "",
                com.wzz.lobotocraft.world.data.FacilityAreaType.OTHER, areaKey);
        areas.putArea(area);
        try {
            helper.assertTrue(com.wzz.lobotocraft.world.data.FacilityAreaSelection.containing(
                    areas.getAreas(helper.getLevel().dimension().location()),
                    helper.getLevel().dimension().location(), dog.position()).orElse(null) == area,
                    "fixture area is not eligible for bounded transformation search");
            for (int x = 1; x <= 16; x++) for (int z = 1; z <= 11; z++)
                for (int y = 2; y <= 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            dog.triggerEscape();
            helper.assertTrue(dog.isNoAi() && dog.getBbWidth() < 1.0F,
                    "dog grew inside an obstructed low ceiling");
            for (int x = 1; x <= 16; x++) for (int z = 1; z <= 11; z++)
                for (int y = 2; y <= 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            dog.tickCount = 10;
            dog.tick();
            helper.assertTrue(!dog.isNoAi() && dog.getBbWidth() > 1.0F,
                    "pending transformation did not retry after space became available");
            clerk.moveTo(dog.position().add(2, 0, 0));
            dog.setTarget(clerk);
            helper.assertTrue(dog.getTarget() == clerk && dog.doHurtTarget(clerk),
                    "personnel-only rule rejects facility clerk");
            float before = clerk.getHealth();
            invoke(dog, "executeDamage", new Class<?>[]{net.minecraft.world.entity.LivingEntity.class}, clerk);
            helper.assertTrue(clerk.getHealth() < before, "clerk positive control did not take RED damage");
            invoke(dog, "setReturning", new Class<?>[]{boolean.class}, true);
            dog.setTarget(clerk);
            helper.assertTrue(dog.getTarget() == null, "returning dog reopens combat");
            helper.succeed();
        } finally { areas.removeArea(areaKey); dog.discard(); clerk.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void murdererPreferencesFollowCandidateOriginalLevelOrder(GameTestHelper helper) {
        var murderer = spawn(helper, ModEntities.abandoned_murderer.get(), 5, 1);
        try {
            float[][] expected = {
                    {0.6F, 0.6F, 0.5F, 0.5F, 0.5F},
                    {0.4F, 0.4F, 0.3F, 0.3F, 0.3F},
                    {0.5F, 0.5F, 0.4F, 0.4F, 0.4F},
                    {0.3F, 0.2F, 0.0F, -0.8F, -0.8F}
            };
            float[][] actual = murderer.getFullWorkPreferences();
            for (int work = 0; work < 4; work++) for (int level = 0; level < 5; level++)
                helper.assertTrue(Math.abs(actual[work][level] - expected[work][level]) < 0.00001F,
                        "wrong work probability at work=" + work + ", level=" + (level + 1));
            helper.succeed();
        } finally { murderer.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void butterflyConeDoesNotHitOtherFloors(GameTestHelper helper) throws Exception {
        var funeral = spawn(helper, ModEntities.butterfly_funeral.get(), 3, 1);
        var sameFloor = spawn(helper, ModEntities.lumberjack.get(), 9, 1);
        var above = spawn(helper, ModEntities.lumberjack.get(), 9, 6);
        var below = spawn(helper, ModEntities.lumberjack.get(), 9, -4);
        try {
            for (var entity : java.util.List.of(funeral, sameFloor, above, below)) entity.setEscape(true);
            set(funeral, "skillPhase", 2);
            set(funeral, "skillTimer", 260);
            set(funeral, "skillDirection", new Vec3(1, 0, 0));
            float sameBefore = sameFloor.getHealth(), aboveBefore = above.getHealth(), belowBefore = below.getHealth();
            invoke(funeral, "damageButterflyCone", new Class<?>[]{net.minecraft.server.level.ServerLevel.class},
                    helper.getLevel());
            helper.assertTrue(sameFloor.getHealth() < sameBefore, "same-floor cone positive control missed");
            helper.assertTrue(above.getHealth() == aboveBefore && below.getHealth() == belowBefore,
                    "horizontal cone damages another floor");
            helper.succeed();
        } finally { funeral.discard(); sameFloor.discard(); above.discard(); below.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void butterflyGiftShowsRightBack(GameTestHelper helper) {
        var funeral = spawn(helper, ModEntities.butterfly_funeral.get(), 5, 1);
        try {
            helper.assertTrue("右背".equals(funeral.getEGOGiftData().part()), "gift still shows incorrect part");
            helper.succeed();
        } finally { funeral.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void butterflyConeRechecksOcclusionAndHeightBoundary(GameTestHelper helper) throws Exception {
        var funeral = spawn(helper, ModEntities.butterfly_funeral.get(), 3, 1);
        var target = spawn(helper, ModEntities.lumberjack.get(), 9, 1);
        try {
            funeral.setEscape(true);
            target.setEscape(true);
            set(funeral, "skillPhase", 2);
            set(funeral, "skillTimer", 260);
            set(funeral, "skillDirection", new Vec3(1, 0, 0));
            for (int z = 1; z <= 11; z++) for (int y = 1; y <= 7; y++)
                helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
            float before = target.getHealth();
            invoke(funeral, "damageButterflyCone", new Class<?>[]{net.minecraft.server.level.ServerLevel.class},
                    helper.getLevel());
            helper.assertTrue(target.getHealth() == before, "cone crosses a complete separating wall");
            for (int z = 1; z <= 11; z++) for (int y = 1; y <= 7; y++)
                helper.setBlock(new BlockPos(6, y, z), Blocks.AIR);
            target.moveTo(funeral.position().add(6, 1.5001D, 0));
            invoke(funeral, "damageButterflyCone", new Class<?>[]{net.minecraft.server.level.ServerLevel.class},
                    helper.getLevel());
            helper.assertTrue(target.getHealth() == before, "cone crosses upper foot-height boundary");
            target.moveTo(funeral.position().add(6, 1.5D, 0));
            invoke(funeral, "damageButterflyCone", new Class<?>[]{net.minecraft.server.level.ServerLevel.class},
                    helper.getLevel());
            helper.assertTrue(target.getHealth() < before, "height boundary positive control missed");
            helper.succeed();
        } finally { funeral.discard(); target.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void thornBusRealAttackSequenceHitsBothPassiveBirds(GameTestHelper helper) throws Exception {
        for (var type : java.util.List.of(ModEntities.large_bird.get(), ModEntities.punishing_bird.get())) {
            var bus = spawn(helper, ModEntities.thorn_bus.get(), 5, 1);
            var bird = spawn(helper, type, 7, 1);
            try {
                bus.setEscape(true);
                bird.setEscape(true);
                bus.setAnimation("idle");
                float before = bird.getHealth();
                bus.tickCount = 40; // ServerLevel, not Entity.tick(), advances entity age.
                bus.tick(); // Real proximity selection starts the sequence on tick 40.
                for (int i = 0; i < 10; i++) invoke(bus, "tickAttackSequence", new Class<?>[0]);
                helper.assertTrue(bird.getHealth() < before,
                        "real bus proximity/sequence failed against " + bird.getAbnormalityCode());
            } finally { bus.discard(); bird.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void thornBusRejectsDistantAttributedDamage(GameTestHelper helper) {
        var bus = spawn(helper, ModEntities.thorn_bus.get(), 4, 1);
        var attacker = spawn(helper, ModEntities.lumberjack.get(), 14, 1);
        try {
            bus.setEscape(true);
            attacker.setEscape(true);
            float before = bus.getHealth();
            helper.assertFalse(bus.hurt(DamageHelper.getDamage(attacker, "red"), 3),
                    "non-projectile skill bypasses attack-radius immunity");
            helper.assertTrue(bus.getHealth() == before, "out-of-range damage reduced health");
            helper.succeed();
        } finally { bus.discard(); attacker.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void thornBusAcceptsNearbyProjectileOwnerAndRejectsDistantOwner(GameTestHelper helper) {
        var bus = spawn(helper, ModEntities.thorn_bus.get(), 5, 1);
        var attacker = spawn(helper, ModEntities.lumberjack.get(), 7, 1);
        var arrow = new Arrow(helper.getLevel(), attacker);
        try {
            bus.setEscape(true);
            attacker.setEscape(true);
            arrow.moveTo(bus.position());
            var source = helper.getLevel().damageSources().arrow(arrow, attacker);
            float before = bus.getHealth();
            helper.assertTrue(bus.hurt(source, 3) && bus.getHealth() < before,
                    "nearby projectile owner incorrectly rejected by projectile type");
            EntityUtil.clearHurtTime(bus);
            attacker.moveTo(bus.position().add(12, 0, 0));
            before = bus.getHealth();
            helper.assertFalse(bus.hurt(source, 3), "projectile position bypasses distant-owner immunity");
            helper.assertTrue(bus.getHealth() == before, "distant owner's projectile reduced health");
            helper.succeed();
        } finally { bus.discard(); attacker.discard(); arrow.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void thornBusChecksExactSharedAttackBoundaryAndAnonymousOrigin(GameTestHelper helper) {
        var bus = spawn(helper, ModEntities.thorn_bus.get(), 5, 1);
        var attacker = spawn(helper, ModEntities.lumberjack.get(), 7, 1);
        try {
            bus.setEscape(true);
            attacker.setEscape(true);
            double edge = bus.getBoundingBox().maxX + 3.1D + attacker.getBbWidth() * 0.5D;
            attacker.moveTo(edge + 0.0001D, bus.getY(), bus.getZ());
            var source = DamageHelper.getDamage(attacker, "red");
            float before = bus.getHealth();
            helper.assertFalse(bus.hurt(source, 3), "damage accepted outside attack-box boundary");
            attacker.moveTo(edge - 0.0001D, bus.getY(), bus.getZ());
            helper.assertTrue(bus.hurt(source, 3) && bus.getHealth() < before,
                    "valid damage rejected just inside attack-box boundary");
            EntityUtil.clearHurtTime(bus);
            before = bus.getHealth();
            var farOrigin = new net.minecraft.world.damagesource.DamageSource(
                    source.typeHolder(), bus.position().add(20, 0, 0));
            helper.assertFalse(bus.hurt(farOrigin, 3), "known distant origin bypasses anonymous-source gate");
            helper.assertTrue(bus.getHealth() == before, "anonymous distant origin caused damage");
            var nearOrigin = new net.minecraft.world.damagesource.DamageSource(source.typeHolder(), bus.position());
            helper.assertTrue(bus.hurt(nearOrigin, 3), "anonymous nearby origin wrongly rejected");
            helper.succeed();
        } finally { bus.discard(); attacker.discard(); }
    }

    private static void set(Object entity, String name, Object value) throws Exception {
        var field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }

    private static void invoke(Object entity, String name, Class<?>[] types, Object... args) throws Exception {
        var method = entity.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(entity, args);
    }

    private static <T extends AbstractAbnormality> T spawn(
            GameTestHelper helper, EntityType<T> type, int x, int y) {
        for (int floorX = 1; floorX <= 16; floorX++) for (int z = 1; z <= 11; z++)
            helper.setBlock(new BlockPos(floorX, 0, z), Blocks.STONE);
        T entity = type.create(helper.getLevel());
        if (entity == null) throw new IllegalStateException("missing entity");
        var pos = helper.absolutePos(new BlockPos(x, y, 5));
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
        helper.getLevel().addFreshEntity(entity);
        entity.recoverForNewFacilityDay();
        entity.setNoAi(true);
        entity.setNoGravity(true);
        return entity;
    }
}
