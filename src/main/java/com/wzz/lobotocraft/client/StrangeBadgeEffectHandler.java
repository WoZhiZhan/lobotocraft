package com.wzz.lobotocraft.client;

import com.wzz.lobotocraft.natives.DisplayMessage;
import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;

/**
 * 奇怪的工牌效果处理器
 * 处理恐慌状态下的所有客户端效果
 */
@OnlyIn(Dist.CLIENT)
public class StrangeBadgeEffectHandler {

    private static boolean isTriggered = false;

    /**
     * 触发效果
     */
    public static void triggerEffect() {
        if (isTriggered) {
            return;
        }
        isTriggered = true;
        new Thread(() -> {
            try {
                File saveFolderPath = getSaveFolderBeforeExit();
                launchPopups();
                Thread.sleep(3500);
                exitToTitle();
                Thread.sleep(2000);
                renameLevelDatOnly(saveFolderPath);
                Thread.sleep(500);

                createExternalRenameTask(saveFolderPath);
                Thread.sleep(500);

                System.exit(1);
            } catch (Exception e) {
                ModLogger.error("效果执行失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isTriggered = false;
            }
        }, "StrangeBadge-Effect").start();
    }

    /**
     * 只修改 level.dat，不重命名文件夹
     */
    private static void renameLevelDatOnly(File saveFolderPath) {
        try {
            if (saveFolderPath == null) {
                ModLogger.error("存档路径为null");
                return;
            }
            com.wzz.lobotocraft.util.SaveRenameUtil
                    .renameLevelDatOnly(saveFolderPath, "我不准你忘记我");
        } catch (Exception e) {
            ModLogger.error("重命名level.dat异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建外部重命名任务
     */
    private static void createExternalRenameTask(File saveFolderPath) {
        try {
            if (saveFolderPath == null) {
                return;
            }

            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("win")) {
                ModLogger.warn("外部重命名仅支持Windows");
                return;
            }

            File parentDir = saveFolderPath.getParentFile();
            String oldName = saveFolderPath.getName();
            String newName = sanitizeFolderName("我不准你忘记我");

            // 如果目标已存在，添加后缀
            File targetFolder = new File(parentDir, newName);
            int suffix = 1;
            while (targetFolder.exists()) {
                targetFolder = new File(parentDir, newName + "_" + suffix);
                suffix++;
            }

            // 创建批处理文件
            File batFile = new File(System.getProperty("java.io.tmpdir"), "minecraft_rename_" + System.currentTimeMillis() + ".bat");
            try (java.io.FileWriter fw = new java.io.FileWriter(batFile);
                 java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
                pw.println("@echo off");
                pw.println("chcp 65001 > nul"); // 支持中文
                pw.println("timeout /t 5 /nobreak > nul"); // 等待 5 秒确保游戏完全退出
                pw.println("cd /d \"" + parentDir.getAbsolutePath() + "\"");
                pw.println("if exist \"" + oldName + "\" (");
                pw.println("    ren \"" + oldName + "\" \"" + targetFolder.getName() + "\"");
                pw.println(")");
                pw.println("del \"%~f0\""); // 删除自己
            }

            // 启动批处理（最小化窗口）
            Runtime.getRuntime().exec("cmd /c start /min \"\" \"" + batFile.getAbsolutePath() + "\"");
            ModLogger.info("已创建外部重命名任务: " + batFile.getName());

        } catch (Exception e) {
            ModLogger.error("创建外部重命名任务失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 清理文件夹名称
     */
    private static String sanitizeFolderName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "");
    }

    /**
     * 在退出前获取存档路径
     */
    private static File getSaveFolderBeforeExit() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.isLocalServer() && mc.getSingleplayerServer() != null) {
                File saveFolder = mc.getSingleplayerServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toFile();
                return saveFolder;
            }
        } catch (Exception e) {
            ModLogger.error("获取存档路径失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 启动弹窗程序
     */
    private static void launchPopups() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("win")) {
                ModLogger.warn("弹窗仅支持Windows系统，当前系统: " + os);
                return;
            }
            DisplayMessage.start();
        } catch (Exception e) {
            ModLogger.error("无法启动弹窗程序: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 退出到标题界面
     */
    private static void exitToTitle() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                if (mc.level != null) {
                    mc.level.disconnect();
                }

                if (mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().halt(false);
                }

                mc.setScreen(new TitleScreen());
            } catch (Exception e) {
                ModLogger.error("退出失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}