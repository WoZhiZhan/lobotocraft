package com.wzz.lobotocraft.client.key;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ToggleJudgementPacket;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.lobotocraft.facility.client.ClientFacilityState;
import com.lobotocraft.facility.network.DayVoteActionPacket;
import com.lobotocraft.facility.network.FacilityNetwork;
import com.wzz.lobotocraft.init.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class ClientKeyHandler {
    private static final int END_DAY_HOLD_TICKS = 30;
    private static final EndDayHoldController END_DAY_HOLD =
            new EndDayHoldController(END_DAY_HOLD_TICKS);

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            resetEndDayHold();
            return;
        }

        while (ModKeyMappings.JUDGEMENT.consumeClick()) {
            MessageLoader.getLoader().sendToServer(new ToggleJudgementPacket());
        }
        while (ModKeyMappings.OPEN_FACILITY_MANAGEMENT.consumeClick()) {
            MessageLoader.getLoader().sendToServer(new RequestFacilityManagementPacket(false));
        }

        var state = ClientFacilityState.get();
        if (mc.level.dimension() != ModDimensions.LOBOTO_KEY) {
            resetEndDayHold();
            return;
        }
        if (state.voteActive()) {
            resetEndDayHold();
            while (ModKeyMappings.END_DAY.consumeClick()) {
                FacilityNetwork.sendToServer(new DayVoteActionPacket(DayVoteActionPacket.Action.APPROVE));
            }
            while (ModKeyMappings.REJECT_DAY_VOTE.consumeClick()) {
                FacilityNetwork.sendToServer(new DayVoteActionPacket(DayVoteActionPacket.Action.REJECT));
            }
            return;
        }
        while (ModKeyMappings.REJECT_DAY_VOTE.consumeClick()) {
            // Drain stale clicks outside a vote.
        }
        while (ModKeyMappings.END_DAY.consumeClick()) {
            // Holding uses isDown(). Drain click counts so an earlier press
            // cannot be misread as an approval if a multiplayer vote starts later.
        }
        if (END_DAY_HOLD.tick(state.dayReady(), ModKeyMappings.END_DAY.isDown())) {
            FacilityNetwork.sendToServer(new DayVoteActionPacket(DayVoteActionPacket.Action.START));
        }
    }

    public static float endDayHoldProgress() {
        return END_DAY_HOLD.progress();
    }

    private static void resetEndDayHold() {
        END_DAY_HOLD.reset();
    }
}
