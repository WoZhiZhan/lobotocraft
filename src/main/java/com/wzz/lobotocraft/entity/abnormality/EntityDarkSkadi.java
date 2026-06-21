package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.SoundUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
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

/**
 * 浊心斯卡蒂 (S-03-07) —— ALEPH 级异想体。
 * 不主动攻击;工作失误时为玩家回血;工作完成给予"同葬无光之愿"祝福;
 * 根据玩家状态发送只对该玩家可见的聊天消息(30秒冷却);
 * 被祝福玩家死亡时原地满血免死复活并使计数器 -1;
 * 计数器归零时消失,并在最近的再生反应堆处生成"伊莎玛拉"。
 */
public class EntityDarkSkadi extends AbstractAbnormality {

    // 持有"同葬无光之愿"祝福的玩家死亡时的免死复活逻辑由死亡事件读取该效果实现。

    // 聊天事件冷却(对每个玩家分别计时,这里用全局简化:600 tick=30秒)
    private int chatCooldown = 0;
    // 玩家死亡复活后需要触发的"复活台词"标记,由死亡事件设置后下一tick消费
    private int reviveLineTimer = 0;

    public EntityDarkSkadi(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "S-03-07";
        this.abnormalityName = "浊心斯卡蒂";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "WHITE";
        this.maxPEOutput = 37;

        // 工作偏好基础值(本能/洞察/沟通/压迫);压迫不可用设为0
        float[] basePreferences = {0.70f, 0.70f, 0.90f, 0.0f};
        initializeWorkPreferences(basePreferences);
        // 计数器极值5
        initializeQliphothCounter(5);
    }

    // ==================== 工作系统 ====================

