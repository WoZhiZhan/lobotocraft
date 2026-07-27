package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.RoomMapScreen;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.world.data.AbnormalityCandidate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：附近异想体清单，交给监控界面弹出"更改住户"选单。
 */
public class NearbyAbnormalitiesPacket implements IMessage {

    private BlockPos roomPos;
    private List<AbnormalityCandidate> candidates;

    public NearbyAbnormalitiesPacket() {
    }

    public NearbyAbnormalitiesPacket(BlockPos roomPos, List<AbnormalityCandidate> candidates) {
        this.roomPos = roomPos;
        this.candidates = candidates;
    }

    public BlockPos getRoomPos() {
        return roomPos;
    }

    public List<AbnormalityCandidate> getCandidates() {
        return candidates;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.roomPos = buf.readBlockPos();
        int size = buf.readVarInt();
        this.candidates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.candidates.add(AbnormalityCandidate.read(buf));
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(roomPos);
        buf.writeVarInt(candidates.size());
        for (AbnormalityCandidate candidate : candidates) {
            candidate.write(buf);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        if (Minecraft.getInstance().screen instanceof RoomMapScreen screen) {
            screen.showAbnormalityPicker(roomPos, candidates);
        }
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
