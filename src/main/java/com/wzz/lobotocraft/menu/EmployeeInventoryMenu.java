package com.wzz.lobotocraft.menu;

import com.wzz.lobotocraft.employee.EmployeeItemWarehouseData;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModMenus;
import com.wzz.lobotocraft.network.PacketDecodeLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EmployeeInventoryMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int MAX_CURIO_COLUMNS = 22;
    public static final int EMPLOYEE_MAIN_Y = 20;
    public static final int EMPLOYEE_HOTBAR_Y = 78;
    public static final int CURIO_LABEL_Y = 102;
    public static final int CURIO_START_Y = 114;

    private final UUID employeeId;
    private final String employeeName;
    private final EmployeeServerPlayer employee;
    private final List<String> curioSlotIdentifiers = new ArrayList<>();
    private final int curioSlotStart;
    private final int curioSlotEnd;
    private final int employeeSlotEnd;
    private final int playerSlotStart;
    private final int playerMainY;
    private final int imageHeight;
    private final int imageWidth;
    private final int curioColumns;

    public EmployeeInventoryMenu(int containerId, Inventory viewerInventory,
                                 EmployeeServerPlayer employee, List<CurioGroup> groups) {
        super(ModMenus.EMPLOYEE_INVENTORY.get(), containerId);
        this.employeeId = employee.getUUID();
        this.employeeName = employee.employeeRecord().name();
        this.employee = employee;
        this.curioSlotStart = 36;
        this.curioColumns = calculateCurioColumns(groups);
        this.imageWidth = curioColumns * 18 + 16;

        addEmployeeInventory(employee.getInventory());
        addServerCurios(employee, groups);
        this.curioSlotEnd = slots.size();
        this.employeeSlotEnd = curioSlotEnd;
        this.playerSlotStart = employeeSlotEnd;
        this.playerMainY = calculatePlayerMainY(curioSlotIdentifiers.size());
        this.imageHeight = playerMainY + 84;
        addViewerInventory(viewerInventory);
    }

    private EmployeeInventoryMenu(int containerId, Inventory viewerInventory, UUID employeeId,
                                  String employeeName, List<CurioGroup> groups) {
        super(ModMenus.EMPLOYEE_INVENTORY.get(), containerId);
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employee = null;
        this.curioSlotStart = 36;
        this.curioColumns = calculateCurioColumns(groups);
        this.imageWidth = curioColumns * 18 + 16;

        addEmployeeInventory(new SimpleContainer(36));
        addClientCurios(groups);
        this.curioSlotEnd = slots.size();
        this.employeeSlotEnd = curioSlotEnd;
        this.playerSlotStart = employeeSlotEnd;
        this.playerMainY = calculatePlayerMainY(curioSlotIdentifiers.size());
        this.imageHeight = playerMainY + 84;
        addViewerInventory(viewerInventory);
    }

    public static EmployeeInventoryMenu fromNetwork(int containerId, Inventory inventory,
                                                    FriendlyByteBuf buffer) {
        UUID employeeId = buffer.readUUID();
        String employeeName = buffer.readUtf(64);
        int count = PacketDecodeLimits.readVarIntCount(buffer, 64, "employee curio group");
        List<CurioGroup> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            groups.add(new CurioGroup(buffer.readUtf(64),
                    PacketDecodeLimits.readVarIntCount(buffer, 64, "employee curio slot")));
        }
        return new EmployeeInventoryMenu(containerId, inventory, employeeId, employeeName, groups);
    }

    public static void writeOpenData(FriendlyByteBuf buffer, EmployeeServerPlayer employee,
                                     List<CurioGroup> groups) {
        buffer.writeUUID(employee.getUUID());
        buffer.writeUtf(employee.employeeRecord().name(), 64);
        int groupCount = Math.min(groups.size(), 64);
        buffer.writeVarInt(groupCount);
        for (int index = 0; index < groupCount; index++) {
            CurioGroup group = groups.get(index);
            buffer.writeUtf(group.identifier(), 64);
            buffer.writeVarInt(Math.max(0, Math.min(group.slots(), 64)));
        }
    }

    public static List<CurioGroup> describeCurios(EmployeeServerPlayer employee) {
        return CuriosApi.getCuriosInventory(employee).resolve()
                .map(handler -> handler.getCurios().entrySet().stream()
                        .filter(entry -> entry.getValue().getSlots() > 0)
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .map(entry -> new CurioGroup(entry.getKey(), entry.getValue().getSlots()))
                        .toList())
                .orElseGet(List::of);
    }

    private void addEmployeeInventory(Container inventory) {
        int startX = (imageWidth - COLUMNS * 18) / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = column + row * COLUMNS + 9;
                addSlot(new PersistingInventorySlot(inventory, index,
                        startX + column * 18, EMPLOYEE_MAIN_Y + row * 18));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new PersistingInventorySlot(inventory, column,
                    startX + column * 18, EMPLOYEE_HOTBAR_Y));
        }
    }

    private void addServerCurios(EmployeeServerPlayer employee, List<CurioGroup> groups) {
        var handler = CuriosApi.getCuriosInventory(employee).resolve().orElse(null);
        Map<String, ICurioStacksHandler> actual = handler == null ? Map.of() : handler.getCurios();
        int flatIndex = 0;
        for (CurioGroup group : groups) {
            ICurioStacksHandler stacksHandler = actual.get(group.identifier());
            int available = stacksHandler == null ? 0 : stacksHandler.getSlots();
            for (int slot = 0; slot < group.slots(); slot++, flatIndex++) {
                int x = 8 + (flatIndex % curioColumns) * 18;
                int y = CURIO_START_Y + (flatIndex / curioColumns) * 18;
                if (slot < available) {
                    addSlot(new PersistingCurioSlot(employee, stacksHandler, slot,
                            group.identifier(), x, y));
                } else {
                    addSlot(new SlotItemHandler(new ItemStackHandler(1) {
                        @Override
                        public boolean isItemValid(int slotIndex, ItemStack stack) {
                            return false;
                        }
                    }, 0, x, y));
                }
                curioSlotIdentifiers.add(group.identifier());
            }
        }
    }

    private void addClientCurios(List<CurioGroup> groups) {
        int total = groups.stream().mapToInt(CurioGroup::slots).sum();
        ItemStackHandler dummy = new ItemStackHandler(Math.max(1, total));
        int flatIndex = 0;
        for (CurioGroup group : groups) {
            for (int slot = 0; slot < group.slots(); slot++, flatIndex++) {
                addSlot(new SlotItemHandler(dummy, flatIndex,
                        8 + (flatIndex % curioColumns) * 18,
                        CURIO_START_Y + (flatIndex / curioColumns) * 18));
                curioSlotIdentifiers.add(group.identifier());
            }
        }
    }

    private void addViewerInventory(Inventory inventory) {
        int startX = (imageWidth - COLUMNS * 18) / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(inventory, column + row * COLUMNS + 9,
                        startX + column * 18, playerMainY + row * 18));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new Slot(inventory, column,
                    startX + column * 18, playerMainY + 58));
        }
    }

    private int calculatePlayerMainY(int curioSlots) {
        int rows = (curioSlots + curioColumns - 1) / curioColumns;
        int curioHeight = rows == 0 ? 10 : rows * 18;
        return CURIO_START_Y + curioHeight + 17;
    }

    @Override
    public boolean stillValid(Player viewer) {
        if (employee == null) return true;
        return viewer.hasPermissions(2)
                && employee.isAlive()
                && !employee.isRemoved()
                && employee.employeeRecord().deployed()
                && !employee.employeeRecord().retired()
                && EmployeeRuntimeManager.activePlayer(employeeId) == employee;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack sourceStack = source.getItem();
        ItemStack original = sourceStack.copy();
        boolean moved;
        if (index < employeeSlotEnd) {
            moved = moveItemStackTo(sourceStack, playerSlotStart, slots.size(), true);
        } else {
            moved = moveItemStackTo(sourceStack, curioSlotStart, curioSlotEnd, false);
            if (!moved) moved = moveItemStackTo(sourceStack, 0, curioSlotStart, false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (sourceStack.isEmpty()) source.set(ItemStack.EMPTY);
        else source.setChanged();
        source.onTake(player, sourceStack);
        persistTarget();
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        persistTarget();
    }

    private void persistTarget() {
        if (employee != null) EmployeeRuntimeManager.persistNow(employeeId);
    }

    public UUID employeeId() {
        return employeeId;
    }

    public String employeeName() {
        return employeeName;
    }

    public int playerMainY() {
        return playerMainY;
    }

    public int playerLabelY() {
        return playerMainY - 12;
    }

    public int imageHeight() {
        return imageHeight;
    }

    public int imageWidth() {
        return imageWidth;
    }

    public int curioSlotCount() {
        return curioSlotIdentifiers.size();
    }

    public String curioIdentifier(int menuSlotIndex) {
        int index = menuSlotIndex - curioSlotStart;
        return index >= 0 && index < curioSlotIdentifiers.size()
                ? curioSlotIdentifiers.get(index) : "";
    }

    public record CurioGroup(String identifier, int slots) {
    }

    static int calculateCurioColumns(List<CurioGroup> groups) {
        int total = groups.stream().mapToInt(group -> Math.max(0, group.slots())).sum();
        return Math.max(COLUMNS, Math.min(MAX_CURIO_COLUMNS, Math.max(1, total)));
    }

    private final class PersistingInventorySlot extends Slot {
        private PersistingInventorySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            persistTarget();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return EmployeeItemWarehouseData.isTransferable(stack) && super.mayPlace(stack);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            persistTarget();
        }
    }

    private final class PersistingCurioSlot extends CurioSlot {
        private PersistingCurioSlot(EmployeeServerPlayer wearer, ICurioStacksHandler handler,
                                    int slot, String identifier, int x, int y) {
            super(wearer, handler.getStacks(), slot, identifier, x, y,
                    handler.getRenders(), handler.canToggleRendering());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            persistTarget();
        }

        @Override
        public void set(ItemStack stack) {
            super.set(stack);
            persistTarget();
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            persistTarget();
        }
    }
}
