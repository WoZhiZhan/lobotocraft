package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class UniqueCommandTargetsPacket implements IMessage {
    public record Target(UUID id, String name) {}
    private UUID source;
    private List<Target> targets;
    public UniqueCommandTargetsPacket() {}
    public UniqueCommandTargetsPacket(UUID source, List<Target> targets) {
        this.source = source;
        this.targets = List.copyOf(targets);
    }
    @Override public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(source);
        buf.writeVarInt(targets.size());
        for (Target target : targets) {
            buf.writeUUID(target.id());
            String name = target.name();
            buf.writeUtf(name.substring(0, Math.min(256, name.length())), 256);
        }
    }
    @Override public void fromBytes(FriendlyByteBuf buf) {
        source = buf.readUUID();
        int count = buf.readVarInt();
        if (count < 0 || count > 128) throw new IllegalArgumentException("Invalid unique command target count");
        targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) targets.add(new Target(buf.readUUID(), buf.readUtf(256)));
    }
    @Override public boolean sendToClient() { return true; }
    @OnlyIn(Dist.CLIENT)
    @Override public void runClient(NetworkEvent.Context context) {
        var client = net.minecraft.client.Minecraft.getInstance();
        client.setScreen(new com.wzz.lobotocraft.client.screen.UniqueCommandTargetScreen(
                source, targets, client.screen));
    }
}
