package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.PanicType;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;

/** Real registered E.G.O stacks exercise the same policy used by autonomous targeting. */
@GameTestHolder(ModMain.MODID)
public final class EmployeePanicRescueGameTests {
    private EmployeePanicRescueGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "employee_panic_rescue")
    public static void rescueSelectionUsesActualWeaponForm(GameTestHelper helper) {
        ItemStack army = new ItemStack(ModItems.ARMY_IN_BLACK_WEAPON.get());
        helper.assertTrue(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, army, true, 0.51F), "healthy army weapon should use WHITE");
        helper.assertTrue(!EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, army, true, 0.5F), "low-health army weapon uses BLACK");

        ItemStack white = ButterflyFuneralWeapon.createWhiteWeapon(ModItems.BUTTERFLY_FUNERAL_WEAPON.get());
        ItemStack black = ButterflyFuneralWeapon.createBlackWeapon(ModItems.BUTTERFLY_FUNERAL_WEAPON.get());
        helper.assertTrue(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, white, true, 1.0F), "WHITE funeral gun was rejected");
        helper.assertTrue(!EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, black, true, 1.0F), "BLACK funeral gun was selected");
        helper.assertTrue(!EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, white, false, 1.0F), "unusable assignment was selected");
        helper.assertTrue(!EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, new ItemStack(ModItems.END_BIRD_WEAPON.get()), true, 1.0F),
                "multi-damage E.G.O is not a WHITE-only rescue");
        helper.succeed();
    }
}
