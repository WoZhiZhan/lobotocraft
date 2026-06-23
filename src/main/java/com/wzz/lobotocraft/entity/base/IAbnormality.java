package com.wzz.lobotocraft.entity.base;

import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 异想体接口 - 所有异想体都应实现此接口
 */
public interface IAbnormality {

    /**
     * 获取实体ID
     */
    int getEntityId();

    /**
     * 获取异想体编号（如 O-03-03）
     */
    String getAbnormalityCode();

    /**
     * 获取异想体名称
     */
    String getAbnormalityName();

    /**
     * 获取风险等级 (ZAYIN, TETH, HE, WAW, ALEPH)
     */
    RiskLevel getRiskLevel();

    /**
     * 获取伤害类型 (WHITE, RED, BLACK, PALE)
     */
    String getDamageType();

    /**
     * 获取最大PE-BOX产量
     */
    int getMaxPEOutput();

    // ==================== 工作相关 ====================

    /**
     * 获取简化的工作偏好数组 [本能, 洞察, 沟通, 压迫]
     * 返回值为0.0-1.0之间的成功率（用于工作计算）
     */
    float[] getWorkPreferences();

    /**
     * 获取完整的工作偏好矩阵 [4种工作][5个等级]
     * 用于图鉴界面显示
     */
    float[][] getFullWorkPreferences();

    /**
     * 工作失败时攻击玩家
     */
    void attackPlayerOnFailure(Player player, WorkType workType);

    /**
     * 获取基础信息解锁成本（PE-BOX数量）
     */
    default int getBasicInfoCost() {
        return 10;  // 默认值
    }

    /**
     * 获取工作偏好解锁成本（PE-BOX数量）
     */
    default int getWorkPreferencesCost() {
        return 8;  // 默认值
    }

    /**
     * 获取敏感信息解锁成本（PE-BOX数量）
     */
    default int getSensitiveInfoCost() {
        return 10;  // 默认值
    }

    /**
     * 获取管理须知解锁成本（PE-BOX数量）
     *
     * @param manualIndex 管理须知索引（0-based）
     */
    default int getManualCost(int manualIndex) {
        return 4;  // 默认值，所有管理须知成本相同
    }

    // 会不会出逃
    default boolean canEscape() {
        return true;
    }

    // 为true时正在出逃
    boolean hasEscape();

    // ==================== 工作结果奖励 ====================

    /**
     * 工作结果为"优"时的奖励
     * 每个异想体可以自定义奖励内容
     */
    default void onGoodWork(net.minecraft.server.level.ServerPlayer player) {
        // 默认：无特殊奖励
    }

    /**
     * 工作结果为"良"时的奖励
     * 每个异想体可以自定义奖励内容
     */
    default void onNormalWork(net.minecraft.server.level.ServerPlayer player) {
        // 默认：无特殊奖励
    }

    /**
     * 工作结果为"差"时的惩罚/特殊效果
     * 每个异想体可以自定义惩罚内容
     */
    default void onBadWork(net.minecraft.server.level.ServerPlayer player) {
        // 默认：降低逆卡巴拉计数器
        decreaseQliphothCounter(1);
    }

    /**
     * 在即将打开百科界面时触发
     * @return 返回false取消打开
     */
    default boolean onOpenManualScreen(net.minecraft.server.level.ServerPlayer player) {
        return true;
    }

    /**
     * 在即将打开工作界面时触发
     * @return 返回false取消打开
     */
    default boolean onOpenWorkScreen(net.minecraft.server.level.ServerPlayer player) {
        return true;
    }

    /**
     * 获取当前计数器值
     */
    int getQliphothCounter();

    int getMaxQliphothCounter();

    /**
     * 减少计数器
     */
    void decreaseQliphothCounter(int amount);

    /**
     * 增加计数器
     */
    void increaseQliphothCounter(int amount);

    /**
     * 计数器归零时触发
     */
    void onQliphothMeltdown();

    /**
     * 是否为工具类型异想体
     */
    default boolean isToolType() {
        return false;
    }

