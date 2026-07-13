package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon.*;

/**
 * 圣宣「蝶之葬礼」的全局效果。
 * 之所以走事件而不是写死在 performShot 里：
 * - 「救赎」加成的措辞是"受到圣宣玩家的伤害"，所以近战/其他来源也该吃加成；
 * - 宣判的"所有伤害无视无敌帧"同理。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class ButterflyFuneralEvents {

    /** 防止附加蓝色伤害递归触发本处理器 */
    private static boolean processingBonusDamage = false;

    /* ---------------- 宣判：无视无敌帧 ---------------- */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!ButterflyFuneralWeapon.isJudging(player)) return;
        // LivingAttackEvent 在 LivingEntity#hurt 的最开头触发，早于无敌帧判定，
        // 所以这里把 invulnerableTime 清掉就能让这次伤害无条件生效
        EntityUtil.clearHurtTime(event.getEntity());
    }

    /* ---------------- 救赎加伤 / 蓝色附伤 / 叠加救赎 ---------------- */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (processingBonusDamage) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 所有这些是 E.G.O 套装特效，未集齐不生效
        if (!EgoArmorHelper.isFullEGO(player, EGO_SET_NAME)) return;
        if (!ButterflyFuneralWeapon.isHoldingWeapon(player)) return;

        LivingEntity target = event.getEntity();

        // 1. 每层救赎让目标受到本玩家的伤害 +1%
        int redemption = ButterflyFuneralWeapon.getRedemptionLayers(target);
        if (redemption > 0) {
            event.setAmount(event.getAmount() * (1.0F + REDEMPTION_DAMAGE_PER_LAYER * redemption));
        }

        // 2. 5 层蝶引 + 50 层救赎 -> 额外 1 点蓝色伤害
        //    延迟到本 tick 末执行，避免在 hurt() 内部递归调用 hurt()
        if (ButterflyFuneralWeapon.getButterflyLayers(player) >= MAX_BUTTERFLY_FULL_EGO
                && redemption >= MAX_REDEMPTION) {
            EntityUtil.clearHurtTime(target, () -> {
                processingBonusDamage = true;
                try {
                    target.hurt(DamageHelper.getDamage(player, "blue"), BLUE_BONUS_DAMAGE);
                } finally {
                    processingBonusDamage = false;
                }
            });
        }

        // 3. 每次造成伤害为目标叠加一层救赎（已满则刷新时间）
        ButterflyFuneralWeapon.addRedemptionLayer(target);
    }

    /* ---------------- 宣判：死亡退出 ---------------- */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && ButterflyFuneralWeapon.isJudging(player)) {
            ButterflyFuneralWeapon.exitJudgement(player);
        }
    }

    /* ---------------- 宣判：持枪条件不再满足时自动退出 ---------------- */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;
        if (!ButterflyFuneralWeapon.isJudging(player)) return;
        if (!ButterflyFuneralWeapon.canJudge(player)) {
            ButterflyFuneralWeapon.exitJudgement(player);
        }
    }
}