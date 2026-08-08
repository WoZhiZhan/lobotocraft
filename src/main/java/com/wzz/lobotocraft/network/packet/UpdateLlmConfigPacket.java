package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.llm.LlmConfig;
import com.wzz.lobotocraft.llm.LlmConfigService;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class UpdateLlmConfigPacket implements IMessage {
    private boolean enabled;
    private String endpoint;
    private String model;
    private int timeoutSeconds;
    private String keyAction;
    private String replacementKey;

    public UpdateLlmConfigPacket() { }

    public UpdateLlmConfigPacket(boolean enabled, String endpoint, String model,
                                 int timeoutSeconds, String keyAction, String replacementKey) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.keyAction = keyAction;
        this.replacementKey = replacementKey;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        enabled = buf.readBoolean();
        endpoint = buf.readUtf(512);
        model = buf.readUtf(128);
        timeoutSeconds = buf.readVarInt();
        keyAction = buf.readUtf(16);
        replacementKey = buf.readUtf(512);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeUtf(endpoint, 512);
        buf.writeUtf(model, 128);
        buf.writeVarInt(timeoutSeconds);
        buf.writeUtf(keyAction, 16);
        buf.writeUtf(replacementKey, 512);
    }

    @Override public boolean sendToServer() { return true; }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || !player.hasPermissions(LlmConfigService.CONFIG_PERMISSION_LEVEL)
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(player.level())) return;
        try {
            LlmConfig.update(enabled, endpoint, model, timeoutSeconds,
                    LlmConfig.KeyAction.byId(keyAction), replacementKey);
            player.sendSystemMessage(Component.literal("§aLLM 热配置已应用。"));
            LlmConfigService.open(player);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendSystemMessage(Component.literal("§cLLM 配置未保存：" + exception.getMessage()));
        }
    }
}
