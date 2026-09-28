package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.UniqueWorkRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class UniqueWorkStatePacket implements IMessage {
    private UUID source;
    private String selectedName;
    private UniqueWorkRequest.Stage stage;
    private List<String> names;
    public UniqueWorkStatePacket() {}
    public UniqueWorkStatePacket(UUID source, String name, UniqueWorkRequest.Stage stage, List<String> names) {
        this.source = source;
        this.selectedName = name;
        this.stage = stage;
        this.names = names;
    }
    @Override public void fromBytes(FriendlyByteBuf buf) {
        source = buf.readUUID();
        selectedName = buf.readUtf(256);
        stage = buf.readEnum(UniqueWorkRequest.Stage.class);
        int count = buf.readVarInt();
        if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid lottery preview size");
        names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) names.add(buf.readUtf(256));
    }
    @Override public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(source);
        buf.writeUtf(shortName(selectedName), 256);
        buf.writeEnum(stage);
        buf.writeVarInt(names.size());
        for (String name : names) buf.writeUtf(shortName(name), 256);
    }
    private static String shortName(String name) {
        return name.length() > 256 ? name.substring(0, 256) : name;
    }
    @Override public boolean sendToClient() { return true; }
    @OnlyIn(Dist.CLIENT)
    @Override public void runClient(NetworkEvent.Context context) {
        com.wzz.lobotocraft.client.screen.UniqueWorkLotteryScreen.receive(source, selectedName, stage, names);
    }
}
