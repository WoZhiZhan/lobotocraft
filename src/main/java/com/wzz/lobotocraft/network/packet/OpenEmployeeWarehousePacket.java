package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.menu.EmployeeWarehouseMenu;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

public final class OpenEmployeeWarehousePacket implements IMessage {
    @Override public void fromBytes(FriendlyByteBuf buffer) { }
    @Override public void toBytes(FriendlyByteBuf buffer) { }
    @Override public boolean sendToServer() { return true; }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || !sender.hasPermissions(2)) return;
        if (!AbnormalitySpawnHelper.isCompany(sender.level())) {
            sender.sendSystemMessage(Component.literal("请回到公司后再管理员工物品。"));
            return;
        }
        NetworkHooks.openScreen(sender, new SimpleMenuProvider(
                (containerId, inventory, player) -> new EmployeeWarehouseMenu(containerId, inventory),
                Component.literal("员工物品总仓")));
    }
}
