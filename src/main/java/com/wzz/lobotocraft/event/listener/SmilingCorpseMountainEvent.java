package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.event.definition.company.CompanyDayAdvanceEvent;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.item.ego.smiling_corpse_mountain.SmilingCorpseMountainBaseArmor;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * 微笑的尸山 套装被动 / 触发效果集合。
 *
 * 护甲(穿全套且锁定):
 *   - 每清 1 石碑, 当天攻速&移速 +2%(最多 +200%), 死亡或隔天归零(见 reset)。
 * 饰品(全套 isFullEGO):
 *   - 每清 1 石碑, 该武器造成的伤害 +1%;
 *   - 每 30s 生成 25s 护盾(值 = 清墓碑数×2, 上限 50), 期间白伤扣盾、黑伤不掉精神;
 *   - (多段/减速由武器特殊攻击结算处理)
 * 通用:
 *   - 带腐败的生物周身按等级环绕黑色粒子。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class SmilingCorpseMountainEvent {

    private static final String SET_ID = "smiling_corpse_mountain";

    // 攻速/移速加成 modifier
    private static final UUID TOMB_AS_UUID = UUID.fromString("c0ffee00-5001-4000-8000-000000005001");
    private static final UUID TOMB_MS_UUID = UUID.fromString("c0ffee00-5002-4000-8000-000000005002");

    // 护盾参数
    private static final int SHIELD_INTERVAL = 600; // 30s
    private static final int SHIELD_DURATION = 500; // 25s
    private static final float SHIELD_MAX = 50F;

    // persistentData 键
    private static final String SHIELD_VALUE = "smiling_shield_value";
    private static final String SHIELD_UNTIL = "smiling_shield_until";
    private static final String SHIELD_NEXT_REGEN = "smiling_shield_next_regen";
    private static final String SHIELD_BLACK_FLAG = "smiling_shield_black_pending";
    private static final String SHIELD_MENTAL_BEFORE = "smiling_shield_mental_before";

    // ========================================================================
    // 每 tick: 护甲攻速移速加成 + 护盾生成
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        // --- 护甲: 攻速&移速 +2%/墓碑, 最多 +200% ---
        boolean fullSet = EgoArmorHelper.isWearingFullSet(player, SET_ID);
        double bonus = 0D;
        if (fullSet) {
            int count = getClearedCount(player);
            bonus = 0.02D * Math.min(count, 100);
        }
        applyMultiply(player.getAttribute(Attributes.ATTACK_SPEED), TOMB_AS_UUID, "smiling_tomb_as", bonus);
        applyMultiply(player.getAttribute(Attributes.MOVEMENT_SPEED), TOMB_MS_UUID, "smiling_tomb_ms", bonus);

        // --- 饰品: 每 30s 生成护盾 ---
        boolean fullEgo = EgoArmorHelper.isFullEGO(player, SET_ID);
        if (fullEgo) {
            long now = player.level().getGameTime();
            long next = player.getPersistentData().getLong(SHIELD_NEXT_REGEN);
            if (next <= 0L) {
                // 首个护盾在 30s 后生成
                player.getPersistentData().putLong(SHIELD_NEXT_REGEN, now + SHIELD_INTERVAL);
            } else if (now >= next) {
                float value = Math.min(getClearedCount(player) * 2F, SHIELD_MAX);
                player.getPersistentData().putFloat(SHIELD_VALUE, value);
                player.getPersistentData().putLong(SHIELD_UNTIL, now + SHIELD_DURATION);
                player.getPersistentData().putLong(SHIELD_NEXT_REGEN, now + SHIELD_INTERVAL);
            }
        } else {
            // 不满足条件: 清掉护盾与计划
            if (player.getPersistentData().getLong(SHIELD_NEXT_REGEN) != 0L
                    || player.getPersistentData().getFloat(SHIELD_VALUE) != 0F) {
                clearShield(player);
            }
        }
    }

    private static void applyMultiply(AttributeInstance inst, UUID id, String name, double amount) {
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(id);
        if (amount <= 0D) {
            if (existing != null) inst.removeModifier(id);
            return;
        }
        if (existing != null) {
            if (existing.getAmount() != amount) {
                inst.removeModifier(id);
                inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ========================================================================
    // 腐败: 黑色粒子环绕(按等级)
    // ========================================================================
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 5 != 0) return;
        MobEffectInstance corr = entity.getEffect(ModMobEffects.CORRUPTION.get());
        if (corr == null) return;
        int amp = corr.getAmplifier(); // 0..4
        int count = (amp + 1) * 3;
        // 近黑色粒子, 等级越高越密越大
        ParticleUtil.spawnParticles(entity,
                ParticleUtil.getDustParticle(0.05F, 0.05F, 0.06F, 1.0F + amp * 0.3F),
                count, 0.03D);
    }

    // ========================================================================
    // LivingHurt HIGHEST: 攻击方 +1%伤害; 受击方护盾(白伤扣盾 / 黑伤记录)
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurtPre(LivingHurtEvent event) {
        // ---- 攻击方: 饰品全套时, 该武器造成的伤害 +1%/墓碑 ----
        Entity srcEntity = event.getSource().getEntity();
        if (srcEntity instanceof Player attacker && !attacker.level().isClientSide) {
            if (EgoArmorHelper.isFullEGO(attacker, SET_ID)
                    && EgoArmorHelper.isHoldingWeapon(attacker, SET_ID)) {
                int count = getClearedCount(attacker);
                if (count > 0) {
                    event.setAmount(event.getAmount() * (1F + 0.01F * count));
                }
            }
        }

        // ---- 受击方: 护盾 ----
        if (event.getEntity() instanceof Player victim && !victim.level().isClientSide) {
            long now = victim.level().getGameTime();
            if (!isShieldActive(victim, now)) return;

            DamageSource source = event.getSource();
            float amount = event.getAmount();

            if (DamageHelper.isWhiteDamage(source)) {
                // 白伤: 由护盾承受
                float shield = getShield(victim);
                if (shield >= amount) {
                    setShield(victim, shield - amount);
                    event.setCanceled(true); // 完全吸收, 精神不掉
                } else {
                    setShield(victim, 0F);
                    event.setAmount(amount - shield); // 溢出部分继续走精神扣除
                }
            } else if (DamageHelper.isBlackDamage(source)) {
                // 黑伤: 照常扣血, 但精神不掉 —— 记录当前精神, 待 LOWEST 补回
                victim.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                    victim.getPersistentData().putBoolean(SHIELD_BLACK_FLAG, true);
                    victim.getPersistentData().putFloat(SHIELD_MENTAL_BEFORE, mental.getMentalValue());
                });
            }
        }
    }

    // ========================================================================
    // LivingHurt LOWEST: 把护盾期间黑伤扣掉的精神补回(receiveCanceled 确保总能收尾)
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurtPost(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (victim.level().isClientSide) return;
        if (!victim.getPersistentData().getBoolean(SHIELD_BLACK_FLAG)) return;

        victim.getPersistentData().putBoolean(SHIELD_BLACK_FLAG, false);
        float before = victim.getPersistentData().getFloat(SHIELD_MENTAL_BEFORE);
        victim.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            // 若因抗性被取消, before==当前值, 相当于 no-op; 否则把被扣的精神补回
            mental.setMentalValue(before);
            MessageLoader.getLoader().sendToPlayer(victim,
                    new MentalValueSyncPacket(mental.getMentalValue(), mental.getMaxMentalValue()));
        });
    }

    // ========================================================================
    // 归零: 死亡 / 隔天
    // ========================================================================
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            resetSmiling(player);
        }
    }

    @SubscribeEvent
    public static void onDayAdvance(CompanyDayAdvanceEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            resetSmiling(player);
        }
    }

    private static void resetSmiling(Player player) {
        for (ItemStack piece : EgoArmorHelper.getArmorPiecesBySet(player, SET_ID)) {
            SmilingCorpseMountainBaseArmor.resetClearedCount(piece);
        }
        clearShield(player);
    }

    // ========================================================================
    // 护盾状态工具
    // ========================================================================
    private static float getShield(Player p) {
        return p.getPersistentData().getFloat(SHIELD_VALUE);
    }

    private static void setShield(Player p, float v) {
        p.getPersistentData().putFloat(SHIELD_VALUE, Math.max(0F, v));
    }

    private static boolean isShieldActive(Player p, long now) {
        return getShield(p) > 0F && p.getPersistentData().getLong(SHIELD_UNTIL) > now;
    }

    private static void clearShield(Player p) {
        p.getPersistentData().putFloat(SHIELD_VALUE, 0F);
        p.getPersistentData().putLong(SHIELD_UNTIL, 0L);
        p.getPersistentData().putLong(SHIELD_NEXT_REGEN, 0L);
    }

    // 从所穿套装护甲读取"已清墓碑数"(每片计数一致, 读第一片即可)
    private static int getClearedCount(Player player) {
        List<ItemStack> pieces = EgoArmorHelper.getArmorPiecesBySet(player, SET_ID);
        if (pieces.isEmpty()) return 0;
        return SmilingCorpseMountainBaseArmor.getClearedCount(pieces.get(0));
    }
}
