package com.wzz.lobotocraft.capability;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工属性实现类
 */
public class EmployeeStats implements IEmployeeStats {
    
    // 四大属性值 (20-100)
    private int fortitude = 20;    // 勇气
    private int prudence = 20;     // 谨慎
    private int temperance = 20;   // 自律
    private int justice = 20;      // 正义
    
    // 各风险等级的工作次数
    private final Map<String, Integer> workCounts = new HashMap<>();
    
    // 常量
    public static final int MIN_STAT = 20;
    public static final int MAX_STAT = 100;
    
    public EmployeeStats() {
        // 初始化工作计数
        workCounts.put("ZAYIN", 0);
        workCounts.put("TETH", 0);
        workCounts.put("HE", 0);
        workCounts.put("WAW", 0);
        workCounts.put("ALEPH", 0);
    }
    
    // ==================== 属性值获取 ====================
    
    @Override
    public int getFortitude() {
        return fortitude;
    }
    
    @Override
    public int getPrudence() {
        return prudence;
    }
    
    @Override
    public int getTemperance() {
        return temperance;
    }
    
    @Override
    public int getJustice() {
        return justice;
    }
    
    // ==================== 属性值设置 ====================
    
    @Override
    public void setFortitude(int value) {
        this.fortitude = Math.max(MIN_STAT, Math.min(MAX_STAT, value));
    }
    
    @Override
    public void setPrudence(int value) {
        this.prudence = Math.max(MIN_STAT, Math.min(MAX_STAT, value));
    }
    
    @Override
    public void setTemperance(int value) {
        this.temperance = Math.max(MIN_STAT, Math.min(MAX_STAT, value));
    }
    
    @Override
    public void setJustice(int value) {
        this.justice = Math.max(MIN_STAT, Math.min(MAX_STAT, value));
    }
    
    // ==================== 属性增加 ====================
    
    @Override
    public int addFortitude(int amount) {
        int oldValue = fortitude;
        setFortitude(fortitude + amount);
        return fortitude - oldValue;
    }
    
    @Override
    public int addPrudence(int amount) {
        int oldValue = prudence;
        setPrudence(prudence + amount);
        return prudence - oldValue;
    }
    
    @Override
    public int addTemperance(int amount) {
        int oldValue = temperance;
        setTemperance(temperance + amount);
        return temperance - oldValue;
    }
    
    @Override
    public int addJustice(int amount) {
        int oldValue = justice;
        setJustice(justice + amount);
        return justice - oldValue;
    }
    
    // ==================== 等级计算 ====================
    
    @Override
    public int getFortitudeLevel() {
        return calculateLevel(fortitude);
    }
    
    @Override
    public int getPrudenceLevel() {
        return calculateLevel(prudence);
    }
    
    @Override
    public int getTemperanceLevel() {
        return calculateLevel(temperance);
    }
    
    @Override
    public int getJusticeLevel() {
        return calculateLevel(justice);
    }
    
    /**
     * 计算属性等级
     * 20-39: 1级
     * 40-59: 2级
     * 60-79: 3级
     * 80-99: 4级
     * 100: 5级
     */
    private int calculateLevel(int stat) {
        if (stat >= 100) return 5;
        if (stat >= 80) return 4;
        if (stat >= 60) return 3;
        if (stat >= 40) return 2;
        return 1;
    }
    
    @Override
    public int getEmployeeLevel() {
        // 员工等级 = 四个属性等级的最小值
        // 这样确保只有四个属性都达到某个等级，员工才会达到该等级
        return Math.min(Math.min(getFortitudeLevel(), getPrudenceLevel()),
                       Math.min(getTemperanceLevel(), getJusticeLevel()));
    }
    
    // ==================== 工作计数 ====================
    
    @Override
    public int getWorkCount(String riskLevel) {
        return workCounts.getOrDefault(riskLevel, 0);
    }
    
    @Override
    public void incrementWorkCount(String riskLevel) {
        workCounts.put(riskLevel, getWorkCount(riskLevel) + 1);
    }
    
    @Override
    public int calculateAttributeIncrease(String riskLevel, String workType) {
        incrementWorkCount(riskLevel);
        int workCount = getWorkCount(riskLevel);
        
        // 根据风险等级决定增加量和频率
        return switch (riskLevel) {
            case "ZAYIN" -> workCount % 2 == 0 ? 1 : 0;  // 每2次+1
            case "TETH" -> 1;   // 每次+1
            case "HE" -> 2;     // 每次+2
            case "WAW" -> 4;    // 每次+4
            case "ALEPH" -> 6;  // 每次+6
            default -> 0;
        };
    }
    
    // ==================== NBT序列化 ====================
    
    public void saveToNBT(CompoundTag tag) {
        tag.putInt("Fortitude", fortitude);
        tag.putInt("Prudence", prudence);
        tag.putInt("Temperance", temperance);
        tag.putInt("Justice", justice);
        
        // 保存工作计数
        CompoundTag workCountTag = new CompoundTag();
        workCounts.forEach(workCountTag::putInt);
        tag.put("WorkCounts", workCountTag);
    }
    
    public void loadFromNBT(CompoundTag tag) {
        fortitude = tag.getInt("Fortitude");
        prudence = tag.getInt("Prudence");
        temperance = tag.getInt("Temperance");
        justice = tag.getInt("Justice");
        
        // 加载工作计数
        if (tag.contains("WorkCounts")) {
            CompoundTag workCountTag = tag.getCompound("WorkCounts");
            workCounts.clear();
            workCounts.put("ZAYIN", workCountTag.getInt("ZAYIN"));
            workCounts.put("TETH", workCountTag.getInt("TETH"));
            workCounts.put("HE", workCountTag.getInt("HE"));
            workCounts.put("WAW", workCountTag.getInt("WAW"));
            workCounts.put("ALEPH", workCountTag.getInt("ALEPH"));
        }
    }
    
    public void copyFrom(EmployeeStats source) {
        this.fortitude = source.fortitude;
        this.prudence = source.prudence;
        this.temperance = source.temperance;
        this.justice = source.justice;
        this.workCounts.clear();
        this.workCounts.putAll(source.workCounts);
    }
}