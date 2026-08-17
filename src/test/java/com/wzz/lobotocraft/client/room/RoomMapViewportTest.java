package com.wzz.lobotocraft.client.room;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomMapViewportTest {

    private static final RoomMapViewport.Bounds VIEWPORT =
            new RoomMapViewport.Bounds(0.0D, 0.0D, 200.0D, 100.0D);

    @Test
    void centersContentThatIsSmallerThanTheViewport() {
        RoomMapViewport.Pan pan = RoomMapViewport.clampPan(
                new RoomMapViewport.Bounds(-20.0D, -10.0D, 20.0D, 10.0D),
                VIEWPORT, projection(1.0D), new RoomMapViewport.Pan(900.0D, -900.0D), 10.0D);

        assertEquals(0.0D, pan.x(), 0.0001D);
        assertEquals(0.0D, pan.y(), 0.0001D);
    }

    @Test
    void clampsEveryEdgeWhenZoomedIntoLargeContent() {
        RoomMapViewport.Bounds content =
                new RoomMapViewport.Bounds(-200.0D, -100.0D, 200.0D, 100.0D);

        RoomMapViewport.Pan positive = RoomMapViewport.clampPan(content, VIEWPORT,
                projection(1.0D), new RoomMapViewport.Pan(999.0D, 999.0D), 10.0D);
        RoomMapViewport.Pan negative = RoomMapViewport.clampPan(content, VIEWPORT,
                projection(1.0D), new RoomMapViewport.Pan(-999.0D, -999.0D), 10.0D);

        assertEquals(110.0D, positive.x(), 0.0001D);
        assertEquals(60.0D, positive.y(), 0.0001D);
        assertEquals(-110.0D, negative.x(), 0.0001D);
        assertEquals(-60.0D, negative.y(), 0.0001D);
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
}
