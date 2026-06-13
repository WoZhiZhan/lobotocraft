package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.api.ObfString;
import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;

public class ThreadExecutor {
    
    /**
     * 在主线程执行任务（客户端或服务端通用）
     */
    public static void executeOnMainThread(LivingEntity living, Runnable task) {
        if (living == null) {
            task.run();
            return;
        }
        Level level = living.level();
        if (level == null) {
            task.run();
            return;
        }
        if (level.isClientSide()) {
            try {
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                Object minecraft = minecraftClass.getMethod(new ObfString("getInstance", "m_91087_").value()).invoke(null);
                if (minecraft != null) {
                    boolean isSameThread = (Boolean) minecraftClass.getMethod(new ObfString("isSameThread", "m_18695_").value()).invoke(minecraft);
                    if (isSameThread) {
                        task.run();
                    } else {
                        minecraftClass.getMethod("execute", Runnable.class).invoke(minecraft, task);
                    }
                    return;
                }
            } catch (Exception e) {
                ModLogger.getLogger().warn("Failed to execute on client main thread, running directly", e);
            }
            task.run();
        } else {
            MinecraftServer server = level.getServer();
            if (server == null) {
                task.run();
                return;
            }
            if (server.isSameThread()) {
                task.run();
            } else {
                server.execute(task);
            }
        }
    }
    
    /**
     * 检查当前是否在主线程
     */
    public static boolean isOnMainThread(Level level) {
        if (level.isClientSide()) {
            try {
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                Object minecraft = minecraftClass.getMethod(new ObfString("getInstance", "m_91087_").value()).invoke(null);
                if (minecraft != null) {
                    return (Boolean) minecraftClass.getMethod(new ObfString("isSameThread", "m_18695_").value()).invoke(minecraft);
                }
            } catch (Exception e) {
                return false;
            }
            return false;
        } else {
            MinecraftServer server = level.getServer();
            return server != null && server.isSameThread();
        }
    }
}