    /**
     * 是否为持续使用型工具
     */
    default boolean isContinuousUseTool() {
        return false;
    }

    /**
     * 是否有常态音效
     *
     * @return true表示此异想体会在空闲时播放环境音效
     */
    default boolean hasAbnormalityAmbientSound() {
        return false;
    }

    /**
     * 获取常态音效
     *
     * @return 音效事件，如果没有则返回null
     */
    default net.minecraft.sounds.SoundEvent getAbnormalityAmbientSound() {
        return null;
    }

    /**
     * 获取常态音效播放间隔（tick）
     *
     * @return 音效播放间隔，默认120 tick (6秒)
     */
    default int getAbnormalityAmbientSoundInterval() {
        return 120;
    }

    /**
     * 获取常态音效播放范围（格）
     *
     * @return 播放范围，默认8格
     */
    default double getAbnormalityAmbientSoundRange() {
        return 8.0;
    }

    /**
     * 获取常态音效音量
     *
     * @return 音量，默认0.8
     */
    default float getAbnormalityAmbientSoundVolume() {
        return 0.8f;
    }

    /**
     * 获取常态音效音调
     *
     * @return 音调，默认1.0
     */
    default float getAbnormalityAmbientSoundPitch() {
        return 1.0f;
    }

    /**
     * /**
     * 获取常态音效的声音类型
     *
     * @return 音效类型，默认为RECORDS（音符盒）
     */
    default net.minecraft.sounds.SoundSource getAbnormalityAmbientSoundSource() {
        return net.minecraft.sounds.SoundSource.RECORDS;
    }

    // ==================== GUI音乐系统 ====================

    /**
     * 是否在管理界面播放背景音乐
     *
     * @return true表示播放音乐，false表示不播放（默认）
     */
    default boolean hasManagementMusic() {
        return false;
    }

    /**
     * 获取管理界面的背景音乐
     *
     * @return 音乐SoundEvent，如果返回null则不播放音乐
     */
    default net.minecraft.sounds.SoundEvent getManagementMusic() {
        return null;
    }

    /**
     * 获取音乐淡入时间（tick）
     *
     * @return 淡入持续时间，默认20 tick (1秒)
     */
    default int getMusicFadeInDuration() {
        return 20;
    }

    /**
     * 获取音乐淡出时间（tick）
     *
     * @return 淡出持续时间，默认20 tick (1秒)
     */
    default int getMusicFadeOutDuration() {
        return 20;
    }

    /**
     * 获取音乐最大音量
     *
     * @return 0.0-1.0之间的值，默认0.5
     */
    default float getMusicMaxVolume() {
        return 0.5f;
    }

    /**
     * 音乐是否循环播放
     *
     * @return true表示循环，默认true
     */
    default boolean isMusicLooping() {
        return true;
    }

    /**
     * 获取音乐分类标识
     * 相同标识的异想体会共享播放进度
     *
     * @return 分类标识，默认使用异想体编号
     */
    default String getMusicCategory() {
        return getAbnormalityCode();
    }

    /**
     * 是否有出逃环境音效（出逃期间循环播放）
     */
    default boolean hasEscapeAmbientSound() {
        return false;
    }

    /**
     * 获取出逃环境音效
     */
    default SoundEvent getEscapeAmbientSound() {
        return null;
    }

    /**
     * 出逃环境音效播放间隔（tick），默认 60 tick = 3秒
     */
    default int getEscapeAmbientSoundInterval() {
        return 60;
    }

    /**
     * 出逃环境音效音量
     */
    default float getEscapeAmbientSoundVolume() {
        return 1.0f;
    }

    /**
     * 出逃环境音效音调
     */
    default float getEscapeAmbientSoundPitch() {
        return 1.0f;
    }

    /**
     * 出逃环境音效的声音类型，默认 HOSTILE
     */
    default SoundSource getEscapeAmbientSoundSource() {
        return SoundSource.HOSTILE;
    }

