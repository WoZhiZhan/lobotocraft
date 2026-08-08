package com.wzz.lobotocraft.employee;

import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;

/** Server-thread inventory recovery and bulk-deposit operations. */
public final class EmployeeWarehouseService {
    private EmployeeWarehouseService() {
    }

    /** Normal bulk deposit. Equipped gifts are intentionally left on the employee. */
    public static int collect(EmployeeServerPlayer employee) {
        return transfer(employee, false);
    }

    /** Death recovery. Curios cannot veto retirement, but still receive their lifecycle callback. */
    public static int recoverOnDeath(EmployeeServerPlayer employee) {
        return transfer(employee, true);
    }

    private static int transfer(EmployeeServerPlayer employee, boolean forceCurioUnequip) {
        if (employee == null || employee.server == null) return 0;
        EmployeeItemWarehouseData warehouse = EmployeeItemWarehouseData.get(employee.server);
        int moved = transferList(warehouse, employee.getInventory().items);
        moved += transferList(warehouse, employee.getInventory().armor);
        moved += transferList(warehouse, employee.getInventory().offhand);
        if (shouldTransferCurios(forceCurioUnequip)) moved += CuriosApi.getCuriosInventory(employee).resolve().map(handler -> {
            int curioMoved = 0;
            for (var entry : handler.getCurios().entrySet()) {
                ICurioStacksHandler group = entry.getValue();
                curioMoved += transferCurioStacks(warehouse, employee, entry.getKey(), group,
                        group.getStacks(), false, forceCurioUnequip);
                curioMoved += transferCurioStacks(warehouse, employee, entry.getKey(), group,
                        group.getCosmeticStacks(), true, forceCurioUnequip);
            }
            return curioMoved;
        }).orElse(0);
        if (moved > 0) EmployeeRuntimeManager.persistNow(employee.getUUID());
        return moved;
    }

    static boolean shouldTransferCurios(boolean deathRecovery) {
        return deathRecovery;
    }

    public static int collectAllActive(net.minecraft.server.MinecraftServer server) {
        int moved = 0;
        for (EmployeeServerPlayer employee : EmployeeRuntimeManager.activePlayers()) {
            if (employee.isAlive() && !employee.isRemoved()) moved += collect(employee);
        }
        return moved;
    }

    private static int transferCurioStacks(EmployeeItemWarehouseData warehouse,
                                           EmployeeServerPlayer employee,
                                           String identifier,
                                           ICurioStacksHandler group,
                                           IDynamicStackHandler stacks,
                                           boolean cosmetic,
                                           boolean forceUnequip) {
        int moved = 0;
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack existing = stacks.getStackInSlot(slot);
            if (!EmployeeItemWarehouseData.isTransferable(existing)) continue;

            boolean visible = cosmetic
                    || slot < group.getRenders().size() && group.getRenders().get(slot);
            SlotContext context = new SlotContext(identifier, employee, slot, cosmetic, visible);
            ICurio curio = CuriosApi.getCurio(existing).resolve().orElse(null);
            if (!forceUnequip && curio != null && !curio.canUnequip(context)) continue;

            ItemStack remainder = warehouse.insert(existing);
            int deposited = existing.getCount() - remainder.getCount();
            if (deposited <= 0) continue;

            stacks.setStackInSlot(slot, remainder);
            moved += deposited;
            if (remainder.isEmpty() && curio != null) {
                curio.onUnequip(context, ItemStack.EMPTY);
            }
        }
        return moved;
    }

    private static int transferList(EmployeeItemWarehouseData warehouse, List<ItemStack> inventory) {
        int moved = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack existing = inventory.get(slot);
            if (!EmployeeItemWarehouseData.isTransferable(existing)) continue;
            ItemStack remainder = warehouse.insert(existing);
            int deposited = existing.getCount() - remainder.getCount();
            if (deposited <= 0) continue;
            moved += deposited;
            inventory.set(slot, remainder);
        }
        return moved;
    }
}
