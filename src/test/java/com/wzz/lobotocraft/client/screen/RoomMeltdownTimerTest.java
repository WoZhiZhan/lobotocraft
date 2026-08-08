package com.wzz.lobotocraft.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RoomMeltdownTimerTest {
    @Test
    void countdownAdvancesBetweenFortyTickSnapshots() {
        assertEquals(800, RoomMapScreen.remainingMeltdownTicks(800, 0));
        assertEquals(780, RoomMapScreen.remainingMeltdownTicks(800, 1_000));
        assertEquals(760, RoomMapScreen.remainingMeltdownTicks(800, 2_000));
    }

    @Test
    void countdownNeverRunsBelowZeroOrBackwardsForClockSkew() {
        assertEquals(0, RoomMapScreen.remainingMeltdownTicks(10, 2_000));
        assertEquals(10, RoomMapScreen.remainingMeltdownTicks(10, -1_000));
    }
}