    // ==================== 工作回调系统 ====================
    /**
     * @param player 工作的玩家
     * @param workType 工作类型
     * @return true表示允许开始工作，false表示阻止工作
     */
    default boolean onWorkStart(net.minecraft.server.level.ServerPlayer player, com.wzz.lobotocraft.work.WorkType workType) {
        return true;  // 默认允许工作
    }

    /**
     * 工作完成后回调
     *
     * @param player   工作的玩家
     * @param workType 工作类型
     * @param result   工作结果
     */
    default void onWorkComplete(net.minecraft.server.level.ServerPlayer player,
                                com.wzz.lobotocraft.work.WorkType workType,
                                com.wzz.lobotocraft.work.WorkResult result) {
    }

    /**
     * 是否发放本次工作的 PE-BOX。
     */
    default boolean shouldGivePEBox(ServerPlayer player, WorkType workType, WorkResult result, int peOutput) {
        return true;
    }

    /**
     * 工作中断时回调
     *
     * @param player   工作的玩家
     * @param workType 工作类型
     * @param reason   中断原因
     */
    default void onWorkInterrupted(net.minecraft.server.level.ServerPlayer player,
                                   com.wzz.lobotocraft.work.WorkType workType,
                                   String reason) {
    }

    /**
     * 工作进行时
     *
     * @param player   工作的玩家
     * @param session 工作会话数据
     * @param workType 工作类型
     */
    default void onWorkTick(ServerPlayer player, WorkManager.WorkSession session, WorkType workType) {
    }

    /**
     * 获取工具使用的危险警告标题
     * 用于持续使用型工具显示在确认界面
     *
     * @return 警告标题，如"危险警告"
     */
    default String getToolWarningTitle() {
        return "§4§l危险警告";
    }

    /**
     * 获取工具使用的危险警告内容
     * 用于持续使用型工具显示在确认界面
     * 每个异想体可以自定义警告内容
     *
     * @return 警告内容列表，每个元素为一行警告文本
     */
    default String[] getToolWarningMessages() {
        return new String[]{
                "§c员工进入后将无法停止使用",
                "§c将会持续受到递增的RED伤害",
                "§c直到死亡为止"
        };
    }

    /**
     * 开始使用持续型工具异想体
     *
     * @param player 使用工具的玩家
     * @return true表示成功开始使用，false表示无法使用
     */
    default boolean startUsing(net.minecraft.server.level.ServerPlayer player) {
        return false; // 默认不支持使用
    }

    /**
     * 停止使用工具（玩家主动停止）
     * 仅对持续使用型工具有效
     *
     * @param player 使用工具的玩家
     */
    default void stopUsing(net.minecraft.server.level.ServerPlayer player) {
        // 默认实现：什么都不做
        // 持续使用型工具应该重写此方法
    }

    // ==================== 可取消使用系统 ====================

    /**
     * 检查玩家是否可以安全取消使用
     * 用于持续使用型工具，某些工具允许在满足条件后安全取消
     *
     * @param player 尝试取消的玩家
     * @return true表示可以安全取消，false表示取消会触发惩罚
     */
    default boolean canSafelyCancelUsing(net.minecraft.server.level.ServerPlayer player) {
        return true; // 默认可以安全取消
    }

    /**
     * 玩家安全取消使用时调用
     * 子类可以重写此方法来添加取消时的特殊逻辑
     *
     * @param player 取消使用的玩家
     */
    default void onSafeCancel(net.minecraft.server.level.ServerPlayer player) {
        // 默认实现：什么都不做
    }

    /**
     * 玩家强制取消使用时调用（不满足安全条件）
     * 子类可以重写此方法来添加惩罚逻辑
     *
     * @param player 强制取消的玩家
     */
    default void onForceCancel(net.minecraft.server.level.ServerPlayer player) {
        // 默认实现：什么都不做
    }

    /**
     * 获取工作所需的最低员工等级
     * 只有达到此等级的员工才能对该异想体进行工作
     *
     * @return 所需员工等级 (1-5)，默认为1
     */
    default int getRequiredEmployeeLevel() {
        return 1; // 默认等级1
    }

