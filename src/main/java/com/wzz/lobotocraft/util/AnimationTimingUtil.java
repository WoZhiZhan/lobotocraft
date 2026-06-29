package com.wzz.lobotocraft.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wzz.lobotocraft.ModMain;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationTimingUtil {
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final double EPSILON = 1.0E-6D;
    private static final Map<String, AnimationTiming> CACHE = new ConcurrentHashMap<>();

    private AnimationTimingUtil() {
    }

    public static int getAnimationDurationTicks(String animationFileName, String animationName, int fallbackTicks) {
        AnimationTiming timing = load(animationFileName, animationName);
        if (timing.animationLengthSeconds <= 0.0D) {
            return Math.max(1, fallbackTicks);
        }
        return secondsToCoveringTicks(timing.animationLengthSeconds);
    }

    public static int getNearestKeyframeTick(String animationFileName, String animationName,
                                             int fallbackTicks) {
        double targetSeconds = Math.max(0, fallbackTicks) / TICKS_PER_SECOND;
        return getNearestKeyframeTick(animationFileName, animationName, targetSeconds, fallbackTicks);
    }

    public static int getNearestKeyframeTick(String animationFileName, String animationName,
                                             double targetSeconds, int fallbackTicks) {
        AnimationTiming timing = load(animationFileName, animationName);
        Double keyframeSeconds = timing.nearestKeyframe(targetSeconds);
        if (keyframeSeconds == null) {
            return Math.max(1, fallbackTicks);
        }
        return secondsToCoveringTicks(keyframeSeconds);
    }

    private static AnimationTiming load(String animationFileName, String animationName) {
        String normalizedFileName = normalizeAnimationFileName(animationFileName);
        String cacheKey = normalizedFileName + "#" + animationName;
        return CACHE.computeIfAbsent(cacheKey, key -> readTiming(normalizedFileName, animationName));
    }

    private static AnimationTiming readTiming(String animationFileName, String animationName) {
        String path = "assets/" + ModMain.MODID + "/animations/" + animationFileName;
        ClassLoader classLoader = AnimationTimingUtil.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(path)) {
            if (stream == null) {
                return AnimationTiming.EMPTY;
            }

            JsonElement rootElement = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            JsonObject root = asObject(rootElement);
            JsonObject animations = root == null ? null : asObject(root.get("animations"));
            JsonObject animation = animations == null ? null : asObject(animations.get(animationName));
            if (animation == null) {
                return AnimationTiming.EMPTY;
            }

            double animationLengthSeconds = asDouble(animation.get("animation_length"), 0.0D);
            TreeSet<Double> keyframes = new TreeSet<>();
            collectBoneKeyframes(animation, keyframes);
            return new AnimationTiming(animationLengthSeconds, new ArrayList<>(keyframes));
        } catch (Exception ignored) {
            return AnimationTiming.EMPTY;
        }
    }

    private static void collectBoneKeyframes(JsonObject animation, TreeSet<Double> keyframes) {
        JsonObject bones = asObject(animation.get("bones"));
        if (bones == null) {
            return;
        }

        for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
            JsonObject bone = asObject(boneEntry.getValue());
            if (bone == null) {
                continue;
            }
            collectTransformKeyframes(bone.get("rotation"), keyframes);
            collectTransformKeyframes(bone.get("position"), keyframes);
            collectTransformKeyframes(bone.get("scale"), keyframes);
        }
    }

    private static void collectTransformKeyframes(JsonElement transformElement, TreeSet<Double> keyframes) {
        JsonObject transform = asObject(transformElement);
        if (transform == null) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : transform.entrySet()) {
            if (isNumericKey(entry.getKey())) {
                keyframes.add(Double.parseDouble(entry.getKey()));
            }
        }
    }

    private static boolean isNumericKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if ((c < '0' || c > '9') && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static double asDouble(JsonElement element, double fallback) {
        try {
            return element == null ? fallback : element.getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String normalizeAnimationFileName(String animationFileName) {
        return animationFileName.endsWith(".animation.json")
                ? animationFileName
                : animationFileName + ".animation.json";
    }

    private static int secondsToCoveringTicks(double seconds) {
        return Math.max(1, (int) Math.ceil(seconds * TICKS_PER_SECOND - EPSILON));
    }

    private record AnimationTiming(double animationLengthSeconds, List<Double> keyframes) {
        private static final AnimationTiming EMPTY = new AnimationTiming(0.0D, List.of());

        private Double nearestKeyframe(double targetSeconds) {
            if (keyframes.isEmpty()) {
                return null;
            }

            double target = animationLengthSeconds > 0.0D
                    ? Math.min(Math.max(0.0D, targetSeconds), animationLengthSeconds)
                    : Math.max(0.0D, targetSeconds);
            Double best = null;
            double bestDistance = Double.MAX_VALUE;
            for (double keyframe : keyframes) {
                if (keyframe <= EPSILON
                        || (animationLengthSeconds > 0.0D && keyframe >= animationLengthSeconds - EPSILON)) {
                    continue;
                }
                double distance = Math.abs(keyframe - target);
                if (distance + EPSILON < bestDistance
                        || (Math.abs(distance - bestDistance) <= EPSILON && (best == null || keyframe > best))) {
                    best = keyframe;
                    bestDistance = distance;
                }
            }
            return best;
        }
    }
}
