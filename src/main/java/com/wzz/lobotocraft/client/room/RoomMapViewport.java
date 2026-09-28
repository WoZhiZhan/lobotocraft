package com.wzz.lobotocraft.client.room;

/** Pure viewport math shared by the facility map camera and its overview window. */
public final class RoomMapViewport {

    private RoomMapViewport() {
    }

    public static Pan clampPan(Bounds content, Bounds viewport, Projection projection,
                               Pan requested, double padding) {
        double safePadding = Math.max(0.0D, Math.min(padding,
                Math.min(viewport.width(), viewport.height()) * 0.5D));
        double x = clampAxis(content.minX(), content.maxX(), viewport.minX(), viewport.maxX(),
                projection.centerWorldX(), projection.screenCenterX(), projection.zoom(),
                requested.x(), safePadding);
        double y = clampAxis(content.minY(), content.maxY(), viewport.minY(), viewport.maxY(),
                projection.centerWorldY(), projection.screenCenterY(), projection.zoom(),
                requested.y(), safePadding);
        return new Pan(x, y);
    }

    public static Bounds visibleWorldBounds(Bounds viewport, Projection projection, Pan pan) {
        double minX = screenToWorld(viewport.minX(), projection.centerWorldX(),
                projection.screenCenterX(), projection.zoom(), pan.x());
        double maxX = screenToWorld(viewport.maxX(), projection.centerWorldX(),
                projection.screenCenterX(), projection.zoom(), pan.x());
        double minY = screenToWorld(viewport.minY(), projection.centerWorldY(),
                projection.screenCenterY(), projection.zoom(), pan.y());
        double maxY = screenToWorld(viewport.maxY(), projection.centerWorldY(),
                projection.screenCenterY(), projection.zoom(), pan.y());
        return new Bounds(Math.min(minX, maxX), Math.min(minY, maxY),
                Math.max(minX, maxX), Math.max(minY, maxY));
    }

    private static double clampAxis(double contentMin, double contentMax,
                                    double viewportMin, double viewportMax,
                                    double centerWorld, double screenCenter, double zoom,
                                    double requestedPan, double padding) {
        double baseMin = (contentMin - centerWorld) * zoom + screenCenter;
        double baseMax = (contentMax - centerWorld) * zoom + screenCenter;
        // Permit every part of the map to cross the viewport, even at overview zoom.
        // Keep a small strip reachable at either extreme rather than locking small maps in place.
        double overlap = Math.min(padding, (baseMax - baseMin) * 0.5D);
        double minimumPan = viewportMin + overlap - baseMax;
        double maximumPan = viewportMax - overlap - baseMin;
        return Math.max(minimumPan, Math.min(maximumPan, requestedPan));
    }

    private static double screenToWorld(double screen, double centerWorld,
                                        double screenCenter, double zoom, double pan) {
        return (screen - screenCenter - pan) / zoom + centerWorld;
    }

    public record Bounds(double minX, double minY, double maxX, double maxY) {
        public Bounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minY)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                    || maxX <= minX || maxY <= minY) {
                throw new IllegalArgumentException("Viewport bounds must be finite and positive");
            }
        }

        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }
    }

    public record Projection(double centerWorldX, double centerWorldY,
                             double screenCenterX, double screenCenterY, double zoom) {
        public Projection {
            if (!Double.isFinite(centerWorldX) || !Double.isFinite(centerWorldY)
                    || !Double.isFinite(screenCenterX) || !Double.isFinite(screenCenterY)
                    || !Double.isFinite(zoom) || zoom <= 0.0D) {
                throw new IllegalArgumentException("Viewport projection must be finite with positive zoom");
            }
        }
    }

    public record Pan(double x, double y) {
        public Pan {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Viewport pan must be finite");
            }
        }
    }
}
