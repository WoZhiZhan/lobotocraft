package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PanicType;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.abnormality.EntityCensored;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class MentalValueEventHandler {

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef");
    private static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-2345-67890abcdef1");

    /** 正处于精神崩溃中的玩家，用于识别"崩溃入场的第一 tick"与"崩溃结束"。 */
    private static final java.util.Set<UUID> BREAKDOWN =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    /** 崩溃减益的补发间隔（3 秒）。 */
    private static final int DEBUFF_REFRESH_TICKS = 60;
    /** 崩溃减益时长，须大于补发间隔，避免两次补发之间断档。 */
    private static final int DEBUFF_DURATION_TICKS = 80;
    /** 失控目标的重新索敌间隔（0.5 秒）；目标不会在 0.05 秒内跑掉，无需每 tick 全 AABB 扫描。 */
    private static final int RETARGET_INTERVAL_TICKS = 10;
    /** 失控索敌的目标缓存；崩溃结束或目标失效时清除，避免长期持有已移除实体。 */
    private static final Map<UUID, LivingEntity> TARGETS = new java.util.HashMap<>();
    /** 谨慎型恐慌自杀倒计时（60 秒）。 */
    private static final int PRUDENCE_PANIC_SUICIDE_TICKS = 20 * 60;
    /** 谨慎型恐慌已持续的 tick 数。 */
    private static final Map<UUID, Integer> PANIC_TICKS = new java.util.HashMap<>();
    private static final java.util.Set<UUID> BERSERK_BREAKDOWN = new java.util.HashSet<>();
    private static final java.util.Set<UUID> PRUDENCE_BREAKDOWN = new java.util.HashSet<>();
    private static final float PRUDENCE_HEAD_PITCH = 72.0F;

    /** 缓存目标是否仍可用：存活、未移除、同世界、仍在索敌半径内。 */
    private static boolean isValidTarget(Player player, LivingEntity target) {
        return isBerserkTarget(player, target)
                && target.level() == player.level()
                && player.distanceToSqr(target) <= 20.0D * 20.0D;
    }

    private static boolean isBerserkTarget(Player player, LivingEntity target) {
        if (target == null || target == player || !target.isAlive() || target.isRemoved()) return false;
        if (target instanceof Player targetPlayer
                && (targetPlayer.isCreative() || targetPlayer.isSpectator())) return false;
        return !(target instanceof AbstractAbnormality abnormality) || abnormality.hasEscape();
    }

    static boolean shouldRecoverPanicFromWhite(boolean targetPanicking,
                                                boolean hasPlayerAttacker,
                                                boolean attackerPanicking) {
        return targetPanicking && hasPlayerAttacker && !attackerPanicking;
    }

    public static boolean isPrudencePanic(Player player) {
        if (player == null) return false;
        return player.getCapability(MentalValueProvider.MENTAL_VALUE)
                .map(mental -> MentalValueUtil.isPanic(mental)
                        && mental.getPanicType() == PanicType.PRUDENCE)
                .orElse(false);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            if (!event.getObject().getCapability(MentalValueProvider.MENTAL_VALUE).isPresent()) {
                event.addCapability(com.wzz.lobotocraft.util.ResourceUtil.createInstance("mental_value"),
                        new MentalValueProvider(player));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                if (MentalValueUtil.isPanic(mental) && mental.getPanicType() == PanicType.NONE) {
                    mental.setPanicType(selectPanicType(serverPlayer, mental));
                } else if (MentalValueUtil.hasRecoveredFromPanic(mental)) {
                    mental.setPanicType(PanicType.NONE);
                }
            });
            syncMentalValue(serverPlayer, MentalValueEvent.ChangeType.SYNC);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                if (!event.isEndConquered()) {
                    mental.setMentalValue(mental.getEffectiveMaxMentalValue());
                }
                syncMentalValue(serverPlayer, MentalValueEvent.ChangeType.SYNC);
            });
        }
    }
    // Colored damage/mental conversion is owned by combat.DamageSettlement.

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }

        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            if (MentalValueUtil.isPanic(mental)) {
                applyMentalBreakdown(player, mental);
            } else {
                removeMentalBreakdown(player);
            }
        });
    }

    @SubscribeEvent
    public static void onMentalValueChanged(MentalValueEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        boolean enteredPanic = event.getOriginalValue() > 0.0F && event.getNewValue() <= 0.0F;
        if (enteredPanic) {
            EntityCensored.onEmployeePanic(serverPlayer);
            serverPlayer.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental ->
                    mental.setPanicType(selectPanicType(serverPlayer, mental)));
            syncMentalValue(serverPlayer, event.getChangeType());
            return;
        }
        serverPlayer.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            if (mental.getPanicType() != PanicType.NONE
                    && MentalValueUtil.hasRecoveredFromPanic(mental)) {
                removeMentalBreakdown(serverPlayer);
                syncMentalValue(serverPlayer, event.getChangeType());
            }
        });
    }

    private static void applyMentalBreakdown(Player player, com.wzz.lobotocraft.capability.IMentalValue mental) {
        UUID playerId = player.getUUID();
        BREAKDOWN.add(playerId);

        // 恐慌类型直接比较四维属性本身。
        // 旧实现比较「最大生命 vs 最大精神」是间接判据：勇气 → 生命的公式为 20+(勇气-20) 且
        // 勇气 <20 时不减益，导致勇气 10 / 谨慎 15 的员工最大生命 20 > 15，被误判为勇气型。
        PanicType panicType = mental.getPanicType();
        if (panicType == PanicType.NONE) {
            panicType = selectPanicType(player, mental);
            mental.setPanicType(panicType);
            if (player instanceof ServerPlayer serverPlayer) {
                syncMentalValue(serverPlayer, MentalValueEvent.ChangeType.SYNC);
            }
        }

        if (panicType == PanicType.BERSERK) {
            if (PRUDENCE_BREAKDOWN.remove(playerId)) {
                clearPrudenceBreakdown(player);
                PANIC_TICKS.remove(playerId);
            }
            BERSERK_BREAKDOWN.add(playerId);
            // 勇气型恐慌：狂暴，不分敌我攻击最近的生物，移动与攻击速度翻倍。
            // 真人保留强制移动；AI 员工由 EmployeeRuntimeManager 使用设施 A* 追击。
            applyBerserkMode(player);
            if (!(player instanceof EmployeeServerPlayer)) autoMoveAndAttack(player);
        } else {
            boolean firstPrudenceTick = PRUDENCE_BREAKDOWN.add(playerId);
            if (BERSERK_BREAKDOWN.remove(playerId)) {
                clearBerserkBreakdown(player);
            }
            // 谨慎型恐慌：最高级缓慢 + 失明，持续 PRUDENCE_PANIC_SUICIDE_TICKS 后自杀。
            // 减益在崩溃入场立即发一次，之后每 DEBUFF_REFRESH_TICKS 补一次（时长覆盖补发间隔，
            // 保证不断档），而非每 tick 重发——每 tick 重发等于每秒 40 个效果同步包。
            if (firstPrudenceTick || player.tickCount % DEBUFF_REFRESH_TICKS == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        DEBUFF_DURATION_TICKS, 255, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                        DEBUFF_DURATION_TICKS, 255, false, false));
            }
            applyPrudencePosture(player);
            tickPrudenceSuicide(player, firstPrudenceTick);
        }
    }

    private static void applyPrudencePosture(Player player) {
        float phase = (player.getUUID().hashCode() & 255) * (Mth.TWO_PI / 255.0F);
        float time = player.tickCount;
        float pitchShake = Mth.sin(time * 0.91F + phase) * 5.0F
                + Mth.sin(time * 2.17F + phase) * 1.5F;
        float yawShake = Mth.sin(time * 1.37F + phase) * 4.0F;
        player.setXRot(Mth.clamp(PRUDENCE_HEAD_PITCH + pitchShake, -90.0F, 90.0F));
        player.setYHeadRot(player.getYRot() + yawShake);
    }

    /** 谨慎型恐慌计时：持续满 {@link #PRUDENCE_PANIC_SUICIDE_TICKS} 后自杀。 */
    private static void tickPrudenceSuicide(Player player, boolean firstTick) {
        UUID id = player.getUUID();
        int elapsed = firstTick ? 0 : PANIC_TICKS.getOrDefault(id, 0) + 1;
        if (elapsed < PRUDENCE_PANIC_SUICIDE_TICKS) {
            PANIC_TICKS.put(id, elapsed);
            return;
        }
        PANIC_TICKS.remove(id);
        // 用 GENERIC_KILL 保证必定致死，不被护甲/抗性/免疫拦下
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    /** 取四维属性；无 EmployeeStats 能力（如原版玩家）时回落到属性推导值。 */
    private static int getStat(Player player, boolean fortitude) {
        return player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .map(stats -> fortitude ? stats.getFortitude() : stats.getPrudence())
                .orElseGet(() -> fortitude
                        ? Math.round(player.getMaxHealth())
                        : Math.round(MentalValueUtil.getEffectiveMaxMentalValue(player)));
    }

    private static void autoMoveAndAttack(Player player) {
        // 索敌降频：全 AABB 扫描每 RETARGET_INTERVAL_TICKS 才做一次，其余 tick 复用缓存目标。
        // 移动与攻击仍逐 tick 执行，手感不变。
        LivingEntity nearestEntity = TARGETS.get(player.getUUID());
        if (!isValidTarget(player, nearestEntity)
                || player.tickCount % RETARGET_INTERVAL_TICKS == 0) {
            nearestEntity = player.level().getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(20.0D),
                            entity -> isBerserkTarget(player, entity))
                    .stream()
                    .min(Comparator.comparingDouble(player::distanceToSqr))
                    .orElse(null);
            if (nearestEntity == null) TARGETS.remove(player.getUUID());
            else TARGETS.put(player.getUUID(), nearestEntity);
        }

        if (nearestEntity != null) {
            double distance = player.distanceTo(nearestEntity);

            if (distance > 2.0D) {
                // 距离较远时，自动移动到生物身边
                double dx = nearestEntity.getX() - player.getX();
                double dy = nearestEntity.getY() - player.getY();
                double dz = nearestEntity.getZ() - player.getZ();

                // 归一化方向向量
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= length;
                dz /= length;

                // 设置玩家移动速度（考虑狂暴模式的速度加成）
                double speed = 0.3D; // 基础移动速度
                player.setDeltaMovement(dx * speed, player.getDeltaMovement().y, dz * speed);

                // 让玩家面向目标
                double angle = Math.atan2(dz, dx);
                float yaw = (float) (angle * 180.0D / Math.PI) - 90.0F;
                player.setYRot(yaw);
                player.setYHeadRot(yaw);
                player.yBodyRot = yaw;

            } else {
                // 距离足够近时，进行攻击
                if (player.tickCount % 10 == 0
                        && player.getAttackStrengthScale(0.0F) >= 1.0F) { // 每0.5秒尝试一次攻击
                    ItemStack stack = player.getMainHandItem();
                    boolean handledByItem = stack.getItem().onLeftClickEntity(stack, player, nearestEntity);
                    if (EgoAttackOwnershipPolicy.shouldUseVanillaFallback(
                            handledByItem, stack.getItem() instanceof BaseEgoWeapon)) {
                        player.attack(nearestEntity);
                    }
                    player.swing(InteractionHand.MAIN_HAND);
                    player.resetAttackStrengthTicker();
                }

                // 攻击时停止移动
                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            }
        } else {
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
        }
    }

    private static void applyBerserkMode(Player player) {
        // 添加移动速度翻倍效果
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            if (movementSpeed.getModifier(SPEED_MODIFIER_UUID) == null) {
                AttributeModifier modifier = new AttributeModifier(
                        SPEED_MODIFIER_UUID,
                        "Mental breakdown speed boost",
                        1.0D, // 翻倍
                        AttributeModifier.Operation.MULTIPLY_TOTAL
                );
                movementSpeed.addTransientModifier(modifier);
            }
        }

        // 添加攻击速度翻倍效果
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            if (attackSpeed.getModifier(ATTACK_SPEED_MODIFIER_UUID) == null) {
                AttributeModifier modifier = new AttributeModifier(
                        ATTACK_SPEED_MODIFIER_UUID,
                        "Mental breakdown attack speed boost",
                        1.0D, // 翻倍
                        AttributeModifier.Operation.MULTIPLY_TOTAL
                );
                attackSpeed.addTransientModifier(modifier);
            }
        }
    }

    private static void clearBerserkBreakdown(Player player) {
        TARGETS.remove(player.getUUID());
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) movementSpeed.removeModifier(SPEED_MODIFIER_UUID);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER_UUID);
    }

    static PanicType selectPanicType(float effectiveMaxHealth, float effectiveMaxMental) {
        return PanicType.resolve(PanicType.NONE, true, effectiveMaxHealth, effectiveMaxMental);
    }

    private static PanicType selectPanicType(
            Player player, com.wzz.lobotocraft.capability.IMentalValue mental) {
        return selectPanicType(player.getMaxHealth(), mental.getEffectiveMaxMentalValue());
    }

    private static void clearPrudenceBreakdown(Player player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
        player.setXRot(0.0F);
        player.xRotO = 0.0F;
        player.setYHeadRot(player.getYRot());
        player.yHeadRotO = player.getYRot();
    }

    private static void removeMentalBreakdown(Player player) {
        removeMentalBreakdown(player, true);
    }

    public static void forceRecover(ServerPlayer player) {
        if (player == null) return;
        removeMentalBreakdown(player, true);
        player.getPersistentData().remove("isharmla_panic");
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.setPanicType(PanicType.NONE);
            mental.setMentalValue(mental.getEffectiveMaxMentalValue(), MentalValueEvent.ChangeType.SET);
        });
        syncMentalValue(player, MentalValueEvent.ChangeType.SET);
    }

    private static void removeMentalBreakdown(Player player, boolean clearPanicType) {
        if (clearPanicType) {
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental ->
                    mental.setPanicType(PanicType.NONE));
        }
        // 未处于崩溃态时直接返回：此前每 tick 无条件调用 removeModifier，属无谓开销
        if (!BREAKDOWN.remove(player.getUUID())) return;
        TARGETS.remove(player.getUUID());
        PANIC_TICKS.remove(player.getUUID());
        BERSERK_BREAKDOWN.remove(player.getUUID());
        boolean wasPrudencePanic = PRUDENCE_BREAKDOWN.remove(player.getUUID());
        clearBerserkBreakdown(player);
        if (wasPrudencePanic) clearPrudenceBreakdown(player);

        // 移除速度修改器
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(SPEED_MODIFIER_UUID);
        }

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER_UUID);
        }

        // 崩溃结束：清空由崩溃施加的减益，不留残留时长
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
    }

    private static void syncMentalValue(ServerPlayer player, MentalValueEvent.ChangeType changeType) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            MessageLoader.getLoader().sendToPlayer(player,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue(),
                            changeType, mental.getPanicType()));
        });
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeMentalBreakdown(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Clear runtime effects and entity references, but keep the selected type
        // in capability NBT when a still-panicking player reconnects.
        removeMentalBreakdown(event.getEntity(), false);
        UUID playerId = event.getEntity().getUUID();
        BREAKDOWN.remove(playerId);
        TARGETS.remove(playerId);
        PANIC_TICKS.remove(playerId);
        BERSERK_BREAKDOWN.remove(playerId);
        PRUDENCE_BREAKDOWN.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BREAKDOWN.clear();
        TARGETS.clear();
        PANIC_TICKS.clear();
        BERSERK_BREAKDOWN.clear();
        PRUDENCE_BREAKDOWN.clear();
    }
}
