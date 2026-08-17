package com.wzz.lobotocraft.core_suppression;

import net.minecraft.resources.ResourceLocation;
import com.wzz.lobotocraft.util.ResourceUtil;

import java.util.Locale;

public enum CoreSuppressionType {
    MALKUTH("Malkuth", 0xE7B847, "malkuth_core_suppression", true, true, 6, 0),
    YESOD("Yesod", 0x8E73B8, "yesod_core_suppression", true, true, 6, 0),
    HOD("Hod", 0xD88965, "hod_core_suppression", true, true, 6, 0),
    NETZACH("Netzach", 0x79A85A, "netzach_core_suppression", true, true, 6, 0),

    // Middle/lower layer completion rules mirror the original game. Chesed is
    // implemented; the remaining lower-layer encounters stay disabled.
    TIPHERETH("Tiphereth", 0xFFC50B, "tiphereth_core_suppression", false, false, 10, 0),
    CHESED("Chesed", 0x4D82C4, "chesed_core_suppression", true, true, 8, 0),
    GEBURAH("Geburah", 0xB72D2D, "geburah_core_suppression", true, false, 0, 2),
    BINAH("Binah", 0xC9B037, "binah_core_suppression", false, false, 0, 3),
    HOKMA("Hokma", 0xD7D7D7, "hokma_core_suppression", false, true, 10, 0);

    private final String displayName;
    private final int color;
    private final String advancementPath;
    private final boolean implemented;
    private final boolean energyQuotaRequired;
    private final int requiredMeltdownLevel;
    private final int requiredBossPhases;

    CoreSuppressionType(String displayName, int color, String advancementPath,
                        boolean implemented, boolean energyQuotaRequired,
                        int requiredMeltdownLevel, int requiredBossPhases) {
        this.displayName = displayName;
        this.color = color;
        this.advancementPath = advancementPath;
        this.implemented = implemented;
        this.energyQuotaRequired = energyQuotaRequired;
        this.requiredMeltdownLevel = requiredMeltdownLevel;
        this.requiredBossPhases = requiredBossPhases;
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTranslationKey() {
        return "core_suppression.lobotocraft." + getId();
    }

    public int getColor() {
        return color;
    }

    public boolean isImplemented() {
        return implemented;
    }

    public boolean isEnergyQuotaRequired() {
        return energyQuotaRequired;
    }

    public int getRequiredMeltdownLevel() {
        return requiredMeltdownLevel;
    }

    public int getRequiredBossPhases() {
        return requiredBossPhases;
    }

    public boolean isFacilityObjectiveMet(int energy, int energyQuota, int meltdownLevel) {
        if (!implemented || requiredBossPhases > 0) return false;
        boolean energyMet = !energyQuotaRequired || energy >= energyQuota;
        boolean meltdownMet = requiredMeltdownLevel <= 0 || meltdownLevel >= requiredMeltdownLevel;
        return energyMet && meltdownMet;
    }

    public ResourceLocation getAdvancementId() {
        return ResourceUtil.createInstance(advancementPath);
    }

    public int getCompletionBit() {
        return 1 << ordinal();
    }

    public static CoreSuppressionType byOrdinal(int ordinal) {
        CoreSuppressionType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public static CoreSuppressionType byId(String id) {
        for (CoreSuppressionType type : values()) {
            if (type.getId().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
