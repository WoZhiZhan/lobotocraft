package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;

import java.util.List;

public final class GeburahBladeTrailProfile {
    private static final List<String> NITAI2 = List.of("nitai2_start", "nitai2_end");
    private static final List<String> NITAI2_TIP =
            List.of(NITAI2.get(NITAI2.size() - 1));
    private static final List<String> LIADAO =
            List.of("liadao_start", "liadao_mid", "liadao_end");
    private static final List<String> CHANGMAO = List.of("changmao_maojian");
    private static final List<String> ZHONGJIAN =
            List.of("zhongjian_start", "zhongjian_end");

    private GeburahBladeTrailProfile() {
    }

    public static Sample forAnimation(String animation) {
        if (animation == null) return null;
        return switch (animation) {
            case "slash" -> new Sample(NITAI2, GeburahMove.SLASH, false);
            case "EGOslash" -> new Sample(LIADAO, GeburahMove.SLASH, true);
            case "stab" -> new Sample(NITAI2_TIP, GeburahMove.STAB, false);
            case "EGOstab" -> new Sample(CHANGMAO, GeburahMove.STAB, true);
            case "verticalslash" ->
                    new Sample(NITAI2, GeburahMove.VERTICAL_SLASH, false);
            case "EGOverticalslash" ->
                    new Sample(ZHONGJIAN, GeburahMove.VERTICAL_SLASH, true);
            case "willofbattle" ->
                    new Sample(NITAI2, GeburahMove.SLASH, false);
            case "EGOwillofbattle" ->
                    new Sample(NITAI2, GeburahMove.SLASH, true);
            default -> null;
        };
    }

    /** Both EGO blood-mist weapons are tracked; capture picks the currently noncollapsed blade span. */
    public static List<Sample> specialCandidates(String animation) {
        if (animation == null) return List.of();
        return switch (animation) {
            case "willofbattle_begin", "willofbattle" ->
                    List.of(new Sample(NITAI2, GeburahMove.SLASH, false));
            case "EGOwillofbattle_begin", "EGOwillofbattle" ->
                    List.of(new Sample(NITAI2, GeburahMove.SLASH, true));
            case "bloodmist" -> List.of(new Sample(NITAI2, GeburahMove.SLASH, false));
            case "EGObloodmist" -> List.of(new Sample(NITAI2, GeburahMove.SLASH, true),
                    new Sample(ZHONGJIAN, GeburahMove.SLASH, true));
            case "bodieseverywhere" -> List.of(new Sample(NITAI2, GeburahMove.SLASH, true));
            default -> List.of();
        };
    }

    public record Sample(List<String> locatorBoneNames, GeburahMove move, boolean ego) {
        public Sample {
            locatorBoneNames = List.copyOf(locatorBoneNames);
        }
    }
}
