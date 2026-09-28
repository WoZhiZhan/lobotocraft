package com.wzz.lobotocraft.network.packet;

import com.lobotocraft.facility.FacilityManager;
import com.lobotocraft.facility.FacilityPauseState;
import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Toggles the server-global facility simulation pause switch. */
public final class ToggleFacilityPausePacket implements IMessage {
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        FacilityPauseState.ToggleResult result = FacilityPauseState.toggle(player);
        if (result.changed()) {
            FacilityManager.syncAll(player.server,
                    FacilityDayData.get(player.server.overworld()));
        }
    }
}
