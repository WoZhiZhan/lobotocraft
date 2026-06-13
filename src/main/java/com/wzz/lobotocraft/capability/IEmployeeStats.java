package com.wzz.lobotocraft.capability;

/**
 * 员工属性接口
 * 四大属性：
 * - 勇气 (Fortitude): 影响最大生命值
 * - 谨慎 (Prudence): 影响最大精神值  
 * - 自律 (Temperance): 影响工作成功率和工作速度
 * - 正义 (Justice): 影响攻击速度和移动速度
 */
public interface IEmployeeStats {

    /**
     * 获取勇气值 (20-100)
     */
    int getFortitude();
    
    /**
     * 获取谨慎值 (20-100)
     */
    int getPrudence();
    
    /**
     * 获取自律值 (20-100)
     */
    int getTemperance();
    
    /**
     * 获取正义值 (20-100)
     */
    int getJustice();

    /**
     * 设置勇气值
     */
    void setFortitude(int value);
    
    /**
     * 设置谨慎值
     */
    void setPrudence(int value);
    
    /**
     * 设置自律值
     */
    void setTemperance(int value);
    
    /**
     * 设置正义值
     */
    void setJustice(int value);

    /**
     * 增加勇气值
     * @param amount 增加量
     * @return 实际增加量（考虑上限）
     */
    int addFortitude(int amount);
    
    /**
     * 增加谨慎值
     * @param amount 增加量
     * @return 实际增加量（考虑上限）
     */
    int addPrudence(int amount);
    
    /**
     * 增加自律值
     * @param amount 增加量
     * @return 实际增加量（考虑上限）
     */
    int addTemperance(int amount);
    
    /**
     * 增加正义值
     * @param amount 增加量
     * @return 实际增加量（考虑上限）
     */
    int addJustice(int amount);
    
    /**
     * 获取勇气等级 (1-5)
     */
    int getFortitudeLevel();
    
    /**
     * 获取谨慎等级 (1-5)
     */
    int getPrudenceLevel();
    
    /**
     * 获取自律等级 (1-5)
     */
    int getTemperanceLevel();
    
    /**
     * 获取正义等级 (1-5)
     */
    int getJusticeLevel();
    
    /**
     * 获取员工总等级 (1-5)
     * 四个属性都提升一级后，员工等级+1
     */
    int getEmployeeLevel();
    
    // ==================== 工作计数 ====================
    
    /**
     * 获取指定风险等级的工作次数
     */
    int getWorkCount(String riskLevel);
    
    /**
     * 增加指定风险等级的工作次数
     */
    void incrementWorkCount(String riskLevel);
    
    /**
     * 检查是否应该增加属性（根据风险等级和工作次数）
     * @param riskLevel 风险等级
     * @param workType 工作类型（用于决定增加哪个属性）
     * @return 应该增加的属性值
     */
    int calculateAttributeIncrease(String riskLevel, String workType);
}