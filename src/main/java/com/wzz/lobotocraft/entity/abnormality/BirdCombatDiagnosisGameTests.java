package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

/** Regression scenarios for common combat eligibility and the birds' independent passive intent. */
@GameTestHolder(BirdCombatDiagnosisGameTests.NAMESPACE)
public final class BirdCombatDiagnosisGameTests {
    static final String NAMESPACE = "lobotocraft_bird_combat_diagnosis";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_damage_diagnosis")
    public static void escapedBirdsAcceptOrdinaryAbnormalityDamage(GameTestHelper helper) {
        for (EntityType<? extends AbstractAbnormality> type : java.util.List.of(
                ModEntities.large_bird.get())) {
            AbstractAbnormality victim = spawn(helper, type, 4);
            EntityLumberjack attacker = spawn(helper, ModEntities.lumberjack.get(), 9);
            try {
                escapeInFixture(victim);
                escapeInFixture(attacker);
                helper.assertTrue(victim.isAbnormalityCombatTarget(), "bird is not a legal escaped combat target");
                var damage = DamageHelper.getDamage(attacker, "red");
                helper.assertTrue(FacilityCombatRules.canDamageProtectedCombatant(damage),
                        "common damage provenance policy rejected abnormality source");
                EntityUtil.clearHurtTime(victim);
                float before = victim.getHealth();
                helper.assertTrue(victim.hurt(damage, 2) && victim.getHealth() < before,
                        victim.getAbnormalityCode() + " rejects an otherwise valid abnormality RED hit");
            } finally { victim.discard(); attacker.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_damage_diagnosis")
    public static void punishingBirdAcceptsOrdinaryAbnormalityDamage(GameTestHelper helper) {
        EntityPunishingBird victim = spawn(helper, ModEntities.punishing_bird.get(), 4);
        EntityLumberjack attacker = spawn(helper, ModEntities.lumberjack.get(), 9);
        try {
            escapeInFixture(victim);
            escapeInFixture(attacker);
            var source = DamageHelper.getDamage(attacker, "red");
            helper.assertTrue(FacilityCombatRules.canDamageProtectedCombatant(source),
                    "common damage provenance rejected source");
            float before = victim.getHealth();
            EntityUtil.clearHurtTime(victim);
            helper.assertTrue(victim.hurt(source, 2) && victim.getHealth() < before,
                    "punishing bird rejects ordinary abnormality hit after common policy allows it");
            helper.succeed();
        } finally { victim.discard(); attacker.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_passive_diagnosis")
    public static void largeBirdCannotExecuteAnUncharmedAbnormality(GameTestHelper helper) {
        EntityLargeBird bird = spawn(helper, ModEntities.large_bird.get(), 4);
        EntityLumberjack victim = spawn(helper, ModEntities.lumberjack.get(), 7);
        try {
            escapeInFixture(bird);
            escapeInFixture(victim);
            helper.assertFalse(bird.doHurtTarget(victim),
                    "ordinary Mob attack entry accepts an uncharmed abnormality for large-bird execution");
            helper.assertFalse(bird.isAttackPending(), "large bird scheduled execution of abnormality");
            helper.succeed();
        } finally { bird.discard(); victim.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_passive_diagnosis")
    public static void punishingBirdDoesNotRetaliateAgainstHeroQueen(GameTestHelper helper) {
        EntityPunishingBird bird = spawn(helper, ModEntities.punishing_bird.get(), 4);
        EntityQueenOfHatred queen = spawn(helper, ModEntities.queen_of_hatred.get(), 9);
        try {
            escapeInFixture(bird);
            queen.setHeroDamageStateForTest(false);
            EntityUtil.clearHurtTime(bird);
            bird.hurt(DamageHelper.getDamage(queen, "black"), 1);
            helper.assertFalse(bird.isAngry() || bird.getTarget() == queen,
                    "abnormality damage triggers species-specific angry chase/retaliation");
            helper.succeed();
        } finally { bird.discard(); queen.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_target_diagnosis")
    public static void commonTargetSelectionDoesNotExcludePassiveBirdVictims(GameTestHelper helper) {
        CommonTargetProbe attacker = new CommonTargetProbe(helper.getLevel());
        place(helper, attacker, 4);
        EntityLargeBird bird = spawn(helper, ModEntities.large_bird.get(), 9);
        try {
            escapeInFixture(attacker);
            escapeInFixture(bird);
            for (int evaluation = 0; evaluation < 21 && attacker.getTarget() != bird; evaluation++) {
                attacker.evaluateTargets();
            }
            helper.assertTrue(attacker.getTarget() == bird,
                    "common target selection did not select the bird: selected=" + attacker.getTarget()
                            + ", attackerAlive=" + attacker.isAlive() + ", escaped=" + attacker.hasEscape()
                            + ", birdAvailable=" + bird.isAbnormalityCombatTarget()
                            + ", distance=" + attacker.distanceTo(bird));
            helper.succeed();
        } finally { attacker.discard(); bird.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_area_diagnosis")
    public static void judgementAreaAttackIncludesEscapedAbnormalities(GameTestHelper helper) throws Exception {
        EntityApprovalBird bird = spawn(helper, ModEntities.approval_bird.get(), 4);
        EntityLumberjack target = spawn(helper, ModEntities.lumberjack.get(), 7);
        try {
            escapeInFixture(bird);
            escapeInFixture(target);
            var select = EntityApprovalBird.class.getDeclaredMethod("findRoomTargets");
            select.setAccessible(true);
            helper.assertTrue(((java.util.List<?>) select.invoke(bird)).contains(target),
                    "judgement target list accepts only personnel/queen, not ordinary escaped abnormalities");
            var attack = EntityApprovalBird.class.getDeclaredMethod("executeAreaAttack");
            attack.setAccessible(true);
            float before = target.getHealth();
            attack.invoke(bird);
            helper.assertTrue(target.getHealth() < before, "judgement real area attack did not damage target");
            helper.succeed();
        } finally { bird.discard(); target.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_state_regression")
    public static void commonTargetsReleaseAContainedVictimAndSuppressorsRecognizeHero(GameTestHelper helper) {
        CommonTargetProbe attacker = new CommonTargetProbe(helper.getLevel());
        place(helper, attacker, 4);
        EntityLumberjack target = spawn(helper, ModEntities.lumberjack.get(), 9);
        EntityQueenOfHatred hero = spawn(helper, ModEntities.queen_of_hatred.get(), 13);
        try {
            escapeInFixture(attacker);
            escapeInFixture(target);
            attacker.setTarget(target);
            helper.assertTrue(attacker.getTarget() == target, "fixture failed to assign escaped target");
            target.setEscape(false);
            attacker.evaluateTargets();
            helper.assertTrue(attacker.getTarget() != target,
                    "common cached target retained a live but contained abnormality");
            attacker.setTarget(target);
            helper.assertTrue(attacker.getTarget() == null, "setTarget bypassed current combat eligibility");
            hero.setHeroDamageStateForTest(false);
            helper.assertTrue(com.wzz.lobotocraft.util.AbnormalityCombatUtil.isValidSuppressorTarget(attacker, hero),
                    "suppressors disagree with common HERO combat identity");
            target.setEscape(true);
            var pending = AbstractAbnormality.class.getDeclaredField("pendingContainmentReturn");
            pending.setAccessible(true);
            pending.setBoolean(target, true);
            helper.assertFalse(target.isAbnormalityCombatTarget(), "pending containment return remains attackable");
            pending.setBoolean(attacker, true);
            attacker.setTarget(hero);
            helper.assertTrue(attacker.getTarget() == null, "returning attacker reopened combat");
            helper.succeed();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        finally { attacker.discard(); target.discard(); hero.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_state_regression")
    public static void judgementLowHealthZeroPaleDoesNotBypassResistance(GameTestHelper helper) throws Exception {
        EntityApprovalBird bird = spawn(helper, ModEntities.approval_bird.get(), 4);
        EntityLumberjack target = spawn(helper, ModEntities.lumberjack.get(), 7);
        try {
            escapeInFixture(bird);
            escapeInFixture(target);
            target.getAttribute(com.wzz.lobotocraft.init.ModAttributes.BLUE_DAMAGE_RESISTANCE.get())
                    .setBaseValue(0);
            target.setHealth(1);
            var attack = EntityApprovalBird.class.getDeclaredMethod("executeAreaAttack");
            attack.setAccessible(true);
            attack.invoke(bird);
            helper.assertTrue(target.isAlive() && target.getHealth() == 1 && target.hasEscape(),
                    "raw HP precheck bypassed zero PALE resistance");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(com.wzz.lobotocraft.entity.EntityGallows.class,
                            target.getBoundingBox().inflate(4)).isEmpty(),
                    "immune target was bound to a delayed gallows execution");
            helper.succeed();
        } finally { bird.discard(); target.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_personnel_regression")
    public static void largeBirdExecutesOnlyPersonnelCharmedByItAndRechecksAtHit(GameTestHelper helper)
            throws Exception {
        EntityLargeBird bird = spawn(helper, ModEntities.large_bird.get(), 4);
        EntityLargeBird other = spawn(helper, ModEntities.large_bird.get(), 13);
        var clerk = ModEntities.clerk.get().create(helper.getLevel());
        if (clerk == null) throw new IllegalStateException("clerk missing");
        BlockPos pos = helper.absolutePos(new BlockPos(6, 1, 5));
        clerk.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        clerk.setNoAi(true);
        clerk.setNoGravity(true);
        helper.getLevel().addFreshEntity(clerk);
        try {
            escapeInFixture(bird);
            var charm = EntityLargeBird.class.getDeclaredMethod("startPersonnelCharm",
                    net.minecraft.world.entity.LivingEntity.class);
            charm.setAccessible(true);
            helper.assertFalse(bird.doHurtTarget(clerk), "uncharmed clerk accepted for execution");
            charm.invoke(other, clerk);
            helper.assertFalse(bird.doHurtTarget(clerk), "another bird's charm accepted for execution");
            charm.invoke(bird, clerk);
            helper.assertTrue(bird.doHurtTarget(clerk), "own charmed nearby clerk rejected");
            clerk.getPersistentData().remove("isLargeBirdCharm");
            tickWithoutMoving(bird, 80);
            helper.assertTrue(clerk.isAlive(), "cleared charm still executed at delayed hit");
            charm.invoke(bird, clerk);
            helper.assertTrue(bird.doHurtTarget(clerk), "new own charm rejected");
            tickWithoutMoving(bird, 22);
            helper.assertFalse(clerk.isAlive(), "own charmed nearby personnel did not get executed");
            helper.succeed();
        } finally { bird.discard(); other.discard(); clerk.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_personnel_regression")
    public static void largeBirdIsPassiveEvenAfterBeingHurtAndGiftUsesHeadwear(GameTestHelper helper) {
        EntityLargeBird bird = spawn(helper, ModEntities.large_bird.get(), 4);
        EntityLumberjack attacker = spawn(helper, ModEntities.lumberjack.get(), 9);
        try {
            escapeInFixture(bird);
            escapeInFixture(attacker);
            bird.hurt(DamageHelper.getDamage(attacker, "red"), 1);
            bird.setTarget(attacker);
            tickWithoutMoving(bird, 45);
            helper.assertTrue(bird.getTarget() == null && !bird.isAttackPending(),
                    "HurtByTarget or common AI restored passive bird's combat target");
            helper.assertTrue("头饰".equals(bird.getEGOGiftData().part()), "gift part is not headwear");
            helper.succeed();
        } finally { bird.discard(); attacker.discard(); }
    }

    private static void tickWithoutMoving(AbstractAbnormality entity, int ticks) {
        var pos = entity.position();
        entity.setNoGravity(true);
        for (int i = 0; i < ticks; i++) {
            entity.moveTo(pos.x, pos.y, pos.z, 0, 0);
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            entity.tickCount++;
            entity.tick();
        }
    }

    private static void escapeInFixture(AbstractAbnormality entity) {
        var position = entity.position();
        entity.triggerEscape();
        // Test combat after escape; other batches may have registered ordinary exits.
        entity.moveTo(position.x, position.y, position.z, 0, 0);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        entity.getNavigation().stop();
        if (!entity.hasEscape()) throw new IllegalStateException("fixture did not escape");
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_damage_source_matrix")
    public static void birdReceiveDamageMatrixHasNoAbnormalitySpeciesWhitelist(GameTestHelper helper) {
        var sources = reportedCombatTypes();
        var victims = java.util.List.of(ModEntities.large_bird.get(),
                ModEntities.punishing_bird.get(), ModEntities.approval_bird.get());
        int checked = 0;
        // This deliberately tests receive/source/colour eligibility, not each source's skill timeline.
        for (var sourceType : sources) {
            AbstractAbnormality source = spawn(helper, sourceType, 13);
            source.setEscape(true);
            try {
                for (var victimType : victims) {
                    AbstractAbnormality victim = spawn(helper, victimType, 4);
                    victim.setEscape(true);
                    try {
                        for (String colour : new String[]{"red", "white", "black", "blue"}) {
                            victim.setHealth(victim.getMaxHealth());
                            EntityUtil.clearHurtTime(victim);
                            float before = victim.getHealth();
                            boolean accepted = victim.hurt(DamageHelper.getDamage(source, colour), 2);
                            helper.assertTrue(accepted && victim.getHealth() < before,
                                    "receive/source matrix failed: " + source.getAbnormalityCode() + " -> "
                                            + victim.getAbnormalityCode() + " " + colour);
                            checked++;
                        }
                    } finally { victim.discard(); }
                }
            } finally { source.discard(); }
        }
        helper.assertTrue(checked == 192, "incomplete damage-source matrix");
        helper.succeed();
    }

    private static java.util.List<EntityType<? extends AbstractAbnormality>> reportedCombatTypes() {
        return java.util.List.of(ModEntities.lumberjack.get(), ModEntities.butterfly_funeral.get(),
                ModEntities.thorn_bus.get(), ModEntities.smiling_corpse_mountain.get(),
                ModEntities.censored.get(), ModEntities.censored_minion.get(),
                ModEntities.fragment_of_the_universe.get(), ModEntities.helper.get(),
                ModEntities.bigbadwolf.get(), ModEntities.redhat_mercenary.get(),
                ModEntities.fourth_match_flame.get(), ModEntities.abandoned_murderer.get(),
                ModEntities.ppodae.get(), ModEntities.blue_star.get(), ModEntities.army_in_black.get(),
                ModEntities.nothing_there.get());
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "judgement_attack_matrix")
    public static void judgementRealAreaSkillDamagesAllReportedAbnormalityTypes(GameTestHelper helper)
            throws Exception {
        EntityApprovalBird bird = spawn(helper, ModEntities.approval_bird.get(), 4);
        bird.setEscape(true);
        var attack = EntityApprovalBird.class.getDeclaredMethod("executeAreaAttack");
        attack.setAccessible(true);
        try {
            for (var type : reportedCombatTypes()) {
                AbstractAbnormality target = spawn(helper, type, 7);
                target.setEscape(true);
                target.setHealth(target.getMaxHealth());
                target.setNoAi(true);
                target.setNoGravity(true);
                float before = target.getHealth();
                EntityUtil.clearHurtTime(target);
                try {
                    attack.invoke(bird);
                    helper.assertTrue(target.getHealth() < before,
                            "judgement real skill failed to damage " + target.getAbnormalityCode());
                } finally { target.discard(); }
            }
            helper.succeed();
        } finally { bird.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE,
            timeoutTicks = 40, batch = "bird_state_regression")
    public static void heroReceivesSuppressorDamageThroughTheCommonPipeline(GameTestHelper helper) {
        EntityQueenOfHatred hero = spawn(helper, ModEntities.queen_of_hatred.get(), 4);
        var bee = ModEntities.worker_bee.get().create(helper.getLevel());
        if (bee == null) throw new IllegalStateException("bee missing");
        try {
            hero.setHeroDamageStateForTest(false);
            helper.assertTrue(com.wzz.lobotocraft.util.AbnormalityCombatUtil.isValidSuppressorTarget(bee, hero),
                    "helper classification rejects HERO");
            float before = hero.getHealth();
            hero.hurt(DamageHelper.getDamage(bee, "red"), 2);
            helper.assertTrue(hero.getHealth() < before && !hero.hasEscape(),
                    "receiver pipeline disagrees with helper classification");
            helper.assertTrue(hero.invulnerableTime == 0, "HERO bypassed common hurt-cooldown cleanup");
            helper.succeed();
        } finally { hero.discard(); bee.discard(); }
    }

    private static final class CommonTargetProbe extends EntityAbandonedMurderer {
        @SuppressWarnings("unchecked")
        private CommonTargetProbe(Level level) {
            super((EntityType<? extends TamableAnimal>) ModEntities.abandoned_murderer.get(), level);
        }
        private void evaluateTargets() { super.customServerAiStep(); }
    }

    private static <T extends AbstractAbnormality> T spawn(
            GameTestHelper helper, EntityType<T> type, int x) {
        T entity = type.create(helper.getLevel());
        if (entity == null) throw new IllegalStateException("missing test entity");
        place(helper, entity, x);
        return entity;
    }

    private static void place(GameTestHelper helper, AbstractAbnormality entity, int x) {
        for (int floorX = 1; floorX <= 16; floorX++) for (int z = 1; z <= 11; z++)
            helper.setBlock(new BlockPos(floorX, 0, z), Blocks.STONE);
        BlockPos pos = helper.absolutePos(new BlockPos(x, 1, 5));
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(entity);
        entity.recoverForNewFacilityDay();
    }
}
