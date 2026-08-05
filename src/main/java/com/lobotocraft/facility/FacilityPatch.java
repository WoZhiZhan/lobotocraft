package com.lobotocraft.facility;

import com.mojang.logging.LogUtils;
import com.lobotocraft.facility.network.FacilityNetwork;
import com.lobotocraft.facility.conversation.CarmenConversationEvents;
import com.lobotocraft.facility.damage.DamageHistoryEvents;
import com.lobotocraft.facility.player.PlayerStatusEvents;
import com.wzz.lobotocraft.ModMain;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

public final class FacilityPatch {
    public static final String MOD_ID = ModMain.MODID;
    public static final String ASSET_NAMESPACE = "lobotocraft_facility";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;

    private FacilityPatch() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        FacilityNetwork.register();
        MinecraftForge.EVENT_BUS.register(FacilityEvents.class);
        MinecraftForge.EVENT_BUS.register(DamageHistoryEvents.class);
        MinecraftForge.EVENT_BUS.register(CarmenConversationEvents.class);
        MinecraftForge.EVENT_BUS.register(PlayerStatusEvents.class);
        LOGGER.info("LobotoCraft integrated facility system loaded");
    }
}
