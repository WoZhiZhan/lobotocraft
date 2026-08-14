package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.menu.EmployeeInventoryMenu;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class OpenEmployeeInventoryPacket implements IMessage {
    private UUID employeeId;

    public OpenEmployeeInventoryPacket() {
    }

    public OpenEmployeeInventoryPacket(UUID employeeId) {
        this.employeeId = employeeId;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        employeeId = buffer.readUUID();
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUUID(employeeId);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || !sender.hasPermissions(2)
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(sender.level())) return;
        EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(employeeId);
        if (employee == null || employee.isRemoved() || !employee.isAlive()
                || !employee.employeeRecord().deployed() || employee.employeeRecord().retired()) {
            sender.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.employee_inventory.unavailable"));
            return;
        }

        List<EmployeeInventoryMenu.CurioGroup> groups = EmployeeInventoryMenu.describeCurios(employee);
        NetworkHooks.openScreen(sender, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("screen.lobotocraft.employee_inventory.title",
                        employee.employeeRecord().name());
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return new EmployeeInventoryMenu(containerId, inventory, employee, groups);
            }
        }, buffer -> EmployeeInventoryMenu.writeOpenData(buffer, employee, groups));
    }
}
