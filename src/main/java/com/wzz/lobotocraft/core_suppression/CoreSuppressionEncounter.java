package com.wzz.lobotocraft.core_suppression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public interface CoreSuppressionEncounter {
    default Component getStartDenialReason(ServerPlayer owner) {
        return null;
    }

    default boolean allowsActiveBreaches() {
        return false;
    }

    boolean start(MinecraftServer server, CoreSuppressionData suppression);

    default boolean debugStart(MinecraftServer server, CoreSuppressionData suppression) {
        return start(server, suppression);
    }

    void tick(MinecraftServer server, CoreSuppressionData suppression);

    void finish(MinecraftServer server, boolean success);

    default boolean ownsParticipantLifecycle() {
        return false;
    }

    default int visualStage(MinecraftServer server) {
        return 0;
    }

    default int mechanicState(MinecraftServer server) {
        return 0;
    }

    default float incomingDamageMultiplier(ServerPlayer target, DamageSource source) {
        return 1.0F;
    }

    default boolean freezesAiEmployees() {
        return false;
    }

    default void onPlayerLogin(ServerPlayer player) {
    }

    default void onPlayerLogout(ServerPlayer player) {
    }

    default void onQliphothMeltdown(MinecraftServer server, CoreSuppressionData suppression,
                                     int overloadLevel) {
    }

    default void onServerStartup(MinecraftServer server, CoreSuppressionData suppression) {
    }
}
