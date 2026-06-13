package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.ModMain;

public class StringUtil {
    
    /**
     * 从完整资源名中提取类型名称
     * 例如: "minecraft:diamond" -> "diamond"
     */
    public static String extractTypeName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "";
        }
        
        int colonIndex = fullName.lastIndexOf(':');
        return colonIndex != -1 ? fullName.substring(colonIndex + 1) : fullName;
    }
    
    /**
     * 标准化类型名称（确保有命名空间）
     * 例如: "diamond" -> "lobotocraft:diamond"
     */
    public static String normalizeTypeName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return ModMain.MODID + ":";
        }
        
        if (!typeName.contains(":")) {
            return ModMain.MODID + ":" + typeName;
        }
        
        return typeName;
    }
    
    /**
     * 转换为蛇形命名（snake_case）
     */
    public static String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    /**
     * 首字母大写
     */
    public static String capitalizeFirst(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
    
    /**
     * 移除颜色代码
     */
    public static String stripColorCodes(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-or]", "");
    }
}