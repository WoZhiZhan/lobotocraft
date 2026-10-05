package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.employee.*;
import com.wzz.lobotocraft.entity.data.AbnormalityEncyclopediaData;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.util.BuffUtil;
import com.wzz.lobotocraft.util.ControlStateRecoveryService;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(GalaxyWolfRegressionGameTests.NAMESPACE)
public final class GalaxyWolfRegressionGameTests {
    static final String NAMESPACE = "lobotocraft_galaxy_wolf";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void galaxyFirstTokenDoesNotRaiseCounterAndRepeatWorkRaisesItOnce(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var galaxy = f.spawn(ModEntities.children_galaxy.get(), 4);
            var one = f.player(6);
            var two = f.player(8);
            galaxy.onWorkComplete(one, WorkType.INSTINCT, WorkResult.GOOD);
            helper.assertTrue(galaxy.getQliphothCounter() == 1, "first token incorrectly raises shared counter");
            galaxy.onWorkComplete(one, WorkType.INSTINCT, WorkResult.GOOD);
            helper.assertTrue(galaxy.getQliphothCounter() == 2
                    && one.getEffect(ModMobEffects.FRIENDSHIP_PROOF.get()).getAmplifier() == 1,
                    "repeat work adds twice or leaves effect snapshot stale");
            galaxy.onWorkComplete(two, WorkType.INSIGHT, WorkResult.GOOD);
            helper.assertTrue(galaxy.getQliphothCounter() == 2 && BuffUtil.getFriendshipProofCounter(two) == 2,
                    "new holder raises source counter instead of sharing its strength");
            for (int i = 0; i < 5; i++) galaxy.onWorkComplete(one, WorkType.INSTINCT, WorkResult.GOOD);
            helper.assertTrue(galaxy.getQliphothCounter() == 5 && BuffUtil.getFriendshipProofCounter(two) == 5,
                    "counter cap or other holder synchronization fails");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void allHoldersDowngradeAndRecoveryReadsLiveSource(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var galaxy = f.spawn(ModEntities.children_galaxy.get(), 4);
            var one = f.player(6);
            var two = f.player(8);
            galaxy.onWorkComplete(one, WorkType.INSTINCT, WorkResult.GOOD);
            galaxy.onWorkComplete(two, WorkType.INSTINCT, WorkResult.GOOD);
            galaxy.setQliphothCounter(5);
            galaxy.decreaseQliphothCounter(3);
            helper.assertTrue(BuffUtil.getFriendshipProofCounter(one) == 2
                    && two.getEffect(ModMobEffects.FRIENDSHIP_PROOF.get()).getAmplifier() == 1,
                    "lower shared strength masked by high amplifier or hidden effect");
            // Inject stale display state: recovery must still use the source, not amplifier.
            one.removeEffect(ModMobEffects.FRIENDSHIP_PROOF.get());
            BuffUtil.giveFriendshipProof(one, 5);
            one.setHealth(1);
            ModMobEffects.FRIENDSHIP_PROOF.get().applyEffectTick(one, 4);
            helper.assertTrue(Math.abs(one.getHealth() - 5) < 0.001,
                    "recovery reads frozen amplifier instead of shared counter (existing heal tuning retained)");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void tokenStaysBoundToItsSourceAcrossSavedPlayerData(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var first = f.spawn(ModEntities.children_galaxy.get(), 4);
            var second = f.spawn(ModEntities.children_galaxy.get(), 12);
            var holder = f.player(6);
            first.onWorkComplete(holder, WorkType.INSTINCT, WorkResult.GOOD);
            first.setQliphothCounter(3);
            second.setQliphothCounter(5);
            helper.assertTrue(BuffUtil.getFriendshipProofCounter(holder) == 3, "another galaxy steals token strength");
            var saved = new CompoundTag();
            holder.addAdditionalSaveData(saved);
            holder.readAdditionalSaveData(saved);
            helper.assertTrue(BuffUtil.getFriendshipProofCounter(holder) == 3, "source identity lost on player reload");
            first.setQliphothCounter(1);
            helper.assertTrue(BuffUtil.getFriendshipProofCounter(holder) == 1, "reload froze original source strength");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void legacyProofBindsOnlyToUniqueSourceAndNewDayUpdatesDisplay(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var galaxy = f.spawn(ModEntities.children_galaxy.get(), 4);
            var holder = f.player(6);
            BuffUtil.giveFriendshipProof(holder, 5);
            galaxy.setQliphothCounter(3);
            helper.assertTrue(BuffUtil.getFriendshipProofCounter(holder) == 3,
                    "legacy holder keeps old amplifier rather than unique source");
            // addFreshEntity already ran same-day catch-up; exercise the actual
            // new-day reset hook, not a no-op catch-up for the same day.
            galaxy.resetForNewDay();
            helper.assertTrue(galaxy.getQliphothCounter() == 1
                    && holder.getEffect(ModMobEffects.FRIENDSHIP_PROOF.get()).getAmplifier() == 0,
                    "day reset leaves token display high");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void missingBoundSourceDoesNotBorrowOtherGalaxyOrHealFromOldAmplifier(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var first = f.spawn(ModEntities.children_galaxy.get(), 4);
            var second = f.spawn(ModEntities.children_galaxy.get(), 12);
            var holder = f.player(6);
            first.onWorkComplete(holder, WorkType.INSTINCT, WorkResult.GOOD);
            first.setQliphothCounter(5);
            first.discard();
            second.setQliphothCounter(3);
            holder.setHealth(1);
            ModMobEffects.FRIENDSHIP_PROOF.get().applyEffectTick(holder, 4);
            helper.assertTrue(BuffUtil.getFriendshipProofCounter(holder) == 0 && holder.getHealth() == 1,
                    "unloaded/missing source borrows other instance or heals using old snapshot");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void swallowedDeathClearsBodyFlagsAndContainedWolfDeathReleasesEmployee(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.spawn(ModEntities.bigbadwolf.get(), 4);
            var victim = f.player(6);
            var rescued = f.player(8);
            f.player(12);
            invoke(wolf, "swallowPlayer", new Class<?>[]{ServerPlayer.class}, victim);
            victim.hurt(victim.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertFalse(victim.isAlive() || victim.getPersistentData().getBoolean("wolf_swallowed")
                    || victim.isInvisible() || victim.isInvulnerable(), "death leaves swallowed body flags");
            invoke(wolf, "swallowPlayer", new Class<?>[]{ServerPlayer.class}, rescued);
            wolf.die(wolf.damageSources().genericKill());
            helper.assertFalse(rescued.getPersistentData().getBoolean("wolf_swallowed")
                    || rescued.isInvisible() || rescued.isInvulnerable(), "contained source death traps employee");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void otherWorkReducesOnlyBoundGalaxyNotNearbyOtherInstance(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var first = f.spawn(ModEntities.children_galaxy.get(), 4);
            var second = f.spawn(ModEntities.children_galaxy.get(), 12);
            var other = f.spawn(ModEntities.red_shoes.get(), 10);
            var holder = f.player(6);
            first.onWorkComplete(holder, WorkType.INSTINCT, WorkResult.GOOD);
            first.setQliphothCounter(3);
            second.setQliphothCounter(5);
            var session = new com.wzz.lobotocraft.work.WorkManager.WorkSession(holder, other, WorkType.INSTINCT, false);
            com.wzz.lobotocraft.event.ForgeModEvent.onWorkComplete(
                    new com.wzz.lobotocraft.event.definition.work.WorkCompleteEvent(
                            holder, other, WorkType.INSTINCT, WorkResult.GOOD, 1, session, false));
            helper.assertTrue(first.getQliphothCounter() == 2 && second.getQliphothCounter() == 5,
                    "other-work listener edits every nearby galaxy instead of the token's source");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void ambiguousLegacyTokenNeverSilentlyChoosesOneOfTwoSources(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var first = f.spawn(ModEntities.children_galaxy.get(), 4);
            var second = f.spawn(ModEntities.children_galaxy.get(), 12);
            var holder = f.player(6);
            BuffUtil.giveFriendshipProof(holder, 5);
            helper.assertTrue(BuffUtil.getFriendshipProofCounter(holder) == 0, "legacy ambiguity guessed a source");
            first.onWorkComplete(holder, WorkType.INSTINCT, WorkResult.GOOD);
            helper.assertTrue(BuffUtil.getFriendshipProofCounter(holder) == first.getQliphothCounter(),
                    "explicit work failed to repair legacy token ownership");
            second.setQliphothCounter(5);
            helper.assertTrue(BuffUtil.friendshipSource(holder) == first, "token rebinds after other source changes");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void swallowFlagsSurviveSaveAndRestorePriorInvisibleInvulnerableState(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.spawn(ModEntities.bigbadwolf.get(), 4);
            var victim = f.player(6);
            f.player(12);
            victim.setInvisible(true);
            victim.setInvulnerable(true);
            invoke(wolf, "swallowPlayer", new Class<?>[]{ServerPlayer.class}, victim);
            var saved = new CompoundTag();
            victim.addAdditionalSaveData(saved);
            victim.readAdditionalSaveData(saved);
            wolf.releaseSwallowed(true);
            helper.assertFalse(victim.getPersistentData().getBoolean("wolf_swallowed"), "saved control stayed active");
            helper.assertTrue(victim.isInvisible() && victim.isInvulnerable(), "release erased pre-existing flags");
            // Fixture cleanup needs an ordinary, killable player.
            victim.setInvisible(false); victim.setInvulnerable(false);
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void swallowedEmployeeCannotResumeManagedCombatOrAcceptNewOrders(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.spawn(ModEntities.bigbadwolf.get(), 6);
            var free = f.player(12);
            var record = new EmployeeRecord(UUID.randomUUID(), "wolf_managed", EmployeeDepartment.CONTROL);
            var pos = helper.absolutePos(new BlockPos(4, 1, 5));
            record.setPosition(helper.getLevel().dimension().location(), pos.getX()+0.5, pos.getY(), pos.getZ()+0.5);
            record.setStats(100, 100, 100, 100);
            helper.assertTrue(EmployeeRuntimeManager.spawn(helper.getLevel().getServer(), record) != null,
                    "managed employee fixture failed");
            try {
                var employee = EmployeeRuntimeManager.activePlayer(record.id());
                wolf.setEscape(true);
                EmployeeRuntimeManager.assignSuppress(record, wolf);
                invoke(wolf, "swallowPlayer", new Class<?>[]{ServerPlayer.class}, employee);
                helper.assertTrue(employee.getPersistentData().getBoolean("wolf_swallowed"), "swallow did not persist");
                helper.assertFalse(EmployeeRuntimeManager.assignFollow(record, free), "swallowed employee accepts order");
                tickManaged(record);
                helper.assertTrue(record.taskType() != EmployeeTaskType.SEARCH_AND_SUPPRESS
                        && employee.isInvisible() && employee.isInvulnerable(), "employee keeps combat or visible/vulnerable body");
                helper.assertTrue(employee.getDeltaMovement().lengthSqr() < 0.00001,
                        "swallowed employee retains movement input");
                wolf.releaseSwallowed(true);
                helper.assertFalse(employee.getPersistentData().getBoolean("wolf_swallowed"),
                        "AI player absent from PlayerList was not released");
                helper.assertTrue(EmployeeRuntimeManager.assignFollow(record, free), "released employee remains unschedulable");
            } finally { EmployeeRuntimeManager.remove(helper.getLevel().getServer(), record); }
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void swallowedSyntheticPlayerReleasesWithPriorControlFlagsRestored(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.spawn(ModEntities.bigbadwolf.get(), 4);
            var victim = f.player(6);
            f.player(12);
            victim.setGameMode(GameType.ADVENTURE);
            invoke(wolf, "swallowPlayer", new Class<?>[]{ServerPlayer.class}, victim);
            helper.assertTrue(victim.isInvisible() && victim.isInvulnerable(), "swallow only changes game mode");
            wolf.releaseSwallowed(true);
            helper.assertFalse(victim.getPersistentData().getBoolean("wolf_swallowed")
                    || victim.isInvisible() || victim.isInvulnerable(), "control flags leaked on release");
            helper.assertTrue(victim.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "release destroys previous game mode");
            helper.assertFalse(wolf.hasSwallowedPlayers(), "wolf swallow list did not clear");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void recoveryClearsSwallowAndWolfDoesNotRecaptureRecoveredPlayer(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.spawn(ModEntities.bigbadwolf.get(), 4);
            var victim = f.player(6);
            f.player(12);
            invoke(wolf, "swallowPlayer", new Class<?>[]{ServerPlayer.class}, victim);
            ControlStateRecoveryService.clearTransientControls(victim);
            helper.assertFalse(victim.getPersistentData().getBoolean("wolf_swallowed")
                    || victim.isInvisible() || victim.isInvulnerable(), "generic recovery left swallowed control flags");
            wolf.releaseSwallowed(true);
            helper.assertFalse(victim.isInvisible() || victim.isSpectator(), "stale wolf membership re-controls recovered employee");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void wolfBodyPaleOneIsNotEquipmentPaleTwoAndGiftSaysFace(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var wolf = f.spawn(ModEntities.bigbadwolf.get(), 4);
            helper.assertTrue("面部".equals(wolf.getEGOGiftData().part()), "wolf gift does not say face");
            helper.assertTrue(AbnormalityEncyclopediaData.getData("F-02-58").sensitiveInfo().contains("PALE(1.0"),
                    "body display not candidate original PALE 1.0");
            helper.assertTrue(wolf.getEGOArmorData().paleResistance() == 2.0F,
                    "body fix incorrectly changes armor PALE 2.0");
            helper.succeed();
        }
    }

    private static void tickManaged(EmployeeRecord record) throws Exception {
        var field = EmployeeRuntimeManager.class.getDeclaredField("ACTIVE");
        field.setAccessible(true);
        var runtime = ((java.util.Map<?,?>)field.get(null)).get(record.id());
        var method = EmployeeRuntimeManager.class.getDeclaredMethod("tick", runtime.getClass());
        method.setAccessible(true);
        method.invoke(null, runtime);
    }

    private static void invoke(Object target, String name, Class<?>[] signature, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, signature);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final List<net.minecraft.world.entity.Entity> entities = new ArrayList<>();
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            for (int x=1;x<=16;x++) for (int z=1;z<=11;z++) helper.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        }
        <T extends Mob> T spawn(EntityType<T> type, int x) {
            T mob=type.create(helper.getLevel());
            if (mob==null) throw new IllegalStateException("missing entity");
            var pos=helper.absolutePos(new BlockPos(x,1,5));
            mob.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            mob.setNoAi(true);
            helper.getLevel().addFreshEntity(mob);
            entities.add(mob);
            return mob;
        }
        EmployeeServerPlayer player(int x) throws Exception {
            var record=new EmployeeRecord(UUID.randomUUID(),"galaxy_wolf",EmployeeDepartment.CONTROL);
            var player=new EmployeeServerPlayer(helper.getLevel(),new GameProfile(record.id(),record.name()),record);
            var pos=helper.absolutePos(new BlockPos(x,1,5));
            player.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            helper.getLevel().addNewPlayer(player);
            var field=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true); field.setInt(player,0);
            player.setInvulnerable(false); player.getAbilities().invulnerable=false;
            entities.add(player);
            return player;
        }
        @Override public void close() {
            for (var e:entities) if (e instanceof ServerPlayer p) {
                ControlStateRecoveryService.clearTransientControls(p);
                BuffUtil.removeFriendshipProof(p); p.kill();
            } else e.discard();
        }
    }
}
