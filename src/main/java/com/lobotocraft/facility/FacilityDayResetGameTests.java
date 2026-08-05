package com.lobotocraft.facility;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModMobEffects;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(ModMain.MODID)
public final class FacilityDayResetGameTests {
    private FacilityDayResetGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 20, required = true, batch = "facility_day_effect_reset")
    public static void newDayClearsEveryAiStatusEffect(GameTestHelper helper) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(),
                "day_effect_reset_test", EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(2, 1, 2)));
        employee.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        helper.getLevel().addNewPlayer(employee);
        employee.addEffect(new MobEffectInstance(ModMobEffects.FRIENDSHIP_PROOF.get(), -1));
        employee.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));

        try {
            helper.assertTrue(employee.hasEffect(ModMobEffects.FRIENDSHIP_PROOF.get()),
                    "friendship proof was not applied before reset");
            helper.assertTrue(employee.hasEffect(MobEffects.MOVEMENT_SPEED),
                    "vanilla effect was not applied before reset");
            FacilityManager.clearStatusEffects(List.of(employee));
            helper.assertTrue(employee.getActiveEffects().isEmpty(),
                    "new day did not clear all AI status effects");
            helper.succeed();
        } finally {
            employee.kill();
        }
    }
}
