package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class EgoAttackChargeHandler {
    private EgoAttackChargeHandler() {
    }

    /**
     * Forge calls {@link net.minecraft.world.item.Item#onLeftClickEntity} only after every
     * {@link AttackEntityEvent} listener has completed.  Some E.G.O implementations return
     * {@code false} when their custom attack is unavailable; Forge then continues into
     * {@link Player#attack} and applies an unintended vanilla PLAYER_ATTACK hit.
     *
     * <p>Take ownership at LOWEST priority so higher-priority locks (for example the teddy
     * execution lock) can still cancel the attack first.  We invoke the weapon hook exactly
     * once and then cancel the event regardless of the hook's return value.  Direct custom
     * {@code target.hurt(...)} calls do not post AttackEntityEvent, so colored E.G.O damage
     * continues through LivingAttackEvent/LivingHurtEvent normally.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        boolean holdingEgoWeapon = stack.getItem() instanceof BaseEgoWeapon;
        if (!EgoAttackOwnershipPolicy.shouldRunCustomAttack(event.isCanceled(), holdingEgoWeapon)) {
            return;
        }

        BaseEgoWeapon weapon = (BaseEgoWeapon) stack.getItem();
        event.setCanceled(true);
        weapon.onLeftClickEntity(stack, player, event.getTarget());
    }

    /** Invoked in the explicit DamageSettlement order; not an independent listener. */
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !BaseEgoWeapon.hasCurrentLeftClickAttack(attacker)) return;
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && !DamageHelper.isRedDamage(event.getSource())
                && !DamageHelper.isWhiteDamage(event.getSource())
                && !DamageHelper.isBlackDamage(event.getSource())
                && !DamageHelper.isBlueDamage(event.getSource())) return;

        event.setAmount(event.getAmount()
                * BaseEgoWeapon.currentLeftClickDamageMultiplier(attacker));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (BaseEgoWeapon.consumePendingAttackStrengthReset(player)) {
            player.resetAttackStrengthTicker();
        }
    }
}
