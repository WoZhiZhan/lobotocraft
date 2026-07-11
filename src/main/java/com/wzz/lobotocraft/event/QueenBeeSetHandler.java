package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.effect.SporeEffect;
import com.wzz.lobotocraft.init.ModEffects;
import com.wzz.lobotocraft.util.DotHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 蜂后(queen_bee)套装效果
 *
 * 1. 玩家造成伤害命中目标后，为其施加【孢子】：无法回血、-10%移速、
 *    每2秒 8点红色DOT，持续10秒。（DOT 与减速在 {@link SporeEffect} 里）
 * 2. 攻击带【威胁】的目标时，若目标对应伤害抗性 > 0.0，所有 DOT 无视抗性。
 *    （在 {@link DotHelper#shouldIgnoreResistance} 里）
 * 3. 玩家生命值低于20%时，被敌对生物优先仇恨，且受到的伤害提高30%。
 */
@Mod.EventBusSubscriber
public class QueenBeeSetHandler {

    /** 低血量阈值 */
    public static final float LOW_HEALTH_RATIO = 0.2f;
    /** 低血量时受到的伤害倍率 */
    public static final float LOW_HEALTH_DAMAGE_TAKEN = 1.3f;
    /** 仇恨拉取半径 */
    public static final double AGGRO_RANGE = 24D;

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target == null || target.level().isClientSide) return;

        // 第1条：命中施加孢子（DOT 本身造成的伤害不再叠孢子）
        if (!DotHelper.isDotDamage(target)
                && event.getSource().getEntity() instanceof Player attacker
                && EgoArmorHelper.isFullEGO(attacker, "queen_bee")) {
            SporeEffect.applySpore(attacker, target);
        }

        // 第3条：自身低于20%生命值时受到的伤害 +30%
        if (target instanceof Player player
                && EgoArmorHelper.isFullEGO(player, "queen_bee")
                && isLowHealth(player)) {
            event.setAmount(event.getAmount() * LOW_HEALTH_DAMAGE_TAKEN);
        }
    }

    /** 孢子：无法恢复生命值 */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModEffects.SPORE.get())) {
            event.setCanceled(true);
        }
    }

    /** 第3条：低血量时被敌对生物优先标记为仇恨目标 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;
        if (!player.isAlive() || !isLowHealth(player)) return;
        if (!EgoArmorHelper.isFullEGO(player, "queen_bee")) return;

        for (Mob mob : EntityUtil.findAllEntities(player, AGGRO_RANGE, Mob.class)) {
            if (!(mob instanceof Enemy)) continue;
            if (mob.getTarget() == player) continue;
            if (!mob.canAttack(player)) continue;
            mob.setTarget(player);
        }
    }

    private static boolean isLowHealth(Player player) {
        return player.getMaxHealth() > 0f
                && player.getHealth() < player.getMaxHealth() * LOW_HEALTH_RATIO;
    }
}