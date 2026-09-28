package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.event.ForgeModEvent;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.item.CaptureUnitItem;
import com.wzz.lobotocraft.item.ego.blue_star.BlueStarWeapon;
import com.wzz.lobotocraft.item.ego.red_shoes.RedShoesDamagePolicy;
import com.wzz.lobotocraft.item.ego.snowqueen.SnowQueenWeapon;
import com.wzz.lobotocraft.menu.EmployeeInventoryMenu;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import com.wzz.lobotocraft.util.ItemUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.AbnormalityWorkRules;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.EndBirdEggTracker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import top.theillusivec4.curios.api.CuriosApi;

/** Dedicated-server regression scenarios for the abnormality fixes reported together. */
@GameTestHolder(ModMain.MODID + "_recent_fixes")
public final class RecentAbnormalityFixGameTests {
    private static final String TEST_NAMESPACE = ModMain.MODID + "_recent_fixes";
    private static final String JUDGEMENT_TAG = "lobotocraft_red_shoes_judgement";
    private static final String JUDGEMENT_TIMER_TAG = "lobotocraft_red_shoes_judgement_timer";
    private static final String CHARMED_TAG = "lobotocraft_red_shoes_charmed";
    private static final String RED_SHOES_SOURCE_TAG = "lobotocraft_red_shoes_source";
    private static final String BLOODLUST_TAG = "lobotocraft_red_shoes_bloodlust";
    private static final String CHARM_HURT_COUNT_TAG = "lobotocraft_red_shoes_hurt_count";
    private static final String BLOODLUST_ATTACK_COOLDOWN_TAG =
            "lobotocraft_red_shoes_attack_cooldown";

