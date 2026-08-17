package com.wzz.lobotocraft.core_suppression;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahEncounterService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.Map;

public final class CoreSuppressionEncounterRegistry {
    private static final Map<CoreSuppressionType, CoreSuppressionEncounter> ENCOUNTERS =
            new EnumMap<>(CoreSuppressionType.class);

    static {
        ENCOUNTERS.put(CoreSuppressionType.GEBURAH, GeburahEncounterService.INSTANCE);
    }

    private CoreSuppressionEncounterRegistry() {
    }

    public static Component getStartDenialReason(ServerPlayer owner, CoreSuppressionType type) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(type);
        return encounter == null ? null : encounter.getStartDenialReason(owner);
    }

    public static boolean allowsActiveBreaches(CoreSuppressionType type) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(type);
        return encounter != null && encounter.allowsActiveBreaches();
    }

    public static boolean start(MinecraftServer server, CoreSuppressionData suppression) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(suppression.getActiveType());
        return encounter == null || encounter.start(server, suppression);
    }

    public static boolean debugStart(MinecraftServer server, CoreSuppressionData suppression) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(suppression.getActiveType());
        return encounter == null || encounter.debugStart(server, suppression);
    }

    public static void tick(MinecraftServer server, CoreSuppressionData suppression) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(suppression.getActiveType());
        if (encounter != null) encounter.tick(server, suppression);
    }

    public static void finish(MinecraftServer server, CoreSuppressionType type, boolean success) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(type);
        if (encounter != null) encounter.finish(server, success);
    }

    public static boolean ownsParticipantLifecycle(CoreSuppressionType type) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(type);
        return encounter != null && encounter.ownsParticipantLifecycle();
    }

    public static int visualStage(MinecraftServer server, CoreSuppressionType type) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(type);
        return encounter == null ? 0 : encounter.visualStage(server);
    }

    public static boolean freezesAiEmployees(CoreSuppressionType type) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(type);
        return encounter != null && encounter.freezesAiEmployees();
    }

    public static void onPlayerLogin(CoreSuppressionType type, ServerPlayer player) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(type);
        if (encounter != null) encounter.onPlayerLogin(player);
    }

    public static void onPlayerLogout(CoreSuppressionType type, ServerPlayer player) {
        CoreSuppressionEncounter encounter = ENCOUNTERS.get(type);
        if (encounter != null) encounter.onPlayerLogout(player);
    }
}
