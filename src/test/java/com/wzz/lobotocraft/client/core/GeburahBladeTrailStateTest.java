package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class GeburahBladeTrailStateTest {
    @AfterEach
    void clear() {
        GeburahBladeTrailState.clear();
    }

    @Test
    void returnsNormalisedRecentBoneTrajectory() {
        GeburahBladeTrailState.capture(4, 20.0D, Vec3.ZERO);
        GeburahBladeTrailState.capture(4, 20.5D, new Vec3(3.0D, 4.0D, 0.0D));

        Vec3 direction = GeburahBladeTrailState.recentDirection(20.6D, 2.0D);
        assertEquals(0.6D, direction.x, 0.001D);
        assertEquals(0.8D, direction.y, 0.001D);
        assertEquals(new Vec3(3.0D, 4.0D, 0.0D),
                GeburahBladeTrailState.recentPosition(20.6D, 2.0D));
    }

    @Test
    void staleOrTeleportSamplesFallBackToServerDirection() {
        GeburahBladeTrailState.capture(4, 20.0D, Vec3.ZERO);
        GeburahBladeTrailState.capture(4, 20.5D, new Vec3(1.0D, 0.0D, 0.0D));
        assertNull(GeburahBladeTrailState.recentDirection(23.0D, 2.0D));
        assertNull(GeburahBladeTrailState.recentPosition(23.0D, 2.0D));

        GeburahBladeTrailState.capture(4, 24.0D, new Vec3(100.0D, 0.0D, 0.0D));
        assertNull(GeburahBladeTrailState.recentDirection(24.1D, 2.0D));
    }

    @Test
    void changingAttackShapeStartsANewTrail() {
        GeburahBladeTrailState.capture(4, 20.0D, Vec3.ZERO, true, GeburahMove.SLASH);
        GeburahBladeTrailState.capture(4, 20.5D, new Vec3(1.0D, 0.0D, 0.0D),
                true, GeburahMove.STAB);

        var trail = GeburahBladeTrailState.recentTrail(20.6D, 2.0D);
        assertEquals(1, trail.size());
        assertEquals(GeburahMove.STAB, trail.get(0).move());
    }

    @Test
    void retainsTheBladeSpanForSweepRendering() {
        Vec3 base = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 tip = new Vec3(1.0D, 2.0D, -1.0D);

        GeburahBladeTrailState.capture(4, 20.0D, base, tip,
                false, GeburahMove.SLASH);

        var point = GeburahBladeTrailState.recentTrail(20.1D, 2.0D).get(0);
        assertEquals(base, point.basePosition());
        assertEquals(tip, point.position());
    }

    @Test
    void convertsCurrentRenderPoseToWorldPosition() {
        Matrix4f pose = new Matrix4f().translation(2.0F, 3.0F, 4.0F);

        assertEquals(new Vec3(12.0D, 23.0D, 34.0D),
                GeburahBladeTrailState.worldPosition(pose, new Vec3(10.0D, 20.0D, 30.0D)));
        assertEquals(new Vec3(12.5D, 24.0D, 32.0D),
                GeburahBladeTrailState.worldPosition(pose,
                        new Vec3(10.0D, 20.0D, 30.0D),
                        new Vec3(0.5D, 1.0D, -2.0D)));
    }

    @Test
    void usesExportedLocatorBonesForEgoSlash() {
        GeburahBladeTrailProfile.Sample profile =
                GeburahBladeTrailProfile.forAnimation("EGOslash");

        assertEquals(List.of("liadao_start", "liadao_mid", "liadao_end"),
                profile.locatorBoneNames());
    }

    @Test
    void representsTheSpearAsATipTrajectory() {
        GeburahBladeTrailProfile.Sample profile =
                GeburahBladeTrailProfile.forAnimation("EGOstab");

        assertEquals(List.of("changmao_maojian"), profile.locatorBoneNames());
    }

    @Test
    void representsNormalStabAsTheNitai2TipTrajectory() {
        GeburahBladeTrailProfile.Sample slash =
                GeburahBladeTrailProfile.forAnimation("slash");
        GeburahBladeTrailProfile.Sample stab =
                GeburahBladeTrailProfile.forAnimation("stab");

        assertEquals(List.of(slash.locatorBoneNames().get(
                slash.locatorBoneNames().size() - 1)), stab.locatorBoneNames());
    }

    @Test
    void selectsTheLocatorSequenceForEachWeapon() {
        assertEquals(List.of("nitai2_start", "nitai2_end"),
                GeburahBladeTrailProfile.forAnimation("slash").locatorBoneNames());
        assertEquals(List.of("zhongjian_start", "zhongjian_end"),
                GeburahBladeTrailProfile.forAnimation("EGOverticalslash")
                        .locatorBoneNames());
    }

    @Test
    void selectsTheWeaponBoneActuallyUsedByEachAttackAnimation() {
        assertProfile("slash", 2, GeburahMove.SLASH, false);
        assertProfile("EGOslash", 3, GeburahMove.SLASH, true);
        assertProfile("stab", 1, GeburahMove.STAB, false);
        assertProfile("EGOstab", 1, GeburahMove.STAB, true);
        assertProfile("verticalslash", 2,
                GeburahMove.VERTICAL_SLASH, false);
        assertProfile("EGOverticalslash", 2,
                GeburahMove.VERTICAL_SLASH, true);
        assertProfile("willofbattle", 2, GeburahMove.SLASH, false);
        assertProfile("EGOwillofbattle", 2, GeburahMove.SLASH, true);
        assertNull(GeburahBladeTrailProfile.forAnimation("idle"));
    }

    private static void assertProfile(String animation, int pointCount,
                                      GeburahMove move, boolean ego) {
        GeburahBladeTrailProfile.Sample profile =
                GeburahBladeTrailProfile.forAnimation(animation);
        assertEquals(pointCount, profile.locatorBoneNames().size());
        assertEquals(move, profile.move());
        assertEquals(ego, profile.ego());
    }
}
