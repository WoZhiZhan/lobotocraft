package com.wzz.lobotocraft.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.wzz.lobotocraft.ModMain;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModKeyMappings {

    public static final String CATEGORY = "key.categories.lobotocraft";

    /** R 键：进入 / 退出宣判（架枪）状态 */
    public static final KeyMapping JUDGEMENT = new KeyMapping(
            "key.lobotocraft.judgement",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );

    public static final KeyMapping END_DAY = new KeyMapping(
            "key.lobotocraft.end_day",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY
    );

    public static final KeyMapping REJECT_DAY_VOTE = new KeyMapping(
            "key.lobotocraft.reject_day_vote",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY
    );

    public static final KeyMapping OPEN_FACILITY_MANAGEMENT = new KeyMapping(
            "key.lobotocraft.open_facility_management",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(JUDGEMENT);
        event.register(END_DAY);
        event.register(REJECT_DAY_VOTE);
        event.register(OPEN_FACILITY_MANAGEMENT);
    }
}
