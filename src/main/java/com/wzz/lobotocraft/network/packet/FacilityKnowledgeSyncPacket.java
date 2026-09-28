package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class FacilityKnowledgeSyncPacket implements IMessage {
    private CompoundTag knowledge;
    public FacilityKnowledgeSyncPacket() {}
    public FacilityKnowledgeSyncPacket(CompoundTag knowledge) { this.knowledge = knowledge.copy(); }
    @Override public void toBytes(FriendlyByteBuf buf) { buf.writeNbt(knowledge); }
    @Override public void fromBytes(FriendlyByteBuf buf) {
        knowledge = buf.readNbt();
        if (knowledge == null) knowledge = new CompoundTag();
    }
    @Override public boolean sendToClient() { return true; }
    @OnlyIn(Dist.CLIENT)
    @Override public void runClient(NetworkEvent.Context context) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .ifPresent(data -> {
                    data.mergeKnowledge(knowledge);
                    if (net.minecraft.client.Minecraft.getInstance().screen
                            instanceof com.wzz.lobotocraft.client.screen.AbnormalityEncyclopediaScreen screen) {
                        screen.refreshSharedKnowledge(data);
                    } else if (net.minecraft.client.Minecraft.getInstance().screen
                            instanceof com.wzz.lobotocraft.client.screen.AbnormalityWorkScreen screen) {
                        screen.refreshSharedKnowledge(data);
                    } else if (net.minecraft.client.Minecraft.getInstance().screen
                            instanceof com.wzz.lobotocraft.client.screen.RoomMapScreen screen) {
                        screen.refreshSharedKnowledge();
                    }
                });
    }
}
