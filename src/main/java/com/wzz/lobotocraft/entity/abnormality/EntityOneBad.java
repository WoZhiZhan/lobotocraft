package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;

public class EntityOneBad extends AbstractAbnormality {
    private int animationTickCounter = 0;
    private String animationprocedure = "empty";

    public EntityOneBad(EntityType<? extends TamableAnimal> p_21803_, Level p_21804_) {
        super(p_21803_, p_21804_);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-03-03";
        this.abnormalityName = "一罪与百善";
        this.riskLevel = RiskLevel.ZAYIN;
        this.damageType = "WHITE";
        this.maxPEOutput = 10;

        // 工作偏好（基础成功率）
        float[] basePreferences = {0.6f, 0.3f, 0.8f, 0.4f};
        // 本能60%，洞察30%，沟通80%，压迫40%
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(3);
    }

    @Override
    public boolean onOpenWorkScreen(ServerPlayer player) {
        if (MentalValueUtil.getMentalValue(player) <= 0) {
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                mental.setMentalValue(mental.getEffectiveMaxMentalValue());
                player.sendSystemMessage(Component.literal(
                        "§6一罪与百善察觉到你的疯狂，为你祈祷，你的精神完全恢复了！§r"
                ));
            });
            return false;
        }
        return super.onOpenWorkScreen(player);
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/repentance_curio.png"),
                "忏悔",
                "头部",
                "repentance_curio",
                "最大精神值+2",
                "对\"一罪与百善\"进行工作",
                "的成功率提高10%"
        );
    }

    // 重写以提供详细的武器数据
    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/repentance_weapon.png"),
                "忏悔",
                getRiskLevel(),
                "WHITE",           // 伤害类型
                "5-7",             // 攻击力
                "普通",            // 攻击速度
                "近",              // 攻击距离
                getWeaponDevelopmentMaxCount(),                  // 研发总数
                "repentance_weapon"
        );
    }

    // 重写以提供详细的护甲数据
    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/repentance_armor.png"),
                "忏悔",
                getRiskLevel(),
                0.9f,    // RED抗性
                0.8f,    // WHITE抗性
                0.9f,    // BLACK抗性
                2.0f,    // PALE抗性
                getArmorDevelopmentMaxCount(),        // 研发总数
                "repentance"
        );
    }

    @Override
    public int getBasicInfoCost() {
        return 10;  // 基础信息：10个PE-BOX
    }

    @Override
    public int getWorkPreferencesCost() {
        return 8;  // 工作偏好：8个PE-BOX
    }

    @Override
    public int getSensitiveInfoCost() {
        return 10;  // 敏感信息：10个PE-BOX
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 4;  // 每个管理须知：4个PE-BOX
    }

    @Override
    public void onGoodWork(net.minecraft.server.level.ServerPlayer player) {
        // 优秀工作：恢复10点精神值
        MentalValueUtil.addMentalValue(player, 10);

        // 发送消息
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§a一罪与百善对你的工作非常满意，你感到精神振奋！§r(+10精神值)"
        ));
    }

    @Override
    public void onNormalWork(net.minecraft.server.level.ServerPlayer player) {
        // 良好工作：恢复5点精神值
        MentalValueUtil.addMentalValue(player, 5);

        // 发送消息
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§e一罪与百善接受了你的工作。§r(+5精神值)"
        ));
    }

    @Override
    public void onBadWork(net.minecraft.server.level.ServerPlayer player) {
        // 差的工作：播放动画并回满精神值
        // 触发特殊动画
        this.animationprocedure = "2.model.new";
        this.animationTickCounter = 0;

        // 延迟回满精神值（在动画播放完成后）
        // 使用调度器在60 tick后执行
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                    serverLevel.getServer().getTickCount() + 60,
                    () -> {
                        MentalValueUtil.setMentalValue(player,
                                player.getCapability(com.wzz.lobotocraft.capability.MentalValueProvider.MENTAL_VALUE)
                                        .map(com.wzz.lobotocraft.capability.IMentalValue::getEffectiveMaxMentalValue)
                                        .orElse(100f)
                        );

                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§6一罪与百善祈祷后，你的精神完全恢复了！§r"
                        ));
                    }
            ));
        }
    }

    @Override
    public boolean onOpenManualScreen(ServerPlayer player) {
        if (MentalValueUtil.getMentalValue(player) <= 0f) {
            MentalValueUtil.setMentalValue(player, 100f);
            player.sendSystemMessage(Component.literal("§6一罪与百善回应了你的祈祷，你的精神完全恢复了！§r"));
            return false;
        }
        return super.onOpenManualScreen(player);
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("进入“一罪与百善”的收容单元时，出于对它的一无所知，员工感受到了恐惧。");
        logs.add("通常情况下，“一罪与百善”能够对员工们产生积极的影响。");
        logs.add("指派一名诚实的员工进入“一罪与百善”的收容单元是再好不过的。");
        logs.add("“一罪与百善”能够看透你的灵魂。");
        logs.add("“一罪与百善”等待着人们的“罪孽”。");
        logs.add("员工的罪孽很快就会被“一罪与百善”吸收。");
        logs.add("“一罪与百善”有时会紧咬牙关，发出骇人的声响，但这其实没什么可担心的。");
        logs.add("“一罪与百善”正缓慢地飘浮在空中。");
        logs.add("“一罪与百善”没有眼睛，但它能感知到员工的存在。");
        logs.add("“一罪与百善”是人类的惩戒者。");
        logs.add("员工见证了“一罪与百善”的庄严肃穆。");
        logs.add("员工完成了工作，但“一罪与百善”没有丝毫的回应。");
        logs.add("在员工进行工作的时候，“一罪与百善”没有任何反应。");
        logs.add("“一罪与百善”并没有回应员工。");
        logs.add("承受苦难，仅仅只是赎罪的开始。");
        logs.add("只有那些能够熟练地欺骗自己的人才能过上“幸福”的生活。");
        logs.add("为了正义而犯下的罪孽，能够被赦免吗？");
        return logs;
    }

    @Override
    public String getAbnormalityCode() {
        return "O-03-03";
    }

    @Override
    public void onQliphothMeltdown() {
        super.onQliphothMeltdown();
    }

    @Override
    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {

    }

    @Override
    public String name() {
        return "onebad";
    }

    @Override
    public void tick() {
        super.tick();
        animationTickCounter++;
        if (!"empty".equals(animationprocedure) &&
                animationTickCounter >= getAnimationDuration(animationprocedure)) {
            this.animationprocedure = "empty";
            animationTickCounter = 0;
        }
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.05f, 0),
                new ObservationLevelBonus(0.0f, 5, true, false, false),
                new ObservationLevelBonus(0.05f, 0, false, true, false),
                new ObservationLevelBonus(0.0f, 5, false, false, true)
        };
    }

    @Override
    public boolean hurt(DamageSource p_27567_, float p_27568_) {
        return super.hurt(p_27567_, p_27568_);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "dj", 4, this::movementPredicate));
        controllerRegistrar.add(new AnimationController<>(this, "use", 4, this::expressionPredicate));
    }

    private PlayState movementPredicate(AnimationState<EntityOneBad> event) {
        return event.setAndContinue(RawAnimation.begin().thenLoop("1.model.new"));
    }

    private PlayState expressionPredicate(AnimationState<EntityOneBad> event) {
        if (!this.animationprocedure.equals("empty")) {
            return event.setAndContinue(RawAnimation.begin().thenLoop(this.animationprocedure));
        }
        return PlayState.CONTINUE;
    }

    private int getAnimationDuration(String animation) {
        return 60;
    }

    // ==================== 常态音效 ====================

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getAbnormalityAmbientSound() {
        return com.wzz.lobotocraft.init.ModSounds.ONEBAD_AMBIENT.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 420;  // 420 tick = 21秒
    }

    @Override
    public double getAbnormalityAmbientSoundRange() {
        return 8.0;  // 8格范围
    }

    @Override
    public net.minecraft.sounds.SoundSource getAbnormalityAmbientSoundSource() {
        return net.minecraft.sounds.SoundSource.HOSTILE;  // 使用HOSTILE类型，音效随距离快速衰减
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.02D)
                .add(Attributes.FLYING_SPEED, 0.02D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }
}