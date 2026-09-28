package com.lobotocraft.facility;

import com.wzz.lobotocraft.init.ModDimensions;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.lobotocraft.facility.data.FacilityDayData;

/** Server-global pause switch for facility simulation controls. */
public final class FacilityPauseState {
    private static final int TOGGLE_COOLDOWN_TICKS = 40;
    private static final Map<MinecraftServer, State> STATES = new IdentityHashMap<>();

    private FacilityPauseState() {
    }

    public static boolean isPaused(MinecraftServer server) {
        return FacilityDayData.get(server.overworld()).isPaused();
    }

    public static int cooldownTicks(MinecraftServer server) {
        State state = state(server);
        return Math.max(0, state.nextToggleTick - server.getTickCount());
    }

    public static ToggleResult toggle(ServerPlayer player) {
        if (player == null || player.level().dimension() != ModDimensions.LOBOTO_KEY) {
            return ToggleResult.NOT_IN_FACILITY;
        }
        MinecraftServer server = player.server;
        State state = state(server);
        int remaining = cooldownTicks(server);
        FacilityDayData data = FacilityDayData.get(server.overworld());
        if (remaining > 0) return new ToggleResult(data.isPaused(), remaining, false);
        data.setPaused(!data.isPaused());
        state.nextToggleTick = server.getTickCount() + TOGGLE_COOLDOWN_TICKS;
        return new ToggleResult(data.isPaused(), TOGGLE_COOLDOWN_TICKS, true);
    }

    public static void clear(MinecraftServer server) {
        if (server != null && server.overworld() != null) {
            FacilityDayData.get(server.overworld()).setPaused(false);
        }
        STATES.remove(server);
    }

    private static State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new State());
    }

    private static final class State {
        private int nextToggleTick;
    }

    public record ToggleResult(boolean paused, int cooldownTicks, boolean changed) {
        private static final ToggleResult NOT_IN_FACILITY = new ToggleResult(false, 0, false);
    }
}
