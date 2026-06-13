package com.wzz.lobotocraft.api;

import static com.wzz.lobotocraft.util.EnvironmentUtil.isObfuscationEnvironment;

/**
 * 字符串封装类，用于处理混淆环境下的字符串映射
 * <p>
 * 用法示例：
 * <pre>
 *   // 定义字符串常量
 *   public static final ObfString METHOD_TICK = new ObfString("tick", "func_12345_");
 *   
 *   // 使用时自动获取当前环境下的正确名称
 *   String name = METHOD_TICK.value(); // 开发环境返回 "tick"，混淆环境返回 "func_12345_"
 * </pre>
 */
public class ObfString {
    
    private final String deobfName;
    private final String obfName;
    
    /**
     * 构造一个混淆字符串封装对象
     * 
     * @param deobfName 反混淆环境下的名称（开发/MCP名）
     * @param obfName   混淆环境下的名称（SRG名/混淆后名称）
     */
    public ObfString(String deobfName, String obfName) {
        this.deobfName = deobfName;
        this.obfName = obfName;
    }
    
    /**
     * 构造一个仅有一个名称的字符串封装（双环境相同）
     * 
     * @param name 双环境共用的名称
     */
    public ObfString(String name) {
        this(name, name);
    }
    
    /**
     * 获取当前环境下正确的字符串值
     * 
     * @return 当前环境下的字符串值
     */
    public String value() {
        return isObfuscationEnvironment() ? obfName : deobfName;
    }
    
    @Override
    public String toString() {
        return value();
    }
    
    /**
     * 批量创建 ObfString 数组的便捷方法
     * 
     * @param deobfNames 反混淆名称数组
     * @param obfNames   混淆名称数组
     * @return ObfString 数组
     */
    public static ObfString[] of(String[] deobfNames, String[] obfNames) {
        if (deobfNames.length != obfNames.length) {
            throw new IllegalArgumentException("deobfNames and obfNames must have same length");
        }
        ObfString[] result = new ObfString[deobfNames.length];
        for (int i = 0; i < deobfNames.length; i++) {
            result[i] = new ObfString(deobfNames[i], obfNames[i]);
        }
        return result;
    }
}