package com.wzz.lobotocraft.core_suppression;

import net.minecraft.resources.ResourceLocation;
import com.wzz.lobotocraft.util.ResourceUtil;

import java.util.Locale;

public enum CoreSuppressionType {
    MALKUTH("Malkuth", 0xE7B847, "malkuth_core_suppression"),
    YESOD("Yesod", 0x8E73B8, "yesod_core_suppression"),
    HOD("Hod", 0xD88965, "hod_core_suppression"),
    NETZACH("Netzach", 0x79A85A, "netzach_core_suppression");

    private final String displayName;
    private final int color;
    private final String advancementPath;

    CoreSuppressionType(String displayName, int color, String advancementPath) {
        this.displayName = displayName;
        this.color = color;
        this.advancementPath = advancementPath;
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
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
