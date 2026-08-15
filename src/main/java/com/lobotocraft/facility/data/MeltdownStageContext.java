package com.lobotocraft.facility.data;

/** Global facility state captured when a timed meltdown instance is created. */
public record MeltdownStageContext(int facilityDay, int triggeredQliphothLevel,
                                   boolean coreSuppressionActive,
                                   String coreSuppressionType,
                                   int coreSuppressionPhase) {
    public MeltdownStageContext {
        facilityDay = Math.max(0, facilityDay);
        triggeredQliphothLevel = Math.max(1, triggeredQliphothLevel);
        coreSuppressionType = coreSuppressionType == null ? "" : coreSuppressionType;
        coreSuppressionPhase = Math.max(-1, coreSuppressionPhase);
    }

    public static MeltdownStageContext legacy(int triggeredQliphothLevel) {
        return new MeltdownStageContext(0, triggeredQliphothLevel, false, "", -1);
    }
}
