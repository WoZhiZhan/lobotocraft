package com.wzz.lobotocraft.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class LambdaPacketHandler {
    private static final Map<String, BiConsumer<CompoundTag, Minecraft>> clientActions = new HashMap<>();
    private static final Map<String, MessageLoader.ServerLambda> serverActions = new HashMap<>();

    public static void registerClient(String actionId, BiConsumer<CompoundTag, Minecraft> action) {
        clientActions.put(actionId, action);
    }

    public static void registerServer(String actionId, MessageLoader.ServerLambda action) {
        serverActions.put(actionId, action);
    }

    public static void runClient(String actionId, CompoundTag data) {
        BiConsumer<CompoundTag, Minecraft> action = clientActions.get(actionId);
        if (action != null) {
            action.accept(data, Minecraft.getInstance());
            clientActions.remove(actionId);
        }
    }

    public static void runServer(String actionId, CompoundTag data, ServerPlayer player) {
        MessageLoader.ServerLambda action = serverActions.get(actionId);
        if (action != null && player != null) {
            action.run(data, player);
            serverActions.remove(actionId);
        }
    }
}
