package com.wzz.lobotocraft.client.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChesedHudLayoutTest {
    @Test
    void matchesTheOriginalCanvasLayoutAtTheReferenceScale() {
        ChesedHudLayout.Layout layout = ChesedHudLayout.calculate(960, 540);

        assertEquals(new ChesedHudLayout.Rect(456, 2, 48, 48), layout.red());
        assertEquals(new ChesedHudLayout.Rect(479, 26, 48, 48), layout.white());
        assertEquals(new ChesedHudLayout.Rect(456, 51, 48, 48), layout.black());
        assertEquals(new ChesedHudLayout.Rect(431, 27, 48, 48), layout.pale());
        assertEquals(new ChesedHudLayout.Rect(471, 41, 19, 18), layout.core());
    }
}
