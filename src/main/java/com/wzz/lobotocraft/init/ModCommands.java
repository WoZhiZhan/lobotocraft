package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.command.CommandRegistry;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandRegistry.register(event.getDispatcher());
    }
}
