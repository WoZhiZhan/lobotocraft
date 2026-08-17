package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahEgoReleasePulseTest {
    @Test
    void impulsePushesAwayFromGeburahAndAddsLift() {
        Vec3 impulse = GeburahEgoReleasePulse.outwardImpulse(new Vec3(3.0D, 5.0D, 4.0D));

        assertTrue(impulse.x > 0.0D);
        assertTrue(impulse.z > 0.0D);
        assertEquals(0.55D, impulse.y, 0.0001D);
    }

    @Test
    void overlappingPlayerStillReceivesFiniteImpulse() {
        Vec3 impulse = GeburahEgoReleasePulse.outwardImpulse(Vec3.ZERO);

        assertTrue(Double.isFinite(impulse.x));
        assertTrue(Double.isFinite(impulse.y));
        assertTrue(Double.isFinite(impulse.z));
        assertTrue(impulse.horizontalDistanceSqr() > 0.0D);
    }
}
