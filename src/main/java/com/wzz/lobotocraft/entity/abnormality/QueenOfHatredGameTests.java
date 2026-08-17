package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.event.definition.company.FacilityQliphothStageEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;

import java.lang.reflect.Field;
import java.util.UUID;

@GameTestHolder(ModMain.MODID + "_queen_of_hatred")
public final class QueenOfHatredGameTests {
    private static final String TEST_NAMESPACE = ModMain.MODID + "_queen_of_hatred";

    private QueenOfHatredGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "queen_data")
    public static void dataMatchesOriginal(GameTestHelper helper) {
        EntityQueenOfHatred queen = spawnQueen(helper, new BlockPos(4, 1, 4));
        try {
            helper.assertTrue(queen.getMaxHealth() == 1200.0F, "max health is not 1200");
            helper.assertTrue(queen.getMaxPEOutput() == 22, "PE output is not 22");
            helper.assertTrue(queen.getQliphothCounter() == 2, "counter is not 2");
            helper.assertTrue("idle_inside".equals(queen.getAnimation()),
                    "new containment instance did not start in idle_inside");
            helper.assertTrue(queen.getWorkResultForPEOutput(8) == WorkResult.BAD,
                    "0-8 should be bad");
            helper.assertTrue(queen.getWorkResultForPEOutput(9) == WorkResult.NORMAL,
                    "9-14 should be normal");
            helper.assertTrue(queen.getWorkResultForPEOutput(15) == WorkResult.GOOD,
                    "15-22 should be good");
            assertResistance(helper, queen, ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.7D);
            assertResistance(helper, queen, ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.2D);
            assertResistance(helper, queen, ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.3D);
            assertResistance(helper, queen, ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.5D);
            helper.succeed();
        } finally {
            queen.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "queen_kiss")
    public static void kissHealsOnlyAfterAnimation(GameTestHelper helper) {
        EntityQueenOfHatred queen = spawnQueen(helper, new BlockPos(4, 1, 4));
        EmployeeServerPlayer employee = employee(helper, new BlockPos(5, 1, 4), "queen_kiss_worker");
        employee.setHealth(2.0F);
        MentalValueUtil.setMentalValue(employee, 1.0F);
        queen.recordCompletedWork(employee, WorkResult.GOOD, 22, false);
        queen.onWorkComplete(employee, WorkType.ATTACHMENT, WorkResult.GOOD);

        helper.assertTrue("CONTAINED_KISS".equals(queen.stateNameForTest()),
                "max-counter work did not enter kiss state");
        helper.assertTrue(employee.getHealth() == 2.0F, "kiss healed before its animation");
        helper.runAfterDelay(45, () -> helper.assertTrue(employee.getHealth() == 2.0F,
                "kiss healed before 2.5 seconds elapsed"));
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(employee.getHealth() == employee.getMaxHealth(),
                    "kiss did not restore health after the animation");
            helper.assertTrue(MentalValueUtil.getMentalValue(employee)
                            == MentalValueUtil.getEffectiveMaxMentalValue(employee),
                    "kiss did not restore mental value after the animation");
            queen.discard();
            employee.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "queen_hysteria_work")
    public static void exactPeOutputControlsHysteria(GameTestHelper helper) {
        EmployeeServerPlayer employee = employee(helper, new BlockPos(2, 1, 2), "queen_pe_worker");
        EntityQueenOfHatred recovered = spawnQueen(helper, new BlockPos(5, 1, 2));
        EntityQueenOfHatred escaped = spawnQueen(helper, new BlockPos(9, 1, 2));
        try {
            recovered.decreaseQliphothCounter(1);
            recovered.recordCompletedWork(employee, WorkResult.GOOD, 16, false);
            recovered.onWorkComplete(employee, WorkType.INSIGHT, WorkResult.GOOD);
            helper.assertTrue(recovered.getQliphothCounter() == 2,
                    "16 PE did not restore the counter");

            escaped.decreaseQliphothCounter(1);
            escaped.recordCompletedWork(employee, WorkResult.GOOD, 15, false);
            escaped.onWorkComplete(employee, WorkType.INSIGHT, WorkResult.GOOD);
            helper.assertTrue(escaped.getQliphothCounter() == 0,
                    "15 PE did not reduce the counter to zero");
            helper.assertTrue(escaped.hasEscape() && escaped.isSnakeForm(),
                    "failed hysteric work did not begin the snake escape");
            helper.assertFalse(escaped.isNoAi(), "snake escape retained NoAI");
            escaped.stopEscape();
            helper.succeed();
        } finally {
            recovered.discard();
            escaped.discard();
            employee.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "queen_stage")
    public static void staffDeathsProtectTheStageCounterAndTriggerHeroHysteria(GameTestHelper helper) {
        EntityQueenOfHatred protectedQueen = spawnQueen(helper, new BlockPos(4, 1, 2));
        EntityQueenOfHatred reducedQueen = spawnQueen(helper, new BlockPos(8, 1, 2));
        EntityClerk clerk = clerk(helper, new BlockPos(2, 1, 2));
        try {
            protectedQueen.onStaffDeath(clerk);
            protectedQueen.onStaffDeath(clerk);
            protectedQueen.onStaffDeath(clerk);
            reducedQueen.onStaffDeath(clerk);
            reducedQueen.onStaffDeath(clerk);
            MinecraftForge.EVENT_BUS.post(new FacilityQliphothStageEvent(helper.getLevel(), 2));
            helper.assertTrue(protectedQueen.getQliphothCounter() == 2,
                    "three stage deaths did not protect the counter");
            helper.assertTrue(reducedQueen.getQliphothCounter() == 1,
                    "fewer than three stage deaths did not reduce the counter");

            protectedQueen.setHeroStaffSnapshotForTest(5, 0);
            protectedQueen.onStaffDeath(clerk);
            helper.assertTrue("HERO_HYSTERIA_START".equals(protectedQueen.stateNameForTest()),
                    "20 percent hero death threshold did not start hysteria");
            Vec3 heroPosition = protectedQueen.position();
            protectedQueen.triggerEscape();
            helper.assertTrue(protectedQueen.position().distanceToSqr(heroPosition) < 0.01D,
                    "hero-to-snake transition moved to a containment exit");
            protectedQueen.stopEscape();
            helper.succeed();
        } finally {
            protectedQueen.discard();
            reducedQueen.discard();
            clerk.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 170, required = true, batch = "queen_return")
    public static void snakeDeathPlaysBothReturnFormsBeforeContainment(GameTestHelper helper) {
        EntityQueenOfHatred queen = spawnQueen(helper, new BlockPos(4, 1, 4));
        queen.triggerEscape();
        helper.assertTrue(queen.hasEscape() && queen.isSnakeForm(),
                "triggerEscape did not enter live snake form");
        helper.assertFalse(queen.isNoAi(), "snake form started with NoAI");
        queen.die(helper.getLevel().damageSources().genericKill());
        helper.assertTrue("VILLAIN_RETURN_SNAKE".equals(queen.stateNameForTest()),
                "suppression skipped snake human animation");

        helper.runAfterDelay(50, () -> {
            helper.assertTrue("VILLAIN_RETURN_HUMAN".equals(queen.stateNameForTest()),
                    "snake human animation did not transition to human form");
            helper.assertFalse(queen.isSnakeForm(), "model did not switch to human return form");
        });
        helper.runAfterDelay(145, () -> {
            helper.assertTrue("CONTAINED".equals(queen.stateNameForTest()),
                    "human return animation did not finish in containment");
            helper.assertFalse(queen.hasEscape(), "escape tracking survived completed return");
            queen.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "queen_nbt")
    public static void nbtReloadRestoresStableVillainState(GameTestHelper helper) {
        EntityQueenOfHatred original = spawnQueen(helper, new BlockPos(4, 1, 4));
        original.triggerEscape();
        CompoundTag tag = new CompoundTag();
        original.addAdditionalSaveData(tag);
        EntityQueenOfHatred loaded = ModEntities.queen_of_hatred.get().create(helper.getLevel());
        if (loaded == null) throw new IllegalStateException("queen could not be re-created");
        loaded.readAdditionalSaveData(tag);
        try {
            helper.assertTrue("VILLAIN_IDLE".equals(loaded.stateNameForTest()),
                    "reload did not restore stable villain state");
            helper.assertTrue(loaded.isSnakeForm(), "reload lost snake model state");
            helper.assertTrue(loaded.hasEscape(), "reload lost escape state");
            helper.succeed();
        } finally {
            original.stopEscape();
            original.discard();
            loaded.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "queen_hero_damage")
    public static void heroStatesAcceptEscapedAbnormalityDamage(GameTestHelper helper) {
        EntityQueenOfHatred idleHero = spawnQueen(helper, new BlockPos(3, 1, 3));
        EntityQueenOfHatred laserHero = spawnQueen(helper, new BlockPos(6, 1, 3));
        EntityQueenOfHatred attacker = spawnQueen(helper, new BlockPos(9, 1, 3));
        try {
            idleHero.setHeroDamageStateForTest(false);
            laserHero.setHeroDamageStateForTest(true);
            attacker.triggerEscape();

            helper.assertFalse(idleHero.hasEscape(), "hero assistance was incorrectly marked escaped");
            helper.assertFalse(laserHero.hasEscape(), "laser assistance was incorrectly marked escaped");
            helper.assertTrue(attacker.hasEscape(), "test attacker did not enter an escaped state");

            attacker.tick();
            helper.assertTrue(attacker.getTarget() == laserHero,
                    "escaped abnormality did not target the nearest non-escaped hero");

            float idleHealth = idleHero.getHealth();
            boolean idleAccepted = idleHero.hurt(DamageHelper.getDamage(attacker, "red"), 40.0F);
            helper.assertTrue(idleAccepted && idleHero.getHealth() < idleHealth,
                    "idle hero did not accept escaped-abnormality damage");

            float laserHealth = laserHero.getHealth();
            boolean laserAccepted = laserHero.hurt(DamageHelper.getDamage(attacker, "red"), 40.0F);
            helper.assertTrue(laserAccepted && laserHero.getHealth() < laserHealth,
                    "laser hero did not accept escaped-abnormality damage");

            float protectedHealth = idleHero.getHealth();
            boolean environmentAccepted = idleHero.hurt(helper.getLevel().damageSources().generic(), 40.0F);
            helper.assertFalse(environmentAccepted, "hero accepted non-hostile generic damage");
            helper.assertTrue(idleHero.getHealth() == protectedHealth,
                    "non-hostile generic damage changed hero health");
            helper.succeed();
        } finally {
            attacker.stopEscape();
            idleHero.discard();
            laserHero.discard();
            attacker.discard();
        }
    }

    private static EntityQueenOfHatred spawnQueen(GameTestHelper helper, BlockPos relative) {
        EntityQueenOfHatred queen = ModEntities.queen_of_hatred.get().create(helper.getLevel());
        if (queen == null) throw new IllegalStateException("queen could not be created");
        move(helper, queen, relative);
        helper.getLevel().addFreshEntity(queen);
        return queen;
    }

    private static EntityClerk clerk(GameTestHelper helper, BlockPos relative) {
        EntityClerk clerk = ModEntities.clerk.get().create(helper.getLevel());
        if (clerk == null) throw new IllegalStateException("clerk could not be created");
        move(helper, clerk, relative);
        helper.getLevel().addFreshEntity(clerk);
        return clerk;
    }

    private static EmployeeServerPlayer employee(GameTestHelper helper, BlockPos relative, String name) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name, EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        move(helper, employee, relative);
        helper.getLevel().addNewPlayer(employee);
        clearSpawnProtection(employee);
        return employee;
    }

    private static void move(GameTestHelper helper, net.minecraft.world.entity.Entity entity,
                             BlockPos relative) {
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        entity.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
    }

    private static void clearSpawnProtection(ServerPlayer employee) {
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(employee, 0);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not clear spawn protection", exception);
        }
    }

    private static void assertResistance(GameTestHelper helper, EntityQueenOfHatred queen,
                                         net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                         double expected) {
        helper.assertTrue(Math.abs(queen.getAttributeValue(attribute) - expected) < 0.0001D,
                "resistance mismatch: expected " + expected + ", actual "
                        + queen.getAttributeValue(attribute));
    }
}
