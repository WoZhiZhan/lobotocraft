package com.wzz.lobotocraft.item;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModDimensions;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/** Regression coverage for the cross-dimension conveyer transaction. */
@GameTestHolder(ModMain.MODID)
public final class ConveyerGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(2, 2, 2);

    private ConveyerGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 80, required = true, batch = "conveyer_dimension")
    public static void mountedPlayerTeleportsOnceAndRejectsImmediateRepeat(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        EmployeeRecord record = new EmployeeRecord(id, "conveyer_test", EmployeeDepartment.CONTROL);
        EmployeeServerPlayer player = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(id, record.name()), record);
        player.setInvulnerable(true);
        BlockPos absolute = helper.absolutePos(PLAYER_POS);
        Vec3 sourcePosition = Vec3.atBottomCenterOf(absolute);
        player.absMoveTo(sourcePosition.x, sourcePosition.y, sourcePosition.z, 0.0F, 0.0F);
        helper.getLevel().addNewPlayer(player);

        Pig vehicle = EntityType.PIG.create(helper.getLevel());
        if (vehicle == null) {
            cleanup(player, null);
            helper.fail("could not create test vehicle");
            return;
        }
        vehicle.setInvulnerable(true);
        vehicle.absMoveTo(sourcePosition.x, sourcePosition.y, sourcePosition.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(vehicle);
        if (!player.startRiding(vehicle, true)) {
            cleanup(player, vehicle);
            helper.fail("could not mount test vehicle");
            return;
        }

        ConveyerItem item = (ConveyerItem) com.wzz.lobotocraft.init.ModItems.CONVEYER.get();
        ItemStack conveyer = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, conveyer);
        InteractionResult first = item.use(
                player.serverLevel(), player, InteractionHand.MAIN_HAND).getResult();
        if (first != InteractionResult.SUCCESS
                || player.serverLevel().dimension() != ModDimensions.LOBOTO_KEY
                || player.isPassenger()
                || vehicle.level().dimension() != Level.OVERWORLD) {
            cleanup(player, vehicle);
            helper.fail("mounted conveyer did not complete one clean dimension transfer");
            return;
        }

        if (!player.getCooldowns().isOnCooldown(item)) {
            cleanup(player, vehicle);
            helper.fail("conveyer did not enter its duplicate-use cooldown");
            return;
        }

        InteractionResult second = item.use(
                player.serverLevel(), player, InteractionHand.MAIN_HAND).getResult();
        if (second == InteractionResult.SUCCESS
                || player.serverLevel().dimension() != ModDimensions.LOBOTO_KEY) {
            cleanup(player, vehicle);
            helper.fail("immediate repeated conveyer use crossed dimensions twice");
            return;
        }

        if (Math.abs(player.getPersistentData().getDouble("lobotocraft:LotoOldX")
                - sourcePosition.x) > 1.0E-6D
                || Math.abs(player.getPersistentData().getDouble("lobotocraft:LotoOldZ")
                - sourcePosition.z) > 1.0E-6D) {
            cleanup(player, vehicle);
            helper.fail("return coordinates were not committed from the original dimension");
            return;
        }

        cleanup(player, vehicle);
        helper.succeed();
    }

    private static void cleanup(EmployeeServerPlayer player, Pig vehicle) {
        if (player != null && !player.isRemoved()) {
            player.stopRiding();
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        if (vehicle != null && !vehicle.isRemoved()) vehicle.discard();
    }
}
