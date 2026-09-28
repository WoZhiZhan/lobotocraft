package com.wzz.lobotocraft.client.room;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomMapViewportTest {

    private static final RoomMapViewport.Bounds VIEWPORT =
            new RoomMapViewport.Bounds(0.0D, 0.0D, 200.0D, 100.0D);

    @Test
    void keepsSmallContentCenteredWhenNoPanIsRequested() {
        RoomMapViewport.Pan pan = RoomMapViewport.clampPan(
                new RoomMapViewport.Bounds(-20.0D, -10.0D, 20.0D, 10.0D),
                VIEWPORT, projection(1.0D), new RoomMapViewport.Pan(0.0D, 0.0D), 2.0D);

        assertEquals(0.0D, pan.x(), 0.0001D);
        assertEquals(0.0D, pan.y(), 0.0001D);
    }

    @Test
    void preservesRequestedPanWhenSmallContentStillFitsInsideTheViewport() {
        RoomMapViewport.Pan requested = new RoomMapViewport.Pan(60.0D, -25.0D);
        RoomMapViewport.Pan pan = RoomMapViewport.clampPan(
                new RoomMapViewport.Bounds(-20.0D, -10.0D, 20.0D, 10.0D),
                VIEWPORT, projection(1.0D), requested, 2.0D);

        assertEquals(requested, pan);
    }

    @Test
    void allowsAViewportOfTravelInBothDirectionsWhenZoomedIntoLargeContent() {
        RoomMapViewport.Bounds content =
                new RoomMapViewport.Bounds(-200.0D, -100.0D, 200.0D, 100.0D);

        RoomMapViewport.Pan positive = RoomMapViewport.clampPan(content, VIEWPORT,
                projection(1.0D), new RoomMapViewport.Pan(200.0D, 100.0D), 2.0D);
        RoomMapViewport.Pan negative = RoomMapViewport.clampPan(content, VIEWPORT,
                projection(1.0D), new RoomMapViewport.Pan(-200.0D, -100.0D), 2.0D);

        assertEquals(200.0D, positive.x(), 0.0001D);
        assertEquals(100.0D, positive.y(), 0.0001D);
        assertEquals(-200.0D, negative.x(), 0.0001D);
        assertEquals(-100.0D, negative.y(), 0.0001D);
    }

    @Test
    void extremeDragsUseAWideButBoundedRangeWithTheLooseMonitorMargin() {
        RoomMapViewport.Bounds content =
                new RoomMapViewport.Bounds(-200.0D, -100.0D, 200.0D, 100.0D);

        RoomMapViewport.Pan positive = RoomMapViewport.clampPan(content, VIEWPORT,
                projection(1.0D), new RoomMapViewport.Pan(1_000_000.0D, 1_000_000.0D), 2.0D);
        RoomMapViewport.Pan negative = RoomMapViewport.clampPan(content, VIEWPORT,
                projection(1.0D), new RoomMapViewport.Pan(-1_000_000.0D, -1_000_000.0D), 2.0D);

        assertTrue(positive.x() >= VIEWPORT.width());
        assertTrue(positive.y() >= VIEWPORT.height());
        assertTrue(negative.x() <= -VIEWPORT.width());
        assertTrue(negative.y() <= -VIEWPORT.height());
        assertTrue(positive.x() < 1_000_000.0D && positive.y() < 1_000_000.0D);
        assertTrue(negative.x() > -1_000_000.0D && negative.y() > -1_000_000.0D);
        assertEquals(positive, RoomMapViewport.clampPan(content, VIEWPORT,
                projection(1.0D), positive, 2.0D));
        assertEquals(negative, RoomMapViewport.clampPan(content, VIEWPORT,
                projection(1.0D), negative, 2.0D));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.5D, 0.75D, 1.0D})
    void overviewRoomCanBeDraggedClearOfTheFixedLowerLeftMinimap(double zoom) {
        RoomMapViewport.Bounds viewport = new RoomMapViewport.Bounds(0, 0, 400, 240);
        // The monitor's unselected minimap is 132 pixels square, inset 8 pixels.
        RoomMapViewport.Bounds minimap = new RoomMapViewport.Bounds(8, 100, 140, 232);
        RoomMapViewport.Bounds content = new RoomMapViewport.Bounds(-180, -100, 180, 100);
        RoomMapViewport.Bounds room = new RoomMapViewport.Bounds(-165, 65, -135, 90);
        RoomMapViewport.Projection projection = new RoomMapViewport.Projection(0, 0, 200, 120, zoom);
        RoomMapViewport.Bounds covered = screenBounds(room, projection, new RoomMapViewport.Pan(0, 0));
        assertTrue(covered.minX() >= minimap.minX() && covered.maxX() <= minimap.maxX());
        assertTrue(covered.minY() >= minimap.minY() && covered.maxY() <= minimap.maxY());

        RoomMapViewport.Pan pan = RoomMapViewport.clampPan(content, viewport, projection,
                new RoomMapViewport.Pan(144, 0), 2.0D);
        RoomMapViewport.Bounds moved = screenBounds(room, projection, pan);

        assertTrue(moved.minX() > minimap.maxX(),
                "The whole room must clear the minimap so its input priority can remain unchanged");
        assertTrue(moved.maxX() < viewport.maxX());
        assertTrue(moved.minY() > viewport.minY() && moved.maxY() < viewport.maxY());
        assertEquals(pan, RoomMapViewport.clampPan(content, viewport, projection, pan, 2.0D),
                "A later viewport clamp must not snap the room back underneath the minimap");
    }

    @Test
    void reportsTheWorldRectangleCurrentlyShownByTheMainMap() {
        RoomMapViewport.Bounds visible = RoomMapViewport.visibleWorldBounds(
                VIEWPORT, projection(2.0D), new RoomMapViewport.Pan(20.0D, -10.0D));

        assertEquals(-60.0D, visible.minX(), 0.0001D);
        assertEquals(-20.0D, visible.minY(), 0.0001D);
        assertEquals(40.0D, visible.maxX(), 0.0001D);
        assertEquals(30.0D, visible.maxY(), 0.0001D);
    }

    private static RoomMapViewport.Projection projection(double zoom) {
        return new RoomMapViewport.Projection(0.0D, 0.0D, 100.0D, 50.0D, zoom);
    }

    private static RoomMapViewport.Bounds screenBounds(RoomMapViewport.Bounds bounds,
                                                       RoomMapViewport.Projection projection,
                                                       RoomMapViewport.Pan pan) {
        return new RoomMapViewport.Bounds(
                (bounds.minX() - projection.centerWorldX()) * projection.zoom()
                        + projection.screenCenterX() + pan.x(),
                (bounds.minY() - projection.centerWorldY()) * projection.zoom()
                        + projection.screenCenterY() + pan.y(),
                (bounds.maxX() - projection.centerWorldX()) * projection.zoom()
                        + projection.screenCenterX() + pan.x(),
                (bounds.maxY() - projection.centerWorldY()) * projection.zoom()
                        + projection.screenCenterY() + pan.y());
    }
}
