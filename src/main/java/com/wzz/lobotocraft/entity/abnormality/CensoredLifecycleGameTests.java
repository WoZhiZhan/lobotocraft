package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.entity.ai.AbnormalityFearService;
import com.wzz.lobotocraft.work.AbnormalityWorkRules;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenDawn;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.EntityRedHoodMercenary;
import com.wzz.lobotocraft.work.WorkType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.UUID;

@GameTestHolder(ModMain.MODID)
public final class CensoredLifecycleGameTests {
    private CensoredLifecycleGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 50, required = true, batch = "censored_attack_sound")
    public static void attackSoundOccursAtSuccessfulHitNotAtWindup(GameTestHelper helper) {
        RecordingCensored censored = recordingCensored(helper);
        EntityClerk clerk = clerkTarget(helper);
        float healthBefore = clerk.getHealth();
        helper.assertTrue(censored.doHurtTarget(clerk), "CENSORED rejected the clerk target");
        helper.assertTrue(censored.attackSounds == 0, "attack sound played before the hit");
        helper.runAfterDelay(23, () -> helper.assertTrue(censored.attackSounds == 0,
                "attack sound played during the windup"));
        helper.runAfterDelay(28, () -> {
            try {
                helper.assertTrue(censored.attackSounds == 1,
                        "successful hit did not play exactly one attack sound");
                helper.assertTrue(clerk.getHealth() < healthBefore || !clerk.isAlive(),
                        "attack sound occurred without damage");
                helper.succeed();
            } finally {
                censored.discard();
                clerk.discard();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 50, required = true, batch = "censored_attack_sound")
    public static void missedAttackDoesNotPlayHitSound(GameTestHelper helper) {
        RecordingCensored censored = recordingCensored(helper);
        EntityClerk clerk = clerkTarget(helper);
        helper.assertTrue(censored.doHurtTarget(clerk), "CENSORED rejected the clerk target");
        BlockPos far = helper.absolutePos(new BlockPos(25, 1, 3));
        clerk.moveTo(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D, 0.0F, 0.0F);
        helper.runAfterDelay(28, () -> {
            try {
                helper.assertTrue(censored.attackSounds == 0,
                        "a target out of hit range still produced the hit sound");
                helper.succeed();
            } finally {
                censored.discard();
                clerk.discard();
            }
        });
    }

    private static RecordingCensored recordingCensored(GameTestHelper helper) {
        RecordingCensored censored = new RecordingCensored(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        censored.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(censored);
        censored.triggerEscape();
        censored.setNoAi(true);
        return censored;
    }

    private static EntityClerk clerkTarget(GameTestHelper helper) {
        EntityClerk clerk = ModEntities.clerk.get().create(helper.getLevel());
        if (clerk == null) throw new IllegalStateException("clerk could not be created");
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 3));
        clerk.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        clerk.setNoAi(true);
        helper.getLevel().addFreshEntity(clerk);
        return clerk;
    }

    private static final class RecordingCensored extends EntityCensored {
        private int attackSounds;

        private RecordingCensored(ServerLevel level) {
            super(ModEntities.censored.get(), level);
        }

        @Override
        protected void playAttackSound() {
            attackSounds++;
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_work_animation")
    public static void failedWorkDamageKeepsContainedIdleAnimation(GameTestHelper helper) {
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 3));
        EmployeeServerPlayer employee = employee(helper, "censored_work_animation", 85);
        try {
            helper.assertTrue("idle".equals(censored.getAnimation()), "fixture is not idle");
            censored.attackPlayerOnFailure(employee, WorkType.INSTINCT);
            helper.assertTrue("idle".equals(censored.getAnimation()),
                    "normal failed work played the corpse-conversion sacrifice animation");
            censored.beginUniqueSacrificeAnimation();
            helper.assertTrue("sacrifice".equals(censored.getAnimation()),
                    "the separate unique sacrifice no longer plays its animation");
            helper.succeed();
        } finally {
            censored.discard();
            employee.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_lifecycle")
    public static void censoredUsesReferenceWorkStats(GameTestHelper helper) {
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 3));
        try {
            helper.assertTrue(censored.getMaxPEOutput() == 32, "wrong maximum PE output");
            helper.assertTrue(censored.getNormalWorkResultMin() == 17, "wrong normal threshold");
            helper.assertTrue(censored.getGoodWorkResultMin() == 33, "CENSORED must have no good result");
            float[][] expected = {
                    {0.8F, 0.7F, 0.6F, 0.5F, 0.4F},
                    {0.9F, 0.8F, 0.7F, 0.6F, 0.5F},
                    {0.7F, 0.6F, 0.5F, 0.4F, 0.3F},
                    {0.0F, 0.0F, 0.0F, 0.0F, 0.0F}
            };
            float[][] actual = censored.getFullWorkPreferences();
            for (int work = 0; work < 4; work++) {
                for (int level = 0; level < 5; level++) {
                    helper.assertTrue(Math.abs(actual[work][level] - expected[work][level]) < 0.001F,
                            "wrong work preference " + work + "/" + level);
                }
            }
            helper.succeed();
        } finally {
            censored.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_lifecycle")
    public static void escapeAndSuppressionUseSharedContainmentLifecycle(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        BlockPos markerPos = new BlockPos(160, 270, 30);
        prepareContainmentFixture(facility, markerPos);
        EntityCensored censored = ModEntities.censored.get().create(facility);
        if (censored == null) {
            clearContainmentFixture(facility, markerPos);
            helper.fail("CENSORED could not be created");
            return;
        }
        censored.moveTo(markerPos.getX() + 2.5D, markerPos.getY(),
                markerPos.getZ() + 0.5D, 0.0F, 0.0F);
        facility.addFreshEntity(censored);
        try {
            RoomMarkerBlockEntity marker = facility.getBlockEntity(markerPos)
                    instanceof RoomMarkerBlockEntity room ? room : null;
            helper.assertTrue(marker != null, "containment fixture has no room marker");
            helper.assertTrue(marker.bindByUuid(facility, censored.getUUID()),
                    "room marker rejected CENSORED binding");
            censored.tick();
            helper.assertTrue(!censored.hasEscape(), "CENSORED spawned in the escaped state");
            helper.assertTrue(censored.isNoAi(), "contained CENSORED did not disable AI");

            censored.triggerEscape();
            helper.assertTrue(censored.hasEscape(), "CENSORED did not enter the escaped state");
            helper.assertTrue(!censored.isNoAi(), "escaped CENSORED kept NoAI enabled");

            censored.stopEscape();
            helper.assertTrue(!censored.hasEscape(), "suppressed CENSORED stayed escaped");
            helper.assertTrue(censored.isNoAi(), "returned CENSORED did not restore containment AI state");
            helper.succeed();
        } finally {
            censored.discard();
            clearContainmentFixture(facility, markerPos);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_lifecycle")
    public static void censoredTargetsEscapedAbnormalitiesRedHoodAndOrdeal(GameTestHelper helper) {
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 3));
        EntityCensored other = ModEntities.censored.get().create(helper.getLevel());
        EntityCensoredMinion child = ModEntities.censored_minion.get().create(helper.getLevel());
        EntityRedHoodMercenary redHood = ModEntities.redhat_mercenary.get().create(helper.getLevel());
        EntityQueenOfHatred hero = ModEntities.queen_of_hatred.get().create(helper.getLevel());
        EntityQueenOfHatred villain = ModEntities.queen_of_hatred.get().create(helper.getLevel());
        EntityGreenDawn ordeal = ModEntities.green_dawn.get().create(helper.getLevel());
        if (other == null || child == null || redHood == null || ordeal == null
                || hero == null || villain == null) {
            censored.discard();
            helper.fail("could not create CENSORED target-filter fixtures");
            return;
        }
        try {
            other.setEscape(true);
            hero.setHeroDamageStateForTest(false);
            villain.setEscape(true);
            child.setParentCensoredUuid(censored.getUUID());
            helper.assertTrue(censored.isValidCombatTarget(other),
                    "CENSORED rejected another escaped abnormality");
            helper.assertTrue(censored.isValidCombatTarget(redHood),
                    "CENSORED rejected Red Hood");
            helper.assertTrue(censored.isValidCombatTarget(ordeal),
                    "CENSORED rejected an Ordeal entity");
            helper.assertTrue(censored.isValidCombatTarget(hero),
                    "CENSORED rejected the HERO Queen");
            helper.assertTrue(censored.isValidCombatTarget(villain),
                    "CENSORED rejected the escaped Queen");
            helper.assertFalse(censored.isValidCombatTarget(child),
                    "CENSORED selected its own child");
            other.setEscape(false);
            helper.assertFalse(censored.isValidCombatTarget(other),
                    "CENSORED selected a contained abnormality");
            helper.succeed();
        } finally {
            censored.discard();
            other.discard();
            child.discard();
            redHood.discard();
            ordeal.discard();
            hero.discard();
            villain.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 50, required = true, batch = "censored_hero_hit")
    public static void censoredAttackActuallyDamagesHeroQueen(GameTestHelper helper) {
        RecordingCensored censored = recordingCensored(helper);
        EntityQueenOfHatred hero = ModEntities.queen_of_hatred.get().create(helper.getLevel());
        if (hero == null) throw new IllegalStateException("Queen fixture could not be created");
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 3));
        hero.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(hero);
        hero.setHeroDamageStateForTest(false);
        hero.setNoAi(true);
        float before = hero.getHealth();
        helper.assertTrue(censored.doHurtTarget(hero), "CENSORED rejected HERO Queen attack");
        helper.runAfterDelay(29, () -> {
            try {
                helper.assertTrue(hero.getHealth() < before,
                        "CENSORED selected HERO Queen but dealt no actual damage");
                helper.succeed();
            } finally {
                censored.discard();
                hero.discard();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 100, required = true, batch = "censored_child_combat")
    public static void censoredChildDamagesEscapedAbnormalityButNotItsFamily(GameTestHelper helper) {
        EntityCensoredMinion child = ModEntities.censored_minion.get().create(helper.getLevel());
        EntityCensored parent = ModEntities.censored.get().create(helper.getLevel());
        EntityCensoredMinion sibling = ModEntities.censored_minion.get().create(helper.getLevel());
        EntityAbandonedMurderer escaped = ModEntities.abandoned_murderer.get().create(helper.getLevel());
        if (child == null || parent == null || sibling == null || escaped == null) {
            helper.fail("CENSORED child target fixtures could not be created");
            return;
        }
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        child.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
        parent.moveTo(origin.getX() + 1.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
        escaped.moveTo(origin.getX() + 1.5D, origin.getY(), origin.getZ() + 1.5D, 0.0F, 0.0F);
        child.setParentCensoredUuid(parent.getUUID());
        helper.getLevel().addFreshEntity(child);
        helper.getLevel().addFreshEntity(escaped);
        parent.setEscape(true);
        sibling.setEscape(true);
        escaped.setEscape(true);
        escaped.setNoAi(true);
        helper.assertFalse(child.isValidCombatTarget(parent), "child accepts the CENSORED body");
        helper.assertFalse(child.isValidCombatTarget(sibling), "child accepts another CENSORED child");
        helper.assertTrue(child.isValidCombatTarget(escaped), "child rejects escaped abnormality");
        float escapedBefore = escaped.getHealth();
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(escaped.getHealth() < escapedBefore,
                        "CENSORED child did not damage a nearby escaped abnormality");
                helper.succeed();
            } finally {
                child.discard();
                parent.discard();
                sibling.discard();
                escaped.discard();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_lifecycle")
    public static void panicInsideCensoredRoomDecrementsExactlyOnce(GameTestHelper helper) {
        ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (facility == null) {
            helper.fail("facility dimension was not loaded");
            return;
        }
        BlockPos markerPos = new BlockPos(160, 270, 30);
        prepareContainmentFixture(facility, markerPos);
        EntityCensored censored = ModEntities.censored.get().create(facility);
        if (censored == null) {
            clearContainmentFixture(facility, markerPos);
            helper.fail("CENSORED could not be created");
            return;
        }
        censored.moveTo(markerPos.getX() + 2.5D, markerPos.getY(), markerPos.getZ() + 0.5D,
                0.0F, 0.0F);
        facility.addFreshEntity(censored);
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), "censored_panic_room",
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(facility,
                new GameProfile(record.id(), record.name()), record);
        employee.moveTo(markerPos.getX() + 1.5D, markerPos.getY(), markerPos.getZ() + 0.5D,
                0.0F, 0.0F);
        facility.addNewPlayer(employee);
        try {
            RoomMarkerBlockEntity marker = (RoomMarkerBlockEntity) facility.getBlockEntity(markerPos);
            helper.assertTrue(marker.bindByUuid(facility, censored.getUUID()),
                    "room marker rejected CENSORED binding");
            com.wzz.lobotocraft.util.MentalValueUtil.setMentalValue(employee, 1.0F);
            com.wzz.lobotocraft.util.MentalValueUtil.setMentalValue(employee, 0.0F);
            helper.assertTrue(censored.getQliphothCounter() == 1,
                    "panic in containment did not lower CENSORED counter immediately");
            com.wzz.lobotocraft.util.MentalValueUtil.setMentalValue(employee, 0.0F);
            helper.assertTrue(censored.getQliphothCounter() == 1,
                    "same panic episode lowered CENSORED counter twice");
            employee.moveTo(markerPos.getX() + 23.5D, markerPos.getY(),
                    markerPos.getZ() + 0.5D, 0.0F, 0.0F);
            com.wzz.lobotocraft.util.MentalValueUtil.setMentalValue(employee, 1.0F);
            com.wzz.lobotocraft.util.MentalValueUtil.setMentalValue(employee, 0.0F);
            helper.assertTrue(censored.getQliphothCounter() == 1,
                    "panic outside containment lowered CENSORED counter");
            helper.succeed();
        } finally {
            employee.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            censored.discard();
            clearContainmentFixture(facility, markerPos);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_lifecycle")
    public static void unregisteredSuppressionReturnsToEscapeOrigin(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 3));
        BlockPos origin = censored.blockPosition();
        try {
            censored.triggerEscape();
            helper.assertTrue(censored.hasEscape(), "unregistered CENSORED did not enter escape state");

            BlockPos far = helper.absolutePos(new BlockPos(12, 1, 12));
            censored.moveTo(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D, 0.0F, 0.0F);
            censored.die(censored.damageSources().genericKill());

            helper.assertTrue(censored.isAlive() && !censored.isRemoved(),
                    "unregistered CENSORED was permanently removed by suppression");
            helper.assertFalse(censored.hasEscape(),
                    "unregistered CENSORED remained escaped after suppression");
            helper.assertTrue(origin.equals(censored.blockPosition()),
                    "unregistered CENSORED did not return to its pre-escape position");
            helper.assertTrue(Math.abs(censored.getMaxHealth() - censored.getHealth()) < 0.001F,
                    "unregistered CENSORED was not restored to full health");
            helper.assertFalse(AbnormalityWorkRules.isRegistered(censored),
                    "unregistered CENSORED claimed a containment slot during suppression");
            helper.succeed();
        } finally {
            censored.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_lifecycle")
    public static void unregisteredSuppressionUsesSupportedFallbackForAirborneOrigin(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 3, 3));
        BlockPos origin = censored.blockPosition();
        try {
            helper.assertTrue(helper.getLevel().getBlockState(origin.below()).isAir(),
                    "fallback fixture must start without support");
            censored.triggerEscape();
            BlockPos far = helper.absolutePos(new BlockPos(12, 1, 12));
            censored.moveTo(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D, 0.0F, 0.0F);
            censored.die(censored.damageSources().genericKill());

            helper.assertTrue(censored.isAlive() && !censored.isRemoved() && !censored.hasEscape(),
                    "unsupported origin prevented suppression recovery");
            helper.assertTrue(origin.below(2).equals(censored.blockPosition()),
                    "suppression did not select the supported position below the airborne origin");
            helper.assertTrue(Math.abs(censored.getMaxHealth() - censored.getHealth()) < 0.001F,
                    "fallback suppression did not restore full health");
            helper.assertFalse(AbnormalityWorkRules.isRegistered(censored),
                    "fallback suppression claimed an unrelated containment slot");
            helper.succeed();
        } finally {
            censored.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 100, required = true, batch = "censored_lifecycle")
    public static void tombstoneSacrificeSpawnsMinionAfterAnimation(GameTestHelper helper) {
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 3));
        censored.triggerEscape();
        BlockPos tombstone = censored.blockPosition().east();
        helper.getLevel().setBlockAndUpdate(tombstone, ModBlocks.TOMBSTONE.get().defaultBlockState());

        helper.runAfterDelay(70, () -> {
            int minionCount = helper.getLevel().getEntitiesOfClass(
                    EntityCensoredMinion.class, censored.getBoundingBox().inflate(8.0D)).size();
            helper.assertTrue(minionCount == 1,
                    "CENSORED sacrifice produced " + minionCount + " minions instead of one");
            helper.assertTrue(!helper.getLevel().getBlockState(tombstone).is(ModBlocks.TOMBSTONE.get()),
                    "CENSORED did not consume the tombstone");
            censored.discard();
            helper.getLevel().getEntitiesOfClass(EntityCensoredMinion.class,
                    censored.getBoundingBox().inflate(8.0D)).forEach(EntityCensoredMinion::discard);
            helper.succeed();
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 180, required = true, batch = "censored_tombstone_reach")
    public static void censoredCanConvertTombstoneAcrossNarrowDoor(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                if (x == 1 || x == 10 || z == 1 || z == 10 || x == 6 && z != 7) {
                    for (int y = 1; y <= 6; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 7));
        censored.triggerEscape();
        BlockPos tombstone = helper.absolutePos(new BlockPos(7, 1, 7));
        helper.getLevel().setBlockAndUpdate(tombstone, ModBlocks.TOMBSTONE.get().defaultBlockState());
        helper.runAfterDelay(145, () -> {
            try {
                helper.assertFalse(helper.getLevel().getBlockState(tombstone).is(ModBlocks.TOMBSTONE.get()),
                        "CENSORED could not convert the visible tombstone across a narrow doorway"
                                + " (position=" + censored.position()
                                + ", animation=" + censored.getAnimation()
                                + ", pathActive=" + censored.getNavigation().isInProgress() + ")");
                helper.assertTrue(!helper.getLevel().getEntitiesOfClass(EntityCensoredMinion.class,
                                new AABB(tombstone).inflate(2.0D)).isEmpty(),
                        "CENSORED consumed tombstone but did not create a child");
                helper.succeed();
            } finally {
                censored.discard();
                helper.getLevel().getEntitiesOfClass(EntityCensoredMinion.class,
                        censored.getBoundingBox().inflate(12.0D)).forEach(EntityCensoredMinion::discard);
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_fear")
    public static void suppressionAllowsANewFearEncounterWithoutHealingEmployee(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        EntityCensored body = spawnCensored(helper, new BlockPos(3, 1, 3));
        EntityCensored other = spawnCensored(helper, new BlockPos(10, 1, 10));
        EmployeeServerPlayer employee = employee(helper, "censored_reencounter", 85);
        String tag = "lobotocraft_fear_encountered_employees";
        try {
            CompoundTag otherMemory = new CompoundTag();
            otherMemory.putBoolean(employee.getUUID().toString(), true);
            other.getPersistentData().put(tag, otherMemory);
            body.triggerEscape();
            employee.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    body.getEyePosition());
            body.tickCount = 5;
            float maximum = com.wzz.lobotocraft.util.MentalValueUtil.getEffectiveMaxMentalValue(employee);
            com.wzz.lobotocraft.util.MentalValueUtil.setMentalValue(employee, maximum);
            AbnormalityFearService.tickEncounter(body, body);
            float afterFirst = com.wzz.lobotocraft.util.MentalValueUtil.getMentalValue(employee);
            helper.assertTrue(afterFirst < maximum, "first visible encounter did not cause fear");
            com.wzz.lobotocraft.util.MentalValueUtil.setMentalValue(employee, maximum);
            AbnormalityFearService.tickEncounter(body, body);
            helper.assertTrue(com.wzz.lobotocraft.util.MentalValueUtil.getMentalValue(employee) == maximum,
                    "fear repeated within the same escape");
            com.wzz.lobotocraft.util.MentalValueUtil.setMentalValue(employee, maximum - 5.0F);
            body.die(body.damageSources().genericKill());
            helper.assertFalse(body.hasEscape(), "suppression did not return CENSORED");
            helper.assertFalse(body.getPersistentData().contains(tag),
                    "suppression retained this CENSORED's encounter memory");
            helper.assertTrue(other.getPersistentData().getCompound(tag)
                            .getBoolean(employee.getUUID().toString()),
                    "suppression erased another source's encounter memory");
            helper.assertTrue(com.wzz.lobotocraft.util.MentalValueUtil.getMentalValue(employee) == maximum - 5.0F,
                    "suppression incorrectly healed the employee");
            body.triggerEscape();
            employee.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    body.getEyePosition());
            AbnormalityFearService.tickEncounter(body, body);
            helper.assertTrue(com.wzz.lobotocraft.util.MentalValueUtil.getMentalValue(employee) < maximum - 5.0F,
                    "a new escape did not cause a new fear encounter");
            helper.succeed();
        } finally {
            body.discard();
            other.discard();
            employee.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_fear")
    public static void censoredFearUsesOriginalBodyAndChildLevels(GameTestHelper helper) {
        EntityCensored body = spawnCensored(helper, new BlockPos(2, 1, 2));
        EntityCensoredMinion child = ModEntities.censored_minion.get().create(helper.getLevel());
        if (child == null) throw new IllegalStateException("CENSORED child could not be created");
        child.moveTo(helper.absolutePos(new BlockPos(3, 1, 2)).getX() + 0.5D,
                helper.absolutePos(new BlockPos(3, 1, 2)).getY(),
                helper.absolutePos(new BlockPos(3, 1, 2)).getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(child);
        EmployeeServerPlayer low = employee(helper, "censored_low", 60);
        EmployeeServerPlayer high = employee(helper, "censored_high", 85);
        try {
            helper.assertTrue(body.encounterFear(low).forcePanic(),
                    "CENSORED body did not force panic for level IV employee");
            helper.assertTrue(body.encounterFear(high).level() == 4,
                    "CENSORED body did not use horror level 4 for level V employee");
            helper.assertTrue(child.encounterFear(low).forcePanic(),
                    "CENSORED child did not force panic for level IV employee");
            helper.assertTrue(child.encounterFear(high).level() == 3,
                    "CENSORED child did not use horror level 3 for level V employee");
            helper.succeed();
        } finally {
            body.discard();
            child.discard();
            low.discard();
            high.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_fear")
    public static void respawnResetsCensoredBodyAndChildEncounters(GameTestHelper helper) {
        EntityCensored body = spawnCensored(helper, new BlockPos(2, 1, 2));
        EntityCensoredMinion child = ModEntities.censored_minion.get().create(helper.getLevel());
        if (child == null) throw new IllegalStateException("CENSORED child could not be created");
        BlockPos childPos = helper.absolutePos(new BlockPos(3, 1, 2));
        child.moveTo(childPos.getX() + 0.5D, childPos.getY(), childPos.getZ() + 0.5D,
                0.0F, 0.0F);
        helper.getLevel().addFreshEntity(child);
        EmployeeServerPlayer employee = employee(helper, "censored_respawn", 85);
        String employeeId = employee.getUUID().toString();
        String otherEmployeeId = UUID.randomUUID().toString();
        try {
            CompoundTag bodyEncounters = new CompoundTag();
            bodyEncounters.putBoolean(employeeId, true);
            bodyEncounters.putBoolean(otherEmployeeId, true);
            body.getPersistentData().put("lobotocraft_fear_encountered_employees", bodyEncounters);
            CompoundTag childEncounters = new CompoundTag();
            childEncounters.putBoolean(employeeId, true);
            child.getPersistentData().put("lobotocraft_fear_encountered_employees", childEncounters);

            AbnormalityFearService.resetEncounter(employee);

            helper.assertFalse(body.getPersistentData().getCompound(
                    "lobotocraft_fear_encountered_employees").contains(employeeId),
                    "respawn did not reset the CENSORED body encounter");
            helper.assertTrue(body.getPersistentData().getCompound(
                    "lobotocraft_fear_encountered_employees").contains(otherEmployeeId),
                    "respawn reset another employee's CENSORED body encounter");
            helper.assertFalse(child.getPersistentData().contains(
                    "lobotocraft_fear_encountered_employees"),
                    "respawn did not reset the CENSORED child encounter");
            helper.succeed();
        } finally {
            body.discard();
            child.discard();
            employee.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_dimensions")
    public static void censoredCollisionMatchesJudgementBird(GameTestHelper helper) {
        EntityCensored censored = spawnCensored(helper, new BlockPos(3, 1, 3));
        try {
            EntityDimensions dimensions = censored.getDimensions(censored.getPose());
            EntityDimensions judgement = ModEntities.approval_bird.get().getDimensions();
            helper.assertTrue(dimensions.width == judgement.width && dimensions.height == judgement.height,
                    "CENSORED collision dimensions do not match the judgement bird");
            helper.succeed();
        } finally {
            censored.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_child_dimensions")
    public static void censoredChildCollisionCoversScaledModel(GameTestHelper helper) {
        EntityCensoredMinion child = ModEntities.censored_minion.get().create(helper.getLevel());
        if (child == null) throw new IllegalStateException("CENSORED child fixture could not be created");
        try {
            EntityDimensions dimensions = child.getDimensions(child.getPose());
            helper.assertTrue(dimensions.width >= 1.9F && dimensions.height >= 1.15F,
                    "CENSORED child collision remains smaller than its intended scaled model");
            helper.succeed();
        } finally {
            child.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "censored_child_escape")
    public static void censoredChildUsesIndependentEscapeLifecycle(GameTestHelper helper) {
        EntityCensoredMinion child = ModEntities.censored_minion.get().create(helper.getLevel());
        if (child == null) throw new IllegalStateException("CENSORED child could not be created");
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        child.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(child);
        child.tick();

        helper.assertTrue(child.hasEscape(), "CENSORED child did not enter the escape lifecycle");
        helper.assertTrue(child.getRiskLevel() == RiskLevel.HE,
                "CENSORED child did not register as an HE escape");
        child.stopEscape();
        helper.assertTrue(child.isRemoved(), "suppressed CENSORED child was not removed");
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "end_bird_eggs")
    public static void endBirdEggsCannotOccupyContainment(GameTestHelper helper) {
        EntityEndBirdEggSmall small = ModEntities.end_bird_egg_small.get().create(helper.getLevel());
        EntityEndBirdEggHigh high = ModEntities.end_bird_egg_high.get().create(helper.getLevel());
        EntityEndBirdEggEye eye = ModEntities.end_bird_egg_eye.get().create(helper.getLevel());
        if (small == null || high == null || eye == null) {
            helper.fail("end bird egg could not be created");
            return;
        }
        try {
            helper.assertFalse(small.canBeContained(), "small egg can occupy containment");
            helper.assertFalse(high.canBeContained(), "high egg can occupy containment");
            helper.assertFalse(eye.canBeContained(), "eye egg can occupy containment");
            helper.succeed();
        } finally {
            small.discard();
            high.discard();
            eye.discard();
        }
    }

    private static EntityCensored spawnCensored(GameTestHelper helper, BlockPos relativePos) {
        EntityCensored censored = ModEntities.censored.get().create(helper.getLevel());
        if (censored == null) {
            helper.fail("CENSORED could not be created");
            throw new IllegalStateException("CENSORED could not be created");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        censored.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(censored);
        return censored;
    }

    private static EmployeeServerPlayer employee(GameTestHelper helper, String name, int stat) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name, EmployeeDepartment.CONTROL);
        record.setStats(stat, stat, stat, stat);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        employee.moveTo(helper.absolutePos(new BlockPos(7, 1, 7)).getX() + 0.5D,
                helper.absolutePos(new BlockPos(7, 1, 7)).getY(),
                helper.absolutePos(new BlockPos(7, 1, 7)).getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addNewPlayer(employee);
        employee.getCapability(com.wzz.lobotocraft.capability.EmployeeStatsProvider.EMPLOYEE_STATS)
                .ifPresent(stats -> {
                    stats.setFortitude(stat);
                    stats.setPrudence(stat);
                    stats.setTemperance(stat);
                    stats.setJustice(stat);
                });
        return employee;
    }

    private static void prepareContainmentFixture(ServerLevel level, BlockPos markerPos) {
        level.getChunkAt(markerPos);
        for (EntityCensored existing : level.getEntitiesOfClass(EntityCensored.class,
                new AABB(markerPos).inflate(8.0D, 5.0D, 8.0D))) {
            existing.discard();
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlockAndUpdate(markerPos.offset(dx, -1, dz),
                        Blocks.STONE.defaultBlockState());
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
}
