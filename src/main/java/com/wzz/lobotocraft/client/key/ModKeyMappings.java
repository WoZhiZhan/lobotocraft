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

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(JUDGEMENT);
    }
}