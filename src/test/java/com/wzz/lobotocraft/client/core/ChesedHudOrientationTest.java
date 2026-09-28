package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.chesed.ChesedDamageKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChesedHudOrientationTest {
    @Test
    void whiteBadgeUsesLeftFacingApex() {
        assertEquals(CoreSuppressionOverlay.TriangleDirection.LEFT,
                CoreSuppressionOverlay.directionFor(ChesedDamageKind.WHITE));
    }

    @Test
    void verticalBadgesNarrowTowardTheirOriginalApex() {
        int downTop = CoreSuppressionOverlay.triangleHalfWidth(
                CoreSuppressionOverlay.TriangleDirection.DOWN, 0, 20, 20);
        int downBottom = CoreSuppressionOverlay.triangleHalfWidth(
                CoreSuppressionOverlay.TriangleDirection.DOWN, 19, 20, 20);
        int upTop = CoreSuppressionOverlay.triangleHalfWidth(
                CoreSuppressionOverlay.TriangleDirection.UP, 0, 20, 20);
        int upBottom = CoreSuppressionOverlay.triangleHalfWidth(
                CoreSuppressionOverlay.TriangleDirection.UP, 19, 20, 20);

        assertTrue(downTop > downBottom, "down triangle must narrow toward the bottom");
        assertTrue(upTop < upBottom, "up triangle must widen toward the bottom");
    }

    @Test
    void whiteBadgeNarrowsTowardTheLeftApex() {
        int leftStart = CoreSuppressionOverlay.triangleHalfHeight(
                CoreSuppressionOverlay.TriangleDirection.LEFT, 0, 20, 20);
        int leftEnd = CoreSuppressionOverlay.triangleHalfHeight(
                CoreSuppressionOverlay.TriangleDirection.LEFT, 19, 20, 20);
        assertTrue(leftStart < leftEnd, "left triangle must narrow toward the left");
    }
}
