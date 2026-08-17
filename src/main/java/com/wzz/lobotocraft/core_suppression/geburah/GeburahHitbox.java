package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GeburahHitbox {
    private static final double CONTACT_MARGIN = 0.2D;

    private final Profile profile;
    private final Vec3 origin;
    private final Vec3 forward;
    private final Vec3 side;

    private GeburahHitbox(Profile profile, Vec3 origin, Vec3 forward) {
        this.profile = profile;
        this.origin = origin;
        this.forward = horizontalDirection(forward);
        this.side = new Vec3(-this.forward.z, 0.0D, this.forward.x);
    }

    public static GeburahHitbox forMove(GeburahMove move, boolean ego,
                                        Vec3 origin, Vec3 forward) {
        return new GeburahHitbox(profile(move, ego), origin, forward);
    }

    public static GeburahHitbox forwardBox(Vec3 origin, Vec3 forward,
                                           double length, double width, double height) {
        if (!Double.isFinite(length) || !Double.isFinite(width) || !Double.isFinite(height)
                || length <= 0.0D || width <= 0.0D || height <= 0.0D) {
            throw new IllegalArgumentException("Forward box dimensions must be finite and positive");
        }
        return new GeburahHitbox(new Profile(Shape.BOX, length, width * 0.5D,
                0.0D, -0.35D, height), origin, forward);
    }

    static Profile profile(GeburahMove move, boolean ego) {
        return switch (move) {
            case SLASH -> new Profile(Shape.ARC, ego ? 6.1D : 5.5D, 0.0D,
                    ego ? 82.0D : 78.0D, -0.4D, ego ? 3.8D : 3.5D);
            case STAB -> new Profile(Shape.BOX, ego ? 6.75D : 6.25D,
                    ego ? 1.65D : 1.35D, 0.0D, -0.35D, ego ? 3.3D : 3.0D);
            case VERTICAL_SLASH -> new Profile(Shape.BOX, ego ? 5.8D : 5.25D,
                    ego ? 2.2D : 1.75D, 0.0D, -0.35D, ego ? 5.6D : 5.0D);
        };
    }

    public boolean intersects(AABB target) {
        if (target.maxY < origin.y + profile.minY()
                || target.minY > origin.y + profile.maxY()) {
            return false;
        }

        Vec3 center = target.getCenter();
        double relativeX = center.x - origin.x;
        double relativeZ = center.z - origin.z;
        double halfX = target.getXsize() * 0.5D;
        double halfZ = target.getZsize() * 0.5D;
        double forwardCenter = relativeX * forward.x + relativeZ * forward.z;
        double sideCenter = relativeX * side.x + relativeZ * side.z;
        double forwardRadius = Math.abs(forward.x) * halfX + Math.abs(forward.z) * halfZ;
        double sideRadius = Math.abs(side.x) * halfX + Math.abs(side.z) * halfZ;

        if (profile.shape() == Shape.BOX) {
            return forwardCenter + forwardRadius >= -CONTACT_MARGIN
                    && forwardCenter - forwardRadius <= profile.reach()
                    && Math.abs(sideCenter) - sideRadius <= profile.halfWidth();
        }

        double centerDistance = Math.hypot(relativeX, relativeZ);
        double targetRadius = Math.hypot(halfX, halfZ);
        if (centerDistance - targetRadius > profile.reach()) return false;
        if (centerDistance <= targetRadius + CONTACT_MARGIN) return true;

        double facingCosine = clamp(forwardCenter / centerDistance, -1.0D, 1.0D);
        double centerAngle = Math.acos(facingCosine);
        double angularPadding = Math.asin(clamp(targetRadius / centerDistance, 0.0D, 1.0D));
        return centerAngle <= Math.toRadians(profile.halfAngleDegrees()) + angularPadding;
    }

    public AABB searchBounds() {
        double horizontal = profile.shape() == Shape.ARC
                ? profile.reach()
                : Math.hypot(profile.reach(), profile.halfWidth());
        return new AABB(origin.x - horizontal, origin.y + profile.minY(),
                origin.z - horizontal, origin.x + horizontal,
                origin.y + profile.maxY(), origin.z + horizontal);
    }

    private static Vec3 horizontalDirection(Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        return horizontal.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : horizontal.normalize();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Shape {
        ARC,
        BOX
    }

    record Profile(Shape shape, double reach, double halfWidth,
                   double halfAngleDegrees, double minY, double maxY) {
    }
}
