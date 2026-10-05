package com.wzz.lobotocraft.entity.control;

import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.abnormality.EntityBigBadWolf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** The swallowed body is inactive, not a hostile combatant. Owns reversible player flags. */
public final class WolfSwallowControl {
    private static final String ACTIVE = "wolf_swallowed"; // preserve legacy saves
    private static final String PREVIOUS = "lobotocraft_wolf_previous_control";
    private static final String SOURCE = "lobotocraft_wolf_source";

    private WolfSwallowControl() {}

    public static boolean isActive(ServerPlayer player) {
        return player != null && player.getPersistentData().getBoolean(ACTIVE);
    }

    public static void start(ServerPlayer player, EntityBigBadWolf wolf) {
        if (!isActive(player)) {
            var previous = new CompoundTag();
            previous.putInt("GameMode", player.gameMode.getGameModeForPlayer().getId());
            previous.putBoolean("Invisible", player.isInvisible());
            previous.putBoolean("Invulnerable", player.isInvulnerable());
            player.getPersistentData().put(PREVIOUS, previous);
        }
        player.getPersistentData().putBoolean(ACTIVE, true);
        player.getPersistentData().putUUID(SOURCE, wolf.getUUID());
        if (player instanceof EmployeeServerPlayer employee)
            EmployeeRuntimeManager.cancelOrder(employee.employeeRecord(), "被狼吞入，任务中断");
        player.setGameMode(GameType.SPECTATOR);
        player.setCamera(wolf);
        tick(player);
    }

    public static void tick(ServerPlayer player) {
        if (!isActive(player)) return;
        if (player instanceof EmployeeServerPlayer employee) employee.clearNavigationInput();
        player.setDeltaMovement(Vec3.ZERO);
        player.setInvisible(true);
        player.setInvulnerable(true);
    }

    public static boolean belongsTo(ServerPlayer player, EntityBigBadWolf wolf) {
        return isActive(player) && (!player.getPersistentData().hasUUID(SOURCE)
                || player.getPersistentData().getUUID(SOURCE).equals(wolf.getUUID()));
    }

    public static void clear(ServerPlayer player) {
        if (!isActive(player)) return;
        var data = player.getPersistentData();
        var previous = data.getCompound(PREVIOUS);
        player.setCamera(player);
        player.setGameMode(previous.contains("GameMode")
                ? GameType.byId(previous.getInt("GameMode")) : GameType.SURVIVAL);
        player.setInvisible(previous.getBoolean("Invisible"));
        player.setInvulnerable(previous.getBoolean("Invulnerable"));
        player.setDeltaMovement(Vec3.ZERO);
        if (player instanceof EmployeeServerPlayer employee) employee.clearNavigationInput();
        data.remove(ACTIVE);
        data.remove(PREVIOUS);
        data.remove(SOURCE);
    }

    public static ServerPlayer findPlayer(MinecraftServer server, java.util.UUID id) {
        var human = server.getPlayerList().getPlayer(id);
        if (human != null) return human;
        var employee = EmployeeRuntimeManager.activePlayer(id);
        if (employee != null && employee.server == server) return employee;
        for (var level : server.getAllLevels())
            if (level.getEntity(id) instanceof ServerPlayer player) return player;
        return null;
    }
}
