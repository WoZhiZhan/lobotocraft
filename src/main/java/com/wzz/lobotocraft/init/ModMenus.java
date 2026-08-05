package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.menu.EmployeeInventoryMenu;
import com.wzz.lobotocraft.menu.EmployeeWarehouseMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ModMain.MODID);

    public static final RegistryObject<MenuType<EmployeeInventoryMenu>> EMPLOYEE_INVENTORY =
            REGISTRY.register("employee_inventory",
                    () -> IForgeMenuType.create(EmployeeInventoryMenu::fromNetwork));

    public static final RegistryObject<MenuType<EmployeeWarehouseMenu>> EMPLOYEE_WAREHOUSE =
            REGISTRY.register("employee_warehouse",
                    () -> IForgeMenuType.create(EmployeeWarehouseMenu::fromNetwork));

    private ModMenus() {
    }
}
