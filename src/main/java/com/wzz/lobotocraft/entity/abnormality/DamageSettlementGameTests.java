package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.event.listener.SkadiBlessingManager;
import com.wzz.lobotocraft.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import static com.wzz.lobotocraft.entity.abnormality.OvertimeEquipmentGameTests.*;

/** Exercises the real hurt/event/capability path, not only arithmetic helpers. */
@GameTestHolder("lobotocraft_damage_settlement")
@PrefixGameTestTemplate(false)
public final class DamageSettlementGameTests {
    private static final String T = "workcloseregressiongametests.navigation_empty";
    private static final String NS = "lobotocraft_damage_settlement";

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void executionRemainsLethalWhenAnEquipmentMultiplierOverflows(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        try {
            equip(victim, Items.AIR, Items.AIR, Items.AIR, Items.AIR, ModItems.END_BIRD_CURIO.get());
            victim.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get()).setBaseValue(1);
            victim.hurt(victim.damageSources().fellOutOfWorld(), Float.POSITIVE_INFINITY);
            h.assertTrue(!victim.isAlive(), "execution overflow after curio multiplier became immunity");
            h.succeed();
        } finally { victim.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void dotRestoresSettledDamageEvenWhenVanillaAbsorptionReducesItToZero(GameTestHelper h) {
        var victim = spawn(h, EntityType.PIG, 5);
        var attacker = spawn(h, EntityType.ZOMBIE, 3);
        try {
            victim.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get()).setBaseValue(1);
            victim.setAbsorptionAmount(20);
            float health = victim.getHealth();
            DotHelper.dealDotDamage(victim, DamageHelper.getDamage(attacker, "red"), 5);
            near(h, victim.getHealth(), health - 5, "vanilla zero amount prevented DOT true-damage restoration");
            victim.setHealth(health); victim.setAbsorptionAmount(20);
            victim.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get()).setBaseValue(0);
            DotHelper.dealDotDamage(victim, DamageHelper.getDamage(attacker, "red"), 5);
            near(h, victim.getHealth(), health, "DOT restoration bypassed zero color resistance");
            h.succeed();
        } finally { victim.discard(); attacker.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void existingInfiniteExecutionIsStillLethalButOrdinaryNanCannotPoisonHealth(GameTestHelper h) {
        var victim = spawn(h, EntityType.PIG, 5);
        var ordinary = spawn(h, EntityType.PIG, 7);
        var attacker = spawn(h, EntityType.ZOMBIE, 3);
        try {
            float health = ordinary.getHealth();
            ordinary.hurt(DamageHelper.getDamage(attacker, "red"), Float.NaN);
            near(h, ordinary.getHealth(), health, "ordinary NaN entered health");
            victim.hurt(victim.damageSources().fellOutOfWorld(), Float.POSITIVE_INFINITY);
            h.assertTrue(!victim.isAlive(), "existing LargeBird-style infinite execution was filtered out");
            h.succeed();
        } finally { victim.discard(); ordinary.discard(); attacker.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void skadiReductionAppliesOnceToWhiteBlackAndRed(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        var source = spawn(h, EntityType.ZOMBIE, 3);
        var skadi = spawn(h, ModEntities.skadi_corrupted.get(), 8);
        try {
            skadi.setQliphothCounter(2);
            victim.addEffect(new MobEffectInstance(ModMobEffects.WISH_WITHOUT_LIGHT.get(), 200));
            SkadiBlessingManager.bind(victim, skadi);
            h.assertTrue(SkadiBlessingManager.isValid(victim), "blessing fixture was invalid");
            victim.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get()).setBaseValue(1);
            victim.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get()).setBaseValue(1);
            victim.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get()).setBaseValue(1);
            for (String color : new String[]{"white", "black", "red"}) {
                victim.setHealth(victim.getMaxHealth()); MentalValueUtil.setMentalValue(victim, 80);
                EntityUtil.clearHurtTime(victim);
                float health = victim.getHealth();
                victim.hurt(DamageHelper.getDamage(source, color), 10);
                near(h, MentalValueUtil.getMentalValue(victim), color.equals("red") ? 80 : 74,
                        color + " blessing missed mental channel");
                near(h, victim.getHealth(), health - (color.equals("white") ? 0 : 6),
                        color + " blessing health multiplier applied twice");
            }
            h.succeed();
        } finally { SkadiBlessingManager.clear(victim); victim.discard(); source.discard(); skadi.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void nonPlayerResistanceAndAbsorptionUseTheSameCore(GameTestHelper h) {
        var victim = spawn(h, EntityType.PIG, 5);
        var source = spawn(h, EntityType.ZOMBIE, 3);
        try {
            victim.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get()).setBaseValue(.5);
            float health = victim.getHealth();
            victim.hurt(DamageHelper.getDamage(source, "white"), 4);
            near(h, victim.getHealth(), health - 2, "non-player WHITE must remain health damage");
            EntityUtil.clearHurtTime(victim);
            victim.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get()).setBaseValue(-.5);
            victim.hurt(DamageHelper.getDamage(source, "white"), 4);
            near(h, victim.getHealth(), health, "non-player negative WHITE was negative damage, not recovery");
            h.succeed();
        } finally { victim.discard(); source.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void fourColorsUseTheSameResistanceOnRealEmployee(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        var source = spawn(h, EntityType.ZOMBIE, 3);
        try {
            victim.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get()).setBaseValue(.5);
            victim.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get()).setBaseValue(.5);
            victim.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get()).setBaseValue(.5);
            victim.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get()).setBaseValue(.5);
            for (String color : new String[]{"red", "white", "black", "pale"}) {
                victim.setHealth(victim.getMaxHealth()); MentalValueUtil.setMentalValue(victim, 80);
                EntityUtil.clearHurtTime(victim);
                float before = victim.getHealth();
                victim.hurt(DamageHelper.getDamage(source, color), 4);
                near(h, MentalValueUtil.getMentalValue(victim), color.equals("white") || color.equals("black") ? 78 : 80,
                        color + " mental channel");
                float lost = before - victim.getHealth();
                if (color.equals("pale")) h.assertTrue(lost >= 2 + victim.getMaxHealth() * .05F - .001F
                        && lost <= 2 + victim.getMaxHealth() * .07F + .001F, "PALE lost base/percentage semantics");
                else near(h, lost, color.equals("white") ? 0 : 2, color + " health channel");
            }
            h.succeed();
        } finally { victim.discard(); source.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void whitePanicRescueRestoresOnceWithoutHealthDamage(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        var attacker = player(h, h.getLevel(), 3);
        boolean oldPvp = h.getLevel().getServer().isPvpAllowed();
        h.getLevel().getServer().setPvpAllowed(true);
        try {
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.FRAGMENT_OF_THE_UNIVERSE_WEAPON.get()));
            MentalValueUtil.setMentalValue(victim, 0); MentalValueUtil.setMentalValue(attacker, 50);
            victim.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get()).setBaseValue(.5);
            float health = victim.getHealth();
            victim.hurt(DamageHelper.getDamage(attacker, "white"), 10);
            near(h, MentalValueUtil.getMentalValue(victim), 5, "WHITE rescue duplicated or skipped resistance");
            near(h, victim.getHealth(), health, "WHITE rescue consumed HP");
            h.succeed();
        } finally {
            h.getLevel().getServer().setPvpAllowed(oldPvp);
            victim.discard(); attacker.discard();
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void fullSanityCapUsesPreHitSnapshotForBothBlackChannels(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        var source = spawn(h, EntityType.ZOMBIE, 3);
        try {
            equip(victim, ModItems.ARMY_IN_BLACK_CHESTPLATE.get(), ModItems.ARMY_IN_BLACK_LEGGINGS.get(),
                    ModItems.ARMY_IN_BLACK_BOOTS.get(), ModItems.ARMY_IN_BLACK_WEAPON.get(), ModItems.ARMY_IN_BLACK_CURIO.get());
            victim.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get()).setBaseValue(1);
            float mental = MentalValueUtil.getEffectiveMaxMentalValue(victim);
            MentalValueUtil.setMentalValue(victim, mental);
            float health = victim.getHealth(), cap = victim.getMaxHealth() * .3F;
            victim.hurt(DamageHelper.getDamage(source, "black"), victim.getMaxHealth());
            near(h, MentalValueUtil.getMentalValue(victim), mental - cap, "BLACK cap recomputed after mental loss");
            h.assertTrue(health - victim.getHealth() <= cap + .001F, "BLACK HP cap exceeded");
            h.succeed();
        } finally { victim.discard(); source.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void cancellationBeforeSettlementCannotConsumeBlackMental(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        var source = spawn(h, EntityType.ZOMBIE, 3);
        Consumer<LivingHurtEvent> cancel = e -> {
            if (e.getEntity() == victim) e.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingHurtEvent.class, cancel);
        try {
            MentalValueUtil.setMentalValue(victim, 50);
            float health = victim.getHealth();
            victim.hurt(DamageHelper.getDamage(source, "black"), 10);
            near(h, MentalValueUtil.getMentalValue(victim), 50, "canceled BLACK consumed mental");
            near(h, victim.getHealth(), health, "canceled BLACK consumed health");
            h.succeed();
        } finally { MinecraftForge.EVENT_BUS.unregister(cancel); victim.discard(); source.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void lateModifierMustReachBothBlackChannelsExactlyOnce(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        var source = spawn(h, EntityType.ZOMBIE, 3);
        Consumer<LivingHurtEvent> modify = e -> {
            if (e.getEntity() == victim) e.setAmount(e.getAmount() * 2);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingHurtEvent.class, modify);
        try {
            MentalValueUtil.setMentalValue(victim, 50);
            victim.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get()).setBaseValue(.5);
            float health = victim.getHealth();
            victim.hurt(DamageHelper.getDamage(source, "black"), 4);
            near(h, MentalValueUtil.getMentalValue(victim), 46, "BLACK mental missed or repeated modifier");
            near(h, victim.getHealth(), health - 4, "BLACK health missed or repeated resistance");
            h.succeed();
        } finally { MinecraftForge.EVENT_BUS.unregister(modify); victim.discard(); source.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void negativeWhiteResistanceRecoversMentalOnlyAtItsMagnitude(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        var source = spawn(h, EntityType.ZOMBIE, 3);
        try {
            MentalValueUtil.setMentalValue(victim, 20);
            victim.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get()).setBaseValue(-.5);
            victim.setHealth(victim.getMaxHealth() / 2);
            float health = victim.getHealth();
            victim.hurt(DamageHelper.getDamage(source, "white"), 10);
            near(h, victim.getHealth(), health, "WHITE absorption healed health");
            near(h, MentalValueUtil.getMentalValue(victim), 25, "WHITE absorption magnitude/channel wrong");
            h.succeed();
        } finally { victim.discard(); source.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30)
    public static void blackShieldNeverTransientlyConsumesMentalOrTriggersPanic(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 5);
        var source = spawn(h, EntityType.ZOMBIE, 3);
        AtomicInteger damageChanges = new AtomicInteger();
        Consumer<MentalValueEvent.Post> observe = e -> {
            if (e.getEntity() == victim && e.getNewValue() < e.getOriginalValue()) damageChanges.incrementAndGet();
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MentalValueEvent.Post.class, observe);
        try {
            MentalValueUtil.setMentalValue(victim, 5);
            victim.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get()).setBaseValue(1);
            victim.getPersistentData().putLong("smiling_shield_until", victim.level().getGameTime() + 100);
            victim.getPersistentData().putFloat("smiling_shield_value", 20);
            damageChanges.set(0); // Do not count fixture initialization (max SP -> 5).
            float health = victim.getHealth();
            victim.hurt(DamageHelper.getDamage(source, "black"), 10);
            near(h, MentalValueUtil.getMentalValue(victim), 5, "BLACK shield did not protect mental");
            h.assertTrue(damageChanges.get() == 0, "BLACK shield briefly consumed mental then restored it");
            near(h, victim.getHealth(), health - 10, "BLACK shield incorrectly blocked health");
            h.succeed();
        } finally { MinecraftForge.EVENT_BUS.unregister(observe); victim.discard(); source.discard(); }
    }

    private static void near(GameTestHelper h, float actual, float expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .001F, message + ": " + actual + " != " + expected);
    }
}
