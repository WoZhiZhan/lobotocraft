package com.wzz.lobotocraft.combat;

import com.wzz.lobotocraft.ModMain;
import com.lobotocraft.facility.damage.DamageHistory;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionDamageHooks;
import com.wzz.lobotocraft.core_suppression.geburah.*;
import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.event.ForgeModEvent;
import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.event.listener.*;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.ego.army_in_black.ArmyInBlackBaseArmor;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.must_open_path.MustOpenPathMechanics;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.*;
import com.wzz.lobotocraft.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import static com.wzz.lobotocraft.combat.DamageCalculation.*;

/**
 * Single owner of the mod's Hurt-stage amount. External attack vetoes run first.
 * Equipment contributes in an explicit order, then resistance/channel conversion occurs once.
 * Vanilla still owns armor, absorption, health application, death and damage attribution.
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class DamageSettlement {
    private DamageSettlement() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        try {
            settleHurt(event);
        } finally {
            if (!event.getEntity().level().isClientSide) DamageHistory.finishHurt(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        try {
            if (event.isCanceled()) return;
            DotHelper.onLivingDamage(event);
            if (event.getAmount() <= 0) return;
            ForgeModEvent.applyHealthProtection(event);
            if (event.isCanceled() || event.getAmount() <= 0) return;
            SkadiEvent.onDamage(event);
            if (event.isCanceled() || event.getAmount() <= 0) return;
            ForgeModEvent.onLivingDamage(event);
            CleanerEvent.onLivingDamage(event);
            MustOpenPathMechanics.onDamage(event);
            GeburahDamageEvents.onLivingDamage(event);
        } finally {
            DamageHistory.finishDamage(event);
        }
    }

    private static void settleHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled()) return;
        // Existing LargeBird execution intentionally submits +Infinity. Preserve its
        // lethal magnitude without allowing Infinity/NaN to enter channel arithmetic.
        if (DamageHelper.isExecution(event.getSource()) && event.getAmount() == Float.POSITIVE_INFINITY)
            event.setAmount(Float.MAX_VALUE);
        if (DamageCalculation.positive(event.getAmount()) == 0) {
            event.setAmount(0);
            event.setCanceled(true);
            return;
        }
        DamageSource source = event.getSource();
        Color color = color(source);
        boolean mentalTarget = victim instanceof Player player
                && player.getCapability(MentalValueProvider.MENTAL_VALUE).isPresent();
        boolean fullSanityCap = victim instanceof Player player && ArmyInBlackBaseArmor.hasFullSanityCap(player);
        boolean whiteRecovery = victim instanceof Player player && recoversWhite(player, source);

        // Preserve the existing sequential flat additions / multipliers. Do not silently
        // regroup independent equipment bonuses into one additive percentage bucket.
        EgoAttackChargeHandler.onLivingHurt(event);
        SmilingCorpseMountainEvent.onHurtPre(event);
        ForgeModEvent.applyOutgoingModifiers(event);
        NothingThereHealthBuffEvent.onLivingHurt(event);
        SheepskinSetHandler.onLivingHurt(event);
        ButterflyFuneralEvents.onLivingHurt(event);
        GeburahSuppressionRewardEvents.onLivingHurt(event);
        ForgeModEvent.applyIncomingModifiers(event);
        if (event.isCanceled()) return;
        QueenBeeSetHandler.onLivingHurt(event);
        SheepskinSetHandler.recordIncomingDamage(event);
        if (event.isCanceled()) return;
        // Entry was finite (or an explicitly normalized execution). An overflow
        // introduced by finite legacy equipment multipliers must saturate, not turn immune.
        if (event.getAmount() == Float.POSITIVE_INFINITY) event.setAmount(Float.MAX_VALUE);

        double resistance = resistance(victim, source, color);
        // Absorption and WHITE panic recovery are not incoming damage and consume no shield.
        if (Double.isFinite(resistance) && resistance > 0 && !whiteRecovery) {
            SmilingCorpseMountainEvent.absorbWhiteDamage(event);
            if (event.isCanceled()) return;
        }
        float incoming = victim instanceof ServerPlayer player
                ? CoreSuppressionDamageHooks.incomingDamageMultiplier(player, source) : 1;
        incoming *= SkadiEvent.incomingDamageMultiplier(victim, source);
        float paleExtra = 0;
        if (mentalTarget && color == Color.PALE && resistance > 0) {
            paleExtra = victim.getMaxHealth() * incoming * (0.05F + victim.getRandom().nextFloat() * 0.02F);
            if (source.getEntity() instanceof LivingEntity attacker)
                paleExtra *= BaseEgoWeapon.currentLeftClickDamageMultiplier(attacker);
        }
        boolean blackShield = victim instanceof Player player && SmilingCorpseMountainEvent.blocksBlackMental(player);
        Result result = DamageCalculation.resolve(new Input(color, event.getAmount(), resistance, mentalTarget,
                incoming, paleExtra, fullSanityCap ? victim.getMaxHealth() * .3F : Float.POSITIVE_INFINITY,
                whiteRecovery, blackShield));
        event.setAmount(result.healthDamage());

        // These intercept the health channel only, never misinterpret WHITE's input as HP loss.
        if (result.hasHealthDamage()) {
            ForgeModEvent.onNothingThereRevive(event);
            if (!event.isCanceled()) BlueStarSetEvent.onLivingHurt(event);
            if (event.isCanceled()) return;
            MustOpenPathMechanics.onArmorHurt(event);
        }
        applyMental(victim, source, result);
        if (result.healthRecovery() > 0) victim.heal(result.healthRecovery());
        if (victim instanceof ServerPlayer player
                && (result.healthDamage() > 0 || result.mentalDamage() > 0)) {
            MessageLoader.getLoader().sendToPlayer(player, new DamageBorderPacket(
                    color == Color.PALE ? "blue" : color == Color.OTHER ? "red" : color.name().toLowerCase(java.util.Locale.ROOT)));
        }
        // DOT's final-health adapter must capture the settled HP amount, not a mental/raw amount.
        DotHelper.onLivingHurt(event);
        // A zero RED resistance on an abnormality must still reach the ordinary
        // Hurt pipeline with amount=0 (not a blanket false/early immunity bypass).
        if (!result.hasHealthDamage() && (mentalTarget || result.healthRecovery() > 0))
            event.setCanceled(true);
    }

    private static void applyMental(LivingEntity victim, DamageSource source, Result result) {
        if (!(victim instanceof Player player)) return;
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            if (result.mentalRecovery() > 0) {
                mental.addMentalValue(result.mentalRecovery(), MentalValueEvent.ChangeType.ADD);
            } else if (result.mentalDamage() > 0) {
                float before = mental.getMentalValue();
                mental.reduceMentalValue(result.mentalDamage(), MentalValueEvent.ChangeType.DAMAGE);
                if (color(source) == Color.WHITE)
                    ForgeModEvent.recoverFragmentAfterDamage(player, source, before - mental.getMentalValue());
            } else return;
            if (player instanceof ServerPlayer serverPlayer)
                MessageLoader.getLoader().sendToPlayer(serverPlayer, new MentalValueSyncPacket(
                        mental.getMentalValue(), mental.getEffectiveMaxMentalValue(),
                        result.mentalRecovery() > 0 ? MentalValueEvent.ChangeType.ADD : MentalValueEvent.ChangeType.DAMAGE,
                        mental.getPanicType()));
        });
    }

    public static Color color(DamageSource source) {
        if (DamageHelper.isRedDamage(source)) return Color.RED;
        if (DamageHelper.isWhiteDamage(source)) return Color.WHITE;
        if (DamageHelper.isBlackDamage(source)) return Color.BLACK;
        if (DamageHelper.isBlueDamage(source)) return Color.PALE;
        return Color.OTHER;
    }

    /** Shared with DOT eligibility; no fallback display table is involved. */
    public static double resistance(LivingEntity victim, DamageSource source, Color color) {
        Attribute attribute = switch (color) {
            case RED -> ModAttributes.RED_DAMAGE_RESISTANCE.get();
            case WHITE -> ModAttributes.WHITE_DAMAGE_RESISTANCE.get();
            case BLACK -> ModAttributes.BLACK_DAMAGE_RESISTANCE.get();
            case PALE -> ModAttributes.BLUE_DAMAGE_RESISTANCE.get();
            case OTHER -> victim instanceof Player ? ModAttributes.RED_DAMAGE_RESISTANCE.get() : null;
        };
        var instance = attribute == null ? null : victim.getAttribute(attribute);
        double value = instance == null ? 1 : instance.getValue();
        if (!Double.isFinite(value)) value = 1;
        if (color == Color.RED) value = victim instanceof Player
                ? GeburahDamageTuning.effectivePlayerRedMultiplier(source.getEntity() instanceof EntityGeburah, value)
                : GeburahDamageTuning.effectiveRedMultiplier(source.getEntity() instanceof EntityGeburah,
                        victim instanceof AbstractAbnormality, value);
        return DotHelper.isIgnoringResistance(victim) && value > 0 ? 1 : value;
    }

    private static boolean recoversWhite(Player victim, DamageSource source) {
        Player attacker = FacilityCombatRules.resolvePlayerAttacker(source);
        return color(source) == Color.WHITE && MentalValueUtil.isPanic(victim)
                && attacker != null && !MentalValueUtil.isPanic(attacker);
    }
}
