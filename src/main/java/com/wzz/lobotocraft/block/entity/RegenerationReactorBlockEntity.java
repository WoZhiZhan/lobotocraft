package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.ParticleUtil;
import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;

public class RegenerationReactorBlockEntity extends BaseGeoBlockEntity {
    
    // 配置参数
    private static final double EFFECT_RADIUS = 16.0; // 影响范围（方块）
    private static final int HEAL_INTERVAL = 200; // 恢复间隔
    private static final float HEAL_PERCENTAGE = 0.2f; // 恢复百分比
    
    // GeckoLib动画
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.hx-1.1");
    
    // 计时器
    private int tickCounter = 0;
    
    public RegenerationReactorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.REGENERATION_REACTOR.get(), pos, blockState);
    }
    
    /**
     * 服务端Tick逻辑
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RegenerationReactorBlockEntity blockEntity) {
        blockEntity.tickCounter++;

        if (blockEntity.tickCounter >= HEAL_INTERVAL) {
            blockEntity.tickCounter = 0;
            blockEntity.performHealing(level, pos);
        }
        if (blockEntity.tickCounter % 10 == 0) {
            blockEntity.sendMessage(level, pos);
        }
    }

    public int getHealCountdown() {
        return Math.max(0, (HEAL_INTERVAL - tickCounter) / 20); // 转换为秒
    }

    public float getHealProgress() {
        return (float) tickCounter / HEAL_INTERVAL;
    }

    private void sendMessage(Level level, BlockPos pos) {
        AABB range = new AABB(pos).inflate(EFFECT_RADIUS);
        List<Player> players = level.getEntitiesOfClass(Player.class, range);

        for (Player player : players) {
            if (player.isDeadOrDying()) continue;
            // 检查视线是否被遮挡
            //boolean isBlocked = !hasLineOfSight(level, pos, player);

            // 获取倒计时
            int countdown = this.getHealCountdown();

            // 构建消息
            Component message;

//            if (isBlocked) {
//                // 被遮挡：显示红色警告
//                message = Component.literal("§c处于再生反应堆治疗范围中 §7(" + countdown + "秒)");
//            } else {
                // 正常状态：显示绿色信息
                String progressBar = getProgressBar(this.getHealProgress());
                message = Component.literal("§a处于再生反应堆治疗范围中 " + progressBar + " §7(§e" + countdown + "§7秒)");

            player.displayClientMessage(message, true);
        }
    }

    /**
     * 生成进度条
     */
    private String getProgressBar(float progress) {
        int bars = 10;
        int filled = (int) (progress * bars);
        StringBuilder sb = new StringBuilder();

        sb.append("§a[");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                sb.append("█");
            } else {
                sb.append("§7░");
            }
        }
        sb.append("§a]");

        return sb.toString();
    }

    /**
     * 执行治疗逻辑
     */
    private void performHealing(Level level, BlockPos pos) {

        AABB range = new AABB(pos).inflate(EFFECT_RADIUS);
        List<Player> players = level.getEntitiesOfClass(Player.class, range);

        for (Player player : players) {
            if (player.isDeadOrDying()) continue;
            boolean hasSight = hasLineOfSight(level, pos, player);
            if (hasSight) {
                healPlayer(player);
            }
        }

        List<EntityClerk> clerks = level.getEntitiesOfClass(EntityClerk.class, range);
        for (EntityClerk clerk : clerks) {
            if (clerk.isDeadOrDying()) continue;
            boolean hasSight = hasLineOfSight(level, pos, clerk);
            if (hasSight) {
                healClerk(clerk);
            }
        }
    }

    /**
     * 检查视线是否通畅（玩家与方块之间没有墙壁阻挡）
     * 修复：反应堆嵌在地里(被方块包围)时,旧逻辑从反应堆中心发射线会立即撞到周围方块,
     * 导致永远判定为被遮挡、玩家无法回血。现改为从玩家眼睛射向反应堆,
     * 只要射线命中点足够接近反应堆(基本到达),即视为视线通畅。
     */
    private boolean hasLineOfSight(Level level, BlockPos reactorPos, LivingEntity entity) {
        // 玩家眼睛位置
        Vec3 playerEye = entity.getEyePosition();

        // 反应堆中心点
        Vec3 reactorCenter = new Vec3(
                reactorPos.getX() + 0.5,
                reactorPos.getY() + 0.5,
                reactorPos.getZ() + 0.5
        );

        // 射线检测：从玩家眼睛射向反应堆中心
        ClipContext context = new ClipContext(
                playerEye,
                reactorCenter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity
        );

        BlockHitResult result = level.clip(context);

        // 完全没有命中方块 = 视线通畅
        if (result.getType() == HitResult.Type.MISS) {
            return true;
        }

        // 命中了方块：若命中点已经非常接近反应堆,说明射线基本到达反应堆,
        // 只是被反应堆自身或与其紧邻的方块挡住(例如反应堆嵌地里),仍视为通畅。
        double distToReactor = result.getLocation().distanceToSqr(reactorCenter);
        if (distToReactor <= 2.25) { // 1.5 格以内
            return true;
        }

        // 命中的就是反应堆所在方块本身,也算到达
        return result.getBlockPos().equals(reactorPos);
    }
    
    /**
     * 治疗玩家
     * 修改：玩家工作时无法受到治疗
     */
    private void healPlayer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (WorkManager.isPlayerWorking(serverPlayer)) {
            return;
        }
        float maxHealth = player.getMaxHealth();
        if (MentalValueUtil.getMentalValue(serverPlayer) <= 0) {
            return;
        }

        float healAmount = maxHealth * HEAL_PERCENTAGE;
        float newHealth = Math.min(player.getHealth() + healAmount, maxHealth);
        player.setHealth(newHealth);
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 60));
        ParticleUtil.spawnParticlesAroundEntity(player, ParticleTypes.HAPPY_VILLAGER, (int) healAmount, 0.1d);
        
        // 恢复精神值（20%）
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            float maxMental = mental.getEffectiveMaxMentalValue();
            float currentMental = mental.getMentalValue();
            float mentalHeal = maxMental * HEAL_PERCENTAGE;
            float newMental = Math.min(currentMental + mentalHeal, maxMental);
            ParticleUtil.spawnParticlesAroundEntity(player, ModParticleTypes.BLUE_GLINT.get(), (int) mentalHeal, 0.1d);
            mental.setMentalValue(newMental);
            MessageLoader.getLoader().sendToPlayer(serverPlayer,
                    new MentalValueSyncPacket((int) newMental, (int) mental.getMaxMentalValue()));
        });
    }

    private void healClerk(EntityClerk clerk) {
        float maxHealth = clerk.getMaxHealth();
        float healAmount = maxHealth * HEAL_PERCENTAGE;
        float newHealth = Math.min(clerk.getHealth() + healAmount, maxHealth);
        clerk.setHealth(newHealth);
        ParticleUtil.spawnParticlesAroundEntity(clerk, ParticleTypes.HAPPY_VILLAGER, (int) healAmount, 0.1d);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }
    
    /**
     * 动画状态机
     */
    private PlayState predicate(AnimationState<RegenerationReactorBlockEntity> state) {
        // 持续播放循环动画
        state.getController().setAnimation(IDLE_ANIMATION);
        return PlayState.CONTINUE;
    }
}
