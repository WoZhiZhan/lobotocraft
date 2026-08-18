package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;

final class CoreSuppressionMusicPolicy {
    private CoreSuppressionMusicPolicy() {
    }

    static String trackPath(CoreSuppressionType type, int visualStage) {
        if (type == null) return null;
        if (visualStage <= 0) return "escape_alert_bgm_1";
        String department = switch (type) {
            case MALKUTH -> "malkuth";
            case YESOD -> "yesod";
            case HOD -> "hod";
            case NETZACH -> "netzach";
            case CHESED -> "chesed";
            default -> null;
        };
        if (department == null) return null;
        int phase = visualStage >= 2 ? 2 : 1;
        return "core." + department + "_" + phase;
    }
}
