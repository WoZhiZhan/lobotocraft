package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.IEmployeeStats;
import com.wzz.lobotocraft.employee.*;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.event.definition.work.WorkPreExtractionEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.network.packet.OpenWorkScreenPacket;
import com.wzz.lobotocraft.work.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;

import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("lobotocraft_work_grade")
@PrefixGameTestTemplate(false)
public final class WorkGradeRegressionGameTests {
    private static final String T = "workcloseregressiongametests.navigation_empty";
    private static final String NS = "lobotocraft_work_grade";
    private static final int[] VALUES = {20, 30, 45, 65, 85};

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 60, batch = "work_grade_extraction")
    public static void humanAndAiUseTheWorkedAttributeAtAllFiveGrades(GameTestHelper h) throws Exception {
        var target = spawn(h);
        List<String> failures = new ArrayList<>();
        try {
            for (boolean ai : new boolean[]{false, true}) {
                var p = player(h, ai);
                try {
                    for (var work : WorkType.values()) for (int grade = 0; grade < 5; grade++) {
                        int[] values = {85, 85, 85, 85};
                        values[work.ordinal()] = VALUES[grade];
                        stats(p, values);
                        h.assertTrue(cap(p).getEmployeeLevel() == 5, "overall V fixture changed");
                        var session = new WorkManager.WorkSession(p, target, work, false);
                        float expectedBase = target.getFullWorkPreferences()[work.ordinal()][grade];
                        float expected = Math.min(.95F, expectedBase + (values[2] - 20) * .001F);
                        float actual = extractedRate(session);
                        if (Math.abs(session.baseSuccessRate - expectedBase) > .00001F
                                || Math.abs(session.actualSuccessRate - expected) > .00001F
                                || Math.abs(actual - expected) > .00001F)
                            failures.add((ai ? "AI" : "human") + ":" + work + ":" + (grade + 1)
                                    + " actual=" + actual + " expected=" + expected);
                    }
                } finally { p.discard(); }
            }
            h.assertTrue(failures.isEmpty(), String.join("; ", failures));
            h.succeed();
        } finally { target.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 60, batch = "work_grade_scan")
    public static void registeredPreferenceTablesUseSingleAttributeRatherThanOverallV(GameTestHelper h) {
        var p = player(h, false);
        int entities = 0, checks = 0;
        List<String> failures = new ArrayList<>();
        try {
            for (var registration : ModEntities.ENTITIES.getEntries()) {
                Entity created = registration.get().create(h.getLevel());
                if (!(created instanceof AbstractAbnormality ab)) continue;
                try {
                    if (!ab.canBeContained() || ab.isToolType()) continue;
                    float[][] matrix = ab.getFullWorkPreferences();
                    if (matrix == null || matrix.length != 4) continue;
                    entities++;
                    for (int work = 0; work < 4; work++) {
                        if (matrix[work] == null || matrix[work].length < 5) continue;
                        for (int grade = 0; grade < 5; grade++) {
                            int[] values = {85, 85, 85, 85}; values[work] = VALUES[grade];
                            stats(p, values);
                            var s = new WorkManager.WorkSession(p, ab, WorkType.values()[work], false);
                            if (Math.abs(s.baseSuccessRate - matrix[work][grade]) > .00001F)
                                failures.add(ab.getAbnormalityCode() + ":" + work + ":" + (grade + 1));
                            checks++;
                        }
                    }
                } finally { ab.discard(); }
            }
            h.assertTrue(entities >= 34 && checks >= 680, "registered matrix scan incomplete");
            h.assertTrue(failures.isEmpty(), "wrong columns: " + failures);
            h.succeed();
        } finally { p.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "work_grade_packet")
    public static void workSelectionWirePreservesFourIndependentGrades(GameTestHelper h) {
        var target = spawn(h);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new OpenWorkScreenPacket(target.getId(), target.getFullWorkPreferences(), target.getRiskLevel(),
                    0, new WorkAttributeLevels(1, 2, 3, 4), new boolean[]{true, true, true, true}).toBytes(buffer);
            buffer.readInt(); buffer.readInt(); buffer.readInt();
            for (int i = 0; i < 20; i++) buffer.readFloat();
            buffer.readEnum(com.wzz.lobotocraft.entity.data.RiskLevel.class); buffer.readInt();
            for (int grade = 1; grade <= 4; grade++)
                h.assertTrue(buffer.readInt() == grade, "packet replaced worked attribute grade " + grade);
            h.succeed();
        } finally { buffer.release(); target.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "work_grade_low_total")
    public static void highSingleAttributeStillUsesVWithOverallII(GameTestHelper h) throws Exception {
        var target = spawn(h);
        try {
            for (boolean ai : new boolean[]{false, true}) {
                var p = player(h, ai);
                try {
                    for (var work : WorkType.values()) {
                        int[] values = {20, 20, 20, 20}; values[work.ordinal()] = 85;
                        stats(p, values);
                        h.assertTrue(cap(p).getEmployeeLevel() == 2, "overall II fixture changed");
                        var session = new WorkManager.WorkSession(p, target, work, false);
                        float expected = target.getFullWorkPreferences()[work.ordinal()][4];
                        h.assertTrue(Math.abs(session.baseSuccessRate - expected) < .00001F,
                                "overall II replaced worked attribute V for " + work);
                        float finalRate = Math.min(.95F, expected + (values[2] - 20) * .001F);
                        h.assertTrue(Math.abs(extractedRate(session) - finalRate) < .00001F,
                                "pre-roll replaced worked attribute V for " + work);
                    }
                } finally { p.discard(); }
            }
            h.succeed();
        } finally { target.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "work_grade_resolved_type")
    public static void scrambledWorkUsesResolvedTypeAttributeNotRequestedType(GameTestHelper h) {
        var target = spawn(h);
        var p = player(h, false);
        try {
            stats(p, new int[]{20, 85, 20, 85});
            var s = new WorkManager.WorkSession(p, target, WorkType.INSTINCT, WorkType.INSIGHT, false);
            h.assertTrue(s.requestedWorkType == WorkType.INSTINCT && s.workType == WorkType.INSIGHT,
                    "scrambled session fixture changed");
            h.assertTrue(Math.abs(s.baseSuccessRate - target.getFullWorkPreferences()[1][4]) < .00001F,
                    "resolved insight failed to use prudence V");
            h.succeed();
        } finally { p.discard(); target.discard(); }
    }

    @SuppressWarnings("unchecked")
    private static float extractedRate(WorkManager.WorkSession s) throws Exception {
        var field = WorkManager.class.getDeclaredField("activeWorks"); field.setAccessible(true);
        var active = (Map<UUID, WorkManager.WorkSession>) field.get(null);
        float[] observed = {Float.NaN};
        Consumer<WorkPreExtractionEvent> listener = event -> {
            if (event.getSession() == s) { observed[0] = event.getSuccessRate(); event.setCanceled(true); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, WorkPreExtractionEvent.class, listener);
        active.put(s.player.getUUID(), s);
        try {
            var method = WorkManager.class.getDeclaredMethod("performExtraction", WorkManager.WorkSession.class);
            method.setAccessible(true); method.invoke(null, s);
            if (!Float.isFinite(observed[0])) throw new IllegalStateException("no extraction event");
            return observed[0];
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); active.remove(s.player.getUUID()); }
    }

    private static IEmployeeStats cap(ServerPlayer p) {
        return p.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .orElseThrow(() -> new IllegalStateException("missing stats"));
    }
    private static void stats(ServerPlayer p, int[] v) {
        var s = cap(p); s.setFortitude(v[0]); s.setPrudence(v[1]); s.setTemperance(v[2]); s.setJustice(v[3]);
    }
    private static EntityOneBad spawn(GameTestHelper h) {
        for (int x = 1; x < 12; x++) for (int z = 1; z < 11; z++)
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var target = ModEntities.onebad.get().create(h.getLevel());
        var pos = h.absolutePos(new BlockPos(4, 1, 5));
        target.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        target.setNoAi(true); h.getLevel().addFreshEntity(target); return target;
    }
    private static ServerPlayer player(GameTestHelper h, boolean ai) {
        UUID id = UUID.randomUUID(); var profile = new GameProfile(id, "grade_test");
        ServerPlayer p = ai ? new EmployeeServerPlayer(h.getLevel(), profile,
                new EmployeeRecord(id, "grade_test", EmployeeDepartment.CONTROL))
                : new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile);
        if (!ai) p.connection = new NoopListener(h.getLevel(), p);
        var pos = h.absolutePos(new BlockPos(7, 1, 5));
        p.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        h.getLevel().addNewPlayer(p); return p;
    }
    private static final class NoopListener extends ServerGamePacketListenerImpl {
        NoopListener(ServerLevel level, ServerPlayer p) {
            super(level.getServer(), new Connection(PacketFlow.CLIENTBOUND), p);
        }
        @Override public void send(Packet<?> packet) {}
        @Override public void send(Packet<?> packet, PacketSendListener listener) {}
    }
}
