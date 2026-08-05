package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.LlmConfigScreen;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class OpenLlmConfigPacket implements IMessage {
    private boolean enabled;
    private String endpoint;
    private String model;
    private int timeoutSeconds;
    private boolean hasApiKey;
    private boolean keyFromEnvironment;

    public OpenLlmConfigPacket() { }

    public OpenLlmConfigPacket(boolean enabled, String endpoint, String model, int timeoutSeconds,
                               boolean hasApiKey, boolean keyFromEnvironment) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.hasApiKey = hasApiKey;
        this.keyFromEnvironment = keyFromEnvironment;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        enabled = buf.readBoolean();
        endpoint = buf.readUtf(512);
        model = buf.readUtf(128);
        timeoutSeconds = buf.readVarInt();
        hasApiKey = buf.readBoolean();
        keyFromEnvironment = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeUtf(endpoint, 512);
        buf.writeUtf(model, 128);
        buf.writeVarInt(timeoutSeconds);
        buf.writeBoolean(hasApiKey);
        buf.writeBoolean(keyFromEnvironment);
    }

    @Override public boolean sendToClient() { return true; }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft.getInstance().setScreen(new LlmConfigScreen(enabled, endpoint, model,
                timeoutSeconds, hasApiKey, keyFromEnvironment));
    }
}