    /**
     * 修改工作成功率
     * 异想体可以根据特殊条件动态调整成功率
     * 例如：快乐泰迪在第二次沟通时返回0.0f（必定失败）
     *
     * @param player 进行工作的玩家
     * @param workType 工作类型
     * @param baseRate 基础成功率（来自工作偏好）
     * @return 修改后的成功率，返回null表示使用基础成功率
     */
    default Float modifyWorkSuccessRate(net.minecraft.server.level.ServerPlayer player,
                                        WorkType workType,
                                        float baseRate) {
        return null;  // 默认不修改
    }

    /**
     * 是否强制工作结果
     * 异想体可以强制指定工作结果，忽略实际成功率
     * 例如：快乐泰迪在第二次沟通时强制返回BAD
     *
     * @param player 进行工作的玩家
     * @param workType 工作类型
     * @return true表示强制结果，false表示按正常流程判定
     */
    default boolean shouldForceWorkResult(net.minecraft.server.level.ServerPlayer player,
                                          WorkType workType) {
        return false;  // 默认不强制
    }

    /**
     * 获取强制的工作结果
     * 仅在shouldForceWorkResult返回true时调用
     *
     * @param player 进行工作的玩家
     * @param workType 工作类型
     * @return 强制的工作结果
     */
    default WorkResult getForcedWorkResult(net.minecraft.server.level.ServerPlayer player,
                                           WorkType workType) {
        return WorkResult.BAD;  // 默认强制为BAD
    }

    /**
     * 每次工作抽取尝试时的回调
     * 在判定成功/失败之前调用，异想体可以记录状态或触发特殊效果
     *
     * @param player 进行工作的玩家
     * @param workType 工作类型
     * @param extractionIndex 当前是第几次抽取（从0开始）
     */
    default void onWorkExtractionAttempt(net.minecraft.server.level.ServerPlayer player,
                                         WorkType workType,
                                         int extractionIndex) {
        // 默认无操作
    }

    /**
     * 获取E.G.O饰品完整数据
     * @return 饰品数据，如果没有则返回null
     */
    default EGOEquipmentData.GiftData getEGOGiftData() {
        return null;
    }

    /**
     * 获取E.G.O武器完整数据
     * @return 武器数据，如果没有则返回null
     */
    default EGOEquipmentData.WeaponData getEGOWeaponData() {
        return null;
    }

    /**
     * 获取E.G.O护甲完整数据
     * @return 护甲数据，如果没有则返回null
     */
    default EGOEquipmentData.ArmorData getEGOArmorData() {
        return null;
    }

    default List<String> getWorkLogs() {
        return null;
    }

    default SoundEvent getAttackSound() {
        return null;
    }

    default SoundEvent getEscapeWarningSound() {
        return null;
    }

    default SoundEvent getEscapeSound() {
        return null;
    }

    /**
     * 获取装备研发所需的能量（PE-BOX数量）
     */
    default int getArmorDevelopmentCost() {
        return 15;  // 默认15个PE-BOX
    }

    /**
     * 获取武器研发所需的能量（PE-BOX数量）
     */
    default int getWeaponDevelopmentCost() {
        return 12;  // 默认12个PE-BOX
    }

    default int getArmorDevelopmentMaxCount() {
        return 5;
    }

    default int getWeaponDevelopmentMaxCount() {
        return 5;
    }

    default float getGiftProbability() {
        return 0.05f;
    }

    default float[] getArmorRenderScale() {
        return new float[] {1.0f, 1.0f, 1.0f};
    }

    default float[] getWeaponRenderScale() {
        return new float[] {1.0f, 1.0f, 1.0f};
    }

    default float[] getGiftRenderScale() {
        return new float[] {1.0f, 1.0f, 1.0f};
    }

    default float[] getArmorRenderOffset() {
        return new float[] {0.0f, 0.0f, 0.0f};
    }

