package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("lobotocraft_geburah_visuals")
@PrefixGameTestTemplate(false)
public final class GeburahSpecialVisualGameTests {
    private static final String T = "workcloseregressiongametests.navigation_empty";
    private static final String NS = "lobotocraft_geburah_visuals";

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "major_visual_cues")
    public static void allMajorSkillsKeepTheirExactDamageCuesAndCombatRandomStream(GameTestHelper h) throws Exception {
        for (GeburahMajorMove move : GeburahMajorMove.values()) {
            try (Fixture f = new Fixture(h)) {
                f.boss.debugSetPhase(move == GeburahMajorMove.BLOOD_MIST ? GeburahPhase.NORMAL : GeburahPhase.EGO);
                var hurtTicks = new ArrayList<Integer>();
                Consumer<LivingHurtEvent> listener = e -> {
                    if (e.getEntity() == f.target && e.getSource().getEntity() == f.boss)
                        hurtTicks.add((int) field(f.boss, "visualElapsed"));
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, listener);
                try {
                    h.assertTrue((boolean) invoke(f.boss, "startMajorAttack",
                            new Class[]{GeburahMajorMove.class, LivingEntity.class}, move, f.target), "major did not start");
                    f.boss.getRandom().setSeed(812);
                    var expected = net.minecraft.util.RandomSource.create(812);
                    for (var cue : move.cues()) expected.nextInt(cue.maxDamage() - cue.minDamage() + 1);
                    for (int tick = 0; tick < move.durationTicks(); tick++) {
                        int before = hurtTicks.size();
                        invoke(f.boss, "tickMajorAttack", new Class[0]);
                        if (hurtTicks.size() > before) {
                            h.assertTrue((boolean) field(f.boss, "visualConnected"),
                                    "successful cue lost its contact signal");
                            h.assertTrue(f.target.position().equals(field(f.boss, "visualContactPosition")),
                                    "contact point did not match the successfully hit target");
                        }
                    }
                    h.assertTrue(hurtTicks.equals(move.cues().stream().map(GeburahMajorMove.Cue::tick).toList()),
                            move + " damage cues changed: " + hurtTicks);
                    h.assertTrue(f.boss.getRandom().nextInt() == expected.nextInt(), "visual consumed combat RNG");
                    h.assertTrue(field(f.boss, "visualSkill") == null, "completed major kept its visual state");
                    h.assertTrue(((Set<?>) field(f.boss, "visualAudience")).isEmpty(), "finished cast kept its audience");
                } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
            }
        }
        h.succeed();
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "will_visual_cues")
    public static void willNormalAndEgoStillDealOneHitAndCancellationClearsWarning(GameTestHelper h) throws Exception {
        for (boolean ego : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(h, true)) {
                f.boss.debugSetPhase(ego ? GeburahPhase.EGO : GeburahPhase.NORMAL);
                var ticks = new ArrayList<Integer>();
                Consumer<LivingHurtEvent> listener = e -> {
                    if (e.getEntity() == f.target && e.getSource().getEntity() == f.boss)
                        ticks.add((int) field(f.boss, "visualElapsed"));
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, listener);
                try {
                    invoke(f.boss, "startWillOfBattle", new Class[]{LivingEntity.class, Vec3.class, boolean.class},
                            f.target, f.target.position().add(-1, 0, 0), ego);
                    h.assertTrue(GeburahSafeTeleport.findWillOfBattle(f.level, f.boss, f.target, ego).isPresent(),
                            "fixture lacks a legal facility landing");
                    f.boss.getRandom().setSeed(930);
                    var expected = net.minecraft.util.RandomSource.create(930); expected.nextInt(8);
                    for (int tick = 0; tick < 53; tick++) invoke(f.boss, "tickWillOfBattle", new Class[0]);
                    h.assertTrue(ticks.equals(List.of(19)), "will damage timing changed: " + ticks);
                    h.assertTrue(f.boss.getRandom().nextInt() == expected.nextInt(), "will visual consumed combat RNG");
                    h.assertTrue(field(f.boss, "visualSkill") == null, "will did not end its visual");
                    invoke(f.boss, "startMajorAttack", new Class[]{GeburahMajorMove.class, LivingEntity.class},
                            GeburahMajorMove.BODIES_EVERYWHERE, f.target);
                    invoke(f.boss, "cancelCombatActions", new Class[0]);
                    h.assertTrue(field(f.boss, "visualSkill") == null, "cancellation left a visual warning");
                } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
            }
        }
        h.succeed();
    }

    private static Object field(Object owner, String name) {
        try { var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        var method = owner.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(owner, args);
    }
    private static final class Fixture implements AutoCloseable {
        final EntityGeburah boss;
        final AbstractAbnormality target;
        final ServerLevel level;
        final Map<BlockPos, BlockState> oldBlocks = new LinkedHashMap<>();
        final List<ChunkPos> addedChunks = new ArrayList<>();
        Fixture(GameTestHelper h) {
            this(h, false);
        }
        Fixture(GameTestHelper h, boolean facility) {
            level = facility ? h.getLevel().getServer().getLevel(com.wzz.lobotocraft.init.ModDimensions.LOBOTO_KEY)
                    : h.getLevel();
            if (level == null) throw new IllegalStateException("facility dimension unavailable");
            BlockPos origin;
            if (facility) {
                var room = com.wzz.lobotocraft.world.data.FacilityWorldLayout.fixedAreas(level.dimension().location())
                        .stream().filter(a -> a.type() == com.wzz.lobotocraft.world.data.FacilityAreaType.REST_ROOM
                                && a.maxPos().getX() - a.minPos().getX() >= 24
                                && a.maxPos().getZ() - a.minPos().getZ() >= 24).findFirst().orElseThrow();
                origin = new BlockPos((room.minPos().getX() + room.maxPos().getX()) / 2,
                        room.minPos().getY() + 1, (room.minPos().getZ() + room.maxPos().getZ()) / 2);
                for (int cx = (origin.getX() - 12) >> 4; cx <= (origin.getX() + 12) >> 4; cx++)
                    for (int cz = (origin.getZ() - 12) >> 4; cz <= (origin.getZ() + 12) >> 4; cz++) {
                        var chunk = new ChunkPos(cx, cz);
                        if (!level.getForcedChunks().contains(chunk.toLong())) {
                            addedChunks.add(chunk); level.setChunkForced(cx, cz, true);
                        }
                    }
            } else origin = h.absolutePos(new BlockPos(15, 2, 15));
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                BlockPos pos = origin.offset(x, -1, z);
                oldBlocks.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                for (int up = 1; up <= 6; up++) {
                    BlockPos air = pos.above(up);
                    oldBlocks.put(air, level.getBlockState(air));
                    level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            boss = ModEntities.geburah.get().create(level);
            target = ModEntities.abandoned_murderer.get().create(level);
            if (boss == null || target == null) throw new IllegalStateException("real combat fixture unavailable");
            boss.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, -90, 0);
            target.moveTo(boss.getX() + 3, boss.getY(), boss.getZ(), 0, 0);
            boss.setNoAi(true); boss.setNoGravity(true); target.setNoAi(true); target.setNoGravity(true);
            target.setEscape(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000); target.setHealth(100_000);
            level.addFreshEntity(boss); level.addFreshEntity(target);
            h.assertTrue(target.canEscape() && target.isAbnormalityCombatTarget(), "invalid combat fixture");
        }
        @Override public void close() {
            boss.discard(); target.discard();
            oldBlocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
            addedChunks.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        }
    }
}
