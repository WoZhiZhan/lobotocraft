package com.wzz.lobotocraft;

import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.work.WorkSpeedModifierManager;
import com.wzz.lobotocraft.work.modifier.ContinuousWorkSpeedModifier;
import com.wzz.lobotocraft.work.modifier.CoreSuppressionSpeedModifier;
import com.wzz.lobotocraft.work.modifier.TT2ProtocolSpeedModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ModMain.MODID)
public class ModMain {

    public static final String MODID = "lobotocraft";

    @SuppressWarnings("removal")
    public ModMain() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::completeSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModEntities.ENTITIES.register(modEventBus);
        ModItems.REGISTRY.register(modEventBus);
        ModTabs.REGISTRY.register(modEventBus);
        ModParticleTypes.REGISTRY.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModMobEffects.EFFECTS.register(modEventBus);
        ModSounds.REGISTRY.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModWorldGen.register(modEventBus);
        MessageLoader.getLoader();
//        System.setProperty("java.awt.headless", "false");
//        com.wzz.lobotocraft.item.debug.DebugTunerFrame.open();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void completeSetup(final FMLLoadCompleteEvent event) {
        WorkSpeedModifierManager.registerGlobalModifier(ContinuousWorkSpeedModifier.getInstance());
        WorkSpeedModifierManager.registerGlobalModifier(TT2ProtocolSpeedModifier.getInstance());
        WorkSpeedModifierManager.registerGlobalModifier(CoreSuppressionSpeedModifier.getInstance());
    }
}
