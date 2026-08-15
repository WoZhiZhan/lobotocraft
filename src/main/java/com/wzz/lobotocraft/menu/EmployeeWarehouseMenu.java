package com.wzz.lobotocraft.menu;

import com.wzz.lobotocraft.employee.EmployeeItemWarehouseData;
import com.wzz.lobotocraft.employee.EmployeeWarehouseService;
import com.wzz.lobotocraft.init.ModMenus;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class EmployeeWarehouseMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int PAGE_SLOTS = COLUMNS * ROWS;
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_COLLECT_ALL = 2;
    public static final int WAREHOUSE_SLOT_Y = 24;
    public static final int PLAYER_LABEL_Y = 140;
    public static final int PLAYER_INVENTORY_Y = 151;
    public static final int PLAYER_HOTBAR_Y = 209;

    private final EmployeeItemWarehouseData warehouse;
    private final PagedContainer pageContainer;
    private final boolean serverMenu;
    private final DataSlot page = DataSlot.standalone();
    private final DataSlot pageCount = DataSlot.standalone();

    public EmployeeWarehouseMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, EmployeeItemWarehouseData.get(((ServerPlayer) inventory.player).server), true);
    }

    private EmployeeWarehouseMenu(int containerId, Inventory inventory,
                                  EmployeeItemWarehouseData warehouse, boolean serverMenu) {
        super(ModMenus.EMPLOYEE_WAREHOUSE.get(), containerId);
        this.warehouse = warehouse;
        this.serverMenu = serverMenu;
        this.pageContainer = new PagedContainer(warehouse);
        addWarehouseSlots();
        addPlayerInventory(inventory);
        addDataSlot(page);
        addDataSlot(pageCount);
        page.set(0);
        pageCount.set(warehouse.pageCount());
    }

    public static EmployeeWarehouseMenu fromNetwork(int containerId, Inventory inventory,
                                                     FriendlyByteBuf ignored) {
        return new EmployeeWarehouseMenu(containerId, inventory,
                new EmployeeItemWarehouseData(), false);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide || player.hasPermissions(2) && AbnormalitySpawnHelper.isCompany(player.level());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!stillValid(player)) return false;
        int target = page.get();
        if (buttonId == BUTTON_PREVIOUS) target--;
        else if (buttonId == BUTTON_NEXT) target++;
        else if (buttonId == BUTTON_COLLECT_ALL) {
            if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)) return false;
            int moved = EmployeeWarehouseService.collectAllActive(serverPlayer.server);
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_warehouse.collected", moved));
            pageCount.set(warehouse.pageCount());
            broadcastChanges();
            return true;
        } else return false;

        target = Math.max(0, Math.min(target, Math.max(0, warehouse.pageCount() - 1)));
        if (target == page.get()) return true;
        page.set(target);
        pageContainer.setPage(target);
        pageCount.set(warehouse.pageCount());
        broadcastChanges();
        return true;
    }

    @Override
    public void broadcastChanges() {
        if (serverMenu) {
            int pages = warehouse.pageCount();
            pageCount.set(pages);
            int boundedPage = Math.max(0, Math.min(page.get(), pages - 1));
            if (boundedPage != page.get()) {
                page.set(boundedPage);
                pageContainer.setPage(boundedPage);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack original = source.getItem().copy();
        if (index < PAGE_SLOTS) {
            ItemStack working = source.getItem();
            if (!moveItemStackTo(working, PAGE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
            if (working.isEmpty()) source.set(ItemStack.EMPTY);
            else source.setChanged();
        } else {
            if (!serverMenu || !EmployeeItemWarehouseData.isTransferable(source.getItem())) return ItemStack.EMPTY;
            ItemStack remainder = warehouse.insert(source.getItem());
            if (remainder.getCount() == source.getItem().getCount()) return ItemStack.EMPTY;
            source.set(remainder);
            pageCount.set(warehouse.pageCount());
        }
        source.onTake(player, source.getItem());
        return original;
    }

    public int page() {
        return page.get();
    }

    public int pageCount() {
        return Math.max(1, pageCount.get());
    }

    private void addWarehouseSlots() {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(pageContainer, column + row * COLUMNS,
                        8 + column * 18, WAREHOUSE_SLOT_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return EmployeeItemWarehouseData.isTransferable(stack);
                    }
                });
            }
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(inventory, column + row * COLUMNS + 9,
                        8 + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    private static final class PagedContainer implements Container {
        private final EmployeeItemWarehouseData data;
        private int page;

        private PagedContainer(EmployeeItemWarehouseData data) {
            this.data = data;
        }

        private void setPage(int page) {
            this.page = Math.max(0, page);
        }

        private int absolute(int slot) {
            return page * PAGE_SLOTS + slot;
        }

        @Override public int getContainerSize() { return PAGE_SLOTS; }
        @Override public boolean isEmpty() {
            for (int slot = 0; slot < PAGE_SLOTS; slot++) if (!getItem(slot).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int slot) { return data.getItem(absolute(slot)); }
        @Override public ItemStack removeItem(int slot, int amount) { return data.removeItem(absolute(slot), amount); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return data.removeItemNoUpdate(absolute(slot)); }
        @Override public void setItem(int slot, ItemStack stack) { data.setItem(absolute(slot), stack); }
        @Override public void setChanged() { data.setDirty(); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            for (int slot = 0; slot < PAGE_SLOTS; slot++) data.setItem(absolute(slot), ItemStack.EMPTY);
        }
    }
}
