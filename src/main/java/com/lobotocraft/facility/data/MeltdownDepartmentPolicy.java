package com.lobotocraft.facility.data;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;

/** Department immunity rules mirrored from CreatureOverloadManager. */
public final class MeltdownDepartmentPolicy {
    private MeltdownDepartmentPolicy() {
    }

    public static boolean isProtected(String departmentId, CoreSuppressionType activeType,
                                      int completedMask, boolean honorCompletedRewards) {
        if (departmentId == null || departmentId.isBlank()) return false;
        if (matches(departmentId, activeType)) return true;
        return hasCompletedRewardImmunity(departmentId, activeType,
            completedMask, honorCompletedRewards);
    }

    /**
     * Original overload statistics exclude completed departments, but do not
     * exclude the department whose suppression is currently active. The latter
     * is removed only from the actual target pool.
     */
    public static boolean hasCompletedRewardImmunity(String departmentId,
                                                      CoreSuppressionType activeType,
                                                      int completedMask,
                                                      boolean honorCompletedRewards) {
        if (departmentId == null || departmentId.isBlank()
                || !honorCompletedRewards || ignoresCompletedRewards(activeType)) return false;
        for (CoreSuppressionType type : CoreSuppressionType.values()) {
            if ((completedMask & type.getCompletionBit()) != 0 && matches(departmentId, type)) {
                return true;
            }
        }
        return false;
    }

    public static String departmentId(CoreSuppressionType type) {
        if (type == null) return "";
        return switch (type) {
            case MALKUTH -> "control";
            case YESOD -> "information";
            case HOD -> "training";
            case NETZACH -> "safety";
            case TIPHERETH -> "central";
            case CHESED -> "welfare";
            case GEBURAH -> "disciplinary";
            case BINAH -> "extraction";
            case HOKMA -> "record";
        };
    }

    private static boolean matches(String departmentId, CoreSuppressionType type) {
        return type != null && departmentId(type).equalsIgnoreCase(departmentId.trim());
    }

    private static boolean ignoresCompletedRewards(CoreSuppressionType activeType) {
        return activeType == CoreSuppressionType.TIPHERETH
            || activeType == CoreSuppressionType.BINAH
            || activeType == CoreSuppressionType.HOKMA;
    }
}
