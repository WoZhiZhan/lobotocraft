package com.lobotocraft.facility;

import com.lobotocraft.facility.data.*;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.entity.abnormality.EntityOneBad;
import com.wzz.lobotocraft.init.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;

import java.util.*;

/** Real bound-room, meltdown lifecycle and entity-synced outline regressions. */
@GameTestHolder("lobotocraft_meltdown_outline")
@PrefixGameTestTemplate(false)
public final class MeltdownOutlineGameTests {
    private static final String T = "workcloseregressiongametests.navigation_empty";
    private static final String NS = "lobotocraft_meltdown_outline";

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "meltdown_outline_live")
    public static void onlyTheMeltingBoundResidentGlowsRedThenRestores(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var neighbor = ModEntities.onebad.get().create(f.level);
            neighbor.moveTo(f.mob.position().add(1, 0, 0));
            neighbor.setNoAi(true); f.level.addFreshEntity(neighbor);
            try {
                h.assertFalse(f.mob.isCurrentlyGlowing(), "ordinary resident unexpectedly glows");
                f.start();
                f.mob.tick(); neighbor.tick();
                h.assertTrue(f.mob.isCurrentlyGlowing() && f.mob.getTeamColor() == 0xFF0000,
                        "active meltdown did not show red resident outline");
                h.assertFalse(f.mob.hasEffect(MobEffects.GLOWING) || f.mob.hasGlowingTag(),
                        "presentation took ownership of another glow source");
                h.assertFalse(neighbor.isCurrentlyGlowing(), "nearby unbound same-code entity glows");
                MeltdownLifecycle.resolve(f.data, f.mob.getUUID());
                f.mob.tick();
                h.assertFalse(f.mob.isCurrentlyGlowing(), "resolved meltdown kept resident glowing");
                h.succeed();
            } finally { neighbor.discard(); }
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "meltdown_outline_escape")
    public static void escapingOrLeavingBoundRoomCannotKeepRedOutline(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.start(); f.mob.tick();
            h.assertTrue(f.mob.getTeamColor() == 0xFF0000, "fixture did not start red");
            f.mob.setEscape(true);
            h.assertTrue(f.mob.isCurrentlyGlowing() && f.mob.getTeamColor() == 0xFFFF00,
                    "escape retained red instead of existing escape yellow");
            f.mob.setEscape(false);
            f.mob.moveTo(f.mob.position().add(30, 0, 0)); f.mob.tick();
            h.assertFalse(f.mob.isCurrentlyGlowing(), "out-of-room nonescaped entity kept meltdown outline");
            h.succeed();
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "meltdown_outline_owned")
    public static void clearingMeltdownPreservesVanillaGlowAndExistingTeamColor(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var board = f.level.getScoreboard();
            String teamName = "glow" + f.mob.getUUID().toString().substring(0, 8);
            var team = board.addPlayerTeam(teamName); team.setColor(ChatFormatting.BLUE);
            board.addPlayerToTeam(f.mob.getScoreboardName(), team);
            f.mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000));
            f.mob.setGlowingTag(true);
            try {
                f.start(); f.mob.tick();
                h.assertTrue(f.mob.getTeamColor() == 0xFF0000, "meltdown does not override visible outline color");
                MeltdownLifecycle.clear(f.data, MeltdownEndReason.DAY_CHANGED);
                f.mob.tick();
                h.assertTrue(f.mob.isCurrentlyGlowing() && f.mob.hasEffect(MobEffects.GLOWING),
                        "ending meltdown erased independent glowing effect");
                h.assertTrue(f.mob.getTeamColor() == ChatFormatting.BLUE.getColor() && f.mob.getTeam() == team,
                        "ending meltdown lost prior team/color");
                h.succeed();
            } finally { board.removePlayerTeam(team); }
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "meltdown_outline_reload")
    public static void syncedResidentAndReloadDeriveOutlineWithoutPermanentGlowTag(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.start(); f.mob.tick();
            var values = f.mob.getEntityData().getNonDefaultValues();
            var replica = ModEntities.onebad.get().create(f.level);
            if (values != null) replica.getEntityData().assignValues(values);
            h.assertTrue(replica.isCurrentlyGlowing() && replica.getTeamColor() == 0xFF0000,
                    "entity sync did not carry the resident outline to a replica without home data");
            CompoundTag saved = new CompoundTag(); f.mob.saveWithoutId(saved);
            var loaded = ModEntities.onebad.get().create(f.level);
            loaded.load(saved); loaded.tick();
            h.assertTrue(loaded.isCurrentlyGlowing() && loaded.getTeamColor() == 0xFF0000,
                    "active meltdown resident lost outline on reload");
            MeltdownLifecycle.end(f.data, f.mob.getUUID(), MeltdownEndReason.EXPIRED);
            loaded.tick(); f.mob.tick();
            h.assertFalse(loaded.isCurrentlyGlowing() || f.mob.isCurrentlyGlowing(),
                    "expired outline persisted after reload");
            replica.discard(); loaded.discard();
            h.succeed();
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "meltdown_outline_work")
    public static void realWorkStartClearsRedWhileFacilityPauseKeepsActiveOutline(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var worker = net.minecraftforge.common.util.FakePlayerFactory.get(f.level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "outline_worker"));
            worker.absMoveTo(161.5, 270, 30.5, 0, 0);
            worker.getCapability(com.wzz.lobotocraft.capability.EmployeeStatsProvider.EMPLOYEE_STATS)
                    .ifPresent(s -> {
                        s.setFortitude(100); s.setPrudence(100); s.setTemperance(100); s.setJustice(100);
                    });
            boolean paused = f.data.isPaused();
            try {
                f.start(); f.mob.tick();
                f.data.setPaused(true);
                FacilityManager.tick(f.level.getServer()); f.mob.tick();
                h.assertTrue(f.mob.isCurrentlyGlowing() && f.mob.getTeamColor() == 0xFF0000,
                        "paused active meltdown lost red outline");
                f.data.setPaused(false);
                h.assertTrue(com.wzz.lobotocraft.work.WorkManager.startWork(worker, f.mob,
                        com.wzz.lobotocraft.work.WorkType.INSTINCT), "real work start rejected");
                h.assertFalse(f.data.getActiveMeltdowns().containsKey(f.mob.getUUID()),
                        "real work start did not resolve meltdown");
                f.mob.tick();
                h.assertFalse(f.mob.isCurrentlyGlowing(), "work-start resolution left red outline");
                h.succeed();
            } finally {
                f.data.setPaused(paused);
                com.wzz.lobotocraft.work.WorkManager.cancelWork(worker, "outline fixture cleanup");
            }
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "meltdown_outline_binding")
    public static void removingRoomMarkerClearsOutlineDespiteActiveRecord(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.start(); f.mob.tick();
            h.assertTrue(f.mob.isCurrentlyGlowing(), "fixture did not glow");
            f.level.setBlockAndUpdate(f.marker, Blocks.AIR.defaultBlockState());
            f.mob.tick();
            h.assertTrue(f.data.getActiveMeltdowns().containsKey(f.mob.getUUID()),
                    "fixture lost the meltdown record instead of removing room qualification");
            h.assertFalse(f.mob.isCurrentlyGlowing(), "lost room binding kept entity glowing");
            h.succeed();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final FacilityDayData data;
        final EntityOneBad mob;
        final Map<UUID, FacilityDayData.ActiveMeltdown> previous;
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final BlockPos marker = new BlockPos(160, 270, 30);
        Fixture(GameTestHelper h) {
            level = h.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            data = FacilityDayData.get(level);
            previous = new HashMap<>(data.getActiveMeltdowns());
            data.getActiveMeltdowns().clear();
            level.getChunkAt(marker);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                for (int dy = -1; dy <= 4; dy++) {
                    var pos = marker.offset(dx, dy, dz);
                    blocks.put(pos, level.getBlockState(pos));
                    level.setBlockAndUpdate(pos, dy == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            level.setBlockAndUpdate(marker, ModBlocks.ROOM_MARKER.get().defaultBlockState());
            mob = ModEntities.onebad.get().create(level);
            mob.moveTo(162.5, 270, 30.5, 0, 0); mob.setNoAi(true); level.addFreshEntity(mob);
            if (!(level.getBlockEntity(marker) instanceof RoomMarkerBlockEntity room)
                    || !room.bindByUuid(level, mob.getUUID())) throw new IllegalStateException("real room binding rejected");
        }
        void start() {
            if (!MeltdownLifecycle.start(data, mob.getUUID(), level.getServer().overworld().getGameTime(),
                    2, mob.getAbnormalityCode(), MeltdownCause.QLIPHOTH_OVERLOAD))
                throw new IllegalStateException("meltdown start rejected");
        }
        @Override public void close() {
            mob.discard(); data.getActiveMeltdowns().clear(); data.getActiveMeltdowns().putAll(previous); data.setDirty();
            blocks.forEach(level::setBlockAndUpdate);
        }
    }
}
