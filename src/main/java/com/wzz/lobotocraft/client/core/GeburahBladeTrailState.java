package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEgoAttackVisuals;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class GeburahBladeTrailState {
    private static final int MAX_SAMPLES = 18;
    private static final ArrayDeque<TimedPosition> TRAIL = new ArrayDeque<>();
    private static int entityId = -1;
    private static double previousSampleTime = Double.NEGATIVE_INFINITY;
    private static double currentSampleTime = Double.NEGATIVE_INFINITY;
    private static Vec3 previousPosition;
    private static Vec3 currentPosition;
    private static GeburahMove currentMove;
    private static boolean currentEgo;
    private static boolean currentEnhanced;
    private static int currentPointCount;
    private static int currentBladeSource;

    private GeburahBladeTrailState() {
    }

    public static Vec3 worldPosition(Matrix4f renderPose, Vec3 cameraPosition) {
        return worldPosition(renderPose, cameraPosition, Vec3.ZERO);
    }

    public static Vec3 worldPosition(Matrix4f renderPose, Vec3 cameraPosition,
                                     Vec3 modelPosition) {
        if (renderPose == null || cameraPosition == null || modelPosition == null) return null;
        Vector4f point = renderPose.transform(new Vector4f(
                (float) modelPosition.x, (float) modelPosition.y,
                (float) modelPosition.z, 1.0F));
        return new Vec3(point.x() + cameraPosition.x,
                point.y() + cameraPosition.y,
                point.z() + cameraPosition.z);
    }

    public static void capture(int sampledEntityId, double sampleTime, Vec3 worldPosition) {
        if (worldPosition == null) return;
        capture(sampledEntityId, sampleTime, List.of(worldPosition), false, null);
    }

    public static void capture(int sampledEntityId, double sampleTime, Vec3 worldPosition,
                               boolean ego) {
        if (worldPosition == null) return;
        capture(sampledEntityId, sampleTime, List.of(worldPosition), ego, null);
    }

    public static void capture(int sampledEntityId, double sampleTime, Vec3 worldPosition,
                               boolean ego, GeburahMove move) {
        if (worldPosition == null) return;
        capture(sampledEntityId, sampleTime, List.of(worldPosition), ego, move);
    }

    public static void capture(int sampledEntityId, double sampleTime,
                               Vec3 basePosition, Vec3 worldPosition,
                               boolean ego, GeburahMove move) {
        if (basePosition == null || worldPosition == null) return;
        capture(sampledEntityId, sampleTime, List.of(basePosition, worldPosition), ego, move);
    }

    public static void capture(int sampledEntityId, double sampleTime,
                               List<Vec3> bladePositions,
                               boolean ego, GeburahMove move) {
        capture(sampledEntityId, sampleTime, bladePositions, ego, move, false);
    }

    public static void capture(int sampledEntityId, double sampleTime,
                               List<Vec3> bladePositions, boolean ego, GeburahMove move,
                               boolean enhanced) {
        capture(sampledEntityId, sampleTime, bladePositions, ego, move, enhanced, 0);
    }

    public static void capture(int sampledEntityId, double sampleTime,
                               List<Vec3> bladePositions, boolean ego, GeburahMove move,
                               boolean enhanced, int bladeSource) {
        if (sampledEntityId < 0 || !Double.isFinite(sampleTime)
                || bladePositions == null || bladePositions.isEmpty()
                || bladePositions.stream().anyMatch(position -> position == null)) return;
        List<Vec3> positions = List.copyOf(bladePositions);
        Vec3 worldPosition = positions.get(positions.size() - 1);
        if (sampledEntityId != entityId || currentPosition == null
                || move != currentMove
                || ego != currentEgo
                || enhanced != currentEnhanced
                || bladeSource != currentBladeSource
                || positions.size() != currentPointCount
                || sampleTime < currentSampleTime
                || currentPosition.distanceToSqr(worldPosition) > 64.0D * 64.0D) {
            TRAIL.clear();
            entityId = sampledEntityId;
            previousSampleTime = sampleTime;
            currentSampleTime = sampleTime;
            previousPosition = worldPosition;
            currentPosition = worldPosition;
            currentMove = move;
            currentEgo = ego;
            currentEnhanced = enhanced;
            currentPointCount = positions.size();
            currentBladeSource = bladeSource;
            TRAIL.addLast(new TimedPosition(sampleTime, positions, ego, move, enhanced));
            return;
        }
        if (sampleTime <= currentSampleTime + 1.0E-5D) {
            currentPosition = worldPosition;
            if (!TRAIL.isEmpty()) TRAIL.removeLast();
            TRAIL.addLast(new TimedPosition(currentSampleTime, positions, ego, move, enhanced));
            return;
        }
        previousSampleTime = currentSampleTime;
        previousPosition = currentPosition;
        currentSampleTime = sampleTime;
        currentPosition = worldPosition;
        currentMove = move;
        currentEgo = ego;
        currentPointCount = positions.size();
        TRAIL.addLast(new TimedPosition(sampleTime, positions, ego, move, enhanced));
        while (TRAIL.size() > MAX_SAMPLES) TRAIL.removeFirst();
    }

    public static Vec3 recentDirection(double now, double maximumAge) {
        if (previousPosition == null || currentPosition == null
                || !Double.isFinite(now) || now - currentSampleTime > maximumAge
                || currentSampleTime <= previousSampleTime) return null;
        Vec3 movement = currentPosition.subtract(previousPosition);
        return movement.lengthSqr() < 1.0E-6D ? null : movement.normalize();
    }

    public static Vec3 recentPosition(double now, double maximumAge) {
        if (currentPosition == null || !Double.isFinite(now)
                || now - currentSampleTime > maximumAge) return null;
        return currentPosition;
    }

    public static List<TrailPoint> recentTrail(double now, double maximumAge) {
        if (!Double.isFinite(now) || !Double.isFinite(maximumAge) || maximumAge <= 0.0D) {
            return List.of();
        }
        while (!TRAIL.isEmpty() && now - TRAIL.peekFirst().time > maximumAge) {
            TRAIL.removeFirst();
        }
        List<TrailPoint> points = new ArrayList<>(TRAIL.size());
        for (TimedPosition sample : TRAIL) {
            float age = (float) Math.max(0.0D,
                    Math.min(1.0D, (now - sample.time) / maximumAge));
            points.add(new TrailPoint(sample.bladePositions,
                    age, sample.ego, sample.move));
        }
        return List.copyOf(points);
    }

    /** Rollback uses the exact legacy six-tick query, including its age normalisation. */
    public static List<TrailPoint> visualTrail(double now, boolean trialEnabled) {
        if (!trialEnabled) return recentTrail(now, 6.0D);
        if (!Double.isFinite(now)) return List.of();
        TRAIL.removeIf(sample -> now - sample.time
                > GeburahEgoAttackVisuals.trailLifetime(sample.enhanced, sample.move));
        List<TrailPoint> points = new ArrayList<>(TRAIL.size());
        for (TimedPosition sample : TRAIL) {
            double lifetime = GeburahEgoAttackVisuals.trailLifetime(sample.enhanced, sample.move);
            float age = (float) Math.max(0.0D, Math.min(1.0D, (now - sample.time) / lifetime));
            points.add(new TrailPoint(sample.bladePositions, age, sample.ego,
                    sample.move, sample.enhanced));
        }
        return List.copyOf(points);
    }

    public static void clear() {
        entityId = -1;
        previousSampleTime = Double.NEGATIVE_INFINITY;
        currentSampleTime = Double.NEGATIVE_INFINITY;
        previousPosition = null;
        currentPosition = null;
        currentMove = null;
        currentEgo = false;
        currentEnhanced = false;
        currentPointCount = 0;
        currentBladeSource = 0;
        TRAIL.clear();
    }

    private record TimedPosition(double time, List<Vec3> bladePositions,
                                 boolean ego, GeburahMove move, boolean enhanced) {
    }

    public record TrailPoint(List<Vec3> bladePositions, float age,
                             boolean ego, GeburahMove move, boolean enhanced) {
        public TrailPoint(List<Vec3> bladePositions, float age, boolean ego, GeburahMove move) {
            this(bladePositions, age, ego, move, false);
        }
        public TrailPoint {
            bladePositions = List.copyOf(bladePositions);
        }

        public Vec3 basePosition() {
            return bladePositions.get(0);
        }

        public Vec3 position() {
            return bladePositions.get(bladePositions.size() - 1);
        }
    }
}