    @Override
    public float[][] getFullWorkPreferences() {
        // 本能1-5级70%、洞察1-5级70%、沟通1-5级90%、压迫不可用(0)
        float[][] prefs = new float[4][5];
        for (int lv = 0; lv < 5; lv++) {
            prefs[WorkType.INSTINCT.ordinal()][lv] = 0.70f;
            prefs[WorkType.INSIGHT.ordinal()][lv] = 0.70f;
            prefs[WorkType.ATTACHMENT.ordinal()][lv] = 0.90f;
            prefs[WorkType.REPRESSION.ordinal()][lv] = 0.0f;
        }
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.04f, 0),                       // Ⅰ 成功率+4%
                new ObservationLevelBonus(0.0f, 2),                        // Ⅱ 工作速度+2
                new ObservationLevelBonus(0.04f, 0),                       // Ⅲ 成功率+4%
                new ObservationLevelBonus(0.0f, 2, true, true, true)       // Ⅳ 速度+2、解锁饰品/护甲/武器
        };
    }

    @Override
    public int getBasicInfoCost() { return 30; }

    @Override
    public int getSensitiveInfoCost() { return 30; }

    @Override
    public int getManualCost(int manualIndex) { return 7; }

    @Override
    public int getWorkPreferencesCost() { return 10; }

    @Override
    public boolean canEscape() {
        return false; // 该异想体不会突破收容
    }

    @Override
    public String getAbnormalityCode() { return "S-03-07"; }

    @Override
    public RiskLevel getRiskLevel() { return riskLevel; }

    @Override
    public String name() { return "skadi_corrupted"; }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        // 机制1:工作失误不造成伤害,反而为玩家恢复 3-6 点生命值
        if (player instanceof ServerPlayer) {
            float heal = 3 + this.random.nextInt(4); // 3-6
            player.heal(heal);
            player.sendSystemMessage(Component.literal("§b斯卡蒂的歌声抚平了你的伤口。§r(+" + (int) heal + " 生命)"));
        }
    }

    // ==================== 工作结果 ====================

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        // 机制2:工作完成后获得"同葬无光之愿"祝福(无限时长,计数器归零时统一移除)
        grantBlessing(player);
        setAnimation("idle");
    }

    private void grantBlessing(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                ModEffects.WISH_WITHOUT_LIGHT.get(),
                -1, 0, false, false, true
        ));
        player.sendSystemMessage(Component.literal("§9你获得了「同葬无光之愿」。"));
    }

    @Override
    public void onQliphothMeltdown() {
        onCounterZero();
    }

    // ==================== tick:聊天事件 + 复活台词 ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (chatCooldown > 0) chatCooldown--;
        if (reviveLineTimer > 0) {
            reviveLineTimer--;
        }

        // 机制3:根据附近玩家状态,发送仅该玩家可见的消息,每次发送30秒冷却
        if (this.tickCount % 20 == 0 && chatCooldown <= 0) {
            trySendChatEvent();
        }
    }

    private void trySendChatEvent() {
        // 公司维度内有斯卡蒂时,针对附近玩家根据状态发消息
        List<Player> players = this.level().getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(48), p -> p.isAlive());
        if (players.isEmpty()) return;

        for (Player p : players) {
            if (!(p instanceof ServerPlayer player)) continue;
            float maxHp = player.getMaxHealth();
            boolean lowHp = player.getHealth() < maxHp * 0.5f;
            boolean lowMental = MentalValueUtil.getMentalValue(player)
                    < MentalValueUtil.getEffectiveMaxMentalValue(player) * 0.5f;

            String msg;
            if (lowHp || lowMental) {
                // 生命值/精神值低于一半:立刻随机出现担忧台词
                String[] worried = {
                        "博士，离开吧。",
                        "博士......来这里。",
                        "这里迟早也会回归它原来的样子。",
                        "放心吧。不会有问题的。"
                };
                msg = worried[this.random.nextInt(worried.length)];
            } else {
                // 状态健康:每30秒随机出现陪伴台词
                String[] calm = {
                        "这样的我，在这里呆着会打扰到你吗？会让你回想起那些不堪的过去吗？",
                        "我......想你允许我陪伴在你身边。等时候到了，我们就一起离开这里，好吗？",
                        "过去的你想过这样的未来吗，博士？哪怕现在的你只会恨我。我不怕你的恨，博士。",
                        "如果想的话，就这样继续恨下去吧......在你依然还能恨的时候。",
                        "我的血亲和我的歌，一点都不可怕。人才可怕。",
                        "梦......你会有怎样一个梦呢？你现在身处何方？那里......会不会也是一个梦呢？"
                };
                msg = calm[this.random.nextInt(calm.length)];
            }
            player.sendSystemMessage(Component.literal("§3[浊心斯卡蒂] §7" + msg));
        }
        chatCooldown = 600; // 30秒冷却
    }

    /**
     * 由免死复活逻辑调用:计数器 -1,播放复活台词。
     * 当计数器归零时执行消失与生成伊莎玛拉。
     */
    public void onBlessedPlayerRevive(ServerPlayer player) {
        decreaseQliphothCounter(1);
        // 复活台词
        String[] reviveLines = {
                "不该再有更多生命消逝了。",
                "别把我当成灾厄。我不想让谁死去。",
                "我们什么都保护不了。我们只能寄望于那个和谐的未来。",
                "我们都失去了所有的家人......你愿意成为现在的我的家人吗？"
        };
        player.sendSystemMessage(Component.literal("§3[浊心斯卡蒂] §7"
                + reviveLines[this.random.nextInt(reviveLines.length)]));

    }

    /**
     * 机制5:计数器归零,斯卡蒂消失,在最近的再生反应堆处生成"伊莎玛拉",并播放出逃音频。
     */
    private void onCounterZero() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // 移除所有玩家身上的"同葬无光之愿"(祝福失效)
        for (Player p : serverLevel.players()) {
            p.removeEffect(ModEffects.WISH_WITHOUT_LIGHT.get());
        }

        // 对所有玩家播放出逃音频
        for (ServerPlayer p : serverLevel.players()) {
            serverLevel.playSound(null, p.blockPosition(),
                    ModSounds.SKADI_ESCAPE.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
        }

        // 记录斯卡蒂当前位置,供镇压伊莎玛拉后让斯卡蒂归位(项3修复)
        com.wzz.lobotocraft.item.SkadiBanishData.get(serverLevel)
                .setSkadiOrigin(this.getX(), this.getY(), this.getZ());

        // 在最近的再生反应堆处生成"伊莎玛拉"(EntityIsharmla)
        SpawnIsharmlaHook.trySpawnIsharmlaAtNearestReactor(serverLevel, this.blockPosition());

        this.discard();
    }

    // ==================== 环境音效 ====================

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.SKADI_AMBIENT.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 400; // 20秒
    }

    // ==================== 工作日志 ====================

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("快走吧，博士......逃走吧，从这里，从我身边......逃走吧。");
        logs.add("该异想体未表现出攻击倾向，并且在工作失误时为玩家回复了精神值。");
        logs.add("该异想体在玩家完成工作后，会给予玩家祝福。");
        logs.add("当有玩家因为各种原因血量和精神值变低时，该异想体展现出了额外的担心。当玩家状态健康时，它似乎也很开心。");
        logs.add("当被祝福的玩家死亡时，该异想体减少了计数器，但是本该死亡的玩家却无事发生一样。");
        logs.add("当计数器下降到0时....");
        return logs;
    }

    // ==================== 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 5, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityDarkSkadi> event) {
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.skadi_corrupted.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.skadi_corrupted.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 250.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ChatCooldown", chatCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chatCooldown = tag.getInt("ChatCooldown");
    }
}
