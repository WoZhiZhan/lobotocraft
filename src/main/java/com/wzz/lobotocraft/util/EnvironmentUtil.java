package com.wzz.lobotocraft.util;

import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;

public class EnvironmentUtil {
    public static boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    public static boolean isClient(Level level) {
        if (level == null)
            return isClient();
        return level.isClientSide;
    }

    public static boolean isObfuscationEnvironment() {
        return FMLLoader.isProduction() || System.getProperties().containsKey("production");
    }
}
