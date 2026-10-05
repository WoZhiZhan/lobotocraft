package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.event.definition.work.WorkCompleteEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.item.ego.helper.HelperWeapon;
import com.wzz.lobotocraft.item.ego.snowqueen.SnowQueenWeapon;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.lang.reflect.Field;
import java.util.UUID;

/** Actual registered equipment, Curios, damage events and suppression transactions. */
@GameTestHolder("lobotocraft_overtime")
@PrefixGameTestTemplate(false)
public final class OvertimeEquipmentGameTests {
    private static final String TEMPLATE = "workcloseregressiongametests.navigation_empty";
    private static final String TEMPLATE_NS = "lobotocraft_overtime";

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void thirdKissLastsSixSecondsNotAccumulatedSlowDuration(GameTestHelper h) {
        var victim = spawn(h, ModEntities.fragment_of_the_universe.get(), 6);
        try {
            var weapon = (SnowQueenWeapon) ModItems.SNOWQUEEN_WEAPON.get();
            weapon.applyKissEffect(victim);
            ModMobEffects.KISS.get().applyEffectTick(victim, 0);
            h.assertFalse(victim.getPersistentData().getBoolean("isFreezeKiss"), "first stack freezes");
            weapon.applyKissEffect(victim);
            ModMobEffects.KISS.get().applyEffectTick(victim, 1);
            h.assertFalse(victim.getPersistentData().getBoolean("isFreezeKiss"), "second stack freezes");
            weapon.applyKissEffect(victim);
            var effect = victim.getEffect(ModMobEffects.KISS.get());
            h.assertTrue(effect != null && effect.getAmplifier() == 2 && effect.getDuration() == 120,
                    "third stack is not exactly six seconds: " + effect);
            h.succeed();
        } finally { victim.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void realFrozenSuppressionReturnsAndCanEscapeAgain(GameTestHelper h) {
        var victim = spawn(h, ModEntities.fragment_of_the_universe.get(), 6);
        var attacker = player(h, h.getLevel(), 3);
        try {
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SNOWQUEEN_WEAPON.get()));
            victim.setNoAi(false);
            victim.triggerEscape();
            var weapon = (SnowQueenWeapon) ModItems.SNOWQUEEN_WEAPON.get();
            for (int i = 0; i < 3; i++) weapon.applyKissEffect(victim);
            ModMobEffects.KISS.get().applyEffectTick(victim, 2);
            victim.setHealth(1);
            h.assertTrue(victim.hurt(DamageHelper.getDamage(attacker, "white"), 1000), "fatal hit rejected");
            h.assertTrue(!victim.hasEscape() && victim.isAlive() && !victim.isRemoved(),
                    "frozen suppression does not return reusable entity");
            h.assertFalse(victim.hasEffect(ModMobEffects.KISS.get()), "kiss survived suppression");
            h.assertFalse(victim.getPersistentData().getBoolean("isFreezeKiss"), "freeze marker survived");
            victim.triggerEscape();
            h.assertTrue(victim.hasEscape() && !victim.isNoAi(), "return left permanent AI lock");
            h.succeed();
        } finally { victim.discard(); attacker.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void largeBirdActualCharmRespectsRegisteredSetAndMissingGift(GameTestHelper h) {
        var bird = spawn(h, ModEntities.large_bird.get(), 6);
        var wearer = player(h, h.getLevel(), 3);
        equip(wearer, ModItems.LARGEBIRD_CHESTPLATE.get(), ModItems.LARGEBIRD_LEGGINGS.get(),
                ModItems.LARGEBIRD_BOOTS.get(), ModItems.LARGEBIRD_WEAPON.get(), ModItems.LARGEBIRD_CURIO.get());
        try {
            bird.setEscape(true);
            bird.tickCount = 500;
            bird.tick();
            h.assertFalse(wearer.getPersistentData().getBoolean("isLargeBirdCharm"),
                    "registered largebird full set is charmed");
            clearCurios(wearer);
            bird.tickCount = 500;
            bird.tick();
            h.assertTrue(wearer.getPersistentData().getBoolean("isLargeBirdCharm"),
                    "missing gift positive control was not charmed");
            equip(wearer, ModItems.LARGEBIRD_CHESTPLATE.get(), ModItems.LARGEBIRD_LEGGINGS.get(),
                    ModItems.LARGEBIRD_BOOTS.get(), ModItems.LARGEBIRD_WEAPON.get(), ModItems.LARGEBIRD_CURIO.get());
            h.assertFalse(bird.doHurtTarget(wearer), "newly immune wearer entered execution");
            bird.tickCount = 20; bird.tick();
            h.assertFalse(wearer.getPersistentData().getBoolean("isLargeBirdCharm"),
                    "gaining immunity retained an active charm lock");
            h.succeed();
        } finally { EntityLargeBird.clearLargeBirdCharm(wearer); bird.discard(); wearer.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void ppodaeRestoresForInstinctNotInsightOrInterruptedWork(GameTestHelper h) {
        var dog = spawn(h, ModEntities.ppodae.get(), 6);
        var worker = player(h, h.getLevel(), 3);
        equip(worker, ModItems.PPODAE_CHESTPLATE.get(), ModItems.PPODAE_LEGGINGS.get(),
                ModItems.PPODAE_BOOTS.get(), ModItems.PPODAE_WEAPON.get(), ModItems.PPODAE_CURIO.get());
        try {
            worker.setHealth(2);
            MinecraftForge.EVENT_BUS.post(new WorkCompleteEvent(worker, dog, WorkType.INSTINCT,
                    WorkResult.NORMAL, 1, null, false));
            near(h, worker.getHealth(), 2 + worker.getMaxHealth() * .1F, "instinct recovery missing");
            float health = worker.getHealth();
            MinecraftForge.EVENT_BUS.post(new WorkCompleteEvent(worker, dog, WorkType.INSIGHT,
                    WorkResult.NORMAL, 1, null, false));
            MinecraftForge.EVENT_BUS.post(new WorkCompleteEvent(worker, dog, WorkType.INSTINCT,
                    WorkResult.NORMAL, 1, null, true));
            near(h, worker.getHealth(), health, "wrong work or interrupted work healed");
            h.succeed();
        } finally { dog.discard(); worker.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void fragmentRestoresAttackerAndSameDimensionButNotOtherDimensionOrPanic(GameTestHelper h) {
        var victim = spawn(h, EntityType.ZOMBIE, 6);
        var attacker = player(h, h.getLevel(), 3);
        var colleague = player(h, h.getLevel(), 11);
        var otherLevel = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var remote = player(h, otherLevel, 12);
        var panicking = player(h, h.getLevel(), 12);
        equip(attacker, ModItems.FRAGMENT_OF_THE_UNIVERSE_CHESTPLATE.get(),
                ModItems.FRAGMENT_OF_THE_UNIVERSE_LEGGINGS.get(), ModItems.FRAGMENT_OF_THE_UNIVERSE_BOOTS.get(),
                ModItems.FRAGMENT_OF_THE_UNIVERSE_WEAPON.get(), ModItems.FRAGMENT_OF_THE_UNIVERSE_CURIO.get());
        try {
            for (var p : new ServerPlayer[]{attacker, colleague, remote}) MentalValueUtil.setMentalValue(p, 10);
            MentalValueUtil.setMentalValue(panicking, 0);
            h.assertTrue(MentalValueUtil.isPanic(panicking), "panic fixture is not panicking");
            victim.hurt(DamageHelper.getDamage(attacker, "red"), 1);
            near(h, MentalValueUtil.getMentalValue(attacker), 13, "attacker excluded");
            near(h, MentalValueUtil.getMentalValue(colleague), 13, "same-dimension player excluded");
            near(h, MentalValueUtil.getMentalValue(remote), 10, "other dimension restored");
            near(h, MentalValueUtil.getMentalValue(panicking), 0, "panicking player restored");
            float before = MentalValueUtil.getMentalValue(attacker);
            victim.setInvulnerable(true);
            victim.hurt(DamageHelper.getDamage(attacker, "red"), 2);
            near(h, MentalValueUtil.getMentalValue(attacker), before, "rejected hit restored sanity");
            h.succeed();
        } finally {
            victim.discard(); attacker.discard(); colleague.discard(); remote.discard(); panicking.discard();
        }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void fragmentRealWeaponRestoresEveryoneAndDoesNotProcOnRescue(GameTestHelper h) {
        var victim = spawn(h, EntityType.ZOMBIE, 6);
        var attacker = player(h, h.getLevel(), 3);
        var colleague = player(h, h.getLevel(), 11);
        equip(attacker, ModItems.FRAGMENT_OF_THE_UNIVERSE_CHESTPLATE.get(),
                ModItems.FRAGMENT_OF_THE_UNIVERSE_LEGGINGS.get(), ModItems.FRAGMENT_OF_THE_UNIVERSE_BOOTS.get(),
                ModItems.FRAGMENT_OF_THE_UNIVERSE_WEAPON.get(), ModItems.FRAGMENT_OF_THE_UNIVERSE_CURIO.get());
        var weapon = (com.wzz.lobotocraft.item.ego.fragment_of_the_universe.FragmentUniverseWeapon)
                ModItems.FRAGMENT_OF_THE_UNIVERSE_WEAPON.get();
        try {
            MentalValueUtil.setMentalValue(attacker, 10);
            MentalValueUtil.setMentalValue(colleague, 10);
            float before = victim.getHealth();
            weapon.onLeftClickEntity(attacker.getMainHandItem(), attacker, victim);
            h.assertTrue(victim.getHealth() < before, "actual fragment weapon did not hurt");
            // Weapon's existing personal +5 is separate from the set's dimension-wide +3.
            near(h, MentalValueUtil.getMentalValue(attacker), 18, "actual weapon excluded wearer from set proc");
            near(h, MentalValueUtil.getMentalValue(colleague), 13, "actual weapon did not restore colleague");
            MentalValueUtil.setMentalValue(colleague, 0);
            float mental = MentalValueUtil.getMentalValue(attacker);
            weapon.onLeftClickEntity(attacker.getMainHandItem(), attacker, colleague);
            near(h, MentalValueUtil.getMentalValue(attacker), mental + 5, "rescue was treated as dealt damage");
            h.succeed();
        } finally { victim.discard(); attacker.discard(); colleague.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void fragmentMentalOnlyDamageAlsoProcsButRescueDoesNot(GameTestHelper h) {
        var attacker = player(h, h.getLevel(), 3);
        var victim = player(h, h.getLevel(), 6);
        equip(attacker, ModItems.FRAGMENT_OF_THE_UNIVERSE_CHESTPLATE.get(),
                ModItems.FRAGMENT_OF_THE_UNIVERSE_LEGGINGS.get(), ModItems.FRAGMENT_OF_THE_UNIVERSE_BOOTS.get(),
                ModItems.FRAGMENT_OF_THE_UNIVERSE_WEAPON.get(), ModItems.FRAGMENT_OF_THE_UNIVERSE_CURIO.get());
        boolean previousPvp = h.getLevel().getServer().isPvpAllowed();
        h.getLevel().getServer().setPvpAllowed(true);
        try {
            // Panicking attacker bypasses normal employee friendly-fire protection, but
            // must still be excluded from the dimension-wide recovery recipient set.
            MentalValueUtil.setMentalValue(attacker, 0);
            MentalValueUtil.setMentalValue(victim, 50);
            h.assertTrue(MentalValueUtil.isPanic(attacker), "hostile attacker fixture did not enter panic");
            h.assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(victim,
                    DamageHelper.getDamage(attacker, "white")), "hostile attack fixture is blocked by friendly-fire");
            float health = victim.getHealth();
            victim.hurt(DamageHelper.getDamage(attacker, "white"), 10);
            near(h, victim.getHealth(), health, "WHITE leaked into health damage");
            near(h, MentalValueUtil.getMentalValue(victim), 43,
                    "valid mental damage was omitted from set recovery");
            MentalValueUtil.setMentalValue(attacker, 10);
            MentalValueUtil.setMentalValue(victim, 0);
            victim.hurt(DamageHelper.getDamage(attacker, "white"), 10);
            near(h, MentalValueUtil.getMentalValue(attacker), 10, "WHITE rescue triggered dealt-damage recovery");
            h.succeed();
        } finally {
            h.getLevel().getServer().setPvpAllowed(previousPvp);
            attacker.discard(); victim.discard();
        }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 60)
    public static void helperSixHitsOnTwoTargetsRestoreOncePerSkill(GameTestHelper h) {
        var attacker = player(h, h.getLevel(), 3);
        var a = spawn(h, EntityType.ZOMBIE, 5);
        var b = spawn(h, EntityType.ZOMBIE, 6);
        equip(attacker, ModItems.HELPER_CHESTPLATE.get(), ModItems.HELPER_LEGGINGS.get(),
                ModItems.HELPER_BOOTS.get(), ModItems.HELPER_WEAPON.get(), ModItems.HELPER_CURIO.get());
        attacker.setYRot(-90);
        attacker.setXRot(0);
        attacker.setHealth(2);
        a.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); a.setHealth(200);
        b.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); b.setHealth(200);
        ((HelperWeapon) ModItems.HELPER_WEAPON.get()).use(h.getLevel(), attacker, InteractionHand.MAIN_HAND);
        h.runAfterDelay(25, () -> {
            try {
                h.assertTrue(a.getHealth() < 190 && b.getHealth() < 190, "multi-target multi-hit fixture missed");
                near(h, attacker.getHealth(), 8, "one skill must restore six only once");
                h.succeed();
            } finally { TimerEntry.shutdown(a); TimerEntry.shutdown(b);
                a.discard(); b.discard(); attacker.discard(); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 60)
    public static void helperRejectedSkillRestoresNothing(GameTestHelper h) {
        var attacker = player(h, h.getLevel(), 3);
        var victim = spawn(h, EntityType.ZOMBIE, 5);
        equip(attacker, ModItems.HELPER_CHESTPLATE.get(), ModItems.HELPER_LEGGINGS.get(),
                ModItems.HELPER_BOOTS.get(), ModItems.HELPER_WEAPON.get(), ModItems.HELPER_CURIO.get());
        attacker.setYRot(-90); attacker.setHealth(2); victim.setInvulnerable(true);
        ((HelperWeapon) ModItems.HELPER_WEAPON.get()).use(h.getLevel(), attacker, InteractionHand.MAIN_HAND);
        h.runAfterDelay(25, () -> {
            try { near(h, attacker.getHealth(), 2, "failed hits healed"); h.succeed(); }
            finally { TimerEntry.shutdown(victim); victim.discard(); attacker.discard(); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 60)
    public static void helperFatalSuppressionAlsoCountsAsOneSuccessfulHit(GameTestHelper h) {
        var attacker = player(h, h.getLevel(), 3);
        var victim = spawn(h, ModEntities.fragment_of_the_universe.get(), 5);
        equip(attacker, ModItems.HELPER_CHESTPLATE.get(), ModItems.HELPER_LEGGINGS.get(),
                ModItems.HELPER_BOOTS.get(), ModItems.HELPER_WEAPON.get(), ModItems.HELPER_CURIO.get());
        victim.triggerEscape(); victim.setHealth(1);
        attacker.setYRot(-90); attacker.setHealth(2);
        ((HelperWeapon) ModItems.HELPER_WEAPON.get()).use(h.getLevel(), attacker, InteractionHand.MAIN_HAND);
        h.runAfterDelay(25, () -> {
            try {
                h.assertTrue(victim.isAlive() && !victim.hasEscape(), "skill did not successfully suppress target");
                near(h, attacker.getHealth(), 8, "fatal return obscured successful damage or restored twice");
                h.succeed();
            } finally { TimerEntry.shutdown(victim); victim.discard(); attacker.discard(); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void snowQueenActualTenPercentProcStacksInsteadOfRefreshingLevelOne(GameTestHelper h) {
        var victim = spawn(h, EntityType.ZOMBIE, 6);
        var attacker = player(h, h.getLevel(), 3);
        equip(attacker, ModItems.SNOWQUEEN_CHESTPLATE.get(), ModItems.SNOWQUEEN_LEGGINGS.get(),
                ModItems.SNOWQUEEN_BOOTS.get(), ModItems.SNOWQUEEN_WEAPON.get(), ModItems.SNOWQUEEN_CURIO.get());
        var weapon = (SnowQueenWeapon) ModItems.SNOWQUEEN_WEAPON.get();
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); victim.setHealth(200);
        try {
            long seed = 0;
            for (; seed < 100000; seed++) {
                attacker.getRandom().setSeed(seed);
                attacker.getRandom().nextInt(7);
                if (attacker.getRandom().nextFloat() < .1F) break;
            }
            h.assertTrue(seed < 100000, "no deterministic chance seed found");
            for (int stack = 0; stack < 3; stack++) {
                attacker.getRandom().setSeed(seed);
                EntityUtil.clearHurtTime(victim);
                weapon.onLeftClickEntity(attacker.getMainHandItem(), attacker, victim);
                var kiss = victim.getEffect(ModMobEffects.KISS.get());
                h.assertTrue(kiss != null && kiss.getAmplifier() == stack,
                        "actual successful chance proc failed to stack at " + stack);
            }
            h.assertTrue(victim.getEffect(ModMobEffects.KISS.get()).getDuration() == 120, "actual freeze exceeds six seconds");
            h.succeed();
        } finally { victim.discard(); attacker.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 140)
    public static void repeatedProcDoesNotExtendFreezeAndNaturalExpiryPreservesNoAi(GameTestHelper h) {
        var victim = spawn(h, EntityType.ZOMBIE, 6);
        var weapon = (SnowQueenWeapon) ModItems.SNOWQUEEN_WEAPON.get();
        for (int i = 0; i < 3; i++) weapon.applyKissEffect(victim);
        h.runAfterDelay(60, () -> {
            int before = victim.getEffect(ModMobEffects.KISS.get()).getDuration();
            weapon.applyKissEffect(victim);
            h.assertTrue(victim.getEffect(ModMobEffects.KISS.get()).getDuration() == before, "hit extended active freeze");
        });
        h.runAfterDelay(123, () -> {
            try {
                h.assertFalse(victim.hasEffect(ModMobEffects.KISS.get()), "freeze did not naturally expire");
                h.assertFalse(victim.getPersistentData().getBoolean("isFreezeKiss"), "expiry left freeze flag");
                h.assertTrue(victim.isNoAi(), "effect stole preexisting NoAI ownership");
                h.succeed();
            } finally { victim.discard(); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void sheepskinRealSuppressionHealsWithoutPreyAndDoesNotRepeatOnNormalReturn(GameTestHelper h) {
        var victim = spawn(h, ModEntities.fragment_of_the_universe.get(), 6);
        var attacker = player(h, h.getLevel(), 3);
        equip(attacker, ModItems.REDHAT_MERCENARY_CHESTPLATE.get(), ModItems.REDHAT_MERCENARY_LEGGINGS.get(),
                ModItems.REDHAT_MERCENARY_BOOTS.get(), ModItems.REDHAT_MERCENARY_WEAPON_KNIFE.get(),
                ModItems.REDHAT_MERCENARY_CURIO.get(), ModItems.BIG_BADWOLF_CURIO.get(), ModItems.SHEEPSKIN_CURIO.get());
        try {
            attacker.setHealth(2); MentalValueUtil.setMentalValue(attacker, 10);
            victim.triggerEscape(); victim.setHealth(1);
            victim.hurt(DamageHelper.getDamage(attacker, "red"), 1000);
            h.assertTrue(!victim.hasEscape() && victim.isAlive(), "real suppression failed");
            near(h, attacker.getHealth(), 2 + attacker.getMaxHealth() * .05F, "suppression recovery missing");
            near(h, MentalValueUtil.getMentalValue(attacker),
                    10 + MentalValueUtil.getEffectiveMaxMentalValue(attacker) * .05F, "suppression sanity missing");
            float health = attacker.getHealth();
            victim.triggerEscape(); victim.stopEscape();
            near(h, attacker.getHealth(), health, "nonlethal return reused previous kill");
            h.succeed();
        } finally { victim.discard(); attacker.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void sheepskinNormalDeathHealsWithoutPrey(GameTestHelper h) {
        var victim = spawn(h, EntityType.ZOMBIE, 6);
        var attacker = player(h, h.getLevel(), 3);
        equip(attacker, ModItems.REDHAT_MERCENARY_CHESTPLATE.get(), ModItems.REDHAT_MERCENARY_LEGGINGS.get(),
                ModItems.REDHAT_MERCENARY_BOOTS.get(), ModItems.REDHAT_MERCENARY_WEAPON_KNIFE.get(),
                ModItems.REDHAT_MERCENARY_CURIO.get(), ModItems.BIG_BADWOLF_CURIO.get(), ModItems.SHEEPSKIN_CURIO.get());
        try {
            attacker.setHealth(2); victim.setHealth(1);
            victim.hurt(DamageHelper.getDamage(attacker, "red"), 1000);
            near(h, attacker.getHealth(), 2 + attacker.getMaxHealth() * .05F, "normal non-prey kill did not heal");
            h.succeed();
        } finally { victim.discard(); attacker.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void pinkSetCapsFinalHealthLossNotPercentageDiscount(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 3);
        equip(victim, ModItems.ARMY_IN_BLACK_CHESTPLATE.get(), ModItems.ARMY_IN_BLACK_LEGGINGS.get(),
                ModItems.ARMY_IN_BLACK_BOOTS.get(), ModItems.ARMY_IN_BLACK_WEAPON.get(), ModItems.ARMY_IN_BLACK_CURIO.get());
        try {
            victim.setHealth(victim.getMaxHealth());
            MentalValueUtil.setMentalValue(victim, MentalValueUtil.getEffectiveMaxMentalValue(victim));
            victim.hurt(victim.damageSources().generic(), 1000);
            h.assertTrue(victim.isAlive(), "full sanity set did not prevent oversized hit");
            h.assertTrue(victim.getMaxHealth() - victim.getHealth() <= victim.getMaxHealth() * .3F + .001F,
                    "final damage exceeds 30% max health");
            EntityUtil.clearHurtTime(victim);
            victim.setHealth(victim.getMaxHealth()); MentalValueUtil.setMentalValue(victim, 10);
            victim.hurt(victim.damageSources().generic(), 3);
            h.assertTrue(victim.getHealth() < victim.getMaxHealth(), "nonfull sanity rejected ordinary hit");
            h.succeed();
        } finally { victim.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30)
    public static void pinkCapPreservesBlackHitEligibilityAndCapsPalePercentageAddition(GameTestHelper h) {
        var victim = player(h, h.getLevel(), 3);
        var attacker = spawn(h, EntityType.ZOMBIE, 6);
        equip(victim, ModItems.ARMY_IN_BLACK_CHESTPLATE.get(), ModItems.ARMY_IN_BLACK_LEGGINGS.get(),
                ModItems.ARMY_IN_BLACK_BOOTS.get(), ModItems.ARMY_IN_BLACK_WEAPON.get(), ModItems.ARMY_IN_BLACK_CURIO.get());
        try {
            float limit = victim.getMaxHealth() * .3F;
            for (String color : new String[]{"black", "blue", "white"}) {
                victim.setHealth(victim.getMaxHealth());
                MentalValueUtil.setMentalValue(victim, MentalValueUtil.getEffectiveMaxMentalValue(victim));
                float beforeMental = MentalValueUtil.getMentalValue(victim);
                EntityUtil.clearHurtTime(victim);
                victim.hurt(DamageHelper.getDamage(attacker, color), 1000);
                h.assertTrue(victim.isAlive() && victim.getMaxHealth() - victim.getHealth() <= limit + .001F,
                        "qualified " + color + " hit exceeded health cap after damage conversion");
                h.assertTrue(beforeMental - MentalValueUtil.getMentalValue(victim) <= limit + .001F,
                        "qualified " + color + " mental component exceeded cap");
            }
            h.succeed();
        } finally { attacker.discard(); victim.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 50, batch = "crimson_noon_death_fixture")
    public static void crimsonNoonDeathSpawnsAtDeathSiteNotContainment(GameTestHelper h) throws Exception {
        var level = h.getLevel().getServer().getLevel(com.wzz.lobotocraft.init.ModDimensions.LOBOTO_KEY);
        var previousGeneration = com.wzz.lobotocraft.world.data.GenerationBiggerData.get(level);
        var fixtureGeneration = com.wzz.lobotocraft.world.data.GenerationBiggerData.load(
                previousGeneration.save(new net.minecraft.nbt.CompoundTag()));
        var bounds = fixtureGeneration.getOrCreate(com.wzz.lobotocraft.world.structure.Structures.LOBOTO.name());
        bounds.generated = true; bounds.centerX = 225; bounds.centerZ = 42; bounds.currentRadius = 40;
        level.getServer().overworld().getDataStorage().set(
                com.wzz.lobotocraft.world.data.GenerationBiggerData.ID, fixtureGeneration);
        try {
        // addFreshEntity succeeds even before a remote chunk's entity section becomes
        // visible to spatial queries. Keep the fixture chunks tracked during this test.
        for (int cx = 13; cx <= 14; cx++) for (int cz = 2; cz <= 3; cz++)
            level.setChunkForced(cx, cz, true);
        for (int x = 214; x <= 241; x++) for (int z = 20; z <= 56; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 269, z), Blocks.STONE.defaultBlockState());
            for (int y = 270; y <= 275; y++)
                level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        var noon = ModEntities.crimson_noon.get().create(level);
        var contained = ModEntities.fragment_of_the_universe.get().create(level);
        noon.moveTo(220.5, 270, 52.5, 0, 0);
        contained.moveTo(239.5, 270, 52.5, 0, 0);
        noon.setNoAi(true); contained.setNoAi(true);
        level.addFreshEntity(noon); level.addFreshEntity(contained);
        h.assertTrue(com.wzz.lobotocraft.employee.FacilityAccessService.isPositionOpen(
                level.getServer(), level.dimension().location(), noon.blockPosition()), "death site is closed");
        h.assertTrue(com.wzz.lobotocraft.employee.FacilityAccessService.isPositionOpen(
                level.getServer(), level.dimension().location(), contained.blockPosition()), "containment is closed");
        var probe = ModEntities.bloody_small.get().create(level);
        h.assertTrue(com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.findBloodySmallSpawnPosition(
                level, probe, noon.blockPosition(), 6) != null, "dawn placement fixture has no usable position");
        float counter = contained.getQliphothCounter();
        var spawned = new java.util.ArrayList<com.wzz.lobotocraft.entity.ordeal.EntityBloodySmall>();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> recorder = event -> {
            if (event.getLevel() == level
                    && event.getEntity() instanceof com.wzz.lobotocraft.entity.ordeal.EntityBloodySmall clown
                    && clown.isCrimsonNoonSpawn()) spawned.add(clown);
        };
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false,
                net.minecraftforge.event.entity.EntityJoinLevelEvent.class, recorder);
        var ordeal = com.wzz.lobotocraft.world.data.OrdealData.get(level);
        // Actual ordeal bookkeeping counts addFreshEntity successes. A spatial query
        // of remotely loaded chunks is not an authoritative spawn-count assertion.
        ordeal.startCrimsonMidday(1);
        noon.setOrdealSpawn(true);
        try {
            noon.die(noon.damageSources().genericKill());
            for (int tick = 0; tick < 24; tick++) noon.tick();
            h.assertTrue(noon.isRemoved(), "24 death ticks did not complete noon death");
            h.assertTrue(spawned.size() == 3, "death did not create exactly three dawns");
            h.assertTrue(ordeal.decrementCrimsonMiddayRemaining() == 2,
                    "death bookkeeping did not count exactly three successful additions");
            for (var clown : spawned) {
                h.assertTrue(Math.abs(clown.getX() - noon.getX()) <= 6
                                && Math.abs(clown.getZ() - noon.getZ()) <= 6
                                && Math.abs(clown.getY() - noon.getY()) < 1,
                        "death spawned dawn at containment rather than death site: " + clown.position());
                net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
                clown.addAdditionalSaveData(saved);
                h.assertFalse(saved.hasUUID("TrackedAbnormality"), "death preassigned a containment target");
            }
            near(h, contained.getQliphothCounter(), counter, "death immediately decreased containment counter");
            h.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(recorder);
            spawned.forEach(Entity::discard);
            ordeal.finishCrimsonMidday();
            noon.discard(); contained.discard();
        }
        } finally {
            level.getServer().overworld().getDataStorage().set(
                    com.wzz.lobotocraft.world.data.GenerationBiggerData.ID, previousGeneration);
            for (int cx = 13; cx <= 14; cx++) for (int cz = 2; cz <= 3; cz++)
                level.setChunkForced(cx, cz, false);
        }
    }

    static <T extends LivingEntity> T spawn(GameTestHelper h, EntityType<T> type, int x) {
        for (int xx = 0; xx < 17; xx++) for (int z = 0; z < 12; z++)
            h.setBlock(new BlockPos(xx, 0, z), Blocks.STONE);
        var e = type.create(h.getLevel());
        if (e == null) throw new IllegalStateException("missing entity");
        var pos = h.absolutePos(new BlockPos(x, 1, 5));
        e.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        if (e instanceof Mob mob) mob.setNoAi(true);
        h.getLevel().addFreshEntity(e);
        return e;
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30, batch = "thorn_defense_audit")
    public static void auditThornSetStillTakesRealButterflyRangedSkill(GameTestHelper h) throws Exception {
        var funeral = spawn(h, ModEntities.butterfly_funeral.get(), 3);
        var wearer = player(h, h.getLevel(), 9);
        equip(wearer, ModItems.THORN_BUS_CHESTPLATE.get(), ModItems.THORN_BUS_LEGGINGS.get(),
                ModItems.THORN_BUS_BOOTS.get(), ModItems.THORN_BUS_WEAPON.get(), ModItems.THORN_BUS_CURIO.get());
        try {
            funeral.setEscape(true);
            set(funeral, "skillPhase", 2);
            set(funeral, "skillTimer", 260);
            set(funeral, "skillDirection", new net.minecraft.world.phys.Vec3(1, 0, 0));
            float mental = MentalValueUtil.getMentalValue(wearer);
            var hit = EntityButterflyFuneral.class.getDeclaredMethod("damageButterflyCone", ServerLevel.class);
            hit.setAccessible(true); hit.invoke(funeral, h.getLevel());
            near(h, MentalValueUtil.getMentalValue(wearer), mental, "thorn set still takes nonprojectile ranged WHITE");
            h.succeed();
        } finally { funeral.discard(); wearer.discard(); }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30, batch = "wolf_intent_audit")
    public static void auditLowHealthWolfStillBlockedByEmployeeFriendlyFire(GameTestHelper h) {
        var attacker = player(h, h.getLevel(), 3);
        var colleague = player(h, h.getLevel(), 5);
        var weapon = (com.wzz.lobotocraft.item.ego.bigbadwolf.BigBadwolfWeapon) ModItems.BIG_BADWOLF_WEAPON.get();
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        attacker.setHealth(attacker.getMaxHealth() * .4F); attacker.setYRot(-90);
        boolean previousPvp = h.getLevel().getServer().isPvpAllowed();
        h.getLevel().getServer().setPvpAllowed(true);
        try {
            h.assertTrue(FacilityCombatRules.blocksEmployeeFriendlyFire(colleague,
                    DamageHelper.getDamage(attacker, "red")), "wolf audit does not reach shared friendly-fire policy");
            h.assertTrue(EntityUtil.findAllLivingEntitiesInLookDirection(attacker, 4, 3).contains(colleague),
                    "colleague was not in actual weapon geometry");
            float before = colleague.getHealth();
            weapon.onEntitySwing(attacker.getMainHandItem(), attacker);
            h.assertTrue(colleague.getHealth() < before, "low-health wolf swing blocked by shared friendly-fire gate");
            h.succeed();
        } finally {
            h.getLevel().getServer().setPvpAllowed(previousPvp);
            TimerEntry.shutdown(colleague); attacker.discard(); colleague.discard();
        }
    }

    @GameTest(template = TEMPLATE, templateNamespace = TEMPLATE_NS, timeoutTicks = 30, batch = "hood_intent_audit")
    public static void auditLowHealthRedHoodStillBlockedByEmployeeFriendlyFire(GameTestHelper h) throws Exception {
        var attacker = player(h, h.getLevel(), 3);
        var colleague = player(h, h.getLevel(), 5);
        var weapon = ModItems.REDHAT_MERCENARY_WEAPON_KNIFE.get();
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        attacker.setHealth(attacker.getMaxHealth() * .4F); attacker.setYRot(-90);
        boolean previousPvp = h.getLevel().getServer().isPvpAllowed();
        h.getLevel().getServer().setPvpAllowed(true);
        try {
            h.assertTrue(FacilityCombatRules.blocksEmployeeFriendlyFire(colleague,
                    DamageHelper.getDamage(attacker, "red")), "knife audit does not reach shared friendly-fire policy");
            h.assertTrue(EntityUtil.findAllLivingEntitiesInLookDirection(attacker, 3, 2).contains(colleague),
                    "colleague was not in actual knife geometry");
            float before = colleague.getHealth();
            var aoe = weapon.getClass().getDeclaredMethod("hurtEntitiesInFront",
                    net.minecraft.world.entity.player.Player.class, LivingEntity.class, float.class);
            aoe.setAccessible(true); aoe.invoke(weapon, attacker, null, 18F);
            h.assertTrue(colleague.getHealth() < before, "fury knife blocked by shared friendly-fire gate");
            h.succeed();
        } finally {
            h.getLevel().getServer().setPvpAllowed(previousPvp);
            attacker.discard(); colleague.discard();
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(object, value);
    }

    static EmployeeServerPlayer player(GameTestHelper h, ServerLevel level, int x) {
        var record = new EmployeeRecord(UUID.randomUUID(), "overtime_" + x, EmployeeDepartment.CONTROL);
        record.setStats(100, 100, 100, 100);
        var p = new EmployeeServerPlayer(level, new GameProfile(record.id(), record.name()), record);
        var pos = h.absolutePos(new BlockPos(x, 1, 5));
        p.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        p.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(s -> {
            s.setFortitude(100); s.setPrudence(100); s.setTemperance(100); s.setJustice(100);
        });
        level.addNewPlayer(p);
        try {
            Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true); f.setInt(p, 0);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        MentalValueUtil.setMentalValue(p, MentalValueUtil.getEffectiveMaxMentalValue(p));
        return p;
    }

    static void equip(ServerPlayer p, Item chest, Item legs, Item boots, Item weapon, Item... curios) {
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(boots));
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        p.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(d -> d.lockArmor());
        var handler = CuriosApi.getCuriosInventory(p).resolve().orElseThrow();
        int index = 0;
        for (var slot : handler.getCurios().values()) {
            for (int i = 0; i < slot.getStacks().getSlots() && index < curios.length; i++)
                slot.getStacks().setStackInSlot(i, new ItemStack(curios[index++]));
        }
        if (index != curios.length) throw new IllegalStateException("insufficient Curios slots");
    }

    private static void clearCurios(ServerPlayer p) {
        CuriosApi.getCuriosInventory(p).resolve().orElseThrow().getCurios().values().forEach(slot -> {
            for (int i = 0; i < slot.getStacks().getSlots(); i++) slot.getStacks().setStackInSlot(i, ItemStack.EMPTY);
        });
    }

    private static void near(GameTestHelper h, float actual, float expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .001F, message + ": actual=" + actual + ", expected=" + expected);
    }
}
