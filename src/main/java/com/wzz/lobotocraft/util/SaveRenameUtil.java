package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 存档重命名工具
 * 修改存档的显示名称和文件夹名
 */
@OnlyIn(Dist.CLIENT)
public class SaveRenameUtil {

    /**
     * 重命名当前存档（包括文件夹名）
     * @param newName 新的存档名称
     * @return 是否成功
     */
    public static boolean renameCurrentSave(String newName) {
        try {
            // 获取当前存档文件夹
            File currentSaveFolder = getCurrentSaveFolder();
            if (currentSaveFolder == null) {
                ModLogger.error("无法找到当前存档文件夹");
                return false;
            }

            return renameSaveWithPath(currentSaveFolder, newName);

        } catch (Exception e) {
            ModLogger.error("重命名存档失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 只重命名 level.dat，不重命名文件夹
     */
    public static boolean renameLevelDatOnly(File saveFolder, String newName) {
        try {
            if (saveFolder == null || !saveFolder.exists()) {
                ModLogger.error("存档文件夹不存在: " + saveFolder);
                return false;
            }

            boolean success = renameLevelDat(saveFolder, newName);
            if (success) {
                ModLogger.info("level.dat重命名成功: " + newName);
            }
            return success;

        } catch (Exception e) {
            ModLogger.error("重命名level.dat失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 使用指定路径重命名存档（包括文件夹名）
     * @param saveFolder 存档文件夹路径
     * @param newName 新的存档名称
     * @return 是否成功
     */
    public static boolean renameSaveWithPath(File saveFolder, String newName) {
        try {
            if (saveFolder == null || !saveFolder.exists()) {
                ModLogger.error("存档文件夹不存在: " + saveFolder);
                return false;
            }

            // 修改level.dat中的存档名称
            boolean levelDatSuccess = renameLevelDat(saveFolder, newName);
            if (!levelDatSuccess) {
                ModLogger.error("level.dat重命名失败");
                return false;
            }

            // 等待文件操作完成
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 步骤3: 重命名文件夹
            boolean folderSuccess = renameSaveFolder(saveFolder, newName);

            if (levelDatSuccess && folderSuccess) {
                return true;
            } else if (levelDatSuccess) {
                ModLogger.info("level.dat重命名成功，文件夹重命名失败（可能文件被占用）");
                return true;
            } else {
                return false;
            }

        } catch (Exception e) {
            ModLogger.error("重命名存档失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 修改level.dat中的名称
     */
    private static boolean renameLevelDat(File saveFolder, String newName) {
        try {
            // 修改level.dat中的存档名称
            File levelDat = new File(saveFolder, "level.dat");
            if (!levelDat.exists()) {
                ModLogger.error("level.dat不存在: " + levelDat.getAbsolutePath());
                return false;
            }

            CompoundTag rootTag;
            try (FileInputStream fis = new FileInputStream(levelDat)) {
                rootTag = NbtIo.readCompressed(fis);
            }

            // 修改LevelName
            if (rootTag.contains("Data")) {
                CompoundTag dataTag = rootTag.getCompound("Data");
                String oldName = dataTag.getString("LevelName");
                dataTag.putString("LevelName", newName);

                File backupFile = new File(saveFolder, "level.dat.backup");
                levelDat.renameTo(backupFile);
                // 写回level.dat
                try (FileOutputStream fos = new FileOutputStream(levelDat)) {
                    NbtIo.writeCompressed(rootTag, fos);
                }

                try (FileInputStream fis = new FileInputStream(levelDat)) {
                    CompoundTag verifyTag = NbtIo.readCompressed(fis);
                    String verifyName = verifyTag.getCompound("Data").getString("LevelName");
                    return verifyName.equals(newName);
                }

            } else {
                ModLogger.error("level.dat格式异常：缺少Data标签");
                return false;
            }

        } catch (Exception e) {
            ModLogger.error("修改level.dat失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 重命名存档文件夹
     */
    private static boolean renameSaveFolder(File currentFolder, String newName) {
        try {
            File parentDir = currentFolder.getParentFile();
            File newFolder = new File(parentDir, sanitizeFolderName(newName));

            // 如果目标文件夹已存在，添加后缀
            int suffix = 1;
            while (newFolder.exists()) {
                newFolder = new File(parentDir, sanitizeFolderName(newName) + "_" + suffix);
                suffix++;
                ModLogger.warn("目标文件夹已存在，使用: " + newFolder.getName());
            }

            // 尝试重命名
            boolean success = currentFolder.renameTo(newFolder);

            if (success) {
                return true;
            } else {
                ModLogger.error("文件夹重命名失败（可能文件被占用）");
                return false;
            }

        } catch (Exception e) {
            ModLogger.error("重命名文件夹失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 清理文件夹名称（移除非法字符）
     */
    private static String sanitizeFolderName(String name) {
        // 移除Windows文件名非法字符
        return name.replaceAll("[\\\\/:*?\"<>|]", "");
    }

    /**
     * 获取当前存档文件夹
     */
    private static File getCurrentSaveFolder() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                ModLogger.error("level为null");
                return null;
            }

            // 对于本地存档
            if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
                File saveFolder = mc.getSingleplayerServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toFile();
                return saveFolder;
            } else {
                ModLogger.error("不是本地存档或单人服务器为null");
            }

        } catch (Exception e) {
            ModLogger.error("获取存档文件夹失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}