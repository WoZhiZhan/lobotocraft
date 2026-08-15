package com.lobotocraft.facility;

import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.OrdealRewardPolicy;
import com.lobotocraft.facility.data.OrdealTier;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket;
import com.wzz.lobotocraft.util.SuppressorCounterUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/** Performs the one-time company-wide settlement when an ordeal ends. */
public final class OrdealRewardService {
    private OrdealRewardService() {
    }

    public static void complete(MinecraftServer server, OrdealTier tier) {
        if (server == null) return;
        int percent = OrdealRewardPolicy.rewardPercent(tier);
        if (percent <= 0) return;

        FacilityDayData facility = FacilityDayData.get(server.overworld());
        int energyBefore = facility.getEnergy();
        facility.addEnergy(OrdealRewardPolicy.rewardForMaximum(facility.getEnergyQuota(), tier));
        int energyAdded = facility.getEnergy() - energyBefore;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isEligibleCompanyPlayer(player)) continue;
            int capacity = SuppressorCounterUtil.capacity(player);
            int counterBefore = SuppressorCounterUtil.get(player);
            SuppressorCounterUtil.restore(player,
                    OrdealRewardPolicy.rewardForMaximum(capacity, tier));
            int counterAfter = SuppressorCounterUtil.get(player);
            MessageLoader.getLoader().sendToPlayer(player,
                    new SuppressorCountSyncPacket(counterAfter, capacity));
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.ordeal.reward", percent, energyAdded,
                    counterAfter - counterBefore)
                    .withStyle(ChatFormatting.GOLD), false);
        }

        FacilityManager.syncAll(server, facility);
    }

    private static boolean isEligibleCompanyPlayer(ServerPlayer player) {
        return !(player instanceof EmployeeServerPlayer)
                && !(player instanceof FakePlayer)
                && !player.getPersistentData().getBoolean("lobotocraft_ai_employee")
                && !player.isSpectator()
                && player.level().dimension() == ModDimensions.LOBOTO_KEY;
    }
}
