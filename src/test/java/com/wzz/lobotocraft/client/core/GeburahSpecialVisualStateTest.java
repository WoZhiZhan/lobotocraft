package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.geburah.*;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahSpecialVisuals.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class GeburahSpecialVisualStateTest {
    private static final UUID CASTER = UUID.randomUUID();
    private Snapshot s(long cast, Skill skill, Event event, int elapsed, int cue) {
        return new Snapshot(CASTER, 5, cast, skill, event, elapsed, cue, event == Event.CUE,
                Vec3.ZERO, new Vec3(0, 0, 3), new Vec3(0, 0, 1));
    }

    @Test
    void stopAndTimeoutCannotBeResurrectedByLatePackets() {
        var state = new GeburahSpecialVisualState();
        state.accept(s(1, Skill.BODIES, Event.START, 0, -1), 100);
        state.accept(s(1, Skill.BODIES, Event.STOP, 12, -1), 112);
        state.accept(s(1, Skill.BODIES, Event.SYNC, 8, -1), 113);
        assertTrue(state.frames(113).isEmpty());
        state.accept(s(2, Skill.BODIES, Event.START, 0, -1), 114);
        assertEquals(1, state.frames(114).size());
        assertTrue(state.frames(200).isEmpty());
        state.accept(s(2, Skill.BODIES, Event.SYNC, 40, -1), 201);
        assertTrue(state.frames(201).isEmpty());
    }

    @Test
    void duplicateCuesAndOldCastCannotProduceMoreAccentsOrRewindTime() {
        var state = new GeburahSpecialVisualState();
        state.accept(s(2, Skill.EGO_BLOOD, Event.START, 0, -1), 0);
        var cue = s(2, Skill.EGO_BLOOD, Event.CUE, 39, 1);
        state.accept(cue, 39); state.accept(cue, 40);
        assertEquals(1, state.accents(40).size());
        state.accept(s(1, Skill.BODIES, Event.START, 0, -1), 40);
        assertEquals(Skill.EGO_BLOOD, state.frames(40).get(0).snapshot().skill());
        state.accept(s(2, Skill.EGO_BLOOD, Event.SYNC, 32, -1), 40);
        assertEquals(40, state.frames(40).get(0).elapsed(), "duplicate cue must not rewind its clock");
        state.clearAccents();
        assertTrue(state.accents(40).isEmpty());
        assertEquals(1, state.frames(40).size(), "switching style must retain live attack for legacy replay");
    }

    @Test
    void activityIsBoundedAndClearRemovesAllWorldState() {
        var state = new GeburahSpecialVisualState();
        for (int i = 0; i < 30; i++) state.accept(new Snapshot(new UUID(0, i), i, i + 1,
                Skill.BODIES, Event.START, 0, -1, false, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, 0, 1)), 0);
        assertEquals(GeburahSpecialVisuals.MAX_CASTS, state.frames(1).size());
        state.clear();
        assertTrue(state.frames(1).isEmpty());
        assertTrue(state.accents(1).isEmpty());
    }

    @Test
    void sharedAreaAndBladeWindowsPreserveProductionTiming() {
        assertEquals(12, GeburahSpecialVisuals.BODIES_LENGTH);
        assertEquals(15, GeburahSpecialVisuals.BODIES_WIDTH);
        assertEquals(8, GeburahSpecialVisuals.BLOOD_LENGTH);
        assertEquals(3, GeburahSpecialVisuals.BLOOD_WIDTH);
        assertEquals(46, GeburahMajorMove.BODIES_EVERYWHERE.cues().get(0).tick());
        assertFalse(GeburahSpecialVisuals.bladeWindow(Skill.BODIES, 10));
        assertTrue(GeburahSpecialVisuals.bladeWindow(Skill.BODIES, 46));
        assertEquals(0, GeburahSpecialVisuals.qualitySegments(0, 65 * 65));
        assertEquals(6, GeburahSpecialVisuals.qualitySegments(2, 1));
        assertFalse(GeburahSpecialVisuals.bladeQuality(2, 1));
        assertFalse(GeburahSpecialVisuals.bladeQuality(0, 65 * 65));
        assertTrue(GeburahSpecialVisuals.bladeQuality(1, 1));
        assertTrue(GeburahSpecialVisuals.deliver(Event.STOP, false, true));
        assertFalse(GeburahSpecialVisuals.deliver(Event.STOP, true, false));
        assertFalse(GeburahSpecialVisuals.deliver(Event.SYNC, false, true));
    }

    @Test
    void switchingBetweenVisibleWeaponsStartsANewBladeSpan() {
        GeburahBladeTrailState.clear();
        try {
            var points = List.of(Vec3.ZERO, new Vec3(0, 0, 2));
            GeburahBladeTrailState.capture(5, 10, points, true, GeburahMove.SLASH, false, 1);
            GeburahBladeTrailState.capture(5, 10.5, points, true, GeburahMove.SLASH, false, 2);
            assertEquals(1, GeburahBladeTrailState.recentTrail(10.5, 6).size());
            assertEquals(2, GeburahBladeTrailProfile.specialCandidates("EGObloodmist").size());
            assertEquals(List.of("nitai2_start", "nitai2_end"),
                    GeburahBladeTrailProfile.specialCandidates("bodieseverywhere").get(0).locatorBoneNames());
        } finally { GeburahBladeTrailState.clear(); }
    }
}
