package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.effect.KissEffect;
import com.wzz.lobotocraft.entity.abnormality.EntityLargeBird;
import com.wzz.lobotocraft.entity.abnormality.EntityRedShoes;
import com.wzz.lobotocraft.event.listener.PlayerControlLock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public final class ControlStateRecoveryService {
    private ControlStateRecoveryService() {
    }

    public static void clearTransientControls(ServerPlayer player) {
        if (player == null) return;
        TimerEntry.shutdown(player);
        PlayerControlLock.unlock(player);
        EntityRedShoes.clearControlForRecovery(player);
        EntityLargeBird.clearLargeBirdCharm(player);
        BuffUtil.removeKiss(player);
        KissEffect.clearFreezeState(player);
        MovementLockUtil.clearAll(player);

        player.getPersistentData().remove("isSnowQueen");
        player.getPersistentData().remove("isSnowQueenDuel");
        player.getPersistentData().remove("isSuperWolf");
        player.getPersistentData().remove("notLargeBirdCharm");
        player.getPersistentData().remove("isAddRedHat");
        player.getPersistentData().remove("isAddBigBadwolf");
        player.setTicksFrozen(0);

        if (player.getPersistentData().getBoolean("wolf_swallowed")) {
            player.getPersistentData().remove("wolf_swallowed");
            player.setCamera(player);
            player.setGameMode(GameType.SURVIVAL);
        }
    }
}
