package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.DamageBorderPacket;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class MentalValueEventHandler {

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef");
    private static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-2345-67890abcdef1");
    private static final Random RANDOM = new Random();

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
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getOriginal().getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(oldMental -> {
                event.getEntity().getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(newMental -> {
                    newMental.copyFrom(oldMental);
                });
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            syncMentalValue(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 重生时恢复满精神值
            serverPlayer.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                mental.setMentalValue(mental.getEffectiveMaxMentalValue());
                syncMentalValue(serverPlayer);
            });
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        DamageSource source = event.getSource();
        float damage = event.getAmount();

        // 发送边框效果到客户端
        if (player instanceof ServerPlayer serverPlayer) {
            String damageTypeId = getDamageTypeId(source);
            if (damageTypeId != null) {
                MessageLoader.getLoader().sendToPlayer(serverPlayer, new DamageBorderPacket(damageTypeId));
            }
        }
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            // 获取对应的抗性值
            double resistance = getResistanceForDamageType(player, source);

            // 如果抗性为-1或更低，免疫并回血
            if (resistance <= -1.0D) {
                event.setCanceled(true);
                player.heal(damage); // 回复等同于伤害值的生命值
                return;
            }

            // 如果抗性为0，完全免疫
            if (resistance <= 0.0D) {
                event.setCanceled(true);
                return;
            }

            // 计算实际伤害
            // resistance < 1: 减少伤害 (例如 0.5 = 减半)
            // resistance = 1: 正常伤害
            // resistance > 1: 增加伤害 (例如 2.0 = 双倍伤害)
            float actualDamage = damage * (float) resistance;

            // 白色伤害：只扣精神值
            if (DamageHelper.isWhiteDamage(source)) {
                mental.reduceMentalValue(actualDamage);
                event.setCanceled(true); // 取消血量伤害
                if (player instanceof ServerPlayer serverPlayer) {
                    syncMentalValue(serverPlayer);
                }
            }
            // 黑色伤害：同时扣血量和精神值
            else if (DamageHelper.isBlackDamage(source)) {
                mental.reduceMentalValue(actualDamage);
                event.setAmount(actualDamage); // 设置实际伤害
                if (player instanceof ServerPlayer serverPlayer) {
                    syncMentalValue(serverPlayer);
                }
            }
            // 蓝色伤害：基础伤害 + 最大生命值的5%-7%
            else if (DamageHelper.isBlueDamage(source)) {
                float maxHealth = player.getMaxHealth();
                float percentDamage = maxHealth * (0.05F + RANDOM.nextFloat() * 0.02F); // 5%-7%
                event.setAmount(actualDamage + percentDamage); // 应用抗性后的伤害 + 百分比伤害
            }
            // 红色伤害：只扣血量
            else if (DamageHelper.isRedDamage(source)) {
                event.setAmount(actualDamage); // 设置实际伤害
            }
            // 其他伤害类型（环境伤害、普通生物攻击等）也应用红抗
            else {
                // 对于非特殊伤害类型，使用红抗来处理
                double redResistance = player.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get());
                
                // 如果红抗为-1或更低，免疫并回血
                if (redResistance <= -1.0D) {
                    event.setCanceled(true);
                    player.heal(damage);
                    return;
                }
                
                // 如果红抗为0，完全免疫
                if (redResistance <= 0.0D) {
                    event.setCanceled(true);
                    return;
                }
                
                // 应用红抗
                event.setAmount(damage * (float) redResistance);
            }
        });
    }

    /**
     * 根据伤害类型获取对应的抗性值
     */
    private static double getResistanceForDamageType(Player player, DamageSource source) {
        Holder<DamageType> holder = source.typeHolder();
        Optional<ResourceKey<DamageType>> optionalKey = holder.unwrapKey();
        if (DamageHelper.isRedDamage(source) || optionalKey.isPresent() && DamageHelper.isVanillaDamage(optionalKey.get())) {
            return player.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        } else if (DamageHelper.isWhiteDamage(source)) {
            return player.getAttributeValue(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        } else if (DamageHelper.isBlackDamage(source)) {
            return player.getAttributeValue(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        } else if (DamageHelper.isBlueDamage(source)) {
            return player.getAttributeValue(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
        }
        return 1.0D; // 默认无抗性
    }

    /**
     * 获取伤害类型ID用于边框显示
     */
    private static String getDamageTypeId(DamageSource source) {
        if (DamageHelper.isRedDamage(source)) {
            return "red";
        } else if (DamageHelper.isWhiteDamage(source)) {
            return "white";
        } else if (DamageHelper.isBlackDamage(source)) {
            return "black";
        } else if (DamageHelper.isBlueDamage(source)) {
            return "blue";
        }
        return "red";
    }

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
            if (mental.isMentalValueEmpty()) {
                applyMentalBreakdown(player, mental);
            } else {
                removeMentalBreakdown(player);
            }
        });
    }

    private static void applyMentalBreakdown(Player player, com.wzz.lobotocraft.capability.IMentalValue mental) {
        float maxHealth = player.getMaxHealth();
        float maxMental = mental.getEffectiveMaxMentalValue();

        if (maxHealth > maxMental) {
            // 情况1：血量上限 > 精神值上限
            // 玩家自动移动到生物身边并攻击，移动和攻击速度翻倍
            applyBerserkMode(player);

            // 自动移动到最近的生物并攻击
            autoMoveAndAttack(player);
        } else {
            // 情况2：精神值上限 > 血量上限
            // 获得最高等级的缓慢和失明
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 255, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 255, false, false));
        }
    }

    private static void autoMoveAndAttack(Player player) {
        // 查找最近的生物
        LivingEntity nearestEntity = player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(20.0D),
                        entity -> entity != player && entity.isAlive())
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);

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

            } else {
                // 距离足够近时，进行攻击
                if (player.tickCount % 10 == 0) { // 每0.5秒攻击一次
                    player.swing(InteractionHand.MAIN_HAND);
                    player.attack(nearestEntity);
                }

                // 攻击时停止移动
                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            }
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

    private static void removeMentalBreakdown(Player player) {
        // 移除速度修改器
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(SPEED_MODIFIER_UUID);
        }

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER_UUID);
        }
    }

    private static void syncMentalValue(ServerPlayer player) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            MessageLoader.getLoader().sendToPlayer(player,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue()));
        });
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeMentalBreakdown(player);
        }
    }
}