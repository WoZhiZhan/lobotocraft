package com.wzz.lobotocraft.client.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahMusicPolicyTest {
    @Test
    void egoTrackStartsAtThePhaseEdgeOnly() {
        GeburahMusicPolicy.Decision transition = GeburahMusicPolicy.decide(
                1, GeburahMusicPolicy.Track.FIRST, true);
        assertTrue(transition.shouldStart());
        assertEquals(GeburahMusicPolicy.Track.EGO, transition.track());

        assertFalse(GeburahMusicPolicy.decide(
                1, GeburahMusicPolicy.Track.EGO, true).shouldStart());
        assertFalse(GeburahMusicPolicy.decide(
                2, GeburahMusicPolicy.Track.EGO, true).shouldStart());
        assertFalse(GeburahMusicPolicy.decide(
                1, GeburahMusicPolicy.Track.EGO, true).shouldStart());
    }

    @Test
    void firstPhaseAdvancesItsTwoTrackQueueOnlyAfterARealStop() {
        assertFalse(GeburahMusicPolicy.decide(
                0, GeburahMusicPolicy.Track.FIRST, true).shouldStart());
        assertEquals(GeburahMusicPolicy.Track.SECOND, GeburahMusicPolicy.decide(
                0, GeburahMusicPolicy.Track.FIRST, false).track());
        assertEquals(GeburahMusicPolicy.Track.FIRST, GeburahMusicPolicy.decide(
                0, GeburahMusicPolicy.Track.SECOND, false).track());
    }
}
