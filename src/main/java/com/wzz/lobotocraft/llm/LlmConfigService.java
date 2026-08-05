package com.wzz.lobotocraft.llm;

import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenLlmConfigPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LlmConfigService {
    public static final int CONFIG_PERMISSION_LEVEL = 4;

    private LlmConfigService() {
    }

    public static void open(ServerPlayer player) {
        if (!player.hasPermissions(CONFIG_PERMISSION_LEVEL)) {
            player.sendSystemMessage(Component.literal("§c没有 LLM 配置权限。"));
            return;
        }
        LlmConfig.State state = LlmConfig.state();
        MessageLoader.getLoader().sendToPlayer(player, new OpenLlmConfigPacket(
                state.enabled(), state.endpoint(), state.model(), state.timeoutSeconds(),
                LlmConfig.hasApiKey(), LlmConfig.keyFromEnvironment()));
    }
}
