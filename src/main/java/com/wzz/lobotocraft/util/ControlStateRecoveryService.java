package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.effect.KissEffect;
import com.wzz.lobotocraft.entity.abnormality.EntityLargeBird;
import com.wzz.lobotocraft.entity.control.HostileControlState;
import com.wzz.lobotocraft.entity.control.WolfSwallowControl;
import com.wzz.lobotocraft.event.listener.PlayerControlLock;
import net.minecraft.server.level.ServerPlayer;

public final class ControlStateRecoveryService {
    private ControlStateRecoveryService() {
    }

    public static void clearTransientControls(ServerPlayer player) {
        if (player == null) return;
        TimerEntry.shutdown(player);
        PlayerControlLock.unlock(player);
        HostileControlState.clearForRecovery(player);
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

        WolfSwallowControl.clear(player);
    }
}
