package com.wzz.lobotocraft.employee;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.List;

/** Server-global department expansion progress, persisted in the overworld. */
public final class DepartmentProgressData extends SavedData {
    private static final String ID = "lobotocraft_department_progress";
    private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 5;

    private final EnumMap<EmployeeDepartment, Integer> levels =
            new EnumMap<>(EmployeeDepartment.class);
    private int pendingDay;
    private int lastExpandedDay;
    private int migrationVersion;
    private EmployeeDepartment lastExpandedDepartment = EmployeeDepartment.UNASSIGNED;

    public DepartmentProgressData() {
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department != EmployeeDepartment.UNASSIGNED) levels.put(department, MIN_LEVEL);
        }
        levels.put(EmployeeDepartment.CONTROL, 1);
    }

    public static DepartmentProgressData get(MinecraftServer server) {
        DepartmentProgressData data = server.overworld().getDataStorage().computeIfAbsent(
                DepartmentProgressData::load, DepartmentProgressData::new, ID);
        data.migrateLegacyAssignments(server);
        return data;
    }

    public synchronized int level(EmployeeDepartment department) {
        if (department == null || department == EmployeeDepartment.UNASSIGNED) return MIN_LEVEL;
        return levels.getOrDefault(department, MIN_LEVEL);
    }

    public synchronized boolean isOpen(EmployeeDepartment department) {
        return level(department) > MIN_LEVEL;
    }

    public synchronized boolean isCentralLowerOpen() {
        return isOpen(EmployeeDepartment.CENTRAL);
    }

    /**
     * Four-cell departments grant one slot at levels I-IV; level V grants none.
     * Architecture has eight cells, so the same four slot-granting expansions open
     * two cells each. Control is the starting exception and always has four cells.
     */
    public synchronized int containmentSlotCapacity(EmployeeDepartment department, int centralSection) {
        int level = level(department);
        if (level <= 0 || department == EmployeeDepartment.UNASSIGNED) return 0;
        if (department == EmployeeDepartment.CONTROL) return 4;
        if (department == EmployeeDepartment.ARCHITECTURE) return Math.min(8, level * 2);
        return Math.min(4, level);
    }

    /** Mirrors LobCorp Sefira.allocateMax: normal 2+level (capped at 5), Tiphereth 5. */
    public synchronized int employeeCapacity(EmployeeDepartment department, int centralSection) {
        if (department == EmployeeDepartment.UNASSIGNED) return Integer.MAX_VALUE;
        if (department == EmployeeDepartment.CENTRAL) {
            return isOpen(department) && (centralSection <= 0 || isCentralLowerOpen()) ? 5 : 0;
        }
        int level = level(department);
        return level <= 0 ? 0 : Math.min(5, 2 + level);
    }

    public synchronized int pendingDay() {
        return pendingDay;
    }

    public synchronized int lastExpandedDay() {
        return lastExpandedDay;
    }

    public synchronized EmployeeDepartment lastExpandedDepartment() {
        return lastExpandedDepartment;
    }

    /** Creates this day's expansion decision. Day 1 intentionally has no decision. */
    public synchronized boolean prepareForDay(int day) {
        if (day < 2 || lastExpandedDay == day) return false;
        if (pendingDay > 0) return false;
        if (pendingDay == day) return false;
        pendingDay = day;
        setDirty();
        return true;
    }

    /** Atomically consumes the pending decision when all server-side checks pass. */
    public synchronized ExpandResult tryExpand(int day, EmployeeDepartment department) {
        if (day < 2) return ExpandResult.INVALID_DAY;
        if (department == null || department == EmployeeDepartment.UNASSIGNED) {
            return ExpandResult.INVALID_DEPARTMENT;
        }
        if (lastExpandedDay == day) return ExpandResult.ALREADY_EXPANDED;
        if (pendingDay == 0) return ExpandResult.NO_PENDING_EXPANSION;
        if (pendingDay != day) return ExpandResult.PENDING_DAY_MISMATCH;

        int currentLevel = level(department);
        if (currentLevel >= MAX_LEVEL) return ExpandResult.MAX_LEVEL;
        if (!meetsUnlockRequirements(department)) return ExpandResult.LOCKED;

        levels.put(department, currentLevel + 1);
        pendingDay = 0;
        lastExpandedDay = day;
        lastExpandedDepartment = department;
        setDirty();
        return ExpandResult.SUCCESS;
    }

    public synchronized boolean canExpand(EmployeeDepartment department) {
        return department != null
                && department != EmployeeDepartment.UNASSIGNED
                && level(department) < MAX_LEVEL
                && meetsUnlockRequirements(department);
    }

    /** OP recovery/testing hook. It changes only the level, not the daily expansion receipt. */
    public synchronized boolean setLevel(EmployeeDepartment department, int level) {
        if (department == null || department == EmployeeDepartment.UNASSIGNED
                || level < MIN_LEVEL || level > MAX_LEVEL) return false;
        if (department == EmployeeDepartment.CONTROL && level == 0) return false;
        levels.put(department, level);
        setDirty();
        return true;
    }

    public synchronized List<EmployeeDepartment> eligibleDepartments() {
        return java.util.Arrays.stream(EmployeeDepartment.values())
                .filter(this::canExpand)
                .toList();
    }

    /** Writes the compact, client-safe state used by expansion and management GUIs. */
    public synchronized CompoundTag writeSnapshot(CompoundTag tag) {
        CompoundTag levelTag = new CompoundTag();
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.UNASSIGNED) continue;
            levelTag.putInt(department.id(), level(department));
        }
        tag.put("Levels", levelTag);
        CompoundTag capacityTag = new CompoundTag();
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.UNASSIGNED) continue;
            capacityTag.putInt(department.id(), employeeCapacity(department, 0));
        }
        capacityTag.putInt("central_lower", employeeCapacity(EmployeeDepartment.CENTRAL, 1));
        tag.put("EmployeeCapacities", capacityTag);
        CompoundTag containmentTag = new CompoundTag();
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.UNASSIGNED) continue;
            containmentTag.putInt(department.id(), containmentSlotCapacity(department, 0));
        }
        containmentTag.putInt("central_lower",
                containmentSlotCapacity(EmployeeDepartment.CENTRAL, 1));
        tag.put("ContainmentCapacities", containmentTag);
        tag.putInt("PendingDay", pendingDay);

        ListTag eligibleTag = new ListTag();
        eligibleDepartments().forEach(department ->
                eligibleTag.add(StringTag.valueOf(department.id())));
        tag.put("EligibleDepartments", eligibleTag);
        return tag;
    }

    public static DepartmentProgressData load(CompoundTag tag) {
        DepartmentProgressData data = new DepartmentProgressData();
        CompoundTag levelTag = tag.getCompound("Levels");
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.UNASSIGNED) continue;
            if (levelTag.contains(department.id())) {
                data.levels.put(department,
                        clampLevel(levelTag.getInt(department.id())));
            }
        }

        // Older or empty data must never leave a world without the starting department.
        data.levels.put(EmployeeDepartment.CONTROL,
                Math.max(1, data.level(EmployeeDepartment.CONTROL)));
        data.pendingDay = Math.max(0, tag.getInt("PendingDay"));
        data.lastExpandedDay = Math.max(0, tag.getInt("LastExpandedDay"));
        data.lastExpandedDepartment = EmployeeDepartment.byId(
                tag.getString("LastExpandedDepartment"));
        data.migrationVersion = Math.max(0, tag.getInt("MigrationVersion"));
        if (data.lastExpandedDepartment == null) {
            data.lastExpandedDepartment = EmployeeDepartment.UNASSIGNED;
        }
        if (data.pendingDay == data.lastExpandedDay) data.pendingDay = 0;
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        CompoundTag levelTag = new CompoundTag();
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.UNASSIGNED) continue;
            levelTag.putInt(department.id(), level(department));
        }
        tag.put("Levels", levelTag);
        tag.putInt("PendingDay", pendingDay);
        tag.putInt("LastExpandedDay", lastExpandedDay);
        tag.putString("LastExpandedDepartment", lastExpandedDepartment.id());
        tag.putInt("MigrationVersion", migrationVersion);
        return tag;
    }

    /** Preserves pre-expansion test worlds without silently deleting their department assignments. */
    private synchronized void migrateLegacyAssignments(MinecraftServer server) {
        if (migrationVersion >= 1) return;
        for (EmployeeRecord record : FacilityPersonnelData.get(server).allRecords()) {
            if (record.retired() || record.department() == EmployeeDepartment.UNASSIGNED) continue;
            openLegacyPath(record.department(), record.centralSection());
        }
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.UNASSIGNED
                    || department == EmployeeDepartment.CENTRAL) continue;
            long assigned = FacilityPersonnelData.get(server).allRecords().stream()
                    .filter(record -> !record.retired() && record.department() == department)
                    .count();
            if (assigned > 0) ensureAtLeast(department, (int) Math.max(1, assigned - 2));
        }
        migrationVersion = 1;
        setDirty();
    }

    private void openLegacyPath(EmployeeDepartment department, int centralSection) {
        switch (department) {
            case CONTROL -> ensureAtLeast(EmployeeDepartment.CONTROL, 1);
            case INFORMATION -> {
                ensureAtLeast(EmployeeDepartment.CONTROL, MAX_LEVEL);
                ensureAtLeast(EmployeeDepartment.INFORMATION, 1);
            }
            case TRAINING, SAFETY -> {
                ensureAtLeast(EmployeeDepartment.CONTROL, MAX_LEVEL);
                ensureAtLeast(EmployeeDepartment.INFORMATION, MAX_LEVEL);
                ensureAtLeast(department, 1);
            }
            case CENTRAL -> {
                completeUpperLayer();
                ensureAtLeast(EmployeeDepartment.CENTRAL, centralSection > 0 ? 3 : 1);
            }
            case WELFARE, DISCIPLINARY -> {
                completeUpperLayer();
                ensureAtLeast(EmployeeDepartment.CENTRAL, MAX_LEVEL);
                ensureAtLeast(department, 1);
            }
            case RECORD, EXTRACTION -> {
                completeMiddleLayer();
                ensureAtLeast(department, 1);
            }
            case ARCHITECTURE -> {
                completeMiddleLayer();
                ensureAtLeast(EmployeeDepartment.RECORD, MAX_LEVEL);
                ensureAtLeast(EmployeeDepartment.EXTRACTION, MAX_LEVEL);
                ensureAtLeast(EmployeeDepartment.ARCHITECTURE, 1);
            }
            case UNASSIGNED -> {
            }
        }
    }

    private void completeUpperLayer() {
        ensureAtLeast(EmployeeDepartment.CONTROL, MAX_LEVEL);
        ensureAtLeast(EmployeeDepartment.INFORMATION, MAX_LEVEL);
        ensureAtLeast(EmployeeDepartment.TRAINING, MAX_LEVEL);
        ensureAtLeast(EmployeeDepartment.SAFETY, MAX_LEVEL);
    }

    private void completeMiddleLayer() {
        completeUpperLayer();
        ensureAtLeast(EmployeeDepartment.CENTRAL, MAX_LEVEL);
        ensureAtLeast(EmployeeDepartment.WELFARE, MAX_LEVEL);
        ensureAtLeast(EmployeeDepartment.DISCIPLINARY, MAX_LEVEL);
    }

    private void ensureAtLeast(EmployeeDepartment department, int level) {
        levels.put(department, Math.max(this.level(department), clampLevel(level)));
    }

    private boolean meetsUnlockRequirements(EmployeeDepartment department) {
        return switch (department) {
            case CONTROL -> true;
            case INFORMATION -> isComplete(EmployeeDepartment.CONTROL);
            case TRAINING, SAFETY -> isComplete(EmployeeDepartment.INFORMATION);
            case CENTRAL -> areComplete(EmployeeDepartment.CONTROL, EmployeeDepartment.INFORMATION,
                    EmployeeDepartment.TRAINING, EmployeeDepartment.SAFETY);
            case WELFARE, DISCIPLINARY -> isComplete(EmployeeDepartment.CENTRAL);
            case RECORD, EXTRACTION -> areComplete(EmployeeDepartment.CENTRAL,
                    EmployeeDepartment.WELFARE, EmployeeDepartment.DISCIPLINARY);
            case ARCHITECTURE -> areComplete(EmployeeDepartment.RECORD,
                    EmployeeDepartment.EXTRACTION);
            case UNASSIGNED -> false;
        };
    }

    private boolean isComplete(EmployeeDepartment department) {
        return level(department) >= MAX_LEVEL;
    }

    private boolean areComplete(EmployeeDepartment... departments) {
        for (EmployeeDepartment department : departments) {
            if (!isComplete(department)) return false;
        }
        return true;
    }

    private static int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    public enum ExpandResult {
        SUCCESS,
        INVALID_DAY,
        INVALID_DEPARTMENT,
        NO_PENDING_EXPANSION,
        PENDING_DAY_MISMATCH,
        ALREADY_EXPANDED,
        MAX_LEVEL,
        LOCKED
    }
}
