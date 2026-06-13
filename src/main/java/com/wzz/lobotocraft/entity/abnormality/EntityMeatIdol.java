package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 血肉偶像 (T-09-79)
 * WAW级持续使用型工具异想体
 * 特殊机制：
 * - 每6秒造成3-6点随机类型伤害
 * - 工作<20秒取消：立刻死亡
 * - 工作21-90秒：其他玩家恢复生命和精神
 * - 工作>90秒：祷告者死亡 + 所有异想体计数器归零
 */
public class EntityMeatIdol extends AbstractAbnormality {

    // 正在祷告的员工UUID
    private UUID prayingPlayerUUID = null;

    // 祷告计时器（tick）
    private int prayingTimer = 0;

    // 伤害计时器
    private int damageTimer = 0;

    // 伤害间隔（6秒 = 120 tick）
    private static final int DAMAGE_INTERVAL = 120;

    // 治疗计时器
    private int healingTimer = 0;

    // 治疗间隔（1秒 = 20 tick）
    private static final int HEALING_INTERVAL = 20;

    // 随机数生成器
    private final Random random = new Random();

    // 伤害类型数组
    private static final String[] DAMAGE_TYPES = {"red", "white", "black", "blue"};

    public EntityMeatIdol(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        // 基础信息
        this.abnormalityCode = "T-09-79";
        this.abnormalityName = "血肉偶像";
        this.riskLevel = RiskLevel.WAW;
        this.damageType = "WHITE"; // 基础伤害类型，实际使用随机类型
        this.maxPEOutput = 0; // 工具类型不产出PE-BOX

        // 工具类型不使用传统工作偏好
        float[] basePreferences = {0f, 0f, 0f, 0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter();
    }

    // ==================== 工具类型标识 ====================

    @Override
    public boolean isToolType() {
        return true;
    }

    @Override
    public boolean isContinuousUseTool() {
        return true;
    }

    // ==================== 解锁成本 ====================

    @Override
    public int getBasicInfoCost() {
        return 0; // 使用时间解锁
    }

    @Override
    public int getWorkPreferencesCost() {
        return 0;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 0;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 0;
    }

    @Override
    public String getAbnormalityCode() {
        return "T-09-79";
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
        return "meat_idol";
    }

    // ==================== 工具使用逻辑 ====================

    /**
     * 开始祷告
     */
    @Override
    public boolean startUsing(ServerPlayer player) {
        if (prayingPlayerUUID != null) {
            player.sendSystemMessage(Component.literal("§c已有员工正在向血肉偶像祷告！"));
            return false;
        }

        this.prayingPlayerUUID = player.getUUID();
        this.prayingTimer = 0;
        this.damageTimer = 0;
        this.healingTimer = 0;

        player.sendSystemMessage(Component.literal(
                "§e" + player.getName().getString() + " 开始向血肉偶像祷告..."
        ));
        player.sendSystemMessage(Component.literal(
                "§c§l警告：祷告时间不足20秒就结束将会立刻死亡！"
        ));

        return true;
    }

    /**
     * 检查是否有员工正在祷告
     */
    public boolean isInUse() {
        return prayingPlayerUUID != null;
    }

    /**
     * 获取祷告时间（秒）
     */
    public int getPrayingTimeSeconds() {
        return prayingTimer / 20;
    }

    /**
     * 停止祷告（玩家主动取消）
     */
    @Override
    public void stopUsing(ServerPlayer player) {
        if (prayingPlayerUUID == null || !prayingPlayerUUID.equals(player.getUUID())) {
            return;
        }

        int seconds = prayingTimer / 20;

        // 恢复玩家状态
        player.setNoGravity(false);

        // 检查是否可以安全取消
        if (canSafelyCancelUsing(player)) {
            // 安全取消
            onSafeCancel(player);
            player.sendSystemMessage(Component.literal(
                    "§a祷告结束，共持续 " + seconds + " 秒"
            ));
        } else {
            // 强制取消 - 不满20秒就取消：立刻死亡
            onForceCancel(player);
            player.sendSystemMessage(Component.literal(
                    "§4§l你违背了血肉偶像的意志！"
            ));

            // 使用虚空伤害必杀
            DamageSource killDamage = player.damageSources().fellOutOfWorld();
            player.hurt(killDamage, Float.MAX_VALUE);

            player.sendSystemMessage(Component.literal(
                    "§c" + player.getName().getString() + " 因祷告不足而死..."
            ));
        }

        resetPraying();
    }

    @Override
    public void tick() {
        super.tick();

        if (prayingPlayerUUID != null && !this.level().isClientSide) {
            ServerPlayer player = ((ServerLevel)this.level()).getServer()
                    .getPlayerList().getPlayer(prayingPlayerUUID);

            if (player == null || !player.isAlive()) {
                // 祷告者离线或死亡
                resetPraying();
                return;
            }

            // 增加祷告时间
            prayingTimer++;
            int seconds = prayingTimer / 20;

            // 强制玩家位置不动（祈祷时无法移动）
            player.teleportTo(this.getX(), this.getY(), this.getZ());
            player.setDeltaMovement(0, 0, 0);
            player.setNoGravity(true);

            // 每6秒造成一次伤害
            damageTimer++;
            if (damageTimer >= DAMAGE_INTERVAL) {
                damageTimer = 0;
                dealRandomDamage(player);
            }

            // 工作时间超过21秒：为其他玩家恢复生命和精神
            if (seconds >= 21) {
                healingTimer++;
                if (healingTimer >= HEALING_INTERVAL) {
                    healingTimer = 0;
                    healOtherPlayers(player);
                }
            }

            // 工作时间超过90秒：祷告者死亡 + 所有异想体计数器归零
            if (seconds > 90) {
                player.sendSystemMessage(Component.literal(
                        "§4§l血肉偶像的力量失控了！"
                ));

                // 祷告者立刻死亡
                DamageSource killDamage = player.damageSources().fellOutOfWorld();
                player.hurt(killDamage, Float.MAX_VALUE);

                player.sendSystemMessage(Component.literal(
                        "§c" + player.getName().getString() + " 被血肉偶像吞噬..."
                ));

                // 所有异想体计数器归零
                resetAllAbnormalityCounters();

                // 广播消息
                broadcastMessage("§4§l警告：所有异想体的逆卡巴拉计数器已归零！");

                resetPraying();
                return;
            }

            // 每10秒发送状态更新
            if (prayingTimer % 200 == 0) {
                player.sendSystemMessage(Component.literal(
                        String.format("§6祷告时间：%d秒", seconds)
                ));

                if (seconds >= 21 && seconds <= 90) {
                    player.sendSystemMessage(Component.literal(
                            "§a其他员工正在恢复生命值和精神值..."
                    ));
                }
            }

            // 发送状态更新到客户端
            if (prayingTimer % 20 == 0) {
                sendStatusUpdate(player);
            }
        }
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    /**
     * 造成随机类型的伤害（3-6点）
     */
    private void dealRandomDamage(ServerPlayer player) {
        // 随机伤害值（3-6点）
        float damage = 3.0f + random.nextFloat() * 3.0f;

        // 随机伤害类型
        String damageType = DAMAGE_TYPES[random.nextInt(DAMAGE_TYPES.length)];

        // 造成伤害
        DamageSource damageSource = DamageHelper.getDamage(this, "lobotocraft:" + damageType);
        player.hurt(damageSource, damage);

        // 发送消息
        String damageTypeName = switch(damageType) {
            case "red" -> "§c红色";
            case "white" -> "§f白色";
            case "black" -> "§8黑色";
            case "blue" -> "§9蓝色";
            default -> "未知";
        };

        player.sendSystemMessage(Component.literal(
                String.format("§c血肉偶像对你造成了 %.1f 点%s§c伤害！", damage, damageTypeName)
        ));
    }

    /**
     * 治疗其他玩家（除祷告者外）
     */
    private void healOtherPlayers(ServerPlayer prayingPlayer) {
        List<ServerPlayer> players = ((ServerLevel)this.level()).getServer()
                .getPlayerList().getPlayers();

        for (ServerPlayer player : players) {
            // 跳过祷告者本人
            if (player.getUUID().equals(prayingPlayerUUID)) {
                continue;
            }

            // 恢复1点生命值
            if (player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0f);
            }

            // 恢复1点精神值
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                float currentMental = mental.getMentalValue();
                float maxMental = mental.getEffectiveMaxMentalValue();
                if (currentMental < maxMental) {
                    mental.setMentalValue(Math.min(maxMental, currentMental + 1.0f));
                }
            });
        }
    }

    /**
     * 将所有异想体的逆卡巴拉计数器归零
     */
    private void resetAllAbnormalityCounters() {
        List<Entity> entities = this.level().getEntities(
                this,
                this.getBoundingBox().inflate(1000) // 大范围检测
        );

        for (Entity entity : entities) {
            if (entity instanceof IAbnormality abnormality) {
                abnormality.decreaseQliphothCounter(abnormality.getQliphothCounter());
            }
        }
    }

    /**
     * 发送状态更新到客户端
     */
    private void sendStatusUpdate(ServerPlayer player) {
        com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(
                player,
                new com.wzz.lobotocraft.network.packet.ToolUsageUpdatePacket(
                        this.getId(),
                        prayingTimer / 20,
                        0, // 不显示固定伤害值
                        DAMAGE_INTERVAL / 20.0f,
                        0, // 不产出能源
                        0,
                        0,
                        player.getHealth(),
                        player.getMaxHealth()
                )
        );
    }

    /**
     * 重置祷告状态
     */
    private void resetPraying() {
        prayingPlayerUUID = null;
        prayingTimer = 0;
        damageTimer = 0;
        healingTimer = 0;
    }

    @Override
    public String getToolWarningTitle() {
        return "§4§l员工使用后可能会产生未知风险，请谨慎使用";
    }

    @Override
    public String[] getToolWarningMessages() {
        return new String[]{
                "§c当玩家进行工作时每6秒对玩家造成一次不同类型的伤害3-6点",
                "§c当玩家工作不满20秒就选择取消工作则直接死亡",
                "§a玩家工作时间超过21秒，则为其他玩家持续恢复生命值和精神值(一秒恢复1点)",
                "§4如果工作时间超过90秒，那么祷告者会立刻死亡且设施内所有异想体的逆卡巴拉计数器都将归零"
        };
    }

    // ==================== 逆卡巴拉计数器 ====================

    @Override
    public void onQliphothMeltdown() {
        // 此异想体不会突破收容
    }

    @Override
    public void onGoodWork(ServerPlayer player) {
        // 工具类型不使用传统工作
    }

    @Override
    public void onNormalWork(ServerPlayer player) {
        // 工具类型不使用传统工作
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        // 工具类型不使用传统工作
    }

    // ==================== 动画系统 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        // 无动画，但仍需注册控制器
        controllerRegistrar.add(new AnimationController<>(this, "controller", 4, event -> PlayState.STOP));
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.0D);
        builder = builder.add(Attributes.MAX_HEALTH, 300f);
        builder = builder.add(Attributes.ARMOR, 30);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 0);
        builder = builder.add(Attributes.FOLLOW_RANGE, 0);
        builder = builder.add(Attributes.FLYING_SPEED, 0.0D);
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        return builder;
    }

    // ==================== NBT存储 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (prayingPlayerUUID != null) {
            tag.putUUID("PrayingPlayerUUID", prayingPlayerUUID);
        }
        tag.putInt("PrayingTimer", prayingTimer);
        tag.putInt("DamageTimer", damageTimer);
        tag.putInt("HealingTimer", healingTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("PrayingPlayerUUID")) {
            prayingPlayerUUID = tag.getUUID("PrayingPlayerUUID");
        }
        prayingTimer = tag.getInt("PrayingTimer");
        damageTimer = tag.getInt("DamageTimer");
        healingTimer = tag.getInt("HealingTimer");
    }

    // ==================== 可取消使用系统实现 ====================

    @Override
    public boolean canSafelyCancelUsing(ServerPlayer player) {
        // 祷告超过20秒才能安全取消
        return prayingTimer >= 400; // 20秒 = 400 tick
    }

    @Override
    public void onSafeCancel(ServerPlayer player) {
        // 安全取消时的逻辑
        // 可以在这里添加额外的效果，比如给予一些奖励等
    }

    @Override
    public void onForceCancel(ServerPlayer player) {
        // 强制取消时的逻辑已在stopUsing中处理
        // 这里可以添加额外的惩罚效果
    }

    @Override
    public int getRequiredEmployeeLevel() {
        return 1; // 需要等级1才能使用
    }
}