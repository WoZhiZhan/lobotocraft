package com.wzz.lobotocraft.event.definition.company;

import com.wzz.lobotocraft.capability.CompanyDailyData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 应用装备抗性时触发（可取消）
 */
@Cancelable
public class CompanyResistanceApplyEvent extends Event {
    private final ServerPlayer player;
    private final CompanyDailyData data;
    private final double redResistance;
    private final double whiteResistance;
    private final double blackResistance;
    private final double blueResistance;
    private String cancelReason = "";

    public CompanyResistanceApplyEvent(ServerPlayer player, CompanyDailyData data,
                                        double redResistance, double whiteResistance,
                                        double blackResistance, double blueResistance) {
        this.player = player;
        this.data = data;
        this.redResistance = redResistance;
        this.whiteResistance = whiteResistance;
        this.blackResistance = blackResistance;
        this.blueResistance = blueResistance;
    }

    public ServerPlayer getPlayer() { return player; }
    public CompanyDailyData getData() { return data; }
    public double getRedResistance() { return redResistance; }
    public double getWhiteResistance() { return whiteResistance; }
    public double getBlackResistance() { return blackResistance; }
    public double getBlueResistance() { return blueResistance; }
    
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String reason) { this.cancelReason = reason; }
}