package com.wzz.lobotocraft.capability;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工属性实现类
 */
public class EmployeeStats implements IEmployeeStats {
    
    // 四大属性值 (20-110)
    private int fortitude = 20;    // 勇气
    private int prudence = 20;     // 谨慎
    private int temperance = 20;   // 自律
    private int justice = 20;      // 正义

    /** Temporary Hod suppression overlay. It is deliberately not serialized here;
     * CoreSuppressionData is the authoritative owner of its lifetime. */
    private int hodPenalty;
    
    // 各风险等级的工作次数
    private final Map<String, Integer> workCounts = new HashMap<>();
    
    // 常量
    public static final int MIN_STAT = 20;
    public static final int MAX_STAT = 110;
    
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
        return effective(fortitude);
    }
    
    @Override
    public int getPrudence() {
        return effective(prudence);
    }
    
    @Override
    public int getTemperance() {
        return effective(temperance);
    }
    
    @Override
    public int getJustice() {
        return effective(justice);
    }

    private int effective(int base) {
        return Math.max(1, base - hodPenalty);
    }

    /** Applies the same temporary Hod reduction to all four displayed stats. */
    public void setHodPenalty(int penalty) {
        hodPenalty = Math.max(0, Math.min(30, penalty));
    }

    public int getHodPenalty() {
        return hodPenalty;
    }

    public void clearHodPenalty() {
        hodPenalty = 0;
    }

    /** Base values are used for persistence so a temporary suppression can never
     * become permanent through an AI runtime save. */
    public int getBaseFortitude() { return fortitude; }
    public int getBasePrudence() { return prudence; }
    public int getBaseTemperance() { return temperance; }
    public int getBaseJustice() { return justice; }
    
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
        return calculateLevel(getFortitude());
    }
    
    @Override
    public int getPrudenceLevel() {
        return calculateLevel(getPrudence());
    }
    
    @Override
    public int getTemperanceLevel() {
        return calculateLevel(getTemperance());
    }
    
    @Override
    public int getJusticeLevel() {
        return calculateLevel(getJustice());
    }
    
    /**
     * 计算属性等级
     * 20-29: I
     * 30-44: II
     * 45-64: III
     * 65-84: IV
     * 85-99: V
     * 100-110: EX（运行时仍按 V 参与判定）
     */
    private int calculateLevel(int stat) {
        return EmployeeLevelCalculator.statLevel(stat);
    }
    
    @Override
    public int getEmployeeLevel() {
        // AgentModel sums the four attribute levels and applies the original
        // <6/<9/<12/<16 thresholds; it does not use the minimum attribute.
        return EmployeeLevelCalculator.fromStatLevels(getFortitudeLevel(), getPrudenceLevel(),
                getTemperanceLevel(), getJusticeLevel());
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
        setFortitude(tag.getInt("Fortitude"));
        setPrudence(tag.getInt("Prudence"));
        setTemperance(tag.getInt("Temperance"));
        setJustice(tag.getInt("Justice"));
        
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
        setFortitude(source.fortitude);
        setPrudence(source.prudence);
        setTemperance(source.temperance);
        setJustice(source.justice);
        this.workCounts.clear();
        this.workCounts.putAll(source.workCounts);
        this.hodPenalty = source.hodPenalty;
    }
}