    private RecentAbnormalityFixGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_big_bad_wolf_animation")
    public static void bigBadWolfAttackAnimationRecoversAfterTargetIsLost(GameTestHelper helper) {
        EntityBigBadWolf wolf = ModEntities.bigbadwolf.get().create(helper.getLevel());
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        if (wolf == null || target == null) {
            helper.fail("could not create big bad wolf attack fixtures");
            return;
        }

        wolf.beginBasicAttack(0, target);
        target.discard();
        for (int tick = 0; tick < EntityBigBadWolf.CLAW_ANIMATION_TICKS - 1; tick++) {
            wolf.tickBasicAttack();
        }
        helper.assertTrue("attack1".equals(wolf.getAnimation()),
                "claw animation ended before its declared duration");
        wolf.tickBasicAttack();
        helper.assertTrue("idle".equals(wolf.getAnimation()),
                "lost claw target left the wolf stuck in its attack animation");

        wolf.beginBasicAttack(1, target);
        for (int tick = 0; tick < EntityBigBadWolf.BITE_ANIMATION_TICKS; tick++) {
            wolf.tickBasicAttack();
        }
        helper.assertTrue("idle".equals(wolf.getAnimation()),
                "lost bite target left the wolf stuck in its attack animation");

        Zombie liveTarget = EntityType.ZOMBIE.create(helper.getLevel());
        if (liveTarget == null) {
            helper.fail("could not create live big bad wolf attack target");
            return;
        }
        float healthBeforeHit = liveTarget.getHealth();
        wolf.beginBasicAttack(0, liveTarget);
        for (int tick = 0; tick < 7; tick++) {
            wolf.tickBasicAttack();
        }
        helper.assertTrue(liveTarget.getHealth() == healthBeforeHit,
                "claw damage was applied before its configured hit frame");
        wolf.tickBasicAttack();
        helper.assertTrue(liveTarget.getHealth() < healthBeforeHit,
                "claw damage was not applied on its configured hit frame");
        helper.assertTrue("attack1".equals(wolf.getAnimation()),
                "claw animation stopped on the hit frame instead of completing");
        for (int tick = 8; tick < EntityBigBadWolf.CLAW_ANIMATION_TICKS; tick++) {
            wolf.tickBasicAttack();
        }
        helper.assertTrue("idle".equals(wolf.getAnimation()),
                "successful claw attack did not return to idle after the full animation");
        liveTarget.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_big_bad_wolf_animation")
    public static void bigBadWolfHowlAnimationRecoversWhenFogInterruptsIt(GameTestHelper helper) {
        EntityBigBadWolf startWolf = spawnEscapedBigBadWolf(helper, new BlockPos(2, 1, 2));
        startWolf.beginHowl(helper.getLevel());
        helper.assertTrue("skill1".equals(startWolf.getAnimation()),
                "wolf did not enter the howl startup animation");
        startWolf.enterFog();
        assertWolfHowlInterrupted(helper, startWolf, "howl startup");
        startWolf.discard();

        EntityBigBadWolf endWolf = spawnEscapedBigBadWolf(helper, new BlockPos(6, 1, 2));
        endWolf.beginHowl(helper.getLevel());
        for (int tick = 0; tick < EntityBigBadWolf.HOWL_START_ANIMATION_TICKS; tick++) {
            endWolf.tickHowl(helper.getLevel());
        }
        for (int tick = 0; tick < 60; tick++) {
            endWolf.tickHowl(helper.getLevel());
        }
        helper.assertTrue("skill2".equals(endWolf.getAnimation()),
                "wolf did not reach the howl ending animation");
        endWolf.enterFog();
        assertWolfHowlInterrupted(helper, endWolf, "howl ending");
        endWolf.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_big_bad_wolf_animation")
    public static void bigBadWolfHowlPhasesMatchAnimationDurations(GameTestHelper helper) {
        EntityBigBadWolf wolf = spawnEscapedBigBadWolf(helper, new BlockPos(2, 1, 2));
        wolf.beginHowl(helper.getLevel());

        for (int tick = 1; tick < EntityBigBadWolf.HOWL_START_ANIMATION_TICKS; tick++) {
            wolf.tickHowl(helper.getLevel());
        }
        helper.assertTrue("skill1".equals(wolf.getAnimation()),
                "howl startup ended before the animation duration");
        wolf.tickHowl(helper.getLevel());
        helper.assertTrue("skill12".equals(wolf.getAnimation()),
                "howl startup did not advance after the animation duration");

        for (int tick = 1; tick < 60; tick++) {
            wolf.tickHowl(helper.getLevel());
        }
        helper.assertTrue("skill12".equals(wolf.getAnimation()),
                "looping howl ended before its configured duration");
        wolf.tickHowl(helper.getLevel());
        helper.assertTrue("skill2".equals(wolf.getAnimation()),
                "looping howl did not advance to its ending animation");

        for (int tick = 1; tick < EntityBigBadWolf.HOWL_END_ANIMATION_TICKS; tick++) {
            wolf.tickHowl(helper.getLevel());
        }
        helper.assertTrue("skill2".equals(wolf.getAnimation()),
                "howl ending stopped before the animation duration");
        wolf.tickHowl(helper.getLevel());
        helper.assertTrue("idle".equals(wolf.getAnimation()),
                "howl ending did not return to idle after the animation duration");

        wolf.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_fragment")
    public static void fragmentCounterDropsWhenPanicCancelsWork(GameTestHelper helper) {
        EntityFragmentOfUniverse fragment = ModEntities.fragment_of_the_universe.get()
                .create(helper.getLevel());
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "fragment_worker", 20);
        if (fragment == null) {
            worker.kill();
            helper.fail("fragment of the universe could not be created");
            return;
        }
        moveTo(helper, fragment, new BlockPos(4, 1, 2));
        helper.getLevel().addFreshEntity(fragment);

        Map<UUID, WorkManager.WorkSession> activeWorks = activeWorks();
        activeWorks.put(worker.getUUID(),
                new WorkManager.WorkSession(worker, fragment, WorkType.INSTINCT, false));
        setPanicking(helper, worker);
        int counterBefore = fragment.getQliphothCounter();

        try {
            WorkManager.cancelWork(worker, "GameTest panic cancellation");
            helper.assertFalse(WorkManager.isPlayerWorking(worker),
                    "panic cancellation left the work session active");
            helper.assertTrue(fragment.getQliphothCounter() == counterBefore - 1,
                    "fragment qliphoth counter did not drop exactly once after panic cancellation");
            helper.succeed();
        } finally {
            activeWorks.remove(worker.getUUID());
            fragment.discard();
            worker.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_wingbeat")
    public static void wingbeatSessionSurvivesLongSameDimensionTeleport(GameTestHelper helper) {
        EntityWingBeat source = ModEntities.wingbeat.get().create(helper.getLevel());
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "wingbeat_worker", 20);
        if (source == null) {
            worker.kill();
            helper.fail("wingbeat could not be created");
            return;
        }
        moveTo(helper, source, new BlockPos(4, 1, 2));
        helper.getLevel().addFreshEntity(source);
        source.onGoodWork(worker);

        List<EntityWingBeat> orbiters = wingbeatsOwnedBy(helper, worker, true);
        helper.assertTrue(orbiters.size() == 3,
                "wingbeat feast did not spawn exactly three orbiters");
        worker.teleportTo(worker.getX() + 70.0D, worker.getY(), worker.getZ());

        source.tick();
        for (EntityWingBeat orbiter : orbiters) {
            if (!orbiter.isRemoved()) orbiter.tick();
        }

        try {
            helper.assertTrue(worker.getPersistentData().getBoolean(EntityWingBeat.ACTIVE_FLAG),
                    "same-dimension teleport ended the wingbeat session");
            for (EntityWingBeat orbiter : orbiters) {
                helper.assertTrue(orbiter.isAlive() && !orbiter.isRemoved(),
                        "same-dimension teleport discarded an orbiter");
                helper.assertTrue(orbiter.distanceToSqr(worker) <= 400.0D,
                        "orbiter did not catch up after a long teleport");
            }
            helper.succeed();
        } finally {
            EntityWingBeat.clearSession(worker);
            source.discard();
            worker.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_blue_star")
    public static void blueStarBeamRecoversPanicWithoutReapplyingIt(GameTestHelper helper) {
        EmployeeServerPlayer attacker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "blue_star_attacker", 100);
        EmployeeServerPlayer target = spawnEmployee(helper, new BlockPos(5, 1, 2),
                "blue_star_target", 20);
        setPanicking(helper, target);
        helper.assertTrue(MentalValueUtil.isPanic(target),
                "blue star target was not panicking before the attack");

        BlueStarWeapon weapon = (BlueStarWeapon) ModItems.BLUE_STAR_WEAPON.get();
        ItemStack stack = new ItemStack(weapon);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = weapon.useForAi(attacker, stack, InteractionHand.MAIN_HAND, target);

        try {
            helper.assertTrue(result == InteractionResult.SUCCESS,
                    "blue star AI beam did not execute");
            float mentalAfter = MentalValueUtil.getMentalValue(target);
            helper.assertTrue(mentalAfter > 0.0F,
                    "blue star beam left SP=" + mentalAfter
                            + ", panic=" + MentalValueUtil.isPanic(target)
                            + ", attackerHand=" + attacker.getMainHandItem());
            helper.assertFalse(MentalValueUtil.isPanic(target),
                    "blue star beam made the recovered target panic again");
            helper.succeed();
        } finally {
            attacker.kill();
            target.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 100, required = true, batch = "recent_blue_star")
    public static void blueStarLowTemperanceExecutionRestoresIdle(GameTestHelper helper) {
        EntityBlueStar blueStar = ModEntities.blue_star.get().create(helper.getLevel());
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(3, 1, 2),
                "blue_star_low_temperance", 20);
        if (blueStar == null) {
            worker.kill();
            helper.fail("blue star could not be created");
            return;
        }
        moveTo(helper, blueStar, new BlockPos(4, 1, 2));
        helper.getLevel().addFreshEntity(blueStar);

        helper.runAfterDelay(70, () -> {
            try {
                helper.assertFalse(worker.isAlive(),
                        "low-temperance employee survived Blue Star execution");
                helper.assertTrue("animation.blue_star.idle".equals(blueStar.getAnimation()),
                        "Blue Star did not restore idle animation after the execution");
                helper.succeed();
            } finally {
                blueStar.discard();
                if (worker.isAlive()) worker.kill();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_hood")
    public static void redHoodRequiresEquippedBigBadWolfAccessory(GameTestHelper helper) {
        EntityRedHoodMercenary redHood = ModEntities.redhat_mercenary.get().create(helper.getLevel());
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "red_hood_wolf_accessory", 100);
        if (redHood == null) {
            worker.kill();
            helper.fail("Little Red could not be created");
            return;
        }
        moveTo(helper, redHood, new BlockPos(4, 1, 2));
        helper.getLevel().addFreshEntity(redHood);
        int initialCounter = redHood.getQliphothCounter();

        try {
            worker.getInventory().add(new ItemStack(ModItems.SHEEPSKIN_CURIO.get()));
            redHood.onWorkComplete(worker, WorkType.INSIGHT, WorkResult.GOOD);
            helper.assertTrue(redHood.getQliphothCounter() == initialCounter,
                    "backpack sheep skin incorrectly satisfied the wolf accessory rule");

            worker.getInventory().clearContent();
            boolean[] equipped = {false};
            CuriosApi.getCuriosInventory(worker).resolve().ifPresent(handler -> {
                var headwear = handler.getCurios().get("headwear");
                if (headwear != null && headwear.getSlots() > 0) {
                    headwear.getStacks().setStackInSlot(
                            0, new ItemStack(ModItems.BIG_BADWOLF_CURIO.get()));
                    equipped[0] = true;
                }
            });
            helper.assertTrue(equipped[0], "wolf accessory Curios slot was unavailable");
            redHood.onWorkComplete(worker, WorkType.INSIGHT, WorkResult.GOOD);
            helper.assertTrue(redHood.getQliphothCounter() == initialCounter - 1,
                    "equipped Big Bad Wolf accessory did not reduce Little Red's counter");
            helper.succeed();
        } finally {
            redHood.discard();
            worker.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_capture_unit")
    public static void coreSuppressionBlocksCaptureAtForgeEventBoundary(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(),
                "capture_unit_suppression", EmployeeDepartment.CONTROL);
        EmployeeServerPlayer player = new EmployeeServerPlayer(facility,
                new GameProfile(record.id(), record.name()), record);
        player.absMoveTo(200.5D, 270.0D, 20.5D, 0.0F, 0.0F);
        facility.addNewPlayer(player);
        EntityOneBad abnormality = ModEntities.onebad.get().create(facility);
        if (abnormality == null) {
            player.kill();
            helper.fail("capture target could not be created");
            return;
        }
        abnormality.absMoveTo(202.5D, 270.0D, 20.5D, 0.0F, 0.0F);
        facility.addFreshEntity(abnormality);
        ItemStack captureUnit = new ItemStack(ModItems.CAPTURE_UNIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, captureUnit);
        CoreSuppressionData suppression = CoreSuppressionData.get(facility);
        if (suppression.isActive()) suppression.finish(false);
        suppression.start(CoreSuppressionType.MALKUTH, player.getUUID(), player.getName().getString(),
                26, 0, 0, Map.of());

        try {
            PlayerInteractEvent.EntityInteract event = new PlayerInteractEvent.EntityInteract(
                    player, InteractionHand.MAIN_HAND, abnormality);
            ForgeModEvent.onPlayerRightEntity(event);
            helper.assertTrue(event.isCanceled()
                            && event.getCancellationResult() == InteractionResult.FAIL,
                    "core suppression capture was not rejected at the Forge event boundary");
            helper.assertFalse(abnormality.isRemoved(),
                    "core suppression still allowed the abnormality to be captured");
            helper.assertFalse(captureUnit.getOrCreateTag().contains(CaptureUnitItem.ENTITY_UUID),
                    "core suppression capture still wrote abnormality data into the unit");
            helper.succeed();
        } finally {
            suppression.finish(false);
            abnormality.discard();
            player.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_shoes_rescue")
    public static void redShoesJudgementCanBeBrokenByDamage(GameTestHelper helper) {
        EntityRedShoes redShoes = spawnRedShoes(helper, new BlockPos(4, 1, 2));
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "red_shoes_rescue", 20);
        float maxHealthBefore = worker.getMaxHealth();

        helper.assertTrue(redShoes.onWorkStart(worker, WorkType.ATTACHMENT),
                "red shoes rejected the synthetic work start");
        redShoes.onWorkComplete(worker, WorkType.ATTACHMENT, WorkResult.GOOD);
        helper.assertTrue(EntityRedShoes.isRedShoesControlled(worker),
                "low-temperance work did not start judgement");
        helper.assertTrue(worker.hasEffect(MobEffects.GLOWING),
                "low-temperance work skipped the judgement rescue window");
        helper.assertTrue(worker.getMaxHealth() == maxHealthBefore,
                "low-temperance work entered bloodlust immediately");

        int acceptedHits = 0;
        for (int hit = 0; hit < 5; hit++) {
            EntityUtil.clearHurtTime(worker);
            if (worker.hurt(worker.damageSources().generic(), 1.0F)) acceptedHits++;
        }

        try {
            helper.assertFalse(EntityRedShoes.isRedShoesControlled(worker),
                    "rescue failed: acceptedHits=" + acceptedHits
                            + ", recordedHits="
                            + worker.getPersistentData().getInt(CHARM_HURT_COUNT_TAG)
                            + ", health=" + worker.getHealth());
            helper.succeed();
        } finally {
            EntityRedShoes.clearControlForRecovery(worker);
            redShoes.discard();
            worker.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_shoes_combat")
    public static void redShoesControlledEmployeeDamagesAnotherEmployee(GameTestHelper helper) {
        EntityRedShoes redShoes = spawnRedShoes(helper, new BlockPos(4, 1, 2));
        EmployeeServerPlayer attacker = spawnEmployee(helper, new BlockPos(3, 1, 2),
                "red_shoes_attacker", 20);
        EmployeeServerPlayer target = spawnEmployee(helper, new BlockPos(3, 1, 3),
                "red_shoes_target", 45);
        float healthBefore = target.getHealth();

        try {
            helper.assertTrue(helper.getLevel().getEntity(redShoes.getUUID()) == redShoes,
                    "red shoes source was not registered in the test level");
            helper.assertTrue(redShoes.onWorkStart(attacker, WorkType.ATTACHMENT),
                    "red shoes rejected the controlled-combat work fixture");
            redShoes.onWorkComplete(attacker, WorkType.ATTACHMENT, WorkResult.GOOD);
            helper.assertTrue(attacker.getPersistentData().getBoolean(JUDGEMENT_TAG),
                    "low-temperance work did not establish judgement");
            helper.assertTrue(attacker.getPersistentData().hasUUID(RED_SHOES_SOURCE_TAG)
                            && helper.getLevel().getEntity(attacker.getPersistentData()
                            .getUUID(RED_SHOES_SOURCE_TAG)) == redShoes,
                    "judgement source UUID did not resolve to the red shoes entity");

            attacker.getPersistentData().putInt(JUDGEMENT_TIMER_TAG, 0);
            // Synthetic ServerPlayers are not part of PlayerList's ordinary tick loop.
            MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(attacker));

            helper.assertTrue(EntityRedShoes.isRedShoesControlled(attacker),
                    "red shoes control ended before the combat check: judgement="
                            + attacker.getPersistentData().getBoolean(JUDGEMENT_TAG)
                            + ", charmed=" + attacker.getPersistentData().getBoolean(CHARMED_TAG)
                            + ", bloodlust=" + attacker.getPersistentData().getBoolean(BLOODLUST_TAG)
                            + ", sourcePresent="
                            + attacker.getPersistentData().hasUUID(RED_SHOES_SOURCE_TAG)
                            + ", sourceAlive=" + redShoes.isAlive()
                            + ", sourceRemoved=" + redShoes.isRemoved()
                            + ", sourceRegistered="
                            + (helper.getLevel().getEntity(redShoes.getUUID()) == redShoes));
            helper.assertTrue(target.getHealth() < healthBefore,
                    "controlled attack failed: judgement="
                            + attacker.getPersistentData().getBoolean(JUDGEMENT_TAG)
                            + ", charmed=" + attacker.getPersistentData().getBoolean(CHARMED_TAG)
                            + ", bloodlust=" + attacker.getPersistentData().getBoolean(BLOODLUST_TAG)
                            + ", cooldown="
                            + attacker.getPersistentData().getInt(BLOODLUST_ATTACK_COOLDOWN_TAG)
                            + ", distanceSq=" + attacker.distanceToSqr(target)
                            + ", targetHealth=" + target.getHealth());
            helper.succeed();
        } finally {
            EntityRedShoes.clearControlForRecovery(attacker);
            redShoes.discard();
            attacker.kill();
            target.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_shoes_rescue_damage")
    public static void redShoesControlledEmployeeCanBeDamaged(GameTestHelper helper) {
        EntityRedShoes redShoes = spawnRedShoes(helper, new BlockPos(5, 1, 2));
        EmployeeServerPlayer attacker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "red_shoes_rescuer", 45);
        EmployeeServerPlayer victim = spawnEmployee(helper, new BlockPos(3, 1, 2),
                "red_shoes_controlled", 20);
        victim.getPersistentData().putBoolean(CHARMED_TAG, true);
        victim.getPersistentData().putUUID(RED_SHOES_SOURCE_TAG, redShoes.getUUID());
        float healthBefore = victim.getHealth();

        try {
            helper.assertTrue(EntityRedShoes.isRedShoesControlled(victim),
                    "test victim was not controlled by Red Shoes");
            helper.assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(
                            victim, attacker.damageSources().playerAttack(attacker)),
                    "controlled victim was still protected as friendly fire");
            EntityUtil.clearHurtTime(victim);
            helper.assertTrue(victim.hurt(attacker.damageSources().playerAttack(attacker), 4.0F),
                    "controlled victim rejected rescue damage");
            helper.assertTrue(victim.getHealth() < healthBefore,
                    "controlled victim accepted the hit without losing health");
            helper.succeed();
        } finally {
            EntityRedShoes.clearControlForRecovery(victim);
            redShoes.discard();
            attacker.kill();
            victim.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_shoes_ai_threat")
    public static void redShoesControlledEmployeeIsAnAiThreat(GameTestHelper helper) {
        EmployeeServerPlayer rescuer = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "red_shoes_ai_rescuer", 45);
        EmployeeServerPlayer controlled = spawnEmployee(helper, new BlockPos(3, 1, 2),
                "red_shoes_ai_controlled", 20);
        controlled.getPersistentData().putBoolean(CHARMED_TAG, true);

        try {
            helper.assertTrue(isThreatForEmployee(rescuer, controlled),
                    "AI threat selection ignored the Red Shoes controlled employee");
            helper.succeed();
        } finally {
            EntityRedShoes.clearControlForRecovery(controlled);
            rescuer.kill();
            controlled.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_shoes_rescue_targets")
    public static void redShoesControlledClerkAndOrdinaryPlayerAreAiThreats(GameTestHelper helper) {
        EmployeeServerPlayer rescuer = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "red_shoes_rescue_target_rescuer", 45);
        EntityClerk clerk = ModEntities.clerk.get().create(helper.getLevel());
        ServerPlayer ordinaryPlayer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "red_shoes_controlled_human"));
        if (clerk == null) {
            rescuer.kill();
            helper.fail("clerk could not be created");
            return;
        }
        moveTo(helper, clerk, new BlockPos(3, 1, 2));
        helper.getLevel().addFreshEntity(clerk);
        clerk.getPersistentData().putBoolean(CHARMED_TAG, true);
        ordinaryPlayer.getPersistentData().putBoolean(CHARMED_TAG, true);

        try {
            boolean clerkThreat = isThreatForEmployee(rescuer, clerk);
            boolean playerThreat = isThreatForEmployee(rescuer, ordinaryPlayer);
            helper.assertTrue(clerkThreat && playerThreat,
                    "AI threat selection ignored Red Shoes controlled rescue targets: clerk="
                            + clerkThreat + ", ordinaryPlayer=" + playerThreat);
            helper.succeed();
        } finally {
            EntityRedShoes.clearControlForRecovery(clerk);
            EntityRedShoes.clearControlForRecovery(ordinaryPlayer);
            clerk.discard();
            rescuer.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "recent_red_shoes_work_lock")
    public static void redShoesControlledPlayerCannotStartWork(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        BlockPos markerPos = new BlockPos(231, 270, 30);
        prepareContainmentFixture(facility, markerPos);
        EntityRedShoes redShoes = ModEntities.red_shoes.get().create(facility);
        if (redShoes == null) {
            clearContainmentFixture(facility, markerPos);
            helper.fail("red shoes could not be created");
            return;
        }
        redShoes.absMoveTo(markerPos.getX() + 2.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addFreshEntity(redShoes);
        RoomMarkerBlockEntity marker = facility.getBlockEntity(markerPos)
                instanceof RoomMarkerBlockEntity room ? room : null;
        if (marker == null || !marker.bindByUuid(facility, redShoes.getUUID())) {
            redShoes.discard();
            clearContainmentFixture(facility, markerPos);
            helper.fail("containment fixture rejected the red shoes binding");
            return;
        }

        ServerPlayer player = FakePlayerFactory.get(facility,
                new GameProfile(UUID.randomUUID(), "red_shoes_working_human"));
        player.absMoveTo(markerPos.getX() + 1.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(100);
            stats.setPrudence(100);
            stats.setTemperance(100);
            stats.setJustice(100);
        });

        try {
            helper.assertTrue(WorkManager.startWork(player, redShoes, WorkType.INSTINCT),
                    "valid ordinary-player work fixture was rejected before control");
            WorkManager.cancelWork(player, "GameTest Red Shoes work-lock baseline");
            player.getPersistentData().putBoolean(CHARMED_TAG, true);
            helper.assertFalse(WorkManager.startWork(player, redShoes, WorkType.INSTINCT),
                    "Red Shoes controlled player still established a work session");
            helper.succeed();
        } finally {
            WorkManager.cancelWork(player, "GameTest Red Shoes work-lock cleanup");
            EntityRedShoes.clearControlForRecovery(player);
            redShoes.discard();
            clearContainmentFixture(facility, markerPos);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_shoes_inventory_lock")
    public static void redShoesControlledEmployeeEquipmentCannotBeTaken(GameTestHelper helper) {
        EmployeeServerPlayer employee = spawnEmployee(helper, new BlockPos(3, 1, 2),
                "red_shoes_inventory_target", 20);
        ServerPlayer viewer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "red_shoes_inventory_viewer"));
        employee.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
        employee.getPersistentData().putBoolean(CHARMED_TAG, true);
        EmployeeInventoryMenu menu = new EmployeeInventoryMenu(
                1, viewer.getInventory(), employee, List.of());
        int employeeHotbarSlotZero = 27;

        try {
            helper.assertFalse(menu.getSlot(employeeHotbarSlotZero).mayPickup(viewer),
                    "controlled employee main-hand slot still allowed ordinary pickup");
            helper.assertTrue(menu.quickMoveStack(viewer, employeeHotbarSlotZero).isEmpty(),
                    "controlled employee main-hand item was shift-click transferred");
            helper.assertTrue(employee.getInventory().getItem(0).is(Items.IRON_AXE),
                    "controlled employee lost the forced iron axe");
            helper.succeed();
        } finally {
            menu.removed(viewer);
            EntityRedShoes.clearControlForRecovery(employee);
            employee.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_shoes_targeting")
    public static void redShoesPrefersClerkThenLowestTemperance(GameTestHelper helper) {
        EntityRedShoes redShoes = spawnRedShoes(helper, new BlockPos(4, 1, 2));
        EmployeeServerPlayer lowest = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "red_shoes_lowest", 20);
        EmployeeServerPlayer higher = spawnEmployee(helper, new BlockPos(2, 1, 3),
                "red_shoes_higher", 30);
        EntityClerk clerk = ModEntities.clerk.get().create(helper.getLevel());
        if (clerk == null) {
            redShoes.discard();
            lowest.kill();
            higher.kill();
            helper.fail("clerk could not be created");
            return;
        }
        moveTo(helper, clerk, new BlockPos(3, 1, 2));
        helper.getLevel().addFreshEntity(clerk);

        redShoes.onQliphothMeltdown();
        helper.assertTrue(EntityRedShoes.isRedShoesControlled(clerk),
                "red shoes did not treat the clerk as lowest temperance");
        helper.assertFalse(EntityRedShoes.isRedShoesControlled(lowest),
                "red shoes selected an employee while a clerk was available");

        EntityRedShoes.clearControlForRecovery(clerk);
        clerk.discard();
        redShoes.onQliphothMeltdown();

        try {
            helper.assertTrue(EntityRedShoes.isRedShoesControlled(lowest),
                    "red shoes did not select the lowest-temperance employee");
            helper.assertFalse(EntityRedShoes.isRedShoesControlled(higher),
                    "red shoes selected a higher-temperance employee");
            helper.succeed();
        } finally {
            EntityRedShoes.clearControlForRecovery(lowest);
            EntityRedShoes.clearControlForRecovery(higher);
            redShoes.discard();
            lowest.kill();
            higher.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_red_shoes_damage")
    public static void redShoesDamageBonusUsesTemperanceLevel(GameTestHelper helper) {
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "red_shoes_damage", 30, 100);
        float[] bonus = {-1.0F};
        worker.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .ifPresent(stats -> bonus[0] = RedShoesDamagePolicy.damageBonus(stats));

        try {
            helper.assertTrue(bonus[0] == 2.0F,
                    "bloodlust bonus used justice instead of temperance: " + bonus[0]);
            helper.succeed();
        } finally {
            worker.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_approval_bird")
    public static void judgementBirdAcceptsDamageFromAbnormalities(GameTestHelper helper) {
        EntityApprovalBird target = ModEntities.approval_bird.get().create(helper.getLevel());
        EntityFragmentOfUniverse attacker = ModEntities.fragment_of_the_universe.get()
                .create(helper.getLevel());
        if (target == null || attacker == null) {
            if (target != null) target.discard();
            if (attacker != null) attacker.discard();
            helper.fail("judgement bird damage test entities could not be created");
            return;
        }
        moveTo(helper, target, new BlockPos(4, 1, 2));
        moveTo(helper, attacker, new BlockPos(2, 1, 2));
        helper.getLevel().addFreshEntity(target);
        helper.getLevel().addFreshEntity(attacker);
        target.setEscape(true);
        float healthBefore = target.getHealth();
        EntityUtil.clearHurtTime(target);
        boolean accepted = target.hurt(
                DamageHelper.getDamage(attacker, "lobotocraft:red"), 4.0F);

        try {
            helper.assertTrue(accepted, "judgement bird rejected abnormality damage");
            helper.assertTrue(target.getHealth() < healthBefore,
                    "judgement bird accepted the hit without losing health");
            helper.succeed();
        } finally {
            target.discard();
            attacker.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "recent_end_bird_cg")
    public static void finalEndBirdEggDelegatesCombinedCgSequence(GameTestHelper helper) {
        EntityEndBird endBird = spawnEndBird(helper, new BlockPos(4, 1, 2));
        EndBirdEggTracker tracker = EndBirdEggTracker.get(helper.getLevel());
        tracker.clear();
        UUID eye = UUID.randomUUID();
        UUID small = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        helper.assertTrue(tracker.beginEncounter(endBird.getUUID(), Map.of(
                        eye, EndBirdEggTracker.EGG_EYE,
                        small, EndBirdEggTracker.EGG_SMALL,
                        high, EndBirdEggTracker.EGG_HIGH)),
                "end bird encounter could not be registered");

        try {
            helper.assertTrue(EntityEndBird.endBirdEggDie(helper.getLevel(), eye,
                            EndBirdEggTracker.EGG_EYE, null),
                    "first event egg did not request its illustration");
            helper.assertTrue(EntityEndBird.endBirdEggDie(helper.getLevel(), small,
                            EndBirdEggTracker.EGG_SMALL, null),
                    "second event egg did not request its illustration");
            helper.assertFalse(EntityEndBird.endBirdEggDie(helper.getLevel(), high,
                            EndBirdEggTracker.EGG_HIGH, null),
                    "final egg tried to send a competing packet instead of the combined sequence");
            helper.succeed();
        } finally {
            tracker.clear();
            endBird.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "recent_end_bird_reward")
    public static void endBirdCurioIsGrantedAndPersistedForAiEmployees(GameTestHelper helper) {
        EmployeeRecord record = spawnManagedEmployee(helper, new BlockPos(2, 1, 2),
                "end_bird_reward");
        EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
        EntityEndBird endBird = spawnEndBird(helper, new BlockPos(4, 1, 2));
        EndBirdEggTracker tracker = EndBirdEggTracker.get(helper.getLevel());
        tracker.clear();
        UUID eye = UUID.randomUUID();
        UUID small = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        helper.assertTrue(employee != null, "managed AI employee was not spawned");
        helper.assertTrue(tracker.beginEncounter(endBird.getUUID(), Map.of(
                        eye, EndBirdEggTracker.EGG_EYE,
                        small, EndBirdEggTracker.EGG_SMALL,
                        high, EndBirdEggTracker.EGG_HIGH)),
                "end bird reward encounter could not be registered");

        try {
            EntityEndBird.endBirdEggDie(helper.getLevel(), eye, EndBirdEggTracker.EGG_EYE, null);
            EntityEndBird.endBirdEggDie(helper.getLevel(), small, EndBirdEggTracker.EGG_SMALL, null);
            EntityEndBird.endBirdEggDie(helper.getLevel(), high, EndBirdEggTracker.EGG_HIGH, null);

            helper.assertTrue(ItemUtil.hasItem(employee, ModItems.END_BIRD_CURIO.get()),
                    "AI employee did not receive the end bird curio");
            helper.assertTrue(savedInventoryContains(record, ModItems.END_BIRD_CURIO.get()),
                    "AI employee end bird curio was not persisted immediately");
            helper.succeed();
        } finally {
            tracker.clear();
            endBird.discard();
            EmployeeRuntimeManager.remove(helper.getLevel().getServer(), record);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "recent_nothing_bad")
    public static void nothingThereBadResultEscapesWithoutKillingWorker(GameTestHelper helper) {
        EntityNothingThere nothingThere = spawnNothingThere(helper, new BlockPos(4, 1, 2));
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "nothing_bad", 100, 100);
        queueForcedWork(worker, nothingThere, WorkResult.BAD);

        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(worker.isAlive(),
                        "bad work killed the worker instead of causing a normal escape");
                helper.assertTrue(nothingThere.hasEscape(),
                        "bad work did not make Nothing There escape");
                helper.assertFalse(nothingThere.isEscapedDisguised(),
                        "bad work incorrectly used the worker's skin");
                helper.succeed();
            } finally {
                activeWorks().remove(worker.getUUID());
                nothingThere.discard();
                worker.kill();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "recent_nothing_justice")
    public static void nothingThereEscapesAfterJusticeThreeWorkerCompletesWork(GameTestHelper helper) {
        EntityNothingThere nothingThere = spawnNothingThere(helper, new BlockPos(4, 1, 2));
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "nothing_justice_three", 100, 45);
        queueForcedWork(worker, nothingThere, WorkResult.GOOD);

        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(worker.isAlive(), "justice III worker died during synthetic work");
                helper.assertTrue(nothingThere.hasEscape(),
                        "justice III worker completed work without triggering escape");
                helper.assertFalse(nothingThere.isEscapedDisguised(),
                        "low-justice completion incorrectly used the worker's skin");
                helper.succeed();
            } finally {
                activeWorks().remove(worker.getUUID());
                nothingThere.discard();
                worker.kill();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_nothing_disguise")
    public static void nothingThereUsesJusticeFourWorkerKilledByWork(GameTestHelper helper) {
        EntityNothingThere nothingThere = spawnNothingThere(helper, new BlockPos(4, 1, 2));
        EmployeeServerPlayer worker = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "nothing_justice_four", 100, 65);
        worker.setHealth(1.0F);
        nothingThere.attackPlayerOnFailure(worker, WorkType.INSTINCT);

        try {
            helper.assertTrue(nothingThere.hasEscape(),
                    "lethal work damage did not make Nothing There escape");
            helper.assertTrue(nothingThere.isEscapedDisguised(),
                    "justice IV worker killed by work was not used as a disguise");
            helper.succeed();
        } finally {
            nothingThere.discard();
            if (worker.isAlive()) worker.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "recent_iron_maiden")
    public static void ironMaidenDoesNotRecognizeRespawnedEntityAsSameSession(GameTestHelper helper) {
        EntityIronMaiden ironMaiden = ModEntities.iron_maiden.get().create(helper.getLevel());
        EmployeeServerPlayer operator = spawnEmployee(helper, new BlockPos(2, 1, 2),
                "iron_maiden_operator", 100);
        if (ironMaiden == null) {
            operator.kill();
            helper.fail("iron maiden could not be created");
            return;
        }
        moveTo(helper, ironMaiden, new BlockPos(4, 1, 2));
        helper.getLevel().addFreshEntity(ironMaiden);
        helper.assertTrue(ironMaiden.startUsing(operator),
                "iron maiden rejected the original operator");

        EmployeeRecord replacementRecord = new EmployeeRecord(operator.getUUID(),
                "iron_maiden_respawn", EmployeeDepartment.CONTROL);
        EmployeeServerPlayer replacement = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(operator.getUUID(), replacementRecord.name()), replacementRecord);
        replacement.setId(operator.getId());

        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(helper.getLevel().getEntity(ironMaiden.getId()) == ironMaiden,
                        "iron maiden was not visible in the server entity index");
                helper.assertTrue(ironMaiden.isUsing(operator),
                        "iron maiden lost the original operator session");
                LivingDeathEvent death = new LivingDeathEvent(
                        operator, operator.damageSources().genericKill());
                helper.assertFalse(MinecraftForge.EVENT_BUS.post(death) || death.isCanceled(),
                        "synthetic operator death was cancelled by another listener");
                helper.assertFalse(ironMaiden.isUsing(operator),
                        "player death did not terminate the Iron Maiden session");
                helper.assertFalse(ironMaiden.isUsing(replacement),
                        "same-UUID and same-ID respawn entity inherited the dead operator's session");
                helper.succeed();
            } finally {
                ironMaiden.discard();
                operator.kill();
                replacement.discard();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 180, required = true, batch = "recent_kiss_noai")
    public static void kissRemovalPreservesPreexistingNoAi(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) {
            helper.fail("zombie could not be created");
            return;
        }
        moveTo(helper, zombie, new BlockPos(3, 1, 2));
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);
        zombie.addEffect(new MobEffectInstance(ModMobEffects.KISS.get(), 40, 2));

        try {
            zombie.tick();
            helper.assertTrue(zombie.getPersistentData().getBoolean("isFreezeKiss"),
                    "three-stack kiss did not enter the frozen state");
            helper.assertTrue(zombie.isNoAi(), "kiss changed a pre-existing NoAI state");
            zombie.removeEffect(ModMobEffects.KISS.get());
            helper.assertFalse(zombie.getPersistentData().getBoolean("isFreezeKiss"),
                    "kiss freeze marker survived effect removal");
            helper.assertTrue(zombie.isNoAi(), "effect removal cleared a pre-existing NoAI state");
        } catch (RuntimeException exception) {
            zombie.discard();
            throw exception;
        }

        helper.runAfterDelay(130, () -> {
            try {
                helper.assertTrue(zombie.isNoAi(),
                        "a delayed kiss callback cleared the pre-existing NoAI state");
                helper.succeed();
            } finally {
                zombie.discard();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 120, required = true, batch = "recent_kiss_containment")
    public static void kissDoesNotSurviveAbnormalityContainmentReturn(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        BlockPos markerPos = new BlockPos(231, 270, 30);
        prepareContainmentFixture(facility, markerPos);
        EntityFragmentOfUniverse fragment = ModEntities.fragment_of_the_universe.get()
                .create(facility);
        if (fragment == null) {
            clearContainmentFixture(facility, markerPos);
            helper.fail("fragment of the universe could not be created");
            return;
        }
        fragment.absMoveTo(markerPos.getX() + 2.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addFreshEntity(fragment);
        RoomMarkerBlockEntity marker = facility.getBlockEntity(markerPos)
                instanceof RoomMarkerBlockEntity room ? room : null;
        if (marker == null || !marker.bindByUuid(facility, fragment.getUUID())) {
            fragment.discard();
            clearContainmentFixture(facility, markerPos);
            helper.fail("containment fixture rejected the abnormality binding");
            return;
        }
        EmployeeRecord workerRecord = new EmployeeRecord(UUID.randomUUID(),
                "kiss_return_worker", EmployeeDepartment.CONTROL);
        EmployeeServerPlayer worker = new EmployeeServerPlayer(facility,
                new GameProfile(workerRecord.id(), workerRecord.name()), workerRecord);
        worker.absMoveTo(markerPos.getX() + 1.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addNewPlayer(worker);
        clearSpawnProtection(worker);
        worker.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(100);
            stats.setPrudence(100);
            stats.setTemperance(100);
            stats.setJustice(100);
        });

        fragment.setEscape(true);
        SnowQueenWeapon weapon = (SnowQueenWeapon) ModItems.SNOWQUEEN_WEAPON.get();
        weapon.applyKissEffect(fragment);
        weapon.applyKissEffect(fragment);
        weapon.applyKissEffect(fragment);

        helper.runAfterDelay(2, () -> {
            try {
                MobEffectInstance kiss = fragment.getEffect(ModMobEffects.KISS.get());
                helper.assertTrue(kiss != null && kiss.getAmplifier() == 2,
                        "test target did not reach three kiss stacks");
                helper.assertTrue(fragment.getPersistentData().getBoolean("isFreezeKiss"),
                        "three-stack kiss did not enter the frozen state");

                fragment.die(fragment.damageSources().genericKill());

                helper.assertTrue(fragment.isAlive() && !fragment.isRemoved(),
                        "suppression destroyed the reusable abnormality entity");
                helper.assertFalse(fragment.hasEscape(),
                        "suppressed abnormality did not return to containment");
                helper.assertFalse(fragment.hasEffect(ModMobEffects.KISS.get()),
                        "kiss effect survived containment return");
                helper.assertFalse(fragment.getPersistentData().getBoolean("isFreezeKiss"),
                        "kiss freeze marker survived containment return");
                helper.assertTrue(AbnormalityWorkRules.isRegistered(fragment),
                        "returned abnormality is not registered for work");
                helper.assertTrue(WorkManager.startWork(worker, fragment, WorkType.INSTINCT),
                        "returned abnormality still rejected a real work session");
                WorkManager.cancelWork(worker, "GameTest containment cleanup");
                ModItems.REMOVER.get().onLeftClickEntity(
                        new ItemStack(ModItems.REMOVER.get()), worker, fragment);
                helper.assertTrue(fragment.isRemoved(),
                        "returned abnormality rejected the entity remover");
                helper.succeed();
            } finally {
                fragment.discard();
                worker.kill();
                clearContainmentFixture(facility, markerPos);
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "recent_containment_binding")
    public static void containmentReturnRestoresLostRoomBinding(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        BlockPos markerPos = new BlockPos(160, 270, 30);
        prepareContainmentFixture(facility, markerPos);
        EntityFragmentOfUniverse fragment = ModEntities.fragment_of_the_universe.get()
                .create(facility);
        if (fragment == null) {
            clearContainmentFixture(facility, markerPos);
            helper.fail("fragment of the universe could not be created");
            return;
        }
        fragment.absMoveTo(markerPos.getX() + 2.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addFreshEntity(fragment);
        RoomMarkerBlockEntity marker = facility.getBlockEntity(markerPos)
                instanceof RoomMarkerBlockEntity room ? room : null;

        try {
            helper.assertTrue(marker != null, "containment fixture has no room marker");
            helper.assertTrue(marker.bindByUuid(facility, fragment.getUUID()),
                    "room marker rejected the abnormality binding");
            fragment.absMoveTo(markerPos.getX() + 48.5D, markerPos.getY(),
                    markerPos.getZ() + 0.5D, 0.0F, 0.0F);
            fragment.setEscape(true);
            CompoundTag legacy = new CompoundTag();
            fragment.addAdditionalSaveData(legacy);
            legacy.remove("EscapeRegistrationRecorded");
            legacy.remove("EscapedFromRegisteredContainment");
            fragment.readAdditionalSaveData(legacy);
            marker.clearBinding();
            helper.assertTrue(marker.getBoundAbnormality() == null,
                    "test precondition did not clear the escaped abnormality binding");

            fragment.die(fragment.damageSources().genericKill());

            helper.assertTrue(fragment.isAlive() && !fragment.isRemoved(),
                    "containment return destroyed the abnormality");
            helper.assertFalse(fragment.hasEscape(),
                    "containment return left the abnormality escaped");
            helper.assertTrue(fragment.getUUID().equals(marker.getBoundAbnormality()),
                    "containment return did not restore the room UUID binding");
            helper.assertTrue(AbnormalityWorkRules.isRegistered(fragment),
                    "returned abnormality still failed the server work-registration rule");
            helper.succeed();
        } finally {
            fragment.discard();
            clearContainmentFixture(facility, markerPos);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "recent_containment_binding")
    public static void unregisteredEscapeWithStaleHomeDoesNotClaimRoom(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) throw new IllegalStateException("facility dimension unavailable");
        BlockPos markerPos = new BlockPos(160, 270, 30);
        prepareContainmentFixture(facility, markerPos);
        EntityFragmentOfUniverse fragment = ModEntities.fragment_of_the_universe.get().create(facility);
        if (fragment == null) throw new IllegalStateException("fragment unavailable");
        try {
            fragment.moveTo(markerPos.getX() + 48.5D, markerPos.getY(),
                    markerPos.getZ() + 0.5D, 0.0F, 0.0F);
            facility.addFreshEntity(fragment);
            fragment.bindHomeRoom(facility.dimension().location(), markerPos);
            RoomMarkerBlockEntity marker = (RoomMarkerBlockEntity) facility.getBlockEntity(markerPos);
            helper.assertFalse(AbnormalityWorkRules.isRegistered(fragment),
                    "stale-home fixture must not be registered");
            fragment.triggerEscape();
            CompoundTag saved = new CompoundTag();
            fragment.addAdditionalSaveData(saved);
            helper.assertTrue(saved.getBoolean("EscapeRegistrationRecorded")
                            && !saved.getBoolean("EscapedFromRegisteredContainment"),
                    "fixture did not record an explicitly unregistered escape");
            fragment.die(fragment.damageSources().genericKill());
            helper.assertTrue(fragment.isAlive() && !fragment.hasEscape(),
                    "unregistered stale-home entity did not recover");
            helper.assertFalse(fragment.getUUID().equals(marker.getBoundAbnormality()),
                    "explicitly unregistered escape claimed the stale home slot: entity="
                            + fragment.getUUID() + ", bound=" + marker.getBoundAbnormality());
            helper.assertFalse(AbnormalityWorkRules.isRegistered(fragment),
                    "stale home was mistaken for an authoritative escape registration");
            helper.succeed();
        } finally {
            fragment.discard();
            clearContainmentFixture(facility, markerPos);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "recent_fixed_escape_binding")
    public static void fixedContainmentUsesMappedCrossChunkEscape(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        BlockPos markerPos = new BlockPos(160, 270, 30);
        BlockPos escapePos = new BlockPos(160, 269, 52);
        List<BlockPos> escapeFixture = List.of(escapePos.below(), escapePos,
                escapePos.above(), escapePos.above(2));
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> previousStates =
                escapeFixture.stream().collect(java.util.stream.Collectors.toMap(
                        BlockPos::immutable, facility::getBlockState));
        prepareContainmentFixture(facility, markerPos);
        facility.setBlockAndUpdate(escapePos.below(), Blocks.STONE.defaultBlockState());
        facility.setBlockAndUpdate(escapePos, ModBlocks.ESCAPE.get().defaultBlockState());
        facility.setBlockAndUpdate(escapePos.above(), Blocks.AIR.defaultBlockState());
        facility.setBlockAndUpdate(escapePos.above(2), Blocks.AIR.defaultBlockState());

        EntityFragmentOfUniverse fragment = ModEntities.fragment_of_the_universe.get().create(facility);
        RoomMarkerBlockEntity marker = facility.getBlockEntity(markerPos)
                instanceof RoomMarkerBlockEntity room ? room : null;
        if (fragment == null || marker == null) {
            clearContainmentFixture(facility, markerPos);
            previousStates.forEach(facility::setBlockAndUpdate);
            helper.fail("fixed escape fixture could not be created");
            return;
        }
        fragment.absMoveTo(markerPos.getX() + 2.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addFreshEntity(fragment);

        try {
            helper.assertTrue(marker.bindByUuid(facility, fragment.getUUID()),
                    "room marker rejected the fixed escape test abnormality");
            fragment.triggerEscape();

            helper.assertTrue(fragment.hasEscape(), "abnormality did not enter escape state");
            helper.assertTrue(fragment.blockPosition().distSqr(escapePos.above()) <= 25.0D,
                    "fixed containment ignored its mapped cross-chunk exit: "
                            + fragment.blockPosition());
            helper.succeed();
        } finally {
            fragment.discard();
            clearContainmentFixture(facility, markerPos);
            previousStates.forEach(facility::setBlockAndUpdate);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "recent_punishing_bird_return")
    public static void punishingBirdIgnoresStaleNestDuringContainmentReturn(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        BlockPos markerPos = new BlockPos(160, 270, 30);
        BlockPos inside = markerPos.offset(2, 0, 0);
        BlockPos lowerSupport = inside.below(3);
        BlockPos staleNest = markerPos.offset(0, 0, 8);
        List<BlockPos> staleFixture = List.of(lowerSupport, staleNest.below(), staleNest,
                staleNest.above(), staleNest.above(2));
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> previousStates =
                staleFixture.stream().collect(java.util.stream.Collectors.toMap(
                        BlockPos::immutable, facility::getBlockState));
        prepareContainmentFixture(facility, markerPos);
        // Keep a second solid support layer below the room floor. The old
        // recovery scan preferred this lower pocket over the real anchor.
        facility.setBlockAndUpdate(lowerSupport, Blocks.STONE.defaultBlockState());
        facility.setBlockAndUpdate(staleNest.below(), Blocks.STONE.defaultBlockState());
        facility.setBlockAndUpdate(staleNest, ModBlocks.PUNISHING_BIRD.get().defaultBlockState());
        facility.setBlockAndUpdate(staleNest.above(), Blocks.AIR.defaultBlockState());
        facility.setBlockAndUpdate(staleNest.above(2), Blocks.AIR.defaultBlockState());

        EntityPunishingBird bird = ModEntities.punishing_bird.get().create(facility);
        RoomMarkerBlockEntity marker = facility.getBlockEntity(markerPos)
                instanceof RoomMarkerBlockEntity room ? room : null;
        if (bird == null || marker == null) {
            clearContainmentFixture(facility, markerPos);
            previousStates.forEach(facility::setBlockAndUpdate);
            helper.fail("punishing bird containment fixture could not be created");
            return;
        }
        bird.absMoveTo(inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addFreshEntity(bird);

        try {
            helper.assertTrue(marker.bindByUuid(facility, bird.getUUID()),
                    "room marker rejected the punishing bird binding");
            setPunishingBirdNest(bird, staleNest);
            bird.escapePosition = inside.immutable();
            bird.setEscape(true);
            finishPunishingBirdReturn(bird);

            helper.assertFalse(bird.hasEscape(), "punishing bird remained escaped after return");
            helper.assertTrue(new AABB(inside).inflate(3.0D).contains(bird.position()),
                    "stale nest overrode the original containment return position: "
                            + bird.blockPosition());
            helper.assertTrue(bird.getY() >= inside.getY(),
                    "punishing bird returned below the containment floor: " + bird.blockPosition());
            helper.assertTrue(facility.noCollision(bird),
                    "punishing bird returned with a colliding bounding box");
            helper.succeed();
        } finally {
            bird.discard();
            clearContainmentFixture(facility, markerPos);
            previousStates.forEach(facility::setBlockAndUpdate);
        }
    }

    private static void prepareContainmentFixture(ServerLevel level, BlockPos markerPos) {
        level.getChunkAt(markerPos);
        for (com.wzz.lobotocraft.entity.base.AbstractAbnormality existing
                : level.getEntitiesOfClass(com.wzz.lobotocraft.entity.base.AbstractAbnormality.class,
                new AABB(markerPos).inflate(8.0D, 5.0D, 8.0D))) {
            existing.discard();
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos floor = markerPos.offset(dx, -1, dz);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlockAndUpdate(markerPos.offset(dx, dy, dz),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(markerPos, ModBlocks.ROOM_MARKER.get().defaultBlockState());
    }

    private static void clearContainmentFixture(ServerLevel level, BlockPos markerPos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    level.setBlockAndUpdate(markerPos.offset(dx, dy, dz),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static EntityRedShoes spawnRedShoes(GameTestHelper helper, BlockPos relative) {
        EntityRedShoes redShoes = ModEntities.red_shoes.get().create(helper.getLevel());
        if (redShoes == null) {
            throw new IllegalStateException("red shoes could not be created");
        }
        moveTo(helper, redShoes, relative);
        helper.getLevel().addFreshEntity(redShoes);
        return redShoes;
    }

    private static boolean isThreatForEmployee(EmployeeServerPlayer observer, LivingEntity target) {
        try {
            Method method = EmployeeRuntimeManager.class.getDeclaredMethod(
                    "isThreatFor", EmployeeServerPlayer.class,
                    net.minecraft.world.entity.LivingEntity.class, boolean.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, observer, target, false);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to inspect employee threat selection", exception);
        }
    }

    private static void setPunishingBirdNest(EntityPunishingBird bird, BlockPos nest) {
        try {
            Field field = EntityPunishingBird.class.getDeclaredField("nestPosition");
            field.setAccessible(true);
            field.set(bird, nest.immutable());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to prepare stale punishing bird nest", exception);
        }
    }

    private static void finishPunishingBirdReturn(EntityPunishingBird bird) {
        try {
            Method method = EntityPunishingBird.class.getDeclaredMethod("finishReturningToNest");
            method.setAccessible(true);
            method.invoke(bird);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to complete punishing bird return", exception);
        }
    }

    private static EmployeeServerPlayer spawnEmployee(GameTestHelper helper, BlockPos relative,
                                                       String name, int temperance) {
        return spawnEmployee(helper, relative, name, temperance, 100);
    }

    private static EmployeeServerPlayer spawnEmployee(GameTestHelper helper, BlockPos relative,
                                                       String name, int temperance, int justice) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name,
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        employee.getAbilities().invulnerable = false;
        moveTo(helper, employee, relative);
        helper.getLevel().addNewPlayer(employee);
        clearSpawnProtection(employee);
        employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(100);
            stats.setPrudence(100);
            stats.setTemperance(temperance);
            stats.setJustice(justice);
        });
        return employee;
    }

    private static EmployeeRecord spawnManagedEmployee(GameTestHelper helper, BlockPos relative,
                                                        String name) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name,
                EmployeeDepartment.CONTROL);
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        record.setPosition(helper.getLevel().dimension().location(),
                position.x, position.y, position.z);
        record.setStats(100, 100, 100, 100);
        if (EmployeeRuntimeManager.spawn(helper.getLevel().getServer(), record) == null) {
            throw new IllegalStateException("managed AI employee could not be spawned");
        }
        return record;
    }

    private static EntityEndBird spawnEndBird(GameTestHelper helper, BlockPos relative) {
        EntityEndBird endBird = ModEntities.end_bird.get().create(helper.getLevel());
        if (endBird == null) throw new IllegalStateException("end bird could not be created");
        moveTo(helper, endBird, relative);
        helper.getLevel().addFreshEntity(endBird);
        return endBird;
    }

    private static EntityBigBadWolf spawnEscapedBigBadWolf(GameTestHelper helper, BlockPos relative) {
        EntityBigBadWolf wolf = ModEntities.bigbadwolf.get().create(helper.getLevel());
        if (wolf == null) throw new IllegalStateException("big bad wolf could not be created");
        moveTo(helper, wolf, relative);
        helper.getLevel().addFreshEntity(wolf);
        wolf.triggerEscape();
        helper.assertTrue(wolf.hasEscape(), "big bad wolf did not enter its escaped state");
        return wolf;
    }

    private static void assertWolfHowlInterrupted(GameTestHelper helper, EntityBigBadWolf wolf,
                                                  String phase) {
        helper.assertTrue("idle".equals(wolf.getAnimation()),
                phase + " remained on its final frame after fog started");
        helper.assertTrue(readIntField(wolf, "howlPhase") == 0,
                phase + " phase was not cleared when fog started");
        helper.assertTrue(readIntField(wolf, "howlTimer") == 0,
                phase + " timer was not cleared when fog started");
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not inspect " + fieldName, exception);
        }
    }

    private static EntityNothingThere spawnNothingThere(GameTestHelper helper, BlockPos relative) {
        EntityNothingThere nothingThere = ModEntities.nothing_there.get().create(helper.getLevel());
        if (nothingThere == null) throw new IllegalStateException("Nothing There could not be created");
        moveTo(helper, nothingThere, relative);
        helper.getLevel().addFreshEntity(nothingThere);
        return nothingThere;
    }

    private static void queueForcedWork(EmployeeServerPlayer worker,
                                        EntityNothingThere nothingThere,
                                        WorkResult result) {
        activeWorks().put(worker.getUUID(),
                new WorkManager.WorkSession(worker, nothingThere, WorkType.INSTINCT, false));
        WorkManager.forceCompleteWork(worker, result, "GameTest forced completion");
    }

    private static boolean savedInventoryContains(EmployeeRecord record,
                                                  net.minecraft.world.item.Item item) {
        CompoundTag saved = record.playerData();
        for (Tag entry : saved.getList("Inventory", Tag.TAG_COMPOUND)) {
            if (ItemStack.of((CompoundTag) entry).is(item)) return true;
        }
        return false;
    }

    private static void clearSpawnProtection(EmployeeServerPlayer employee) {
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(employee, 0);
            employee.setInvulnerable(false);
            employee.getAbilities().invulnerable = false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not clear synthetic player spawn protection", exception);
        }
    }

    private static void setPanicking(GameTestHelper helper, EmployeeServerPlayer employee) {
        helper.assertTrue(employee.getCapability(MentalValueProvider.MENTAL_VALUE).isPresent(),
                "synthetic employee has no mental capability");
        MentalValueUtil.setMentalValue(employee, 0.0F);
        helper.assertTrue(MentalValueUtil.isPanic(employee),
                "synthetic employee did not enter panic at zero mental value");
    }

    private static List<EntityWingBeat> wingbeatsOwnedBy(GameTestHelper helper,
                                                         EmployeeServerPlayer owner,
                                                         boolean smallOnly) {
        List<EntityWingBeat> matches = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof EntityWingBeat wingBeat
                    && owner.getUUID().equals(wingBeat.getWingBeatOwnerUUID())
                    && (!smallOnly || wingBeat.isSmall())) {
                matches.add(wingBeat);
            }
        }
        return matches;
    }

    private static void moveTo(GameTestHelper helper, net.minecraft.world.entity.Entity entity,
                               BlockPos relative) {
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        entity.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, WorkManager.WorkSession> activeWorks() {
        try {
            Field field = WorkManager.class.getDeclaredField("activeWorks");
            field.setAccessible(true);
            return (Map<UUID, WorkManager.WorkSession>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not access WorkManager sessions for GameTest", exception);
        }
    }
}