    default float[] getWeaponRenderOffset() {
        return new float[] {0.0f, 0.0f, 0.0f};
    }

    default float[] getGiftRenderOffset() {
        return new float[] {0.0f, 0.0f, 0.0f};
    }

    /**
     * 观察等级加成数据类
     */
    class ObservationLevelBonus {
        private final float successRateBonus;  // 成功率加成（如 0.04f = 4%）
        private final int speedBonus;           // 速度加成（tick减少）
        private final boolean unlockGift;       // 是否解锁饰品
        private final boolean unlockArmor;      // 是否解锁护甲
        private final boolean unlockWeapon;     // 是否解锁武器

        public ObservationLevelBonus(float successRate, int speed) {
            this(successRate, speed, false, false, false);
        }

        public ObservationLevelBonus(float successRate, int speed,
                                     boolean gift, boolean armor, boolean weapon) {
            this.successRateBonus = successRate;
            this.speedBonus = speed;
            this.unlockGift = gift;
            this.unlockArmor = armor;
            this.unlockWeapon = weapon;
        }

        public float getSuccessRateBonus() { return successRateBonus; }
        public int getSpeedBonus() { return speedBonus; }
        public boolean unlocksGift() { return unlockGift; }
        public boolean unlocksArmor() { return unlockArmor; }
        public boolean unlocksWeapon() { return unlockWeapon; }
    }

    /**
     * 获取观察等级加成配置（4个等级）
     * 默认配置：标准加成
     *
     * @return 长度为4的数组，分别对应观察等级1-4的加成
     */
    default ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.05f, 0),      // Lv1: 成功率+5%
                new ObservationLevelBonus(0.0f, 5, true, false, false), // Lv2: 速度+5, 解锁饰品
                new ObservationLevelBonus(0.05f, 0),      // Lv3: 成功率+5%
                new ObservationLevelBonus(0.0f, 5, false, true, true)   // Lv4: 速度+5, 解锁护甲武器
        };
    }

    /**
     * 获取观察等级加成的显示文本（用于GUI）
     *
     * @return 长度为4的数组，每个元素是该等级的加成描述
     */
    default String[] getObservationBonusTexts() {
        ObservationLevelBonus[] bonuses = getObservationBonuses();
        String[] texts = new String[4];

        for (int i = 0; i < 4; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append(toRomanNumeral(i + 1)).append("   ");

            ObservationLevelBonus bonus = bonuses[i];
            boolean hasContent = false;

            if (bonus.getSuccessRateBonus() > 0) {
                sb.append(String.format("成功率 +%.0f%%", bonus.getSuccessRateBonus() * 100));
                hasContent = true;
            }

            if (bonus.getSpeedBonus() > 0) {
                if (hasContent) sb.append("、");
                sb.append("工作速度 +").append(bonus.getSpeedBonus());
                hasContent = true;
            }

            // 解锁信息
            if (bonus.unlocksGift()) {
                if (hasContent) sb.append("、");
                sb.append("解锁E.G.O饰品");
                hasContent = true;
            }

            if (bonus.unlocksArmor() || bonus.unlocksWeapon()) {
                if (hasContent) sb.append("、");
                sb.append("解锁E.G.O");
                if (bonus.unlocksArmor()) sb.append("护甲");
                if (bonus.unlocksArmor() && bonus.unlocksWeapon()) sb.append("、");
                if (bonus.unlocksWeapon()) sb.append("武器");
            }

            texts[i] = sb.toString();
        }

        return texts;
    }

    /**
     * 将数字转换为罗马数字
     */
    private String toRomanNumeral(int num) {
        String[] romans = {"I", "II", "III", "VI", "V"};
        if (num > 0 && num <= romans.length) {
            return romans[num - 1];
        }
        return String.valueOf(num);
    }

    /**
     * 获取特定观察等级的加成
     */
    default ObservationLevelBonus getObservationBonus(int level) {
        if (level < 1 || level > 4) return new ObservationLevelBonus(0, 0);
        return getObservationBonuses()[level - 1];
    }
}
