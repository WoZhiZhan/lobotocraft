package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahDamageTuning;
import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.entity.EntityLightFollower;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.event.listener.BlackForestEvent.BlackForestSavedData;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.TimerEntry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;

/** Only the confirmed 0.2.7 combat pairs, not a general abnormality hostility matrix. */
@GameTestHolder(Version027CombatRegressionGameTests.TEST_NAMESPACE)
public final class Version027CombatRegressionGameTests {
    static final String TEST_NAMESPACE = "lobotocraft_v027_combat";

    private Version027CombatRegressionGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "v027_combat")
    public static void escapedBirdsNeverProactivelySelectHeroQueen(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        for (EntityType<? extends AbstractAbnormality> type : List.of(
                ModEntities.large_bird.get(), ModEntities.punishing_bird.get())) {
            try (CombatFixture fixture = new CombatFixture(helper)) {
                AbstractAbnormality bird = fixture.spawn(type, 0.0D);
                bird.triggerEscape();
                EntityQueenOfHatred hero = fixture.spawn(ModEntities.queen_of_hatred.get(), 4.0D);
                hero.setHeroDamageStateForTest(false);
                helper.assertTrue(bird.hasEscape() && !hero.hasEscape()
                                && hero.isAbnormalityCombatTarget(),
                        "bird/HERO combat fixture did not enter the expected identities");

                boolean selected = false;
                // Never tick the queen: this is proactive selection, not retaliation.
                for (int tick = 0; tick < 100; tick++) {
                    fixture.tick(bird, 0.0D);
                    selected |= bird.getTarget() == hero;
                }
                if (selected) failures.add(bird.getAbnormalityCode() + " proactively selected HERO");
            }
        }
        helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "v027_combat")
    public static void escapedCombatantsAndHeroAcquireAndDamageGeburah(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        List<EntityType<? extends AbstractAbnormality>> types = List.of(
                ModEntities.lumberjack.get(), ModEntities.queen_of_hatred.get(),
                ModEntities.queen_of_hatred.get(), ModEntities.fragment_of_the_universe.get(),
                ModEntities.censored.get());
        String[] names = {"lumberjack", "queen HERO", "queen VILLAIN", "fragment", "CENSORED"};
        int[] earliestHits = {47, 26, 265, 40, 25};
        int[] windows = {90, 100, 360, 100, 100};
        for (int index = 0; index < types.size(); index++) {
            try (CombatFixture fixture = new CombatFixture(helper)) {
                AbstractAbnormality attacker = fixture.spawn(types.get(index), 0.0D);
                if (index == 1) {
                    EntityQueenOfHatred hero = (EntityQueenOfHatred) attacker;
                    hero.setHeroDamageStateForTest(false);
                    CompoundTag saved = new CompoundTag();
                    hero.addAdditionalSaveData(saved);
                    // A ready normal attack, but no teleport or laser pre-empts the star.
                    saved.putInt("QueenHeroNormalCooldown", 2);
                    saved.putInt("QueenHeroLaserCooldown", 1000);
                    saved.putInt("QueenHeroTeleportCooldown", 1000);
                    hero.readAdditionalSaveData(saved);
                    helper.assertTrue(!hero.hasEscape() && hero.isAbnormalityCombatTarget(),
                            "HERO must be a combatant without becoming escaped");
                } else {
                    attacker.triggerEscape();
                    helper.assertTrue(attacker.hasEscape(), names[index] + " did not escape");
                }
                // Freshly spawned Geburah is real, but never ticked or registered as an encounter.
                // That avoids retaliation/GodKill and preserves the existing facility challenge.
                double distance = index == 1 ? 4.0D : 2.5D;
                EntityGeburah geburah = fixture.spawn(ModEntities.geburah.get(), distance);
                if (attacker instanceof EntityCensored) {
                    helper.assertTrue(attacker.isWithinMeleeAttackRange(geburah),
                            "CENSORED fixture is outside melee startup range: distanceSquared="
                                    + attacker.getPerceivedTargetDistanceSquareForMeleeAttack(geburah)
                                    + ", reachSquared=" + attacker.getMeleeAttackRangeSqr(geburah));
                }
                float initialHealth = geburah.getHealth();
                boolean selected = false;
                int firstDamageTick = -1;
                for (int tick = 1; tick <= windows[index]; tick++) {
                    fixture.tick(attacker, 0.0D);
                    selected |= attacker.getTarget() == geburah;
                    // Fragment keeps its selected victim in the attack timeline, not Mob.target.
                    if (attacker instanceof EntityFragmentOfUniverse) {
                        selected |= "attack1".equals(attacker.getAnimation())
                                || "attack2".equals(attacker.getAnimation())
                                || "sing".equals(attacker.getAnimation());
                    }
                    if (geburah.getHealth() < initialHealth) {
                        firstDamageTick = tick;
                        break;
                    }
                }
                if (!selected || firstDamageTick < earliestHits[index]) {
                    failures.add(names[index] + ": selected=" + selected
                            + ", firstDamageTick=" + firstDamageTick
                            + ", expected " + earliestHits[index] + ".." + windows[index]
                            + ", HP=" + initialHealth + "->" + geburah.getHealth()
                            + ", animation=" + attacker.getAnimation());
                }
                if (index == 1) {
                    float heroHealth = attacker.getHealth();
                    attacker.hurt(DamageHelper.getDamage(geburah, "red"), 1.0F);
                    if (attacker.getHealth() >= heroHealth || attacker.hasEscape()) {
                        failures.add("HERO rejected Geburah damage or changed its escape identity");
                    }
                }
            }
        }
        helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "v027_combat")
    public static void busFuneralAndWolfActuallyHitEscapedCensored(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        List<EntityType<? extends AbstractAbnormality>> types = List.of(
                ModEntities.thorn_bus.get(), ModEntities.butterfly_funeral.get(),
                ModEntities.bigbadwolf.get());
        int[] earliestHits = {43, 24, 9};
        int[] windows = {80, 100, 100};
        for (int index = 0; index < types.size(); index++) {
            try (CombatFixture fixture = new CombatFixture(helper)) {
                AbstractAbnormality attacker = fixture.spawn(types.get(index), 0.0D);
                attacker.triggerEscape();
                EntityCensored censored = fixture.spawn(ModEntities.censored.get(), 2.0D);
                censored.triggerEscape();
                helper.assertTrue(attacker.hasEscape() && censored.hasEscape(),
                        "CENSORED attack pair did not escape");
                float initialHealth = censored.getHealth();
                int firstDamageTick = -1;
                for (int tick = 1; tick <= windows[index]; tick++) {
                    fixture.tick(attacker, 0.0D);
                    if (censored.getHealth() < initialHealth) {
                        firstDamageTick = tick;
                        break;
                    }
                }
                // No setTarget/doHurtTarget injection: acquisition and the hit filter both run.
                if (firstDamageTick < earliestHits[index]) {
                    failures.add(attacker.getAbnormalityCode() + ": firstDamageTick="
                            + firstDamageTick + ", expected " + earliestHits[index] + ".." + windows[index]
                            + ", baseTarget=" + (attacker.getTarget() == censored)
                            + ", animation=" + attacker.getAnimation());
                }
            }
        }
        helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "v027_combat")
    public static void nothingPhaseThreeUsesResistanceWithoutLosingGeburahOverride(GameTestHelper helper) {
        boolean override = GeburahDamageTuning.abnormalityOverrideEnabled();
        double multiplier = (double) field(GeburahDamageTuning.class, null, "abnormalityDamageMultiplier");
        double resistance = (double) field(GeburahDamageTuning.class, null, "abnormalityAssumedRedResistance");
        List<String> failures = new ArrayList<>();
        try (CombatFixture fixture = new CombatFixture(helper)) {
            GeburahDamageTuning.resetAbnormalityOverride();
            EntityNothingThere nothing = fixture.spawn(ModEntities.nothing_there.get(), 0.0D);
            nothing.triggerEscape();
            helper.assertTrue(Math.abs(nothing.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get())
                            - 0.3D) < 0.0001D, "phase one red resistance changed");
            nothing.transitionToPhase2();
            helper.assertTrue(Math.abs(nothing.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get())
                            - 0.3D) < 0.0001D, "phase two red resistance changed");
            nothing.transitionToPhase3();
            helper.assertTrue(nothing.getCurrentPhase() == 2, "phase three fixture could not transform");
            nothing.setNoAi(true);
            EntityFragmentOfUniverse ordinary = fixture.spawn(ModEntities.fragment_of_the_universe.get(), 5.0D);
            EntityGeburah geburah = fixture.spawn(ModEntities.geburah.get(), 8.0D);
            float[] ordinaryEventAmount = {Float.NaN};
            float[] geburahEventAmount = {Float.NaN};
            Consumer<LivingHurtEvent> recorder = event -> {
                if (event.getEntity() != nothing || !DamageHelper.isRedDamage(event.getSource())) return;
                if (event.getSource().getEntity() == ordinary) ordinaryEventAmount[0] = event.getAmount();
                if (event.getSource().getEntity() == geburah) geburahEventAmount[0] = event.getAmount();
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, recorder);
            try {
                if (nothing.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get()) != 0.0D) {
                    failures.add("phase three RED attribute must be 0.0");
                }
                nothing.setHealth(nothing.getMaxHealth() - 200.0F);
                for (int tick = 0; tick < 299; tick++) fixture.tick(nothing, 0.0D);
                int lastDamage = (int) field(EntityNothingThere.class, nothing, "lastDamagedTick");
                float before = nothing.getHealth();
                EntityUtil.clearHurtTime(nothing);
                nothing.hurt(DamageHelper.getDamage(ordinary, "red"), 40.0F);
                if (ordinaryEventAmount[0] != 0.0F || nothing.getHealth() != before) {
                    failures.add("ordinary RED must reach LivingHurtEvent with zero damage: event="
                            + ordinaryEventAmount[0] + ", HP=" + before + "->" + nothing.getHealth());
                }
                if ((int) field(EntityNothingThere.class, nothing, "lastDamagedTick") != lastDamage) {
                    failures.add("zero RED damage refreshed lastDamagedTick");
                }
                fixture.tick(nothing, 0.0D);
                if (nothing.getHealth() <= before) failures.add("zero RED delayed the 300-tick regeneration");

                before = nothing.getHealth();
                EntityUtil.clearHurtTime(nothing);
                boolean accepted = nothing.hurt(DamageHelper.getDamage(geburah, "red"), 4.0F);
                float expected = (float) (4.0D * GeburahDamageTuning.DEFAULT_ABNORMALITY_DAMAGE_MULTIPLIER
                        * GeburahDamageTuning.DEFAULT_ABNORMALITY_ASSUMED_RED_RESISTANCE);
                if (!accepted || geburahEventAmount[0] != expected
                        || Math.abs(before - nothing.getHealth() - expected) > 0.001F) {
                    failures.add("default Geburah override did not pass the common RED pipeline: accepted="
                            + accepted + ", event=" + geburahEventAmount[0]
                            + ", HP=" + before + "->" + nothing.getHealth());
                }
                if ((int) field(EntityNothingThere.class, nothing, "lastDamagedTick") != nothing.tickCount) {
                    failures.add("effective Geburah damage did not refresh lastDamagedTick");
                }
                float afterHit = nothing.getHealth();
                for (int tick = 0; tick < 20; tick++) fixture.tick(nothing, 0.0D);
                if (nothing.getHealth() != afterHit) failures.add("effective damage failed to interrupt regeneration");

                // Simulate the phase-three attribute written by the previous version.
                nothing.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get()).setBaseValue(0.3D);
                CompoundTag legacy = new CompoundTag();
                nothing.addAdditionalSaveData(legacy);
                EntityNothingThere loaded = fixture.spawn(ModEntities.nothing_there.get(), 10.0D);
                loaded.readAdditionalSaveData(legacy);
                if (loaded.getCurrentPhase() != 2
                        || loaded.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get()) != 0.0D) {
                    failures.add("legacy phase-three save did not restore zero RED resistance");
                }
            } finally {
                MinecraftForge.EVENT_BUS.unregister(recorder);
            }
        } finally {
            GeburahDamageTuning.configureAbnormalityOverride(override, multiplier, resistance);
        }
        helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "v027_combat")
    public static void wolfEntersFogAfterThreeHundredActualDamageDespiteHealing(GameTestHelper helper) {
        try (CombatFixture fixture = new CombatFixture(helper)) {
            EntityBigBadWolf wolf = fixture.spawn(ModEntities.bigbadwolf.get(), 0.0D);
            EntityFragmentOfUniverse attacker = fixture.spawn(ModEntities.fragment_of_the_universe.get(), 5.0D);
            wolf.triggerEscape();
            helper.assertTrue(wolf.hasEscape() && !inFog(wolf), "first escape must howl, not enter fog");
            float initialHealth = wolf.getHealth();
            helper.assertFalse(wolf.hurt(wolf.damageSources().cactus(), 300.0F),
                    "environment damage unexpectedly reached the protected wolf");
            helper.assertFalse(inFog(wolf), "rejected damage contributed to the fog threshold");

            EntityUtil.clearHurtTime(wolf);
            wolf.hurt(DamageHelper.getDamage(attacker, "red"), 150.0F);
            helper.assertTrue(Math.abs(initialHealth - wolf.getHealth() - 150.0F) < 0.001F,
                    "first hit did not deal 150 actual health damage");
            helper.assertFalse(inFog(wolf), "150 damage triggered fog early");
            wolf.heal(150.0F);
            helper.assertTrue(wolf.getHealth() == initialHealth, "healing fixture did not restore the first hit");

            CompoundTag saved = new CompoundTag();
            wolf.addAdditionalSaveData(saved);
            wolf.discard();
            wolf = fixture.spawn(ModEntities.bigbadwolf.get(), 0.0D);
            wolf.readAdditionalSaveData(saved);
            helper.assertTrue(wolf.hasEscape() && !inFog(wolf), "reload changed the wolf combat state");

            EntityUtil.clearHurtTime(wolf);
            wolf.hurt(DamageHelper.getDamage(attacker, "red"), 149.0F);
            helper.assertTrue(Math.abs(initialHealth - wolf.getHealth() - 149.0F) < 0.001F,
                    "second hit did not deal 149 actual health damage");
            helper.assertFalse(inFog(wolf), "299 accumulated damage triggered fog early");
            EntityUtil.clearHurtTime(wolf);
            wolf.hurt(DamageHelper.getDamage(attacker, "red"), 1.0F);
            helper.assertTrue(Math.abs(initialHealth - wolf.getHealth() - 150.0F) < 0.001F,
                    "threshold hit did not deal one actual health damage");
            helper.assertTrue(inFog(wolf), "300 accumulated damage was cancelled by healing; net loss is only 150");
            wolf.stopEscape();
            helper.assertTrue(!wolf.hasEscape() && !inFog(wolf), "containment return did not clear fog");
            wolf.triggerEscape();
            EntityUtil.clearHurtTime(wolf);
            wolf.hurt(DamageHelper.getDamage(attacker, "red"), 299.0F);
            helper.assertFalse(inFog(wolf), "a new escape inherited the previous damage counter");
            wolf.stopEscape();
            helper.assertFalse(wolf.hasEscape(), "second containment return failed");
            saved = new CompoundTag();
            wolf.addAdditionalSaveData(saved);
            helper.assertTrue(saved.getFloat("DamageSinceLastFog") == 0.0F,
                    "stopEscape did not clear the partial damage counter used by day recovery");
        }
        try (CombatFixture fixture = new CombatFixture(helper)) {
            EntityBigBadWolf wolf = fixture.spawn(ModEntities.bigbadwolf.get(), 0.0D);
            EntityFragmentOfUniverse attacker = fixture.spawn(ModEntities.fragment_of_the_universe.get(), 5.0D);
            wolf.triggerEscape();
            float initialHealth = wolf.getHealth();
            wolf.setHealth(initialHealth - 149.0F);
            CompoundTag legacy = new CompoundTag();
            wolf.addAdditionalSaveData(legacy);
            legacy.remove("DamageSinceLastFog");
            legacy.putFloat("LastFogHealth", initialHealth);
            wolf.discard();
            wolf = fixture.spawn(ModEntities.bigbadwolf.get(), 0.0D);
            wolf.readAdditionalSaveData(legacy);
            wolf.heal(149.0F);
            EntityUtil.clearHurtTime(wolf);
            wolf.hurt(DamageHelper.getDamage(attacker, "red"), 150.0F);
            helper.assertFalse(inFog(wolf), "legacy migration triggered before 300 actual damage");
            EntityUtil.clearHurtTime(wolf);
            wolf.hurt(DamageHelper.getDamage(attacker, "red"), 1.0F);
            helper.assertTrue(inFog(wolf), "legacy LastFogHealth lower bound was lost after healing");
        }
        helper.succeed();
    }

    private static boolean inFog(EntityBigBadWolf wolf) {
        CompoundTag saved = new CompoundTag();
        wolf.addAdditionalSaveData(saved);
        return saved.getBoolean("InFog");
    }

    private static Object field(Class<?> type, Object owner, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not inspect " + type.getSimpleName() + "." + name, exception);
        }
    }

    /** One-way timelines with scoped clock advancement; no victim AI or persistent clock changes. */
    private static final class CombatFixture implements AutoCloseable {
        private final ServerLevel level;
        private final Vec3 origin;
        private final List<LivingEntity> entities = new ArrayList<>();
        private final Map<BlockPos, BlockState> floor = new HashMap<>();
        private final Map<MinecraftServer, Object> emergencyStates;
        private final Object previousEmergency;
        private final BlackForestSavedData previousForest;

        @SuppressWarnings("unchecked")
        private CombatFixture(GameTestHelper helper) {
            level = helper.getLevel();
            origin = helper.absoluteVec(new Vec3(10.5D, 1.0D, 6.5D));
            // Escape score intentionally outlives discard. Swap only this server's state,
            // then restore the original object instead of resetting another test's alarm.
            emergencyStates = (Map<MinecraftServer, Object>) field(
                    EscapeTracker.class, EscapeTracker.getInstance(), "states");
            previousEmergency = emergencyStates.remove(level.getServer());
            previousForest = BlackForestSavedData.get(level);
            level.getDataStorage().set("black_forest_event", new BlackForestSavedData());
            for (int x = -5; x <= 5; x++) {
                for (int z = -4; z <= 10; z++) {
                    BlockPos pos = BlockPos.containing(origin).offset(x, -1, z);
                    floor.put(pos, level.getBlockState(pos));
                    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                }
            }
        }

        private <T extends LivingEntity> T spawn(EntityType<T> type, double forward) {
            T entity = type.create(level);
            if (entity == null) throw new IllegalStateException("could not create " + type);
            entities.add(entity);
            entity.moveTo(origin.x, origin.y, origin.z + forward, 0.0F, 0.0F);
            entity.setNoGravity(true);
            entity.getRandom().setSeed(2707L);
            if (entity instanceof Mob mob) mob.setPersistenceRequired();
            if (!level.addFreshEntity(entity)) throw new IllegalStateException("could not add " + type);
            return entity;
        }

        private void tick(AbstractAbnormality attacker, double forward) {
            attacker.moveTo(origin.x, origin.y, origin.z + forward, 0.0F, 0.0F);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setOnGround(true);
            // ServerLevel.tickNonPassenger normally advances age before calling Entity.tick.
            attacker.setOldPosAndRot();
            attacker.tickCount++;
            MinecraftServer server = level.getServer();
            long gameTime = level.getGameTime();
            int serverTime = server.getTickCount();
            try {
                Field serverTick = MinecraftServer.class.getDeclaredField("tickCount");
                serverTick.setAccessible(true);
                try {
                    // MeleeAttackGoal retries use gameTime; Mob goal scheduling uses server tick parity.
                    // Entity age alone leaves a first null-target check throttled for the entire test.
                    level.serverLevelData.setGameTime(gameTime + attacker.tickCount);
                    serverTick.setInt(server, serverTime + attacker.tickCount);
                    attacker.tick();
                } finally {
                    level.serverLevelData.setGameTime(gameTime);
                    serverTick.setInt(server, serverTime);
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("could not advance the synchronous combat fixture clock", exception);
            }
        }

        @Override
        public void close() {
            List<Entity> followers = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof EntityLightFollower)) continue;
                CompoundTag saved = new CompoundTag();
                entity.saveWithoutId(saved);
                if (saved.hasUUID("OwnerUUID") && entities.stream()
                        .anyMatch(owner -> owner.getUUID().equals(saved.getUUID("OwnerUUID")))) {
                    followers.add(entity);
                }
            }
            for (LivingEntity entity : entities) {
                TimerEntry.shutdown(entity);
                entity.discard();
            }
            followers.forEach(Entity::discard);
            floor.forEach(level::setBlockAndUpdate);
            level.getDataStorage().set("black_forest_event", previousForest);
            if (previousEmergency == null) emergencyStates.remove(level.getServer());
            else emergencyStates.put(level.getServer(), previousEmergency);
        }
    }
}
