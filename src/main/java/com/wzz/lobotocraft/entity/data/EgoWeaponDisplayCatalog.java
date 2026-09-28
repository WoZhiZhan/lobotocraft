package com.wzz.lobotocraft.entity.data;

import java.util.Map;

/**
 * Facility-screen fallback data for researched E.G.O whose abnormality entity is not loaded.
 * A loaded abnormality's {@link EGOEquipmentData.WeaponData} remains authoritative.
 */
public final class EgoWeaponDisplayCatalog {
    private static final Map<String, WeaponSummary> WEAPONS = Map.ofEntries(
            entry("T-01-54", "RED", "15", "慢", "锤"),
            entry("0-02-62", "BLUE", "2-4", "2.0", "近战"),
            entry("D-01-106", "WHITE", "24", "6s", "35格"),
            entry("F-02-58", "RED", "12-17", "0.6", "3格"),
            entry("O-03-93", "WHITE", "8-12 / 15-18", "1.5s", "25格"),
            entry("T-01-68", "WHITE", "2", "2.6", "10格"),
            entry("O-01-55", "BLACK", "15", "2", "3x3"),
            entry("O-05-61", "BLUE", "8", "1.0", "4格"),
            entry("O-02-63", "ALL", "18", "普通", "4格"),
            entry("F-01-02", "RED", "20-30", "极慢", "远"),
            entry("O-03-60", "RED", "5-9", "1.7", "4格"),
            entry("T-04-06", "WHITE", "2-3", "0.6", "10格"),
            entry("T-05-41", "RED", "2", "1.6", "4格"),
            entry("O-02-40", "BLACK", "22-28", "3.0", "近"),
            entry("O-01-67", "BLACK", "6", "1s", "25格"),
            entry("O-06-20", "RED", "10~14", "1.1", "3格"),
            entry("D-02-107", "RED", "5-7", "1.0", "2格"),
            entry("O-02-56", "RED", "2-3", "0.7", "远程武器"),
            entry("T-04-50", "RED", "8", "1.0", "4格"),
            entry("O-04-08", "RED", "8", "1.0", "4格"),
            entry("F-01-57", "RED", "11-13", "1.2", "3格"),
            entry("O-03-03", "WHITE", "5-7", "普通", "近"),
            entry("T-01-75", "BLACK", "16", "1.5", "3格"),
            entry("F-01-37", "WHITE", "6-12", "1.2", "1.2格"),
            entry("O-01-12", "WHITE", "2-3", "0.6", "10格"),
            entry("O-02-98", "BLACK", "1-4", "快", "3格"),
            entry("F-04-83", "RED", "5-7", "2s", "近"),
            entry("S-03-07", "RED", "27-32", "0.6", "不变")
    );

    private EgoWeaponDisplayCatalog() {
    }

    public static WeaponSummary find(String abnormalityCode) {
        return WEAPONS.get(abnormalityCode);
    }

    private static Map.Entry<String, WeaponSummary> entry(String abnormalityCode,
                                                           String damageType,
                                                           String attackPower,
                                                           String attackSpeed,
                                                           String attackRange) {
        return Map.entry(abnormalityCode,
                new WeaponSummary(damageType, attackPower, attackSpeed, attackRange));
    }

    public record WeaponSummary(String damageType, String attackPower,
                                String attackSpeed, String attackRange) {
    }
}
