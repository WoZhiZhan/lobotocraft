package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

@GameTestHolder(ModMain.MODID + "_lumberjack")
public final class LumberjackGameTests {
    private static final String TEST_NAMESPACE = ModMain.MODID + "_lumberjack";

    private LumberjackGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "lumberjack_data")
    public static void dataMatchesDesign(GameTestHelper helper) {
        EntityLumberjack lumberjack = spawn(helper, new BlockPos(4, 1, 2));
        try {
            helper.assertTrue(Math.abs(lumberjack.getBbWidth() - 1.5F) < 0.001F,
                    "collision width is not 1.5");
            helper.assertTrue(Math.abs(lumberjack.getBbHeight() - 3.0F) < 0.001F,
                    "collision height is not 3.0");
            helper.assertTrue(lumberjack.getMaxHealth() == 430.0F, "max health is not 430");
            helper.assertTrue(lumberjack.getMaxPEOutput() == 18, "PE output is not 18");
            helper.assertTrue(lumberjack.getQliphothCounter() == 1, "counter is not 1");
            helper.assertTrue(lumberjack.getWorkResultForPEOutput(8) == WorkResult.BAD,
                    "0-8 should be bad");
            helper.assertTrue(lumberjack.getWorkResultForPEOutput(9) == WorkResult.NORMAL,
                    "9-14 should be normal");
            helper.assertTrue(lumberjack.getWorkResultForPEOutput(15) == WorkResult.GOOD,
                    "15-18 should be good");
            assertResistance(helper, lumberjack, ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.5D);
            assertResistance(helper, lumberjack, ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D);
            assertResistance(helper, lumberjack, ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D);
            assertResistance(helper, lumberjack, ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
            helper.succeed();
        } finally {
            lumberjack.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "lumberjack_data")
    public static void legacyPaleResistanceRestoresTheConfirmedValue(GameTestHelper helper) {
        EntityLumberjack lumberjack = spawn(helper, new BlockPos(4, 1, 2));
        try {
            lumberjack.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get()).setBaseValue(2.0D);
            net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
            lumberjack.addAdditionalSaveData(saved);
            lumberjack.readAdditionalSaveData(saved);
            assertResistance(helper, lumberjack, ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
            helper.succeed();
        } finally { lumberjack.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "lumberjack_manuals")
    public static void workCompletionFollowsManuals(GameTestHelper helper) {
        EntityLumberjack badResult = spawn(helper, new BlockPos(3, 1, 2));
        EntityLumberjack highTemperance = spawn(helper, new BlockPos(6, 1, 2));
        EntityLumberjack lowTemperance = spawn(helper, new BlockPos(9, 1, 2));
        EmployeeServerPlayer high = employee(helper, new BlockPos(2, 1, 2), "lumberjack_high_temp");
        EmployeeServerPlayer low = employee(helper, new BlockPos(2, 1, 4), "lumberjack_low_temp");
        high.getCapability(com.wzz.lobotocraft.capability.EmployeeStatsProvider.EMPLOYEE_STATS)
                .ifPresent(stats -> stats.setTemperance(45));
        low.getCapability(com.wzz.lobotocraft.capability.EmployeeStatsProvider.EMPLOYEE_STATS)
                .ifPresent(stats -> stats.setTemperance(30));

        try {
            badResult.onWorkComplete(low, WorkType.INSTINCT, WorkResult.BAD);
            helper.assertTrue(badResult.getQliphothCounter() == 0,
                    "bad result did not reduce the counter");
            badResult.onWorkComplete(high, WorkType.REPRESSION, WorkResult.GOOD);
            helper.assertTrue(badResult.isSacrificeArmedForTest(),
                    "work while armed cleared the pending sacrifice");
            EntityLumberjack genericBadHook = spawn(helper, new BlockPos(12, 1, 2));
            genericBadHook.onBadWork(low);
            helper.assertTrue(genericBadHook.getQliphothCounter() == 1,
                    "generic bad-work hook duplicated the woodsman's completion rule");
            genericBadHook.discard();
            highTemperance.onWorkComplete(high, WorkType.INSIGHT, WorkResult.GOOD);
            helper.assertTrue(highTemperance.getQliphothCounter() == 0,
                    "temperance above II did not reduce the counter");
            lowTemperance.onWorkComplete(low, WorkType.ATTACHMENT, WorkResult.GOOD);
            helper.assertTrue(lowTemperance.getQliphothCounter() == 1,
                    "temperance II incorrectly reduced the counter");
            helper.succeed();
        } finally {
            badResult.discard();
            highTemperance.discard();
            lowTemperance.discard();
            high.discard();
            low.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 100, required = true, batch = "lumberjack_sacrifice")
    public static void onlyNextAiWorkStartIsSacrificed(GameTestHelper helper) {
        EntityLumberjack lumberjack = spawn(helper, new BlockPos(4, 1, 2));
        EmployeeServerPlayer ai = employee(helper, new BlockPos(2, 1, 2), "lumberjack_ai");
        lumberjack.decreaseQliphothCounter(1);

        helper.assertTrue(lumberjack.isSacrificeArmedForTest(), "counter zero did not arm sacrifice");
        helper.assertFalse(lumberjack.hasEscape(), "counter zero escaped immediately");
        ServerPlayer ordinaryPlayer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "lumberjack_human"));
        helper.assertTrue(lumberjack.onWorkStart(ordinaryPlayer, WorkType.INSTINCT),
                "ordinary player work start was incorrectly rejected");
        helper.assertTrue(lumberjack.isSacrificeArmedForTest(),
                "ordinary player consumed the AI sacrifice");
        helper.assertFalse(lumberjack.onWorkStart(ai, WorkType.INSTINCT),
                "AI sacrifice incorrectly established a work session");
        helper.assertFalse(ai.isAlive(), "AI employee survived the sacrifice");
        helper.assertFalse(lumberjack.hasEscape(), "lumberjack escaped before attack1 ended");

        helper.runAfterDelay(65, () -> helper.assertFalse(lumberjack.hasEscape(),
                "lumberjack escaped before attack1 finished"));
        helper.runAfterDelay(75, () -> {
            helper.assertTrue(lumberjack.hasEscape(), "lumberjack did not escape after attack1 finished");
            lumberjack.discard();
            ai.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "lumberjack_tombstone")
    public static void tombstoneSearchUsesRadiusFour(GameTestHelper helper) {
        EntityLumberjack lumberjack = spawn(helper, new BlockPos(5, 1, 5));
        BlockPos center = lumberjack.blockPosition();
        BlockPos inside = center.offset(4, 0, 0);
        BlockPos outside = center.offset(5, 0, 0);
        helper.getLevel().setBlock(inside, ModBlocks.TOMBSTONE.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(outside, ModBlocks.TOMBSTONE.get().defaultBlockState(), 3);

        try {
            BlockPos found = invokeNearestTombstone(lumberjack);
            helper.assertTrue(inside.equals(found), "radius-four tombstone was not selected");
            helper.getLevel().setBlock(inside, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(invokeNearestTombstone(lumberjack) == null,
                    "radius-five tombstone was incorrectly selected");
            helper.succeed();
        } finally {
            helper.getLevel().setBlock(inside, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(outside, Blocks.AIR.defaultBlockState(), 3);
            lumberjack.discard();
        }
    }

    private static EntityLumberjack spawn(GameTestHelper helper, BlockPos relative) {
        EntityLumberjack lumberjack = ModEntities.lumberjack.get().create(helper.getLevel());
        if (lumberjack == null) throw new IllegalStateException("lumberjack could not be created");
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        lumberjack.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(lumberjack);
        return lumberjack;
    }

    private static EmployeeServerPlayer employee(GameTestHelper helper, BlockPos relative, String name) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name, EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        employee.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        helper.getLevel().addNewPlayer(employee);
        clearSpawnProtection(employee);
        return employee;
    }

    private static BlockPos invokeNearestTombstone(EntityLumberjack lumberjack) {
        try {
            Method method = EntityLumberjack.class.getDeclaredMethod(
                    "findNearestTombstone", net.minecraft.server.level.ServerLevel.class);
            method.setAccessible(true);
            return (BlockPos) method.invoke(lumberjack, lumberjack.level());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not inspect tombstone search", exception);
        }
    }

    private static void clearSpawnProtection(EmployeeServerPlayer employee) {
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(employee, 0);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not clear spawn protection", exception);
        }
    }

    private static void assertResistance(GameTestHelper helper, EntityLumberjack lumberjack,
                                         net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                         double expected) {
        helper.assertTrue(Math.abs(lumberjack.getAttributeValue(attribute) - expected) < 0.0001D,
                "resistance mismatch for " + attribute.getDescriptionId()
                        + ": expected " + expected + ", actual "
                        + lumberjack.getAttributeValue(attribute));
    }
}
