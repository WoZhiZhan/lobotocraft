package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeburahCounterPassiveTest {
    @Test
    void tracksEachResponsibleActorIndependentlyAndCountersOnThirdTrigger() {
        GeburahCounterPassive passive = new GeburahCounterPassive();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(GeburahCounterPassive.Result.IMMUNE, passive.record(first, 1));
        assertEquals(GeburahCounterPassive.Result.IMMUNE, passive.record(second, 2));
        assertEquals(GeburahCounterPassive.Result.IMMUNE, passive.record(first, 3));
        assertEquals(GeburahCounterPassive.Result.COUNTER, passive.record(first, 4));
        assertEquals(0, passive.count(first, 4));
        assertEquals(1, passive.count(second, 4));
    }

    @Test
    void countExpiresTenSecondsAfterTheLastTrigger() {
        GeburahCounterPassive passive = new GeburahCounterPassive();
        UUID responsible = UUID.randomUUID();

        passive.record(responsible, 10);
        passive.record(responsible, 100);

        assertEquals(2, passive.count(responsible, 299));
        assertEquals(0, passive.count(responsible, 300));
        assertEquals(GeburahCounterPassive.Result.IMMUNE, passive.record(responsible, 301));
    }
}
