package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahHitboxTest {
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 FORWARD = new Vec3(0.0D, 0.0D, 1.0D);

    @Test
    void slashUsesTheWideForwardArcShownByTheBladeTrail() {
        GeburahHitbox hitbox = GeburahHitbox.forMove(
                GeburahMove.SLASH, false, ORIGIN, FORWARD);

        assertTrue(hitbox.intersects(boxAtPolar(5.0D, 72.0D, 0.0D, 2.0D)));
        assertTrue(hitbox.intersects(boxAtPolar(5.2D, -72.0D, 0.0D, 2.0D)));
        assertFalse(hitbox.intersects(boxAtPolar(5.0D, 105.0D, 0.0D, 2.0D)));
        assertFalse(hitbox.intersects(boxAtPolar(2.0D, 180.0D, 0.0D, 2.0D)));
    }

    @Test
    void egoSlashIncludesTheSecondExtendedTrail() {
        AABB distalTarget = boxAt(0.0D, 5.8D, 0.0D, 2.0D);

        assertFalse(GeburahHitbox.forMove(
                GeburahMove.SLASH, false, ORIGIN, FORWARD).intersects(distalTarget));
        assertTrue(GeburahHitbox.forMove(
                GeburahMove.SLASH, true, ORIGIN, FORWARD).intersects(distalTarget));
    }

    @Test
    void stabUsesANarrowLongCapsuleInsteadOfCenterDistance() {
        GeburahHitbox hitbox = GeburahHitbox.forMove(
                GeburahMove.STAB, false, ORIGIN, FORWARD);

        assertTrue(hitbox.intersects(boxAt(1.25D, 5.9D, 0.2D, 2.8D)));
        assertFalse(hitbox.intersects(boxAt(1.85D, 5.9D, 0.2D, 2.8D)));
        assertFalse(hitbox.intersects(boxAt(0.0D, -1.0D, 0.2D, 2.8D)));
    }

    @Test
    void verticalSlashCoversTheTallForwardImpactVolume() {
        GeburahHitbox hitbox = GeburahHitbox.forMove(
                GeburahMove.VERTICAL_SLASH, false, ORIGIN, FORWARD);

        assertTrue(hitbox.intersects(boxAt(1.55D, 4.8D, 4.2D, 4.9D)));
        assertFalse(hitbox.intersects(boxAt(2.35D, 4.8D, 0.0D, 2.0D)));
        assertFalse(hitbox.intersects(boxAt(0.0D, 4.0D, 5.5D, 6.0D)));
    }

    @Test
    void hitboxRotatesWithGeburah() {
        GeburahHitbox hitbox = GeburahHitbox.forMove(
                GeburahMove.STAB, false, ORIGIN, new Vec3(1.0D, 0.0D, 0.0D));

        assertTrue(hitbox.intersects(boxAt(5.9D, 0.0D, 0.0D, 2.0D)));
        assertFalse(hitbox.intersects(boxAt(0.0D, 5.9D, 0.0D, 2.0D)));
    }

    @Test
    void customForwardBoxUsesFullWidthLengthAndHeight() {
        GeburahHitbox hitbox = GeburahHitbox.forwardBox(
                ORIGIN, FORWARD, 12.0D, 15.0D, 5.0D);

        assertTrue(hitbox.intersects(boxAt(7.35D, 11.8D, 0.0D, 4.9D)));
        assertFalse(hitbox.intersects(boxAt(7.75D, 11.8D, 0.0D, 4.9D)));
        assertFalse(hitbox.intersects(boxAt(0.0D, 12.4D, 0.0D, 4.9D)));
        assertFalse(hitbox.intersects(boxAt(0.0D, 6.0D, 5.2D, 5.8D)));
    }

    @Test
    void customForwardBoxRotatesWithAttackDirection() {
        GeburahHitbox hitbox = GeburahHitbox.forwardBox(
                ORIGIN, new Vec3(1.0D, 0.0D, 0.0D), 8.0D, 3.0D, 8.0D);

        assertTrue(hitbox.intersects(boxAt(7.8D, 1.35D, 0.0D, 7.8D)));
        assertFalse(hitbox.intersects(boxAt(1.35D, 7.8D, 0.0D, 7.8D)));
    }

    private static AABB boxAtPolar(double radius, double angleDegrees,
                                   double minY, double maxY) {
        double angle = Math.toRadians(angleDegrees);
        return boxAt(Math.sin(angle) * radius, Math.cos(angle) * radius, minY, maxY);
    }

    private static AABB boxAt(double x, double z, double minY, double maxY) {
        return new AABB(x - 0.1D, minY, z - 0.1D, x + 0.1D, maxY, z + 0.1D);
    }
}
