package com.wzz.lobotocraft.client.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeburahStatusClientStateTest {
    @AfterEach
    void clear() {
        GeburahStatusClientState.clear();
    }

    @Test
    void bleedStacksAreBoundedAndExpireWithDuration() {
        GeburahStatusClientState.updateBleed(9, 3);

        assertEquals(5, GeburahStatusClientState.bleedStacks());
        for (int i = 0; i < 3; i++) GeburahStatusClientState.tick();
        assertEquals(0, GeburahStatusClientState.bleedStacks());
        assertEquals(0, GeburahStatusClientState.bleedTicks());
    }
}
