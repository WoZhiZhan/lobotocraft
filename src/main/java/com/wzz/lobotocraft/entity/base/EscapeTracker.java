package com.wzz.lobotocraft.entity.base;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.EscapeAlertPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端出逃计数器，跟踪当前出逃中的异想体数量并广播警报等级
 */
public class EscapeTracker {

    private static final EscapeTracker INSTANCE = new EscapeTracker();

    /** 当前出逃中的所有异想体总数 */
    private int totalEscapeCount = 0;
    /** 当前出逃中的WAW+ALEPH数量 */
    private int wawAlephEscapeCount = 0;
    /** 当前出逃中的ALEPH数量 */
    private int alephEscapeCount = 0;

    public static EscapeTracker getInstance() { return INSTANCE; }

    private EscapeTracker() {}

    public synchronized void onEscapeStart(AbstractAbnormality entity) {
        RiskLevel risk = entity.getRiskLevel();
        totalEscapeCount++;
        if (risk == RiskLevel.WAW || risk == RiskLevel.ALEPH) wawAlephEscapeCount++;
        if (risk == RiskLevel.ALEPH) alephEscapeCount++;
        broadcast(entity);
    }

    public synchronized void onEscapeStop(AbstractAbnormality entity) {
        RiskLevel risk = entity.getRiskLevel();
        totalEscapeCount = Math.max(0, totalEscapeCount - 1);
        if (risk == RiskLevel.WAW || risk == RiskLevel.ALEPH)
            wawAlephEscapeCount = Math.max(0, wawAlephEscapeCount - 1);
        if (risk == RiskLevel.ALEPH)
            alephEscapeCount = Math.max(0, alephEscapeCount - 1);
        broadcast(entity);
    }

    /**
     * 计算当前警报等级：
     * -1 无出逃
     *  0 任意出逃
     *  1 WAW/ALEPH 1~3只
     *  2 ALEPH >3只（最高优先级）
     */
    public int getCurrentAlertLevel() {
        if (alephEscapeCount > 3) return 2;
        if (wawAlephEscapeCount >= 1) return 1;
        if (totalEscapeCount > 0) return 0;
        return -1;
    }

    private void broadcast(AbstractAbnormality entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        int level = getCurrentAlertLevel();
        EscapeAlertPacket packet = new EscapeAlertPacket(level);
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            MessageLoader.getLoader().sendToPlayer(player, packet);
        }
    }
}